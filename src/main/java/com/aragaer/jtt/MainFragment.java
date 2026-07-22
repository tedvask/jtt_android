// -*- Mode: Java; tab-width: 4; indent-tabs-mode: nil; -*-
// vim: et ts=4 sts=4 sw=4 syntax=java
package com.aragaer.jtt;

import android.app.ActionBar;
import android.app.Fragment;
import android.content.*;
import android.os.Bundle;
import androidx.viewpager.widget.ViewPager;
import android.util.Log;
import android.view.*;
import android.widget.ListView;

import com.aragaer.jtt.core.ThreeIntervals;
import com.aragaer.jtt.mechanics.AndroidAnnouncer;
import com.aragaer.jtt.mechanics.AndroidTicker;
import com.aragaer.jtt.resources.RuntimeResources;
import com.aragaer.jtt.resources.StringResources;
import com.aragaer.jtt.today.TodayAdapter;


public class MainFragment extends Fragment {
    private DigitalClockView clock;
    private TodayAdapter today;
    private ViewPager pager;
    private int tickNumber;
    private int page;
    private ThreeIntervals intervals;

    private final BroadcastReceiver receiver = new BroadcastReceiver() {
            @Override public void onReceive(Context context, Intent intent) {
                if (!AndroidTicker.ACTION_JTT_TICK.equals(intent.getAction()))
                    return;
                tickNumber = intent.getIntExtra("jtt", 0);

                intervals = (ThreeIntervals) intent.getSerializableExtra("intervals");
                clock.update(tickNumber, intervals);
                if (intervals == null) {
                    Log.w("JTT", "Got null intervals object");
                    return;
                }
                today.tick(intervals);
            }
        };

    @Override public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        StringResources.setLocaleToContext(getActivity());
        pager = new ViewPager(getActivity());
        final ViewPagerAdapter pager_adapter = new ViewPagerAdapter(getActivity(), pager);
        clock = new DigitalClockView(getActivity());
        if (savedInstanceState != null) {
            tickNumber = savedInstanceState.getInt("tickNumber", 0);
            page = savedInstanceState.getInt("page", 0);
        }
        clock.update(tickNumber, intervals);

        final ListView today_list = new ListView(getActivity());
        today = new TodayAdapter(getActivity(), 0, RuntimeResources.get(getActivity()).getStringResources());
        today_list.setAdapter(today);
        if (intervals != null)
            today.tick(intervals);

        pager_adapter.addView(clock, R.string.clock);
        pager_adapter.addView(today_list, R.string.today);

        pager.setAdapter(pager_adapter);
        pager.setCurrentItem(page, false);
        if (android.os.Build.VERSION.SDK_INT >= 33)
            getActivity().registerReceiver(receiver, new IntentFilter(AndroidTicker.ACTION_JTT_TICK),
                    Context.RECEIVER_NOT_EXPORTED);
        else
            getActivity().registerReceiver(receiver, new IntentFilter(AndroidTicker.ACTION_JTT_TICK));

        android.content.Intent last = AndroidAnnouncer.getLastTick();
        if (last != null)
            receiver.onReceive(getActivity(), last);
        // widget-launched windows sometimes come up without insets
        // applied (content slides under the status/action bars); detect
        // the broken geometry once and recreate - same cure as a manual
        // theme switch, but automatic and only when actually broken
        if (android.os.Build.VERSION.SDK_INT >= 23)
            pager.post(new Runnable() {
                @Override public void run() {
                    final android.app.Activity a = getActivity();
                    if (a == null || a.getIntent().getBooleanExtra("jtt_relaid", false))
                        return;
                    final android.view.WindowInsets wi = pager.getRootWindowInsets();
                    if (wi == null)
                        return;
                    final android.util.TypedValue tv = new android.util.TypedValue();
                    int ab = 0;
                    if (a.getTheme().resolveAttribute(android.R.attr.actionBarSize, tv, true))
                        ab = android.util.TypedValue.complexToDimensionPixelSize(
                                tv.data, getResources().getDisplayMetrics());
                    final int expectedTop = wi.getSystemWindowInsetTop() + ab;
                    final int[] loc = new int[2];
                    pager.getLocationOnScreen(loc);
                    if (loc[1] < expectedTop - 8) {
                        a.getIntent().putExtra("jtt_relaid", true);
                        a.recreate();
                    }
                }
            });

        return pager;
    }

    @Override public void onStart() {
        ActionBar actionBar = getActivity().getActionBar();
        if (actionBar != null)
            actionBar.setDisplayOptions(ActionBar.DISPLAY_SHOW_CUSTOM);
        super.onStart();
    }

    @Override public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt("tickNumber", tickNumber);
        outState.putInt("page", page);
    }

    @Override public void onDestroyView() {
        getActivity().unregisterReceiver(receiver);
        page = pager.getCurrentItem();
        super.onDestroyView();
    }
}
