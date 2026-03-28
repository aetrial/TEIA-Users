package com.teia.users.services;

import com.teia.users.dto.AgentPromptRequest;
import com.teia.users.dto.AgentPromptResponse;
import com.teia.users.dto.AgentPromptSummaryResponse;
import com.teia.users.models.AgentPrompt;
import com.teia.users.repositories.AgentPromptRepository;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for AgentPromptService.
 */
@ExtendWith(MockitoExtension.class)
class AgentPromptServiceTest {

    @Mock
    private AgentPromptRepository agentPromptRepository;

    @InjectMocks
    private AgentPromptService agentPromptService;

    private static final Long CLIENT_ID = 1L;

    @Nested
    @DisplayName("getActivePrompt Tests")
    class GetActivePromptTests {

        @Test
        @DisplayName("Should return empty when no active prompt")
        void shouldReturnEmptyWhenNoActivePrompt() {
            when(agentPromptRepository.findActiveByClientId(CLIENT_ID)).thenReturn(Optional.empty());

            Optional<AgentPromptResponse> result = agentPromptService.getActivePrompt(CLIENT_ID);

            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("Should return response when active prompt exists")
        void shouldReturnResponseWhenActivePromptExists() {
            AgentPrompt prompt = AgentPrompt.builder()
                    .id(1L).clientId(CLIENT_ID).content("Test").version(1).isActive(true).createdAt(LocalDateTime.now()).build();
            when(agentPromptRepository.findActiveByClientId(CLIENT_ID)).thenReturn(Optional.of(prompt));

            Optional<AgentPromptResponse> result = agentPromptService.getActivePrompt(CLIENT_ID);

            assertTrue(result.isPresent());
            assertEquals(1, result.get().getVersion());
            assertEquals("Test", result.get().getContent());
            assertTrue(result.get().getIsActive());
        }
    }

    @Nested
    @DisplayName("getActivePromptContent Tests")
    class GetActivePromptContentTests {

        @Test
        @DisplayName("Should return content string when active prompt exists")
        void shouldReturnContentString() {
            AgentPrompt prompt = AgentPrompt.builder()
                    .id(1L).clientId(CLIENT_ID).content("System prompt content").version(1).isActive(true).createdAt(LocalDateTime.now()).build();
            when(agentPromptRepository.findActiveByClientId(CLIENT_ID)).thenReturn(Optional.of(prompt));

            Optional<String> result = agentPromptService.getActivePromptContent(CLIENT_ID);

            assertTrue(result.isPresent());
            assertEquals("System prompt content", result.get());
        }

        @Test
        @DisplayName("Should return empty when no active prompt")
        void shouldReturnEmptyWhenNoActivePrompt() {
            when(agentPromptRepository.findActiveByClientId(CLIENT_ID)).thenReturn(Optional.empty());

            Optional<String> result = agentPromptService.getActivePromptContent(CLIENT_ID);

            assertTrue(result.isEmpty());
        }
    }

    @Nested
    @DisplayName("listPrompts Tests")
    class ListPromptsTests {

        @Test
        @DisplayName("Should return empty list when no prompts")
        void shouldReturnEmptyList() {
            when(agentPromptRepository.findAllByClientIdOrderByVersionDesc(CLIENT_ID)).thenReturn(Collections.emptyList());

            List<AgentPromptSummaryResponse> result = agentPromptService.listPrompts(CLIENT_ID);

            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("Should return summaries in order")
        void shouldReturnSummariesInOrder() {
            List<AgentPrompt> prompts = List.of(
                    AgentPrompt.builder().id(2L).clientId(CLIENT_ID).content("V2").version(2).isActive(true).createdAt(LocalDateTime.now()).build(),
                    AgentPrompt.builder().id(1L).clientId(CLIENT_ID).content("V1").version(1).isActive(false).createdAt(LocalDateTime.now()).build()
            );
            when(agentPromptRepository.findAllByClientIdOrderByVersionDesc(CLIENT_ID)).thenReturn(prompts);

            List<AgentPromptSummaryResponse> result = agentPromptService.listPrompts(CLIENT_ID);

            assertEquals(2, result.size());
            assertEquals(2, result.get(0).getVersion());
            assertEquals(1, result.get(1).getVersion());
        }

        @Test
        @DisplayName("Should truncate preview for long content")
        void shouldTruncatePreview() {
            String longContent = "A".repeat(150);
            AgentPrompt prompt = AgentPrompt.builder()
                    .id(1L).clientId(CLIENT_ID).content(longContent).version(1).isActive(true).createdAt(LocalDateTime.now()).build();
            when(agentPromptRepository.findAllByClientIdOrderByVersionDesc(CLIENT_ID)).thenReturn(List.of(prompt));

            List<AgentPromptSummaryResponse> result = agentPromptService.listPrompts(CLIENT_ID);

            assertEquals(1, result.size());
            assertEquals(103, result.get(0).getContentPreview().length()); // 100 + "..."
            assertTrue(result.get(0).getContentPreview().endsWith("..."));
        }
    }

    @Nested
    @DisplayName("createPrompt Tests")
    class CreatePromptTests {

        @Test
        @DisplayName("Should start with version 1 when no existing prompts")
        void shouldStartWithVersion1() {
            when(agentPromptRepository.findMaxVersionByClientId(CLIENT_ID)).thenReturn(0);
            when(agentPromptRepository.save(any())).thenAnswer(inv -> {
                AgentPrompt p = inv.getArgument(0);
                p.setId(1L);
                return p;
            });

            AgentPromptRequest request = new AgentPromptRequest();
            request.setContent("First prompt");

            AgentPromptResponse result = agentPromptService.createPrompt(CLIENT_ID, request);

            assertEquals(1, result.getVersion());
        }

