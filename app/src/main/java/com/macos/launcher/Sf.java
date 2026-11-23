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

    /** Drawable scaled so its longest side is boxPx (aspect kept). */
    public static Drawable sized(Context c, String name, int color, int boxPx) {
        Drawable d = get(c, name, color);
        if (d == null) return null;
        float iw = Math.max(1, d.getIntrinsicWidth()), ih = Math.max(1, d.getIntrinsicHeight());
        float k = boxPx / Math.max(iw, ih);
        d.setBounds(0, 0, Math.max(1, Math.round(iw * k)), Math.max(1, Math.round(ih * k)));
        return d;
    }
}
