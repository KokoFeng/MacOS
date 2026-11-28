package com.macos.launcher;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.drawable.GradientDrawable;
import android.speech.RecognizerIntent;
import android.text.Editable;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.SoundEffectConstants;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The launcher's own on-screen keyboard. It replaces the system keyboard for every text field inside the launcher
 * and comes in two designs: iPad (full, with number row) and iPhone (compact, glass keys). Phone/tablet is detected
 * automatically and can be switched in System Settings. Includes QuickType suggestions, emoji (with search),
 * personal stickers, memoji-style faces, dictation, language switching and a floating mode.
 */
public class MacKeyboard {
    // ---------------------------------------------------------------- languages
    static final String[] LANG_NAMES = {"English (US)", "Bahasa Indonesia", "Espa\u00f1ol", "Fran\u00e7ais", "Portugu\u00eas", "\u0420\u0443\u0441\u0441\u043a\u0438\u0439",
            "\u0627\u0644\u0639\u0631\u0628\u064a\u0629", "\u0939\u093f\u0928\u094d\u0926\u0940", "\ud55c\uad6d\uc5b4", "Ti\u1ebfng Vi\u1ec7t", "Bahasa Melayu"};
    static final String[] LANG_TAGS = {"en-US", "id-ID", "es-ES", "fr-FR", "pt-BR", "ru-RU", "ar-SA", "hi-IN", "ko-KR", "vi-VN", "ms-MY"};
    static final String[][] LAYOUT = {
        {"qwertyuiop", "asdfghjkl", "zxcvbnm"},
        {"qwertyuiop", "asdfghjkl", "zxcvbnm"},
        {"qwertyuiop", "asdfghjkl\u00f1", "zxcvbnm"},
        {"azertyuiop", "qsdfghjklm", "wxcvbn"},
        {"qwertyuiop", "asdfghjkl\u00e7", "zxcvbnm"},
        {"\u0439\u0446\u0443\u043a\u0435\u043d\u0433\u0448\u0449\u0437\u0445", "\u0444\u044b\u0432\u0430\u043f\u0440\u043e\u043b\u0434\u0436\u044d", "\u044f\u0447\u0441\u043c\u0438\u0442\u044c\u0431\u044e"},
        {"\u0636\u0635\u062b\u0642\u0641\u063a\u0639\u0647\u062e\u062d\u062c\u062f", "\u0634\u0633\u064a\u0628\u0644\u0627\u062a\u0646\u0645\u0643\u0637", "\u0626\u0621\u0624\u0631\u0644\u0627\u0649\u0629\u0648\u0632\u0638"},
        {"\u094c\u0948\u093e\u0940\u0942\u092c\u0939\u0917\u0926\u091c\u0921", "\u094b\u0947\u0902\u093f\u0941\u092a\u0930\u0915\u0924\u091a\u091f", "\u0949\u0902\u092e\u0928\u0935\u0932\u0938\u092f\u0936"},
        {"\u3142\u3148\u3137\u3131\u3145\u315b\u3155\u3151\u3150\u3154", "\u3141\u3134\u3147\u3139\u314e\u3157\u3153\u314f\u3163", "\u314b\u314c\u314a\u314d\u3160\u315c\u3161"},
        {"qwertyuiop", "asdfghjkl", "zxcvbnm"},
        {"qwertyuiop", "asdfghjkl", "zxcvbnm"},
    };
    static final String[][] ALT = {
        {"a", "\u00e0\u00e1\u00e2\u00e4\u00e6\u00e3\u00e5\u0101"}, {"e", "\u00e8\u00e9\u00ea\u00eb\u0113\u0117\u0119"}, {"i", "\u00ee\u00ef\u00ed\u012b\u012f\u00ec"},
        {"o", "\u00f4\u00f6\u00f2\u00f3\u0153\u00f8\u014d\u00f5"}, {"u", "\u00fb\u00fc\u00f9\u00fa\u016b"}, {"c", "\u00e7\u0107\u010d"}, {"n", "\u00f1\u0144"},
        {"s", "\u00df\u015b\u0161"}, {"y", "\u00ff"}, {"z", "\u017e\u017a\u017c"}, {"l", "\u0142"}, {"d", "\u0111\u00f0"},
        {"\u0103", "\u00e2"}, {".", "\u2026"}, {"-", "\u2013\u2014\u2022"}, {"$", "\u20ac\u00a3\u00a5\u20b9\u20a9"}, {"?", "\u00bf"}, {"!", "\u00a1"},
        {"'", "\u2018\u2019"}, {"\"", "\u201c\u201d\u00ab\u00bb"}, {"0", "\u00b0"}, {"/", "\\"}, {"&", "\u00a7"},
    };

    // ---------------------------------------------------------------- word lists for QuickType
    static final String EN = "the be to of and a in that have I it for not on with he as you do at this but his by from they we say her she or an will my one all would there their what so up out if about who get which go me when make can like time no just him know take people into year your good some could them see other than then now look only come its over think also back after use two how our work first well way even new want because any these give day most us is are was were been has had did said am im don't can't i'm hello hi thanks thank please yes okay ok sorry love home today tomorrow tonight morning night where why again very much more here there great really still never always little going should something nothing everything friend family happy birthday welcome message photo video music game phone computer search settings file folder open close save delete copy paste new name password wifi bluetooth weather calendar notes mail safari store keyboard";
    static final String ID = "yang dan di ke dari untuk dengan ini itu ada tidak saya kamu dia kita kami mereka akan sudah belum bisa harus mau apa siapa kapan dimana kenapa bagaimana terima kasih tolong maaf selamat pagi siang sore malam halo hai iya ya tidak nggak gak banget sekali juga lagi aja dong deh sih kok nih tuh lah kan pun atau tapi karena jadi kalau jika saat setelah sebelum sama lebih paling semua banyak sedikit baru lama besar kecil baik buruk bagus rumah orang teman keluarga hari ini besok kemarin sekarang nanti tadi udah masih sedang cuma hanya boleh perlu ingin pergi datang pulang makan minum tidur kerja belajar main lihat dengar bicara tanya jawab kirim terima buka tutup simpan hapus salin tempel pesan foto video musik permainan telepon komputer pencarian pengaturan berkas folder kata sandi cuaca kalender catatan surel toko papan ketik";
    static List<String> dict;

    static void initDict() {
        if (dict != null) return;
        dict = new ArrayList<>();
        for (String w : (EN + " " + ID).split(" ")) if (!w.isEmpty() && !dict.contains(w)) dict.add(w);
    }

    // ---------------------------------------------------------------- state
    final MainActivity a;
    final FrameLayout host;
    final LinearLayout panel;
    EditText target;
    boolean showing, floating, tablet;
    int shift, page, lang;          // page: 0 letters, 1 numbers, 2 symbols, 3 emoji
    long lastShift, lastSpace;
    int emCat = 1;
    boolean emSearch;
    final StringBuilder searchBuf = new StringBuilder();
    final android.os.Handler h = new android.os.Handler();
    TextView bubble;
    FrameLayout altPop;
    static final String[] CAT_ICON = {"\uD83D\uDD58", "\uD83D\uDE00", "\uD83D\uDC3B", "\uD83C\uDF54", "\u26BD", "\uD83D\uDE97", "\uD83D\uDCA1", "\uD83D\uDD23", "\uD83C\uDFF3", "\uD83C\uDFAD", "\uD83D\uDDBC"};
    static String[] emojiCache;

