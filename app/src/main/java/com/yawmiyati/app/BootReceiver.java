package com.yawmiyati.app;

import android.content.*;

public class BootReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context c, Intent i) {
        String a = i.getAction();
        if (Intent.ACTION_BOOT_COMPLETED.equals(a)
                || Intent.ACTION_MY_PACKAGE_REPLACED.equals(a)
                || Intent.ACTION_TIME_CHANGED.equals(a)
                || Intent.ACTION_TIMEZONE_CHANGED.equals(a)
                || AlarmManagerCompat.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED.equals(a)) {
            MainActivity.rescheduleAll(c);
        }
    }

    // Kept in one place so the receiver compiles on API 31+ without referencing
    // a missing framework constant on older releases.
    static final class AlarmManagerCompat {
        static final String ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED =
                "android.app.action.SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED";
    }
}
