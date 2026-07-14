// -*- Mode: Java; tab-width: 4; indent-tabs-mode: nil; -*-
// vim: et ts=4 sts=4 sw=4 syntax=java
package com.aragaer.jtt;

import java.util.Map;
import java.util.HashMap;

import com.aragaer.jtt.core.ChimeLogic;
import com.aragaer.jtt.core.Hour;
import com.aragaer.jtt.core.ThreeIntervals;
import com.aragaer.jtt.mechanics.AndroidTicker;
import com.aragaer.jtt.resources.RuntimeResources;
import com.aragaer.jtt.resources.StringResources;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.*;
import android.content.res.TypedArray;
import android.graphics.*;
import android.os.Build;
import android.os.Bundle;
import android.widget.RemoteViews;

/* Bar-style widgets: glyph + strike count, toki progress bar with the
 * bell (shoukoku) diamond at the centre, absolute bell time, and - on
 * the wide variant - the next seam (ake-mutsu / kure-mutsu).
 *
 * Data path: the ticker publishes a sticky ACTION_JTT_TICK with "jtt"
 * (wrapped tick) and "intervals" (ThreeIntervals) extras; every redraw
 * pulls the latest sticky synchronously, so no per-widget state or
 * receiver registration is needed.  Bitmaps are sized per widget id
 * from AppWidgetManager options, so launcher grid changes (Android 12+)
 * are honoured instead of fought. */
public class JTTWidgetProvider {
	private static final String PKG_NAME = "com.aragaer.jtt";
	private static final int ACCENT = 0xFFFFC15E; // gold: strikes, bell diamond

	// last tick, pushed by JttStatus with every ACTION_JTT_TICK kick
	private static volatile int sWrapped = -1;
	private static volatile ThreeIntervals sIntervals;

	private static final class WidgetHolder {
		final ComponentName cn;
		final boolean wide;

		WidgetHolder(final Class<? extends JTTWidget> cls, final boolean wide) {
			cn = new ComponentName(PKG_NAME, cls.getName());
			this.wide = wide;
		}
	}

	static private final Map<Class<?>, WidgetHolder> classes = new HashMap<>();

	static void draw_all(final Context c) {
		for (WidgetHolder holder : classes.values())
			draw(c, null, holder);
	}

	private static abstract class JTTWidget extends AppWidgetProvider {
		protected JTTWidget(final boolean wide) {
			final Class<? extends JTTWidget> cls = getClass();
			if (!classes.containsKey(cls))
				classes.put(cls, new WidgetHolder(cls, wide));
		}

		public void onReceive(Context c, Intent i) {
			final String action = i.getAction();
			if (action == null)
				return;
			try {
				if (action.equals(AndroidTicker.ACTION_JTT_TICK)) {
					final int wrapped = i.getIntExtra("jtt", -1);
					if (wrapped >= 0)
						sWrapped = wrapped;
					final ThreeIntervals ti = (ThreeIntervals) i.getSerializableExtra("intervals");
					if (ti != null)
						sIntervals = ti;
					draw(c, null, classes.get(getClass()));
				} else if (action.equals(AppWidgetManager.ACTION_APPWIDGET_UPDATE)
						|| action.equals(AppWidgetManager.ACTION_APPWIDGET_OPTIONS_CHANGED)) {
					startTicker(c);
					draw(c, i.getIntArrayExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS),
							classes.get(getClass()));
				}
			} catch (Throwable t) {
				android.util.Log.e("jtt", "widget onReceive failed", t);
			}
		}

