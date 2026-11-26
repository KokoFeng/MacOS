package com.macos.launcher;

import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONObject;

/** Weather: city list on the left, big forecast on the right, like the macOS Tahoe app. Data: Open-Meteo. */
public class WeatherApp {
    static final int W = 0xFFFFFFFF, W70 = 0xB3FFFFFF, W55 = 0x8CFFFFFF;

    final MainActivity a;
    List<Wx.City> cities;
    int sel = 0;
    LinearLayout side, content;
    FrameLayout detailBg;
    EditText search;
    TextView unitBtn;
    boolean closed;

    public static void open(MainActivity a) { new WeatherApp(a); }

    WeatherApp(MainActivity act) {
        a = act;
        cities = Wx.cities(a);

        // ----- sidebar -----
        side = new LinearLayout(a);
        side.setOrientation(LinearLayout.VERTICAL);
        side.setPadding(a.dp(8), a.dp(6), a.dp(8), a.dp(8));
        ScrollView sv = new ScrollView(a);
        sv.setVerticalScrollBarEnabled(false);
        sv.addView(side);
        FrameLayout sideWrap = new FrameLayout(a);
        sideWrap.setBackgroundColor(0xFF3B4756);
        LinearLayout sideCol = new LinearLayout(a);
        sideCol.setOrientation(LinearLayout.VERTICAL);
        search = new EditText(a);
        search.setHint(L.t("Search for a city"));
        search.setHintTextColor(0x99FFFFFF);
        search.setTextColor(W);
        search.setTextSize(12.5f);
        search.setSingleLine(true);
        search.setInputType(InputType.TYPE_CLASS_TEXT);
        search.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        search.setPadding(a.dp(12), 0, a.dp(12), 0);
        search.setBackground(Ui.rr(0x33FFFFFF, a.dp(14)));
        search.setTypeface(Emoji.tf(a, false));
        search.setOnEditorActionListener((v, id, e) -> { runSearch(search.getText().toString().trim()); return true; });
        LinearLayout.LayoutParams sl = new LinearLayout.LayoutParams(-1, a.dp(30));
        sl.setMargins(a.dp(8), a.dp(8), a.dp(8), a.dp(2));
        sideCol.addView(search, sl);
        sideCol.addView(sv, new LinearLayout.LayoutParams(-1, 0, 1f));
        sideWrap.addView(sideCol, new FrameLayout.LayoutParams(-1, -1));

        // ----- detail -----
        detailBg = new FrameLayout(a);
        ScrollView dv = new ScrollView(a);
        dv.setVerticalScrollBarEnabled(false);
        content = new LinearLayout(a);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(a.dp(14), a.dp(8), a.dp(14), a.dp(16));
        dv.addView(content);
        detailBg.addView(dv, new FrameLayout.LayoutParams(-1, -1));

        LinearLayout body = new LinearLayout(a);
        body.addView(sideWrap, new LinearLayout.LayoutParams(a.dp(178), -1));
        body.addView(detailBg, new LinearLayout.LayoutParams(0, -1, 1f));

        // ----- toolbar: unit toggle -----
        LinearLayout tb = new LinearLayout(a);
        tb.setGravity(Gravity.CENTER_VERTICAL | Gravity.RIGHT);
        unitBtn = Ui.tv(a, Wx.fahr(a) ? "\u00b0F" : "\u00b0C", 13, Theme.fg(), true);
        unitBtn.setGravity(Gravity.CENTER);
        unitBtn.setBackground(Ui.rr(Theme.dark ? 0x2EFFFFFF : 0x14000000, a.dp(14)));
        unitBtn.setOnClickListener(v -> {
            boolean f = !Wx.fahr(a);
            a.prefs.edit().putBoolean("wx_f", f).apply();
            unitBtn.setText(f ? "\u00b0F" : "\u00b0C");
            refreshSide();
            showCity(sel);
        });
        tb.addView(unitBtn, new LinearLayout.LayoutParams(a.dp(38), a.dp(28)));

        MacWindow w = new MacWindow(a, "Weather", tb, null, body, 0xFF1D6FD1);
        a.openWindow(w, 700, 340);
        refreshSide();
        showCity(0);
        Wx.locate(a, () -> {
            if (closed) return;
            cities = Wx.cities(a);
            refreshSide();
            showCity(sel);
        });
    }

