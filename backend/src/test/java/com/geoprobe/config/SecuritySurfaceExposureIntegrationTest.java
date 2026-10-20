package com.geoprobe.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "app.security.expose-swagger=false",
        "app.security.expose-dev-endpoints=false"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SecuritySurfaceExposureIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void shouldDenySwaggerDocsEvenForAuthenticatedUserWhenSwaggerExposureDisabled() throws Exception {
        String token = registerAndLogin();

        mockMvc.perform(get("/v3/api-docs")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));
    }

    @Test
    void shouldDenyDevEndpointsEvenForAuthenticatedUserWhenDevExposureDisabled() throws Exception {
        String token = registerAndLogin();

        mockMvc.perform(post("/api/v1/dev/seed/dashboard")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));
    }

    @Test
    void shouldDenyLegacyTrackingEvenForAuthenticatedUserWhenLegacyTrackingExposureDisabled() throws Exception {
        String token = registerAndLogin();

        mockMvc.perform(get("/api/v1/tracking")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));
    }

    @Test
    void shouldReturnNotFoundForRemovedLegacyDiagnosisRoutes() throws Exception {
        String token = registerAndLogin();

        mockMvc.perform(get("/api/v1/diagnoses")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/v1/reports/550e8400-e29b-41d4-a716-446655440000")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isNotFound());
    }

    @Test
    void shouldReturnNotFoundForRemovedPublicReportShareRoutes() throws Exception {
        String token = registerAndLogin();

        mockMvc.perform(get("/api/v1/public/reports/share-public-preview")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isNotFound());
    }

    private String registerAndLogin() throws Exception {
        String username = "security-surface-" + System.nanoTime() + "@example.com";
        String password = "StrongPass123";
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "username", username,
                                "password", password
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        MvcResult loginResult = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "username", username,
                                "password", password
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn();

        JsonNode data = objectMapper.readTree(loginResult.getResponse().getContentAsString(StandardCharsets.UTF_8)).path("data");
        return data.path("token").asText();
    }
}
