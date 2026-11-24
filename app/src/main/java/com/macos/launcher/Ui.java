package com.macos.launcher;

import android.content.Context;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.TextView;

public class Ui {
    public static int dp(Context c, float v) { return Math.round(v * c.getResources().getDisplayMetrics().density); }

    public static GradientDrawable rr(int color, float r) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(r);
        return g;
    }

    public static TextView tv(Context c, String s, float sp, int color, boolean bold) {
        TextView t = new TextView(c);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        t.setTypeface(Emoji.tf(c, bold));
        return t;
    }

    public static TextView circleBtn(Context c, String glyph) {
        TextView t = tv(c, glyph, 17, Theme.fg(), false);
        t.setGravity(Gravity.CENTER);
        GradientDrawable g = new GradientDrawable();
        g.setShape(GradientDrawable.OVAL);
        g.setColor(Theme.dark ? 0x2EFFFFFF : 0x14000000);
        t.setBackground(g);
        String sym = "\u2039".equals(glyph) || "\u2018".equals(glyph) ? "chevron.left" : "\u203A".equals(glyph) ? "chevron.right" : "+".equals(glyph) ? "plus" : "\u2212".equals(glyph) ? "minus" : "\u27F3".equals(glyph) ? "arrow.clockwise" : "\u2191".equals(glyph) ? "square.and.arrow.up" : null;
        if (sym != null) {
            int box = dp(c, "chevron.left".equals(sym) || "chevron.right".equals(sym) ? 14 : 13);
            android.graphics.drawable.Drawable d = Sf.sized(c, sym, Theme.fg(), box);
            if (d != null) { SfText st = new SfText(c, d); st.setBackground(g); return st; }
        }
        return t;
    }

    public static android.widget.FrameLayout iconBtn(Context c, int type) {
        android.widget.FrameLayout f = new android.widget.FrameLayout(c);
        GradientDrawable g = new GradientDrawable();
        g.setShape(GradientDrawable.OVAL);
        g.setColor(Theme.dark ? 0x2EFFFFFF : 0x14000000);
        f.setBackground(g);
        f.addView(new IconView(c, type, Theme.fg()), new android.widget.FrameLayout.LayoutParams(dp(c, 16), dp(c, 16), Gravity.CENTER));
        return f;
    }

    /** Circle toolbar button with a centred SF Symbol (box = longest symbol side in dp). */
    public static TextView sfBtn(Context c, String sym, float boxDp) {
        android.graphics.drawable.Drawable d = Sf.sized(c, sym, Theme.fg(), dp(c, boxDp));
        GradientDrawable g = new GradientDrawable();
        g.setShape(GradientDrawable.OVAL);
        g.setColor(Theme.dark ? 0x2EFFFFFF : 0x14000000);
        if (d == null) { TextView t = tv(c, "?", 14, Theme.fg(), false); t.setGravity(Gravity.CENTER); t.setBackground(g); return t; }
        SfText st = new SfText(c, d);
        st.setBackground(g);
        return st;
    }

    public static View sepLine(Context c) {
        View v = new View(c);
        v.setBackgroundColor(Theme.sep());
        return v;
    }

    /** Circle button that draws a centred SF Symbol. */
    public static class SfText extends TextView {
        android.graphics.drawable.Drawable d;
        public SfText(Context c, android.graphics.drawable.Drawable d) { super(c); this.d = d; setGravity(Gravity.CENTER); }
        public void setSymbol(android.graphics.drawable.Drawable n) { if (n != null) { d = n; invalidate(); } }
        @Override protected void onDraw(Canvas cv) {
            super.onDraw(cv);
            int w = d.getBounds().width(), h = d.getBounds().height();
            int l = (getWidth() - w) / 2, t = (getHeight() - h) / 2;
            cv.save(); cv.translate(l, t); d.draw(cv); cv.restore();
        }
    }

    public static class FolderIcon extends View {
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        public FolderIcon(Context c) { super(c); }
        @Override protected void onDraw(Canvas c) {
            float w = getWidth(), h = getHeight();
            p.setColor(0xFF3A9CF0);
            c.drawRoundRect(new RectF(.04f * w, .1f * h, .42f * w, .32f * h), .08f * w, .08f * w, p);
            c.drawRoundRect(new RectF(.04f * w, .2f * h, .96f * w, .86f * h), .1f * w, .1f * w, p);
            p.setShader(new LinearGradient(0, .26f * h, 0, .88f * h, 0xFF6FCDFB, 0xFF2B98F3, Shader.TileMode.CLAMP));
            c.drawRoundRect(new RectF(.04f * w, .28f * h, .96f * w, .88f * h), .1f * w, .1f * w, p);
            p.setShader(null);
        }
    }

    public static class FileIcon extends View {
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final String ext;
        public FileIcon(Context c, String ext) { super(c); this.ext = ext.length() > 4 ? ext.substring(0, 4) : ext; }
        private int color() {
            switch (ext.toLowerCase()) {
                case "jpg": case "jpeg": case "png": case "webp": case "gif": return 0xFF30B65A;
                case "mp4": case "mkv": case "mov": case "avi": return 0xFF8E5CF0;
                case "mp3": case "wav": case "m4a": case "flac": case "ogg": return 0xFFF0455A;
                case "pdf": return 0xFFE5453B;
                case "apk": return 0xFF3DDC84;
                case "zip": case "rar": case "7z": return 0xFFF0A020;
                default: return 0xFF3A8EF0;
            }
        }
        @Override protected void onDraw(Canvas c) {
            float w = getWidth(), h = getHeight();
            p.setStyle(Paint.Style.FILL);
            p.setColor(0xFFFFFFFF);
            c.drawRoundRect(new RectF(.2f * w, .06f * h, .8f * w, .94f * h), .07f * w, .07f * w, p);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(Math.max(1f, w * .018f));
            p.setColor(0x33000000);
            c.drawRoundRect(new RectF(.2f * w, .06f * h, .8f * w, .94f * h), .07f * w, .07f * w, p);
            p.setStyle(Paint.Style.FILL);
            p.setColor(color());
            p.setTextAlign(Paint.Align.CENTER);
            p.setTypeface(Emoji.font(true));
            p.setTextSize(w * .17f);
            c.drawText(ext.toUpperCase(), w / 2f, h * .6f, p);
            c.drawRoundRect(new RectF(.32f * w, .72f * h, .68f * w, .76f * h), 4, 4, p);
            c.drawRoundRect(new RectF(.32f * w, .8f * h, .56f * w, .84f * h), 4, 4, p);
        }
    }
}
