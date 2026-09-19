package com.example.loyaltyapp;

import android.content.Intent;
import android.content.SharedPreferences;
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
import java.util.Locale;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import android.util.Base64;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * Device-bound email + custom token flow:
 * 1) /auth/register sends a short-lived email link bound to a local verifier.
 * 2) Deep link myapp://verify?token=... opens here
 * 3) /auth/verify exchanges the link and local verifier for a custom token.
 * 4) signInWithCustomToken(customToken)
 * 5) Ensure user doc has full model; if missing fullName/birthday -> open
 * LoyaltyActivity on Profile tab
 * 6) Always upsert FCM token to backend devices collection (existing user or
 * fresh sign-in)
 */
public class SignUpActivity extends AppCompatActivity {

    private static final String TAG = "SignUpActivity";

    private com.example.loyaltyapp.databinding.ActivitySignUpBinding binding;
    private FirebaseAuth auth;
    private FirebaseFirestore db;
    private ApiService api;
    private SharedPreferences signInPrefs;
    private boolean busy;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = com.example.loyaltyapp.databinding.ActivitySignUpBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        auth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();
        api = ApiClient.getClient().create(ApiService.class);
        signInPrefs = getSharedPreferences("email_signin", MODE_PRIVATE);

        binding.continueButton.setOnClickListener(v -> onContinue());

        // If already signed in, ensure device token is registered, then go to main.
        FirebaseUser u = auth.getCurrentUser();
        if (u != null && !u.isAnonymous()) {
            Log.i(TAG, "User already signed in: " + u.getUid());
            // P0 security: never log raw FCM token value. Token is a long-lived
            // device credential. Anyone who reads logcat (USB, crash reports,
            // logging libs) could impersonate the device and receive its pushes.
            FirebaseMessaging.getInstance().getToken()
                    .addOnSuccessListener(t -> {
                        if (t != null && !t.isEmpty()) {
                            TokenRegistrar.ensureDevice(getApplicationContext(), t);
                        }
                    })
                    .addOnFailureListener(e -> Log.w("FCM", "existing user getToken failed"));
            ensureUserDocAndRoute(u.getUid(), u.getEmail());
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
        if (busy) return;
        String email = binding.EmailInput.getText() == null ? "" : binding.EmailInput.getText().toString().trim();
        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            binding.EmailInput.setError("Enter a valid email");
            return;
        }

        Map<String, String> body = new HashMap<>();
        body.put("email", email);
        String normalizedEmail = email.toLowerCase(Locale.ROOT);
        String verifier = signInPrefs.getString("verifier", null);
        // Reuse for resend/retry so an earlier delivered email remains usable on this phone.
        if (verifier == null || !normalizedEmail.equals(signInPrefs.getString("email", null))) {
            byte[] bytes = new byte[32];
            new SecureRandom().nextBytes(bytes);
            verifier = Base64.encodeToString(bytes, Base64.URL_SAFE | Base64.NO_WRAP | Base64.NO_PADDING);
            if (!signInPrefs.edit().putString("verifier", verifier).putString("email", normalizedEmail).commit()) {
                toast("Could not save sign-in request. Please try again.");
                return;
            }
        }
        try {
            body.put("challenge", Base64.encodeToString(MessageDigest.getInstance("SHA-256")
                    .digest(verifier.getBytes(StandardCharsets.UTF_8)),
                    Base64.URL_SAFE | Base64.NO_WRAP | Base64.NO_PADDING));
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
        setBusy(true);

        api.registerEmail(body).enqueue(new Callback<Map<String, Object>>() {
            @Override
            public void onResponse(Call<Map<String, Object>> call, Response<Map<String, Object>> resp) {
                setBusy(false);
                if (resp.isSuccessful() && resp.body() != null && Boolean.TRUE.equals(resp.body().get("ok"))) {
                    toast("Email sent. Open the link on this phone. Check spam too.");
                } else {
                    toast(resp.code() == 429 ? "Too many requests. Please wait before trying again."
                            : "Could not send email. Please try again later.");
                }
            }

            @Override
            public void onFailure(Call<Map<String, Object>> call, Throwable t) {
                setBusy(false);
                toast("Could not connect. Check your connection and try again.");
            }
        });
    }

    private void handleVerifyDeepLink(@NonNull Intent intent) {
        Uri data = intent.getData();
        if (data == null)
            return;

        String token = null;
        if ("myapp".equalsIgnoreCase(data.getScheme()) && "verify".equalsIgnoreCase(data.getHost())) {
            token = data.getQueryParameter("token");
        }

        if (token == null || token.isEmpty())
            return;
        verifyAndSignIn(token);
    }

