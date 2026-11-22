package com.macos.launcher;

import android.content.Context;
import android.graphics.*;
import android.view.View;
import java.io.File;

public class WallpaperView extends View {
    public static Bitmap blur;
    public static int bw, bh;
    private Bitmap sharp;
    private final Paint p = new Paint(Paint.FILTER_BITMAP_FLAG);

    public WallpaperView(Context c) { super(c); }
    public Bitmap getSharp() { return sharp; }

    @Override protected void onSizeChanged(int w, int h, int ow, int oh) { if (w > 0 && h > 0) build(w, h); }

    private void build(int w, int h) {
        Bitmap b = null;
        File f = new File(getContext().getFilesDir(), "wall.jpg");
        if (f.exists()) {
            try {
                Bitmap src = BitmapFactory.decodeFile(f.getPath());
                if (src != null) {
                    b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
                    Canvas c = new Canvas(b);
                    float s = Math.max(w / (float) src.getWidth(), h / (float) src.getHeight());
                    Matrix m = new Matrix();
                    m.setScale(s, s);
                    m.postTranslate((w - src.getWidth() * s) / 2f, (h - src.getHeight() * s) / 2f);
                    c.drawBitmap(src, m, p);
                    src.recycle();
                }
            } catch (Throwable t) { b = null; }
        }
        if (b == null) b = procedural(w, h);
        sharp = b;
        Bitmap s = b;
        int tw = Math.max(2, w / 26), th = Math.max(2, h / 26);
        while (s.getWidth() / 2 > tw) {
            Bitmap n = Bitmap.createScaledBitmap(s, Math.max(tw, s.getWidth() / 2), Math.max(th, s.getHeight() / 2), true);
            if (s != b) s.recycle();
            s = n;
        }
        blur = Bitmap.createScaledBitmap(s, tw, th, true);
        bw = w; bh = h;
        invalidate();
    }

    /** Tahoe-style blue wave when no custom wallpaper is set. */
    private Bitmap procedural(int w, int h) {
        Bitmap b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        Paint q = new Paint(Paint.ANTI_ALIAS_FLAG);
        q.setShader(new LinearGradient(0, 0, w, h, new int[]{0xFFC9D6E6, 0xFF8FB9EA, 0xFF2F78E8, 0xFF0A45D4},
                new float[]{0f, .35f, .7f, 1f}, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, w, h, q);
        q.setShader(new RadialGradient(w * .02f, h * .45f, w * .5f, 0xFFEBDDAF, 0x00EBDDAF, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, w, h, q);
        q.setShader(new RadialGradient(w * .55f, h * .95f, Math.max(w, h) * .55f, 0xFF0A3BC8, 0x000A3BC8, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, w, h, q);
        q.setShader(new RadialGradient(w * .3f, h * .9f, Math.max(w, h) * .35f, 0xFF7DB7F2, 0x007DB7F2, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, w, h, q);
        Path wave = new Path();
        wave.moveTo(0, h * 1.02f);
        wave.quadTo(w * .38f, h * .2f, w * .9f, -h * .05f);
        q.setShader(new LinearGradient(0, h, w, 0, 0x00FFFFFF, 0xCCFFFFFF, Shader.TileMode.CLAMP));
        q.setStyle(Paint.Style.STROKE);
        q.setStrokeWidth(Math.max(2f, w * .006f));
        c.drawPath(wave, q);
        q.setStrokeWidth(w * .05f);
        q.setShader(new LinearGradient(0, h, w, 0, 0x00FFFFFF, 0x33FFFFFF, Shader.TileMode.CLAMP));
        c.drawPath(wave, q);
        return b;
    }

    @Override protected void onDraw(Canvas c) {
        if (sharp != null) c.drawBitmap(sharp, 0, 0, p);
    }
}
