package com.macos.launcher;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;

public class Theme {
    public static boolean dark, glassClear, floatApps, showCal = true;
    public static int accent = 0xFF0A84FF, tlClose = 0xFFFF5F57, tlMin = 0xFFFEBC2E, tlZoom = 0xFF28C840, dockSize = 50;
    public static float glassK = 1f;
    public static String appearance = "Light", iconStyle = "Default";

    public static void load(Context c) {
        SharedPreferences p = c.getSharedPreferences("mac", 0);
        appearance = p.getString("appearance", "Light");
        if (appearance.equals("Auto"))
            dark = (c.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
        else dark = appearance.equals("Dark");
        accent = p.getInt("accent", 0xFF0A84FF);
        glassClear = p.getString("glass", "Tinted").equals("Clear");
        glassK = glassClear ? 0.6f : 1f;
        iconStyle = p.getString("iconstyle", "Default");
        tlClose = p.getInt("tl_close", 0xFFFF5F57);
        tlMin = p.getInt("tl_min", 0xFFFEBC2E);
        tlZoom = p.getInt("tl_zoom", 0xFF28C840);
        dockSize = p.getInt("dock_sz", 50);
        floatApps = p.getBoolean("float_apps", false);
        showCal = p.getBoolean("show_cal", true);
    }

    public static int bg() { return dark ? 0xFF1E1E20 : 0xFFFFFFFF; }
    public static int pane() { return dark ? 0xFF1C1C1E : 0xFFF2F2F7; }
    public static int side() { return dark ? 0xFF2A2A2D : 0xFFEDEEF3; }
    public static int card() { return dark ? 0xFF2C2C2E : 0xFFFFFFFF; }
    public static int fg() { return dark ? 0xFFF2F2F7 : 0xFF1D1D1F; }
    public static int sub() { return 0xFF8E8E93; }
    public static int sep() { return dark ? 0x33FFFFFF : 0x1F000000; }
    public static int hover() { return dark ? 0x33FFFFFF : 0x1A000000; }
}
