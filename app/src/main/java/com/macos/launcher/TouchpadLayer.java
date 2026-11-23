package com.macos.launcher;

import android.graphics.*;
import android.os.SystemClock;
import android.view.InputDevice;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;

/**
 * Full-screen virtual trackpad. The whole screen is the touch surface (no pad box):
 * 1 finger = move cursor, tap = click, hold+drag = drag, 2 fingers = scroll, 2-finger tap = right click.
 * Events are injected into MainActivity.root (a sibling of this layer), so there is no touch recursion.
 */
public class TouchpadLayer {
    final MainActivity a;
    final FrameLayout screen;
    View cursor, capture;
    boolean on;
    boolean injecting;
    float cx, cy;
    long downTime;

    public TouchpadLayer(MainActivity act) {
        a = act; screen = a.screen;
        cx = a.sw / 2f; cy = a.sh / 2f;
    }

    public void setEnabled(boolean e) {
        on = e;
        if (e) build(); else remove();
    }

    void remove() {
        if (cursor != null) { screen.removeView(cursor); cursor = null; }
        if (capture != null) { screen.removeView(capture); capture = null; }
        a.dockPointer(0, 0, false);
    }

    /** Layers live in {@code screen}, always above the launcher content. */
    public void toFront() {
        if (!on) return;
        if (capture != null) capture.bringToFront();
        if (cursor != null) cursor.bringToFront();
    }

    float speed() { return a.prefs.getFloat("tp_speed", 1.6f); }
    boolean tap() { return a.prefs.getBoolean("tp_tap", true); }
    boolean natural() { return a.prefs.getBoolean("tp_natural", true); }
    boolean secondary() { return a.prefs.getBoolean("tp_secondary", true); }

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
        cursor.setElevation(a.dp(60));

        capture = new View(a);
        capture.setOnTouchListener(new View.OnTouchListener() {
            float lx, ly, lastSx = Float.NaN, lastSy = Float.NaN;
            long t0;
            boolean moved, holding, two;
            final Runnable hold = new Runnable() {
                @Override public void run() { if (!moved && !two) { holding = true; dispatch(MotionEvent.ACTION_DOWN); } }
            };
            @Override public boolean onTouch(View v, MotionEvent e) {
                switch (e.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        lx = e.getX(); ly = e.getY(); t0 = SystemClock.uptimeMillis();
                        moved = false; holding = false; two = false; lastSx = lastSy = Float.NaN;
                        a.h.postDelayed(hold, a.prefs.getInt("tp_hold", 420));
                        return true;
                    case MotionEvent.ACTION_POINTER_DOWN:
                        two = true; a.h.removeCallbacks(hold); lastSx = lastSy = Float.NaN;
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        if (e.getPointerCount() >= 2) {
                            float mx = (e.getX(0) + e.getX(1)) / 2f, my = (e.getY(0) + e.getY(1)) / 2f;
                            if (!Float.isNaN(lastSy)) {
                                float ddx = mx - lastSx, ddy = my - lastSy;
                                if (Math.abs(ddx) > 0.5f || Math.abs(ddy) > 0.5f) { moved = true; scroll(ddx, ddy); }
                            }
                            lastSx = mx; lastSy = my;
                            return true;
                        }
                        if (two) return true;
                        float dx = e.getX() - lx, dy = e.getY() - ly;
                        float dist = (float) Math.hypot(dx, dy);
                        if (dist > a.dp(3)) { moved = true; a.h.removeCallbacks(hold); }
                        lx = e.getX(); ly = e.getY();
                        // light pointer acceleration: slow = precise, fast = covers the whole screen
                        float gain = speed() * (1f + Math.min(1.6f, dist / a.dp(7)));
                        cx = Math.max(0, Math.min(a.sw - 1, cx + dx * gain));
                        cy = Math.max(0, Math.min(a.sh - 1, cy + dy * gain));
                        place();
                        if (holding) dispatch(MotionEvent.ACTION_MOVE);
                        return true;
                    case MotionEvent.ACTION_POINTER_UP:
                        lastSx = lastSy = Float.NaN;
                        return true;
                    case MotionEvent.ACTION_UP: case MotionEvent.ACTION_CANCEL:
                        a.h.removeCallbacks(hold);
                        long dt = SystemClock.uptimeMillis() - t0;
                        if (holding) { dispatch(MotionEvent.ACTION_UP); holding = false; }
                        else if (!moved && dt < 300 && e.getActionMasked() == MotionEvent.ACTION_UP) {
                            if (two) { if (secondary()) longClick(); } else if (tap()) click();
                        }
                        two = false; lastSx = lastSy = Float.NaN;
                        return true;
                }
                return true;
            }
        });
        screen.addView(capture, new FrameLayout.LayoutParams(MainActivity.MATCH, MainActivity.MATCH));
        screen.addView(cursor, new FrameLayout.LayoutParams(a.dp(26), a.dp(28)));
        place();
    }

    void place() {
        if (cursor == null) return;
        // the arrow tip sits 1dp inside the view, so offset to make the tip the exact hotspot
        cursor.setX(cx - a.den);
        cursor.setY(cy - a.den);
        a.dockPointer(cx, cy, true);
    }

    void dispatch(int action) {
        long t = SystemClock.uptimeMillis();
        if (action == MotionEvent.ACTION_DOWN) downTime = t;
        MotionEvent ev = MotionEvent.obtain(downTime, t, action, cx, cy, 0);
        ev.setSource(InputDevice.SOURCE_TOUCHSCREEN);
        injecting = true;
        try { a.root.dispatchTouchEvent(ev); } finally { injecting = false; }
        ev.recycle();
    }

    void click() { dispatch(MotionEvent.ACTION_DOWN); dispatch(MotionEvent.ACTION_UP); }

    void longClick() {
        dispatch(MotionEvent.ACTION_DOWN);
        a.h.postDelayed(() -> dispatch(MotionEvent.ACTION_UP), 650);
    }

    void scroll(float dx, float dy) {
        float k = natural() ? 1f : -1f;
        float v = k * dy / a.dp(14), hz = -k * dx / a.dp(14);
        long t = SystemClock.uptimeMillis();
        MotionEvent.PointerProperties[] pp = {new MotionEvent.PointerProperties()};
        pp[0].id = 0;
        pp[0].toolType = MotionEvent.TOOL_TYPE_MOUSE;
        MotionEvent.PointerCoords[] pc = {new MotionEvent.PointerCoords()};
        pc[0].x = cx; pc[0].y = cy;
        pc[0].setAxisValue(MotionEvent.AXIS_VSCROLL, v);
        pc[0].setAxisValue(MotionEvent.AXIS_HSCROLL, hz);
        MotionEvent ev = MotionEvent.obtain(t, t, MotionEvent.ACTION_SCROLL, 1, pp, pc, 0, 0, 1f, 1f, 0, 0, InputDevice.SOURCE_MOUSE, 0);
        a.root.dispatchGenericMotionEvent(ev);
        ev.recycle();
    }
}
