package com.geoprobe.service;

import com.geoprobe.common.BusinessException;
import com.geoprobe.common.ErrorCode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class AuthRateLimitService {

    private static final long MILLIS_PER_SECOND = 1_000L;
    private static final long CLEANUP_INTERVAL_OPS = 128L;
    private static final String UNKNOWN_IP = "unknown-ip";
    private static final String UNKNOWN_USER = "unknown-user";

    @Value("${app.security.auth-rate-limit.window-seconds:60}")
    private int windowSeconds;

    @Value("${app.security.auth-rate-limit.login-per-ip:60}")
    private int loginPerIp;

    @Value("${app.security.auth-rate-limit.login-per-user-ip:12}")
    private int loginPerUserIp;

    @Value("${app.security.auth-rate-limit.register-per-ip:30}")
    private int registerPerIp;

    @Value("${app.security.auth-rate-limit.register-per-user-ip:6}")
    private int registerPerUserIp;

    @Value("${app.security.auth-rate-limit.lookup-per-ip:60}")
    private int lookupPerIp;

    @Value("${app.security.auth-rate-limit.lookup-per-user-ip:12}")
    private int lookupPerUserIp;

    @Value("${app.security.auth-rate-limit.password-reset-per-ip:20}")
    private int passwordResetPerIp;

    @Value("${app.security.auth-rate-limit.password-reset-per-user-ip:3}")
    private int passwordResetPerUserIp;

    private final Map<String, WindowCounter> counters = new ConcurrentHashMap<>();
    private final AtomicLong operationCounter = new AtomicLong();

    public void checkLoginAllowed(String username, String clientIp) {
        String normalizedIp = normalizeIp(clientIp);
        String normalizedUsername = normalizeUsername(username);
        enforce("login:ip:" + normalizedIp, loginPerIp, "登录");
        enforce("login:user-ip:" + normalizedIp + ":" + normalizedUsername, loginPerUserIp, "登录");
    }

    public void checkRegisterAllowed(String username, String clientIp) {
        String normalizedIp = normalizeIp(clientIp);
        String normalizedUsername = normalizeUsername(username);
        enforce("register:ip:" + normalizedIp, registerPerIp, "注册");
        enforce("register:user-ip:" + normalizedIp + ":" + normalizedUsername, registerPerUserIp, "注册");
    }

    public void checkLookupAllowed(String username, String clientIp) {
        String normalizedIp = normalizeIp(clientIp);
        String normalizedUsername = normalizeUsername(username);
        enforce("lookup:ip:" + normalizedIp, lookupPerIp, "账号检查");
        enforce("lookup:user-ip:" + normalizedIp + ":" + normalizedUsername, lookupPerUserIp, "账号检查");
    }

    public void checkPasswordResetAllowed(String username, String clientIp) {
        String normalizedIp = normalizeIp(clientIp);
        String normalizedUsername = normalizeUsername(username);
        enforce("password-reset:ip:" + normalizedIp, passwordResetPerIp, "密码重置");
        enforce("password-reset:user-ip:" + normalizedIp + ":" + normalizedUsername, passwordResetPerUserIp, "密码重置");
    }

    private void enforce(String key, int limit, String actionName) {
        if (limit <= 0) {
            return;
        }

        long now = System.currentTimeMillis();
        long windowMillis = Math.max(1, windowSeconds) * MILLIS_PER_SECOND;

        WindowCounter counter = counters.compute(key, (k, current) -> {
            if (current == null || now - current.windowStartMillis >= windowMillis) {
                return new WindowCounter(now, 1);
            }
            current.count += 1;
            return current;
        });

        if (counter != null && counter.count > limit) {
            throw new BusinessException(ErrorCode.AUTH_RATE_LIMITED, actionName + "请求过于频繁，请稍后再试");
        }

        cleanupIfNeeded(now, windowMillis);
    }

    private void cleanupIfNeeded(long now, long windowMillis) {
        if (operationCounter.incrementAndGet() % CLEANUP_INTERVAL_OPS != 0) {
            return;
        }
        long expireBefore = now - (windowMillis * 2);
        counters.entrySet().removeIf(entry -> entry.getValue().windowStartMillis < expireBefore);
    }

    private String normalizeIp(String clientIp) {
        if (!StringUtils.hasText(clientIp)) {
            return UNKNOWN_IP;
        }
        return clientIp.trim();
    }

    private String normalizeUsername(String username) {
        if (!StringUtils.hasText(username)) {
            return UNKNOWN_USER;
        }
        return username.trim().toLowerCase(Locale.ROOT);
    }

    private static class WindowCounter {
        private final long windowStartMillis;
        private int count;

        private WindowCounter(long windowStartMillis, int count) {
            this.windowStartMillis = windowStartMillis;
            this.count = count;
        }
    }
}
