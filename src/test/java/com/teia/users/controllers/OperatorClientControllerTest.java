package com.teia.users.controllers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.teia.users.dto.ClientConfigRequest;
import com.teia.users.dto.ClientConfigResponse;
import com.teia.users.dto.ClientSummaryResponse;
import com.teia.users.services.ClientConfigService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("OperatorClientController")
class OperatorClientControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private ClientConfigService clientConfigService;

    private ClientConfigResponse createMockConfigResponse() {
        return ClientConfigResponse.builder()
                .id(1L)
                .clientId(100L)
                .twilioAccountSid("AC123456789")
                .twilioAuthToken("****5678")
                .twilioCallNumber("+15551234567")
                .twilioWhatsappNumber("+15559876543")
                .elevenLabsApiKey("****abcd")
                .elevenLabsAgentId("agent-123")
                .openAiApiKey("****wxyz")
                .openAiAssistantId("asst_ABC123")
                .calApiKey("****1234")
                .calUserId("cal-user-1")
                .calEventId("cal-event-1")
                .updatedAt(LocalDateTime.now())
                .build();
    }

    @Nested
    @DisplayName("GET /operator/clients")
    class ListClientsEndpoint {

        @Test
        @DisplayName("ADMIN role should access list of clients")
        @WithMockUser(roles = "ADMIN")
        void adminShouldAccessListOfClients() throws Exception {
            List<ClientSummaryResponse> clients = Arrays.asList(
                    ClientSummaryResponse.builder()
                            .clientId(1L)
                            .name("Client One")
                            .email("client1@example.com")
                            .hasConfig(true)
                            .creditBalance(new BigDecimal("100.00"))
                            .build(),
                    ClientSummaryResponse.builder()
                            .clientId(2L)
                            .name("Client Two")
                            .email("client2@example.com")
                            .hasConfig(false)
                            .creditBalance(BigDecimal.ZERO)
                            .build()
            );

            when(clientConfigService.listClientsWithConfigStatus()).thenReturn(clients);

            mockMvc.perform(get("/operator/clients"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(2))
                    .andExpect(jsonPath("$[0].clientId").value(1))
                    .andExpect(jsonPath("$[0].hasConfig").value(true))
                    .andExpect(jsonPath("$[0].creditBalance").value(100.00))
                    .andExpect(jsonPath("$[1].hasConfig").value(false));

            verify(clientConfigService).listClientsWithConfigStatus();
        }

        @Test
        @DisplayName("OPERATOR role should access list of clients")
        @WithMockUser(roles = "OPERATOR")
        void operatorShouldAccessListOfClients() throws Exception {
            when(clientConfigService.listClientsWithConfigStatus()).thenReturn(List.of());

            mockMvc.perform(get("/operator/clients"))
                    .andExpect(status().isOk());

            verify(clientConfigService).listClientsWithConfigStatus();
        }

        @Test
        @DisplayName("CLIENT role should receive 403 Forbidden")
        @WithMockUser(roles = "CLIENT")
        void clientRoleShouldBeForbidden() throws Exception {
            mockMvc.perform(get("/operator/clients"))
                    .andExpect(status().isForbidden());

            verify(clientConfigService, never()).listClientsWithConfigStatus();
        }

        @Test
        @DisplayName("Unauthenticated request should receive 401 Unauthorized")
        void unauthenticatedShouldBeUnauthorized() throws Exception {
            mockMvc.perform(get("/operator/clients"))
                    .andExpect(status().isUnauthorized());

            verify(clientConfigService, never()).listClientsWithConfigStatus();
        }
    }

    @Nested
    @DisplayName("POST /operator/clients/{clientId}/config")
    class CreateConfigEndpoint {

        @Test
        @DisplayName("ADMIN should create client config")
        @WithMockUser(roles = "ADMIN")
        void adminShouldCreateConfig() throws Exception {
            ClientConfigRequest request = new ClientConfigRequest();
            request.setTwilioAccountSid("AC123456789");
            request.setTwilioAuthToken("auth-token-12345678");
            request.setOpenAiApiKey("sk-test-api-key-12345");

            ClientConfigResponse response = createMockConfigResponse();
            when(clientConfigService.createOrUpdateConfig(eq(100L), any(ClientConfigRequest.class)))
                    .thenReturn(response);

            mockMvc.perform(post("/operator/clients/100/config")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.clientId").value(100))
                    .andExpect(jsonPath("$.twilioAuthToken").value("****5678"));

            verify(clientConfigService).createOrUpdateConfig(eq(100L), any(ClientConfigRequest.class));
        }

        @Test
        @DisplayName("OPERATOR should create client config")
        @WithMockUser(roles = "OPERATOR")
        void operatorShouldCreateConfig() throws Exception {
            ClientConfigRequest request = new ClientConfigRequest();
            request.setTwilioAccountSid("AC123456789");

            ClientConfigResponse response = createMockConfigResponse();
            when(clientConfigService.createOrUpdateConfig(eq(100L), any(ClientConfigRequest.class)))
                    .thenReturn(response);

            mockMvc.perform(post("/operator/clients/100/config")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk());

            verify(clientConfigService).createOrUpdateConfig(eq(100L), any(ClientConfigRequest.class));
        }

        @Test
        @DisplayName("CLIENT role should receive 403 Forbidden")
        @WithMockUser(roles = "CLIENT")
        void clientRoleShouldBeForbidden() throws Exception {
            ClientConfigRequest request = new ClientConfigRequest();

            mockMvc.perform(post("/operator/clients/100/config")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isForbidden());

            verify(clientConfigService, never()).createOrUpdateConfig(any(), any());
        }

        @Test
        @DisplayName("Should return 404 when client not found")
        @WithMockUser(roles = "ADMIN")
        void shouldReturn404WhenClientNotFound() throws Exception {
            ClientConfigRequest request = new ClientConfigRequest();

            when(clientConfigService.createOrUpdateConfig(eq(999L), any(ClientConfigRequest.class)))
                    .thenThrow(new ClientConfigService.ClientNotFoundException("Client not found with id: 999"));

            mockMvc.perform(post("/operator/clients/999/config")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.status").value(404));
        }
    }

    @Nested
    @DisplayName("GET /operator/clients/{clientId}/config")
    class GetConfigEndpoint {

        @Test
        @DisplayName("ADMIN should get client config with masked sensitive fields")
        @WithMockUser(roles = "ADMIN")
        void adminShouldGetConfigWithMaskedFields() throws Exception {
            ClientConfigResponse response = createMockConfigResponse();
            when(clientConfigService.getConfig(100L)).thenReturn(response);

            mockMvc.perform(get("/operator/clients/100/config"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.clientId").value(100))
                    .andExpect(jsonPath("$.twilioAccountSid").value("AC123456789"))
                    .andExpect(jsonPath("$.twilioAuthToken").value("****5678"))
                    .andExpect(jsonPath("$.elevenLabsApiKey").value("****abcd"))
                    .andExpect(jsonPath("$.openAiApiKey").value("****wxyz"))
                    .andExpect(jsonPath("$.calApiKey").value("****1234"));

            verify(clientConfigService).getConfig(100L);
        }

        @Test
        @DisplayName("OPERATOR should get client config")
        @WithMockUser(roles = "OPERATOR")
        void operatorShouldGetConfig() throws Exception {
            ClientConfigResponse response = createMockConfigResponse();
            when(clientConfigService.getConfig(100L)).thenReturn(response);

            mockMvc.perform(get("/operator/clients/100/config"))
                    .andExpect(status().isOk());

            verify(clientConfigService).getConfig(100L);
        }

        @Test
        @DisplayName("CLIENT role should receive 403 Forbidden")
        @WithMockUser(roles = "CLIENT")
        void clientRoleShouldBeForbidden() throws Exception {
            mockMvc.perform(get("/operator/clients/100/config"))
                    .andExpect(status().isForbidden());

            verify(clientConfigService, never()).getConfig(any());
        }

        @Test
        @DisplayName("Should return 404 when config not found")
        @WithMockUser(roles = "ADMIN")
        void shouldReturn404WhenConfigNotFound() throws Exception {
            when(clientConfigService.getConfig(100L))
                    .thenThrow(new ClientConfigService.ConfigNotFoundException("Config not found for client: 100"));

            mockMvc.perform(get("/operator/clients/100/config"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.status").value(404));
        }
    }

    @Nested
    @DisplayName("PATCH /operator/clients/{clientId}/config")
    class PatchConfigEndpoint {

        @Test
        @DisplayName("ADMIN should patch client config")
        @WithMockUser(roles = "ADMIN")
        void adminShouldPatchConfig() throws Exception {
            ClientConfigRequest request = new ClientConfigRequest();
            request.setTwilioCallNumber("+15551112222");

            ClientConfigResponse response = createMockConfigResponse();
            when(clientConfigService.patchConfig(eq(100L), any(ClientConfigRequest.class)))
                    .thenReturn(response);

            mockMvc.perform(patch("/operator/clients/100/config")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk());

            verify(clientConfigService).patchConfig(eq(100L), any(ClientConfigRequest.class));
        }

        @Test
        @DisplayName("OPERATOR should patch client config")
        @WithMockUser(roles = "OPERATOR")
        void operatorShouldPatchConfig() throws Exception {
            ClientConfigRequest request = new ClientConfigRequest();
            request.setElevenLabsAgentId("new-agent-id");

            ClientConfigResponse response = createMockConfigResponse();
            when(clientConfigService.patchConfig(eq(100L), any(ClientConfigRequest.class)))
                    .thenReturn(response);

            mockMvc.perform(patch("/operator/clients/100/config")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk());

            verify(clientConfigService).patchConfig(eq(100L), any(ClientConfigRequest.class));
        }

        @Test
        @DisplayName("CLIENT role should receive 403 Forbidden")
        @WithMockUser(roles = "CLIENT")
        void clientRoleShouldBeForbidden() throws Exception {
            ClientConfigRequest request = new ClientConfigRequest();

            mockMvc.perform(patch("/operator/clients/100/config")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isForbidden());

            verify(clientConfigService, never()).patchConfig(any(), any());
        }

        @Test
        @DisplayName("Should return 404 when config not found")
        @WithMockUser(roles = "ADMIN")
        void shouldReturn404WhenConfigNotFound() throws Exception {
            ClientConfigRequest request = new ClientConfigRequest();

            when(clientConfigService.patchConfig(eq(100L), any(ClientConfigRequest.class)))
                    .thenThrow(new ClientConfigService.ConfigNotFoundException("Config not found for client: 100"));

            mockMvc.perform(patch("/operator/clients/100/config")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.status").value(404));
        }
    }

    @Nested
    @DisplayName("Security - CLIENT role denied on all /operator/** endpoints")
    class ClientRoleDeniedOnAllEndpoints {

        @Test
        @DisplayName("CLIENT cannot access GET /operator/clients")
        @WithMockUser(roles = "CLIENT")
        void clientCannotListClients() throws Exception {
            mockMvc.perform(get("/operator/clients"))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("CLIENT cannot access POST /operator/clients/{id}/config")
        @WithMockUser(roles = "CLIENT")
        void clientCannotCreateConfig() throws Exception {
            mockMvc.perform(post("/operator/clients/1/config")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("CLIENT cannot access GET /operator/clients/{id}/config")
        @WithMockUser(roles = "CLIENT")
        void clientCannotGetConfig() throws Exception {
            mockMvc.perform(get("/operator/clients/1/config"))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("CLIENT cannot access PATCH /operator/clients/{id}/config")
        @WithMockUser(roles = "CLIENT")
        void clientCannotPatchConfig() throws Exception {
            mockMvc.perform(patch("/operator/clients/1/config")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isForbidden());
        }
    }
}
