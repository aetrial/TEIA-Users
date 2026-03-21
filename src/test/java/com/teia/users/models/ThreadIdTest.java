package com.teia.users.models;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ThreadIdTest {

    @Test
    void testDefaultConstructor() {
        ThreadId threadId = new ThreadId();
        assertNull(threadId.getToPhoneNumber());
        assertNull(threadId.getFromPhoneNumber());
        assertNull(threadId.getThreadId());
    }

    @Test
    void testParameterizedConstructor() {
        ThreadId threadId = new ThreadId("+1234567890", "+0987654321", "thread-123");
        assertEquals("+1234567890", threadId.getToPhoneNumber());
        assertEquals("+0987654321", threadId.getFromPhoneNumber());
        assertEquals("thread-123", threadId.getThreadId());
    }

    @Test
    void testEquals() {
        ThreadId threadId1 = new ThreadId("+1234567890", "+0987654321", "thread-123");
        ThreadId threadId2 = new ThreadId("+1234567890", "+0987654321", "thread-123");
        ThreadId threadId3 = new ThreadId("+1111111111", "+0987654321", "thread-123");

        assertEquals(threadId1, threadId2);
        assertNotEquals(threadId1, threadId3);
    }

    @Test
    void testHashCode() {
        ThreadId threadId1 = new ThreadId("+1234567890", "+0987654321", "thread-123");
        ThreadId threadId2 = new ThreadId("+1234567890", "+0987654321", "thread-123");

        assertEquals(threadId1.hashCode(), threadId2.hashCode());
    }
}
