package com.codemind.platform.auth;

import com.codemind.platform.identity.CurrentUser;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

@Service
public class JwtTokenService {

    private final String jwtSecret;
    private final long accessTokenMinutes;

    public JwtTokenService(
            @Value("${codemind.security.jwt-secret}") String jwtSecret,
            @Value("${codemind.security.access-token-minutes}") long accessTokenMinutes
    ) {
        this.jwtSecret = jwtSecret;
        this.accessTokenMinutes = accessTokenMinutes;
    }

    public String createAccessToken(CurrentUser currentUser) {
        Instant now = Instant.now();
        Instant expiresAt = now.plusSeconds(accessTokenMinutes * 60);

        return Jwts.builder()
                .subject(currentUser.username())
                .claim("user_id", currentUser.userId().toString())
                .claim("display_name", currentUser.displayName())
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiresAt))
                .signWith(getSigningKey())
                .compact();
    }

    public CurrentUser parseAccessToken(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();

        String username = claims.getSubject();
        String userId = claims.get("user_id", String.class);
        String displayName = claims.get("display_name", String.class);

        return new CurrentUser(
                UUID.fromString(userId),
                username,
                displayName
        );
    }

    public long getAccessTokenSeconds() {
        return accessTokenMinutes * 60;
    }

    private SecretKey getSigningKey() {
        byte[] keyBytes = jwtSecret.getBytes(StandardCharsets.UTF_8);

        if (keyBytes.length < 32) {
            throw new IllegalStateException("JWT secret must be at least 32 bytes.");
        }

        return Keys.hmacShaKeyFor(keyBytes);
    }
}