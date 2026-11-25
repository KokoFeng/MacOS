package com.macos.launcher;

import android.animation.ValueAnimator;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Feng Store: a Mac App Store look-alike with a built-in (offline) catalog. No backend:
 * Apple-style built-in apps are "downloaded" locally, third-party apps hand off to Google Play.
 */
public class FengStore {
    static class App {
        String name, url, domain, res, cat, sub, dev, desc, tab;
        boolean builtin, arcade;
    }

    static final List<App> APPS = new ArrayList<>();
    static final String[] GATED = {"News", "TV", "Notes", "Reminders", "Calendar", "Contacts", "Maps", "Photo Booth", "Music", "Mail", "Messages",
            "Feng Cut", "Feng Design", "Feng Photo", "Feng Docs", "Feng Sheets", "Feng Code", "2048", "Blocks"};
    static final String[] TABS = {"Discover", "Arcade", "Create", "Work", "Play", "Develop", "Categories", "Updates"};
    static final String[] TAB_SYM = {"star", "arcade.stick.console", "paintbrush.pointed", "paperplane", "gamecontroller", "hammer", "square.grid.2x2", "arrow.down.app"};
    static final String[][] CATS = {
            {"Business", "briefcase.fill", "FFB07A4F"}, {"Developer Tools", "hammer.fill", "FF0A84FF"}, {"Education", "graduationcap.fill", "FF6E6E73"},
            {"Entertainment", "popcorn.fill", "FFFF3B30"}, {"Finance", "creditcard.fill", "FF30B06A"}, {"Games", "gamecontroller.fill", "FF0A84FF"},
            {"Graphics & Design", "paintpalette.fill", "FFFF9F0A"}, {"Health & Fitness", "figure.run", "FF30D158"}, {"Lifestyle", "chair.lounge.fill", "FFFF3B30"},
            {"Medical", "cross.case.fill", "FF5E5CE6"}, {"Music", "music.note", "FF30D158"}, {"News", "newspaper.fill", "FF6E6E73"},
            {"Photo & Video", "camera.fill", "FF6E6E73"}, {"Productivity", "paperplane.fill", "FF0A84FF"}, {"Reference", "quote.bubble.fill", "FF6E6E73"},
            {"Social Networking", "bubble.left.and.bubble.right.fill", "FFFF3B30"}, {"Sports", "sportscourt.fill", "FF30D158"}, {"Travel", "airplane", "FF0A84FF"},
            {"Utilities", "plus.forwardslash.minus", "FF6E6E73"}, {"Weather", "cloud.sun.fill", "FF0A84FF"}};

    static String tabOf(String cat) {
        switch (cat) {
            case "Graphics & Design": case "Photo & Video": case "Music": return "Create";
            case "Productivity": case "Business": return "Work";
            case "Games": return "Play";
            case "Developer Tools": return "Develop";
            default: return "";
        }
    }

    static void b(String name, String res, String cat, String sub, String desc) {
        App a = new App(); a.name = name; a.res = res; a.cat = cat; a.sub = sub; a.desc = desc; a.builtin = true; a.dev = "Feng-Dev / Feng iOS on Droid"; a.tab = tabOf(cat);
        APPS.add(a);
    }

    static void p(String name, String url, String cat, String sub, String dev, String desc, boolean arcade) {
        App a = new App(); a.name = name; a.url = url; a.domain = Store.host(url); a.cat = cat; a.sub = sub; a.dev = dev; a.desc = desc; a.arcade = arcade; a.tab = tabOf(cat);
        APPS.add(a);
    }

    static {
        b("News", "mac_news", "News", "Top stories, all in one place", "Read top stories from BBC and ANTARA with a clean card layout, save stories for later and search headlines.");
        b("Notes", "mac_notes", "Productivity", "Capture thoughts and ideas", "Write and organise notes that are stored on your device, grouped by when you last edited them.");
        b("Reminders", "mac_reminders", "Productivity", "Never forget a thing", "Keep track of tasks with Today, Scheduled, Flagged and your own lists.");
        b("Calendar", "mac_calendar", "Productivity", "Plan your days", "See your events by day, week, month or year and add new ones in a moment.");
        b("Contacts", "mac_contacts", "Productivity", "Your address book", "Browse the contacts on your phone and call, message or email them from a tidy card.");
        b("Maps", "mac_maps", "Travel", "Find places, switch to satellite", "Search for places, pin favourites and switch between the standard and satellite map.");
        b("Photo Booth", "mac_photo_booth", "Photo & Video", "Photos and videos with effects", "Take photos and videos with live effects, a self-timer, flash and a gallery that saves to Photos.");
        b("TV", "mac_tv", "Entertainment", "Watch the shows you love", "Opens Apple TV on the web so you can browse and watch.");
        b("Music", "mac_music", "Music", "All your music", "Opens your music player.");
        b("Mail", "mac_mail", "Productivity", "Email, your way", "Opens your default email app.");
        b("Messages", "mac_messages", "Social Networking", "Stay in touch", "Opens your default messaging app.");
        b("Feng Cut", "mac_feng_cut", "Photo & Video", "Video editor", "Cut videos together, trim and split clips, change speed, add filters, colour adjustments and text, rotate, pick an aspect ratio and export up to 1080p. There is no premium plan: every tool is unlocked.");
        b("Feng Design", "mac_feng_design", "Graphics & Design", "Posters, posts and stories", "Design on a canvas with text, shapes and your own images. Start from a template, move, scale and rotate layers, change colours and opacity, then save a PNG to Photos. Everything is free.");
        b("Feng Photo", "mac_feng_photo", "Photo & Video", "Photo editor", "Open a photo and adjust brightness, contrast, saturation and warmth, apply filters, rotate, flip and crop, draw, add text and save the result to Photos. All tools are free.");
        b("Feng Docs", "mac_feng_docs", "Productivity", "Word processor", "Write documents with bold, italic, underline, headings, colours, highlights, alignment and bullet points. Documents are saved on your phone and can be exported as PDF.");
        b("Feng Sheets", "mac_feng_sheets", "Productivity", "Spreadsheets", "Work with a grid of cells and formulas such as SUM, AVERAGE, MIN, MAX, COUNT, ROUND and IF. Your sheet is saved on your phone and can be exported as CSV.");
        b("Feng Code", "mac_feng_code", "Developer Tools", "Code editor", "Write HTML, CSS and JavaScript with syntax colours and run them instantly in a preview panel with a console.");
        b("2048", "mac_game_2048", "Games", "Join the numbers", "Swipe to slide tiles and combine matching numbers to reach 2048.");
        b("Blocks", "mac_game_blocks", "Games", "Stack falling blocks", "Rotate and drop falling blocks to clear lines. Touch controls and buttons, with levels that speed up.");

    }

