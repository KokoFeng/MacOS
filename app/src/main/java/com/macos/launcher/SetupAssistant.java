package com.macos.launcher;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.view.animation.PathInterpolator;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import java.util.Locale;

/** First-run "Setup Assistant" in the style of macOS Tahoe. */
public class SetupAssistant {
    static final String[] HELLO = {"hello", "hola", "bonjour", "你好", "こんにちは", "halo", "hallo", "안녕하세요", "ciao", "olá", "สวัสดี", "привет"};
    static final String[] COUNTRIES = {"Indonesia", "Malaysia", "Singapore", "Philippines", "Thailand", "Vietnam", "India", "Japan", "South Korea",
            "China", "Australia", "United States", "United Kingdom", "Canada", "Germany", "France", "Italy", "Spain", "Brazil", "Netherlands"};

    final MainActivity a;
    final FrameLayout v, page;
    final TextView back, next;
    int idx = 0;
    String country, look;
    EditText fName, fPw, fPw2, fHint;
    int helloI = 0;
    TextView helloTv;
    boolean done;

    public static void show(MainActivity a) { new SetupAssistant(a); }

    int dp(float x) { return a.dp(x); }

    SetupAssistant(MainActivity act) {
        a = act;
        country = Locale.getDefault().getCountry().equals("ID") ? "Indonesia" : "United States";
        look = a.prefs.getString("appearance", "Light");
        a.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN);
        v = new FrameLayout(a);
        v.setClickable(true);
        v.setBackgroundColor(0xFF000000);
        ImageView bg = new ImageView(a);
        bg.setScaleType(ImageView.ScaleType.CENTER_CROP);
        if (WallpaperView.blur != null) bg.setImageBitmap(WallpaperView.blur);
        else if (a.wall.getSharp() != null) bg.setImageBitmap(a.wall.getSharp());
        v.addView(bg, new FrameLayout.LayoutParams(-1, -1));
        View dim = new View(a);
        dim.setBackgroundColor(0x38000000);
        v.addView(dim, new FrameLayout.LayoutParams(-1, -1));
        page = new FrameLayout(a);
        FrameLayout.LayoutParams pl = new FrameLayout.LayoutParams(-1, -1);
        pl.bottomMargin = dp(56);
        v.addView(page, pl);
        back = pill("Go Back", false);
        next = pill("Continue", true);
        FrameLayout.LayoutParams bl = new FrameLayout.LayoutParams(-2, dp(34), Gravity.BOTTOM | Gravity.LEFT);
        bl.leftMargin = dp(28); bl.bottomMargin = dp(14);
        v.addView(back, bl);
        FrameLayout.LayoutParams nl = new FrameLayout.LayoutParams(-2, dp(34), Gravity.BOTTOM | Gravity.RIGHT);
        nl.rightMargin = dp(28); nl.bottomMargin = dp(14);
        v.addView(next, nl);
        back.setOnClickListener(x -> go(idx - 1));
        next.setOnClickListener(x -> advance());
        a.screen.addView(v, new FrameLayout.LayoutParams(-1, -1));
        v.setAlpha(0f);
        v.animate().alpha(1f).setDuration(500).start();
        render(null, true);
    }

    TextView pill(String t, boolean primary) {
        TextView b = Ui.tv(a, t, 13.5f, Color.WHITE, true);
        b.setGravity(Gravity.CENTER);
        b.setPadding(dp(22), 0, dp(22), 0);
        b.setBackground(Ui.rr(primary ? Theme.accent : 0x40FFFFFF, dp(17)));
        return b;
    }

    TextView title(String s) {
        TextView t = Ui.tv(a, s, 24, Color.WHITE, true);
        t.setGravity(Gravity.CENTER);
        t.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        t.setShadowLayer(dp(6), 0, dp(1), 0x44000000);
        return t;
    }

    TextView sub(String s) {
        TextView t = Ui.tv(a, s, 13, 0xCCFFFFFF, false);
        t.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams l = new LinearLayout.LayoutParams(dp(420), -2);
        l.topMargin = dp(6);
        t.setLayoutParams(l);
        return t;
    }

    LinearLayout col() {
        LinearLayout c = new LinearLayout(a);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setGravity(Gravity.CENTER_HORIZONTAL);
        return c;
    }

    EditText field(String hint, boolean pw) {
        EditText e = new EditText(a);
        e.setHint(hint);
        e.setSingleLine(true);
        e.setTextSize(13.5f);
        e.setTextColor(Color.WHITE);
        e.setHintTextColor(0x99FFFFFF);
        e.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
        e.setBackground(Ui.rr(0x40303034, dp(10)));
        e.setPadding(dp(14), 0, dp(14), 0);
        e.setInputType(InputType.TYPE_CLASS_TEXT | (pw ? InputType.TYPE_TEXT_VARIATION_PASSWORD : InputType.TYPE_TEXT_FLAG_CAP_WORDS));
        return e;
    }

    // ---------- flow ----------
    void advance() {
        if (idx == 3) {
            String n = fName.getText().toString().trim();
            if (n.isEmpty()) { Toast.makeText(a, "Enter your full name", Toast.LENGTH_SHORT).show(); return; }
            String p1 = fPw.getText().toString();
            if (!p1.equals(fPw2.getText().toString())) { Toast.makeText(a, "The passwords do not match", Toast.LENGTH_SHORT).show(); return; }
            a.prefs.edit().putString("user_name", n).apply();
            if (p1.isEmpty()) a.prefs.edit().remove("lock_pw").remove("lock_hint").apply();
            else a.prefs.edit().putString("lock_pw", LockScreen.hash(p1)).putString("lock_hint", fHint.getText().toString().trim()).apply();
        }
        if (idx == 1) a.prefs.edit().putString("country", country).apply();
        if (idx == 4) a.prefs.edit().putString("appearance", look).apply();
        go(idx + 1);
    }

    void go(int to) {
        if (to < 0 || done) return;
        boolean fwd = to > idx;
        View old = page.getChildCount() > 0 ? page.getChildAt(0) : null;
        idx = to;
        render(old, fwd);
    }

    void render(final View old, boolean fwd) {
        hideIme();
        back.setVisibility(idx == 0 || idx == 5 ? View.GONE : View.VISIBLE);
        next.setVisibility(idx == 5 ? View.GONE : View.VISIBLE);
        next.setText(idx == 0 ? "Continue" : "Continue");
        View nv;
        switch (idx) {
            case 0: nv = hello(); break;
            case 1: nv = countryPage(); break;
            case 2: nv = privacy(); break;
            case 3: nv = account(); break;
            case 4: nv = lookPage(); break;
            default: nv = finish(); break;
        }
        page.addView(nv, new FrameLayout.LayoutParams(-1, -1));
        PathInterpolator ip = new PathInterpolator(.2f, .9f, .25f, 1f);
        float d = dp(48) * (fwd ? 1 : -1);
        nv.setAlpha(0f); nv.setTranslationX(old == null ? 0 : d);
        nv.animate().alpha(1f).translationX(0).setDuration(420).setInterpolator(ip).start();
        if (old != null) old.animate().alpha(0f).translationX(-d * .6f).setDuration(260).withEndAction(() -> page.removeView(old)).start();
    }

    // ---------- pages ----------
    View hello() {
        FrameLayout f = new FrameLayout(a);
        helloTv = new TextView(a);
        helloTv.setTextColor(Color.WHITE);
        helloTv.setTypeface(Typeface.create("cursive", Typeface.NORMAL));
        helloTv.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, a.sh * .2f);
        helloTv.setText(HELLO[0]);
        helloTv.setShadowLayer(dp(10), 0, dp(2), 0x44000000);
        f.addView(helloTv, new FrameLayout.LayoutParams(-2, -2, Gravity.CENTER));
        a.h.postDelayed(new Runnable() {
            @Override public void run() {
                if (idx != 0 || done || helloTv == null) return;
                helloTv.animate().alpha(0f).setDuration(350).withEndAction(() -> {
                    if (idx != 0) return;
                    helloI = (helloI + 1) % HELLO.length;
                    helloTv.setText(HELLO[helloI]);
                    helloTv.animate().alpha(1f).setDuration(450).start();
                }).start();
                a.h.postDelayed(this, 2200);
            }
        }, 2200);
        return f;
    }

    View countryPage() {
        LinearLayout c = col();
        c.setGravity(Gravity.CENTER);
        c.addView(title("Select Your Country or Region"));
        c.addView(sub("Your country or region will be used to set the language, time and date formats."));
        final LinearLayout list = new LinearLayout(a);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setBackground(Ui.rr(0x33FFFFFF, dp(14)));
        list.setPadding(dp(6), dp(6), dp(6), dp(6));
        for (final String n : COUNTRIES) {
            final TextView r = Ui.tv(a, n, 13.5f, Color.WHITE, false);
            r.setPadding(dp(12), 0, dp(12), 0);
            r.setGravity(Gravity.CENTER_VERTICAL);
            r.setTag(n);
            r.setBackground(n.equals(country) ? Ui.rr(Theme.accent, dp(8)) : null);
            r.setOnClickListener(x -> {
                country = n;
                for (int i = 0; i < list.getChildCount(); i++) {
                    TextView o = (TextView) list.getChildAt(i);
                    o.setBackground(o.getTag().equals(country) ? Ui.rr(Theme.accent, dp(8)) : null);
                }
            });
            list.addView(r, new LinearLayout.LayoutParams(-1, dp(30)));
        }
        ScrollView sv = new ScrollView(a);
        sv.setVerticalScrollBarEnabled(false);
        sv.addView(list);
        LinearLayout.LayoutParams l = new LinearLayout.LayoutParams(dp(300), 0, 1f);
        l.topMargin = dp(12);
        l.bottomMargin = dp(4);
        c.addView(sv, l);
        c.setPadding(0, dp(22), 0, 0);
        return c;
    }

    View privacy() {
        LinearLayout c = col();
        c.setGravity(Gravity.CENTER);
        ImageView ic = Sf.img(a, "hand.raised.fill", 0xFFFFFFFF);
        c.addView(ic, new LinearLayout.LayoutParams(dp(54), dp(54)));
        LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(-2, -2);
        tl.topMargin = dp(12);
        TextView t = title("Data & Privacy");
        c.addView(t, tl);
        c.addView(sub("This app was created by Feng-Dev / Feng iOS on Droid. Your name, password and settings are stored only on this device and are never sent anywhere."));
        return c;
    }

    View account() {
        ScrollView sv = new ScrollView(a);
        sv.setVerticalScrollBarEnabled(false);
        sv.setFillViewport(true);
        LinearLayout c = col();
        c.setGravity(Gravity.CENTER);
        c.setPadding(0, dp(18), 0, dp(10));
        c.addView(title("Create a Computer Account"));
        c.addView(sub("Fill out the following information to create your account."));
        fName = field("Full Name", false);
        fPw = field("Password (optional)", true);
        fPw2 = field("Verify", true);
        fHint = field("Hint (recommended)", false);
        fName.setText(a.prefs.getString("user_name", "").equals("User") ? "" : a.prefs.getString("user_name", ""));
        for (EditText e : new EditText[]{fName, fPw, fPw2, fHint}) {
            LinearLayout.LayoutParams l = new LinearLayout.LayoutParams(dp(280), dp(36));
            l.topMargin = dp(9);
            c.addView(e, l);
        }
        sv.addView(c, new FrameLayout.LayoutParams(-1, -2));
        return sv;
    }

    View lookPage() {
        LinearLayout c = col();
        c.setGravity(Gravity.CENTER);
        c.addView(title("Choose Your Look"));
        c.addView(sub("Choose a look for your desktop. You can change it later in System Settings."));
        final LinearLayout row = new LinearLayout(a);
        row.setGravity(Gravity.CENTER);
        final String[] names = {"Light", "Dark", "Auto"};
        final int[] topC = {0xFFEFF2F8, 0xFF2A2A2E, 0xFFEFF2F8}, botC = {0xFFD7DDEA, 0xFF18181B, 0xFF1C1C1F};
        final FrameLayout[] cards = new FrameLayout[3];
        for (int i = 0; i < 3; i++) {
            final int k = i;
            LinearLayout item = col();
            FrameLayout card = new FrameLayout(a);
            GradientDrawable g = new GradientDrawable(i == 2 ? GradientDrawable.Orientation.LEFT_RIGHT : GradientDrawable.Orientation.TOP_BOTTOM,
                    i == 2 ? new int[]{0xFFEFF2F8, 0xFFD7DDEA, 0xFF2A2A2E, 0xFF18181B} : new int[]{topC[i], botC[i]});
            g.setCornerRadius(dp(14));
            card.setBackground(g);
            View bar = new View(a);
            bar.setBackground(Ui.rr(i == 1 ? 0x33FFFFFF : 0x22000000, dp(4)));
            FrameLayout.LayoutParams bp = new FrameLayout.LayoutParams(dp(50), dp(8), Gravity.TOP | Gravity.CENTER_HORIZONTAL);
            bp.topMargin = dp(12);
            card.addView(bar, bp);
            cards[i] = card;
            card.setOnClickListener(x -> {
                look = names[k];
                for (int j = 0; j < 3; j++) cards[j].setForeground(look.equals(names[j]) ? ring() : null);
            });
            card.setForeground(look.equals(names[i]) ? ring() : null);
            item.addView(card, new LinearLayout.LayoutParams(dp(110), dp(76)));
            TextView lb = Ui.tv(a, names[i], 12.5f, Color.WHITE, false);
            LinearLayout.LayoutParams ll = new LinearLayout.LayoutParams(-2, -2);
            ll.topMargin = dp(8);
            item.addView(lb, ll);
            LinearLayout.LayoutParams il = new LinearLayout.LayoutParams(-2, -2);
            il.leftMargin = dp(10); il.rightMargin = dp(10);
            row.addView(item, il);
        }
        LinearLayout.LayoutParams rl = new LinearLayout.LayoutParams(-2, -2);
        rl.topMargin = dp(18);
        c.addView(row, rl);
        return c;
    }

    android.graphics.drawable.Drawable ring() {
        GradientDrawable g = new GradientDrawable();
        g.setCornerRadius(dp(14));
        g.setStroke(dp(3), Theme.accent);
        return g;
    }

    View finish() {
        FrameLayout f = new FrameLayout(a);
        LinearLayout c = col();
        c.setGravity(Gravity.CENTER);
        ProgressBar pb = new ProgressBar(a);
        pb.setIndeterminate(true);
        pb.getIndeterminateDrawable().setTint(Color.WHITE);
        c.addView(pb, new LinearLayout.LayoutParams(dp(34), dp(34)));
        final TextView t = Ui.tv(a, "Setting Up Your Mac\u2026", 16, Color.WHITE, true);
        LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(-2, -2);
        tl.topMargin = dp(14);
        c.addView(t, tl);
        f.addView(c, new FrameLayout.LayoutParams(-2, -2, Gravity.CENTER));
        a.h.postDelayed(() -> {
            if (done) return;
            pb.setVisibility(View.GONE);
            t.setText("Welcome to Mac");
            t.setTextSize(30);
            t.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
            t.setAlpha(0f);
            t.animate().alpha(1f).setDuration(500).start();
            a.h.postDelayed(this::close, 1700);
        }, 2600);
        return f;
    }

    void hideIme() {
        try { ((android.view.inputmethod.InputMethodManager) a.getSystemService(android.content.Context.INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(v.getWindowToken(), 0); } catch (Throwable t) { }
    }

    void close() {
        if (done) return;
        done = true;
        hideIme();
        a.prefs.edit().putBoolean("setup_done", true).apply();
        a.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING);
        final boolean restyle = !look.equals(Theme.appearance);
        v.animate().alpha(0f).scaleX(1.05f).scaleY(1.05f).setDuration(600).setInterpolator(new PathInterpolator(.2f, .9f, .25f, 1f))
                .setListener(new AnimatorListenerAdapter() {
                    @Override public void onAnimationEnd(Animator an) {
                        a.screen.removeView(v);
                        if (restyle) a.recreate(); else a.afterSetup();
                    }
                }).start();
    }
}
