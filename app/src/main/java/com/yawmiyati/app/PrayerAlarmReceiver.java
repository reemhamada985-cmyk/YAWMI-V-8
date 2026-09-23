package com.yawmiyati.app;

import android.app.*;
import android.content.*;
import androidx.core.app.NotificationCompat;

public class PrayerAlarmReceiver extends BroadcastReceiver {
    static final String ADHAN_CH = "prayer_adhan_v4";
    static final String REM_CH = "prayer_reminder_v4";
    static final String SERVICE_CH = "prayer_service_v4";

    @Override public void onReceive(Context c, Intent i) {
        String name = i.getStringExtra("name");
        if (name == null || name.trim().isEmpty()) name = "الصلاة";
        String kind = i.getStringExtra("kind");
        if ("reminder".equals(kind)) showReminder(c, name); else showAdhan(c, name);
    }

    static void ensure(Context c) {
        if (android.os.Build.VERSION.SDK_INT < 26) return;
        NotificationManager nm = c.getSystemService(NotificationManager.class);
        if (nm == null) return;

        NotificationChannel adhan = new NotificationChannel(
                ADHAN_CH, "أذان الصلاة", NotificationManager.IMPORTANCE_HIGH);
        adhan.enableVibration(true);
        adhan.setSound(null, null);
        adhan.setShowBadge(true);
        nm.createNotificationChannel(adhan);

        NotificationChannel reminder = new NotificationChannel(
                REM_CH, "تذكيرات الصلاة", NotificationManager.IMPORTANCE_HIGH);
        reminder.enableVibration(true);
        nm.createNotificationChannel(reminder);

        NotificationChannel service = new NotificationChannel(
                SERVICE_CH, "تشغيل الأذان", NotificationManager.IMPORTANCE_LOW);
        service.setSound(null, null);
        service.enableVibration(false);
        service.setShowBadge(false);
        nm.createNotificationChannel(service);
    }

    static PendingIntent open(Context c) {
        Intent o = new Intent(c, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        return PendingIntent.getActivity(c, 9001, o,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    static void showAdhan(Context c, String name) {
        ensure(c);
        if (androidx.core.app.NotificationManagerCompat.from(c).areNotificationsEnabled()) {
            NotificationCompat.Builder b = new NotificationCompat.Builder(c, ADHAN_CH)
                    .setSmallIcon(R.drawable.ic_stat_yawmy)
                    .setContentTitle("يومي — حان وقت صلاة " + name)
                    .setContentText("حان الآن وقت صلاة " + name + ".")
                    .setPriority(NotificationCompat.PRIORITY_MAX)
                    .setCategory(NotificationCompat.CATEGORY_ALARM)
                    .setAutoCancel(true)
                    .setContentIntent(open(c));
            androidx.core.app.NotificationManagerCompat.from(c)
                    .notify(2000 + Math.abs(name.hashCode()), b.build());
        }

        try {
            Intent s = new Intent(c, AdhanService.class).putExtra("name", name);
            if (android.os.Build.VERSION.SDK_INT >= 26) c.startForegroundService(s);
            else c.startService(s);
        } catch (Exception ignored) {
        }
    }

    static void showReminder(Context c, String name) {
        ensure(c);
        if (!androidx.core.app.NotificationManagerCompat.from(c).areNotificationsEnabled()) return;
        NotificationCompat.Builder b = new NotificationCompat.Builder(c, REM_CH)
                .setSmallIcon(R.drawable.ic_stat_yawmy)
                .setContentTitle("يومي — تذكير صلاة " + name)
                .setContentText("اقترب موعد أذان " + name + ".")
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setContentIntent(open(c));
        androidx.core.app.NotificationManagerCompat.from(c)
                .notify(3000 + Math.abs(name.hashCode()), b.build());
    }

    static void showTest(Context c) {
        ensure(c);
        if (!androidx.core.app.NotificationManagerCompat.from(c).areNotificationsEnabled()) return;
        NotificationCompat.Builder b = new NotificationCompat.Builder(c, REM_CH)
                .setSmallIcon(R.drawable.ic_stat_yawmy)
                .setContentTitle("يومي — اختبار الإشعارات")
                .setContentText("الإشعارات تعمل على هذا الجهاز.")
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setContentIntent(open(c));
        androidx.core.app.NotificationManagerCompat.from(c).notify(3999, b.build());
    }
}
