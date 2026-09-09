package com.school.school_management_system.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.function.Function;

/**
 * Creates and validates JWT access tokens.
 *
 * <p>Configuration:
 * <pre>
 *   app.jwt.secret         at least 32 characters; supply via FRAS_JWT_SECRET
 *   app.jwt.expiration-ms  token lifetime, default 24h
 * </pre>
 *
 * <p>Three things changed here from the original implementation:
 * <ul>
 *   <li>The secret is decoded as UTF-8 rather than the platform default
 *       charset, so a token signed on one machine verifies on another.</li>
 *   <li>A blank secret no longer produces a weak key: a cryptographically
 *       random one is generated for the lifetime of the process, which is
 *       safe by default at the cost of invalidating tokens on restart.</li>
 *   <li>A secret that is present but too short for HS256 fails fast with an
 *       actionable message instead of throwing from deep inside jjwt.</li>
 * </ul>
 */
@Component
public class JwtUtil {

    private static final Logger log = LoggerFactory.getLogger(JwtUtil.class);

    /** HS256 needs a key of at least 256 bits. */
    private static final int MIN_SECRET_BYTES = 32;

    private final SecretKey signingKey;
    private final long expirationMs;

    public JwtUtil(
            @Value("${app.jwt.secret:}") String secret,
            @Value("${app.jwt.expiration-ms:86400000}") long expirationMs
    ) {
        this.signingKey = buildKey(secret);
        this.expirationMs = expirationMs > 0 ? expirationMs : 86_400_000L;
    }

    private static SecretKey buildKey(String secret) {
        if (secret == null || secret.isBlank()) {
            log.warn("app.jwt.secret is not set - generating a random signing key for this process. "
                    + "Every issued token becomes invalid when the backend restarts. "
                    + "Set FRAS_JWT_SECRET to a value of at least {} characters to fix this.",
                    MIN_SECRET_BYTES);
            return Jwts.SIG.HS256.key().build();
        }

        byte[] keyBytes = secret.getBytes(StandardCharsets.UTF_8);
        if (keyBytes.length < MIN_SECRET_BYTES) {
            throw new IllegalStateException(
                    "app.jwt.secret is only " + keyBytes.length + " bytes long; HS256 requires at least "
                            + MIN_SECRET_BYTES + ". Generate one with: openssl rand -base64 48");
        }
        return Keys.hmacShaKeyFor(keyBytes);
    }

    public long getExpirationMs() {
        return expirationMs;
    }

    public String generateToken(CustomUserDetails userDetails) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + expirationMs);

        return Jwts.builder()
                .subject(userDetails.getUsername())
                .claim("userId", userDetails.getId())
                .claim("role", userDetails.getUser().getRole().name())
                .issuedAt(now)
                .expiration(expiry)
                .signWith(signingKey)
                .compact();
    }

    public String extractEmail(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    public String extractRole(String token) {
        return extractClaim(token, claims -> claims.get("role", String.class));
    }

    public Date extractExpiration(String token) {
        return extractClaim(token, Claims::getExpiration);
    }

    /**
     * True when the token's signature verifies, it has not expired, and it
     * was issued for {@code expectedEmail}. Any parsing or signature
     * failure is a plain "not valid" - callers never see the exception.
     */
    public boolean isTokenValid(String token, String expectedEmail) {
        try {
            String email = extractEmail(token);
            return email != null
                    && email.equalsIgnoreCase(expectedEmail)
                    && !extractExpiration(token).before(new Date());
        } catch (ExpiredJwtException e) {
            return false;
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    private <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
        Claims claims = Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
        return claimsResolver.apply(claims);
    }
}
