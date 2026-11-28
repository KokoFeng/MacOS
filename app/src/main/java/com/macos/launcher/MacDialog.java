package com.macos.launcher;

import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.animation.PathInterpolator;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.Calendar;
import java.util.Locale;

/** macOS Tahoe style sheets that replace the stock Android dialogs: text prompt, confirm, date and time. */
public class MacDialog {
    public interface Text { void ok(String s); }
    public interface Date { void ok(int y, int m, int d); }
    public interface Time { void ok(int h, int m); }

    // ------------------------------------------------------------ building blocks
    static FrameLayout layer(MainActivity a) {
        FrameLayout sc = new FrameLayout(a);
        sc.setBackgroundColor(0x59000000);
        sc.setClickable(true);
        a.screen.addView(sc, new FrameLayout.LayoutParams(-1, -1));
        if (a.kb != null) a.kb.host.bringToFront();
        sc.setAlpha(0f);
        sc.animate().alpha(1f).setDuration(150).start();
        return sc;
    }

    static LinearLayout card(MainActivity a, FrameLayout sc, int wdp) {
        LinearLayout card = new LinearLayout(a);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER_HORIZONTAL);
        card.setPadding(a.dp(22), a.dp(20), a.dp(22), a.dp(18));
        GradientDrawable g = new GradientDrawable();
        g.setColor(Theme.dark ? 0xF2303033 : 0xF2F7F7F9);
        g.setCornerRadius(a.dp(26));
        g.setStroke(1, Theme.dark ? 0x33FFFFFF : 0x1F000000);
        card.setBackground(g);
        card.setElevation(a.dp(24));
        card.setClickable(true);
        sc.addView(card, new FrameLayout.LayoutParams(a.dp(wdp), -2, Gravity.CENTER));
        card.setScaleX(1.1f);
        card.setScaleY(1.1f);
        card.animate().scaleX(1f).scaleY(1f).setDuration(300).setInterpolator(new PathInterpolator(.2f, 1.1f, .3f, 1f)).start();
        return card;
    }

    static void icon(MainActivity a, LinearLayout card, String res) {
        try {
            ImageView ic = new ImageView(a);
            ic.setImageDrawable(a.getDrawable(a.res(res)));
            card.addView(ic, new LinearLayout.LayoutParams(a.dp(52), a.dp(52)));
        } catch (Throwable t) { }
    }

    static TextView title(MainActivity a, LinearLayout card, String s) {
        TextView t = Ui.tv(a, s, 15, Theme.fg(), true);
        t.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams l = new LinearLayout.LayoutParams(-1, -2);
        l.topMargin = a.dp(10);
        card.addView(t, l);
        return t;
    }

    static void close(final MainActivity a, final FrameLayout sc) {
        if (a.kb != null) a.kb.hide();
        sc.animate().alpha(0f).setDuration(130).withEndAction(() -> a.screen.removeView(sc)).start();
    }

    static TextView button(MainActivity a, String label, boolean primary, boolean danger, View.OnClickListener r) {
        TextView b = Ui.tv(a, label, 13.5f, primary || danger ? Color.WHITE : Theme.fg(), true);
        b.setGravity(Gravity.CENTER);
        int c = danger ? 0xFFFF453A : primary ? Theme.accent : (Theme.dark ? 0x33FFFFFF : 0x14000000);
        b.setBackground(Ui.rr(c, a.dp(17)));
        b.setOnClickListener(r);
        return b;
    }

    static void buttons(MainActivity a, LinearLayout card, TextView cancel, TextView ok) {
        LinearLayout row = new LinearLayout(a);
        LinearLayout.LayoutParams l = new LinearLayout.LayoutParams(0, a.dp(34), 1f);
        l.rightMargin = a.dp(4);
        row.addView(cancel, l);
        LinearLayout.LayoutParams r = new LinearLayout.LayoutParams(0, a.dp(34), 1f);
        r.leftMargin = a.dp(4);
        row.addView(ok, r);
        LinearLayout.LayoutParams rl = new LinearLayout.LayoutParams(-1, -2);
        rl.topMargin = a.dp(16);
        card.addView(row, rl);
    }

    // ------------------------------------------------------------ prompt
    public static void prompt(final MainActivity a, String title, String init, final Text cb) {
        final FrameLayout sc = layer(a);
        LinearLayout card = card(a, sc, 320);
        icon(a, card, "mac_finder");
        title(a, card, title);
        final EditText et = new EditText(a);
        et.setText(init == null ? "" : init);
        et.setSingleLine(true);
        et.setTextSize(14);
        et.setTextColor(Theme.fg());
        et.setHintTextColor(Theme.sub());
        et.setTypeface(Emoji.font(false));
        et.setSelectAllOnFocus(true);
        et.setPadding(a.dp(12), 0, a.dp(12), 0);
        et.setBackground(Ui.rr(Theme.dark ? 0x2EFFFFFF : 0x12000000, a.dp(10)));
        et.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_DONE);
        LinearLayout.LayoutParams el = new LinearLayout.LayoutParams(-1, a.dp(36));
        el.topMargin = a.dp(12);
        card.addView(et, el);
        final Runnable submit = () -> {
            String s = et.getText().toString().trim();
            close(a, sc);
            if (!s.isEmpty()) cb.ok(s);
        };
        et.setOnEditorActionListener((v, id, e) -> { submit.run(); return true; });
        buttons(a, card, button(a, L.t("Cancel"), false, false, v -> close(a, sc)), button(a, "OK", true, false, v -> submit.run()));
        et.requestFocus();
        et.postDelayed(() -> { if (a.kb != null) { a.kb.setTarget(et); a.kb.show(); } }, 120);
    }

    // ------------------------------------------------------------ confirm
    public static void confirm(final MainActivity a, String msg, String yesLabel, boolean danger, final Runnable yes) {
        final FrameLayout sc = layer(a);
        LinearLayout card = card(a, sc, 300);
        icon(a, card, "mac_finder");
        TextView t = title(a, card, msg);
        t.setTextSize(14);
        buttons(a, card, button(a, L.t("Cancel"), false, false, v -> close(a, sc)),
                button(a, yesLabel, !danger, danger, v -> { close(a, sc); if (yes != null) yes.run(); }));
    }

    // ------------------------------------------------------------ number stepper column
    static LinearLayout stepper(final MainActivity a, final int[] val, final int min, final int max, final TextView out, final String[] names) {
        LinearLayout col = new LinearLayout(a);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setGravity(Gravity.CENTER_HORIZONTAL);
        final Runnable show = () -> out.setText(names != null ? names[val[0] - min] : String.format(Locale.US, "%02d", val[0]));
        View.OnClickListener up = v -> { val[0] = val[0] >= max ? min : val[0] + 1; show.run(); };
        View.OnClickListener dn = v -> { val[0] = val[0] <= min ? max : val[0] - 1; show.run(); };
        TextView u = Ui.tv(a, "\u25B2", 14, Theme.accent, true);
        u.setGravity(Gravity.CENTER);
        u.setOnClickListener(up);
        TextView d = Ui.tv(a, "\u25BC", 14, Theme.accent, true);
        d.setGravity(Gravity.CENTER);
        d.setOnClickListener(dn);
        out.setGravity(Gravity.CENTER);
        out.setBackground(Ui.rr(Theme.dark ? 0x2EFFFFFF : 0x12000000, a.dp(8)));
        show.run();
        col.addView(u, new LinearLayout.LayoutParams(a.dp(70), a.dp(30)));
        col.addView(out, new LinearLayout.LayoutParams(a.dp(70), a.dp(38)));
        col.addView(d, new LinearLayout.LayoutParams(a.dp(70), a.dp(30)));
        return col;
    }

    // ------------------------------------------------------------ date
    public static void date(final MainActivity a, int y, int m0, int d, String clearLabel, final Date cb, final Runnable onClear) {
        final FrameLayout sc = layer(a);
        LinearLayout card = card(a, sc, 330);
        title(a, card, L.t("Date"));
        final int[] dd = {d}, mm = {m0 + 1}, yy = {y};
        String[] mn = new String[12];
        for (int i = 0; i < 12; i++) mn[i] = new java.text.SimpleDateFormat("MMM", L.locale()).format(new java.util.GregorianCalendar(2024, i, 1).getTime());
        LinearLayout row = new LinearLayout(a);
        row.setGravity(Gravity.CENTER);
        row.addView(stepper(a, dd, 1, 31, Ui.tv(a, "", 16, Theme.fg(), true), null));
        row.addView(new View(a), new LinearLayout.LayoutParams(a.dp(8), 1));
        row.addView(stepper(a, mm, 1, 12, Ui.tv(a, "", 16, Theme.fg(), true), mn));
        row.addView(new View(a), new LinearLayout.LayoutParams(a.dp(8), 1));
        TextView yt = Ui.tv(a, "", 16, Theme.fg(), true);
        final int[] yv = {yy[0]};
        row.addView(stepper(a, yv, y - 20, y + 30, yt, null));
        yt.setText(String.valueOf(yv[0]));
        // the year column shows 4 digits
        fixYear(yt, yv);
        LinearLayout.LayoutParams rl = new LinearLayout.LayoutParams(-1, -2);
        rl.topMargin = a.dp(10);
        card.addView(row, rl);
        TextView cancel = button(a, onClear != null ? clearLabel : L.t("Cancel"), false, false, v -> { close(a, sc); if (onClear != null) onClear.run(); });
        TextView ok = button(a, "OK", true, false, v -> {
            Calendar c = Calendar.getInstance();
            c.clear();
            c.set(yv[0], mm[0] - 1, 1);
            int day = Math.min(dd[0], c.getActualMaximum(Calendar.DAY_OF_MONTH));
            close(a, sc);
            cb.ok(yv[0], mm[0] - 1, day);
        });
        buttons(a, card, cancel, ok);
    }

    static void fixYear(final TextView yt, final int[] yv) {
        yt.addTextChangedListener(new android.text.TextWatcher() {
            boolean busy;
            public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
            public void onTextChanged(CharSequence s, int a, int b, int c) { }
            public void afterTextChanged(android.text.Editable e) {
                if (busy) return;
                busy = true;
                String want = String.valueOf(yv[0]);
                if (!e.toString().equals(want)) { e.replace(0, e.length(), want); }
                busy = false;
            }
        });
    }

    // ------------------------------------------------------------ time
    public static void time(final MainActivity a, int h, int m, String extraLabel, final Time cb, final Runnable extra) {
        final FrameLayout sc = layer(a);
        LinearLayout card = card(a, sc, 300);
        title(a, card, L.t("Time"));
        final int[] hh = {h}, mm = {m};
        LinearLayout row = new LinearLayout(a);
        row.setGravity(Gravity.CENTER);
        row.addView(stepper(a, hh, 0, 23, Ui.tv(a, "", 18, Theme.fg(), true), null));
        TextView colon = Ui.tv(a, ":", 20, Theme.fg(), true);
        colon.setGravity(Gravity.CENTER);
        row.addView(colon, new LinearLayout.LayoutParams(a.dp(16), -2));
        row.addView(stepper(a, mm, 0, 59, Ui.tv(a, "", 18, Theme.fg(), true), null));
        LinearLayout.LayoutParams rl = new LinearLayout.LayoutParams(-1, -2);
        rl.topMargin = a.dp(10);
        card.addView(row, rl);
        if (extra != null) {
            TextView ex = button(a, extraLabel, false, false, v -> { close(a, sc); extra.run(); });
            LinearLayout.LayoutParams el = new LinearLayout.LayoutParams(-1, a.dp(34));
            el.topMargin = a.dp(12);
            card.addView(ex, el);
        }
        buttons(a, card, button(a, L.t("Cancel"), false, false, v -> close(a, sc)), button(a, "OK", true, false, v -> { close(a, sc); cb.ok(hh[0], mm[0]); }));
    }
}
