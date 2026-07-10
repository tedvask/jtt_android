// -*- Mode: Java; tab-width: 4; indent-tabs-mode: nil; -*-
// vim: et ts=4 sts=4 sw=4 syntax=java
package com.aragaer.jtt.astronomy;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

import com.aragaer.jtt.Settings;


public class AndroidDayBoundaryHandler implements DayBoundaryHandler {

    /* Official sunrise/sunset: sun 50' below the horizon. */
    private static final double OFFICIAL = 0.8333;
    /* Civil twilight: sun 6 degrees below the horizon. */
    private static final double CIVIL = 6;
    /* Edo-period convention for ake-mutsu/kure-mutsu:
     * sun 7 degrees 21' 40" below the horizon. */
    private static final double EDO = 7.3611;

    private static final double[] DEPRESSIONS = {OFFICIAL, CIVIL, EDO};

    private final SharedPreferences _pref;

    /* package private */ AndroidDayBoundaryHandler(Context context) {
        _pref = PreferenceManager.getDefaultSharedPreferences(context);
    }

    public double getDepression() {
        String boundary = _pref.getString(Settings.PREF_BOUNDARY, "0");
        try {
            return DEPRESSIONS[Integer.parseInt(boundary)];
        } catch (NumberFormatException | ArrayIndexOutOfBoundsException e) {
            return OFFICIAL;
        }
    }
}
