package com.example.loyaltyapp.network.contract;

/** {@code data} payload of a successful {@code POST /rewards/redeem}. */
public class RedeemResult {
    public String code;
    public String rewardId;
    public int cost;
    public int totalPoints;
    public long expiresAtEpochMs;
}
