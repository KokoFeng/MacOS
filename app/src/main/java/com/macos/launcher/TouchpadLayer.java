package com.macos.launcher;

import android.graphics.*;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.InputDevice;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;

/** Virtual trackpad: a pad on screen drives a cursor; clicks/scroll are injected into the launcher UI. */
public class TouchpadLayer {
    final MainActivity a;
    final FrameLayout root;
    View cursor;
    FrameLayout pad;
    boolean on;
    float cx, cy;
    long downTime;

    public TouchpadLayer(MainActivity act) {
        a = act; root = a.root;
        cx = a.sw / 2f; cy = a.sh / 2f;
    }

    public void setEnabled(boolean e) {
        on = e;
        if (e) build(); else remove();
    }

    void remove() {
        if (cursor != null) { root.removeView(cursor); cursor = null; }
        if (pad != null) { root.removeView(pad); pad = null; }
    }

    public void toFront() {
        if (!on) return;
        if (pad != null) pad.bringToFront();
        if (cursor != null) cursor.bringToFront();
    }

    float speed() { return a.prefs.getFloat("tp_speed", 1.6f); }
    boolean tap() { return a.prefs.getBoolean("tp_tap", true); }
    boolean natural() { return a.prefs.getBoolean("tp_natural", true); }

    void build() {
        remove();
        final float s = a.den;
        cursor = new View(a) {
            final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
            final Path path = new Path();
            {
                path.moveTo(1 * s, 1 * s); path.lineTo(1 * s, 18 * s); path.lineTo(5.4f * s, 14.2f * s);
                path.lineTo(8.2f * s, 20.6f * s); path.lineTo(11f * s, 19.4f * s); path.lineTo(8.2f * s, 13.2f * s);
                path.lineTo(13.5f * s, 13.2f * s); path.close();
            }
            @Override protected void onDraw(Canvas c) {
                p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(2.4f * s); p.setStrokeJoin(Paint.Join.ROUND); p.setColor(0xFFFFFFFF);
                c.drawPath(path, p);
                p.setStyle(Paint.Style.FILL); p.setColor(0xFF000000);
                c.drawPath(path, p);
            }
        };
        cursor.setClickable(false);
        pad = new FrameLayout(a);
        pad.setBackground(new GlassDrawable(pad, a.dp(22), a.dp(1), 0x40FFFFFF));
        TextView hint = Ui.tv(a, "Touchpad", 11, 0x99FFFFFF, false);
        pad.addView(hint, new FrameLayout.LayoutParams(MainActivity.WRAP, MainActivity.WRAP, Gravity.CENTER));
        TextView x = Ui.tv(a, "×", 16, 0xCCFFFFFF, true);
        x.setGravity(Gravity.CENTER);
        x.setOnClickListener(v -> { a.prefs.edit().putBoolean("tp_on", false).apply(); setEnabled(false); });
        pad.addView(x, new FrameLayout.LayoutParams(a.dp(30), a.dp(30), Gravity.TOP | Gravity.RIGHT));
        pad.setOnTouchListener(new View.OnTouchListener() {
            float lx, ly, lastScroll = Float.NaN;
            long t0;
            boolean moved, holding, two;
            final Runnable hold = new Runnable() {
                @Override public void run() { if (!moved && !two) { holding = true; dispatch(MotionEvent.ACTION_DOWN); } }
            };
            @Override public boolean onTouch(View v, MotionEvent e) {
                switch (e.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        lx = e.getX(); ly = e.getY(); t0 = SystemClock.uptimeMillis();
                        moved = false; holding = false; two = false; lastScroll = Float.NaN;
                        a.h.postDelayed(hold, 420);
                        return true;
                    case MotionEvent.ACTION_POINTER_DOWN:
                        two = true; a.h.removeCallbacks(hold); lastScroll = Float.NaN;
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        if (e.getPointerCount() >= 2) {
                            float y = (e.getY(0) + e.getY(1)) / 2f;
                            if (!Float.isNaN(lastScroll)) {
                                float dy = y - lastScroll;
                                if (Math.abs(dy) > 0.5f) { moved = true; scroll(dy); }
                            }
                            lastScroll = y;
                            return true;
                        }
                        float dx = e.getX() - lx, dy2 = e.getY() - ly;
                        if (Math.abs(dx) + Math.abs(dy2) > a.dp(3)) { moved = true; a.h.removeCallbacks(hold); }
                        lx = e.getX(); ly = e.getY();
                        if (!two) {
                            cx = Math.max(0, Math.min(a.sw - 1, cx + dx * speed()));
                            cy = Math.max(0, Math.min(a.sh - 1, cy + dy2 * speed()));
                            place();
                            if (holding) dispatch(MotionEvent.ACTION_MOVE);
                        }
                        return true;
                    case MotionEvent.ACTION_POINTER_UP:
                        lastScroll = Float.NaN;
                        return true;
                    case MotionEvent.ACTION_UP: case MotionEvent.ACTION_CANCEL:
                        a.h.removeCallbacks(hold);
                        long dt = SystemClock.uptimeMillis() - t0;
                        if (holding) { dispatch(MotionEvent.ACTION_UP); holding = false; }
                        else if (!moved && dt < 300) {
                            if (two) longClick(); else if (tap()) click();
                        }
                        two = false; lastScroll = Float.NaN;
                        return true;
                }
                return true;
            }
        });
        FrameLayout.LayoutParams pl = new FrameLayout.LayoutParams(a.dp(240), a.dp(140), Gravity.BOTTOM | Gravity.RIGHT);
        pl.rightMargin = a.dp(14); pl.bottomMargin = a.dp(12);
        root.addView(pad, pl);
        root.addView(cursor, new FrameLayout.LayoutParams(a.dp(26), a.dp(28)));
        place();
    }

