// -*- Mode: Java; tab-width: 4; indent-tabs-mode: nil; -*-
// vim: et ts=4 sts=4 sw=4 syntax=java
package com.aragaer.jtt.core;


/* Everything the widget shows, computed away from any Canvas so it can
 * be unit-tested: the main toki:koku reading, the bell timestamp, the
 * hour bounds, which seam comes next, and the bar fraction. */
public class WidgetContent {
    public final String main;      // e.g. "子\u20099:10"
    public final long bell;        // bell timestamp, 0 when unknown
    public final long hourStart, hourEnd;
    public final long seam;        // end of the current interval
    public final boolean seamIsDusk; // true: dusk comes next; false: dawn
    public final float fraction;   // bar fill, 0..1

    public WidgetContent(final int wrapped, final ThreeIntervals intervals) {
        final Hour h = Hour.fromTickNumber(wrapped, 1);
        main = Hour.Glyphs[h.num] + "\u2009" + ChimeLogic.clockString(h);
        fraction = (h.quarter * Hour.TICKS_PER_QUARTER + h.tick)
                / (float) Hour.TICKS_PER_HOUR;
        if (intervals == null) {
            bell = hourStart = hourEnd = seam = 0;
            seamIsDusk = false;
            return;
        }
        final long[] tr = intervals.getTransitions();
        bell = ChimeLogic.bellTimestamp(tr, intervals.isDay(), wrapped);
        final int half = Hour.TICKS_PER_HOUR / 2;
        final int m = wrapped - (intervals.isDay() ? Hour.TICKS_PER_INTERVAL : 0);
        final int centre = ((m + half) / Hour.TICKS_PER_HOUR) * Hour.TICKS_PER_HOUR;
        hourStart = ChimeLogic.timeOfTick(tr, centre - half);
        hourEnd = ChimeLogic.timeOfTick(tr, centre + half);
        seam = tr[2];
        seamIsDusk = intervals.isDay();
    }
}
