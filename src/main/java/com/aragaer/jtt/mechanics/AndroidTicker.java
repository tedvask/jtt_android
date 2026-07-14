// -*- Mode: Java; tab-width: 4; indent-tabs-mode: nil; -*-
// vim: et ts=4 sts=4 sw=4 syntax=java
package com.aragaer.jtt.mechanics;

import com.aragaer.jtt.core.Clockwork;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;


/* One mechanism, no races: every tick is an exact alarm.  Alarm fires
 * (unfreezing the process if an OEM battery manager froze it), we
 * announce and arm the next one.  That's all. */
public class AndroidTicker implements Ticker {
    public static final String ACTION_JTT_TICK = "com.aragaer.jtt.action.TICK";

    static volatile AndroidTicker instance;

    private final Clockwork _clockwork;
    private final Announcer _announcer;
    private final AlarmManager _am;
    private final PendingIntent _pi;

    public AndroidTicker(Context context, Clockwork clockwork, Announcer announcer) {
        _clockwork = clockwork;
        _announcer = announcer;
        _am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= 23)
            flags |= PendingIntent.FLAG_IMMUTABLE;
        _pi = PendingIntent.getBroadcast(context, 0,
                new Intent(context, TickAlarmReceiver.class), flags);
        instance = this;
    }

    public void start() {
        tick();
    }

    public void stop() {
        _am.cancel(_pi);
    }

    void tick() {
        long now = System.currentTimeMillis();
        _clockwork.setTime(now);
        long next = ((now - _clockwork.start) / _clockwork.repeat + 1)
                * _clockwork.repeat + _clockwork.start;
        arm(next);
        Log.i("jttclock", "armed next in " + (next - now) + " ms");
        try {
            _announcer.announce(now);
        } catch (Throwable t) {
            Log.e("jttclock", "announce FAILED", t);
        }
    }

    private void arm(long at) {
        try {
            if (Build.VERSION.SDK_INT >= 31 && !_am.canScheduleExactAlarms())
                _am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, _pi);
            else if (Build.VERSION.SDK_INT >= 23)
                _am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, _pi);
            else
                _am.set(AlarmManager.RTC_WAKEUP, at, _pi);
        } catch (SecurityException e) {
            _am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, _pi);
        }
    }
}
