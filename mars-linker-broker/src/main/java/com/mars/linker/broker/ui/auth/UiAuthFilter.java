package com.mars.linker.broker.ui.auth;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Base64;
import java.util.Set;

@Component
@ConditionalOnProperty(prefix = "mars.linker.ui", name = "auth-enabled", havingValue = "true")
public class UiAuthFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(UiAuthFilter.class);
    private static final String ROLE_UI_VIEW = "ROLE_UI_VIEW";
    private static final String ROLE_UI_MANAGE = "ROLE_UI_MANAGE";
    private static final Set<String> READ_METHODS = Set.of("GET", "HEAD", "OPTIONS");

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String path = request.getRequestURI();
        if (!path.startsWith("/api/ui/")) {
            filterChain.doFilter(request, response);
            return;
        }

        String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            sendError(response, HttpStatus.UNAUTHORIZED, "Missing or invalid Authorization header");
            return;
        }

        String token = authHeader.substring(7).trim();
        if (token.isEmpty()) {
            sendError(response, HttpStatus.UNAUTHORIZED, "Empty token");
            return;
        }

        TokenInfo tokenInfo = parseToken(token);
        if (tokenInfo == null) {
            sendError(response, HttpStatus.UNAUTHORIZED, "Invalid token");
            return;
        }

        String method = request.getMethod();
        if (!READ_METHODS.contains(method)) {
            if (!tokenInfo.roles.contains(ROLE_UI_MANAGE)) {
                sendError(response, HttpStatus.FORBIDDEN, "Insufficient permissions: " + ROLE_UI_MANAGE + " required");
                return;
            }
        } else {
            if (!tokenInfo.roles.contains(ROLE_UI_VIEW) && !tokenInfo.roles.contains(ROLE_UI_MANAGE)) {
                sendError(response, HttpStatus.FORBIDDEN, "Insufficient permissions: " + ROLE_UI_VIEW + " required");
                return;
            }
        }

        filterChain.doFilter(request, response);
    }

    private TokenInfo parseToken(String token) {
        try {
            String[] parts = token.split("\\.");
            if (parts.length < 2) return null;
            String payload = new String(Base64.getUrlDecoder().decode(parts[1]));
            return TokenInfo.fromPayload(payload);
        } catch (Exception e) {
            log.debug("Token parse error: {}", e.getMessage());
            return null;
        }
    }

    private void sendError(HttpServletResponse response, HttpStatus status, String message) throws IOException {
        response.setStatus(status.value());
        response.setContentType("application/json");
        response.getWriter().write("{\"error\":\"" + message + "\"}");
    }

    private static class TokenInfo {
        final Set<String> roles;

        TokenInfo(Set<String> roles) {
            this.roles = roles;
        }

        static TokenInfo fromPayload(String payload) {
            Set<String> roles = new java.util.HashSet<>();
            if (payload.contains("ROLE_UI_VIEW")) roles.add(ROLE_UI_VIEW);
            if (payload.contains("ROLE_UI_MANAGE")) roles.add(ROLE_UI_MANAGE);
            if (roles.isEmpty()) roles.add(ROLE_UI_VIEW);
            return new TokenInfo(roles);
        }
    }
}
