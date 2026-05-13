package com.mars.linker.broker.ui.auth;

import com.mars.linker.broker.ui.config.MarsLinkerUiProperties;
import com.nimbusds.jose.JWSObject;
import com.nimbusds.jose.crypto.MACVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;

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
import java.text.ParseException;
import java.util.Date;
import java.util.List;
import java.util.Set;

@Component
@ConditionalOnProperty(prefix = "mars.linker.ui", name = "auth-enabled", havingValue = "true")
public class UiAuthFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(UiAuthFilter.class);
    private static final String ROLE_UI_VIEW = "ROLE_UI_VIEW";
    private static final String ROLE_UI_MANAGE = "ROLE_UI_MANAGE";
    private static final Set<String> READ_METHODS = Set.of("GET", "HEAD", "OPTIONS");

    private final MarsLinkerUiProperties uiProperties;
    private final byte[] secretKeyBytes;

    public UiAuthFilter(MarsLinkerUiProperties uiProperties) {
        this.uiProperties = uiProperties;
        this.secretKeyBytes = uiProperties.getJwtSecretKey().getBytes(java.nio.charset.StandardCharsets.UTF_8);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String path = request.getRequestURI();
        if (!path.startsWith("/api/ui/")) {
            filterChain.doFilter(request, response);
            return;
        }
        if (path.startsWith("/api/ui/auth/")) {
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
            sendError(response, HttpStatus.UNAUTHORIZED, "Invalid or expired token");
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
            SignedJWT signedJWT = SignedJWT.parse(token);
            MACVerifier verifier = new MACVerifier(secretKeyBytes);
            if (!signedJWT.verify(verifier)) {
                log.debug("JWT signature verification failed");
                return null;
            }
            JWTClaimsSet claims = signedJWT.getJWTClaimsSet();
            Date expirationTime = claims.getExpirationTime();
            if (expirationTime != null && expirationTime.before(new Date())) {
                log.debug("JWT token expired at {}", expirationTime);
                return null;
            }
            List<String> roles = claims.getStringListClaim("roles");
            if (roles == null || roles.isEmpty()) {
                roles = List.of(ROLE_UI_VIEW);
            }
            return new TokenInfo(Set.copyOf(roles));
        } catch (Exception e) {
            log.debug("Token parse/verify error: {}", e.getMessage());
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
    }
}