    void place() { if (cursor != null) { cursor.setX(cx); cursor.setY(cy); } }

    boolean overPad() {
        if (pad == null) return false;
        int[] l = new int[2];
        pad.getLocationInWindow(l);
        return cx >= l[0] && cx <= l[0] + pad.getWidth() && cy >= l[1] && cy <= l[1] + pad.getHeight();
    }

    void dispatch(int action) {
        if (overPad()) return;
        long t = SystemClock.uptimeMillis();
        if (action == MotionEvent.ACTION_DOWN) downTime = t;
        MotionEvent ev = MotionEvent.obtain(downTime, t, action, cx, cy, 0);
        ev.setSource(InputDevice.SOURCE_TOUCHSCREEN);
        root.dispatchTouchEvent(ev);
        ev.recycle();
    }

    void click() { dispatch(MotionEvent.ACTION_DOWN); dispatch(MotionEvent.ACTION_UP); }

    void longClick() {
        dispatch(MotionEvent.ACTION_DOWN);
        a.h.postDelayed(() -> dispatch(MotionEvent.ACTION_UP), 650);
    }

    void scroll(float dy) {
        if (overPad()) return;
        float v = (natural() ? dy : -dy) / a.dp(14);
        long t = SystemClock.uptimeMillis();
        MotionEvent.PointerProperties[] pp = {new MotionEvent.PointerProperties()};
        pp[0].id = 0;
        pp[0].toolType = MotionEvent.TOOL_TYPE_MOUSE;
        MotionEvent.PointerCoords[] pc = {new MotionEvent.PointerCoords()};
        pc[0].x = cx; pc[0].y = cy;
        pc[0].setAxisValue(MotionEvent.AXIS_VSCROLL, v);
        MotionEvent ev = MotionEvent.obtain(t, t, MotionEvent.ACTION_SCROLL, 1, pp, pc, 0, 0, 1f, 1f, 0, 0, InputDevice.SOURCE_MOUSE, 0);
        root.dispatchGenericMotionEvent(ev);
        ev.recycle();
    }
}
