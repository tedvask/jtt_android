// -*- Mode: Java; tab-width: 4; indent-tabs-mode: nil; -*-
// vim: et ts=4 sts=4 sw=4 syntax=java
package com.aragaer.jtt.core;

import static org.junit.Assert.*;

import org.junit.*;


public class QuietHoursEdgeTest {

    @Test public void equalBoundsMeanAlwaysQuiet() {
        // the documented degenerate case: from == to honours the user's
        // explicit equal bounds as a full-day quiet window
        for (int hour = 0; hour < 24; hour++)
            assertTrue(ChimeLogic.isQuiet(hour, 7, 7));
    }
}
