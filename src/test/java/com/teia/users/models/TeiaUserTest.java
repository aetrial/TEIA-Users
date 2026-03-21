package com.teia.users.models;

import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import static org.junit.jupiter.api.Assertions.*;

class TeiaUserTest {

    @Test
    void testDefaultConstructor() {
        TeiaUser user = new TeiaUser();
        assertNotNull(user.getCreateDate());
        assertNotNull(user.getUpdateDate());
    }

    @Test
    void testParameterizedConstructor() {
        ElevenLabsInfo elevenLabsInfo = new ElevenLabsInfo("agent-id", "api-key");
        TwilioInfo twilioInfo = new TwilioInfo("sid", "token", "+1111111111", "+2222222222");
        OpenAIInfo openAIInfo = new OpenAIInfo("openai-key", "assistant-key");
        CalInfo calInfo = new CalInfo("cal-api-key", "cal-user-id", "cal-event-id");

        TeiaUser user = new TeiaUser(
                "Test User",
                "test@example.com",
                "+1234567890",
                elevenLabsInfo,
                twilioInfo,
                openAIInfo,
                calInfo,
                new ArrayList<>()
        );

        assertEquals("Test User", user.getName());
        assertEquals("test@example.com", user.getEmail());
        assertEquals("+1234567890", user.getPhoneNumber());
        assertNotNull(user.getElevenLabsInfo());
        assertNotNull(user.getTwilioInfo());
        assertNotNull(user.getOpenAIInfo());
        assertNotNull(user.getCalInfo());
        assertNotNull(user.getContacts());
        assertNotNull(user.getCreateDate());
        assertNotNull(user.getUpdateDate());
    }

    @Test
    void testSettersAndGetters() {
        TeiaUser user = new TeiaUser();
        user.setName("Test User");
        user.setEmail("test@example.com");
        user.setPhoneNumber("+1234567890");

        assertEquals("Test User", user.getName());
        assertEquals("test@example.com", user.getEmail());
        assertEquals("+1234567890", user.getPhoneNumber());
    }

    @Test
    void testCalInfoRelationship() {
        TeiaUser user = new TeiaUser();
        CalInfo calInfo = new CalInfo("api-key", "user-id", "event-id");
        user.setCalInfo(calInfo);

        assertNotNull(user.getCalInfo());
        assertEquals("api-key", user.getCalInfo().getApiKey());
        assertEquals("user-id", user.getCalInfo().getUserId());
        assertEquals("event-id", user.getCalInfo().getEventId());
    }
}
