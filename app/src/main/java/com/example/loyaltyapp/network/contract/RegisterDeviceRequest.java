package com.example.loyaltyapp.network.contract;

/** Body for {@code POST /push/registerDevice}. */
public class RegisterDeviceRequest {
    public final String deviceId;
    public final String fcmToken;
    public final String platform;

    public RegisterDeviceRequest(String deviceId, String fcmToken, String platform) {
        this.deviceId = deviceId;
        this.fcmToken = fcmToken;
        this.platform = platform;
    }
}
