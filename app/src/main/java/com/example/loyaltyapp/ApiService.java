package com.example.loyaltyapp;

import androidx.annotation.Nullable;

import com.example.loyaltyapp.network.ApiResponse;
import com.example.loyaltyapp.network.IdempotencyKey;
import com.example.loyaltyapp.network.contract.BirthdayResult;
import com.example.loyaltyapp.network.contract.CancelRedeemRequest;
import com.example.loyaltyapp.network.contract.CancelRedeemResult;
import com.example.loyaltyapp.network.contract.EarnRequest;
import com.example.loyaltyapp.network.contract.EarnResult;
import com.example.loyaltyapp.network.contract.RedeemRequest;
import com.example.loyaltyapp.network.contract.RedeemResult;
import com.example.loyaltyapp.network.contract.RegisterDeviceRequest;
import com.example.loyaltyapp.network.contract.RegisterDeviceResult;

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
 * {@code ApiErrors.from(response)}. Request/response bodies live as top-level
 * types in {@code com.example.loyaltyapp.network.contract}.
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
