package com.example.loyaltyapp.fragments;

import static android.content.Context.VIBRATOR_SERVICE;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.util.Log;
import android.view.*;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.example.loyaltyapp.R;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.*;
import com.google.firebase.auth.FirebaseAuth;
import com.journeyapps.barcodescanner.*;

import java.util.HashMap;
import java.util.Map;

/**
 * Safe ScanFragment:
 * - ZXing DecoratedBarcodeView
 * - Accepts only VALID_QR
 * - Debounced, main-thread UI updates, attached-checks
 * - Firestore transaction: +5 points, +1 visit, add /users/{uid}/activities doc
 * - Overlays auto-dismiss and resume scanning
 */
public class ScanFragment extends Fragment {

    private static final String TAG = "ScanFragment";
    private static final String VALID_QR = "https://view.page/MJXH0v";
    private static final int POINTS_EARN = 5;
    private static final long DEBOUNCE_MS = 1200;
    private static final long OVERLAY_MS = 1200;

    // Views
    private DecoratedBarcodeView barcodeView;
    private ImageView btnFlashlight, btnClose;
    private FrameLayout successOverlay, errorOverlay;
    private TextView successMessage, successDetails, errorMessage;
    private Button btnRetry;
    private View btnManualEntry; // if you wired it in layout

    // State
    private final Handler ui = new Handler(Looper.getMainLooper());
    private boolean handling;       // guards decode re-entry
    private long lastScanTs = 0L;
    private boolean torchOn = false;

    // Firebase
    private final FirebaseAuth auth = FirebaseAuth.getInstance();
    private final FirebaseFirestore db = FirebaseFirestore.getInstance();

    // Permission
    private ActivityResultLauncher<String> cameraPermLauncher;

