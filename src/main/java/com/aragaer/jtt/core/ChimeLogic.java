// -*- Mode: Java; tab-width: 4; indent-tabs-mode: nil; -*-
// vim: et ts=4 sts=4 sw=4 syntax=java
package com.aragaer.jtt.core;


public class ChimeLogic {
    public static final int AT_START = 0, AT_CENTRE = 1, AT_BOTH = 2;

    /* Traditional bell counts, indexed by Hour.num
     * (0 = Cock ... 11 = Monkey): 9 at Rat/Horse descending to 4. */
    private static final int[] BELLS = {6, 5, 4, 9, 8, 7, 6, 5, 4, 9, 8, 7};

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
}