        @Test
        @DisplayName("Should increment version")
        void shouldIncrementVersion() {
            when(agentPromptRepository.findMaxVersionByClientId(CLIENT_ID)).thenReturn(5);
            when(agentPromptRepository.save(any())).thenAnswer(inv -> {
                AgentPrompt p = inv.getArgument(0);
                p.setId(1L);
                return p;
            });

            AgentPromptRequest request = new AgentPromptRequest();
            request.setContent("New prompt");

            AgentPromptResponse result = agentPromptService.createPrompt(CLIENT_ID, request);

            assertEquals(6, result.getVersion());
        }

        @Test
        @DisplayName("Should call deactivateAll before creating")
        void shouldDeactivateAllBeforeCreating() {
            when(agentPromptRepository.findMaxVersionByClientId(CLIENT_ID)).thenReturn(0);
            when(agentPromptRepository.save(any())).thenAnswer(inv -> {
                AgentPrompt p = inv.getArgument(0);
                p.setId(1L);
                return p;
            });

            AgentPromptRequest request = new AgentPromptRequest();
            request.setContent("Prompt");

            agentPromptService.createPrompt(CLIENT_ID, request);

            verify(agentPromptRepository).deactivateAllByClientId(CLIENT_ID);
        }

        @Test
        @DisplayName("Should create prompt with isActive=true")
        void shouldCreateWithIsActiveTrue() {
            when(agentPromptRepository.findMaxVersionByClientId(CLIENT_ID)).thenReturn(0);
            ArgumentCaptor<AgentPrompt> captor = ArgumentCaptor.forClass(AgentPrompt.class);
            when(agentPromptRepository.save(captor.capture())).thenAnswer(inv -> {
                AgentPrompt p = inv.getArgument(0);
                p.setId(1L);
                return p;
            });

            AgentPromptRequest request = new AgentPromptRequest();
            request.setContent("Prompt");

            agentPromptService.createPrompt(CLIENT_ID, request);

            assertTrue(captor.getValue().getIsActive());
        }

        @Test
        @DisplayName("Should return correct response")
        void shouldReturnCorrectResponse() {
            when(agentPromptRepository.findMaxVersionByClientId(CLIENT_ID)).thenReturn(2);
            when(agentPromptRepository.save(any())).thenAnswer(inv -> {
                AgentPrompt p = inv.getArgument(0);
                p.setId(99L);
                p.setCreatedAt(LocalDateTime.now());
                return p;
            });

            AgentPromptRequest request = new AgentPromptRequest();
            request.setContent("Test content");

            AgentPromptResponse result = agentPromptService.createPrompt(CLIENT_ID, request);

            assertEquals(99L, result.getId());
            assertEquals(3, result.getVersion());
            assertEquals("Test content", result.getContent());
            assertTrue(result.getIsActive());
            assertNotNull(result.getCreatedAt());
        }
    }

    @Nested
    @DisplayName("activatePrompt Tests")
    class ActivatePromptTests {

        @Test
        @DisplayName("Should return empty when prompt not found")
        void shouldReturnEmptyWhenNotFound() {
            when(agentPromptRepository.findByIdAndClientId(99L, CLIENT_ID)).thenReturn(Optional.empty());

            Optional<AgentPromptResponse> result = agentPromptService.activatePrompt(CLIENT_ID, 99L);

            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("Should return response when found")
        void shouldReturnResponseWhenFound() {
            AgentPrompt prompt = AgentPrompt.builder()
                    .id(1L).clientId(CLIENT_ID).content("Rollback").version(1).isActive(false).createdAt(LocalDateTime.now()).build();
            when(agentPromptRepository.findByIdAndClientId(1L, CLIENT_ID)).thenReturn(Optional.of(prompt));
            when(agentPromptRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            Optional<AgentPromptResponse> result = agentPromptService.activatePrompt(CLIENT_ID, 1L);

            assertTrue(result.isPresent());
            assertEquals(1, result.get().getVersion());
            assertTrue(result.get().getIsActive());
        }

        @Test
        @DisplayName("Should call deactivateAll")
        void shouldCallDeactivateAll() {
            AgentPrompt prompt = AgentPrompt.builder()
                    .id(1L).clientId(CLIENT_ID).content("Rollback").version(1).isActive(false).createdAt(LocalDateTime.now()).build();
            when(agentPromptRepository.findByIdAndClientId(1L, CLIENT_ID)).thenReturn(Optional.of(prompt));
            when(agentPromptRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            agentPromptService.activatePrompt(CLIENT_ID, 1L);

            verify(agentPromptRepository).deactivateAllByClientId(CLIENT_ID);
        }

        @Test
        @DisplayName("Should set isActive=true on save")
        void shouldSetIsActiveTrue() {
            AgentPrompt prompt = AgentPrompt.builder()
                    .id(1L).clientId(CLIENT_ID).content("Rollback").version(1).isActive(false).createdAt(LocalDateTime.now()).build();
            when(agentPromptRepository.findByIdAndClientId(1L, CLIENT_ID)).thenReturn(Optional.of(prompt));
            ArgumentCaptor<AgentPrompt> captor = ArgumentCaptor.forClass(AgentPrompt.class);
            when(agentPromptRepository.save(captor.capture())).thenAnswer(inv -> inv.getArgument(0));

            agentPromptService.activatePrompt(CLIENT_ID, 1L);

            assertTrue(captor.getValue().getIsActive());
        }
    }
}
