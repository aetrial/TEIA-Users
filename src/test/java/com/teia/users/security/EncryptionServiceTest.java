package com.teia.users.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Base64;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for EncryptionService.
 * Tests encryption/decryption functionality, key validation, and edge cases.
 */
class EncryptionServiceTest {

    // Valid 32-byte key encoded as base64
    private static final String VALID_KEY = "K7gNU3sdo+OL0wNhqoVWhr3g6s1xYv72ol/pe/Unols=";

    // Another valid 32-byte key for testing wrong key scenarios
    private static final String DIFFERENT_KEY = "ywapTHQDhw9YdzwAD+3m0gPOa3Hl/M3cUN5e3upf6OA=";

    private EncryptionService encryptionService;

    @BeforeEach
    void setUp() {
        encryptionService = new EncryptionService();
        ReflectionTestUtils.setField(encryptionService, "encryptionKeyBase64", VALID_KEY);
        encryptionService.init();
    }

    @Nested
    @DisplayName("Encryption and Decryption")
    class EncryptDecrypt {

        @Test
        @DisplayName("encrypt then decrypt returns original plaintext")
        void encryptThenDecryptReturnsOriginal() {
            String plaintext = "sk-1234567890abcdef";

            String ciphertext = encryptionService.encrypt(plaintext);
            String decrypted = encryptionService.decrypt(ciphertext);

            assertEquals(plaintext, decrypted);
        }

        @Test
        @DisplayName("encrypt then decrypt works for various API key formats")
        void encryptDecryptVariousFormats() {
            String[] testValues = {
                "sk-proj-abc123xyz789",                          // OpenAI format
                "AC1234567890abcdef1234567890abcd",              // Twilio SID format
                "1234567890abcdef1234567890abcdef",              // Twilio auth token
                "xi-api-key-1234567890",                         // ElevenLabs format
                "cal_live_1234567890abcdef",                     // Cal.com format
                "a",                                              // Single character
                "a".repeat(200),                                  // Long value
                "special!@#$%^&*()chars",                        // Special characters
                "unicode-\u00e9\u00e8\u00ea-chars",              // Unicode
                "",                                               // Empty string
            };

            for (String value : testValues) {
                if (value.isEmpty()) {
                    // Empty string should still work
                    String encrypted = encryptionService.encrypt(value);
                    String decrypted = encryptionService.decrypt(encrypted);
                    assertEquals(value, decrypted, "Failed for empty string");
                } else {
                    String encrypted = encryptionService.encrypt(value);
                    String decrypted = encryptionService.decrypt(encrypted);
                    assertEquals(value, decrypted, "Failed for: " + value.substring(0, Math.min(20, value.length())));
                }
            }
        }

        @Test
        @DisplayName("different plaintexts produce different ciphertexts")
        void differentPlaintextsProduceDifferentCiphertexts() {
            String plaintext1 = "api-key-one";
            String plaintext2 = "api-key-two";

            String ciphertext1 = encryptionService.encrypt(plaintext1);
            String ciphertext2 = encryptionService.encrypt(plaintext2);

            assertNotEquals(ciphertext1, ciphertext2);
        }

        @Test
        @DisplayName("same plaintext produces different ciphertexts (due to random IV)")
        void samePlaintextProducesDifferentCiphertexts() {
            String plaintext = "same-api-key";
            Set<String> ciphertexts = new HashSet<>();

            // Encrypt the same value multiple times
            for (int i = 0; i < 10; i++) {
                String ciphertext = encryptionService.encrypt(plaintext);
                ciphertexts.add(ciphertext);
            }

            // All ciphertexts should be unique due to random IV
            assertEquals(10, ciphertexts.size(), "Random IV should produce unique ciphertexts");
        }

        @Test
        @DisplayName("ciphertext is valid base64")
        void ciphertextIsValidBase64() {
            String plaintext = "test-api-key";

            String ciphertext = encryptionService.encrypt(plaintext);

            assertDoesNotThrow(() -> Base64.getDecoder().decode(ciphertext));
        }

        @Test
        @DisplayName("null input returns null for encrypt")
        void encryptNullReturnsNull() {
            assertNull(encryptionService.encrypt(null));
        }

        @Test
        @DisplayName("null input returns null for decrypt")
        void decryptNullReturnsNull() {
            assertNull(encryptionService.decrypt(null));
        }
    }

    @Nested
    @DisplayName("Decryption Failures")
    class DecryptionFailures {

        @Test
        @DisplayName("wrong key fails decryption")
        void wrongKeyFailsDecryption() {
            // Encrypt with original key
            String plaintext = "secret-api-key";
            String ciphertext = encryptionService.encrypt(plaintext);

            // Create new service with different key
            EncryptionService differentKeyService = new EncryptionService();
            ReflectionTestUtils.setField(differentKeyService, "encryptionKeyBase64", DIFFERENT_KEY);
            differentKeyService.init();

            // Decryption should fail
            assertThrows(EncryptionService.EncryptionException.class,
                () -> differentKeyService.decrypt(ciphertext));
        }

