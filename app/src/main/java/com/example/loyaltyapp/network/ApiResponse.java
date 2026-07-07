package com.example.loyaltyapp.network;

import androidx.annotation.Nullable;

/**
 * The backend response envelope.
 *
 * <p>Success: {@code {"ok": true, "data": {...}}}.
 * Error: {@code {"ok": false, "code": "SOME_CODE", "message": "..."}}.
 *
 * <p>Both shapes are parsed into this one class — Gson leaves absent fields
 * null — so a Retrofit call can be declared {@code Call<ApiResponse<T>>} and
 * the same type covers the error body. Use {@link ApiErrors} to turn a failed
 * response into an {@link ApiException}.
 *
 * @param <T> type of the {@code data} payload on success
 */
public class ApiResponse<T> {
    public boolean ok;

    @Nullable
    public T data;

    // Present only on error bodies.
    @Nullable
    public String code;

    @Nullable
    public String message;
}
