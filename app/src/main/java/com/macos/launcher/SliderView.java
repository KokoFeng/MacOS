package com.macos.launcher;

import android.content.Context;
import android.graphics.*;
import android.view.MotionEvent;
import android.view.View;

public class SliderView extends View {
    public interface Listener { void onChange(float v); }
    private float v;
    private Listener l;
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float den;

    public SliderView(Context c, float initial, Listener l) {
        super(c);
        v = initial; this.l = l;
        den = c.getResources().getDisplayMetrics().density;
    }

    @Override protected void onDraw(Canvas c) {
        float w = getWidth(), h = getHeight(), cy = h / 2f, tr = h * .4f, th = 4f * den;
        float x = tr + v * (w - 2 * tr);
        p.setColor(0x59FFFFFF);
        c.drawRoundRect(new RectF(tr, cy - th / 2, w - tr, cy + th / 2), th / 2, th / 2, p);
        p.setColor(0xFFFFFFFF);
        c.drawRoundRect(new RectF(tr, cy - th / 2, x, cy + th / 2), th / 2, th / 2, p);
        p.setColor(0x33000000);
        c.drawCircle(x, cy + den, tr, p);
        p.setColor(0xFFFFFFFF);
        c.drawCircle(x, cy, tr, p);
    }

    @Override public boolean onTouchEvent(MotionEvent e) {
        if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(true);
        float tr = getHeight() * .4f;
        v = Math.max(0f, Math.min(1f, (e.getX() - tr) / (getWidth() - 2 * tr)));
        invalidate();
        if (l != null) l.onChange(v);
        return true;
    }
}
