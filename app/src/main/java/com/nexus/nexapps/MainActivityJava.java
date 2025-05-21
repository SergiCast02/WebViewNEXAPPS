package com.nexus.nexapps;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.DownloadManager;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.util.Log;
import android.view.Window;
import android.view.WindowManager;
import android.webkit.DownloadListener;
import android.webkit.JavascriptInterface;
import android.webkit.PermissionRequest;
import android.webkit.RenderProcessGoneDetail;
import android.webkit.URLUtil;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.firebase.messaging.FirebaseMessaging;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class MainActivityJava extends AppCompatActivity {

    /* -------------------------- CONSTANTES -------------------------- */
    private static final String TAG                      = "NexusWebView";
    private static final int    FILECHOOSER_RESULTCODE   = 1;
    private static final int    LOCATION_PERMISSION_REQ   = 100;
    private static final int    NOTIF_PERMISSION_REQ     = 2000;
    private static final int    CAMERA_PERMISSION_REQ    = 3000;

    /* --------------------------- CAMPOS ----------------------------- */
    private WebView web;
    private ValueCallback<Uri[]> filePathCallback;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // —————— Esto pinta la status bar en WebView ——————
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            Window w = getWindow();
            w.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
            w.setStatusBarColor(Color.rgb(200, 35, 51));
        }

        WebView.setWebContentsDebuggingEnabled(true);
        Log.d(TAG, "Arrancando MainActivity");

        setContentView(R.layout.activity_main);

        createNotificationChannel();

        // 1) Pide permisos de Cámara/Audio si faltan
        checkAndRequestCameraPermissions();

        // 2) Configura el WebView
        setupWebView();

        // 3) Token FCM
        FirebaseMessaging.getInstance().getToken().addOnCompleteListener(task -> {
            if (!task.isSuccessful()) {
                Log.w(TAG, "FCM token failed", task.getException());
                return;
            }
            String tok = task.getResult();
            Log.d(TAG, "FCM token listo: " + tok);
            getSharedPreferences("fcm", MODE_PRIVATE)
                    .edit().putString("fcm_token", tok).apply();
            runOnUiThread(this::injectFCMTokenIfReady);
        });

        web.loadUrl("https://testing.apps.nexushn.com/nexapps/login");
        // web.loadUrl("https://apps.nexushn.com/nexapps/login");
        // web.loadUrl("https://pedidos.nexushn.com");
    }

    // ------------------ PERMISOS CÁMARA / AUDIO ------------------

    private void checkAndRequestCameraPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            List<String> perms = new ArrayList<>();
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                    != PackageManager.PERMISSION_GRANTED) {
                perms.add(Manifest.permission.CAMERA);
            }
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                    != PackageManager.PERMISSION_GRANTED) {
                perms.add(Manifest.permission.RECORD_AUDIO);
            }
            if (!perms.isEmpty()) {
                ActivityCompat.requestPermissions(
                        this,
                        perms.toArray(new String[0]),
                        CAMERA_PERMISSION_REQ
                );
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == CAMERA_PERMISSION_REQ) {
            boolean allGranted = true;
            for (int res : grantResults) {
                if (res != PackageManager.PERMISSION_GRANTED) {
                    allGranted = false;
                    break;
                }
            }
            if (!allGranted) {
                new AlertDialog.Builder(this)
                        .setTitle("Permisos requeridos")
                        .setMessage("La aplicación necesita permisos de cámara y audio para funcionar correctamente.")
                        .setPositiveButton("Configuración", (d, w) -> {
                            Intent i = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                            i.setData(Uri.fromParts("package", getPackageName(), null));
                            startActivity(i);
                        })
                        .setNegativeButton("Cancelar", (d, w) -> d.dismiss())
                        .show();
            }
        }

        // Manejo de tus otros requestCodes (LOCATION, FILECHOOSER, NOTIF)...
        if (requestCode == LOCATION_PERMISSION_REQ &&
                (grantResults.length == 0 || grantResults[0] != PackageManager.PERMISSION_GRANTED)) {
            abrirAjustes("Se necesita permiso de ubicación");
        }
        if (requestCode == FILECHOOSER_RESULTCODE &&
                (grantResults.length == 0 || grantResults[0] != PackageManager.PERMISSION_GRANTED)) {
            abrirAjustes("Se necesita permiso de almacenamiento");
        }
    }

    // ------------------ CONFIGURACIÓN WEBVIEW ------------------

    @SuppressWarnings("SetJavaScriptEnabled")
    private void setupWebView() {
        web = findViewById(R.id.webView);
        WebSettings ws = web.getSettings();
        ws.setJavaScriptEnabled(true);
        ws.setDomStorageEnabled(true);
        ws.setBuiltInZoomControls(true);
        ws.setDisplayZoomControls(false);
        ws.setLoadWithOverviewMode(true);
        ws.setUseWideViewPort(true);
        ws.setGeolocationEnabled(true);
        ws.setMediaPlaybackRequiresUserGesture(false);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            ws.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
            ws.setSafeBrowsingEnabled(false);
            web.setRendererPriorityPolicy(WebView.RENDERER_PRIORITY_BOUND, true);
        }

        web.addJavascriptInterface(new Object() {
            @JavascriptInterface
            public void sendAuth(String uid, String jwt) throws IOException {
                Log.d(TAG, "► sendAuth(): uid=" + uid + " jwt len=" + (jwt==null?"null":jwt.length()));
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
                askPostNotificationPermissionIfNeeded();
                injectFCMTokenIfReady();
                view.evaluateJavascript(
                        "(function(){ if(window.AndroidBridge) AndroidBridge.sendAuth(" +
                                "localStorage.getItem('android_userid')||'', " +
                                "localStorage.getItem('jwtToken')||'' ); })();", null);
            }

            @Override
            public void onReceivedError(WebView view, WebResourceRequest req, WebResourceError err) {
                Log.e(TAG, "❌ Error cargando página: " + err.getErrorCode() + " – " + err.getDescription());
            }

            @Override
            public boolean onRenderProcessGone(WebView view, RenderProcessGoneDetail detail) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    Log.w(TAG, "Render process muerto (crash=" + detail.didCrash() + ")");
                }
                view.destroy();
                recreate();
                return true;
            }
        });

        web.setWebChromeClient(new WebChromeClient() {
            // Grant camera / audio requests
            @Override
            public void onPermissionRequest(final PermissionRequest request) {
                runOnUiThread(() -> {
                    String[] resources = request.getResources();
                    boolean grant = false;
                    for (String r : resources) {
                        if (PermissionRequest.RESOURCE_VIDEO_CAPTURE.equals(r) ||
                                PermissionRequest.RESOURCE_AUDIO_CAPTURE.equals(r)) {
                            grant = true;
                        }
                    }
                    if (grant) request.grant(resources);
                    else       request.deny();
                });
            }

            // File chooser si lo usas
            @Override
            public boolean onShowFileChooser(WebView webView,
                                             ValueCallback<Uri[]> filePathCallback,
                                             FileChooserParams fileChooserParams) {
                MainActivityJava.this.filePathCallback = filePathCallback;
                abrirSelectorDeArchivos();
                return true;
            }
        });

        web.setDownloadListener(new DownloadListener() {
            @Override
            public void onDownloadStart(String url,
                                        String userAgent,
                                        String contentDisposition,
                                        String mimeType,
                                        long contentLength) {
                // 1) Construir el nombre de archivo
                String fileName = URLUtil.guessFileName(url, contentDisposition, mimeType);

                // 2) Crear petición al sistema
                DownloadManager.Request request = new DownloadManager.Request(Uri.parse(url));
                request.setMimeType(mimeType);
                request.addRequestHeader("User-Agent", userAgent);
                request.setTitle(fileName);
                request.setDescription("Descargando archivo...");
                request.allowScanningByMediaScanner();
                request.setNotificationVisibility(
                        DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED
                );

                // 3) Carpeta de destino: Descargas públicas
                request.setDestinationInExternalPublicDir(
                        Environment.DIRECTORY_DOWNLOADS,
                        fileName
                );

                // 4) Encolar en el DownloadManager
                DownloadManager dm = (DownloadManager) getSystemService(DOWNLOAD_SERVICE);
                dm.enqueue(request);

                Toast.makeText(MainActivityJava.this,
                        "Descarga iniciada: " + fileName,
                        Toast.LENGTH_SHORT).show();
            }
        });
    }

    // ------------------ MÉTODOS AUXILIARES ------------------

    // Notificaciones (Android O+)
    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            String id   = "default_channel";
            String name = "General";
            NotificationChannel ch = new NotificationChannel(
                    id, name, NotificationManager.IMPORTANCE_HIGH
            );
            ch.setDescription("Canal por defecto para notificaciones");
            NotificationManager nm = getSystemService(NotificationManager.class);
            nm.createNotificationChannel(ch);
        }
    }

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

    private void injectFCMTokenIfReady() {
        SharedPreferences sp = getSharedPreferences("fcm", MODE_PRIVATE);
        String tok = sp.getString("fcm_token", null);
        if (tok != null && web != null) {
            String esc = tok.replace("'", "\\'");
            web.evaluateJavascript(
                    "localStorage.setItem('fcm_token', '" + esc + "');", null
            );
        }
    }

    private void abrirSelectorDeArchivos() {
        Intent i = new Intent(Intent.ACTION_GET_CONTENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("*/*");
        startActivityForResult(
                Intent.createChooser(i, "Selecciona un archivo"),
                FILECHOOSER_RESULTCODE
        );
    }

    private void abrirAjustes(String message) {
        new AlertDialog.Builder(this)
                .setTitle("Permiso necesario")
                .setMessage(message)
                .setPositiveButton("Configuración", (d, w) -> {
                    Intent i = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                    i.setData(Uri.fromParts("package", getPackageName(), null));
                    startActivity(i);
                })
                .setNegativeButton("Cancelar", (d, w) -> d.dismiss())
                .show();
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

    @Override
    public void onBackPressed() {
        if (web.canGoBack()) web.goBack();
        else super.onBackPressed();
    }

}
