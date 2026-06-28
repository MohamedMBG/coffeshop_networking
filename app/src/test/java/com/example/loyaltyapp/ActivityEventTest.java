package com.example.loyaltyapp;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.mockito.Mockito.when;

import com.example.loyaltyapp.models.ActivityEvent;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentSnapshot;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Date;

/**
 * Validates the normalized ActivityEvent.fromDoc parsing, including the
 * legacy-field fallback path so that documents written under the old schema
 * (`points`, `storeName`, `voucherId`, etc.) still bind correctly.
 */
public class ActivityEventTest {

    @Mock
    DocumentSnapshot mockDocumentSnapshot;

    private AutoCloseable mocks;

    @Before
    public void setUp() {
        mocks = MockitoAnnotations.openMocks(this);
    }

    /**
     * Document uses the normalized schema. Verifies straight-through binding.
     */
    @Test
    public void testFromDocSuccess_NormalizedSchema() {
        Timestamp testTimestamp = new Timestamp(new Date());

        when(mockDocumentSnapshot.getId()).thenReturn("doc_123");
        when(mockDocumentSnapshot.getString("type")).thenReturn(ActivityEvent.TYPE_EARN);
        when(mockDocumentSnapshot.getLong("delta")).thenReturn(25L);
        when(mockDocumentSnapshot.getString("desc")).thenReturn("Coffee Shop");
        when(mockDocumentSnapshot.getString("refId")).thenReturn("voucher_abc");
        when(mockDocumentSnapshot.getTimestamp("ts")).thenReturn(testTimestamp);

        ActivityEvent event = ActivityEvent.fromDoc(mockDocumentSnapshot);

        assertNotNull(event);
        assertEquals("doc_123", event.id);
        assertEquals(ActivityEvent.TYPE_EARN, event.type);
        assertEquals(25, event.delta);
        assertEquals("Coffee Shop", event.desc);
        assertEquals("voucher_abc", event.refId);
        assertEquals(testTimestamp, event.ts);
    }

    /**
     * Document uses the legacy schema (type=scan, points, storeName, voucherId).
     * Verifies that fromDoc translates each legacy field to its canonical name
     * and that the "scan" alias normalizes to TYPE_EARN.
     */
    @Test
    public void testFromDocSuccess_LegacyFallback() {
        Timestamp testTimestamp = new Timestamp(new Date());

        when(mockDocumentSnapshot.getId()).thenReturn("legacy_1");
        when(mockDocumentSnapshot.getString("type")).thenReturn("scan");
        when(mockDocumentSnapshot.getLong("delta")).thenReturn(null);
        when(mockDocumentSnapshot.getLong("points")).thenReturn(15L);
        when(mockDocumentSnapshot.getString("desc")).thenReturn(null);
        when(mockDocumentSnapshot.getString("storeName")).thenReturn("Cafe");
        when(mockDocumentSnapshot.getString("refId")).thenReturn(null);
        when(mockDocumentSnapshot.getString("voucherId")).thenReturn("v1");
        when(mockDocumentSnapshot.getTimestamp("ts")).thenReturn(testTimestamp);

        ActivityEvent event = ActivityEvent.fromDoc(mockDocumentSnapshot);

        assertNotNull(event);
        assertEquals(ActivityEvent.TYPE_EARN, event.type);
        assertEquals(15, event.delta);
        assertEquals("Cafe", event.desc);
        assertEquals("v1", event.refId);
    }

    /**
     * All fields null. Confirms default values prevent NPE in adapters.
     */
    @Test
    public void testFromDocWithNullFields() {
        when(mockDocumentSnapshot.getId()).thenReturn("doc_nulls");

        ActivityEvent event = ActivityEvent.fromDoc(mockDocumentSnapshot);

        assertNotNull(event);
        assertEquals("doc_nulls", event.id);
        assertEquals("", event.type);
        assertEquals(0, event.delta);
        assertEquals("", event.desc);
        assertEquals("", event.refId);
        assertNull(event.ts);
    }

    /**
     * fromDoc swallows exceptions and returns null rather than crashing the
     * caller.
     */
    @Test
    public void testFromDocExceptionHandling() {
        when(mockDocumentSnapshot.getId()).thenThrow(new RuntimeException("boom"));

        ActivityEvent event = ActivityEvent.fromDoc(mockDocumentSnapshot);
        assertNull(event);
    }
}
