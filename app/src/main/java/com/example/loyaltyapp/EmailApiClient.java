package com.example.loyaltyapp;

import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class EmailApiClient {
    public static final String BASE_URL = BuildConfig.LEGACY_EMAIL_API_BASE_URL;

    private static Retrofit retrofit;

    public static Retrofit getClient() {
        if (retrofit == null) {
            retrofit = new Retrofit.Builder()
                    .baseUrl(BASE_URL)
                    .addConverterFactory(GsonConverterFactory.create())
                    .build();
        }
        return retrofit;
    }
}
