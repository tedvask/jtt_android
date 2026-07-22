// -*- Mode: Java; tab-width: 4; indent-tabs-mode: nil; -*-
// vim: et ts=4 sts=4 sw=4 syntax=java
package com.aragaer.jtt.core;

import static org.junit.Assert.*;

import org.junit.*;


/* JttStatus.setIntervals composes hour bounds from getHourBoundary with a
 * special branch for the Cock and the Hare (quarter >= 2 picks the previous
 * interval, otherwise the next).  These tests pin that legacy composition
 * to ChimeLogic.timeOfTick: the two must agree on every tick of the day.
 * Once they are proven equal, setIntervals may simply call timeOfTick and
 * the special branch can be retired. */
public class SeamBoundsTest {

    // interval lengths divisible by both 240 and 12 so that the integer
    // divisions of both formulas land on exactly the same millisecond
    private static final long[] DAY_FRAME = {0, 240_000, 600_000, 840_000};
    private static final long[] NIGHT_FRAME = {-360_000, 0, 240_000, 600_000};

    private static long[] legacyBounds(long[] tr, Hour h) {
        final int lower = Hour.lowerBoundary(h.num), upper = Hour.upperBoundary(h.num);
        long start = Hour.getHourBoundary(tr[1], tr[2], lower);
        long end = Hour.getHourBoundary(tr[1], tr[2], upper);
        if (end < start) { // Cock or Hare
            if (h.quarter >= 2)
                start = Hour.getHourBoundary(tr[0], tr[1], lower);
            else
                end = Hour.getHourBoundary(tr[2], tr[3], upper);
        }
        return new long[] {start, end};
    }

    private static long[] newBounds(long[] tr, int wrapped) {
        final int half = Hour.TICKS_PER_HOUR / 2;
        final int m = wrapped % Hour.TICKS_PER_INTERVAL;
        final int centre = ((m + half) / Hour.TICKS_PER_HOUR) * Hour.TICKS_PER_HOUR;
        return new long[] {ChimeLogic.timeOfTick(tr, centre - half),
                           ChimeLogic.timeOfTick(tr, centre + half)};
    }

    @Test public void legacyAndTimeOfTickAgreeOnEveryTickOfTheDay() {
        for (int wrapped = 0; wrapped < Hour.TICKS_PER_DAY; wrapped++) {
            final long[] tr = wrapped >= Hour.TICKS_PER_INTERVAL ? DAY_FRAME : NIGHT_FRAME;
            final Hour h = Hour.fromTickNumber(wrapped);
            assertArrayEquals("wrapped " + wrapped,
                    legacyBounds(tr, h), newBounds(tr, wrapped));
        }
    }

    @Test public void cockBeforeTheTransitionTakesTheNextInterval() {
        // wrapped 470: hour of the Cock, quarter 1 - its end lies in the
        // upcoming interval
        final long[] b = legacyBounds(DAY_FRAME, Hour.fromTickNumber(470));
        assertEquals(570_000, b[0]);
        assertEquals(620_000, b[1]);
        assertArrayEquals(b, newBounds(DAY_FRAME, 470));
    }

    @Test public void cockAfterTheTransitionTakesThePreviousInterval() {
        // wrapped 10: hour of the Cock, quarter 3 - its start lies in the
        // interval already left behind
        final long[] b = legacyBounds(NIGHT_FRAME, Hour.fromTickNumber(10));
        assertEquals(-30_000, b[0]);
        assertEquals(20_000, b[1]);
        assertArrayEquals(b, newBounds(NIGHT_FRAME, 10));
    }
}
