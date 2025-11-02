package com.example.loyaltyapp;

import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

// ApiClient.java
public class ApiClient {
    private static final String BASE_URL =
            "https://email-api-git-main-programmingmbmy-3449s-projects.vercel.app/"; // must end with /

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
