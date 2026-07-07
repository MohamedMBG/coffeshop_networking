package com.example.loyaltyapp.network.contract;

/** {@code data} payload of a successful {@code POST /loyalty/earn}. */
public class EarnResult {
    public int pointsGranted;
    public int totalPoints;
    public int totalVisits;
}