		private void startTicker(Context c) {
			Intent intent = new Intent(c, JttService.class);
			try {
				if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
					c.startForegroundService(intent);
				else
					c.startService(intent);
			} catch (IllegalStateException e) {
				// target 31+: background FGS start may be restricted; the
				// widget still draws, service resumes on next app open/boot.
			}
		}
	}

	private static void draw(Context c, int[] ids, final WidgetHolder holder) {
		final AppWidgetManager awm = AppWidgetManager.getInstance(c.getApplicationContext());
		if (ids == null)
			ids = awm.getAppWidgetIds(holder.cn);
		if (ids.length == 0)
			return;

		final int wrapped = sWrapped;
		final ThreeIntervals intervals = sIntervals;

		final PendingIntent pi = PendingIntent.getActivity(c, 0,
				new Intent(c, JTTMainActivity.class),
				Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0);

		if (wrapped < 0) { // no tick pushed yet - keep the loading face
			final RemoteViews rv = new RemoteViews(PKG_NAME, R.layout.widget_loading);
			rv.setOnClickPendingIntent(R.id.clock, pi);
			for (int id : ids)
				awm.updateAppWidget(id, rv);
			return;
		}

		final float density = c.getResources().getDisplayMetrics().density;

		for (int id : ids) {
			try {
				final Bundle opt = awm.getAppWidgetOptions(id);
				int wdp = opt == null ? 0 : opt.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 0);
				int hdp = opt == null ? 0 : opt.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 0);
				if (wdp <= 0) wdp = holder.wide ? 250 : 120;
				if (hdp <= 0) hdp = 60;
				wdp = Math.max(100, Math.min(wdp, 500));
				hdp = Math.max(44, Math.min(hdp, 200));

				final Bitmap bmp = render(c, wrapped, intervals, holder.wide,
						Math.round(wdp * density), Math.round(hdp * density));
				final RemoteViews rv = new RemoteViews(PKG_NAME, R.layout.widget);
				rv.setImageViewBitmap(R.id.clock, bmp);
				rv.setOnClickPendingIntent(R.id.clock, pi);
				awm.updateAppWidget(id, rv);
				bmp.recycle();
			} catch (Throwable t) {
				android.util.Log.e("jtt", "widget draw failed for id " + id, t);
			}
		}
	}

	private static Bitmap render(final Context c, final int wrapped,
			final ThreeIntervals intervals, final boolean wide,
			final int w, final int h) {
		final Hour hour = Hour.fromTickNumber(wrapped, 1);
		final StringResources sr = RuntimeResources.get(c).getStringResources();

		final int theme = Settings.getWidgetTheme(c);
		final TypedArray ta = c.obtainStyledAttributes(null, R.styleable.Widget, 0, theme);
		final int bgColor = ta.getColor(R.styleable.Widget_widget_background, 0xCC101214);
		final int fgColor = ta.getColor(R.styleable.Widget_text_color, 0xFFE6E8EB);
		final int shadow = ta.getColor(R.styleable.Widget_text_shadow, 0xFF000000);
		ta.recycle();

		final Bitmap bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
		final Canvas cv = new Canvas(bmp);

		final Paint bg = new Paint(Paint.ANTI_ALIAS_FLAG);
		bg.setColor(bgColor);
		final float radius = Math.min(h * 0.22f, 14 * c.getResources().getDisplayMetrics().density);
		cv.drawRoundRect(new RectF(1, 1, w - 1, h - 1), radius, radius, bg);

		final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
		text.setColor(fgColor);
		text.setShadowLayer(3, 0, 0, shadow);

		final float pad = h * 0.16f;
		final float topZoneMid = h * 0.38f;

		// glyph + strike count (left)
		text.setTextAlign(Paint.Align.LEFT);
		text.setTextSize(h * 0.50f);
		text.setFakeBoldText(true);
		final String glyph = Hour.Glyphs[hour.num];
		final float glyphBase = topZoneMid - (text.ascent() + text.descent()) / 2f;
		cv.drawText(glyph, pad, glyphBase, text);
		final float glyphEnd = pad + text.measureText(glyph);

		text.setColor(ACCENT);
		text.setTextSize(h * 0.32f);
		final String strikes = String.valueOf(ChimeLogic.bellsFor(hour.num));
		cv.drawText(strikes, glyphEnd + h * 0.12f,
				topZoneMid - (text.ascent() + text.descent()) / 2f, text);
		text.setFakeBoldText(false);

		// right block: bell time (+ seam on wide)
		if (intervals != null) {
			final long[] tr = intervals.getTransitions();
			final long bellTs = ChimeLogic.bellTimestamp(tr, intervals.isDay(), wrapped);
			final String bell = "\u9418 " + sr.format_time(bellTs); // 鐘
			text.setTextAlign(Paint.Align.RIGHT);
			text.setColor(ACCENT);
			text.setTextSize(h * 0.24f);
			if (wide) {
				cv.drawText(bell, w - pad, h * 0.30f, text);
				// next seam: 明 (ake-mutsu) at night, 暮 (kure-mutsu) by day
				final String seam = (intervals.isDay() ? "\u66AE " : "\u660E ")
						+ sr.format_time(tr[2]);
				text.setColor(fgColor & 0x00FFFFFF | 0xA0000000);
				text.setTextSize(h * 0.21f);
				cv.drawText(seam, w - pad, h * 0.58f, text);
			} else {
				cv.drawText(bell, w - pad,
						topZoneMid - (text.ascent() + text.descent()) / 2f, text);
			}
		}

		// toki progress bar with quarter marks and the shoukoku diamond
		final float barTop = h * 0.72f, barBot = h * 0.82f;
		final float barL = pad, barR = w - pad;
		final Paint bar = new Paint(Paint.ANTI_ALIAS_FLAG);
		bar.setColor(fgColor & 0x00FFFFFF | 0x40000000);
		cv.drawRoundRect(new RectF(barL, barTop, barR, barBot),
				(barBot - barTop) / 2, (barBot - barTop) / 2, bar);

		final float frac = (hour.quarter * Hour.TICKS_PER_QUARTER + hour.tick)
				/ (float) Hour.TICKS_PER_HOUR;
		bar.setColor(fgColor & 0x00FFFFFF | 0xD0000000);
		cv.drawRoundRect(new RectF(barL, barTop, barL + (barR - barL) * frac, barBot),
				(barBot - barTop) / 2, (barBot - barTop) / 2, bar);

		bar.setStrokeWidth(Math.max(1.5f, h * 0.015f));
		bar.setColor(fgColor & 0x00FFFFFF | 0x70000000);
		for (float q = 0.25f; q < 0.9f; q += 0.25f) {
			final float x = barL + (barR - barL) * q;
			cv.drawLine(x, barTop - h * 0.03f, x, barBot + h * 0.03f, bar);
		}

		final float cx = (barL + barR) / 2f, cy = barTop - h * 0.09f, d = h * 0.06f;
		final Path diamond = new Path();
		diamond.moveTo(cx, cy - d);
		diamond.lineTo(cx + d, cy);
		diamond.lineTo(cx, cy + d);
		diamond.lineTo(cx - d, cy);
		diamond.close();
		final Paint gold = new Paint(Paint.ANTI_ALIAS_FLAG);
		gold.setColor(ACCENT);
		cv.drawPath(diamond, gold);

		return bmp;
	}

	/* Compact bar widget (2x1) */
	public static class Widget1 extends JTTWidget {
		public Widget1() {
			super(false);
		}
	}

	/* Wide bar widget (4x1) with the next seam */
	public static class Widget12 extends JTTWidget {
		public Widget12() {
			super(true);
		}
	}
}
