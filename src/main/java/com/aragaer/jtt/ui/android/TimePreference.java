// -*- Mode: Java; tab-width: 4; indent-tabs-mode: nil; -*-
// vim: et ts=4 sts=4 sw=4 syntax=java
package com.aragaer.jtt.ui.android;

import android.content.Context;
import android.content.res.TypedArray;
import android.preference.DialogPreference;
import android.util.AttributeSet;
import android.view.View;
import android.widget.TimePicker;

import java.util.Locale;


/* HH:MM picker for the legacy preference framework; stores "HH:MM". */
public class TimePreference extends DialogPreference {
    private TimePicker picker;
    private String value = "00:00";

    public TimePreference(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override protected View onCreateDialogView() {
        picker = new TimePicker(getContext());
        picker.setIs24HourView(true);
        return picker;
    }

    @Override protected void onBindDialogView(View v) {
        super.onBindDialogView(v);
        final int[] hm = parse(value);
        picker.setCurrentHour(hm[0]);
        picker.setCurrentMinute(hm[1]);
    }

    @Override protected void onDialogClosed(boolean positiveResult) {
        if (!positiveResult)
            return;
        final String v = String.format(Locale.US, "%02d:%02d",
                picker.getCurrentHour(), picker.getCurrentMinute());
        if (callChangeListener(v)) {
            value = v;
            persistString(v);
            setSummary(v);
        }
    }

    @Override protected Object onGetDefaultValue(TypedArray a, int index) {
        return a.getString(index);
    }

    @Override protected void onSetInitialValue(boolean restore, Object defaultValue) {
        value = restore ? getPersistedString("00:00")
                        : String.valueOf(defaultValue);
        if (!value.contains(":")) // legacy bare hour
            value = String.format(Locale.US, "%02d:00", Integer.parseInt(value));
        setSummary(value);
    }

    private static int[] parse(String v) {
        try {
            final String[] p = v.split(":");
            return new int[] {Integer.parseInt(p[0]), Integer.parseInt(p[1])};
        } catch (Exception e) {
            return new int[] {0, 0};
        }
    }
}
