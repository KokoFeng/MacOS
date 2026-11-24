package com.macos.launcher;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.os.BatteryManager;
import android.os.Build;
import android.os.SystemClock;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.view.animation.PathInterpolator;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import java.security.MessageDigest;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** macOS Tahoe lock screen: wallpaper, date + big clock, avatar, name, password pill, hint panel, shake on error. */
public class LockScreen {
    public static final String[] REQ_LABELS = {"Immediately", "5 seconds", "1 minute", "5 minutes", "15 minutes", "1 hour", "4 hours", "8 hours"};
    static final long[] REQ_MS = {0, 5000, 60000, 300000, 900000, 3600000, 14400000, 28800000};
    public static final String[] IDLE_LABELS = {"Never", "1 minute", "2 minutes", "5 minutes", "10 minutes", "30 minutes"};
    static final long[] IDLE_MS = {0, 60000, 120000, 300000, 600000, 1800000};

    final MainActivity a;
    public boolean locked, resumed;
    FrameLayout v;
    LinearLayout top, block, row, panel;
    TextView dateTv, clockTv, nameTv, capTv;
    View avatar, arrow;
    EditText et;
    int fails;
    boolean pendingCheck, needLock;
    long offAt;
    boolean panelOn;

    public LockScreen(MainActivity act) { a = act; }

    // ---------- prefs ----------
    static boolean id() { String l = Locale.getDefault().getLanguage(); return l.equals("in") || l.equals("id"); }
    String t(String en, String idn) { return id() ? idn : en; }
    public boolean hasPw() { return a.prefs.getString("lock_pw", "").length() > 0; }
    public String userName() { return a.prefs.getString("user_name", "User"); }
    static String hash(String pw) {
        try {
            byte[] d = MessageDigest.getInstance("SHA-256").digest(("macos-lock:" + pw).getBytes("UTF-8"));
            StringBuilder sb = new StringBuilder();
            for (byte b : d) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) { return pw; }
    }
    long reqMs() {
        String s = a.prefs.getString("lock_req", "Immediately");
        for (int i = 0; i < REQ_LABELS.length; i++) if (REQ_LABELS[i].equals(s)) return REQ_MS[i];
        return 0;
    }
    long idleMs() {
        String s = a.prefs.getString("lock_idle", "Never");
        for (int i = 0; i < IDLE_LABELS.length; i++) if (IDLE_LABELS[i].equals(s)) return IDLE_MS[i];
        return 0;
    }

    // ---------- lifecycle hooks (called from MainActivity) ----------
    public void onScreenOff() {
        pendingCheck = true;
        offAt = SystemClock.elapsedRealtime();
        if (resumed && hasPw() && reqMs() == 0) lock(false);
    }
    public void onScreenOn() {
        if (pendingCheck && hasPw() && SystemClock.elapsedRealtime() - offAt >= reqMs()) needLock = true;
        pendingCheck = false;
        if (resumed && needLock) { needLock = false; lock(false); }
    }
    public void onResume() {
        resumed = true;
        if (needLock) { needLock = false; if (hasPw()) lock(false); }
        a.h.removeCallbacks(idleCk);
        a.h.postDelayed(idleCk, 5000);
    }
    public void onPause() { resumed = false; a.h.removeCallbacks(idleCk); }
    final Runnable idleCk = new Runnable() {
        @Override public void run() {
            if (!Guard.ok(a)) { Guard.block(a); return; }
            long ms = idleMs();
            if (!locked && resumed && ms > 0 && SystemClock.elapsedRealtime() - a.lastTouch >= ms) lock(true);
            a.h.postDelayed(this, 5000);
        }
    };
    public void onBack() {
        if (panelOn) { togglePanel(); return; }
        if (et != null) { et.clearFocus(); hideIme(); }
    }

    // ---------- build ----------
    int dp(float x) { return a.dp(x); }
    void px(TextView tv, float p) { tv.setTextSize(TypedValue.COMPLEX_UNIT_PX, p); }

