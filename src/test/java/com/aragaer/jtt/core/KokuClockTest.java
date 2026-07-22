// -*- Mode: Java; tab-width: 4; indent-tabs-mode: nil; -*-
// vim: et ts=4 sts=4 sw=4 syntax=java
package com.aragaer.jtt.core;

import static org.junit.Assert.*;

import org.junit.*;


public class KokuClockTest {

    // asymmetric on purpose: night 200s, day 300s, next night 180s
    private static final long[] TR = {0, 200_000, 500_000, 680_000};

    @Test public void kokuIsZeroAtHourStart() {
        assertEquals(0, ChimeLogic.kokuOfHour(20));
    }

    @Test public void kokuIsTwentyExactlyAtTheBell() {
        // wrapped 0 is the centre of the hour of the Cock (dusk bell)
        assertEquals(20, ChimeLogic.kokuOfHour(0));
    }

    @Test public void kokuIsThirtyNineAtTheLastTick() {
        assertEquals(39, ChimeLogic.kokuOfHour(59));
    }

    @Test public void kokuWrapsAroundTheDay() {
        assertEquals(0, ChimeLogic.kokuOfHour(460));
    }

    @Test public void clockStringAtMonkeyBell() {
        // Monkey centre: 11 * 40 = 440; seven strikes, koku 20
        assertEquals("7:20", ChimeLogic.clockString(Hour.fromTickNumber(440)));
    }

    @Test public void clockStringPadsKoku() {
        // three ticks after the start of the Horse: 9 strikes, koku 03
        assertEquals("9:03", ChimeLogic.clockString(Hour.fromTickNumber(343)));
    }

    @Test public void timeOfTickMapsAllThreeIntervals() {
        assertEquals(200_000, ChimeLogic.timeOfTick(TR, 0));
        assertEquals(350_000, ChimeLogic.timeOfTick(TR, 120));
        assertEquals(500_000, ChimeLogic.timeOfTick(TR, 240));
        assertEquals(180_000, ChimeLogic.timeOfTick(TR, -24));
        assertEquals(545_000, ChimeLogic.timeOfTick(TR, 300));
    }

    @Test public void bellTimestampAgreesWithTimeOfTickForEveryTickOfTheDay() {
        for (int wrapped = 0; wrapped < Hour.TICKS_PER_DAY; wrapped++) {
            final boolean isDay = wrapped >= Hour.TICKS_PER_INTERVAL;
            final int m = wrapped - (isDay ? Hour.TICKS_PER_INTERVAL : 0);
            final int half = Hour.TICKS_PER_HOUR / 2;
            final int centre = ((m + half) / Hour.TICKS_PER_HOUR) * Hour.TICKS_PER_HOUR;
            assertEquals("wrapped " + wrapped,
                    ChimeLogic.bellTimestamp(TR, isDay, wrapped),
                    ChimeLogic.timeOfTick(TR, centre));
        }
    }

    @Test public void fourHourRowsAreContiguous() {
        // day time, hour of the Monkey approaching: rows must chain
        final int wrapped = 437, half = Hour.TICKS_PER_HOUR / 2;
        final int m = wrapped - Hour.TICKS_PER_INTERVAL;
        final int c0 = ((m + half) / Hour.TICKS_PER_HOUR) * Hour.TICKS_PER_HOUR;
        for (int k = 0; k < 3; k++) {
            final long endK = ChimeLogic.timeOfTick(TR, c0 + k * Hour.TICKS_PER_HOUR + half);
            final long startNext = ChimeLogic.timeOfTick(TR, c0 + (k + 1) * Hour.TICKS_PER_HOUR - half);
            assertEquals(endK, startNext);
        }
    }
}
