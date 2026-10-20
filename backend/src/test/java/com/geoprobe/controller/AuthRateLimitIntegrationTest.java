package com.geoprobe.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.HashMap;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "app.security.auth-rate-limit.window-seconds=60",
        "app.security.auth-rate-limit.login-per-ip=6",
        "app.security.auth-rate-limit.login-per-user-ip=2",
        "app.security.auth-rate-limit.register-per-ip=3",
        "app.security.auth-rate-limit.register-per-user-ip=3",
        "app.security.auth-rate-limit.lookup-per-ip=3",
        "app.security.auth-rate-limit.lookup-per-user-ip=2",
        "app.security.auth-rate-limit.password-reset-per-ip=3",
        "app.security.auth-rate-limit.password-reset-per-user-ip=2"
})
class AuthRateLimitIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void shouldRateLimitLoginForSameUserAndIp() throws Exception {
        String ip = "203.0.113.10";
        String username = "rate-login-" + System.nanoTime() + "@example.com";
        String password = "StrongPass123";

        register(username, password, ip);

        Map<String, Object> loginPayload = new HashMap<>();
        loginPayload.put("username", username);
        loginPayload.put("password", "wrong-password");

        for (int i = 0; i < 2; i++) {
            mockMvc.perform(post("/api/v1/auth/login")
                            .header("X-Forwarded-For", ip)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(loginPayload)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value(1002));
        }

        mockMvc.perform(post("/api/v1/auth/login")
                        .header("X-Forwarded-For", ip)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginPayload)))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value(1003));
    }

    @Test
    void shouldRateLimitRegisterForSameIp() throws Exception {
        String ip = "203.0.113.11";
        for (int i = 0; i < 3; i++) {
            register("rate-register-" + i + "-" + System.nanoTime() + "@example.com", "StrongPass123", ip);
        }

        mockMvc.perform(post("/api/v1/auth/register")
                        .header("X-Forwarded-For", ip)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registerPayload("rate-register-block-" + System.nanoTime() + "@example.com", "StrongPass123"))))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value(1003));
    }

    @Test
    void shouldRateLimitLookupForSameUserAndIp() throws Exception {
        String ip = "203.0.113.12";
        Map<String, Object> lookupPayload = Map.of("username", "rate-lookup-" + System.nanoTime() + "@example.com");

        for (int i = 0; i < 2; i++) {
            mockMvc.perform(post("/api/v1/auth/lookup")
                            .header("X-Forwarded-For", ip)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(lookupPayload)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(0));
        }

        mockMvc.perform(post("/api/v1/auth/lookup")
                        .header("X-Forwarded-For", ip)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(lookupPayload)))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value(1003));
    }

    @Test
    void shouldRateLimitPasswordResetForSameUserAndIp() throws Exception {
        String ip = "203.0.113.13";
        Map<String, Object> payload = Map.of("username", "rate-reset-" + System.nanoTime() + "@example.com");

        for (int i = 0; i < 2; i++) {
            mockMvc.perform(post("/api/v1/auth/password-reset/request")
                            .header("X-Forwarded-For", ip)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(payload)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(0));
        }

        mockMvc.perform(post("/api/v1/auth/password-reset/request")
                        .header("X-Forwarded-For", ip)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value(1003));
    }

    private void register(String username, String password, String ip) throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .header("X-Forwarded-For", ip)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registerPayload(username, password))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
    }

    private Map<String, Object> registerPayload(String username, String password) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("username", username);
        payload.put("password", password);
        payload.put("displayName", "Rate User");
        payload.put("companyName", "GeoProbe");
        return payload;
    }
}
