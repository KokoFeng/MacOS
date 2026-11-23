package com.macos.launcher;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.view.Gravity;
import android.view.View;
import android.view.animation.PathInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

/** macOS-style alert: app icon, bold title, small message, full-width pill buttons. */
public class MacAlert {
    public static FrameLayout show(final Context c, final FrameLayout parent, String title, String msg,
                                   String[] labels, final Runnable[] actions, boolean cancelable) {
        final FrameLayout sc = new FrameLayout(c);
        sc.setBackgroundColor(0x59000000);
        sc.setClickable(true);
        int w = Ui.dp(c, 292);
        LinearLayout card = new LinearLayout(c);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER_HORIZONTAL);
        card.setPadding(Ui.dp(c, 22), Ui.dp(c, 22), Ui.dp(c, 22), Ui.dp(c, 18));
        card.setBackground(Ui.rr(Theme.dark ? 0xF2303033 : 0xF2F7F7F9, Ui.dp(c, 26)));
        card.setElevation(Ui.dp(c, 24));
        card.setClickable(true);
        try {
            Drawable d = c.getResources().getDrawable(c.getResources().getIdentifier("mac_finder", "drawable", c.getPackageName()), c.getTheme());
            ImageView ic = new ImageView(c);
            ic.setImageDrawable(d);
            card.addView(ic, new LinearLayout.LayoutParams(Ui.dp(c, 58), Ui.dp(c, 58)));
        } catch (Throwable t) { }
        TextView tt = Ui.tv(c, title, 15, Theme.fg(), true);
        tt.setGravity(Gravity.CENTER);
        tt.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(-1, -2);
        tl.topMargin = Ui.dp(c, 12);
        card.addView(tt, tl);
        TextView mm = Ui.tv(c, msg, 12.5f, Theme.dark ? 0xFFD1D1D6 : 0xFF3C3C43, false);
        mm.setGravity(Gravity.CENTER);
        mm.setLineSpacing(0, 1.08f);
        LinearLayout.LayoutParams ml = new LinearLayout.LayoutParams(-1, -2);
        ml.topMargin = Ui.dp(c, 6);
        card.addView(mm, ml);
        for (int i = 0; i < labels.length; i++) {
            final int k = i;
            TextView b = Ui.tv(c, labels[i], 13.5f, i == 0 ? Color.WHITE : Theme.fg(), true);
            b.setGravity(Gravity.CENTER);
            b.setBackground(Ui.rr(i == 0 ? Theme.accent : (Theme.dark ? 0x33FFFFFF : 0x14000000), Ui.dp(c, 17)));
            LinearLayout.LayoutParams bl = new LinearLayout.LayoutParams(-1, Ui.dp(c, 34));
            bl.topMargin = Ui.dp(c, i == 0 ? 16 : 8);
            b.setOnClickListener(v -> {
                dismiss(sc, parent);
                if (actions != null && k < actions.length && actions[k] != null) actions[k].run();
            });
            card.addView(b, bl);
        }
        sc.addView(card, new FrameLayout.LayoutParams(w, -2, Gravity.CENTER));
        if (cancelable) sc.setOnClickListener(v -> dismiss(sc, parent));
        parent.addView(sc, new FrameLayout.LayoutParams(-1, -1));
        sc.setAlpha(0f);
        card.setScaleX(1.12f); card.setScaleY(1.12f);
        sc.animate().alpha(1f).setDuration(160).start();
        card.animate().scaleX(1f).scaleY(1f).setDuration(320).setInterpolator(new PathInterpolator(.2f, 1.1f, .3f, 1f)).start();
        return sc;
    }

    static void dismiss(final View sc, final FrameLayout parent) {
        sc.animate().alpha(0f).setDuration(140).withEndAction(() -> parent.removeView(sc)).start();
    }
}
