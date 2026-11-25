package com.macos.launcher;

import android.annotation.SuppressLint;
import android.graphics.Color;
import android.graphics.Typeface;
import android.text.Editable;
import android.text.InputType;
import android.text.Spannable;
import android.text.TextWatcher;
import android.text.style.ForegroundColorSpan;
import android.view.Gravity;
import android.view.View;
import android.webkit.ConsoleMessage;
import android.webkit.WebChromeClient;
import android.webkit.WebView;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileInputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Code: file list, syntax-highlighted editor, and a live preview/run panel for HTML, CSS and JavaScript. */
public class CodeApp {
    static final Pattern KW = Pattern.compile("\\b(function|var|let|const|return|if|else|for|while|class|new|this|import|from|export|def|print|in|is|not|and|or|None|True|False|true|false|null|undefined|public|private|static|void|int|string|async|await|try|catch|throw)\\b");
    static final Pattern STR = Pattern.compile("(\"[^\"\\n]*\"|'[^'\\n]*'|`[^`]*`)");
    static final Pattern NUM = Pattern.compile("\\b\\d+(\\.\\d+)?\\b");
    static final Pattern CMT = Pattern.compile("(//[^\\n]*|#[^\\n]*|/\\*[\\s\\S]*?\\*/|<!--[\\s\\S]*?-->)");
    static final Pattern TAG = Pattern.compile("</?[a-zA-Z][a-zA-Z0-9-]*|/?>");

    final MainActivity a;
    final File dir;
    String cur;
    EditText ed;
    LinearLayout list;
    FrameLayout previewBox;
    WebView web;
    TextView console;
    boolean binding;
    final Runnable hl = this::highlight;

    public static void open(MainActivity a) { new CodeApp(a); }

