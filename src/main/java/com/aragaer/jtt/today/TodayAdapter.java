// -*- Mode: Java; tab-width: 4; indent-tabs-mode: nil; -*-
// vim: et ts=4 sts=4 sw=4 syntax=java
package com.aragaer.jtt.today;

import com.aragaer.jtt.R;
import com.aragaer.jtt.Settings;
import com.aragaer.jtt.core.Hour;
import com.aragaer.jtt.core.ThreeIntervals;
import com.aragaer.jtt.resources.StringResources;

import android.content.Context;
import android.preference.PreferenceManager;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;

import org.jetbrains.annotations.NotNull;

import java.lang.System;

public class TodayAdapter extends ArrayAdapter<TodayItem> implements
                                                              StringResources.StringResourceChangeListener {
    /* Centres per the three intervals: 19; full hours between them: 17. */
    private static final int CENTRES = Hour.HOURS_PER_INTERVAL * 3 + 1;
    private static final int FULL_HOURS = CENTRES - 2;

    private ThreeIntervals _intervals;
    private int selected;

    public TodayAdapter(Context c, int layout_id, StringResources sr) {
        super(c, layout_id);
        sr.registerStringResourceChangeListener(this,
                                                StringResources.TYPE_HOUR_NAME | StringResources.TYPE_TIME_FORMAT);
        String mark;
        try {
            mark = boundaryMark(PreferenceManager.getDefaultSharedPreferences(c)
                                    .getString(Settings.PREF_BOUNDARY, "0"));
        } catch (RuntimeException e) {
            // Android statics are unavailable in JVM unit tests
            mark = "";
        }
        HourItem.extras = new String[] { c.getString(R.string.sunset) + mark, "", "",
                                         c.getString(R.string.midnight), "", "",
                                         c.getString(R.string.sunrise) + mark, "", "",
                                         c.getString(R.string.noon), "", "" };
    }

    /* package private */ static String boundaryMark(String boundaryMode) {
        switch (boundaryMode) {
        case "1":
            return " (6\u00b0)";
        case "2":
            return " (7\u00b022\u2032)";
        default:
            return "";
        }
    }

    @Override
    public @NotNull View getView(int position, View v, @NotNull ViewGroup parent) {
        TodayItem item = getItem(position);
        if (item == null)
            return v;
        return item.toView(parent.getContext(), v, selected - position);
    }

    /* Walks the three intervals and emits one self-contained row per
     * fully-defined hour: its start boundary, bell (centre), and end
     * boundary.  The two edge hours around transitions[0]/[3] lack one
     * boundary and are dropped; "now" always lies in the middle
     * interval, far from the edges. */
    private synchronized void buildItems() {
        final long[] transitions = _intervals.getTransitions();
        clear();

        final int hours = Hour.HOURS_PER_INTERVAL;
        long[] centres = new long[CENTRES];
        long[] bounds = new long[CENTRES - 1]; // bounds[k] precedes centres[k+1]
        int k = 0;
        centres[k++] = transitions[0];
        for (int i = 1; i < transitions.length; i++) {
            final long start = transitions[i - 1];
            final long diff = transitions[i] - start;
            for (int j = 1; j <= hours; j++) {
                bounds[k - 1] = start + (j * 2 - 1) * diff / hours / 2;
                centres[k++] = start + j * diff / hours;
            }
        }

        int h_add = _intervals.isDay() ? 0 : hours;
        for (int c = 1; c <= FULL_HOURS; c++)
            add(new HourItem(bounds[c - 1], centres[c], bounds[c], h_add + c));
    }

    public void tick(ThreeIntervals intervals) {
        long now = System.currentTimeMillis();
        if (!intervals.surrounds(now))
            return;

        if (!intervals.equals(_intervals)) {
            _intervals = intervals;
            buildItems();
        }

        // check that items are built
        if (getCount() < FULL_HOURS)
            // transitions are set but items aren't built
            // this means we're currently in the build process
            return;

        for (selected = 0; selected < getCount() - 1; selected++) {
            HourItem item = (HourItem) getItem(selected);
            if (item == null || item.end > now)
                break;
        }

        notifyDataSetChanged();
    }

    @Override
    public boolean isEnabled(int pos) {
        return false;
    }

    public void onStringResourcesChanged(int changes) {
        notifyDataSetChanged();
    }

    @Override
    public int getViewTypeCount() {
        return 1;
    }

    @Override
    public int getItemViewType(int position) {
        return 0;
    }
}
