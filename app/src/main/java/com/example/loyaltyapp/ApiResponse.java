package com.example.loyaltyapp;

/** Backend success envelope: {ok:true, data:...}. */
public class ApiResponse<T> {
    public boolean ok;
    public T data;
}
