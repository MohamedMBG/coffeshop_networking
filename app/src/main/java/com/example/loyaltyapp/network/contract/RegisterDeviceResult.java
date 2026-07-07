package com.example.loyaltyapp.network.contract;

/** {@code data} payload of a successful {@code POST /push/registerDevice}. */
public class RegisterDeviceResult {
    public String deviceId;
    // Backend timestamp of last check-in. Typed as epoch millis; confirm the
    // backend doesn't send an ISO-8601 string before relying on this field.
    public long lastSeenAt;
}
