package com.teia.users.services;

import com.teia.users.dto.ClientConfigRequest;
import com.teia.users.dto.ClientConfigResponse;
import com.teia.users.dto.ClientSummaryResponse;
import com.teia.users.models.ClientConfig;
import com.teia.users.models.TeiaUser;
import com.teia.users.models.enums.Role;
import com.teia.users.repositories.ClientConfigRepository;
import com.teia.users.repositories.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ClientConfigService")
class ClientConfigServiceTest {

    @Mock
    private ClientConfigRepository clientConfigRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private ClientConfigService clientConfigService;

    private TeiaUser mockClient;
    private ClientConfig mockConfig;

    @BeforeEach
    void setUp() {
        mockClient = new TeiaUser();
        mockClient.setId(100L);
        mockClient.setName("Test Client");
        mockClient.setEmail("test@example.com");
        mockClient.setRole(Role.CLIENT);
        mockClient.setCreditBalance(new BigDecimal("50.00"));

        mockConfig = new ClientConfig();
        mockConfig.setId(1L);
        mockConfig.setClient(mockClient);
        mockConfig.setTwilioAccountSid("AC123456789");
        mockConfig.setTwilioAuthToken("auth-token-secret-12345678");
        mockConfig.setTwilioCallNumber("+15551234567");
        mockConfig.setElevenLabsApiKey("eleven-api-key-abcdefgh");
        mockConfig.setElevenLabsAgentId("agent-123");
        mockConfig.setOpenAiApiKey("sk-openai-api-key-wxyzabcd");
        mockConfig.setOpenAiAssistantId("asst_ABC123");
        mockConfig.setCalApiKey("cal-api-key-12345678");
        mockConfig.setCalUserId("cal-user-1");
        mockConfig.setCalEventId("cal-event-1");
    }

    @Nested
    @DisplayName("createOrUpdateConfig")
    class CreateOrUpdateConfig {

        @Test
        @DisplayName("should create new config when none exists")
        void shouldCreateNewConfig() {
            when(userRepository.findById(100L)).thenReturn(Optional.of(mockClient));
            when(clientConfigRepository.findByClientId(100L)).thenReturn(Optional.empty());
            when(clientConfigRepository.save(any(ClientConfig.class))).thenAnswer(invocation -> {
                ClientConfig saved = invocation.getArgument(0);
                saved.setId(1L);
                return saved;
            });

            ClientConfigRequest request = new ClientConfigRequest();
            request.setTwilioAccountSid("AC123456789");
            request.setTwilioAuthToken("secret-token");

            ClientConfigResponse response = clientConfigService.createOrUpdateConfig(100L, request);

            assertNotNull(response);
            assertEquals(100L, response.getClientId());
            assertEquals("AC123456789", response.getTwilioAccountSid());
            assertEquals("****oken", response.getTwilioAuthToken()); // Last 4 chars of "secret-token"
            verify(clientConfigRepository).save(any(ClientConfig.class));
        }

        @Test
        @DisplayName("should update existing config")
        void shouldUpdateExistingConfig() {
            when(userRepository.findById(100L)).thenReturn(Optional.of(mockClient));
            when(clientConfigRepository.findByClientId(100L)).thenReturn(Optional.of(mockConfig));
            when(clientConfigRepository.save(any(ClientConfig.class))).thenReturn(mockConfig);

            ClientConfigRequest request = new ClientConfigRequest();
            request.setTwilioAccountSid("AC_UPDATED");
            request.setTwilioAuthToken("new-secret-token");

            ClientConfigResponse response = clientConfigService.createOrUpdateConfig(100L, request);

            assertNotNull(response);
            verify(clientConfigRepository).save(any(ClientConfig.class));
        }

        @Test
        @DisplayName("should throw ClientNotFoundException when client not found")
        void shouldThrowWhenClientNotFound() {
            when(userRepository.findById(999L)).thenReturn(Optional.empty());

            ClientConfigRequest request = new ClientConfigRequest();

            assertThrows(ClientConfigService.ClientNotFoundException.class,
                    () -> clientConfigService.createOrUpdateConfig(999L, request));
        }
    }

    @Nested
    @DisplayName("getConfig")
    class GetConfig {

        @Test
        @DisplayName("should return config with masked sensitive fields")
        void shouldReturnMaskedConfig() {
            when(clientConfigRepository.findByClientId(100L)).thenReturn(Optional.of(mockConfig));

            ClientConfigResponse response = clientConfigService.getConfig(100L);

            assertNotNull(response);
            assertEquals(100L, response.getClientId());
            assertEquals("AC123456789", response.getTwilioAccountSid());
            assertEquals("****5678", response.getTwilioAuthToken()); // Last 4 of "auth-token-secret-12345678"
            assertEquals("****efgh", response.getElevenLabsApiKey()); // Last 4 of "eleven-api-key-abcdefgh"
            assertEquals("****abcd", response.getOpenAiApiKey()); // Last 4 of "sk-openai-api-key-wxyzabcd"
            assertEquals("****5678", response.getCalApiKey()); // Last 4 of "cal-api-key-12345678"
        }

