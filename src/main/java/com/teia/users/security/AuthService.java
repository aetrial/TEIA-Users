package com.teia.users.security;

import com.auth0.jwt.interfaces.DecodedJWT;
import com.teia.users.dto.LoginRequest;
import com.teia.users.dto.LoginResponse;
import com.teia.users.dto.RefreshRequest;
import com.teia.users.dto.RefreshResponse;
import com.teia.users.models.RefreshToken;
import com.teia.users.models.TeiaUser;
import com.teia.users.repositories.RefreshTokenRepository;
import com.teia.users.repositories.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Authentication service handling login, token refresh, and logout operations.
 */
@Service
public class AuthService {

    private static final Logger logger = LoggerFactory.getLogger(AuthService.class);

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtService jwtService;
    private final PasswordEncoder passwordEncoder;

    public AuthService(
            UserRepository userRepository,
            RefreshTokenRepository refreshTokenRepository,
            JwtService jwtService,
            PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.jwtService = jwtService;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * Authenticate user with email and password, return access and refresh tokens.
     *
     * @throws BadCredentialsException if credentials are invalid
     */
    @Transactional
    public LoginResponse login(LoginRequest request) {
        TeiaUser user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> {
                    logger.warn("Login attempt for non-existent email: {}", request.getEmail());
                    return new BadCredentialsException("Invalid email or password");
                });

        if (user.getPassword() == null || !passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            logger.warn("Failed login attempt for user: {}", request.getEmail());
            throw new BadCredentialsException("Invalid email or password");
        }

        String accessToken = jwtService.generateAccessToken(user);
        String refreshToken = jwtService.generateRefreshToken(user);

        // Store refresh token in database for revocation support
        RefreshToken refreshTokenEntity = new RefreshToken(
                refreshToken,
                user.getId(),
                jwtService.getRefreshTokenExpiry()
        );
        refreshTokenRepository.save(refreshTokenEntity);

        logger.info("User logged in successfully: {}", user.getEmail());

        return new LoginResponse(
                accessToken,
                refreshToken,
                jwtService.getAccessTokenExpiryMs() / 1000  // Convert to seconds for client
        );
    }

    /**
     * Refresh an access token using a valid refresh token.
     *
     * @throws BadCredentialsException if refresh token is invalid, expired, or revoked
     */
    @Transactional
    public RefreshResponse refresh(RefreshRequest request) {
        String refreshTokenStr = request.getRefreshToken();

        // Validate the JWT signature and expiry
        DecodedJWT decodedJwt = jwtService.validateToken(refreshTokenStr);
        if (decodedJwt == null) {
            logger.warn("Invalid or expired refresh token presented");
            throw new BadCredentialsException("Invalid or expired refresh token");
        }

        // Check if token exists in DB and is not revoked
        RefreshToken storedToken = refreshTokenRepository.findByToken(refreshTokenStr)
                .orElseThrow(() -> {
                    logger.warn("Refresh token not found in database");
                    return new BadCredentialsException("Invalid refresh token");
                });

        if (!storedToken.isValid()) {
            logger.warn("Revoked or expired refresh token used for user ID: {}", storedToken.getUserId());
            throw new BadCredentialsException("Refresh token has been revoked or expired");
        }

        // Fetch the user and generate new access token
        TeiaUser user = userRepository.findById(storedToken.getUserId())
                .orElseThrow(() -> {
                    logger.error("User not found for refresh token, user ID: {}", storedToken.getUserId());
                    return new BadCredentialsException("User not found");
                });

        String newAccessToken = jwtService.generateAccessToken(user);

        logger.info("Access token refreshed for user: {}", user.getEmail());

        return new RefreshResponse(
                newAccessToken,
                jwtService.getAccessTokenExpiryMs() / 1000
        );
    }

    /**
     * Logout by revoking the provided refresh token.
     */
    @Transactional
    public void logout(RefreshRequest request) {
        String refreshTokenStr = request.getRefreshToken();

        refreshTokenRepository.findByToken(refreshTokenStr)
                .ifPresent(token -> {
                    token.setRevoked(true);
                    refreshTokenRepository.save(token);
                    logger.info("Refresh token revoked for user ID: {}", token.getUserId());
                });
    }

    /**
     * Revoke all refresh tokens for a user (e.g., for password change or security breach).
     */
    @Transactional
    public void revokeAllUserTokens(Long userId) {
        refreshTokenRepository.revokeAllByUserId(userId);
        logger.info("All refresh tokens revoked for user ID: {}", userId);
    }
}
