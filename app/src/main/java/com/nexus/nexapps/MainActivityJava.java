package com.nexus.nexapps;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Log;
import android.webkit.JavascriptInterface;
import android.webkit.RenderProcessGoneDetail;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.firebase.messaging.FirebaseMessaging;

import java.io.IOException;

public class MainActivityJava extends AppCompatActivity {

    /* -------------------------- CONSTANTES -------------------------- */
    private static final String TAG                    = "NexusWebView";
    private static final int    FILECHOOSER_RESULTCODE = 1;
    private static final int    LOCATION_PERMISSION_REQ = 100;
    private static final int    NOTIF_PERMISSION_REQ   = 2000;

    /* --------------------------- CAMPOS ----------------------------- */
    private WebView web;
    private ValueCallback<Uri[]> filePathCallback;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        WebView.setWebContentsDebuggingEnabled(true);
        Log.d(TAG, "Arrancando MainActivity");

        setContentView(R.layout.activity_main);

        createNotificationChannel();

        web = findViewById(R.id.webView);
        WebSettings ws = web.getSettings();
        ws.setJavaScriptEnabled(true);
        ws.setDomStorageEnabled(true);
        ws.setBuiltInZoomControls(true);
        ws.setDisplayZoomControls(false);
        ws.setLoadWithOverviewMode(true);
        ws.setUseWideViewPort(true);
        ws.setGeolocationEnabled(true);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            ws.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
            ws.setSafeBrowsingEnabled(false);
            // Salvavidas si el render-process muere
            web.setRendererPriorityPolicy(WebView.RENDERER_PRIORITY_BOUND, true);
        }

        web.addJavascriptInterface(new Object() {
            @JavascriptInterface
            public void sendAuth(String uid, String jwt) throws IOException {
                Log.d(TAG, "► sendAuth(): uid=" + uid + " jwt len=" + (jwt == null ? "null" : jwt.length()));
                SharedPreferences sp = getSharedPreferences("fcm", MODE_PRIVATE);
                sp.edit()
                        .putString("user_id", uid)
                        .putString("jwtToken", jwt)
                        .apply();

                if (sp.contains("fcm_token")) {
                    MyFirebaseService.trySendToBackend(MainActivityJava.this);
                }
            }
        }, "AndroidBridge");

        web.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                Log.d(TAG, "onPageFinished: " + url);
                // Pedimos permiso de notificaciones *después* de cargar la página
                askPostNotificationPermissionIfNeeded();
                // Inyectamos el token FCM en localStorage
                injectFCMTokenIfReady();

                // Inyección de UID+JWT desde localStorage
                view.evaluateJavascript(
                        "(function(){                                          \n" +
                                "  if(typeof AndroidBridge!=='undefined' && AndroidBridge.sendAuth){\n" +
                                "     AndroidBridge.sendAuth(\n" +
                                "        localStorage.getItem('android_userid')||'',\n" +
                                "        localStorage.getItem('jwtToken')||''\n" +
                                "     );\n" +
                                "  }\n" +
                                "})();", null
                );
            }

            @Override
            public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                Log.e(TAG, "❌ Error cargando página: " + error.getErrorCode() + " – " + error.getDescription());
            }

            @Override
            public boolean onRenderProcessGone(WebView view, RenderProcessGoneDetail detail) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    Log.w(TAG, "Render process muerto (crash=" + detail.didCrash() + ")");
                }
                // Limpia y recrea la Activity
                view.destroy();
                recreate();
                return true;
            }
        });

        web.setWebChromeClient(new WebChromeClient() {
            // Si necesitas callbacks especiales (file chooser, geoloc, etc.)
        });

        // Obtener token FCM, guardarlo y luego inyectar cuando la página esté lista
        FirebaseMessaging.getInstance().getToken().addOnCompleteListener(task -> {
            if (!task.isSuccessful()) {
                Log.w(TAG, "FCM token failed", task.getException());
                return;
            }
            String tok = task.getResult();
            Log.d(TAG, "FCM token listo: " + tok);
            getSharedPreferences("fcm", MODE_PRIVATE)
                    .edit().putString("fcm_token", tok).apply();

            // Asegurarse de inyectar sólo cuando el WebView ya está cargado
            runOnUiThread(this::injectFCMTokenIfReady);
        });

        web.loadUrl("https://apps.nexushn.com/nexapps/login");
    }

    // Canal de notificaciones para Android O+
    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            String channelId   = "default_channel";
            String channelName = "General";
            NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            NotificationChannel ch = new NotificationChannel(
                    channelId,
                    channelName,
                    NotificationManager.IMPORTANCE_HIGH
            );
            ch.setDescription("Canal por defecto para notificaciones");
            nm.createNotificationChannel(ch);
        }
    }

    // Se pide permiso POST_NOTIFICATIONS tras renderizar la web
    private void askPostNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                        != PackageManager.PERMISSION_GRANTED) {

            ActivityCompat.requestPermissions(
                    this,
                    new String[]{ Manifest.permission.POST_NOTIFICATIONS },
                    NOTIF_PERMISSION_REQ
            );
        }
    }

    // Inyecta fcm_token desde SharedPreferences → localStorage
    private void injectFCMTokenIfReady() {
        SharedPreferences sp = getSharedPreferences("fcm", MODE_PRIVATE);
        String tok = sp.getString("fcm_token", null);
        if (tok != null && web != null) {
            String escaped = tok.replace("'", "\\'");
            web.evaluateJavascript(
                    "localStorage.setItem('fcm_token', '" + escaped + "');",
                    null
            );
        }
    }

    // Archivo / geoloc / storage
    private void abrirSelectorDeArchivos() {
        Intent i = new Intent(Intent.ACTION_GET_CONTENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("*/*");
        startActivityForResult(Intent.createChooser(i, "Selecciona un archivo"), FILECHOOSER_RESULTCODE);
    }

    private boolean checkLocationPermission() {
        return ContextCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
    }

    private boolean checkStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return ContextCompat.checkSelfPermission(this,
                    Manifest.permission.READ_MEDIA_IMAGES)
                    == PackageManager.PERMISSION_GRANTED;
        } else {
            return ContextCompat.checkSelfPermission(this,
                    Manifest.permission.READ_EXTERNAL_STORAGE)
                    == PackageManager.PERMISSION_GRANTED;
        }
    }

    private void requestStoragePermission() {
        String perm = (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
                ? Manifest.permission.READ_MEDIA_IMAGES
                : Manifest.permission.READ_EXTERNAL_STORAGE;
        ActivityCompat.requestPermissions(this, new String[]{ perm }, FILECHOOSER_RESULTCODE);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] perms, int[] res) {
        super.onRequestPermissionsResult(requestCode, perms, res);
        if (requestCode == LOCATION_PERMISSION_REQ &&
                (res.length == 0 || res[0] != PackageManager.PERMISSION_GRANTED)) {
            abrirAjustes();
        }
        if (requestCode == FILECHOOSER_RESULTCODE &&
                (res.length == 0 || res[0] != PackageManager.PERMISSION_GRANTED)) {
            abrirAjustes();
        }
        // No es crítico manejar POST_NOTIFICATIONS aquí
    }

    private void abrirAjustes() {
        new AlertDialog.Builder(this)
                .setTitle("Permiso necesario")
                .setMessage("Actívalo manualmente en la configuración de la app.")
                .setPositiveButton("Configuración", (d, w) -> {
                    Intent i = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                    i.setData(Uri.fromParts("package", getPackageName(), null));
                    startActivity(i);
                })
                .setNegativeButton("Cancelar", (d, w) -> d.dismiss())
                .show();
    }

    @Override
    public void onBackPressed() {
        if (web.canGoBack()) web.goBack();
        else super.onBackPressed();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == FILECHOOSER_RESULTCODE && filePathCallback != null) {
            Uri[] result = null;
            if (resultCode == Activity.RESULT_OK && data != null && data.getData() != null) {
                result = new Uri[]{ data.getData() };
            }
            filePathCallback.onReceiveValue(result);
            filePathCallback = null;
        }
    }
}