    CodeApp(MainActivity act) {
        a = act;
        dir = new File(a.getFilesDir(), "code");
        dir.mkdirs();
        if (names().isEmpty()) {
            write("index.html", "<!DOCTYPE html>\n<html>\n<head>\n  <style>\n    body { font-family: sans-serif; text-align: center; padding-top: 40px; }\n    h1 { color: #0a84ff; }\n  </style>\n</head>\n<body>\n  <h1>Hello, Feng Code</h1>\n  <button onclick=\"document.body.style.background='#ffe'\">Click me</button>\n</body>\n</html>\n");
            write("script.js", "// Press Run to execute this file\nfunction fib(n) { return n < 2 ? n : fib(n - 1) + fib(n - 2); }\nfor (let i = 0; i < 10; i++) console.log('fib(' + i + ') = ' + fib(i));\n");
        }
        TextView run = Ui.tv(a, "\u25B6 Run", 13, Color.WHITE, true);
        run.setGravity(Gravity.CENTER);
        run.setBackground(Ui.rr(0xFF30D158, a.dp(15)));
        run.setOnClickListener(v -> run());
        TextView add = Ui.sfBtn(a, "plus", 13);
        add.setOnClickListener(v -> a.prompt("New file name", "untitled.html", s -> { String n = s.trim().replaceAll("[^A-Za-z0-9._-]", "_"); if (!n.isEmpty()) { write(n, ""); open(n); } }));
        TextView del = Ui.sfBtn(a, "trash", 13);
        del.setOnClickListener(v -> { if (cur != null) a.confirm("Delete " + cur + "?", () -> { new File(dir, cur).delete(); cur = null; List<String> n = names(); if (n.isEmpty()) write("untitled.html", ""); open(names().get(0)); }); });
        LinearLayout tb = new LinearLayout(a);
        tb.setGravity(Gravity.CENTER_VERTICAL);
        tb.addView(new View(a), new LinearLayout.LayoutParams(0, 1, 1f));
        tb.addView(add, new LinearLayout.LayoutParams(a.dp(30), a.dp(30)));
        LinearLayout.LayoutParams dl = new LinearLayout.LayoutParams(a.dp(30), a.dp(30));
        dl.leftMargin = a.dp(6);
        tb.addView(del, dl);
        LinearLayout.LayoutParams rl = new LinearLayout.LayoutParams(a.dp(70), a.dp(30));
        rl.leftMargin = a.dp(8);
        tb.addView(run, rl);

        LinearLayout body = new LinearLayout(a);
        LinearLayout left = new LinearLayout(a);
        left.setOrientation(LinearLayout.VERTICAL);
        left.setBackgroundColor(0xFF21232A);
        list = new LinearLayout(a);
        list.setOrientation(LinearLayout.VERTICAL);
        ScrollView sv = new ScrollView(a);
        sv.setVerticalScrollBarEnabled(false);
        sv.addView(list);
        left.addView(sv, new LinearLayout.LayoutParams(-1, -1));
        body.addView(left, new LinearLayout.LayoutParams(a.dp(150), -1));

        ed = new EditText(a);
        ed.setBackgroundColor(0xFF1B1D23);
        ed.setTextColor(0xFFE6E6E6);
        ed.setTypeface(Typeface.MONOSPACE);
        ed.setTextSize(12.5f);
        ed.setGravity(Gravity.TOP | Gravity.START);
        ed.setHorizontallyScrolling(true);
        ed.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
        ed.setPadding(a.dp(12), a.dp(10), a.dp(12), a.dp(10));
        ed.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int x, int y, int z) {}
            public void onTextChanged(CharSequence s, int x, int y, int z) {}
            public void afterTextChanged(Editable e) {
                if (binding || cur == null) return;
                write(cur, e.toString());
                a.h.removeCallbacks(hl);
                a.h.postDelayed(hl, 250);
            }
        });
        LinearLayout mid = new LinearLayout(a);
        mid.setOrientation(LinearLayout.VERTICAL);
        mid.addView(ed, new LinearLayout.LayoutParams(-1, 0, 1f));
        console = Ui.tv(a, "", 11.5f, 0xFFB8F5C8, false);
        console.setTypeface(Typeface.MONOSPACE);
        console.setBackgroundColor(0xFF111317);
        console.setPadding(a.dp(10), a.dp(6), a.dp(10), a.dp(6));
        console.setMaxLines(6);
        console.setVisibility(View.GONE);
        mid.addView(console, new LinearLayout.LayoutParams(-1, -2));
        body.addView(mid, new LinearLayout.LayoutParams(0, -1, 1.2f));
        previewBox = new FrameLayout(a);
        previewBox.setBackgroundColor(0xFFFFFFFF);
        previewBox.setVisibility(View.GONE);
        body.addView(previewBox, new LinearLayout.LayoutParams(0, -1, 1f));
        MacWindow w = new MacWindow(a, "Feng Code", tb, null, body);
        w.onClose = () -> { if (web != null) { web.destroy(); web = null; } };
        a.openWindow(w, 760, 360);
        drawList();
        open(names().get(0));
    }

    List<String> names() {
        List<String> l = new ArrayList<>();
        String[] f = dir.list();
        if (f != null) { Arrays.sort(f); l.addAll(Arrays.asList(f)); }
        return l;
    }

    String read(String n) {
        try (FileInputStream in = new FileInputStream(new File(dir, n))) {
            byte[] b = new byte[(int) new File(dir, n).length()];
            int r = 0;
            while (r < b.length) { int k = in.read(b, r, b.length - r); if (k < 0) break; r += k; }
            return new String(b, 0, r, "UTF-8");
        } catch (Exception e) { return ""; }
    }

    void write(String n, String t) {
        try (FileOutputStream o = new FileOutputStream(new File(dir, n))) { o.write(t.getBytes("UTF-8")); } catch (Exception e) { }
    }

    void open(String n) {
        cur = n;
        binding = true;
        ed.setText(read(n));
        binding = false;
        highlight();
        drawList();
    }

    void drawList() {
        list.removeAllViews();
        TextView h = Ui.tv(a, "FILES", 10.5f, 0xFF8B8F9A, true);
        h.setPadding(a.dp(12), a.dp(10), 0, a.dp(4));
        list.addView(h);
        for (final String n : names()) {
            TextView t = Ui.tv(a, n, 12.5f, n.equals(cur) ? Color.WHITE : 0xFFC9CCD3, false);
            t.setSingleLine(true);
            t.setPadding(a.dp(12), a.dp(6), a.dp(8), a.dp(6));
            if (n.equals(cur)) t.setBackground(Ui.rr(0xFF0A84FF, a.dp(7)));
            t.setOnClickListener(v -> open(n));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
            lp.setMargins(a.dp(6), 0, a.dp(6), 0);
            list.addView(t, lp);
        }
    }

    void highlight() {
        Editable e = ed.getText();
        for (ForegroundColorSpan s : e.getSpans(0, e.length(), ForegroundColorSpan.class)) e.removeSpan(s);
        if (e.length() > 60000) return;
        String t = e.toString();
        boolean markup = cur != null && (cur.endsWith(".html") || cur.endsWith(".xml") || cur.endsWith(".htm"));
        if (markup) paint(e, TAG.matcher(t), 0xFFFF7B72);
        paint(e, KW.matcher(t), 0xFFC792EA);
        paint(e, NUM.matcher(t), 0xFFF78C6C);
        paint(e, STR.matcher(t), 0xFFC3E88D);
        paint(e, CMT.matcher(t), 0xFF6A737D);
    }

    void paint(Spannable sp, Matcher m, int color) {
        while (m.find()) sp.setSpan(new ForegroundColorSpan(color), m.start(), m.end(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
    }

    @SuppressLint("SetJavaScriptEnabled")
    void run() {
        if (cur == null) return;
        String code = ed.getText().toString();
        String html;
        if (cur.endsWith(".js")) html = "<html><body style='font-family:sans-serif'><script>\n" + code + "\n</script></body></html>";
        else if (cur.endsWith(".css")) html = "<html><head><style>" + code + "</style></head><body><h1>Heading</h1><p>Paragraph text</p><button>Button</button></body></html>";
        else if (cur.endsWith(".html") || cur.endsWith(".htm")) html = code;
        else { console.setVisibility(View.VISIBLE); console.setText("Only .html, .css and .js files can be run here."); return; }
        previewBox.setVisibility(View.VISIBLE);
        console.setVisibility(View.VISIBLE);
        console.setText("");
        if (web == null) {
            web = new WebView(a);
            web.getSettings().setJavaScriptEnabled(true);
            web.getSettings().setDomStorageEnabled(true);
            web.setWebChromeClient(new WebChromeClient() {
                @Override public boolean onConsoleMessage(ConsoleMessage m) {
                    console.append(m.message() + "\n");
                    return true;
                }
            });
            previewBox.addView(web, new FrameLayout.LayoutParams(-1, -1));
        }
        web.loadDataWithBaseURL("https://preview.local/", html, "text/html", "utf-8", null);
    }
}
