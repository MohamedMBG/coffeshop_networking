package com.example.loyaltyapp;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.util.Patterns;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.example.loyaltyapp.ApiService.VerifyResponse;
import com.example.loyaltyapp.services.TokenRegistrar;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;
import com.google.firebase.messaging.FirebaseMessaging;

import java.util.HashMap;
import java.util.Map;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * SMTP + custom token flow:
 * 1) /api/register sends email with verify.html?token=...
 * 2) Deep link myapp://verify?token=... opens here
 * 3) /api/verify -> { ok, email, customToken }
 * 4) signInWithCustomToken(customToken)
 * 5) Ensure user doc has full model; if missing fullName/birthday -> open LoyaltyActivity on Profile tab
 * 6) Always upsert FCM token to backend devices collection (existing user or fresh sign-in)
 */
public class SignUpActivity extends AppCompatActivity {

    private static final String TAG = "SignUpActivity";

    private EditText emailInput;
    private FirebaseAuth auth;
    private FirebaseFirestore db;
    private ApiService api;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sign_up);

        auth = FirebaseAuth.getInstance();
        db   = FirebaseFirestore.getInstance();
        api  = ApiClient.getClient().create(ApiService.class);

        emailInput = findViewById(R.id.EmailInput);
        findViewById(R.id.continueButton).setOnClickListener(v -> onContinue());

        // If already signed in, ensure device token is registered, then go to main.
        FirebaseUser u = auth.getCurrentUser();
        if (u != null && !u.isAnonymous()) {
            Log.i(TAG, "User already signed in: " + u.getUid());
            FirebaseMessaging.getInstance().getToken()
                    .addOnSuccessListener(t -> {
                        Log.i("FCM", "existing user getToken() -> " + t);
                        if (t != null && !t.isEmpty()) {
                            TokenRegistrar.ensureDevice(t, "client");
                        }
                    })
                    .addOnFailureListener(e ->
                            Log.w("FCM", "existing user getToken failed: " + e.getMessage())
                    );
            goToMain(false);
            return;
        }

        // Handle deep link if the app was opened from the verification email.
        handleVerifyDeepLink(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleVerifyDeepLink(intent);
    }

    private void onContinue() {
        String email = emailInput.getText() == null ? "" : emailInput.getText().toString().trim();
        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            emailInput.setError("Enter a valid email"); return;
        }

        Map<String, String> body = new HashMap<>();
        body.put("email", email);

        api.registerEmail(body).enqueue(new Callback<Map<String, Object>>() {
            @Override public void onResponse(Call<Map<String, Object>> call, Response<Map<String, Object>> resp) {
                if (resp.isSuccessful() && resp.body() != null && Boolean.TRUE.equals(resp.body().get("ok"))) {
                    toast("Verification email sent. Check your inbox.");
                } else {
                    toast("Failed to send verification.");
                }
            }
            @Override public void onFailure(Call<Map<String, Object>> call, Throwable t) {
                toast("Network error: " + t.getMessage());
            }
        });
    }

    private void handleVerifyDeepLink(@NonNull Intent intent) {
        Uri data = intent.getData();
        if (data == null) return;

        String token = null;
        if ("myapp".equalsIgnoreCase(data.getScheme()) && "verify".equalsIgnoreCase(data.getHost())) {
            token = data.getQueryParameter("token");
        }

        if (token == null || token.isEmpty()) return;
        verifyAndSignIn(token);
    }

    private void verifyAndSignIn(@NonNull String token) {
        Map<String, String> body = new HashMap<>();
        body.put("token", token);

        api.verifyToken(body).enqueue(new Callback<VerifyResponse>() {
            @Override public void onResponse(Call<VerifyResponse> call, Response<VerifyResponse> resp) {
                VerifyResponse vr = resp.body();
                if (!resp.isSuccessful() || vr == null || !vr.ok || vr.customToken == null || vr.customToken.isEmpty()) {
                    toast("Verification failed."); return;
                }

                auth.signInWithCustomToken(vr.customToken)
                        .addOnSuccessListener(cred -> {
                            FirebaseUser fu = cred.getUser();
                            if (fu == null) { toast("Auth error."); return; }

                            Log.i(TAG, "Sign-in OK: " + fu.getUid());
                            ensureUserDocAndRoute(fu.getUid(), vr.email);

                            // Force-get FCM token once after fresh sign-in and upsert it.
                            FirebaseMessaging.getInstance().getToken()
                                    .addOnSuccessListener(fcmToken -> {
                                        Log.i("FCM", "fresh sign-in getToken() -> " + fcmToken);
                                        if (fcmToken != null && !fcmToken.isEmpty()) {
                                            TokenRegistrar.ensureDevice(fcmToken, "client");
                                        }
                                    })
                                    .addOnFailureListener(e ->
                                            Log.w("FCM", "fresh sign-in getToken failed: " + e.getMessage())
                                    );
                        })
                        .addOnFailureListener(e -> {
                            Log.e(TAG, "signInWithCustomToken failed", e);
                            toast("Sign-in failed: " + e.getMessage());
                        });
            }

            @Override public void onFailure(Call<VerifyResponse> call, Throwable t) {
                toast("Network error: " + t.getMessage());
            }
        });
    }

    /**
     * Ensure Firestore doc has your full model keys; then decide whether to open Profile tab.
     * We DO NOT overwrite points/visits; we just ensure keys/timestamps exist.
     */
    private void ensureUserDocAndRoute(@NonNull String uid, String emailFromVerify) {
        final String emailLower = emailFromVerify != null ? emailFromVerify.toLowerCase() : null;

        DocumentReference userRef = db.collection("users").document(uid);
        userRef.get().addOnSuccessListener(doc -> {
            boolean exists = doc.exists();

            String fullName = exists ? doc.getString("fullName") : null;
            String birthday = exists ? doc.getString("birthday") : null;
            Number pointsN  = exists ? doc.getLong("points") : null;
            Number visitsN  = exists ? doc.getLong("visits") : null;
            Boolean verifiedB = exists ? doc.getBoolean("isVerified") : null;
            String emailInDoc = exists ? doc.getString("email") : null;

            int points = pointsN == null ? 0 : pointsN.intValue();
            int visits = visitsN == null ? 0 : visitsN.intValue();
            boolean isVerified = verifiedB != null ? verifiedB : true;

            Map<String, Object> up = new HashMap<>();
            up.put("uid", uid);
            up.put("email", emailInDoc != null ? emailInDoc.toLowerCase() : emailLower);
            if (!exists) {
                up.put("fullName", fullName != null ? fullName : "");
                up.put("birthday", birthday != null ? birthday : "");
                up.put("points", points);
                up.put("visits", visits);
                up.put("isVerified", isVerified);
                up.put("createdAt", FieldValue.serverTimestamp());
            }
            up.put("updatedAt", FieldValue.serverTimestamp());

            userRef.set(up, SetOptions.merge())
                    .addOnSuccessListener(unused -> {
                        boolean missingProfile =
                                (fullName == null || fullName.trim().isEmpty()) ||
                                        (birthday == null || birthday.trim().isEmpty());
                        goToMain(missingProfile);
                    })
                    .addOnFailureListener(e -> {
                        Log.e(TAG, "Profile init failed", e);
                        toast("Profile init failed: " + e.getMessage());
                        goToMain(true);
                    });

        }).addOnFailureListener(e -> {
            Log.e(TAG, "Profile check failed", e);
            toast("Profile check failed: " + e.getMessage());
            goToMain(true);
        });
    }

    /** Pass a flag so LoyaltyActivity selects the Profile tab */
    private void goToMain(boolean forceProfile) {
        Intent i = new Intent(this, LoyaltyActivity.class);
        i.putExtra("force_profile", forceProfile);
        startActivity(i);
        finish();
    }

    private void toast(String s) { Toast.makeText(this, s, Toast.LENGTH_SHORT).show(); }
}
