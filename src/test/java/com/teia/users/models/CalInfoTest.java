package com.teia.users.models;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CalInfoTest {

    @Test
    void testDefaultConstructor() {
        CalInfo calInfo = new CalInfo();
        assertNotNull(calInfo.getCreateDate());
        assertNotNull(calInfo.getUpdateDate());
    }

    @Test
    void testParameterizedConstructor() {
        CalInfo calInfo = new CalInfo("api-key-123", "user-456", "event-789");
        assertEquals("api-key-123", calInfo.getApiKey());
        assertEquals("user-456", calInfo.getUserId());
        assertEquals("event-789", calInfo.getEventId());
        assertNotNull(calInfo.getCreateDate());
        assertNotNull(calInfo.getUpdateDate());
    }

    @Test
    void testSettersAndGetters() {
        CalInfo calInfo = new CalInfo();
        calInfo.setApiKey("test-api-key");
        calInfo.setUserId("test-user-id");
        calInfo.setEventId("test-event-id");

        assertEquals("test-api-key", calInfo.getApiKey());
        assertEquals("test-user-id", calInfo.getUserId());
        assertEquals("test-event-id", calInfo.getEventId());
    }
}
