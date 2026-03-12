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
import androidx.lifecycle.ViewModelProvider;

import com.example.loyaltyapp.R;
import com.example.loyaltyapp.viewmodels.ScanViewModel;

// ZXing (Barcode) Imports
import com.journeyapps.barcodescanner.BarcodeCallback;
import com.journeyapps.barcodescanner.BarcodeResult;
import com.journeyapps.barcodescanner.DecoratedBarcodeView;

public class ScanFragment extends Fragment {

    private static final String TAG = "ScanFragment";
    private static final long DEBOUNCE_MS = 1500;

    private com.example.loyaltyapp.databinding.FragmentScanBinding binding;

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

    // VIEWMODEL
    private ScanViewModel viewModel;
    private ActivityResultLauncher<String> cameraPermissionLauncher;

    // --- SCANNER CALLBACK ---
    private final BarcodeCallback scanCallback = new BarcodeCallback() {
        @Override
        public void barcodeResult(BarcodeResult result) {
            if (result == null || result.getText() == null)
                return;

            long now = System.currentTimeMillis();
            if (isProcessingScan || (now - lastScanTimestamp) < DEBOUNCE_MS) {
                return;
            }

            isProcessingScan = true;
            lastScanTimestamp = now;

            final String scannedContent = result.getText().trim();
            triggerHapticFeedback(60);
            viewModel.processScannedCode(scannedContent);
        }
    };

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        binding = com.example.loyaltyapp.databinding.FragmentScanBinding.inflate(inflater, container, false);
        View view = binding.getRoot();
        
        viewModel = new ViewModelProvider(this).get(ScanViewModel.class);
        
        initializeViews();
        setupClickListeners();
        setupPermissionLauncher();
        checkPermissionAndStart();
        
        viewModel.getScanState().observe(getViewLifecycleOwner(), this::renderState);
        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        if (hasCameraPermission())
            resumeScanner();
    }

    @Override
    public void onPause() {
        super.onPause();
        pauseScanner();
        if (viewModel != null) {
            viewModel.clearState();
        }
        isProcessingScan = false;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }

    // ============================================================================================
    // INIT
    // ============================================================================================

    private void initializeViews() {
        barcodeView = binding.barcodeScanner;
        btnFlashlight = binding.btnFlashlight;
        btnClose = binding.btnClose;
        successOverlay = binding.successOverlay;
        errorOverlay = binding.errorOverlay;
        successMessage = binding.successMessage;
        successDetails = binding.successDetails;
        errorMessage = binding.errorMessage;
        btnRetry = binding.btnRetry;
        btnManualEntry = binding.btnManualEntry;

        if (btnManualEntry != null) {
            btnManualEntry.setOnClickListener(view -> {
                if (viewModel != null) viewModel.postError("Manual entry is not implemented yet");
            });
        }

        barcodeView.decodeContinuous(scanCallback);
    }

    private void setupClickListeners() {
        btnFlashlight.setOnClickListener(v -> toggleFlashlight());
        btnClose.setOnClickListener(v -> safelyExitFragment());
        btnRetry.setOnClickListener(v -> {
            if (viewModel != null) viewModel.clearState();
        });
    }

    // ============================================================================================
    // VIEW MODEL OBSERVER STATE
    // ============================================================================================

    private void renderState(ScanViewModel.ScanState state) {
        if (!isAdded()) return;

        if (state.isLoading) {
            pauseScanner();
        } else if (state.errorMsg != null) {
            pauseScanner();
            if (errorMessage != null) errorMessage.setText(state.errorMsg);
            if (errorOverlay != null) errorOverlay.setVisibility(View.VISIBLE);
            if (successOverlay != null) successOverlay.setVisibility(View.GONE);
        } else if (state.isSuccess) {
            pauseScanner();
            if (successMessage != null) successMessage.setText(state.successMain);
            if (successDetails != null) successDetails.setText(state.successSub);
            if (successOverlay != null) successOverlay.setVisibility(View.VISIBLE);
            if (errorOverlay != null) errorOverlay.setVisibility(View.GONE);
        } else {
            // Cleared state (null / false)
            if (errorOverlay != null) errorOverlay.setVisibility(View.GONE);
            if (successOverlay != null) successOverlay.setVisibility(View.GONE);
            resetScanState();
            resumeScanner();
        }
    }

    // ============================================================================================
    // UI HELPERS
    // ============================================================================================

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
        cameraPermissionLauncher = registerForActivityResult(new ActivityResultContracts.RequestPermission(),
                isGranted -> {
                    if (isGranted)
                        resumeScanner();
                    else
                        showToast("Camera permission required");
                });
    }

    private void checkPermissionAndStart() {
        if (hasCameraPermission())
            resumeScanner();
        else
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA);
    }

    private boolean hasCameraPermission() {
        return ContextCompat.checkSelfPermission(
                requireContext(), Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED;
    }

    private void resumeScanner() {
        if (isAdded() && barcodeView != null)
            barcodeView.resume();
    }

    private void pauseScanner() {
        if (barcodeView != null)
            barcodeView.pause();
    }

    private void toggleFlashlight() {
        try {
            if (barcodeView == null)
                return;
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
            if (!isAdded())
                return;
            Vibrator v = (Vibrator) requireContext().getSystemService(Context.VIBRATOR_SERVICE);
            if (v == null)
                return;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                v.vibrate(VibrationEffect.createOneShot(milliseconds, VibrationEffect.DEFAULT_AMPLITUDE));
            } else {
                // noinspection deprecation
                v.vibrate(milliseconds);
            }
        } catch (Exception ignored) {
        }
    }

    private void safelyExitFragment() {
        try {
            requireActivity().getOnBackPressedDispatcher().onBackPressed();
        } catch (Exception ignored) {
        }
    }
}