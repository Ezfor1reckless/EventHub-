package com.eventhub.api.auth.service;

import com.eventhub.api.auth.repository.RefreshTokenRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 「吊销某用户全部会话」的唯一入口。
 *
 * <p>存在的理由是事务传播：{@link AuthService#refresh} 在判定刷新令牌泄露后会
 * 抛出异常，而抛出运行时异常会让<b>它所在的整个事务回滚</b>——如果吊销动作留在
 * 那个事务里，就会被一并回滚，泄露处置形同虚设。
 *
 * <p>用 {@code REQUIRES_NEW} 挂起外层事务、另起一个事务提交，
 * 这样「吊销」是既成事实，不受调用方后续回滚的影响。
 */
@Service
@RequiredArgsConstructor
public class SessionRevocationService {

    private final RefreshTokenRepository refreshTokenRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int revokeAllSessions(Long userId) {
        return refreshTokenRepository.revokeAllForUser(userId);
    }
}
