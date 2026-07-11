// -*- Mode: Java; tab-width: 4; indent-tabs-mode: nil; -*-
// vim: et ts=4 sts=4 sw=4 syntax=java
package com.aragaer.jtt.today;

import java.util.ArrayList;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.modules.junit4.PowerMockRunner;

import com.aragaer.jtt.core.ThreeIntervals;
import com.aragaer.jtt.resources.StringResources;

import static org.junit.Assert.*;
import static org.mockito.Matchers.*;
import static org.mockito.Mockito.verify;
import static org.powermock.api.mockito.PowerMockito.*;


@RunWith(PowerMockRunner.class)
public class TodayAdapterTest {

    private static final int FULL_HOURS = 17;

    private static final Context mockContext = mock(Context.class);
    private static final StringResources mockSR = mock(StringResources.class);

    private FakeTodayAdapter adapter;
    private ArrayList<TodayItem> initial;

    @Before public void setUp() {
        adapter = new FakeTodayAdapter();

        initial = new ArrayList<>();
        initial.add(new HourItem(System.currentTimeMillis()-2020,
                                 System.currentTimeMillis()-2000,
                                 System.currentTimeMillis()-1980, 0));
        adapter.items.addAll(initial);
    }

    /* Intervals of 240 units each: hour = 40 units, so each row is
     * (start = centre-20, bell = centre, end = centre+20). */
    @Test public void testTickWithValidIntervals() {
        long now = System.currentTimeMillis();
        ThreeIntervals intervals = new ThreeIntervals(new long[]{now-360, now-120, now+120, now+360}, true);

        adapter.tick(intervals);

        assertEquals("initial list cleared", FULL_HOURS, adapter.getCount());
        for (int i = 0; i < FULL_HOURS; i++) {
            HourItem hour = (HourItem) adapter.getItem(i);
            long centre = now-360 + 40*(i+1);
            assertEquals(centre-20, hour.start);
            assertEquals(centre, hour.time);
            assertEquals(centre+20, hour.end);
            assertEquals((i+1)%12, hour.hnum);
        }

        assertTrue(adapter.datasetChanged);
    }

    @Test public void testTickWithValidIntervalsAtNight() {
        long now = System.currentTimeMillis();
        ThreeIntervals intervals = new ThreeIntervals(new long[]{now-360, now-120, now+120, now+360}, false);

        adapter.tick(intervals);

        assertEquals("initial list cleared", FULL_HOURS, adapter.getCount());
        for (int i = 0; i < FULL_HOURS; i++) {
            HourItem hour = (HourItem) adapter.getItem(i);
            assertEquals((i+7)%12, hour.hnum);
        }

        assertTrue(adapter.datasetChanged);
    }

    @Test public void testSelectsTheHourContainingNow() {
        long now = System.currentTimeMillis();
        ThreeIntervals intervals = new ThreeIntervals(new long[]{now-360, now-120, now+120, now+360}, true);

        adapter.tick(intervals);

        HourItem current = (HourItem) adapter.getItem(adapter.lastSelected());
        assertTrue(current.start <= now);
        assertTrue(current.end > now);
    }

    @Test public void testTickWithStaleIntervals() {
        long now = System.currentTimeMillis();
        ThreeIntervals intervals = new ThreeIntervals(new long[]{now-1000, now-800, now-600, now-400}, true);

        adapter.tick(intervals);

        assertEquals("should not modify initial list", initial, adapter.items);
        assertFalse(adapter.datasetChanged);
    }

    @Test public void testNotRebuildIfIntervalsIsTheSame() {
        long now = System.currentTimeMillis();
        ThreeIntervals intervals = new ThreeIntervals(new long[]{now-360, now-120, now+120, now+360}, false);
        adapter.tick(intervals);
        ArrayList<TodayItem> saved = new ArrayList<>(adapter.items);
        adapter.datasetChanged = false;
        adapter.clearCalled = false;

        adapter.tick(intervals);

        assertFalse("should not clear the list", adapter.clearCalled);
        assertEquals("Still has the same items", saved, adapter.items);
        assertTrue("should update selected item", adapter.datasetChanged);
    }

    @Test public void testNotRebuildIfInRebuild() {
        long now = System.currentTimeMillis();
        ThreeIntervals intervals = new ThreeIntervals(new long[]{now-360, now-120, now+120, now+360}, false);
        adapter.tick(intervals);
        adapter.datasetChanged = false;
        adapter.clearCalled = false;
        adapter.items.clear();

        adapter.tick(intervals);

        assertFalse("should not clear the list", adapter.clearCalled);
        assertEquals("should not add more items to the list", 0, adapter.getCount());
        assertFalse("should not update selected item", adapter.datasetChanged);
    }

    @Test public void testStringChanges() {
        verify(mockSR)
            .registerStringResourceChangeListener(adapter,
                                                  StringResources.TYPE_HOUR_NAME | StringResources.TYPE_TIME_FORMAT);

        adapter.onStringResourcesChanged(0);

        assertTrue("should rebuild list", adapter.datasetChanged);
    }

    @Test public void testInitialize() {
        assertEquals(1, adapter.getViewTypeCount());
        for (int i = 0; i < FULL_HOURS; i++) {
            assertFalse(adapter.isEnabled(i));
            assertEquals(0, adapter.getItemViewType(i));
        }
    }

    @Test public void testViews() {
        long now = System.currentTimeMillis();
        ThreeIntervals intervals = new ThreeIntervals(new long[]{now-360, now-120, now+120, now+360}, false);
        adapter.tick(intervals);

        int selected = adapter.lastSelected();

        View mockView = mock(View.class);
        ViewGroup mockVG = mock(ViewGroup.class);
        when(mockVG.getContext()).thenReturn(mockContext);
        for (int i = 0; i < FULL_HOURS; i++) {
            TodayItem spyTI = spy(adapter.items.get(i));
            doReturn(mockView).when(spyTI).toView(any(Context.class), any(View.class), anyInt());
            adapter.items.set(i, spyTI);
            adapter.getView(i, mockView, mockVG);
            verify(spyTI).toView(mockContext, mockView, selected - i);
        }
    }

    private static class FakeTodayAdapter extends TodayAdapter {
        final ArrayList<TodayItem> items = new ArrayList<>();
        boolean datasetChanged;
        boolean clearCalled;

        FakeTodayAdapter() {
            super(mockContext, 0, mockSR);
        }

        int lastSelected() {
            // recompute the way getView sees it: probe via getView contract
            // selected is private; expose through toView diff on item 0
            return _probeSelected();
        }

        private int _probeSelected() {
            for (int i = 0; i < items.size(); i++) {
                HourItem h = (HourItem) items.get(i);
                long now = System.currentTimeMillis();
                if (h.start <= now && h.end > now)
                    return i;
            }
            return items.size() - 1;
        }

        @Override public void clear() {
            items.clear();
            clearCalled = true;
        }

        @Override public void add(TodayItem item) {
            items.add(item);
        }

        @Override public int getCount() {
            return items.size();
        }

        @Override public TodayItem getItem(int position) {
            return items.get(position);
        }

        @Override public void notifyDataSetChanged() {
            datasetChanged = true;
        }
    }
}
