package com.macos.launcher;

import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import org.json.JSONArray;
import org.json.JSONObject;

/** Calendar: Day / Week / Month / Year views, local events with colour-coded calendars. */
public class CalendarApp {
    static class Ev { long id; String title, date, time = ""; int cal; }

    static final String[] CALS = {"Calendar", "Home", "Work", "Travel"};
    static final int[] CAL_C = {0xFF0A84FF, 0xFF30D158, 0xFFFF9F0A, 0xFFBF5AF2};
    static final String[] MODES = {"Day", "Week", "Month", "Year"};

    final MainActivity a;
    final List<Ev> evs = new ArrayList<>();
    final boolean[] vis = {true, true, true, true};
    final SimpleDateFormat iso = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
    Calendar cursor = Calendar.getInstance();
    String mode = "Month";
    LinearLayout side, content, seg, titleBar;
    TextView titleTv;

    public static void open(MainActivity a) { new CalendarApp(a); }

    CalendarApp(MainActivity act) {
        a = act;
        load();
        LinearLayout tb = new LinearLayout(a);
        tb.setGravity(Gravity.CENTER_VERTICAL);
        TextView add = Ui.sfBtn(a, "plus", 13);
        add.setOnClickListener(v -> editDialog(null, iso.format(cursor.getTime())));
        tb.addView(add, new LinearLayout.LayoutParams(a.dp(32), a.dp(32)));
        tb.addView(new View(a), new LinearLayout.LayoutParams(0, 1, 1f));
        seg = new LinearLayout(a);
        seg.setBackground(Ui.rr(Theme.dark ? 0x2EFFFFFF : 0x12000000, a.dp(16)));
        seg.setPadding(a.dp(3), a.dp(3), a.dp(3), a.dp(3));
        tb.addView(seg, new LinearLayout.LayoutParams(-2, a.dp(32)));
        tb.addView(new View(a), new LinearLayout.LayoutParams(0, 1, 1f));

        LinearLayout body = new LinearLayout(a);
        side = new LinearLayout(a);
        side.setOrientation(LinearLayout.VERTICAL);
        side.setPadding(a.dp(12), a.dp(8), a.dp(8), a.dp(8));
        ScrollView sv = new ScrollView(a);
        sv.setBackgroundColor(Theme.side());
        sv.setVerticalScrollBarEnabled(false);
        sv.addView(side);
        body.addView(sv, new LinearLayout.LayoutParams(a.dp(150), -1));
        LinearLayout right = new LinearLayout(a);
        right.setOrientation(LinearLayout.VERTICAL);
        titleBar = new LinearLayout(a);
        titleBar.setGravity(Gravity.CENTER_VERTICAL);
        titleBar.setPadding(a.dp(14), a.dp(8), a.dp(10), a.dp(6));
        titleTv = Ui.tv(a, "", 22, Theme.fg(), true);
        titleBar.addView(titleTv, new LinearLayout.LayoutParams(0, -2, 1f));
        TextView prev = Ui.circleBtn(a, "\u2039"), today = Ui.tv(a, "Today", 12.5f, Theme.fg(), true), next = Ui.circleBtn(a, "\u203A");
        today.setGravity(Gravity.CENTER);
        today.setBackground(Ui.rr(Theme.dark ? 0x2EFFFFFF : 0x14000000, a.dp(14)));
        prev.setOnClickListener(v -> step(-1));
        next.setOnClickListener(v -> step(1));
        today.setOnClickListener(v -> { cursor = Calendar.getInstance(); refresh(); });
        titleBar.addView(prev, new LinearLayout.LayoutParams(a.dp(28), a.dp(28)));
        LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(a.dp(58), a.dp(28));
        tl.setMargins(a.dp(6), 0, a.dp(6), 0);
        titleBar.addView(today, tl);
        titleBar.addView(next, new LinearLayout.LayoutParams(a.dp(28), a.dp(28)));
        right.addView(titleBar, new LinearLayout.LayoutParams(-1, -2));
        content = new LinearLayout(a);
        content.setOrientation(LinearLayout.VERTICAL);
        right.addView(content, new LinearLayout.LayoutParams(-1, 0, 1f));
        body.addView(right, new LinearLayout.LayoutParams(0, -1, 1f));
        a.openWindow(new MacWindow(a, "Calendar", tb, null, body), 720, 340);
        refresh();
    }

