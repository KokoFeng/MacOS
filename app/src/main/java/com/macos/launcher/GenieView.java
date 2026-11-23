package com.macos.launcher;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.View;

/** Draws a window snapshot warped like the macOS Genie effect: the bottom funnels into the dock icon first. */
public class GenieView extends View {
    private static final int C = 10, R = 30;
    private final Bitmap bm;
    private final float x, y, w, h, tx, ty;
    private final float[] v = new float[(C + 1) * (R + 1) * 2];
    private final Paint p = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG);
    private float t;

    public GenieView(Context c, Bitmap bm, float x, float y, float w, float h, float tx, float ty) {
        super(c);
        this.bm = bm; this.x = x; this.y = y; this.w = w; this.h = h; this.tx = tx; this.ty = ty;
        setClickable(false);
        setElevation(40f * c.getResources().getDisplayMetrics().density);
    }

    public void setT(float t) { this.t = t; invalidate(); }

    private static float ease(float a) { a = Math.max(0f, Math.min(1f, a)); return a * a * (3f - 2f * a); }

    @Override protected void onDraw(Canvas c) {
        final float g = 0.62f;
        int k = 0;
        for (int i = 0; i <= R; i++) {
            float fv = i / (float) R;                       // 0 = top row, 1 = bottom row
            float tr = ease(t * (1f + g) - g * (1f - fv));   // bottom rows leave first
            float y0 = y + fv * h;
            float yy = y0 + (ty - y0) * tr;
            for (int j = 0; j <= C; j++) {
                float x0 = x + w * j / (float) C;
                // neck narrows faster than the vertical travel so the shape bends like a genie
                float tn = ease(tr * 1.18f);
                v[k++] = x0 + (tx - x0) * tn;
                v[k++] = yy;
            }
        }
        p.setAlpha(Math.round(255 * (1f - Math.max(0f, t - 0.82f) / 0.18f * 0.9f)));
        c.drawBitmapMesh(bm, C, R, v, 0, null, 0, p);
    }
}
