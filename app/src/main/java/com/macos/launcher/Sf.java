package com.macos.launcher;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.widget.ImageView;

/** Apple SF Symbols (converted from the supplied SVG set to vector drawables). */
public class Sf {
    public static Drawable get(Context c, String name, int color) {
        int id = c.getResources().getIdentifier("sf_" + name.replace('.', '_'), "drawable", c.getPackageName());
        if (id == 0) return null;
        Drawable d = c.getResources().getDrawable(id, c.getTheme()).mutate();
        d.setTint(color);
        return d;
    }

    public static ImageView img(Context c, String name, int color) {
        ImageView v = new ImageView(c);
        v.setScaleType(ImageView.ScaleType.FIT_CENTER);
        Drawable d = get(c, name, color);
        if (d != null) v.setImageDrawable(d);
        return v;
    }

    /** Drawable scaled so its height is hPx (width keeps the symbol's aspect). */
    public static Drawable sized(Context c, String name, int color, int hPx) {
        Drawable d = get(c, name, color);
        if (d == null) return null;
        float r = d.getIntrinsicWidth() / (float) Math.max(1, d.getIntrinsicHeight());
        d.setBounds(0, 0, Math.round(hPx * r), hPx);
        return d;
    }
}
