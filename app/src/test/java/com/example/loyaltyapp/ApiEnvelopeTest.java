package com.example.loyaltyapp;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import org.junit.Test;
import static org.junit.Assert.*;

public class ApiEnvelopeTest {
    @Test
    public void redeemEnvelopePreservesTypedPayload() {
        ApiResponse<ApiService.RedeemResult> result = new Gson().fromJson(
                "{\"ok\":true,\"data\":{\"code\":\"ABC123\",\"totalPoints\":75,"
                        + "\"expiresAtEpochMs\":1800000000000}}",
                new TypeToken<ApiResponse<ApiService.RedeemResult>>() {}.getType());
        assertTrue(result.ok);
        assertEquals("ABC123", result.data.code);
        assertEquals(75, result.data.totalPoints);
        assertEquals(1800000000000L, result.data.expiresAtEpochMs);
    }

    @Test
    public void backendAuthErrorsHaveActionableMessages() {
        ApiError result = new Gson().fromJson(
                "{\"ok\":false,\"code\":\"AUTH_REQUIRED\",\"message\":\"Authentication required\"}",
                ApiError.class);
        assertFalse(result.ok);
        assertEquals("Please sign in again.", ApiErrors.messageFor(result.code, result.message));
        assertEquals("Please sign in again.", ApiErrors.messageFor("AUTH_INVALID_TOKEN", "raw"));
        assertEquals("Please sign in again.", ApiErrors.messageFor("AUTH_MALFORMED_TOKEN", "raw"));
    }
}
