package com.teia.users.models;

import com.teia.users.repositories.ClientConfigRepository;
import com.teia.users.repositories.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityManager;
import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
@DisplayName("ClientConfig Entity")
class ClientConfigTest {

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private ClientConfigRepository clientConfigRepository;

    @Autowired
    private UserRepository userRepository;

    private TeiaUser testClient;

    @BeforeEach
    void setUp() {
        // Create required related entities
        ElevenLabsInfo elevenLabsInfo = new ElevenLabsInfo("agent-id", "api-key");
        TwilioInfo twilioInfo = new TwilioInfo("AC123", "token", "+15551234567", "+15559876543");
        OpenAIInfo openAIInfo = new OpenAIInfo("sk-api-key", "asst_123");
        CalInfo calInfo = new CalInfo("cal-api-key", "user-id", "event-id");

        testClient = new TeiaUser();
        testClient.setName("Test Client");
        testClient.setEmail("testclient-" + System.nanoTime() + "@example.com");
        testClient.setPhoneNumber("+15551234567");
        testClient.setElevenLabsInfo(elevenLabsInfo);
        testClient.setTwilioInfo(twilioInfo);
        testClient.setOpenAIInfo(openAIInfo);
        testClient.setCalInfo(calInfo);
        testClient.setCreditBalance(new BigDecimal("100.00"));

        testClient = userRepository.save(testClient);
        entityManager.flush();
    }

    @Nested
    @DisplayName("Encryption")
    class EncryptionTests {

        @Test
        @DisplayName("sensitive fields should be encrypted when stored and decrypted when read")
        void sensitiveFieldsShouldBeEncryptedAndDecrypted() {
            String plainTwilioToken = "my-secret-twilio-token";
            String plainElevenLabsKey = "my-secret-eleven-labs-key";
            String plainOpenAiKey = "my-secret-openai-key";
            String plainCalKey = "my-secret-cal-key";

            ClientConfig config = new ClientConfig();
            config.setClient(testClient);
            config.setTwilioAccountSid("AC123456789");
            config.setTwilioAuthToken(plainTwilioToken);
            config.setTwilioCallNumber("+15551234567");
            config.setElevenLabsApiKey(plainElevenLabsKey);
            config.setElevenLabsAgentId("agent-123");
            config.setOpenAiApiKey(plainOpenAiKey);
            config.setOpenAiAssistantId("asst_ABC123");
            config.setCalApiKey(plainCalKey);
            config.setCalUserId("cal-user-1");
            config.setCalEventId("cal-event-1");

            ClientConfig saved = clientConfigRepository.save(config);
            entityManager.flush();
            entityManager.clear();

            // Read back from database
            ClientConfig retrieved = clientConfigRepository.findById(saved.getId()).orElseThrow();

            // Values should be decrypted to original plaintext
            assertEquals(plainTwilioToken, retrieved.getTwilioAuthToken());
            assertEquals(plainElevenLabsKey, retrieved.getElevenLabsApiKey());
            assertEquals(plainOpenAiKey, retrieved.getOpenAiApiKey());
            assertEquals(plainCalKey, retrieved.getCalApiKey());
        }

        @Test
        @DisplayName("non-sensitive fields should not be encrypted")
        void nonSensitiveFieldsShouldNotBeEncrypted() {
            String accountSid = "AC123456789";
            String agentId = "agent-123";
            String assistantId = "asst_ABC123";

            ClientConfig config = new ClientConfig();
            config.setClient(testClient);
            config.setTwilioAccountSid(accountSid);
            config.setTwilioAuthToken("secret");
            config.setElevenLabsAgentId(agentId);
            config.setElevenLabsApiKey("secret");
            config.setOpenAiAssistantId(assistantId);
            config.setOpenAiApiKey("secret");
            config.setCalApiKey("secret");

            ClientConfig saved = clientConfigRepository.save(config);
            entityManager.flush();
            entityManager.clear();

            ClientConfig retrieved = clientConfigRepository.findById(saved.getId()).orElseThrow();

            // Non-encrypted fields should be unchanged
            assertEquals(accountSid, retrieved.getTwilioAccountSid());
            assertEquals(agentId, retrieved.getElevenLabsAgentId());
            assertEquals(assistantId, retrieved.getOpenAiAssistantId());
        }
    }

