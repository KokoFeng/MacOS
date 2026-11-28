package com.macos.launcher;

import android.content.Context;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.os.BatteryManager;
import android.content.Intent;
import android.content.IntentFilter;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.TimeZone;
import org.json.JSONArray;
import org.json.JSONObject;

/** Native macOS-style desktop widgets (Calendar, Clock, Weather, Reminders, Notes, Batteries) + the "Edit Widgets" gallery. */
public class MacWidgets {
    // type, group, label, width dp, height dp, icon drawable
    static final String[][] CATALOG = {
        {"cal_month", "Calendar", "Month", "150", "150", "mac_calendar"},
        {"cal_day", "Calendar", "Day", "150", "150", "mac_calendar"},
        {"clock_digital", "Clock", "Digital", "150", "150", "mac_clock"},
        {"clock_analog", "Clock", "Analog", "150", "150", "mac_clock"},
        {"clock_city", "Clock", "City", "150", "150", "mac_clock"},
        {"clock_world", "Clock", "World", "310", "150", "mac_clock"},
        {"wx_small", "Weather", "Small", "150", "150", "mac_weather"},
        {"wx_medium", "Weather", "Medium", "310", "150", "mac_weather"},
        {"wx_large", "Weather", "Large", "310", "310", "mac_weather"},
        {"rem_small", "Reminders", "Small", "150", "150", "mac_reminders"},
        {"rem_medium", "Reminders", "Medium", "310", "150", "mac_reminders"},
        {"notes", "Notes", "Note", "150", "150", "mac_notes"},
        {"battery", "Batteries", "Batteries", "310", "110", "mac_system_settings"},
    };
    static final String[] GROUPS = {"Calendar", "Clock", "Weather", "Reminders", "Notes", "Batteries"};

    static String[] info(String type) {
        for (String[] c : CATALOG) if (c[0].equals(type)) return c;
        return null;
    }

    // ===================================================== persistence
    public static void restore(MainActivity a) {
        a.nativeLayer.removeAllViews();
        for (String e : a.prefs.getString("nwidgets", "").split(";")) {
            if (e.isEmpty()) continue;
            String[] p = e.split(":");
            try { place(a, p[0], Integer.parseInt(p[1]), Integer.parseInt(p[2])); } catch (Exception ex) { }
        }
    }