    // ---------- data ----------
    void load() {
        try {
            JSONArray ar = new JSONArray(a.prefs.getString("cal_events", "[]"));
            for (int i = 0; i < ar.length(); i++) {
                JSONObject o = ar.getJSONObject(i);
                Ev e = new Ev(); e.id = o.getLong("id"); e.title = o.getString("t"); e.date = o.getString("d"); e.time = o.optString("tm", ""); e.cal = o.optInt("c");
                evs.add(e);
            }
        } catch (Exception e) { }
    }

    void save() {
        try {
            JSONArray ar = new JSONArray();
            for (Ev e : evs) ar.put(new JSONObject().put("id", e.id).put("t", e.title).put("d", e.date).put("tm", e.time).put("c", e.cal));
            a.prefs.edit().putString("cal_events", ar.toString()).apply();
        } catch (Exception e) { }
    }

    List<Ev> on(String d) {
        List<Ev> l = new ArrayList<>();
        for (Ev e : evs) if (e.date.equals(d) && vis[e.cal]) l.add(e);
        Collections.sort(l, new Comparator<Ev>() { public int compare(Ev x, Ev y) { return x.time.compareTo(y.time); } });
        return l;
    }

    // ---------- navigation ----------
    void step(int d) {
        switch (mode) {
            case "Day": cursor.add(Calendar.DAY_OF_MONTH, d); break;
            case "Week": cursor.add(Calendar.DAY_OF_MONTH, 7 * d); break;
            case "Month": cursor.add(Calendar.MONTH, d); break;
            default: cursor.add(Calendar.YEAR, d);
        }
        refresh();
    }

    void refresh() {
        drawSeg(); drawSide(); drawContent();
    }

    void drawSeg() {
        seg.removeAllViews();
        for (final String m : MODES) {
            TextView t = Ui.tv(a, m, 12.5f, Theme.fg(), m.equals(mode));
            t.setGravity(Gravity.CENTER);
            t.setPadding(a.dp(12), 0, a.dp(12), 0);
            if (m.equals(mode)) t.setBackground(Ui.rr(Theme.dark ? 0x40FFFFFF : 0xFFFFFFFF, a.dp(13)));
            t.setOnClickListener(v -> { mode = m; refresh(); });
            seg.addView(t, new LinearLayout.LayoutParams(-2, -1));
        }
    }

    void drawSide() {
        side.removeAllViews();
        TextView h = Ui.tv(a, "iCloud", 11.5f, Theme.sub(), true);
        h.setPadding(0, 0, 0, a.dp(4));
        side.addView(h);
        for (int i = 0; i < CALS.length; i++) {
            final int k = i;
            LinearLayout r = new LinearLayout(a);
            r.setGravity(Gravity.CENTER_VERTICAL);
            r.setPadding(0, a.dp(5), 0, a.dp(5));
            FrameLayout cb = new FrameLayout(a);
            GradientDrawable g = new GradientDrawable();
            g.setCornerRadius(a.dp(4));
            if (vis[i]) g.setColor(CAL_C[i]); else g.setStroke(a.dp(1.5f), CAL_C[i]);
            cb.setBackground(g);
            if (vis[i]) cb.addView(Sf.img(a, "checkmark", Color.WHITE), new FrameLayout.LayoutParams(a.dp(9), a.dp(9), Gravity.CENTER));
            r.addView(cb, new LinearLayout.LayoutParams(a.dp(16), a.dp(16)));
            TextView n = Ui.tv(a, CALS[i], 13, Theme.fg(), false);
            n.setPadding(a.dp(8), 0, 0, 0);
            r.addView(n);
            r.setOnClickListener(v -> { vis[k] = !vis[k]; refresh(); });
            side.addView(r);
        }
        side.addView(miniMonth());
    }

