package com.example.loyaltyapp;

import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class ApiClient {
    // P1: base URL pulled from Gradle buildConfigField so dev/staging/prod
    // can ship distinct builds without source edits. Trailing slash is
    // required by Retrofit.
    public static final String BASE_URL = BuildConfig.API_BASE_URL;

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
