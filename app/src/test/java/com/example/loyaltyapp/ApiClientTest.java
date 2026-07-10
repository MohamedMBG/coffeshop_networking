package com.example.loyaltyapp;

import org.junit.Test;
import retrofit2.Retrofit;
import static org.junit.Assert.*;

/**
 * Unit tests for ApiClient to ensure Retrofit is correctly configured.
 */
public class ApiClientTest {

    /**
     * Test that getting the Retrofit client returns a non-null instance
     * and uses a singleton pattern (returns the same instance).
     */
    @Test
    public void testGetClientReturnsValidInstance() {
        Retrofit retrofit1 = ApiClient.getClient();
        assertNotNull("Retrofit instance should not be null", retrofit1);

        Retrofit retrofit2 = ApiClient.getClient();
        assertSame("Retrofit instances should be the same across calls (singleton)", retrofit1, retrofit2);

        assertEquals("Base URL should end with a trailing slash",
                "https://bean-backend-ejzg.onrender.com/api/v1/",
                retrofit1.baseUrl().toString());
    }
}