    View miniMonth() {
        LinearLayout box = new LinearLayout(a);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(0, a.dp(16), 0, 0);
        SimpleDateFormat mf = new SimpleDateFormat("MMMM yyyy", Locale.getDefault());
        TextView t = Ui.tv(a, mf.format(cursor.getTime()), 11.5f, Theme.fg(), true);
        box.addView(t);
        Calendar c = (Calendar) cursor.clone();
        c.set(Calendar.DAY_OF_MONTH, 1);
        int first = c.getFirstDayOfWeek();
        int off = (c.get(Calendar.DAY_OF_WEEK) - first + 7) % 7;
        int days = c.getActualMaximum(Calendar.DAY_OF_MONTH);
        String today = iso.format(new Date());
        LinearLayout row = null;
        for (int i = 0; i < off + days; i++) {
            if (i % 7 == 0) { row = new LinearLayout(a); box.addView(row, new LinearLayout.LayoutParams(-1, a.dp(18))); }
            TextView d = new TextView(a);
            d.setGravity(Gravity.CENTER);
            d.setTextSize(9.5f);
            if (i >= off) {
                int day = i - off + 1;
                Calendar x = (Calendar) c.clone(); x.set(Calendar.DAY_OF_MONTH, day);
                final String ds = iso.format(x.getTime());
                d.setText(String.valueOf(day));
                boolean td = ds.equals(today);
                d.setTextColor(td ? Color.WHITE : Theme.fg());
                if (td) { GradientDrawable g = new GradientDrawable(); g.setShape(GradientDrawable.OVAL); g.setColor(0xFFFF3B30); d.setBackground(g); }
                d.setOnClickListener(v -> { try { cursor.setTime(iso.parse(ds)); } catch (Exception e) { } mode = "Day"; refresh(); });
            }
            row.addView(d, new LinearLayout.LayoutParams(0, -1, 1f));
        }
        return box;
    }

    void drawContent() {
        content.removeAllViews();
        SimpleDateFormat mf = new SimpleDateFormat("MMMM", Locale.getDefault());
        switch (mode) {
            case "Month": setTitle(mf.format(cursor.getTime()), String.valueOf(cursor.get(Calendar.YEAR))); monthGrid(false); break;
            case "Week": {
                Calendar w = weekStart();
                setTitle(mf.format(w.getTime()), String.valueOf(w.get(Calendar.YEAR)));
                monthGrid(true); break;
            }
            case "Day": setTitle(new SimpleDateFormat("EEEE d MMMM", Locale.getDefault()).format(cursor.getTime()), String.valueOf(cursor.get(Calendar.YEAR))); dayList(); break;
            default: setTitle(String.valueOf(cursor.get(Calendar.YEAR)), ""); yearGrid();
        }
    }

    void setTitle(String bold, String light) {
        android.text.SpannableStringBuilder sb = new android.text.SpannableStringBuilder(bold);
        if (!light.isEmpty()) {
            sb.append(' ').append(light);
            sb.setSpan(new android.text.style.StyleSpan(Typeface.NORMAL), bold.length(), sb.length(), 0);
        }
        titleTv.setText(sb);
    }

    Calendar weekStart() {
        Calendar w = (Calendar) cursor.clone();
        w.add(Calendar.DAY_OF_MONTH, -((w.get(Calendar.DAY_OF_WEEK) - w.getFirstDayOfWeek() + 7) % 7));
        return w;
    }

