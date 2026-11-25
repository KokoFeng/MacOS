package com.macos.launcher;

import android.content.ContentValues;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;
import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/** Design: poster / social post editor with text, shapes, images, templates, layers and export. All tools are free. */
public class DesignApp {
    static class Layer {
        String type, text = "";
        float x, y, w, h, rot, size = 80;
        int color = 0xFF0A84FF, alpha = 255;
        boolean bold = true, italic;
        Bitmap bmp;
    }

    static final String[][] SIZES = {{"Instagram Post", "1080", "1080"}, {"Story", "1080", "1920"}, {"Poster A4", "1240", "1754"}, {"Thumbnail", "1280", "720"}, {"Presentation", "1920", "1080"}};
    static final String[] TPL = {"Quote", "Big Sale", "Birthday", "Announcement", "Story Intro", "Thumbnail"};
    static final int[] COLORS = {0xFFFFFFFF, 0xFF000000, 0xFFFF3B30, 0xFFFF9F0A, 0xFFFFD60A, 0xFF30D158, 0xFF0A84FF, 0xFF5E5CE6, 0xFFBF5AF2, 0xFFFF2D92, 0xFF8E8E93, 0xFF1C1C1E};

    final MainActivity a;
    final List<Layer> layers = new ArrayList<>();
    Layer sel;
    int dw = 1080, dh = 1080, bg1 = 0xFFFFFFFF, bg2 = 0xFFFFFFFF;
    String tab = "Design";
    DView dv;
    LinearLayout panel, tabs;

    public static void open(MainActivity a) { new DesignApp(a); }

    DesignApp(MainActivity act) {
        a = act;
        TextView exp = chip("Save PNG", true);
        exp.setOnClickListener(v -> export());
        LinearLayout tb = new LinearLayout(a);
        tb.setGravity(Gravity.CENTER_VERTICAL);
        tb.addView(new View(a), new LinearLayout.LayoutParams(0, 1, 1f));
        tb.addView(exp, new LinearLayout.LayoutParams(a.dp(80), a.dp(30)));
        LinearLayout body = new LinearLayout(a);
        FrameLayout stage = new FrameLayout(a);
        stage.setBackgroundColor(Theme.dark ? 0xFF111113 : 0xFFE9E9EE);
        dv = new DView();
        stage.addView(dv, new FrameLayout.LayoutParams(-1, -1));
        body.addView(stage, new LinearLayout.LayoutParams(0, -1, 1f));
        LinearLayout side = new LinearLayout(a);
        side.setOrientation(LinearLayout.VERTICAL);
        side.setBackgroundColor(Theme.side());
        tabs = new LinearLayout(a);
        tabs.setPadding(a.dp(6), a.dp(6), a.dp(6), a.dp(6));
        for (final String t : new String[]{"Design", "Add", "Style"}) {
            TextView c = chip(t, t.equals(tab));
            c.setOnClickListener(v -> { tab = t; drawTabs(); drawPanel(); });
            tabs.addView(c, new LinearLayout.LayoutParams(0, a.dp(28), 1f));
        }
        side.addView(tabs, new LinearLayout.LayoutParams(-1, -2));
        panel = new LinearLayout(a);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(a.dp(12), a.dp(4), a.dp(12), a.dp(10));
        ScrollView sv = new ScrollView(a);
        sv.setVerticalScrollBarEnabled(false);
        sv.addView(panel);
        side.addView(sv, new LinearLayout.LayoutParams(-1, 0, 1f));
        body.addView(side, new LinearLayout.LayoutParams(a.dp(214), -1));
        a.openWindow(new MacWindow(a, "Feng Design", tb, null, body), 780, 360);
        template(0);
        drawPanel();
    }

