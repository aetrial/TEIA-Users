package com.teia.users.controllers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.teia.users.dto.AgentPromptRequest;
import com.teia.users.dto.AgentPromptResponse;
import com.teia.users.dto.AgentPromptSummaryResponse;
import com.teia.users.models.TeiaUser;
import com.teia.users.models.enums.Role;
import com.teia.users.security.JwtService;
import com.teia.users.services.AgentPromptService;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for AgentPromptController.
 * Tests prompt versioning, activation, and client isolation.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AgentPromptControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JwtService jwtService;

    @MockBean
    private AgentPromptService agentPromptService;

    @Value("${internal.secret}")
    private String internalSecret;

    private static final Long CLIENT_A_ID = 1L;
    private static final Long CLIENT_B_ID = 2L;

    private String tokenClientA;
    private String tokenClientB;

    @BeforeEach
    void setUp() {
        // Generate tokens for test clients
        tokenClientA = generateToken(CLIENT_A_ID, "clienta@test.com", Role.CLIENT);
        tokenClientB = generateToken(CLIENT_B_ID, "clientb@test.com", Role.CLIENT);
    }

    private TeiaUser createTestUser(Long id, String email, Role role) {
        TeiaUser user = new TeiaUser();
        user.setId(id);
        user.setEmail(email);
        user.setRole(role);
        user.setName("Test User " + id);
        user.setPhoneNumber("+1234567890");
        return user;
    }

    private String generateToken(Long userId, String email, Role role) {
        TeiaUser user = createTestUser(userId, email, role);
        return jwtService.generateAccessToken(user);
    }

    /**
     * Computes HMAC-SHA256 signature for internal authentication.
     * For GET requests, use empty string as body.
     */
    private String computeSignature(String body, String timestamp) throws Exception {
        String signatureInput = body + timestamp;
        Mac mac = Mac.getInstance("HmacSHA256");
        SecretKeySpec keySpec = new SecretKeySpec(internalSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        mac.init(keySpec);
        byte[] hmacBytes = mac.doFinal(signatureInput.getBytes(StandardCharsets.UTF_8));

        StringBuilder sb = new StringBuilder(hmacBytes.length * 2);
        for (byte b : hmacBytes) {
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1) sb.append('0');
            sb.append(hex);
        }
        return sb.toString();
    }

    @Nested
    @DisplayName("GET /prompts/active Tests")
    class GetActivePromptTests {

        @Test
        @DisplayName("AC-6: Should return 404 when no active prompt exists")
        void shouldReturn404WhenNoActivePrompt() throws Exception {
            when(agentPromptService.getActivePrompt(CLIENT_A_ID)).thenReturn(Optional.empty());

            mockMvc.perform(get("/prompts/active")
                            .header("Authorization", "Bearer " + tokenClientA))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.error").value("No active prompt found"));
        }

        @Test
        @DisplayName("Should return active prompt when one exists")
        void shouldReturnActivePrompt() throws Exception {
            AgentPromptResponse response = AgentPromptResponse.builder()
                    .id(1L).version(1).content("You are a helpful assistant.")
                    .isActive(true).createdAt(LocalDateTime.now()).build();
            when(agentPromptService.getActivePrompt(CLIENT_A_ID)).thenReturn(Optional.of(response));

            mockMvc.perform(get("/prompts/active")
                            .header("Authorization", "Bearer " + tokenClientA))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.version").value(1))
                    .andExpect(jsonPath("$.content").value("You are a helpful assistant."))
                    .andExpect(jsonPath("$.isActive").value(true));
        }

        @Test
        @DisplayName("AC-4: Client A cannot see Client B's active prompt (isolation)")
        void shouldIsolateClientPrompts() throws Exception {
            // Client A has no active prompt
            when(agentPromptService.getActivePrompt(CLIENT_A_ID)).thenReturn(Optional.empty());

            mockMvc.perform(get("/prompts/active")
                            .header("Authorization", "Bearer " + tokenClientA))
                    .andExpect(status().isNotFound());

            // Verify service was called with clientA's ID, not clientB's
            verify(agentPromptService).getActivePrompt(CLIENT_A_ID);
            verify(agentPromptService, never()).getActivePrompt(CLIENT_B_ID);
        }

        @Test
        @DisplayName("Should return 401 for unauthenticated request")
        void shouldReject401ForUnauthenticated() throws Exception {
            mockMvc.perform(get("/prompts/active"))
                    .andExpect(status().isUnauthorized());
        }
    }

    @Nested
    @DisplayName("GET /prompts Tests")
    class ListPromptsTests {

        @Test
        @DisplayName("Should return empty list when no prompts exist")
        void shouldReturnEmptyList() throws Exception {
            when(agentPromptService.listPrompts(CLIENT_A_ID)).thenReturn(Collections.emptyList());

            mockMvc.perform(get("/prompts")
                            .header("Authorization", "Bearer " + tokenClientA))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$").isArray())
                    .andExpect(jsonPath("$").isEmpty());
        }

        @Test
        @DisplayName("Should return prompts in version descending order")
        void shouldReturnPromptsInVersionDescOrder() throws Exception {
            List<AgentPromptSummaryResponse> summaries = List.of(
                    AgentPromptSummaryResponse.fromContent(3L, 3, "V3", true, LocalDateTime.now()),
                    AgentPromptSummaryResponse.fromContent(2L, 2, "V2", false, LocalDateTime.now()),
                    AgentPromptSummaryResponse.fromContent(1L, 1, "V1", false, LocalDateTime.now())
            );
            when(agentPromptService.listPrompts(CLIENT_A_ID)).thenReturn(summaries);

            mockMvc.perform(get("/prompts")
                            .header("Authorization", "Bearer " + tokenClientA))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(3)))
                    .andExpect(jsonPath("$[0].version").value(3))
                    .andExpect(jsonPath("$[1].version").value(2))
                    .andExpect(jsonPath("$[2].version").value(1));
        }

        @Test
        @DisplayName("Should truncate content preview to 100 chars")
        void shouldTruncateContentPreview() throws Exception {
            String longContent = "A".repeat(150);
            List<AgentPromptSummaryResponse> summaries = List.of(
                    AgentPromptSummaryResponse.fromContent(1L, 1, longContent, true, LocalDateTime.now())
            );
            when(agentPromptService.listPrompts(CLIENT_A_ID)).thenReturn(summaries);

            mockMvc.perform(get("/prompts")
                            .header("Authorization", "Bearer " + tokenClientA))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].contentPreview", hasLength(103))) // 100 chars + "..."
                    .andExpect(jsonPath("$[0].contentPreview", org.hamcrest.Matchers.endsWith("...")));
        }

        @Test
        @DisplayName("AC-4: Should only return prompts for authenticated client")
        void shouldIsolateClientPromptLists() throws Exception {
            List<AgentPromptSummaryResponse> summaries = List.of(
                    AgentPromptSummaryResponse.fromContent(1L, 1, "A's prompt", true, LocalDateTime.now())
            );
            when(agentPromptService.listPrompts(CLIENT_A_ID)).thenReturn(summaries);

            mockMvc.perform(get("/prompts")
                            .header("Authorization", "Bearer " + tokenClientA))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(1)))
                    .andExpect(jsonPath("$[0].contentPreview").value("A's prompt"));

            verify(agentPromptService).listPrompts(CLIENT_A_ID);
            verify(agentPromptService, never()).listPrompts(CLIENT_B_ID);
        }
    }

    @Nested
    @DisplayName("POST /prompts Tests")
    class CreatePromptTests {

        @Test
        @DisplayName("AC-1: First prompt should have version 1")
        void shouldStartWithVersion1() throws Exception {
            AgentPromptResponse response = AgentPromptResponse.builder()
                    .id(1L).version(1).content("First prompt")
                    .isActive(true).createdAt(LocalDateTime.now()).build();
            when(agentPromptService.createPrompt(eq(CLIENT_A_ID), any())).thenReturn(response);

            AgentPromptRequest request = new AgentPromptRequest();
            request.setContent("First prompt");

            mockMvc.perform(post("/prompts")
                            .header("Authorization", "Bearer " + tokenClientA)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.version").value(1))
                    .andExpect(jsonPath("$.isActive").value(true));
        }

        @Test
        @DisplayName("AC-1: Version should increment with each POST")
        void shouldIncrementVersion() throws Exception {
            AgentPromptResponse response = AgentPromptResponse.builder()
                    .id(2L).version(3).content("Third prompt")
                    .isActive(true).createdAt(LocalDateTime.now()).build();
            when(agentPromptService.createPrompt(eq(CLIENT_A_ID), any())).thenReturn(response);

            AgentPromptRequest request = new AgentPromptRequest();
            request.setContent("Third prompt");

            mockMvc.perform(post("/prompts")
                            .header("Authorization", "Bearer " + tokenClientA)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.version").value(3));
        }

        @Test
        @DisplayName("AC-5: Should return 400 for empty content")
        void shouldRejectEmptyContent() throws Exception {
            AgentPromptRequest request = new AgentPromptRequest();
            request.setContent("");

            mockMvc.perform(post("/prompts")
                            .header("Authorization", "Bearer " + tokenClientA)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("AC-5: Should return 400 for blank content")
        void shouldRejectBlankContent() throws Exception {
            AgentPromptRequest request = new AgentPromptRequest();
            request.setContent("   ");

            mockMvc.perform(post("/prompts")
                            .header("Authorization", "Bearer " + tokenClientA)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("AC-5: Should return 400 for content over 4000 chars")
        void shouldRejectContentOver4000Chars() throws Exception {
            AgentPromptRequest request = new AgentPromptRequest();
            request.setContent("A".repeat(4001));

            mockMvc.perform(post("/prompts")
                            .header("Authorization", "Bearer " + tokenClientA)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("Should accept content at exactly 4000 chars")
        void shouldAcceptContentAt4000Chars() throws Exception {
            String content = "A".repeat(4000);
            AgentPromptResponse response = AgentPromptResponse.builder()
                    .id(1L).version(1).content(content)
                    .isActive(true).createdAt(LocalDateTime.now()).build();
            when(agentPromptService.createPrompt(eq(CLIENT_A_ID), any())).thenReturn(response);

            AgentPromptRequest request = new AgentPromptRequest();
            request.setContent(content);

            mockMvc.perform(post("/prompts")
                            .header("Authorization", "Bearer " + tokenClientA)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.version").value(1));
        }
    }

    @Nested
    @DisplayName("POST /prompts/{id}/activate Tests")
    class ActivatePromptTests {

        @Test
        @DisplayName("AC-2: Should rollback to previous version")
        void shouldRollbackToPreviousVersion() throws Exception {
            AgentPromptResponse response = AgentPromptResponse.builder()
                    .id(1L).version(1).content("V1")
                    .isActive(true).createdAt(LocalDateTime.now()).build();
            when(agentPromptService.activatePrompt(CLIENT_A_ID, 1L)).thenReturn(Optional.of(response));

            mockMvc.perform(post("/prompts/1/activate")
                            .header("Authorization", "Bearer " + tokenClientA))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.version").value(1))
                    .andExpect(jsonPath("$.isActive").value(true));
        }

        @Test
        @DisplayName("Should return 404 for unknown prompt ID")
        void shouldReturn404ForUnknownId() throws Exception {
            when(agentPromptService.activatePrompt(CLIENT_A_ID, 99999L)).thenReturn(Optional.empty());

            mockMvc.perform(post("/prompts/99999/activate")
                            .header("Authorization", "Bearer " + tokenClientA))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.error").value("Prompt not found"));
        }

        @Test
        @DisplayName("AC-4: Should return 404 when trying to activate other client's prompt")
        void shouldRejectOtherClientPromptActivation() throws Exception {
            // Client A tries to activate a prompt that belongs to client B
            // Service returns empty because the prompt doesn't belong to client A
            when(agentPromptService.activatePrompt(CLIENT_A_ID, 100L)).thenReturn(Optional.empty());

            mockMvc.perform(post("/prompts/100/activate")
                            .header("Authorization", "Bearer " + tokenClientA))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("Should be idempotent when re-activating already active prompt")
        void shouldBeIdempotentForActivePrompt() throws Exception {
            AgentPromptResponse response = AgentPromptResponse.builder()
                    .id(1L).version(1).content("Active")
                    .isActive(true).createdAt(LocalDateTime.now()).build();
            when(agentPromptService.activatePrompt(CLIENT_A_ID, 1L)).thenReturn(Optional.of(response));

            mockMvc.perform(post("/prompts/1/activate")
                            .header("Authorization", "Bearer " + tokenClientA))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.isActive").value(true));
        }
    }

    @Nested
    @DisplayName("GET /internal/clients/{clientId}/prompt Tests")
    class InternalPromptEndpointTests {

        @Test
        @DisplayName("AC-3: Should return active prompt content")
        void shouldReturnActivePromptContent() throws Exception {
            when(agentPromptService.getActivePromptContent(CLIENT_A_ID)).thenReturn(Optional.of("System prompt content"));

            String path = "/internal/clients/" + CLIENT_A_ID + "/prompt";
            String timestamp = String.valueOf(System.currentTimeMillis());
            String signature = computeSignature("", timestamp); // Empty body for GET

            mockMvc.perform(get(path)
                            .header("X-Internal-Key", signature)
                            .header("X-Timestamp", timestamp)
                            .header("X-Client-ID", CLIENT_A_ID.toString()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content").value("System prompt content"));
        }

        @Test
        @DisplayName("AC-6: Should return 404 when no active prompt")
        void shouldReturn404WhenNoActivePrompt() throws Exception {
            when(agentPromptService.getActivePromptContent(CLIENT_A_ID)).thenReturn(Optional.empty());

            String path = "/internal/clients/" + CLIENT_A_ID + "/prompt";
            String timestamp = String.valueOf(System.currentTimeMillis());
            String signature = computeSignature("", timestamp); // Empty body for GET

            mockMvc.perform(get(path)
                            .header("X-Internal-Key", signature)
                            .header("X-Timestamp", timestamp)
                            .header("X-Client-ID", CLIENT_A_ID.toString()))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.error").value("No active prompt found"));
        }

        @Test
        @DisplayName("Should return 401 when no HMAC headers")
        void shouldReject401WithoutHeaders() throws Exception {
            mockMvc.perform(get("/internal/clients/1/prompt"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("Should return 401 for invalid signature")
        void shouldReject401ForBadSignature() throws Exception {
            String path = "/internal/clients/" + CLIENT_A_ID + "/prompt";
            String timestamp = String.valueOf(System.currentTimeMillis());

            mockMvc.perform(get(path)
                            .header("X-Internal-Key", "invalid-signature")
                            .header("X-Timestamp", timestamp)
                            .header("X-Client-ID", CLIENT_A_ID.toString()))
                    .andExpect(status().isUnauthorized());
        }
    }
}
