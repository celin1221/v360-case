package com.v360.gateway.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.List;

@Service
public class JwtService {

    private final ClientCredentialsProperties properties;
    private final SecretKey key;

    public JwtService(ClientCredentialsProperties properties) {
        this.properties = properties;
        byte[] secretBytes = properties.getJwt().getSecret().getBytes(StandardCharsets.UTF_8);
        this.key = Keys.hmacShaKeyFor(secretBytes);
    }

    public String generateToken(String clientId, String tenantCode, List<String> roles) {
        Instant now = Instant.now();
        Instant expiresAt = now.plus(properties.getJwt().getExpirationSeconds(), ChronoUnit.SECONDS);

        return Jwts.builder()
                .subject(clientId)
                .claim("tenantCode", tenantCode)
                .claim("roles", roles)
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiresAt))
                .signWith(key)
                .compact();
    }

    public boolean validateToken(String token) {
        try {
            Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    public Claims extractClaims(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public String extractClientId(String token) {
        return extractClaims(token).getSubject();
    }

    public String extractTenantCode(String token) {
        return extractClaims(token).get("tenantCode", String.class);
    }

    @SuppressWarnings("unchecked")
    public List<String> extractRoles(String token) {
        return extractClaims(token).get("roles", List.class);
    }

    public long getExpirationSeconds() {
        return properties.getJwt().getExpirationSeconds();
    }
}
