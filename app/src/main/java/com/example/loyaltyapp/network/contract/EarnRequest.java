package com.example.loyaltyapp.network.contract;

/** Body for {@code POST /loyalty/earn}. */
public class EarnRequest {
    public final String code;

    public EarnRequest(String code) {
        this.code = code;
    }
}
