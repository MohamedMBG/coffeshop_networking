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
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
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
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;
import com.journeyapps.barcodescanner.BarcodeCallback;
import com.journeyapps.barcodescanner.BarcodeResult;
import com.journeyapps.barcodescanner.DecoratedBarcodeView;

import java.util.HashMap;
import java.util.Map;

/**
 * ScanFragment (safe & robust):
 * - Uses ZXing's DecoratedBarcodeView for continuous scanning.
 * - Accepts only a single VALID_QR (demo). Everything else ⇒ error overlay.
 * - Debounces fast repeated reads (handling flag + time window).
 * - Runs a Firestore transaction to atomically:
 *   * add +POINTS_EARN to /users/{uid}.points
 *   * add +1 to /users/{uid}.visits
 *   * append an /users/{uid}/activities event document
 * - Shows success/error overlays; auto-dismisses success and resumes scanning.
 * - Handles camera permission with Activity Result API.
 * - Includes flashlight toggle and safe lifecycle handling.
 */
public class ScanFragment extends Fragment {

    // ---------- Config ----------
    private static final String TAG = "ScanFragment";

    /** the exact QR text we accept. */
    private static final String VALID_QR = "https://view.page/MJXH0v";

    /** How many points a valid scan earns. */
    private static final int POINTS_EARN = 5;

    /** Minimum milliseconds between accepted scans (debounce window). */
    private static final long DEBOUNCE_MS = 1200;

    /** How long the success overlay stays visible before auto-hiding. */
    private static final long OVERLAY_MS = 1200;

    // ---------- Views ----------
    private DecoratedBarcodeView barcodeView; // camera + scanner view
    private ImageView btnFlashlight, btnClose;
    private FrameLayout successOverlay, errorOverlay;
    private TextView successMessage, successDetails, errorMessage;
    private Button btnRetry;
    private View btnManualEntry;

    // ---------- State ----------
    private final Handler ui = new Handler(Looper.getMainLooper());
    /** True when we're already handling a decode → prevents re-entrancy/double handling. */
    private boolean handling = false;
    /** Timestamp of last accepted scan, for debounce. */
    private long lastScanTs = 0L;
    /** Tracks flashlight state for toggling icon. */
    private boolean torchOn = false;

    // ---------- Firebase ----------
    private final FirebaseAuth auth = FirebaseAuth.getInstance();
    private final FirebaseFirestore db = FirebaseFirestore.getInstance();

    // ---------- Permission ----------
    private ActivityResultLauncher<String> cameraPermLauncher;

    // ---------- ZXing scan callback ----------
    private final BarcodeCallback callback = new BarcodeCallback() {
        @Override
        public void barcodeResult(BarcodeResult result) {
            // 1) Ignore invalid callbacks.
            if (result == null || result.getText() == null) return;

            // 2) Debounce: ignore scans if we're already handling one OR it came too soon.
            long now = System.currentTimeMillis();
            if (handling || (now - lastScanTs) < DEBOUNCE_MS) return;

            // 3) Lock handling until we finish, and remember this time.
            handling = true;
            lastScanTs = now;

            // 4) Get the raw text, give haptic feedback, and process.
            final String raw = result.getText().trim();
            vibrate(60);
            onDecoded(raw);
        }
    };

    // ---------- Fragment lifecycle ----------
    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {

        // Inflate the layout that contains:
        // - DecoratedBarcodeView with id @+id/barcodeScanner
        // - Overlays (successOverlay, errorOverlay)
        // - Buttons (flashlight, close, retry)
        View v = inflater.inflate(R.layout.fragment_scan, container, false);

        // Bind view references once.
        barcodeView    = v.findViewById(R.id.barcodeScanner);
        btnFlashlight  = v.findViewById(R.id.btnFlashlight);
        btnClose       = v.findViewById(R.id.btnClose);
        successOverlay = v.findViewById(R.id.successOverlay);
        errorOverlay   = v.findViewById(R.id.errorOverlay);
        successMessage = v.findViewById(R.id.successMessage);
        successDetails = v.findViewById(R.id.successDetails);
        errorMessage   = v.findViewById(R.id.errorMessage);
        btnRetry       = v.findViewById(R.id.btnRetry);
        btnManualEntry = v.findViewById(R.id.btnManualEntry); // optional

        // Start continuous decoding. The callback will be invoked on each detected barcode.
        barcodeView.decodeContinuous(callback);

        // UI events: flashlight toggle, close-back, retry (after error), manual entry demo.
        btnFlashlight.setOnClickListener(x -> toggleTorch());
        btnClose.setOnClickListener(x -> safeBack());
        btnRetry.setOnClickListener(x -> {
            hideError();
            allowNext();   // unlock handling so we can accept next scans
            resumeScanner();
        });
        if (btnManualEntry != null) {
            btnManualEntry.setOnClickListener(x -> showError("Manual entry not implemented"));
        }

        // Prepare camera permission launcher (Activity Result API).
        cameraPermLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestPermission(),
                granted -> {
                    if (granted) resumeScanner();
                    else toast("Camera permission is required.");
                }
        );

