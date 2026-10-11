package com.eventhub.api.user.repository;

import com.eventhub.api.user.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    /**
     * 邮箱大小写不敏感查询。注册时按原样存储，登录时用这个方法匹配，
     * 避免 {@code Foo@x.com} 与 {@code foo@x.com} 被当成两个账号。
     */
    Optional<User> findByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCase(String email);
}
