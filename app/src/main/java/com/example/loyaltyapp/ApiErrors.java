package com.example.loyaltyapp;

import com.google.gson.Gson;

import okhttp3.ResponseBody;
import retrofit2.Response;

/**
 * Parses the backend error envelope {ok:false, code, message} and maps error
 * codes to user-facing text. Both methods are null-safe and never throw.
 */
public final class ApiErrors {
    private static final Gson GSON = new Gson();

    private ApiErrors() {}

    /** Parse {ok,code,message} from a non-2xx Retrofit response; null if unparseable. */
    public static ApiError parse(Response<?> response) {
        if (response == null) return null;
        try (ResponseBody eb = response.errorBody()) {
            if (eb == null) return null;
            return GSON.fromJson(eb.string(), ApiError.class);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * User-facing message for a transport-level failure (no HTTP response, e.g.
     * no connectivity or a timeout). Keeps network copy in the message layer
     * rather than hard-coded in repositories.
     */
    public static String networkMessageFor(Throwable t) {
        if (t instanceof java.net.UnknownHostException) {
            return "No internet connection. Check your network and try again.";
        }
        if (t instanceof java.net.SocketTimeoutException) {
            return "Network timeout. Please try again.";
        }
        return "Network error. Please try again.";
    }

    /** Convenience: parse the response and return the user-facing message for its code. */
    public static String messageFor(Response<?> response) {
        ApiError err = parse(response);
        return err == null
                ? messageFor(null, null)
                : messageFor(err.code, err.message);
    }

    /**
     * Map a backend error code to a user-facing message. Falls back to the
     * server-supplied message, then a generic string.
     */
    public static String messageFor(String code, String serverMessage) {
        if (code == null) {
            return serverMessage != null ? serverMessage : "Something went wrong. Please try again.";
        }
        switch (code) {
            // earn
            case "EARN_CODE_NOT_FOUND":      return "This code doesn't exist.";
            case "EARN_CODE_EXPIRED":        return "This code has expired.";
            case "EARN_CODE_ALREADY_USED":   return "This code was already used.";
            case "EARN_CODE_INVALID_FORMAT": return "This QR code isn't valid.";
            case "VISIT_COOLDOWN":           return "You just earned points. Please try again shortly.";
            // redeem
            case "INSUFFICIENT_POINTS":      return "You don't have enough points.";
            case "REWARD_INACTIVE":          return "This reward is no longer available.";
            case "REWARD_NOT_FOUND":         return "This reward doesn't exist.";
            case "REDEEM_PENDING_LIMIT":     return "You already have a pending reward. Use or cancel it first.";
            // cancel
            case "REDEEM_NOT_FOUND":         return "This reward code wasn't found.";
            case "REDEEM_NOT_PENDING":       return "This reward can no longer be cancelled.";
            // birthday
            case "BIRTHDAY_NOT_SET":         return "Add your birthday in your profile first.";
            case "BIRTHDAY_NOT_TODAY":       return "Your birthday reward is only available on your birthday.";
            case "BIRTHDAY_ALREADY_CLAIMED": return "You've already claimed your birthday reward this year.";
            // generic
            case "RATE_LIMITED":             return "Too many requests. Please slow down.";
            case "UNAUTHENTICATED":          return "Please sign in again.";
            default: return serverMessage != null ? serverMessage : "Something went wrong. Please try again.";
        }
    }
}
