package com.cari.ambalajlar;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.ComponentActivity;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.mlkit.vision.barcode.BarcodeScanning;
import com.google.mlkit.vision.barcode.common.Barcode;
import com.google.mlkit.vision.barcode.BarcodeScanner;
import com.google.mlkit.vision.barcode.BarcodeScannerOptions;
import com.google.mlkit.vision.common.InputImage;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/** Fully on-device, one scan per button tap. No Google Play Services model download needed. */
public final class BarcodeScannerActivity extends ComponentActivity {
    public static final String EXTRA_BARCODE = "barcode";
    private PreviewView cameraPreview;
    private BarcodeScanner mlScanner;
    private ExecutorService cameraExecutor;
    private ProcessCameraProvider cameraProvider;
    private final AtomicBoolean decoding = new AtomicBoolean(false);
    private final AtomicBoolean finished = new AtomicBoolean(false);

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            setResult(Activity.RESULT_CANCELED);
            finish();
            return;
        }
        cameraExecutor = Executors.newSingleThreadExecutor();
        BarcodeScannerOptions options = new BarcodeScannerOptions.Builder()
                .setBarcodeFormats(
                    Barcode.FORMAT_EAN_13, Barcode.FORMAT_EAN_8,
                    Barcode.FORMAT_UPC_A, Barcode.FORMAT_UPC_E,
                    Barcode.FORMAT_CODE_128, Barcode.FORMAT_CODE_39,
                    Barcode.FORMAT_CODE_93, Barcode.FORMAT_ITF,
                    Barcode.FORMAT_QR_CODE, Barcode.FORMAT_DATA_MATRIX,
                    Barcode.FORMAT_PDF417, Barcode.FORMAT_AZTEC,
                    Barcode.FORMAT_CODABAR)
                .build();
        mlScanner = BarcodeScanning.getClient(options);
        createInterface();
        startCamera();
    }

    private int dp(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private void createInterface() {
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);
        cameraPreview = new PreviewView(this);
        cameraPreview.setImplementationMode(PreviewView.ImplementationMode.COMPATIBLE);
        root.addView(cameraPreview, new FrameLayout.LayoutParams(-1, -1));

        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.VERTICAL);
        top.setPadding(dp(18), dp(42), dp(18), dp(14));
        top.setBackgroundColor(0xAA000000);
        TextView title = new TextView(this);
        title.setText("Barkodu kameraya gösterin");
        title.setTextSize(20);
        title.setTextColor(Color.WHITE);
        title.setGravity(Gravity.CENTER_HORIZONTAL);
        top.addView(title, new LinearLayout.LayoutParams(-1, -2));
        TextView details = new TextView(this);
        details.setText("Her okutma bir adet sayılır. Yeni ürün için tekrar Barkod Okut'a basın.");
        details.setTextColor(Color.WHITE);
        details.setGravity(Gravity.CENTER_HORIZONTAL);
        details.setPadding(0, dp(8), 0, 0);
        top.addView(details, new LinearLayout.LayoutParams(-1, -2));
        FrameLayout.LayoutParams topParams = new FrameLayout.LayoutParams(-1, -2, Gravity.TOP);
        root.addView(top, topParams);

        Button close = new Button(this);
        close.setText("Vazgeç");
        close.setOnClickListener(v -> finish());
        FrameLayout.LayoutParams buttonParams = new FrameLayout.LayoutParams(-1, dp(58), Gravity.BOTTOM);
        buttonParams.setMargins(dp(22), dp(12), dp(22), dp(28));
        root.addView(close, buttonParams);
        setContentView(root);
    }

    private void startCamera() {
        ListenableFuture<ProcessCameraProvider> future = ProcessCameraProvider.getInstance(this);
        future.addListener(() -> {
            if (isFinishing() || isDestroyed()) return;
            try {
                cameraProvider = future.get();
                Preview preview = new Preview.Builder().build();
                preview.setSurfaceProvider(cameraPreview.getSurfaceProvider());
                ImageAnalysis analysis = new ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build();
                analysis.setAnalyzer(cameraExecutor, this::analyzeFrame);
                cameraProvider.unbindAll();
                cameraProvider.bindToLifecycle(this, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis);
            } catch (Exception error) {
                Toast.makeText(this, "Kamera başlatılamadı: " + error.getMessage(), Toast.LENGTH_LONG).show();
                finish();
            }
        }, ContextCompat.getMainExecutor(this));
    }

    @androidx.camera.core.ExperimentalGetImage
    private void analyzeFrame(ImageProxy frame) {
        if (finished.get() || !decoding.compareAndSet(false, true)) {
            frame.close();
            return;
        }
        android.media.Image image = frame.getImage();
        if (image == null) {
            decoding.set(false);
            frame.close();
            return;
        }
        InputImage input = InputImage.fromMediaImage(image, frame.getImageInfo().getRotationDegrees());
        mlScanner.process(input)
                .addOnSuccessListener(results -> {
                    for (Barcode found : results) {
                        String value = found.getRawValue();
                        if (value != null && !value.trim().isEmpty() && finished.compareAndSet(false, true)) {
                            Intent data = new Intent();
                            data.putExtra(EXTRA_BARCODE, value.trim());
                            setResult(Activity.RESULT_OK, data);
                            finish();
                            break;
                        }
                    }
                })
                .addOnFailureListener(error -> android.util.Log.w("CariBarcode", "Decode failed", error))
                .addOnCompleteListener(task -> {
                    frame.close();
                    decoding.set(false);
                });
    }

    @Override
    protected void onDestroy() {
        if (cameraProvider != null) cameraProvider.unbindAll();
        if (mlScanner != null) mlScanner.close();
        if (cameraExecutor != null) cameraExecutor.shutdown();
        super.onDestroy();
    }
}
