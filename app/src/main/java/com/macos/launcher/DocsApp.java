package com.macos.launcher;

import android.content.ContentValues;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.pdf.PdfDocument;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import android.text.Editable;
import android.text.InputType;
import android.text.Layout;
import android.text.Spannable;
import android.text.Spanned;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.text.TextWatcher;
import android.text.style.AlignmentSpan;
import android.text.style.BackgroundColorSpan;
import android.text.style.ForegroundColorSpan;
import android.text.style.RelativeSizeSpan;
import android.text.style.StrikethroughSpan;
import android.text.style.StyleSpan;
import android.text.style.UnderlineSpan;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import org.json.JSONArray;
import org.json.JSONObject;

/** Docs: rich-text word processor (bold, italic, underline, strike, headings, colours, alignment, bullets) with PDF export. */
public class DocsApp {
    final MainActivity a;
    final File dir;
    File curFile;
    EditText ed;
    LinearLayout list;
    TextView count;
    boolean binding;

    public static void open(MainActivity a) { new DocsApp(a); }

    DocsApp(MainActivity act) {
        a = act;
        dir = new File(a.getFilesDir(), "docs");
        dir.mkdirs();
        TextView add = Ui.sfBtn(a, "square.and.pencil", 14), del = Ui.sfBtn(a, "trash", 14);
        add.setOnClickListener(v -> newDoc());
        del.setOnClickListener(v -> { if (curFile != null) a.confirm("Delete this document?", () -> { curFile.delete(); curFile = null; File[] f = files(); if (f.length == 0) newDoc(); else load(f[0]); drawList(); }); });
        TextView pdf = Ui.tv(a, "Export PDF", 12.5f, Color.WHITE, true);
        pdf.setGravity(Gravity.CENTER);
        pdf.setBackground(Ui.rr(Theme.accent, a.dp(15)));
        pdf.setOnClickListener(v -> exportPdf());
        LinearLayout tb = new LinearLayout(a);
        tb.setGravity(Gravity.CENTER_VERTICAL);
        tb.addView(new View(a), new LinearLayout.LayoutParams(0, 1, 1f));
        tb.addView(del, new LinearLayout.LayoutParams(a.dp(30), a.dp(30)));
        LinearLayout.LayoutParams al = new LinearLayout.LayoutParams(a.dp(30), a.dp(30));
        al.leftMargin = a.dp(6);
        tb.addView(add, al);
        LinearLayout.LayoutParams pl = new LinearLayout.LayoutParams(a.dp(84), a.dp(30));
        pl.leftMargin = a.dp(8);
        tb.addView(pdf, pl);

        LinearLayout body = new LinearLayout(a);
        LinearLayout left = new LinearLayout(a);
        left.setBackgroundColor(Theme.side());
        list = new LinearLayout(a);
        list.setOrientation(LinearLayout.VERTICAL);
        ScrollView sv = new ScrollView(a);
        sv.setVerticalScrollBarEnabled(false);
        sv.addView(list);
        left.addView(sv, new LinearLayout.LayoutParams(-1, -1));
        body.addView(left, new LinearLayout.LayoutParams(a.dp(170), -1));

        LinearLayout right = new LinearLayout(a);
        right.setOrientation(LinearLayout.VERTICAL);
        HorizontalScrollView hs = new HorizontalScrollView(a);
        hs.setHorizontalScrollBarEnabled(false);
        LinearLayout tools = new LinearLayout(a);
        tools.setGravity(Gravity.CENTER_VERTICAL);
        tools.setPadding(a.dp(8), a.dp(6), a.dp(8), a.dp(6));
        tool(tools, "B", Typeface.BOLD, () -> style(Typeface.BOLD));
        tool(tools, "I", Typeface.ITALIC, () -> style(Typeface.ITALIC));
        tool(tools, "U", 0, () -> toggle(UnderlineSpan.class, new UnderlineSpan()));
        tool(tools, "S", 0, () -> toggle(StrikethroughSpan.class, new StrikethroughSpan()));
        tool(tools, "H1", 0, () -> size(1.7f));
        tool(tools, "H2", 0, () -> size(1.35f));
        tool(tools, "Aa", 0, () -> size(1f));
        tool(tools, "\u2630", 0, () -> align(Layout.Alignment.ALIGN_NORMAL));
        tool(tools, "\u2261", 0, () -> align(Layout.Alignment.ALIGN_CENTER));
        tool(tools, "\u2630\u200B", 0, () -> align(Layout.Alignment.ALIGN_OPPOSITE));
        tool(tools, "\u2022", 0, this::bullet);
        int[] cols = {0xFF000000, 0xFFFF3B30, 0xFFFF9F0A, 0xFF30D158, 0xFF0A84FF, 0xFFBF5AF2};
        for (final int c : cols) swatch(tools, c, false);
        for (final int c : new int[]{0xFFFFF59D, 0xFFB9F6CA, 0xFFB3E5FC}) swatch(tools, c, true);
        hs.addView(tools);
        right.addView(hs, new LinearLayout.LayoutParams(-1, -2));
        right.addView(Ui.sepLine(a), new LinearLayout.LayoutParams(-1, 1));
        ed = new EditText(a);
        ed.setBackgroundColor(Theme.dark ? 0xFF1C1C1E : 0xFFFFFFFF);
        ed.setTextColor(Theme.dark ? 0xFFF2F2F7 : 0xFF111111);
        ed.setHintTextColor(Theme.sub());
        ed.setHint("Start typing\u2026");
        ed.setTextSize(15);
        ed.setLineSpacing(0, 1.12f);
        ed.setGravity(Gravity.TOP | Gravity.START);
        ed.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        ed.setPadding(a.dp(26), a.dp(16), a.dp(26), a.dp(16));
        Emoji.apply(ed);
        ed.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int x, int y, int z) {}
            public void onTextChanged(CharSequence s, int x, int y, int z) {}
            public void afterTextChanged(Editable e) { if (!binding) { save(); updateCount(); } }
        });
        ScrollView es = new ScrollView(a);
        es.setFillViewport(true);
        es.addView(ed, new android.widget.FrameLayout.LayoutParams(-1, -2));
        right.addView(es, new LinearLayout.LayoutParams(-1, 0, 1f));
        count = Ui.tv(a, "0 words", 11, Theme.sub(), false);
        count.setPadding(a.dp(14), a.dp(3), 0, a.dp(4));
        right.addView(count);
        body.addView(right, new LinearLayout.LayoutParams(0, -1, 1f));
        a.openWindow(new MacWindow(a, "Feng Docs", tb, null, body), 760, 360);
        File[] f = files();
        if (f.length == 0) newDoc(); else load(f[0]);
        drawList();
    }

    // ---------- toolbar ----------
    void tool(LinearLayout p, String s, int style, final Runnable r) {
        TextView t = Ui.tv(a, s, 14, Theme.fg(), false);
        t.setTypeface(Typeface.defaultFromStyle(style));
        t.setGravity(Gravity.CENTER);
        t.setBackground(Ui.rr(Theme.dark ? 0x26FFFFFF : 0x0F000000, a.dp(8)));
        t.setOnClickListener(v -> r.run());
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(a.dp(34), a.dp(30));
        lp.rightMargin = a.dp(4);
        p.addView(t, lp);
    }

    void swatch(LinearLayout p, final int color, final boolean bg) {
        View v = new View(a);
        android.graphics.drawable.GradientDrawable g = new android.graphics.drawable.GradientDrawable();
        g.setShape(bg ? android.graphics.drawable.GradientDrawable.RECTANGLE : android.graphics.drawable.GradientDrawable.OVAL);
        g.setCornerRadius(a.dp(5));
        g.setColor(color);
        g.setStroke(1, Theme.sep());
        v.setBackground(g);
        v.setOnClickListener(x -> {
            int s = ed.getSelectionStart(), e = ed.getSelectionEnd();
            if (s == e) return;
            Editable t = ed.getText();
            Class<?> k = bg ? BackgroundColorSpan.class : ForegroundColorSpan.class;
            for (Object o : t.getSpans(s, e, k)) t.removeSpan(o);
            t.setSpan(bg ? new BackgroundColorSpan(color) : new ForegroundColorSpan(color), s, e, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            save();
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(a.dp(22), a.dp(22));
        lp.setMargins(a.dp(4), 0, a.dp(2), 0);
        p.addView(v, lp);
    }

    boolean sel() {
        if (ed.getSelectionStart() == ed.getSelectionEnd()) { Toast.makeText(a, "Select some text first", Toast.LENGTH_SHORT).show(); return false; }
        return true;
    }

    void style(int st) {
        if (!sel()) return;
        int s = ed.getSelectionStart(), e = ed.getSelectionEnd();
        Editable t = ed.getText();
        boolean has = false;
        for (StyleSpan sp : t.getSpans(s, e, StyleSpan.class)) if (sp.getStyle() == st && t.getSpanStart(sp) <= s && t.getSpanEnd(sp) >= e) has = true;
        if (has) { for (StyleSpan sp : t.getSpans(s, e, StyleSpan.class)) if (sp.getStyle() == st) t.removeSpan(sp); }
        else t.setSpan(new StyleSpan(st), s, e, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        save();
    }

    <T> void toggle(Class<T> k, Object span) {
        if (!sel()) return;
        int s = ed.getSelectionStart(), e = ed.getSelectionEnd();
        Editable t = ed.getText();
        T[] ex = t.getSpans(s, e, k);
        if (ex.length > 0) for (T o : ex) t.removeSpan(o);
        else t.setSpan(span, s, e, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        save();
    }

    void size(float f) {
        if (!sel()) return;
        int s = ed.getSelectionStart(), e = ed.getSelectionEnd();
        Editable t = ed.getText();
        for (RelativeSizeSpan sp : t.getSpans(s, e, RelativeSizeSpan.class)) t.removeSpan(sp);
        if (f != 1f) t.setSpan(new RelativeSizeSpan(f), s, e, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        save();
    }

    int[] para() {
        Editable t = ed.getText();
        int s = ed.getSelectionStart(), e = ed.getSelectionEnd();
        String x = t.toString();
        int ps = s > 0 ? x.lastIndexOf('\n', s - 1) + 1 : 0;
        int pe = x.indexOf('\n', e);
        if (pe < 0) pe = x.length(); else pe++;
        return new int[]{ps, Math.min(pe, x.length())};
    }

    void align(Layout.Alignment al) {
        int[] p = para();
        Editable t = ed.getText();
        if (p[1] <= p[0]) return;
        for (AlignmentSpan.Standard sp : t.getSpans(p[0], p[1], AlignmentSpan.Standard.class)) t.removeSpan(sp);
        t.setSpan(new AlignmentSpan.Standard(al), p[0], p[1], Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        save();
    }

    void bullet() {
        Editable t = ed.getText();
        int[] p = para();
        String line = t.subSequence(p[0], p[1]).toString();
        if (line.startsWith("\u2022 ")) t.delete(p[0], p[0] + 2); else t.insert(p[0], "\u2022 ");
    }

    // ---------- documents ----------
    File[] files() {
        File[] f = dir.listFiles();
        if (f == null) return new File[0];
        Arrays.sort(f, new Comparator<File>() { public int compare(File x, File y) { return Long.compare(y.lastModified(), x.lastModified()); } });
        return f;
    }

    void newDoc() {
        File f = new File(dir, "d" + System.currentTimeMillis() + ".json");
        curFile = f;
        binding = true;
        ed.setText("");
        binding = false;
        save();
        drawList();
        ed.requestFocus();
    }

    void load(File f) {
        curFile = f;
        binding = true;
        try {
            JSONObject o = new JSONObject(readAll(f));
            ed.setText(o.optString("t", ""));
            Editable t = ed.getText();
            JSONArray sp = o.optJSONArray("s");
            if (sp != null) for (int i = 0; i < sp.length(); i++) {
                JSONObject s = sp.getJSONObject(i);
                int st = s.getInt("a"), en = Math.min(s.getInt("b"), t.length());
                if (st >= en) continue;
                Object span = null;
                switch (s.getString("k")) {
                    case "B": span = new StyleSpan(Typeface.BOLD); break;
                    case "I": span = new StyleSpan(Typeface.ITALIC); break;
                    case "U": span = new UnderlineSpan(); break;
                    case "S": span = new StrikethroughSpan(); break;
                    case "Z": span = new RelativeSizeSpan((float) s.getDouble("v")); break;
                    case "C": span = new ForegroundColorSpan(s.getInt("v")); break;
                    case "H": span = new BackgroundColorSpan(s.getInt("v")); break;
                    case "A": span = new AlignmentSpan.Standard(s.getInt("v") == 1 ? Layout.Alignment.ALIGN_CENTER : s.getInt("v") == 2 ? Layout.Alignment.ALIGN_OPPOSITE : Layout.Alignment.ALIGN_NORMAL); break;
                    default: break;
                }
                if (span != null) t.setSpan(span, st, en, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
        } catch (Exception e) { ed.setText(""); }
        binding = false;
        updateCount();
    }

    String readAll(File f) throws Exception {
        try (FileInputStream in = new FileInputStream(f)) {
            byte[] b = new byte[(int) f.length()];
            int r = 0;
            while (r < b.length) { int k = in.read(b, r, b.length - r); if (k < 0) break; r += k; }
            return new String(b, 0, r, "UTF-8");
        }
    }

    void save() {
        if (curFile == null) return;
        try {
            Editable t = ed.getText();
            JSONArray sp = new JSONArray();
            for (Object o : t.getSpans(0, t.length(), Object.class)) {
                JSONObject j = new JSONObject();
                if (o instanceof StyleSpan) j.put("k", ((StyleSpan) o).getStyle() == Typeface.BOLD ? "B" : "I");
                else if (o instanceof UnderlineSpan) j.put("k", "U");
                else if (o instanceof StrikethroughSpan) j.put("k", "S");
                else if (o instanceof RelativeSizeSpan) j.put("k", "Z").put("v", ((RelativeSizeSpan) o).getSizeChange());
                else if (o instanceof ForegroundColorSpan) j.put("k", "C").put("v", ((ForegroundColorSpan) o).getForegroundColor());
                else if (o instanceof BackgroundColorSpan) j.put("k", "H").put("v", ((BackgroundColorSpan) o).getBackgroundColor());
                else if (o instanceof AlignmentSpan.Standard) { Layout.Alignment al = ((AlignmentSpan.Standard) o).getAlignment(); j.put("k", "A").put("v", al == Layout.Alignment.ALIGN_CENTER ? 1 : al == Layout.Alignment.ALIGN_OPPOSITE ? 2 : 0); }
                else continue;
                j.put("a", t.getSpanStart(o)).put("b", t.getSpanEnd(o));
                sp.put(j);
            }
            JSONObject doc = new JSONObject().put("t", t.toString()).put("s", sp);
            try (FileOutputStream out = new FileOutputStream(curFile)) { out.write(doc.toString().getBytes("UTF-8")); }
        } catch (Exception e) { }
        drawList();
    }

    void updateCount() {
        String s = ed.getText().toString().trim();
        count.setText((s.isEmpty() ? 0 : s.split("\\s+").length) + " words  \u00B7  " + s.length() + " characters");
    }

    void drawList() {
        list.removeAllViews();
        SimpleDateFormat df = new SimpleDateFormat("d MMM, HH:mm", Locale.getDefault());
        TextView h = Ui.tv(a, "Documents", 11.5f, Theme.sub(), true);
        h.setPadding(a.dp(14), a.dp(10), 0, a.dp(4));
        list.addView(h);
        for (final File f : files()) {
            String title = "Untitled";
            try { String t = new JSONObject(readAll(f)).optString("t", "").trim(); if (!t.isEmpty()) title = t.split("\n")[0]; } catch (Exception e) { }
            LinearLayout r = new LinearLayout(a);
            r.setOrientation(LinearLayout.VERTICAL);
            r.setPadding(a.dp(12), a.dp(7), a.dp(8), a.dp(7));
            if (f.equals(curFile)) r.setBackground(Ui.rr(Theme.accent & 0x00FFFFFF | 0x33000000, a.dp(9)));
            TextView t = Ui.tv(a, title, 13, Theme.fg(), true);
            t.setSingleLine(true);
            r.addView(t);
            r.addView(Ui.tv(a, df.format(new Date(f.lastModified())), 11, Theme.sub(), false));
            r.setOnClickListener(v -> { load(f); drawList(); });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
            lp.setMargins(a.dp(6), 0, a.dp(6), 0);
            list.addView(r, lp);
        }
    }

    // ---------- PDF ----------
    void exportPdf() {
        try {
            Editable t = ed.getText();
            int pw = 595, ph = 842, m = 50;
            TextPaint tp = new TextPaint(TextPaint.ANTI_ALIAS_FLAG);
            tp.setTextSize(12);
            tp.setColor(Color.BLACK);
            StaticLayout lay = StaticLayout.Builder.obtain(t, 0, t.length(), tp, pw - 2 * m).setLineSpacing(0, 1.15f).build();
            PdfDocument pdf = new PdfDocument();
            int pageH = ph - 2 * m, line = 0, n = 1;
            do {
                int top = lay.getLineTop(line), end = line;
                while (end < lay.getLineCount() && lay.getLineBottom(end) - top <= pageH) end++;
                if (end == line) end++;
                PdfDocument.Page pg = pdf.startPage(new PdfDocument.PageInfo.Builder(pw, ph, n++).create());
                android.graphics.Canvas c = pg.getCanvas();
                c.translate(m, m - top);
                c.clipRect(0, top, pw - 2 * m, lay.getLineBottom(end - 1) + 1);
                lay.draw(c);
                pdf.finishPage(pg);
                line = end;
            } while (line < lay.getLineCount());
            String name = "Feng Docs " + new SimpleDateFormat("yyyy-MM-dd HH.mm.ss", Locale.US).format(new Date()) + ".pdf";
            if (Build.VERSION.SDK_INT >= 29) {
                ContentValues v = new ContentValues();
                v.put(MediaStore.Downloads.DISPLAY_NAME, name);
                v.put(MediaStore.Downloads.MIME_TYPE, "application/pdf");
                v.put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS);
                android.net.Uri u = a.getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, v);
                try (OutputStream o = a.getContentResolver().openOutputStream(u)) { pdf.writeTo(o); }
            } else {
                File f = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), name);
                try (FileOutputStream o = new FileOutputStream(f)) { pdf.writeTo(o); }
            }
            pdf.close();
            Toast.makeText(a, "Saved to Downloads", Toast.LENGTH_SHORT).show();
        } catch (Exception e) { Toast.makeText(a, "Couldn\u2019t export the PDF", Toast.LENGTH_SHORT).show(); }
    }
}
