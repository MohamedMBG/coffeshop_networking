package com.example.loyaltyapp;

import com.google.gson.Gson;
import org.junit.Test;
import java.util.Map;
import retrofit2.http.POST;
import static org.junit.Assert.*;

public class EmailAuthContractTest {
    @Test
    public void signupRoutesMatchBackendWithoutDuplicateApiPrefix() throws Exception {
        assertEquals("auth/register", ApiService.class.getMethod("registerEmail", Map.class)
                .getAnnotation(POST.class).value());
        assertEquals("auth/verify", ApiService.class.getMethod("verifyToken", Map.class)
                .getAnnotation(POST.class).value());
    }

    @Test
    public void verificationReadsCustomTokenAtTopLevel() {
        ApiService.VerifyResponse response = new Gson().fromJson(
                "{\"ok\":true,\"email\":\"customer@example.com\",\"customToken\":\"firebase-token\"}",
                ApiService.VerifyResponse.class);
        assertTrue(response.ok);
        assertEquals("customer@example.com", response.email);
        assertEquals("firebase-token", response.customToken);
    }
}
