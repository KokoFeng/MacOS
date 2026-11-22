package com.macos.launcher;

import android.app.AlertDialog;
import android.app.DownloadManager;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.net.Uri;
import android.os.Environment;
import android.text.InputType;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.webkit.URLUtil;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;

/** Safari-style browser: sidebar (tabs + bookmarks), Start Page with Favourites, share menu, Add to Dock. */
public class SafariApp {
    static class Tab { WebView web; String title = "Start Page", url = ""; }
    static final String[][] FAV = {{"Google", "https://www.google.com"}, {"YouTube", "https://www.youtube.com"},
        {"Wikipedia", "https://en.wikipedia.org"}, {"GitHub", "https://github.com"}, {"Facebook", "https://www.facebook.com"},
        {"Instagram", "https://www.instagram.com"}, {"X", "https://x.com"}, {"Reddit", "https://www.reddit.com"}};

    final MainActivity a;
    final List<Tab> tabs = new ArrayList<>();
    int cur = 0;
    final boolean webapp;
    final String appName;
    MacWindow win;
    EditText addr;
    TextView webTitle;
    FrameLayout main;
    ScrollView start;
    LinearLayout sideList;
    View sidebar, bar;
    boolean sideOn = true;

    public static void open(MainActivity a, String url) { new SafariApp(a, url, null); }
    public static void openWebApp(MainActivity a, String name, String url) { new SafariApp(a, url, name); }

    SafariApp(MainActivity act, String url, String name) {
        a = act;
        webapp = name != null;
        appName = webapp ? name : "Safari";
        final int M = MainActivity.MATCH, W = MainActivity.WRAP;
        LinearLayout tb = new LinearLayout(a);
        tb.setGravity(Gravity.CENTER_VERTICAL);
        TextView back = Ui.circleBtn(a, "‹"), fwd = Ui.circleBtn(a, "›");
        back.setOnClickListener(v -> { Tab t = tab(); if (t.web != null && t.web.canGoBack()) t.web.goBack(); else if (!webapp && t.web != null) { t.web.stopLoading(); } });
        fwd.setOnClickListener(v -> { Tab t = tab(); if (t.web != null && t.web.canGoForward()) t.web.goForward(); });
        main = new FrameLayout(a);
        bar = new View(a);
        bar.setBackgroundColor(Theme.accent);
        LinearLayout body = new LinearLayout(a);
        body.setBackgroundColor(Theme.pane());

        if (webapp) {
            tb.addView(back, new LinearLayout.LayoutParams(a.dp(30), a.dp(30)));
            webTitle = Ui.tv(a, name, 14, Theme.fg(), true);
            webTitle.setGravity(Gravity.CENTER);
            tb.addView(webTitle, new LinearLayout.LayoutParams(0, W, 1f));
            TextView rl = Ui.circleBtn(a, "⟳");
            rl.setOnClickListener(v -> { Tab t = tab(); if (t.web != null) t.web.reload(); });
            tb.addView(rl, new LinearLayout.LayoutParams(a.dp(30), a.dp(30)));
            body.addView(main, new LinearLayout.LayoutParams(M, M));
        } else {
            View sb = Ui.iconBtn(a, IconView.STAGE);
            sb.setOnClickListener(v -> { sideOn = !sideOn; sidebar.setVisibility(sideOn ? View.VISIBLE : View.GONE); });
            tb.addView(sb, new LinearLayout.LayoutParams(a.dp(30), a.dp(30)));
            LinearLayout.LayoutParams bl = new LinearLayout.LayoutParams(a.dp(30), a.dp(30));
            bl.leftMargin = a.dp(8);
            tb.addView(back, bl);
            LinearLayout.LayoutParams fl = new LinearLayout.LayoutParams(a.dp(30), a.dp(30));
            fl.leftMargin = a.dp(4);
            tb.addView(fwd, fl);
            addr = new EditText(a);
            addr.setSingleLine(true);
            addr.setTextSize(13);
            addr.setTextColor(Theme.fg());
            addr.setHintTextColor(Theme.sub());
            addr.setHint("Search or enter website name");
            addr.setGravity(Gravity.CENTER_VERTICAL);
            addr.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
            addr.setImeOptions(EditorInfo.IME_ACTION_GO);
            addr.setPadding(a.dp(14), 0, a.dp(14), 0);
            addr.setBackground(Ui.rr(Theme.dark ? 0x2EFFFFFF : 0x12000000, a.dp(16)));
            addr.setSelectAllOnFocus(true);
            addr.setOnEditorActionListener((v, id, ev) -> {
                if (id == EditorInfo.IME_ACTION_GO || (ev != null && ev.getKeyCode() == KeyEvent.KEYCODE_ENTER)) {
                    go(addr.getText().toString());
                    addr.clearFocus();
                    return true;
                }
                return false;
            });
            LinearLayout.LayoutParams al = new LinearLayout.LayoutParams(0, a.dp(32), 1f);
            al.setMargins(a.dp(10), 0, a.dp(10), 0);
            tb.addView(addr, al);
            TextView share = Ui.circleBtn(a, "↑"), plus = Ui.circleBtn(a, "+");
            share.setOnClickListener(v -> showShare(v));
            plus.setOnClickListener(v -> newTab(null));
            tb.addView(share, new LinearLayout.LayoutParams(a.dp(30), a.dp(30)));
            LinearLayout.LayoutParams pl = new LinearLayout.LayoutParams(a.dp(30), a.dp(30));
            pl.leftMargin = a.dp(6);
            tb.addView(plus, pl);

            sideList = new LinearLayout(a);
            sideList.setOrientation(LinearLayout.VERTICAL);
            sideList.setPadding(a.dp(10), a.dp(4), a.dp(10), a.dp(10));
            ScrollView sv = new ScrollView(a);
            sv.setBackgroundColor(Theme.side());
            sv.setVerticalScrollBarEnabled(false);
            sv.addView(sideList);
            sidebar = sv;
            body.addView(sv, new LinearLayout.LayoutParams(a.dp(150), M));
            body.addView(main, new LinearLayout.LayoutParams(0, M, 1f));
        }
        win = new MacWindow(a, appName, tb, null, body);
        win.setTag(this);
        win.onClose = () -> { for (Tab t : tabs) if (t.web != null) { t.web.stopLoading(); t.web.destroy(); } };
        a.openWindow(win, webapp ? 560 : 700, 330);
        tabs.add(new Tab());
        select(0);
        if (url != null) go(url);
    }

