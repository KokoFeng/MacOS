package com.macos.launcher;

import android.appwidget.AppWidgetHostView;
import android.graphics.Outline;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewOutlineProvider;
import android.widget.FrameLayout;
import android.widget.TextView;

/** Hosts a real Android widget on the desktop. Long-press = edit mode (drag / remove). */
public class WidgetBox extends FrameLayout {
    public final int appWidgetId;
    private final MainActivity a;
    private final TextView badge;
    private boolean edit, moved;
    private float dx, dy, downX, downY;
    private final Runnable lp = new Runnable() { @Override public void run() { enterEdit(); } };

    public WidgetBox(MainActivity act, AppWidgetHostView hv, int id) {
        super(act);
        a = act;
        appWidgetId = id;
        final float r = a.dp(22);
        addView(hv, new FrameLayout.LayoutParams(MainActivity.MATCH, MainActivity.MATCH));
        setOutlineProvider(new ViewOutlineProvider() {
            @Override public void getOutline(View v, Outline o) { o.setRoundRect(0, 0, v.getWidth(), v.getHeight(), r); }
        });
        setClipToOutline(true);
        badge = Ui.tv(act, "−", 18, 0xFFFFFFFF, true);
        badge.setGravity(Gravity.CENTER);
        badge.setBackground(Ui.rr(0xCC3A3A3C, a.dp(14)));
        badge.setVisibility(GONE);
        addView(badge, new FrameLayout.LayoutParams(a.dp(28), a.dp(28), Gravity.TOP | Gravity.LEFT));
    }

    void enterEdit() {
        edit = true;
        badge.setVisibility(VISIBLE);
        animate().scaleX(1.04f).scaleY(1.04f).setDuration(120).start();
        a.setEditingWidget(this);
    }

    public void exitEdit() {
        edit = false;
        badge.setVisibility(GONE);
        animate().scaleX(1f).scaleY(1f).setDuration(120).start();
    }

    @Override public boolean onInterceptTouchEvent(MotionEvent e) {
        if (edit) return true;
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downX = e.getRawX(); downY = e.getRawY();
                postDelayed(lp, 600);
                break;
            case MotionEvent.ACTION_MOVE:
                if (Math.abs(e.getRawX() - downX) > ViewConfiguration.get(a).getScaledTouchSlop()
                        || Math.abs(e.getRawY() - downY) > ViewConfiguration.get(a).getScaledTouchSlop()) removeCallbacks(lp);
                break;
            case MotionEvent.ACTION_UP: case MotionEvent.ACTION_CANCEL:
                removeCallbacks(lp);
                break;
        }
        return false;
    }

    @Override public boolean onTouchEvent(MotionEvent e) {
        if (!edit) return false;
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                dx = getX() - e.getRawX(); dy = getY() - e.getRawY(); moved = false;
                downX = e.getRawX(); downY = e.getRawY();
                return true;
            case MotionEvent.ACTION_MOVE:
                if (Math.abs(e.getRawX() - downX) > 12 || Math.abs(e.getRawY() - downY) > 12) moved = true;
                if (moved) { setX(e.getRawX() + dx); setY(Math.max(a.mbH(), e.getRawY() + dy)); }
                return true;
            case MotionEvent.ACTION_UP:
                if (!moved && e.getX() < a.dp(36) && e.getY() < a.dp(36)) a.removeWidget(this);
                else a.saveWidgets();
                return true;
        }
        return true;
    }
}
