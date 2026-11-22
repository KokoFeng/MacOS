package com.macos.launcher;

import android.graphics.Typeface;
import android.os.Environment;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class TerminalApp {
    final MainActivity a;
    final File home;
    File cwd;
    TextView out, prompt;
    EditText in;
    ScrollView sv;
    MacWindow win;
    volatile Process proc;
    final List<String> hist = new ArrayList<>();
    int hp = 0;

    public static void open(MainActivity a) { new TerminalApp(a); }

    TerminalApp(MainActivity act) {
        a = act;
        home = a.hasStorage() ? Environment.getExternalStorageDirectory() : a.getFilesDir();
        cwd = home;
        final int bg = 0xFF1E1E1E, fg = 0xFFE8E8E8;
        out = new TextView(a);
        out.setTypeface(Typeface.MONOSPACE);
        out.setTextSize(12.5f);
        out.setTextColor(fg);
        out.setTextIsSelectable(true);
        prompt = new TextView(a);
        prompt.setTypeface(Typeface.MONOSPACE);
        prompt.setTextSize(12.5f);
        prompt.setTextColor(fg);
        in = new EditText(a);
        in.setBackground(null);
        in.setTypeface(Typeface.MONOSPACE);
        in.setTextSize(12.5f);
        in.setTextColor(fg);
        in.setPadding(0, 0, 0, 0);
        in.setSingleLine(true);
        in.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
        in.setImeOptions(EditorInfo.IME_ACTION_SEND | EditorInfo.IME_FLAG_NO_EXTRACT_UI);
        in.setOnEditorActionListener((v, id, ev) -> {
            String s = in.getText().toString();
            in.setText("");
            run(s);
            return true;
        });
        LinearLayout row = new LinearLayout(a);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.addView(prompt);
        row.addView(in, new LinearLayout.LayoutParams(0, MainActivity.WRAP, 1f));
        LinearLayout content = new LinearLayout(a);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(a.dp(10), a.dp(6), a.dp(10), a.dp(10));
        content.addView(out);
        content.addView(row);
        content.setOnClickListener(v -> focusInput());
        sv = new ScrollView(a);
        sv.setBackgroundColor(bg);
        sv.addView(content);

        LinearLayout keys = new LinearLayout(a);
        keys.setBackgroundColor(0xFF2A2A2A);
        keys.setGravity(Gravity.CENTER_VERTICAL);
        String[] ks = {"^C", "↑", "↓", "clear"};
        for (final String k : ks) {
            TextView t = Ui.tv(a, k, 12, 0xFFDDDDDD, true);
            t.setPadding(a.dp(14), a.dp(4), a.dp(14), a.dp(4));
            t.setOnClickListener(v -> key(k));
            keys.addView(t);
        }
        LinearLayout body = new LinearLayout(a);
        body.setOrientation(LinearLayout.VERTICAL);
        body.addView(sv, new LinearLayout.LayoutParams(MainActivity.MATCH, 0, 1f));
        body.addView(keys, new LinearLayout.LayoutParams(MainActivity.MATCH, MainActivity.WRAP));

        TextView title = Ui.tv(a, "user — -sh — 80×24", 13, 0xFFBDBDBD, true);
        title.setGravity(Gravity.CENTER);
        win = new MacWindow(a, "Terminal", title, null, body, bg);
        win.onClose = () -> { Process p = proc; if (p != null) p.destroy(); };
        a.openWindow(win, 560, 300);
        out.append("Last login: " + new SimpleDateFormat("EEE MMM d HH:mm:ss", Locale.US).format(new Date()) + " on console\n");
        updatePrompt();
        focusInput();
    }

    void focusInput() {
        in.requestFocus();
        android.view.inputmethod.InputMethodManager im = (android.view.inputmethod.InputMethodManager) a.getSystemService(android.content.Context.INPUT_METHOD_SERVICE);
        im.showSoftInput(in, 0);
    }

    String promptText() { return "android:" + (cwd.equals(home) ? "~" : cwd.getName()) + " user$ "; }
    void updatePrompt() { prompt.setText(promptText()); }

    void key(String k) {
        if (k.equals("^C")) { Process p = proc; if (p != null) { p.destroy(); append("^C\n"); } }
        else if (k.equals("clear")) out.setText("");
        else if (k.equals("↑")) { if (hp > 0) { hp--; in.setText(hist.get(hp)); in.setSelection(in.getText().length()); } }
        else if (k.equals("↓")) {
            if (hp < hist.size() - 1) { hp++; in.setText(hist.get(hp)); in.setSelection(in.getText().length()); }
            else { hp = hist.size(); in.setText(""); }
        }
    }

    void append(String s) {
        out.append(s);
        if (out.length() > 40000) out.setText(out.getText().subSequence(out.length() - 30000, out.length()));
        sv.post(() -> sv.fullScroll(View.FOCUS_DOWN));
    }

    void run(String cmd) {
        String line = cmd.trim();
        append(promptText() + cmd + "\n");
        if (line.isEmpty()) return;
        hist.add(line);
        hp = hist.size();
        if (line.equals("clear")) { out.setText(""); return; }
        if (line.equals("exit")) { win.close(); return; }
        if (line.equals("cd") || line.startsWith("cd ")) { cd(line.substring(2).trim()); return; }
        if (proc != null) { append("Perintah sebelumnya masih berjalan (tekan ^C).\n"); return; }
        final String c = line;
        new Thread(() -> {
            try {
                ProcessBuilder pb = new ProcessBuilder("sh", "-c", c);
                pb.directory(cwd);
                pb.redirectErrorStream(true);
                pb.environment().put("HOME", home.getAbsolutePath());
                pb.environment().put("TERM", "xterm");
                Process p = pb.start();
                proc = p;
                BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream()));
                char[] buf = new char[1024];
                int n;
                while ((n = r.read(buf)) > 0) {
                    final String s = new String(buf, 0, n);
                    a.runOnUiThread(() -> append(s));
                }
                p.waitFor();
            } catch (Exception e) {
                final String m = String.valueOf(e.getMessage());
                a.runOnUiThread(() -> append(m + "\n"));
            } finally {
                proc = null;
                a.runOnUiThread(this::updatePrompt);
            }
        }).start();
    }

    void cd(String path) {
        try {
            File t;
            if (path.isEmpty() || path.equals("~")) t = home;
            else if (path.startsWith("~/")) t = new File(home, path.substring(2));
            else if (path.startsWith("/")) t = new File(path);
            else t = new File(cwd, path);
            t = t.getCanonicalFile();
            if (t.isDirectory()) { cwd = t; updatePrompt(); }
            else append("cd: " + path + ": No such file or directory\n");
        } catch (Exception e) { append("cd: " + path + ": error\n"); }
    }
}
