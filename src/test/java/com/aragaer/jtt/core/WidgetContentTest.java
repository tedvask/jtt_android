// -*- Mode: Java; tab-width: 4; indent-tabs-mode: nil; -*-
// vim: et ts=4 sts=4 sw=4 syntax=java
package com.aragaer.jtt.core;

import static org.junit.Assert.*;

import org.junit.*;


public class WidgetContentTest {

    private static final long[] TR = {0, 240_000, 600_000, 840_000};
    private static final ThreeIntervals NIGHT = new ThreeIntervals(TR, false);
    private static final ThreeIntervals DAY = new ThreeIntervals(TR, true);

    @Test public void mainReadingIsGlyphAndClockString() {
        // wrapped 120: centre of the Rat, nine strikes, koku 20
        assertEquals("\u5B50\u20099:20", new WidgetContent(120, NIGHT).main);
    }

    @Test public void bellMatchesChimeLogic() {
        assertEquals(ChimeLogic.bellTimestamp(TR, false, 130),
                new WidgetContent(130, NIGHT).bell);
    }

    @Test public void hourBoundsSurroundTheBell() {
        final WidgetContent w = new WidgetContent(130, NIGHT);
        assertTrue(w.hourStart < w.bell && w.bell < w.hourEnd);
        assertEquals(60_000, w.hourEnd - w.hourStart); // one toki of the 360s middle interval
    }

    @Test public void fractionIsZeroAtHourStartAndGrows() {
        assertEquals(0f, new WidgetContent(20, NIGHT).fraction, 1e-6f);
        assertEquals(0.5f, new WidgetContent(40, NIGHT).fraction, 1e-6f);
        assertEquals(39 / 40f, new WidgetContent(59, NIGHT).fraction, 1e-6f);
    }

    @Test public void nightPromisesDawnAndDayPromisesDusk() {
        assertFalse(new WidgetContent(120, NIGHT).seamIsDusk);
        assertTrue(new WidgetContent(360, DAY).seamIsDusk);
        assertEquals(TR[2], new WidgetContent(120, NIGHT).seam);
    }

    @Test public void nullIntervalsStillGiveTheMainReading() {
        final WidgetContent w = new WidgetContent(120, null);
        assertEquals("\u5B50\u20099:20", w.main);
        assertEquals(0, w.bell);
    }
}
