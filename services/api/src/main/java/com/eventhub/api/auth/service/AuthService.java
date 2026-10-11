package com.eventhub.api.auth.service;

import com.eventhub.api.auth.domain.RefreshToken;
import com.eventhub.api.auth.dto.LoginRequest;
import com.eventhub.api.auth.dto.RegisterRequest;
import com.eventhub.api.auth.dto.TokenResponse;
import com.eventhub.api.audit.service.AuditService;
import com.eventhub.api.auth.repository.RefreshTokenRepository;
import com.eventhub.api.common.exception.BusinessException;
import com.eventhub.api.common.exception.ErrorCode;
import com.eventhub.api.common.ratelimit.RateLimitService;
import com.eventhub.api.common.web.ClientIpResolver;
import com.eventhub.api.outbox.OutboxEventTypes;
import com.eventhub.api.security.JwtTokenProvider;
import com.eventhub.api.security.TokenBlacklistService;
import com.eventhub.api.user.domain.Role;
import com.eventhub.api.user.domain.User;
import com.eventhub.api.user.domain.UserStatus;
import com.eventhub.api.user.repository.UserRepository;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.EnumSet;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private static final int REFRESH_TOKEN_BYTES = 32;
    private static final SecureRandom RANDOM = new SecureRandom();

    /**
     * 登录限流：同一 IP 每分钟 10 次。
     * 目的是把在线暴力破解的成本抬到不现实，同时不误伤正常用户的手滑重试。
     */
    private static final int LOGIN_LIMIT = 10;
    private static final Duration LOGIN_WINDOW = Duration.ofMinutes(1);

    /** 注册限流：同一 IP 每小时 5 次，挡批量注册小号。 */
    private static final int REGISTER_LIMIT = 5;
    private static final Duration REGISTER_WINDOW = Duration.ofHours(1);

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final SessionRevocationService sessionRevocation;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider tokenProvider;
    private final TokenBlacklistService tokenBlacklist;
    private final AuditService auditService;
    private final RateLimitService rateLimitService;
    private final ClientIpResolver clientIpResolver;

    @Transactional
    public TokenResponse register(RegisterRequest request) {
        rateLimitService.check("register", clientIpResolver.currentIp(), REGISTER_LIMIT, REGISTER_WINDOW);

        if (userRepository.existsByEmailIgnoreCase(request.email())) {
            throw BusinessException.of(ErrorCode.EMAIL_ALREADY_REGISTERED);
        }

        User user = User.builder()
                .email(request.email().trim())
                .passwordHash(passwordEncoder.encode(request.password()))
                .nickname(request.nickname().trim())
                .phone(request.phone())
                .status(UserStatus.ACTIVE)
                .roles(EnumSet.of(Role.USER))
                .build();

        userRepository.save(user);
        log.info("新用户注册 userId={}", user.getId());

        auditService.record(user.getId(), OutboxEventTypes.AUDIT_USER_REGISTERED,
                "User", user.getId(), "email=" + user.getEmail());

        return issueTokens(user);
    }

    @Transactional
    public TokenResponse login(LoginRequest request) {
        rateLimitService.check("login", clientIpResolver.currentIp(), LOGIN_LIMIT, LOGIN_WINDOW);

        // 邮箱不存在与密码错误返回同一个错误码，避免账号枚举
        User user = userRepository.findByEmailIgnoreCase(request.email().trim())
                .orElseThrow(() -> BusinessException.of(ErrorCode.INVALID_CREDENTIALS));

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw BusinessException.of(ErrorCode.INVALID_CREDENTIALS);
        }
        if (!user.isActive()) {
            throw BusinessException.of(ErrorCode.ACCOUNT_DISABLED);
        }

        auditService.record(user.getId(), OutboxEventTypes.AUDIT_USER_LOGGED_IN,
                "User", user.getId(), null);

        return issueTokens(user);
    }

    /**
     * 刷新令牌并轮换：旧令牌立即作废，返回一对全新的令牌。
     *
     * <p>重放检测：如果收到的令牌<b>已经被吊销过</b>，说明它可能已泄露并被二次使用。
     * 此时吊销该用户全部刷新令牌，强制所有设备重新登录——这是 OAuth 2.0
     * refresh token rotation 的标准处置。
     */
    @Transactional
    public TokenResponse refresh(String rawRefreshToken) {
        String hash = sha256Hex(rawRefreshToken);

        RefreshToken stored = refreshTokenRepository.findByTokenHash(hash)
                .orElseThrow(() -> BusinessException.of(ErrorCode.INVALID_REFRESH_TOKEN));

        if (stored.isRevoked()) {
            log.warn("检测到已吊销的刷新令牌被重用 userId={}，吊销该用户全部会话", stored.getUserId());
            // 必须走 REQUIRES_NEW：紧接着抛出的异常会回滚当前事务，
            // 吊销若留在本事务里就会被一起回滚，泄露处置等于没做。
            sessionRevocation.revokeAllSessions(stored.getUserId());
            throw BusinessException.of(ErrorCode.TOKEN_REVOKED);
        }
        if (!stored.isUsable()) {
            throw BusinessException.of(ErrorCode.INVALID_REFRESH_TOKEN);
        }

        stored.revoke();

        User user = userRepository.findById(stored.getUserId())
                .orElseThrow(() -> BusinessException.of(ErrorCode.INVALID_REFRESH_TOKEN));
        if (!user.isActive()) {
            throw BusinessException.of(ErrorCode.ACCOUNT_DISABLED);
        }

        return issueTokens(user);
    }

    /**
     * 登出。两件事都要做：
     * <ol>
     *   <li>把当前 access token 的 jti 加入黑名单——否则它在剩余寿命内仍然可用；</li>
     *   <li>吊销该用户全部 refresh token——否则客户端能立刻换一对新的回来。</li>
     * </ol>
     *
     * @param rawAccessToken 当前请求携带的访问令牌，可能是 null 或已过期
     */
    @Transactional
    public void logout(String rawAccessToken, Long userId) {
        blacklistAccessToken(rawAccessToken);
        int revoked = sessionRevocation.revokeAllSessions(userId);
        log.info("用户登出 userId={}，吊销刷新令牌 {} 条", userId, revoked);
    }

    private void blacklistAccessToken(String rawAccessToken) {
        if (rawAccessToken == null || rawAccessToken.isBlank()) {
            return;
        }
        try {
            Claims claims = tokenProvider.parseAccessToken(rawAccessToken).getPayload();
            Duration remaining = Duration.between(Instant.now(), claims.getExpiration().toInstant());
            tokenBlacklist.blacklist(claims.getId(), remaining);
        } catch (JwtException | IllegalArgumentException ex) {
            // 令牌本来就无效或已过期，无需拉黑——它已经没用了
            log.debug("登出时访问令牌不可解析，跳过黑名单: {}", ex.getMessage());
        }
    }

    private TokenResponse issueTokens(User user) {
        String accessToken = tokenProvider.createAccessToken(user);

        String rawRefreshToken = generateRefreshToken();
        RefreshToken refreshToken = new RefreshToken(
                user.getId(),
                sha256Hex(rawRefreshToken),
                Instant.now().plus(tokenProvider.refreshTtl())
        );
        refreshTokenRepository.save(refreshToken);

        return TokenResponse.of(accessToken, rawRefreshToken, tokenProvider.accessTtl().toSeconds());
    }

    /** 256 位熵的随机串，Base64URL 无填充。不是 JWT——它必须可被服务端单独吊销。 */
    private static String generateRefreshToken() {
        byte[] bytes = new byte[REFRESH_TOKEN_BYTES];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String sha256Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(hashed.length * 2);
            for (byte b : hashed) {
                sb.append(Character.forDigit((b >> 4) & 0xF, 16));
                sb.append(Character.forDigit(b & 0xF, 16));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException ex) {
            // SHA-256 是 JDK 强制支持的算法，走不到这里
            throw new IllegalStateException("SHA-256 不可用", ex);
        }
    }
}
