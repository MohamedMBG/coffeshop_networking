package com.example.loyaltyapp;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.example.loyaltyapp.network.ApiResponse;
import com.example.loyaltyapp.network.contract.EarnRequest;
import com.example.loyaltyapp.network.contract.EarnResult;
import com.example.loyaltyapp.network.contract.CancelRedeemRequest;
import com.example.loyaltyapp.network.contract.RedeemRequest;
import com.example.loyaltyapp.network.contract.RedeemResult;
import com.example.loyaltyapp.network.contract.RegisterDeviceRequest;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import org.junit.Test;

import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class ApiServiceContractTest {

    private static final Gson GSON = new Gson();

    private static ApiService service() {
        return new Retrofit.Builder()
                .baseUrl("https://example.com/api/v1/")
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                .create(ApiService.class);
    }

    /**
     * Invoking each method builds its Call, which forces Retrofit to parse the
     * method annotations (@POST path, @Header, @Body). A malformed annotation
     * throws here — no network I/O happens until execute/enqueue.
     */
    @Test
    public void allEndpoints_haveValidAnnotations() {
        ApiService api = service();
        assertNotNull(api.earn("key", new EarnRequest("ABC")));
        assertNotNull(api.redeem("key", new RedeemRequest("r1")));
        assertNotNull(api.cancelRedeem("key", new CancelRedeemRequest("code1")));
        assertNotNull(api.claimBirthday("key"));
        assertNotNull(api.registerDevice(
                new RegisterDeviceRequest("dev1", "fcm1", "android")));
    }

    @Test
    public void requestBodies_serializeExpectedFieldNames() {
        assertEquals("{\"code\":\"ABC\"}", GSON.toJson(new EarnRequest("ABC")));
        assertEquals("{\"rewardId\":\"r1\"}", GSON.toJson(new RedeemRequest("r1")));

        String device = GSON.toJson(new RegisterDeviceRequest("dev1", "fcm1", "android"));
        assertTrue(device.contains("\"deviceId\":\"dev1\""));
        assertTrue(device.contains("\"fcmToken\":\"fcm1\""));
        assertTrue(device.contains("\"platform\":\"android\""));
    }

    @Test
    public void earnResponse_parsesFromEnvelope() {
        String json = "{\"ok\":true,\"data\":{"
                + "\"pointsGranted\":10,\"totalPoints\":100,\"totalVisits\":5}}";
        ApiResponse<EarnResult> resp = GSON.fromJson(
                json, new TypeToken<ApiResponse<EarnResult>>() {}.getType());

        assertTrue(resp.ok);
        assertNotNull(resp.data);
        assertEquals(10, resp.data.pointsGranted);
        assertEquals(100, resp.data.totalPoints);
        assertEquals(5, resp.data.totalVisits);
    }

    @Test
    public void redeemResponse_parsesExpiryAsLong() {
        String json = "{\"ok\":true,\"data\":{"
                + "\"code\":\"XYZ\",\"rewardId\":\"r1\",\"cost\":50,"
                + "\"totalPoints\":25,\"expiresAtEpochMs\":1751850000000}}";
        ApiResponse<RedeemResult> resp = GSON.fromJson(
                json, new TypeToken<ApiResponse<RedeemResult>>() {}.getType());

        assertEquals("XYZ", resp.data.code);
        assertEquals(50, resp.data.cost);
        assertEquals(1751850000000L, resp.data.expiresAtEpochMs);
    }
}
