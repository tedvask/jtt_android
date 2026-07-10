// -*- Mode: Java; tab-width: 4; indent-tabs-mode: nil; -*-
// vim: et ts=4 sts=4 sw=4 syntax=java
package com.aragaer.jtt.core;

import static org.junit.Assert.*;

import org.junit.*;


public class ChimeLogicTest {

    @Test public void firstDeliveryIsSilent() {
        assertEquals(0, ChimeLogic.strikesFor(-1, 120, ChimeLogic.AT_CENTRE));
    }

    @Test public void duplicateTickIsSilent() {
        assertEquals(0, ChimeLogic.strikesFor(120, 120, ChimeLogic.AT_CENTRE));
    }

    @Test public void nineStrikesAtRatCentre() {
        // wrapped 120 is the centre of the hour of the Rat (midnight bell)
        assertEquals(9, ChimeLogic.strikesFor(119, 120, ChimeLogic.AT_CENTRE));
    }

    @Test public void centreModeIsSilentAtHourStart() {
        assertEquals(0, ChimeLogic.strikesFor(99, 100, ChimeLogic.AT_CENTRE));
    }

    @Test public void startModeStrikesAtHourStart() {
        // wrapped 100 is the start of the hour of the Rat
        assertEquals(9, ChimeLogic.strikesFor(99, 100, ChimeLogic.AT_START));
    }

    @Test public void bothModeChimesTwicePerHour() {
        int prev = -1, chimes = 0;
        for (int w = 0; w < Hour.TICKS_PER_DAY; w++) {
            if (ChimeLogic.strikesFor(prev, w, ChimeLogic.AT_BOTH) > 0)
                chimes++;
            prev = w;
        }
        // 12 starts + 12 centres, minus the suppressed first delivery
        assertEquals(23, chimes);
    }

    @Test public void quietWindowWrapsMidnight() {
        assertTrue(ChimeLogic.isQuiet(23, 23, 7));
        assertTrue(ChimeLogic.isQuiet(3, 23, 7));
        assertFalse(ChimeLogic.isQuiet(7, 23, 7));
        assertFalse(ChimeLogic.isQuiet(12, 23, 7));
    }

    @Test public void quietWindowWithinOneDay() {
        assertTrue(ChimeLogic.isQuiet(12, 8, 18));
        assertFalse(ChimeLogic.isQuiet(19, 8, 18));
        assertFalse(ChimeLogic.isQuiet(7, 8, 18));
    }
}
