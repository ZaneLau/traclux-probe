package com.geoprobe.filter;

import com.geoprobe.common.JwtUtil;
import com.geoprobe.entity.User;
import com.geoprobe.mapper.UserMapper;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthFilter extends OncePerRequestFilter {

    private static final String TOKEN_PREFIX = "Bearer ";
    public static final String AUTH_COOKIE_NAME = "geoprobe_auth_token";
    private static final int ROLE_ADMIN = 1;

    private final JwtUtil jwtUtil;
    private final UserMapper userMapper;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String token = resolveToken(request);
        if (StringUtils.hasText(token) && SecurityContextHolder.getContext().getAuthentication() == null) {
            try {
                if (jwtUtil.isTokenValid(token)) {
                    String userId = jwtUtil.getUserIdFromToken(token);
                    User user = findActiveUser(userId);
                    if (user != null) {
                        UsernamePasswordAuthenticationToken authentication =
                                new UsernamePasswordAuthenticationToken(userId, null, resolveAuthorities(user));
                        authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                        SecurityContextHolder.getContext().setAuthentication(authentication);
                    } else {
                        log.info("Skip JWT authentication because user is missing or disabled, userId={}", userId);
                    }
                }
            } catch (Exception ex) {
                log.warn("Failed to process JWT token", ex);
            }
        }

        filterChain.doFilter(request, response);
    }

    private String resolveToken(HttpServletRequest request) {
        String authHeader = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (StringUtils.hasText(authHeader) && authHeader.startsWith(TOKEN_PREFIX)) {
            return authHeader.substring(TOKEN_PREFIX.length());
        }
        Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            for (Cookie cookie : cookies) {
                if (AUTH_COOKIE_NAME.equals(cookie.getName()) && StringUtils.hasText(cookie.getValue())) {
                    return cookie.getValue().trim();
                }
            }
        }
        return null;
    }

    private User findActiveUser(String userId) {
        if (!StringUtils.hasText(userId)) {
            return null;
        }
        User user = userMapper.selectById(userId);
        if (user == null || Integer.valueOf(0).equals(user.getEnabled())) {
            return null;
        }
        return user;
    }

    private List<SimpleGrantedAuthority> resolveAuthorities(User user) {
        if (user != null && Integer.valueOf(ROLE_ADMIN).equals(user.getRole())) {
            return List.of(new SimpleGrantedAuthority("ROLE_ADMIN"));
        }
        return Collections.emptyList();
    }
}
