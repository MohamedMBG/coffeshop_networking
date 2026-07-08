package com.example.loyaltyapp;

import android.app.Dialog;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;

import com.google.zxing.BarcodeFormat;
import com.journeyapps.barcodescanner.BarcodeEncoder;

/**
 * Shows a pending redeem code as a QR for the cashier to scan, with a live
 * countdown to its expiry. The backend has already deducted the points; this is
 * display-only — completion happens when the cashier scans the code.
 *
 * A DialogFragment (not a bare AlertDialog) so it survives configuration
 * changes and its CountDownTimer is bound to the fragment view lifecycle.
 */
public class RedeemCodeDialog extends DialogFragment {
    private static final String TAG = "RedeemCodeDialog";
    private static final String ARG_CODE = "code";
    private static final String ARG_EXPIRES = "expiresAtEpochMs";

    private CountDownTimer timer;
    private TextView countdownText;

    public static RedeemCodeDialog newInstance(String code, long expiresAtEpochMs) {
        RedeemCodeDialog f = new RedeemCodeDialog();
        Bundle args = new Bundle();
        args.putString(ARG_CODE, code);
        args.putLong(ARG_EXPIRES, expiresAtEpochMs);
        f.setArguments(args);
        return f;
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        Bundle args = requireArguments();
        String code = args.getString(ARG_CODE, "");
        long expiresAtEpochMs = args.getLong(ARG_EXPIRES, 0L);

        View view = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_redeem_code, null);
        ImageView qr = view.findViewById(R.id.qrImage);
        TextView codeText = view.findViewById(R.id.codeText);
        countdownText = view.findViewById(R.id.countdownText);

        // Text code always shown, so the cashier can key it in even if the QR
        // fails to render.
        codeText.setText(code);
        try {
            Bitmap bmp = new BarcodeEncoder().encodeBitmap(code, BarcodeFormat.QR_CODE, 600, 600);
            qr.setImageBitmap(bmp);
            qr.setVisibility(View.VISIBLE);
        } catch (Exception e) {
            Log.e(TAG, "QR encode failed", e);
            // Hide the blank image; the text code above remains the fallback.
            qr.setVisibility(View.GONE);
        }

        startCountdown(expiresAtEpochMs);

        return new AlertDialog.Builder(requireContext())
                .setView(view)
                .setPositiveButton(R.string.redeem_done_button, null)
                .create();
    }

    private void startCountdown(long expiresAtEpochMs) {
        long remaining = expiresAtEpochMs - System.currentTimeMillis();
        if (remaining <= 0) {
            // Already past (e.g. clock skew): skip the timer, label it expired.
            countdownText.setText(R.string.redeem_expired);
            return;
        }
        timer = new CountDownTimer(remaining, 1000) {
            @Override
            public void onTick(long msLeft) {
                long s = msLeft / 1000;
                countdownText.setText(getString(R.string.redeem_expires_format, s / 60, s % 60));
            }

            @Override
            public void onFinish() {
                countdownText.setText(R.string.redeem_expired);
            }
        };
        timer.start();
    }

    @Override
    public void onDestroyView() {
        // Stop the timer on dismiss or config change so it can't tick on
        // detached views.
        if (timer != null) {
            timer.cancel();
            timer = null;
        }
        super.onDestroyView();
    }
}
