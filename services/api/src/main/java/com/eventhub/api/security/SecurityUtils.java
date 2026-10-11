package com.eventhub.api.security;

import com.eventhub.api.common.exception.BusinessException;
import com.eventhub.api.common.exception.ErrorCode;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;

/**
 * 读取当前认证主体的工具方法。
 */
public final class SecurityUtils {

    private SecurityUtils() {
    }

    public static Optional<AuthPrincipal> currentPrincipal() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof AuthPrincipal principal) {
            return Optional.of(principal);
        }
        return Optional.empty();
    }

    /** 受保护接口里取当前用户 id；取不到说明配置有误（该路由本不该匿名可达）。 */
    public static Long requireUserId() {
        return currentPrincipal()
                .map(AuthPrincipal::userId)
                .orElseThrow(() -> BusinessException.of(ErrorCode.UNAUTHORIZED));
    }
}
