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
        {"General", "gearshape.fill", 0xFF8E8E93}, {"Language & Region", "globe", 0xFF0A84FF}, {"Keyboard", "keyboard", 0xFF8E8E93}, {"Accessibility", "accessibility", 0xFF0A84FF}, {"Appearance", IconView.CONTRAST, 0xFF1C1C1E},
        {"Desktop & Dock", IconView.DOCK, 0xFF2C2C2E}, {"Displays", "display", 0xFF0A84FF}, {"Sound", "speaker.wave.3.fill", 0xFFFF375F},
        {"Wallpaper", "photo.fill", 0xFF32ADE6}, {"Windows", "macwindow", 0xFF5E5CE6}, {"Notifications", "bell.badge.fill", 0xFFFF3B30},
        {"Lock Screen", "lock.fill", 0xFF1C1C1E}, {"Touch ID & Password", "touchid", 0xFFFF453A}, {"Trackpad", "rectangle.and.hand.point.up.left.fill", 0xFF8E8E93}, {"Launcher", "square.grid.2x2.fill", 0xFF8E5CF0}};
    static final int[] SWATCH = {0xFFFF5F57, 0xFFFEBC2E, 0xFF28C840, 0xFF0A84FF, 0xFFBF5AF2, 0xFFFF375F, 0xFFFF9F0A, 0xFF30D158, 0xFF8E8E93, 0xFFFFFFFF};

    final MainActivity a;
    LinearLayout detail;
    TextView toolTitle;
    final List<View> rows = new ArrayList<>();
    final List<String> names = new ArrayList<>();
    String cur = "";
    final List<String> nav = new ArrayList<>();
    int navI = -1;
    boolean navigating;

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
        FrameLayout av = new FrameLayout(a);
        GradientDrawable ag = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{0xFFC9CDD6, 0xFF8D929E});
        ag.setShape(GradientDrawable.OVAL);
        av.setBackground(ag);
        av.setClipToOutline(true);
        android.widget.ImageView pi = Sf.img(a, "person.fill", 0xF2FFFFFF);
        FrameLayout.LayoutParams pil = new FrameLayout.LayoutParams(a.dp(22), a.dp(22), Gravity.CENTER | Gravity.BOTTOM);
        pil.bottomMargin = a.dp(-2);
        av.addView(pi, pil);
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
            Object icn = s[1];
            LinearLayout r = new LinearLayout(a);
            r.setGravity(Gravity.CENTER_VERTICAL);
            r.setPadding(a.dp(6), a.dp(5), a.dp(6), a.dp(5));
            FrameLayout sq = new FrameLayout(a);
            int bc = (Integer) s[2];
            GradientDrawable sg = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                    new int[]{lighten(bc, .16f), bc});
            sg.setCornerRadius(a.dp(5.6f));
            sq.setBackground(sg);
            if (icn instanceof Integer) sq.addView(new IconView(a, (Integer) icn, 0xFFFFFFFF), new FrameLayout.LayoutParams(a.dp(14), a.dp(14), Gravity.CENTER));
            else sq.addView(Sf.img(a, (String) icn, 0xFFFFFFFF), new FrameLayout.LayoutParams(a.dp(14), a.dp(14), Gravity.CENTER));
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
        LinearLayout tb = new LinearLayout(a);
        tb.setGravity(Gravity.CENTER_VERTICAL);
        TextView bk = Ui.circleBtn(a, "\u2039"), fw = Ui.circleBtn(a, "\u203A");
        bk.setOnClickListener(v -> { if (navI > 0) { navigating = true; show(nav.get(--navI)); navigating = false; } });
        fw.setOnClickListener(v -> { if (navI < nav.size() - 1) { navigating = true; show(nav.get(++navI)); navigating = false; } });
        tb.addView(bk, new LinearLayout.LayoutParams(a.dp(32), a.dp(32)));
        LinearLayout.LayoutParams fl = new LinearLayout.LayoutParams(a.dp(32), a.dp(32));
        fl.leftMargin = a.dp(4);
        tb.addView(fw, fl);
        tb.addView(toolTitle);
        MacWindow w = new MacWindow(a, "System Settings", tb, null, body);
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
        if (!navigating) { while (nav.size() > navI + 1) nav.remove(nav.size() - 1); nav.add(name); navI++; }
        toolTitle.setText(L.t(name));
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
            case "Lock Screen": lockScreen(); break;
            case "Touch ID & Password": touchPw(); break;
            case "General": general(); break;
            case "Language & Region": language(); break;
            case "Keyboard": keyboardPage(); break;
            case "Wi-Fi": wifi(); break;
            case "Bluetooth": bluetooth(); break;
            case "Battery": battery(); break;
            case "Accessibility": shortcut(name, Settings.ACTION_ACCESSIBILITY_SETTINGS); break;
            case "Displays": displays(); break;
            case "Sound": shortcut(name, Settings.ACTION_SOUND_SETTINGS); break;
            default: appearance();
        }
    }

    void keyboardPage() {
        LinearLayout c = card();
        String det = MacKeyboard.isTabletDevice(a) ? "Tablet" : "Phone";
        row(c, "Detected device", "The keyboard design follows your device", Ui.tv(a, det, 12.5f, Theme.sub(), true));
        LinearLayout seg = new LinearLayout(a);
        final String[] opts = {"Auto", "iPad", "iPhone"};
        String cur = a.prefs.getString("kb_type", "Auto");
        for (final String o : opts) {
            TextView t = Ui.tv(a, o, 12, o.equals(cur) ? 0xFFFFFFFF : Theme.fg(), true);
            t.setGravity(Gravity.CENTER);
            t.setBackground(o.equals(cur) ? Ui.rr(Theme.accent, a.dp(12)) : null);
            t.setOnClickListener(v -> { a.prefs.edit().putString("kb_type", o).apply(); show("Keyboard"); });
            seg.addView(t, new LinearLayout.LayoutParams(a.dp(58), a.dp(26)));
        }
        row(c, "Keyboard Type", "iPad or iPhone design", seg);
        LinearLayout c2 = card();
        toggleRow(c2, "Floating keyboard", "A small keyboard you can move", a.prefs.getBoolean("kb_float", false), v -> a.prefs.edit().putBoolean("kb_float", v).apply());
        toggleRow(c2, "Predictive text", "QuickType suggestions above the keys", a.prefs.getBoolean("kb_predict", true), v -> a.prefs.edit().putBoolean("kb_predict", v).apply());
        toggleRow(c2, "Key click sound", null, a.prefs.getBoolean("kb_sound", true), v -> a.prefs.edit().putBoolean("kb_sound", v).apply());
        toggleRow(c2, "Haptic feedback", null, a.prefs.getBoolean("kb_haptic", true), v -> a.prefs.edit().putBoolean("kb_haptic", v).apply());
        LinearLayout c3 = card();
        row(c3, "Stickers", "Add your own in the emoji panel (+)", null);
        row(c3, "Learned words", "Words QuickType picked up from you", button("Reset", () -> {
            android.content.SharedPreferences.Editor ed = a.prefs.edit();
            for (String k : new java.util.ArrayList<>(a.prefs.getAll().keySet())) if (k.startsWith("kbw_")) ed.remove(k);
            ed.remove("kb_learned").apply();
        }));
    }

    void language() {
        LinearLayout c = card();
        for (int i = 0; i < L.CODES.length; i++) {
            final int k = i;
            LinearLayout r = new LinearLayout(a);
            r.setGravity(Gravity.CENTER_VERTICAL);
            r.setPadding(a.dp(14), a.dp(10), a.dp(14), a.dp(10));
            LinearLayout tx = new LinearLayout(a);
            tx.setOrientation(LinearLayout.VERTICAL);
            tx.addView(Ui.tv(a, L.NAMES[i], 13, Theme.fg(), false));
            if (i != 0) tx.addView(Ui.tv(a, L.ENG[i], 11, Theme.sub(), false));
            r.addView(tx, new LinearLayout.LayoutParams(0, W, 1f));
            if (L.index() == i) {
                android.widget.ImageView ck = Sf.img(a, "checkmark", Theme.accent);
                r.addView(ck, new LinearLayout.LayoutParams(a.dp(16), a.dp(16)));
            }
            r.setOnClickListener(v -> {
                if (L.index() == k) return;
                L.set(a, k);
                a.recreate();
            });
            if (c.getChildCount() > 0) {
                View sp = new View(a);
                sp.setBackgroundColor(Theme.sep());
                LinearLayout.LayoutParams sl = new LinearLayout.LayoutParams(M, 1);
                sl.leftMargin = a.dp(14);
                c.addView(sp, sl);
            }
            c.addView(r);
        }
    }

    void shortcut(final String n, final String action) {
        LinearLayout c = card();
        row(c, "Pengaturan sistem Android", "Buka halaman " + n + " bawaan perangkat", button("Buka", () -> a.startExternal(new Intent(action))));
    }

    void appearance() {
        LinearLayout c = card();
        LinearLayout ch = new LinearLayout(a);
        for (final String n : new String[]{"Auto", "Light", "Dark"}) {
            View t = n.equals("Light") ? gradThumb(0xFFEAF2FF, 0xFF9CC3F5) : n.equals("Dark") ? gradThumb(0xFF3A3A44, 0xFF15151A) : gradThumb(0xFFB5D2F7, 0xFF2A2A33);
            ch.addView(choice(n, t, Theme.appearance.equals(n), () -> pref("appearance", n, "Appearance")));
        }
        row(c, "Appearance", null, ch);

        LinearLayout fl = new LinearLayout(a);
        for (final String n : Emoji.FONTS) {
            FrameLayout f = new FrameLayout(a);
            f.setBackground(Ui.rr(Theme.dark ? 0x33FFFFFF : 0x0F000000, a.dp(8)));
            TextView aa = new TextView(a);
            aa.setText("Aa");
            aa.setTextSize(20);
            aa.setTextColor(Theme.fg());
            aa.setTypeface(Emoji.faceFor(n, false));
            aa.setGravity(Gravity.CENTER);
            f.addView(aa, new FrameLayout.LayoutParams(M, M));
            fl.addView(choice(n, f, Emoji.name().equals(n), () -> { Emoji.reset(); pref("ui_font", n, "Appearance"); }));
        }
        row(c, "Font", "Pilih font untuk teks. Emoji tetap memakai Apple Color Emoji.", fl);

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

    // ---------- new panes (macOS Tahoe style) ----------
    View dropdown(String cur, final String[] opts, final java.util.function.Consumer<String> cb) {
        final TextView t = Ui.tv(a, cur + " \u25BE", 12.5f, Theme.fg(), false);
        t.setPadding(a.dp(10), a.dp(4), a.dp(10), a.dp(4));
        t.setBackground(Ui.rr(Theme.dark ? 0x2EFFFFFF : 0x14000000, a.dp(9)));
        t.setOnClickListener(v -> {
            Runnable[] acts = new Runnable[opts.length];
            for (int i = 0; i < opts.length; i++) { final String o = opts[i]; acts[i] = () -> { t.setText(o + " \u25BE"); cb.accept(o); }; }
            a.showPopup(t, opts, acts);
        });
        return t;
    }

    View segment(final String[] opts, int sel, final java.util.function.IntConsumer cb) {
        LinearLayout seg = new LinearLayout(a);
        seg.setBackground(Ui.rr(Theme.dark ? 0x2EFFFFFF : 0x14000000, a.dp(10)));
        seg.setPadding(a.dp(2), a.dp(2), a.dp(2), a.dp(2));
        for (int i = 0; i < opts.length; i++) {
            final int k = i;
            TextView t = Ui.tv(a, opts[i], 12, i == sel ? Theme.fg() : Theme.sub(), i == sel);
            t.setGravity(Gravity.CENTER);
            t.setPadding(a.dp(10), a.dp(5), a.dp(10), a.dp(5));
            if (i == sel) t.setBackground(Ui.rr(Theme.dark ? 0xFF58585C : 0xFFFFFFFF, a.dp(8)));
            t.setOnClickListener(v -> cb.accept(k));
            seg.addView(t, new LinearLayout.LayoutParams(0, W, 1f));
        }
        return seg;
    }

    static int lighten(int c, float f) {
        int r = Math.round(android.graphics.Color.red(c) + (255 - android.graphics.Color.red(c)) * f);
        int g = Math.round(android.graphics.Color.green(c) + (255 - android.graphics.Color.green(c)) * f);
        int b = Math.round(android.graphics.Color.blue(c) + (255 - android.graphics.Color.blue(c)) * f);
        return android.graphics.Color.rgb(r, g, b);
    }

    TextView chev() {
        TextView t = Ui.tv(a, "", 18, Theme.sub(), false);
        android.graphics.drawable.Drawable d = Sf.sized(a, "chevron.right", Theme.sub(), a.dp(12));
        if (d != null) t.setCompoundDrawables(d, null, null, null);
        return t;
    }

    void linkRow(LinearLayout c, String label, final Runnable r) {
        row(c, label, null, chev());
        c.getChildAt(c.getChildCount() - 1).setOnClickListener(v -> r.run());
    }

    View sliderRow(float init, String left, String right, final java.util.function.Consumer<Float> cb) {
        LinearLayout r = new LinearLayout(a);
        r.setOrientation(LinearLayout.VERTICAL);
        SliderView sl = new SliderView(a, init, v -> cb.accept(v));
        sl.setPalette(0x33808080, Theme.accent);
        r.addView(sl, new LinearLayout.LayoutParams(a.dp(170), a.dp(24)));
        LinearLayout lb = new LinearLayout(a);
        lb.addView(Ui.tv(a, left, 10, Theme.sub(), false), new LinearLayout.LayoutParams(0, W, 1f));
        lb.addView(Ui.tv(a, right, 10, Theme.sub(), false));
        r.addView(lb, new LinearLayout.LayoutParams(a.dp(170), W));
        return r;
    }

    void headerCard(String title, String sub, View icon) {
        LinearLayout c = new LinearLayout(a);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setGravity(Gravity.CENTER_HORIZONTAL);
        c.setBackground(Ui.rr(Theme.card(), a.dp(12)));
        c.setPadding(a.dp(16), a.dp(14), a.dp(16), a.dp(14));
        if (icon != null) c.addView(icon, new LinearLayout.LayoutParams(a.dp(40), a.dp(40)));
        TextView t = Ui.tv(a, title, 17, Theme.fg(), true);
        t.setPadding(0, a.dp(6), 0, a.dp(2));
        c.addView(t);
        TextView s = Ui.tv(a, sub, 11.5f, Theme.sub(), false);
        s.setGravity(Gravity.CENTER);
        c.addView(s);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(M, W);
        lp.bottomMargin = a.dp(12);
        detail.addView(c, lp);
    }

    void label(String s) {
        TextView t = Ui.tv(a, s, 13, Theme.fg(), true);
        t.setPadding(a.dp(6), a.dp(2), 0, a.dp(6));
        detail.addView(t);
    }


    // ----- Lock Screen / Touch ID & Password -----
    void toggleRow(LinearLayout c, String label, String sub, boolean on, final java.util.function.Consumer<Boolean> cb) {
        row(c, label, sub, new ToggleView(a, on, v -> cb.accept(v)));
    }

    void lockScreen() {
        LinearLayout c = card();
        row(c, "Turn display off when inactive", "Layar dikunci otomatis setelah tidak disentuh",
                dropdown(a.prefs.getString("lock_idle", "Never"), LockScreen.IDLE_LABELS, s -> a.prefs.edit().putString("lock_idle", s).apply()));
        row(c, "Require password after screen saver begins or display is turned off", null,
                dropdown(a.prefs.getString("lock_req", "Immediately"), LockScreen.REQ_LABELS, s -> a.prefs.edit().putString("lock_req", s).apply()));
        LinearLayout c2 = card();
        row(c2, "Profile Photo", "Shown on the lock screen", button(LockScreen.hasPhoto(a) ? "Remove" : "Choose", () -> {
            if (LockScreen.hasPhoto(a)) { LockScreen.removePhoto(a); show("Lock Screen"); }
            else a.pick("image/*", false, us -> { LockScreen.savePhoto(a, us.get(0)); a.runOnUiThread(() -> show("Lock Screen")); });
        }));
        toggleRow(c2, "Show user name and photo", null, a.prefs.getBoolean("lock_name_on", true), v -> a.prefs.edit().putBoolean("lock_name_on", v).apply());
        toggleRow(c2, "Show password hints", null, a.prefs.getBoolean("lock_hints_on", true), v -> a.prefs.edit().putBoolean("lock_hints_on", v).apply());
        LinearLayout mr = new LinearLayout(a);
        mr.setGravity(Gravity.CENTER_VERTICAL);
        mr.addView(new ToggleView(a, a.prefs.getBoolean("lock_msg_on", false), v -> a.prefs.edit().putBoolean("lock_msg_on", v).apply()));
        TextView set = Ui.tv(a, "Set\u2026", 12.5f, Theme.fg(), false);
        set.setPadding(a.dp(12), a.dp(5), a.dp(12), a.dp(5));
        set.setBackground(Ui.rr(Theme.dark ? 0x2EFFFFFF : 0x14000000, a.dp(10)));
        set.setOnClickListener(v -> a.prompt("Lock screen message", a.prefs.getString("lock_msg", ""), s -> a.prefs.edit().putString("lock_msg", s).putBoolean("lock_msg_on", true).apply()));
        LinearLayout.LayoutParams sl = new LinearLayout.LayoutParams(W, W);
        sl.leftMargin = a.dp(8);
        mr.addView(set, sl);
        row(c2, "Show message when locked", null, mr);
        LinearLayout c3 = card();
        row(c3, "Clock style", "Gaya jam di layar kunci", segment(new String[]{"Sans", "Serif"}, "Serif".equals(a.prefs.getString("lock_clock", "Sans")) ? 1 : 0,
                i -> { a.prefs.edit().putString("lock_clock", i == 1 ? "Serif" : "Sans").apply(); show("Lock Screen"); }));
        row(c3, "Lock Screen", null, button("Lock Now", () -> a.lock.lock(true)));
    }

    void touchPw() {
        LinearLayout c = card();
        row(c, "Name", null, button(a.lock.userName() + "  \u2039\u203A", () ->
                a.prompt("Name", a.lock.userName(), s -> { a.prefs.edit().putString("user_name", s).apply(); show("Touch ID & Password"); })));
        boolean has = a.lock.hasPw();
        row(c, "Password", has ? "Kata sandi kunci layar sudah diatur" : "Belum ada kata sandi", button(has ? "Change\u2026" : "Set\u2026",
                () -> a.lock.passwordDialog(() -> show("Touch ID & Password"))));
        if (has) {
            String hint = a.prefs.getString("lock_hint", "");
            if (hint.length() > 0) row(c, "Password hint", null, value(hint));
            LinearLayout c2 = card();
            TextView off = Ui.tv(a, "Turn Off Password\u2026", 12.5f, 0xFFFFFFFF, true);
            off.setPadding(a.dp(14), a.dp(5), a.dp(14), a.dp(5));
            off.setBackground(Ui.rr(0xFFFF453A, a.dp(12)));
            off.setOnClickListener(v -> MacDialog.confirm(a, L.t("Turn off the lock screen password?"), L.t("Turn Off"), true,
                    () -> { a.prefs.edit().remove("lock_pw").remove("lock_hint").apply(); show("Touch ID & Password"); }));
            row(c2, "Lock screen password", null, off);
        }
    }

    // ----- General -----
    void general() {
        FrameLayout ic = new FrameLayout(a);
        ic.setBackground(Ui.rr(0xFF8E8E93, a.dp(10)));
        TextView g = Ui.tv(a, "\u2699", 24, 0xFFFFFFFF, false);
        g.setGravity(Gravity.CENTER);
        ic.addView(g, new FrameLayout.LayoutParams(M, M));
        headerCard("General", "Manage your overall setup and preferences, such as software updates, device language, and more.", ic);
        LinearLayout c = card();
        row(c, "Name", null, value(Build.MODEL));
        row(c, "Manufacturer", null, value(Build.MANUFACTURER));
        row(c, "Android", null, value(Build.VERSION.RELEASE));
        row(c, "Launcher", null, value("macOS Tahoe Launcher"));
        LinearLayout c1 = card();
        linkRow(c1, "About", () -> a.startExternal(new Intent("android.settings.DEVICE_INFO_SETTINGS")));
        linkRow(c1, "Software Update", () -> a.startExternal(new Intent("android.settings.SYSTEM_UPDATE_SETTINGS")));
        linkRow(c1, "Storage", () -> a.startExternal(new Intent(Settings.ACTION_INTERNAL_STORAGE_SETTINGS)));
        LinearLayout c2 = card();
        linkRow(c2, "AirDrop & Handoff", () -> a.startExternal(new Intent(Settings.ACTION_BLUETOOTH_SETTINGS)));
        LinearLayout c3 = card();
        linkRow(c3, "AutoFill & Passwords", () -> a.startExternal(new Intent(Settings.ACTION_PRIVACY_SETTINGS)));
        linkRow(c3, "Date & Time", () -> a.startExternal(new Intent(Settings.ACTION_DATE_SETTINGS)));
        linkRow(c3, "Language & Region", () -> show("Language & Region"));
    }

    // ----- Desktop & Dock -----
    void dock() {
        LinearLayout c = card();
        float v0 = (Theme.dockSize - 36) / 28f;
        row(c, "Size", null, sliderRow(v0, "Small", "Large", v -> {
            int s = 36 + Math.round(v * 28);
            if (s == Theme.dockSize) return;
            Theme.dockSize = s;
            a.prefs.edit().putInt("dock_sz", s).apply();
            a.buildDock();
        }));
        boolean magOn = a.prefs.getBoolean("dock_mag", true);
        float mv = magOn ? a.prefs.getFloat("dock_mag_v", .55f) : 0f;
        row(c, "Magnification", null, sliderRow(mv, "Off", "Large", v -> {
            a.prefs.edit().putBoolean("dock_mag", v > 0.04f).putFloat("dock_mag_v", Math.max(0.05f, v)).apply();
        }));
        LinearLayout c1 = card();
        row(c1, "Dock position on screen", null, value("Bottom"));
        row(c1, "Minimized window animation", null, dropdown(a.prefs.getString("min_effect", "Genie").equals("Genie") ? "Genie Effect" : "Scale Effect",
                new String[]{"Genie Effect", "Scale Effect"}, o -> a.prefs.edit().putString("min_effect", o.startsWith("Genie") ? "Genie" : "Scale").apply()));
        row(c1, "Window title bar double-click action", null, dropdown(a.prefs.getString("title_dbl", "Zoom"),
                new String[]{"Zoom", "Minimize", "None"}, o -> a.prefs.edit().putString("title_dbl", o).apply()));
        LinearLayout c2 = card();
        row(c2, "Automatically hide and show the Dock", null, new ToggleView(a, a.prefs.getBoolean("dock_auto", false), on -> {
            a.prefs.edit().putBoolean("dock_auto", on).apply();
            a.dockPeek = false; a.lastAway = null; a.updateDock();
        }));
        row(c2, "Animate opening applications", null, new ToggleView(a, a.prefs.getBoolean("dock_anim", true), on -> a.prefs.edit().putBoolean("dock_anim", on).apply()));
        row(c2, "Show indicators for open applications", null, new ToggleView(a, a.prefs.getBoolean("dock_dots", true), on -> {
            a.prefs.edit().putBoolean("dock_dots", on).apply();
            a.refreshDots();
        }));
        label("Desktop & Stage Manager");
        LinearLayout c3 = card();
        row(c3, "Widget kalender", null, new ToggleView(a, Theme.showCal, on -> {
            a.prefs.edit().putBoolean("show_cal", on).apply();
            a.restartWith("Settings:Desktop & Dock");
        }));
        row(c3, "Widgets", "Tahan lama widget di desktop untuk memindah atau menghapus", button("Tambah\u2026", a::showWidgetPicker));
        row(c3, "Hapus semua widget", null, button("Hapus", a::clearWidgets));
    }

    // ----- Notifications -----
    void notifications() {
        FrameLayout ic = new FrameLayout(a);
        ic.setBackground(Ui.rr(0xFFFF3B30, a.dp(10)));
        TextView g = Ui.tv(a, "\uD83D\uDD14", 20, 0xFFFFFFFF, false);
        g.setGravity(Gravity.CENTER);
        ic.addView(g, new FrameLayout.LayoutParams(M, M));
        headerCard("Notifications", "Customize when and how notifications appear, if they play a sound, and which apps can send them.", ic);
        label("Notification Center");
        LinearLayout c = card();
        boolean on = NotifService.inst != null;
        row(c, "Akses notifikasi", on ? "Aktif" : "Belum aktif",
                button(on ? "Atur" : "Aktifkan", () -> a.startExternal(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))));
        row(c, "Show previews", null, dropdown(a.prefs.getString("notif_prev", "Always"), new String[]{"Always", "Never"},
                o -> a.prefs.edit().putString("notif_prev", o).apply()));
        row(c, "Notification Center", "Klik jam di menu bar untuk membuka", button("Buka", a::showNotificationCenter));
        label("Application Notifications");
        LinearLayout list = card();
        a.ensureApps();
        int n = 0;
        for (final MainActivity.App ap : a.apps) {
            if (ap.cn == null) continue;
            if (n++ >= 60) break;
            LinearLayout r = new LinearLayout(a);
            r.setGravity(Gravity.CENTER_VERTICAL);
            r.setPadding(a.dp(14), a.dp(8), a.dp(14), a.dp(8));
            ImageView iv = new ImageView(a);
            iv.setImageDrawable(ap.icon);
            r.addView(iv, new LinearLayout.LayoutParams(a.dp(28), a.dp(28)));
            LinearLayout tx = new LinearLayout(a);
            tx.setOrientation(LinearLayout.VERTICAL);
            tx.setPadding(a.dp(10), 0, 0, 0);
            tx.addView(Ui.tv(a, ap.label, 13, Theme.fg(), false));
            tx.addView(Ui.tv(a, "Banners, Sounds, Badges", 11, Theme.sub(), false));
            r.addView(tx, new LinearLayout.LayoutParams(0, W, 1f));
            r.addView(chev());
            r.setOnClickListener(v -> a.startExternal(new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, ap.cn.getPackageName())));
            if (list.getChildCount() > 0) {
                View sp = new View(a);
                sp.setBackgroundColor(Theme.sep());
                LinearLayout.LayoutParams sl = new LinearLayout.LayoutParams(M, 1);
                sl.leftMargin = a.dp(52);
                list.addView(sp, sl);
            }
            list.addView(r);
        }
    }

    // ----- Trackpad -----
    int tpTab = 0;

    void trackpad() {
        LinearLayout prev = new LinearLayout(a);
        prev.setGravity(Gravity.CENTER);
        prev.setBackground(Ui.rr(Theme.card(), a.dp(12)));
        FrameLayout pad = new FrameLayout(a);
        pad.setBackground(Ui.rr(Theme.dark ? 0xFF3A3A3E : 0xFFF2F2F5, a.dp(10)));
        View dot = new View(a);
        GradientDrawable dg = new GradientDrawable();
        dg.setShape(GradientDrawable.OVAL);
        dg.setColor(Theme.accent);
        dot.setBackground(dg);
        pad.addView(dot, new FrameLayout.LayoutParams(a.dp(7), a.dp(7), Gravity.CENTER));
        prev.addView(pad, new LinearLayout.LayoutParams(a.dp(110), a.dp(66)));
        LinearLayout.LayoutParams pl = new LinearLayout.LayoutParams(M, a.dp(86));
        pl.bottomMargin = a.dp(10);
        detail.addView(prev, pl);
        LinearLayout segRow = new LinearLayout(a);
        segRow.setGravity(Gravity.CENTER);
        segRow.addView(segment(new String[]{"Point & Click", "Scroll & Zoom", "More Gestures"}, tpTab, k -> { tpTab = k; show("Trackpad"); }));
        LinearLayout.LayoutParams sgl = new LinearLayout.LayoutParams(M, W);
        sgl.bottomMargin = a.dp(10);
        detail.addView(segRow, sgl);
        LinearLayout c = card();
        if (tpTab == 0) {
            row(c, "Touchpad mode", "Seluruh layar menjadi trackpad, kursor bebas ke semua sisi layar.",
                    new ToggleView(a, a.prefs.getBoolean("tp_on", false), on -> { a.prefs.edit().putBoolean("tp_on", on).apply(); a.tp.setEnabled(on); }));
            float sp0 = a.prefs.getFloat("tp_speed", 1.6f);
            row(c, "Tracking speed", null, sliderRow((sp0 - 0.6f) / 2.4f, "Slow", "Fast", v -> a.prefs.edit().putFloat("tp_speed", 0.6f + v * 2.4f).apply()));
            float hold = (a.prefs.getInt("tp_hold", 420) - 250) / 450f;
            row(c, "Click", "Waktu tahan untuk menyeret", sliderRow(hold, "Light", "Firm", v -> a.prefs.edit().putInt("tp_hold", 250 + Math.round(v * 450)).apply()));
            row(c, "Secondary click", null, dropdown(a.prefs.getBoolean("tp_secondary", true) ? "Click with Two Fingers" : "Off",
                    new String[]{"Click with Two Fingers", "Off"}, o -> a.prefs.edit().putBoolean("tp_secondary", !o.equals("Off")).apply()));
            row(c, "Tap to click", "Tap with one finger", new ToggleView(a, a.prefs.getBoolean("tp_tap", true), on -> a.prefs.edit().putBoolean("tp_tap", on).apply()));
        } else if (tpTab == 1) {
            row(c, "Natural scrolling", "Arah scroll mengikuti gerakan jari", new ToggleView(a, a.prefs.getBoolean("tp_natural", true), on -> a.prefs.edit().putBoolean("tp_natural", on).apply()));
        } else {
            row(c, "Move cursor", "Geser 1 jari di mana saja pada layar", null);
            row(c, "Click", "Ketuk 1 jari", null);
            row(c, "Drag", "Tahan lalu geser (seret jendela)", null);
            row(c, "Scroll", "Geser 2 jari", null);
            row(c, "Right click", "Ketuk 2 jari", null);
        }
    }

    // ----- Wi-Fi -----
    boolean hasLoc() { return a.checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED; }

    void wifiPanel() {
        try {
            if (Build.VERSION.SDK_INT >= 29) a.startExternal(new Intent(Settings.Panel.ACTION_WIFI));
            else a.startExternal(new Intent(Settings.ACTION_WIFI_SETTINGS));
        } catch (Exception e) { a.startExternal(new Intent(Settings.ACTION_WIFI_SETTINGS)); }
    }

    @SuppressWarnings("deprecation")
    void wifi() {
        final android.net.wifi.WifiManager wm = (android.net.wifi.WifiManager) a.getApplicationContext().getSystemService(android.content.Context.WIFI_SERVICE);
        boolean en = wm != null && wm.isWifiEnabled();
        LinearLayout c = card();
        row(c, "Wi-Fi", "Set up Wi-Fi to wirelessly connect to the internet. Turn on Wi-Fi, then choose a network to join.",
                new ToggleView(a, en, on -> wifiPanel()));
        String cur = null;
        try {
            if (en && hasLoc()) { String s = wm.getConnectionInfo().getSSID(); if (s != null && !s.contains("unknown")) cur = s.replace("\"", ""); }
        } catch (Exception e) { }
        LinearLayout st = new LinearLayout(a);
        st.setGravity(Gravity.CENTER_VERTICAL);
        View dot = new View(a);
        GradientDrawable dg = new GradientDrawable();
        dg.setShape(GradientDrawable.OVAL);
        dg.setColor(cur != null ? 0xFF30D158 : 0xFFFFCC00);
        dot.setBackground(dg);
        st.addView(dot, new LinearLayout.LayoutParams(a.dp(8), a.dp(8)));
        TextView stt = Ui.tv(a, cur != null ? "Connected to " + cur : "Not connected", 12.5f, Theme.sub(), false);
        stt.setPadding(a.dp(8), 0, 0, 0);
        st.addView(stt, new LinearLayout.LayoutParams(0, W, 1f));
        row(c, "Status", null, st);
        label("Personal Hotspots");
        LinearLayout h = card();
        row(h, "Hotspot", "Bagikan koneksi internet perangkat ini", button("Buka", () -> {
            try { a.startExternal(new Intent().setClassName("com.android.settings", "com.android.settings.TetherSettings")); }
            catch (Exception e) { a.startExternal(new Intent(Settings.ACTION_WIRELESS_SETTINGS)); }
        }));
        label("Other Networks");
        detail.addView(WifiHelper.listView(a, Theme.fg(), Theme.sub(), Theme.accent, Theme.card(), null));
        LinearLayout more = card();
        row(more, "Open Wi-Fi settings", "Join hidden or saved networks system-wide", button("Open", () -> wifiPanel()));
    }

    // ----- Bluetooth -----
    void bluetooth() {
        label("Bluetooth");
        detail.addView(BtHelper.listView(a, Theme.fg(), Theme.sub(), Theme.accent, Theme.card()));
        LinearLayout more = card();
        row(more, "Open Bluetooth settings", "Manage devices system-wide", button("Open", () -> a.startExternal(new Intent(Settings.ACTION_BLUETOOTH_SETTINGS))));
    }

    // ----- Battery -----
    int batTab = 0;

    class Bars extends View {
        final float[] vals; final float max; final int color; final String[] xl; final String ylTop, ylMid, ylBot;
        final android.graphics.Paint p = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
        Bars(float[] v, float max, int color, String[] xl, String top, String mid, String bot) {
            super(a); vals = v; this.max = max; this.color = color; this.xl = xl; ylTop = top; ylMid = mid; ylBot = bot;
        }
        @Override protected void onDraw(android.graphics.Canvas c) {
            float w = getWidth(), h = getHeight(), lab = a.dp(30), bot = a.dp(16), pw = w - lab, ph = h - bot;
            p.setColor(Theme.dark ? 0xFF2F3B33 : 0xFFE3EFE6);
            p.setColor(0x14808080);
            c.drawRect(0, 0, pw, ph, p);
            p.setColor(0x33808080);
            p.setStrokeWidth(1);
            for (int i = 0; i <= 2; i++) c.drawLine(0, ph * i / 2f, pw, ph * i / 2f, p);
            p.setTextSize(a.dp(9));
            p.setColor(Theme.sub());
            c.drawText(ylTop, pw + a.dp(4), a.dp(9), p);
            c.drawText(ylMid, pw + a.dp(4), ph / 2f + a.dp(3), p);
            c.drawText(ylBot, pw + a.dp(4), ph, p);
            int n = vals.length;
            float bw = pw / n;
            p.setColor(color);
            for (int i = 0; i < n; i++) {
                if (vals[i] < 0) continue;
                float bh = Math.min(1f, vals[i] / max) * ph;
                c.drawRoundRect(new android.graphics.RectF(i * bw + bw * .12f, ph - bh, (i + 1) * bw - bw * .12f, ph), bw * .2f, bw * .2f, p);
            }
            p.setColor(Theme.sub());
            for (int i = 0; i < xl.length; i++) if (xl[i] != null) c.drawText(xl[i], i * bw, h - a.dp(3), p);
        }
    }

    void battery() {
        android.content.Intent b = a.registerReceiver(null, new android.content.IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        int status = b == null ? -1 : b.getIntExtra(android.os.BatteryManager.EXTRA_STATUS, -1);
        int health = b == null ? -1 : b.getIntExtra(android.os.BatteryManager.EXTRA_HEALTH, -1);
        boolean charging = status == android.os.BatteryManager.BATTERY_STATUS_CHARGING || status == android.os.BatteryManager.BATTERY_STATUS_FULL;
        android.os.PowerManager pm = (android.os.PowerManager) a.getSystemService(android.content.Context.POWER_SERVICE);
        boolean saver = pm != null && pm.isPowerSaveMode();
        LinearLayout c = card();
        row(c, "Low Power Mode", null, dropdown(saver ? "Always" : "Never", new String[]{"Open Battery Saver\u2026"}, o -> a.startExternal(new Intent(Settings.ACTION_BATTERY_SAVER_SETTINGS))));
        row(c, "Battery Health", null, value(health == android.os.BatteryManager.BATTERY_HEALTH_GOOD ? "Normal" : health == android.os.BatteryManager.BATTERY_HEALTH_OVERHEAT ? "Overheated"
                : health == android.os.BatteryManager.BATTERY_HEALTH_DEAD ? "Service Recommended" : "Normal"));
        LinearLayout c2 = card();
        LinearLayout seg = new LinearLayout(a);
        seg.setGravity(Gravity.CENTER);
        seg.setPadding(a.dp(12), a.dp(10), a.dp(12), a.dp(4));
        seg.addView(segment(new String[]{"Last 24 Hours", "Last 10 Days"}, batTab, k -> { batTab = k; show("Battery"); }), new LinearLayout.LayoutParams(M, W));
        c2.addView(seg);
        TextView st = Ui.tv(a, charging ? "Charging" : "On Battery", 12.5f, Theme.fg(), true);
        st.setPadding(a.dp(14), a.dp(6), a.dp(14), 0);
        c2.addView(st);
        List<long[]> log = BattLog.load(a.prefs);
        long nowH = System.currentTimeMillis() / 3600000L;
        float[] lv, sc;
        String[] xl;
        float scMax;
        if (batTab == 0) {
            lv = new float[24]; sc = new float[24]; xl = new String[24];
            java.util.Arrays.fill(lv, -1f);
            for (long[] r : log) {
                int idx = (int) (r[0] - (nowH - 23));
                if (idx >= 0 && idx < 24) { lv[idx] = r[1]; sc[idx] = r[2] / 60f; }
            }
            java.util.Calendar cal = java.util.Calendar.getInstance();
            int hr = cal.get(java.util.Calendar.HOUR_OF_DAY);
            for (int i = 0; i < 24; i += 3) xl[i] = String.valueOf(((hr - 23 + i) % 24 + 24) % 24);
            scMax = 60f;
        } else {
            lv = new float[10]; sc = new float[10]; xl = new String[10];
            java.util.Arrays.fill(lv, -1f);
            int[] cnt = new int[10];
            for (long[] r : log) {
                int idx = (int) ((r[0] - (nowH - 239)) / 24);
                if (idx >= 0 && idx < 10) {
                    if (r[1] >= 0) { lv[idx] = (lv[idx] < 0 ? 0 : lv[idx] * cnt[idx]) + r[1]; cnt[idx]++; lv[idx] /= cnt[idx]; }
                    sc[idx] += r[2] / 60f;
                }
            }
            java.util.Calendar cal = java.util.Calendar.getInstance();
            for (int i = 0; i < 10; i++) { java.util.Calendar d = (java.util.Calendar) cal.clone(); d.add(java.util.Calendar.DAY_OF_YEAR, i - 9); xl[i] = String.valueOf(d.get(java.util.Calendar.DAY_OF_MONTH)); }
            scMax = 480f;
        }
        TextView t1 = Ui.tv(a, "Battery Level", 12.5f, Theme.fg(), true);
        t1.setPadding(a.dp(14), a.dp(10), a.dp(14), a.dp(4));
        c2.addView(t1);
        LinearLayout.LayoutParams cl = new LinearLayout.LayoutParams(M, a.dp(86));
        cl.setMargins(a.dp(14), 0, a.dp(8), a.dp(4));
        c2.addView(new Bars(lv, 100f, 0xFF30D158, xl, "100%", "50%", "0%"), cl);
        TextView t2 = Ui.tv(a, "Screen On Usage", 12.5f, Theme.fg(), true);
        t2.setPadding(a.dp(14), a.dp(8), a.dp(14), a.dp(4));
        c2.addView(t2);
        LinearLayout.LayoutParams cl2 = new LinearLayout.LayoutParams(M, a.dp(86));
        cl2.setMargins(a.dp(14), 0, a.dp(8), a.dp(12));
        c2.addView(new Bars(sc, scMax, 0xFF0A84FF, xl, batTab == 0 ? "60m" : "8h", batTab == 0 ? "30m" : "4h", "0m"), cl2);
        LinearLayout c3 = card();
        row(c3, "Pengaturan baterai Android", "Penggunaan baterai per aplikasi", button("Buka", () -> a.startExternal(new Intent(Intent.ACTION_POWER_USAGE_SUMMARY))));
        row(c3, "Catatan", "Grafik dicatat sejak launcher aktif; waktu layar dihitung selama launcher berjalan.", null);
    }

    // ----- Displays -----
    void displays() {
        LinearLayout pv = new LinearLayout(a);
        pv.setOrientation(LinearLayout.VERTICAL);
        pv.setGravity(Gravity.CENTER_HORIZONTAL);
        pv.setBackground(Ui.rr(Theme.card(), a.dp(12)));
        pv.setPadding(0, a.dp(12), 0, a.dp(10));
        ImageView wp = new ImageView(a);
        wp.setScaleType(ImageView.ScaleType.CENTER_CROP);
        Bitmap b = a.wall.getSharp();
        if (b != null) wp.setImageBitmap(b);
        wp.setBackground(Ui.rr(0xFF000000, a.dp(8)));
        wp.setPadding(a.dp(2), a.dp(2), a.dp(2), a.dp(2));
        pv.addView(wp, new LinearLayout.LayoutParams(a.dp(120), a.dp(72)));
        TextView nm = Ui.tv(a, "Built-in Display", 13, Theme.fg(), true);
        nm.setPadding(0, a.dp(6), 0, 0);
        pv.addView(nm);
        pv.addView(Ui.tv(a, a.getResources().getDisplayMetrics().widthPixels + " \u00D7 " + a.getResources().getDisplayMetrics().heightPixels, 11, Theme.sub(), false));
        LinearLayout.LayoutParams pl = new LinearLayout.LayoutParams(M, W);
        pl.bottomMargin = a.dp(12);
        detail.addView(pv, pl);
        LinearLayout c = card();
        LinearLayout ch = new LinearLayout(a);
        ch.setGravity(Gravity.CENTER);
        final float[] sc = {1.25f, 1.12f, 1.0f, 0.9f, 0.8f};
        String[] lb = {"Larger Text", "", "Default", "", "More Space"};
        float cur = a.prefs.getFloat("ui_scale", 1.0f);
        for (int i = 0; i < 5; i++) {
            final float v = sc[i];
            View t = gradThumb(0xFFEAF2FF, 0xFFBBD2F2);
            ch.addView(choice(lb[i], t, Math.abs(cur - v) < 0.01f, () -> { a.prefs.edit().putFloat("ui_scale", v).apply(); a.restartWith("Settings:Displays"); }));
        }
        row(c, "Resolution", "Skala tampilan launcher", ch);
        LinearLayout c2 = card();
        float br = a.prefs.getFloat("brightness", -1f);
        if (br < 0) br = 0.7f;
        row(c2, "Brightness", "Kecerahan layar saat launcher aktif", sliderRow(br, "", "", v -> {
            float val = Math.max(0.03f, v);
            a.prefs.edit().putFloat("brightness", val).apply();
            android.view.WindowManager.LayoutParams lp = a.getWindow().getAttributes();
            lp.screenBrightness = val;
            a.getWindow().setAttributes(lp);
        }));
        row(c2, "Automatically adjust brightness", "Diatur oleh pengaturan Android", button("Atur", () -> a.startExternal(new Intent(Settings.ACTION_DISPLAY_SETTINGS))));
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
