package com.example.loyaltyapp;

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
            // AuthInterceptor injects the Firebase bearer token on every call and
            // refreshes-and-retries once on 401 (see AuthInterceptor).
            OkHttpClient http = new OkHttpClient.Builder()
                    .addInterceptor(new AuthInterceptor())
                    .connectTimeout(15, TimeUnit.SECONDS)
                    // The backend runs on an instance that sleeps when idle and takes up to ~80s
                    // to answer the first request after waking. A 25s read timeout turned that
                    // wake-up into "Could not connect" on the first sign-in of the day, so the
                    // read budget covers a cold start instead. The connect timeout stays short:
                    // a genuinely unreachable host still fails fast.
                    .readTimeout(90, TimeUnit.SECONDS)
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
