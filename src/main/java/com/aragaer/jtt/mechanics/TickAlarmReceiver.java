// -*- Mode: Java; tab-width: 4; indent-tabs-mode: nil; -*-
// vim: et ts=4 sts=4 sw=4 syntax=java
package com.aragaer.jtt.mechanics;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;


public class TickAlarmReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        final AndroidTicker ticker = AndroidTicker.instance;
        if (ticker != null) {
            ticker.tick();
            return;
        }
        Intent service = new Intent();
        service.setClassName(context.getPackageName(), "com.aragaer.jtt.JttService");
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                context.startForegroundService(service);
            else
                context.startService(service);
        } catch (Exception ignored) {}
    }
}
