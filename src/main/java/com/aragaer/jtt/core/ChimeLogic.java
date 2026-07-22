// -*- Mode: Java; tab-width: 4; indent-tabs-mode: nil; -*-
// vim: et ts=4 sts=4 sw=4 syntax=java
package com.aragaer.jtt.core;


public class ChimeLogic {
    public static final int AT_START = 0, AT_CENTRE = 1, AT_BOTH = 2;

    /* Traditional bell counts, indexed by Hour.num
     * (0 = Cock ... 11 = Monkey): 9 at Rat/Horse descending to 4. */
    private static final int[] BELLS = {6, 5, 4, 9, 8, 7, 6, 5, 4, 9, 8, 7};

    /* Traditional strike count of the hour, by Hour.num. */
    public static int bellsFor(int hourNum) {
        return BELLS[hourNum];
    }

    /* Returns the number of strikes due at this tick, or 0. */
    public static int strikesFor(int prevWrapped, int wrapped, int mode) {
        if (prevWrapped < 0 || prevWrapped == wrapped)
            return 0; // first delivery or duplicate
        Hour hour = Hour.fromTickNumber(wrapped);
        boolean atStart = hour.quarter == 0 && hour.tick == 0;
        boolean atCentre = hour.quarter == 2 && hour.tick == 0;
        if (atStart && (mode == AT_START || mode == AT_BOTH))
            return BELLS[hour.num];
        if (atCentre && (mode == AT_CENTRE || mode == AT_BOTH))
            return BELLS[hour.num];
        return 0;
    }

    /* Timestamp of the current hour's centre - its bell moment.
     * The current tick always lies in the middle interval; the centre
     * is at most half an hour away, so it lands either inside that
     * interval or exactly on its far edge (tick 240 = transitions[2]). */
    public static long bellTimestamp(long[] transitions, boolean isDay, int wrapped) {
        int tickInMiddle = wrapped - (isDay ? Hour.TICKS_PER_INTERVAL : 0);
        int phase = wrapped % Hour.TICKS_PER_HOUR;
        int centre = phase < Hour.TICKS_PER_HOUR / 2
            ? tickInMiddle - phase
            : tickInMiddle + Hour.TICKS_PER_HOUR - phase;
        long start = transitions[1], length = transitions[2] - transitions[1];
        return start + centre * length / Hour.TICKS_PER_INTERVAL;
    }

    /* Quiet hours by conventional clock, [from..to) wrapping midnight.
     * from == to disables nothing is a degenerate full-quiet: treat as
     * "always quiet" to honour the user's explicit equal bounds. */
    public static boolean isQuiet(int hourOfDay, int from, int to) {
        if (from == to)
            return true;
        if (from < to)
            return hourOfDay >= from && hourOfDay < to;
        return hourOfDay >= from || hourOfDay < to;
    }

    /* koku within the hour: 0 at hour start, 20 exactly at the bell, 39 last */
    public static int kokuOfHour(int wrapped) {
        return (wrapped + Hour.TICKS_PER_HOUR / 2) % Hour.TICKS_PER_HOUR;
    }

    /* vernacular Edo clock string, strike-count:koku - e.g. "7:23" */
    public static String clockString(Hour hour) {
        return String.format(java.util.Locale.US, "%d:%02d",
                bellsFor(hour.num), kokuOfHour(hour.wrapped));
    }

    /* Timestamp of a tick given in middle-interval coordinates.  The
     * four transition points cover one interval before and one after
     * the current one, enough for the current hour plus three ahead. */
    public static long timeOfTick(long[] transitions, int t) {
        final int T = Hour.TICKS_PER_INTERVAL;
        if (t < 0)
            return transitions[0] + (t + T) * (transitions[1] - transitions[0]) / T;
        if (t <= T)
            return transitions[1] + t * (transitions[2] - transitions[1]) / T;
        return transitions[2] + (t - T) * (transitions[3] - transitions[2]) / T;
    }

    /* minute-granular quiet window; from == to keeps the documented
     * "always quiet" degenerate meaning */
    public static boolean isQuietMinutes(int minuteOfDay, int fromMin, int toMin) {
        if (fromMin == toMin)
            return true;
        if (fromMin < toMin)
            return minuteOfDay >= fromMin && minuteOfDay < toMin;
        return minuteOfDay >= fromMin || minuteOfDay < toMin;
    }
}
