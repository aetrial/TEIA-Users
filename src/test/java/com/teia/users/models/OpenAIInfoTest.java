package com.teia.users.models;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class OpenAIInfoTest {

    @Test
    void testDefaultConstructor() {
        OpenAIInfo info = new OpenAIInfo();
        assertNotNull(info.getCreateDate());
        assertNotNull(info.getUpdateDate());
    }

    @Test
    void testParameterizedConstructor() {
        OpenAIInfo info = new OpenAIInfo("api-key-123", "assistant-key-456");
        assertEquals("api-key-123", info.getOpenAIApiKey());
        assertEquals("assistant-key-456", info.getOpenAIAssistantKey());
        assertNotNull(info.getCreateDate());
        assertNotNull(info.getUpdateDate());
    }

    @Test
    void testSettersAndGetters() {
        OpenAIInfo info = new OpenAIInfo();
        info.setOpenAIApiKey("test-api-key");
        info.setOpenAIAssistantKey("test-assistant-key");

        assertEquals("test-api-key", info.getOpenAIApiKey());
        assertEquals("test-assistant-key", info.getOpenAIAssistantKey());
    }
}
