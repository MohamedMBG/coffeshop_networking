package com.example.loyaltyapp.network;

import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

import com.example.loyaltyapp.R;
import com.google.gson.Gson;

import retrofit2.Response;

/**
 * Turns a failed backend response into an {@link ApiException}: parses the
 * {@code {ok,code,message}} error envelope, reads {@code Retry-After}, and maps
 * the backend code (falling back to the HTTP status) to a user-facing
 * {@link StringRes}.
 */
public final class ApiErrors {

    private static final Gson GSON = new Gson();

    private ApiErrors() {}

    /**
     * Build an {@link ApiException} from a non-successful Retrofit response.
     * Safe to call only when {@code response.isSuccessful()} is false.
     */
    public static ApiException from(Response<?> response) {
        int status = response.code();
        String code = null;
        String serverMessage = null;

        try {
            if (response.errorBody() != null) {
                String raw = response.errorBody().string();
                if (raw != null && !raw.isEmpty()) {
                    ApiResponse<?> env = GSON.fromJson(raw, ApiResponse.class);
                    if (env != null) {
                        code = env.code;
                        serverMessage = env.message;
                    }
                }
            }
        } catch (Exception ignored) {
            // Malformed or unreadable body — fall back to status-based mapping.
        }

        Integer retryAfter = parseRetryAfter(response.headers().get("Retry-After"));
        return new ApiException(status, code, serverMessage, retryAfter, messageRes(code, status));
    }

    /**
     * Map a backend error code to a user-facing string, falling back to the
     * HTTP status when the code is unknown or absent.
     */
    @StringRes
    public static int messageRes(@Nullable String code, int httpStatus) {
        if (code != null) {
            switch (code) {
                case "EARN_CODE_NOT_FOUND":      return R.string.err_earn_code_not_found;
                case "EARN_CODE_EXPIRED":        return R.string.err_earn_code_expired;
                case "EARN_CODE_ALREADY_USED":   return R.string.err_earn_code_already_used;
                case "EARN_CODE_INVALID_FORMAT": return R.string.err_earn_code_invalid_format;
                case "VISIT_COOLDOWN":           return R.string.err_visit_cooldown;
                case "RATE_LIMITED":             return R.string.err_rate_limited;
                case "INSUFFICIENT_POINTS":      return R.string.err_insufficient_points;
                case "REWARD_INACTIVE":          return R.string.err_reward_inactive;
                case "REDEEM_PENDING_LIMIT":     return R.string.err_redeem_pending_limit;
                case "REDEEM_NOT_FOUND":         return R.string.err_redeem_not_found;
                case "REDEEM_NOT_PENDING":       return R.string.err_redeem_not_pending;
                case "BIRTHDAY_NOT_SET":         return R.string.err_birthday_not_set;
                case "BIRTHDAY_NOT_TODAY":       return R.string.err_birthday_not_today;
                case "BIRTHDAY_ALREADY_CLAIMED": return R.string.err_birthday_already_claimed;
                default: break; // unknown code — fall through to status mapping
            }
        }

        switch (httpStatus) {
            case 401: return R.string.err_unauthorized;
            case 404: return R.string.err_not_found;
            case 409: return R.string.err_conflict;
            case 410: return R.string.err_gone;
            case 422: return R.string.err_unprocessable;
            case 429: return R.string.err_rate_limited;
            default:  return R.string.err_generic;
        }
    }

    /**
     * Retry-After as whole seconds, or null. HTTP allows an HTTP-date form too;
     * the backend sends seconds, so only that is parsed.
     */
    // ponytail: seconds-only. Add HTTP-date parsing if the backend ever sends it.
    @Nullable
    static Integer parseRetryAfter(@Nullable String header) {
        if (header == null) {
            return null;
        }
        try {
            int seconds = Integer.parseInt(header.trim());
            return seconds >= 0 ? seconds : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
