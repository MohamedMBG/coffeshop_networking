package com.example.loyaltyapp;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.WindowManager;

import androidx.activity.EdgeToEdge;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;

public class MainActivity extends AppCompatActivity {


    private ListenerRegistration gateListener;
    private static final int SPLASH_DURATION = 2000; // 2 seconds
    private FirebaseAuth auth;
    private FirebaseFirestore db;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Optional: make the splash full-screen
        getWindow().setFlags(
                WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN
        );

        setContentView(R.layout.activity_main); // your splash layout (logo + background)

        auth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        // Delay splash for 2 seconds then decide destination
        new Handler(Looper.getMainLooper()).postDelayed(this::decideNextScreen, SPLASH_DURATION);
    }

    @Override protected void onStart() {
        super.onStart();
        gateListener = db.collection("meta").document("app_status")
                .addSnapshotListener((doc, err) -> {
                    if (err != null || doc == null) return;
                    boolean active = Boolean.TRUE.equals(doc.getBoolean("isActive"));
                    if (!active) {
                        Intent i = new Intent(this, BlockedActivity.class);
                        i.putExtra("reason", doc.getString("message"));
                        startActivity(i);
                        finish();
                    }
                });
    }

    @Override protected void onStop() {
        super.onStop();
        if (gateListener != null) {
            gateListener.remove();
            gateListener = null;
        }
    }

    private void decideNextScreen() {
        FirebaseUser user = auth.getCurrentUser();

        if (user == null) {
            // No user signed in → go to SignUp
            goToSignUp();
            return;
        }

        // Check if verified in Firestore
        db.collection("users").document(user.getUid())
                .get()
                .addOnSuccessListener(this::handleUserDoc)
                .addOnFailureListener(e -> goToSignUp());
    }

    private void handleUserDoc(DocumentSnapshot snapshot) {
        boolean verified = snapshot.exists() && Boolean.TRUE.equals(snapshot.getBoolean("isVerified"));

        if (verified) goToLoyalty(false);
        else goToLoyalty(true);
    }

    private void goToSignUp() {
        startActivity(new Intent(this, SignUpActivity.class));
        finish();
    }

    private void goToLoyalty(boolean requireProfile) {
        Intent i = new Intent(this, LoyaltyActivity.class);
        i.putExtra("require_profile", requireProfile);
        startActivity(i);
        finish();
    }
}