    Tab tab() { return tabs.get(cur); }

    void newTab(String url) {
        tabs.add(new Tab());
        select(tabs.size() - 1);
        if (url != null) go(url);
    }

    void select(int i) {
        cur = i;
        main.removeAllViews();
        Tab t = tab();
        if (t.web == null) { buildStart(); main.addView(start, new FrameLayout.LayoutParams(MainActivity.MATCH, MainActivity.MATCH)); }
        else main.addView(t.web, new FrameLayout.LayoutParams(MainActivity.MATCH, MainActivity.MATCH));
        main.addView(bar, new FrameLayout.LayoutParams(0, a.dp(2), Gravity.TOP | Gravity.LEFT));
        if (addr != null && !addr.hasFocus()) addr.setText(t.url);
        refreshSide();
    }

    String resolve(String in) {
        String t = in.trim();
        if (t.startsWith("http://") || t.startsWith("https://")) return t;
        if (t.contains(".") && !t.contains(" ")) return "https://" + t;
        try { return "https://www.google.com/search?q=" + URLEncoder.encode(t, "UTF-8"); } catch (Exception e) { return "https://www.google.com"; }
    }

    void go(String in) {
        if (in == null || in.trim().isEmpty()) return;
        Tab t = tab();
        String u = resolve(in);
        if (t.web == null) { t.web = newWeb(t); select(cur); }
        t.url = u;
        if (addr != null && !addr.hasFocus()) addr.setText(u);
        t.web.loadUrl(u);
    }