    public MacKeyboard(final MainActivity act) {
        a = act;
        tablet = detectTablet();
        lang = a.prefs.getInt("kb_lang", 0);
        host = new FrameLayout(a);
        host.setClipChildren(false);
        host.setVisibility(View.GONE);
        panel = new LinearLayout(a);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setClickable(true);
        host.addView(panel, new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM));
        a.screen.addView(host, new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM));
        a.getWindow().getDecorView().getViewTreeObserver().addOnGlobalFocusChangeListener((o, n) -> {
            if (n instanceof EditText) {
                EditText e = (EditText) n;
                e.setShowSoftInputOnFocus(false);
                hideSystemIme(e);
                setTarget(e);
                show();
            } else if (o instanceof EditText && showing) {
                h.postDelayed(() -> { View f = a.getCurrentFocus(); if (!(f instanceof EditText)) hide(); }, 120);
            }
        });
    }

    boolean detectTablet() {
        String t = a.prefs.getString("kb_type", "Auto");
        if (t.equals("iPad")) return true;
        if (t.equals("iPhone")) return false;
        return isTabletDevice(a);
    }

    public static boolean isTabletDevice(Context c) {
        return c.getResources().getConfiguration().smallestScreenWidthDp >= 600;
    }

    void hideSystemIme(View v) {
        try { ((InputMethodManager) a.getSystemService(Context.INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(v.getWindowToken(), 0); } catch (Exception e) { }
    }

    void setTarget(EditText e) {
        if (target != e) { target = e; shift = 0; page = 0; emSearch = false; searchBuf.setLength(0); autoCap(); }
    }

    /** Called from MainActivity for taps on an already focused field so it can reopen after being hidden. */
    public void onTouch(MotionEvent e) {
        if (e.getActionMasked() != MotionEvent.ACTION_UP) return;
        View f = a.getCurrentFocus();
        if (!(f instanceof EditText) || showing) return;
        Rect r = new Rect();
        f.getGlobalVisibleRect(r);
        if (r.contains((int) e.getRawX(), (int) e.getRawY())) { setTarget((EditText) f); f.setFocusable(true); ((EditText) f).setShowSoftInputOnFocus(false); show(); }
    }

    public void show() {
        if (target == null) return;
        tablet = detectTablet();
        floating = a.prefs.getBoolean("kb_float", false);
        showing = true;
        host.setVisibility(View.VISIBLE);
        host.bringToFront();
        build();
        host.post(this::pan);
    }

    public void hide() {
        showing = false;
        host.setVisibility(View.GONE);
        unpan();
        dismissAlt();
    }

    // ---------------------------------------------------------------- panning of the screen content
    void pan() {
        if (!showing || target == null || floating) { unpan(); return; }
        int[] loc = new int[2];
        target.getLocationOnScreen(loc);
        float cur = a.screen.getChildAt(0).getTranslationY();
        float baseBottom = loc[1] + target.getHeight() - cur;
        float limit = a.sh - panel.getHeight() - a.dp(10);
        float ty = baseBottom > limit ? limit - baseBottom : 0;
        ty = Math.max(-a.sh * .6f, ty);
        for (int i = 0; i < a.screen.getChildCount(); i++) {
            View c = a.screen.getChildAt(i);
            if (c != host) c.animate().translationY(ty).setDuration(160).start();
        }
    }

    void unpan() {
        for (int i = 0; i < a.screen.getChildCount(); i++) {
            View c = a.screen.getChildAt(i);
            if (c != host && c.getTranslationY() != 0) c.animate().translationY(0).setDuration(160).start();
        }
    }

    // ---------------------------------------------------------------- colours
    boolean dark() { return Theme.dark; }
    int cBg() { return dark() ? 0xF21C1C1E : (tablet ? 0xF5D1D4DB : 0xF0E4E6EC); }
    int cKey() { return dark() ? 0xFF5B5B5F : 0xFFFFFFFF; }
    int cSpecial() { return dark() ? 0xFF3A3A3C : 0xFFAEB4BE; }
    int cText() { return dark() ? 0xFFFFFFFF : 0xFF000000; }
    int cPress() { return dark() ? 0xFF7A7A7F : 0xFFC7CBD3; }

    GradientDrawable keyBg(int color) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(a.dp(tablet ? 7 : 8.5f));
        return g;
    }

    // ---------------------------------------------------------------- build
    int rowHeight(int rows) {
        float avail = Math.min(a.sh * (floating ? .45f : .56f), a.dp(floating ? 230 : 330));
        int rh = (int) ((avail - a.dp(40)) / rows);
        return Math.max(a.dp(30), Math.min(a.dp(floating ? 34 : 50), rh));
    }

    void build() {
        panel.removeAllViews();
        dismissAlt();
        boolean ph = !tablet || floating;
        int pad = a.dp(floating ? 6 : (ph ? 4 : 6));
        panel.setPadding(pad, a.dp(4), pad, a.dp(floating ? 4 : 6));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(cBg());
        float r = a.dp(floating ? 18 : (tablet ? 0 : 14));
        bg.setCornerRadii(new float[]{r, r, r, r, floating ? r : 0, floating ? r : 0, floating ? r : 0, floating ? r : 0});
        panel.setBackground(bg);
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) panel.getLayoutParams();
        if (floating) {
            lp.width = a.dp(330);
            lp.gravity = Gravity.BOTTOM | Gravity.LEFT;
            if (panel.getTag() == null) { panel.setTranslationX((a.sw - a.dp(330)) / 2f); panel.setTranslationY(-a.dp(10)); panel.setTag("f"); }
        } else {
            lp.width = (!tablet) ? Math.min(a.sw, a.dp(620)) : -1;
            lp.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
            panel.setTranslationX(0); panel.setTranslationY(0); panel.setTag(null);
        }
        panel.setLayoutParams(lp);
        panel.setElevation(a.dp(8));

        if (page == 3) { buildEmoji(); }
        else {
            panel.addView(buildBar(), new LinearLayout.LayoutParams(-1, a.dp(emSearch ? 42 : 38)));
            int rows = (tablet && !floating && page == 0) ? 5 : 4;
            int rh = rowHeight(rows);
            if (page == 0) letters(rh); else symbols(rh);
        }
        if (floating) {
            View grab = new View(a);
            GradientDrawable gd = new GradientDrawable();
            gd.setColor(dark() ? 0x66FFFFFF : 0x55000000);
            gd.setCornerRadius(a.dp(3));
            grab.setBackground(gd);
            FrameLayout gw = new FrameLayout(a);
            gw.addView(grab, new FrameLayout.LayoutParams(a.dp(70), a.dp(5), Gravity.CENTER));
            gw.setOnTouchListener(new View.OnTouchListener() {
                float dx, dy;
                @Override public boolean onTouch(View v, MotionEvent e) {
                    switch (e.getActionMasked()) {
                        case MotionEvent.ACTION_DOWN: dx = panel.getTranslationX() - e.getRawX(); dy = panel.getTranslationY() - e.getRawY(); return true;
                        case MotionEvent.ACTION_MOVE:
                            panel.setTranslationX(Math.max(0, Math.min(a.sw - panel.getWidth(), e.getRawX() + dx)));
                            panel.setTranslationY(Math.min(0, Math.max(-(a.sh - panel.getHeight()), e.getRawY() + dy)));
                            return true;
                    }
                    return true;
                }
            });
            panel.addView(gw, new LinearLayout.LayoutParams(-1, a.dp(14)));
        }
    }

    // ---------------------------------------------------------------- suggestion bar
    LinearLayout bar;

    View buildBar() {
        bar = new LinearLayout(a);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        if (emSearch) {
            LinearLayout wrap = new LinearLayout(a);
            wrap.setGravity(Gravity.CENTER_VERTICAL);
            TextView q = Ui.tv(a, searchBuf.length() == 0 ? L.t("Search") : searchBuf.toString(), 14, searchBuf.length() == 0 ? 0xFF8E8E93 : cText(), false);
            q.setPadding(a.dp(10), 0, a.dp(10), 0);
            q.setGravity(Gravity.CENTER_VERTICAL);
            q.setBackground(keyBg(cKey()));
            wrap.addView(q, new LinearLayout.LayoutParams(a.dp(130), -1));
            HorizontalScrollView hs = new HorizontalScrollView(a);
            hs.setHorizontalScrollBarEnabled(false);
            LinearLayout res = new LinearLayout(a);
            res.setGravity(Gravity.CENTER_VERTICAL);
            if (searchBuf.length() > 0) for (final String e : searchEmoji(searchBuf.toString())) res.addView(emojiCell(e, 24, () -> { insert(e); recent(e); }));
            hs.addView(res);
            wrap.addView(hs, new LinearLayout.LayoutParams(0, -1, 1f));
            TextView x = Ui.tv(a, "\u2715", 15, 0xFF8E8E93, true);
            x.setGravity(Gravity.CENTER);
            x.setOnClickListener(v -> { emSearch = false; searchBuf.setLength(0); page = 3; build(); });
            wrap.addView(x, new LinearLayout.LayoutParams(a.dp(36), -1));
            bar.addView(wrap, new LinearLayout.LayoutParams(-1, -1));
            return bar;
        }
        if (tablet && !floating) {
            bar.addView(barBtn("\u21B6", () -> target.onTextContextMenuItem(android.R.id.undo)), new LinearLayout.LayoutParams(a.dp(40), -1));
            bar.addView(barBtn("\u21B7", () -> target.onTextContextMenuItem(android.R.id.redo)), new LinearLayout.LayoutParams(a.dp(40), -1));
            bar.addView(barBtn("\u2398", () -> target.onTextContextMenuItem(android.R.id.paste)), new LinearLayout.LayoutParams(a.dp(40), -1));
        }
        List<String> sg = a.prefs.getBoolean("kb_predict", true) && !isPasswordField() ? suggestions() : new ArrayList<String>();
        for (int i = 0; i < 3; i++) {
            final String w = i < sg.size() ? sg.get(i) : "";
            TextView t = Ui.tv(a, w, 14.5f, cText(), false);
            t.setGravity(Gravity.CENTER);
            t.setSingleLine(true);
            if (!w.isEmpty()) t.setOnClickListener(v -> pickSuggestion(w));
            bar.addView(t, new LinearLayout.LayoutParams(0, -1, 1f));
            if (i < 2) {
                View d = new View(a);
                d.setBackgroundColor(dark() ? 0x33FFFFFF : 0x22000000);
                LinearLayout.LayoutParams dl = new LinearLayout.LayoutParams(1, a.dp(20));
                bar.addView(d, dl);
            }
        }
        if (tablet && !floating) bar.addView(barBtn("Aa", null), new LinearLayout.LayoutParams(a.dp(52), -1));
        return bar;
    }

    TextView barBtn(String s, final Runnable r) {
        TextView t = Ui.tv(a, s, 17, cText(), false);
        t.setGravity(Gravity.CENTER);
        if (r != null) t.setOnClickListener(v -> { r.run(); tick(v); });
        return t;
    }

    boolean isPasswordField() {
        if (target == null) return false;
        int it = target.getInputType() & android.text.InputType.TYPE_MASK_VARIATION;
        return it == android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD || it == android.text.InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
                || it == android.text.InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD;
    }

    // ---------------------------------------------------------------- key rows
    LinearLayout row(int rh) {
        LinearLayout r = new LinearLayout(a);
        r.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, rh);
        lp.topMargin = a.dp(tablet && !floating ? 5 : 6);
        panel.addView(r, lp);
        return r;
    }

    void add(LinearLayout r, View v, float w) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -1, w);
        int m = a.dp(tablet && !floating ? 3 : 2.5f);
        lp.setMargins(m, 0, m, 0);
        r.addView(v, lp);
    }

    void spacer(LinearLayout r, float w) { r.addView(new View(a), new LinearLayout.LayoutParams(0, 1, w)); }

    String cased(String c) { return shift > 0 ? c.toUpperCase(LANG_LOCALE()) : c; }

    Locale LANG_LOCALE() { return Locale.forLanguageTag(LANG_TAGS[lang]); }

    void letters(int rh) {
        String[] lay = LAYOUT[lang];
        boolean big = tablet && !floating;
        if (big) {
            LinearLayout r0 = row(rh);
            String nums = "1234567890";
            for (int i = 0; i < nums.length(); i++) add(r0, charKey(String.valueOf(nums.charAt(i)), false), 1f);
            add(r0, delKey(), 1.4f);
        }
        LinearLayout r1 = row(rh);
        if (big) add(r1, special("tab", () -> insert("\t")), 1.4f);
        for (int i = 0; i < lay[0].length(); i++) add(r1, charKey(cased(String.valueOf(lay[0].charAt(i))), true), 1f);
        LinearLayout r2 = row(rh);
        if (big) add(r2, capsKey(), 1.7f); else spacer(r2, .5f);
        for (int i = 0; i < lay[1].length(); i++) add(r2, charKey(cased(String.valueOf(lay[1].charAt(i))), true), 1f);
        if (big) add(r2, special("return", this::enter), 1.7f); else spacer(r2, .5f);
        LinearLayout r3 = row(rh);
        add(r3, shiftKey(), big ? 2.1f : 1.4f);
        for (int i = 0; i < lay[2].length(); i++) add(r3, charKey(cased(String.valueOf(lay[2].charAt(i))), true), 1f);
        if (big) { add(r3, charKey(",", false), 1f); add(r3, charKey(".", false), 1f); add(r3, shiftKey(), 2.1f); }
        else add(r3, delKey(), 1.4f);
        bottomRow(rh, false);
    }

    void symbols(int rh) {
        String[][] s = page == 1
                ? new String[][]{{"1234567890"}, {"-/:;()$&@\""}, {".,?!'"}}
                : new String[][]{{"[]{}#%^*+="}, {"_\\|~<>\u20ac\u00a3\u00a5\u2022"}, {".,?!'"}};
        LinearLayout r1 = row(rh);
        for (int i = 0; i < s[0][0].length(); i++) add(r1, charKey(String.valueOf(s[0][0].charAt(i)), true), 1f);
        LinearLayout r2 = row(rh);
        for (int i = 0; i < s[1][0].length(); i++) add(r2, charKey(String.valueOf(s[1][0].charAt(i)), true), 1f);
        LinearLayout r3 = row(rh);
        add(r3, special(page == 1 ? "#+=" : "123", () -> { page = page == 1 ? 2 : 1; build(); }), 1.5f);
        spacer(r3, .2f);
        for (int i = 0; i < s[2][0].length(); i++) add(r3, charKey(String.valueOf(s[2][0].charAt(i)), true), 1.1f);
        spacer(r3, .2f);
        add(r3, delKey(), 1.5f);
        bottomRow(rh, true);
    }

    void bottomRow(int rh, boolean sym) {
        LinearLayout r = row(rh);
        boolean big = tablet && !floating;
        if (big && !sym) {
            add(r, special("\uD83D\uDE00", () -> { page = 3; build(); }), 1.3f);
            add(r, special(".?123", () -> { page = 1; build(); }), 1.3f);
            add(r, special("\uD83C\uDFA4", this::dictate), 1.3f);
            add(r, spaceKey(), 6f);
            add(r, special(".?123", () -> { page = 1; build(); }), 1.3f);
            add(r, globeKey(), 1.3f);
            add(r, hideKey(), 1.3f);
        } else {
            add(r, special(sym ? "ABC" : "123", () -> { page = sym ? 0 : 1; build(); }), 1.4f);
            add(r, globeKey(), 1.1f);
            add(r, spaceKey(), 4.6f);
            add(r, special("\uD83C\uDFA4", this::dictate), 1.1f);
            add(r, special("return", this::enter), 1.7f);
        }
    }

    // ---------------------------------------------------------------- key factories
    void tick(View v) {
        if (a.prefs.getBoolean("kb_haptic", true)) v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
        if (a.prefs.getBoolean("kb_sound", true)) v.playSoundEffect(SoundEffectConstants.CLICK);
    }

    TextView base(String label, int bgc, float size) {
        TextView t = new TextView(a);
        t.setText(label);
        t.setTextColor(cText());
        t.setTextSize(size);
        t.setGravity(Gravity.CENTER);
        t.setTypeface(Emoji.font(false));
        t.setIncludeFontPadding(false);
        t.setBackground(keyBg(bgc));
        return t;
    }

    TextView special(final String label, final Runnable r) {
        final TextView t = base(label, cSpecial(), label.length() > 3 && !label.startsWith("\uD83D") ? 12.5f : 15f);
        if (label.equals("\uD83D\uDE00") || label.equals("\uD83C\uDFA4")) t.setTextSize(17);
        t.setOnTouchListener((v, e) -> {
            if (e.getActionMasked() == MotionEvent.ACTION_DOWN) { t.setBackground(keyBg(cPress())); tick(v); }
            else if (e.getActionMasked() == MotionEvent.ACTION_UP) { t.setBackground(keyBg(cSpecial())); r.run(); }
            else if (e.getActionMasked() == MotionEvent.ACTION_CANCEL) t.setBackground(keyBg(cSpecial()));
            return true;
        });
        return t;
    }

    View shiftKey() {
        final TextView t = base(shift == 2 ? "\u21EA" : "\u21E7", shift > 0 ? cKey() : cSpecial(), 19);
        t.setOnTouchListener((v, e) -> {
            if (e.getActionMasked() == MotionEvent.ACTION_DOWN) tick(v);
            if (e.getActionMasked() == MotionEvent.ACTION_UP) {
                long now = System.currentTimeMillis();
                if (shift == 2) shift = 0;
                else if (now - lastShift < 320) shift = 2;
                else shift = shift == 0 ? 1 : 0;
                lastShift = now;
                build();
            }
            return true;
        });
        return t;
    }

    View capsKey() {
        final TextView t = base("caps lock", shift == 2 ? cKey() : cSpecial(), 12.5f);
        t.setGravity(Gravity.BOTTOM | Gravity.LEFT);
        t.setPadding(a.dp(8), 0, 0, a.dp(6));
        t.setOnTouchListener((v, e) -> {
            if (e.getActionMasked() == MotionEvent.ACTION_DOWN) tick(v);
            if (e.getActionMasked() == MotionEvent.ACTION_UP) { shift = shift == 2 ? 0 : 2; build(); }
            return true;
        });
        return t;
    }

    View delKey() {
        final TextView t = base("\u232B", cSpecial(), 19);
        final Runnable rep = new Runnable() { @Override public void run() { backspace(); tick(t); h.postDelayed(this, 55); } };
        t.setOnTouchListener((v, e) -> {
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN: t.setBackground(keyBg(cPress())); backspace(); tick(v); h.postDelayed(rep, 420); break;
                case MotionEvent.ACTION_UP: case MotionEvent.ACTION_CANCEL: h.removeCallbacks(rep); t.setBackground(keyBg(cSpecial())); refreshBar(); break;
            }
            return true;
        });
        return t;
    }

    View spaceKey() {
        final TextView t = base(tablet && !floating ? "" : LANG_NAMES[lang].split(" ")[0], cKey(), 13);
        t.setTextColor(0xFF8E8E93);
        t.setOnTouchListener((v, e) -> {
            if (e.getActionMasked() == MotionEvent.ACTION_DOWN) { t.setBackground(keyBg(cPress())); tick(v); }
            if (e.getActionMasked() == MotionEvent.ACTION_UP) {
                t.setBackground(keyBg(cKey()));
                long now = System.currentTimeMillis();
                CharSequence before = textBefore(2);
                if (now - lastSpace < 450 && before.length() == 2 && before.charAt(1) == ' ' && Character.isLetterOrDigit(before.charAt(0)) && !emSearch) {
                    replaceBefore(1, ". ");
                } else commit(" ");
                lastSpace = now;
            }
            if (e.getActionMasked() == MotionEvent.ACTION_CANCEL) t.setBackground(keyBg(cKey()));
            return true;
        });
        return t;
    }

    View hideKey() {
        final TextView t = base("\u2328\u2193", cSpecial(), 16);
        final boolean[] lp = {false};
        t.setOnTouchListener((v, e) -> {
            if (e.getActionMasked() == MotionEvent.ACTION_DOWN) { lp[0] = false; tick(v); h.postDelayed(() -> { lp[0] = true; kbMenu(t, true); }, 420); }
            if (e.getActionMasked() == MotionEvent.ACTION_UP) { h.removeCallbacksAndMessages(null); if (!lp[0]) hide(); }
            if (e.getActionMasked() == MotionEvent.ACTION_CANCEL) h.removeCallbacksAndMessages(null);
            return true;
        });
        return t;
    }

    View globeKey() {
        final TextView t = base("\uD83C\uDF10", cSpecial(), 17);
        final boolean[] lp = {false};
        t.setOnTouchListener((v, e) -> {
            if (e.getActionMasked() == MotionEvent.ACTION_DOWN) { lp[0] = false; tick(v); h.postDelayed(() -> { lp[0] = true; kbMenu(t, false); }, 380); }
            if (e.getActionMasked() == MotionEvent.ACTION_UP) {
                h.removeCallbacksAndMessages(null);
                if (!lp[0]) { lang = (lang + 1) % LANG_NAMES.length; a.prefs.edit().putInt("kb_lang", lang).apply(); build(); }
            }
            if (e.getActionMasked() == MotionEvent.ACTION_CANCEL) h.removeCallbacksAndMessages(null);
            return true;
        });
        return t;
    }

    /** Key that types a character. Long press shows accents; sliding picks one. */
    View charKey(final String ch, final boolean bubbleOn) {
        final TextView t = base(ch, cKey(), tablet && !floating ? 21 : 20);
        final String alts = altFor(ch);
        final boolean[] pop = {false};
        final LinearLayout[] alt = {null};
        final int[] sel = {-1};
        t.setOnTouchListener((v, e) -> {
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    t.setBackground(keyBg(cPress()));
                    tick(v);
                    pop[0] = false; sel[0] = -1;
                    if (bubbleOn && (!tablet || floating)) showBubble(t, ch);
                    if (!alts.isEmpty()) h.postDelayed(() -> { pop[0] = true; hideBubble(); alt[0] = showAlts(t, ch, alts); }, 380);
                    return true;
                case MotionEvent.ACTION_MOVE:
                    if (pop[0] && alt[0] != null) sel[0] = highlight(alt[0], e.getRawX());
                    return true;
                case MotionEvent.ACTION_UP:
                    h.removeCallbacksAndMessages(null);
                    t.setBackground(keyBg(cKey()));
                    hideBubble();
                    if (pop[0] && alt[0] != null) {
                        String chosen = sel[0] >= 0 ? ((TextView) alt[0].getChildAt(sel[0])).getText().toString() : ch;
                        dismissAlt();
                        typeChar(chosen);
                    } else typeChar(ch);
                    return true;
                case MotionEvent.ACTION_CANCEL:
                    h.removeCallbacksAndMessages(null);
                    t.setBackground(keyBg(cKey()));
                    hideBubble(); dismissAlt();
                    return true;
            }
            return true;
        });
        return t;
    }

    String altFor(String ch) {
        String lower = ch.toLowerCase();
        for (String[] p : ALT) if (p[0].equals(lower)) return shift > 0 && Character.isLetter(ch.charAt(0)) ? p[1].toUpperCase() : p[1];
        return "";
    }

    void showBubble(View key, String ch) {
        hideBubble();
        bubble = Ui.tv(a, ch, 28, cText(), false);
        bubble.setGravity(Gravity.CENTER);
        bubble.setBackground(keyBg(dark() ? 0xFF6A6A6E : 0xFFFFFFFF));
        bubble.setElevation(a.dp(6));
        int[] kl = new int[2], hl = new int[2];
        key.getLocationOnScreen(kl);
        host.getLocationOnScreen(hl);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(key.getWidth() + a.dp(14), key.getHeight() + a.dp(18), Gravity.TOP | Gravity.LEFT);
        lp.leftMargin = kl[0] - hl[0] - a.dp(7);
        lp.topMargin = kl[1] - hl[1] - key.getHeight() - a.dp(14);
        host.addView(bubble, lp);
    }

    void hideBubble() { if (bubble != null) { host.removeView(bubble); bubble = null; } }

    LinearLayout showAlts(View key, String ch, String alts) {
        dismissAlt();
        final LinearLayout box = new LinearLayout(a);
        box.setPadding(a.dp(4), a.dp(4), a.dp(4), a.dp(4));
        box.setBackground(keyBg(dark() ? 0xFF4A4A4E : 0xFFFFFFFF));
        box.setElevation(a.dp(8));
        List<String> all = new ArrayList<>();
        all.add(ch);
        for (int i = 0; i < alts.length(); i++) all.add(String.valueOf(alts.charAt(i)));
        for (String s : all) {
            TextView t = Ui.tv(a, s, 22, cText(), false);
            t.setGravity(Gravity.CENTER);
            box.addView(t, new LinearLayout.LayoutParams(a.dp(34), a.dp(44)));
        }
        int[] kl = new int[2], hl = new int[2];
        key.getLocationOnScreen(kl);
        host.getLocationOnScreen(hl);
        int bw = a.dp(34) * all.size() + a.dp(8);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.LEFT);
        int left = kl[0] - hl[0] + key.getWidth() / 2 - bw / 2;
        lp.leftMargin = Math.max(a.dp(4), Math.min(a.sw - bw - a.dp(4), left));
        lp.topMargin = kl[1] - hl[1] - a.dp(52);
        host.addView(box, lp);
        altPop = new FrameLayout(a);       // marker so dismissAlt knows what to remove
        altBox = box;
        highlight(box, kl[0] + key.getWidth() / 2f);
        return box;
    }

    LinearLayout altBox;

    int highlight(LinearLayout box, float rawX) {
        int[] bl = new int[2];
        box.getLocationOnScreen(bl);
        int idx = -1;
        for (int i = 0; i < box.getChildCount(); i++) {
            View c = box.getChildAt(i);
            c.setBackground(null);
            ((TextView) c).setTextColor(cText());
            int[] cl = new int[2];
            c.getLocationOnScreen(cl);
            if (rawX >= cl[0] && rawX < cl[0] + c.getWidth()) idx = i;
        }
        if (idx >= 0) {
            box.getChildAt(idx).setBackground(keyBg(Theme.accent));
            ((TextView) box.getChildAt(idx)).setTextColor(Color.WHITE);
        }
        return idx;
    }

    void dismissAlt() {
        if (altBox != null) { host.removeView(altBox); altBox = null; }
        altPop = null;
    }

    /** Long-press menu on globe / hide key, styled like a macOS context menu. */
    void kbMenu(View anchor, boolean hideKeyMenu) {
        final LinearLayout m = new LinearLayout(a);
        m.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable g = new GradientDrawable();
        g.setColor(dark() ? 0xF23A3A3C : 0xF5F6F6F8);
        g.setCornerRadius(a.dp(14));
        g.setStroke(1, dark() ? 0x33FFFFFF : 0x22000000);
        m.setBackground(g);
        m.setElevation(a.dp(10));
        m.setPadding(a.dp(4), a.dp(4), a.dp(4), a.dp(4));
        final Runnable[] close = new Runnable[1];
        List<String> labels = new ArrayList<>();
        List<Runnable> acts = new ArrayList<>();
        if (hideKeyMenu) {
            labels.add(floating ? "Dock" : "Floating");
            acts.add(() -> { a.prefs.edit().putBoolean("kb_float", !floating).apply(); floating = !floating; panel.setTag(null); build(); host.post(this::pan); });
            labels.add("Hide Keyboard");
            acts.add(this::hide);
        } else {
            labels.add("Keyboard Settings\u2026");
            acts.add(() -> { hide(); a.openBuiltin("System Settings"); });
            labels.add("Emoji");
            acts.add(() -> { page = 3; build(); });
            for (int i = 0; i < LANG_NAMES.length; i++) {
                final int k = i;
                labels.add((k == lang ? "\u2713 " : "    ") + LANG_NAMES[i]);
                acts.add(() -> { lang = k; a.prefs.edit().putInt("kb_lang", lang).apply(); page = 0; build(); });
            }
        }
        for (int i = 0; i < labels.size(); i++) {
            final Runnable act = acts.get(i);
            TextView t = Ui.tv(a, labels.get(i), 13.5f, cText(), false);
            t.setGravity(Gravity.CENTER_VERTICAL);
            t.setPadding(a.dp(14), 0, a.dp(14), 0);
            t.setOnClickListener(v -> { close[0].run(); act.run(); });
            m.addView(t, new LinearLayout.LayoutParams(a.dp(190), a.dp(34)));
        }
        close[0] = () -> host.removeView(m);
        int[] kl = new int[2], hl = new int[2];
        anchor.getLocationOnScreen(kl);
        host.getLocationOnScreen(hl);
        int mh = a.dp(34) * labels.size() + a.dp(8);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.LEFT);
        lp.leftMargin = Math.max(a.dp(4), Math.min(a.sw - a.dp(200), kl[0] - hl[0]));
        lp.topMargin = Math.max(a.dp(4), kl[1] - hl[1] - mh - a.dp(6));
        host.addView(m, lp);
        m.setAlpha(0f);
        m.animate().alpha(1f).setDuration(120).start();
        m.setOnTouchListener((v, e) -> true);
        m.postDelayed(() -> close[0].run(), 5000);
    }

    // ---------------------------------------------------------------- text editing
    CharSequence textBefore(int n) {
        if (target == null) return "";
        int s = Math.max(0, Math.min(target.getSelectionStart(), target.getSelectionEnd()));
        return target.getText().subSequence(Math.max(0, s - n), s);
    }

    void insert(String s) {
        if (target == null) return;
        Editable ed = target.getText();
        int st = Math.max(0, target.getSelectionStart()), en = Math.max(0, target.getSelectionEnd());
        ed.replace(Math.min(st, en), Math.max(st, en), s);
        refreshBar();
    }

    void replaceBefore(int n, String s) {
        if (target == null) return;
        int st = Math.max(0, target.getSelectionStart());
        target.getText().replace(Math.max(0, st - n), st, s);
        autoCap();
        refreshBar();
    }

    void typeChar(String ch) {
        if (emSearch) { searchBuf.append(ch.toLowerCase()); if (shift == 1) shift = 0; build(); return; }
        commit(ch);
        if (shift == 1) { shift = 0; build(); }
    }

    void commit(String s) {
        if (emSearch) { searchBuf.append(s); build(); return; }
        // learn a finished word
        if (s.equals(" ") || ".,!?;:".contains(s)) learn(currentWord());
        insert(s);
        if (s.equals(" ") || s.equals(".") || s.equals("!") || s.equals("?")) autoCap();
    }

    void backspace() {
        if (emSearch) { if (searchBuf.length() > 0) searchBuf.deleteCharAt(searchBuf.length() - 1); build(); return; }
        if (target == null) return;
        int st = target.getSelectionStart(), en = target.getSelectionEnd();
        Editable ed = target.getText();
        if (st != en) ed.delete(Math.min(st, en), Math.max(st, en));
        else if (st > 0) {
            int n = (st >= 2 && Character.isLowSurrogate(ed.charAt(st - 1)) && Character.isHighSurrogate(ed.charAt(st - 2))) ? 2 : 1;
            ed.delete(st - n, st);
        }
        if (st == 0 && en == 0) return;
        autoCap();
    }

    void enter() {
        if (target == null) return;
        int act = target.getImeOptions() & EditorInfo.IME_MASK_ACTION;
        boolean multi = (target.getInputType() & android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE) != 0 || target.getMaxLines() > 1 && !target.isSingleLine();
        if (act != EditorInfo.IME_ACTION_NONE && act != EditorInfo.IME_ACTION_UNSPECIFIED && !multi) {
            target.onEditorAction(act);
        } else if (multi) { insert("\n"); autoCap(); }
        else {
            target.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER));
            target.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ENTER));
            target.onEditorAction(EditorInfo.IME_ACTION_DONE);
        }
    }

    void autoCap() {
        if (target == null || shift == 2 || isPasswordField() || LANG_TAGS[lang].startsWith("ar") || LANG_TAGS[lang].startsWith("hi") || LANG_TAGS[lang].startsWith("ko")) return;
        CharSequence b = textBefore(3);
        String s = b.toString();
        boolean cap = s.isEmpty() || s.endsWith("\n") || s.matches(".*[.!?] $");
        int want = cap ? 1 : 0;
        if (want != shift && page == 0 && !emSearch) { shift = want; if (showing) build(); }
    }

    // ---------------------------------------------------------------- QuickType
    String currentWord() {
        CharSequence b = textBefore(40);
        int i = b.length();
        while (i > 0 && (Character.isLetter(b.charAt(i - 1)) || b.charAt(i - 1) == '\'')) i--;
        return b.subSequence(i, b.length()).toString();
    }

    void learn(String w) {
        if (w.length() < 3 || isPasswordField()) return;
        String key = "kbw_" + w.toLowerCase();
        a.prefs.edit().putInt(key, a.prefs.getInt(key, 0) + 1).apply();
        String all = a.prefs.getString("kb_learned", "");
        if (!(" " + all + " ").contains(" " + w.toLowerCase() + " ")) {
            all = (all + " " + w.toLowerCase()).trim();
            if (all.length() > 6000) all = all.substring(all.length() - 6000);
            a.prefs.edit().putString("kb_learned", all).apply();
        }
    }

    List<String> suggestions() {
        initDict();
        List<String> out = new ArrayList<>();
        String w = currentWord();
        if (w.isEmpty()) {
            boolean idl = LANG_TAGS[lang].startsWith("id") || LANG_TAGS[lang].startsWith("ms");
            String[] d = idl ? new String[]{"Saya", "Terima kasih", "Halo"} : new String[]{"I", "The", "I\u2019m"};
            for (String s : d) out.add(s);
            return out;
        }
        String lw = w.toLowerCase();
        out.add("\u201C" + w + "\u201D");
        List<String> c = new ArrayList<>();
        for (String s : a.prefs.getString("kb_learned", "").split(" "))
            if (s.startsWith(lw) && !s.equals(lw) && !c.contains(s)) c.add(s);
        // most used learned words first
        java.util.Collections.sort(c, (x, y) -> a.prefs.getInt("kbw_" + y, 0) - a.prefs.getInt("kbw_" + x, 0));
        for (String s : dict) if (s.startsWith(lw) && !s.equals(lw) && !c.contains(s)) c.add(s);
        for (String s : c) {
            if (out.size() >= 3) break;
            out.add(matchCase(w, s));
        }
        return out;
    }

    String matchCase(String typed, String cand) {
        if (typed.length() > 1 && typed.equals(typed.toUpperCase())) return cand.toUpperCase();
        if (Character.isUpperCase(typed.charAt(0))) return Character.toUpperCase(cand.charAt(0)) + cand.substring(1);
        return cand;
    }

    void pickSuggestion(String s) {
        if (target == null) return;
        String w = currentWord();
        if (s.startsWith("\u201C")) { commit(" "); return; }
        replaceBefore(w.length(), s + " ");
        learn(s);
    }

    void refreshBar() {
        if (!showing || page == 3 || emSearch || panel.getChildCount() == 0) return;
        View nb = buildBar();
        panel.removeViewAt(0);
        panel.addView(nb, 0, new LinearLayout.LayoutParams(-1, a.dp(38)));
    }

    // ---------------------------------------------------------------- dictation
    void dictate() {
        try {
            Intent i = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
            i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
            i.putExtra(RecognizerIntent.EXTRA_LANGUAGE, LANG_TAGS[lang]);
            a.startActivityForResult(i, 9091);
        } catch (Exception e) { Toast.makeText(a, L.t("Dictation is not available"), Toast.LENGTH_SHORT).show(); }
    }

    public void onDictation(int res, Intent data) {
        if (res != android.app.Activity.RESULT_OK || data == null) return;
        ArrayList<String> r = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
        if (r == null || r.isEmpty() || target == null) return;
        String s = r.get(0);
        CharSequence b = textBefore(1);
        insert((b.length() > 0 && b.charAt(0) != ' ' ? " " : "") + s + " ");
        autoCap();
    }

    // ---------------------------------------------------------------- emoji panel
    static String[] allEmoji(Paint p) {
        if (emojiCache != null) return emojiCache;
        String[] cats = new String[9];
        cats[1] = range(p, 0x1F600, 0x1F64F, 0x1F910, 0x1F92F, 0x1F970, 0x1F97A, 0x1FAE0, 0x1FAE8, 0x1F972, 0x1F972) + "\uD83D\uDC4B\uD83E\uDD1A\uD83D\uDD90\u270B\uD83D\uDD96\uD83D\uDC4C\uD83E\uDD0C\uD83E\uDD0F\u270C\uD83E\uDD1E\uD83E\uDD1F\uD83E\uDD18\uD83E\uDD19\uD83D\uDC48\uD83D\uDC49\uD83D\uDC46\uD83D\uDC47\u261D\uD83D\uDC4D\uD83D\uDC4E\u270A\uD83D\uDC4A\uD83E\uDD1B\uD83E\uDD1C\uD83D\uDC4F\uD83D\uDE4C\uD83D\uDC50\uD83E\uDD32\uD83E\uDD1D\uD83D\uDE4F\uD83D\uDC40\uD83D\uDC68\uD83D\uDC69\uD83D\uDC76\uD83D\uDC7B\uD83D\uDC80\uD83D\uDC7D\uD83E\uDD16\uD83D\uDCA9";
        cats[2] = range(p, 0x1F400, 0x1F43E, 0x1F980, 0x1F9AE, 0x1F330, 0x1F344, 0x1F337, 0x1F33C) + "\uD83D\uDC35\uD83D\uDE48\uD83D\uDE49\uD83D\uDE4A";
        cats[3] = range(p, 0x1F345, 0x1F37F, 0x1F950, 0x1F96B, 0x1F32D, 0x1F32F);
        cats[4] = "\u26BD\uD83C\uDFC0\uD83C\uDFC8\u26BE\uD83E\uDD4E\uD83C\uDFBE\uD83C\uDFD0\uD83C\uDFC9\uD83C\uDFB1\uD83C\uDFD3\uD83C\uDFF8\uD83E\uDD45\u26F3\uD83C\uDFF9\uD83C\uDFA3\uD83E\uDD4A\uD83E\uDD4B\u26F8\uD83C\uDFBF\uD83C\uDFC2" + range(p, 0x1F3A0, 0x1F3AF, 0x1F3C5, 0x1F3CA);
        cats[5] = range(p, 0x1F680, 0x1F6A4, 0x1F3E0, 0x1F3F0, 0x1F30D, 0x1F30F, 0x1F5FB, 0x1F5FF, 0x1F6B2, 0x1F6B2);
        cats[6] = "\u231A\uD83D\uDCF1\uD83D\uDCBB\u2328\uD83D\uDDA5\uD83D\uDDA8\uD83D\uDDB1" + range(p, 0x1F4A1, 0x1F4A9, 0x1F4B0, 0x1F4FF, 0x1F50B, 0x1F52E);
        cats[7] = "\u2764\uD83E\uDDE1\uD83D\uDC9B\uD83D\uDC9A\uD83D\uDC99\uD83D\uDC9C\uD83D\uDDA4\uD83E\uDD0D\uD83E\uDD0E\uD83D\uDC94\u2763\uD83D\uDC95\uD83D\uDC9E\uD83D\uDC93\uD83D\uDC97\uD83D\uDC96\uD83D\uDC98\uD83D\uDC9D\uD83D\uDC9F\u2705\u274C\u2753\u2757\u203C\u2049\uD83D\uDCAF\uD83D\uDD25\u2728\u2B50\uD83C\uDF1F\uD83D\uDCAB" + range(p, 0x1F520, 0x1F53D);
        StringBuilder fl = new StringBuilder();
        for (String cc : new String[]{"ID", "US", "GB", "JP", "KR", "CN", "IN", "FR", "DE", "ES", "IT", "BR", "RU", "MY", "SG", "SA", "AE", "VN", "TH", "PH", "AU", "CA", "MX", "TR", "EG", "NL", "PT", "AR", "ZA", "NG", "PK", "BD", "CH", "SE", "NO", "PL", "UA", "KE", "NZ", "IE"}) {
            String f = new String(Character.toChars(0x1F1E6 + cc.charAt(0) - 'A')) + new String(Character.toChars(0x1F1E6 + cc.charAt(1) - 'A'));
            if (p.hasGlyph(f)) fl.append(f);
        }
        cats[8] = fl.toString();
        emojiCache = cats;
        return cats;
    }

    static String range(Paint p, int... r) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i + 1 < r.length; i += 2)
            for (int c = r[i]; c <= r[i + 1]; c++) {
                String s = new String(Character.toChars(c));
                if (p.hasGlyph(s)) sb.append(s);
            }
        return sb.toString();
    }

    static List<String> split(String s) {
        List<String> out = new ArrayList<>();
        int i = 0;
        while (i < s.length()) {
            int cp = s.codePointAt(i);
            int n = Character.charCount(cp);
            // flags = two regional indicators; also keep variation selectors / ZWJ with the previous emoji
            if (cp >= 0x1F1E6 && cp <= 0x1F1FF && i + n < s.length()) n += Character.charCount(s.codePointAt(i + n));
            out.add(s.substring(i, i + n));
            i += n;
        }
        return out;
    }

    TextView emojiCell(final String e, float size, final Runnable r) {
        TextView t = new TextView(a);
        t.setText(e);
        t.setTextSize(size);
        t.setGravity(Gravity.CENTER);
        t.setTypeface(Emoji.font(false));
        t.setIncludeFontPadding(false);
        t.setOnClickListener(v -> { r.run(); tick(v); });
        t.setMinWidth(a.dp(size > 24 ? 40 : 36));
        return t;
    }

    List<String> recents() {
        List<String> l = new ArrayList<>();
        String s = a.prefs.getString("kb_recent", "");
        if (!s.isEmpty()) l.addAll(split(s));
        return l;
    }

    void recent(String e) {
        List<String> l = recents();
        l.remove(e);
        l.add(0, e);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < Math.min(30, l.size()); i++) sb.append(l.get(i));
        a.prefs.edit().putString("kb_recent", sb.toString()).apply();
    }

    List<String> searchEmoji(String q) {
        List<String> res = new ArrayList<>();
        String[] cats = allEmoji(new TextView(a).getPaint());
        String lq = q.toLowerCase(Locale.ROOT).trim();
        if (lq.isEmpty()) return res;
        for (int c = 1; c < 9; c++) {
            for (String e : split(cats[c])) {
                boolean hit = false;
                int cp = e.codePointAt(0);
                if (c == 8) {
                    String cc = "" + (char) ('A' + cp - 0x1F1E6) + (char) ('A' + e.codePointAt(2) - 0x1F1E6);
                    hit = new Locale("", cc).getDisplayCountry(Locale.ENGLISH).toLowerCase(Locale.ROOT).contains(lq) || cc.toLowerCase(Locale.ROOT).equals(lq);
                } else {
                    try { String n = Character.getName(cp); hit = n != null && n.toLowerCase(Locale.ROOT).contains(lq); } catch (Throwable t) { }
                }
                if (hit && !res.contains(e)) res.add(e);
                if (res.size() >= 40) return res;
            }
        }
        return res;
    }

    void buildEmoji() {
        final boolean big = tablet && !floating;
        int rows = floating ? 4 : (big ? 6 : 4);
        int cell = a.dp(big ? 46 : 40);
        TextView title = Ui.tv(a, emCat == 0 ? "FREQUENTLY USED" : emCat == 9 ? "MEMOJI" : emCat == 10 ? "STICKERS" : new String[]{"", "SMILEYS & PEOPLE", "ANIMALS & NATURE", "FOOD & DRINK", "ACTIVITY", "TRAVEL & PLACES", "OBJECTS", "SYMBOLS", "FLAGS"}[emCat], 10.5f, 0xFF8E8E93, true);
        title.setPadding(a.dp(10), a.dp(4), 0, a.dp(2));
        panel.addView(title, new LinearLayout.LayoutParams(-1, a.dp(22)));
        HorizontalScrollView hs = new HorizontalScrollView(a);
        hs.setHorizontalScrollBarEnabled(false);
        LinearLayout grid = new LinearLayout(a);
        List<String> items = new ArrayList<>();
        if (emCat == 0) items = recents();
        else if (emCat >= 1 && emCat <= 8) items = split(allEmoji(new TextView(a).getPaint())[emCat]);
        if (emCat == 10) stickerGrid(grid, rows, cell);
        else if (emCat == 9) memojiGrid(grid, rows, cell);
        else if (items.isEmpty()) { TextView t = Ui.tv(a, L.t("No Results"), 13, 0xFF8E8E93, false); t.setPadding(a.dp(12), a.dp(20), 0, 0); grid.addView(t); }
        else {
            int cols = (items.size() + rows - 1) / rows;
            for (int c = 0; c < cols; c++) {
                LinearLayout col = new LinearLayout(a);
                col.setOrientation(LinearLayout.VERTICAL);
                for (int r = 0; r < rows; r++) {
                    int i = c * rows + r;
                    if (i < items.size()) {
                        final String e = items.get(i);
                        TextView t = emojiCell(e, big ? 29 : 25, () -> { insert(e); recent(e); });
                        col.addView(t, new LinearLayout.LayoutParams(cell, cell));
                    } else col.addView(new View(a), new LinearLayout.LayoutParams(cell, cell));
                }
                grid.addView(col);
            }
        }
        hs.addView(grid);
        panel.addView(hs, new LinearLayout.LayoutParams(-1, cell * rows));

        // bottom bar: ABC | search | categories | space | delete | hide
        LinearLayout b = new LinearLayout(a);
        b.setGravity(Gravity.CENTER_VERTICAL);
        b.addView(barBtn("ABC", () -> { page = 0; build(); }), new LinearLayout.LayoutParams(a.dp(48), -1));
        b.addView(barBtn("\uD83D\uDD0D", () -> { emSearch = true; searchBuf.setLength(0); page = 0; build(); }), new LinearLayout.LayoutParams(a.dp(36), -1));
        HorizontalScrollView cs = new HorizontalScrollView(a);
        cs.setHorizontalScrollBarEnabled(false);
        LinearLayout cl = new LinearLayout(a);
        for (int i = 0; i < CAT_ICON.length; i++) {
            final int k = i;
            TextView t = Ui.tv(a, CAT_ICON[i], 17, cText(), false);
            t.setGravity(Gravity.CENTER);
            t.setTypeface(Emoji.font(false));
            if (i == emCat) t.setBackground(keyBg(dark() ? 0x44FFFFFF : 0x22000000));
            t.setOnClickListener(v -> { emCat = k; build(); });
            cl.addView(t, new LinearLayout.LayoutParams(a.dp(38), a.dp(32)));
        }
        cs.addView(cl);
        b.addView(cs, new LinearLayout.LayoutParams(0, -1, 1f));
        b.addView(barBtn("\u232B", this::backspace), new LinearLayout.LayoutParams(a.dp(44), -1));
        b.addView(barBtn("\u2328\u2193", this::hide), new LinearLayout.LayoutParams(a.dp(44), -1));
        panel.addView(b, new LinearLayout.LayoutParams(-1, a.dp(38)));
    }

    void memojiGrid(LinearLayout grid, int rows, int cell) {
        String faces = "\uD83D\uDE00\uD83D\uDE0D\uD83E\uDD29\uD83D\uDE0E\uD83E\uDD14\uD83E\uDD73\uD83D\uDE02\uD83D\uDE2D\uD83D\uDE21\uD83E\uDD7A\uD83D\uDE34\uD83D\uDE18\uD83E\uDD2A\uD83D\uDE07\uD83E\uDD2F\uD83E\uDD76\uD83E\uDD75\uD83E\uDD2B\uD83E\uDD17";
        int[] pastel = {0xFFFFD6E0, 0xFFD6E8FF, 0xFFDFF7D8, 0xFFFFE9C7, 0xFFE6D9FF, 0xFFD3F4F2};
        List<String> fs = split(faces);
        int cols = (fs.size() + rows - 1) / rows;
        for (int c = 0; c < cols; c++) {
            LinearLayout col = new LinearLayout(a);
            col.setOrientation(LinearLayout.VERTICAL);
            for (int r = 0; r < rows; r++) {
                int i = c * rows + r;
                FrameLayout f = new FrameLayout(a);
                if (i < fs.size()) {
                    final String e = fs.get(i);
                    GradientDrawable g = new GradientDrawable();
                    g.setShape(GradientDrawable.OVAL);
                    g.setColor(pastel[i % pastel.length]);
                    TextView t = new TextView(a);
                    t.setText(e);
                    t.setTextSize(26);
                    t.setGravity(Gravity.CENTER);
                    t.setTypeface(Emoji.font(false));
                    t.setBackground(g);
                    t.setOnClickListener(v -> { insert(e); recent(e); tick(v); });
                    f.addView(t, new FrameLayout.LayoutParams(cell - a.dp(8), cell - a.dp(8), Gravity.CENTER));
                }
                col.addView(f, new LinearLayout.LayoutParams(cell, cell));
            }
            grid.addView(col);
        }
    }

    File stickerDir() { File d = new File(a.getFilesDir(), "stickers"); d.mkdirs(); return d; }

    void stickerGrid(LinearLayout grid, int rows, int cell) {
        List<File> fs = new ArrayList<>();
        File[] ls = stickerDir().listFiles();
        if (ls != null) { java.util.Arrays.sort(ls); for (File f : ls) fs.add(f); }
        int total = fs.size() + 1;
        int cols = (total + rows - 1) / rows;
        for (int c = 0; c < cols; c++) {
            LinearLayout col = new LinearLayout(a);
            col.setOrientation(LinearLayout.VERTICAL);
            for (int r = 0; r < rows; r++) {
                int i = c * rows + r;
                FrameLayout f = new FrameLayout(a);
                if (i == 0) {
                    TextView plus = Ui.tv(a, "+", 26, cText(), false);
                    plus.setGravity(Gravity.CENTER);
                    plus.setBackground(keyBg(dark() ? 0x33FFFFFF : 0x14000000));
                    plus.setOnClickListener(v -> addSticker());
                    f.addView(plus, new FrameLayout.LayoutParams(cell - a.dp(8), cell - a.dp(8), Gravity.CENTER));
                } else if (i < total) {
                    final File sf = fs.get(i - 1);
                    android.widget.ImageView iv = new android.widget.ImageView(a);
                    iv.setImageBitmap(android.graphics.BitmapFactory.decodeFile(sf.getPath()));
                    iv.setScaleType(android.widget.ImageView.ScaleType.FIT_CENTER);
                    iv.setOnClickListener(v -> copySticker(sf));
                    iv.setOnLongClickListener(v -> { sf.delete(); build(); return true; });
                    f.addView(iv, new FrameLayout.LayoutParams(cell - a.dp(6), cell - a.dp(6), Gravity.CENTER));
                }
                col.addView(f, new LinearLayout.LayoutParams(cell, cell));
            }
            grid.addView(col);
        }
    }

    void addSticker() {
        a.pick("image/*", false, us -> {
            try {
                java.io.InputStream in = a.getContentResolver().openInputStream(us.get(0));
                android.graphics.Bitmap b = android.graphics.BitmapFactory.decodeStream(in);
                in.close();
                int mx = 512;
                float sc = Math.min(1f, mx / (float) Math.max(b.getWidth(), b.getHeight()));
                android.graphics.Bitmap o = android.graphics.Bitmap.createScaledBitmap(b, Math.max(1, (int) (b.getWidth() * sc)), Math.max(1, (int) (b.getHeight() * sc)), true);
                java.io.FileOutputStream fo = new java.io.FileOutputStream(new File(stickerDir(), "s" + System.currentTimeMillis() + ".png"));
                o.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, fo);
                fo.close();
                a.runOnUiThread(() -> { if (showing) { page = 3; emCat = 10; build(); } });
            } catch (Exception e) { }
        });
    }

    void copySticker(File f) {
        try {
            ClipboardManager cm = (ClipboardManager) a.getSystemService(Context.CLIPBOARD_SERVICE);
            cm.setPrimaryClip(ClipData.newUri(a.getContentResolver(), "sticker", FileProv.uri(f)));
            Toast.makeText(a, L.t("Sticker copied"), Toast.LENGTH_SHORT).show();
        } catch (Exception e) { }
    }
}
