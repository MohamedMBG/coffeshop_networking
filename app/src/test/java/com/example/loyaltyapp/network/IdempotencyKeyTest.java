package com.example.loyaltyapp.network;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

import org.junit.Test;

import java.util.UUID;

public class IdempotencyKeyTest {

    @Test
    public void header_isCanonicalName() {
        assertEquals("Idempotency-Key", IdempotencyKey.HEADER);
    }

    @Test
    public void generate_returnsParseableUuid() {
        String key = IdempotencyKey.generate();
        // Round-trips through UUID.fromString => it's a valid UUID string.
        assertEquals(key, UUID.fromString(key).toString());
    }

    @Test
    public void generate_isUniquePerCall() {
        assertNotEquals(IdempotencyKey.generate(), IdempotencyKey.generate());
    }
}