    void monthGrid(boolean weekOnly) {
        Calendar start;
        int rows;
        if (weekOnly) { start = weekStart(); rows = 1; }
        else {
            start = (Calendar) cursor.clone();
            start.set(Calendar.DAY_OF_MONTH, 1);
            start.add(Calendar.DAY_OF_MONTH, -((start.get(Calendar.DAY_OF_WEEK) - start.getFirstDayOfWeek() + 7) % 7));
            rows = 6;
        }
        LinearLayout hd = new LinearLayout(a);
        SimpleDateFormat wf = new SimpleDateFormat("EEE", Locale.getDefault());
        Calendar hc = (Calendar) start.clone();
        for (int i = 0; i < 7; i++) {
            TextView t = Ui.tv(a, wf.format(hc.getTime()), 11.5f, Theme.sub(), false);
            t.setGravity(Gravity.CENTER);
            hd.addView(t, new LinearLayout.LayoutParams(0, -2, 1f));
            hc.add(Calendar.DAY_OF_MONTH, 1);
        }
        content.addView(hd, new LinearLayout.LayoutParams(-1, a.dp(20)));
        content.addView(Ui.sepLine(a), new LinearLayout.LayoutParams(-1, 1));
        String today = iso.format(new Date());
        Calendar c = (Calendar) start.clone();
        for (int r = 0; r < rows; r++) {
            LinearLayout row = new LinearLayout(a);
            for (int k = 0; k < 7; k++) {
                final String ds = iso.format(c.getTime());
                boolean other = !weekOnly && c.get(Calendar.MONTH) != cursor.get(Calendar.MONTH);
                LinearLayout cell = new LinearLayout(a);
                cell.setOrientation(LinearLayout.VERTICAL);
                cell.setPadding(a.dp(3), a.dp(2), a.dp(3), a.dp(2));
                cell.setBackground(cellBg());
                boolean td = ds.equals(today);
                TextView d = Ui.tv(a, String.valueOf(c.get(Calendar.DAY_OF_MONTH)), 11.5f, td ? Color.WHITE : other ? Theme.sub() : Theme.fg(), td);
                d.setGravity(Gravity.CENTER);
                if (td) { GradientDrawable g = new GradientDrawable(); g.setShape(GradientDrawable.OVAL); g.setColor(0xFFFF3B30); d.setBackground(g); }
                LinearLayout.LayoutParams dl = new LinearLayout.LayoutParams(a.dp(20), a.dp(20));
                dl.gravity = Gravity.RIGHT;
                cell.addView(d, dl);
                int shown = 0;
                for (final Ev e : on(ds)) {
                    if (shown++ >= (weekOnly ? 9 : 2)) { cell.addView(Ui.tv(a, "\u2026", 10, Theme.sub(), false)); break; }
                    TextView ch = Ui.tv(a, (e.time.isEmpty() ? "" : e.time + " ") + e.title, 9.5f, Theme.fg(), false);
                    ch.setSingleLine(true);
                    ch.setPadding(a.dp(4), 0, a.dp(2), 0);
                    ch.setBackground(Ui.rr((CAL_C[e.cal] & 0x00FFFFFF) | 0x33000000, a.dp(3)));
                    ch.setOnClickListener(v -> editDialog(e, e.date));
                    LinearLayout.LayoutParams cl = new LinearLayout.LayoutParams(-1, -2);
                    cl.topMargin = a.dp(1);
                    cell.addView(ch, cl);
                }
                cell.setOnClickListener(v -> editDialog(null, ds));
                row.addView(cell, new LinearLayout.LayoutParams(0, -1, 1f));
                c.add(Calendar.DAY_OF_MONTH, 1);
            }
            content.addView(row, new LinearLayout.LayoutParams(-1, 0, 1f));
        }
    }

    android.graphics.drawable.Drawable cellBg() {
        GradientDrawable g = new GradientDrawable();
        g.setColor(0);
        g.setStroke(1, Theme.sep());
        return g;
    }

    void dayList() {
        ScrollView sv = new ScrollView(a);
        LinearLayout l = new LinearLayout(a);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setPadding(a.dp(16), a.dp(6), a.dp(16), a.dp(12));
        List<Ev> es = on(iso.format(cursor.getTime()));
        if (es.isEmpty()) l.addView(Ui.tv(a, "No Events", 14, Theme.sub(), false));
        for (final Ev e : es) {
            LinearLayout r = new LinearLayout(a);
            r.setGravity(Gravity.CENTER_VERTICAL);
            r.setPadding(0, a.dp(7), 0, a.dp(7));
            View bar = new View(a);
            bar.setBackground(Ui.rr(CAL_C[e.cal], a.dp(2)));
            r.addView(bar, new LinearLayout.LayoutParams(a.dp(4), a.dp(32)));
            LinearLayout tx = new LinearLayout(a);
            tx.setOrientation(LinearLayout.VERTICAL);
            tx.setPadding(a.dp(10), 0, 0, 0);
            tx.addView(Ui.tv(a, e.title, 14, Theme.fg(), true));
            tx.addView(Ui.tv(a, (e.time.isEmpty() ? "All-day" : e.time) + "  \u00B7  " + CALS[e.cal], 11.5f, Theme.sub(), false));
            r.addView(tx);
            r.setOnClickListener(v -> editDialog(e, e.date));
            l.addView(r);
        }
        sv.addView(l);
        content.addView(sv, new LinearLayout.LayoutParams(-1, -1));
    }