    static {
        for (App x : APPS) if (x.name.equals("2048") || x.name.equals("Blocks")) x.arcade = true;
    }

    static FengStore cur;

    public static boolean gated(String name) {
        for (String g : GATED) if (g.equals(name)) return true;
        return false;
    }

    public static boolean installed(MainActivity a, String name) {
        return a.prefs.getStringSet("store_inst", new HashSet<String>()).contains(name);
    }

    static void markInstalled(MainActivity a, String name) {
        Set<String> s = new HashSet<>(a.prefs.getStringSet("store_inst", new HashSet<String>()));
        s.add(name);
        a.prefs.edit().putStringSet("store_inst", s).apply();
    }

    public static void needInstall(final MainActivity a, final String name) {
        MacAlert.show(a, a.screen, "\u201C" + name + "\u201D isn\u2019t installed",
                "Download it from Feng Store first, then open it again.",
                new String[]{"Open Feng Store", "Cancel"}, new Runnable[]{() -> open(a, name), null}, true);
    }

    public static void open(final MainActivity a) { open(a, null); }

    public static void open(final MainActivity a, final String app) {
        a.openOrFocus("Feng Store", () -> cur = new FengStore(a));
        if (app != null && cur != null) cur.showDetail(find(app));
    }

    public static void onResume() { if (cur != null) cur.refreshButtons(); }

    static App find(String name) { for (App x : APPS) if (x.name.equals(name)) return x; return null; }

    // ---------- instance ----------
    final MainActivity a;
    final Map<String, Float> dl = new HashMap<>();
    final Map<String, ValueAnimator> anims = new HashMap<>();
    final List<Btn> buttons = new ArrayList<>();
    final ExecutorService ex = Executors.newFixedThreadPool(3);
    final Map<String, Bitmap> favs = new HashMap<>();
    String tab = "Discover", query = "", catFilter;
    App detail;
    LinearLayout side;
    FrameLayout host;
    TextView back, titleTv, share;
    boolean closed;

