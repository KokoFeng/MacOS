package com.macos.launcher;

import android.content.ContentValues;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
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
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/** Photo editor: adjustments, filters, rotate/flip/crop, drawing, text, and save to Photos. All tools are free. */
public class PhotoApp {
    static final String[] FILTERS = {"Original", "Black & White", "Sepia", "Vivid", "Cool", "Warm", "Fade", "Noir", "Vintage", "Dramatic"};
    static final int[] SWATCH = {0xFFFFFFFF, 0xFF000000, 0xFFFF3B30, 0xFFFF9F0A, 0xFFFFD60A, 0xFF30D158, 0xFF0A84FF, 0xFFBF5AF2};

    static class Stroke { Path path = new Path(); int color; float width; }
    static class Txt { String s; float x, y, size; int color; }

    final MainActivity a;
    Bitmap base;
    int bri, con, sat = 100, warm, filter;
    final List<Stroke> strokes = new ArrayList<>();
    final List<Txt> texts = new ArrayList<>();
    int drawColor = 0xFFFF3B30;
    float brush = 10;
    String mode = "Adjust";
    PhotoView pv;
    LinearLayout panel;

    public static void open(MainActivity a) { new PhotoApp(a); }

    PhotoApp(MainActivity act) {
        a = act;
        TextView openB = chip("Open", false), save = chip("Save", true);
        openB.setOnClickListener(v -> a.pick("image/*", false, us -> load(us.get(0))));
        save.setOnClickListener(v -> save());
        LinearLayout tb = new LinearLayout(a);
        tb.setGravity(Gravity.CENTER_VERTICAL);
        tb.addView(new View(a), new LinearLayout.LayoutParams(0, 1, 1f));
        tb.addView(openB, new LinearLayout.LayoutParams(a.dp(60), a.dp(30)));
        LinearLayout.LayoutParams sl = new LinearLayout.LayoutParams(a.dp(60), a.dp(30));
        sl.leftMargin = a.dp(8);
        tb.addView(save, sl);

        LinearLayout body = new LinearLayout(a);
        FrameLayout stage = new FrameLayout(a);
        stage.setBackgroundColor(Theme.dark ? 0xFF111113 : 0xFFE9E9EE);
        pv = new PhotoView();
        stage.addView(pv, new FrameLayout.LayoutParams(-1, -1));
        body.addView(stage, new LinearLayout.LayoutParams(0, -1, 1f));
        LinearLayout side = new LinearLayout(a);
        side.setOrientation(LinearLayout.VERTICAL);
        side.setBackgroundColor(Theme.side());
        HorizontalScrollView hs = new HorizontalScrollView(a);
        hs.setHorizontalScrollBarEnabled(false);
        LinearLayout tabs = new LinearLayout(a);
        tabs.setPadding(a.dp(6), a.dp(6), a.dp(6), a.dp(6));
        for (final String t : new String[]{"Adjust", "Filters", "Crop", "Draw", "Text"}) {
            TextView c = chip(t, t.equals(mode));
            c.setOnClickListener(v -> { mode = t; refreshTabs(tabs); drawPanel(); pv.invalidate(); });
            tabs.addView(c, new LinearLayout.LayoutParams(a.dp(58), a.dp(28)));
        }
        hs.addView(tabs);
        side.addView(hs, new LinearLayout.LayoutParams(-1, -2));
        panel = new LinearLayout(a);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(a.dp(12), a.dp(4), a.dp(12), a.dp(10));
        ScrollView sv = new ScrollView(a);
        sv.setVerticalScrollBarEnabled(false);
        sv.addView(panel);
        side.addView(sv, new LinearLayout.LayoutParams(-1, 0, 1f));
        body.addView(side, new LinearLayout.LayoutParams(a.dp(210), -1));
        MacWindow w = new MacWindow(a, "Feng Photo", tb, null, body);
        a.openWindow(w, 760, 360);
        drawPanel();
    }

