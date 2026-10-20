package com.geoprobe.service;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.geoprobe.common.BusinessException;
import com.geoprobe.common.ErrorCode;
import com.geoprobe.config.properties.AuthProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

@Service
@RequiredArgsConstructor
public class TurnstileVerifier {

    private final AuthProperties authProperties;
    private final ObjectMapper objectMapper;

    public void verify(String token, String clientIp) {
        AuthProperties.Turnstile turnstile = authProperties.getTurnstile();
        if (!turnstile.isEnabled()) {
            return;
        }
        if (!StringUtils.hasText(turnstile.getSecret()) || !StringUtils.hasText(token)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "Human verification is required");
        }

        String body = "secret=" + encode(turnstile.getSecret())
                + "&response=" + encode(token)
                + (StringUtils.hasText(clientIp) ? "&remoteip=" + encode(clientIp) : "");
        HttpRequest request = HttpRequest.newBuilder(URI.create(turnstile.getSiteverifyUrl()))
                .timeout(Duration.ofSeconds(Math.max(1, turnstile.getTimeoutSeconds())))
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_FORM_URLENCODED_VALUE)
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        try {
            HttpResponse<String> response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
            TurnstileResponse payload = objectMapper.readValue(response.body(), TurnstileResponse.class);
            if (!payload.success()) {
                throw new BusinessException(ErrorCode.FORBIDDEN, "Human verification failed");
            }
        } catch (IOException ex) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "Human verification is temporarily unavailable");
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "Human verification is temporarily unavailable");
        }
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private record TurnstileResponse(@JsonProperty("success") boolean success) {
    }
}
