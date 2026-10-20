package com.cari.ambalajlar;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.DownloadManager;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.webkit.CookieManager;
import android.webkit.DownloadListener;
import android.webkit.PermissionRequest;
import android.webkit.URLUtil;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import androidx.core.content.ContextCompat;
import androidx.webkit.JavaScriptReplyProxy;
import androidx.webkit.WebMessageCompat;
import androidx.webkit.WebViewCompat;
import androidx.webkit.WebViewFeature;

import org.json.JSONObject;

import java.util.Collections;
import java.security.SecureRandom;
import android.content.SharedPreferences;

public final class MainActivity extends Activity {
    // Change this to your own HTTPS site BEFORE shipping a variant for another server.
    private static final String SITE = "https://cari.ambalajlar.net";
    private static final Uri TRUSTED_ORIGIN = Uri.parse(SITE);
    private static final int FILE_PICKER = 1;
    private static final int NATIVE_SCANNER = 2;
    private static final int SCAN_CAMERA_PERMISSION = 101;

    private static final int NOTIFICATION_PERMISSION = 102;
    private String lastRegistrationError = "";
    private WebView webView;
    private ValueCallback<Uri[]> uploadMessage;
    private JavaScriptReplyProxy pendingReply;
    private String pendingRequestId;
    private String pendingPage;
    private PermissionRequest pendingWebCameraRequest;

    private static boolean trusted(Uri uri) {
        return uri != null && "https".equalsIgnoreCase(uri.getScheme())
            && TRUSTED_ORIGIN.getHost().equalsIgnoreCase(uri.getHost())
            && (uri.getPort() == -1 || uri.getPort() == 443);
    }

    private void reply(String status, String barcode, String detail) {
        JavaScriptReplyProxy proxy = pendingReply;
        if (proxy == null) return;
        try {
            // Do not deliver old scans to a different page after navigating while camera was open.
            if (pendingPage != null && pendingPage.equals(webView.getUrl()) && trusted(Uri.parse(webView.getUrl()))) {
                JSONObject response = new JSONObject();
                response.put("id", pendingRequestId);
                response.put("status", status);
                response.put("barcode", barcode == null ? "" : barcode);
                response.put("detail", detail == null ? "" : detail);
                proxy.postMessage(response.toString());
            }
        } catch (Exception error) {
            android.util.Log.w("CariBarcode", "Unable to deliver scan response", error);
        } finally {
            pendingReply = null;
            pendingRequestId = null;
            pendingPage = null;
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        webView = new WebView(this);
        setContentView(webView);
        NativeNotificationService.createChannels(this);
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, NOTIFICATION_PERMISSION);
        }
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(true); // Keep Android content:// file picker uploads working.
        settings.setJavaScriptCanOpenWindowsAutomatically(false);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        WebView.setWebContentsDebuggingEnabled(false);
        CookieManager.getInstance().setAcceptCookie(true);

