package com.macos.launcher;

import android.annotation.SuppressLint;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.LruCache;
import android.view.Gravity;
import android.view.View;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.json.JSONArray;
import org.json.JSONObject;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserFactory;

/** Apple News style reader: sidebar of topics, Top Stories + card grid from public RSS feeds, in-window article view. */
public class NewsApp {
    static class Item { String title, link, src, img, desc = ""; long ts; }
    static class Topic { String name, letter; int color; String[] feeds; String[] srcNames; }

    static final int RED = 0xFFFF2D55;
    static final String BBC = "https://feeds.bbci.co.uk/news/";
    static final Topic[] TOPICS = {
            topic("Today", "N", RED, new String[]{BBC + "rss.xml", "https://www.antaranews.com/rss/terkini.xml"}, "BBC News", "ANTARA"),
            topic("Indonesia", "I", 0xFFE5333B, new String[]{"https://www.antaranews.com/rss/terkini.xml", "https://www.antaranews.com/rss/nasional.xml"}, "ANTARA", "ANTARA"),
            topic("World", "W", 0xFF0A84FF, new String[]{BBC + "world/rss.xml"}, "BBC News"),
            topic("Technology", "T", 0xFF5E5CE6, new String[]{BBC + "technology/rss.xml"}, "BBC News"),
            topic("Business", "B", 0xFF30B0C7, new String[]{BBC + "business/rss.xml"}, "BBC News"),
            topic("Science", "S", 0xFF30D158, new String[]{BBC + "science_and_environment/rss.xml"}, "BBC News"),
            topic("Health", "H", 0xFFFF9F0A, new String[]{BBC + "health/rss.xml"}, "BBC News"),
            topic("Entertainment", "E", 0xFFBF5AF2, new String[]{BBC + "entertainment_and_arts/rss.xml"}, "BBC News"),
            topic("Sports", "S", 0xFFFF453A, new String[]{"https://feeds.bbci.co.uk/sport/rss.xml"}, "BBC Sport")};

    static Topic topic(String n, String l, int c, String[] f, String... s) {
        Topic t = new Topic(); t.name = n; t.letter = l; t.color = c; t.feeds = f; t.srcNames = s; return t;
    }

    final MainActivity a;
    final Handler ui = new Handler(Looper.getMainLooper());
    final ExecutorService ex = Executors.newFixedThreadPool(4);
    final Map<String, List<Item>> cache = new HashMap<>();
    final Map<String, Long> cacheTs = new HashMap<>();
    final LruCache<String, Bitmap> imgs = new LruCache<>(60);
    String sel = "Today", query = "";
    LinearLayout side;
    FrameLayout host;
    ScrollView feedScroll;
    boolean closed;

    public static void open(MainActivity a) { new NewsApp(a); }