    @Nested
    @DisplayName("Repository operations")
    class RepositoryOperations {

        @Test
        @DisplayName("should find config by client ID")
        void shouldFindByClientId() {
            ClientConfig config = new ClientConfig();
            config.setClient(testClient);
            config.setTwilioAccountSid("AC123");
            config.setTwilioAuthToken("token");
            config.setElevenLabsApiKey("key");
            config.setOpenAiApiKey("key");
            config.setCalApiKey("key");

            clientConfigRepository.save(config);
            entityManager.flush();
            entityManager.clear();

            var found = clientConfigRepository.findByClientId(testClient.getId());

            assertTrue(found.isPresent());
            assertEquals("AC123", found.get().getTwilioAccountSid());
        }

        @Test
        @DisplayName("should check existence by client ID")
        void shouldCheckExistenceByClientId() {
            assertFalse(clientConfigRepository.existsByClientId(testClient.getId()));

            ClientConfig config = new ClientConfig();
            config.setClient(testClient);
            config.setTwilioAuthToken("token");
            config.setElevenLabsApiKey("key");
            config.setOpenAiApiKey("key");
            config.setCalApiKey("key");

            clientConfigRepository.save(config);
            entityManager.flush();

            assertTrue(clientConfigRepository.existsByClientId(testClient.getId()));
        }
    }

    @Nested
    @DisplayName("TeiaUser creditBalance field")
    class CreditBalanceTests {

        @Test
        @DisplayName("creditBalance should be persisted correctly")
        void creditBalanceShouldBePersisted() {
            entityManager.clear();

            TeiaUser retrieved = userRepository.findById(testClient.getId()).orElseThrow();

            assertEquals(new BigDecimal("100.00"), retrieved.getCreditBalance());
        }

        @Test
        @DisplayName("creditBalance should default to zero")
        void creditBalanceShouldDefaultToZero() {
            ElevenLabsInfo elevenLabsInfo = new ElevenLabsInfo("agent-id", "api-key");
            TwilioInfo twilioInfo = new TwilioInfo("AC123", "token", "+15551234567", "+15559876543");
            OpenAIInfo openAIInfo = new OpenAIInfo("sk-api-key", "asst_123");
            CalInfo calInfo = new CalInfo("cal-api-key", "user-id", "event-id");

            TeiaUser newUser = new TeiaUser();
            newUser.setName("New User");
            newUser.setEmail("newuser-" + System.nanoTime() + "@example.com");
            newUser.setPhoneNumber("+15559999999");
            newUser.setElevenLabsInfo(elevenLabsInfo);
            newUser.setTwilioInfo(twilioInfo);
            newUser.setOpenAIInfo(openAIInfo);
            newUser.setCalInfo(calInfo);
            // Do not set creditBalance - should default

            TeiaUser saved = userRepository.save(newUser);
            entityManager.flush();
            entityManager.clear();

            TeiaUser retrieved = userRepository.findById(saved.getId()).orElseThrow();
            assertEquals(0, retrieved.getCreditBalance().compareTo(BigDecimal.ZERO));
        }
    }

    @Nested
    @DisplayName("Timestamp management")
    class TimestampTests {

        @Test
        @DisplayName("updatedAt should be set automatically")
        void updatedAtShouldBeSetAutomatically() {
            ClientConfig config = new ClientConfig();
            config.setClient(testClient);
            config.setTwilioAuthToken("token");
            config.setElevenLabsApiKey("key");
            config.setOpenAiApiKey("key");
            config.setCalApiKey("key");

            ClientConfig saved = clientConfigRepository.save(config);
            entityManager.flush();

            assertNotNull(saved.getUpdatedAt());
        }
    }
}