    static void save(MainActivity a) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < a.nativeLayer.getChildCount(); i++) {
            View c = a.nativeLayer.getChildAt(i);
            if (c instanceof Box) sb.append(((Box) c).type).append(':').append((int) c.getX()).append(':').append((int) c.getY()).append(';');
        }
        a.prefs.edit().putString("nwidgets", sb.toString()).apply();
    }

    static void place(MainActivity a, String type, int x, int y) {
        String[] in = info(type);
        if (in == null) return;
        Box b = new Box(a, type, make(a, type));
        a.nativeLayer.addView(b, new FrameLayout.LayoutParams(a.dp(Integer.parseInt(in[3])), a.dp(Integer.parseInt(in[4])), Gravity.TOP | Gravity.LEFT));
        b.setX(x);
        b.setY(y);
    }

    static void add(MainActivity a, String type) {
        int n = a.nativeLayer.getChildCount() % 5;
        place(a, type, a.dp(200) + n * a.dp(18), a.mbH() + a.dp(14) + n * a.dp(18));
        save(a);
    }

    public static void exitEdit(MainActivity a) {
        for (int i = 0; i < a.nativeLayer.getChildCount(); i++) {
            View c = a.nativeLayer.getChildAt(i);
            if (c instanceof Box && ((Box) c).edit) ((Box) c).exitEdit();
        }
        save(a);
    }

    // ===================================================== draggable container
    static class Box extends FrameLayout {
        final String type;
        final MainActivity a;
        boolean edit, moved;
        float dx, dy, downX, downY;
        final TextView badge;
        final Runnable lp = new Runnable() { @Override public void run() { enter(); } };

        Box(MainActivity act, String type, View content) {
            super(act);
            a = act;
            this.type = type;
            final float r = a.dp(22);
            addView(content, new FrameLayout.LayoutParams(-1, -1));
            setOutlineProvider(new android.view.ViewOutlineProvider() {
                @Override public void getOutline(View v, android.graphics.Outline o) { o.setRoundRect(0, 0, v.getWidth(), v.getHeight(), r); }
            });
            setClipToOutline(true);
            badge = Ui.tv(act, "\u2212", 18, 0xFFFFFFFF, true);
            badge.setGravity(Gravity.CENTER);
            badge.setBackground(Ui.rr(0xCC3A3A3C, a.dp(14)));
            badge.setVisibility(GONE);
            addView(badge, new FrameLayout.LayoutParams(a.dp(28), a.dp(28), Gravity.TOP | Gravity.LEFT));
        }

        void enter() {
            exitEditOthers();
            edit = true;
            badge.setVisibility(VISIBLE);
            animate().scaleX(1.04f).scaleY(1.04f).setDuration(120).start();
        }

        void exitEditOthers() {
            for (int i = 0; i < a.nativeLayer.getChildCount(); i++) {
                View c = a.nativeLayer.getChildAt(i);
                if (c != this && c instanceof Box && ((Box) c).edit) ((Box) c).exitEdit();
            }
            a.exitWidgetEditAndroid();
        }

        void exitEdit() {
            edit = false;
            badge.setVisibility(GONE);
            animate().scaleX(1f).scaleY(1f).setDuration(120).start();
        }

        @Override public boolean onInterceptTouchEvent(MotionEvent e) {
            if (edit) return true;
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    downX = e.getRawX(); downY = e.getRawY(); moved = false;
                    postDelayed(lp, 600);
                    break;
                case MotionEvent.ACTION_MOVE: {
                    int sl = ViewConfiguration.get(a).getScaledTouchSlop();
                    if (Math.abs(e.getRawX() - downX) > sl || Math.abs(e.getRawY() - downY) > sl) removeCallbacks(lp);
                    break;
                }
                case MotionEvent.ACTION_UP: case MotionEvent.ACTION_CANCEL:
                    removeCallbacks(lp);
                    break;
            }
            return false;
        }

        @Override public boolean onTouchEvent(MotionEvent e) {
            if (!edit) {
                if (e.getActionMasked() == MotionEvent.ACTION_UP && Math.abs(e.getRawX() - downX) < a.dp(10) && Math.abs(e.getRawY() - downY) < a.dp(10)) tap();
                return true;
            }
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    dx = getX() - e.getRawX(); dy = getY() - e.getRawY(); moved = false;
                    downX = e.getRawX(); downY = e.getRawY();
                    return true;
                case MotionEvent.ACTION_MOVE:
                    if (Math.abs(e.getRawX() - downX) > 12 || Math.abs(e.getRawY() - downY) > 12) moved = true;
                    if (moved) { setX(e.getRawX() + dx); setY(Math.max(a.mbH(), e.getRawY() + dy)); }
                    return true;
                case MotionEvent.ACTION_UP:
                    if (!moved && e.getX() < a.dp(36) && e.getY() < a.dp(36)) { a.nativeLayer.removeView(this); save(a); }
                    else save(a);
                    return true;
            }
            return true;
        }

        void tap() {
            if (type.startsWith("wx_")) a.openBuiltin("Weather");
            else if (type.startsWith("cal_")) a.openBuiltin("Calendar");
            else if (type.startsWith("rem_")) a.openBuiltin("Reminders");
            else if (type.equals("notes")) a.openBuiltin("Notes");
        }
    }

    // ===================================================== factory
    static View make(MainActivity a, String type) {
        switch (type) {
            case "cal_month": return calMonth(a);
            case "cal_day": return calDay(a);
            case "clock_digital": return new DigitalClock(a);
            case "clock_analog": return clockFace(a, false, TimeZone.getDefault(), null, true);
            case "clock_city": return clockFace(a, true, TimeZone.getDefault(), cityTag(a), false);
            case "clock_world": return worldClock(a);
            case "wx_small": return wxSmall(a);
            case "wx_medium": return wxMedium(a, false);
            case "wx_large": return wxMedium(a, true);
            case "rem_small": return reminders(a, 3);
            case "rem_medium": return reminders(a, 4);
            case "notes": return notes(a);
            case "battery": return battery(a);
        }
        return new View(a);
    }

    static String cityTag(MainActivity a) {
        String n = a.prefs.getString("wx_hname", "");
        if (n.length() >= 3) return n.substring(0, 3).toUpperCase();
        return "LOC";
    }

    static FrameLayout glass(MainActivity a, int tint) {
        FrameLayout f = new FrameLayout(a);
        f.setBackground(new GlassDrawable(f, a.dp(22), a.dp(0.8f), tint));
        return f;
    }

    static FrameLayout solid(MainActivity a, int color) {
        FrameLayout f = new FrameLayout(a);
        f.setBackground(Ui.rr(color, a.dp(22)));
        return f;
    }

    // ----- calendar -----
    static View calMonth(MainActivity a) {
        Calendar cal = Calendar.getInstance();
        int today = cal.get(Calendar.DAY_OF_MONTH);
        Calendar f = (Calendar) cal.clone();
        f.set(Calendar.DAY_OF_MONTH, 1);
        int off = f.get(Calendar.DAY_OF_WEEK) - 1, dim = cal.getActualMaximum(Calendar.DAY_OF_MONTH);
        FrameLayout box = glass(a, 0xD9FFFFFF);
        LinearLayout w = new LinearLayout(a);
        w.setOrientation(LinearLayout.VERTICAL);
        w.setPadding(a.dp(12), a.dp(10), a.dp(12), a.dp(8));
        w.addView(Ui.tv(a, new SimpleDateFormat("MMMM", L.locale()).format(cal.getTime()).toUpperCase(L.locale()), 9.5f, 0xFFFF3B30, true));
        String[] dn = {"S", "M", "T", "W", "T", "F", "S"};
        LinearLayout hr = new LinearLayout(a);
        for (String d : dn) { TextView t = Ui.tv(a, d, 8.5f, 0xFF8E8E93, true); t.setGravity(Gravity.CENTER); hr.addView(t, new LinearLayout.LayoutParams(0, a.dp(15), 1f)); }
        LinearLayout.LayoutParams hl = new LinearLayout.LayoutParams(-1, -2);
        hl.topMargin = a.dp(3);
        w.addView(hr, hl);
        int d = 1 - off;
        for (int r = 0; r < 6; r++) {
            LinearLayout row = new LinearLayout(a);
            for (int c = 0; c < 7; c++, d++) {
                FrameLayout cell = new FrameLayout(a);
                if (d >= 1 && d <= dim) {
                    TextView t = Ui.tv(a, String.valueOf(d), 8.5f, d == today ? Color.WHITE : (c == 0 || c == 6 ? 0xFF8E8E93 : 0xFF1C1C1E), d == today);
                    t.setGravity(Gravity.CENTER);
                    if (d == today) { GradientDrawable g = new GradientDrawable(); g.setShape(GradientDrawable.OVAL); g.setColor(0xFFFF3B30); t.setBackground(g); }
                    cell.addView(t, new FrameLayout.LayoutParams(a.dp(15), a.dp(15), Gravity.CENTER));
                }
                row.addView(cell, new LinearLayout.LayoutParams(0, a.dp(15), 1f));
            }
            w.addView(row);
        }
        box.addView(w, new FrameLayout.LayoutParams(-1, -1));
        return box;
    }

    static View calDay(MainActivity a) {
        Calendar cal = Calendar.getInstance();
        FrameLayout box = solid(a, Theme.dark ? 0xFF2C2C2E : 0xFFF5F5F7);
        LinearLayout w = new LinearLayout(a);
        w.setOrientation(LinearLayout.VERTICAL);
        w.setPadding(a.dp(14), a.dp(12), a.dp(12), a.dp(10));
        w.addView(Ui.tv(a, new SimpleDateFormat("EEEE", L.locale()).format(cal.getTime()).toUpperCase(L.locale()), 11, 0xFFFF3B30, true));
        w.addView(Ui.tv(a, String.valueOf(cal.get(Calendar.DAY_OF_MONTH)), 38, Theme.dark ? 0xFFFFFFFF : 0xFF1C1C1E, false));
        // today's events from the Calendar app
        String ymd = new SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(cal.getTime());
        List<String> ev = new ArrayList<>();
        try {
            JSONArray ar = new JSONArray(a.prefs.getString("cal_events", "[]"));
            for (int i = 0; i < ar.length(); i++) {
                JSONObject o = ar.getJSONObject(i);
                if (ymd.equals(o.optString("d"))) ev.add((o.optString("tm", "").isEmpty() ? "" : o.optString("tm") + "  ") + o.optString("t"));
            }
        } catch (Exception e) { }
        View sp = new View(a);
        w.addView(sp, new LinearLayout.LayoutParams(1, 0, 1f));
        if (ev.isEmpty()) w.addView(Ui.tv(a, "No events today", 11.5f, 0xFF8E8E93, false));
        else for (int i = 0; i < Math.min(2, ev.size()); i++) {
            TextView t = Ui.tv(a, ev.get(i), 11, Theme.dark ? 0xFFFFFFFF : 0xFF1C1C1E, true);
            t.setSingleLine(true);
            t.setEllipsize(android.text.TextUtils.TruncateAt.END);
            t.setPadding(a.dp(6), 0, 0, 0);
            t.setBackground(new LeftBar(0xFF0A84FF, a.dp(2)));
            w.addView(t);
        }
        box.addView(w, new FrameLayout.LayoutParams(-1, -1));
        return box;
    }

    static class LeftBar extends android.graphics.drawable.Drawable {
        final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        final int w;
        LeftBar(int c, int w) { p.setColor(c); this.w = w; }
        @Override public void draw(Canvas c) { c.drawRoundRect(new RectF(0, 0, w, getBounds().height()), w, w, p); }
        @Override public void setAlpha(int a) { }
        @Override public void setColorFilter(ColorFilter f) { }
        @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
    }

    // ----- clocks -----
    static class DigitalClock extends View {
        final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        final MainActivity a;

        DigitalClock(MainActivity a) { super(a); this.a = a; }

        @Override protected void onDraw(Canvas cv) {
            float w = getWidth(), h = getHeight(), r = a.dp(22);
            p.setStyle(Paint.Style.FILL);
            p.setColor(0xFFFFFFFF);
            cv.drawRoundRect(new RectF(0, 0, w, h), r, r, p);
            Path path = new Path();
            float m = a.dp(2.5f), x0 = m, y0 = m, x1 = w - m, y1 = h - m, rr = r - m;
            path.moveTo(w / 2f, y0);
            path.lineTo(x1 - rr, y0);
            path.arcTo(new RectF(x1 - 2 * rr, y0, x1, y0 + 2 * rr), -90, 90);
            path.lineTo(x1, y1 - rr);
            path.arcTo(new RectF(x1 - 2 * rr, y1 - 2 * rr, x1, y1), 0, 90);
            path.lineTo(x0 + rr, y1);
            path.arcTo(new RectF(x0, y1 - 2 * rr, x0 + 2 * rr, y1), 90, 90);
            path.lineTo(x0, y0 + rr);
            path.arcTo(new RectF(x0, y0, x0 + 2 * rr, y0 + 2 * rr), 180, 90);
            path.close();
            PathMeasure pm = new PathMeasure(path, true);
            float len = pm.getLength();
            int sec = Calendar.getInstance().get(Calendar.SECOND);
            float[] pos = new float[2], tan = new float[2];
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeCap(Paint.Cap.ROUND);
            p.setStrokeWidth(a.dp(1.6f));
            float tl = a.dp(7);
            for (int i = 0; i < 60; i++) {
                pm.getPosTan(len * i / 60f, pos, tan);
                float nx = tan[1], ny = -tan[0];   // inward normal for a clockwise path
                p.setColor(i <= sec ? 0xFF1C1C1E : 0xFFC9C9CE);
                cv.drawLine(pos[0], pos[1], pos[0] + nx * tl, pos[1] + ny * tl, p);
            }
            p.setStyle(Paint.Style.FILL);
            p.setColor(0xFF000000);
            p.setTypeface(Emoji.font(true));
            p.setTextAlign(Paint.Align.CENTER);
            String t = new SimpleDateFormat("h:mm", java.util.Locale.US).format(new Date());
            p.setTextSize(h * .43f);
            p.setTextScaleX(.86f);
            cv.drawText(t, w / 2f, h / 2f + h * .15f, p);
            p.setTextScaleX(1f);
            postInvalidateDelayed(1000);
        }
    }

    static class Face extends View {
        final TimeZone tz;
        final boolean dark, glassFace;
        final String tag;
        final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);

        Face(Context c, TimeZone tz, boolean dark, String tag, boolean glassFace) { super(c); this.tz = tz; this.dark = dark; this.tag = tag; this.glassFace = glassFace; }

        @Override protected void onDraw(Canvas cv) {
            float w = getWidth(), h = getHeight(), cx = w / 2f, cy = h / 2f, r = Math.min(w, h) / 2f - 1;
            int fg = dark ? 0xFFFFFFFF : 0xFF1C1C1E;
            p.setStyle(Paint.Style.FILL);
            p.setColor(dark ? 0xFF2A2A2C : 0xFFFFFFFF);
            cv.drawCircle(cx, cy, r, p);
            Calendar c = Calendar.getInstance(tz);
            float sec = c.get(Calendar.SECOND) + c.get(Calendar.MILLISECOND) / 1000f;
            float min = c.get(Calendar.MINUTE) + sec / 60f;
            float hr = (c.get(Calendar.HOUR) % 12) + min / 60f;
            p.setStrokeCap(Paint.Cap.ROUND);
            if (glassFace) {
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(Math.max(1f, r * .012f));
                for (int i = 0; i < 60; i++) {
                    double an = Math.PI * 2 * i / 60;
                    float a0 = i % 5 == 0 ? r * .92f : r * .95f;
                    p.setColor(i % 5 == 0 ? 0xFF8E8E93 : 0xFFC7C7CC);
                    cv.drawLine((float) (cx + Math.sin(an) * a0), (float) (cy - Math.cos(an) * a0), (float) (cx + Math.sin(an) * r * .985f), (float) (cy - Math.cos(an) * r * .985f), p);
                }
            }
            p.setStyle(Paint.Style.FILL);
            p.setTypeface(Emoji.font(true));
            p.setTextAlign(Paint.Align.CENTER);
            p.setColor(fg);
            boolean nums = glassFace || r > a2();
            if (nums) {
                p.setTextSize(r * (glassFace ? .24f : .2f));
                for (int i = 1; i <= 12; i++) {
                    double an = Math.PI * 2 * i / 12;
                    cv.drawText(String.valueOf(i), (float) (cx + Math.sin(an) * r * .76f), (float) (cy - Math.cos(an) * r * .76f) + r * .08f, p);
                }
            } else {
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(r * .05f);
                p.setColor(0xFF8E8E93);
                for (int i = 0; i < 12; i++) {
                    double an = Math.PI * 2 * i / 12;
                    cv.drawLine((float) (cx + Math.sin(an) * r * .82f), (float) (cy - Math.cos(an) * r * .82f), (float) (cx + Math.sin(an) * r * .92f), (float) (cy - Math.cos(an) * r * .92f), p);
                }
                p.setStyle(Paint.Style.FILL);
            }
            if (tag != null) {
                p.setColor(0xFF8E8E93);
                p.setTextSize(r * .17f);
                cv.drawText(tag, cx, cy - r * .32f, p);
            }
            p.setStyle(Paint.Style.STROKE);
            p.setColor(fg);
            p.setStrokeWidth(r * .085f);
            hand(cv, cx, cy, hr / 12f * 360f, r * .5f, 0);
            p.setStrokeWidth(r * .06f);
            hand(cv, cx, cy, min / 60f * 360f, r * .78f, 0);
            p.setColor(0xFFFF9500);
            p.setStrokeWidth(r * .028f);
            hand(cv, cx, cy, sec / 60f * 360f, r * .86f, r * .16f);
            p.setStyle(Paint.Style.FILL);
            cv.drawCircle(cx, cy, r * .055f, p);
            p.setColor(dark ? 0xFF2A2A2C : 0xFFFFFFFF);
            cv.drawCircle(cx, cy, r * .02f, p);
            postInvalidateDelayed(66);
        }

        float a2() { return 1e9f; }

        void hand(Canvas cv, float cx, float cy, float deg, float len, float tail) {
            double an = Math.toRadians(deg);
            cv.drawLine((float) (cx - Math.sin(an) * tail), (float) (cy + Math.cos(an) * tail), (float) (cx + Math.sin(an) * len), (float) (cy - Math.cos(an) * len), p);
        }
    }

    static View clockFace(MainActivity a, boolean dark, TimeZone tz, String tag, boolean glassFace) {
        FrameLayout box = dark ? solid(a, 0xFF1C1C1E) : glass(a, 0xCCEAF3FF);
        box.addView(new Face(a, tz, false, tag, glassFace), new FrameLayout.LayoutParams(-1, -1));
        ((FrameLayout.LayoutParams) box.getChildAt(0).getLayoutParams()).setMargins(a.dp(12), a.dp(12), a.dp(12), a.dp(12));
        if (dark) ((Face) box.getChildAt(0)).setTag("city");
        return box;
    }

    static View worldClock(MainActivity a) {
        FrameLayout box = solid(a, 0xFF1C1C1E);
        LinearLayout row = new LinearLayout(a);
        row.setPadding(a.dp(10), a.dp(10), a.dp(10), a.dp(8));
        String[][] cs = {{cityName(a), TimeZone.getDefault().getID()}, {"Paris", "Europe/Paris"}, {"Tokyo", "Asia/Tokyo"}, {"New York", "America/New_York"}};
        TimeZone local = TimeZone.getDefault();
        long now = System.currentTimeMillis();
        for (String[] c : cs) {
            TimeZone tz = TimeZone.getTimeZone(c[1]);
            LinearLayout col = new LinearLayout(a);
            col.setOrientation(LinearLayout.VERTICAL);
            col.setGravity(Gravity.CENTER_HORIZONTAL);
            Face f = new Face(a, tz, isNight(tz), null, true);
            col.addView(f, new LinearLayout.LayoutParams(a.dp(62), a.dp(62)));
            TextView n = Ui.tv(a, c[0], 11, 0xFFFFFFFF, true);
            n.setSingleLine(true);
            n.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams nl = new LinearLayout.LayoutParams(-2, -2);
            nl.topMargin = a.dp(6);
            col.addView(n, nl);
            Calendar cl = Calendar.getInstance(local), ct = Calendar.getInstance(tz);
            int dd = ct.get(Calendar.DAY_OF_YEAR) - cl.get(Calendar.DAY_OF_YEAR);
            if (ct.get(Calendar.YEAR) != cl.get(Calendar.YEAR)) dd = ct.get(Calendar.YEAR) > cl.get(Calendar.YEAR) ? 1 : -1;
            col.addView(Ui.tv(a, dd == 0 ? "Today" : dd > 0 ? "Tomorrow" : "Yesterday", 9.5f, 0xFF8E8E93, true));
            int off = (tz.getOffset(now) - local.getOffset(now)) / 3600000;
            col.addView(Ui.tv(a, (off >= 0 ? "+" : "") + off + "HRS", 9.5f, 0xFF8E8E93, true));
            row.addView(col, new LinearLayout.LayoutParams(0, -1, 1f));
        }
        box.addView(row, new FrameLayout.LayoutParams(-1, -1));
        return box;
    }

    static boolean isNight(TimeZone tz) {
        int h = Calendar.getInstance(tz).get(Calendar.HOUR_OF_DAY);
        return h < 6 || h >= 18;
    }

    static String cityName(MainActivity a) {
        String n = a.prefs.getString("wx_hname", "");
        return n.isEmpty() || n.equals("My Location") ? "Local" : n;
    }

    // ----- weather -----
    static FrameLayout wxShell(MainActivity a) {
        FrameLayout box = new FrameLayout(a);
        GradientDrawable gd0 = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{0xFF1D6FD1, 0xFF6FB3F0});
        gd0.setCornerRadius(a.dp(22));
        box.setBackground(gd0);
        return box;
    }

    static View wxSmall(final MainActivity a) { return wxBind(a, wxShell(a), 0); }

    static View wxMedium(final MainActivity a, final boolean large) { return wxBind(a, wxShell(a), large ? 2 : 1); }

    /** mode 0 small, 1 medium, 2 large. Re-binds the content whenever new data arrives. */
    static View wxBind(final MainActivity a, final FrameLayout box, final int mode) {
        final Wx.City c = Wx.cities(a).get(0);
        final Wx.Cb cb = (f, aq) -> {
            if (f == null) return;
            try { wxRender(a, box, f, mode); } catch (Exception e) { }
        };
        JSONObject cached = Wx.cached(a, c);
        if (cached != null) cb.on(cached, null);
        Wx.locate(a, () -> Wx.fetch(a, Wx.cities(a).get(0), false, cb));
        return box;
    }

    static void wxRender(MainActivity a, FrameLayout box, JSONObject f, int mode) throws Exception {
        JSONObject cu = f.getJSONObject("current"), d = f.getJSONObject("daily"), h = f.getJSONObject("hourly");
        int code = cu.getInt("weather_code");
        boolean day = cu.getInt("is_day") == 1;
        int[] g = Wx.sky(code, day);
        GradientDrawable gd = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{g[0], g[1]});
        gd.setCornerRadius(a.dp(22));
        box.setBackground(gd);
        box.removeAllViews();
        String name = Wx.cities(a).get(0).name;
        String hl = "H:" + Wx.temp(d.getJSONArray("temperature_2m_max").getDouble(0)) + " L:" + Wx.temp(d.getJSONArray("temperature_2m_min").getDouble(0));
        String temp = Wx.temp(cu.getDouble("temperature_2m"));
        LinearLayout w = new LinearLayout(a);
        w.setOrientation(LinearLayout.VERTICAL);
        w.setPadding(a.dp(14), a.dp(12), a.dp(14), a.dp(10));
        if (mode == 0) {
            w.addView(Ui.tv(a, name + " \u27A4", 12.5f, 0xFFFFFFFF, true));
            w.addView(Ui.tv(a, temp, 40, 0xFFFFFFFF, false));
            w.addView(new View(a), new LinearLayout.LayoutParams(1, 0, 1f));
            Wx.Icon ic = new Wx.Icon(a, code, day);
            w.addView(ic, new LinearLayout.LayoutParams(a.dp(18), a.dp(18)));
            w.addView(Ui.tv(a, Wx.cond(code, day), 12, 0xFFFFFFFF, true));
            w.addView(Ui.tv(a, hl, 11.5f, 0xFFFFFFFF, false));
        } else {
            LinearLayout top = new LinearLayout(a);
            LinearLayout l = new LinearLayout(a);
            l.setOrientation(LinearLayout.VERTICAL);
            l.addView(Ui.tv(a, name, 14, 0xFFFFFFFF, true));
            l.addView(Ui.tv(a, temp, mode == 2 ? 42 : 34, 0xFFFFFFFF, false));
            top.addView(l, new LinearLayout.LayoutParams(0, -2, 1f));
            LinearLayout r = new LinearLayout(a);
            r.setOrientation(LinearLayout.VERTICAL);
            r.setGravity(Gravity.RIGHT);
            r.addView(new Wx.Icon(a, code, day), new LinearLayout.LayoutParams(a.dp(18), a.dp(18)));
            r.addView(Ui.tv(a, Wx.cond(code, day), 12, 0xFFFFFFFF, false));
            r.addView(Ui.tv(a, hl, 11.5f, 0xFFFFFFFF, false));
            top.addView(r);
            w.addView(top, new LinearLayout.LayoutParams(-1, -2));
            View sep = new View(a);
            sep.setBackgroundColor(0x40FFFFFF);
            LinearLayout.LayoutParams sl = new LinearLayout.LayoutParams(-1, 1);
            sl.setMargins(0, a.dp(6), 0, a.dp(4));
            w.addView(sep, sl);
            JSONArray ht = h.getJSONArray("time"), htmp = h.getJSONArray("temperature_2m"), hc = h.getJSONArray("weather_code"), hd = h.getJSONArray("is_day");
            String nowH = cu.getString("time").substring(0, 13) + ":00";
            int st = 0;
            for (int i = 0; i < ht.length(); i++) if (ht.getString(i).equals(nowH)) { st = i; break; }
            LinearLayout hr = new LinearLayout(a);
            for (int i = st; i < Math.min(ht.length(), st + 6); i++) {
                LinearLayout col = new LinearLayout(a);
                col.setOrientation(LinearLayout.VERTICAL);
                col.setGravity(Gravity.CENTER_HORIZONTAL);
                String lb;
                try { lb = new SimpleDateFormat("ha", L.locale()).format(new SimpleDateFormat("yyyy-MM-dd'T'HH:mm", java.util.Locale.US).parse(ht.getString(i))); } catch (Exception e) { lb = ""; }
                col.addView(Ui.tv(a, lb, 9.5f, 0xCCFFFFFF, true));
                LinearLayout.LayoutParams il = new LinearLayout.LayoutParams(a.dp(20), a.dp(20));
                il.topMargin = a.dp(3);
                col.addView(new Wx.Icon(a, hc.getInt(i), hd.getInt(i) == 1), il);
                col.addView(Ui.tv(a, Wx.temp(htmp.getDouble(i)), 11.5f, 0xFFFFFFFF, true));
                hr.addView(col, new LinearLayout.LayoutParams(0, -2, 1f));
            }
            w.addView(hr, new LinearLayout.LayoutParams(-1, -2));
            if (mode == 2) {
                View s2 = new View(a);
                s2.setBackgroundColor(0x40FFFFFF);
                LinearLayout.LayoutParams s2l = new LinearLayout.LayoutParams(-1, 1);
                s2l.setMargins(0, a.dp(6), 0, a.dp(2));
                w.addView(s2, s2l);
                JSONArray dt = d.getJSONArray("time"), dmax = d.getJSONArray("temperature_2m_max"), dmin = d.getJSONArray("temperature_2m_min"), dc = d.getJSONArray("weather_code");
                double gmin = 1e9, gmax = -1e9;
                for (int i = 0; i < 5; i++) { gmin = Math.min(gmin, dmin.getDouble(i)); gmax = Math.max(gmax, dmax.getDouble(i)); }
                for (int i = 1; i < 6; i++) {
                    LinearLayout row = new LinearLayout(a);
                    row.setGravity(Gravity.CENTER_VERTICAL);
                    String dn;
                    try { dn = new SimpleDateFormat("EEE", L.locale()).format(new SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).parse(dt.getString(i))); } catch (Exception e) { dn = ""; }
                    row.addView(Ui.tv(a, dn, 12, 0xFFFFFFFF, true), new LinearLayout.LayoutParams(a.dp(40), -2));
                    row.addView(new Wx.Icon(a, dc.getInt(i), true), new LinearLayout.LayoutParams(a.dp(20), a.dp(20)));
                    TextView lo = Ui.tv(a, Wx.temp(dmin.getDouble(i)), 11.5f, 0x99FFFFFF, true);
                    lo.setGravity(Gravity.RIGHT);
                    row.addView(lo, new LinearLayout.LayoutParams(a.dp(34), -2));
                    LinearLayout.LayoutParams rl = new LinearLayout.LayoutParams(0, a.dp(10), 1f);
                    rl.setMargins(a.dp(6), 0, a.dp(6), 0);
                    row.addView(new WeatherApp.RangeBar(a, dmin.getDouble(i), dmax.getDouble(i), gmin, gmax, Wx.fahr(a), Double.NaN), rl);
                    row.addView(Ui.tv(a, Wx.temp(dmax.getDouble(i)), 11.5f, 0xFFFFFFFF, true), new LinearLayout.LayoutParams(a.dp(34), -2));
                    LinearLayout.LayoutParams rw = new LinearLayout.LayoutParams(-1, 0, 1f);
                    w.addView(row, rw);
                }
            }
        }
        box.addView(w, new FrameLayout.LayoutParams(-1, -1));
    }

    // ----- reminders -----
    static View reminders(MainActivity a, int rows) {
        FrameLayout box = solid(a, Theme.dark ? 0xFF2C2C2E : 0xFFFFFFFF);
        LinearLayout w = new LinearLayout(a);
        w.setOrientation(LinearLayout.VERTICAL);
        w.setPadding(a.dp(14), a.dp(12), a.dp(14), a.dp(10));
        List<String> items = new ArrayList<>();
        String listName = "Reminders";
        int color = 0xFF0A84FF;
        try {
            JSONObject o = new JSONObject(a.prefs.getString("reminders", "{}"));
            JSONArray ls = o.optJSONArray("lists"), is = o.optJSONArray("items");
            if (ls != null && ls.length() > 0) { listName = ls.getJSONObject(0).getString("n"); color = ls.getJSONObject(0).getInt("c"); }
            if (is != null) for (int i = 0; i < is.length(); i++) {
                JSONObject j = is.getJSONObject(i);
                if (j.getInt("l") == 0 && !j.optBoolean("d")) items.add(j.getString("t"));
            }
        } catch (Exception e) { }
        int fg = Theme.dark ? 0xFFFFFFFF : 0xFF1C1C1E;
        LinearLayout hd = new LinearLayout(a);
        hd.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout hl = new LinearLayout(a);
        hl.setOrientation(LinearLayout.VERTICAL);
        hl.addView(Ui.tv(a, String.valueOf(items.size()), 26, fg, true));
        hl.addView(Ui.tv(a, listName, 12.5f, color, true));
        hd.addView(hl, new LinearLayout.LayoutParams(0, -2, 1f));
        w.addView(hd, new LinearLayout.LayoutParams(-1, -2));
        for (int i = 0; i < Math.min(rows, items.size()); i++) {
            LinearLayout r = new LinearLayout(a);
            r.setGravity(Gravity.CENTER_VERTICAL);
            View ring = new View(a);
            GradientDrawable g = new GradientDrawable();
            g.setShape(GradientDrawable.OVAL);
            g.setStroke(a.dp(1.2f), 0xFFB0B0B5);
            ring.setBackground(g);
            r.addView(ring, new LinearLayout.LayoutParams(a.dp(13), a.dp(13)));
            TextView t = Ui.tv(a, items.get(i), 11.5f, fg, false);
            t.setSingleLine(true);
            t.setEllipsize(android.text.TextUtils.TruncateAt.END);
            t.setPadding(a.dp(8), 0, 0, 0);
            r.addView(t, new LinearLayout.LayoutParams(0, -2, 1f));
            LinearLayout.LayoutParams rl = new LinearLayout.LayoutParams(-1, -2);
            rl.topMargin = a.dp(i == 0 ? 6 : 5);
            w.addView(r, rl);
        }
        if (items.isEmpty()) {
            TextView t = Ui.tv(a, "No Results", 11.5f, 0xFF8E8E93, false);
            t.setPadding(0, a.dp(8), 0, 0);
            w.addView(t);
        }
        box.addView(w, new FrameLayout.LayoutParams(-1, -1));
        return box;
    }

    // ----- notes -----
    static View notes(MainActivity a) {
        FrameLayout box = solid(a, Theme.dark ? 0xFF2C2C2E : 0xFFFFFFFF);
        View band = new View(a);
        band.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{0xFFFFD60A, 0xFFFFC400}));
        box.addView(band, new FrameLayout.LayoutParams(-1, a.dp(46), Gravity.TOP));
        LinearLayout w = new LinearLayout(a);
        w.setOrientation(LinearLayout.VERTICAL);
        w.setPadding(a.dp(14), a.dp(56), a.dp(14), a.dp(10));
        String title = "No Notes", body = "";
        long ts = 0;
        try {
            JSONArray ar = new JSONArray(a.prefs.getString("notes", "[]"));
            JSONObject best = null;
            for (int i = 0; i < ar.length(); i++) { JSONObject o = ar.getJSONObject(i); if (best == null || o.getLong("ts") > best.getLong("ts")) best = o; }
            if (best != null) {
                String[] ln = best.optString("t", "").split("\n");
                title = ln.length > 0 && !ln[0].trim().isEmpty() ? ln[0] : "New Note";
                StringBuilder sb = new StringBuilder();
                for (int i = 1; i < ln.length && sb.length() < 90; i++) if (!ln[i].trim().isEmpty()) sb.append(ln[i]).append('\n');
                body = sb.toString().trim();
                ts = best.getLong("ts");
            }
        } catch (Exception e) { }
        int fg = Theme.dark ? 0xFFFFFFFF : 0xFF1C1C1E;
        TextView t = Ui.tv(a, title, 13.5f, fg, true);
        t.setSingleLine(true);
        t.setEllipsize(android.text.TextUtils.TruncateAt.END);
        w.addView(t);
        TextView b = Ui.tv(a, body, 11.5f, 0xFF8E8E93, false);
        b.setMaxLines(3);
        b.setEllipsize(android.text.TextUtils.TruncateAt.END);
        w.addView(b);
        w.addView(new View(a), new LinearLayout.LayoutParams(1, 0, 1f));
        if (ts > 0) w.addView(Ui.tv(a, new SimpleDateFormat("h:mm a", L.locale()).format(new Date(ts)), 11, 0xFF8E8E93, false));
        box.addView(w, new FrameLayout.LayoutParams(-1, -1));
        return box;
    }

    // ----- batteries -----
    static class Ring extends View {
        final float level;
        final boolean charging, empty;
        final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);

        Ring(Context c, float level, boolean charging, boolean empty) { super(c); this.level = level; this.charging = charging; this.empty = empty; }

        @Override protected void onDraw(Canvas cv) {
            float w = getWidth(), h = getHeight(), s = Math.min(w, h), cx = w / 2f, cy = h / 2f, sw = s * .09f, r = s / 2f - sw;
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(sw);
            p.setStrokeCap(Paint.Cap.ROUND);
            p.setColor(0x44FFFFFF);
            cv.drawCircle(cx, cy, r, p);
            if (empty) return;
            p.setColor(level <= .2f ? 0xFFFF3B30 : 0xFF34C759);
            cv.drawArc(new RectF(cx - r, cy - r, cx + r, cy + r), -90, 360f * level, false, p);
            // phone glyph
            p.setStyle(Paint.Style.FILL);
            p.setColor(0xFF1C1C1E);
            float pw = s * .2f, ph = s * .34f;
            cv.drawRoundRect(new RectF(cx - pw / 2, cy - ph / 2, cx + pw / 2, cy + ph / 2), pw * .3f, pw * .3f, p);
            p.setColor(0xFFFFFFFF);
            cv.drawRoundRect(new RectF(cx - pw / 2 + s * .02f, cy - ph / 2 + s * .035f, cx + pw / 2 - s * .02f, cy + ph / 2 - s * .035f), pw * .2f, pw * .2f, p);
            if (charging) {
                p.setColor(0xFF34C759);
                p.setTextAlign(Paint.Align.CENTER);
                p.setTypeface(Emoji.font(true));
                p.setTextSize(s * .17f);
                cv.drawText("\u26A1", cx, cy - r - sw * .1f, p);
            }
        }
    }

    static View battery(MainActivity a) {
        FrameLayout box = glass(a, 0xB3FFFFFF);
        LinearLayout row = new LinearLayout(a);
        row.setGravity(Gravity.CENTER);
        row.setPadding(a.dp(10), a.dp(8), a.dp(10), a.dp(8));
        Intent b = a.registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        int l = b == null ? 0 : b.getIntExtra(BatteryManager.EXTRA_LEVEL, 0), sc = b == null ? 100 : b.getIntExtra(BatteryManager.EXTRA_SCALE, 100);
        int st = b == null ? 0 : b.getIntExtra(BatteryManager.EXTRA_STATUS, 0);
        boolean ch = st == BatteryManager.BATTERY_STATUS_CHARGING || st == BatteryManager.BATTERY_STATUS_FULL;
        float lv = sc > 0 ? l / (float) sc : 0f;
        for (int i = 0; i < 4; i++) {
            LinearLayout col = new LinearLayout(a);
            col.setOrientation(LinearLayout.VERTICAL);
            col.setGravity(Gravity.CENTER_HORIZONTAL);
            col.addView(new Ring(a, lv, ch, i != 0), new LinearLayout.LayoutParams(a.dp(58), a.dp(58)));
            TextView t = Ui.tv(a, i == 0 ? Math.round(lv * 100) + "%" : " ", 13, 0xFF1C1C1E, false);
            LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(-2, -2);
            tl.topMargin = a.dp(4);
            col.addView(t, tl);
            row.addView(col, new LinearLayout.LayoutParams(0, -2, 1f));
        }
        box.addView(row, new FrameLayout.LayoutParams(-1, -1));
        return box;
    }

    // ===================================================== gallery ("Edit Widgets")
    public static void picker(final MainActivity a) {
        a.dismissOverlays();
        FrameLayout sc = a.scrim();
        sc.setBackgroundColor(0x40000000);
        int pw = Math.min(a.dp(640), a.sw - a.dp(30)), ph = Math.min(a.dp(330), a.sh - a.mbH() - a.dp(30));
        final LinearLayout panel = new LinearLayout(a);
        panel.setClickable(true);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setBackground(Ui.rr(Theme.dark ? 0xF2262628 : 0xF2F5F5F7, a.dp(26)));
        panel.setClipToOutline(true);
        // header
        LinearLayout hd = new LinearLayout(a);
        hd.setGravity(Gravity.CENTER_VERTICAL);
        hd.setPadding(a.dp(16), a.dp(10), a.dp(12), a.dp(6));
        final EditText q = new EditText(a);
        q.setHint(L.t("Search") + " " + L.t("Widgets"));
        q.setHintTextColor(0xFF8E8E93);
        q.setTextColor(Theme.fg());
        q.setTextSize(12.5f);
        q.setSingleLine(true);
        q.setPadding(a.dp(12), 0, a.dp(12), 0);
        q.setBackground(Ui.rr(Theme.dark ? 0x2EFFFFFF : 0x14000000, a.dp(14)));
        hd.addView(q, new LinearLayout.LayoutParams(0, a.dp(30), 1f));
        TextView done = Ui.tv(a, "Done", 13, 0xFFFFFFFF, true);
        done.setGravity(Gravity.CENTER);
        done.setBackground(Ui.rr(Theme.accent, a.dp(15)));
        done.setPadding(a.dp(16), 0, a.dp(16), 0);
        done.setOnClickListener(v -> a.dismissOverlays());
        LinearLayout.LayoutParams dl = new LinearLayout.LayoutParams(-2, a.dp(30));
        dl.leftMargin = a.dp(10);
        hd.addView(done, dl);
        panel.addView(hd, new LinearLayout.LayoutParams(-1, -2));
        // body: groups left, previews right
        LinearLayout body = new LinearLayout(a);
        final LinearLayout left = new LinearLayout(a);
        left.setOrientation(LinearLayout.VERTICAL);
        left.setPadding(a.dp(10), a.dp(4), a.dp(6), a.dp(8));
        ScrollView ls = new ScrollView(a);
        ls.setVerticalScrollBarEnabled(false);
        ls.addView(left);
        final ScrollView rs = new ScrollView(a);
        rs.setVerticalScrollBarEnabled(false);
        final LinearLayout right = new LinearLayout(a);
        right.setOrientation(LinearLayout.VERTICAL);
        right.setGravity(Gravity.CENTER_HORIZONTAL);
        right.setPadding(a.dp(10), a.dp(6), a.dp(14), a.dp(14));
        rs.addView(right);
        body.addView(ls, new LinearLayout.LayoutParams(a.dp(150), -1));
        body.addView(rs, new LinearLayout.LayoutParams(0, -1, 1f));
        panel.addView(body, new LinearLayout.LayoutParams(-1, 0, 1f));
        final String[] cur = {GROUPS[0]};
        final Runnable[] show = new Runnable[1];
        show[0] = () -> {
            left.removeAllViews();
            for (final String g : GROUPS) {
                LinearLayout r = new LinearLayout(a);
                r.setGravity(Gravity.CENTER_VERTICAL);
                r.setPadding(a.dp(8), a.dp(6), a.dp(8), a.dp(6));
                boolean sel = g.equals(cur[0]) && q.getText().length() == 0;
                if (sel) r.setBackground(Ui.rr(Theme.dark ? 0x33FFFFFF : 0x1F000000, a.dp(10)));
                ImageView ic = new ImageView(a);
                String res = "mac_calendar";
                for (String[] c : CATALOG) if (c[1].equals(g)) { res = c[5]; break; }
                if (g.equals("Batteries")) res = "mac_system_settings";
                try { ic.setImageDrawable(a.getDrawable(a.res(res))); } catch (Exception e) { }
                r.addView(ic, new LinearLayout.LayoutParams(a.dp(24), a.dp(24)));
                TextView t = Ui.tv(a, g, 13, Theme.fg(), false);
                t.setPadding(a.dp(8), 0, 0, 0);
                r.addView(t);
                r.setOnClickListener(v -> { cur[0] = g; q.setText(""); show[0].run(); });
                left.addView(r);
            }
            right.removeAllViews();
            String qs = q.getText().toString().trim().toLowerCase(L.locale());
            for (final String[] c : CATALOG) {
                boolean match = qs.isEmpty() ? c[1].equals(cur[0]) : (L.t(c[1]).toLowerCase(L.locale()).contains(qs) || c[1].toLowerCase().contains(qs) || c[2].toLowerCase().contains(qs));
                if (!match) continue;
                FrameLayout pv = new FrameLayout(a);
                View wv = make(a, c[0]);
                FrameLayout wrap = new FrameLayout(a);
                wrap.setClipToOutline(true);
                wrap.setOutlineProvider(new android.view.ViewOutlineProvider() {
                    @Override public void getOutline(View v, android.graphics.Outline o) { o.setRoundRect(0, 0, v.getWidth(), v.getHeight(), a.dp(22)); }
                });
                wrap.addView(wv, new FrameLayout.LayoutParams(-1, -1));
                pv.addView(wrap, new FrameLayout.LayoutParams(a.dp(Integer.parseInt(c[3])), a.dp(Integer.parseInt(c[4]))));
                TextView plus = Ui.tv(a, "+", 18, 0xFFFFFFFF, true);
                plus.setGravity(Gravity.CENTER);
                plus.setBackground(Ui.rr(0xFF34C759, a.dp(12)));
                FrameLayout.LayoutParams pl = new FrameLayout.LayoutParams(a.dp(24), a.dp(24), Gravity.TOP | Gravity.LEFT);
                pl.setMargins(a.dp(-6), a.dp(-6), 0, 0);
                // the whole preview (and the + badge) adds the widget; the live preview itself is not interactive
                final String type = c[0];
                final View.OnClickListener addIt = v -> {
                    try {
                        add(a, type);
                        a.dismissOverlays();
                    } catch (Throwable ex) {
                        android.widget.Toast.makeText(a, String.valueOf(ex), android.widget.Toast.LENGTH_LONG).show();
                    }
                };
                View hit = new View(a);
                hit.setOnClickListener(addIt);
                pv.addView(hit, new FrameLayout.LayoutParams(-1, -1));
                plus.setOnClickListener(addIt);
                pv.addView(plus, pl);
                LinearLayout.LayoutParams vl = new LinearLayout.LayoutParams(-2, -2);
                vl.topMargin = a.dp(12);
                vl.gravity = Gravity.CENTER_HORIZONTAL;
                right.addView(pv, vl);
                TextView cap = Ui.tv(a, L.t(c[1]) + " \u2022 " + L.t(c[2]), 11, 0xFF8E8E93, false);
                cap.setGravity(Gravity.CENTER);
                LinearLayout.LayoutParams cl = new LinearLayout.LayoutParams(-2, -2);
                cl.topMargin = a.dp(4);
                cl.gravity = Gravity.CENTER_HORIZONTAL;
                right.addView(cap, cl);
            }
        };
        q.addTextChangedListener(new android.text.TextWatcher() {
            public void beforeTextChanged(CharSequence s, int x, int y, int z) { }
            public void onTextChanged(CharSequence s, int x, int y, int z) { }
            public void afterTextChanged(android.text.Editable e) { show[0].run(); }
        });
        show[0].run();
        TextView and = Ui.tv(a, "Android Widgets\u2026", 12, Theme.accent, true);
        and.setPadding(a.dp(18), a.dp(2), a.dp(18), a.dp(10));
        and.setOnClickListener(v -> a.showWidgetPicker());
        panel.addView(and);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(pw, ph, Gravity.CENTER);
        sc.addView(panel, lp);
        panel.setScaleX(.95f); panel.setScaleY(.95f); panel.setAlpha(0);
        panel.animate().scaleX(1).scaleY(1).alpha(1).setDuration(180).start();
    }
}
