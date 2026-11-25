package com.macos.launcher;

import android.content.ContentValues;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.json.JSONObject;

/** Sheets: a spreadsheet with formulas (+ - * / ^, cell refs, SUM AVERAGE MIN MAX COUNT ROUND ABS SQRT IF), saved locally, exports CSV. */
public class SheetsApp {
    static final int COLS = 12, ROWS = 60;

    final MainActivity a;
    final Map<String, String> cells = new HashMap<>();
    final TextView[][] views = new TextView[ROWS][COLS];
    int sr, sc;
    EditText bar;
    TextView nameBox;

    public static void open(MainActivity a) { new SheetsApp(a); }

    SheetsApp(MainActivity act) {
        a = act;
        try {
            JSONObject o = new JSONObject(a.prefs.getString("sheet1", "{}"));
            java.util.Iterator<String> it = o.keys();
            while (it.hasNext()) { String k = it.next(); cells.put(k, o.getString(k)); }
        } catch (Exception e) { }
        TextView exp = Ui.tv(a, "Export CSV", 12.5f, Color.WHITE, true);
        exp.setGravity(Gravity.CENTER);
        exp.setBackground(Ui.rr(Theme.accent, a.dp(15)));
        exp.setOnClickListener(v -> exportCsv());
        TextView clr = Ui.tv(a, "Clear", 12.5f, Theme.fg(), false);
        clr.setGravity(Gravity.CENTER);
        clr.setBackground(Ui.rr(Theme.dark ? 0x2EFFFFFF : 0x14000000, a.dp(15)));
        clr.setOnClickListener(v -> a.confirm("Clear the whole sheet?", () -> { cells.clear(); save(); refreshAll(); }));
        LinearLayout tb = new LinearLayout(a);
        tb.setGravity(Gravity.CENTER_VERTICAL);
        tb.addView(new View(a), new LinearLayout.LayoutParams(0, 1, 1f));
        tb.addView(clr, new LinearLayout.LayoutParams(a.dp(56), a.dp(30)));
        LinearLayout.LayoutParams el = new LinearLayout.LayoutParams(a.dp(86), a.dp(30));
        el.leftMargin = a.dp(8);
        tb.addView(exp, el);

        LinearLayout col = new LinearLayout(a);
        col.setOrientation(LinearLayout.VERTICAL);
        LinearLayout fb = new LinearLayout(a);
        fb.setGravity(Gravity.CENTER_VERTICAL);
        fb.setPadding(a.dp(8), a.dp(6), a.dp(8), a.dp(6));
        nameBox = Ui.tv(a, "A1", 12.5f, Theme.fg(), true);
        nameBox.setGravity(Gravity.CENTER);
        nameBox.setBackground(Ui.rr(Theme.dark ? 0x2EFFFFFF : 0x12000000, a.dp(8)));
        fb.addView(nameBox, new LinearLayout.LayoutParams(a.dp(52), a.dp(30)));
        bar = new EditText(a);
        bar.setSingleLine(true);
        bar.setTextSize(13);
        bar.setTextColor(Theme.fg());
        bar.setHint("Enter a value or a formula such as =SUM(A1:A5)");
        bar.setHintTextColor(Theme.sub());
        bar.setImeOptions(EditorInfo.IME_ACTION_DONE);
        bar.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        bar.setBackground(Ui.rr(Theme.dark ? 0x2EFFFFFF : 0x12000000, a.dp(8)));
        bar.setPadding(a.dp(10), 0, a.dp(10), 0);
        bar.setOnEditorActionListener((tv, id, ev) -> { commit(); return true; });
        LinearLayout.LayoutParams bl = new LinearLayout.LayoutParams(0, a.dp(30), 1f);
        bl.leftMargin = a.dp(8);
        fb.addView(bar, bl);
        col.addView(fb, new LinearLayout.LayoutParams(-1, -2));
        col.addView(Ui.sepLine(a), new LinearLayout.LayoutParams(-1, 1));

        LinearLayout grid = new LinearLayout(a);
        grid.setOrientation(LinearLayout.VERTICAL);
        LinearLayout head = new LinearLayout(a);
        head.addView(hdr(""), new LinearLayout.LayoutParams(a.dp(36), a.dp(26)));
        for (int c = 0; c < COLS; c++) head.addView(hdr(colName(c)), new LinearLayout.LayoutParams(a.dp(86), a.dp(26)));
        grid.addView(head);
        for (int r = 0; r < ROWS; r++) {
            LinearLayout row = new LinearLayout(a);
            row.addView(hdr(String.valueOf(r + 1)), new LinearLayout.LayoutParams(a.dp(36), a.dp(28)));
            for (int c = 0; c < COLS; c++) {
                final int rr = r, cc = c;
                TextView t = Ui.tv(a, "", 12.5f, Theme.fg(), false);
                t.setSingleLine(true);
                t.setGravity(Gravity.CENTER_VERTICAL);
                t.setPadding(a.dp(6), 0, a.dp(6), 0);
                t.setBackground(cellBg(false));
                t.setOnClickListener(v -> select(rr, cc));
                views[r][c] = t;
                row.addView(t, new LinearLayout.LayoutParams(a.dp(86), a.dp(28)));
            }
            grid.addView(row);
        }
        HorizontalScrollView hs = new HorizontalScrollView(a);
        hs.setHorizontalScrollBarEnabled(false);
        ScrollView vs = new ScrollView(a);
        vs.setVerticalScrollBarEnabled(false);
        vs.addView(grid);
        hs.addView(vs);
        col.addView(hs, new LinearLayout.LayoutParams(-1, 0, 1f));
        a.openWindow(new MacWindow(a, "Feng Sheets", tb, null, col), 740, 350);
        refreshAll();
        select(0, 0);
    }

