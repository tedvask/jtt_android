// -*- Mode: Java; tab-width: 4; indent-tabs-mode: nil; -*-
// vim: et ts=4 sts=4 sw=4 syntax=java
package com.aragaer.jtt;

import java.util.Calendar;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.widget.FrameLayout;
import android.widget.TextView;

import com.aragaer.jtt.core.ChimeLogic;
import com.aragaer.jtt.core.Hour;
import com.aragaer.jtt.core.ThreeIntervals;
import com.aragaer.jtt.resources.RuntimeResources;
import com.aragaer.jtt.resources.StringResources;

import com.luckycatlabs.sunrisesunset.Zenith;
import com.luckycatlabs.sunrisesunset.calculator.SolarEventCalculator;
import com.luckycatlabs.sunrisesunset.dto.Location;


/* The digital face: toki:koku in the centre, the bell under it, and two
 * twilight ladders above - morning on the left, evening on the right,
 * each ending with the current toki duration.  The Edo line and toki
 * durations come from the tick's own transitions; the other four angles
 * are computed per day from the stored location.  Missing events (high
 * latitudes in summer) show as a dash. */
public class DigitalClockView extends FrameLayout {

    private static final double[] ANGLES = {18, 12, 6, 0.833333}; // astro, naut, civil, official

    private final TextView[] left = new TextView[6], right = new TextView[6];
    private final TextView kokuBig, bellLine, civilSmall;
    private final StringResources sr;
    private String ladderKey = "";

    public DigitalClockView(Context context) {
        super(context);
        inflate(context, R.layout.digital_clock, this);
        sr = RuntimeResources.get(context).getStringResources();
        final int[] lid = {R.id.l0, R.id.l1, R.id.l2, R.id.l3, R.id.l4, R.id.l5};
        final int[] rid = {R.id.r0, R.id.r1, R.id.r2, R.id.r3, R.id.r4, R.id.r5};
        for (int i = 0; i < 6; i++) {
            left[i] = findViewById(lid[i]);
            right[i] = findViewById(rid[i]);
        }
        kokuBig = findViewById(R.id.koku_big);
        bellLine = findViewById(R.id.bell_line);
        civilSmall = findViewById(R.id.civil_small);
    }

    public void update(int wrapped, ThreeIntervals intervals) {
        final Hour h = Hour.fromTickNumber(wrapped);
        kokuBig.setText(Hour.Glyphs[h.num] + "\u2009" + ChimeLogic.clockString(h));
        civilSmall.setText(sr.format_time(System.currentTimeMillis()));

        if (intervals == null)
            return;
        final long[] tr = intervals.getTransitions();
        final long bellTs = ChimeLogic.bellTimestamp(tr, intervals.isDay(), wrapped);
        bellLine.setText(getContext().getString(R.string.notification_bell,
                sr.format_time(bellTs)));

        // day interval: the middle one if we are in daytime, else the next
        final long dayStart = intervals.isDay() ? tr[1] : tr[2];
        final long dayEnd = intervals.isDay() ? tr[2] : tr[3];
        final long nightLen = intervals.isDay() ? tr[3] - tr[2] : tr[2] - tr[1];

        final String key = dayStart + ":" + dayEnd;
        if (!key.equals(ladderKey)) {
            ladderKey = key;
            fillLadders(dayStart, dayEnd, nightLen);
        }
    }

    private void fillLadders(long edoDawn, long edoDusk, long nightLen) {
        final Context c = getContext();
        final String dash = "\u2014";
        final int[] labels = {R.string.lbl_astro, R.string.lbl_naut,
                              R.string.lbl_edo, R.string.lbl_civil};

        Calendar[][] sun = computeSun();
        // morning column: astro, naut, Edo, civil, sunrise, day-toki duration
        for (int i = 0; i < 4; i++) {
            final String t;
            if (i == 2)
                t = sr.format_time(edoDawn);
            else {
                final int a = i < 2 ? i : i - 1; // ANGLES index skips Edo
                t = sun[a][0] == null ? dash : sr.format_time(sun[a][0].getTimeInMillis());
            }
            left[i].setText(c.getString(labels[i]) + " " + t);
        }
        left[4].setText(c.getString(R.string.lbl_sunrise) + " "
                + (sun[3][0] == null ? dash : sr.format_time(sun[3][0].getTimeInMillis())));
        left[5].setText(c.getString(R.string.lbl_day_toki) + " "
                + duration((edoDusk - edoDawn) / Hour.HOURS_PER_INTERVAL));

        // evening column mirrors it
        for (int i = 0; i < 4; i++) {
            final String t;
            if (i == 2)
                t = sr.format_time(edoDusk);
            else {
                final int a = i < 2 ? i : i - 1;
                t = sun[a][1] == null ? dash : sr.format_time(sun[a][1].getTimeInMillis());
            }
            right[i].setText(c.getString(labels[i]) + " " + t);
        }
        right[4].setText(c.getString(R.string.lbl_sunset) + " "
                + (sun[3][1] == null ? dash : sr.format_time(sun[3][1].getTimeInMillis())));
        right[5].setText(c.getString(R.string.lbl_night_toki) + " "
                + duration(nightLen / Hour.HOURS_PER_INTERVAL));
    }

    /* [angle][0=rise, 1=set]; angle order matches ANGLES */
    private Calendar[][] computeSun() {
        final Calendar[][] out = new Calendar[ANGLES.length][2];
        try {
            final float[] loc = getLocation();
            final SolarEventCalculator calc = new SolarEventCalculator(
                    new Location(loc[0], loc[1]),
                    fixedZone((int) TimeUnit.HOURS.toMillis(Math.round(loc[1] / 15))));
            final Calendar noon = Calendar.getInstance();
            for (int i = 0; i < ANGLES.length; i++) {
                final Zenith z = new Zenith(90 + ANGLES[i]);
                out[i][0] = calc.computeSunriseCalendar(z, (Calendar) noon.clone());
                out[i][1] = calc.computeSunsetCalendar(z, (Calendar) noon.clone());
            }
        } catch (Throwable t) {
            android.util.Log.e("jttclock", "ladder computation failed", t);
        }
        return out;
    }

    private float[] getLocation() {
        final SharedPreferences pref =
                PreferenceManager.getDefaultSharedPreferences(getContext());
        final String[] ll = pref.getString(Settings.PREF_LOCATION, "").split(":");
        if (ll.length == 2)
            try {
                return new float[] { Float.parseFloat(ll[0]), Float.parseFloat(ll[1]) };
            } catch (NumberFormatException ignored) {}
        return new float[] { 0, 0 };
    }

    private static TimeZone fixedZone(int offsetMillis) {
        for (String tzName : TimeZone.getAvailableIDs(offsetMillis)) {
            TimeZone tz = TimeZone.getTimeZone(tzName);
            if (!tz.useDaylightTime())
                return tz;
        }
        return TimeZone.getDefault();
    }

    private static String duration(long ms) {
        final long m = ms / 60000;
        return String.format(java.util.Locale.US, "%d:%02d", m / 60, m % 60);
    }
}
