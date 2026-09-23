package com.yawmiyati.app;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.*;
import android.provider.Settings;
import android.view.View;
import android.webkit.*;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.Space;
import android.widget.Toast;

import androidx.core.app.NotificationManagerCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.webkit.WebViewAssetLoader;

import org.json.JSONArray;
import org.json.JSONObject;

import java.time.Instant;

public class MainActivity extends Activity {
    static final int REQ_NOTIFICATIONS = 7001;
    static final String PREFS = "yawmiyati_native";
    static final String ALARMS = "alarms";

    WebView webView;
    Space topInset;
    Space bottomInset;

    @Override
    public void onCreate(Bundle b) {
        super.onCreate(b);

        // Android 15 enforces edge-to-edge for targetSdk 35. Keep the system bars
        // outside the WebView by reserving explicit native inset spacers.
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        getWindow().setStatusBarColor(Color.rgb(246, 248, 251));
        getWindow().setNavigationBarColor(Color.rgb(246, 248, 251));
        if (Build.VERSION.SDK_INT >= 28) {
            getWindow().setNavigationBarDividerColor(Color.rgb(231, 234, 240));
        }
        WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView())
                .setAppearanceLightStatusBars(true);
        WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView())
                .setAppearanceLightNavigationBars(true);

        topInset = new Space(this);
        bottomInset = new Space(this);

        webView = new WebView(this);
        WebSettings s = webView.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setAllowFileAccess(false);
        s.setAllowContentAccess(false);
        s.setAllowFileAccessFromFileURLs(false);
        s.setAllowUniversalAccessFromFileURLs(false);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        s.setCacheMode(WebSettings.LOAD_DEFAULT);
        webView.setBackgroundColor(Color.rgb(246, 248, 251));
        webView.setOverScrollMode(View.OVER_SCROLL_NEVER);

        final WebViewAssetLoader assetLoader = new WebViewAssetLoader.Builder()
                .addPathHandler("/assets/", new WebViewAssetLoader.AssetsPathHandler(this))
                .build();

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                return assetLoader.shouldInterceptRequest(request.getUrl());
            }

            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, String url) {
                return assetLoader.shouldInterceptRequest(Uri.parse(url));
            }

            @Override
            public void onPageFinished(WebView v, String url) {
                v.post(MainActivity.this::syncNotificationStateToJs);
            }
        });
        webView.addJavascriptInterface(new NativeBridge(this), "Android");

        LinearLayout shell = new LinearLayout(this);
        shell.setOrientation(LinearLayout.VERTICAL);
        shell.setBackgroundColor(Color.rgb(246, 248, 251));
        shell.addView(topInset, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0));
        shell.addView(webView, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
        shell.addView(bottomInset, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0));

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.rgb(246, 248, 251));
        root.addView(shell, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));
        setContentView(root);

        ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            WindowInsetsCompat bars = insets.getInsets(
                    WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
            setSpacerHeight(topInset, bars.top);
            setSpacerHeight(bottomInset, bars.bottom);
            return insets;
        });
        ViewCompat.requestApplyInsets(root);

        createChannels();
        webView.loadUrl("https://appassets.androidplatform.net/assets/index.html");
    }

    static void setSpacerHeight(View view, int px) {
        if (view == null) return;
        android.view.ViewGroup.LayoutParams lp = view.getLayoutParams();
        if (lp != null && lp.height != px) {
            lp.height = px;
            view.setLayoutParams(lp);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        if (webView != null) {
            webView.postDelayed(() -> {
                syncNotificationStateToJs();
                rescheduleAll(this);
            }, 250);
        }
    }

    void syncNotificationStateToJs() {
        if (webView == null) return;
        boolean enabled = arePrayerNotificationsEnabled();
        boolean exact = exactAlarmAllowed();
        webView.evaluateJavascript(
                "try{if(window.onNativeNotificationState)window.onNativeNotificationState(" + enabled + "," + exact + ")}catch(e){}",
                null);
    }

    boolean areNotificationsEnabled() {
        return NotificationManagerCompat.from(this).areNotificationsEnabled();
    }

    boolean arePrayerNotificationsEnabled() {
        if (!areNotificationsEnabled()) return false;
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationManager nm = getSystemService(NotificationManager.class);
            NotificationChannel adhan = nm.getNotificationChannel(PrayerAlarmReceiver.ADHAN_CH);
            if (adhan != null && adhan.getImportance() == NotificationManager.IMPORTANCE_NONE) return false;
            NotificationChannel rem = nm.getNotificationChannel(PrayerAlarmReceiver.REM_CH);
            if (rem != null && rem.getImportance() == NotificationManager.IMPORTANCE_NONE) return false;
        }
        return true;
    }

    boolean exactAlarmAllowed() {
        if (Build.VERSION.SDK_INT < 31) return true;
        AlarmManager am = (AlarmManager) getSystemService(ALARM_SERVICE);
        return am != null && am.canScheduleExactAlarms();
    }

    void createChannels() {
        PrayerAlarmReceiver.ensure(this);
    }

    void requestNotificationPermission() {
        createChannels();
        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQ_NOTIFICATIONS);
        } else {
            syncNotificationStateToJs();
            requestExactAlarmIfNeeded();
            rescheduleAll(this);
        }
    }

    void requestExactAlarmIfNeeded() {
        if (Build.VERSION.SDK_INT >= 31 && !exactAlarmAllowed()) {
            try {
                startActivity(new Intent(
                        Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                        Uri.parse("package:" + getPackageName())));
            } catch (Exception ignored) {
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int r, String[] p, int[] g) {
        super.onRequestPermissionsResult(r, p, g);
        if (r == REQ_NOTIFICATIONS) {
            createChannels();
            if (arePrayerNotificationsEnabled()) requestExactAlarmIfNeeded();
            rescheduleAll(this);
            syncNotificationStateToJs();
        }
    }

    static int alarmId(String key) {
        return (key.hashCode() & 0x7fffffff) + 1000;
    }

    static void scheduleOne(Context ctx, String name, String iso, int reminderMinutes, boolean enabled) {
        if (!enabled) return;
        try {
            long prayerAt = Instant.parse(iso).toEpochMilli();
            cancelOne(ctx, name, "adhan");
            cancelOne(ctx, name, "reminder");
            SharedPreferences sp = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
            JSONArray arr;
            try {
                arr = new JSONArray(sp.getString(ALARMS, "[]"));
            } catch (Exception e) {
                arr = new JSONArray();
            }
            JSONArray out = new JSONArray();
            for (int j = 0; j < arr.length(); j++) {
                JSONObject x = arr.getJSONObject(j);
                if (!name.equals(x.optString("name"))) out.put(x);
            }
            out.put(new JSONObject()
                    .put("name", name)
                    .put("iso", iso)
                    .put("reminder", reminderMinutes));
            sp.edit().putString(ALARMS, out.toString()).apply();

            scheduleAt(ctx, name, prayerAt, "adhan");
            if (reminderMinutes > 0) {
                long reminderAt = prayerAt - reminderMinutes * 60_000L;
                if (reminderAt > System.currentTimeMillis()) {
                    scheduleAt(ctx, name, reminderAt, "reminder");
                }
            }
        } catch (Exception ignored) {
        }
    }

    static void scheduleAt(Context ctx, String name, long at, String kind) {
        if (at <= System.currentTimeMillis()) return;
        AlarmManager am = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;
        Intent i = new Intent(ctx, PrayerAlarmReceiver.class)
                .putExtra("name", name)
                .putExtra("kind", kind);
        PendingIntent pi = PendingIntent.getBroadcast(
                ctx,
                alarmId(name + kind),
                i,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        try {
            if (Build.VERSION.SDK_INT >= 31 && am.canScheduleExactAlarms()) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi);
            } else if (Build.VERSION.SDK_INT >= 23) {
                // Graceful fallback when the special exact-alarm access has not been granted yet.
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi);
            } else {
                am.set(AlarmManager.RTC_WAKEUP, at, pi);
            }
        } catch (SecurityException e) {
            try {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi);
            } catch (Exception ignored) {
            }
        }
    }

    static void cancelOne(Context ctx, String name, String kind) {
        try {
            AlarmManager am = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
            if (am == null) return;
            Intent i = new Intent(ctx, PrayerAlarmReceiver.class);
            PendingIntent pi = PendingIntent.getBroadcast(
                    ctx,
                    alarmId(name + kind),
                    i,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            am.cancel(pi);
            pi.cancel();
        } catch (Exception ignored) {
        }
    }

    static void cancelAllAlarms(Context ctx) {
        String[] names = {"الفجر", "الظهر", "العصر", "المغرب", "العشاء"};
        for (String n : names) {
            cancelOne(ctx, n, "adhan");
            cancelOne(ctx, n, "reminder");
        }
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(ALARMS).apply();
    }

    static void rescheduleAll(Context ctx) {
        try {
            SharedPreferences sp = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
            JSONArray arr = new JSONArray(sp.getString(ALARMS, "[]"));
            long now = System.currentTimeMillis();
            for (int i = 0; i < arr.length(); i++) {
                JSONObject x = arr.getJSONObject(i);
                long t = Instant.parse(x.getString("iso")).toEpochMilli();
                if (t > now) {
                    scheduleAt(ctx, x.getString("name"), t, "adhan");
                    int rm = x.optInt("reminder", 0);
                    if (rm > 0 && t - rm * 60_000L > now) {
                        scheduleAt(ctx, x.getString("name"), t - rm * 60_000L, "reminder");
                    }
                }
            }
        } catch (Exception ignored) {
        }
    }

    public static class NativeBridge {
        final MainActivity a;
        NativeBridge(MainActivity x) { a = x; }

        @JavascriptInterface
        public void requestNotificationPermission() { a.runOnUiThread(a::requestNotificationPermission); }

        @JavascriptInterface
        public boolean areNotificationsEnabled() { return a.arePrayerNotificationsEnabled(); }

        @JavascriptInterface
        public boolean isExactAlarmAllowed() { return a.exactAlarmAllowed(); }

        @JavascriptInterface
        public void requestExactAlarmAccess() { a.runOnUiThread(a::requestExactAlarmIfNeeded); }

        @JavascriptInterface
        public void schedulePrayerAlarm(String name, String iso, String json) {
            try {
                JSONObject o = new JSONObject(json);
                boolean enabled = o.optBoolean("enabled", false) || o.optBoolean("sound", false);
                scheduleOne(a, name, iso, o.optInt("reminderMinutes", 0), enabled);
            } catch (Exception ignored) {
            }
        }

        @JavascriptInterface
        public void cancelPrayerAlarms() { cancelAllAlarms(a); }

        @JavascriptInterface
        public void sendTestNotification() {
            a.runOnUiThread(() -> {
                PrayerAlarmReceiver.showTest(a);
            });
        }

        @JavascriptInterface
        public void playAdhan(String name) {
            a.runOnUiThread(() -> {
                try {
                    Intent s = new Intent(a, AdhanService.class)
                            .putExtra("name", name == null ? "تجريبي" : name);
                    if (Build.VERSION.SDK_INT >= 26) a.startForegroundService(s); else a.startService(s);
                } catch (Exception e) {
                    Toast.makeText(a, "تعذر تشغيل الأذان", Toast.LENGTH_SHORT).show();
                }
            });
        }

        @JavascriptInterface
        public void setCity(String city) { /* JS stores the selected city locally. */ }
    }
}
