package com.example.loyaltyapp.fragments;

// Standard Android Imports
import android.Manifest;
import android.content.Context;
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

// AndroidX Imports
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

// Firebase Imports
import com.example.loyaltyapp.R;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreException;

// ZXing (Barcode) Imports
import com.journeyapps.barcodescanner.BarcodeCallback;
import com.journeyapps.barcodescanner.BarcodeResult;
import com.journeyapps.barcodescanner.DecoratedBarcodeView;

// Java Imports
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

public class ScanFragment extends Fragment {

    private static final String TAG = "ScanFragment";
    private static final long DEBOUNCE_MS = 1500;
    private static final long OVERLAY_MS = 2000;
    // 4 Hours in Milliseconds
    private static final long VISIT_TIME_WINDOW_MILLIS = 4 * 60 * 60 * 1000;

    // UI VIEWS
    private DecoratedBarcodeView barcodeView;
    private ImageView btnFlashlight, btnClose;
    private FrameLayout successOverlay, errorOverlay;
    private TextView successMessage, successDetails, errorMessage;
    private Button btnRetry;
    private View btnManualEntry;

    // STATE
    private final Handler uiHandler = new Handler(Looper.getMainLooper());
    private boolean isProcessingScan = false;
    private long lastScanTimestamp = 0L;
    private boolean isTorchOn = false;

    // FIREBASE
    private final FirebaseAuth auth = FirebaseAuth.getInstance();
    private final FirebaseFirestore db = FirebaseFirestore.getInstance();
    private ActivityResultLauncher<String> cameraPermissionLauncher;

