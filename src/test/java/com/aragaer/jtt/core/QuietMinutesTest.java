// -*- Mode: Java; tab-width: 4; indent-tabs-mode: nil; -*-
// vim: et ts=4 sts=4 sw=4 syntax=java
package com.aragaer.jtt.core;

import static org.junit.Assert.*;

import org.junit.*;


public class QuietMinutesTest {

    @Test public void plainWindowIncludesStartExcludesEnd() {
        assertTrue(ChimeLogic.isQuietMinutes(22 * 60 + 30, 22 * 60 + 30, 23 * 60));
        assertTrue(ChimeLogic.isQuietMinutes(22 * 60 + 59, 22 * 60 + 30, 23 * 60));
        assertFalse(ChimeLogic.isQuietMinutes(23 * 60, 22 * 60 + 30, 23 * 60));
        assertFalse(ChimeLogic.isQuietMinutes(22 * 60 + 29, 22 * 60 + 30, 23 * 60));
    }

    @Test public void midnightWrapWorksToTheMinute() {
        final int from = 23 * 60 + 45, to = 6 * 60 + 15;
        assertTrue(ChimeLogic.isQuietMinutes(23 * 60 + 45, from, to));
        assertTrue(ChimeLogic.isQuietMinutes(0, from, to));
        assertTrue(ChimeLogic.isQuietMinutes(6 * 60 + 14, from, to));
        assertFalse(ChimeLogic.isQuietMinutes(6 * 60 + 15, from, to));
        assertFalse(ChimeLogic.isQuietMinutes(23 * 60 + 44, from, to));
    }

    @Test public void equalBoundsStayAlwaysQuiet() {
        for (int m = 0; m < 24 * 60; m += 97)
            assertTrue(ChimeLogic.isQuietMinutes(m, 7 * 60, 7 * 60));
    }

    @Test public void legacyBareHourParsesAsWholeHour() {
        assertEquals(23 * 60, com.aragaer.jtt.Settings.parseTimePref("23", 0));
        assertEquals(4 * 60 + 30, com.aragaer.jtt.Settings.parseTimePref("04:30", 0));
        assertEquals(7 * 60, com.aragaer.jtt.Settings.parseTimePref("мусор", 7 * 60));
    }
}
