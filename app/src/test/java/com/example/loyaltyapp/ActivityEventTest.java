package com.example.loyaltyapp;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.mockito.Mockito.when;

import com.example.loyaltyapp.models.ActivityEvent;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentSnapshot;

import java.util.Date;

/**
 * Unit tests to test Firebase data parsing inside ActivityEvent.
 * Uses Mockito to mock DocumentSnapshot interactions.
 */
public class ActivityEventTest {

    @Mock
    DocumentSnapshot mockDocumentSnapshot;

    @Before
    public void setUp() {
        // Initialize Mockito annotations to inject the mock DocumentSnapshot
        MockitoAnnotations.openMocks(this);
    }

    /**
     * Test fromDoc() method when passing a fully populated DocumentSnapshot.
     * Checks if data translates to model properties correctly.
     */
    @Test
    public void testFromDocSuccess() {
        Timestamp testTimestamp = new Timestamp(new Date());

        // Define the behavior of the mocked snapshot
        when(mockDocumentSnapshot.getId()).thenReturn("doc_123");
        when(mockDocumentSnapshot.getString("type")).thenReturn("scan");
        when(mockDocumentSnapshot.getLong("points")).thenReturn(25L);
        when(mockDocumentSnapshot.getString("storeName")).thenReturn("Coffee Shop");
        when(mockDocumentSnapshot.getTimestamp("ts")).thenReturn(testTimestamp);

        // Convert mocked doc to ActivityEvent
        ActivityEvent event = ActivityEvent.fromDoc(mockDocumentSnapshot);

        // Assert parsed values equal the mocked data
        assertNotNull(event);
        assertEquals("doc_123", event.id);
        assertEquals("scan", event.type);
        assertEquals(25, event.points);
        assertEquals("Coffee Shop", event.storeName);
        assertEquals(testTimestamp, event.ts);
    }

    /**
     * Test fromDoc() method with a DocumentSnapshot containing missing or null
     * fields.
     * Expects default values to be set to avoid NullPointerExceptions.
     */
    @Test
    public void testFromDocWithNullFields() {
        when(mockDocumentSnapshot.getId()).thenReturn("doc_nulls");
        when(mockDocumentSnapshot.getString("type")).thenReturn(null);
        when(mockDocumentSnapshot.getLong("points")).thenReturn(null);
        when(mockDocumentSnapshot.getString("storeName")).thenReturn(null);
        when(mockDocumentSnapshot.getTimestamp("ts")).thenReturn(null);

        ActivityEvent event = ActivityEvent.fromDoc(mockDocumentSnapshot);

        assertNotNull(event);
        assertEquals("doc_nulls", event.id);
        assertEquals("", event.type); // safeStr handles nulls and turns them to empty string
        assertEquals(0, event.points); // Default value when null
        assertEquals("", event.storeName); // safeStr logic
        assertNull(event.ts);
    }

    /**
     * Test fromDoc() handling an exception scenario.
     * In the original class, if an Exception occurs, it returns null.
     */
    @Test
    public void testFromDocExceptionHandling() {
        // Simulate an exception when trying to fetch the ID
        when(mockDocumentSnapshot.getId()).thenThrow(new RuntimeException("Simulated exception"));

        ActivityEvent event = ActivityEvent.fromDoc(mockDocumentSnapshot);
        // Ensure that exceptions are caught and fromDoc gracefully returns null
        assertNull(event);
    }
}