        @Test
        @DisplayName("tampered ciphertext fails decryption (GCM authentication)")
        void tamperedCiphertextFailsDecryption() {
            String plaintext = "secret-api-key";
            String ciphertext = encryptionService.encrypt(plaintext);

            // Tamper with the ciphertext (flip a bit)
            byte[] decoded = Base64.getDecoder().decode(ciphertext);
            decoded[decoded.length - 1] ^= 0x01; // Flip last bit
            String tamperedCiphertext = Base64.getEncoder().encodeToString(decoded);

            assertThrows(EncryptionService.EncryptionException.class,
                () -> encryptionService.decrypt(tamperedCiphertext));
        }

        @Test
        @DisplayName("invalid base64 fails decryption")
        void invalidBase64FailsDecryption() {
            assertThrows(EncryptionService.EncryptionException.class,
                () -> encryptionService.decrypt("not-valid-base64!!!"));
        }

        @Test
        @DisplayName("truncated ciphertext fails decryption")
        void truncatedCiphertextFailsDecryption() {
            String plaintext = "secret-api-key";
            String ciphertext = encryptionService.encrypt(plaintext);

            // Truncate to be too short
            String truncated = ciphertext.substring(0, 10);

            assertThrows(EncryptionService.EncryptionException.class,
                () -> encryptionService.decrypt(truncated));
        }
    }

    @Nested
    @DisplayName("Key Validation")
    class KeyValidation {

        @Test
        @DisplayName("missing key throws on init")
        void missingKeyThrowsOnInit() {
            EncryptionService service = new EncryptionService();
            ReflectionTestUtils.setField(service, "encryptionKeyBase64", "");

            IllegalStateException exception = assertThrows(IllegalStateException.class, service::init);
            assertTrue(exception.getMessage().contains("ENCRYPTION_KEY"));
        }

        @Test
        @DisplayName("null key throws on init")
        void nullKeyThrowsOnInit() {
            EncryptionService service = new EncryptionService();
            ReflectionTestUtils.setField(service, "encryptionKeyBase64", null);

            IllegalStateException exception = assertThrows(IllegalStateException.class, service::init);
            assertTrue(exception.getMessage().contains("ENCRYPTION_KEY"));
        }

        @Test
        @DisplayName("wrong length key throws on init")
        void wrongLengthKeyThrowsOnInit() {
            EncryptionService service = new EncryptionService();
            // 16-byte key (too short)
            String shortKey = Base64.getEncoder().encodeToString(new byte[16]);
            ReflectionTestUtils.setField(service, "encryptionKeyBase64", shortKey);

            IllegalStateException exception = assertThrows(IllegalStateException.class, service::init);
            assertTrue(exception.getMessage().contains("32 bytes"));
        }

        @Test
        @DisplayName("invalid base64 key throws on init")
        void invalidBase64KeyThrowsOnInit() {
            EncryptionService service = new EncryptionService();
            ReflectionTestUtils.setField(service, "encryptionKeyBase64", "not-valid-base64!!!");

            IllegalStateException exception = assertThrows(IllegalStateException.class, service::init);
            assertTrue(exception.getMessage().contains("base64"));
        }
    }

    @Nested
    @DisplayName("Helper Methods")
    class HelperMethods {

        @Test
        @DisplayName("appearsEncrypted returns true for valid ciphertext")
        void appearsEncryptedTrueForCiphertext() {
            String plaintext = "api-key";
            String ciphertext = encryptionService.encrypt(plaintext);

            assertTrue(encryptionService.appearsEncrypted(ciphertext));
        }

        @Test
        @DisplayName("appearsEncrypted returns false for plaintext")
        void appearsEncryptedFalseForPlaintext() {
            assertFalse(encryptionService.appearsEncrypted("sk-plain-api-key"));
        }

        @Test
        @DisplayName("appearsEncrypted returns false for null")
        void appearsEncryptedFalseForNull() {
            assertFalse(encryptionService.appearsEncrypted(null));
        }

        @Test
        @DisplayName("appearsEncrypted returns false for empty string")
        void appearsEncryptedFalseForEmpty() {
            assertFalse(encryptionService.appearsEncrypted(""));
        }

        @Test
        @DisplayName("tryDecrypt returns decrypted value for valid ciphertext")
        void tryDecryptReturnsValueForValidCiphertext() {
            String plaintext = "api-key";
            String ciphertext = encryptionService.encrypt(plaintext);

            assertEquals(plaintext, encryptionService.tryDecrypt(ciphertext));
        }

        @Test
        @DisplayName("tryDecrypt returns null for invalid ciphertext")
        void tryDecryptReturnsNullForInvalidCiphertext() {
            assertNull(encryptionService.tryDecrypt("not-encrypted"));
        }
    }
}
