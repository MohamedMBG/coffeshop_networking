package com.example.loyaltyapp.models;

public class Rewards {
    public String id;          // Firestore document id (set after fetch)
    public String name;
    public double priceMAD;    // optional, for display
    public int redeemPoints;   // required points
    public String imagePath;   // Storage path or public URL
    public String category;    // optional, for filtering
    public boolean active;

    public Rewards() {
    }

    public Rewards(String id, String name, double priceMAD, int redeemPoints, String imagePath, String category, boolean active) {
        this.id = id;
        this.name = name;
        this.priceMAD = priceMAD;
        this.redeemPoints = redeemPoints;
        this.imagePath = imagePath;
        this.category = category;
        this.active = active;
    }
}
