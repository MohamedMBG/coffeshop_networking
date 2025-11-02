package com.example.loyaltyapp;
// ApiService.java
import java.util.Map;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.POST;

public interface ApiService {
    @POST("api/register")
    Call<Map<String, Object>> registerEmail(@Body Map<String, String> body);

    @POST("api/verify")
    Call<VerifyResponse> verifyToken(@Body Map<String, String> body);

    // Plain POJO for /verify
    class VerifyResponse {
        public boolean ok;
        public String email;
        public String customToken;
    }
}
