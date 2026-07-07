package com.example.loyaltyapp;

import android.content.Context;
import android.graphics.Bitmap;
import android.os.CountDownTimer;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;

import com.google.zxing.BarcodeFormat;
import com.journeyapps.barcodescanner.BarcodeEncoder;

import java.util.Locale;

/**
 * Shows a pending redeem code as a QR for the cashier to scan, with a live
 * countdown to its expiry. The backend has already deducted the points; this is
 * display-only. Completion happens when the cashier scans the code.
 */
public final class RedeemCodeDialog {
    private static final String TAG = "RedeemCodeDialog";

    private RedeemCodeDialog() {}

    public static void show(Context context, String code, long expiresAtEpochMs) {
        View view = LayoutInflater.from(context).inflate(R.layout.dialog_redeem_code, null);
        ImageView qr = view.findViewById(R.id.qrImage);
        TextView codeText = view.findViewById(R.id.codeText);
        TextView countdownText = view.findViewById(R.id.countdownText);

        codeText.setText(code);
        try {
            Bitmap bmp = new BarcodeEncoder().encodeBitmap(code, BarcodeFormat.QR_CODE, 600, 600);
            qr.setImageBitmap(bmp);
        } catch (Exception e) {
            Log.e(TAG, "QR encode failed", e);
        }

        AlertDialog dialog = new AlertDialog.Builder(context)
                .setView(view)
                .setPositiveButton("Done", null)
                .create();

        long remaining = expiresAtEpochMs - System.currentTimeMillis();
        // Tie the countdown to the dialog; cancel it on dismiss to avoid leaks.
        CountDownTimer timer = new CountDownTimer(Math.max(0, remaining), 1000) {
            @Override
            public void onTick(long msLeft) {
                long s = msLeft / 1000;
                countdownText.setText(String.format(Locale.getDefault(), "Expires in %d:%02d", s / 60, s % 60));
            }

            @Override
            public void onFinish() {
                countdownText.setText("Expired");
            }
        };
        dialog.setOnDismissListener(d -> timer.cancel());
        dialog.show();
        timer.start();
    }
}
