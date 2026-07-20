package com.example.loyaltyapp;

import java.util.Map;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.Header;
import retrofit2.http.POST;

/**
 * Backend REST contract (base /api/v1). Success bodies come wrapped in
 * {ok:true, data:...} — see {@link ApiResponse}. Errors come as
 * {ok:false, code, message} — parse via {@link ApiErrors}. Every write carries
 * an Idempotency-Key (see {@link Idempotency}); registerDevice does not.
 */
public interface ApiService {

    // ---- Backend contract (typed) ------------------------------------------

    @POST("loyalty/earn")
    Call<ApiResponse<EarnResult>> earn(
            @Header("Idempotency-Key") String idempotencyKey,
            @Body EarnRequest body);

    @POST("rewards/redeem")
    Call<ApiResponse<RedeemResult>> redeem(
            @Header("Idempotency-Key") String idempotencyKey,
            @Body RedeemRequest body);

    @POST("rewards/redeem/cancel")
    Call<ApiResponse<CancelResult>> cancelRedeem(
            @Header("Idempotency-Key") String idempotencyKey,
            @Body CancelRequest body);

    @POST("rewards/birthday")
    Call<ApiResponse<BirthdayResult>> redeemBirthday(
            @Header("Idempotency-Key") String idempotencyKey);

    @POST("push/registerDevice")
    Call<ApiResponse<DeviceResult>> registerDevice(@Body DeviceRequest body);

    @POST("push/unregisterDevice")
    Call<ApiResponse<UnregisterDeviceResult>> unregisterDevice(@Body DeviceIdRequest body);

    @POST("push/interest")
    Call<ApiResponse<InterestResult>> recordInterest(@Body InterestRequest body);

    // ---- Request DTOs ------------------------------------------------------

    class EarnRequest {
        public String code;
        public EarnRequest(String code) { this.code = code; }
    }

    class RedeemRequest {
        public String rewardId;
        public RedeemRequest(String rewardId) { this.rewardId = rewardId; }
    }

    class CancelRequest {
        public String code;
        public CancelRequest(String code) { this.code = code; }
    }

    class DeviceRequest {
        public String deviceId;
        public String fcmToken;
        public String platform;
        public DeviceRequest(String deviceId, String fcmToken, String platform) {
            this.deviceId = deviceId;
            this.fcmToken = fcmToken;
            this.platform = platform;
        }
    }

    /** Identifies this app installation for authenticated logout unregistration. */
    class DeviceIdRequest {
        /** Client-generated stable installation id; the backend verifies its current owner. */
        public String deviceId;
        public DeviceIdRequest(String deviceId) { this.deviceId = deviceId; }
    }

    /** Behavioral menu signal used to calculate the authenticated customer's top interest. */
    class InterestRequest {
        /** Client-visible menu category; normalized and validated by the backend. */
        public String category;
        public InterestRequest(String category) { this.category = category; }
    }

    // ---- Response DTOs (payloads inside ApiResponse.data) -------------------

    class EarnResult {
        public int pointsGranted;
        public int totalPoints;
        public int totalVisits;
    }

    class RedeemResult {
        public String code;
        public String rewardId;
        public int cost;
        public int totalPoints;
        public long expiresAtEpochMs;
    }

    class CancelResult {
        public String code;
        public int refunded;
        public int totalPoints;
    }

    class BirthdayResult {
        public int pointsGranted;
        public int totalPoints;
        public int year;
    }

    class DeviceResult {
        public String deviceId;
        public long lastSeenAt;
    }

    /** Confirms that an installation can no longer receive pushes for the previous user. */
    class UnregisterDeviceResult {
        /** Stable installation id supplied by the client. */
        public String deviceId;
        /** True when the backend device record is disabled or was already absent. */
        public boolean disabled;
    }

    /** Updated interest aggregate returned after a category-selection event. */
    class InterestResult {
        /** Highest-scoring normalized menu category for the authenticated customer. */
        public String topInterest;
        /** New accumulated score for the submitted category. */
        public long categoryScore;
    }

    // ---- Legacy (no backend route; removed in Step 3 once callers migrate) --

    @Deprecated
    @POST("api/register")
    Call<Map<String, Object>> registerEmail(@Body Map<String, String> body);

    @Deprecated
    @POST("api/verify")
    Call<VerifyResponse> verifyToken(@Body Map<String, String> body);

    // Plain POJO for legacy /verify
    class VerifyResponse {
        public boolean ok;
        public String email;
        public String customToken;
    }
}
