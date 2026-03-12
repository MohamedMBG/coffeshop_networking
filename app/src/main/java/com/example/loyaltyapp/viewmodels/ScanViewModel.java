package com.example.loyaltyapp.viewmodels;

import android.os.Handler;
import android.os.Looper;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.example.loyaltyapp.data.repository.ScanRepository;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import java.util.Map;

public class ScanViewModel extends ViewModel {

    private final ScanRepository repository;
    private final FirebaseAuth auth;
    
    private final Handler handler = new Handler(Looper.getMainLooper());

    private final MutableLiveData<ScanState> scanState = new MutableLiveData<>();

    public ScanViewModel() {
        this(new ScanRepository(), FirebaseAuth.getInstance());
    }

    @androidx.annotation.VisibleForTesting
    public ScanViewModel(ScanRepository repository, FirebaseAuth auth) {
        this.repository = repository;
        this.auth = auth;
    }

    public LiveData<ScanState> getScanState() {
        return scanState;
    }

    public void processScannedCode(String rawData) {
        if (rawData == null || rawData.isEmpty()) {
            postError("Invalid QR Code");
            return;
        }

        FirebaseUser currentUser = auth.getCurrentUser();
        if (currentUser == null) {
            postError("Authentication required");
            return;
        }

        if (rawData.startsWith("REDEEM|")) {
            String[] parts = rawData.split("\\|");
            if (parts.length < 4) {
                postError("Invalid redemption QR format");
                return;
            }

            String codeId = parts[1];
            String qrUserUid = parts[2];
            String costString = parts[3];

            int qrCostPoints;
            try {
                qrCostPoints = Integer.parseInt(costString);
            } catch (NumberFormatException e) {
                postError("Invalid points in QR code");
                return;
            }

            if (!currentUser.getUid().equals(qrUserUid)) {
                postError("This code belongs to another account");
                return;
            }

            executeSpendTransaction(codeId, qrUserUid, qrCostPoints, currentUser.getUid());
        } else {
            executeEarnTransaction(rawData, currentUser.getUid());
        }
    }

    private void executeEarnTransaction(String voucherId, String userUid) {
        scanState.setValue(new ScanState(true, null, null, null, false));
        repository.executeEarnTransaction(voucherId, userUid)
            .addOnSuccessListener(result -> {
                int points = (int) result.get("points");
                boolean visitCounted = (boolean) result.get("visitCounted");

                String mainMsg = "+" + points + " Points";
                String subMsg = visitCounted ? "Visit counted & points added!" : "Points added (Same Visit)";
                
                postSuccess(mainMsg, subMsg);
            })
            .addOnFailureListener(e -> {
                String msg = e.getMessage() != null ? e.getMessage() : "Transaction failed";
                if (msg.contains("not found"))
                    msg = "Invalid QR Code";
                if (msg.toLowerCase().contains("expired"))
                    msg = "This code has expired";
                postError(msg);
            });
    }

    private void executeSpendTransaction(String redeemDocId, String qrUserUid, int qrCostPoints, String currentUserUid) {
        scanState.setValue(new ScanState(true, null, null, null, false));
        repository.executeSpendTransaction(redeemDocId, qrUserUid, qrCostPoints, currentUserUid)
            .addOnSuccessListener(itemName -> {
                postSuccess("Confirmed!", "Redeemed: " + itemName);
            })
            .addOnFailureListener(e -> {
                String msg = e.getMessage() != null ? e.getMessage() : "Redemption failed";
                if (msg.toLowerCase().contains("not found"))
                    msg = "Invalid redeem code";
                postError(msg);
            });
    }

    public void postSuccess(String main, String sub) {
        scanState.setValue(new ScanState(false, null, main, sub, true));
        handler.removeCallbacksAndMessages(null);
        handler.postDelayed(() -> {
            scanState.setValue(new ScanState(false, null, null, null, false));
        }, 2000);
    }

    public void postError(String msg) {
        scanState.setValue(new ScanState(false, msg, null, null, false));
    }

    public void clearState() {
        handler.removeCallbacksAndMessages(null);
        scanState.setValue(new ScanState(false, null, null, null, false));
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        handler.removeCallbacksAndMessages(null);
    }

    public static class ScanState {
        public final boolean isLoading;
        public final String errorMsg;
        public final String successMain;
        public final String successSub;
        public final boolean isSuccess;

        public ScanState(boolean isLoading, String errorMsg, String successMain, String successSub, boolean isSuccess) {
            this.isLoading = isLoading;
            this.errorMsg = errorMsg;
            this.successMain = successMain;
            this.successSub = successSub;
            this.isSuccess = isSuccess;
        }
    }
}