    // --- SCANNER CALLBACK ---
    private final BarcodeCallback scanCallback = new BarcodeCallback() {
        @Override
        public void barcodeResult(BarcodeResult result) {
            if (result == null || result.getText() == null) return;

            long now = System.currentTimeMillis();
            if (isProcessingScan || (now - lastScanTimestamp) < DEBOUNCE_MS) {
                return;
            }

            isProcessingScan = true;
            lastScanTimestamp = now;

            final String scannedContent = result.getText().trim();
            triggerHapticFeedback(60);
            processScannedCode(scannedContent);
        }
    };

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_scan, container, false);
        initializeViews(view);
        setupClickListeners();
        setupPermissionLauncher();
        checkPermissionAndStart();
        return view;
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
        hideOverlays();
        isProcessingScan = false;
    }

    // ============================================================================================
    // INIT
    // ============================================================================================

    private void initializeViews(View v) {
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

        if (btnManualEntry != null) {
            btnManualEntry.setOnClickListener(view ->
                    showError("Manual entry is not implemented yet"));
        }

        barcodeView.decodeContinuous(scanCallback);
    }

    private void setupClickListeners() {
        btnFlashlight.setOnClickListener(v -> toggleFlashlight());
        btnClose.setOnClickListener(v -> safelyExitFragment());
        btnRetry.setOnClickListener(v -> {
            hideError();
            resetScanState();
            resumeScanner();
        });
    }

    // ============================================================================================
    // CORE LOGIC ROUTING
    // ============================================================================================

    private void processScannedCode(@NonNull String rawData) {
        if (rawData.isEmpty()) {
            showError("Invalid QR Code");
            return;
        }

        // REDEEM flow from cashier app:
        // Format: REDEEM|codeId|userUid|costPoints
        if (rawData.startsWith("REDEEM|")) {
            String[] parts = rawData.split("\\|");
            if (parts.length < 4) {
                showError("Invalid redemption QR format");
                return;
            }

            String codeId     = parts[1];
            String qrUserUid  = parts[2];
            String costString = parts[3];

            int qrCostPoints;
            try {
                qrCostPoints = Integer.parseInt(costString);
            } catch (NumberFormatException e) {
                showError("Invalid points in QR code");
                return;
            }

            executeSpendTransaction(codeId, qrUserUid, qrCostPoints);
            return;
        }

        // Default: EARN Logic (Add Points)
        // rawData is just the document ID for /earn_codes/{id}
        executeEarnTransaction(rawData);
    }

    // ============================================================================================
    // LOGIC A: EARNING POINTS (Standard Receipt Scan)
    // ============================================================================================

    private void executeEarnTransaction(@NonNull String voucherId) {
        FirebaseUser currentUser = auth.getCurrentUser();
        if (currentUser == null) {
            showError("Authentication required");
            return;
        }

        final DocumentReference voucherRef  = db.collection("earn_codes").document(voucherId);
        final DocumentReference userRef     = db.collection("users").document(currentUser.getUid());
        final DocumentReference activityRef = userRef.collection("activities").document();

        db.runTransaction(transaction -> {
            DocumentSnapshot voucherSnap = transaction.get(voucherRef);
            DocumentSnapshot userSnap    = transaction.get(userRef);

            if (!voucherSnap.exists()) {
                throw new FirebaseFirestoreException("Voucher not found", FirebaseFirestoreException.Code.NOT_FOUND);
            }

            String status      = voucherSnap.getString("status");
            Long validForSec   = voucherSnap.getLong("validForSec");
            Timestamp createdAt= voucherSnap.getTimestamp("createdAt");
            Long pointsLong    = voucherSnap.getLong("points");
            int pointsVal      = pointsLong != null ? pointsLong.intValue() : 0;

            if (status == null) {
                throw new FirebaseFirestoreException("Invalid voucher", FirebaseFirestoreException.Code.DATA_LOSS);
            }

            // Only allow "pending"
            if (!"pending".equalsIgnoreCase(status)) {
                throw new FirebaseFirestoreException("Voucher is " + status, FirebaseFirestoreException.Code.ABORTED);
            }

            if (createdAt != null && validForSec != null) {
                long ageMs = System.currentTimeMillis() - createdAt.toDate().getTime();
                if (ageMs > validForSec * 1000L) {
                    throw new FirebaseFirestoreException("Voucher expired", FirebaseFirestoreException.Code.ABORTED);
                }
            }

            long currentPoints = userSnap.getLong("points") != null ? userSnap.getLong("points") : 0L;

            // --- NEW VISIT LOGIC ---
            long currentVisits = userSnap.getLong("visits") != null ? userSnap.getLong("visits") : 0L;
            Timestamp lastVisitTs = userSnap.getTimestamp("lastVisitTimestamp");

            boolean incrementVisit = false;
            Date now = new Date();
            long nowMillis = now.getTime();

            if (lastVisitTs == null) {
                // First visit ever
                incrementVisit = true;
            } else {
                long lastVisitMillis = lastVisitTs.toDate().getTime();
                long timeDifference = nowMillis - lastVisitMillis;

                // Check 4-Hour Rule
                if (timeDifference > VISIT_TIME_WINDOW_MILLIS) {
                    incrementVisit = true;
                } else {
                    incrementVisit = false;
                }
            }
            // -----------------------

            // 1. Mark Voucher as redeemed
            Map<String, Object> vUpd = new HashMap<>();
            vUpd.put("status", "redeemed");
            vUpd.put("redeemedAt", new Timestamp(now));
            vUpd.put("redeemedByUid", currentUser.getUid());
            transaction.update(voucherRef, vUpd);

            // 2. Add Points to User + Visits Logic
            Map<String, Object> uUpd = new HashMap<>();
            uUpd.put("points", currentPoints + pointsVal);
            uUpd.put("updatedAt", new Timestamp(now));

            if (incrementVisit) {
                uUpd.put("visits", currentVisits + 1);
                uUpd.put("lastVisitTimestamp", new Timestamp(now));
            }

            transaction.update(userRef, uUpd);

            // 3. Log Activity
            Map<String, Object> log = new HashMap<>();
            log.put("type", "earn");
            log.put("points", pointsVal);
            log.put("voucherId", voucherId);
            log.put("ts", new Timestamp(now));
            transaction.set(activityRef, log);

            // Return Data for UI
            Map<String, Object> result = new HashMap<>();
            result.put("points", pointsVal);
            result.put("visitCounted", incrementVisit);
            return result;

        }).addOnSuccessListener(result -> {
            // Unpack result
            int points = (int) result.get("points");
            boolean visitCounted = (boolean) result.get("visitCounted");

            String subMsg = visitCounted ? "Visit counted & points added!" : "Points added (Same Visit)";
            showSuccess("+" + points + " Points", subMsg);
        }).addOnFailureListener(e -> {
            String msg = e.getMessage() != null ? e.getMessage() : "Transaction failed";
            if (msg.contains("not found")) msg = "Invalid QR Code";
            if (msg.toLowerCase().contains("expired")) msg = "This code has expired";
            showError(msg);
        });
    }

    // ============================================================================================
    // LOGIC B: SPENDING POINTS (Redeeming Gift) -- REDEEM QR
    // ============================================================================================

    private void executeSpendTransaction(@NonNull String redeemDocId,
                                         @NonNull String qrUserUid,
                                         int qrCostPoints) {

        FirebaseUser currentUser = auth.getCurrentUser();
        if (currentUser == null) {
            showError("Authentication required");
            return;
        }

        // Extra security: QR must belong to this logged-in user
        if (!currentUser.getUid().equals(qrUserUid)) {
            showError("This code belongs to another account");
            return;
        }

        final DocumentReference redeemRef  = db.collection("redeem_codes").document(redeemDocId);
        final DocumentReference userRef    = db.collection("users").document(currentUser.getUid());
        final DocumentReference activityRef= userRef.collection("activities").document();

        db.runTransaction(transaction -> {
            DocumentSnapshot redeemSnap = transaction.get(redeemRef);
            DocumentSnapshot userSnap   = transaction.get(userRef);

            if (!redeemSnap.exists()) {
                throw new FirebaseFirestoreException("Redemption code not found",
                        FirebaseFirestoreException.Code.NOT_FOUND);
            }

            // Ownership: this doc must target this user
            String targetUid = redeemSnap.getString("userUid");
            if (targetUid != null && !targetUid.equals(currentUser.getUid())) {
                throw new FirebaseFirestoreException("Code belongs to another user",
                        FirebaseFirestoreException.Code.PERMISSION_DENIED);
            }

            String status   = redeemSnap.getString("status");
            String type     = redeemSnap.getString("type");
            Long costLong   = redeemSnap.getLong("costPoints");
            String itemName = redeemSnap.getString("itemName");
            int costPoints  = costLong != null ? costLong.intValue() : qrCostPoints;

            if (type == null || !"REDEEM".equalsIgnoreCase(type)) {
                throw new FirebaseFirestoreException("Wrong code type",
                        FirebaseFirestoreException.Code.ABORTED);
            }

            if (status == null) {
                throw new FirebaseFirestoreException("Invalid code status",
                        FirebaseFirestoreException.Code.DATA_LOSS);
            }

            // From cashier app: ACTIVE when generated
            if (!"ACTIVE".equalsIgnoreCase(status)) {
                throw new FirebaseFirestoreException("Code already " + status,
                        FirebaseFirestoreException.Code.ABORTED);
            }

            long userBalance = userSnap.getLong("points") != null ? userSnap.getLong("points") : 0L;

            if (userBalance < costPoints) {
                throw new FirebaseFirestoreException("Insufficient Points",
                        FirebaseFirestoreException.Code.ABORTED);
            }

            // 1. Deduct Points
            Map<String, Object> uUpd = new HashMap<>();
            uUpd.put("points", userBalance - costPoints);
            uUpd.put("updatedAt", FieldValue.serverTimestamp());
            transaction.update(userRef, uUpd);

            // 2. Mark Redemption as completed
            Map<String, Object> rUpd = new HashMap<>();
            rUpd.put("status", "completed");
            rUpd.put("completedAt", FieldValue.serverTimestamp());
            rUpd.put("completedByUid", currentUser.getUid());
            transaction.update(redeemRef, rUpd);

            // 3. Log Activity
            Map<String, Object> log = new HashMap<>();
            log.put("type", "spend");
            log.put("points", -costPoints);
            log.put("item", itemName);
            log.put("redeemCodeId", redeemDocId);
            log.put("ts", FieldValue.serverTimestamp());
            transaction.set(activityRef, log);

            return itemName != null ? itemName : "Reward";

        }).addOnSuccessListener(itemName ->
                showSuccess("Confirmed!", "Redeemed: " + itemName)
        ).addOnFailureListener(e -> {
            String msg = e.getMessage() != null ? e.getMessage() : "Redemption failed";
            if (msg.toLowerCase().contains("not found")) msg = "Invalid redeem code";
            showError(msg);
        });
    }

    // ============================================================================================
    // UI HELPERS
    // ============================================================================================

    private void showSuccess(String main, String sub) {
        runOnUi(() -> {
            if (!isAdded()) return;
            pauseScanner();
            if (successMessage != null) successMessage.setText(main);
            if (successDetails != null) successDetails.setText(sub);
            if (successOverlay != null) successOverlay.setVisibility(View.VISIBLE);

            uiHandler.postDelayed(() -> {
                hideSuccess();
                resetScanState();
                resumeScanner();
            }, OVERLAY_MS);
        });
    }

    private void hideSuccess() {
        runOnUi(() -> {
            if (isAdded() && successOverlay != null) {
                successOverlay.setVisibility(View.GONE);
            }
        });
    }

    private void showError(String msg) {
        runOnUi(() -> {
            if (!isAdded()) return;
            pauseScanner();
            if (errorMessage != null) errorMessage.setText(msg);
            if (errorOverlay != null) errorOverlay.setVisibility(View.VISIBLE);
        });
    }

    private void hideError() {
        runOnUi(() -> {
            if (isAdded() && errorOverlay != null) {
                errorOverlay.setVisibility(View.GONE);
            }
        });
    }

    private void hideOverlays() {
        hideSuccess();
        hideError();
    }

    private void resetScanState() {
        isProcessingScan = false;
        lastScanTimestamp = System.currentTimeMillis();
    }

    private void showToast(String message) {
        runOnUi(() -> {
            if (isAdded()) {
                Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void runOnUi(Runnable r) {
        uiHandler.post(r);
    }

    // ============================================================================================
    // HARDWARE
    // ============================================================================================

    private void setupPermissionLauncher() {
        cameraPermissionLauncher =
                registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
                    if (isGranted) resumeScanner();
                    else showToast("Camera permission required");
                });
    }

    private void checkPermissionAndStart() {
        if (hasCameraPermission()) resumeScanner();
        else cameraPermissionLauncher.launch(Manifest.permission.CAMERA);
    }

    private boolean hasCameraPermission() {
        return ContextCompat.checkSelfPermission(
                requireContext(), Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED;
    }

    private void resumeScanner() {
        if (isAdded() && barcodeView != null) barcodeView.resume();
    }

    private void pauseScanner() {
        if (barcodeView != null) barcodeView.pause();
    }

    private void toggleFlashlight() {
        try {
            if (barcodeView == null) return;
            if (isTorchOn) {
                barcodeView.setTorchOff();
                isTorchOn = false;
                btnFlashlight.setImageResource(R.drawable.ic_flashlight_off);
            } else {
                barcodeView.setTorchOn();
                isTorchOn = true;
                btnFlashlight.setImageResource(R.drawable.ic_flashlight_on);
            }
        } catch (Exception e) {
            Log.e(TAG, "Flashlight error", e);
        }
    }

    private void triggerHapticFeedback(int milliseconds) {
        try {
            if (!isAdded()) return;
            Vibrator v = (Vibrator) requireContext().getSystemService(Context.VIBRATOR_SERVICE);
            if (v == null) return;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                v.vibrate(VibrationEffect.createOneShot(milliseconds, VibrationEffect.DEFAULT_AMPLITUDE));
            } else {
                //noinspection deprecation
                v.vibrate(milliseconds);
            }
        } catch (Exception ignored) {}
    }

    private void safelyExitFragment() {
        try {
            requireActivity().getOnBackPressedDispatcher().onBackPressed();
        } catch (Exception ignored) {}
    }
}