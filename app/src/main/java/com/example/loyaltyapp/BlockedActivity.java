package com.example.loyaltyapp;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;


public class BlockedActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_blocked);

        String msg = getIntent().getStringExtra("reason");
        TextView tv = findViewById(R.id.blockMsg);
        tv.setText(msg != null ? msg : "Application désactivée.");

        Button support = findViewById(R.id.btnSupport);
        Button quit    = findViewById(R.id.btnQuit);

        support.setOnClickListener(v -> {
            // Example: email to your support
            Intent email = new Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:support@mds.example"));
            email.putExtra(Intent.EXTRA_SUBJECT, "Réactivation abonnement");
            startActivity(Intent.createChooser(email, "Contacter le support"));
        });

        quit.setOnClickListener(v -> finish());
    }

    @Override public void onBackPressed() {
        // Optional: prevent "back" into the app
        super.onBackPressed();
        finish();
    }
}