package com.macos.launcher;

import android.graphics.*;
import android.graphics.drawable.Drawable;
import android.view.View;

/**
 * Liquid Glass (macOS Tahoe look): blurred + saturated backdrop, very light neutral tint,
 * a soft "lens" glow that hugs the inside of the rim, and a thin specular rim lit from the top-left
 * (and a weaker bounce on the bottom-right). No big radial hot-spot, no thick white border.
 */
public class GlassDrawable extends Drawable {
    private final View host;
    private final float radius, stroke;
    private final int tint;
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint rim = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint rimDark = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint lens = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint sheen = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint bg = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG);
    private final RectF r = new RectF(), in = new RectF();
    private final Path clip = new Path();
    private final int[] loc = new int[2];
    private final Rect dst = new Rect();
    private float rad, lensW;
    private static final int STEPS = 5;

    public GlassDrawable(View host, float radius, float stroke, int tint) {
        this.host = host; this.radius = radius; this.stroke = Math.max(1f, stroke); this.tint = tint;
        ColorMatrix cm = new ColorMatrix();
        cm.setSaturation(1.9f);
        // gentle lift so the backdrop reads as bright, clean glass instead of muddy grey
        ColorMatrix lift = new ColorMatrix(new float[]{
                1.04f, 0, 0, 0, 6,
                0, 1.04f, 0, 0, 6,
                0, 0, 1.04f, 0, 6,
                0, 0, 0, 1, 0});
        cm.postConcat(lift);
        bg.setColorFilter(new ColorMatrixColorFilter(cm));
        rim.setStyle(Paint.Style.STROKE);
        rimDark.setStyle(Paint.Style.STROKE);
        lens.setStyle(Paint.Style.STROKE);
    }

    @Override protected void onBoundsChange(Rect b) {
        if (b.isEmpty()) return;
        r.set(b);
        r.inset(stroke / 2f, stroke / 2f);
        rad = Math.min(radius, Math.min(r.width(), r.height()) / 2f);
        float k = Theme.glassK;
        int a = Math.min(255, Math.round(Color.alpha(tint) * 0.74f * k));
        int cr = Color.red(tint), cg = Color.green(tint), cb = Color.blue(tint);
        // nearly flat tint, only a whisper of vertical falloff
        fill.setShader(new LinearGradient(0, b.top, 0, b.bottom,
                Color.argb(Math.min(255, Math.round(a * 1.06f)), cr, cg, cb),
                Color.argb(Math.round(a * 0.94f), cr, cg, cb), Shader.TileMode.CLAMP));
        rim.setStrokeWidth(stroke);
        // light comes from the top-left; opposite corner catches a weaker reflection
        rim.setShader(new LinearGradient(b.left, b.top, b.right, b.bottom,
                new int[]{0x99FFFFFF, 0x38FFFFFF, 0x14FFFFFF, 0x14FFFFFF, 0x4DFFFFFF},
                new float[]{0f, .16f, .45f, .72f, 1f}, Shader.TileMode.CLAMP));
        rimDark.setStrokeWidth(stroke);
        rimDark.setColor(Theme.dark ? 0x40000000 : 0x14000000);
        lensW = Math.max(stroke * 3f, Math.min(r.width(), r.height()) * .09f);
        lens.setStrokeWidth(lensW / STEPS * 1.15f);
        sheen.setShader(new LinearGradient(0, b.top, 0, b.top + Math.min(b.height(), rad * 2.2f),
                0x1AFFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP));
        clip.reset();
        clip.addRoundRect(r, rad, rad, Path.Direction.CW);
    }

    @Override public void draw(Canvas c) {
        if (r.isEmpty()) return;
        Bitmap bl = WallpaperView.blur;
        int s = c.save();
        c.clipPath(clip);
        if (bl != null && host != null) {
            host.getLocationInWindow(loc);
            c.save();
            c.translate(-loc[0], -loc[1]);
            dst.set(0, 0, WallpaperView.bw, WallpaperView.bh);
            c.drawBitmap(bl, null, dst, bg);
            c.restore();
        }
        c.drawRoundRect(r, rad, rad, fill);
        c.drawRect(r, sheen);
        // lens glow: brighter just inside the rim, fading toward the middle (refraction at the edge)
        for (int i = 0; i < STEPS; i++) {
            float d = stroke + (lensW / STEPS) * (i + .5f);
            in.set(r.left + d - stroke / 2f, r.top + d - stroke / 2f, r.right - d + stroke / 2f, r.bottom - d + stroke / 2f);
            if (in.width() <= 2 || in.height() <= 2) break;
            float rr = Math.max(0f, rad - d + stroke / 2f);
            float f = 1f - i / (float) STEPS;
            lens.setColor(Color.argb(Math.round((Theme.dark ? 20 : 30) * f * f), 255, 255, 255));
            c.drawRoundRect(in, rr, rr, lens);
        }
        c.restoreToCount(s);
        c.drawRoundRect(r, rad, rad, rimDark);
        c.drawRoundRect(r, rad, rad, rim);
    }

    @Override public void setAlpha(int a) {}
    @Override public void setColorFilter(ColorFilter f) {}
    @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
}
