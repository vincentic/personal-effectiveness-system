package com.example.pms.support;

import com.example.pms.repo.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/** users 表为空时创建默认管理员，密码为 BCrypt（Gin 版可直接登录同一账号）。 */
@Component
public class AppInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AppInitializer.class);

    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final String adminUsername;
    private final String adminPassword;

    public AppInitializer(UserRepository users,
                          PasswordEncoder encoder,
                          @Value("${pms.admin.username}") String adminUsername,
                          @Value("${pms.admin.password}") String adminPassword) {
        this.users = users;
        this.encoder = encoder;
        this.adminUsername = adminUsername;
        this.adminPassword = adminPassword;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (users.count() > 0) {
            return;
        }
        users.create(adminUsername, encoder.encode(adminPassword), "系统管理员", true);
        log.info("已创建默认管理员账号：{}（首次启动，生产环境请通过 ADMIN_USERNAME / ADMIN_PASSWORD 覆盖）",
                adminUsername);
    }
}