    TextView chip(String s, boolean on) {
        TextView t = Ui.tv(a, s, 12, on ? Color.WHITE : Theme.fg(), on);
        t.setGravity(Gravity.CENTER);
        t.setBackground(Ui.rr(on ? Theme.accent : (Theme.dark ? 0x2EFFFFFF : 0x14000000), a.dp(14)));
        return t;
    }

    void drawTabs() {
        String[] n = {"Design", "Add", "Style"};
        for (int i = 0; i < tabs.getChildCount(); i++) {
            TextView c = (TextView) tabs.getChildAt(i);
            boolean on = n[i].equals(tab);
            c.setTextColor(on ? Color.WHITE : Theme.fg());
            c.setBackground(Ui.rr(on ? Theme.accent : (Theme.dark ? 0x2EFFFFFF : 0x14000000), a.dp(14)));
        }
    }

    // ---------- panels ----------
    TextView btn(String s, Runnable r) {
        TextView t = chip(s, false);
        t.setOnClickListener(v -> r.run());
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, a.dp(30));
        lp.topMargin = a.dp(5);
        t.setLayoutParams(lp);
        panel.addView(t);
        return t;
    }

    TextView label(String s) {
        TextView t = Ui.tv(a, s, 11.5f, Theme.sub(), true);
        t.setPadding(0, a.dp(10), 0, a.dp(2));
        panel.addView(t);
        return t;
    }

    void swatches(final PhotoApp.IntCb cb) {
        LinearLayout row = new LinearLayout(a);
        row.setPadding(0, a.dp(4), 0, 0);
        LinearLayout row2 = new LinearLayout(a);
        row2.setPadding(0, a.dp(4), 0, 0);
        for (int i = 0; i < COLORS.length; i++) {
            final int c = COLORS[i];
            View v = new View(a);
            android.graphics.drawable.GradientDrawable g = new android.graphics.drawable.GradientDrawable();
            g.setShape(android.graphics.drawable.GradientDrawable.OVAL);
            g.setColor(c);
            g.setStroke(1, 0x55888888);
            v.setBackground(g);
            v.setOnClickListener(x -> cb.on(c));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(a.dp(22), a.dp(22));
            lp.rightMargin = a.dp(4);
            (i < 6 ? row : row2).addView(v, lp);
        }
        panel.addView(row);
        panel.addView(row2);
    }

    void slider(String name, int min, int max, int val, final PhotoApp.IntCb cb) {
        label(name);
        SeekBar s = new SeekBar(a);
        s.setMax(max - min);
        s.setProgress(val - min);
        final int mn = min;
        s.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar sb, int p, boolean u) { if (u) cb.on(p + mn); }
            public void onStartTrackingTouch(SeekBar sb) {}
            public void onStopTrackingTouch(SeekBar sb) {}
        });
        panel.addView(s);
    }

    void drawPanel() {
        panel.removeAllViews();
        if (tab.equals("Design")) {
            label("Size");
            for (final String[] s : SIZES) btn(s[0] + "  " + s[1] + "\u00D7" + s[2], () -> { dw = Integer.parseInt(s[1]); dh = Integer.parseInt(s[2]); dv.invalidate(); });
            label("Background");
            swatches(c -> { bg1 = c; bg2 = c; dv.invalidate(); });
            btn("Gradient: Ocean", () -> { bg1 = 0xFF0A84FF; bg2 = 0xFF5E5CE6; dv.invalidate(); });
            btn("Gradient: Sunset", () -> { bg1 = 0xFFFF9F0A; bg2 = 0xFFFF2D92; dv.invalidate(); });
            label("Templates");
            for (int i = 0; i < TPL.length; i++) { final int k = i; btn(TPL[i], () -> { template(k); drawPanel(); }); }
        } else if (tab.equals("Add")) {
            btn("Text", () -> addText("Your text"));
            btn("Rectangle", () -> addShape("RECT"));
            btn("Rounded rectangle", () -> addShape("ROUND"));
            btn("Circle", () -> addShape("CIRCLE"));
            btn("Triangle", () -> addShape("TRI"));
            btn("Star", () -> addShape("STAR"));
            btn("Line", () -> addShape("LINE"));
            btn("Image from gallery", () -> a.pick("image/*", false, us -> addImage(us.get(0))));
        } else {
            if (sel == null) { label("Tap a layer on the canvas to edit it."); return; }
            label("Colour");
            swatches(c -> { sel.color = c; dv.invalidate(); });
            slider("Opacity", 10, 255, sel.alpha, v -> { sel.alpha = v; dv.invalidate(); });
            if (sel.type.equals("TEXT")) {
                slider("Text size", 20, 400, (int) sel.size, v -> { sel.size = v; measure(sel); dv.invalidate(); });
                btn("Edit text", () -> a.prompt("Text", sel.text, s -> { sel.text = s; measure(sel); dv.invalidate(); }));
                btn(sel.bold ? "Bold: on" : "Bold: off", () -> { sel.bold = !sel.bold; measure(sel); dv.invalidate(); drawPanel(); });
                btn(sel.italic ? "Italic: on" : "Italic: off", () -> { sel.italic = !sel.italic; dv.invalidate(); drawPanel(); });
            }
            label("Arrange");
            btn("Bring to front", () -> { layers.remove(sel); layers.add(sel); dv.invalidate(); });
            btn("Send to back", () -> { layers.remove(sel); layers.add(0, sel); dv.invalidate(); });
            btn("Duplicate", () -> { Layer c = copy(sel); c.x += 40; c.y += 40; layers.add(c); sel = c; dv.invalidate(); });
            btn("Delete", () -> { layers.remove(sel); sel = null; dv.invalidate(); drawPanel(); });
        }
    }

    // ---------- layers ----------
    Layer copy(Layer l) {
        Layer c = new Layer();
        c.type = l.type; c.text = l.text; c.x = l.x; c.y = l.y; c.w = l.w; c.h = l.h; c.rot = l.rot; c.size = l.size; c.color = l.color; c.alpha = l.alpha; c.bold = l.bold; c.italic = l.italic; c.bmp = l.bmp;
        return c;
    }

    void addText(String t) {
        Layer l = new Layer();
        l.type = "TEXT"; l.text = t; l.x = dw / 2f; l.y = dh / 2f; l.size = dw * .08f; l.color = 0xFF111111;
        measure(l);
        layers.add(l);
        select(l);
    }

    void addShape(String t) {
        Layer l = new Layer();
        l.type = t; l.x = dw / 2f; l.y = dh / 2f; l.w = dw * .3f; l.h = t.equals("LINE") ? dw * .012f : dw * .3f;
        if (t.equals("LINE")) l.w = dw * .5f;
        layers.add(l);
        select(l);
    }

    void addImage(Uri u) {
        try {
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inJustDecodeBounds = true;
            try (InputStream in = a.getContentResolver().openInputStream(u)) { BitmapFactory.decodeStream(in, null, o); }
            int s = 1;
            while (Math.max(o.outWidth, o.outHeight) / s > 1600) s *= 2;
            o = new BitmapFactory.Options();
            o.inSampleSize = s;
            Bitmap b;
            try (InputStream in = a.getContentResolver().openInputStream(u)) { b = BitmapFactory.decodeStream(in, null, o); }
            Layer l = new Layer();
            l.type = "IMAGE"; l.bmp = b; l.x = dw / 2f; l.y = dh / 2f;
            float k = Math.min(dw * .6f / b.getWidth(), dh * .6f / b.getHeight());
            l.w = b.getWidth() * k; l.h = b.getHeight() * k;
            layers.add(l);
            select(l);
        } catch (Exception e) { Toast.makeText(a, "Couldn\u2019t open that image", Toast.LENGTH_SHORT).show(); }
    }

    void select(Layer l) {
        sel = l;
        dv.invalidate();
        tab = "Style";
        drawTabs();
        drawPanel();
    }

    final Paint mp = new Paint(Paint.ANTI_ALIAS_FLAG);

    void measure(Layer l) {
        mp.setTextSize(l.size);
        mp.setTypeface(Emoji.font(l.bold));
        String[] ln = l.text.split("\n", -1);
        float w = 10;
        for (String s : ln) w = Math.max(w, mp.measureText(s));
        l.w = w + l.size * .2f;
        l.h = ln.length * l.size * 1.2f;
    }

    void template(int k) {
        layers.clear();
        sel = null;
        float W = dw, H = dh;
        switch (k) {
            case 0:
                bg1 = 0xFF1C1C3A; bg2 = 0xFF5E5CE6;
                tl("\u201CThe best way to predict\nthe future is to build it.\u201D", W / 2, H * .45f, W * .065f, 0xFFFFFFFF, true);
                tl("\u2014 Your Name", W / 2, H * .72f, W * .04f, 0xCCFFFFFF, false);
                break;
            case 1:
                bg1 = 0xFFFFD60A; bg2 = 0xFFFFD60A;
                sh("CIRCLE", W / 2, H * .42f, W * .62f, W * .62f, 0xFFFF3B30);
                tl("SALE", W / 2, H * .36f, W * .2f, 0xFFFFFFFF, true);
                tl("50% OFF", W / 2, H * .53f, W * .1f, 0xFFFFFFFF, true);
                sh("ROUND", W / 2, H * .86f, W * .5f, H * .1f, 0xFF111111);
                tl("Shop now", W / 2, H * .86f, W * .05f, 0xFFFFFFFF, true);
                break;
            case 2:
                bg1 = 0xFFFF9ECD; bg2 = 0xFFFFD6A5;
                sh("CIRCLE", W * .15f, H * .2f, W * .12f, W * .12f, 0xFFFFFFFF);
                sh("CIRCLE", W * .85f, H * .3f, W * .09f, W * .09f, 0xFFFFD60A);
                sh("STAR", W * .8f, H * .8f, W * .15f, W * .15f, 0xFFFFFFFF);
                sh("STAR", W * .2f, H * .75f, W * .1f, W * .1f, 0xFF5E5CE6);
                tl("Happy\nBirthday!", W / 2, H * .5f, W * .14f, 0xFFFFFFFF, true);
                break;
            case 3:
                bg1 = 0xFFFFFFFF; bg2 = 0xFFFFFFFF;
                sh("RECT", W / 2, H * .17f, W, H * .34f, 0xFF0A84FF);
                tl("Big Announcement", W / 2, H * .17f, W * .08f, 0xFFFFFFFF, true);
                tl("Add the details of your event here.\nDate \u00B7 Time \u00B7 Place", W / 2, H * .55f, W * .045f, 0xFF333333, false);
                break;
            case 4:
                bg1 = 0xFF000000; bg2 = 0xFF2A2A72;
                tl("Hello,\nworld", W / 2, H * .5f, W * .16f, 0xFFFFFFFF, true);
                break;
            default:
                bg1 = 0xFFE5333B; bg2 = 0xFF111111;
                tl("YOUR TITLE\nGOES HERE", W / 2, H * .5f, W * .12f, 0xFFFFFFFF, true);
        }
        if (dv != null) dv.invalidate();
    }

    void tl(String t, float x, float y, float size, int color, boolean bold) {
        Layer l = new Layer();
        l.type = "TEXT"; l.text = t; l.x = x; l.y = y; l.size = size; l.color = color; l.bold = bold;
        measure(l);
        layers.add(l);
    }

    void sh(String t, float x, float y, float w, float h, int color) {
        Layer l = new Layer();
        l.type = t; l.x = x; l.y = y; l.w = w; l.h = h; l.color = color;
        layers.add(l);
    }

    // ---------- rendering ----------
    void render(Canvas c) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        p.setShader(new LinearGradient(0, 0, dw * .4f, dh, bg1, bg2, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, dw, dh, p);
        p.setShader(null);
        for (Layer l : layers) drawLayer(c, l, p);
    }

    void drawLayer(Canvas c, Layer l, Paint p) {
        c.save();
        c.translate(l.x, l.y);
        c.rotate(l.rot);
        p.setColor(l.color);
        p.setAlpha(l.alpha);
        p.setStyle(Paint.Style.FILL);
        float hw = l.w / 2f, hh = l.h / 2f;
        switch (l.type) {
            case "RECT": case "LINE": c.drawRect(-hw, -hh, hw, hh, p); break;
            case "ROUND": c.drawRoundRect(new RectF(-hw, -hh, hw, hh), Math.min(l.w, l.h) * .2f, Math.min(l.w, l.h) * .2f, p); break;
            case "CIRCLE": c.drawOval(new RectF(-hw, -hh, hw, hh), p); break;
            case "TRI": { Path t = new Path(); t.moveTo(0, -hh); t.lineTo(hw, hh); t.lineTo(-hw, hh); t.close(); c.drawPath(t, p); break; }
            case "STAR": {
                Path t = new Path();
                for (int i = 0; i < 10; i++) {
                    double ang = -Math.PI / 2 + i * Math.PI / 5;
                    float r = i % 2 == 0 ? 1f : .42f;
                    float px = (float) Math.cos(ang) * hw * r, py = (float) Math.sin(ang) * hh * r;
                    if (i == 0) t.moveTo(px, py); else t.lineTo(px, py);
                }
                t.close();
                c.drawPath(t, p);
                break;
            }
            case "IMAGE": p.setColor(Color.WHITE); p.setAlpha(l.alpha); c.drawBitmap(l.bmp, null, new RectF(-hw, -hh, hw, hh), p); break;
            case "TEXT": {
                p.setTextSize(l.size);
                p.setTypeface(Emoji.font(l.bold));
                p.setTextSkewX(l.italic ? -.25f : 0);
                p.setTextAlign(Paint.Align.CENTER);
                String[] ln = l.text.split("\n", -1);
                float lh = l.size * 1.2f, y0 = -lh * ln.length / 2f + lh * .82f;
                for (int i = 0; i < ln.length; i++) c.drawText(ln[i], 0, y0 + i * lh, p);
                p.setTextSkewX(0);
                break;
            }
            default: break;
        }
        c.restore();
    }

    // ---------- canvas view ----------
    class DView extends View {
        float s, ox, oy;
        float lx, ly;
        boolean multi;
        float d0, a0, w0, h0, r0, size0;
        final Paint sp = new Paint(Paint.ANTI_ALIAS_FLAG);

        DView() { super(a); }

        void fit() {
            s = Math.min((getWidth() - a.dp(24)) / (float) dw, (getHeight() - a.dp(24)) / (float) dh);
            ox = (getWidth() - dw * s) / 2f;
            oy = (getHeight() - dh * s) / 2f;
        }

        @Override protected void onDraw(Canvas c) {
            fit();
            c.save();
            c.translate(ox, oy);
            c.scale(s, s);
            c.clipRect(0, 0, dw, dh);
            render(c);
            c.restore();
            if (sel != null) {
                c.save();
                c.translate(ox + sel.x * s, oy + sel.y * s);
                c.rotate(sel.rot);
                sp.setStyle(Paint.Style.STROKE);
                sp.setStrokeWidth(a.dp(1.5f));
                sp.setColor(0xFF0A84FF);
                float hw = sel.w * s / 2f, hh = sel.h * s / 2f;
                c.drawRect(-hw, -hh, hw, hh, sp);
                sp.setStyle(Paint.Style.FILL);
                sp.setColor(0xFFFFFFFF);
                for (float[] k : new float[][]{{-hw, -hh}, {hw, -hh}, {-hw, hh}, {hw, hh}}) { c.drawCircle(k[0], k[1], a.dp(4), sp); }
                c.restore();
            }
        }

        Layer hit(float x, float y) {
            for (int i = layers.size() - 1; i >= 0; i--) {
                Layer l = layers.get(i);
                double r = Math.toRadians(-l.rot);
                float dx = x - l.x, dy = y - l.y;
                float rx = (float) (dx * Math.cos(r) - dy * Math.sin(r)), ry = (float) (dx * Math.sin(r) + dy * Math.cos(r));
                float pad = Math.max(0, a.dp(8) / s - Math.min(l.w, l.h) / 2f);
                if (Math.abs(rx) <= l.w / 2f + pad && Math.abs(ry) <= l.h / 2f + pad) return l;
            }
            return null;
        }

        @Override public boolean onTouchEvent(MotionEvent e) {
            fit();
            int n = e.getPointerCount();
            if (n >= 2 && sel != null) {
                float dx = e.getX(1) - e.getX(0), dy = e.getY(1) - e.getY(0);
                float d = (float) Math.hypot(dx, dy), ang = (float) Math.toDegrees(Math.atan2(dy, dx));
                if (!multi) { multi = true; d0 = d; a0 = ang; w0 = sel.w; h0 = sel.h; r0 = sel.rot; size0 = sel.size; }
                else {
                    float f = d / Math.max(1f, d0);
                    sel.w = w0 * f; sel.h = h0 * f; sel.rot = r0 + (ang - a0);
                    if (sel.type.equals("TEXT")) { sel.size = size0 * f; DesignApp.this.measure(sel); }
                    invalidate();
                }
                return true;
            }
            if (multi) { if (e.getActionMasked() == MotionEvent.ACTION_UP) multi = false; if (n < 2) { multi = false; lx = e.getX(); ly = e.getY(); } return true; }
            float x = (e.getX() - ox) / s, y = (e.getY() - oy) / s;
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN: {
                    Layer h = hit(x, y);
                    if (h != sel) { sel = h; if (h != null && tab.equals("Style")) drawPanel(); else if (h != null) { tab = "Style"; drawTabs(); drawPanel(); } }
                    lx = e.getX(); ly = e.getY();
                    invalidate();
                    return true;
                }
                case MotionEvent.ACTION_MOVE:
                    if (sel != null) { sel.x += (e.getX() - lx) / s; sel.y += (e.getY() - ly) / s; invalidate(); }
                    lx = e.getX(); ly = e.getY();
                    return true;
                default: return true;
            }
        }
    }

    void export() {
        try {
            Layer keep = sel;
            sel = null;
            Bitmap out = Bitmap.createBitmap(dw, dh, Bitmap.Config.ARGB_8888);
            render(new Canvas(out));
            sel = keep;
            String name = "Feng Design " + new SimpleDateFormat("yyyy-MM-dd HH.mm.ss", Locale.US).format(new Date()) + ".png";
            ContentValues v = new ContentValues();
            v.put(MediaStore.Images.Media.DISPLAY_NAME, name);
            v.put(MediaStore.Images.Media.MIME_TYPE, "image/png");
            if (Build.VERSION.SDK_INT >= 29) v.put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/Feng Design");
            else {
                File dir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "Feng Design");
                dir.mkdirs();
                v.put(MediaStore.Images.Media.DATA, new File(dir, name).getAbsolutePath());
            }
            Uri u = a.getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, v);
            try (OutputStream os = a.getContentResolver().openOutputStream(u)) { out.compress(Bitmap.CompressFormat.PNG, 100, os); }
            Toast.makeText(a, "Saved to Photos", Toast.LENGTH_SHORT).show();
        } catch (Exception e) { Toast.makeText(a, "Couldn\u2019t save the design", Toast.LENGTH_SHORT).show(); }
    }
}