    // ===== sidebar =====
    void refreshSide() {
        side.removeAllViews();
        cities = Wx.cities(a);
        for (int i = 0; i < cities.size(); i++) {
            final int k = i;
            final Wx.City c = cities.get(i);
            JSONObject j = Wx.cached(a, c);
            int code = 3;
            boolean day = true;
            String t = "--", cond = "", hl = "";
            try {
                if (j != null) {
                    JSONObject cu = j.getJSONObject("current"), d = j.getJSONObject("daily");
                    code = cu.getInt("weather_code");
                    day = cu.getInt("is_day") == 1;
                    t = Wx.temp(cu.getDouble("temperature_2m"));
                    cond = L.t(Wx.cond(code, day));
                    hl = "H:" + Wx.temp(d.getJSONArray("temperature_2m_max").getDouble(0)) + "  L:" + Wx.temp(d.getJSONArray("temperature_2m_min").getDouble(0));
                }
            } catch (Exception e) { }
            FrameLayout card = new FrameLayout(a);
            int[] g = Wx.sky(code, day);
            GradientDrawable gd = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{g[0], g[1]});
            gd.setCornerRadius(a.dp(14));
            if (i == sel) gd.setStroke(a.dp(1.6f), 0xCCFFFFFF);
            card.setBackground(gd);
            card.setPadding(a.dp(10), a.dp(7), a.dp(10), a.dp(7));
            LinearLayout l = new LinearLayout(a);
            l.setOrientation(LinearLayout.VERTICAL);
            l.addView(Ui.tv(a, c.name, 13, W, true));
            TextView sub = Ui.tv(a, c.here ? "My Location" : "", 10, W70, false);
            l.addView(sub);
            TextView cd = Ui.tv(a, cond, 10, W, false);
            LinearLayout.LayoutParams cl = new LinearLayout.LayoutParams(-2, -2);
            cl.topMargin = a.dp(6);
            l.addView(cd, cl);
            card.addView(l, new FrameLayout.LayoutParams(-2, -2, Gravity.LEFT));
            LinearLayout r = new LinearLayout(a);
            r.setOrientation(LinearLayout.VERTICAL);
            r.setGravity(Gravity.RIGHT);
            TextView tt = Ui.tv(a, t, 24, W, false);
            r.addView(tt);
            LinearLayout.LayoutParams hlp = new LinearLayout.LayoutParams(-2, -2);
            hlp.topMargin = a.dp(2);
            r.addView(Ui.tv(a, hl, 9.5f, W, false), hlp);
            card.addView(r, new FrameLayout.LayoutParams(-2, -2, Gravity.RIGHT));
            card.setOnClickListener(v -> { sel = k; refreshSide(); showCity(k); });
            card.setOnLongClickListener(v -> {
                if (!c.here) a.confirm("Remove " + c.name + "?", () -> { Wx.removeCity(a, k); sel = 0; refreshSide(); showCity(0); });
                return true;
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
            lp.topMargin = a.dp(6);
            side.addView(card, lp);
        }
    }

    void runSearch(final String q) {
        if (q.isEmpty()) { refreshSide(); return; }
        side.removeAllViews();
        side.addView(Ui.tv(a, "Loading\u2026", 12, W70, false));
        Wx.search(q, res -> {
            if (closed) return;
            side.removeAllViews();
            if (res.isEmpty()) { side.addView(Ui.tv(a, "No Results", 12, W70, false)); return; }
            for (final Wx.City c : res) {
                TextView t = Ui.tv(a, c.name, 12.5f, W, false);
                t.setPadding(a.dp(8), a.dp(8), a.dp(8), a.dp(8));
                t.setOnClickListener(v -> {
                    Wx.addCity(a, c);
                    search.setText("");
                    refreshSide();
                    sel = Wx.cities(a).size() - 1;
                    refreshSide();
                    showCity(sel);
                });
                side.addView(t);
            }
        }, a);
    }

    // ===== detail =====
    void showCity(final int i) {
        if (i >= cities.size()) return;
        final Wx.City c = cities.get(i);
        JSONObject j = Wx.cached(a, c);
        if (j != null) render(c, j, Wx.cachedAir(a, c)); else renderLoading();
        Wx.fetch(a, c, false, (f, aq) -> {
            if (closed || sel != i) return;
            if (f != null) render(c, f, aq);
            refreshSideKeep();
        });
    }

    void refreshSideKeep() {
        if (search.getText().length() == 0) refreshSide();
    }

    void renderLoading() {
        content.removeAllViews();
        applySky(3, true);
        TextView t = Ui.tv(a, "Loading\u2026", 14, W, false);
        t.setPadding(0, a.dp(120), 0, 0);
        t.setGravity(Gravity.CENTER);
        content.addView(t, new LinearLayout.LayoutParams(-1, -2));
    }

    void applySky(int code, boolean day) {
        int[] g = Wx.sky(code, day);
        detailBg.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{g[0], g[1]}));
    }

    LinearLayout card(String title) {
        LinearLayout c = new LinearLayout(a);
        c.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable gd = new GradientDrawable();
        gd.setColor(0x33FFFFFF);
        gd.setCornerRadius(a.dp(16));
        gd.setStroke(1, 0x22FFFFFF);
        c.setBackground(gd);
        c.setPadding(a.dp(12), a.dp(10), a.dp(12), a.dp(10));
        if (title != null) {
            TextView t = Ui.tv(a, title.toUpperCase(L.locale()), 9.5f, W70, true);
            t.setPadding(0, 0, 0, a.dp(6));
            c.addView(t);
        }
        return c;
    }

    LinearLayout.LayoutParams gap(int top) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2);
        p.topMargin = a.dp(top);
        return p;
    }

    void pair(View l, View r) {
        LinearLayout row = new LinearLayout(a);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -1, 1f);
        lp.rightMargin = a.dp(5);
        LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(0, -1, 1f);
        rp.leftMargin = a.dp(5);
        row.addView(l, lp);
        row.addView(r, rp);
        content.addView(row, gap(10));
    }

    String hourLabel(String iso) {
        try {
            Date d = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm", java.util.Locale.US).parse(iso);
            return new SimpleDateFormat("ha", L.locale()).format(d);
        } catch (Exception e) { return iso; }
    }

    String clock(String iso) {
        try {
            Date d = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm", java.util.Locale.US).parse(iso);
            return new SimpleDateFormat("h:mma", L.locale()).format(d);
        } catch (Exception e) { return iso; }
    }

    void render(Wx.City c, JSONObject j, JSONObject air) {
        try {
            content.removeAllViews();
            final boolean f = Wx.fahr(a);
            JSONObject cu = j.getJSONObject("current"), d = j.getJSONObject("daily"), h = j.getJSONObject("hourly");
            int code = cu.getInt("weather_code");
            boolean day = cu.getInt("is_day") == 1;
            applySky(code, day);

            // header
            LinearLayout hd = new LinearLayout(a);
            hd.setOrientation(LinearLayout.VERTICAL);
            hd.setGravity(Gravity.CENTER_HORIZONTAL);
            hd.setPadding(0, a.dp(6), 0, a.dp(4));
            if (c.here) hd.addView(Ui.tv(a, L.t("My Location").toUpperCase(L.locale()), 9.5f, W70, true));
            hd.addView(Ui.tv(a, c.name, 22, W, false));
            hd.addView(Ui.tv(a, Wx.temp(cu.getDouble("temperature_2m")), 62, W, false));
            hd.addView(Ui.tv(a, Wx.cond(code, day), 14, W, true));
            hd.addView(Ui.tv(a, "H:" + Wx.temp(d.getJSONArray("temperature_2m_max").getDouble(0)) + "  L:" + Wx.temp(d.getJSONArray("temperature_2m_min").getDouble(0)), 13, W, true));
            content.addView(hd, new LinearLayout.LayoutParams(-1, -2));

            // hourly
            JSONArray ht = h.getJSONArray("time"), htmp = h.getJSONArray("temperature_2m"), hcode = h.getJSONArray("weather_code"),
                    hpp = h.getJSONArray("precipitation_probability"), hday = h.getJSONArray("is_day");
            String nowH = cu.getString("time").substring(0, 13) + ":00";
            int start = 0;
            for (int i = 0; i < ht.length(); i++) if (ht.getString(i).equals(nowH)) { start = i; break; }
            String rainAt = null;
            for (int i = start; i < Math.min(ht.length(), start + 24); i++) if (hpp.optInt(i) >= 40) { rainAt = hourLabel(ht.getString(i)); break; }
            String gust = Math.round(cu.getDouble("wind_gusts_10m")) + (f ? " mph" : " km/h");
            String sum = (rainAt != null ? L.t(Wx.cond(code, day).equals("Rain") ? "Rain" : "Rain") + " \u2022 " + rainAt + ". " : L.t(Wx.cond(code, day)) + ". ") + L.t("Wind") + " " + gust + ".";
            LinearLayout hc = card(null);
            TextView st = Ui.tv(a, sum, 11, W, false);
            st.setPadding(0, 0, 0, a.dp(6));
            hc.addView(st);
            View dv = new View(a);
            dv.setBackgroundColor(0x33FFFFFF);
            hc.addView(dv, new LinearLayout.LayoutParams(-1, 1));
            HorizontalScrollView hs = new HorizontalScrollView(a);
            hs.setHorizontalScrollBarEnabled(false);
            LinearLayout hr = new LinearLayout(a);
            JSONArray sr = d.getJSONArray("sunrise"), ss = d.getJSONArray("sunset");
            for (int i = start; i < Math.min(ht.length(), start + 25); i++) {
                String iso = ht.getString(i);
                // sunrise / sunset marker falling inside the previous hour
                for (int dd = 0; dd < 3; dd++) {
                    for (int sx = 0; sx < 2; sx++) {
                        String ev = sx == 0 ? sr.optString(dd, "") : ss.optString(dd, "");
                        if (ev.length() >= 13 && i > start && ev.substring(0, 13).equals(ht.getString(i - 1).substring(0, 13)))
                            hr.addView(hourCol(clock(ev), null, sx == 0 ? 2 : 1, sx == 0 ? "Sunrise" : "Sunset", 0, false));
                    }
                }
                hr.addView(hourCol(i == start ? L.t("Now") : hourLabel(iso), null, 0, Wx.temp(htmp.getDouble(i)), hcode.getInt(i), hday.getInt(i) == 1,
                        hpp.optInt(i)));
            }
            hs.addView(hr);
            hc.addView(hs, new LinearLayout.LayoutParams(-1, -2));
            content.addView(hc, gap(8));

            // 10-day
            LinearLayout dc = card("10-Day Forecast");
            JSONArray dt = d.getJSONArray("time"), dmax = d.getJSONArray("temperature_2m_max"), dmin = d.getJSONArray("temperature_2m_min"),
                    dcode = d.getJSONArray("weather_code"), dpp = d.getJSONArray("precipitation_probability_max");
            double gmin = 1e9, gmax = -1e9;
            for (int i = 0; i < dt.length(); i++) { gmin = Math.min(gmin, dmin.getDouble(i)); gmax = Math.max(gmax, dmax.getDouble(i)); }
            for (int i = 0; i < dt.length(); i++) {
                LinearLayout r = new LinearLayout(a);
                r.setGravity(Gravity.CENTER_VERTICAL);
                r.setPadding(0, a.dp(5), 0, a.dp(5));
                String dn = i == 0 ? L.t("Today") : dayName(dt.getString(i));
                TextView dtv = Ui.tv(a, dn, 12.5f, W, true);
                r.addView(dtv, new LinearLayout.LayoutParams(a.dp(58), -2));
                LinearLayout ic = new LinearLayout(a);
                ic.setOrientation(LinearLayout.VERTICAL);
                ic.setGravity(Gravity.CENTER_HORIZONTAL);
                ic.addView(new Wx.Icon(a, dcode.getInt(i), true), new LinearLayout.LayoutParams(a.dp(22), a.dp(22)));
                if (dpp.optInt(i) >= 30) ic.addView(Ui.tv(a, dpp.optInt(i) + "%", 8.5f, 0xFF7FD3FF, true));
                r.addView(ic, new LinearLayout.LayoutParams(a.dp(36), -2));
                TextView lo = Ui.tv(a, Wx.temp(dmin.getDouble(i)), 12.5f, W55, true);
                lo.setGravity(Gravity.RIGHT);
                r.addView(lo, new LinearLayout.LayoutParams(a.dp(36), -2));
                RangeBar rb = new RangeBar(a, dmin.getDouble(i), dmax.getDouble(i), gmin, gmax, f, i == 0 ? cu.getDouble("temperature_2m") : Double.NaN);
                LinearLayout.LayoutParams rl = new LinearLayout.LayoutParams(0, a.dp(14), 1f);
                rl.setMargins(a.dp(6), 0, a.dp(6), 0);
                r.addView(rb, rl);
                r.addView(Ui.tv(a, Wx.temp(dmax.getDouble(i)), 12.5f, W, true), new LinearLayout.LayoutParams(a.dp(36), -2));
                dc.addView(r);
                if (i < dt.length() - 1) {
                    View sp = new View(a);
                    sp.setBackgroundColor(0x22FFFFFF);
                    dc.addView(sp, new LinearLayout.LayoutParams(-1, 1));
                }
            }
            content.addView(dc, gap(10));

            // air quality + wind
            LinearLayout aqc = card("Air Quality");
            int aqi = -1;
            try { if (air != null) aqi = (int) Math.round(air.getJSONObject("current").getDouble("us_aqi")); } catch (Exception e) { }
            if (aqi >= 0) {
                aqc.addView(Ui.tv(a, String.valueOf(aqi), 24, W, false));
                aqc.addView(Ui.tv(a, aqi <= 50 ? "Good" : aqi <= 100 ? "Moderate" : aqi <= 150 ? "Unhealthy for Sensitive Groups" : "Unhealthy", 12, W, true));
                aqc.addView(new Marker(a, Math.min(1f, aqi / 300f), new int[]{0xFF4CD964, 0xFFFFD60A, 0xFFFF9500, 0xFFFF3B30, 0xFFAF52DE}), new LinearLayout.LayoutParams(-1, a.dp(10)));
            } else aqc.addView(Ui.tv(a, "--", 24, W, false));
            double ws = cu.getDouble("wind_speed_10m"), wd = cu.getDouble("wind_direction_10m");
            LinearLayout wc = card("Wind");
            LinearLayout wr = new LinearLayout(a);
            wr.setGravity(Gravity.CENTER_VERTICAL);
            LinearLayout wt = new LinearLayout(a);
            wt.setOrientation(LinearLayout.VERTICAL);
            String un = f ? " mph" : " km/h";
            wt.addView(Ui.tv(a, Math.round(ws) + un, 13, W, true));
            wt.addView(Ui.tv(a, L.t("Gusts") + " " + Math.round(cu.getDouble("wind_gusts_10m")) + un, 10.5f, W70, false));
            wt.addView(Ui.tv(a, Math.round(wd) + "\u00b0 " + Wx.compass(wd), 10.5f, W70, false));
            wr.addView(wt, new LinearLayout.LayoutParams(0, -2, 1f));
            wr.addView(new Compass(a, (float) wd, Math.round(ws) + "", f ? "mph" : "km/h"), new LinearLayout.LayoutParams(a.dp(64), a.dp(64)));
            wc.addView(wr);
            pair(aqc, wc);

            // UV + sunset
            double uv = cu.optDouble("uv_index", 0);
            LinearLayout uc = card("UV Index");
            uc.addView(Ui.tv(a, String.valueOf(Math.round(uv)), 24, W, false));
            uc.addView(Ui.tv(a, uv < 3 ? "Low" : uv < 6 ? "Moderate" : uv < 8 ? "High" : uv < 11 ? "Very High" : "Extreme", 12, W, true));
            uc.addView(new Marker(a, Math.min(1f, (float) uv / 12f), new int[]{0xFF4CD964, 0xFFFFD60A, 0xFFFF9500, 0xFFFF3B30, 0xFFAF52DE}), new LinearLayout.LayoutParams(-1, a.dp(10)));
            LinearLayout sc = card("Sunset");
            sc.addView(Ui.tv(a, clock(ss.getString(0)), 22, W, false));
            sc.addView(Ui.tv(a, L.t("Sunrise") + ": " + clock(sr.getString(0)), 11, W70, false));
            pair(uc, sc);

            // feels like + precipitation
            LinearLayout fc = card("Feels Like");
            fc.addView(Ui.tv(a, Wx.temp(cu.getDouble("apparent_temperature")), 24, W, false));
            LinearLayout pc = card("Precipitation");
            double mm = d.getJSONArray("precipitation_sum").optDouble(0, 0);
            pc.addView(Ui.tv(a, (f ? String.format(java.util.Locale.US, "%.2f in", mm / 25.4) : String.format(java.util.Locale.US, "%.1f mm", mm)), 22, W, false));
            pc.addView(Ui.tv(a, L.t("Today") + " \u2022 " + dpp.optInt(0) + "%", 11, W70, false));
            pair(fc, pc);

            // humidity + visibility + pressure
            LinearLayout hu = card("Humidity");
            hu.addView(Ui.tv(a, Math.round(cu.getDouble("relative_humidity_2m")) + "%", 24, W, false));
            LinearLayout vi = card("Visibility");
            double vis = cu.optDouble("visibility", 10000) / 1000.0;
            vi.addView(Ui.tv(a, f ? Math.round(vis * 0.621) + " mi" : Math.round(vis) + " km", 22, W, false));
            pair(hu, vi);
            LinearLayout pr = card("Pressure");
            pr.addView(Ui.tv(a, Math.round(cu.getDouble("pressure_msl")) + " hPa", 22, W, false));
            LinearLayout sp = new LinearLayout(a);
            pair(pr, sp);
            content.addView(new View(a), new LinearLayout.LayoutParams(-1, a.dp(8)));
        } catch (Exception e) {
            content.removeAllViews();
            TextView t = Ui.tv(a, "No connection", 14, W, false);
            t.setPadding(0, a.dp(120), 0, 0);
            t.setGravity(Gravity.CENTER);
            content.addView(t, new LinearLayout.LayoutParams(-1, -2));
        }
    }

    String dayName(String ymd) {
        try { return new SimpleDateFormat("EEE", L.locale()).format(new SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).parse(ymd)); }
        catch (Exception e) { return ymd; }
    }

    LinearLayout hourCol(String time, Object unused, int special, String temp, int code, boolean day) { return hourCol(time, unused, special, temp, code, day, 0); }

    LinearLayout hourCol(String time, Object unused, int special, String temp, int code, boolean day, int pp) {
        LinearLayout c = new LinearLayout(a);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setGravity(Gravity.CENTER_HORIZONTAL);
        c.setPadding(a.dp(8), a.dp(8), a.dp(8), a.dp(2));
        c.addView(Ui.tv(a, time, 10.5f, W, true));
        Wx.Icon ic = new Wx.Icon(a, code, day);
        ic.special = special;
        LinearLayout.LayoutParams il = new LinearLayout.LayoutParams(a.dp(26), a.dp(26));
        il.topMargin = a.dp(6);
        c.addView(ic, il);
        TextView pt = Ui.tv(a, pp >= 30 && special == 0 ? pp + "%" : " ", 8.5f, 0xFF7FD3FF, true);
        c.addView(pt);
        c.addView(Ui.tv(a, special != 0 ? L.t(temp) : temp, 12.5f, W, true));
        return c;
    }

    // ===== custom views =====
    static int tempColor(double c) {
        if (c < 5) return 0xFF64B5F6;
        if (c < 12) return 0xFF4DD0E1;
        if (c < 19) return 0xFF9CCC65;
        if (c < 25) return 0xFFFFD54F;
        if (c < 31) return 0xFFFF9800;
        return 0xFFFF5722;
    }

    static class RangeBar extends View {
        final double lo, hi, gl, gh, cur;
        final boolean f;
        final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);

        RangeBar(android.content.Context c, double lo, double hi, double gl, double gh, boolean f, double cur) {
            super(c); this.lo = lo; this.hi = hi; this.gl = gl; this.gh = gh; this.f = f; this.cur = cur;
        }

        double toC(double v) { return f ? (v - 32) * 5 / 9 : v; }

        @Override protected void onDraw(Canvas cv) {
            float w = getWidth(), h = getHeight(), th = h * .38f, cy = h / 2f;
            p.setColor(0x40FFFFFF);
            cv.drawRoundRect(new RectF(0, cy - th / 2, w, cy + th / 2), th, th, p);
            double span = Math.max(1, gh - gl);
            float x1 = (float) ((lo - gl) / span * w), x2 = (float) ((hi - gl) / span * w);
            if (x2 - x1 < th) x2 = x1 + th;
            p.setShader(new LinearGradient(x1, 0, x2, 0, tempColor(toC(lo)), tempColor(toC(hi)), Shader.TileMode.CLAMP));
            cv.drawRoundRect(new RectF(x1, cy - th / 2, x2, cy + th / 2), th, th, p);
            p.setShader(null);
            if (!Double.isNaN(cur)) {
                float cx = (float) ((cur - gl) / span * w);
                p.setColor(0xFFFFFFFF);
                cv.drawCircle(cx, cy, th * .78f, p);
                p.setColor(0x55000000);
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(1f);
                cv.drawCircle(cx, cy, th * .78f, p);
                p.setStyle(Paint.Style.FILL);
            }
        }
    }

    static class Marker extends View {
        final float pos;
        final int[] cols;
        final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);

        Marker(android.content.Context c, float pos, int[] cols) { super(c); this.pos = pos; this.cols = cols; }

        @Override protected void onDraw(Canvas cv) {
            float w = getWidth(), h = getHeight(), th = h * .5f, cy = h / 2f;
            p.setShader(new LinearGradient(0, 0, w, 0, cols, null, Shader.TileMode.CLAMP));
            cv.drawRoundRect(new RectF(0, cy - th / 2, w, cy + th / 2), th, th, p);
            p.setShader(null);
            float x = Math.max(h / 2, Math.min(w - h / 2, pos * w));
            p.setColor(0xFFFFFFFF);
            cv.drawCircle(x, cy, h * .46f, p);
            p.setColor(0x55000000);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(1f);
            cv.drawCircle(x, cy, h * .46f, p);
            p.setStyle(Paint.Style.FILL);
        }
    }

    static class Compass extends View {
        final float deg;
        final String speed, unit;
        final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);

        Compass(android.content.Context c, float deg, String speed, String unit) { super(c); this.deg = deg; this.speed = speed; this.unit = unit; }

        @Override protected void onDraw(Canvas cv) {
            float s = Math.min(getWidth(), getHeight()), cx = getWidth() / 2f, cy = getHeight() / 2f, r = s / 2f - 3;
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(s * .045f);
            p.setColor(0x44FFFFFF);
            cv.drawCircle(cx, cy, r, p);
            p.setStrokeWidth(1.5f);
            p.setColor(0xAAFFFFFF);
            for (int i = 0; i < 72; i++) {
                double an = Math.PI * 2 * i / 72;
                float a0 = i % 18 == 0 ? r * .8f : r * .88f;
                cv.drawLine((float) (cx + Math.cos(an) * a0), (float) (cy + Math.sin(an) * a0), (float) (cx + Math.cos(an) * r * .95f), (float) (cy + Math.sin(an) * r * .95f), p);
            }
            p.setStyle(Paint.Style.FILL);
            p.setTextAlign(Paint.Align.CENTER);
            p.setTextSize(s * .15f);
            p.setColor(0xFFFFFFFF);
            p.setTypeface(Emoji.font(true));
            String[] lb = {"N", "E", "S", "W"};
            for (int i = 0; i < 4; i++) {
                double an = Math.PI / 2 * i - Math.PI / 2;
                cv.drawText(lb[i], (float) (cx + Math.cos(an) * r * .62f), (float) (cy + Math.sin(an) * r * .62f) + s * .05f, p);
            }
            p.setTextSize(s * .22f);
            cv.drawText(speed, cx, cy + s * .04f, p);
            p.setTextSize(s * .1f);
            p.setColor(0xB3FFFFFF);
            cv.drawText(unit, cx, cy + s * .16f, p);
            cv.save();
            cv.rotate(deg, cx, cy);
            p.setColor(0xFFFFFFFF);
            cv.drawCircle(cx, cy - r * .93f, s * .045f, p);
            p.setStrokeWidth(2.5f);
            p.setStyle(Paint.Style.STROKE);
            cv.drawLine(cx, cy - r * .88f, cx, cy - r * .76f, p);
            cv.restore();
        }
    }
}
