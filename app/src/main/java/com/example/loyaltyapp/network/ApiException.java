package com.example.loyaltyapp.network;

import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

/**
 * A failed backend call, in structured form. Carries the HTTP status, the
 * backend error {@code code}, the raw server message (for logs, not UI), an
 * optional {@code Retry-After} in seconds, and a {@link StringRes} id for the
 * user-facing message.
 *
 * <p>Produced by {@link ApiErrors#from}. Callers show {@link #messageRes} via a
 * {@code Context} and, on {@link #isRateLimited()}, back off for
 * {@link #retryAfterSeconds} before retrying.
 */
public class ApiException extends Exception {

    public final int httpStatus;

    /** Backend error code, e.g. {@code INSUFFICIENT_POINTS}. Null if none was parsed. */
    @Nullable
    public final String code;

    /** Seconds to wait before retrying (429). Null if not supplied. */
    @Nullable
    public final Integer retryAfterSeconds;

    @StringRes
    public final int messageRes;

    public ApiException(int httpStatus,
                        @Nullable String code,
                        @Nullable String serverMessage,
                        @Nullable Integer retryAfterSeconds,
                        @StringRes int messageRes) {
        // Keep the server message as the Throwable message (diagnostics only —
        // never surface it directly; use messageRes for UI).
        super(serverMessage);
        this.httpStatus = httpStatus;
        this.code = code;
        this.retryAfterSeconds = retryAfterSeconds;
        this.messageRes = messageRes;
    }

    public boolean isRateLimited() {
        return httpStatus == 429;
    }
}
