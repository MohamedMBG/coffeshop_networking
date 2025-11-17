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
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.google.firebase.firestore.SetOptions;
import com.journeyapps.barcodescanner.BarcodeCallback;
import com.journeyapps.barcodescanner.BarcodeResult;
import com.journeyapps.barcodescanner.DecoratedBarcodeView;

import java.util.HashMap;
 import java.util.Map;

/**
 * ScanFragment (voucher redemption):
 * - Scans a QR that contains ONLY the Firestore document ID of /earn_codes/{voucherId}.
 * - Runs a Firestore TRANSACTION to:
 *      * verify voucher exists, pending, and not expired,
 *      * set voucher -> redeemed (with redeemedAt, redeemedByUid),
 *      * increment user's points/visits,
 *      * append an activity entry.
 * - Debounces scans, overlays for success/error, flashlight, permission, lifecycle safe.
 */
public class ScanFragment extends Fragment {

    // ---------- Config ----------
    private static final String TAG = "ScanFragment";

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
    private boolean handling = false;     // prevents re-entrancy
    private long lastScanTs = 0L;         // debounce window
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

    // ---------- Fragment lifecycle ----------
    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {

        View v = inflater.inflate(R.layout.fragment_scan, container, false);

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
        barcodeView.decodeContinuous(callback);

        btnFlashlight.setOnClickListener(x -> toggleTorch());
        btnClose.setOnClickListener(x -> safeBack());
        btnRetry.setOnClickListener(x -> {
            hideError();
            allowNext();
            resumeScanner();
        });
        if (btnManualEntry != null) {
            btnManualEntry.setOnClickListener(x -> showError("Manual entry not implemented"));
        }

        cameraPermLauncher = registerForActivityResult( new ActivityResultContracts.RequestPermission(), granted -> {
            if (granted) resumeScanner();
            else toast("Camera permission is required.");
        });

        ensurePermissionThenStart();
        return v;
    }

    @Override
    public void onResume() {
        super.onResume();
        if (hasCameraPermission()) resumeScanner();
    }

    @Override
    public void onPause() {
        super.onPause();
        pauseScanner();
        hideSuccess();
        hideError();
        handling = false;
    }

    // ---------- Permission & camera control ----------
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

    // ---------- Decoding & redemption ----------
    /** After decode: interpret text as a Firestore voucherId and redeem it with a transaction. */
    private void onDecoded(@NonNull String raw) {
        // The cashier encodes ONLY the document ID inside the QR.
        String voucherId = raw.trim();

        if (voucherId.isEmpty()) {
            showError("Invalid QR");
            return;
        }
        redeemVoucher(voucherId);
    }

    /**
     * Firestore transaction:
     *  - Read /earn_codes/{voucherId}
     *  - Validate: exists, status == "pending", not expired.
     *  - Update voucher -> redeemed (redeemedAt, redeemedByUid)
     *  - Update /users/{uid}: points += voucher.points, visits += 1
     *  - Append /users/{uid}/activities entry with details
     */

    private void redeemVoucher(@NonNull String voucherId) {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) { showError("Not signed in"); return; }

        DocumentReference voucherRef = db.collection("earn_codes").document(voucherId);
        DocumentReference userRef    = db.collection("users").document(user.getUid());
        DocumentReference activityRef= userRef.collection("activities").document(); // new doc id (write-only)

        db.runTransaction(tr -> {
            // --- READS (all reads must be done before any writes) ---
            DocumentSnapshot vSnap = tr.get(voucherRef);           // read 1
            DocumentSnapshot uSnap = tr.get(userRef);              // read 2

            if (!vSnap.exists()) {
                throw new FirebaseFirestoreException("Invalid code",
                        FirebaseFirestoreException.Code.NOT_FOUND);
            }

            String status = vSnap.getString("status");
            Long validForSec = vSnap.getLong("validForSec");
            com.google.firebase.Timestamp createdAt = vSnap.getTimestamp("createdAt");
            Integer points = vSnap.getLong("points") == null ? null : vSnap.getLong("points").intValue();

            if (status == null || validForSec == null || createdAt == null || points == null) {
                throw new FirebaseFirestoreException("Malformed voucher",
                        FirebaseFirestoreException.Code.DATA_LOSS);
            }
            if (!"pending".equals(status)) {
                throw new FirebaseFirestoreException("Already " + status,
                        FirebaseFirestoreException.Code.ABORTED);
            }
            long ageMs = System.currentTimeMillis() - createdAt.toDate().getTime();
            if (ageMs > validForSec * 1000L) {
                throw new FirebaseFirestoreException("Expired",
                        FirebaseFirestoreException.Code.ABORTED);
            }

            long curPoints = uSnap.getLong("points") == null ? 0L : uSnap.getLong("points");
            long curVisits = uSnap.getLong("visits") == null ? 0L : uSnap.getLong("visits");

            // --- WRITES (after all reads) ---
            Map<String, Object> vUpd = new HashMap<>();
            vUpd.put("status", "redeemed");
            vUpd.put("redeemedAt", FieldValue.serverTimestamp());
            vUpd.put("redeemedByUid", user.getUid());
            tr.update(voucherRef, vUpd);                            // write 1

            Map<String, Object> uUpd = new HashMap<>();
            uUpd.put("points", curPoints + points);
            uUpd.put("visits", curVisits + 1);
            uUpd.put("updatedAt", FieldValue.serverTimestamp());
            tr.set(userRef, uUpd, SetOptions.merge());              // write 2

            Map<String, Object> ev = new HashMap<>();
            ev.put("type", "earn");
            ev.put("points", points);
            ev.put("voucherId", voucherId);
            ev.put("ts", FieldValue.serverTimestamp());
            String orderNo = vSnap.getString("orderNo");
            Double amountMAD = vSnap.getDouble("amountMAD");
            if (orderNo != null)   ev.put("orderNo", orderNo);
            if (amountMAD != null) ev.put("amountMAD", amountMAD);
            tr.set(activityRef, ev);

            return points; // pass back to UI
        }).addOnSuccessListener(points -> {
            showSuccess("+" + points + " points", "Scan recorded");
        }).addOnFailureListener(e -> {
            String msg = e.getMessage();
            if (msg == null) msg = "Failed to redeem";
            if (msg.contains("Already")) msg = "Already redeemed";
            showError(msg);
            Log.e(TAG, "Redeem failed", e);
        });
    }


    // ---------- Overlays & feedback ----------
    private void showSuccess(String main, String details) {
        postUi(() -> {
            if (!isAdded() || getView() == null) return;
            pauseScanner();
            if (successMessage != null) successMessage.setText(main);
            if (successDetails != null) successDetails.setText(details == null ? "" : details);
            if (successOverlay != null) successOverlay.setVisibility(View.VISIBLE);

            ui.postDelayed(() -> {
                hideSuccess();
                allowNext();
                resumeScanner();
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
                //noinspection deprecation
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

    private void postUi(Runnable r) {
        ui.post(r);
    }
}