    // ZXing callback
    private final BarcodeCallback callback = new BarcodeCallback() {
        @Override public void barcodeResult(BarcodeResult result) {
            if (result == null || result.getText() == null) return;
            long now = System.currentTimeMillis();
            if (handling || (now - lastScanTs) < DEBOUNCE_MS) return;

            handling = true;
            lastScanTs = now;

            final String raw = result.getText().trim();
            vibrate(60);
            onDecoded(raw);
        }
    };

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.fragment_scan, container, false);

        // Bind
        barcodeView    = v.findViewById(R.id.barcodeScanner);
        btnFlashlight  = v.findViewById(R.id.btnFlashlight);
        btnClose       = v.findViewById(R.id.btnClose);
        successOverlay = v.findViewById(R.id.successOverlay);
        errorOverlay   = v.findViewById(R.id.errorOverlay);
        successMessage = v.findViewById(R.id.successMessage);
        successDetails = v.findViewById(R.id.successDetails);
        errorMessage   = v.findViewById(R.id.errorMessage);
        btnRetry       = v.findViewById(R.id.btnRetry);
        btnManualEntry = v.findViewById(R.id.btnManualEntry);

        // ZXing
        barcodeView.decodeContinuous(callback);

        // UI events
        btnFlashlight.setOnClickListener(v1 -> toggleTorch());
        btnClose.setOnClickListener(v12 -> safeBack());
        btnRetry.setOnClickListener(v13 -> {
            hideError();
            allowNext();
            resumeScanner();
        });
        if (btnManualEntry != null) {
            btnManualEntry.setOnClickListener(v14 -> {
                showError("Manual entry not implemented");
            });
        }

        // Permission launcher
        cameraPermLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestPermission(),
                granted -> { if (granted) resumeScanner(); else toast("Camera permission is required."); }
        );

        ensurePermissionThenStart();
        return v;
    }

    // ------- Lifecycle -------
    @Override public void onResume() {
        super.onResume();
        if (hasCameraPermission()) resumeScanner();
    }

    @Override public void onPause() {
        super.onPause();
        pauseScanner();
        // don’t keep overlays on other screens
        hideSuccess();
        hideError();
        handling = false;
    }

    // ------- Permission / Camera -------
    private void ensurePermissionThenStart() {
        if (hasCameraPermission()) {
            resumeScanner();
        } else {
            cameraPermLauncher.launch(Manifest.permission.CAMERA);
        }
    }

    private boolean hasCameraPermission() {
        return ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED;
    }

    private void resumeScanner() {
        if (!isAdded() || barcodeView == null) return;
        barcodeView.resume();
    }

    private void pauseScanner() {
        if (barcodeView != null) barcodeView.pause();
    }

    private void toggleTorch() {
        try {
            if (torchOn) {
                barcodeView.setTorchOff();
                torchOn = false;
                btnFlashlight.setImageResource(R.drawable.ic_flashlight_off);
            } else {
                barcodeView.setTorchOn();
                torchOn = true;
                btnFlashlight.setImageResource(R.drawable.ic_flashlight_on);
            }
        } catch (Throwable t) {
            Log.w(TAG, "Torch toggle failed", t);
        }
    }

    private void safeBack() {
        try {
            requireActivity().getOnBackPressedDispatcher().onBackPressed();
        } catch (Throwable ignore) {}
    }

    // ------- Decode handling -------
    private void onDecoded(@NonNull String raw) {
        // accept only your code
        if (!equalsNormalized(raw, VALID_QR)) {
            showError("Invalid QR code");
            // don’t resume immediately -> user sees overlay, taps retry
            return;
        }
        // Valid → save activity atomically
        saveEarnAndActivity(raw);
    }

    private boolean equalsNormalized(String a, String b) {
        if (a == null || b == null) return false;
        a = a.trim(); b = b.trim();
        if (a.endsWith("/")) a = a.substring(0, a.length() - 1);
        if (b.endsWith("/")) b = b.substring(0, b.length() - 1);
        return a.equalsIgnoreCase(b);
    }

    private void saveEarnAndActivity(@NonNull String payload) {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) {
            showError("Not signed in");
            return;
        }
        DocumentReference userRef = db.collection("users").document(user.getUid());

        db.runTransaction(tr -> {
            DocumentSnapshot snap = tr.get(userRef);
            long curPoints = snap.getLong("points") == null ? 0L : snap.getLong("points");
            long curVisits = snap.getLong("visits") == null ? 0L : snap.getLong("visits");

            Map<String, Object> up = new HashMap<>();
            up.put("points", curPoints + POINTS_EARN);
            up.put("visits", curVisits + 1);
            up.put("updatedAt", FieldValue.serverTimestamp());
            tr.set(userRef, up, SetOptions.merge());

            Map<String, Object> ev = new HashMap<>();
            ev.put("type", "scan");
            ev.put("points", POINTS_EARN);
            ev.put("payload", payload);
            ev.put("storeName", ""); // optional
            ev.put("ts", FieldValue.serverTimestamp());
            tr.set(userRef.collection("activities").document(), ev);

            return null;
        }).addOnSuccessListener(unused -> {
            showSuccess("+" + POINTS_EARN + " points", "Scan recorded");
        }).addOnFailureListener(e -> {
            Log.e(TAG, "Transaction failed", e);
            showError("Failed to save scan");
        });
    }

    // ------- Overlays & feedback -------
    private void showSuccess(String main, String details) {
        postUi(() -> {
            if (!isAdded() || getView() == null) return;
            pauseScanner(); // freeze camera while showing overlay
            if (successMessage != null) successMessage.setText(main);
            if (successDetails != null) successDetails.setText(details == null ? "" : details);
            if (successOverlay != null) successOverlay.setVisibility(View.VISIBLE);

            ui.postDelayed(() -> {
                hideSuccess();
                allowNext();
                resumeScanner();   // safe resume
            }, OVERLAY_MS);
        });
    }

    private void hideSuccess() {
        postUi(() -> {
            if (!isAdded() || getView() == null) return;
            if (successOverlay != null) successOverlay.setVisibility(View.GONE);
        });
    }

    private void showError(String msg) {
        postUi(() -> {
            if (!isAdded() || getView() == null) return;
            pauseScanner();
            if (errorMessage != null) errorMessage.setText(msg);
            if (errorOverlay != null) errorOverlay.setVisibility(View.VISIBLE);
        });
    }

    private void hideError() {
        postUi(() -> {
            if (!isAdded() || getView() == null) return;
            if (errorOverlay != null) errorOverlay.setVisibility(View.GONE);
        });
    }

    private void allowNext() {
        handling = false;
        lastScanTs = System.currentTimeMillis();
    }

    private void vibrate(int ms) {
        try {
            if (!isAdded()) return;
            Vibrator vib = (Vibrator) requireContext().getSystemService(VIBRATOR_SERVICE);
            if (vib == null) return;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vib.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE));
            } else {
                vib.vibrate(ms);
            }
        } catch (Throwable ignore) {}
    }

    private void toast(String s) {
        postUi(() -> {
            if (!isAdded()) return;
            Toast.makeText(requireContext(), s, Toast.LENGTH_SHORT).show();
        });
    }

    private void postUi(Runnable r) { ui.post(r); }
}
