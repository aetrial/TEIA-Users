package com.teia.users.security;

import com.teia.users.models.*;
import com.teia.users.repositories.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests verifying that sensitive fields are encrypted in the database
 * but decrypted transparently when loaded via JPA.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class EncryptedFieldIntegrationTest {

    @Autowired
    private TwilioInfoRepository twilioInfoRepository;

    @Autowired
    private OpenAIInfoRepository openAIInfoRepository;

    @Autowired
    private ElevenLabsInfoRepository elevenLabsInfoRepository;

    @Autowired
    private CalInfoRepository calInfoRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private EncryptionService encryptionService;

    // Test values - these are plaintext values we'll save
    private static final String TWILIO_AUTH_TOKEN = "auth_token_secret_12345";
    private static final String OPENAI_API_KEY = "sk-proj-openai-test-key-12345";
    private static final String ELEVENLABS_API_KEY = "xi-api-elevenlabs-test-key-12345";
    private static final String CAL_API_KEY = "cal_live_test_key_12345";

    @Test
    @DisplayName("TwilioInfo.twilioAuthToken is encrypted in DB but decrypted on read")
    void twilioAuthTokenIsEncrypted() {
        // Create and save entity with plaintext
        TwilioInfo twilioInfo = new TwilioInfo();
        twilioInfo.setTwilioAccSID("AC1234567890");
        twilioInfo.setTwilioAuthToken(TWILIO_AUTH_TOKEN);
        twilioInfo.setTwilioPhoneNumberCalls("+15551234567");
        twilioInfo.setTwilioPhoneNumberWhatsapp("+15557654321");

        TwilioInfo saved = twilioInfoRepository.save(twilioInfo);
        twilioInfoRepository.flush();

        // Query raw DB value using JDBC
        String rawDbValue = jdbcTemplate.queryForObject(
            "SELECT twilio_auth_token FROM twilio_info WHERE id = ?",
            String.class,
            saved.getId()
        );

        // Verify: raw DB value is NOT the plaintext (it's encrypted)
        assertNotNull(rawDbValue);
        assertNotEquals(TWILIO_AUTH_TOKEN, rawDbValue, "DB should store encrypted value, not plaintext");
        assertTrue(encryptionService.appearsEncrypted(rawDbValue), "DB value should appear encrypted");

        // Verify: loading via repository returns decrypted plaintext
        TwilioInfo loaded = twilioInfoRepository.findById(saved.getId()).orElseThrow();
        assertEquals(TWILIO_AUTH_TOKEN, loaded.getTwilioAuthToken(), "Loaded value should match original plaintext");
    }

    @Test
    @DisplayName("OpenAIInfo.openAIApiKey is encrypted in DB but decrypted on read")
    void openAIApiKeyIsEncrypted() {
        // Create and save entity with plaintext
        OpenAIInfo openAIInfo = new OpenAIInfo();
        openAIInfo.setOpenAIApiKey(OPENAI_API_KEY);
        openAIInfo.setOpenAIAssistantKey("asst_abc123");

        OpenAIInfo saved = openAIInfoRepository.save(openAIInfo);
        openAIInfoRepository.flush();

        // Query raw DB value using JDBC
        String rawDbValue = jdbcTemplate.queryForObject(
            "SELECT open_ai_api_key FROM open_ai_info WHERE id = ?",
            String.class,
            saved.getId()
        );

        // Verify: raw DB value is NOT the plaintext
        assertNotNull(rawDbValue);
        assertNotEquals(OPENAI_API_KEY, rawDbValue, "DB should store encrypted value, not plaintext");
        assertTrue(encryptionService.appearsEncrypted(rawDbValue), "DB value should appear encrypted");

        // Verify: loading via repository returns decrypted plaintext
        OpenAIInfo loaded = openAIInfoRepository.findById(saved.getId()).orElseThrow();
        assertEquals(OPENAI_API_KEY, loaded.getOpenAIApiKey(), "Loaded value should match original plaintext");
    }

    @Test
    @DisplayName("ElevenLabsInfo.elevenLabsApiKey is encrypted in DB but decrypted on read")
    void elevenLabsApiKeyIsEncrypted() {
        // Create and save entity with plaintext
        ElevenLabsInfo elevenLabsInfo = new ElevenLabsInfo();
        elevenLabsInfo.setElevenLabsApiKey(ELEVENLABS_API_KEY);
        elevenLabsInfo.setElevenLabsAgentId("agent_xyz789");

        ElevenLabsInfo saved = elevenLabsInfoRepository.save(elevenLabsInfo);
        elevenLabsInfoRepository.flush();

        // Query raw DB value using JDBC
        String rawDbValue = jdbcTemplate.queryForObject(
            "SELECT eleven_labs_api_key FROM eleven_labs_info WHERE id = ?",
            String.class,
            saved.getId()
        );

        // Verify: raw DB value is NOT the plaintext
        assertNotNull(rawDbValue);
        assertNotEquals(ELEVENLABS_API_KEY, rawDbValue, "DB should store encrypted value, not plaintext");
        assertTrue(encryptionService.appearsEncrypted(rawDbValue), "DB value should appear encrypted");

        // Verify: loading via repository returns decrypted plaintext
        ElevenLabsInfo loaded = elevenLabsInfoRepository.findById(saved.getId()).orElseThrow();
        assertEquals(ELEVENLABS_API_KEY, loaded.getElevenLabsApiKey(), "Loaded value should match original plaintext");
    }

    @Test
    @DisplayName("CalInfo.apiKey is encrypted in DB but decrypted on read")
    void calApiKeyIsEncrypted() {
        // Create and save entity with plaintext
        CalInfo calInfo = new CalInfo();
        calInfo.setApiKey(CAL_API_KEY);
        calInfo.setUserId("user_123");
        calInfo.setEventId("event_456");

        CalInfo saved = calInfoRepository.save(calInfo);
        calInfoRepository.flush();

        // Query raw DB value using JDBC
        String rawDbValue = jdbcTemplate.queryForObject(
            "SELECT api_key FROM cal_info WHERE id = ?",
            String.class,
            saved.getId()
        );

        // Verify: raw DB value is NOT the plaintext
        assertNotNull(rawDbValue);
        assertNotEquals(CAL_API_KEY, rawDbValue, "DB should store encrypted value, not plaintext");
        assertTrue(encryptionService.appearsEncrypted(rawDbValue), "DB value should appear encrypted");

        // Verify: loading via repository returns decrypted plaintext
        CalInfo loaded = calInfoRepository.findById(saved.getId()).orElseThrow();
        assertEquals(CAL_API_KEY, loaded.getApiKey(), "Loaded value should match original plaintext");
    }

    @Test
    @DisplayName("Updating encrypted field re-encrypts with new ciphertext")
    void updatingEncryptedFieldReEncrypts() {
        // Create and save initial entity
        TwilioInfo twilioInfo = new TwilioInfo();
        twilioInfo.setTwilioAccSID("AC1234567890");
        twilioInfo.setTwilioAuthToken(TWILIO_AUTH_TOKEN);
        twilioInfo.setTwilioPhoneNumberCalls("+15551234567");
        twilioInfo.setTwilioPhoneNumberWhatsapp("+15557654321");

        TwilioInfo saved = twilioInfoRepository.save(twilioInfo);
        twilioInfoRepository.flush();

        // Get initial encrypted value
        String initialEncrypted = jdbcTemplate.queryForObject(
            "SELECT twilio_auth_token FROM twilio_info WHERE id = ?",
            String.class,
            saved.getId()
        );

        // Update the field
        String newToken = "new_auth_token_67890";
        saved.setTwilioAuthToken(newToken);
        twilioInfoRepository.save(saved);
        twilioInfoRepository.flush();

        // Get new encrypted value
        String newEncrypted = jdbcTemplate.queryForObject(
            "SELECT twilio_auth_token FROM twilio_info WHERE id = ?",
            String.class,
            saved.getId()
        );

        // Verify: new encrypted value is different
        assertNotEquals(initialEncrypted, newEncrypted, "Updated field should have different ciphertext");

        // Verify: loading returns new plaintext
        TwilioInfo loaded = twilioInfoRepository.findById(saved.getId()).orElseThrow();
        assertEquals(newToken, loaded.getTwilioAuthToken());
    }

    @Test
    @DisplayName("Multiple entities have unique ciphertexts for same plaintext value")
    void multipleSavesProduceUniqueCiphertexts() {
        // Save two entities with the same API key value
        CalInfo calInfo1 = new CalInfo();
        calInfo1.setApiKey(CAL_API_KEY);
        calInfo1.setUserId("user_1");
        calInfo1.setEventId("event_1");

        CalInfo calInfo2 = new CalInfo();
        calInfo2.setApiKey(CAL_API_KEY); // Same plaintext
        calInfo2.setUserId("user_2");
        calInfo2.setEventId("event_2");

        CalInfo saved1 = calInfoRepository.save(calInfo1);
        CalInfo saved2 = calInfoRepository.save(calInfo2);
        calInfoRepository.flush();

        // Get encrypted values
        String encrypted1 = jdbcTemplate.queryForObject(
            "SELECT api_key FROM cal_info WHERE id = ?",
            String.class,
            saved1.getId()
        );
        String encrypted2 = jdbcTemplate.queryForObject(
            "SELECT api_key FROM cal_info WHERE id = ?",
            String.class,
            saved2.getId()
        );

        // Verify: same plaintext produces different ciphertexts (due to random IV)
        assertNotEquals(encrypted1, encrypted2, "Same plaintext should produce different ciphertexts");

        // Verify: both decrypt to the same plaintext
        CalInfo loaded1 = calInfoRepository.findById(saved1.getId()).orElseThrow();
        CalInfo loaded2 = calInfoRepository.findById(saved2.getId()).orElseThrow();
        assertEquals(CAL_API_KEY, loaded1.getApiKey());
        assertEquals(CAL_API_KEY, loaded2.getApiKey());
    }
}