        @Test
        @DisplayName("should throw ConfigNotFoundException when config not found")
        void shouldThrowWhenConfigNotFound() {
            when(clientConfigRepository.findByClientId(100L)).thenReturn(Optional.empty());

            assertThrows(ClientConfigService.ConfigNotFoundException.class,
                    () -> clientConfigService.getConfig(100L));
        }
    }

    @Nested
    @DisplayName("patchConfig")
    class PatchConfig {

        @Test
        @DisplayName("should update only provided fields")
        void shouldUpdateOnlyProvidedFields() {
            when(clientConfigRepository.findByClientId(100L)).thenReturn(Optional.of(mockConfig));
            when(clientConfigRepository.save(any(ClientConfig.class))).thenReturn(mockConfig);

            ClientConfigRequest request = new ClientConfigRequest();
            request.setTwilioCallNumber("+15559999999"); // Only update this field

            ClientConfigResponse response = clientConfigService.patchConfig(100L, request);

            assertNotNull(response);
            // Original values should be preserved, only twilioCallNumber updated
            verify(clientConfigRepository).save(argThat(config ->
                    config.getTwilioAccountSid().equals("AC123456789") &&
                    config.getTwilioCallNumber().equals("+15559999999")
            ));
        }

        @Test
        @DisplayName("should throw ConfigNotFoundException when config not found")
        void shouldThrowWhenConfigNotFound() {
            when(clientConfigRepository.findByClientId(100L)).thenReturn(Optional.empty());

            ClientConfigRequest request = new ClientConfigRequest();

            assertThrows(ClientConfigService.ConfigNotFoundException.class,
                    () -> clientConfigService.patchConfig(100L, request));
        }
    }

    @Nested
    @DisplayName("listClientsWithConfigStatus")
    class ListClientsWithConfigStatus {

        @Test
        @DisplayName("should return only CLIENT role users with config status")
        void shouldReturnOnlyClientRoleUsers() {
            TeiaUser client1 = new TeiaUser();
            client1.setId(1L);
            client1.setName("Client One");
            client1.setEmail("client1@example.com");
            client1.setRole(Role.CLIENT);
            client1.setCreditBalance(new BigDecimal("100.00"));

            TeiaUser client2 = new TeiaUser();
            client2.setId(2L);
            client2.setName("Client Two");
            client2.setEmail("client2@example.com");
            client2.setRole(Role.CLIENT);
            client2.setCreditBalance(BigDecimal.ZERO);

            TeiaUser admin = new TeiaUser();
            admin.setId(3L);
            admin.setName("Admin User");
            admin.setEmail("admin@example.com");
            admin.setRole(Role.ADMIN);

            when(userRepository.findAll()).thenReturn(Arrays.asList(client1, client2, admin));
            when(clientConfigRepository.existsByClientId(1L)).thenReturn(true);
            when(clientConfigRepository.existsByClientId(2L)).thenReturn(false);

            List<ClientSummaryResponse> result = clientConfigService.listClientsWithConfigStatus();

            assertEquals(2, result.size());
            assertTrue(result.stream().noneMatch(c -> c.getName().equals("Admin User")));
            assertTrue(result.stream().anyMatch(c -> c.getClientId() == 1L && c.isHasConfig()));
            assertTrue(result.stream().anyMatch(c -> c.getClientId() == 2L && !c.isHasConfig()));
        }

        @Test
        @DisplayName("should include credit balance in response")
        void shouldIncludeCreditBalance() {
            TeiaUser client = new TeiaUser();
            client.setId(1L);
            client.setName("Test Client");
            client.setEmail("test@example.com");
            client.setRole(Role.CLIENT);
            client.setCreditBalance(new BigDecimal("250.50"));

            when(userRepository.findAll()).thenReturn(List.of(client));
            when(clientConfigRepository.existsByClientId(1L)).thenReturn(true);

            List<ClientSummaryResponse> result = clientConfigService.listClientsWithConfigStatus();

            assertEquals(1, result.size());
            assertEquals(new BigDecimal("250.50"), result.get(0).getCreditBalance());
        }
    }

    @Nested
    @DisplayName("maskSensitiveValue")
    class MaskSensitiveValue {

        @Test
        @DisplayName("should mask values showing last 4 characters")
        void shouldMaskWithLast4Chars() {
            mockConfig.setTwilioAuthToken("secret12345678");
            when(clientConfigRepository.findByClientId(100L)).thenReturn(Optional.of(mockConfig));

            ClientConfigResponse response = clientConfigService.getConfig(100L);

            assertEquals("****5678", response.getTwilioAuthToken());
        }

        @Test
        @DisplayName("should return **** for null values")
        void shouldReturnMaskForNull() {
            mockConfig.setTwilioAuthToken(null);
            when(clientConfigRepository.findByClientId(100L)).thenReturn(Optional.of(mockConfig));

            ClientConfigResponse response = clientConfigService.getConfig(100L);

            assertEquals("****", response.getTwilioAuthToken());
        }

        @Test
        @DisplayName("should return **** for short values")
        void shouldReturnMaskForShortValues() {
            mockConfig.setTwilioAuthToken("abc");
            when(clientConfigRepository.findByClientId(100L)).thenReturn(Optional.of(mockConfig));

            ClientConfigResponse response = clientConfigService.getConfig(100L);

            assertEquals("****", response.getTwilioAuthToken());
        }
    }
}
