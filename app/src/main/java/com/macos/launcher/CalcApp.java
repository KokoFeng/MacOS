package com.macos.launcher;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import java.math.BigDecimal;
import java.math.MathContext;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** macOS Tahoe Calculator: Basic, Scientific, Programmer, Convert and RPN modes with a history sidebar. */
public class CalcApp {
    static final int BG = 0xFF212225, SIDE = 0xFF2A2B2E, NUM = 0xFF4C4D52, FUN = 0xFF35363A, GRY = 0xFF6D6E73, OPR = 0xFFFF9F0A;
    static final String M = "\u2212"; // minus sign

    static final String[][] BASIC = {
        {"\u232B", "AC", "%", "\u00F7"}, {"7", "8", "9", "\u00D7"}, {"4", "5", "6", M}, {"1", "2", "3", "+"}, {"+/-", "0", ".", "="}};
    static final String[][] SCI = {
        {"(", ")", "mc", "m+", "m-", "mr", "\u232B", "AC", "%", "\u00F7"},
        {"2nd", "x\u00B2", "x\u00B3", "x\u02B8", "y\u02E3", "2\u02E3", "7", "8", "9", "\u00D7"},
        {"1/x", "\u00B2\u221Ax", "\u00B3\u221Ax", "\u02B8\u221Ax", "log_y", "log\u2082", "4", "5", "6", M},
        {"x!", "sin", "cos", "tan", "e", "EE", "1", "2", "3", "+"},
        {"Rand", "sinh", "cosh", "tanh", "\u03C0", "Rad", "+/-", "0", ".", "="}};
    static final String[][] PROG = {
        {"AND", "OR", "XOR", "NOT", "AC", "\u232B"}, {"A", "B", "7", "8", "9", "\u00F7"}, {"C", "D", "4", "5", "6", "\u00D7"},
        {"E", "F", "1", "2", "3", M}, {"<<", ">>", "0", "\u00B1", "=", "+"}};

    final MainActivity a;
    MacWindow win;
    LinearLayout main, sidebar, histList;
    TextView big, sub;
    String mode;
    final StringBuilder expr = new StringBuilder();
    String subText = "";
    boolean justEval, err, second, deg;
    double mem;
    final List<String[]> hist = new ArrayList<>();

    // convert
    int cat = 2, uFrom = 5, uTo = 7;
    TextView outTv, fromChip, toChip, catChip;
    // programmer
    int base = 10;
    long pAcc;
    String pOp;
    StringBuilder pEntry = new StringBuilder("0");
    boolean pFresh = true;
    TextView[] baseRows = new TextView[4];
    // rpn
    final List<Double> stack = new ArrayList<>();
    final StringBuilder rEntry = new StringBuilder();

    public static void open(MainActivity a) { new CalcApp(a); }

    CalcApp(MainActivity act) {
        a = act;
        mode = a.prefs.getString("calc_mode", "Basic");
        loadHist();
        LinearLayout body = new LinearLayout(a);
        sidebar = new LinearLayout(a);
        sidebar.setOrientation(LinearLayout.VERTICAL);
        sidebar.setBackgroundColor(SIDE);
        sidebar.setVisibility(View.GONE);
        TextView today = Ui.tv(a, "Today", 12, 0xFFAAAAAF, true);
        today.setPadding(a.dp(12), a.dp(8), a.dp(8), a.dp(4));
        sidebar.addView(today);
        histList = new LinearLayout(a);
        histList.setOrientation(LinearLayout.VERTICAL);
        ScrollView hs = new ScrollView(a);
        hs.setVerticalScrollBarEnabled(false);
        hs.addView(histList);
        sidebar.addView(hs, new LinearLayout.LayoutParams(MainActivity.MATCH, 0, 1f));
        body.addView(sidebar, new LinearLayout.LayoutParams(a.dp(150), MainActivity.MATCH));
        main = new LinearLayout(a);
        main.setOrientation(LinearLayout.VERTICAL);
        main.setPadding(a.dp(8), 0, a.dp(8), a.dp(8));
        body.addView(main, new LinearLayout.LayoutParams(0, MainActivity.MATCH, 1f));

        LinearLayout tb = new LinearLayout(a);
        tb.setGravity(Gravity.CENTER_VERTICAL);
        FrameLayout sb = circle(0);
        sb.setOnClickListener(v -> {
            sidebar.setVisibility(sidebar.getVisibility() == View.VISIBLE ? View.GONE : View.VISIBLE);
            refreshHist();
        });
        tb.addView(sb, new LinearLayout.LayoutParams(a.dp(34), a.dp(34)));
        tb.addView(new View(a), new LinearLayout.LayoutParams(0, 1, 1f));
        FrameLayout mb = circle(1);
        mb.setOnClickListener(v -> modeMenu(v));
        tb.addView(mb, new LinearLayout.LayoutParams(a.dp(34), a.dp(34)));

        win = new MacWindow(a, "Calculator", tb, null, body, BG);
        int[] sz = size(mode);
        a.openWindow(win, sz[0], sz[1]);
        build();
    }