    TextView chip(String s, boolean on) {
        TextView t = Ui.tv(a, s, 12, on ? Color.WHITE : Theme.fg(), on);
        t.setGravity(Gravity.CENTER);
        t.setBackground(Ui.rr(on ? Theme.accent : (Theme.dark ? 0x2EFFFFFF : 0x14000000), a.dp(14)));
        return t;
    }

    void refreshTabs(LinearLayout tabs) {
        String[] n = {"Adjust", "Filters", "Crop", "Draw", "Text"};
        for (int i = 0; i < tabs.getChildCount(); i++) {
            TextView c = (TextView) tabs.getChildAt(i);
            boolean on = n[i].equals(mode);
            c.setTextColor(on ? Color.WHITE : Theme.fg());
            c.setBackground(Ui.rr(on ? Theme.accent : (Theme.dark ? 0x2EFFFFFF : 0x14000000), a.dp(14)));
        }
    }

    void load(Uri u) {
        try {
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inJustDecodeBounds = true;
            try (InputStream in = a.getContentResolver().openInputStream(u)) { BitmapFactory.decodeStream(in, null, o); }
            int sample = 1;
            while (Math.max(o.outWidth, o.outHeight) / sample > 2400) sample *= 2;
            o = new BitmapFactory.Options();
            o.inSampleSize = sample;
            try (InputStream in = a.getContentResolver().openInputStream(u)) { base = BitmapFactory.decodeStream(in, null, o); }
            if (base == null) throw new RuntimeException();
            try (InputStream in = a.getContentResolver().openInputStream(u)) {
                int rot = new android.media.ExifInterface(in).getAttributeInt(android.media.ExifInterface.TAG_ORIENTATION, 1);
                int deg = rot == 6 ? 90 : rot == 3 ? 180 : rot == 8 ? 270 : 0;
                if (deg != 0) { Matrix m = new Matrix(); m.postRotate(deg); base = Bitmap.createBitmap(base, 0, 0, base.getWidth(), base.getHeight(), m, true); }
            } catch (Exception e) { }
            strokes.clear(); texts.clear();
            bri = 0; con = 0; sat = 100; warm = 0; filter = 0;
            drawPanel();
            pv.invalidate();
        } catch (Exception e) { Toast.makeText(a, "Couldn\u2019t open that image", Toast.LENGTH_SHORT).show(); }
    }

    // ---------- colour pipeline ----------
    ColorMatrix filterMatrix(int f) {
        ColorMatrix m = new ColorMatrix();
        switch (f) {
            case 1: m.setSaturation(0); break;
            case 2: m.set(new float[]{.393f, .769f, .189f, 0, 0, .349f, .686f, .168f, 0, 0, .272f, .534f, .131f, 0, 0, 0, 0, 0, 1, 0}); break;
            case 3: m.setSaturation(1.6f); break;
            case 4: m.set(new float[]{.9f, 0, 0, 0, 0, 0, 1, 0, 0, 0, 0, 0, 1.15f, 0, 8, 0, 0, 0, 1, 0}); break;
            case 5: m.set(new float[]{1.15f, 0, 0, 0, 8, 0, 1.02f, 0, 0, 0, 0, 0, .85f, 0, 0, 0, 0, 0, 1, 0}); break;
            case 6: m.setSaturation(.7f); m.postConcat(new ColorMatrix(new float[]{.85f, 0, 0, 0, 30, 0, .85f, 0, 0, 30, 0, 0, .85f, 0, 30, 0, 0, 0, 1, 0})); break;
            case 7: m.setSaturation(0); m.postConcat(new ColorMatrix(new float[]{1.5f, 0, 0, 0, -60, 0, 1.5f, 0, 0, -60, 0, 0, 1.5f, 0, -60, 0, 0, 0, 1, 0})); break;
            case 8: m.set(new float[]{.393f, .769f, .189f, 0, 0, .349f, .686f, .168f, 0, 0, .272f, .534f, .131f, 0, 0, 0, 0, 0, 1, 0}); m.postConcat(new ColorMatrix(new float[]{.9f, 0, 0, 0, 20, 0, .9f, 0, 0, 20, 0, 0, .9f, 0, 20, 0, 0, 0, 1, 0})); break;
            case 9: m.setSaturation(.8f); m.postConcat(new ColorMatrix(new float[]{1.4f, 0, 0, 0, -50, 0, 1.4f, 0, 0, -50, 0, 0, 1.4f, 0, -50, 0, 0, 0, 1, 0})); break;
            default: break;
        }
        return m;
    }

