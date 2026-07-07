package com.example.loyaltyapp;

import androidx.annotation.Nullable;

import com.example.loyaltyapp.network.ApiResponse;
import com.example.loyaltyapp.network.IdempotencyKey;

import java.util.Map;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.Header;
import retrofit2.http.POST;

/**
 * Backend REST contract.
 *
 * <p>New endpoints are relative to a base URL ending in {@code /api/v1/} (e.g.
 * {@code https://host/api/v1/}) and go to the Spring backend. Every write
 * carries an {@link IdempotencyKey#HEADER} — generate one key per logical
 * action and reuse it on retries (see {@link IdempotencyKey}); the auth token
 * is attached transparently by the OkHttp interceptor. Responses come wrapped
 * in {@link ApiResponse}; turn a failed call into a message with
 * {@code ApiErrors.from(response)}.
 *
 * <p>{@code registerDevice} deliberately takes no idempotency key (it is an
 * idempotent upsert on the backend side).
 */
public interface ApiService {

    // ---------------------------------------------------------------------
    // Economy (Spring backend, /api/v1)
    // ---------------------------------------------------------------------

    /** POST /loyalty/earn — redeem an earn code for points. */
    @POST("loyalty/earn")
    Call<ApiResponse<EarnResult>> earn(
            @Header(IdempotencyKey.HEADER) String idempotencyKey,
            @Body EarnRequest body);

    /** POST /rewards/redeem — spend points on a reward, returns a pending code. */
    @POST("rewards/redeem")
    Call<ApiResponse<RedeemResult>> redeem(
            @Header(IdempotencyKey.HEADER) String idempotencyKey,
            @Body RedeemRequest body);

    /** POST /rewards/redeem/cancel — cancel a pending redeem and refund points. */
    @POST("rewards/redeem/cancel")
    Call<ApiResponse<CancelRedeemResult>> cancelRedeem(
            @Header(IdempotencyKey.HEADER) String idempotencyKey,
            @Body CancelRedeemRequest body);

    /** POST /rewards/birthday — claim the yearly birthday bonus. No request body. */
    @POST("rewards/birthday")
    Call<ApiResponse<BirthdayResult>> claimBirthday(
            @Header(IdempotencyKey.HEADER) String idempotencyKey);

    /** POST /push/registerDevice — upsert this device's FCM token. No idempotency key. */
    @POST("push/registerDevice")
    Call<ApiResponse<RegisterDeviceResult>> registerDevice(
            @Body RegisterDeviceRequest body);

    // ---------------------------------------------------------------------
    // Request bodies
    // ---------------------------------------------------------------------

    class EarnRequest {
        public final String code;
        public EarnRequest(String code) { this.code = code; }
    }

    class RedeemRequest {
        public final String rewardId;
        public RedeemRequest(String rewardId) { this.rewardId = rewardId; }
    }

    class CancelRedeemRequest {
        public final String code;
        public CancelRedeemRequest(String code) { this.code = code; }
    }

    class RegisterDeviceRequest {
        public final String deviceId;
        public final String fcmToken;
        public final String platform;
        public RegisterDeviceRequest(String deviceId, String fcmToken, String platform) {
            this.deviceId = deviceId;
            this.fcmToken = fcmToken;
            this.platform = platform;
        }
    }

    // ---------------------------------------------------------------------
    // Response payloads (the `data` inside ApiResponse)
    // ---------------------------------------------------------------------

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

    class CancelRedeemResult {
        public String code;
        // Points returned to the balance by the cancel. Confirm shape with backend
        // (points refunded vs. boolean flag) before wiring the redeem cancel UI.
        public int refunded;
        public int totalPoints;
    }

    class BirthdayResult {
        public int pointsGranted;
        public int totalPoints;
        public int year;
    }

    class RegisterDeviceResult {
        public String deviceId;
        // Backend timestamp of last check-in. Typed as epoch millis; confirm the
        // backend doesn't send an ISO-8601 string before relying on this field.
        public long lastSeenAt;
    }

    // ---------------------------------------------------------------------
    // Legacy — old email/verify backend. Still used by SignUpActivity and
    // LoyaltyActivity; remove when those screens migrate to the new contract.
    // ---------------------------------------------------------------------

    /** @deprecated legacy email backend; no route on the new backend. */
    @Deprecated
    @POST("api/register")
    Call<Map<String, Object>> registerEmail(@Body Map<String, String> body);

    /** @deprecated legacy email backend; no route on the new backend. */
    @Deprecated
    @POST("api/verify")
    Call<VerifyResponse> verifyToken(@Body Map<String, String> body);

    /**
     * @deprecated wrong path + body; replaced by {@link #claimBirthday}. Kept
     * until LoyaltyActivity is migrated off it.
     */
    @Deprecated
    @POST("api/rewards/birthday")
    Call<Map<String, Object>> claimBirthdayReward(@Body @Nullable Map<String, String> body);

    /** Plain POJO for legacy /verify. */
    class VerifyResponse {
        public boolean ok;
        public String email;
        public String customToken;
    }
}