    int[] size(String m) {
        if (m.equals("Scientific") || m.equals("Programmer")) return new int[]{560, 340};
        return new int[]{310, 360};
    }

    FrameLayout circle(int type) {
        FrameLayout f = new FrameLayout(a);
        GradientDrawable g = new GradientDrawable();
        g.setShape(GradientDrawable.OVAL);
        g.setColor(0x33FFFFFF);
        f.setBackground(g);
        f.addView(new Glyph(type), new FrameLayout.LayoutParams(a.dp(17), a.dp(17), Gravity.CENTER));
        return f;
    }

    class Glyph extends View {
        final int type;
        final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        Glyph(int t) { super(a); type = t; }
        @Override protected void onDraw(Canvas c) {
            float w = getWidth(), h = getHeight();
            p.setColor(0xFFE8E8EC);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(a.dp(1.4f));
            c.drawRoundRect(new RectF(w * .06f, h * .12f, w * .94f, h * .88f), w * .2f, w * .2f, p);
            if (type == 0) {
                c.drawLine(w * .38f, h * .12f, w * .38f, h * .88f, p);
                p.setStrokeWidth(a.dp(1.2f));
                c.drawLine(w * .16f, h * .34f, w * .28f, h * .34f, p);
                c.drawLine(w * .16f, h * .5f, w * .28f, h * .5f, p);
            } else {
                p.setStyle(Paint.Style.FILL);
                for (int r = 0; r < 2; r++) for (int q = 0; q < 3; q++)
                    c.drawCircle(w * (.28f + q * .22f), h * (.5f + r * .2f), a.dp(1.1f), p);
                c.drawRect(w * .24f, h * .24f, w * .76f, h * .38f, p);
            }
        }
    }

    // ================= mode menu =================
    void modeMenu(View anchor) {
        String[] names = {"Basic", "Scientific", "Programmer", "-", "Convert", "RPN Mode", "-", "Math Notes\u2026"};
        String[] labels = new String[names.length];
        Runnable[] acts = new Runnable[names.length];
        for (int i = 0; i < names.length; i++) {
            final String n = names[i];
            if (n.equals("-")) { labels[i] = "-"; continue; }
            String nm = n.equals("RPN Mode") ? "RPN" : n;
            labels[i] = (mode.equals(nm) ? "\u2713  " : "     ") + n;
            if (n.startsWith("Math")) acts[i] = () -> Toast.makeText(a, "Math Notes belum tersedia", Toast.LENGTH_SHORT).show();
            else acts[i] = () -> setMode(nm);
        }
        a.showPopup(anchor, labels, acts);
    }

    void setMode(String m) {
        mode = m;
        a.prefs.edit().putString("calc_mode", m).apply();
        if (!win.isZoomed()) {
            int[] sz = size(m);
            android.view.ViewGroup.LayoutParams lp = win.getLayoutParams();
            lp.width = Math.min(a.dp(sz[0]), a.sw - a.dp(20));
            lp.height = Math.min(a.dp(sz[1]), a.sh - a.mbH() - a.dp(20));
            win.requestLayout();
        }
        build();
    }

    // ================= building =================
    void build() {
        main.removeAllViews();
        expr.setLength(0); subText = ""; justEval = false; err = false;
        if (mode.equals("Convert")) buildConvert();
        else if (mode.equals("Programmer")) buildProg();
        else if (mode.equals("RPN")) buildRpn();
        else { buildDisplay(); main.addView(keys(mode.equals("Scientific") ? sciSpec() : BASIC, mode.equals("Scientific") ? 14f : 19f),
                new LinearLayout.LayoutParams(MainActivity.MATCH, 0, .72f)); show(); }
    }

    String[][] sciSpec() {
        String[][] s = new String[SCI.length][];
        for (int i = 0; i < SCI.length; i++) s[i] = SCI[i].clone();
        s[4][5] = deg ? "Deg" : "Rad";
        if (second) {
            s[3][1] = "sin\u207B\u00B9"; s[3][2] = "cos\u207B\u00B9"; s[3][3] = "tan\u207B\u00B9";
            s[4][1] = "sinh\u207B\u00B9"; s[4][2] = "cosh\u207B\u00B9"; s[4][3] = "tanh\u207B\u00B9";
            s[1][5] = "e\u02E3"; s[2][4] = "ln"; s[2][5] = "log\u2081\u2080";
        }
        return s;
    }

    TextView text(String s, float sp, int col, int grav) {
        TextView t = Ui.tv(a, s, sp, col, false);
        t.setGravity(grav);
        return t;
    }

