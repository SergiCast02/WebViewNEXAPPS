package com.nexus.nexapps;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.os.Build;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;

import android.webkit.CookieManager;
import android.content.SharedPreferences;

import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

import java.io.IOException;

import okhttp3.FormBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class MyFirebaseService extends FirebaseMessagingService {
    private static final String TAG        = "NexusWebView";
    private static final String CHANNEL_ID = "default_channel";

    @Override
    public void onNewToken(@NonNull String token) {
        super.onNewToken(token);
        Log.d(TAG, "onNewToken(): " + token);
        getSharedPreferences("fcm", MODE_PRIVATE)
                .edit().putString("fcm_token", token).apply();
        trySendToBackend(this);
    }

    @Override
    public void onMessageReceived(@NonNull RemoteMessage remoteMessage) {
        super.onMessageReceived(remoteMessage);
        Log.d(TAG, "Mensaje entrante de FCM: " + remoteMessage);

        // Datos en payload
        if (remoteMessage.getData().size() > 0) {
            Log.d(TAG, "Datos: " + remoteMessage.getData());
        }

        // Notificación en payload
        RemoteMessage.Notification notif = remoteMessage.getNotification();
        if (notif != null) {
            String title = notif.getTitle();
            String body  = notif.getBody();
            Log.d(TAG, "Notificación recibida → " + title + ": " + body);
            showNotification(title, body);
        }
    }

    private void showNotification(String title, String body) {
        NotificationManager nm =
                (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

        // Crear canal para Android O+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel ch = new NotificationChannel(
                    CHANNEL_ID,
                    "General",
                    NotificationManager.IMPORTANCE_HIGH
            );
            ch.setDescription("Canal por defecto");
            nm.createNotificationChannel(ch);
        }

        Notification notif = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.icono_nexus_chikito)  // tu icono de notificación
                .setContentTitle(title)
                .setContentText(body)
                .setAutoCancel(true)
                .build();

        nm.notify((int) System.currentTimeMillis(), notif);
    }

    public static void trySendToBackend(Context ctx) {
        SharedPreferences sp = ctx.getSharedPreferences("fcm", MODE_PRIVATE);
        String fcmToken = sp.getString("fcm_token", null);
        if (fcmToken == null) return;

        // Extrae cookies y CSRF
        String cookies = CookieManager.getInstance()
                .getCookie("https://pedidos.nexushn.com");
        String csrf = "";
        if (cookies != null) {
            for (String c : cookies.split(";")) {
                String[] p = c.trim().split("=");
                if (p.length == 2 && p[0].equals("csrftoken")) {
                    csrf = p[1];
                    break;
                }
            }
        }

        String finalCsrf = csrf;
        new Thread(() -> {
            try {
                OkHttpClient client = new OkHttpClient();
                RequestBody body = new FormBody.Builder()
                        .add("token", fcmToken)
                        .build();

//                Request req = new Request.Builder()
//                        .url("https://pedidos.nexushn.com/api/fcm/register/")
//                        .addHeader("Content-Type", "application/x-www-form-urlencoded")
//                        .addHeader("X-CSRFToken", finalCsrf)
//                        .addHeader("Cookie", cookies)
//                        .post(body)
//                        .build();

                Request.Builder builder = new Request.Builder()
                        .url("https://pedidos.nexushn.com/api/fcm/register/")
                        .addHeader("Content-Type", "application/x-www-form-urlencoded");

                if (finalCsrf != null && !finalCsrf.isEmpty()) {
                    builder.addHeader("X-CSRFToken", finalCsrf);
                }
                if (cookies != null && !cookies.isEmpty()) {
                    builder.addHeader("Cookie", cookies);
                }

                Request req = builder.post(body).build();


                try (Response r = client.newCall(req).execute()) {
                    if (r.isSuccessful()) {
                        Log.d(TAG, "✅ FCM registrado OK (session auth)");
                    } else {
                        Log.e(TAG, "❌ Registro FCM falló: " +
                                r.code() + " — " + r.body().string());
                    }
                }
            } catch (IOException ex) {
                Log.e(TAG, "❌ Error en registro FCM", ex);
            }
        }).start();
    }
}
