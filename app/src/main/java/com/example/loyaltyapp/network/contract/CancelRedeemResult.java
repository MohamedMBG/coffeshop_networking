package com.example.loyaltyapp.network.contract;

/** {@code data} payload of a successful {@code POST /rewards/redeem/cancel}. */
public class CancelRedeemResult {
    public String code;
    // Points returned to the balance by the cancel. Confirm shape with backend
    // (points refunded vs. boolean flag) before wiring the redeem cancel UI.
    public int refunded;
    public int totalPoints;
}