    void buildDisplay() {
        FrameLayout d = new FrameLayout(a);
        sub = text("", 12, 0xFF9A9AA0, Gravity.RIGHT);
        d.addView(sub, new FrameLayout.LayoutParams(MainActivity.MATCH, MainActivity.WRAP, Gravity.TOP | Gravity.RIGHT));
        big = text("0", 38, 0xFFFFFFFF, Gravity.RIGHT | Gravity.BOTTOM);
        big.setSingleLine(true);
        big.setAutoSizeTextTypeUniformWithConfiguration(14, 44, 2, android.util.TypedValue.COMPLEX_UNIT_SP);
        d.addView(big, new FrameLayout.LayoutParams(MainActivity.MATCH, MainActivity.MATCH, Gravity.BOTTOM));
        d.setPadding(a.dp(6), a.dp(2), a.dp(8), a.dp(4));
        main.addView(d, new LinearLayout.LayoutParams(MainActivity.MATCH, 0, .28f));
    }

    LinearLayout keys(String[][] spec, final float sp) {
        LinearLayout grid = new LinearLayout(a);
        grid.setOrientation(LinearLayout.VERTICAL);
        for (String[] row : spec) {
            LinearLayout r = new LinearLayout(a);
            for (final String k : row) {
                TextView t = Ui.tv(a, k, sp, 0xFFFFFFFF, false);
                t.setGravity(Gravity.CENTER);
                t.setSingleLine(true);
                int col = FUN;
                if (k.matches("[0-9.]|[A-F]|\\+/-|\u00B1")) col = NUM;
                if (k.equals("\u00F7") || k.equals("\u00D7") || k.equals(M) || k.equals("+") || k.equals("=") || k.equals("Enter")) col = OPR;
                if (k.equals("AC") || k.equals("\u232B") || k.equals("%")) col = GRY;
                if (k.equals("2nd") && second) col = 0xFF8A8B90;
                final GradientDrawable g = new GradientDrawable();
                g.setCornerRadius(a.dp(40));
                g.setColor(col);
                g.setStroke(1, 0x22FFFFFF);
                t.setBackground(g);
                final int fc = col;
                t.setOnTouchListener((v, e) -> {
                    int ac = e.getActionMasked();
                    if (ac == MotionEvent.ACTION_DOWN) g.setColor(lighten(fc));
                    else if (ac == MotionEvent.ACTION_UP || ac == MotionEvent.ACTION_CANCEL) g.setColor(fc);
                    return false;
                });
                t.setOnClickListener(v -> press(k));
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, MainActivity.MATCH, 1f);
                lp.setMargins(a.dp(2), a.dp(2), a.dp(2), a.dp(2));
                r.addView(t, lp);
            }
            grid.addView(r, new LinearLayout.LayoutParams(MainActivity.MATCH, 0, 1f));
        }
        return grid;
    }

    static int lighten(int c) {
        int r = Math.min(255, ((c >> 16) & 255) + 38), g = Math.min(255, ((c >> 8) & 255) + 38), b = Math.min(255, (c & 255) + 38);
        return 0xFF000000 | r << 16 | g << 8 | b;
    }

    // ================= basic / scientific input =================
    void show() {
        if (big == null) return;
        if (err) big.setText("Error");
        else big.setText(expr.length() == 0 ? "0" : pretty(expr.toString()));
        if (sub != null) sub.setText(subText);
    }

    void press(String k) {
        if (mode.equals("RPN")) { rpnPress(k); return; }
        if (mode.equals("Programmer")) { progPress(k); return; }
        if (err && !k.equals("AC")) { expr.setLength(0); err = false; }
        switch (k) {
            case "AC": expr.setLength(0); subText = ""; justEval = false; err = false; break;
            case "\u232B": backspace(); break;
            case "=": equals(); return;
            case "2nd": second = !second; build(); return;
            case "Rad": case "Deg": deg = !deg; build(); return;
            case "mc": mem = 0; return;
            case "m+": mem += curValue(); return;
            case "m-": mem -= curValue(); return;
            case "mr": add(raw(mem), true); break;
            case "+/-": negate(); break;
            case "Rand": add(raw(Math.random()), true); break;
            default: addKey(k);
        }
        if (mode.equals("Convert")) convUpdate(); else show();
    }

    void addKey(String k) {
        switch (k) {
            case "x\u00B2": add("^2", false); break;
            case "x\u00B3": add("^3", false); break;
            case "x\u02B8": case "y\u02E3": add("^", false); break;
            case "2\u02E3": add("2^", true); break;
            case "e\u02E3": add("e^", true); break;
            case "1/x": add("^(" + M + "1)", false); break;
            case "\u00B2\u221Ax": add("sqrt(", true); break;
            case "\u00B3\u221Ax": add("cbrt(", true); break;
            case "\u02B8\u221Ax": add("\u02B8\u221A", false); break;
            case "log_y": add("log_", false); break;
            case "log\u2082": add("log2(", true); break;
            case "log\u2081\u2080": add("log10(", true); break;
            case "ln": add("ln(", true); break;
            case "x!": add("!", false); break;
            case "EE": add("E", false); break;
            case "\u03C0": add("\u03C0", true); break;
            case "e": add("e", true); break;
            case "sin": case "cos": case "tan": case "sinh": case "cosh": case "tanh": add(k + "(", true); break;
            case "sin\u207B\u00B9": add("asin(", true); break;
            case "cos\u207B\u00B9": add("acos(", true); break;
            case "tan\u207B\u00B9": add("atan(", true); break;
            case "sinh\u207B\u00B9": add("asinh(", true); break;
            case "cosh\u207B\u00B9": add("acosh(", true); break;
            case "tanh\u207B\u00B9": add("atanh(", true); break;
            case "\u00F7": case "\u00D7": case "+": case M: add(k, false); break;
            default: add(k, k.matches("[0-9.(]"));
        }
    }

    /** Appends text; operand-like input after "=" starts a fresh expression, operators continue from the result. */
    void add(String s, boolean starts) {
        if (justEval) {
            if (starts) expr.setLength(0);
            justEval = false; subText = "";
        }
        expr.append(s);
    }

    void backspace() {
        justEval = false;
        int n = expr.length();
        if (n == 0) return;
        String e = expr.toString();
        if (e.endsWith("(")) {
            int j = n - 1;
            while (j > 0 && (Character.isLowerCase(e.charAt(j - 1)) || Character.isDigit(e.charAt(j - 1)))) j--;
            while (j < n - 1 && Character.isDigit(e.charAt(j))) j++;
            expr.setLength(j);
        } else if (e.endsWith("log_")) expr.setLength(n - 4);
        else expr.setLength(n - 1);
    }

    void negate() {
        String e = expr.toString();
        justEval = false;
        if (e.isEmpty()) { expr.append(M); return; }
        if (e.startsWith(M + "(") && e.endsWith(")")) { expr.setLength(0); expr.append(e, 2, e.length() - 1); }
        else { expr.setLength(0); expr.append(M).append("(").append(e).append(")"); }
    }

    double curValue() {
        try { return new Ev(expr.toString(), deg).parse(); } catch (Exception e) { return 0; }
    }

    void equals() {
        if (expr.length() == 0) return;
        String e = expr.toString();
        try {
            double v = new Ev(e, deg).parse();
            if (Double.isNaN(v) || Double.isInfinite(v)) throw new Exception("nan");
            String r = raw(v);
            if (mode.equals("Convert")) { expr.setLength(0); expr.append(r); convUpdate(); return; }
            addHist(pretty(e), pretty(r), r);
            subText = pretty(e) + " =";
            expr.setLength(0); expr.append(r);
            justEval = true;
        } catch (Exception ex) { err = true; }
        show();
    }

    // ================= formatting =================
    static String raw(double d) {
        if (d == 0) return "0";
        double ad = Math.abs(d);
        if (ad >= 1e15 || ad < 1e-9) {
            String t = String.format(Locale.US, "%.9E", d);
            int ei = t.indexOf('E');
            String mant = t.substring(0, ei).replaceAll("0+$", "").replaceAll("\\.$", "");
            int ex = Integer.parseInt(t.substring(ei + 1));
            return mant + "E" + (ex >= 0 ? "+" : "-") + Math.abs(ex);
        }
        return new BigDecimal(d).round(new MathContext(14)).stripTrailingZeros().toPlainString();
    }

    static String group(String digits) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < digits.length(); i++) {
            if (i > 0 && (digits.length() - i) % 3 == 0) sb.append(',');
            sb.append(digits.charAt(i));
        }
        return sb.toString();
    }

    static String pretty(String e) {
        Matcher m = Pattern.compile("(?<![\\d.])(\\d{4,})").matcher(e);
        StringBuffer sb = new StringBuffer();
        while (m.find()) m.appendReplacement(sb, group(m.group(1)));
        m.appendTail(sb);
        String r = sb.toString().replace("*", "\u00D7").replace("/", "\u00F7");
        return r.replaceAll("(?<=\\d|\\))-", M).replaceAll("^-", M);
    }

    // ================= history =================
    void loadHist() {
        hist.clear();
        String s = a.prefs.getString("calc_hist", "");
        for (String line : s.split("\n")) {
            String[] f = line.split("\t", -1);
            if (f.length == 3) hist.add(f);
        }
    }

    void saveHist() {
        StringBuilder sb = new StringBuilder();
        for (String[] h : hist) sb.append(h[0]).append('\t').append(h[1]).append('\t').append(h[2]).append('\n');
        a.prefs.edit().putString("calc_hist", sb.toString()).apply();
    }

    void addHist(String e, String r, String rawv) {
        hist.add(0, new String[]{e, r, rawv});
        while (hist.size() > 60) hist.remove(hist.size() - 1);
        saveHist();
        refreshHist();
    }

    void refreshHist() {
        histList.removeAllViews();
        for (final String[] h : new ArrayList<>(hist)) {
            LinearLayout it = new LinearLayout(a);
            it.setOrientation(LinearLayout.VERTICAL);
            it.setPadding(a.dp(12), a.dp(6), a.dp(8), a.dp(6));
            TextView e = Ui.tv(a, h[0], 10.5f, 0xFFA0A0A6, false);
            e.setSingleLine(true);
            TextView r = Ui.tv(a, h[1], 14, 0xFFFFFFFF, true);
            r.setSingleLine(true);
            it.addView(e); it.addView(r);
            it.setOnClickListener(v -> {
                if (mode.equals("Basic") || mode.equals("Scientific") || mode.equals("Convert")) {
                    expr.setLength(0); expr.append(h[2]); justEval = true; subText = h[0] + " ="; err = false;
                    if (mode.equals("Convert")) convUpdate(); else show();
                }
            });
            it.setOnLongClickListener(v -> {
                a.showPopup(v, new String[]{"Copy", "Delete"}, new Runnable[]{
                    () -> {
                        android.content.ClipboardManager cm = (android.content.ClipboardManager) a.getSystemService(android.content.Context.CLIPBOARD_SERVICE);
                        cm.setPrimaryClip(android.content.ClipData.newPlainText("calc", h[1]));
                        Toast.makeText(a, "Disalin", Toast.LENGTH_SHORT).show();
                    },
                    () -> { hist.remove(h); saveHist(); refreshHist(); }});
                return true;
            });
            histList.addView(it);
            View sp = new View(a);
            sp.setBackgroundColor(0x1FFFFFFF);
            LinearLayout.LayoutParams sl = new LinearLayout.LayoutParams(MainActivity.MATCH, 1);
            sl.leftMargin = a.dp(12);
            histList.addView(sp, sl);
        }
        if (hist.isEmpty()) {
            TextView t = Ui.tv(a, "No history", 12, 0xFF77777C, false);
            t.setPadding(a.dp(12), a.dp(8), 0, 0);
            histList.addView(t);
        } else {
            TextView clr = Ui.tv(a, "Clear", 12, OPR, true);
            clr.setPadding(a.dp(12), a.dp(10), 0, a.dp(10));
            clr.setOnClickListener(v -> { hist.clear(); saveHist(); refreshHist(); });
            histList.addView(clr);
        }
    }

    // ================= convert =================
    static final String[] CATS = {"Length", "Mass", "Time", "Temperature", "Area", "Volume", "Speed", "Data"};
    static final String[][] UN = {
        {"mm", "cm", "m", "km", "in", "ft", "yd", "mi"}, {"mg", "g", "kg", "t", "oz", "lb"},
        {"ms", "s", "min", "hr", "day", "wk", "mo", "yr"}, {"\u00B0C", "\u00B0F", "K"},
        {"cm\u00B2", "m\u00B2", "ha", "km\u00B2", "ft\u00B2", "ac"}, {"mL", "L", "gal", "qt", "cup", "fl oz"},
        {"m/s", "km/h", "mph", "kn"}, {"B", "KB", "MB", "GB", "TB"}};
    static final double[][] FA = {
        {.001, .01, 1, 1000, .0254, .3048, .9144, 1609.344}, {1e-6, .001, 1, 1000, .028349523125, .45359237},
        {.001, 1, 60, 3600, 86400, 604800, 2629800, 31536000}, {1, 1, 1},
        {1e-4, 1, 1e4, 1e6, .09290304, 4046.8564224}, {.001, 1, 3.785411784, .946352946, .2365882365, .0295735295625},
        {1, 1 / 3.6, .44704, .514444}, {1, 1e3, 1e6, 1e9, 1e12}};

    double convert(double v, int c, int from, int to) {
        if (c == 3) {
            double cc = from == 0 ? v : from == 1 ? (v - 32) * 5 / 9 : v - 273.15;
            return to == 0 ? cc : to == 1 ? cc * 9 / 5 + 32 : cc + 273.15;
        }
        return v * FA[c][from] / FA[c][to];
    }

    void buildConvert() {
        LinearLayout top = new LinearLayout(a);
        top.setOrientation(LinearLayout.VERTICAL);
        top.setPadding(a.dp(6), 0, a.dp(8), 0);
        catChip = Ui.tv(a, CATS[cat] + " \u25BE", 11.5f, 0xFF9A9AA0, false);
        catChip.setOnClickListener(v -> {
            String[] l = CATS.clone();
            Runnable[] r = new Runnable[l.length];
            for (int i = 0; i < l.length; i++) { final int k = i; r[i] = () -> { cat = k; uFrom = 0; uTo = Math.min(1, UN[k].length - 1); expr.setLength(0); build(); }; }
            a.showPopup(v, l, r);
        });
        top.addView(catChip);
        big = text("0", 30, 0xFFFFFFFF, Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        big.setSingleLine(true);
        big.setAutoSizeTextTypeUniformWithConfiguration(14, 34, 2, android.util.TypedValue.COMPLEX_UNIT_SP);
        fromChip = unitChip(true);
        top.addView(row2(big, fromChip), new LinearLayout.LayoutParams(MainActivity.MATCH, 0, 1f));
        LinearLayout sepRow = new LinearLayout(a);
        sepRow.setGravity(Gravity.CENTER_VERTICAL);
        TextView sw = Ui.tv(a, "\u21C5", 16, OPR, true);
        sw.setOnClickListener(v -> { int t = uFrom; uFrom = uTo; uTo = t; build(); });
        sepRow.addView(sw);
        View line = new View(a);
        line.setBackgroundColor(0x33FFFFFF);
        LinearLayout.LayoutParams ll = new LinearLayout.LayoutParams(0, 1, 1f);
        ll.leftMargin = a.dp(8);
        sepRow.addView(line, ll);
        top.addView(sepRow, new LinearLayout.LayoutParams(MainActivity.MATCH, a.dp(22)));
        outTv = text("0", 30, 0xFFC8C8CC, Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        outTv.setSingleLine(true);
        outTv.setAutoSizeTextTypeUniformWithConfiguration(14, 34, 2, android.util.TypedValue.COMPLEX_UNIT_SP);
        toChip = unitChip(false);
        top.addView(row2(outTv, toChip), new LinearLayout.LayoutParams(MainActivity.MATCH, 0, 1f));
        main.addView(top, new LinearLayout.LayoutParams(MainActivity.MATCH, 0, .34f));
        main.addView(keys(BASIC, 17f), new LinearLayout.LayoutParams(MainActivity.MATCH, 0, .66f));
        sub = null;
        convUpdate();
    }

    LinearLayout row2(TextView value, View chip) {
        LinearLayout r = new LinearLayout(a);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.addView(value, new LinearLayout.LayoutParams(0, MainActivity.MATCH, 1f));
        r.addView(chip);
        return r;
    }

    TextView unitChip(final boolean from) {
        TextView t = Ui.tv(a, UN[cat][from ? uFrom : uTo] + " \u25BE", 12, 0xFFC8C8CC, false);
        t.setPadding(a.dp(8), a.dp(4), a.dp(4), a.dp(4));
        t.setOnClickListener(v -> {
            String[] l = UN[cat];
            Runnable[] r = new Runnable[l.length];
            for (int i = 0; i < l.length; i++) { final int k = i; r[i] = () -> { if (from) uFrom = k; else uTo = k; ((TextView) v).setText(l[k] + " \u25BE"); convUpdate(); }; }
            a.showPopup(v, l, r);
        });
        return t;
    }

    void convUpdate() {
        if (big == null || outTv == null) return;
        big.setText(err ? "Error" : expr.length() == 0 ? "0" : pretty(expr.toString()));
        try {
            double v = expr.length() == 0 ? 0 : new Ev(expr.toString(), deg).parse();
            double o = convert(v, cat, uFrom, uTo);
            String r = raw(o);
            int dot = r.indexOf('.'), ex = r.indexOf('E');
            if (ex < 0) outTv.setText(pretty(dot < 0 ? r : r.substring(0, dot)) + (dot < 0 ? "" : r.substring(dot)));
            else outTv.setText(r);
        } catch (Exception e) { outTv.setText("\u2014"); }
    }

    // ================= programmer =================
    void buildProg() {
        LinearLayout row = new LinearLayout(a);
        LinearLayout left = new LinearLayout(a);
        left.setOrientation(LinearLayout.VERTICAL);
        String[] names = {"HEX", "DEC", "OCT", "BIN"};
        int[] bs = {16, 10, 8, 2};
        big = text("0", 22, 0xFFFFFFFF, Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        big.setSingleLine(true);
        big.setAutoSizeTextTypeUniformWithConfiguration(10, 26, 2, android.util.TypedValue.COMPLEX_UNIT_SP);
        big.setPadding(0, 0, a.dp(6), 0);
        left.addView(big, new LinearLayout.LayoutParams(MainActivity.MATCH, 0, 1.1f));
        for (int i = 0; i < 4; i++) {
            final int b = bs[i];
            TextView t = Ui.tv(a, "", 11.5f, 0xFFC8C8CC, false);
            t.setGravity(Gravity.CENTER_VERTICAL);
            t.setSingleLine(true);
            t.setPadding(a.dp(6), 0, a.dp(4), 0);
            t.setTag(names[i]);
            t.setOnClickListener(v -> { long c = progCur(); base = b; pEntry.setLength(0); pEntry.append(Long.toString(c, base).toUpperCase()); pFresh = true; progShow(); });
            baseRows[i] = t;
            left.addView(t, new LinearLayout.LayoutParams(MainActivity.MATCH, 0, 1f));
        }
        row.addView(left, new LinearLayout.LayoutParams(0, MainActivity.MATCH, .42f));
        row.addView(keys(PROG, 15f), new LinearLayout.LayoutParams(0, MainActivity.MATCH, .58f));
        main.addView(row, new LinearLayout.LayoutParams(MainActivity.MATCH, MainActivity.MATCH));
        progShow();
    }

    long progCur() {
        try { return Long.parseLong(pEntry.toString().isEmpty() ? "0" : pEntry.toString(), base); } catch (Exception e) { return 0; }
    }

    void progShow() {
        if (big == null) return;
        long c = progCur();
        big.setText(pEntry.length() == 0 ? "0" : pEntry.toString());
        int[] bs = {16, 10, 8, 2};
        String[] nm = {"HEX", "DEC", "OCT", "BIN"};
        for (int i = 0; i < 4; i++) {
            baseRows[i].setText(nm[i] + "  " + Long.toString(c, bs[i]).toUpperCase());
            baseRows[i].setTextColor(bs[i] == base ? OPR : 0xFFC8C8CC);
        }
    }

    long progApply(long x, long y, String op) {
        switch (op) {
            case "+": return x + y;
            case M: return x - y;
            case "\u00D7": return x * y;
            case "\u00F7": return y == 0 ? 0 : x / y;
            case "AND": return x & y;
            case "OR": return x | y;
            case "XOR": return x ^ y;
            case "<<": return x << y;
            case ">>": return x >> y;
        }
        return y;
    }

    void progPress(String k) {
        if (k.equals("AC")) { pAcc = 0; pOp = null; pEntry.setLength(0); pEntry.append("0"); pFresh = true; }
        else if (k.equals("\u232B")) { if (!pFresh && pEntry.length() > 0) pEntry.setLength(pEntry.length() - 1); if (pEntry.length() == 0) pEntry.append("0"); }
        else if (k.equals("NOT")) { pEntry.setLength(0); pEntry.append(Long.toString(~progCur(), base).toUpperCase()); pFresh = true; }
        else if (k.equals("\u00B1")) { pEntry.setLength(0); pEntry.append(Long.toString(-progCur(), base).toUpperCase()); pFresh = true; }
        else if (k.equals("=")) {
            if (pOp != null) { pAcc = progApply(pAcc, progCur(), pOp); pOp = null; pEntry.setLength(0); pEntry.append(Long.toString(pAcc, base).toUpperCase()); pFresh = true; }
        } else if (k.matches("[0-9A-F]")) {
            if (Character.digit(k.charAt(0), 16) >= base) return;
            if (pFresh) { pEntry.setLength(0); pFresh = false; }
            if (pEntry.length() < 16) { if (pEntry.toString().equals("0")) pEntry.setLength(0); pEntry.append(k); }
        } else {
            if (pOp != null && !pFresh) pAcc = progApply(pAcc, progCur(), pOp); else pAcc = progCur();
            pOp = k; pFresh = true;
            pEntry.setLength(0); pEntry.append(Long.toString(pAcc, base).toUpperCase());
        }
        progShow();
    }

    // ================= RPN =================
    void buildRpn() {
        stack.clear(); rEntry.setLength(0);
        big = text("", 20, 0xFFFFFFFF, Gravity.RIGHT | Gravity.BOTTOM);
        big.setPadding(a.dp(6), a.dp(2), a.dp(8), a.dp(4));
        main.addView(big, new LinearLayout.LayoutParams(MainActivity.MATCH, 0, .3f));
        String[][] spec = new String[BASIC.length][];
        for (int i = 0; i < BASIC.length; i++) spec[i] = BASIC[i].clone();
        spec[4][3] = "Enter";
        main.addView(keys(spec, 17f), new LinearLayout.LayoutParams(MainActivity.MATCH, 0, .7f));
        rpnShow();
    }

    void rpnShow() {
        StringBuilder sb = new StringBuilder();
        int from = Math.max(0, stack.size() - 3);
        for (int i = from; i < stack.size(); i++) sb.append(pretty(raw(stack.get(i)))).append('\n');
        sb.append(rEntry.length() == 0 ? (stack.isEmpty() ? "0" : "") : pretty(rEntry.toString()));
        big.setText(sb.toString().replaceAll("\n$", ""));
    }

    void rpnPush() {
        if (rEntry.length() > 0) {
            try { stack.add(Double.parseDouble(rEntry.toString())); } catch (Exception e) { }
            rEntry.setLength(0);
        }
    }

    void rpnPress(String k) {
        switch (k) {
            case "AC": stack.clear(); rEntry.setLength(0); break;
            case "\u232B": if (rEntry.length() > 0) rEntry.setLength(rEntry.length() - 1); else if (!stack.isEmpty()) stack.remove(stack.size() - 1); break;
            case "Enter": if (rEntry.length() > 0) rpnPush(); else if (!stack.isEmpty()) stack.add(stack.get(stack.size() - 1)); break;
            case "+/-":
                if (rEntry.length() > 0) { if (rEntry.charAt(0) == '-') rEntry.deleteCharAt(0); else rEntry.insert(0, '-'); }
                else if (!stack.isEmpty()) stack.set(stack.size() - 1, -stack.get(stack.size() - 1));
                break;
            case "%": rpnPush(); if (!stack.isEmpty()) stack.set(stack.size() - 1, stack.get(stack.size() - 1) / 100); break;
            case "+": case M: case "\u00D7": case "\u00F7": {
                rpnPush();
                if (stack.size() >= 2) {
                    double y = stack.remove(stack.size() - 1), x = stack.remove(stack.size() - 1);
                    stack.add(k.equals("+") ? x + y : k.equals(M) ? x - y : k.equals("\u00D7") ? x * y : x / y);
                }
                break;
            }
            default:
                if (k.equals(".") && rEntry.indexOf(".") >= 0) break;
                if (rEntry.length() < 15) rEntry.append(k);
        }
        rpnShow();
    }

    // ================= expression evaluator =================
    static class Ev {
        final String s;
        int i = 0;
        final boolean deg;
        Ev(String s, boolean deg) { this.s = s; this.deg = deg; }

        double parse() throws Exception {
            double v = expr();
            skip();
            if (i < s.length()) throw new Exception("syntax");
            return v;
        }
        void skip() { while (i < s.length() && s.charAt(i) == ' ') i++; }
        boolean eat(String t) { skip(); if (s.startsWith(t, i)) { i += t.length(); return true; } return false; }
        boolean startsPrimary() {
            skip();
            if (i >= s.length()) return false;
            char c = s.charAt(i);
            return Character.isDigit(c) || c == '.' || c == '\u03C0' || c == '(' || (c >= 'a' && c <= 'z' && !s.startsWith("log_", i));
        }
        double expr() throws Exception {
            double v = term();
            for (;;) {
                if (eat("+")) v += term();
                else if (eat(M) || eat("-")) v -= term();
                else return v;
            }
        }
        double term() throws Exception {
            double v = power();
            for (;;) {
                if (eat("\u00D7") || eat("*")) v *= power();
                else if (eat("\u00F7") || eat("/")) v /= power();
                else if (startsPrimary()) v *= power();
                else return v;
            }
        }
        double power() throws Exception {
            double b = unary();
            if (eat("^")) return Math.pow(b, power());
            if (eat("\u02B8\u221A")) return Math.pow(b, 1.0 / power());
            if (eat("log_")) return Math.log(b) / Math.log(power());
            return b;
        }
        double unary() throws Exception {
            if (eat(M) || eat("-")) return -unary();
            if (eat("+")) return unary();
            return postfix();
        }
        double postfix() throws Exception {
            double v = primary();
            for (;;) {
                if (eat("!")) v = fact(v);
                else if (eat("%")) v /= 100;
                else return v;
            }
        }
        double fact(double v) throws Exception {
            if (v < 0 || v != Math.floor(v) || v > 170) throw new Exception("fact");
            double r = 1;
            for (int k = 2; k <= (int) v; k++) r *= k;
            return r;
        }
        double primary() throws Exception {
            skip();
            if (i >= s.length()) throw new Exception("eof");
            char c = s.charAt(i);
            if (c == '(') { i++; double v = expr(); eat(")"); return v; }
            if (c == '\u03C0') { i++; return Math.PI; }
            if (Character.isDigit(c) || c == '.') {
                int st = i;
                while (i < s.length() && (Character.isDigit(s.charAt(i)) || s.charAt(i) == '.')) i++;
                if (i < s.length() && s.charAt(i) == 'E') {
                    int j = i + 1;
                    if (j < s.length() && (s.charAt(j) == '+' || s.charAt(j) == '-' || s.charAt(j) == M.charAt(0))) j++;
                    if (j < s.length() && Character.isDigit(s.charAt(j))) {
                        while (j < s.length() && Character.isDigit(s.charAt(j))) j++;
                        String num = s.substring(st, j).replace(M, "-");
                        i = j;
                        return Double.parseDouble(num);
                    }
                }
                return Double.parseDouble(s.substring(st, i));
            }
            if (c == 'e' && !(i + 1 < s.length() && Character.isLetter(s.charAt(i + 1)))) { i++; return Math.E; }
            if (c >= 'a' && c <= 'z') {
                int st = i;
                while (i < s.length() && (Character.isLetterOrDigit(s.charAt(i)))) i++;
                String name = s.substring(st, i);
                if (!eat("(")) throw new Exception("fn");
                double x = expr();
                eat(")");
                return fn(name, x);
            }
            throw new Exception("syntax");
        }
        double fn(String n, double x) throws Exception {
            double r = deg ? Math.toRadians(x) : x;
            switch (n) {
                case "sin": return Math.sin(r);
                case "cos": return Math.cos(r);
                case "tan": return Math.tan(r);
                case "asin": return deg ? Math.toDegrees(Math.asin(x)) : Math.asin(x);
                case "acos": return deg ? Math.toDegrees(Math.acos(x)) : Math.acos(x);
                case "atan": return deg ? Math.toDegrees(Math.atan(x)) : Math.atan(x);
                case "sinh": return Math.sinh(x);
                case "cosh": return Math.cosh(x);
                case "tanh": return Math.tanh(x);
                case "asinh": return Math.log(x + Math.sqrt(x * x + 1));
                case "acosh": return Math.log(x + Math.sqrt(x * x - 1));
                case "atanh": return 0.5 * Math.log((1 + x) / (1 - x));
                case "ln": return Math.log(x);
                case "log10": return Math.log10(x);
                case "log2": return Math.log(x) / Math.log(2);
                case "sqrt": return Math.sqrt(x);
                case "cbrt": return Math.cbrt(x);
            }
            throw new Exception("fn");
        }
    }
}
