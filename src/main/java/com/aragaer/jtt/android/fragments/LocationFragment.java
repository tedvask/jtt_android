// -*- Mode: Java; tab-width: 4; indent-tabs-mode: nil; -*-
// vim: et ts=4 sts=4 sw=4 syntax=java
package com.aragaer.jtt.android.fragments;

import com.aragaer.jtt.LocationPreference;
import com.aragaer.jtt.R;
import com.aragaer.jtt.Settings;
import com.aragaer.jtt.android.dialogs.WaitingForLocationDialog;
import com.aragaer.jtt.resources.StringResources;

import android.Manifest;
import android.app.ActionBar;
import android.content.*;
import android.content.pm.PackageManager;
import android.location.*;
import android.os.Build;
import android.os.Bundle;
import android.preference.*;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.widget.Toast;

import org.jetbrains.annotations.NotNull;

import java.util.concurrent.TimeUnit;


public class LocationFragment extends PreferenceFragment implements Preference.OnPreferenceClickListener {
    private Preference.OnPreferenceChangeListener _listener;

    public void setChangeListener(Preference.OnPreferenceChangeListener listener) {
        _listener = listener;
    }

    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        StringResources.setLocaleToContext(getActivity());
        addPreferencesFromResource(R.xml.location);
        setHasOptionsMenu(true);
        if (_listener != null)
            findPreference(Settings.PREF_LOCATION).setOnPreferenceChangeListener(_listener);
    }

    @Override public void onStart() {
        super.onStart();
        SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(getActivity());
        if (!settings.contains(Settings.PREF_LOCATION))
            Toast.makeText(getActivity(), "Please set location", Toast.LENGTH_LONG).show();
        Preference auto_button = findPreference("jtt_auto");
        auto_button.setOnPreferenceClickListener(this);
        ActionBar actionBar = getActivity().getActionBar();
        if (actionBar != null) {
            actionBar.setTitle(R.string.location);
            actionBar.setDisplayOptions(ActionBar.DISPLAY_SHOW_TITLE);
        }
    }

    @Override public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        super.onCreateOptionsMenu(menu, inflater);
        menu.clear();
    }

    /* For computing sunrise/sunset, a city-level fix from the last
     * quarter of an hour is more than precise enough. */
    private static final long FRESH_FIX_MS = TimeUnit.MINUTES.toMillis(15);

    private static final String[] PROVIDERS = {
        LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER,
    };

    private boolean canUseLocation() {
        Log.d("JTT LOCATION", "Checking location service access");

        LocationManager lm = (LocationManager) getActivity().getSystemService(Context.LOCATION_SERVICE);

        if (lm == null)
            return false;

        boolean anyProvider = false;
        for (String provider : PROVIDERS)
            anyProvider |= lm.isProviderEnabled(provider);

        if (!anyProvider) {
            Log.d("JTT LOCATION", "No provider found");
            Toast.makeText(getActivity(), R.string.no_providers, Toast.LENGTH_SHORT).show();
            getActivity().startActivity(new Intent(android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS));
            return false;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            boolean fine = getActivity().checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
            boolean coarse = getActivity().checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
            if (!fine && !coarse) {
                Log.d("JTT LOCATION", "Requesting location permissions");
                // Both are requested so that "approximate only" on
                // Android 12+ still counts as success.
                requestPermissions(new String[]{
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION}, 0);
                return false;
            }
            Log.d("JTT LOCATION", "Permission granted (fine=" + fine + ")");
        }
        return true;
    }

    private void getLocation() {
        LocationManager lm = (LocationManager) getActivity().getSystemService(Context.LOCATION_SERVICE);
        if (lm == null)
            return;

        LocationPreference pref = (LocationPreference) findPreference(Settings.PREF_LOCATION);

        // A recent last-known fix resolves instantly, without the dialog.
        Location best = null;
        for (String provider : new String[] {LocationManager.NETWORK_PROVIDER,
                                             LocationManager.GPS_PROVIDER, "fused"})
            try {
                Location last = lm.getLastKnownLocation(provider);
                if (last != null && (best == null || last.getTime() > best.getTime()))
                    best = last;
            } catch (SecurityException | IllegalArgumentException e) {
                // provider missing or not permitted - skip it
            }
        if (best != null && best.getTime() > System.currentTimeMillis() - FRESH_FIX_MS) {
            Log.d("JTT LOCATION", "Got a recent last known location");
            pref.setNewLocation(best);
            return;
        }

        // Otherwise listen on every enabled provider we validated above -
        // never on getBestProvider's pick, which may be gps indoors or
        // the passive provider that fires for nobody.
        WaitingForLocationDialog dialog = new WaitingForLocationDialog(getActivity());
        dialog.setPreference(pref);
        dialog.show();
        boolean requested = false;
        for (String provider : PROVIDERS)
            if (lm.isProviderEnabled(provider))
                try {
                    Log.d("JTT LOCATION", "Requesting updates from " + provider);
                    lm.requestLocationUpdates(provider, 0, 0, dialog);
                    requested = true;
                } catch (SecurityException e) {
                    Log.d("JTT LOCATION", "Not permitted: " + provider);
                }
        if (!requested) {
            dialog.dismiss();
            Toast.makeText(getActivity(), R.string.location_denied, Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NotNull String[] permissions, @NotNull int[] grantResults) {
        for (int result : grantResults)
            if (result == PackageManager.PERMISSION_GRANTED) {
                getLocation();
                return;
            }
        Toast.makeText(getActivity(), R.string.location_denied, Toast.LENGTH_SHORT).show();
    }

    @Override
    public boolean onPreferenceClick(Preference preference) {
        if (preference.getKey().equals("jtt_auto")) {
            if (canUseLocation())
                getLocation();
            return true;
        }
        return false;
    }
}
