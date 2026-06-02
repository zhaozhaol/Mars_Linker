package com.mars.linker.broker.ui.api;

import com.mars.linker.broker.ui.config.MarsLinkerUiProperties;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.KeyLengthException;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/ui/auth")
@ConditionalOnProperty(prefix = "mars.linker.ui", name = "auth-enabled", havingValue = "true")
public class AuthController {

    private static final Logger log = LoggerFactory.getLogger(AuthController.class);

    private final MarsLinkerUiProperties uiProperties;
    private final MACSigner signer;

    public AuthController(MarsLinkerUiProperties uiProperties) throws KeyLengthException {
        this.uiProperties = uiProperties;
        this.signer = new MACSigner(uiProperties.getJwtSecretKey().getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody Map<String, String> credentials) {
        String username = credentials.get("username");
        String password = credentials.get("password");
        if (username == null || password == null) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", "username and password required"));
        }
        if (!username.equals(uiProperties.getAdminUsername()) || !password.equals(uiProperties.getAdminPassword())) {
            log.warn("UI login failed for username={}", username);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Invalid username or password"));
        }
        try {
            String accessToken = signToken(username, List.of("ROLE_UI_VIEW", "ROLE_UI_MANAGE"),
                    uiProperties.getJwtExpireSeconds());
            String refreshToken = signToken(username, List.of("ROLE_UI_VIEW", "ROLE_UI_MANAGE"),
                    uiProperties.getJwtRefreshExpireSeconds());
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("accessToken", accessToken);
            result.put("refreshToken", refreshToken);
            result.put("expiresIn", uiProperties.getJwtExpireSeconds());
            result.put("tokenType", "Bearer");
            log.info("UI login success for username={}", username);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("Failed to sign JWT", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Token generation failed"));
        }
    }

    @PostMapping("/refresh")
    public ResponseEntity<?> refresh(@RequestBody Map<String, String> body) {
        String refreshToken = body.get("refreshToken");
        if (refreshToken == null || refreshToken.isEmpty()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", "refreshToken required"));
        }
        try {
            SignedJWT signedJWT = SignedJWT.parse(refreshToken);
            com.nimbusds.jose.crypto.MACVerifier verifier =
                    new com.nimbusds.jose.crypto.MACVerifier(
                            uiProperties.getJwtSecretKey().getBytes(java.nio.charset.StandardCharsets.UTF_8));
            if (!signedJWT.verify(verifier)) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(Map.of("error", "Invalid refresh token"));
            }
            JWTClaimsSet claims = signedJWT.getJWTClaimsSet();
            if (claims.getExpirationTime() != null && claims.getExpirationTime().before(new Date())) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(Map.of("error", "Refresh token expired"));
            }
            String username = claims.getSubject();
            List<String> roles = claims.getStringListClaim("roles");
            if (roles == null || roles.isEmpty() || !roles.contains("ROLE_UI_VIEW")) {
                roles = List.of("ROLE_UI_VIEW", "ROLE_UI_MANAGE");
            }
            String newAccessToken = signToken(username, roles, uiProperties.getJwtExpireSeconds());
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("accessToken", newAccessToken);
            result.put("expiresIn", uiProperties.getJwtExpireSeconds());
            result.put("tokenType", "Bearer");
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.debug("Refresh token error: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Invalid or expired refresh token"));
        }
    }

    private String signToken(String subject, List<String> roles, int expireSeconds) throws Exception {
        long now = System.currentTimeMillis();
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .subject(subject)
                .claim("roles", roles)
                .issueTime(new Date(now))
                .expirationTime(new Date(now + expireSeconds * 1000L))
                .build();
        SignedJWT signedJWT = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims);
        signedJWT.sign(signer);
        return signedJWT.serialize();
    }
}