    WebView newWeb(final Tab t) {
        WebView w = new WebView(a);
        WebSettings s = w.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setLoadWithOverviewMode(true);
        s.setUseWideViewPort(true);
        s.setSupportZoom(true);
        s.setBuiltInZoomControls(true);
        s.setDisplayZoomControls(false);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE);
        w.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView v, String u) {
                if (u.startsWith("http://") || u.startsWith("https://")) return false;
                try { a.startExternal(Intent.parseUri(u, Intent.URI_INTENT_SCHEME)); } catch (Exception e) { }
                return true;
            }
            @Override public void onPageStarted(WebView v, String u, Bitmap f) {
                t.url = u;
                if (addr != null && tab() == t && !addr.hasFocus()) addr.setText(u);
            }
        });
        w.setWebChromeClient(new WebChromeClient() {
            @Override public void onProgressChanged(WebView v, int p) {
                if (tab() != t) return;
                FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) bar.getLayoutParams();
                lp.width = p >= 100 ? 0 : (int) (v.getWidth() * p / 100f);
                bar.setLayoutParams(lp);
            }
            @Override public void onReceivedTitle(WebView v, String title) {
                t.title = title;
                if (!webapp) refreshSide();
            }
        });
        w.setDownloadListener((u, ua, cd, mime, len) -> {
            try {
                DownloadManager.Request r = new DownloadManager.Request(Uri.parse(u));
                r.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
                r.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, URLUtil.guessFileName(u, cd, mime));
                ((DownloadManager) a.getSystemService(Context.DOWNLOAD_SERVICE)).enqueue(r);
                Toast.makeText(a, "Mengunduh…", Toast.LENGTH_SHORT).show();
            } catch (Exception e) { Toast.makeText(a, "Gagal mengunduh", Toast.LENGTH_SHORT).show(); }
        });
        return w;
    }

    // ---------- Start Page ----------
    List<String[]> favourites() {
        List<String[]> l = new ArrayList<>();
        for (String[] f : FAV) l.add(f);
        l.addAll(bookmarks());
        return l;
    }

    void buildStart() {
        start = new ScrollView(a);
        start.setBackgroundColor(Theme.pane());
        LinearLayout c = new LinearLayout(a);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setPadding(a.dp(24), a.dp(18), a.dp(24), a.dp(18));
        c.addView(Ui.tv(a, "Favourites", 22, Theme.fg(), true));
        final List<String[]> fav = favourites();
        GridView g = new GridView(a);
        g.setNumColumns(GridView.AUTO_FIT);
        g.setColumnWidth(a.dp(84));
        g.setStretchMode(GridView.STRETCH_COLUMN_WIDTH);
        g.setVerticalSpacing(a.dp(12));
        g.setEnabled(false);
        g.setAdapter(new android.widget.BaseAdapter() {
            @Override public int getCount() { return fav.size(); }
            @Override public Object getItem(int i) { return fav.get(i); }
            @Override public long getItemId(int i) { return i; }
            @Override public View getView(int i, View cv, android.view.ViewGroup p) {
                final String[] f = fav.get(i);
                LinearLayout t = new LinearLayout(a);
                t.setOrientation(LinearLayout.VERTICAL);
                t.setGravity(Gravity.CENTER_HORIZONTAL);
                ImageView iv = new ImageView(a);
                iv.setImageDrawable(new BitmapDrawable(a.getResources(), Store.faviconTile(a, f[1], f[0])));
                t.addView(iv, new LinearLayout.LayoutParams(a.dp(56), a.dp(56)));
                TextView l = Ui.tv(a, f[0], 11, Theme.fg(), false);
                l.setGravity(Gravity.CENTER);
                l.setSingleLine(true);
                LinearLayout.LayoutParams ll = new LinearLayout.LayoutParams(MainActivity.MATCH, MainActivity.WRAP);
                ll.topMargin = a.dp(4);
                t.addView(l, ll);
                t.setOnClickListener(v -> go(f[1]));
                Store.fetchFavicon(a, f[1], () -> { if (tab().web == null && start != null) { select(cur); } });
                return t;
            }
        });
        int rows = (fav.size() + 2) / 3;
        LinearLayout.LayoutParams gl = new LinearLayout.LayoutParams(MainActivity.MATCH, rows * a.dp(92));
        gl.topMargin = a.dp(16);
        c.addView(g, gl);
        start.addView(c);
    }

    // ---------- bookmarks ----------
    List<String[]> bookmarks() {
        List<String[]> l = new ArrayList<>();
        for (String line : a.prefs.getString("bookmarks", "").split("\n")) {
            String[] f = line.split("\t", -1);
            if (f.length == 2 && !f[1].isEmpty()) l.add(f);
        }
        return l;
    }

    void saveBookmarks(List<String[]> l) {
        StringBuilder sb = new StringBuilder();
        for (String[] f : l) sb.append(f[0].replace('\t', ' ').replace('\n', ' ')).append('\t').append(f[1]).append('\n');
        a.prefs.edit().putString("bookmarks", sb.toString()).apply();
    }

    void addBookmark() {
        Tab t = tab();
        if (t.url.isEmpty()) return;
        List<String[]> l = bookmarks();
        for (String[] f : l) if (f[1].equals(t.url)) return;
        l.add(new String[]{t.title.isEmpty() ? Store.host(t.url) : t.title, t.url});
        saveBookmarks(l);
        Toast.makeText(a, "Bookmark ditambahkan", Toast.LENGTH_SHORT).show();
        refreshSide();
    }

    // ---------- sidebar ----------
    void refreshSide() {
        if (sideList == null) return;
        sideList.removeAllViews();
        row("Start Page", tab().web == null, () -> { Tab t = tab(); if (t.web != null) { t.web.stopLoading(); t.web.destroy(); t.web = null; t.url = ""; t.title = "Start Page"; } select(cur); }, null);
        head("Tabs");
        for (int i = 0; i < tabs.size(); i++) {
            final int k = i;
            Tab t = tabs.get(i);
            row(t.web == null ? "Start Page" : (t.title == null || t.title.isEmpty() ? Store.host(t.url) : t.title), i == cur, () -> select(k), () -> closeTab(k));
        }
        row("+ New Tab", false, () -> newTab(null), null);
        head("Bookmarks");
        for (final String[] f : bookmarks()) row(f[0], false, () -> go(f[1]), () -> {
            List<String[]> l = bookmarks();
            for (int i = 0; i < l.size(); i++) if (l.get(i)[1].equals(f[1])) { l.remove(i); break; }
            saveBookmarks(l);
            refreshSide();
        });
    }

    void head(String s) {
        TextView t = Ui.tv(a, s, 10.5f, Theme.sub(), true);
        t.setPadding(a.dp(6), a.dp(12), 0, a.dp(3));
        sideList.addView(t);
    }

    void row(String label, boolean sel, final Runnable click, final Runnable remove) {
        LinearLayout r = new LinearLayout(a);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setPadding(a.dp(8), a.dp(6), a.dp(4), a.dp(6));
        if (sel) r.setBackground(Ui.rr(Theme.accent, a.dp(8)));
        TextView t = Ui.tv(a, label, 12.5f, sel ? 0xFFFFFFFF : Theme.fg(), false);
        t.setSingleLine(true);
        t.setEllipsize(android.text.TextUtils.TruncateAt.END);
        r.addView(t, new LinearLayout.LayoutParams(0, MainActivity.WRAP, 1f));
        if (remove != null) {
            TextView x = Ui.tv(a, "×", 15, sel ? 0xFFFFFFFF : Theme.sub(), true);
            x.setPadding(a.dp(8), 0, a.dp(6), 0);
            x.setOnClickListener(v -> remove.run());
            r.addView(x);
        }
        r.setOnClickListener(v -> click.run());
        sideList.addView(r);
    }

    void closeTab(int i) {
        Tab t = tabs.get(i);
        if (t.web != null) { t.web.stopLoading(); t.web.destroy(); }
        tabs.remove(i);
        if (tabs.isEmpty()) { win.close(); return; }
        select(Math.min(i, tabs.size() - 1));
    }

    // ---------- share / add to dock ----------
    void showShare(View anchor) {
        a.showPopup(anchor, new String[]{"Add Bookmark", "Add to Dock…", "Open in Default Browser", "Copy Link"}, new Runnable[]{
            this::addBookmark, this::addToDock,
            () -> { if (!tab().url.isEmpty()) a.startExternal(new Intent(Intent.ACTION_VIEW, Uri.parse(tab().url))); },
            () -> {
                if (tab().url.isEmpty()) return;
                ((ClipboardManager) a.getSystemService(Context.CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText("url", tab().url));
                Toast.makeText(a, "Link disalin", Toast.LENGTH_SHORT).show();
            }});
    }

    void addToDock() {
        final Tab t = tab();
        if (t.web == null || t.url.isEmpty()) { Toast.makeText(a, "Buka sebuah website dulu", Toast.LENGTH_SHORT).show(); return; }
        final String host = Store.host(t.url);
        Store.fetchFavicon(a, t.url, null);
        LinearLayout v = new LinearLayout(a);
        v.setOrientation(LinearLayout.VERTICAL);
        v.setPadding(a.dp(22), a.dp(14), a.dp(22), a.dp(4));
        v.setGravity(Gravity.CENTER_HORIZONTAL);
        ImageView ic = new ImageView(a);
        ic.setImageDrawable(new BitmapDrawable(a.getResources(), Store.faviconTile(a, t.url, host)));
        v.addView(ic, new LinearLayout.LayoutParams(a.dp(64), a.dp(64)));
        final EditText name = new EditText(a);
        name.setSingleLine(true);
        name.setText(t.title == null || t.title.isEmpty() ? host : t.title);
        name.setSelectAllOnFocus(true);
        v.addView(name, new LinearLayout.LayoutParams(MainActivity.MATCH, MainActivity.WRAP));
        TextView u = Ui.tv(a, t.url, 11, Theme.sub(), false);
        u.setSingleLine(true);
        v.addView(u);
        final CheckBox cd = new CheckBox(a), ch = new CheckBox(a);
        cd.setText("Add to Dock"); cd.setChecked(true);
        ch.setText("Add to Home (Desktop)"); ch.setChecked(true);
        v.addView(cd);
        v.addView(ch);
        new AlertDialog.Builder(a).setTitle("Add to Dock").setView(v)
            .setPositiveButton("Done", (d, w) -> {
                String n = name.getText().toString().trim();
                a.addWebApp(n.isEmpty() ? host : n, t.url, cd.isChecked(), ch.isChecked());
            })
            .setNegativeButton("Cancel", null).show();
    }

    void menuAction(String it) {
        switch (it) {
            case "New Tab": newTab(null); break;
            case "Close Tab": closeTab(cur); break;
            case "Add to Dock…": addToDock(); break;
            case "Open Location…": if (addr != null) { addr.requestFocus(); } break;
        }
    }
}
