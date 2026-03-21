package com.teia.users.models;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ElevenLabsInfoTest {

    @Test
    void testDefaultConstructor() {
        ElevenLabsInfo info = new ElevenLabsInfo();
        assertNotNull(info.getCreateDate());
        assertNotNull(info.getUpdateDate());
    }

    @Test
    void testParameterizedConstructor() {
        ElevenLabsInfo info = new ElevenLabsInfo("agent-id-123", "api-key-456");
        assertEquals("agent-id-123", info.getElevenLabsAgentId());
        assertEquals("api-key-456", info.getElevenLabsApiKey());
        assertNotNull(info.getCreateDate());
        assertNotNull(info.getUpdateDate());
    }

    @Test
    void testSettersAndGetters() {
        ElevenLabsInfo info = new ElevenLabsInfo();
        info.setElevenLabsAgentId("test-agent-id");
        info.setElevenLabsApiKey("test-api-key");

        assertEquals("test-agent-id", info.getElevenLabsAgentId());
        assertEquals("test-api-key", info.getElevenLabsApiKey());
    }
}
