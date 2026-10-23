package com.gps.tracking.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

@Component
@Slf4j
public class JwtTokenProvider {

    private static final String HMAC_ALGO = "HmacSHA256";
    private static final String HEADER_JSON = "{\"alg\":\"HS256\",\"typ\":\"JWT\"}";
    private static final String ENCODED_HEADER = Base64.getUrlEncoder().withoutPadding()
            .encodeToString(HEADER_JSON.getBytes(StandardCharsets.UTF_8));

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${app.jwt.secret:TrackFleetSecureJwtSecretKeyForSimulationAlertsAndRoleDashboards2026!}")
    private String secretKey;

    @Value("${app.jwt.expiration-ms:604800000}") // 7 days default
    private long expirationMs;

    public String generateToken(UserPrincipal principal) {
        try {
            long now = System.currentTimeMillis();
            long exp = now + expirationMs;

            Map<String, Object> claims = new HashMap<>();
            claims.put("sub", principal.getUsername());
            claims.put("role", principal.getRole());
            claims.put("iat", now / 1000);
            claims.put("exp", exp / 1000);

            if (principal.getDriverId() != null) claims.put("driverId", principal.getDriverId());
            if (principal.getDriverCode() != null) claims.put("driverCode", principal.getDriverCode());
            if (principal.getDriverName() != null) claims.put("driverName", principal.getDriverName());
            if (principal.getVehicleId() != null) claims.put("vehicleId", principal.getVehicleId());
            if (principal.getVehicleCode() != null) claims.put("vehicleCode", principal.getVehicleCode());

            String payloadJson = objectMapper.writeValueAsString(claims);
            String encodedPayload = Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(payloadJson.getBytes(StandardCharsets.UTF_8));

            String dataToSign = ENCODED_HEADER + "." + encodedPayload;
            String signature = sign(dataToSign);

            return dataToSign + "." + signature;
        } catch (Exception e) {
            log.error("Error generating JWT token", e);
            throw new RuntimeException("Failed to generate JWT token", e);
        }
    }

    public boolean validateToken(String token) {
        try {
            if (token == null || !token.contains(".")) return false;
            String[] parts = token.split("\\.");
            if (parts.length != 3) return false;

            String dataToSign = parts[0] + "." + parts[1];
            String expectedSig = sign(dataToSign);

            if (!MessageDigest.isEqual(parts[2].getBytes(StandardCharsets.UTF_8), expectedSig.getBytes(StandardCharsets.UTF_8))) {
                log.warn("Invalid JWT signature");
                return false;
            }

            // Check expiration
            byte[] payloadBytes = Base64.getUrlDecoder().decode(parts[1]);
            Map<?, ?> claims = objectMapper.readValue(payloadBytes, Map.class);
            if (claims.containsKey("exp")) {
                long exp = ((Number) claims.get("exp")).longValue();
                if (System.currentTimeMillis() / 1000 > exp) {
                    log.warn("JWT token has expired");
                    return false;
                }
            }
            return true;
        } catch (Exception e) {
            log.warn("JWT validation error: {}", e.getMessage());
            return false;
        }
    }

    public UserPrincipal getPrincipalFromToken(String token) {
        try {
            String[] parts = token.split("\\.");
            byte[] payloadBytes = Base64.getUrlDecoder().decode(parts[1]);
            Map<?, ?> claims = objectMapper.readValue(payloadBytes, Map.class);

            String username = (String) claims.get("sub");
            String role = (String) claims.get("role");

            Long driverId = claims.get("driverId") != null ? ((Number) claims.get("driverId")).longValue() : null;
            String driverCode = (String) claims.get("driverCode");
            String driverName = (String) claims.get("driverName");
            Long vehicleId = claims.get("vehicleId") != null ? ((Number) claims.get("vehicleId")).longValue() : null;
            String vehicleCode = (String) claims.get("vehicleCode");

            return UserPrincipal.builder()
                    .username(username)
                    .role(role)
                    .driverId(driverId)
                    .driverCode(driverCode)
                    .driverName(driverName)
                    .vehicleId(vehicleId)
                    .vehicleCode(vehicleCode)
                    .build();
        } catch (Exception e) {
            log.error("Failed to extract principal from token", e);
            return null;
        }
    }

    private String sign(String data) throws Exception {
        Mac mac = Mac.getInstance(HMAC_ALGO);
        SecretKeySpec secretKeySpec = new SecretKeySpec(secretKey.getBytes(StandardCharsets.UTF_8), HMAC_ALGO);
        mac.init(secretKeySpec);
        byte[] hmacBytes = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
        return Base64.getUrlEncoder().withoutPadding().encodeToString(hmacBytes);
    }
}
