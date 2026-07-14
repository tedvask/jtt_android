// -*- Mode: Java; tab-width: 4; indent-tabs-mode: nil; -*-
// vim: et ts=4 sts=4 sw=4 syntax=java
package com.aragaer.jtt;

import com.aragaer.jtt.core.*;
import com.aragaer.jtt.mechanics.AndroidTicker;
import com.aragaer.jtt.resources.RuntimeResources;
import com.aragaer.jtt.resources.StringResources;
import com.aragaer.jtt.resources.StringResources.StringResourceChangeListener;

import android.app.*;
import android.content.*;
import androidx.core.app.NotificationCompat;

import android.os.Build;
import android.widget.RemoteViews;


public class JttStatus extends BroadcastReceiver implements StringResourceChangeListener {
    private static final int APP_ID = 1;
    private static final String CHANNEL_ID = "jtt_notification_channel";

    private final JttService context;
    private final StringResources sr;
    private Hour h = new Hour(0);
    private long start, end;
    private ThreeIntervals lastIntervals;
    private int lastWrapped = -1;
    private final NotificationManager nm;

    public JttStatus(final JttService ctx) {
        context = ctx;
        nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        createNotificationChannel();
        sr = RuntimeResources.get(context).getStringResources();
        sr.registerStringResourceChangeListener(this,
                                                StringResources.TYPE_HOUR_NAME | StringResources.TYPE_TIME_FORMAT);

        if (Build.VERSION.SDK_INT >= 33)
            context.registerReceiver(this, new IntentFilter(AndroidTicker.ACTION_JTT_TICK),
                                     Context.RECEIVER_NOT_EXPORTED);
        else
            context.registerReceiver(this, new IntentFilter(AndroidTicker.ACTION_JTT_TICK));

        android.content.Intent last = com.aragaer.jtt.mechanics.AndroidAnnouncer.getLastTick();
        if (last != null)
            onReceive(context, last);
    }

    public void release() {
        context.stopForeground(true);
        sr.unregisterStringResourceChangeListener(this);
        context.unregisterReceiver(this);
        deleteNotificationChannel();
    }

    @Override public void onReceive(Context ctx, Intent intent) {
        final String action = intent.getAction();
        if (!AndroidTicker.ACTION_JTT_TICK.equals(action))
            return;

        final int wrapped = intent.getIntExtra("jtt", -1);
        if (wrapped >= 0)
            lastWrapped = wrapped;
        final ThreeIntervals data = (ThreeIntervals) intent.getSerializableExtra("intervals");
        if (data != null)
            lastIntervals = data;

        // FIXME: kicking widgets from here
        try {
            Intent widgetIntent = new Intent(intent);
            widgetIntent.setClass(ctx, JTTWidgetProvider.Widget1.class);
            ctx.sendBroadcast(widgetIntent);
            widgetIntent.setClass(ctx, JTTWidgetProvider.Widget12.class);
            ctx.sendBroadcast(widgetIntent);
        } catch (Throwable t) {
            android.util.Log.e("jtt", "widget kick failed", t);
        }

        if (lastIntervals == null || lastWrapped < 0)
            return;
        try {
            setIntervals(lastIntervals, Hour.fromTickNumber(lastWrapped));
        } catch (Throwable t) {
            android.util.Log.e("jtt", "status update failed", t);
        }
    }

    private void setIntervals(ThreeIntervals intervals, Hour hour) {
        Interval currentInterval = intervals.getMiddleInterval();
        h = hour;
        final long[] tr = intervals.getTransitions();
        final int lower = Hour.lowerBoundary(h.num),
            upper = Hour.upperBoundary(h.num);
        start = Hour.getHourBoundary(currentInterval.start, currentInterval.end, lower);
        end = Hour.getHourBoundary(currentInterval.start, currentInterval.end, upper);
        if (end < start) {// Cock or Hare
            if (h.quarter >= 2) // we've passed the transition
                start = Hour.getHourBoundary(tr[0], tr[1], lower);
            else
                end = Hour.getHourBoundary(tr[2], tr[3], upper);
        }

        show();
    }

    private void show() {
        try {
            if (Build.VERSION.SDK_INT >= 34)
                context.startForeground(APP_ID, buildNotification(),
                        android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
            else
                context.startForeground(APP_ID, buildNotification());
        } catch (IllegalStateException e) {
            // target 31+: FGS start from background may be restricted;
            // the next tick with the app foregrounded promotes it again.
        }
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O)
            return;
        CharSequence name = context.getString(R.string.app_name);
        int importance = NotificationManager.IMPORTANCE_LOW;
        NotificationChannel channel = new NotificationChannel(CHANNEL_ID, name, importance);
        nm.createNotificationChannel(channel);
    }

    private void deleteNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            nm.deleteNotificationChannel(CHANNEL_ID);
    }

    private String bellText() {
        if (lastIntervals == null)
            return null;
        final long bellTs = ChimeLogic.bellTimestamp(
                lastIntervals.getTransitions(), lastIntervals.isDay(), lastWrapped);
        return context.getString(R.string.notification_bell, sr.format_time(bellTs));
    }

    private Notification buildNotification() {
        int hf = h.quarter * Hour.TICKS_PER_QUARTER + h.tick;
        final String bell = bellText();

        // expanded: the classic full view
        RemoteViews big = new RemoteViews(context.getPackageName(), R.layout.notification);
        big.setTextViewText(R.id.image, Hour.Glyphs[h.num]);
        big.setTextViewText(R.id.title, sr.getHrOf(h.num));
        big.setTextViewText(R.id.quarter,
                bell != null ? bell : sr.getQuarter(h.quarter));
        big.setProgressBar(R.id.fraction, Hour.TICKS_PER_HOUR, hf, false);
        big.setTextViewText(R.id.start, sr.format_time(start));
        big.setTextViewText(R.id.end, sr.format_time(end));

        // collapsed: one honest line - glyph, hour name, bell time
        RemoteViews compact = new RemoteViews(context.getPackageName(), R.layout.notification_compact);
        compact.setTextViewText(R.id.image, Hour.Glyphs[h.num]);
        compact.setTextViewText(R.id.title, sr.getHrOf(h.num));
        compact.setTextViewText(R.id.bounds,
                sr.format_time(start) + "\u2013" + sr.format_time(end));
        compact.setTextViewText(R.id.bell, bell != null ? bell : "");

        return new NotificationCompat.Builder(context)
            .setStyle(new NotificationCompat.DecoratedCustomViewStyle())
            .setCustomContentView(compact)
            .setCustomBigContentView(big)
            .setOngoing(true)
            .setSmallIcon(R.drawable.notification_icon, h.num)
            .setContentIntent(PendingIntent.getActivity(context, 0,
                                                        new Intent(context, JTTMainActivity.class),
                                                        Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0))
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setChannelId(CHANNEL_ID)
            .getNotification();
    }

    public void onStringResourcesChanged(final int changes) {
        show();
    }
}
