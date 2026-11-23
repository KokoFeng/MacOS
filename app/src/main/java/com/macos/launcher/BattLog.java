package com.macos.launcher;

import android.content.SharedPreferences;
import java.util.ArrayList;
import java.util.List;

/** Hourly battery level + screen-on seconds, kept for 10 days, used by the Battery pane charts. */
public class BattLog {
    static final String KEY = "batt_log";
    static final int KEEP = 240;

    /** Each row: {hourEpoch, level(-1 = none), screenSeconds}. */
    public static List<long[]> load(SharedPreferences p) {
        List<long[]> out = new ArrayList<>();
        for (String line : p.getString(KEY, "").split("\n")) {
            String[] f = line.split(",");
            if (f.length != 3) continue;
            try { out.add(new long[]{Long.parseLong(f[0]), Long.parseLong(f[1]), Long.parseLong(f[2])}); } catch (Exception e) { }
        }
        return out;
    }

    static void save(SharedPreferences p, List<long[]> l) {
        while (l.size() > KEEP) l.remove(0);
        StringBuilder sb = new StringBuilder();
        for (long[] r : l) sb.append(r[0]).append(',').append(r[1]).append(',').append(r[2]).append('\n');
        p.edit().putString(KEY, sb.toString()).apply();
    }

    static long[] bucket(List<long[]> l, long hour) {
        if (!l.isEmpty() && l.get(l.size() - 1)[0] == hour) return l.get(l.size() - 1);
        long[] r = {hour, -1, 0};
        l.add(r);
        return r;
    }

    public static synchronized void sample(SharedPreferences p, int level) {
        List<long[]> l = load(p);
        bucket(l, System.currentTimeMillis() / 3600000L)[1] = level;
        save(p, l);
    }

    public static synchronized void addScreen(SharedPreferences p, long seconds) {
        if (seconds <= 0) return;
        List<long[]> l = load(p);
        long[] b = bucket(l, System.currentTimeMillis() / 3600000L);
        b[2] = Math.min(3600, b[2] + seconds);
        save(p, l);
    }
}
