package com.yawmiyati.app;

import android.app.*;
import android.content.*;
import android.content.pm.ServiceInfo;
import android.media.*;
import android.os.*;
import androidx.core.app.NotificationCompat;

public class AdhanService extends Service {
    private MediaPlayer player;
    private AudioManager audioManager;
    private AudioFocusRequest audioFocusRequest;
    private static final int ID = 4801;

    @Override public void onCreate() {
        super.onCreate();
        PrayerAlarmReceiver.ensure(this);
        audioManager = (AudioManager) getSystemService(AUDIO_SERVICE);
        Notification n = new NotificationCompat.Builder(this, PrayerAlarmReceiver.SERVICE_CH)
                .setSmallIcon(R.drawable.ic_stat_yawmy)
                .setContentTitle("يومي — الأذان")
                .setContentText("حان وقت الصلاة")
                .setCategory(NotificationCompat.CATEGORY_SERVICE)
                .setOngoing(true)
                .build();
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK);
        } else {
            startForeground(ID, n);
        }
    }

    @Override public int onStartCommand(Intent i, int flags, int startId) {
        stopCurrent();
        try {
            AudioAttributes aa = new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build();

            if (audioManager != null && Build.VERSION.SDK_INT >= 26) {
                audioFocusRequest = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                        .setAudioAttributes(aa)
                        .setWillPauseWhenDucked(false)
                        .build();
                audioManager.requestAudioFocus(audioFocusRequest);
            } else if (audioManager != null) {
                audioManager.requestAudioFocus(null, AudioManager.STREAM_ALARM,
                        AudioManager.AUDIOFOCUS_GAIN_TRANSIENT);
            }

            player = MediaPlayer.create(this, R.raw.adhan, aa, 0);
            if (player == null) throw new IllegalStateException("local adhan unavailable");
            player.setWakeMode(getApplicationContext(), PowerManager.PARTIAL_WAKE_LOCK);
            player.setOnCompletionListener(mp -> stopSelf(startId));
            player.setOnErrorListener((mp, what, extra) -> {
                stopSelf(startId);
                return true;
            });
            player.start();
        } catch (Exception e) {
            stopSelf(startId);
        }
        return START_NOT_STICKY;
    }

    private void stopCurrent() {
        if (player != null) {
            try { if (player.isPlaying()) player.stop(); } catch (Exception ignored) {}
            try { player.release(); } catch (Exception ignored) {}
            player = null;
        }
    }

    @Override public void onDestroy() {
        stopCurrent();
        try {
            if (audioManager != null && Build.VERSION.SDK_INT >= 26 && audioFocusRequest != null) {
                audioManager.abandonAudioFocusRequest(audioFocusRequest);
            }
        } catch (Exception ignored) {}
        super.onDestroy();
    }

    @Override public IBinder onBind(Intent i) { return null; }
}
