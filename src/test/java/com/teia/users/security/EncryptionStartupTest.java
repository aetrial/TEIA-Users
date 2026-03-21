package com.teia.users.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests that verify EncryptionService initialization behavior.
 *
 * These tests verify that:
 * - Missing ENCRYPTION_KEY causes initialization failure
 * - Wrong key length causes initialization failure
 * - Valid key allows successful initialization
 */
class EncryptionStartupTest {

    @Test
    @DisplayName("EncryptionService fails to initialize when ENCRYPTION_KEY is missing")
    void serviceFailsWithoutEncryptionKey() {
        EncryptionService service = new EncryptionService();
        ReflectionTestUtils.setField(service, "encryptionKeyBase64", "");

        IllegalStateException exception = assertThrows(
            IllegalStateException.class,
            service::init,
            "Should throw IllegalStateException for missing key"
        );

        assertTrue(
            exception.getMessage().contains("ENCRYPTION_KEY"),
            "Exception message should mention ENCRYPTION_KEY"
        );
    }

    @Test
    @DisplayName("EncryptionService fails to initialize when ENCRYPTION_KEY is null")
    void serviceFailsWithNullEncryptionKey() {
        EncryptionService service = new EncryptionService();
        ReflectionTestUtils.setField(service, "encryptionKeyBase64", null);

        IllegalStateException exception = assertThrows(
            IllegalStateException.class,
            service::init,
            "Should throw IllegalStateException for null key"
        );

        assertTrue(
            exception.getMessage().contains("ENCRYPTION_KEY"),
            "Exception message should mention ENCRYPTION_KEY"
        );
    }

    @Test
    @DisplayName("EncryptionService fails to initialize when ENCRYPTION_KEY has wrong length (16 bytes)")
    void serviceFailsWithShortKey() {
        EncryptionService service = new EncryptionService();
        // 16-byte key (too short for AES-256)
        String shortKey = Base64.getEncoder().encodeToString(new byte[16]);
        ReflectionTestUtils.setField(service, "encryptionKeyBase64", shortKey);

        IllegalStateException exception = assertThrows(
            IllegalStateException.class,
            service::init,
            "Should throw IllegalStateException for short key"
        );

        assertTrue(
            exception.getMessage().contains("32 bytes") ||
            exception.getMessage().contains("256 bits"),
            "Exception message should mention required key length. Got: " + exception.getMessage()
        );
    }

    @Test
    @DisplayName("EncryptionService fails to initialize when ENCRYPTION_KEY has wrong length (64 bytes)")
    void serviceFailsWithLongKey() {
        EncryptionService service = new EncryptionService();
        // 64-byte key (too long for AES-256)
        String longKey = Base64.getEncoder().encodeToString(new byte[64]);
        ReflectionTestUtils.setField(service, "encryptionKeyBase64", longKey);

        IllegalStateException exception = assertThrows(
            IllegalStateException.class,
            service::init,
            "Should throw IllegalStateException for long key"
        );

        assertTrue(
            exception.getMessage().contains("32 bytes") ||
            exception.getMessage().contains("256 bits"),
            "Exception message should mention required key length. Got: " + exception.getMessage()
        );
    }

    @Test
    @DisplayName("EncryptionService fails to initialize when ENCRYPTION_KEY is invalid base64")
    void serviceFailsWithInvalidBase64() {
        EncryptionService service = new EncryptionService();
        ReflectionTestUtils.setField(service, "encryptionKeyBase64", "not-valid-base64!!!");

        IllegalStateException exception = assertThrows(
            IllegalStateException.class,
            service::init,
            "Should throw IllegalStateException for invalid base64"
        );

        assertTrue(
            exception.getMessage().toLowerCase().contains("base64"),
            "Exception message should mention base64. Got: " + exception.getMessage()
        );
    }

    @Test
    @DisplayName("EncryptionService initializes successfully with valid ENCRYPTION_KEY")
    void serviceInitializesWithValidKey() {
        EncryptionService service = new EncryptionService();
        // Valid 32-byte key
        byte[] keyBytes = new byte[32];
        new java.security.SecureRandom().nextBytes(keyBytes);
        String validKey = Base64.getEncoder().encodeToString(keyBytes);
        ReflectionTestUtils.setField(service, "encryptionKeyBase64", validKey);

        // Should not throw
        assertDoesNotThrow(
            service::init,
            "Should initialize successfully with valid key"
        );

        // Verify encryption works
        String plaintext = "test-value";
        String encrypted = service.encrypt(plaintext);
        String decrypted = service.decrypt(encrypted);
        assertEquals(plaintext, decrypted, "Encryption/decryption should work after init");
    }
}
