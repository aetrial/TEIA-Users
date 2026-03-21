package com.teia.users.models;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ThreadTest {

    @Test
    void testDefaultConstructor() {
        Thread thread = new Thread();
        assertNull(thread.getId());
    }

    @Test
    void testParameterizedConstructor() {
        Thread thread = new Thread("+1234567890", "+0987654321", "thread-123");
        assertNotNull(thread.getId());
        assertEquals("+1234567890", thread.getToPhoneNumber());
        assertEquals("+0987654321", thread.getFromPhoneNumber());
        assertEquals("thread-123", thread.getThreadId());
    }

    @Test
    void testConstructorWithThreadId() {
        ThreadId threadId = new ThreadId("+1234567890", "+0987654321", "thread-123");
        Thread thread = new Thread(threadId);
        assertEquals(threadId, thread.getId());
    }

    @Test
    void testHelperMethods() {
        Thread thread = new Thread("+1234567890", "+0987654321", "thread-123");
        assertEquals("+1234567890", thread.getToPhoneNumber());
        assertEquals("+0987654321", thread.getFromPhoneNumber());
        assertEquals("thread-123", thread.getThreadId());
    }

    @Test
    void testHelperMethodsWithNullId() {
        Thread thread = new Thread();
        assertNull(thread.getToPhoneNumber());
        assertNull(thread.getFromPhoneNumber());
        assertNull(thread.getThreadId());
    }
}
