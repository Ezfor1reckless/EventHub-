package com.eventhub.api.auth.repository;

import com.eventhub.api.auth.domain.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    /** 注销时一次性吊销该用户所有未失效的刷新令牌（所有设备下线）。 */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update RefreshToken t set t.revoked = true where t.userId = :userId and t.revoked = false")
    int revokeAllForUser(@Param("userId") Long userId);

    /** 清理任务用：删除已过期或已吊销的记录。 */
    @Modifying
    @Query("delete from RefreshToken t where t.expiresAt < :cutoff or t.revoked = true")
    int deleteStale(@Param("cutoff") Instant cutoff);
}
