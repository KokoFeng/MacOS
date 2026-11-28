package com.macos.launcher;

import android.animation.ValueAnimator;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Outline;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.drawable.GradientDrawable;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.PathInterpolator;
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
    private final GradientDrawable bgd;
    private float rad;
    private boolean zoomed;
    private float sx, sy, savedX, savedY;
    private int sw, sh;
    private Bitmap snap;
    private float snapX, snapY, snapW, snapH;
    private long lastUp;

    public MacWindow(MainActivity act, String appName, View toolbar, String title, View body) {
        this(act, appName, toolbar, title, body, Theme.bg());
    }

    public MacWindow(MainActivity act, String appName, View toolbar, String title, View body, int bgColor) {
        super(act);
        a = act;
        this.appName = appName;
        rad = a.dp(24);
        bgd = Ui.rr(bgColor, rad);
        bgd.setStroke(1, Theme.sep());
        setBackground(bgd);
        setOutlineProvider(new ViewOutlineProvider() {
            @Override public void getOutline(View v, Outline o) { o.setRoundRect(0, 0, v.getWidth(), v.getHeight(), rad); }
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
            float ox, oy, dx0, dy0;
            boolean drag;
            @Override public boolean onTouch(View v, MotionEvent e) {
                switch (e.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        ox = getX() - e.getRawX(); oy = getY() - e.getRawY();
                        dx0 = e.getRawX(); dy0 = e.getRawY(); drag = false;
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        if (Math.abs(e.getRawX() - dx0) + Math.abs(e.getRawY() - dy0) > a.dp(6)) drag = true;
                        if (!zoomed && drag) { setX(e.getRawX() + ox); setY(Math.max(a.mbH(), e.getRawY() + oy)); }
                        return true;
                    case MotionEvent.ACTION_UP:
                        if (!drag) {
                            long now = SystemClock.uptimeMillis();
                            if (now - lastUp < 320) { lastUp = 0; titleDouble(); } else lastUp = now;
                        }
                        return true;
                }
                return true;
            }
        });

        // resize from the right / bottom / left edges and the corners
        edge(Gravity.RIGHT | Gravity.CENTER_VERTICAL, 1, 0, 12, -1);
        edge(Gravity.LEFT | Gravity.CENTER_VERTICAL, -1, 0, 12, -1);
        edge(Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL, 0, 1, -1, 12);
        edge(Gravity.BOTTOM | Gravity.LEFT, -1, 1, 26, 26);
        edge(Gravity.BOTTOM | Gravity.RIGHT, 1, 1, 30, 30);
        View gl = new View(a) {
            final android.graphics.Paint p = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
            @Override protected void onDraw(android.graphics.Canvas c) {
                p.setColor(Theme.dark ? 0x66FFFFFF : 0x66000000);
                p.setStrokeWidth(a.dp(1.4f));
                p.setStrokeCap(android.graphics.Paint.Cap.ROUND);
                float w = getWidth(), h = getHeight();
                for (int k = 1; k <= 3; k++) c.drawLine(w - a.dp(3) - k * a.dp(4), h - a.dp(3), w - a.dp(3), h - a.dp(3) - k * a.dp(4), p);
            }
        };
        gl.setEnabled(false);
        addView(gl, new FrameLayout.LayoutParams(a.dp(26), a.dp(26), Gravity.BOTTOM | Gravity.RIGHT));
        setActive(true);
    }

    /** sx/sy: -1 grows to the left/top, 1 to the right/bottom, 0 not at all. Sizes in dp, -1 = fill. */
    private void edge(int gravity, final int sx, final int sy, int wdp, int hdp) {
        View e = new View(a);
        e.setOnTouchListener(new View.OnTouchListener() {
            float rx, ry, x0, y0; int w0, h0;
            @Override public boolean onTouch(View v, MotionEvent ev) {
                if (zoomed) return true;
                if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(true);
                ViewGroup.LayoutParams lp = getLayoutParams();
                switch (ev.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        rx = ev.getRawX(); ry = ev.getRawY(); w0 = getWidth(); h0 = getHeight(); x0 = getX(); y0 = getY();
                        return true;
                    case MotionEvent.ACTION_MOVE: {
                        float dx = ev.getRawX() - rx, dy = ev.getRawY() - ry;
                        int minW = a.dp(220), minH = a.dp(150);
                        if (sx != 0) {
                            int nw = Math.max(minW, Math.min(a.sw, w0 + (int) (sx * dx)));
                            lp.width = nw;
                            if (sx < 0) setX(x0 + (w0 - nw));
                        }
                        if (sy != 0) lp.height = Math.max(minH, Math.min(a.sh - a.mbH(), h0 + (int) (sy * dy)));
                        requestLayout();
                        return true;
                    }
                }
                return true;
            }
        });
        int w = wdp < 0 ? ViewGroup.LayoutParams.MATCH_PARENT : a.dp(wdp);
        int h = hdp < 0 ? ViewGroup.LayoutParams.MATCH_PARENT : a.dp(hdp);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(w, h, gravity);
        // keep side strips clear of the title bar and of the corner handles
        if (wdp < 0) { lp.leftMargin = a.dp(26); lp.rightMargin = a.dp(30); }
        if (hdp < 0) { lp.topMargin = a.dp(50); lp.bottomMargin = a.dp(30); }
        addView(e, lp);
    }

    private void titleDouble() {
        String act = a.prefs.getString("title_dbl", "Zoom");
        if (act.equals("Zoom")) toggleZoom(); else if (act.equals("Minimize")) minimize();
    }

    private boolean genieOn() { return a.prefs.getString("min_effect", "Genie").equals("Genie"); }
    public boolean isZoomed() { return zoomed; }

    private void setRad(float r) { rad = r; bgd.setCornerRadius(r); invalidateOutline(); }

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

    /** macOS Tahoe open: quick zoom-in from ~86% with a long ease-out tail. */
    public void playOpen(int ww, int hh) {
        setPivotX(ww / 2f); setPivotY(hh / 2f);
        setAlpha(0f); setScaleX(.86f); setScaleY(.86f);
        animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(320)
                .setInterpolator(new PathInterpolator(.16f, 1f, .3f, 1f)).start();
    }

    /** macOS close: shrink slightly and fade out fast. */
    public void close() {
        setPivotX(getWidth() / 2f); setPivotY(getHeight() / 2f);
        animate().scaleX(.9f).scaleY(.9f).alpha(0).setDuration(170).setInterpolator(new AccelerateInterpolator(1.4f))
                .withEndAction(() -> {
                    if (getParent() != null) ((ViewGroup) getParent()).removeView(this);
                    if (onClose != null) onClose.run();
                    a.onWindowClosed(this);
                }).start();
    }

    private void genie(final boolean reverse, final Runnable done) {
        final float[] tg = a.dockTarget(appName);
        if (!reverse) {
            int w = getWidth(), h = getHeight();
            float sc = Math.min(1f, 900f / Math.max(w, h));
            snap = Bitmap.createBitmap(Math.max(1, (int) (w * sc)), Math.max(1, (int) (h * sc)), Bitmap.Config.ARGB_8888);
            Canvas c = new Canvas(snap);
            c.scale(sc, sc);
            Path clip = new Path();
            clip.addRoundRect(new RectF(0, 0, w, h), rad, rad, Path.Direction.CW);
            c.clipPath(clip);
            draw(c);
            snapX = getX(); snapY = getY(); snapW = w; snapH = h;
        }
        final GenieView gv = new GenieView(a, snap, snapX, snapY, snapW, snapH, tg[0], tg[1]);
        a.root.addView(gv, a.root.indexOfChild(a.winLayer) + 1, new FrameLayout.LayoutParams(MainActivity.MATCH, MainActivity.MATCH));
        if (!reverse) setVisibility(INVISIBLE);
        ValueAnimator va = reverse ? ValueAnimator.ofFloat(1f, 0f) : ValueAnimator.ofFloat(0f, 1f);
        va.setDuration(540);
        va.setInterpolator(new AccelerateDecelerateInterpolator());
        va.addUpdateListener(an -> gv.setT((Float) an.getAnimatedValue()));
        va.addListener(new android.animation.AnimatorListenerAdapter() {
            @Override public void onAnimationEnd(android.animation.Animator an) {
                a.root.removeView(gv);
                done.run();
            }
        });
        gv.setT(reverse ? 1f : 0f);
        va.start();
    }

    public void minimize() {
        if (minimized) return;
        minimized = true;
        if (zoomed) { /* keep zoom state; restore returns to the same size */ }
        if (genieOn() && getWidth() > 0 && getHeight() > 0) {
            genie(false, () -> { setVisibility(GONE); a.onWindowMinimized(this); a.updateDock(); });
        } else {
            float[] tg = a.dockTarget(appName);
            setPivotX(tg[0] - getX()); setPivotY(tg[1] - getY());
            animate().scaleX(.06f).scaleY(.06f).alpha(0).setDuration(300).setInterpolator(new AccelerateInterpolator(1.2f))
                    .withEndAction(() -> { setVisibility(GONE); a.onWindowMinimized(this); a.updateDock(); }).start();
        }
    }

    public void restore() {
        if (!minimized) return;
        minimized = false;
        if (snap != null && genieOn()) {
            genie(true, () -> {
                setVisibility(VISIBLE); setAlpha(1f); setScaleX(1f); setScaleY(1f);
                a.focusWindow(this);
                a.updateDock();
            });
        } else {
            setVisibility(VISIBLE);
            animate().scaleX(1).scaleY(1).alpha(1).setDuration(300).setInterpolator(new DecelerateInterpolator()).start();
            a.focusWindow(this);
            a.updateDock();
        }
    }

    /** Green button: fills the whole screen under the menu bar (the Dock slides away, like macOS full screen). */
    public void toggleZoom() {
        final ViewGroup.LayoutParams lp = getLayoutParams();
        final float fx = getX(), fy = getY(), fr = rad;
        final int fw = getWidth(), fh = getHeight();
        final float tx, ty, tr;
        final int tw, th;
        if (!zoomed) {
            sx = fx; sy = fy; sw = fw; sh = fh;
            View par = (View) getParent();
            int pw = par != null && par.getWidth() > 0 ? par.getWidth() : a.sw;
            int ph = par != null && par.getHeight() > 0 ? par.getHeight() : a.sh;
            tx = 0; ty = 0; tw = pw; th = ph; tr = 0;
        } else { tx = sx; ty = sy; tw = sw; th = sh; tr = a.dp(24); }
        zoomed = !zoomed;
        getChildAt(0).setPadding(0, zoomed ? a.topInset : 0, 0, 0);
        a.updateDock();
        ValueAnimator va = ValueAnimator.ofFloat(0f, 1f);
        va.setDuration(300);
        va.setInterpolator(new PathInterpolator(.2f, .9f, .25f, 1f));
        va.addUpdateListener(an -> {
            float t = (float) an.getAnimatedValue();
            setX(fx + (tx - fx) * t);
            setY(fy + (ty - fy) * t);
            lp.width = (int) (fw + (tw - fw) * t);
            lp.height = (int) (fh + (th - fh) * t);
            setRad(fr + (tr - fr) * t);
            requestLayout();
        });
        va.start();
    }
}