        // Ask for camera permission (or start immediately if already granted).
        ensurePermissionThenStart();
        return v;
    }

    @Override
    public void onResume() {
        super.onResume();
        // If we already have permission, resume the camera/decoder.
        if (hasCameraPermission()) resumeScanner();
    }

    @Override
    public void onPause() {
        super.onPause();
        // Always pause the camera to free resources when fragment not visible.
        pauseScanner();
        // Also hide overlays so they don't remain visible when leaving.
        hideSuccess();
        hideError();
        // Unlock handling so next time we can accept new scans.
        handling = false;
    }

    // ---------- Permission & camera control ----------
    /** Ensures CAMERA permission exists and starts the scanner, or requests permission. */
    private void ensurePermissionThenStart() {
        if (hasCameraPermission()) {
            resumeScanner();
        } else {
            cameraPermLauncher.launch(Manifest.permission.CAMERA);
        }
    }

    /** Returns true if CAMERA permission is granted. */
    private boolean hasCameraPermission() {
        return ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED;
    }

    /** Resumes the scanner safely (fragment must be added and view not null). */
    private void resumeScanner() {
        if (!isAdded() || barcodeView == null) return;
        barcodeView.resume();
    }

    /** Pauses the scanner (safe to call multiple times). */
    private void pauseScanner() {
        if (barcodeView != null) barcodeView.pause();
    }

    /** Toggles flashlight and updates icon; wrapped in try/catch for devices without torch. */
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

    /** Navigates back using OnBackPressedDispatcher safely. */
    private void safeBack() {
        try {
            requireActivity().getOnBackPressedDispatcher().onBackPressed();
        } catch (Throwable ignore) {}
    }

    // ---------- Decoding & validation ----------
    /** Called after a QR is decoded and passed debounce. Validates and routes the flow. */
    private void onDecoded(@NonNull String raw) {
        // DEMO RULE: only accept exactly VALID_QR (normalized comparison).
        if (!equalsNormalized(raw, VALID_QR)) {
            // Show an error overlay and keep scanner paused until user taps "Retry".
            showError("Invalid QR code");
            return;
        }
        // Valid scan ⇒ write points/visits/activity atomically with a Firestore transaction.
        saveEarnAndActivity(raw);
    }

    /** Compares two URLs/strings ignoring case, trimming, and stripping a trailing slash. */
    private boolean equalsNormalized(String a, String b) {
        if (a == null || b == null) return false;
        a = a.trim();
        b = b.trim();
        if (a.endsWith("/")) a = a.substring(0, a.length() - 1);
        if (b.endsWith("/")) b = b.substring(0, b.length() - 1);
        return a.equalsIgnoreCase(b);
    }

    // ---------- Firestore transaction ----------
    /**
     * Atomically:
     *  - increments /users/{uid}.points by POINTS_EARN
     *  - increments /users/{uid}.visits by 1
     *  - appends an event doc to /users/{uid}/activities
     */
    private void saveEarnAndActivity(@NonNull String payload) {
        // Must have a signed-in Firebase user (Auth).
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) {
            showError("Not signed in");
            return;
        }

        // Reference to the user's document.
        DocumentReference userRef = db.collection("users").document(user.getUid());

        // Use a transaction for consistency under concurrent updates.
        db.runTransaction(tr -> {
            // 1) Read current user document snapshot.
            DocumentSnapshot snap = tr.get(userRef);

            // 2) Pull existing counters (default to 0 if missing).
            long curPoints = snap.getLong("points") == null ? 0L : snap.getLong("points");
            long curVisits = snap.getLong("visits") == null ? 0L : snap.getLong("visits");

            // 3) Prepare updated fields (merge to preserve other fields).
            Map<String, Object> up = new HashMap<>();
            up.put("points", curPoints + POINTS_EARN);
            up.put("visits", curVisits + 1);
            up.put("updatedAt", FieldValue.serverTimestamp());
            tr.set(userRef, up, SetOptions.merge());

            // 4) Prepare an activity/event record for the history list.
            Map<String, Object> ev = new HashMap<>();
            ev.put("type", "scan");
            ev.put("points", POINTS_EARN);
            ev.put("payload", payload);     // what was scanned (for traceability)
            ev.put("storeName", "");        // optional: fill if you know the store
            ev.put("ts", FieldValue.serverTimestamp());
            tr.set(userRef.collection("activities").document(), ev);

            return null; // transaction requires a return
        }).addOnSuccessListener(unused -> {
            // Transaction succeeded → show success overlay, then auto-dismiss & resume scan.
            showSuccess("+" + POINTS_EARN + " points", "Scan recorded");
        }).addOnFailureListener(e -> {
            // Any failure (permission, rules, offline, etc.)
            Log.e(TAG, "Transaction failed", e);
            showError("Failed to save scan");
        });
    }

    // ---------- Overlays & feedback ----------
    /** Shows success overlay for OVERLAY_MS, then hides it and resumes scanning safely. */
    private void showSuccess(String main, String details) {
        postUi(() -> {
            if (!isAdded() || getView() == null) return;

            // Pause scanning while the overlay is visible to avoid reading again underneath.
            pauseScanner();

            // Update overlay text.
            if (successMessage != null) successMessage.setText(main);
            if (successDetails != null) successDetails.setText(details == null ? "" : details);

            // Show overlay.
            if (successOverlay != null) successOverlay.setVisibility(View.VISIBLE);

            // Auto-hide after OVERLAY_MS, then allow next scans and resume camera.
            ui.postDelayed(() -> {
                hideSuccess();
                allowNext();  // unlock handling flag & refresh lastScanTs
                resumeScanner();
            }, OVERLAY_MS);
        });
    }

    /** Hides success overlay (safe-guarded with isAdded/getView checks). */
    private void hideSuccess() {
        postUi(() -> {
            if (!isAdded() || getView() == null) return;
            if (successOverlay != null) successOverlay.setVisibility(View.GONE);
        });
    }

    /** Shows error overlay and keeps scanner paused until user hits the Retry button. */
    private void showError(String msg) {
        postUi(() -> {
            if (!isAdded() || getView() == null) return;
            pauseScanner();
            if (errorMessage != null) errorMessage.setText(msg);
            if (errorOverlay != null) errorOverlay.setVisibility(View.VISIBLE);
        });
    }

    /** Hides error overlay. */
    private void hideError() {
        postUi(() -> {
            if (!isAdded() || getView() == null) return;
            if (errorOverlay != null) errorOverlay.setVisibility(View.GONE);
        });
    }

    /** Unlocks handling so new scans can be accepted; also refreshes lastScanTs. */
    private void allowNext() {
        handling = false;
        lastScanTs = System.currentTimeMillis();
    }

    /** Gives a short vibration if possible (API 26+ uses VibrationEffect). */
    private void vibrate(int ms) {
        try {
            if (!isAdded()) return;
            Vibrator vib = (Vibrator) requireContext().getSystemService(VIBRATOR_SERVICE);
            if (vib == null) return;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vib.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE));
            } else {
                //noinspection deprecation
                vib.vibrate(ms);
            }
        } catch (Throwable ignore) {}
    }

    /** Shows a Toast safely on the main thread (no-op if fragment is not added). */
    private void toast(String s) {
        postUi(() -> {
            if (!isAdded()) return;
            Toast.makeText(requireContext(), s, Toast.LENGTH_SHORT).show();
        });
    }

    /** Utility to post UI work on the main thread handler. */
    private void postUi(Runnable r) {
        ui.post(r);
    }
}