    TextView hdr(String s) {
        TextView t = Ui.tv(a, s, 11.5f, Theme.sub(), true);
        t.setGravity(Gravity.CENTER);
        t.setBackgroundColor(Theme.dark ? 0xFF2A2A2D : 0xFFF2F2F5);
        return t;
    }

    android.graphics.drawable.Drawable cellBg(boolean sel) {
        android.graphics.drawable.GradientDrawable g = new android.graphics.drawable.GradientDrawable();
        g.setColor(sel ? (Theme.dark ? 0x330A84FF : 0x1A0A84FF) : 0);
        g.setStroke(sel ? a.dp(2) : 1, sel ? 0xFF0A84FF : Theme.sep());
        return g;
    }

    static String colName(int c) { return String.valueOf((char) ('A' + c)); }
    static String key(int r, int c) { return colName(c) + (r + 1); }

    void select(int r, int c) {
        commit();
        views[sr][sc].setBackground(cellBg(false));
        sr = r; sc = c;
        views[r][c].setBackground(cellBg(true));
        nameBox.setText(key(r, c));
        String raw = cells.get(key(r, c));
        bar.setText(raw == null ? "" : raw);
        bar.setSelection(bar.getText().length());
    }

    void commit() {
        String k = key(sr, sc), v = bar.getText().toString();
        String old = cells.get(k);
        if (v.isEmpty()) cells.remove(k); else cells.put(k, v);
        if ((old == null ? "" : old).equals(v)) return;
        save();
        refreshAll();
    }

    void save() {
        try { a.prefs.edit().putString("sheet1", new JSONObject(cells).toString()).apply(); } catch (Exception e) { }
    }

    void refreshAll() {
        for (int r = 0; r < ROWS; r++) for (int c = 0; c < COLS; c++) {
            String s = display(key(r, c));
            views[r][c].setText(s);
            boolean num = s.matches("-?\\d+(\\.\\d+)?");
            views[r][c].setGravity((num ? Gravity.END : Gravity.START) | Gravity.CENTER_VERTICAL);
            views[r][c].setTextColor(s.startsWith("#") ? 0xFFFF3B30 : Theme.fg());
        }
    }

    // ---------- formula engine ----------
    String display(String k) {
        String raw = cells.get(k);
        if (raw == null) return "";
        if (!raw.startsWith("=")) return raw;
        try {
            double v = new Eval(raw.substring(1), new HashSet<String>(), k).run();
            if (v == Math.rint(v) && Math.abs(v) < 1e15) return String.valueOf((long) v);
            return String.format(Locale.US, "%.6g", v).replaceAll("0+$", "").replaceAll("\\.$", "");
        } catch (Exception e) { return "#ERR"; }
    }

