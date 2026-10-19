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

public final class MainActivity extends Activity {
    // Change this to your own HTTPS site BEFORE shipping a variant for another server.
    private static final String SITE = "https://cari.ambalajlar.net";
    private static final Uri TRUSTED_ORIGIN = Uri.parse(SITE);
    private static final int FILE_PICKER = 1;
    private static final int NATIVE_SCANNER = 2;
    private static final int SCAN_CAMERA_PERMISSION = 101;

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
        webView.loadUrl(SITE + "/");
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
