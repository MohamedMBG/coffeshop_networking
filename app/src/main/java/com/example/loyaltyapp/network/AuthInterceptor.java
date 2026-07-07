package com.example.loyaltyapp.network;

import androidx.annotation.Nullable;

import com.google.android.gms.tasks.Tasks;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.GetTokenResult;

import java.io.IOException;

import okhttp3.Interceptor;
import okhttp3.Request;
import okhttp3.Response;

/**
 * Attaches the Firebase ID token as an {@code Authorization: Bearer} header on
 * every outgoing request, so the backend can authenticate the caller.
 *
 * <p>Runs on OkHttp's background dispatcher thread, so blocking on the token
 * task via {@link Tasks#await} is safe here (never call this on the main
 * thread). A cached token is used first ({@code getIdToken(false)}); if the
 * backend rejects it with a 401 — e.g. the token expired or a role/claim was
 * just granted — the token is force-refreshed once and the request replayed.
 */
public final class AuthInterceptor implements Interceptor {

    @Nullable
    private final FirebaseAuth injectedAuth;

    public AuthInterceptor() {
        // Resolve FirebaseAuth lazily (see auth()): constructing this must not
        // touch Firebase, so ApiClient can be built in plain-JVM unit tests
        // where Firebase is never initialized.
        this.injectedAuth = null;
    }

    // Constructor injection keeps the interceptor unit-testable with a mocked auth.
    public AuthInterceptor(FirebaseAuth auth) {
        this.injectedAuth = auth;
    }

    private FirebaseAuth auth() {
        return injectedAuth != null ? injectedAuth : FirebaseAuth.getInstance();
    }

    @Override
    public Response intercept(Chain chain) throws IOException {
        Request original = chain.request();
        FirebaseUser user = auth().getCurrentUser();

        // No signed-in user: send the request as-is and let the backend answer
        // with 401. We never fabricate or omit-then-guess an identity.
        if (user == null) {
            return chain.proceed(original);
        }

        String token = fetchToken(user, false);
        if (token == null) {
            // Couldn't prove who we are; proceed unauthenticated rather than
            // block the call — backend will reject if the endpoint needs auth.
            return chain.proceed(original);
        }

        Response response = chain.proceed(withBearer(original, token));

        // One force-refresh retry on 401. Covers an expired cached token and
        // the "role just granted, old token lacks the claim" case.
        if (response.code() == 401) {
            String refreshed = fetchToken(user, true);
            if (refreshed != null && !refreshed.equals(token)) {
                response.close();
                response = chain.proceed(withBearer(original, refreshed));
            }
        }

        return response;
    }

    private static Request withBearer(Request request, String token) {
        return request.newBuilder()
                .header("Authorization", "Bearer " + token)
                .build();
    }

    /** Blocking token fetch. Returns {@code null} if the token can't be obtained. */
    @Nullable
    private static String fetchToken(FirebaseUser user, boolean forceRefresh) {
        try {
            GetTokenResult result = Tasks.await(user.getIdToken(forceRefresh));
            return result != null ? result.getToken() : null;
        } catch (Exception e) {
            // ExecutionException / InterruptedException — treat as "no token".
            // Do not log: the token itself is a credential.
            return null;
        }
    }
}
