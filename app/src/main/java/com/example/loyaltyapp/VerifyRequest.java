package com.example.loyaltyapp;


public class VerifyRequest {
    private String token;
    private String uid;

    public VerifyRequest(String token, String uid) {
        this.token = token;
        this.uid = uid;
    }
}