    void yearGrid() {
        ScrollView sv = new ScrollView(a);
        LinearLayout col = new LinearLayout(a);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(a.dp(10), a.dp(4), a.dp(10), a.dp(10));
        SimpleDateFormat mf = new SimpleDateFormat("MMMM", Locale.getDefault());
        String today = iso.format(new Date());
        for (int r = 0; r < 4; r++) {
            LinearLayout row = new LinearLayout(a);
            for (int k = 0; k < 3; k++) {
                final int m = r * 3 + k;
                Calendar c = Calendar.getInstance();
                c.set(cursor.get(Calendar.YEAR), m, 1);
                LinearLayout box = new LinearLayout(a);
                box.setOrientation(LinearLayout.VERTICAL);
                box.setPadding(a.dp(6), a.dp(4), a.dp(6), a.dp(4));
                box.addView(Ui.tv(a, mf.format(c.getTime()), 12.5f, 0xFFFF3B30, true));
                int off = (c.get(Calendar.DAY_OF_WEEK) - c.getFirstDayOfWeek() + 7) % 7, days = c.getActualMaximum(Calendar.DAY_OF_MONTH);
                LinearLayout wr = null;
                for (int i = 0; i < off + days; i++) {
                    if (i % 7 == 0) { wr = new LinearLayout(a); box.addView(wr, new LinearLayout.LayoutParams(-1, a.dp(14))); }
                    TextView d = new TextView(a);
                    d.setGravity(Gravity.CENTER);
                    d.setTextSize(8.5f);
                    if (i >= off) {
                        Calendar x = (Calendar) c.clone(); x.set(Calendar.DAY_OF_MONTH, i - off + 1);
                        boolean td = iso.format(x.getTime()).equals(today);
                        d.setText(String.valueOf(i - off + 1));
                        d.setTextColor(td ? Color.WHITE : Theme.fg());
                        if (td) { GradientDrawable g = new GradientDrawable(); g.setShape(GradientDrawable.OVAL); g.setColor(0xFFFF3B30); d.setBackground(g); }
                    }
                    wr.addView(d, new LinearLayout.LayoutParams(0, -1, 1f));
                }
                box.setOnClickListener(v -> { cursor.set(Calendar.MONTH, m); cursor.set(Calendar.DAY_OF_MONTH, 1); mode = "Month"; refresh(); });
                row.addView(box, new LinearLayout.LayoutParams(0, -2, 1f));
            }
            col.addView(row);
        }
        sv.addView(col);
        content.addView(sv, new LinearLayout.LayoutParams(-1, -1));
    }

