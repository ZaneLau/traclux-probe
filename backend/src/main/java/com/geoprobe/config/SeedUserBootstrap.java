package com.geoprobe.config;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.geoprobe.config.properties.SeedUserProperties;
import com.geoprobe.entity.User;
import com.geoprobe.mapper.UserMapper;
import com.geoprobe.service.PasswordAuditService;
import com.geoprobe.service.UserTenancyBootstrapService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Slf4j
@Component
@RequiredArgsConstructor
public class SeedUserBootstrap implements ApplicationRunner {

    private final SeedUserProperties seedUserProperties;
    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final UserTenancyBootstrapService userTenancyBootstrapService;
    private final PasswordAuditService passwordAuditService;

    @Override
    public void run(ApplicationArguments args) {
        if (!seedUserProperties.isEnabled()) {
            return;
        }

        if (!StringUtils.hasText(seedUserProperties.getUsername()) || !StringUtils.hasText(seedUserProperties.getPassword())) {
            throw new IllegalStateException("APP_BOOTSTRAP_SEED_USER_ENABLED=true 时必须提供种子账号用户名和密码");
        }

        String username = seedUserProperties.getUsername().trim();
        User existingUser = userMapper.selectOne(new LambdaQueryWrapper<User>()
                .eq(User::getUsername, username)
                .last("LIMIT 1"));
        if (existingUser != null) {
            userTenancyBootstrapService.ensureUserTenancy(existingUser.getId());
            log.info("Seed account already exists: username={}", username);
            return;
        }

        User seedUser = User.builder()
                .username(username)
                .password(passwordEncoder.encode(seedUserProperties.getPassword()))
                .displayName(resolveDisplayName(username))
                .companyName(resolveCompanyName())
                .creditsRemaining(seedUserProperties.getCreditsRemaining())
                .role(seedUserProperties.getRole())
                .enabled(1)
                .build();

        int insertedRows = userMapper.insert(seedUser);
        if (insertedRows != 1 || seedUser.getId() == null) {
            throw new IllegalStateException("种子账号创建失败");
        }

        userTenancyBootstrapService.ensureUserTenancy(seedUser.getId());
        passwordAuditService.recordUserPasswordWrite(
                seedUser.getId(),
                seedUser.getUsername(),
                "USER_PASSWORD_BOOTSTRAP_SEEDED",
                "APP_BOOTSTRAP_SEED_USER"
        );

        log.info("Seed account created for alpha rehearsal: username={}", username);
    }

    private String resolveDisplayName(String username) {
        if (StringUtils.hasText(seedUserProperties.getDisplayName())) {
            return seedUserProperties.getDisplayName().trim();
        }
        return username;
    }

    private String resolveCompanyName() {
        if (StringUtils.hasText(seedUserProperties.getCompanyName())) {
            return seedUserProperties.getCompanyName().trim();
        }
        return "AeoProbe Alpha";
    }
}