    NewsApp(MainActivity act) {
        a = act;
        TextView refresh = Ui.sfBtn(a, "arrow.clockwise", 13);
        refresh.setOnClickListener(v -> { cache.remove(sel); show(sel); });
        LinearLayout tb = new LinearLayout(a);
        tb.setGravity(Gravity.CENTER_VERTICAL);
        tb.addView(new View(a), new LinearLayout.LayoutParams(0, 1, 1f));
        tb.addView(refresh, new LinearLayout.LayoutParams(a.dp(32), a.dp(32)));
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
            public void afterTextChanged(Editable e) { query = e.toString().toLowerCase(Locale.ROOT).trim(); renderFeed(); }
        });
        LinearLayout.LayoutParams sl = new LinearLayout.LayoutParams(-1, a.dp(32));
        sl.setMargins(a.dp(10), a.dp(8), a.dp(10), a.dp(6));
        left.addView(search, sl);
        side = new LinearLayout(a);
        side.setOrientation(LinearLayout.VERTICAL);
        side.setPadding(a.dp(8), 0, a.dp(8), a.dp(8));
        ScrollView sv = new ScrollView(a);
        sv.setVerticalScrollBarEnabled(false);
        sv.addView(side);
        left.addView(sv, new LinearLayout.LayoutParams(-1, 0, 1f));
        body.addView(left, new LinearLayout.LayoutParams(a.dp(190), -1));
        host = new FrameLayout(a);
        host.setBackgroundColor(Theme.dark ? 0xFF161618 : 0xFFF4F4F6);
        body.addView(host, new LinearLayout.LayoutParams(0, -1, 1f));
        MacWindow w = new MacWindow(a, "News", tb, null, body);
        w.onClose = () -> closed = true;
        a.openWindow(w, 740, 350);
        drawSide();
        show("Today");
    }

    // ---------- sidebar ----------
    void drawSide() {
        side.removeAllViews();
        addRow(topicOf("Today"), null);
        addRow(topicOf("Indonesia"), null);
        header("Library");
        addPlain("Saved Stories", "bookmark.fill");
        addPlain("History", "clock.fill");
        header("Topics");
        for (int i = 2; i < TOPICS.length; i++) addRow(TOPICS[i], null);
    }

    Topic topicOf(String n) { for (Topic t : TOPICS) if (t.name.equals(n)) return t; return TOPICS[0]; }

    void header(String s) {
        TextView h = Ui.tv(a, s, 11.5f, Theme.sub(), true);
        h.setPadding(a.dp(6), a.dp(12), 0, a.dp(3));
        side.addView(h);
    }

    LinearLayout rowBase(final String name) {
        LinearLayout r = new LinearLayout(a);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setPadding(a.dp(6), a.dp(5), a.dp(6), a.dp(5));
        if (sel.equals(name)) r.setBackground(Ui.rr(RED, a.dp(9)));
        r.setOnClickListener(v -> { sel = name; drawSide(); show(name); });
        return r;
    }

    void addRow(Topic t, Object unused) {
        LinearLayout r = rowBase(t.name);
        boolean on = sel.equals(t.name);
        FrameLayout tile = new FrameLayout(a);
        GradientDrawable g = new GradientDrawable();
        g.setCornerRadius(a.dp(5));
        g.setColor(on ? 0x33FFFFFF : t.color);
        tile.setBackground(g);
        if (t.name.equals("Today")) tile.addView(Sf.img(a, "newspaper.fill", Color.WHITE), new FrameLayout.LayoutParams(a.dp(12), a.dp(12), Gravity.CENTER));
        else {
            TextView l = Ui.tv(a, t.letter, 11, Color.WHITE, true);
            l.setGravity(Gravity.CENTER);
            tile.addView(l, new FrameLayout.LayoutParams(-1, -1));
        }
        r.addView(tile, new LinearLayout.LayoutParams(a.dp(20), a.dp(20)));
        TextView n = Ui.tv(a, t.name, 13, on ? Color.WHITE : Theme.fg(), false);
        n.setPadding(a.dp(9), 0, 0, 0);
        r.addView(n);
        side.addView(r);
    }

    void addPlain(String name, String sym) {
        LinearLayout r = rowBase(name);
        boolean on = sel.equals(name);
        r.addView(Sf.img(a, sym, on ? Color.WHITE : RED), new LinearLayout.LayoutParams(a.dp(16), a.dp(16)) {{ leftMargin = a.dp(2); }});
        TextView n = Ui.tv(a, name, 13, on ? Color.WHITE : Theme.fg(), false);
        n.setPadding(a.dp(11), 0, 0, 0);
        r.addView(n);
        side.addView(r);
    }

    // ---------- data ----------
    void show(final String name) {
        host.removeAllViews();
        if (name.equals("Saved Stories") || name.equals("History")) {
            cache.put(name, loadList(name.equals("Saved Stories") ? "news_saved" : "news_hist"));
            renderFeed();
            return;
        }
        List<Item> c = cache.get(name);
        Long ts = cacheTs.get(name);
        if (c != null && ts != null && System.currentTimeMillis() - ts < 300000) { renderFeed(); return; }
        TextView l = Ui.tv(a, "Loading\u2026", 14, Theme.sub(), false);
        host.addView(l, new FrameLayout.LayoutParams(-2, -2, Gravity.CENTER));
        final Topic t = topicOf(name);
        ex.execute(() -> {
            final List<Item> all = new ArrayList<>();
            List<List<Item>> parts = new ArrayList<>();
            for (int i = 0; i < t.feeds.length; i++) {
                try { parts.add(fetch(t.feeds[i], t.srcNames[Math.min(i, t.srcNames.length - 1)])); } catch (Exception e) { parts.add(new ArrayList<Item>()); }
            }
            int max = 0;
            for (List<Item> p : parts) max = Math.max(max, p.size());
            for (int i = 0; i < max && all.size() < 40; i++) for (List<Item> p : parts) if (i < p.size()) all.add(p.get(i));
            if (parts.size() == 1) Collections.sort(all, new Comparator<Item>() { public int compare(Item x, Item y) { return Long.compare(y.ts, x.ts); } });
            ui.post(() -> {
                if (closed) return;
                cache.put(name, all);
                cacheTs.put(name, System.currentTimeMillis());
                if (sel.equals(name)) renderFeed();
            });
        });
    }

    List<Item> fetch(String url, String src) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setRequestProperty("User-Agent", "Mozilla/5.0 FengNews/1.0");
        c.setConnectTimeout(9000); c.setReadTimeout(9000);
        List<Item> out = new ArrayList<>();
        try (InputStream in = c.getInputStream()) {
            XmlPullParser p = XmlPullParserFactory.newInstance().newPullParser();
            p.setInput(in, null);
            Item cur = null;
            String tag = null;
            int ev = p.getEventType();
            SimpleDateFormat f1 = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss Z", Locale.US), f2 = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz", Locale.US);
            while (ev != XmlPullParser.END_DOCUMENT) {
                if (ev == XmlPullParser.START_TAG) {
                    tag = p.getName();
                    if (tag.equals("item")) { cur = new Item(); cur.src = src; }
                    else if (cur != null) {
                        if ((tag.equals("media:thumbnail") || tag.equals("media:content")) && cur.img == null && p.getAttributeValue(null, "url") != null) cur.img = p.getAttributeValue(null, "url");
                        else if (tag.equals("enclosure") && cur.img == null) {
                            String ty = p.getAttributeValue(null, "type");
                            if (ty == null || ty.startsWith("image")) cur.img = p.getAttributeValue(null, "url");
                        }
                    }
                } else if (ev == XmlPullParser.TEXT && cur != null && tag != null) {
                    String tx = p.getText().trim();
                    if (!tx.isEmpty()) {
                        switch (tag) {
                            case "title": cur.title = (cur.title == null ? "" : cur.title) + tx; break;
                            case "link": cur.link = tx; break;
                            case "description": cur.desc = android.text.Html.fromHtml(tx).toString().trim(); break;
                            case "pubDate":
                                try { cur.ts = f1.parse(tx).getTime(); } catch (Exception e) { try { cur.ts = f2.parse(tx).getTime(); } catch (Exception e2) { cur.ts = System.currentTimeMillis(); } }
                                break;
                            default: break;
                        }
                    }
                } else if (ev == XmlPullParser.END_TAG) {
                    if (p.getName().equals("item") && cur != null) {
                        if (cur.title != null && cur.link != null) {
                            if (cur.img != null && cur.img.contains("ichef.bbci.co.uk")) cur.img = cur.img.replace("/240/", "/800/").replace("/standard/240/", "/standard/800/");
                            out.add(cur);
                        }
                        cur = null;
                    }
                    tag = null;
                }
                ev = p.next();
            }
        }
        return out;
    }

    List<Item> loadList(String key) {
        List<Item> l = new ArrayList<>();
        try {
            JSONArray ar = new JSONArray(a.prefs.getString(key, "[]"));
            for (int i = 0; i < ar.length(); i++) {
                JSONObject o = ar.getJSONObject(i);
                Item it = new Item(); it.title = o.getString("t"); it.link = o.getString("u"); it.src = o.optString("s"); it.img = o.optString("i", null);
                if (it.img != null && it.img.isEmpty()) it.img = null;
                it.ts = o.optLong("ts");
                l.add(it);
            }
        } catch (Exception e) { }
        return l;
    }

    void saveList(String key, List<Item> l) {
        try {
            JSONArray ar = new JSONArray();
            for (Item it : l) ar.put(new JSONObject().put("t", it.title).put("u", it.link).put("s", it.src == null ? "" : it.src).put("i", it.img == null ? "" : it.img).put("ts", it.ts));
            a.prefs.edit().putString(key, ar.toString()).apply();
        } catch (Exception e) { }
    }

    boolean isSaved(Item it) { for (Item s : loadList("news_saved")) if (s.link.equals(it.link)) return true; return false; }

    void toggleSave(Item it) {
        List<Item> l = loadList("news_saved");
        boolean found = false;
        for (int i = l.size() - 1; i >= 0; i--) if (l.get(i).link.equals(it.link)) { l.remove(i); found = true; }
        if (!found) l.add(0, it);
        saveList("news_saved", l);
    }

    void addHistory(Item it) {
        List<Item> l = loadList("news_hist");
        for (int i = l.size() - 1; i >= 0; i--) if (l.get(i).link.equals(it.link)) l.remove(i);
        l.add(0, it);
        while (l.size() > 50) l.remove(l.size() - 1);
        saveList("news_hist", l);
    }

    // ---------- feed page ----------
    static String ago(long ts) {
        long m = (System.currentTimeMillis() - ts) / 60000;
        if (m < 1) return "now";
        if (m < 60) return m + "m ago";
        if (m < 1440) return (m / 60) + "h ago";
        return (m / 1440) + "d ago";
    }

    void renderFeed() {
        host.removeAllViews();
        List<Item> src = cache.get(sel);
        if (src == null) return;
        List<Item> items = new ArrayList<>();
        for (Item it : src) if (query.isEmpty() || it.title.toLowerCase(Locale.ROOT).contains(query)) items.add(it);
        feedScroll = new ScrollView(a);
        feedScroll.setVerticalScrollBarEnabled(false);
        LinearLayout col = new LinearLayout(a);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(a.dp(18), a.dp(10), a.dp(18), a.dp(18));
        LinearLayout hd = new LinearLayout(a);
        hd.setGravity(Gravity.CENTER_VERTICAL);
        hd.addView(Sf.img(a, "apple.logo", Theme.fg()), new LinearLayout.LayoutParams(a.dp(20), a.dp(24)));
        TextView nt = Ui.tv(a, "News", 26, Theme.fg(), true);
        nt.setTypeface(Emoji.font(true));
        nt.setPadding(a.dp(3), 0, 0, 0);
        hd.addView(nt);
        col.addView(hd);
        String sub = sel.equals("Today") ? new SimpleDateFormat("MMMM d", Locale.getDefault()).format(new Date()) : sel;
        TextView d = Ui.tv(a, sub, 26, 0xFF8E8E93, true);
        col.addView(d);
        if (items.isEmpty()) {
            String m = sel.equals("Saved Stories") ? "No saved stories yet" : sel.equals("History") ? "No history yet" : cache.get(sel).isEmpty() ? "Can\u2019t load news. Check your connection." : "No results";
            TextView e = Ui.tv(a, m, 14, Theme.sub(), false);
            e.setPadding(0, a.dp(24), 0, 0);
            col.addView(e);
        } else {
            TextView ts = Ui.tv(a, sel.equals("Saved Stories") || sel.equals("History") ? sel : "Top Stories", 20, RED, true);
            ts.setPadding(0, a.dp(14), 0, a.dp(8));
            col.addView(ts);
            int i = 0;
            while (i < items.size()) {
                LinearLayout row = new LinearLayout(a);
                int n = Math.min(2, items.size() - i);
                for (int k = 0; k < 2; k++) {
                    if (k < n) {
                        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1f);
                        lp.setMargins(k == 0 ? 0 : a.dp(6), 0, k == 0 ? a.dp(6) : 0, a.dp(12));
                        row.addView(card(items.get(i + k), i == 0), lp);
                    } else row.addView(new View(a), new LinearLayout.LayoutParams(0, 1, 1f));
                }
                col.addView(row);
                i += 2;
            }
        }
        feedScroll.addView(col);
        host.addView(feedScroll, new FrameLayout.LayoutParams(-1, -1));
    }

    View card(final Item it, boolean big) {
        LinearLayout c = new LinearLayout(a);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setBackground(Ui.rr(Theme.card(), a.dp(14)));
        c.setClipToOutline(true);
        c.setOutlineProvider(new android.view.ViewOutlineProvider() {
            @Override public void getOutline(View v, android.graphics.Outline o) { o.setRoundRect(0, 0, v.getWidth(), v.getHeight(), a.dp(14)); }
        });
        c.setElevation(a.dp(1.5f));
        if (it.img != null) {
            ImageView iv = new ImageView(a);
            iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
            iv.setBackgroundColor(Theme.dark ? 0xFF2A2A2D : 0xFFE5E5EA);
            load(it.img, iv);
            c.addView(iv, new LinearLayout.LayoutParams(-1, a.dp(big ? 150 : 110)));
        }
        LinearLayout tx = new LinearLayout(a);
        tx.setOrientation(LinearLayout.VERTICAL);
        tx.setPadding(a.dp(12), a.dp(10), a.dp(12), a.dp(8));
        TextView s = Ui.tv(a, it.src == null ? "" : it.src, 10.5f, Theme.dark ? 0xFFE0E0E5 : 0xFF1D1D1F, true);
        s.setTypeface(Emoji.font(true));
        tx.addView(s);
        TextView h = Ui.tv(a, it.title, 14, Theme.fg(), true);
        h.setMaxLines(4);
        h.setEllipsize(android.text.TextUtils.TruncateAt.END);
        h.setPadding(0, a.dp(3), 0, a.dp(8));
        tx.addView(h);
        LinearLayout f = new LinearLayout(a);
        f.setGravity(Gravity.CENTER_VERTICAL);
        f.addView(Ui.tv(a, ago(it.ts), 10.5f, Theme.sub(), false), new LinearLayout.LayoutParams(0, -2, 1f));
        TextView more = Ui.tv(a, "\u2026", 14, Theme.sub(), true);
        f.addView(more);
        tx.addView(f);
        c.addView(tx);
        c.setOnClickListener(v -> openArticle(it));
        return c;
    }

    void load(final String url, final ImageView iv) {
        iv.setTag(url);
        Bitmap b = imgs.get(url);
        if (b != null) { iv.setImageBitmap(b); return; }
        ex.execute(() -> {
            try {
                HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
                c.setRequestProperty("User-Agent", "Mozilla/5.0 FengNews/1.0");
                c.setConnectTimeout(8000); c.setReadTimeout(8000);
                byte[] data;
                try (InputStream in = c.getInputStream()) {
                    java.io.ByteArrayOutputStream bo = new java.io.ByteArrayOutputStream();
                    byte[] buf = new byte[16384]; int n;
                    while ((n = in.read(buf)) > 0) bo.write(buf, 0, n);
                    data = bo.toByteArray();
                }
                BitmapFactory.Options o = new BitmapFactory.Options();
                o.inJustDecodeBounds = true;
                BitmapFactory.decodeByteArray(data, 0, data.length, o);
                int sample = 1;
                while (o.outWidth / (sample * 2) >= 700) sample *= 2;
                o = new BitmapFactory.Options();
                o.inSampleSize = sample;
                final Bitmap bm = BitmapFactory.decodeByteArray(data, 0, data.length, o);
                if (bm == null) return;
                imgs.put(url, bm);
                ui.post(() -> { if (url.equals(iv.getTag())) iv.setImageBitmap(bm); });
            } catch (Exception e) { }
        });
    }

    // ---------- article ----------
    @SuppressLint("SetJavaScriptEnabled")
    void openArticle(final Item it) {
        addHistory(it);
        host.removeAllViews();
        LinearLayout col = new LinearLayout(a);
        col.setOrientation(LinearLayout.VERTICAL);
        LinearLayout top = new LinearLayout(a);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setPadding(a.dp(10), a.dp(8), a.dp(10), a.dp(8));
        top.setBackgroundColor(Theme.dark ? 0xFF1E1E20 : 0xFFFFFFFF);
        TextView back = Ui.circleBtn(a, "\u2039");
        back.setOnClickListener(v -> { if (cache.get(sel) != null) renderFeed(); else show(sel); });
        top.addView(back, new LinearLayout.LayoutParams(a.dp(30), a.dp(30)));
        TextView t = Ui.tv(a, it.src == null ? "News" : it.src, 14, Theme.fg(), true);
        t.setGravity(Gravity.CENTER);
        top.addView(t, new LinearLayout.LayoutParams(0, -2, 1f));
        final TextView save = Ui.sfBtn(a, isSaved(it) ? "bookmark.fill" : "bookmark", 13);
        save.setOnClickListener(v -> {
            toggleSave(it);
            ((Ui.SfText) save).setSymbol(Sf.sized(a, isSaved(it) ? "bookmark.fill" : "bookmark", Theme.fg(), a.dp(13)));
        });
        top.addView(save, new LinearLayout.LayoutParams(a.dp(30), a.dp(30)));
        TextView share = Ui.sfBtn(a, "square.and.arrow.up", 13);
        share.setOnClickListener(v -> {
            android.content.Intent i = new android.content.Intent(android.content.Intent.ACTION_SEND);
            i.setType("text/plain");
            i.putExtra(android.content.Intent.EXTRA_TEXT, it.title + "\n" + it.link);
            a.startExternal(android.content.Intent.createChooser(i, "Share"));
        });
        LinearLayout.LayoutParams shl = new LinearLayout.LayoutParams(a.dp(30), a.dp(30));
        shl.leftMargin = a.dp(6);
        top.addView(share, shl);
        col.addView(top, new LinearLayout.LayoutParams(-1, -2));
        col.addView(Ui.sepLine(a), new LinearLayout.LayoutParams(-1, 1));
        WebView w = new WebView(a);
        WebSettings s = w.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        w.setWebViewClient(new WebViewClient());
        w.loadUrl(it.link);
        col.addView(w, new LinearLayout.LayoutParams(-1, 0, 1f));
        host.addView(col, new FrameLayout.LayoutParams(-1, -1));
    }
}