    double valueOf(String k, Set<String> stack) {
        String raw = cells.get(k);
        if (raw == null || raw.isEmpty()) return 0;
        if (raw.startsWith("=")) {
            if (stack.contains(k)) throw new RuntimeException("circular");
            Set<String> s2 = new HashSet<>(stack);
            return new Eval(raw.substring(1), s2, k).run();
        }
        try { return Double.parseDouble(raw.trim()); } catch (Exception e) { return 0; }
    }

    class Eval {
        final String s;
        final Set<String> stack;
        int p;

        Eval(String s, Set<String> stack, String self) { this.s = s.replace(" ", ""); this.stack = stack; stack.add(self); }

        double run() { double v = expr(); if (p < s.length()) throw new RuntimeException("syntax"); return v; }

        char peek() { return p < s.length() ? s.charAt(p) : 0; }

        double expr() {
            double v = term();
            while (peek() == '+' || peek() == '-') { char o = s.charAt(p++); double r = term(); v = o == '+' ? v + r : v - r; }
            return v;
        }

        double term() {
            double v = pow();
            while (peek() == '*' || peek() == '/') { char o = s.charAt(p++); double r = pow(); if (o == '/' && r == 0) throw new RuntimeException("div0"); v = o == '*' ? v * r : v / r; }
            return v;
        }

        double pow() {
            double v = unary();
            if (peek() == '^') { p++; v = Math.pow(v, pow()); }
            return v;
        }

        double unary() {
            if (peek() == '-') { p++; return -unary(); }
            if (peek() == '+') { p++; return unary(); }
            return atom();
        }

        double atom() {
            char c = peek();
            if (c == '(') { p++; double v = expr(); if (peek() != ')') throw new RuntimeException("paren"); p++; return v; }
            if (Character.isDigit(c) || c == '.') {
                int st = p;
                while (Character.isDigit(peek()) || peek() == '.') p++;
                return Double.parseDouble(s.substring(st, p));
            }
            if (Character.isLetter(c)) {
                int st = p;
                while (Character.isLetter(peek())) p++;
                String id = s.substring(st, p).toUpperCase(Locale.ROOT);
                if (peek() == '(') { p++; return call(id); }
                int ns = p;
                while (Character.isDigit(peek())) p++;
                if (ns == p) throw new RuntimeException("name");
                return valueOf(id + s.substring(ns, p), stack);
            }
            throw new RuntimeException("syntax");
        }

        double call(String fn) {
            java.util.List<Double> vals = new java.util.ArrayList<>();
            java.util.List<Double> ifArgs = new java.util.ArrayList<>();
            if (peek() != ')') {
                while (true) {
                    int save = p;
                    String rng = tryRange();
                    if (rng != null) vals.addAll(range(rng));
                    else { p = save; double v = cond(); vals.add(v); ifArgs.add(v); }
                    if (peek() == ',' || peek() == ';') { p++; continue; }
                    break;
                }
            }
            if (peek() != ')') throw new RuntimeException("paren");
            p++;
            switch (fn) {
                case "SUM": { double t = 0; for (double v : vals) t += v; return t; }
                case "AVERAGE": case "AVG": { if (vals.isEmpty()) return 0; double t = 0; for (double v : vals) t += v; return t / vals.size(); }
                case "MIN": { double m = Double.MAX_VALUE; for (double v : vals) m = Math.min(m, v); return vals.isEmpty() ? 0 : m; }
                case "MAX": { double m = -Double.MAX_VALUE; for (double v : vals) m = Math.max(m, v); return vals.isEmpty() ? 0 : m; }
                case "COUNT": return vals.size();
                case "ABS": return Math.abs(vals.get(0));
                case "SQRT": return Math.sqrt(vals.get(0));
                case "ROUND": { double f = Math.pow(10, vals.size() > 1 ? vals.get(1) : 0); return Math.round(vals.get(0) * f) / f; }
                case "IF": return vals.get(0) != 0 ? vals.get(1) : (vals.size() > 2 ? vals.get(2) : 0);
                default: throw new RuntimeException("fn");
            }
        }

