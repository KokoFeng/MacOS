package com.macos.launcher;

import android.app.DownloadManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
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
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import java.net.URLEncoder;

public class SafariApp {
    final MainActivity a;
    WebView web;
    EditText addr;
    View bar;

    public static void open(MainActivity a, String url) { new SafariApp(a, url); }

    SafariApp(MainActivity act, String url) {
        a = act;
        final int M = MainActivity.MATCH, W = MainActivity.WRAP;
        LinearLayout tb = new LinearLayout(a);
        tb.setGravity(Gravity.CENTER_VERTICAL);
        TextView back = Ui.circleBtn(a, "‹"), fwd = Ui.circleBtn(a, "›"), reload = Ui.circleBtn(a, "⟳"), plus = Ui.circleBtn(a, "+");
        tb.addView(back, new LinearLayout.LayoutParams(a.dp(30), a.dp(30)));
        LinearLayout.LayoutParams fl = new LinearLayout.LayoutParams(a.dp(30), a.dp(30));
        fl.leftMargin = a.dp(6);
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
        LinearLayout.LayoutParams al = new LinearLayout.LayoutParams(0, a.dp(32), 1f);
        al.setMargins(a.dp(10), 0, a.dp(10), 0);
        tb.addView(addr, al);
        tb.addView(reload, new LinearLayout.LayoutParams(a.dp(30), a.dp(30)));
        LinearLayout.LayoutParams pl = new LinearLayout.LayoutParams(a.dp(30), a.dp(30));
        pl.leftMargin = a.dp(6);
        tb.addView(plus, pl);

        FrameLayout body = new FrameLayout(a);
        web = new WebView(a);
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setLoadWithOverviewMode(true);
        s.setUseWideViewPort(true);
        s.setSupportZoom(true);
        s.setBuiltInZoomControls(true);
        s.setDisplayZoomControls(false);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE);
        bar = new View(a);
        bar.setBackgroundColor(Theme.accent);
        web.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView v, String u) {
                if (u.startsWith("http://") || u.startsWith("https://")) return false;
                try { a.startExternal(Intent.parseUri(u, Intent.URI_INTENT_SCHEME)); } catch (Exception e) { }
                return true;
            }
            @Override public void onPageStarted(WebView v, String u, Bitmap f) { if (!addr.hasFocus()) addr.setText(u); }
        });
        web.setWebChromeClient(new WebChromeClient() {
            @Override public void onProgressChanged(WebView v, int p) {
                FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) bar.getLayoutParams();
                lp.width = p >= 100 ? 0 : (int) (v.getWidth() * p / 100f);
                bar.setLayoutParams(lp);
            }
        });
        web.setDownloadListener((u, ua, cd, mime, len) -> {
            try {
                DownloadManager.Request r = new DownloadManager.Request(Uri.parse(u));
                r.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
                r.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, URLUtil.guessFileName(u, cd, mime));
                ((DownloadManager) a.getSystemService(Context.DOWNLOAD_SERVICE)).enqueue(r);
                Toast.makeText(a, "Mengunduh…", Toast.LENGTH_SHORT).show();
            } catch (Exception e) { Toast.makeText(a, "Gagal mengunduh", Toast.LENGTH_SHORT).show(); }
        });
        body.addView(web, new FrameLayout.LayoutParams(M, M));
        body.addView(bar, new FrameLayout.LayoutParams(0, a.dp(2), Gravity.TOP | Gravity.LEFT));

        back.setOnClickListener(v -> { if (web.canGoBack()) web.goBack(); });
        fwd.setOnClickListener(v -> { if (web.canGoForward()) web.goForward(); });
        reload.setOnClickListener(v -> web.reload());
        plus.setOnClickListener(v -> SafariApp.open(a, null));
        addr.setOnEditorActionListener((v, id, ev) -> {
            if (id == EditorInfo.IME_ACTION_GO || (ev != null && ev.getKeyCode() == KeyEvent.KEYCODE_ENTER)) {
                load(addr.getText().toString());
                addr.clearFocus();
                web.requestFocus();
                return true;
            }
            return false;
        });
        MacWindow w = new MacWindow(a, "Safari", tb, null, body);
        w.onClose = () -> { web.stopLoading(); web.destroy(); };
        a.openWindow(w, 640, 320);
        load(url == null ? "https://www.google.com" : url);
    }

    void load(String in) {
        String t = in.trim();
        if (t.isEmpty()) return;
        String u;
        if (t.startsWith("http://") || t.startsWith("https://")) u = t;
        else if (t.contains(".") && !t.contains(" ")) u = "https://" + t;
        else {
            try { u = "https://www.google.com/search?q=" + URLEncoder.encode(t, "UTF-8"); } catch (Exception e) { return; }
        }
        web.loadUrl(u);
    }
}
