package com.example.loyaltyapp.viewmodels;

import android.os.Handler;
import android.os.Looper;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.example.loyaltyapp.data.repository.ScanRepository;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

/**
 * Drives the QR-scan screen. The customer only scans earn codes: the scanned
 * string is a bare earn code sent to the backend (POST /loyalty/earn). Redeem
 * codes are shown by the customer and scanned by the cashier, so there is no
 * redeem path here. Exposes a single {@link ScanState} the fragment observes.
 */
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

        // The scanned string is a bare earn code. Backend identifies the user
        // from the auth token, so no uid is passed.
        executeEarnTransaction(rawData);
    }

    /**
     * Send the scanned earn code to the backend. The repository handles the
     * REST call, idempotency key, and error-envelope mapping; here we only turn
     * the result into UI state.
     */
    private void executeEarnTransaction(String code) {
        scanState.setValue(new ScanState(true, null, null, null, false));
        repository.earn(code, new ScanRepository.EarnCallback() {
            @Override
            public void onSuccess(int pointsGranted, int totalPoints, int totalVisits) {
                // Backend returns no "visit counted" flag, so surface the new
                // balance instead of the old same-visit/new-visit sub-message.
                postSuccess("+" + pointsGranted + " Points", "Balance: " + totalPoints + " pts");
            }

            @Override
            public void onError(String message) {
                postError(message);
            }
        });
    }
    // Show the success state briefly, then auto-clear so the scanner is ready
    // for the next code. Any pending clear is cancelled first to avoid overlap.
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
