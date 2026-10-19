package com.geoprobe.config;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.geoprobe.entity.User;
import com.geoprobe.mapper.UserMapper;
import com.geoprobe.service.PasswordAuditService;
import com.geoprobe.service.UserTenancyBootstrapService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@Profile({"dev", "local-seed"})
@RequiredArgsConstructor
public class GeodemoAccountBootstrap implements ApplicationRunner {

    private static final String DEMO_USERNAME = "geodemo";
    private static final String DEMO_PASSWORD = "123456";
    private static final int DEMO_INITIAL_CREDITS = 20;

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final UserTenancyBootstrapService userTenancyBootstrapService;
    private final PasswordAuditService passwordAuditService;

    @Override
    public void run(ApplicationArguments args) {
        User existing = userMapper.selectOne(
                new LambdaQueryWrapper<User>()
                        .eq(User::getUsername, DEMO_USERNAME)
                        .last("LIMIT 1")
        );
        if (existing != null) {
            return;
        }

        User demoUser = User.builder()
                .username(DEMO_USERNAME)
                .password(passwordEncoder.encode(DEMO_PASSWORD))
                .displayName("Geo Demo")
                .companyName("GeoProbe Local")
                .creditsRemaining(DEMO_INITIAL_CREDITS)
                .role(0)
                .enabled(1)
                .build();
        userMapper.insert(demoUser);
        userTenancyBootstrapService.ensureUserTenancy(demoUser.getId());
        passwordAuditService.recordUserPasswordWrite(
                demoUser.getId(),
                demoUser.getUsername(),
                "USER_PASSWORD_BOOTSTRAP_SEEDED",
                "GEODEMO_LOCAL_SEED"
        );
        log.info("Created local demo account: username={}", DEMO_USERNAME);
    }
}
