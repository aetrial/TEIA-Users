package com.teia.users.migration;

import com.teia.users.security.EncryptionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

/**
 * One-time migration runner to encrypt existing plaintext credentials in the database.
 *
 * This runner is disabled by default. To run the migration:
 * 1. Set encryption.migration.enabled=true in application.properties or via env var
 * 2. Start the application
 * 3. The migration will run automatically on startup
 * 4. Disable the migration flag after successful completion
 *
 * The migration is idempotent - it detects already-encrypted values and skips them.
 * It uses JDBC directly to avoid the JPA converter intercepting the values.
 */
@Component
public class DataMigrationRunner implements ApplicationRunner {

    private static final Logger logger = LoggerFactory.getLogger(DataMigrationRunner.class);

    private final JdbcTemplate jdbcTemplate;
    private final EncryptionService encryptionService;

    @Value("${encryption.migration.enabled:false}")
    private boolean migrationEnabled;

    public DataMigrationRunner(JdbcTemplate jdbcTemplate, EncryptionService encryptionService) {
        this.jdbcTemplate = jdbcTemplate;
        this.encryptionService = encryptionService;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (!migrationEnabled) {
            logger.debug("Encryption migration is disabled. Set encryption.migration.enabled=true to run migration.");
            return;
        }

        logger.info("=== Starting encryption migration for sensitive credentials ===");

        int totalMigrated = 0;
        int totalSkipped = 0;

        // Migrate each table/column combination
        MigrationResult twilioResult = migrateTable("twilio_info", "twilio_auth_token");
        totalMigrated += twilioResult.migrated;
        totalSkipped += twilioResult.skipped;

        MigrationResult openAiResult = migrateTable("open_ai_info", "open_ai_api_key");
        totalMigrated += openAiResult.migrated;
        totalSkipped += openAiResult.skipped;

        MigrationResult elevenLabsResult = migrateTable("eleven_labs_info", "eleven_labs_api_key");
        totalMigrated += elevenLabsResult.migrated;
        totalSkipped += elevenLabsResult.skipped;

        MigrationResult calResult = migrateTable("cal_info", "api_key");
        totalMigrated += calResult.migrated;
        totalSkipped += calResult.skipped;

        logger.info("=== Encryption migration complete ===");
        logger.info("Total records migrated: {}", totalMigrated);
        logger.info("Total records skipped (already encrypted): {}", totalSkipped);
        logger.info("IMPORTANT: Set encryption.migration.enabled=false to disable this migration on next startup");
    }

    private MigrationResult migrateTable(String tableName, String columnName) {
        logger.info("Migrating table '{}', column '{}'...", tableName, columnName);

        int migrated = 0;
        int skipped = 0;

        try {
            String selectSql = String.format("SELECT id, %s FROM %s WHERE %s IS NOT NULL",
                    columnName, tableName, columnName);

            List<Map<String, Object>> rows = jdbcTemplate.queryForList(selectSql);

            for (Map<String, Object> row : rows) {
                Long id = ((Number) row.get("id")).longValue();
                String currentValue = (String) row.get(columnName);

                if (currentValue == null || currentValue.isBlank()) {
                    continue;
                }

                // Check if already encrypted by attempting to decrypt
                if (isAlreadyEncrypted(currentValue)) {
                    logger.debug("Row {} in {} already encrypted, skipping", id, tableName);
                    skipped++;
                    continue;
                }

                // Encrypt the plaintext value
                String encryptedValue = encryptionService.encrypt(currentValue);

                // Update the row
                String updateSql = String.format("UPDATE %s SET %s = ? WHERE id = ?",
                        tableName, columnName);
                jdbcTemplate.update(updateSql, encryptedValue, id);

                migrated++;
                logger.debug("Encrypted row {} in {}", id, tableName);
            }

            logger.info("Table '{}': {} migrated, {} skipped", tableName, migrated, skipped);
        } catch (Exception e) {
            logger.error("Error migrating table '{}': {}", tableName, e.getMessage(), e);
            throw new RuntimeException("Migration failed for table " + tableName, e);
        }

        return new MigrationResult(migrated, skipped);
    }

    /**
     * Attempts to determine if a value is already encrypted.
     * Uses heuristics: tries to decrypt the value - if successful, it's encrypted.
     */
    private boolean isAlreadyEncrypted(String value) {
        // First check if it looks like encrypted data (base64 with right length)
        if (!encryptionService.appearsEncrypted(value)) {
            return false;
        }

        // Try to decrypt - if successful, it's encrypted
        String decrypted = encryptionService.tryDecrypt(value);
        return decrypted != null;
    }

    private record MigrationResult(int migrated, int skipped) {}
}
