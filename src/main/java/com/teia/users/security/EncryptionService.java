package com.teia.users.security;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * AES-256-GCM encryption service for protecting sensitive data at rest.
 *
 * This service provides authenticated encryption using AES-256-GCM, which offers
 * both confidentiality and integrity protection. Each encryption operation uses
 * a unique random IV (Initialization Vector) to ensure that identical plaintexts
 * produce different ciphertexts.
 *
 * The encryption key must be provided via the ENCRYPTION_KEY environment variable
 * as a base64-encoded 32-byte (256-bit) value.
 *
 * Ciphertext format: base64(IV || ciphertext || authTag)
 * - IV: 12 bytes (96 bits, GCM standard)
 * - Auth Tag: 16 bytes (128 bits)
 */
@Service
public class EncryptionService {

    private static final Logger logger = LoggerFactory.getLogger(EncryptionService.class);

    private static final String ALGORITHM = "AES";
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int GCM_IV_LENGTH = 12; // 96 bits - recommended for GCM
    private static final int GCM_TAG_LENGTH = 128; // 128 bits - maximum security
    private static final int AES_KEY_LENGTH = 32; // 256 bits

    private final SecureRandom secureRandom = new SecureRandom();
    private SecretKey secretKey;

    @Value("${encryption.key:}")
    private String encryptionKeyBase64;

    @PostConstruct
    public void init() {
        if (encryptionKeyBase64 == null || encryptionKeyBase64.isBlank()) {
            throw new IllegalStateException(
                "ENCRYPTION_KEY environment variable is not set. " +
                "Application cannot start without a valid encryption key. " +
                "Generate a key with: openssl rand -base64 32"
            );
        }

        try {
            byte[] keyBytes = Base64.getDecoder().decode(encryptionKeyBase64);
            if (keyBytes.length != AES_KEY_LENGTH) {
                throw new IllegalStateException(
                    String.format(
                        "ENCRYPTION_KEY must be exactly %d bytes (256 bits) when decoded. " +
                        "Got %d bytes. Generate a valid key with: openssl rand -base64 32",
                        AES_KEY_LENGTH, keyBytes.length
                    )
                );
            }
            this.secretKey = new SecretKeySpec(keyBytes, ALGORITHM);
            logger.info("EncryptionService initialized successfully with AES-256-GCM");
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException(
                "ENCRYPTION_KEY is not valid base64. " +
                "Generate a valid key with: openssl rand -base64 32", e
            );
        }
    }

    /**
     * Encrypts a plaintext string using AES-256-GCM.
     *
     * @param plaintext The string to encrypt
     * @return Base64-encoded ciphertext (IV || encrypted data || auth tag)
     * @throws EncryptionException if encryption fails
     */
    public String encrypt(String plaintext) {
        if (plaintext == null) {
            return null;
        }

        try {
            // Generate random IV
            byte[] iv = new byte[GCM_IV_LENGTH];
            secureRandom.nextBytes(iv);

            // Initialize cipher
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            GCMParameterSpec parameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, parameterSpec);

            // Encrypt
            byte[] plaintextBytes = plaintext.getBytes(java.nio.charset.StandardCharsets.UTF_8);
            byte[] ciphertextBytes = cipher.doFinal(plaintextBytes);

            // Combine IV + ciphertext (auth tag is appended by GCM mode)
            ByteBuffer byteBuffer = ByteBuffer.allocate(iv.length + ciphertextBytes.length);
            byteBuffer.put(iv);
            byteBuffer.put(ciphertextBytes);

            return Base64.getEncoder().encodeToString(byteBuffer.array());
        } catch (Exception e) {
            throw new EncryptionException("Failed to encrypt data", e);
        }
    }

    /**
     * Decrypts a ciphertext string that was encrypted using AES-256-GCM.
     *
     * @param ciphertext Base64-encoded ciphertext (IV || encrypted data || auth tag)
     * @return The original plaintext string
     * @throws EncryptionException if decryption fails (wrong key, tampered data, etc.)
     */
    public String decrypt(String ciphertext) {
        if (ciphertext == null) {
            return null;
        }

        try {
            byte[] decoded = Base64.getDecoder().decode(ciphertext);

            if (decoded.length < GCM_IV_LENGTH + GCM_TAG_LENGTH / 8) {
                throw new EncryptionException("Ciphertext too short to be valid");
            }

            // Extract IV and ciphertext
            ByteBuffer byteBuffer = ByteBuffer.wrap(decoded);
            byte[] iv = new byte[GCM_IV_LENGTH];
            byteBuffer.get(iv);
            byte[] ciphertextBytes = new byte[byteBuffer.remaining()];
            byteBuffer.get(ciphertextBytes);

            // Initialize cipher
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            GCMParameterSpec parameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.DECRYPT_MODE, secretKey, parameterSpec);

            // Decrypt
            byte[] plaintextBytes = cipher.doFinal(ciphertextBytes);
            return new String(plaintextBytes, java.nio.charset.StandardCharsets.UTF_8);
        } catch (EncryptionException e) {
            throw e;
        } catch (Exception e) {
            throw new EncryptionException("Failed to decrypt data", e);
        }
    }

    /**
     * Checks if a string appears to be encrypted (base64 with sufficient length).
     * This is a heuristic check, not a guarantee.
     *
     * @param value The value to check
     * @return true if the value appears to be encrypted
     */
    public boolean appearsEncrypted(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }

        try {
            byte[] decoded = Base64.getDecoder().decode(value);
            // Minimum length: IV (12) + auth tag (16) + at least 1 byte of data
            return decoded.length >= GCM_IV_LENGTH + GCM_TAG_LENGTH / 8 + 1;
        } catch (IllegalArgumentException e) {
            // Not valid base64
            return false;
        }
    }

    /**
     * Attempts to decrypt a value. If decryption fails, returns null.
     * Useful for migration scenarios where data may already be encrypted.
     *
     * @param value The value to try decrypting
     * @return The decrypted value, or null if decryption fails
     */
    public String tryDecrypt(String value) {
        try {
            return decrypt(value);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Runtime exception for encryption/decryption failures.
     */
    public static class EncryptionException extends RuntimeException {
        public EncryptionException(String message) {
            super(message);
        }

        public EncryptionException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
