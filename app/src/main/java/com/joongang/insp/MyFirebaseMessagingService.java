package com.joongang.insp;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;

import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

import org.json.JSONObject;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public class MyFirebaseMessagingService extends FirebaseMessagingService {

    static final String CHANNEL_ID   = "jaitpms_default";
    static final String REGISTER_URL = "https://jaitpms.com/printer-monitor/nas-web/push_register.php";
    static final String PUSH_KEY     = "jaitpms_push_7f3a9c";

    @Override
    public void onNewToken(@NonNull String token) {
        postToken(getApplicationContext(), token);
    }

    @Override
    public void onMessageReceived(@NonNull RemoteMessage msg) {
        String title = "JAIT PMS", body = "";
        if (msg.getNotification() != null) {
            if (msg.getNotification().getTitle() != null) title = msg.getNotification().getTitle();
            if (msg.getNotification().getBody() != null)  body  = msg.getNotification().getBody();
        }
        if (body.isEmpty() && msg.getData().containsKey("body")) body = msg.getData().get("body");
        showNotification(getApplicationContext(), title, body);
    }

    static void ensureChannel(Context ctx) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel ch = new NotificationChannel(CHANNEL_ID, "JAIT PMS 알림", NotificationManager.IMPORTANCE_HIGH);
            ch.setDescription("점검 서명완료·임박 알림");
            NotificationManager nm = ctx.getSystemService(NotificationManager.class);
            if (nm != null) nm.createNotificationChannel(ch);
        }
    }

    static void showNotification(Context ctx, String title, String body) {
        ensureChannel(ctx);
        NotificationCompat.Builder b = new NotificationCompat.Builder(ctx, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle(title)
                .setContentText(body)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH);
        NotificationManager nm = ctx.getSystemService(NotificationManager.class);
        if (nm != null) nm.notify((int) (System.currentTimeMillis() & 0x7fffffff), b.build());
    }

    static void registerToken(final Context ctx) {
        try {
            FirebaseMessaging.getInstance().getToken()
                    .addOnSuccessListener(token -> postToken(ctx, token));
        } catch (Exception ignored) {}
    }

    static void postToken(final Context ctx, final String token) {
        if (token == null || token.isEmpty()) return;
        new Thread(() -> {
            try {
                JSONObject j = new JSONObject();
                j.put("key", PUSH_KEY);
                j.put("token", token);
                j.put("label", Build.MANUFACTURER + " " + Build.MODEL);
                HttpURLConnection c = (HttpURLConnection) new URL(REGISTER_URL).openConnection();
                c.setRequestMethod("POST");
                c.setConnectTimeout(15000);
                c.setReadTimeout(15000);
                c.setDoOutput(true);
                c.setRequestProperty("Content-Type", "application/json; charset=utf-8");
                byte[] out = j.toString().getBytes("UTF-8");
                OutputStream os = c.getOutputStream();
                os.write(out);
                os.close();
                c.getResponseCode();
                c.disconnect();
            } catch (Exception ignored) {}
        }).start();
    }
}
