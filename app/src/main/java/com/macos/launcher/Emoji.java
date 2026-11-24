package com.macos.launcher;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.AssetManager;
import android.graphics.Typeface;
import android.graphics.fonts.Font;
import android.graphics.fonts.FontFamily;
import android.os.Build;
import android.widget.TextView;
import java.util.HashMap;
import java.util.Map;

/**
 * App-wide text font. Normal text uses the font the user picked (System / iOS / iOS Rounded);
 * Apple Color Emoji is only a fallback for emoji glyphs.
 */
public class Emoji {
    public static final String[] FONTS = {"iOS", "iOS Rounded", "System"};
    private static Context app;
    private static SharedPreferences sp;
    private static final Typeface[] cur = new Typeface[2];
    private static final Map<String, Typeface> memo = new HashMap<>();

    public static void init(Context c) {
        if (app != null) return;
        app = c.getApplicationContext();
        sp = app.getSharedPreferences("mac", 0);
    }

    public static void reset() { cur[0] = null; cur[1] = null; memo.clear(); }

    public static String name() { return sp == null ? "iOS" : sp.getString("ui_font", "iOS"); }

    public static Typeface font(boolean bold) {
        int i = bold ? 1 : 0;
        if (cur[i] == null) cur[i] = faceFor(name(), bold);
        return cur[i];
    }

    public static Typeface tf(Context c, boolean bold) {
        init(c);
        return font(bold);
    }

    public static void apply(TextView v) {
        Typeface t = v.getTypeface();
        v.setTypeface(tf(v.getContext(), t != null && t.isBold()));
    }

    private static Font f(AssetManager am, String path, int weight) throws Exception {
        return new Font.Builder(am, path).setWeight(weight).build();
    }

    public static Typeface faceFor(String n, boolean bold) {
        String key = n + (bold ? "B" : "N");
        Typeface m = memo.get(key);
        if (m != null) return m;
        Typeface t = null;
        try {
            if (Build.VERSION.SDK_INT >= 29 && app != null) {
                AssetManager am = app.getAssets();
                FontFamily emoji = new FontFamily.Builder(new Font.Builder(am, "AppleColorEmoji.ttf").build()).build();
                Typeface.CustomFallbackBuilder b;
                int w = 0;
                if (n.equals("iOS")) {
                    FontFamily txt = new FontFamily.Builder(f(am, "fonts/ios_regular.ttf", 400))
                            .addFont(f(am, "fonts/ios_medium.ttf", 500)).addFont(f(am, "fonts/ios_bold.ttf", 700)).build();
                    b = new Typeface.CustomFallbackBuilder(txt).addCustomFallback(emoji);
                    w = bold ? 700 : 400;
                } else if (n.equals("iOS Rounded")) {
                    FontFamily txt = new FontFamily.Builder(f(am, "fonts/rounded_regular.ttf", 400))
                            .addFont(f(am, "fonts/rounded_semibold.ttf", 600)).addFont(f(am, "fonts/rounded_bold.ttf", 700)).build();
                    b = new Typeface.CustomFallbackBuilder(txt).addCustomFallback(emoji);
                    w = bold ? 600 : 400;
                } else {
                    b = new Typeface.CustomFallbackBuilder(emoji);
                }
                t = b.setSystemFallback(bold && w == 0 ? "sans-serif-medium" : "sans-serif").build();
                if (w > 0) t = Typeface.create(t, w, false);
                else if (bold) t = Typeface.create(t, Typeface.BOLD);
            } else if (app != null && !n.equals("System")) {
                String p = n.equals("iOS") ? (bold ? "fonts/ios_bold.ttf" : "fonts/ios_regular.ttf") : (bold ? "fonts/rounded_semibold.ttf" : "fonts/rounded_regular.ttf");
                t = Typeface.createFromAsset(app.getAssets(), p);
            }
        } catch (Throwable e) { t = null; }
        if (t == null) t = Typeface.create(bold ? "sans-serif-medium" : "sans-serif", bold ? Typeface.BOLD : Typeface.NORMAL);
        memo.put(key, t);
        return t;
    }
}
