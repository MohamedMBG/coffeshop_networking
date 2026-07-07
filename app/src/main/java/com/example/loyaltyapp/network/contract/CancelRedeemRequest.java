package com.example.loyaltyapp.network.contract;

/** Body for {@code POST /rewards/redeem/cancel}. */
public class CancelRedeemRequest {
    public final String code;

    public CancelRedeemRequest(String code) {
        this.code = code;
    }
}
