package com.teia.users.models;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TwilioInfoTest {

    @Test
    void testDefaultConstructor() {
        TwilioInfo info = new TwilioInfo();
        assertNotNull(info.getCreateDate());
        assertNotNull(info.getUpdateDate());
    }

    @Test
    void testParameterizedConstructor() {
        TwilioInfo info = new TwilioInfo("sid-123", "token-456", "+1111111111", "+2222222222");
        assertEquals("sid-123", info.getTwilioAccSID());
        assertEquals("token-456", info.getTwilioAuthToken());
        assertEquals("+1111111111", info.getTwilioPhoneNumberCalls());
        assertEquals("+2222222222", info.getTwilioPhoneNumberWhatsapp());
        assertNotNull(info.getCreateDate());
        assertNotNull(info.getUpdateDate());
    }

    @Test
    void testSettersAndGetters() {
        TwilioInfo info = new TwilioInfo();
        info.setTwilioAccSID("test-sid");
        info.setTwilioAuthToken("test-token");
        info.setTwilioPhoneNumberCalls("+3333333333");
        info.setTwilioPhoneNumberWhatsapp("+4444444444");

        assertEquals("test-sid", info.getTwilioAccSID());
        assertEquals("test-token", info.getTwilioAuthToken());
        assertEquals("+3333333333", info.getTwilioPhoneNumberCalls());
        assertEquals("+4444444444", info.getTwilioPhoneNumberWhatsapp());
    }
}
