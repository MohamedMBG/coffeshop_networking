package com.example.loyaltyapp.services;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.os.Build;
import android.util.Log;

import com.example.loyaltyapp.R;
import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

public class MyFirebaseService extends FirebaseMessagingService {
    @Override public void onNewToken(@NonNull String token) {
        android.util.Log.i("FCM", "onNewToken -> " + token);
        TokenRegistrar.ensureDevice(token, "client");
    }


    @Override
    public void onMessageReceived(@NonNull RemoteMessage msg) {
        String title = "Message";
        String body  = "New message";

        if (msg.getNotification() != null) {
            title = msg.getNotification().getTitle() != null ? msg.getNotification().getTitle() : title;
            body  = msg.getNotification().getBody()  != null ? msg.getNotification().getBody()  : body;
        } else if (!msg.getData().isEmpty()) {
            title = msg.getData().getOrDefault("title", title);
            body  = msg.getData().getOrDefault("body", body);
        }

        String channelId = "push_default";
        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel ch = new NotificationChannel(channelId, "Push", NotificationManager.IMPORTANCE_HIGH);
            nm.createNotificationChannel(ch);
        }
        NotificationCompat.Builder b = new NotificationCompat.Builder(this, channelId)
                .setSmallIcon(R.drawable.ic_notifications)   // make sure this drawable exists
                .setContentTitle(title)
                .setContentText(body)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH);
        nm.notify((int) System.currentTimeMillis(), b.build());
    }


}
