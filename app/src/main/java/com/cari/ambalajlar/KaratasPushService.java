package com.cari.ambalajlar;

import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;

import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

/** Native high-priority notification channel for foreground FCM deliveries.
 * Android displays background notification payloads itself when the app is not in foreground.
 */
public final class KaratasPushService extends FirebaseMessagingService {
    public static final String CHANNEL_ID = "karatas_important";

    public static void createChannel(android.content.Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager manager = (NotificationManager)context.getSystemService(NOTIFICATION_SERVICE);
            if (manager.getNotificationChannel(CHANNEL_ID) != null) return;
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID,
                    "Sipariş ve personel mesajları", NotificationManager.IMPORTANCE_HIGH);
            channel.setDescription("Muhasebe, depo, teslimat ve personel mesaj bildirimleri");
            channel.enableVibration(true);
            channel.enableLights(true);
            channel.setSound(android.provider.Settings.System.DEFAULT_NOTIFICATION_URI,
                    new android.media.AudioAttributes.Builder()
                        .setUsage(android.media.AudioAttributes.USAGE_NOTIFICATION)
                        .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION).build());
            manager.createNotificationChannel(channel);
        }
    }

    @Override public void onNewToken(String token) {
        super.onNewToken(token);
        // The token is re-registered securely on the next authenticated WebView page load.
    }

    @Override public void onMessageReceived(RemoteMessage msg) {
        super.onMessageReceived(msg);
        if (Build.VERSION.SDK_INT >= 33
                && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                   != PackageManager.PERMISSION_GRANTED) return;
        createChannel(this);
        String route = msg.getData().get("route");
        if (route == null || !(route.equals("messages") || route.matches("order-show&id=[0-9]{1,15}"))) {
            route = "dashboard";
        }
        String title = msg.getNotification() != null ? msg.getNotification().getTitle() : null;
        String body = msg.getNotification() != null ? msg.getNotification().getBody() : null;
        if (title == null) title = "Karataş Cari";
        if (body == null) body = "Yeni bildiriminiz var.";
        Intent open = new Intent(this, MainActivity.class);
        open.putExtra("push_route", route);
        open.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        int notificationId = (int)(System.nanoTime() & 0x7fffffff);
        PendingIntent pending = PendingIntent.getActivity(this, notificationId, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        NotificationCompat.Builder notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle(title).setContentText(body)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(body))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true).setContentIntent(pending)
                .setDefaults(android.app.Notification.DEFAULT_SOUND | android.app.Notification.DEFAULT_VIBRATE);
        ((NotificationManager)getSystemService(NOTIFICATION_SERVICE)).notify(notificationId, notification.build());
    }
}
