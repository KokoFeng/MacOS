package com.macos.launcher;

import android.graphics.*;
import android.graphics.drawable.Drawable;
import android.view.View;

/** Liquid glass: blurred+saturated backdrop, tint, specular edge. */
public class GlassDrawable extends Drawable {
    private final View host;
    private final float radius, stroke;
    private final int tint;
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint edge = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint hi = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint bg = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG);
    private final RectF r = new RectF();
    private final Path clip = new Path();
    private final int[] loc = new int[2];
    private final Rect dst = new Rect();
    private float rad;

    public GlassDrawable(View host, float radius, float stroke, int tint) {
        this.host = host; this.radius = radius; this.stroke = stroke; this.tint = tint;
        ColorMatrix cm = new ColorMatrix();
        cm.setSaturation(1.55f);
        bg.setColorFilter(new ColorMatrixColorFilter(cm));
        edge.setStyle(Paint.Style.STROKE);
    }

    @Override protected void onBoundsChange(Rect b) {
        if (b.isEmpty()) return;
        r.set(b);
        r.inset(stroke / 2f, stroke / 2f);
        rad = Math.min(radius, Math.min(r.width(), r.height()) / 2f);
        int a = Color.alpha(tint), cr = Color.red(tint), cg = Color.green(tint), cb = Color.blue(tint);
        fill.setShader(new LinearGradient(0, b.top, 0, b.bottom,
                Color.argb(Math.min(255, a * 5 / 4), cr, cg, cb), Color.argb(a * 3 / 4, cr, cg, cb), Shader.TileMode.CLAMP));
        edge.setStrokeWidth(stroke);
        edge.setShader(new LinearGradient(b.left, b.top, b.right, b.bottom,
                new int[]{0xF2FFFFFF, 0x1FFFFFFF, 0x1FFFFFFF, 0xB3FFFFFF}, new float[]{0f, .38f, .62f, 1f}, Shader.TileMode.CLAMP));
        hi.setShader(new RadialGradient(b.left + b.width() * .25f, b.top, Math.max(b.width(), b.height()) * .8f,
                0x4DFFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP));
        clip.reset();
        clip.addRoundRect(r, rad, rad, Path.Direction.CW);
    }

    @Override public void draw(Canvas c) {
        if (r.isEmpty()) return;
        Bitmap bl = WallpaperView.blur;
        if (bl != null && host != null) {
            host.getLocationInWindow(loc);
            int s = c.save();
            c.clipPath(clip);
            c.translate(-loc[0], -loc[1]);
            dst.set(0, 0, WallpaperView.bw, WallpaperView.bh);
            c.drawBitmap(bl, null, dst, bg);
            c.restoreToCount(s);
        }
        c.drawRoundRect(r, rad, rad, fill);
        c.drawRoundRect(r, rad, rad, hi);
        c.drawRoundRect(r, rad, rad, edge);
    }

    @Override public void setAlpha(int a) {}
    @Override public void setColorFilter(ColorFilter f) {}
    @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
}
