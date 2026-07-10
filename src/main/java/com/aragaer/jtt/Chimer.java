// -*- Mode: Java; tab-width: 4; indent-tabs-mode: nil; -*-
// vim: et ts=4 sts=4 sw=4 syntax=java
package com.aragaer.jtt;

import java.util.Calendar;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.util.Log;

import com.aragaer.jtt.core.ChimeLogic;
import com.aragaer.jtt.mechanics.AndroidTicker;


public class Chimer extends BroadcastReceiver {
    private static final long STRIKE_INTERVAL_MS = 3000;

    private final JttService context;
    private final SharedPreferences pref;
    private final Handler handler = new Handler();
    private int lastWrapped = -1;

    public Chimer(final JttService ctx) {
        context = ctx;
        pref = PreferenceManager.getDefaultSharedPreferences(ctx);
        context.registerReceiver(this, new IntentFilter(AndroidTicker.ACTION_JTT_TICK));
    }

    public void release() {
        context.unregisterReceiver(this);
        handler.removeCallbacksAndMessages(null);
    }

    @Override public void onReceive(Context ctx, Intent intent) {
        if (!AndroidTicker.ACTION_JTT_TICK.equals(intent.getAction()))
            return;
        final int wrapped = intent.getIntExtra("jtt", -1);
        if (wrapped < 0)
            return;
        final int mode = parseInt(pref.getString(Settings.PREF_CHIME_WHEN, "1"), ChimeLogic.AT_CENTRE);
        final int strikes = ChimeLogic.strikesFor(lastWrapped, wrapped, mode);
        lastWrapped = wrapped;
        if (strikes == 0)
            return;
        if (pref.getBoolean(Settings.PREF_QUIET, true)) {
            final int from = parseInt(pref.getString(Settings.PREF_QUIET_FROM, "23"), 23);
            final int to = parseInt(pref.getString(Settings.PREF_QUIET_TO, "7"), 7);
            final int hourOfDay = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
            if (ChimeLogic.isQuiet(hourOfDay, from, to))
                return;
        }
        strike(strikes);
    }

    private void strike(final int count) {
        for (int i = 0; i < count; i++)
            handler.postDelayed(new Runnable() {
                @Override public void run() {
                    playOnce();
                }
            }, i * STRIKE_INTERVAL_MS);
    }

    private void playOnce() {
        MediaPlayer player = null;
        try {
            if (pref.getBoolean(Settings.PREF_CHIME_BUILTIN, true)) {
                player = MediaPlayer.create(context, R.raw.bell);
            } else {
                final String uri = pref.getString(Settings.PREF_CHIME_RINGTONE, "");
                player = uri.isEmpty()
                    ? MediaPlayer.create(context, R.raw.bell)
                    : MediaPlayer.create(context, Uri.parse(uri));
            }
            if (player == null)
                return;
            player.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
                @Override public void onCompletion(MediaPlayer done) {
                    done.release();
                }
            });
            player.start();
        } catch (Exception e) {
            Log.w("jtt", "Chime failed", e);
            if (player != null)
                player.release();
        }
    }

    private static int parseInt(String value, int fallback) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
}
