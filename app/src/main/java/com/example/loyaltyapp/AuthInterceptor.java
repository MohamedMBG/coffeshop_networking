package com.example.loyaltyapp;

import androidx.annotation.NonNull;

import com.google.android.gms.tasks.Tasks;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.GetTokenResult;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import okhttp3.Interceptor;
import okhttp3.Request;
import okhttp3.Response;

/**
 * Attaches the Firebase ID token as "Authorization: Bearer &lt;token&gt;" to every
 * request. getIdToken(false) uses the cached token via a blocking Tasks.await —
 * safe because OkHttp runs interceptors on its own dispatcher thread, never the
 * UI thread. On 401 it refreshes once with getIdToken(true) and retries: needed
 * right after a role grant when the cached token still lacks the new claim.
 */
final class AuthInterceptor implements Interceptor {

    @NonNull
    @Override
    public Response intercept(@NonNull Chain chain) throws IOException {
        Request original = chain.request();

        Response response = chain.proceed(withBearer(original, idToken(false)));

        if (response.code() == 401) {
            response.close();
            response = chain.proceed(withBearer(original, idToken(true)));
        }
        return response;
    }

    private static Request withBearer(Request req, String token) {
        if (token == null) return req;
        return req.newBuilder()
                .header("Authorization", "Bearer " + token)
                .build();
    }

    /**
     * Blocking token fetch. Returns null if signed-out or the fetch fails; the
     * request then proceeds without a bearer and the backend answers 401.
     */
    private static String idToken(boolean forceRefresh) {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) return null;
        try {
            GetTokenResult r = Tasks.await(user.getIdToken(forceRefresh), 15, TimeUnit.SECONDS);
            return r.getToken();
        } catch (Exception e) {
            return null;
        }
    }
}
