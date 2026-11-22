package com.macos.launcher;

import android.graphics.*;
import android.graphics.drawable.Drawable;

public class Backdrop extends Drawable {
    private final Paint p = new Paint(Paint.FILTER_BITMAP_FLAG);
    private final int dim;
    public Backdrop(int dim) {
        this.dim = dim;
        ColorMatrix cm = new ColorMatrix();
        cm.setSaturation(1.4f);
        p.setColorFilter(new ColorMatrixColorFilter(cm));
    }
    @Override public void draw(Canvas c) {
        if (WallpaperView.blur != null) c.drawBitmap(WallpaperView.blur, null, getBounds(), p);
        c.drawColor(dim);
    }
    @Override public void setAlpha(int a) {}
    @Override public void setColorFilter(ColorFilter f) {}
    @Override public int getOpacity() { return PixelFormat.OPAQUE; }
}
