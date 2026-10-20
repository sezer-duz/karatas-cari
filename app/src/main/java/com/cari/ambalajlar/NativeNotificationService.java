package com.cari.ambalajlar;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Firebase-free, user-authorized HTTPS polling.
 * A user-visible foreground notification is mandatory; Android can still
 * suspend or terminate foreground services depending on device policy.
 */
public final class NativeNotificationService extends Service {
    public static final String ACTION_STOP = "com.cari.ambalajlar.STOP_NATIVE";
    public static final String PREFS = "karatas_native_notifications";
    private static final String BASE = "https://cari.ambalajlar.net";
    private static final String RUNNING_CHANNEL = "karatas_poller";
    private static final String ALERT_CHANNEL = "karatas_native_alerts_v1";
    private static final int SERVICE_ID = 3001;
    private ScheduledExecutorService executor;

    public static void createChannels(android.content.Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager manager = (NotificationManager)context.getSystemService(NOTIFICATION_SERVICE);
            NotificationChannel running = new NotificationChannel(RUNNING_CHANNEL,
                    "Bildirim bağlantısı", NotificationManager.IMPORTANCE_LOW);
            running.setDescription("Karataş Cari arka plan bildirim bağlantısı");
            running.setSound(null, null);
            manager.createNotificationChannel(running);
            NotificationChannel alerts = new NotificationChannel(ALERT_CHANNEL,
                    "Sipariş ve personel mesajları", NotificationManager.IMPORTANCE_HIGH);
            alerts.setDescription("Yeni sipariş, teslimat ve personel mesajları");
            alerts.enableVibration(true);
            alerts.setSound(android.provider.Settings.System.DEFAULT_NOTIFICATION_URI,
                    new android.media.AudioAttributes.Builder()
                            .setUsage(android.media.AudioAttributes.USAGE_NOTIFICATION)
                            .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION).build());
            manager.createNotificationChannel(alerts);
        }
    }

    @Override public void onCreate() {
        super.onCreate();
        createChannels(this);
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_STOP.equals(intent.getAction())) {
            stopSelf();
            return START_NOT_STICKY;
        }
        Intent screen = new Intent(this, MainActivity.class);
        PendingIntent open = PendingIntent.getActivity(this, 3001, screen,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification status = new NotificationCompat.Builder(this, RUNNING_CHANNEL)
                .setContentTitle("Karataş Cari bildirimleri aktif")
                .setContentText("Yeni sipariş ve mesajlar kontrol ediliyor")
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setOngoing(true).setContentIntent(open).build();
        try {
            if (Build.VERSION.SDK_INT >= 29) {
                startForeground(SERVICE_ID, status,
                        android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
            } else startForeground(SERVICE_ID, status);
        } catch (RuntimeException error) {
            Log.w("CariNative", "Cannot start foreground notifications", error);
            stopSelf();
            return START_NOT_STICKY;
        }
        if (executor == null) {
            executor = Executors.newSingleThreadScheduledExecutor();
            executor.scheduleWithFixedDelay(this::safePoll, 0, 15, TimeUnit.SECONDS);
        }
        return START_STICKY;
    }

    private void safePoll() {
        try { poll(); } catch (Exception e) { Log.w("CariNative", "Poll failed; retrying", e); }
    }

    private void poll() throws Exception {
        SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        String secret = prefs.getString("token", "");
        if (secret == null || !secret.matches("[a-f0-9]{64}")
                || !prefs.getBoolean("registered", false)) {stopSelf(); return;}
        long cursor = prefs.getLong("cursor", 0);
        HttpURLConnection connection = null;
        try {
            URL url = new URL(BASE + "/native-poll.php?after=" + cursor);
            connection = (HttpURLConnection)url.openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(6000);
            connection.setReadTimeout(8000);
            connection.setRequestProperty("Authorization", "Bearer " + secret);
            connection.setRequestProperty("Accept", "application/json");
            connection.setUseCaches(false);
            int response = connection.getResponseCode();
            if (response == 401 || response == 403) {
                prefs.edit().putBoolean("registered", false).apply();
                stopSelf();
                return;
            }
            if (response != 200) return;
            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(connection.getInputStream(), java.nio.charset.StandardCharsets.UTF_8))) {
                char[] buffer = new char[2048];
                int count;
                while ((count = reader.read(buffer)) != -1) {
                    output.append(buffer, 0, count);
                    if (output.length() > 128000) throw new IllegalStateException("Response too large");
                }
            }
            JSONObject result = new JSONObject(output.toString());
            JSONArray events = result.optJSONArray("items");
            if (events == null) return;
            for (int i = 0; i < events.length(); i++) {
                JSONObject item = events.getJSONObject(i);
                long id = item.optLong("id", 0);
                if (id <= cursor) continue;
                notifyEvent(item, id);
                cursor = id;
                prefs.edit().putLong("cursor", cursor).apply();
            }
        } finally { if (connection != null) connection.disconnect(); }
    }

    private void notifyEvent(JSONObject item, long id) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) return;
        String route = item.optString("route", "dashboard");
        if (!route.equals("messages") && !route.matches("order-show&id=[0-9]{1,15}"))
            route = "dashboard";
        Intent launch = new Intent(this, MainActivity.class);
        launch.putExtra("push_route", route);
        launch.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pending = PendingIntent.getActivity(this, (int)(id & 0x7fffffff),
                launch, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        String title = item.optString("title", "Karataş Cari");
        String body = item.optString("body", "Yeni bildiriminiz var.");
        NotificationCompat.Builder notification = new NotificationCompat.Builder(this, ALERT_CHANNEL)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle(title).setContentText(body)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(body))
                .setAutoCancel(true).setPriority(NotificationCompat.PRIORITY_HIGH)
                .setDefaults(Notification.DEFAULT_SOUND | Notification.DEFAULT_VIBRATE)
                .setContentIntent(pending);
        ((NotificationManager)getSystemService(NOTIFICATION_SERVICE))
                .notify((int)(id & 0x7fffffff), notification.build());
    }

    @Override public void onDestroy() {
        if (executor != null) {executor.shutdownNow();executor = null;}
        super.onDestroy();
    }
    @Override public IBinder onBind(Intent intent) {return null;}
}
