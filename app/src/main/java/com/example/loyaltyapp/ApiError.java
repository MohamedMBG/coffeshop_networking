package com.example.loyaltyapp;

/** Backend error envelope: {ok:false, code, message}. */
public class ApiError {
    public boolean ok;
    public String code;
    public String message;
}