    private void verifyAndSignIn(@NonNull String token) {
        if (busy) return;
        String verifier = signInPrefs.getString("verifier", null);
        if (verifier == null || !token.matches("[A-Za-z0-9_-]{43}")) {
            toast("Request an email on this phone, then open its link here.");
            return;
        }
        Map<String, String> body = new HashMap<>();
        body.put("token", token);
        body.put("verifier", verifier);
        setBusy(true);
        getIntent().setData(null);

        api.verifyToken(body).enqueue(new Callback<VerifyResponse>() {
            @Override
            public void onResponse(Call<VerifyResponse> call, Response<VerifyResponse> resp) {
                VerifyResponse vr = resp.body();
                if (!resp.isSuccessful() || vr == null || !vr.ok || vr.customToken == null
                        || vr.customToken.isEmpty()) {
                    setBusy(false);
                    // A refused account and a stale link need different fixes, so show the
                    // server's reason instead of blaming the link for both.
                    toast(resp.isSuccessful()
                            ? "Sign-in unavailable. Request a new email on this phone."
                            : ApiErrors.messageFor(resp));
                    return;
                }

                auth.signInWithCustomToken(vr.customToken)
                        .addOnSuccessListener(cred -> {
                            FirebaseUser fu = cred.getUser();
                            if (fu == null) {
                                setBusy(false);
                                toast("Auth error.");
                                return;
                            }

                            Log.i(TAG, "Sign-in OK: " + fu.getUid());
                            signInPrefs.edit().clear().apply();
                            ensureUserDocAndRoute(fu.getUid(), vr.email);

                            // Force-get FCM token once after fresh sign-in and upsert it.
                            // P0 security: do not log fcmToken value (device credential).
                            FirebaseMessaging.getInstance().getToken()
                                    .addOnSuccessListener(fcmToken -> {
                                        if (fcmToken != null && !fcmToken.isEmpty()) {
                                            TokenRegistrar.ensureDevice(getApplicationContext(), fcmToken);
                                        }
                                    })
                                    .addOnFailureListener(
                                            e -> Log.w("FCM", "fresh sign-in getToken failed"));
                        })
                        .addOnFailureListener(e -> {
                            Log.e(TAG, "signInWithCustomToken failed", e);
                            setBusy(false);
                            toast("Sign-in could not finish. Request a new email and try again.");
                        });
            }

            @Override
            public void onFailure(Call<VerifyResponse> call, Throwable t) {
                setBusy(false);
                toast("Connection interrupted. Request a new email if this link no longer works.");
            }
        });
    }

    /**
     * Ensure Firestore doc has your full model keys; then decide whether to open
     * Profile tab.
     * We DO NOT overwrite points/visits; we just ensure keys/timestamps exist.
     */
    private void ensureUserDocAndRoute(@NonNull String uid, String emailFromVerify) {
        final String emailLower = emailFromVerify != null ? emailFromVerify.toLowerCase(Locale.ROOT) : null;

        DocumentReference userRef = db.collection("users").document(uid);
        userRef.get().addOnSuccessListener(doc -> {
            boolean exists = doc.exists();

            // P0 security: do NOT read or write `isVerified` here. That field is
            // now owned by the backend (set true only after email verify succeeds
            // server-side). Routing decisions use `profileComplete` instead,
            // which is a separate client-owned flag for "user filled the form".
            String fullName = exists ? doc.getString("fullName") : null;
            String birthday = exists ? doc.getString("birthday") : null;
            String gender = exists ? doc.getString("gender") : null;
            Boolean profileCompleteB = exists ? doc.getBoolean("profileComplete") : null;
            String emailInDoc = exists ? doc.getString("email") : null;

            boolean profileComplete = profileCompleteB != null && profileCompleteB;

            // The email backend creates the profile. Existing profiles need no client write
            // just to sign in (also avoids older deployed rules rejecting uid/updatedAt).
            if (exists) {
                goToMain(!profileComplete || fullName == null || fullName.trim().isEmpty()
                        || birthday == null || birthday.trim().isEmpty()
                        || gender == null || gender.trim().isEmpty());
                return;
            }

            Map<String, Object> up = new HashMap<>();
            up.put("uid", uid);
            up.put("email", emailInDoc != null ? emailInDoc.toLowerCase() : emailLower);
            // Create branch only: seed all empty profile fields plus
            // profileComplete=false. Firestore rules permit create only when
            // points/visits/isVerified are absent or zero/false. We deliberately
            // omit `points`/`visits`/`isVerified` so backend remains sole owner
            // of those fields.
            if (!exists) {
                up.put("fullName", "");
                up.put("birthday", "");
                up.put("gender", "");
                up.put("phone", "");
                up.put("address", "");
                up.put("profileComplete", false);
                up.put("createdAt", FieldValue.serverTimestamp());
            }
            up.put("updatedAt", FieldValue.serverTimestamp());

            userRef.set(up, SetOptions.merge())
                    .addOnSuccessListener(unused -> {
                        boolean missingProfile = (fullName == null || fullName.trim().isEmpty()) ||
                                (birthday == null || birthday.trim().isEmpty()) ||
                                (gender == null || gender.trim().isEmpty()) ||
                                !profileComplete;
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
        i.putExtra("require_profile", forceProfile);
        startActivity(i);
        finish();
    }

    private void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_SHORT).show();
    }

    private void setBusy(boolean value) {
        busy = value;
        binding.continueButton.setEnabled(!value);
        binding.EmailInput.setEnabled(!value);
    }
}
