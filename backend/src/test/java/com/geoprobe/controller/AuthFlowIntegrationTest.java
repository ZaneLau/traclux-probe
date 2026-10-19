package com.geoprobe.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.geoprobe.entity.PasswordResetToken;
import com.geoprobe.mapper.PasswordResetTokenMapper;
import com.geoprobe.mapper.UserMapper;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthFlowIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private PasswordResetTokenMapper passwordResetTokenMapper;

    @Test
    void shouldPassAuthAndJwtFlow() throws Exception {
        String username = "user" + System.currentTimeMillis() + "@example.com";
        String password = "StrongPass123";

        Map<String, Object> registerPayload = new HashMap<>();
        registerPayload.put("username", username);
        registerPayload.put("password", password);
        registerPayload.put("displayName", "Test User");
        registerPayload.put("companyName", "GeoProbe");

        MvcResult registerResult = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registerPayload)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.username").value(username))
                .andReturn();

        JsonNode registerBody = objectMapper.readTree(registerResult.getResponse().getContentAsString());
        String registerToken = registerBody.path("data").path("token").asText();
        assertFalse(registerToken.isBlank());
        Cookie registerAuthCookie = registerResult.getResponse().getCookie("geoprobe_auth_token");
        assertNotNull(registerAuthCookie);

        Map<String, Object> loginPayload = new HashMap<>();
        loginPayload.put("username", username);
        loginPayload.put("password", password);

        MvcResult loginResult = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginPayload)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.username").value(username))
                .andReturn();

        JsonNode loginBody = objectMapper.readTree(loginResult.getResponse().getContentAsString());
        String loginToken = loginBody.path("data").path("token").asText();
        assertFalse(loginToken.isBlank());
        String authCookie = loginResult.getResponse().getHeader(HttpHeaders.SET_COOKIE);
        assertNotNull(authCookie);
        Cookie authCookieObject = loginResult.getResponse().getCookie("geoprobe_auth_token");
        assertNotNull(authCookieObject);

        mockMvc.perform(post("/api/v1/auth/logout")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + loginToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        mockMvc.perform(get("/api/v1/brand-spaces"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401));

        mockMvc.perform(get("/api/v1/health/llm"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401));

        mockMvc.perform(get("/api/v1/brand-spaces")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + loginToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.items").isArray())
                .andExpect(jsonPath("$.data.total").value(0));

        mockMvc.perform(get("/api/v1/brand-spaces")
                        .param("token", loginToken))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401));

        mockMvc.perform(get("/api/v1/auth/session")
                        .cookie(authCookieObject))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.userId").isString())
                .andExpect(jsonPath("$.data.username").value(username));

        mockMvc.perform(get("/api/v1/brand-spaces")
                        .cookie(authCookieObject))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.items").isArray())
                .andExpect(jsonPath("$.data.total").value(0));

        mockMvc.perform(post("/api/v1/auth/logout")
                        .cookie(authCookieObject))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(result -> {
                    String clearCookie = result.getResponse().getHeader(HttpHeaders.SET_COOKIE);
                    assertNotNull(clearCookie);
                    assertFalse(clearCookie.isBlank());
                    org.assertj.core.api.Assertions.assertThat(clearCookie).contains("geoprobe_auth_token=");
                    org.assertj.core.api.Assertions.assertThat(clearCookie).contains("Max-Age=0");
                });

        mockMvc.perform(get("/api/v1/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.status").value("UP"))
                .andExpect(jsonPath("$.data.version").value("0.1.0"));

        mockMvc.perform(get("/api/v1/credits/balance")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + loginToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.creditsRemaining").value(0))
                .andExpect(jsonPath("$.data.totalPurchased").value(0))
                .andExpect(jsonPath("$.data.totalConsumed").value(0));
    }

    @Test
    void shouldRejectJwtWhenUserNoLongerExists() throws Exception {
        String username = "deleted-user-" + System.currentTimeMillis() + "@example.com";
        String password = "StrongPass123";

        Map<String, Object> registerPayload = new HashMap<>();
        registerPayload.put("username", username);
        registerPayload.put("password", password);

        MvcResult registerResult = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registerPayload)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn();

        JsonNode registerBody = objectMapper.readTree(registerResult.getResponse().getContentAsString());
        String token = registerBody.path("data").path("token").asText();
        String userId = registerBody.path("data").path("userId").asText();

        userMapper.deleteById(userId);

        mockMvc.perform(get("/api/v1/credits/balance")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401))
                .andExpect(jsonPath("$.msg").value("未认证或Token过期"));
    }

    @Test
    void shouldLookupExistingAccountAndNextAction() throws Exception {
        String username = "lookup-user-" + System.currentTimeMillis() + "@example.com";
        String password = "StrongPass123";

        mockMvc.perform(post("/api/v1/auth/lookup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("username", username))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.registered").value(false))
                .andExpect(jsonPath("$.data.nextAction").value("register"));

        Map<String, Object> registerPayload = new HashMap<>();
        registerPayload.put("username", username);
        registerPayload.put("password", password);

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registerPayload)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        mockMvc.perform(post("/api/v1/auth/lookup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("username", username))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.registered").value(true))
                .andExpect(jsonPath("$.data.nextAction").value("login"));
    }

    @Test
    void shouldRejectTooShortRegistrationPassword() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "username", "short-password-" + System.nanoTime() + "@example.com",
                                "password", "123456"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    void shouldBlockCrossOriginCookieAuthenticatedWrite() throws Exception {
        String username = "csrf-guard-" + System.currentTimeMillis() + "@example.com";
        String password = "StrongPass123";

        MvcResult registerResult = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "username", username,
                                "password", password))))
                .andExpect(status().isOk())
                .andReturn();

        Cookie authCookieObject = registerResult.getResponse().getCookie("geoprobe_auth_token");
        assertNotNull(authCookieObject);

        mockMvc.perform(post("/api/v1/auth/logout")
                        .header(HttpHeaders.ORIGIN, "https://evil.example")
                        .cookie(authCookieObject))
                .andExpect(status().isForbidden());
    }

    @Test
    void shouldRequestPasswordResetWithoutRevealingAccountExistence() throws Exception {
        String username = "reset-request-" + System.currentTimeMillis() + "@example.com";
        String password = "StrongPass123";

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "username", username,
                                "password", password))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        mockMvc.perform(post("/api/v1/auth/password-reset/request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("username", username))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        mockMvc.perform(post("/api/v1/auth/password-reset/request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("username", "missing-" + System.nanoTime() + "@example.com"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
    }

    @Test
    void shouldConfirmPasswordResetAndRejectTokenReuse() throws Exception {
        String username = "reset-confirm-" + System.currentTimeMillis() + "@example.com";
        String oldPassword = "StrongPass123";
        String newPassword = "StrongerPass456";

        MvcResult registerResult = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "username", username,
                                "password", oldPassword))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn();

        String userId = objectMapper.readTree(registerResult.getResponse().getContentAsString())
                .path("data")
                .path("userId")
                .asText();
        String resetToken = "reset-token-" + System.nanoTime();
        passwordResetTokenMapper.insert(PasswordResetToken.builder()
                .userId(userId)
                .username(username)
                .tokenHash(sha256(resetToken))
                .expiresAt(LocalDateTime.now().plusMinutes(10))
                .requestedIp("127.0.0.1")
                .createdAt(LocalDateTime.now())
                .build());

        mockMvc.perform(post("/api/v1/auth/password-reset/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "token", resetToken,
                                "password", newPassword))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "username", username,
                                "password", oldPassword))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(1002));

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "username", username,
                                "password", newPassword))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        mockMvc.perform(post("/api/v1/auth/password-reset/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "token", resetToken,
                                "password", "AnotherPass789"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    void shouldInvalidateOlderPasswordResetTokenWhenNewTokenIsIssued() throws Exception {
        String username = "reset-supersede-" + System.currentTimeMillis() + "@example.com";
        String password = "StrongPass123";

        MvcResult registerResult = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "username", username,
                                "password", password))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn();

        String userId = objectMapper.readTree(registerResult.getResponse().getContentAsString())
                .path("data")
                .path("userId")
                .asText();
        String olderToken = "older-reset-token-" + System.nanoTime();
        passwordResetTokenMapper.insert(PasswordResetToken.builder()
                .userId(userId)
                .username(username)
                .tokenHash(sha256(olderToken))
                .expiresAt(LocalDateTime.now().plusMinutes(10))
                .requestedIp("127.0.0.1")
                .createdAt(LocalDateTime.now())
                .build());

        mockMvc.perform(post("/api/v1/auth/password-reset/request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("username", username))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        mockMvc.perform(post("/api/v1/auth/password-reset/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "token", olderToken,
                                "password", "StrongerPass456"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    void shouldInvalidateAllUnusedPasswordResetTokensAfterSuccessfulReset() throws Exception {
        String username = "reset-all-unused-" + System.currentTimeMillis() + "@example.com";
        String oldPassword = "StrongPass123";
        String newPassword = "StrongerPass456";

        MvcResult registerResult = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "username", username,
                                "password", oldPassword))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn();

        String userId = objectMapper.readTree(registerResult.getResponse().getContentAsString())
                .path("data")
                .path("userId")
                .asText();
        String tokenToUse = "reset-token-use-" + System.nanoTime();
        String siblingToken = "reset-token-sibling-" + System.nanoTime();
        LocalDateTime now = LocalDateTime.now();
        passwordResetTokenMapper.insert(PasswordResetToken.builder()
                .userId(userId)
                .username(username)
                .tokenHash(sha256(tokenToUse))
                .expiresAt(now.plusMinutes(10))
                .requestedIp("127.0.0.1")
                .createdAt(now)
                .build());
        passwordResetTokenMapper.insert(PasswordResetToken.builder()
                .userId(userId)
                .username(username)
                .tokenHash(sha256(siblingToken))
                .expiresAt(now.plusMinutes(10))
                .requestedIp("127.0.0.1")
                .createdAt(now.plusSeconds(1))
                .build());

        mockMvc.perform(post("/api/v1/auth/password-reset/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "token", tokenToUse,
                                "password", newPassword))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        mockMvc.perform(post("/api/v1/auth/password-reset/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "token", siblingToken,
                                "password", "AnotherPass789"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "username", username,
                                "password", newPassword))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
    }

    private String sha256(String value) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
    }
}
