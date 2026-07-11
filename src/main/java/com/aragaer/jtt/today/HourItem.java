// -*- Mode: Java; tab-width: 4; indent-tabs-mode: nil; -*-
// vim: et ts=4 sts=4 sw=4 syntax=java
package com.aragaer.jtt.today;

import java.util.ArrayList;
import java.util.List;

import com.aragaer.jtt.R;
import com.aragaer.jtt.core.ChimeLogic;
import com.aragaer.jtt.core.Hour;
import com.aragaer.jtt.resources.RuntimeResources;
import com.aragaer.jtt.resources.StringResources;

import android.content.Context;
import android.widget.TextView;
import android.view.View;

/* One self-contained hour row of the Today list.
 * time (from TodayItem) is the hour's centre - the bell moment. */
class HourItem extends TodayItem {
    /* package private */ final int hnum;
    /* package private */ final long start, end;

    /* package private */ HourItem(long start, long bell, long end, int h) {
        super(bell);
        this.start = start;
        this.end = end;
        hnum = h % 12;
    }

    static String[] extras;

    /* The meta line is an ordered list of facts about the hour,
     * joined with a separator.  Extending the row (alarms, watches,
     * anything) means appending one more segment here. */
    private List<String> metaSegments(Context c, StringResources sr) {
        List<String> segments = new ArrayList<>();
        segments.add(sr.format_time(start) + "\u2013" + sr.format_time(end));
        segments.add(c.getString(R.string.bell_short) + " " + sr.format_time(time));
        return segments;
    }

    @Override
    public View toView(Context c, View v, int sel_p_diff) {
        if (v == null)
            v = View.inflate(c, R.layout.today_item, null);
        final StringResources sr = RuntimeResources.get(c).getStringResources();

        ((TextView) v.findViewById(R.id.curr)).setText(sel_p_diff == 0 ? "\u25b6" : "");
        ((TextView) v.findViewById(R.id.glyph)).setText(Hour.Glyphs[hnum]);
        ((TextView) v.findViewById(R.id.count)).setText(String.valueOf(ChimeLogic.bellsFor(hnum)));
        ((TextView) v.findViewById(R.id.name)).setText(sr.getHrOf(hnum));

        StringBuilder meta = new StringBuilder();
        for (String segment : metaSegments(c, sr)) {
            if (meta.length() > 0)
                meta.append(" \u00b7 ");
            meta.append(segment);
        }
        ((TextView) v.findViewById(R.id.meta)).setText(meta.toString());

        ((TextView) v.findViewById(R.id.extra)).setText(extras[hnum]);
        return v;
    }
}
