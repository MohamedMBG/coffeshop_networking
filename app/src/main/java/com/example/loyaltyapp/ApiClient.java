package com.example.loyaltyapp;

import com.example.loyaltyapp.network.AuthInterceptor;

import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
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
            // AuthInterceptor stamps every request with the Firebase ID token
            // so the backend can authenticate the caller. Timeouts mirror the
            // existing TokenRegistrar OkHttp client for consistent behaviour.
            OkHttpClient http = new OkHttpClient.Builder()
                    .addInterceptor(new AuthInterceptor())
                    .connectTimeout(15, TimeUnit.SECONDS)
                    .readTimeout(25, TimeUnit.SECONDS)
                    .writeTimeout(25, TimeUnit.SECONDS)
                    .build();

            retrofit = new Retrofit.Builder()
                    .baseUrl(BASE_URL)
                    .client(http)
                    .addConverterFactory(GsonConverterFactory.create())
                    .build();
        }
        return retrofit;
    }
}
