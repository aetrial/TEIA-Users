package com.teia.users.services;

import com.teia.users.repositories.RefreshTokenRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Scheduled service that periodically cleans up expired refresh tokens from the database.
 * Runs according to the cron expression defined in refresh-token.cleanup.cron property.
 */
@Service
public class RefreshTokenCleanupService {

    private static final Logger logger = LoggerFactory.getLogger(RefreshTokenCleanupService.class);

    private final RefreshTokenRepository refreshTokenRepository;

    public RefreshTokenCleanupService(RefreshTokenRepository refreshTokenRepository) {
        this.refreshTokenRepository = refreshTokenRepository;
    }

    /**
     * Deletes expired refresh tokens from the database.
     * Runs on schedule defined by refresh-token.cleanup.cron property (default: daily at 2 AM).
     */
    @Scheduled(cron = "${refresh-token.cleanup.cron}")
    @Transactional
    public void cleanupExpiredTokens() {
        logger.info("Starting scheduled cleanup of expired refresh tokens");
        try {
            refreshTokenRepository.deleteExpiredTokens();
            logger.info("Expired refresh tokens cleaned up successfully");
        } catch (Exception e) {
            logger.error("Failed to clean up expired refresh tokens", e);
        }
    }
}