    // ---------- event popup ----------
    void editDialog(final Ev existing, String date) {
        a.dismissOverlays();
        a.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN);
        FrameLayout sc = a.scrim();
        final Ev e = existing != null ? existing : new Ev();
        if (existing == null) { e.id = System.nanoTime(); e.date = date; e.title = ""; }
        LinearLayout c = new LinearLayout(a);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setPadding(a.dp(16), a.dp(14), a.dp(16), a.dp(14));
        c.setBackground(Ui.rr(Theme.dark ? 0xFF2C2C2E : 0xFFF6F6F8, a.dp(20)));
        c.setClickable(true);
        c.setElevation(a.dp(20));
        TextView h = Ui.tv(a, existing == null ? "New Event" : "Edit Event", 15, Theme.fg(), true);
        h.setGravity(Gravity.CENTER);
        c.addView(h);
        final EditText title = new EditText(a);
        title.setHint("New Event");
        title.setText(e.title);
        title.setSingleLine(true);
        title.setTextSize(14);
        title.setTextColor(Theme.fg());
        title.setHintTextColor(Theme.sub());
        title.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        title.setBackground(Ui.rr(Theme.dark ? 0x2EFFFFFF : 0x12000000, a.dp(9)));
        title.setPadding(a.dp(12), 0, a.dp(12), 0);
        LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(-1, a.dp(36));
        tl.topMargin = a.dp(10);
        c.addView(title, tl);
        final TextView dateB = chip(e.date), timeB = chip(e.time.isEmpty() ? "All-day" : e.time);
        LinearLayout dr = new LinearLayout(a);
        dr.addView(dateB, new LinearLayout.LayoutParams(0, a.dp(34), 1f));
        LinearLayout.LayoutParams tbl = new LinearLayout.LayoutParams(0, a.dp(34), 1f);
        tbl.leftMargin = a.dp(8);
        dr.addView(timeB, tbl);
        LinearLayout.LayoutParams drl = new LinearLayout.LayoutParams(-1, -2);
        drl.topMargin = a.dp(8);
        c.addView(dr, drl);
        dateB.setOnClickListener(v -> {
            Calendar x = Calendar.getInstance();
            try { x.setTime(iso.parse(e.date)); } catch (Exception ex) { }
            MacDialog.date(a, x.get(Calendar.YEAR), x.get(Calendar.MONTH), x.get(Calendar.DAY_OF_MONTH), null,
                    (y, m, d) -> { e.date = String.format(Locale.US, "%04d-%02d-%02d", y, m + 1, d); dateB.setText(e.date); }, null);
        });
        timeB.setOnClickListener(v -> {
            int hh = 9, mm = 0;
            if (e.time.length() == 5) { hh = Integer.parseInt(e.time.substring(0, 2)); mm = Integer.parseInt(e.time.substring(3)); }
            MacDialog.time(a, hh, mm, L.t("All-day"), (hr, mi) -> { e.time = String.format(Locale.US, "%02d:%02d", hr, mi); timeB.setText(e.time); },
                    () -> { e.time = ""; timeB.setText("All-day"); });
        });
        LinearLayout calRow = new LinearLayout(a);
        calRow.setGravity(Gravity.CENTER);
        final View[] dots = new View[CALS.length];
        for (int i = 0; i < CALS.length; i++) {
            final int k = i;
            TextView t = Ui.tv(a, CALS[i], 11.5f, e.cal == i ? Color.WHITE : Theme.fg(), e.cal == i);
            t.setGravity(Gravity.CENTER);
            t.setBackground(Ui.rr(e.cal == i ? CAL_C[i] : (Theme.dark ? 0x2EFFFFFF : 0x12000000), a.dp(13)));
            t.setTag(i);
            t.setOnClickListener(v -> {
                e.cal = k;
                for (int j = 0; j < calRow.getChildCount(); j++) {
                    TextView o = (TextView) calRow.getChildAt(j);
                    boolean s = j == k;
                    o.setTextColor(s ? Color.WHITE : Theme.fg());
                    o.setBackground(Ui.rr(s ? CAL_C[j] : (Theme.dark ? 0x2EFFFFFF : 0x12000000), a.dp(13)));
                }
            });
            LinearLayout.LayoutParams cl = new LinearLayout.LayoutParams(0, a.dp(26), 1f);
            cl.leftMargin = i == 0 ? 0 : a.dp(5);
            calRow.addView(t, cl);
        }
        LinearLayout.LayoutParams crl = new LinearLayout.LayoutParams(-1, -2);
        crl.topMargin = a.dp(10);
        c.addView(calRow, crl);
        LinearLayout br = new LinearLayout(a);
        br.setGravity(Gravity.RIGHT);
        if (existing != null) {
            TextView del = chip("Delete");
            del.setTextColor(0xFFFF3B30);
            del.setOnClickListener(v -> { evs.remove(existing); save(); a.dismissOverlays(); refresh(); });
            br.addView(del, new LinearLayout.LayoutParams(a.dp(80), a.dp(32)));
        }
        TextView cancel = chip("Cancel");
        cancel.setOnClickListener(v -> a.dismissOverlays());
        LinearLayout.LayoutParams cnl = new LinearLayout.LayoutParams(a.dp(80), a.dp(32));
        cnl.leftMargin = a.dp(8);
        br.addView(cancel, cnl);
        TextView ok = Ui.tv(a, existing == null ? "Add" : "Save", 13, Color.WHITE, true);
        ok.setGravity(Gravity.CENTER);
        ok.setBackground(Ui.rr(Theme.accent, a.dp(16)));
        ok.setOnClickListener(v -> {
            String t = title.getText().toString().trim();
            e.title = t.isEmpty() ? "New Event" : t;
            if (existing == null) evs.add(e);
            save(); a.dismissOverlays();
            try { cursor.setTime(iso.parse(e.date)); } catch (Exception ex) { }
            refresh();
        });
        LinearLayout.LayoutParams okl = new LinearLayout.LayoutParams(a.dp(80), a.dp(32));
        okl.leftMargin = a.dp(8);
        br.addView(ok, okl);
        LinearLayout.LayoutParams brl = new LinearLayout.LayoutParams(-1, -2);
        brl.topMargin = a.dp(14);
        c.addView(br, brl);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(a.dp(320), -2, Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        lp.topMargin = a.mbH() + a.dp(8);
        sc.addView(c, lp);
        c.setAlpha(0); c.setScaleX(.96f); c.setScaleY(.96f);
        c.animate().alpha(1).scaleX(1).scaleY(1).setDuration(180).start();
    }

    TextView chip(String s) {
        TextView t = Ui.tv(a, s, 12.5f, Theme.fg(), false);
        t.setGravity(Gravity.CENTER);
        t.setBackground(Ui.rr(Theme.dark ? 0x2EFFFFFF : 0x12000000, a.dp(16)));
        return t;
    }
}
