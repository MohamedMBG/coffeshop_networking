package com.example.loyaltyapp.network.contract;

/** Body for {@code POST /rewards/redeem}. */
public class RedeemRequest {
    public final String rewardId;

    public RedeemRequest(String rewardId) {
        this.rewardId = rewardId;
    }
}
