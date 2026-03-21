package com.teia.users.security;

import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTVerifier;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTVerificationException;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.teia.users.models.TeiaUser;
import com.teia.users.models.enums.Role;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Date;
import java.util.UUID;

/**
 * Service for JWT token generation, validation, and claim extraction.
 * Uses Auth0 java-jwt library for HS256 signed tokens.
 */
@Service
public class JwtService {

    private static final Logger logger = LoggerFactory.getLogger(JwtService.class);

    private static final String ISSUER = "teia-users";
    private static final String CLAIM_USER_ID = "userId";
    private static final String CLAIM_EMAIL = "email";
    private static final String CLAIM_ROLE = "role";
    private static final int MIN_SECRET_LENGTH = 32;

    private final Algorithm algorithm;
    private final JWTVerifier verifier;
    private final long accessTokenExpiryMs;
    private final long refreshTokenExpiryMs;

    public JwtService(
            @Value("${jwt.secret}") String secret,
            @Value("${jwt.access-expiry-ms:900000}") long accessTokenExpiryMs,
            @Value("${jwt.refresh-expiry-ms:604800000}") long refreshTokenExpiryMs) {
        validateSecret(secret);
        this.algorithm = Algorithm.HMAC256(secret);
        this.verifier = JWT.require(algorithm)
                .withIssuer(ISSUER)
                .build();
        this.accessTokenExpiryMs = accessTokenExpiryMs;
        this.refreshTokenExpiryMs = refreshTokenExpiryMs;
    }

    /**
     * Validates that the JWT secret is present and meets minimum length requirements.
     * Fails fast at startup if requirements are not met.
     */
    private void validateSecret(String secret) {
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException(
                "JWT_SECRET environment variable is not set. " +
                "Set JWT_SECRET to a secure random string of at least " + MIN_SECRET_LENGTH + " characters (256 bits for HS256)."
            );
        }
        if (secret.length() < MIN_SECRET_LENGTH) {
            throw new IllegalStateException(
                "JWT_SECRET is too short (" + secret.length() + " characters). " +
                "Set JWT_SECRET to at least " + MIN_SECRET_LENGTH + " characters (256 bits for HS256) for secure signing."
            );
        }
        logger.info("JWT secret validated: meets minimum length requirement of {} characters", MIN_SECRET_LENGTH);
    }

    /**
     * Generate an access token for the given user.
     * Contains userId, email, and role claims.
     */
    public String generateAccessToken(TeiaUser user) {
        Instant now = Instant.now();
        Instant expiry = now.plusMillis(accessTokenExpiryMs);

        return JWT.create()
                .withIssuer(ISSUER)
                .withSubject(user.getId().toString())
                .withClaim(CLAIM_USER_ID, user.getId())
                .withClaim(CLAIM_EMAIL, user.getEmail())
                .withClaim(CLAIM_ROLE, user.getRole().name())
                .withIssuedAt(Date.from(now))
                .withExpiresAt(Date.from(expiry))
                .withJWTId(UUID.randomUUID().toString())
                .sign(algorithm);
    }

    /**
     * Generate a refresh token string.
     * The refresh token itself is a signed JWT that contains the user ID.
     */
    public String generateRefreshToken(TeiaUser user) {
        Instant now = Instant.now();
        Instant expiry = now.plusMillis(refreshTokenExpiryMs);

        return JWT.create()
                .withIssuer(ISSUER)
                .withSubject(user.getId().toString())
                .withClaim(CLAIM_USER_ID, user.getId())
                .withIssuedAt(Date.from(now))
                .withExpiresAt(Date.from(expiry))
                .withJWTId(UUID.randomUUID().toString())
                .sign(algorithm);
    }

    /**
     * Validate a token and return the decoded JWT if valid.
     * Returns null if the token is invalid or expired.
     */
    public DecodedJWT validateToken(String token) {
        try {
            return verifier.verify(token);
        } catch (JWTVerificationException e) {
            logger.debug("Token validation failed: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Check if a token is valid (not expired and properly signed).
     */
    public boolean isTokenValid(String token) {
        return validateToken(token) != null;
    }

    /**
     * Extract the user ID from a validated token.
     */
    public Long extractUserId(DecodedJWT jwt) {
        return jwt.getClaim(CLAIM_USER_ID).asLong();
    }

    /**
     * Extract the user ID from a token string.
     * Returns null if the token is invalid.
     */
    public Long extractUserId(String token) {
        DecodedJWT jwt = validateToken(token);
        return jwt != null ? extractUserId(jwt) : null;
    }

    /**
     * Extract the email from a validated token.
     */
    public String extractEmail(DecodedJWT jwt) {
        return jwt.getClaim(CLAIM_EMAIL).asString();
    }

    /**
     * Extract the email from a token string.
     * Returns null if the token is invalid.
     */
    public String extractEmail(String token) {
        DecodedJWT jwt = validateToken(token);
        return jwt != null ? extractEmail(jwt) : null;
    }

    /**
     * Extract the role from a validated token.
     */
    public Role extractRole(DecodedJWT jwt) {
        String roleName = jwt.getClaim(CLAIM_ROLE).asString();
        return roleName != null ? Role.valueOf(roleName) : null;
    }

    /**
     * Extract the role from a token string.
     * Returns null if the token is invalid.
     */
    public Role extractRole(String token) {
        DecodedJWT jwt = validateToken(token);
        return jwt != null ? extractRole(jwt) : null;
    }

    /**
     * Get the access token expiry duration in milliseconds.
     */
    public long getAccessTokenExpiryMs() {
        return accessTokenExpiryMs;
    }

    /**
     * Get the refresh token expiry duration in milliseconds.
     */
    public long getRefreshTokenExpiryMs() {
        return refreshTokenExpiryMs;
    }

    /**
     * Get the expiry instant for a new refresh token.
     */
    public Instant getRefreshTokenExpiry() {
        return Instant.now().plusMillis(refreshTokenExpiryMs);
    }
}
