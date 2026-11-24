package com.macos.launcher;

import android.content.ComponentName;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.*;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import java.io.File;
import java.io.FileOutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Dock/Desktop item persistence + web-app icons (favicon tiles). */
public class Store {
    public static class Item {
        public String type, name, extra;   // type: b = built-in, a = android app, w = web app
        public Item(String t, String n, String e) { type = t; name = n; extra = e == null ? "" : e; }
        public boolean same(Item o) { return type.equals(o.type) && name.equals(o.name) && extra.equals(o.extra); }
    }

    static final Set<String> tried = new HashSet<>();

    public static List<Item> defaults() {
        List<Item> l = new ArrayList<>();
        for (String[] d : MainActivity.DOCK) l.add(new Item("b", d[0], ""));
        return l;
    }

    public static List<Item> load(SharedPreferences p, String key, List<Item> def) {
        String s = p.getString(key, null);
        if (s == null) return new ArrayList<>(def);
        List<Item> out = new ArrayList<>();
        for (String line : s.split("\n")) {
            if (line.isEmpty()) continue;
            String[] f = line.split("\t", -1);
            if (f.length >= 2) out.add(new Item(f[0], f[1], f.length > 2 ? f[2] : ""));
        }
        return out;
    }

    public static void save(SharedPreferences p, String key, List<Item> l) {
        StringBuilder sb = new StringBuilder();
        for (Item i : l)
            sb.append(i.type).append('\t').append(i.name.replace('\t', ' ').replace('\n', ' ')).append('\t')
                    .append(i.extra.replace('\t', ' ').replace('\n', ' ')).append('\n');
        p.edit().putString(key, sb.toString()).apply();
    }

    public static Drawable icon(MainActivity a, Item it) {
        try {
            if (it.type.equals("b")) {
                for (String[] d : MainActivity.DOCK) if (d[0].equals(it.name)) return a.getDrawable(a.res(d[1]));
            } else if (it.type.equals("a")) {
                return a.getPackageManager().getActivityIcon(ComponentName.unflattenFromString(it.extra));
            } else if (it.type.equals("w")) {
                return new BitmapDrawable(a.getResources(), faviconTile(a, it.extra, it.name));
            }
        } catch (Exception e) { }
        return new BitmapDrawable(a.getResources(), letterTile(a, it.name, 128));
    }

    public static Bitmap letterTile(Context c, String name, int px) {
        Bitmap b = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888);
        Canvas cv = new Canvas(b);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        int[] pal = {0xFF0A84FF, 0xFFBF5AF2, 0xFFFF375F, 0xFFFF9F0A, 0xFF30D158, 0xFF5AC8FA, 0xFFFF453A};
        p.setColor(pal[Math.abs(name.hashCode()) % pal.length]);
        cv.drawRoundRect(new RectF(0, 0, px, px), px * .225f, px * .225f, p);
        p.setColor(0xFFFFFFFF);
        p.setTextAlign(Paint.Align.CENTER);
        p.setTypeface(Emoji.font(true));
        p.setTextSize(px * .5f);
        cv.drawText(name.isEmpty() ? "?" : name.substring(0, 1).toUpperCase(), px / 2f, px / 2f - (p.ascent() + p.descent()) / 2f, p);
        return b;
    }

    static String host(String url) {
        String h = null;
        try { h = Uri.parse(url).getHost(); } catch (Exception e) { }
        return h == null ? url : h;
    }

    static File favFile(Context c, String url) {
        return new File(c.getFilesDir(), "fav_" + Integer.toHexString(host(url).hashCode()) + ".png");
    }

    public static Bitmap faviconTile(Context c, String url, String name) {
        File f = favFile(c, url);
        if (f.exists()) {
            Bitmap b = BitmapFactory.decodeFile(f.getPath());
            if (b != null) return b;
        }
        return letterTile(c, name, 128);
    }

    public static void fetchFavicon(final MainActivity a, final String url, final Runnable done) {
        final File f = favFile(a, url);
        if (f.exists() || !tried.add(host(url))) return;
        new Thread(() -> {
            try {
                URL u = new URL("https://www.google.com/s2/favicons?sz=128&domain=" + host(url));
                HttpURLConnection c = (HttpURLConnection) u.openConnection();
                c.setConnectTimeout(6000);
                c.setReadTimeout(6000);
                Bitmap ic = BitmapFactory.decodeStream(c.getInputStream());
                if (ic == null) return;
                Bitmap t = Bitmap.createBitmap(128, 128, Bitmap.Config.ARGB_8888);
                Canvas cv = new Canvas(t);
                Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
                p.setColor(0xFFF5F5F7);
                cv.drawRoundRect(new RectF(0, 0, 128, 128), 29, 29, p);
                cv.drawBitmap(ic, null, new RectF(28, 28, 100, 100), p);
                FileOutputStream fo = new FileOutputStream(f);
                t.compress(Bitmap.CompressFormat.PNG, 100, fo);
                fo.close();
                if (done != null) a.runOnUiThread(done);
            } catch (Exception e) { }
        }).start();
    }
}