    FengStore(MainActivity act) {
        a = act;
        LinearLayout tb = new LinearLayout(a);
        tb.setGravity(Gravity.CENTER_VERTICAL);
        back = Ui.circleBtn(a, "\u2039");
        back.setVisibility(View.INVISIBLE);
        back.setOnClickListener(v -> goBack());
        tb.addView(back, new LinearLayout.LayoutParams(a.dp(30), a.dp(30)));
        titleTv = Ui.tv(a, "Discover", 14, Theme.fg(), true);
        titleTv.setGravity(Gravity.CENTER);
        tb.addView(titleTv, new LinearLayout.LayoutParams(0, -2, 1f));
        share = Ui.sfBtn(a, "square.and.arrow.up", 13);
        share.setVisibility(View.INVISIBLE);
        share.setOnClickListener(v -> shareApp());
        tb.addView(share, new LinearLayout.LayoutParams(a.dp(30), a.dp(30)));

        LinearLayout body = new LinearLayout(a);
        LinearLayout left = new LinearLayout(a);
        left.setOrientation(LinearLayout.VERTICAL);
        left.setBackgroundColor(Theme.side());
        final EditText search = new EditText(a);
        search.setHint("Search");
        search.setSingleLine(true);
        search.setTextSize(13);
        search.setTextColor(Theme.fg());
        search.setHintTextColor(Theme.sub());
        Emoji.apply(search);
        search.setBackground(Ui.rr(Theme.dark ? 0x2EFFFFFF : 0x12000000, a.dp(10)));
        search.setPadding(a.dp(12), 0, a.dp(12), 0);
        search.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int x, int y, int z) {}
            public void onTextChanged(CharSequence s, int x, int y, int z) {}
            public void afterTextChanged(Editable e) { query = e.toString().toLowerCase(Locale.ROOT).trim(); detail = null; catFilter = null; render(); }
        });
        LinearLayout.LayoutParams sl = new LinearLayout.LayoutParams(-1, a.dp(32));
        sl.setMargins(a.dp(10), a.dp(8), a.dp(10), a.dp(8));
        left.addView(search, sl);
        side = new LinearLayout(a);
        side.setOrientation(LinearLayout.VERTICAL);
        side.setPadding(a.dp(8), 0, a.dp(8), 0);
        ScrollView sv = new ScrollView(a);
        sv.setVerticalScrollBarEnabled(false);
        sv.addView(side);
        left.addView(sv, new LinearLayout.LayoutParams(-1, 0, 1f));
        left.addView(profile(), new LinearLayout.LayoutParams(-1, a.dp(52)));
        body.addView(left, new LinearLayout.LayoutParams(a.dp(172), -1));
        host = new FrameLayout(a);
        host.setBackgroundColor(Theme.dark ? 0xFF1C1C1E : 0xFFFFFFFF);
        body.addView(host, new LinearLayout.LayoutParams(0, -1, 1f));
        MacWindow w = new MacWindow(a, "Feng Store", tb, null, body);
        w.onClose = () -> { closed = true; cur = null; for (ValueAnimator v : new ArrayList<>(anims.values())) v.cancel(); };
        a.openWindow(w, 780, 370);
        drawSide();
        render();
    }

    View profile() {
        LinearLayout r = new LinearLayout(a);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setPadding(a.dp(12), 0, a.dp(8), 0);
        FrameLayout av = new FrameLayout(a);
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{0xFFC9CDD6, 0xFF8D929E});
        g.setShape(GradientDrawable.OVAL);
        av.setBackground(g);
        ImageView pi = Sf.img(a, "person.fill", 0xF2FFFFFF);
        av.addView(pi, new FrameLayout.LayoutParams(a.dp(20), a.dp(20), Gravity.CENTER | Gravity.BOTTOM) {{ bottomMargin = a.dp(-2); }});
        av.setClipToOutline(true);
        av.setOutlineProvider(new android.view.ViewOutlineProvider() {
            @Override public void getOutline(View v, android.graphics.Outline o) { o.setOval(0, 0, v.getWidth(), v.getHeight()); }
        });
        r.addView(av, new LinearLayout.LayoutParams(a.dp(30), a.dp(30)));
        LinearLayout t = new LinearLayout(a);
        t.setOrientation(LinearLayout.VERTICAL);
        t.setPadding(a.dp(8), 0, 0, 0);
        TextView n = Ui.tv(a, a.prefs.getString("user_name", "User"), 12.5f, Theme.fg(), true);
        n.setSingleLine(true);
        t.addView(n);
        t.addView(Ui.tv(a, "Feng Store", 11, Theme.sub(), false));
        r.addView(t);
        return r;
    }

    void drawSide() {
        side.removeAllViews();
        for (int i = 0; i < TABS.length; i++) {
            final String t = TABS[i];
            LinearLayout r = new LinearLayout(a);
            r.setGravity(Gravity.CENTER_VERTICAL);
            r.setPadding(a.dp(8), a.dp(7), a.dp(8), a.dp(7));
            boolean on = tab.equals(t) && query.isEmpty();
            if (on) r.setBackground(Ui.rr(Theme.dark ? 0x33FFFFFF : 0x1F000000, a.dp(9)));
            r.addView(Sf.img(a, TAB_SYM[i], 0xFF0A84FF), new LinearLayout.LayoutParams(a.dp(18), a.dp(18)));
            TextView n = Ui.tv(a, t, 13.5f, Theme.fg(), false);
            n.setPadding(a.dp(10), 0, 0, 0);
            r.addView(n, new LinearLayout.LayoutParams(0, -2, 1f));
            if (t.equals("Updates")) {
                int c = installedList().size();
                if (c > 0) r.addView(Ui.tv(a, String.valueOf(c), 12, Theme.sub(), false));
            }
            r.setOnClickListener(v -> { tab = t; query = ""; detail = null; catFilter = null; drawSide(); render(); });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
            lp.bottomMargin = a.dp(1);
            side.addView(r, lp);
        }
    }

    void goBack() {
        if (detail != null) detail = null;
        else if (catFilter != null) catFilter = null;
        render();
    }

    void showDetail(App ap) {
        if (ap == null) return;
        detail = ap;
        render();
    }

    void shareApp() {
        if (detail == null) return;
        Intent i = new Intent(Intent.ACTION_SEND);
        i.setType("text/plain");
        i.putExtra(Intent.EXTRA_TEXT, detail.name + (detail.url != null ? "\n" + detail.url : ""));
        a.startExternal(Intent.createChooser(i, "Share"));
    }

    // ---------- install logic ----------
    boolean isInstalled(App ap) { return installed(a, ap.name); }

    void openApp(App ap) {
        if (ap.builtin) a.openBuiltin(ap.name); else openWeb(a, ap.name);
    }

    void uninstall(App ap) {
        Set<String> st = new HashSet<>(a.prefs.getStringSet("store_inst", new HashSet<String>()));
        st.remove(ap.name);
        a.prefs.edit().putStringSet("store_inst", st).apply();
        MacWindow w = a.findWindow(ap.name);
        if (w != null) w.close();
        Store.Item it = ap.builtin ? new Store.Item("b", ap.name, "") : new Store.Item("w", ap.name, ap.url);
        a.removeFromList("dock_items", it);
        a.removeFromList("desk_items", it);
        a.apps = null;
        a.buildDock();
        a.buildDesktopIcons();
        Toast.makeText(a, ap.name + " removed", Toast.LENGTH_SHORT).show();
        drawSide();
        render();
    }

    public static void openWeb(MainActivity a, String name) {
        App ap = find(name);
        if (ap == null || ap.url == null) return;
        MacWindow w = a.findWindow(ap.name);
        if (w != null) { w.restore(); a.focusWindow(w); } else SafariApp.openWebApp(a, ap.name, ap.url);
    }

    public static String urlOf(String name) { App ap = find(name); return ap == null ? null : ap.url; }

    public static int catIndex(String cat) {
        switch (cat) {
            case "Social Networking": return 1;
            case "Productivity": case "Business": case "Finance": return 2;
            case "Entertainment": case "Music": case "Games": case "Photo & Video": return 3;
            case "News": case "Reference": case "Education": return 4;
            default: return 0;
        }
    }

    void startDownload(final App ap) {
        if (anims.containsKey(ap.name)) return;
        ValueAnimator v = ValueAnimator.ofFloat(0f, 1f);
        v.setDuration(ap.builtin ? 2800 : 2200);
        v.setInterpolator(new DecelerateInterpolator(.7f));
        v.addUpdateListener(an -> { dl.put(ap.name, (Float) an.getAnimatedValue()); tickButtons(); });
        v.addListener(new android.animation.AnimatorListenerAdapter() {
            boolean cancelled;
            @Override public void onAnimationCancel(android.animation.Animator an) { cancelled = true; }
            @Override public void onAnimationEnd(android.animation.Animator an) {
                anims.remove(ap.name);
                dl.remove(ap.name);
                if (cancelled || closed) { tickButtons(); return; }
                markInstalled(a, ap.name);
                a.apps = null;
                if (ap.builtin) a.dockInsert(new Store.Item("b", ap.name, ""));
                else {
                    a.dockInsert(new Store.Item("w", ap.name, ap.url));
                    if (!Store.favFile(a, ap.url).exists()) Store.tried.remove(Store.host(ap.url));
                    Store.fetchFavicon(a, ap.url, () -> a.runOnUiThread(() -> { a.apps = null; a.buildDock(); a.buildDesktopIcons(); }));
                }
                a.buildDock();
                Toast.makeText(a, ap.name + " is ready", Toast.LENGTH_SHORT).show();
                drawSide();
                tickButtons();
            }
        });
        if (!ap.builtin) Store.fetchFavicon(a, ap.url, null);
        anims.put(ap.name, v);
        dl.put(ap.name, 0f);
        tickButtons();
        v.start();
    }

    void cancelDownload(App ap) {
        ValueAnimator v = anims.get(ap.name);
        if (v != null) v.cancel();
    }

    void tickButtons() { for (Btn b : buttons) b.invalidate(); }

    void refreshButtons() { drawSide(); tickButtons(); if (tab.equals("Updates")) render(); }

    List<App> installedList() {
        List<App> l = new ArrayList<>();
        for (App ap : APPS) if (isInstalled(ap)) l.add(ap);
        return l;
    }

    // ---------- download button ----------
    class Btn extends View {
        final App ap;
        final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        float morph;
        boolean wasDl;
        ValueAnimator ma;

        Btn(App ap) {
            super(a);
            this.ap = ap;
            setOnClickListener(v -> {
                if (anims.containsKey(ap.name)) cancelDownload(ap);
                else if (isInstalled(ap)) openApp(ap);
                else startDownload(ap);
            });
        }

        @Override protected void onDraw(Canvas c) {
            boolean d = dl.containsKey(ap.name);
            if (d != wasDl) {
                wasDl = d;
                if (ma != null) ma.cancel();
                ma = ValueAnimator.ofFloat(morph, d ? 1f : 0f);
                ma.setDuration(260);
                ma.addUpdateListener(an -> { morph = (Float) an.getAnimatedValue(); invalidate(); });
                ma.start();
            }
            float w = getWidth(), h = getHeight();
            float cw = w + (h - w) * morph;
            RectF r = new RectF((w - cw) / 2f, 0, (w + cw) / 2f, h);
            p.setStyle(Paint.Style.FILL);
            p.setColor(Theme.dark ? 0x33FFFFFF : 0x12000000);
            c.drawRoundRect(r, h / 2f, h / 2f, p);
            if (morph < .5f) {
                p.setColor(0xFF0A84FF);
                p.setAlpha((int) (255 * (1 - morph * 2)));
                p.setTextAlign(Paint.Align.CENTER);
                p.setTextSize(a.dp(12));
                p.setTypeface(Emoji.font(true));
                String t = isInstalled(ap) ? "OPEN" : "GET";
                c.drawText(t, w / 2f, h / 2f + p.getTextSize() * .35f, p);
                p.setAlpha(255);
            } else {
                float cx = w / 2f, cy = h / 2f, rad = h / 2f - a.dp(2.5f);
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(a.dp(2));
                p.setColor(Theme.dark ? 0x44FFFFFF : 0x33000000);
                c.drawCircle(cx, cy, rad, p);
                p.setColor(0xFF0A84FF);
                p.setStrokeCap(Paint.Cap.ROUND);
                Float pr = dl.get(ap.name);
                c.drawArc(new RectF(cx - rad, cy - rad, cx + rad, cy + rad), -90, 360f * (pr == null ? 0 : Math.max(.03f, pr)), false, p);
                p.setStyle(Paint.Style.FILL);
                float sq = h * .17f;
                c.drawRoundRect(new RectF(cx - sq, cy - sq, cx + sq, cy + sq), a.dp(1.5f), a.dp(1.5f), p);
            }
        }
    }

    View button(App ap, int wDp, int hDp) {
        Btn b = new Btn(ap);
        buttons.add(b);
        b.setLayoutParams(new LinearLayout.LayoutParams(a.dp(wDp), a.dp(hDp)));
        return b;
    }

    // ---------- icons ----------
    View icon(final App ap, int sizeDp) {
        final ImageView iv = new ImageView(a);
        final int sz = a.dp(sizeDp);
        iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
        iv.setClipToOutline(true);
        iv.setOutlineProvider(new android.view.ViewOutlineProvider() {
            @Override public void getOutline(View v, android.graphics.Outline o) { o.setRoundRect(0, 0, v.getWidth(), v.getHeight(), sz * .225f); }
        });
        if (ap.builtin) {
            iv.setImageResource(a.res(ap.res));
        } else {
            {
                iv.setBackgroundColor(Theme.dark ? 0xFF3A3A3C : 0xFFF2F2F7);
                int pad = sz / 5;
                iv.setPadding(pad, pad, pad, pad);
                iv.setImageDrawable(Sf.get(a, "app.fill", Theme.sub()));
                Bitmap c = favs.get(ap.domain);
                if (c != null) { iv.setImageBitmap(c); iv.setBackgroundColor(0xFFFFFFFF); }
                else ex.execute(() -> {
                    final Bitmap b = favicon(ap.domain);
                    if (b != null) a.runOnUiThread(() -> { favs.put(ap.domain, b); iv.setImageBitmap(b); iv.setBackgroundColor(0xFFFFFFFF); });
                });
            }
        }
        iv.setLayoutParams(new LinearLayout.LayoutParams(sz, sz));
        return iv;
    }

    Bitmap favicon(String domain) {
        try {
            File f = new File(a.getCacheDir(), "fav_" + domain + ".png");
            if (!f.exists()) {
                HttpURLConnection c = (HttpURLConnection) new URL("https://www.google.com/s2/favicons?domain=" + domain + "&sz=128").openConnection();
                c.setConnectTimeout(8000); c.setReadTimeout(8000);
                try (InputStream in = c.getInputStream(); FileOutputStream out = new FileOutputStream(f)) {
                    byte[] buf = new byte[8192]; int n;
                    while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
                }
            }
            return BitmapFactory.decodeFile(f.getAbsolutePath());
        } catch (Exception e) { return null; }
    }

    // ---------- pages ----------
    void render() {
        buttons.clear();
        host.removeAllViews();
        boolean sub = detail != null || catFilter != null;
        back.setVisibility(sub ? View.VISIBLE : View.INVISIBLE);
        share.setVisibility(detail != null && !detail.builtin ? View.VISIBLE : View.INVISIBLE);
        View page;
        if (detail != null) { titleTv.setText(""); page = detailPage(detail); }
        else if (!query.isEmpty()) { titleTv.setText("Search"); page = listPage("Search results", filter(query), false); }
        else if (catFilter != null) { titleTv.setText(catFilter); page = listPage(catFilter, byCat(catFilter), false); }
        else {
            titleTv.setText(tab);
            switch (tab) {
                case "Discover": page = discover(); break;
                case "Arcade": page = arcade(); break;
                case "Categories": page = categories(); break;
                case "Updates": page = updates(); break;
                default: page = listPage(tab, byTab(tab), false);
            }
        }
        host.addView(page, new FrameLayout.LayoutParams(-1, -1));
    }

    List<App> filter(String q) {
        List<App> l = new ArrayList<>();
        for (App x : APPS) if (x.name.toLowerCase(Locale.ROOT).contains(q) || x.sub.toLowerCase(Locale.ROOT).contains(q) || x.cat.toLowerCase(Locale.ROOT).contains(q)) l.add(x);
        return l;
    }

    List<App> byCat(String c) { List<App> l = new ArrayList<>(); for (App x : APPS) if (x.cat.equals(c)) l.add(x); return l; }
    List<App> byTab(String t) { List<App> l = new ArrayList<>(); for (App x : APPS) if (x.tab.equals(t)) l.add(x); return l; }

    ScrollView scroll(LinearLayout col) {
        ScrollView sv = new ScrollView(a);
        sv.setVerticalScrollBarEnabled(false);
        sv.addView(col);
        return sv;
    }

    LinearLayout pageCol() {
        LinearLayout c = new LinearLayout(a);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setPadding(a.dp(18), a.dp(10), a.dp(18), a.dp(18));
        return c;
    }

    TextView big(String s) {
        TextView t = Ui.tv(a, s, 26, Theme.fg(), true);
        t.setPadding(0, 0, 0, a.dp(8));
        return t;
    }

    TextView section(String s) {
        TextView t = Ui.tv(a, s, 17, Theme.fg(), true);
        t.setPadding(0, a.dp(14), 0, a.dp(8));
        return t;
    }

    View discover() {
        LinearLayout col = pageCol();
        col.addView(big("Discover"));
        col.addView(hero(find("News"), "FEATURED APP", "Read the world\u2019s top stories", "Download News and catch up in one place."));
        LinearLayout fr = new LinearLayout(a);
        fr.addView(featured(find("Photo Booth"), "FROM THE EDITORS", "Smile for the camera"), new LinearLayout.LayoutParams(0, -2, 1f));
        LinearLayout.LayoutParams l2 = new LinearLayout.LayoutParams(0, -2, 1f);
        l2.leftMargin = a.dp(10);
        fr.addView(featured(find("Feng Cut"), "GET STARTED", "Edit videos like a pro"), l2);
        LinearLayout.LayoutParams fl = new LinearLayout.LayoutParams(-1, -2);
        fl.topMargin = a.dp(12);
        col.addView(fr, fl);
        col.addView(section("Best New Apps and Updates"));
        List<App> best = new ArrayList<>();
        for (String n : new String[]{"Notes", "Reminders", "Feng Design", "Feng Docs", "Calendar", "Feng Sheets"}) best.add(find(n));
        rows(col, best, 2);
        col.addView(sepLine(), new LinearLayout.LayoutParams(-1, 1) {{ topMargin = a.dp(14); }});
        col.addView(section("Apps and Games We Love Right Now"));
        List<App> love = new ArrayList<>();
        for (String n : new String[]{"Maps", "2048", "Music", "Feng Code", "Feng Photo", "Blocks"}) love.add(find(n));
        rows(col, love, 2);
        return scroll(col);
    }

    View sepLine() { return Ui.sepLine(a); }

    View hero(final App ap, String label, String title, String sub) {
        FrameLayout f = new FrameLayout(a);
        f.setClipToOutline(true);
        f.setOutlineProvider(new android.view.ViewOutlineProvider() {
            @Override public void getOutline(View v, android.graphics.Outline o) { o.setRoundRect(0, 0, v.getWidth(), v.getHeight(), a.dp(14)); }
        });
        LinearLayout row = new LinearLayout(a);
        LinearLayout left = new LinearLayout(a);
        left.setOrientation(LinearLayout.VERTICAL);
        left.setPadding(a.dp(16), a.dp(14), a.dp(12), a.dp(14));
        left.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{0xFF414859, 0xFF2B3040}));
        left.addView(Ui.tv(a, label, 10.5f, 0xCCFFFFFF, true));
        TextView t = Ui.tv(a, title, 19, Color.WHITE, false);
        t.setPadding(0, a.dp(6), 0, 0);
        left.addView(t);
        View sp = new View(a);
        left.addView(sp, new LinearLayout.LayoutParams(1, 0, 1f));
        left.addView(Ui.tv(a, sub, 11.5f, 0xDDFFFFFF, false));
        row.addView(left, new LinearLayout.LayoutParams(0, -1, 2f));
        FrameLayout right = new FrameLayout(a);
        ImageView bg = new ImageView(a);
        bg.setScaleType(ImageView.ScaleType.CENTER_CROP);
        if (a.wall.getSharp() != null) bg.setImageBitmap(a.wall.getSharp());
        right.addView(bg, new FrameLayout.LayoutParams(-1, -1));
        View ic = icon(ap, 76);
        right.addView(ic, new FrameLayout.LayoutParams(a.dp(76), a.dp(76), Gravity.CENTER));
        row.addView(right, new LinearLayout.LayoutParams(0, -1, 3f));
        f.addView(row, new FrameLayout.LayoutParams(-1, -1));
        f.setLayoutParams(new LinearLayout.LayoutParams(-1, a.dp(160)));
        f.setOnClickListener(v -> showDetail(ap));
        return f;
    }

    View featured(final App ap, String label, String title) {
        LinearLayout c = new LinearLayout(a);
        c.setGravity(Gravity.CENTER_VERTICAL);
        c.setPadding(a.dp(14), a.dp(12), a.dp(12), a.dp(12));
        c.setBackground(Ui.rr(Theme.dark ? 0xFF2A2A2D : 0xFFF4F4F6, a.dp(14)));
        LinearLayout t = new LinearLayout(a);
        t.setOrientation(LinearLayout.VERTICAL);
        t.addView(Ui.tv(a, label, 10, Theme.sub(), true));
        TextView n = Ui.tv(a, title, 15.5f, Theme.fg(), false);
        n.setPadding(0, a.dp(3), 0, a.dp(3));
        t.addView(n);
        t.addView(Ui.tv(a, ap.sub, 11.5f, Theme.sub(), false));
        c.addView(t, new LinearLayout.LayoutParams(0, -2, 1f));
        FrameLayout circle = new FrameLayout(a);
        GradientDrawable g = new GradientDrawable();
        g.setShape(GradientDrawable.OVAL);
        g.setColor(Theme.dark ? 0xFF3A3A3E : 0xFFE4ECFA);
        circle.setBackground(g);
        circle.addView(icon(ap, 52), new FrameLayout.LayoutParams(a.dp(52), a.dp(52), Gravity.CENTER));
        c.addView(circle, new LinearLayout.LayoutParams(a.dp(78), a.dp(78)));
        c.setOnClickListener(v -> showDetail(ap));
        return c;
    }

    void rows(LinearLayout col, List<App> apps, int cols) {
        for (int i = 0; i < apps.size(); i += cols) {
            LinearLayout row = new LinearLayout(a);
            for (int k = 0; k < cols; k++) {
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1f);
                lp.leftMargin = k == 0 ? 0 : a.dp(18);
                if (i + k < apps.size()) row.addView(appRow(apps.get(i + k), i + cols < apps.size() || k < cols), lp);
                else row.addView(new View(a), lp);
            }
            col.addView(row);
        }
    }

    View appRow(final App ap, boolean sep) {
        LinearLayout r = new LinearLayout(a);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setPadding(0, a.dp(7), 0, a.dp(7));
        r.addView(icon(ap, 46));
        LinearLayout t = new LinearLayout(a);
        t.setOrientation(LinearLayout.VERTICAL);
        t.setPadding(a.dp(12), 0, a.dp(8), 0);
        TextView n = Ui.tv(a, ap.name, 13.5f, Theme.fg(), false);
        n.setSingleLine(true);
        n.setEllipsize(android.text.TextUtils.TruncateAt.END);
        t.addView(n);
        TextView s = Ui.tv(a, ap.sub, 11.5f, Theme.sub(), false);
        s.setSingleLine(true);
        s.setEllipsize(android.text.TextUtils.TruncateAt.END);
        t.addView(s);
        r.addView(t, new LinearLayout.LayoutParams(0, -2, 1f));
        r.addView(button(ap, 62, 26));
        r.setOnClickListener(v -> showDetail(ap));
        return r;
    }

    View listPage(String title, List<App> apps, boolean unused) {
        LinearLayout col = pageCol();
        col.addView(big(title));
        if (apps.isEmpty()) {
            TextView e = Ui.tv(a, "No Results", 14, Theme.sub(), false);
            e.setPadding(0, a.dp(20), 0, 0);
            col.addView(e);
        } else rows(col, apps, 2);
        return scroll(col);
    }

    View arcade() {
        LinearLayout col = pageCol();
        FrameLayout f = new FrameLayout(a);
        f.setClipToOutline(true);
        f.setOutlineProvider(new android.view.ViewOutlineProvider() {
            @Override public void getOutline(View v, android.graphics.Outline o) { o.setRoundRect(0, 0, v.getWidth(), v.getHeight(), a.dp(14)); }
        });
        ImageView bg = new ImageView(a);
        bg.setScaleType(ImageView.ScaleType.CENTER_CROP);
        if (a.wall.getSharp() != null) bg.setImageBitmap(a.wall.getSharp());
        f.addView(bg, new FrameLayout.LayoutParams(-1, -1));
        TextView t = Ui.tv(a, "Arcade", 34, Color.WHITE, true);
        t.setShadowLayer(a.dp(6), 0, 0, 0x66000000);
        f.addView(t, new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.LEFT) {{ leftMargin = a.dp(16); topMargin = a.dp(12); }});
        final App top = find("2048");
        LinearLayout card = new LinearLayout(a);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(a.dp(14), a.dp(10), a.dp(14), a.dp(10));
        card.setBackground(Ui.rr(0x66202028, a.dp(12)));
        card.addView(Ui.tv(a, "SHOWCASE", 10.5f, 0xCCFFFFFF, true));
        TextView ct = Ui.tv(a, "Join the numbers", 17, Color.WHITE, true);
        ct.setPadding(0, a.dp(3), 0, a.dp(2));
        card.addView(ct);
        card.addView(Ui.tv(a, "View Game \u203A", 11.5f, Color.WHITE, true));
        card.setOnClickListener(v -> showDetail(top));
        f.addView(card, new FrameLayout.LayoutParams(a.dp(220), -2, Gravity.BOTTOM | Gravity.LEFT) {{ leftMargin = a.dp(14); bottomMargin = a.dp(14); }});
        f.setLayoutParams(new LinearLayout.LayoutParams(-1, a.dp(180)));
        col.addView(f);
        col.addView(section("Popular Games"));
        List<App> g = new ArrayList<>();
        for (App x : APPS) if (x.arcade) g.add(x);
        rows(col, g, 2);
        return scroll(col);
    }

    View categories() {
        LinearLayout col = pageCol();
        col.addView(big("Categories"));
        for (int i = 0; i < CATS.length; i += 3) {
            LinearLayout row = new LinearLayout(a);
            for (int k = 0; k < 3; k++) {
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1f);
                lp.leftMargin = k == 0 ? 0 : a.dp(14);
                if (i + k >= CATS.length) { row.addView(new View(a), lp); continue; }
                final String[] c = CATS[i + k];
                LinearLayout cell = new LinearLayout(a);
                cell.setOrientation(LinearLayout.VERTICAL);
                LinearLayout rr = new LinearLayout(a);
                rr.setGravity(Gravity.CENTER_VERTICAL);
                rr.setPadding(0, a.dp(9), 0, a.dp(9));
                int color = (int) Long.parseLong(c[2], 16);
                rr.addView(Sf.img(a, c[1], color), new LinearLayout.LayoutParams(a.dp(24), a.dp(22)));
                TextView n = Ui.tv(a, c[0], 13, Theme.fg(), false);
                n.setPadding(a.dp(12), 0, 0, 0);
                n.setSingleLine(true);
                rr.addView(n);
                cell.addView(rr);
                cell.addView(sepLine(), new LinearLayout.LayoutParams(-1, 1));
                cell.setOnClickListener(v -> { catFilter = c[0]; render(); });
                row.addView(cell, lp);
            }
            col.addView(row);
        }
        return scroll(col);
    }

    View updates() {
        LinearLayout col = pageCol();
        col.addView(big("Updates"));
        List<App> inst = installedList();
        if (inst.isEmpty()) {
            TextView e = Ui.tv(a, "Nothing installed yet. Get apps from Discover.", 14, Theme.sub(), false);
            e.setPadding(0, a.dp(14), 0, 0);
            col.addView(e);
        } else {
            col.addView(section("Installed"));
            rows(col, inst, 2);
            TextView ok = Ui.tv(a, "All apps are up to date.", 12, Theme.sub(), false);
            ok.setPadding(0, a.dp(14), 0, 0);
            col.addView(ok);
        }
        return scroll(col);
    }

    View detailPage(final App ap) {
        LinearLayout col = pageCol();
        LinearLayout top = new LinearLayout(a);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.addView(icon(ap, 92));
        LinearLayout t = new LinearLayout(a);
        t.setOrientation(LinearLayout.VERTICAL);
        t.setPadding(a.dp(18), 0, 0, 0);
        TextView n = Ui.tv(a, ap.name, 24, Theme.fg(), true);
        t.addView(n);
        TextView s = Ui.tv(a, ap.sub, 14, Theme.sub(), false);
        s.setPadding(0, a.dp(2), 0, a.dp(12));
        t.addView(s);
        t.addView(button(ap, 78, 30));
        if (isInstalled(ap)) {
            TextView rm = Ui.tv(a, "Delete App", 12, 0xFFFF3B30, false);
            rm.setPadding(0, a.dp(8), 0, 0);
            rm.setOnClickListener(v -> uninstall(ap));
            t.addView(rm);
        }
        top.addView(t);
        col.addView(top);
        col.addView(sepLine(), new LinearLayout.LayoutParams(-1, 1) {{ topMargin = a.dp(16); }});
        LinearLayout info = new LinearLayout(a);
        String[][] cells = {{"CATEGORY", ap.cat}, {"DEVELOPER", ap.dev}, {"PRICE", "Free"}, {"TYPE", ap.builtin ? "Built-in app" : "Web app"}};
        for (int i = 0; i < cells.length; i++) {
            LinearLayout c = new LinearLayout(a);
            c.setOrientation(LinearLayout.VERTICAL);
            c.setGravity(Gravity.CENTER_HORIZONTAL);
            c.setPadding(a.dp(6), a.dp(10), a.dp(6), a.dp(10));
            c.addView(Ui.tv(a, cells[i][0], 10, Theme.sub(), true));
            TextView v = Ui.tv(a, cells[i][1], 12.5f, Theme.fg(), true);
            v.setGravity(Gravity.CENTER);
            v.setPadding(0, a.dp(4), 0, 0);
            c.addView(v);
            info.addView(c, new LinearLayout.LayoutParams(0, -2, 1f));
            if (i < cells.length - 1) info.addView(sepV(), new LinearLayout.LayoutParams(1, a.dp(34)) {{ gravity = Gravity.CENTER_VERTICAL; }});
        }
        col.addView(info);
        col.addView(sepLine(), new LinearLayout.LayoutParams(-1, 1));
        TextView dh = Ui.tv(a, "Description", 17, Theme.fg(), true);
        dh.setPadding(0, a.dp(14), 0, a.dp(6));
        col.addView(dh);
        TextView d = Ui.tv(a, ap.desc, 13.5f, Theme.fg(), false);
        d.setLineSpacing(0, 1.1f);
        col.addView(d);
        if (!ap.builtin) {
            TextView note = Ui.tv(a, "Runs in its own window like a Mac app. Needs an internet connection; sign in with your own account.", 11.5f, Theme.sub(), false);
            note.setPadding(0, a.dp(12), 0, 0);
            col.addView(note);
        }
        return scroll(col);
    }

    View sepV() {
        View v = new View(a);
        v.setBackgroundColor(Theme.sep());
        return v;
    }
}
