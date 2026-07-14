// -*- Mode: Java; tab-width: 4; indent-tabs-mode: nil; -*-
// vim: et ts=4 sts=4 sw=4 syntax=java
package com.aragaer.jtt.mechanics;

import android.content.Context;
import android.content.Intent;

import com.aragaer.jtt.core.*;


/* Sticky broadcasts are dead on Android 14 for NOT_EXPORTED receivers:
 * live sends lose the sender identity and get dropped.  So: ordinary
 * same-app broadcast (always delivered) + a static copy of the last
 * tick for anyone who registers mid-flight and needs instant state -
 * that was the only thing sticky ever gave us. */
public class AndroidAnnouncer implements Announcer {

    private static volatile Intent lastTick;

    private final Context _context;
    private final IntervalProvider _intervalProvider;

    public AndroidAnnouncer(Context context, IntervalProvider intervalProvider) {
	_context = context;
	_intervalProvider = intervalProvider;
    }

    /* last announced tick, for instant state at registration time */
    public static Intent getLastTick() {
        return lastTick == null ? null : new Intent(lastTick);
    }

    @Override public void announce(long timestamp) {
	ThreeIntervals intervals = _intervalProvider.getIntervalsForTimestamp(timestamp);
	Hour hour = Hour.fromInterval(intervals.getMiddleInterval(), timestamp);
	Intent intent = new Intent(AndroidTicker.ACTION_JTT_TICK)
	    .putExtra("intervals", intervals)
	    .putExtra("hour", hour.num)
	    .putExtra("jtt", hour.wrapped);
	lastTick = intent;
	_context.sendBroadcast(new Intent(intent).setPackage(_context.getPackageName()));
    }
}
