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
import java.util.Optional;

@Service
public class JwtService {

    private final ClientCredentialsProperties properties;
    private final SecretKey key;

    public JwtService(ClientCredentialsProperties properties) {
        this.properties = properties;
        byte[] secretBytes = properties.getJwt().getSecret().getBytes(StandardCharsets.UTF_8);
        this.key = Keys.hmacShaKeyFor(secretBytes);
    }

    public String generateToken(ClientPrincipal principal) {
        Instant now = Instant.now();
        Instant expiresAt = now.plus(properties.getJwt().getExpirationSeconds(), ChronoUnit.SECONDS);

        return Jwts.builder()
                .subject(principal.clientId())
                .claim("tenantCode", principal.tenantCode())
                .claim("roles", principal.roles())
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiresAt))
                .signWith(key)
                .compact();
    }

    @SuppressWarnings("unchecked")
    public Optional<ClientPrincipal> parseAndValidate(String token) {
        try {
            Claims payload = Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();

            String clientId = payload.getSubject();
            String tenantCode = payload.get("tenantCode", String.class);
            List<String> roles = payload.get("roles", List.class);

            if (clientId != null && tenantCode != null) {
                return Optional.of(new ClientPrincipal(clientId, tenantCode, roles != null ? roles : List.of()));
            }
            return Optional.empty();
        } catch (JwtException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    public long getExpirationSeconds() {
        return properties.getJwt().getExpirationSeconds();
    }
}