    ColorMatrix fullMatrix() {
        ColorMatrix cm = new ColorMatrix();
        cm.setSaturation(sat / 100f);
        float c = 1f + con / 100f, t = (1f - c) * 128f + bri * 2.55f;
        cm.postConcat(new ColorMatrix(new float[]{c, 0, 0, 0, t, 0, c, 0, 0, t, 0, 0, c, 0, t, 0, 0, 0, 1, 0}));
        float w = warm * .6f;
        cm.postConcat(new ColorMatrix(new float[]{1, 0, 0, 0, w, 0, 1, 0, 0, 0, 0, 0, 1, 0, -w, 0, 0, 0, 1, 0}));
        if (filter != 0) cm.postConcat(filterMatrix(filter));
        return cm;
    }

    // ---------- panels ----------
    void drawPanel() {
        panel.removeAllViews();
        if (base == null) {
            TextView t = Ui.tv(a, "Tap Open to choose a photo.", 13, Theme.sub(), false);
            t.setPadding(0, a.dp(14), 0, 0);
            panel.addView(t);
            return;
        }
        switch (mode) {
            case "Adjust":
                slider("Brightness", -100, 100, bri, v -> { bri = v; pv.invalidate(); });
                slider("Contrast", -80, 100, con, v -> { con = v; pv.invalidate(); });
                slider("Saturation", 0, 200, sat, v -> { sat = v; pv.invalidate(); });
                slider("Warmth", -100, 100, warm, v -> { warm = v; pv.invalidate(); });
                break;
            case "Filters":
                for (int i = 0; i < FILTERS.length; i++) {
                    final int k = i;
                    TextView t = chip(FILTERS[i], filter == i);
                    t.setOnClickListener(v -> { filter = k; drawPanel(); pv.invalidate(); });
                    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, a.dp(30));
                    lp.topMargin = a.dp(5);
                    panel.addView(t, lp);
                }
                break;
            case "Crop":
                String[] ar = {"Rotate left", "Rotate right", "Flip horizontal", "Flip vertical", "Crop 1:1", "Crop 4:3", "Crop 3:4", "Crop 16:9", "Crop 9:16"};
                for (final String n : ar) {
                    TextView t = chip(n, false);
                    t.setOnClickListener(v -> geometry(n));
                    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, a.dp(30));
                    lp.topMargin = a.dp(5);
                    panel.addView(t, lp);
                }
                break;
            case "Draw":
                swatches(c -> drawColor = c);
                slider("Brush size", 2, 60, (int) brush, v -> brush = v);
                TextView u = chip("Undo stroke", false);
                u.setOnClickListener(v -> { if (!strokes.isEmpty()) strokes.remove(strokes.size() - 1); pv.invalidate(); });
                LinearLayout.LayoutParams ul = new LinearLayout.LayoutParams(-1, a.dp(30));
                ul.topMargin = a.dp(8);
                panel.addView(u, ul);
                panel.addView(Ui.tv(a, "Draw on the photo with your finger.", 11, Theme.sub(), false));
                break;
            default:
                TextView add = chip("Add text", true);
                add.setOnClickListener(v -> a.prompt("Text", "", s -> {
                    Txt t = new Txt();
                    t.s = s; t.x = .5f; t.y = .5f; t.size = base.getWidth() * .08f; t.color = 0xFFFFFFFF;
                    texts.add(t);
                    pv.invalidate();
                }));
                panel.addView(add, new LinearLayout.LayoutParams(-1, a.dp(30)));
                swatches(c -> { if (!texts.isEmpty()) texts.get(texts.size() - 1).color = c; pv.invalidate(); });
                slider("Text size", 2, 30, (int) (base.getWidth() * .08f / base.getWidth() * 100), v -> { if (!texts.isEmpty()) texts.get(texts.size() - 1).size = base.getWidth() * v / 100f; pv.invalidate(); });
                TextView rm = chip("Remove last text", false);
                rm.setOnClickListener(v -> { if (!texts.isEmpty()) texts.remove(texts.size() - 1); pv.invalidate(); });
                LinearLayout.LayoutParams rl = new LinearLayout.LayoutParams(-1, a.dp(30));
                rl.topMargin = a.dp(8);
                panel.addView(rm, rl);
                panel.addView(Ui.tv(a, "Drag on the photo to move the last text.", 11, Theme.sub(), false));
        }
    }

    interface IntCb { void on(int v); }

    void slider(String label, int min, int max, int val, final IntCb cb) {
        TextView t = Ui.tv(a, label, 12, Theme.fg(), false);
        t.setPadding(0, a.dp(8), 0, 0);
        panel.addView(t);
        SeekBar s = new SeekBar(a);
        s.setMax(max - min);
        s.setProgress(val - min);
        final int mn = min;
        s.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar sb, int p, boolean u) { cb.on(p + mn); }
            public void onStartTrackingTouch(SeekBar sb) {}
            public void onStopTrackingTouch(SeekBar sb) {}
        });
        panel.addView(s);
    }

    void swatches(final IntCb cb) {
        LinearLayout row = new LinearLayout(a);
        row.setPadding(0, a.dp(8), 0, 0);
        for (final int c : SWATCH) {
            View v = new View(a);
            android.graphics.drawable.GradientDrawable g = new android.graphics.drawable.GradientDrawable();
            g.setShape(android.graphics.drawable.GradientDrawable.OVAL);
            g.setColor(c);
            g.setStroke(1, 0x55888888);
            v.setBackground(g);
            v.setOnClickListener(x -> cb.on(c));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(a.dp(18), a.dp(18));
            lp.rightMargin = a.dp(4);
            row.addView(v, lp);
        }
        panel.addView(row);
    }

    void geometry(String op) {
        strokes.clear(); texts.clear();
        Matrix m = new Matrix();
        int w = base.getWidth(), h = base.getHeight();
        int cx = 0, cy = 0, cw = w, ch = h;
        switch (op) {
            case "Rotate left": m.postRotate(-90); break;
            case "Rotate right": m.postRotate(90); break;
            case "Flip horizontal": m.postScale(-1, 1); break;
            case "Flip vertical": m.postScale(1, -1); break;
            default: {
                float r = op.endsWith("1:1") ? 1f : op.endsWith("4:3") ? 4f / 3 : op.endsWith("3:4") ? 3f / 4 : op.endsWith("16:9") ? 16f / 9 : 9f / 16;
                if (w / (float) h > r) { cw = Math.round(h * r); cx = (w - cw) / 2; } else { ch = Math.round(w / r); cy = (h - ch) / 2; }
            }
        }
        base = Bitmap.createBitmap(base, cx, cy, cw, ch, m, true);
        pv.invalidate();
    }

    // ---------- view ----------
    class PhotoView extends View {
        final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        Stroke cur;
        float s, ox, oy;

        PhotoView() { super(a); }

        void layout() {
            s = Math.min(getWidth() / (float) base.getWidth(), getHeight() / (float) base.getHeight());
            ox = (getWidth() - base.getWidth() * s) / 2f;
            oy = (getHeight() - base.getHeight() * s) / 2f;
        }

        @Override protected void onDraw(Canvas c) {
            if (base == null) return;
            layout();
            p.setColorFilter(new ColorMatrixColorFilter(fullMatrix()));
            c.save();
            c.translate(ox, oy);
            c.scale(s, s);
            c.drawBitmap(base, 0, 0, p);
            p.setColorFilter(null);
            renderOverlays(c);
            c.restore();
        }

        @Override public boolean onTouchEvent(MotionEvent e) {
            if (base == null) return true;
            layout();
            float x = (e.getX() - ox) / s, y = (e.getY() - oy) / s;
            if (mode.equals("Draw")) {
                if (e.getAction() == MotionEvent.ACTION_DOWN) { cur = new Stroke(); cur.color = drawColor; cur.width = brush * base.getWidth() / 1000f; cur.path.moveTo(x, y); strokes.add(cur); }
                else if (cur != null && e.getAction() == MotionEvent.ACTION_MOVE) cur.path.lineTo(x, y);
                invalidate();
                return true;
            }
            if (mode.equals("Text") && !texts.isEmpty() && (e.getAction() == MotionEvent.ACTION_DOWN || e.getAction() == MotionEvent.ACTION_MOVE)) {
                Txt t = texts.get(texts.size() - 1);
                t.x = Math.max(0, Math.min(1, x / base.getWidth()));
                t.y = Math.max(0, Math.min(1, y / base.getHeight()));
                invalidate();
                return true;
            }
            return true;
        }
    }

    void renderOverlays(Canvas c) {
        Paint sp = new Paint(Paint.ANTI_ALIAS_FLAG);
        sp.setStyle(Paint.Style.STROKE);
        sp.setStrokeCap(Paint.Cap.ROUND);
        sp.setStrokeJoin(Paint.Join.ROUND);
        for (Stroke st : strokes) { sp.setColor(st.color); sp.setStrokeWidth(st.width); c.drawPath(st.path, sp); }
        Paint tp = new Paint(Paint.ANTI_ALIAS_FLAG);
        tp.setTypeface(Emoji.font(true));
        tp.setTextAlign(Paint.Align.CENTER);
        for (Txt t : texts) {
            tp.setTextSize(t.size);
            tp.setColor(t.color);
            tp.setShadowLayer(t.size * .06f, 0, 0, 0x88000000);
            c.drawText(t.s, t.x * base.getWidth(), t.y * base.getHeight() - (tp.ascent() + tp.descent()) / 2f, tp);
        }
    }

    void save() {
        if (base == null) { Toast.makeText(a, "Open a photo first", Toast.LENGTH_SHORT).show(); return; }
        try {
            Bitmap out = Bitmap.createBitmap(base.getWidth(), base.getHeight(), Bitmap.Config.ARGB_8888);
            Canvas c = new Canvas(out);
            Paint p = new Paint(Paint.FILTER_BITMAP_FLAG);
            p.setColorFilter(new ColorMatrixColorFilter(fullMatrix()));
            c.drawBitmap(base, 0, 0, p);
            renderOverlays(c);
            String name = "Feng Photo " + new SimpleDateFormat("yyyy-MM-dd HH.mm.ss", Locale.US).format(new Date()) + ".jpg";
            ContentValues v = new ContentValues();
            v.put(MediaStore.Images.Media.DISPLAY_NAME, name);
            v.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg");
            if (Build.VERSION.SDK_INT >= 29) v.put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/Feng Photo");
            else {
                File dir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "Feng Photo");
                dir.mkdirs();
                v.put(MediaStore.Images.Media.DATA, new File(dir, name).getAbsolutePath());
            }
            Uri u = a.getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, v);
            try (OutputStream os = a.getContentResolver().openOutputStream(u)) { out.compress(Bitmap.CompressFormat.JPEG, 95, os); }
            Toast.makeText(a, "Saved to Photos", Toast.LENGTH_SHORT).show();
        } catch (Exception e) { Toast.makeText(a, "Couldn\u2019t save the photo", Toast.LENGTH_SHORT).show(); }
    }
}
