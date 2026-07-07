package com.example.loyaltyapp.network;

import java.util.UUID;

/**
 * Idempotency keys for backend write requests.
 *
 * <p>The backend deduplicates writes by the {@code Idempotency-Key} header: two
 * requests carrying the same key are executed at most once. This lets a client
 * safely retry a write (earn, redeem, cancel, birthday, ...) after a network
 * failure without double-charging points.
 *
 * <p><b>Usage contract.</b> Generate ONE key per logical action and hold it in
 * the ViewModel/repository for the lifetime of that action. Reuse the same key
 * for every retry of that action; mint a new key only for a genuinely new
 * action. Do NOT generate a key per HTTP attempt (e.g. inside an OkHttp
 * interceptor) — that would defeat deduplication, because each retry would look
 * to the backend like a brand-new write.
 *
 * <p>Attach it to write endpoints as a Retrofit header parameter:
 * <pre>{@code
 * @POST("loyalty/earn")
 * Call<ApiResponse<EarnResult>> earn(
 *         @Header(IdempotencyKey.HEADER) String key,
 *         @Body EarnRequest body);
 * }</pre>
 * Reads (GET) never carry an idempotency key.
 */
public final class IdempotencyKey {

    /** HTTP header name the backend reads to deduplicate writes. */
    public static final String HEADER = "Idempotency-Key";

    private IdempotencyKey() {}

    // ponytail: intentionally just a generator + header name. Reuse-across-retries
    // is the caller's job (hold the key in the VM/repo), not an interceptor — a
    // per-request interceptor would mint a new key each retry and break dedup.
    /** A fresh v4 UUID key. Call once per logical write, then reuse it on retries. */
    public static String generate() {
        return UUID.randomUUID().toString();
    }
}
