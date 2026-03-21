package com.teia.users.security;

import com.teia.users.config.ApplicationContextProvider;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * JPA AttributeConverter that transparently encrypts/decrypts string fields.
 *
 * Apply this converter to sensitive entity fields using:
 *   @Convert(converter = EncryptedStringConverter.class)
 *   private String sensitiveField;
 *
 * The field will be encrypted before being stored in the database and
 * decrypted when loaded from the database. This provides encryption at rest
 * without requiring changes to service layer code.
 *
 * Note: JPA AttributeConverters are instantiated by Hibernate, not Spring,
 * so we use ApplicationContextProvider to obtain the EncryptionService bean.
 */
@Converter
public class EncryptedStringConverter implements AttributeConverter<String, String> {

    private static final Logger logger = LoggerFactory.getLogger(EncryptedStringConverter.class);

    private EncryptionService getEncryptionService() {
        return ApplicationContextProvider.getBean(EncryptionService.class);
    }

    /**
     * Encrypts the entity attribute value before storing in the database.
     *
     * @param attribute The plaintext value from the entity
     * @return The encrypted value to store in the database
     */
    @Override
    public String convertToDatabaseColumn(String attribute) {
        if (attribute == null) {
            return null;
        }

        try {
            return getEncryptionService().encrypt(attribute);
        } catch (Exception e) {
            logger.error("Failed to encrypt attribute value", e);
            throw new RuntimeException("Encryption failed", e);
        }
    }

    /**
     * Decrypts the database column value when loading into the entity.
     *
     * @param dbData The encrypted value from the database
     * @return The decrypted plaintext value for the entity
     */
    @Override
    public String convertToEntityAttribute(String dbData) {
        if (dbData == null) {
            return null;
        }

        try {
            return getEncryptionService().decrypt(dbData);
        } catch (Exception e) {
            logger.error("Failed to decrypt attribute value - data may be corrupted or key may have changed", e);
            throw new RuntimeException("Decryption failed", e);
        }
    }
}