        double cond() {
            double l = expr();
            String[] ops = {">=", "<=", "<>", ">", "<", "="};
            for (String o : ops) if (s.startsWith(o, p)) {
                p += o.length();
                double r = expr();
                switch (o) {
                    case ">=": return l >= r ? 1 : 0; case "<=": return l <= r ? 1 : 0; case "<>": return l != r ? 1 : 0;
                    case ">": return l > r ? 1 : 0; case "<": return l < r ? 1 : 0; default: return l == r ? 1 : 0;
                }
            }
            return l;
        }

        String tryRange() {
            int st = p;
            if (!Character.isLetter(peek())) return null;
            int q = p;
            while (q < s.length() && Character.isLetter(s.charAt(q))) q++;
            int d = q;
            while (d < s.length() && Character.isDigit(s.charAt(d))) d++;
            if (d == q || d >= s.length() || s.charAt(d) != ':') return null;
            int q2 = d + 1, e = q2;
            while (e < s.length() && Character.isLetter(s.charAt(e))) e++;
            int e2 = e;
            while (e2 < s.length() && Character.isDigit(s.charAt(e2))) e2++;
            if (e == q2 || e2 == e) return null;
            p = e2;
            return s.substring(st, e2);
        }

        java.util.List<Double> range(String r) {
            String[] ab = r.toUpperCase(Locale.ROOT).split(":");
            int c1 = ab[0].charAt(0) - 'A', r1 = Integer.parseInt(ab[0].replaceAll("[A-Z]", "")) - 1;
            int c2 = ab[1].charAt(0) - 'A', r2 = Integer.parseInt(ab[1].replaceAll("[A-Z]", "")) - 1;
            java.util.List<Double> l = new java.util.ArrayList<>();
            for (int rr = Math.min(r1, r2); rr <= Math.max(r1, r2); rr++)
                for (int cc = Math.min(c1, c2); cc <= Math.max(c1, c2); cc++) {
                    String k = colName(cc) + (rr + 1);
                    String raw = cells.get(k);
                    if (raw == null || raw.isEmpty()) continue;
                    l.add(valueOf(k, stack));
                }
            return l;
        }
    }

    // ---------- export ----------
    void exportCsv() {
        commit();
        int maxR = 0, maxC = 0;
        for (String k : cells.keySet()) {
            maxC = Math.max(maxC, k.charAt(0) - 'A');
            maxR = Math.max(maxR, Integer.parseInt(k.substring(1)) - 1);
        }
        StringBuilder sb = new StringBuilder();
        for (int r = 0; r <= maxR; r++) {
            for (int c = 0; c <= maxC; c++) {
                String v = display(key(r, c));
                if (v.contains(",") || v.contains("\"") || v.contains("\n")) v = "\"" + v.replace("\"", "\"\"") + "\"";
                sb.append(v);
                if (c < maxC) sb.append(',');
            }
            sb.append('\n');
        }
        try {
            String name = "Feng Sheets " + new java.text.SimpleDateFormat("yyyy-MM-dd HH.mm.ss", Locale.US).format(new java.util.Date()) + ".csv";
            if (Build.VERSION.SDK_INT >= 29) {
                ContentValues v = new ContentValues();
                v.put(MediaStore.Downloads.DISPLAY_NAME, name);
                v.put(MediaStore.Downloads.MIME_TYPE, "text/csv");
                v.put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS);
                Uri u = a.getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, v);
                try (OutputStream o = a.getContentResolver().openOutputStream(u)) { o.write(sb.toString().getBytes("UTF-8")); }
            } else {
                File f = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), name);
                try (FileOutputStream o = new FileOutputStream(f)) { o.write(sb.toString().getBytes("UTF-8")); }
            }
            Toast.makeText(a, "Saved to Downloads", Toast.LENGTH_SHORT).show();
        } catch (Exception e) { Toast.makeText(a, "Couldn\u2019t export", Toast.LENGTH_SHORT).show(); }
    }
}
