package com.macos.launcher;

import android.animation.ValueAnimator;
import android.graphics.Outline;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Floating macOS-style window: traffic lights, drag by titlebar, resize grip, zoom/minimize/close. */
public class MacWindow extends FrameLayout {
    public final String appName;
    public Runnable onClose;
    public boolean minimized;
    private final MainActivity a;
    private final View[] lights = new View[3];
    private boolean zoomed;
    private float sx, sy, savedY;
    private int sw, sh;

    public MacWindow(MainActivity act, String appName, View toolbar, String title, View body) {
        this(act, appName, toolbar, title, body, Theme.bg());
    }

    public MacWindow(MainActivity act, String appName, View toolbar, String title, View body, int bgColor) {
        super(act);
        a = act;
        this.appName = appName;
        final float radius = a.dp(24);
        GradientDrawable bg = Ui.rr(bgColor, radius);
        bg.setStroke(1, Theme.sep());
        setBackground(bg);
        setOutlineProvider(new ViewOutlineProvider() {
            @Override public void getOutline(View v, Outline o) { o.setRoundRect(0, 0, v.getWidth(), v.getHeight(), radius); }
        });
        setClipToOutline(true);
        setElevation(a.dp(24));

        LinearLayout col = new LinearLayout(a);
        col.setOrientation(LinearLayout.VERTICAL);
        LinearLayout bar = new LinearLayout(a);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(a.dp(16), 0, a.dp(12), 0);
        LinearLayout lg = new LinearLayout(a);
        lg.setGravity(Gravity.CENTER_VERTICAL);
        for (int i = 0; i < 3; i++) {
            View d = new View(a);
            lights[i] = d;
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(a.dp(13), a.dp(13));
            lp.rightMargin = a.dp(8);
            final int k = i;
            d.setOnClickListener(v -> { if (k == 0) close(); else if (k == 1) minimize(); else toggleZoom(); });
            lg.addView(d, lp);
        }
        bar.addView(lg);
        if (toolbar != null) {
            LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(0, MainActivity.MATCH, 1f);
            tl.leftMargin = a.dp(8);
            bar.addView(toolbar, tl);
        } else {
            TextView t = Ui.tv(a, title == null ? appName : title, 13.5f, Theme.fg(), true);
            t.setGravity(Gravity.CENTER);
            bar.addView(t, new LinearLayout.LayoutParams(0, MainActivity.WRAP, 1f));
        }
        col.addView(bar, new LinearLayout.LayoutParams(MainActivity.MATCH, a.dp(46)));
        col.addView(body, new LinearLayout.LayoutParams(MainActivity.MATCH, 0, 1f));
        addView(col, new FrameLayout.LayoutParams(MainActivity.MATCH, MainActivity.MATCH));

        bar.setOnTouchListener(new View.OnTouchListener() {
            float ox, oy;
            @Override public boolean onTouch(View v, MotionEvent e) {
                switch (e.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN: ox = getX() - e.getRawX(); oy = getY() - e.getRawY(); return true;
                    case MotionEvent.ACTION_MOVE:
                        if (!zoomed) { setX(e.getRawX() + ox); setY(Math.max(a.mbH(), e.getRawY() + oy)); }
                        return true;
                }
                return true;
            }
        });

        View grip = new View(a);
        grip.setOnTouchListener(new View.OnTouchListener() {
            float rx, ry; int w0, h0;
            @Override public boolean onTouch(View v, MotionEvent e) {
                if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(true);
                ViewGroup.LayoutParams lp = getLayoutParams();
                switch (e.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN: rx = e.getRawX(); ry = e.getRawY(); w0 = getWidth(); h0 = getHeight(); return true;
                    case MotionEvent.ACTION_MOVE:
                        lp.width = Math.max(a.dp(340), Math.min(a.sw, w0 + (int) (e.getRawX() - rx)));
                        lp.height = Math.max(a.dp(230), Math.min(a.sh, h0 + (int) (e.getRawY() - ry)));
                        requestLayout();
                        return true;
                }
                return true;
            }
        });
        addView(grip, new FrameLayout.LayoutParams(a.dp(28), a.dp(28), Gravity.BOTTOM | Gravity.RIGHT));
        setActive(true);
    }

    public void setActive(boolean act) {
        int[] cols = {Theme.tlClose, Theme.tlMin, Theme.tlZoom};
        for (int i = 0; i < 3; i++) {
            GradientDrawable g = new GradientDrawable();
            g.setShape(GradientDrawable.OVAL);
            g.setColor(act ? cols[i] : (Theme.dark ? 0xFF5A5A5E : 0xFFC9C9CE));
            lights[i].setBackground(g);
        }
        setElevation(a.dp(act ? 26 : 10));
    }

    @Override public boolean dispatchTouchEvent(MotionEvent ev) {
        if (ev.getActionMasked() == MotionEvent.ACTION_DOWN) a.focusWindow(this);
        return super.dispatchTouchEvent(ev);
    }

    public void close() {
        animate().scaleX(.9f).scaleY(.9f).alpha(0).setDuration(130).withEndAction(() -> {
            if (getParent() != null) ((ViewGroup) getParent()).removeView(this);
            if (onClose != null) onClose.run();
            a.onWindowClosed(this);
        }).start();
    }

    public void minimize() {
        minimized = true;
        savedY = getY();
        animate().scaleX(.15f).scaleY(.15f).alpha(0).y(a.sh - a.dp(30)).setDuration(260)
                .withEndAction(() -> { setVisibility(GONE); a.onWindowMinimized(this); }).start();
    }

    public void restore() {
        if (!minimized) return;
        minimized = false;
        setVisibility(VISIBLE);
        animate().scaleX(1).scaleY(1).alpha(1).y(savedY).setDuration(260).setInterpolator(new DecelerateInterpolator()).start();
        a.focusWindow(this);
    }

    public void toggleZoom() {
        final ViewGroup.LayoutParams lp = getLayoutParams();
        final float fx = getX(), fy = getY();
        final int fw = getWidth(), fh = getHeight();
        final float tx, ty;
        final int tw, th;
        if (!zoomed) {
            sx = fx; sy = fy; sw = fw; sh = fh;
            tx = 0; ty = a.mbH(); tw = a.sw; th = a.sh - a.mbH() - a.dp(76);
        } else { tx = sx; ty = sy; tw = sw; th = sh; }
        zoomed = !zoomed;
        ValueAnimator va = ValueAnimator.ofFloat(0f, 1f);
        va.setDuration(240);
        va.setInterpolator(new DecelerateInterpolator());
        va.addUpdateListener(an -> {
            float t = (float) an.getAnimatedValue();
            setX(fx + (tx - fx) * t);
            setY(fy + (ty - fy) * t);
            lp.width = (int) (fw + (tw - fw) * t);
            lp.height = (int) (fh + (th - fh) * t);
            requestLayout();
        });
        va.start();
    }
}
