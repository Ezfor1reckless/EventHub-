package com.eventhub.api.security;

import com.eventhub.api.user.domain.Role;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * 从 {@code Authorization: Bearer <token>} 还原认证上下文。
 *
 * <p>令牌非法时<b>不直接返回 401</b>，而是留空上下文继续走过滤器链：
 * 受保护的路由最后由 {@link RestAuthenticationEntryPoint} 统一给出 401，
 * 公开路由则照常放行。这样同一段代码不需要知道当前路由是否需要认证。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtTokenProvider tokenProvider;
    private final TokenBlacklistService blacklist;

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain chain) throws ServletException, IOException {

        String token = extractToken(request);
        if (token != null && SecurityContextHolder.getContext().getAuthentication() == null) {
            try {
                Claims claims = tokenProvider.parseAccessToken(token).getPayload();
                if (!blacklist.isBlacklisted(claims.getId())) {
                    SecurityContextHolder.getContext().setAuthentication(buildAuthentication(claims, request));
                }
            } catch (JwtException | IllegalArgumentException ex) {
                // 令牌坏了/过期了。清空上下文，交给后续的 EntryPoint 决定是否拒绝。
                SecurityContextHolder.clearContext();
                log.debug("访问令牌无效: {}", ex.getMessage());
            }
        }

        chain.doFilter(request, response);
    }

    private UsernamePasswordAuthenticationToken buildAuthentication(Claims claims, HttpServletRequest request) {
        Long userId = Long.valueOf(claims.getSubject());
        String email = claims.get("email", String.class);

        List<String> rawRoles = claims.get("roles", List.class);
        Set<Role> roles = rawRoles == null
                ? EnumSet.noneOf(Role.class)
                : rawRoles.stream().map(Role::valueOf).collect(java.util.stream.Collectors.toCollection(
                        () -> EnumSet.noneOf(Role.class)));

        var authorities = roles.stream().map(r -> new SimpleGrantedAuthority(r.authority())).toList();
        var principal = new AuthPrincipal(userId, email, roles);

        var authentication = new UsernamePasswordAuthenticationToken(principal, null, authorities);
        authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
        return authentication;
    }

    private String extractToken(HttpServletRequest request) {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header != null && header.startsWith(BEARER_PREFIX)) {
            String value = header.substring(BEARER_PREFIX.length()).trim();
            return value.isEmpty() ? null : value;
        }
        return null;
    }
}
