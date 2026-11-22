package com.macos.launcher;

import android.content.Context;
import android.graphics.*;
import android.view.MotionEvent;
import android.view.View;

public class ToggleView extends View {
    public interface Listener { void onToggle(boolean on); }
    private boolean on;
    private final Listener l;
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float den;

    public ToggleView(Context c, boolean on, Listener l) {
        super(c);
        this.on = on; this.l = l;
        den = c.getResources().getDisplayMetrics().density;
    }

    @Override protected void onMeasure(int w, int h) { setMeasuredDimension(Math.round(42 * den), Math.round(26 * den)); }

    @Override protected void onDraw(Canvas c) {
        float w = getWidth(), h = getHeight();
        p.setColor(on ? Theme.accent : (Theme.dark ? 0x59787880 : 0x33787880));
        c.drawRoundRect(new RectF(0, 0, w, h), h / 2, h / 2, p);
        float r = h / 2f - 2 * den, cx = on ? w - h / 2f : h / 2f;
        p.setColor(0x33000000);
        c.drawCircle(cx, h / 2f + den, r, p);
        p.setColor(0xFFFFFFFF);
        c.drawCircle(cx, h / 2f, r, p);
    }

    @Override public boolean onTouchEvent(MotionEvent e) {
        if (e.getActionMasked() == MotionEvent.ACTION_UP) {
            on = !on;
            invalidate();
            if (l != null) l.onToggle(on);
        }
        return true;
    }
}
