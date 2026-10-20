package com.geoprobe.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
public class AuthCookieOriginGuardFilter extends OncePerRequestFilter {

    private static final Set<String> SAFE_METHODS = Set.of("GET", "HEAD", "OPTIONS", "TRACE");

    @Value("${app.cors-origins:http://localhost:5173,http://127.0.0.1:5173,http://localhost:3000,http://127.0.0.1:3000,http://localhost:3001,http://127.0.0.1:3001,http://localhost:3002,http://127.0.0.1:3002}")
    private String corsOrigins;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        if (shouldBlock(request)) {
            response.sendError(HttpStatus.FORBIDDEN.value(), "Cross-origin authenticated write is not allowed");
            return;
        }
        filterChain.doFilter(request, response);
    }

    private boolean shouldBlock(HttpServletRequest request) {
        if (SAFE_METHODS.contains(request.getMethod().toUpperCase(Locale.ROOT)) || !hasAuthCookie(request)) {
            return false;
        }

        String origin = request.getHeader(HttpHeaders.ORIGIN);
        if (!StringUtils.hasText(origin)) {
            String referer = request.getHeader(HttpHeaders.REFERER);
            origin = extractOrigin(referer);
        }

        // Non-browser clients and same-origin form posts may omit Origin/Referer.
        if (!StringUtils.hasText(origin)) {
            return false;
        }

        return !sameOrigin(request, origin) && !allowedOrigins().contains(origin);
    }

    private boolean hasAuthCookie(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return false;
        }
        for (Cookie cookie : cookies) {
            if (JwtAuthFilter.AUTH_COOKIE_NAME.equals(cookie.getName()) && StringUtils.hasText(cookie.getValue())) {
                return true;
            }
        }
        return false;
    }

    private Set<String> allowedOrigins() {
        return Arrays.stream(corsOrigins.split(","))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .collect(Collectors.toUnmodifiableSet());
    }

    private boolean sameOrigin(HttpServletRequest request, String origin) {
        String scheme = forwardedValue(request, "X-Forwarded-Proto", request.getScheme());
        String host = forwardedValue(request, "X-Forwarded-Host", request.getServerName());
        String port = forwardedValue(request, "X-Forwarded-Port", String.valueOf(request.getServerPort()));
        String requestOrigin = scheme + "://" + host;
        if (StringUtils.hasText(port) && !isDefaultPort(scheme, port) && !host.contains(":")) {
            requestOrigin += ":" + port;
        }
        return requestOrigin.equalsIgnoreCase(origin);
    }

    private String forwardedValue(HttpServletRequest request, String header, String fallback) {
        String value = request.getHeader(header);
        if (!StringUtils.hasText(value)) {
            return fallback;
        }
        return value.split(",")[0].trim();
    }

    private boolean isDefaultPort(String scheme, String port) {
        return ("http".equalsIgnoreCase(scheme) && "80".equals(port))
                || ("https".equalsIgnoreCase(scheme) && "443".equals(port));
    }

    private String extractOrigin(String rawUrl) {
        if (!StringUtils.hasText(rawUrl)) {
            return null;
        }
        try {
            URI uri = new URI(rawUrl);
            if (!StringUtils.hasText(uri.getScheme()) || !StringUtils.hasText(uri.getHost())) {
                return null;
            }
            String origin = uri.getScheme() + "://" + uri.getHost();
            if (uri.getPort() > -1 && !isDefaultPort(uri.getScheme(), String.valueOf(uri.getPort()))) {
                origin += ":" + uri.getPort();
            }
            return origin;
        } catch (URISyntaxException ex) {
            return null;
        }
    }
}
