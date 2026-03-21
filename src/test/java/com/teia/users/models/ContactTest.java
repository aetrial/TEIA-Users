package com.teia.users.models;

import com.teia.users.models.enums.ChannelType;
import com.teia.users.models.enums.ContactStates;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ContactTest {

    @Test
    void testDefaultConstructor() {
        Contact contact = new Contact();
        assertNotNull(contact.getCreateDate());
        assertNotNull(contact.getUpdateDate());
    }

    @Test
    void testParameterizedConstructor() {
        TeiaUser user = new TeiaUser();
        Contact contact = new Contact(
                "+1234567890",
                ChannelType.CALL,
                ContactStates.ACTIVE,
                "John Doe",
                user
        );

        assertEquals("+1234567890", contact.getPhoneNumber());
        assertEquals(ChannelType.CALL, contact.getChannel());
        assertEquals(ContactStates.ACTIVE, contact.getStatus());
        assertEquals("John Doe", contact.getName());
        assertEquals(user, contact.getTeiaUser());
        assertNotNull(contact.getCreateDate());
        assertNotNull(contact.getUpdateDate());
    }

    @Test
    void testSettersAndGetters() {
        Contact contact = new Contact();
        contact.setPhoneNumber("+0987654321");
        contact.setChannel(ChannelType.WHATSAPP);
        contact.setStatus(ContactStates.INACTIVE);
        contact.setName("Jane Doe");

        assertEquals("+0987654321", contact.getPhoneNumber());
        assertEquals(ChannelType.WHATSAPP, contact.getChannel());
        assertEquals(ContactStates.INACTIVE, contact.getStatus());
        assertEquals("Jane Doe", contact.getName());
    }
}
