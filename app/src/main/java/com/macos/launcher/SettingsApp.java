package com.macos.launcher;

import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Outline;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.provider.Settings;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class SettingsApp {
    static final int M = MainActivity.MATCH, W = MainActivity.WRAP;
    static final Object[][] SIDE = {
        {"Wi-Fi", IconView.WIFI, 0xFF0A84FF}, {"Bluetooth", IconView.BT, 0xFF0A84FF}, {"Battery", IconView.BATT, 0xFF30D158},
        {"General", -1, 0xFF8E8E93}, {"Accessibility", -1, 0xFF0A84FF}, {"Appearance", IconView.CONTRAST, 0xFF1C1C1E},
        {"Desktop & Dock", IconView.STAGE, 0xFF0A84FF}, {"Displays", -1, 0xFF0A84FF}, {"Sound", -1, 0xFFFF375F},
        {"Wallpaper", -1, 0xFF32ADE6}, {"Windows", -1, 0xFFFF5F57}, {"Notifications", -1, 0xFFFF3B30},
        {"Trackpad", -1, 0xFF8E8E93}, {"Launcher", -1, 0xFF8E5CF0}};
    static final int[] SWATCH = {0xFFFF5F57, 0xFFFEBC2E, 0xFF28C840, 0xFF0A84FF, 0xFFBF5AF2, 0xFFFF375F, 0xFFFF9F0A, 0xFF30D158, 0xFF8E8E93, 0xFFFFFFFF};

    final MainActivity a;
    LinearLayout detail;
    TextView toolTitle;
    final List<View> rows = new ArrayList<>();
    final List<String> names = new ArrayList<>();
    String cur = "";

    public static void open(MainActivity a, String pane) {
        MacWindow w = a.findWindow("System Settings");
        if (w != null) {
            w.restore();
            a.focusWindow(w);
            if (pane != null) ((SettingsApp) w.getTag()).show(pane);
            return;
        }
        new SettingsApp(a, pane == null ? "Appearance" : pane);
    }

    SettingsApp(MainActivity act, String pane) {
        a = act;
        toolTitle = Ui.tv(a, "", 14, Theme.fg(), true);
        toolTitle.setPadding(a.dp(8), 0, 0, 0);

        LinearLayout side = new LinearLayout(a);
        side.setOrientation(LinearLayout.VERTICAL);
        side.setPadding(a.dp(10), a.dp(2), a.dp(10), a.dp(10));
        final EditText search = new EditText(a);
        search.setHint("Search");
        search.setSingleLine(true);
        search.setTextSize(12.5f);
        search.setTextColor(Theme.fg());
        search.setHintTextColor(Theme.sub());
        search.setPadding(a.dp(12), 0, a.dp(12), 0);
        search.setBackground(Ui.rr(Theme.dark ? 0x2EFFFFFF : 0x14000000, a.dp(10)));
        side.addView(search, new LinearLayout.LayoutParams(M, a.dp(30)));
        LinearLayout prof = new LinearLayout(a);
        prof.setGravity(Gravity.CENTER_VERTICAL);
        prof.setPadding(a.dp(4), a.dp(12), 0, a.dp(8));
        TextView av = Ui.tv(a, "M", 15, 0xFFFFFFFF, true);
        av.setGravity(Gravity.CENTER);
        GradientDrawable ag = new GradientDrawable();
        ag.setShape(GradientDrawable.OVAL);
        ag.setColors(new int[]{0xFF7DB7F2, 0xFF0A47D6});
        av.setBackground(ag);
        prof.addView(av, new LinearLayout.LayoutParams(a.dp(34), a.dp(34)));
        LinearLayout pt = new LinearLayout(a);
        pt.setOrientation(LinearLayout.VERTICAL);
        pt.setPadding(a.dp(10), 0, 0, 0);
        pt.addView(Ui.tv(a, "macOS Launcher", 13, Theme.fg(), true));
        pt.addView(Ui.tv(a, "Pengaturan", 11, Theme.sub(), false));
        prof.addView(pt);
        side.addView(prof);
        for (final Object[] s : SIDE) {
            final String n = (String) s[0];
            int ic = (Integer) s[1];
            LinearLayout r = new LinearLayout(a);
            r.setGravity(Gravity.CENTER_VERTICAL);
            r.setPadding(a.dp(6), a.dp(5), a.dp(6), a.dp(5));
            FrameLayout sq = new FrameLayout(a);
            sq.setBackground(Ui.rr((Integer) s[2], a.dp(7)));
            if (ic >= 0) sq.addView(new IconView(a, ic, 0xFFFFFFFF), new FrameLayout.LayoutParams(a.dp(16), a.dp(16), Gravity.CENTER));
            else {
                TextView l = Ui.tv(a, n.substring(0, 1), 12, 0xFFFFFFFF, true);
                l.setGravity(Gravity.CENTER);
                sq.addView(l, new FrameLayout.LayoutParams(M, M));
            }
            r.addView(sq, new LinearLayout.LayoutParams(a.dp(24), a.dp(24)));
            TextView t = Ui.tv(a, n, 13, Theme.fg(), false);
            t.setPadding(a.dp(9), 0, 0, 0);
            t.setSingleLine(true);
            t.setTag("label");
            r.addView(t);
            r.setTag(n);
            r.setOnClickListener(v -> show(n));
            rows.add(r);
            names.add(n);
            side.addView(r);
        }
        search.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int x, int y, int z) {}
            public void onTextChanged(CharSequence s, int x, int y, int z) {}
            public void afterTextChanged(Editable e) {
                String q = e.toString().toLowerCase(Locale.ROOT).trim();
                for (int i = 0; i < rows.size(); i++)
                    rows.get(i).setVisibility(q.isEmpty() || names.get(i).toLowerCase(Locale.ROOT).contains(q) ? View.VISIBLE : View.GONE);
            }
        });
        ScrollView sv = new ScrollView(a);
        sv.setBackgroundColor(Theme.side());
        sv.setVerticalScrollBarEnabled(false);
        sv.addView(side);

        detail = new LinearLayout(a);
        detail.setOrientation(LinearLayout.VERTICAL);
        detail.setPadding(a.dp(16), a.dp(6), a.dp(16), a.dp(16));
        ScrollView dv = new ScrollView(a);
        dv.setBackgroundColor(Theme.pane());
        dv.setVerticalScrollBarEnabled(false);
        dv.addView(detail);

        LinearLayout body = new LinearLayout(a);
        body.addView(sv, new LinearLayout.LayoutParams(a.dp(190), M));
        body.addView(dv, new LinearLayout.LayoutParams(0, M, 1f));
        MacWindow w = new MacWindow(a, "System Settings", toolTitle, null, body);
        w.setTag(this);
        a.openWindow(w, 640, 330);
        show(pane);
    }

    // ---------- helpers ----------
    LinearLayout card() {
        LinearLayout c = new LinearLayout(a);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setBackground(Ui.rr(Theme.card(), a.dp(12)));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(M, W);
        lp.bottomMargin = a.dp(12);
        detail.addView(c, lp);
        return c;
    }

    void row(LinearLayout card, String label, String sub, View right) {
        if (card.getChildCount() > 0) {
            View s = new View(a);
            s.setBackgroundColor(Theme.sep());
            LinearLayout.LayoutParams sl = new LinearLayout.LayoutParams(M, 1);
            sl.leftMargin = a.dp(14);
            card.addView(s, sl);
        }
        LinearLayout r = new LinearLayout(a);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setPadding(a.dp(14), a.dp(10), a.dp(14), a.dp(10));
        LinearLayout tx = new LinearLayout(a);
        tx.setOrientation(LinearLayout.VERTICAL);
        tx.addView(Ui.tv(a, label, 13, Theme.fg(), false));
        if (sub != null) tx.addView(Ui.tv(a, sub, 11, Theme.sub(), false));
        r.addView(tx, new LinearLayout.LayoutParams(0, W, 1f));
        if (right != null) r.addView(right);
        card.addView(r);
    }

    TextView button(String s, final Runnable r) {
        TextView t = Ui.tv(a, s, 12.5f, 0xFFFFFFFF, true);
        t.setPadding(a.dp(14), a.dp(5), a.dp(14), a.dp(5));
        t.setBackground(Ui.rr(Theme.accent, a.dp(12)));
        t.setOnClickListener(v -> r.run());
        return t;
    }

    TextView value(String s) { return Ui.tv(a, s, 12.5f, Theme.sub(), false); }

    View choice(String label, View thumb, boolean sel, final Runnable r) {
        LinearLayout c = new LinearLayout(a);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setGravity(Gravity.CENTER_HORIZONTAL);
        FrameLayout ring = new FrameLayout(a);
        ring.setPadding(a.dp(2), a.dp(2), a.dp(2), a.dp(2));
        ring.setBackground(Ui.rr(sel ? Theme.accent : 0, a.dp(10)));
        ring.addView(thumb, new FrameLayout.LayoutParams(a.dp(58), a.dp(40)));
        c.addView(ring);
        TextView t = Ui.tv(a, label, 11, Theme.fg(), false);
        t.setPadding(0, a.dp(3), 0, 0);
        c.addView(t);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(W, W);
        lp.leftMargin = a.dp(10);
        c.setLayoutParams(lp);
        c.setOnClickListener(v -> r.run());
        return c;
    }

    View gradThumb(int c1, int c2) {
        View v = new View(a);
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, new int[]{c1, c2});
        g.setCornerRadius(a.dp(8));
        v.setBackground(g);
        return v;
    }

    void pref(String k, String v, String pane) { a.prefs.edit().putString(k, v).apply(); a.restartWith("Settings:" + pane); }

    // ---------- panes ----------
    void show(String name) {
        cur = name;
        toolTitle.setText(name);
        detail.removeAllViews();
        for (View r : rows) {
            boolean sel = r.getTag().equals(name);
            r.setBackground(sel ? Ui.rr(Theme.accent, a.dp(8)) : null);
            ((TextView) r.findViewWithTag("label")).setTextColor(sel ? 0xFFFFFFFF : Theme.fg());
        }
        switch (name) {
            case "Appearance": appearance(); break;
            case "Wallpaper": wallpaper(); break;
            case "Desktop & Dock": dock(); break;
            case "Windows": windows(); break;
            case "Launcher": launcher(); break;
            case "Notifications": notifications(); break;
            case "Trackpad": trackpad(); break;
            case "General": general(); break;
            case "Wi-Fi": shortcut(name, Settings.ACTION_WIFI_SETTINGS); break;
            case "Bluetooth": shortcut(name, Settings.ACTION_BLUETOOTH_SETTINGS); break;
            case "Battery": shortcut(name, Intent.ACTION_POWER_USAGE_SUMMARY); break;
            case "Accessibility": shortcut(name, Settings.ACTION_ACCESSIBILITY_SETTINGS); break;
            case "Displays": shortcut(name, Settings.ACTION_DISPLAY_SETTINGS); break;
            case "Sound": shortcut(name, Settings.ACTION_SOUND_SETTINGS); break;
            default: appearance();
        }
    }

    void shortcut(final String n, final String action) {
        LinearLayout c = card();
        row(c, "Pengaturan sistem Android", "Buka halaman " + n + " bawaan perangkat", button("Buka", () -> a.startExternal(new Intent(action))));
    }

    void general() {
        LinearLayout c = card();
        row(c, "Name", null, value(Build.MODEL));
        row(c, "Manufacturer", null, value(Build.MANUFACTURER));
        row(c, "Android", null, value(Build.VERSION.RELEASE));
        row(c, "Launcher", null, value("macOS Tahoe Launcher 2.0"));
    }

    void appearance() {
        LinearLayout c = card();
        LinearLayout ch = new LinearLayout(a);
        for (final String n : new String[]{"Auto", "Light", "Dark"}) {
            View t = n.equals("Light") ? gradThumb(0xFFEAF2FF, 0xFF9CC3F5) : n.equals("Dark") ? gradThumb(0xFF3A3A44, 0xFF15151A) : gradThumb(0xFFB5D2F7, 0xFF2A2A33);
            ch.addView(choice(n, t, Theme.appearance.equals(n), () -> pref("appearance", n, "Appearance")));
        }
        row(c, "Appearance", null, ch);

        LinearLayout g = new LinearLayout(a);
        for (final String n : new String[]{"Clear", "Tinted"}) {
            FrameLayout f = new FrameLayout(a);
            f.addView(gradThumb(0xFF6FA8F5, 0xFF2F6FE0), new FrameLayout.LayoutParams(M, M));
            View pill = new View(a);
            pill.setBackground(Ui.rr(n.equals("Clear") ? 0x44FFFFFF : 0xAAFFFFFF, a.dp(7)));
            FrameLayout.LayoutParams pl = new FrameLayout.LayoutParams(a.dp(40), a.dp(18), Gravity.CENTER);
            f.addView(pill, pl);
            g.addView(choice(n, f, (Theme.glassClear ? "Clear" : "Tinted").equals(n), () -> pref("glass", n, "Appearance")));
        }
        row(c, "Liquid Glass", "Pilih tampilan Liquid Glass", g);

        LinearLayout cr = new LinearLayout(a);
        cr.setGravity(Gravity.CENTER_VERTICAL);
        int[] cols = {0, 0xFF0A84FF, 0xFFBF5AF2, 0xFFFF375F, 0xFFFF453A, 0xFFFF9F0A, 0xFFFFD60A, 0xFF30D158, 0xFF8E8E93};
        for (final int col : cols) {
            GradientDrawable d = new GradientDrawable();
            d.setShape(GradientDrawable.OVAL);
            if (col == 0) {
                d.setGradientType(GradientDrawable.SWEEP_GRADIENT);
                d.setColors(new int[]{0xFFFF453A, 0xFFFFD60A, 0xFF30D158, 0xFF0A84FF, 0xFFBF5AF2, 0xFFFF453A});
            } else d.setColor(col);
            boolean sel = col == 0 ? Theme.accent == 0xFF0A84FF : Theme.accent == col;
            if (sel) d.setStroke(a.dp(2), Theme.fg());
            View v = new View(a);
            v.setBackground(d);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(a.dp(20), a.dp(20));
            lp.leftMargin = a.dp(7);
            v.setOnClickListener(x -> { a.prefs.edit().putInt("accent", col == 0 ? 0xFF0A84FF : col).apply(); a.restartWith("Settings:Appearance"); });
            cr.addView(v, lp);
        }
        row(c, "Color", null, cr);

        LinearLayout is = new LinearLayout(a);
        for (final String n : new String[]{"Default", "Dark", "Clear", "Tinted"}) {
            ImageView iv = new ImageView(a);
            iv.setImageResource(a.res("mac_system_settings"));
            a.styleIcon(iv, n);
            is.addView(choice(n, iv, Theme.iconStyle.equals(n), () -> pref("iconstyle", n, "Appearance")));
        }
        row(c, "Icon style", "Gaya ikon di Dock", is);
    }

    void wallpaper() {
        LinearLayout c = card();
        ImageView pv = new ImageView(a);
        pv.setScaleType(ImageView.ScaleType.CENTER_CROP);
        Bitmap b = a.wall.getSharp();
        if (b != null) pv.setImageBitmap(b);
        pv.setOutlineProvider(new ViewOutlineProvider() {
            @Override public void getOutline(View v, Outline o) { o.setRoundRect(0, 0, v.getWidth(), v.getHeight(), a.dp(12)); }
        });
        pv.setClipToOutline(true);
        LinearLayout.LayoutParams pl = new LinearLayout.LayoutParams(M, a.dp(130));
        pl.setMargins(a.dp(12), a.dp(12), a.dp(12), a.dp(6));
        c.addView(pv, pl);
        row(c, "Foto dari galeri", "Pilih gambar sebagai wallpaper", button("Pilih…", a::pickWallpaper));
        row(c, "Wallpaper bawaan", "Gelombang biru macOS Tahoe", button("Reset", a::resetWallpaper));
    }

    void dock() {
        LinearLayout c = card();
        final TextView val = value(Theme.dockSize + "");
        SliderView sl = new SliderView(a, (Theme.dockSize - 36) / 28f, v -> {
            int s = 36 + Math.round(v * 28);
            a.prefs.edit().putInt("dock_sz", s).apply();
            val.setText(s + "");
        });
        sl.setPalette(0x33808080, Theme.accent);
        LinearLayout r = new LinearLayout(a);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.addView(sl, new LinearLayout.LayoutParams(a.dp(150), a.dp(26)));
        val.setPadding(a.dp(8), 0, 0, 0);
        r.addView(val);
        row(c, "Ukuran Dock", null, r);
        row(c, "Terapkan ukuran Dock", null, button("Terapkan", () -> a.restartWith("Settings:Desktop & Dock")));
        row(c, "Widget kalender", null, new ToggleView(a, Theme.showCal, on -> {
            a.prefs.edit().putBoolean("show_cal", on).apply();
            a.restartWith("Settings:Desktop & Dock");
        }));
        LinearLayout c2 = card();
        row(c2, "Widgets", "Tahan lama widget di desktop untuk memindah atau menghapus", button("Tambah…", a::showWidgetPicker));
        row(c2, "Hapus semua widget", null, button("Hapus", a::clearWidgets));
    }

    void windows() {
        LinearLayout c = card();
        LinearLayout pv = new LinearLayout(a);
        int[] cur3 = {Theme.tlClose, Theme.tlMin, Theme.tlZoom};
        for (int col : cur3) {
            View d = new View(a);
            GradientDrawable g = new GradientDrawable();
            g.setShape(GradientDrawable.OVAL);
            g.setColor(col);
            d.setBackground(g);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(a.dp(14), a.dp(14));
            lp.leftMargin = a.dp(7);
            pv.addView(d, lp);
        }
        row(c, "Tombol jendela", "Tutup · Minimalkan · Perbesar", pv);
        String[] labels = {"Tutup (merah)", "Minimalkan (kuning)", "Perbesar (hijau)"};
        final String[] keys = {"tl_close", "tl_min", "tl_zoom"};
        for (int i = 0; i < 3; i++) {
            final String key = keys[i];
            LinearLayout cd = card();
            HorizontalScrollView hs = new HorizontalScrollView(a);
            hs.setHorizontalScrollBarEnabled(false);
            LinearLayout sw = new LinearLayout(a);
            sw.setGravity(Gravity.CENTER_VERTICAL);
            for (final int col : SWATCH) {
                View d = new View(a);
                GradientDrawable g = new GradientDrawable();
                g.setShape(GradientDrawable.OVAL);
                g.setColor(col);
                g.setStroke(1, Theme.sep());
                d.setBackground(g);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(a.dp(20), a.dp(20));
                lp.leftMargin = a.dp(7);
                d.setOnClickListener(v -> { a.prefs.edit().putInt(key, col).apply(); a.restartWith("Settings:Windows"); });
                sw.addView(d, lp);
            }
            hs.addView(sw);
            row(cd, labels[i], "Pilih warna atau ketik kode hex", hs);
            final EditText hex = new EditText(a);
            hex.setHint("#RRGGBB");
            hex.setSingleLine(true);
            hex.setTextSize(13);
            hex.setTextColor(Theme.fg());
            hex.setHintTextColor(Theme.sub());
            hex.setPadding(a.dp(10), 0, a.dp(10), 0);
            hex.setBackground(Ui.rr(Theme.dark ? 0x2EFFFFFF : 0x12000000, a.dp(10)));
            LinearLayout hr = new LinearLayout(a);
            hr.setGravity(Gravity.CENTER_VERTICAL);
            hr.addView(hex, new LinearLayout.LayoutParams(a.dp(110), a.dp(30)));
            TextView ap = button("Terapkan", () -> {
                try {
                    int col = Color.parseColor(hex.getText().toString().trim()) | 0xFF000000;
                    a.prefs.edit().putInt(key, col).apply();
                    a.restartWith("Settings:Windows");
                } catch (Exception e) { Toast.makeText(a, "Kode warna tidak valid", Toast.LENGTH_SHORT).show(); }
            });
            LinearLayout.LayoutParams al = new LinearLayout.LayoutParams(W, W);
            al.leftMargin = a.dp(8);
            hr.addView(ap, al);
            row(cd, "Warna custom", null, hr);
        }
        LinearLayout c3 = card();
        row(c3, "Kembalikan warna asli", null, button("Reset", () -> {
            SharedPreferences.Editor e = a.prefs.edit();
            for (String k : keys) e.remove(k);
            e.apply();
            a.restartWith("Settings:Windows");
        }));
    }

    void notifications() {
        LinearLayout c = card();
        boolean on = NotifService.inst != null;
        row(c, "Akses notifikasi", on ? "Aktif" : "Belum aktif",
                button(on ? "Atur" : "Aktifkan", () -> a.startExternal(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))));
        row(c, "Notification Center", "Klik jam di menu bar untuk membuka", button("Buka", a::showNotificationCenter));
    }

    void trackpad() {
        LinearLayout c = card();
        row(c, "Touchpad mode", "Kursor dan panel touchpad di layar. Bekerja di dalam launcher (desktop dan jendela).",
                new ToggleView(a, a.prefs.getBoolean("tp_on", false), on -> { a.prefs.edit().putBoolean("tp_on", on).apply(); a.tp.setEnabled(on); }));
        float sp0 = a.prefs.getFloat("tp_speed", 1.6f);
        final TextView val = value(String.format(Locale.US, "%.1fx", sp0));
        SliderView sl = new SliderView(a, (sp0 - 0.6f) / 2.4f, v -> {
            float sp = 0.6f + v * 2.4f;
            a.prefs.edit().putFloat("tp_speed", sp).apply();
            val.setText(String.format(Locale.US, "%.1fx", sp));
        });
        sl.setPalette(0x33808080, Theme.accent);
        LinearLayout r = new LinearLayout(a);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.addView(sl, new LinearLayout.LayoutParams(a.dp(150), a.dp(26)));
        val.setPadding(a.dp(8), 0, 0, 0);
        r.addView(val);
        row(c, "Kecepatan kursor", null, r);
        LinearLayout c2 = card();
        row(c2, "Ketuk untuk klik", null, new ToggleView(a, a.prefs.getBoolean("tp_tap", true), on -> a.prefs.edit().putBoolean("tp_tap", on).apply()));
        row(c2, "Scroll natural", "Arah scroll mengikuti gerakan jari", new ToggleView(a, a.prefs.getBoolean("tp_natural", true), on -> a.prefs.edit().putBoolean("tp_natural", on).apply()));
        LinearLayout c3 = card();
        row(c3, "Gerakan", "1 jari: gerakkan kursor · Ketuk: klik · Tahan lalu geser: seret jendela · 2 jari geser: scroll · Ketuk 2 jari: klik kanan (tekan lama)", null);
    }

    void launcher() {
        LinearLayout c = card();
        row(c, "Aplikasi Android sebagai jendela", "Aktifkan freeform di Opsi Pengembang (Enable freeform windows + Force activities to be resizable). Tanpa itu aplikasi tetap layar penuh.",
                new ToggleView(a, Theme.floatApps, on -> { Theme.floatApps = on; a.prefs.edit().putBoolean("float_apps", on).apply(); }));
        LinearLayout c2 = card();
        row(c2, "Jadikan launcher utama", null, button("Buka", () -> a.startExternal(new Intent(Settings.ACTION_HOME_SETTINGS))));
        row(c2, "Izin akses file (Finder)", a.hasStorage() ? "Sudah diizinkan" : "Belum diizinkan", button("Atur", a::requestStorage));
        row(c2, "Reset pilihan aplikasi Dock", "Hapus aplikasi yang ditetapkan lewat tekan lama", button("Reset", () -> {
            SharedPreferences.Editor e = a.prefs.edit();
            for (String[] it : MainActivity.DOCK) e.remove("map_" + it[0]);
            e.apply();
            Toast.makeText(a, "Pilihan Dock direset", Toast.LENGTH_SHORT).show();
        }));
        LinearLayout c3 = card();
        row(c3, "Orientasi", null, value("Terkunci landscape"));
        row(c3, "Versi", null, value("2.0"));
    }
}
