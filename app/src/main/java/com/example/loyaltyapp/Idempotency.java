package com.example.loyaltyapp;

import java.util.UUID;

/**
 * Idempotency-Key generator. Create ONE key per logical write (earn, redeem,
 * cancel, birthday) and REUSE the same key across network retries of that write
 * so the backend replays the first result instead of charging twice. Store the
 * value on the ViewModel/repo for the action — never regenerate per HTTP retry.
 */
public final class Idempotency {
    private Idempotency() {}

    public static String newKey() {
        return UUID.randomUUID().toString();
    }
}
