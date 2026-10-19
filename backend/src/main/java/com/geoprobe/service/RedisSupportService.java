package com.geoprobe.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.connection.stream.StreamRecords;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class RedisSupportService {

    private final ObjectProvider<StringRedisTemplate> stringRedisTemplateProvider;
    private final ObjectMapper objectMapper;

    @EventListener(ApplicationReadyEvent.class)
    public void logStartupStatus() {
        if (!isEnabled()) {
            log.info("Redis support disabled: StringRedisTemplate bean not available");
            return;
        }
        if (ping()) {
            log.info("Redis connectivity check passed");
            return;
        }
        log.warn("Redis template is present but ping failed");
    }

    public boolean isEnabled() {
        return stringRedisTemplateProvider.getIfAvailable() != null;
    }

    public boolean ping() {
        StringRedisTemplate redisTemplate = stringRedisTemplateProvider.getIfAvailable();
        if (redisTemplate == null) {
            return false;
        }
        try {
            String pong = redisTemplate.execute((RedisCallback<String>) connection -> connection.ping());
            return StringUtils.hasText(pong);
        } catch (Exception ex) {
            log.warn("Redis ping failed: {}", ex.getMessage());
            return false;
        }
    }

    public boolean setIfAbsent(String key, String value, Duration ttl, String purpose) {
        StringRedisTemplate redisTemplate = stringRedisTemplateProvider.getIfAvailable();
        if (redisTemplate == null || !StringUtils.hasText(key) || value == null) {
            return false;
        }
        try {
            return Boolean.TRUE.equals(redisTemplate.opsForValue().setIfAbsent(key, value, ttl));
        } catch (Exception ex) {
            log.warn("Redis setIfAbsent failed, key={}, purpose={}, reason={}", key, purpose, ex.getMessage());
            return false;
        }
    }

    public void setString(String key, String value, Duration ttl, String purpose) {
        StringRedisTemplate redisTemplate = stringRedisTemplateProvider.getIfAvailable();
        if (redisTemplate == null || !StringUtils.hasText(key) || value == null) {
            return;
        }
        try {
            if (ttl != null && !ttl.isZero() && !ttl.isNegative()) {
                redisTemplate.opsForValue().set(key, value, ttl);
            } else {
                redisTemplate.opsForValue().set(key, value);
            }
        } catch (Exception ex) {
            log.warn("Redis string write failed, key={}, purpose={}, reason={}", key, purpose, ex.getMessage());
        }
    }

    public String getString(String key, String purpose) {
        StringRedisTemplate redisTemplate = stringRedisTemplateProvider.getIfAvailable();
        if (redisTemplate == null || !StringUtils.hasText(key)) {
            return null;
        }
        try {
            return redisTemplate.opsForValue().get(key);
        } catch (Exception ex) {
            log.warn("Redis string read failed, key={}, purpose={}, reason={}", key, purpose, ex.getMessage());
            return null;
        }
    }

    public void setJson(String key, Object value, Duration ttl, String purpose) {
        if (value == null) {
            return;
        }
        try {
            setString(key, objectMapper.writeValueAsString(value), ttl, purpose);
        } catch (Exception ex) {
            log.warn("Redis json write failed, key={}, purpose={}, reason={}", key, purpose, ex.getMessage());
        }
    }

    public <T> T getJson(String key, TypeReference<T> typeReference, String purpose) {
        String payload = getString(key, purpose);
        if (!StringUtils.hasText(payload)) {
            return null;
        }
        try {
            return objectMapper.readValue(payload, typeReference);
        } catch (Exception ex) {
            log.warn("Redis json read failed, key={}, purpose={}, reason={}", key, purpose, ex.getMessage());
            return null;
        }
    }

    public void delete(String key, String purpose) {
        StringRedisTemplate redisTemplate = stringRedisTemplateProvider.getIfAvailable();
        if (redisTemplate == null || !StringUtils.hasText(key)) {
            return;
        }
        try {
            redisTemplate.delete(key);
        } catch (Exception ex) {
            log.warn("Redis delete failed, key={}, purpose={}, reason={}", key, purpose, ex.getMessage());
        }
    }

    public long increment(String key, Duration ttl, String purpose) {
        StringRedisTemplate redisTemplate = stringRedisTemplateProvider.getIfAvailable();
        if (redisTemplate == null || !StringUtils.hasText(key)) {
            return 0L;
        }
        try {
            Long value = redisTemplate.opsForValue().increment(key);
            if (value == null) {
                return 0L;
            }
            if (ttl != null && !ttl.isZero() && !ttl.isNegative()) {
                redisTemplate.expire(key, ttl);
            }
            return Math.max(0L, value);
        } catch (Exception ex) {
            log.warn("Redis increment failed, key={}, purpose={}, reason={}", key, purpose, ex.getMessage());
            return 0L;
        }
    }

    public void appendJsonListItem(String key, Object value, Duration ttl, long maxLength, String purpose) {
        StringRedisTemplate redisTemplate = stringRedisTemplateProvider.getIfAvailable();
        if (redisTemplate == null || !StringUtils.hasText(key) || value == null) {
            return;
        }
        try {
            String payload = objectMapper.writeValueAsString(value);
            redisTemplate.opsForList().rightPush(key, payload);
            if (maxLength > 0) {
                redisTemplate.opsForList().trim(key, -maxLength, -1L);
            }
            if (ttl != null && !ttl.isZero() && !ttl.isNegative()) {
                redisTemplate.expire(key, ttl);
            }
        } catch (Exception ex) {
            log.warn("Redis list append failed, key={}, purpose={}, reason={}", key, purpose, ex.getMessage());
        }
    }

    public List<String> readStringList(String key, long start, long end, String purpose) {
        StringRedisTemplate redisTemplate = stringRedisTemplateProvider.getIfAvailable();
        if (redisTemplate == null || !StringUtils.hasText(key)) {
            return List.of();
        }
        try {
            List<String> items = redisTemplate.opsForList().range(key, start, end);
            return items == null ? List.of() : List.copyOf(items);
        } catch (Exception ex) {
            log.warn("Redis list read failed, key={}, purpose={}, reason={}", key, purpose, ex.getMessage());
            return List.of();
        }
    }

    public String appendStreamEntry(String key, Map<String, String> fields, Duration ttl, String purpose) {
        StringRedisTemplate redisTemplate = stringRedisTemplateProvider.getIfAvailable();
        if (redisTemplate == null || !StringUtils.hasText(key) || fields == null || fields.isEmpty()) {
            return null;
        }
        try {
            RecordId recordId = redisTemplate.opsForStream().add(
                    StreamRecords.newRecord()
                            .ofMap(fields)
                            .withStreamKey(key)
            );
            if (ttl != null && !ttl.isZero() && !ttl.isNegative()) {
                redisTemplate.expire(key, ttl);
            }
            return recordId == null ? null : recordId.getValue();
        } catch (Exception ex) {
            log.warn("Redis stream append failed, key={}, purpose={}, reason={}", key, purpose, ex.getMessage());
            return null;
        }
    }

    public <T> List<T> readJsonList(String key, TypeReference<T> typeReference, String purpose) {
        List<String> items = readStringList(key, 0, -1, purpose);
        if (items.isEmpty()) {
            return List.of();
        }
        List<T> result = new ArrayList<>(items.size());
        for (String item : items) {
            if (!StringUtils.hasText(item)) {
                continue;
            }
            try {
                result.add(objectMapper.readValue(item, typeReference));
            } catch (Exception ex) {
                log.warn("Redis list json decode failed, key={}, purpose={}, reason={}", key, purpose, ex.getMessage());
            }
        }
        return List.copyOf(result);
    }
}
