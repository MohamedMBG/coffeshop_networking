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
 * Validates ActivityEvent.fromDoc parsing against the canonical backend schema
 * (pointsDelta / createdAt / refId / balanceAfter) and the legacy-field
 * fallback path so documents written under the old app schema still bind.
 */
public class ActivityEventTest {

    @Mock
    DocumentSnapshot mockDocumentSnapshot;

    private AutoCloseable mocks;

    @Before
    public void setUp() {
        mocks = MockitoAnnotations.openMocks(this);
    }

    /** Document uses the canonical backend schema. */
    @Test
    public void testFromDocSuccess_BackendSchema() {
        Timestamp testTimestamp = new Timestamp(new Date());

        when(mockDocumentSnapshot.getId()).thenReturn("doc_123");
        when(mockDocumentSnapshot.getString("type")).thenReturn(ActivityEvent.TYPE_EARN);
        when(mockDocumentSnapshot.getLong("pointsDelta")).thenReturn(25L);
        when(mockDocumentSnapshot.getString("desc")).thenReturn("Coffee Shop");
        when(mockDocumentSnapshot.getString("refId")).thenReturn("voucher_abc");
        when(mockDocumentSnapshot.get("createdAt")).thenReturn(testTimestamp);
        when(mockDocumentSnapshot.getLong("balanceAfter")).thenReturn(120L);

        ActivityEvent event = ActivityEvent.fromDoc(mockDocumentSnapshot);

        assertNotNull(event);
        assertEquals("doc_123", event.id);
        assertEquals(ActivityEvent.TYPE_EARN, event.type);
        assertEquals(25, event.delta);
        assertEquals("Coffee Shop", event.desc);
        assertEquals("voucher_abc", event.refId);
        assertEquals(testTimestamp, event.ts);
        assertEquals(120, event.balanceAfter);
    }

    /** createdAt stored as epoch millis (Number) instead of a Timestamp. */
    @Test
    public void testFromDoc_CreatedAtEpochMillis() {
        long epochMs = 1_700_000_000_000L;
        when(mockDocumentSnapshot.getId()).thenReturn("doc_epoch");
        when(mockDocumentSnapshot.getString("type")).thenReturn(ActivityEvent.TYPE_REDEEM);
        when(mockDocumentSnapshot.get("createdAt")).thenReturn(epochMs);

        ActivityEvent event = ActivityEvent.fromDoc(mockDocumentSnapshot);

        assertNotNull(event);
        assertNotNull(event.ts);
        assertEquals(epochMs, event.ts.toDate().getTime());
    }

    /**
     * Legacy document (type=scan, points, storeName, voucherId, ts). Verifies
     * fallback to old field names and the "scan" -> earn alias.
     */
    @Test
    public void testFromDocSuccess_LegacyFallback() {
        Timestamp testTimestamp = new Timestamp(new Date());

        when(mockDocumentSnapshot.getId()).thenReturn("legacy_1");
        when(mockDocumentSnapshot.getString("type")).thenReturn("scan");
        // Missing backend fields: real DocumentSnapshot returns null here (the
        // mock defaults to 0L, so stub explicitly) to exercise the fallback.
        when(mockDocumentSnapshot.getLong("pointsDelta")).thenReturn(null);
        when(mockDocumentSnapshot.getLong("delta")).thenReturn(null);
        when(mockDocumentSnapshot.getLong("points")).thenReturn(15L);
        when(mockDocumentSnapshot.getString("storeName")).thenReturn("Cafe");
        when(mockDocumentSnapshot.getString("voucherId")).thenReturn("v1");
        when(mockDocumentSnapshot.get("ts")).thenReturn(testTimestamp);

        ActivityEvent event = ActivityEvent.fromDoc(mockDocumentSnapshot);

        assertNotNull(event);
        assertEquals(ActivityEvent.TYPE_EARN, event.type);
        assertEquals(15, event.delta);
        assertEquals("Cafe", event.desc);
        assertEquals("v1", event.refId);
        assertEquals(testTimestamp, event.ts);
    }

    /** Legacy "spend" alias maps onto the canonical redeem type. */
    @Test
    public void testFromDoc_LegacySpendMapsToRedeem() {
        when(mockDocumentSnapshot.getId()).thenReturn("legacy_2");
        when(mockDocumentSnapshot.getString("type")).thenReturn("spend");

        ActivityEvent event = ActivityEvent.fromDoc(mockDocumentSnapshot);

        assertNotNull(event);
        assertEquals(ActivityEvent.TYPE_REDEEM, event.type);
    }

    /** All fields null. Confirms defaults prevent NPEs in adapters. */
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
        assertEquals(0, event.balanceAfter);
        assertNull(event.ts);
    }

    /** fromDoc swallows exceptions and returns null rather than crashing. */
    @Test
    public void testFromDocExceptionHandling() {
        when(mockDocumentSnapshot.getId()).thenThrow(new RuntimeException("boom"));

        ActivityEvent event = ActivityEvent.fromDoc(mockDocumentSnapshot);
        assertNull(event);
    }
}
