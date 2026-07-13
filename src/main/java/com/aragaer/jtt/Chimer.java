// -*- Mode: Java; tab-width: 4; indent-tabs-mode: nil; -*-
// vim: et ts=4 sts=4 sw=4 syntax=java
package com.aragaer.jtt;

import java.util.Calendar;
import java.util.concurrent.TimeUnit;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.util.Log;

import android.os.Build;

import androidx.core.app.NotificationCompat;

import com.aragaer.jtt.core.ChimeLogic;
import com.aragaer.jtt.core.Hour;
import com.aragaer.jtt.mechanics.AndroidTicker;
import com.aragaer.jtt.resources.RuntimeResources;


public class Chimer extends BroadcastReceiver {
    private static final long STRIKE_INTERVAL_MS = 3000;
    private static final int OUT_SOUND = 0, OUT_NOTIFY = 1, OUT_BOTH = 2;
    private static final int NOTIFICATION_ID = 2;
    private static final String CHANNEL_ID = "jtt_chime_channel";

    private final JttService context;
    private final SharedPreferences pref;
    private final Handler handler = new Handler();
    private int lastWrapped = -1;

    public Chimer(final JttService ctx) {
        context = ctx;
        pref = PreferenceManager.getDefaultSharedPreferences(ctx);
        createNotificationChannel();
        if (android.os.Build.VERSION.SDK_INT >= 33)
            context.registerReceiver(this, new IntentFilter(AndroidTicker.ACTION_JTT_TICK),
                    android.content.Context.RECEIVER_NOT_EXPORTED);
        else
            context.registerReceiver(this, new IntentFilter(AndroidTicker.ACTION_JTT_TICK));
    }

    public void release() {
        context.unregisterReceiver(this);
        handler.removeCallbacksAndMessages(null);
        deleteNotificationChannel();
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
        final int output = parseInt(pref.getString(Settings.PREF_CHIME_OUTPUT, "0"), OUT_SOUND);
        if (output == OUT_SOUND || output == OUT_BOTH)
            strike(strikes);
        if (output == OUT_NOTIFY || output == OUT_BOTH)
            notifyChime(Hour.fromTickNumber(wrapped).num, strikes);
    }

    private void notifyChime(final int hourNum, final int strikes) {
        NotificationManager nm =
            (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null)
            return;
        String strikesText = context.getResources()
            .getQuantityString(R.plurals.chime_strikes, strikes, strikes);
        NotificationCompat.Builder builder = new NotificationCompat.Builder(context)
            .setSmallIcon(R.drawable.notification_icon, hourNum)
            .setContentTitle(Hour.Glyphs[hourNum] + " " +
                RuntimeResources.get(context).getStringResources().getHrOf(hourNum))
            .setContentText(strikesText)
            .setAutoCancel(true)
            .setContentIntent(PendingIntent.getActivity(context, 0,
                new Intent(context, JTTMainActivity.class),
                Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0))
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setChannelId(CHANNEL_ID);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            builder.setTimeoutAfter(TimeUnit.MINUTES.toMillis(30));
        nm.notify(NOTIFICATION_ID, builder.getNotification());
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O)
            return;
        NotificationManager nm =
            (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null)
            return;
        NotificationChannel channel = new NotificationChannel(CHANNEL_ID,
            context.getString(R.string.chimes), NotificationManager.IMPORTANCE_LOW);
        nm.createNotificationChannel(channel);
    }

    private void deleteNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O)
            return;
        NotificationManager nm =
            (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null)
            nm.deleteNotificationChannel(CHANNEL_ID);
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