    void build() {
        final float H = a.sh;
        v = new FrameLayout(a);
        v.setClickable(true);
        v.setBackgroundColor(0xFF000000);
        android.widget.ImageView bg = new android.widget.ImageView(a);
        bg.setScaleType(android.widget.ImageView.ScaleType.CENTER_CROP);
        if (a.wall.getSharp() != null) bg.setImageBitmap(a.wall.getSharp());
        v.addView(bg, new FrameLayout.LayoutParams(-1, -1));
        View shade = new View(a);
        shade.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0x14000000, 0x00000000, 0x00000000, 0x30000000}));
        v.addView(shade, new FrameLayout.LayoutParams(-1, -1));

        // date + clock
        top = new LinearLayout(a);
        top.setOrientation(LinearLayout.VERTICAL);
        top.setGravity(Gravity.CENTER_HORIZONTAL);
        dateTv = new TextView(a);
        dateTv.setTextColor(0xE8FFFFFF);
        dateTv.setTypeface(Emoji.font(false));
        dateTv.setIncludeFontPadding(false);
        dateTv.setShadowLayer(dp(6), 0, dp(1), 0x33000000);
        px(dateTv, Math.max(dp(13), H * .036f));
        top.addView(dateTv);
        clockTv = new TextView(a);
        clockTv.setIncludeFontPadding(false);
        boolean serif = "Serif".equals(a.prefs.getString("lock_clock", "Sans"));
        clockTv.setTypeface(serif ? Typeface.create("serif", Typeface.BOLD) : Emoji.font(true));
        float cs = H * (serif ? .165f : .15f);
        px(clockTv, cs);
        clockTv.getPaint().setShader(new LinearGradient(0, 0, 0, cs, 0xFFFFFFFF, 0xC8F2F6FF, Shader.TileMode.CLAMP));
        clockTv.setTextColor(0xFFFFFFFF);
        clockTv.setShadowLayer(dp(14), 0, dp(2), 0x2E000000);
        LinearLayout.LayoutParams cl = new LinearLayout.LayoutParams(-2, -2);
        cl.topMargin = Math.round(H * .008f);
        top.addView(clockTv, cl);
        FrameLayout.LayoutParams tl = new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        tl.topMargin = Math.round(H * .085f);
        v.addView(top, tl);

        buildStatus();

        // login block
        block = new LinearLayout(a);
        block.setOrientation(LinearLayout.VERTICAL);
        block.setGravity(Gravity.CENTER_HORIZONTAL);
        boolean showUser = a.prefs.getBoolean("lock_name_on", true);
        int as = Math.max(dp(44), Math.min(dp(64), Math.round(H * .105f)));
        avatar = new Avatar(a);
        nameTv = new TextView(a);
        nameTv.setText(userName());
        nameTv.setTextColor(0xFFFFFFFF);
        nameTv.setTypeface(Emoji.font(true));
        nameTv.setTextSize(13.5f);
        nameTv.setShadowLayer(dp(4), 0, 0, 0x44000000);
        if (showUser) {
            block.addView(avatar, new LinearLayout.LayoutParams(as, as));
            LinearLayout.LayoutParams nl = new LinearLayout.LayoutParams(-2, -2);
            nl.topMargin = dp(6);
            block.addView(nameTv, nl);
        }
        buildPanel();
        block.addView(panel, new LinearLayout.LayoutParams(dp(300), -2));

        // password pill + ? button
        row = new LinearLayout(a);
        row.setGravity(Gravity.CENTER_VERTICAL);
        final FrameLayout pill = new FrameLayout(a);
        pill.setBackground(new GlassDrawable(pill, dp(15), dp(1), 0x66404044));
        et = new EditText(a);
        et.setBackground(null);
        et.setSingleLine(true);
        et.setTextColor(0xFFFFFFFF);
        et.setHintTextColor(0x99FFFFFF);
        et.setTypeface(Emoji.font(false));
        et.setHint(t("Enter Password", "Ketik Kata Sandi"));
        et.setTextSize(13.5f);
        et.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        et.setImeOptions(EditorInfo.IME_ACTION_GO | EditorInfo.IME_FLAG_NO_EXTRACT_UI | EditorInfo.IME_FLAG_NO_FULLSCREEN);
        et.setPadding(dp(13), 0, dp(32), 0);
        et.setGravity(Gravity.CENTER_VERTICAL);
        if (Build.VERSION.SDK_INT >= 29) {
            GradientDrawable cur = new GradientDrawable();
            cur.setColor(0xFFFFFFFF);
            cur.setSize(Math.max(2, dp(1.5f)), dp(16));
            et.setTextCursorDrawable(cur);
        }
        et.setOnEditorActionListener((tv, id, ev) -> { submit(); return true; });
        pill.addView(et, new FrameLayout.LayoutParams(-1, -1));
        FrameLayout ar = new FrameLayout(a);
        arrow = ar;
        GradientDrawable ag = new GradientDrawable();
        ag.setShape(GradientDrawable.OVAL);
        ag.setColor(0x73FFFFFF);
        ar.setBackground(ag);
        ar.addView(Sf.img(a, "arrow.right", 0xFFFFFFFF), new FrameLayout.LayoutParams(dp(11), dp(11), Gravity.CENTER));
        ar.setOnClickListener(x -> submit());
        FrameLayout.LayoutParams arl = new FrameLayout.LayoutParams(dp(22), dp(22), Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        arl.rightMargin = dp(4);
        pill.addView(ar, arl);
        final boolean noPw = !hasPw();
        ar.setVisibility(noPw ? View.VISIBLE : View.GONE);
        et.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int x, int y, int z) {}
            public void onTextChanged(CharSequence s, int x, int y, int z) {}
            public void afterTextChanged(Editable e) { arrow.setVisibility(e.length() > 0 || noPw ? View.VISIBLE : View.GONE); }
        });
        row.addView(pill, new LinearLayout.LayoutParams(dp(176), dp(30)));
        TextView q = new TextView(a);
        q.setText("?");
        q.setTextColor(0xE6FFFFFF);
        q.setTextSize(14);
        q.setTypeface(Emoji.font(true));
        q.setGravity(Gravity.CENTER);
        GradientDrawable qg = new GradientDrawable();
        qg.setShape(GradientDrawable.OVAL);
        qg.setColor(0x00000000);
        qg.setStroke(Math.max(2, dp(1.6f)), 0xB3FFFFFF);
        q.setBackground(qg);
        q.setOnClickListener(x -> togglePanel());
        LinearLayout.LayoutParams ql = new LinearLayout.LayoutParams(dp(28), dp(28));
        ql.leftMargin = dp(8);
        row.addView(q, ql);
        LinearLayout.LayoutParams rl = new LinearLayout.LayoutParams(-2, -2);
        rl.topMargin = dp(9);
        block.addView(row, rl);

        capTv = new TextView(a);
        capTv.setText(noPw ? t("Tap the arrow to unlock", "Ketuk panah untuk membuka") : t("Your password is required to log in", "Kata sandi Anda diperlukan untuk masuk"));
        capTv.setTextColor(0x99FFFFFF);
        capTv.setTextSize(11);
        capTv.setGravity(Gravity.CENTER);
        capTv.setTypeface(Emoji.font(false));
        LinearLayout.LayoutParams cpl = new LinearLayout.LayoutParams(dp(230), -2);
        cpl.topMargin = dp(9);
        block.addView(capTv, cpl);
        FrameLayout.LayoutParams bl = new FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        bl.bottomMargin = Math.round(H * .13f);
        v.addView(block, bl);

        // message when locked
        String msg = a.prefs.getString("lock_msg", "");
        if (a.prefs.getBoolean("lock_msg_on", false) && msg.length() > 0) {
            TextView m = new TextView(a);
            m.setText(msg);
            m.setTextColor(0xE6FFFFFF);
            m.setTextSize(12.5f);
            m.setGravity(Gravity.CENTER);
            m.setShadowLayer(dp(4), 0, 0, 0x55000000);
            FrameLayout.LayoutParams ml = new FrameLayout.LayoutParams(dp(420), -2, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
            ml.bottomMargin = dp(10);
            v.addView(m, ml);
        }
        tickClock();
    }

    void buildStatus() {
        LinearLayout st = new LinearLayout(a);
        st.setGravity(Gravity.CENTER_VERTICAL);
        TextView abc = new TextView(a);
        abc.setText("ABC");
        abc.setTextColor(0xFFFFFFFF);
        abc.setTextSize(12);
        abc.setTypeface(Emoji.font(true));
        st.addView(abc);
        View kb = new Glyph(a, 0);
        LinearLayout.LayoutParams kl = new LinearLayout.LayoutParams(dp(20), dp(14));
        kl.leftMargin = dp(5); kl.rightMargin = dp(12);
        st.addView(kb, kl);
        IconView bat = new IconView(a, IconView.BATT, Color.WHITE);
        Intent b = a.registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        if (b != null) {
            int l = b.getIntExtra(BatteryManager.EXTRA_LEVEL, -1), s = b.getIntExtra(BatteryManager.EXTRA_SCALE, 100);
            if (l >= 0) bat.setLevel(l / (float) s);
        }
        st.addView(bat, new LinearLayout.LayoutParams(dp(26), dp(18)));
        st.addView(new IconView(a, IconView.WIFI, Color.WHITE), new LinearLayout.LayoutParams(dp(22), dp(18)));
        LinearLayout.LayoutParams pl = new LinearLayout.LayoutParams(dp(16), dp(16));
        pl.leftMargin = dp(10);
        st.addView(new Glyph(a, 1), pl);
        FrameLayout.LayoutParams sl = new FrameLayout.LayoutParams(-2, dp(20), Gravity.TOP | Gravity.RIGHT);
        sl.topMargin = dp(10) + a.topInset / 2;
        sl.rightMargin = dp(16);
        v.addView(st, sl);
    }

    void buildPanel() {
        panel = new LinearLayout(a);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setGravity(Gravity.CENTER_HORIZONTAL);
        panel.setVisibility(View.GONE);
        String hint = a.prefs.getString("lock_hint", "");
        if (a.prefs.getBoolean("lock_hints_on", true) && hint.length() > 0) {
            TextView h = new TextView(a);
            h.setText(t("Hint: ", "Petunjuk: ") + hint);
            h.setTextColor(0xFFFFFFFF);
            h.setTextSize(13);
            h.setGravity(Gravity.CENTER);
            h.setTypeface(Emoji.font(true));
            h.setShadowLayer(dp(4), 0, 0, 0x55000000);
            panel.addView(h);
        }
        TextView tx = new TextView(a);
        tx.setText(t("If you forget your password, you can use your Apple Account or recovery key to reset it. The lock password will be cleared to continue.",
                "Jika Anda lupa kata sandi, Anda dapat menggunakan Akun Apple atau kunci pemulihan Anda untuk mengatur ulang. Kata sandi kunci layar akan dihapus untuk melanjutkan."));
        tx.setTextColor(0xFFFFFFFF);
        tx.setTextSize(12.5f);
        tx.setGravity(Gravity.CENTER);
        tx.setShadowLayer(dp(4), 0, 0, 0x55000000);
        LinearLayout.LayoutParams txl = new LinearLayout.LayoutParams(-1, -2);
        txl.topMargin = dp(4);
        panel.addView(tx, txl);
        LinearLayout rs = new LinearLayout(a);
        rs.setGravity(Gravity.CENTER_VERTICAL);
        FrameLayout ic = new FrameLayout(a);
        GradientDrawable g = new GradientDrawable();
        g.setShape(GradientDrawable.OVAL);
        g.setColor(0xFFFFFFFF);
        ic.setBackground(g);
        ic.addView(Sf.img(a, "arrow.right", 0xFF303034), new FrameLayout.LayoutParams(dp(11), dp(11), Gravity.CENTER));
        rs.addView(ic, new LinearLayout.LayoutParams(dp(22), dp(22)));
        TextView rt = new TextView(a);
        rt.setText(t("Reset Password", "Atur Ulang Kata Sandi"));
        rt.setTextColor(0xFFFFFFFF);
        rt.setTextSize(13.5f);
        rt.setPadding(dp(8), 0, 0, 0);
        rs.addView(rt);
        rs.setOnClickListener(x -> resetPw());
        LinearLayout.LayoutParams rsl = new LinearLayout.LayoutParams(-2, -2);
        rsl.topMargin = dp(8);
        rsl.bottomMargin = dp(10);
        panel.addView(rs, rsl);
    }

    void togglePanel() {
        panelOn = !panelOn;
        panel.setVisibility(panelOn ? View.VISIBLE : View.GONE);
        if (panelOn) { panel.setAlpha(0); panel.animate().alpha(1).setDuration(180).start(); }
        avatar.setVisibility(panelOn ? View.GONE : View.VISIBLE);
        nameTv.setVisibility(panelOn ? View.GONE : View.VISIBLE);
    }

    void resetPw() {
        new AlertDialog.Builder(a).setMessage(t("Clear the lock screen password?", "Hapus kata sandi kunci layar?"))
                .setPositiveButton(t("Reset", "Atur Ulang"), (d, w) -> {
                    a.prefs.edit().remove("lock_pw").remove("lock_hint").apply();
                    unlock();
                }).setNegativeButton(t("Cancel", "Batal"), null).show();
    }

    // ---------- clock ----------
    final Runnable clockTick = new Runnable() {
        @Override public void run() { tickClock(); if (locked) a.h.postDelayed(this, 1000); }
    };
    void tickClock() {
        if (dateTv == null) return;
        boolean h24 = android.text.format.DateFormat.is24HourFormat(a);
        String tp = h24 ? (id() ? "HH.mm" : "HH:mm") : (id() ? "h.mm" : "h:mm");
        Date d = new Date();
        dateTv.setText(new SimpleDateFormat(id() ? "EEE d MMM" : "EEE MMM d", Locale.getDefault()).format(d));
        clockTv.setText(new SimpleDateFormat(tp, Locale.getDefault()).format(d));
    }

    // ---------- lock / unlock ----------
    void hideIme() {
        try { ((InputMethodManager) a.getSystemService(android.content.Context.INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(v.getWindowToken(), 0); } catch (Throwable t) { }
    }

    public void lock(boolean animate) {
        if (locked) return;
        a.dismissOverlays();
        locked = true; fails = 0; panelOn = false;
        if (a.tp != null) a.tp.setEnabled(false);
        a.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN | WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN);
        build();
        a.screen.addView(v, new FrameLayout.LayoutParams(-1, -1));
        a.h.removeCallbacks(clockTick);
        a.h.postDelayed(clockTick, 1000);
        if (!animate) { a.root.setVisibility(View.INVISIBLE); return; }
        final PathInterpolator ip = new PathInterpolator(.22f, .9f, .25f, 1f);
        v.setAlpha(0f);
        v.setPivotX(a.sw / 2f); v.setPivotY(a.sh / 2f);
        v.setScaleX(1.05f); v.setScaleY(1.05f);
        a.root.setPivotX(a.sw / 2f); a.root.setPivotY(a.sh / 2f);
        a.root.animate().setStartDelay(0).alpha(0f).scaleX(.96f).scaleY(.96f).setDuration(420).setInterpolator(ip).start();
        v.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(480).setInterpolator(ip).withEndAction(() -> {
            a.root.setVisibility(View.INVISIBLE);
            a.root.setAlpha(1f); a.root.setScaleX(1f); a.root.setScaleY(1f);
        }).start();
        top.setAlpha(0f); top.setTranslationY(-dp(8));
        top.animate().alpha(1f).translationY(0).setStartDelay(80).setDuration(520).setInterpolator(ip).start();
        block.setAlpha(0f); block.setTranslationY(dp(12));
        block.animate().alpha(1f).translationY(0).setStartDelay(200).setDuration(480).setInterpolator(ip).start();
    }

    public void unlock() {
        if (!locked || v == null) return;
        locked = false;
        hideIme();
        a.h.removeCallbacks(clockTick);
        final PathInterpolator ip = new PathInterpolator(.22f, .9f, .25f, 1f);
        final View lv = v;
        a.root.setVisibility(View.VISIBLE);
        a.root.setPivotX(a.sw / 2f); a.root.setPivotY(a.sh / 2f);
        a.root.setAlpha(0f); a.root.setScaleX(1.045f); a.root.setScaleY(1.045f);
        a.root.animate().alpha(1f).scaleX(1f).scaleY(1f).setStartDelay(60).setDuration(460).setInterpolator(ip).start();
        block.animate().setStartDelay(0).alpha(0f).translationY(-dp(10)).setDuration(220).start();
        top.animate().setStartDelay(0).alpha(0f).translationY(-dp(14)).setDuration(300).start();
        lv.animate().alpha(0f).scaleX(1.06f).scaleY(1.06f).setDuration(420).setInterpolator(ip).setListener(new AnimatorListenerAdapter() {
            @Override public void onAnimationEnd(Animator an) {
                a.screen.removeView(lv);
                a.root.setAlpha(1f); a.root.setScaleX(1f); a.root.setScaleY(1f);
                a.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING);
                if (a.tp != null) a.tp.setEnabled(a.prefs.getBoolean("tp_on", false));
                a.lastTouch = SystemClock.elapsedRealtime();
            }
        }).start();
        v = null; et = null;
    }

    void submit() {
        if (!locked || et == null) return;
        String saved = a.prefs.getString("lock_pw", "");
        String s = et.getText().toString();
        if (saved.isEmpty() || hash(s).equals(saved)) { unlock(); return; }
        fails++;
        et.setText("");
        float d = dp(12);
        ObjectAnimator.ofFloat(row, "translationX", 0, -d, d * .85f, -d * .65f, d * .45f, -d * .22f, 0).setDuration(430).start();
        if (fails >= 3 && !panelOn && a.prefs.getBoolean("lock_hints_on", true) && a.prefs.getString("lock_hint", "").length() > 0) togglePanel();
    }

    // ---------- change password dialog (System Settings) ----------
    EditText field(String hint, boolean pw) {
        EditText e = new EditText(a);
        e.setHint(hint);
        e.setSingleLine(true);
        e.setTextSize(13);
        e.setTextColor(Theme.fg());
        e.setHintTextColor(Theme.sub());
        e.setBackground(Ui.rr(Theme.dark ? 0x2EFFFFFF : 0x12000000, dp(9)));
        e.setPadding(dp(12), 0, dp(12), 0);
        e.setInputType(InputType.TYPE_CLASS_TEXT | (pw ? InputType.TYPE_TEXT_VARIATION_PASSWORD : 0));
        return e;
    }

    public void passwordDialog(final Runnable done) {
        a.dismissOverlays();
        a.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN);
        FrameLayout sc = a.scrim();
        LinearLayout c = new LinearLayout(a);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setPadding(dp(18), dp(16), dp(18), dp(14));
        c.setBackground(Ui.rr(Theme.dark ? 0xFF2C2C2E : 0xFFF6F6F8, dp(20)));
        c.setClickable(true);
        c.setElevation(dp(20));
        TextView title = Ui.tv(a, hasPw() ? "Change Password" : "Set Password", 15, Theme.fg(), true);
        title.setGravity(Gravity.CENTER);
        c.addView(title);
        final EditText cur = hasPw() ? field("Current Password", true) : null;
        final EditText n1 = field("New Password", true), n2 = field("Verify", true), hn = field("Password Hint (recommended)", false);
        EditText[] fs = cur != null ? new EditText[]{cur, n1, n2, hn} : new EditText[]{n1, n2, hn};
        for (EditText f : fs) {
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(34));
            lp.topMargin = dp(8);
            c.addView(f, lp);
        }
        LinearLayout br = new LinearLayout(a);
        br.setGravity(Gravity.RIGHT);
        TextView cancel = Ui.tv(a, "Cancel", 13, Theme.fg(), false);
        cancel.setPadding(dp(16), dp(6), dp(16), dp(6));
        cancel.setBackground(Ui.rr(Theme.dark ? 0x2EFFFFFF : 0x14000000, dp(12)));
        cancel.setOnClickListener(x -> a.dismissOverlays());
        TextView ok = Ui.tv(a, hasPw() ? "Change Password" : "Set Password", 13, 0xFFFFFFFF, true);
        ok.setPadding(dp(16), dp(6), dp(16), dp(6));
        ok.setBackground(Ui.rr(Theme.accent, dp(12)));
        ok.setOnClickListener(x -> {
            if (cur != null && !hash(cur.getText().toString()).equals(a.prefs.getString("lock_pw", ""))) {
                Toast.makeText(a, "Current password is incorrect", Toast.LENGTH_SHORT).show(); return;
            }
            String p1 = n1.getText().toString();
            if (p1.isEmpty()) { Toast.makeText(a, "Enter a new password", Toast.LENGTH_SHORT).show(); return; }
            if (!p1.equals(n2.getText().toString())) { Toast.makeText(a, "Passwords do not match", Toast.LENGTH_SHORT).show(); return; }
            a.prefs.edit().putString("lock_pw", hash(p1)).putString("lock_hint", hn.getText().toString().trim()).apply();
            a.dismissOverlays();
            if (done != null) done.run();
        });
        LinearLayout.LayoutParams okl = new LinearLayout.LayoutParams(-2, -2);
        okl.leftMargin = dp(8);
        br.addView(cancel);
        br.addView(ok, okl);
        LinearLayout.LayoutParams brl = new LinearLayout.LayoutParams(-1, -2);
        brl.topMargin = dp(14);
        c.addView(br, brl);
        ScrollView sv = new ScrollView(a);
        sv.setVerticalScrollBarEnabled(false);
        sv.addView(c);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(dp(320), -2, Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        lp.topMargin = a.mbH() + dp(8);
        sc.addView(sv, lp);
        sv.setAlpha(0); sv.setScaleX(.96f); sv.setScaleY(.96f);
        sv.animate().alpha(1).scaleX(1).scaleY(1).setDuration(180).start();
    }

    // ---------- small drawn glyphs ----------
    static class Avatar extends View {
        final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        Avatar(android.content.Context c) { super(c); }
        @Override protected void onDraw(Canvas c) {
            float w = getWidth(), h = getHeight(), r = Math.min(w, h) / 2f;
            Path clip = new Path();
            clip.addCircle(w / 2f, h / 2f, r, Path.Direction.CW);
            c.save();
            c.clipPath(clip);
            p.setShader(new LinearGradient(0, 0, 0, h, 0xFFC9CDD6, 0xFF8D929E, Shader.TileMode.CLAMP));
            c.drawRect(0, 0, w, h, p);
            p.setShader(null);
            p.setColor(0xF2FFFFFF);
            c.drawCircle(w / 2f, h * .38f, r * .3f, p);
            c.drawOval(new RectF(w / 2f - r * .62f, h * .64f, w / 2f + r * .62f, h * 1.3f), p);
            c.restore();
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(Math.max(1.5f, r * .04f));
            p.setColor(0x66FFFFFF);
            c.drawCircle(w / 2f, h / 2f, r - p.getStrokeWidth() / 2f, p);
            p.setStyle(Paint.Style.FILL);
        }
    }

    static class Glyph extends View {
        final int type;
        final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        Glyph(android.content.Context c, int type) { super(c); this.type = type; }
        @Override protected void onDraw(Canvas c) {
            float w = getWidth(), h = getHeight(), s = Math.min(w, h);
            p.setColor(0xFFFFFFFF);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeCap(Paint.Cap.ROUND);
            p.setStrokeWidth(Math.max(1.2f, s * .09f));
            if (type == 0) { // keyboard
                float sw = p.getStrokeWidth();
                c.drawRoundRect(new RectF(sw, sw, w - sw, h - sw), h * .2f, h * .2f, p);
                p.setStyle(Paint.Style.FILL);
                for (int r = 0; r < 2; r++) for (int i = 0; i < 4; i++)
                    c.drawCircle(w * (.22f + i * .185f), h * (.36f + r * .22f), s * .045f, p);
                c.drawRoundRect(new RectF(w * .3f, h * .66f, w * .7f, h * .74f), 2, 2, p);
            } else { // power
                float cx = w / 2f, cy = h / 2f + s * .04f, r = s * .36f;
                c.drawArc(new RectF(cx - r, cy - r, cx + r, cy + r), -60, 300, false, p);
                c.drawLine(cx, cy - r * 1.15f, cx, cy - r * .1f, p);
            }
        }
    }
}