        if (WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)) {
            WebViewCompat.addWebMessageListener(webView, "KaratasNativePoll",
                Collections.singleton(SITE), (view, message, origin, isMainFrame, replyProxy) -> {
                    if (!isMainFrame || !trusted(origin) || !trusted(Uri.parse(view.getUrl()))
                          || message.getType()!=WebMessageCompat.TYPE_STRING) return;
                    try {
                        JSONObject data = new JSONObject(message.getData());
                        if ("error".equals(data.optString("action"))) {
                            String detail = data.optString("detail", "Sunucuya cihaz kaydı yapılamadı");
                            if (!detail.equals(lastRegistrationError)) {
                                lastRegistrationError = detail;
                                runOnUiThread(() -> Toast.makeText(MainActivity.this,
                                    "Arka plan bildirimleri: " + detail, Toast.LENGTH_LONG).show());
                            }
                            return;
                        }
                        if (!"ready".equals(data.optString("action"))) return;
                        lastRegistrationError = "";
                        SharedPreferences prefs = getSharedPreferences(NativeNotificationService.PREFS, MODE_PRIVATE);
                        if (!prefs.getString("device_id", "").equals(data.optString("device_id"))) return;
                        String user = data.optString("user_id", "");
                        if (!user.matches("[0-9]{1,14}")) return;
                        long cursor = prefs.getLong("cursor", -1);
                        if (!user.equals(prefs.getString("user_id", ""))) cursor = -1;
                        if (cursor < 0) cursor = data.optLong("cursor", 0);
                        prefs.edit().putString("user_id", user).putLong("cursor", cursor)
                            .putBoolean("registered", true).apply();
                        try {
                            Intent start = new Intent(this, NativeNotificationService.class);
                            ContextCompat.startForegroundService(this, start);
                        } catch (RuntimeException error) {
                            android.util.Log.w("CariNative", "Could not start notifications", error);
                        }
                    } catch (Exception error) {
                        android.util.Log.w("CariNative", "Notification registration callback failed", error);
                    }
                });

            WebViewCompat.addWebMessageListener(webView, "KaratasBarcode",
                Collections.singleton(SITE), (view, message, origin, isMainFrame, replyProxy) -> {
                    // Only the trusted top-level HTTPS origin is allowed to launch native camera.
                    if (!isMainFrame || !trusted(origin) || !trusted(Uri.parse(view.getUrl()))) return;
                    if (message.getType() != WebMessageCompat.TYPE_STRING) return;
                    try {
                        JSONObject data = new JSONObject(message.getData());
                        String id = data.optString("id", "");
                        if (!"scan".equals(data.optString("action")) || !id.matches("[A-Za-z0-9_-]{1,50}")) return;
                        if (pendingReply != null) {
                            JSONObject busy = new JSONObject();
                            busy.put("id", id);
                            busy.put("status", "error");
                            busy.put("detail", "Barkod tarayıcı zaten açık.");
                            replyProxy.postMessage(busy.toString());
                            return;
                        }
                        pendingRequestId = id;
                        pendingReply = replyProxy;
                        pendingPage = view.getUrl();
                        startScanner();
                    } catch (Exception ignored) { /* Invalid payload; no native operation. */ }
                });
        }

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                if (!request.isForMainFrame()) return false;
                Uri uri = request.getUrl();
                if (trusted(uri)) return false;
                try {
                    startActivity(new Intent(Intent.ACTION_VIEW, uri));
                } catch (Exception error) {
                    Toast.makeText(MainActivity.this, "Bağlantı açılamadı", Toast.LENGTH_SHORT).show();
                }
                return true;
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                if (trusted(Uri.parse(url))) {
                    // The existing server-side logout form handles the CSRF token and session.
                    // Insert a mobile-only menu entry without changing the hosted PHP site.
                    view.evaluateJavascript("""
                        (function () {
                          var sidebars = document.querySelectorAll('.sidebar-inner');
                          for (var i = 0; i < sidebars.length; i++) {
                            var sidebar = sidebars[i];
                            if (sidebar.querySelector('.karatas-logout-menu')) continue;
                            var links = sidebar.querySelectorAll('a.sidebar-link');
                            var messages = null;
                            for (var j = 0; j < links.length; j++) {
                              if ((links[j].textContent || '').trim() === 'Personel Mesajları') {
                                messages = links[j];
                                break;
                              }
                            }
                            if (!messages) continue;
                            var form = sidebar.querySelector('.sidebar-user form[action]');
                            if (!form || String(form.getAttribute('method')).toLowerCase() !== 'post') continue;
                            var action = new URL(form.getAttribute('action'), window.location.href);
                            if (action.origin !== window.location.origin || action.searchParams.get('route') !== 'logout') continue;
                            var button = document.createElement('button');
                            button.type = 'button';
                            button.className = 'sidebar-link karatas-logout-menu';
                            var icon = document.createElement('i');
                            icon.className = 'bi bi-box-arrow-right';
                            var text = document.createElement('span');
                            text.textContent = 'Çıkış Yap';
                            button.appendChild(icon);
                            button.appendChild(text);
                            button.addEventListener('click', (function (logoutForm) {
                              return function () {
                                if (typeof logoutForm.requestSubmit === 'function') logoutForm.requestSubmit();
                                else logoutForm.submit();
                              };
                            })(form));
                            messages.insertAdjacentElement('afterend', button);
                          }
                        })();
                        """, null);
                }
                if (trusted(Uri.parse(url))) registerNativeNotifications(view);
                if (trusted(Uri.parse(url)) && "login".equals(Uri.parse(url).getQueryParameter("route"))) {
                    stopNativeNotifications();
                    view.clearHistory();
                }
            }
            @Override
            public void onPageStarted(WebView view, String url, android.graphics.Bitmap favicon) {
                if (pendingReply != null && !url.equals(pendingPage)) reply("cancel", null, "Sayfa değişti.");
            }
        });
        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onShowFileChooser(WebView v, ValueCallback<Uri[]> callback, FileChooserParams params) {
                if (uploadMessage != null) uploadMessage.onReceiveValue(null);
                uploadMessage = callback;
                try {
                    startActivityForResult(params.createIntent(), FILE_PICKER);
                    return true;
                } catch (Exception error) {
                    uploadMessage.onReceiveValue(null);
                    uploadMessage = null;
                    Toast.makeText(MainActivity.this, "Dosya seçici açılamadı", Toast.LENGTH_LONG).show();
                    return false;
                }
            }
            @Override
            public void onPermissionRequest(PermissionRequest request) {
                runOnUiThread(() -> {
                    if (!trusted(request.getOrigin()) ||
                        ContextCompat.checkSelfPermission(MainActivity.this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                        request.deny();
                        return;
                    }
                    for (String resource : request.getResources()) {
                        if (!PermissionRequest.RESOURCE_VIDEO_CAPTURE.equals(resource)) {
                            request.deny();
                            return;
                        }
                    }
                    request.grant(new String[]{PermissionRequest.RESOURCE_VIDEO_CAPTURE});
                    pendingWebCameraRequest = request;
                });
            }
            @Override
            public void onPermissionRequestCanceled(PermissionRequest request) {
                if (pendingWebCameraRequest == request) pendingWebCameraRequest = null;
            }
        });
        webView.setDownloadListener((url, agent, disposition, type, length) -> {
            if (!trusted(Uri.parse(url))) {
                Toast.makeText(this, "İndirme adresi güvenilir değil", Toast.LENGTH_SHORT).show();
                return;
            }
            DownloadManager.Request req = new DownloadManager.Request(Uri.parse(url));
            req.setMimeType(type);
            String cookie = CookieManager.getInstance().getCookie(url);
            if (cookie != null) req.addRequestHeader("Cookie", cookie);
            req.addRequestHeader("User-Agent", agent);
            req.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
            req.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, URLUtil.guessFileName(url, disposition, type));
            ((DownloadManager) getSystemService(DOWNLOAD_SERVICE)).enqueue(req);
            Toast.makeText(this, "İndirme başlatıldı", Toast.LENGTH_SHORT).show();
        });
        webView.loadUrl(resolvePushStartUrl(getIntent()));
    }

    private static String resolvePushStartUrl(Intent intent) {
        String route = intent == null ? null : intent.getStringExtra("push_route");
        if (route != null && (route.equals("messages") || route.matches("order-show&id=[0-9]{1,15}"))) {
            return SITE + "/index.php?route=" + route;
        }
        return SITE + "/";
    }

    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        if (webView != null) webView.loadUrl(resolvePushStartUrl(intent));
    }

    private static String randomHex(int size) {
        byte[] bytes = new byte[size];
        new SecureRandom().nextBytes(bytes);
        StringBuilder result = new StringBuilder(size * 2);
        for (byte b : bytes) {
            result.append(Character.forDigit((b >> 4) & 15, 16));
            result.append(Character.forDigit(b & 15, 16));
        }
        return result.toString();
    }

    private void stopNativeNotifications() {
        SharedPreferences prefs = getSharedPreferences(NativeNotificationService.PREFS, MODE_PRIVATE);
        prefs.edit().putBoolean("registered", false).remove("token")
                .remove("user_id").remove("cursor").apply();
        stopService(new Intent(this, NativeNotificationService.class));
    }

    /** A registered session and a CSRF-protected server exchange are required. */
    private void registerNativeNotifications(WebView view) {
        SharedPreferences prefs = getSharedPreferences(NativeNotificationService.PREFS, MODE_PRIVATE);
        String deviceId = prefs.getString("device_id", "");
        if (deviceId.length() != 32) {
            deviceId = randomHex(16);
            prefs.edit().putString("device_id", deviceId).apply();
        }
        String token = prefs.getString("token", "");
        if (token.length() != 64) {
            token = randomHex(32);
            prefs.edit().putString("token", token).apply();
        }
        String js = "(function(){"
          + "var box=document.getElementById('workflowNotifications');"
          + "if(!box||!box.dataset.csrf||!box.dataset.userId)return;"
          + "var req=new URLSearchParams();"
          + "req.set('_token',box.dataset.csrf);"
          + "req.set('device_id'," + JSONObject.quote(deviceId) + ");"
          + "req.set('token'," + JSONObject.quote(token) + ");"
          + "fetch('/index.php?route=native-register',{method:'POST',credentials:'same-origin',"
          + "headers:{'Content-Type':'application/x-www-form-urlencoded;charset=UTF-8'},body:req.toString()})"
          + ".then(function(r){if(!r.ok)throw Error('HTTP '+r.status);return r.json();})"
          + ".then(function(r){if(r.ok&&window.KaratasNativePoll)"
          + "window.KaratasNativePoll.postMessage(JSON.stringify({action:'ready',"
          + "device_id:" + JSONObject.quote(deviceId) + ",user_id:box.dataset.userId,cursor:r.cursor}));"
          + "}).catch(function(e){"
          + "if(window.KaratasNativePoll)window.KaratasNativePoll.postMessage("
          + "JSON.stringify({action:'error',detail:'Kayıt başarısız ('+String(e.message||'ağ')+')'}));"
          + "});"
          + "})();";
        if (view != null && trusted(Uri.parse(view.getUrl()))) view.evaluateJavascript(js, null);
    }

    private void startScanner() {
        if (pendingReply == null || pendingPage == null || !pendingPage.equals(webView.getUrl())) return;
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            try {
                startActivityForResult(new Intent(this, BarcodeScannerActivity.class), NATIVE_SCANNER);
            } catch (Exception error) {
                reply("error", null, "Kamera başlatılamadı.");
            }
        } else if (Build.VERSION.SDK_INT >= 23) {
            requestPermissions(new String[]{Manifest.permission.CAMERA}, SCAN_CAMERA_PERMISSION);
        } else {
            reply("error", null, "Kamera izni bulunmuyor.");
        }
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(code, permissions, results);
        if (code != SCAN_CAMERA_PERMISSION) return;
        if (results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED) startScanner();
        else reply("error", null, "Kamera izni verilmedi. Barkodu elle girebilirsiniz.");
    }

    @Override
    protected void onActivityResult(int code, int result, Intent data) {
        super.onActivityResult(code, result, data);
        if (code == FILE_PICKER) {
            if (uploadMessage != null) {
                uploadMessage.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(result, data));
                uploadMessage = null;
            }
        } else if (code == NATIVE_SCANNER) {
            if (result == RESULT_OK && data != null) {
                String barcode = data.getStringExtra(BarcodeScannerActivity.EXTRA_BARCODE);
                if (barcode != null && !barcode.trim().isEmpty()) reply("ok", barcode, "");
                else reply("error", null, "Barkod okunamadı.");
            } else reply("cancel", null, "İşlem iptal edildi.");
        }
    }

    @Override
    public void onBackPressed() {
        if (webView != null && webView.canGoBack()) webView.goBack();
        else super.onBackPressed();
    }

    @Override
    protected void onDestroy() {
        if (webView != null) {
            webView.destroy();
        }
        super.onDestroy();
    }
}
