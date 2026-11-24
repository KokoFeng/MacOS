package com.macos.launcher;

import android.content.Context;
import android.graphics.Typeface;
import android.graphics.fonts.Font;
import android.graphics.fonts.FontFamily;
import android.os.Build;
import android.widget.TextView;

/** Apple Color Emoji as the first font; Latin text falls through to the normal system font. */
public class Emoji {
    private static final Typeface[] cache = new Typeface[2];

    public static Typeface tf(Context c, boolean bold) {
        int i = bold ? 1 : 0;
        if (cache[i] != null) return cache[i];
        String fam = bold ? "sans-serif-medium" : "sans-serif";
        Typeface t = null;
        try {
            if (Build.VERSION.SDK_INT >= 29) {
                Font f = new Font.Builder(c.getApplicationContext().getAssets(), "AppleColorEmoji.ttf").build();
                FontFamily ff = new FontFamily.Builder(f).build();
                t = new Typeface.CustomFallbackBuilder(ff).setSystemFallback(fam).build();
                if (bold) t = Typeface.create(t, Typeface.BOLD);
            }
        } catch (Throwable e) { t = null; }
        if (t == null) t = Typeface.create(fam, bold ? Typeface.BOLD : Typeface.NORMAL);
        cache[i] = t;
        return t;
    }

    public static void apply(TextView v) {
        Typeface cur = v.getTypeface();
        v.setTypeface(tf(v.getContext(), cur != null && cur.isBold()));
    }
}
