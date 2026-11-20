package com.example.pms.support;

import com.example.pms.domain.User;
import com.example.pms.repo.UserRepository;
import org.springframework.stereotype.Component;

/** 读取当前登录用户实体，对应 Django 的 request.user。 */
@Component
public class CurrentUser {

    private final UserRepository users;

    public CurrentUser(UserRepository users) {
        this.users = users;
    }

    public User get() {
        return users.findByUsername(Auth.username())
                .orElseThrow(() -> new IllegalStateException("登录用户在数据库中不存在"));
    }

    public long id() {
        return get().getId();
    }
}
