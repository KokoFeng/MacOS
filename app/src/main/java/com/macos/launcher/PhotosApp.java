package com.macos.launcher;

import android.content.ContentResolver;
import android.content.ContentUris;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.Build;
import android.provider.MediaStore;
import android.util.LruCache;
import android.util.Size;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ScrollView;
import android.widget.TextView;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** macOS Photos-style gallery: sidebar (Library, Pinned, Albums) + grid with Years / Months / All Photos. */
public class PhotosApp {
    static class Media { long id, date, dur; boolean video; String bucket; Uri uri; }
    static class Row { String header; List<Media> tiles; }

    final MainActivity a;
    final List<Media> all = new ArrayList<>();
    List<Media> shown = new ArrayList<>();
    final List<Row> rows = new ArrayList<>();
    String mode = "All Photos", filter = "Library";
    int colDp = 100, contentW = 0;
    BaseAdapter ad;
    ListView lv;
    FrameLayout body;
    LinearLayout sideList;
    LinearLayout seg;
    final LruCache<Long, Bitmap> cache = new LruCache<>(160);
    final ExecutorService ex = Executors.newFixedThreadPool(3);

    public static void open(MainActivity a) { new PhotosApp(a); }

    PhotosApp(MainActivity act) {
        a = act;
        final int M = MainActivity.MATCH, W = MainActivity.WRAP;
        LinearLayout tb = new LinearLayout(a);
        tb.setGravity(Gravity.CENTER_VERTICAL);
        TextView minus = Ui.circleBtn(a, "−"), plus = Ui.circleBtn(a, "+");
        minus.setOnClickListener(v -> { colDp = Math.max(60, colDp - 20); rebuild(); });
        plus.setOnClickListener(v -> { colDp = Math.min(220, colDp + 20); rebuild(); });
        tb.addView(minus, new LinearLayout.LayoutParams(a.dp(30), a.dp(30)));
        LinearLayout.LayoutParams pl = new LinearLayout.LayoutParams(a.dp(30), a.dp(30));
        pl.leftMargin = a.dp(6);
        tb.addView(plus, pl);
        View sp = new View(a);
        tb.addView(sp, new LinearLayout.LayoutParams(0, 1, 1f));
        seg = new LinearLayout(a);
        seg.setBackground(Ui.rr(Theme.dark ? 0x2EFFFFFF : 0x12000000, a.dp(16)));
        seg.setPadding(a.dp(3), a.dp(3), a.dp(3), a.dp(3));
        tb.addView(seg, new LinearLayout.LayoutParams(W, a.dp(32)));
        tb.addView(new View(a), new LinearLayout.LayoutParams(0, 1, 1f));
        drawSeg();

        LinearLayout hb = new LinearLayout(a);
        body = new FrameLayout(a);
        hb.setBackgroundColor(Theme.bg());
        if (!a.hasStorage()) {
            LinearLayout c = new LinearLayout(a);
            c.setOrientation(LinearLayout.VERTICAL);
            c.setGravity(Gravity.CENTER);
            c.addView(Ui.tv(a, "Photos butuh izin akses foto dan file.", 14, Theme.fg(), false));
            TextView b = Ui.tv(a, "Beri Akses", 14, 0xFFFFFFFF, true);
            b.setPadding(a.dp(18), a.dp(8), a.dp(18), a.dp(8));
            b.setBackground(Ui.rr(Theme.accent, a.dp(16)));
            b.setOnClickListener(v -> a.requestStorage());
            LinearLayout.LayoutParams bl = new LinearLayout.LayoutParams(W, W);
            bl.topMargin = a.dp(12);
            c.addView(b, bl);
            c.addView(Ui.tv(a, "Setelah diizinkan, tutup lalu buka Photos lagi.", 11, Theme.sub(), false));
            body.addView(c, new FrameLayout.LayoutParams(M, M));
            a.openWindow(new MacWindow(a, "Photos", null, "Photos", body), 640, 320);
            return;
        }
        sideList = new LinearLayout(a);
        sideList.setOrientation(LinearLayout.VERTICAL);
        sideList.setPadding(a.dp(10), a.dp(4), a.dp(10), a.dp(10));
        ScrollView sv = new ScrollView(a);
        sv.setBackgroundColor(Theme.side());
        sv.setVerticalScrollBarEnabled(false);
        sv.addView(sideList);
        hb.addView(sv, new LinearLayout.LayoutParams(a.dp(160), M));

        lv = new ListView(a);
        lv.setDivider(new ColorDrawable(0));
        lv.setDividerHeight(0);
        lv.setPadding(a.dp(6), 0, a.dp(6), a.dp(6));
        lv.setClipToPadding(false);
        ad = new BaseAdapter() {
            @Override public int getCount() { return rows.size(); }
            @Override public Object getItem(int i) { return rows.get(i); }
            @Override public long getItemId(int i) { return i; }
            @Override public View getView(int i, View cv, ViewGroup p) { return rowView(rows.get(i)); }
        };
        lv.setAdapter(ad);
        lv.addOnLayoutChangeListener((v, l, t, r, bt, ol, ot, or, ob) -> {
            int w = r - l;
            if (w != contentW) { contentW = w; rebuild(); }
        });
        body.addView(lv, new FrameLayout.LayoutParams(M, M));
        hb.addView(body, new LinearLayout.LayoutParams(0, M, 1f));
        a.openWindow(new MacWindow(a, "Photos", tb, null, hb), 660, 330);
        drawSide();
        new Thread(() -> {
            load();
            a.runOnUiThread(() -> { applyFilter(); drawSide(); });
        }).start();
    }

    // ---------- data ----------
    void load() {
        ContentResolver cr = a.getContentResolver();
        add(cr, MediaStore.Images.Media.EXTERNAL_CONTENT_URI, false);
        add(cr, MediaStore.Video.Media.EXTERNAL_CONTENT_URI, true);
        Collections.sort(all, (x, y) -> Long.compare(y.date, x.date));
    }

    void add(ContentResolver cr, Uri base, boolean video) {
        String[] proj = video
                ? new String[]{"_id", "datetaken", "date_added", "bucket_display_name", "duration"}
                : new String[]{"_id", "datetaken", "date_added", "bucket_display_name"};
        try (Cursor c = cr.query(base, proj, null, null, "date_added DESC")) {
            if (c == null) return;
            int n = 0;
            while (c.moveToNext() && n++ < 4000) {
                Media m = new Media();
                m.id = c.getLong(0);
                long dt = c.getLong(1);
                m.date = dt > 0 ? dt : c.getLong(2) * 1000L;
                m.bucket = c.getString(3) == null ? "Other" : c.getString(3);
                m.video = video;
                if (video) m.dur = c.getLong(4);
                m.uri = ContentUris.withAppendedId(base, m.id);
                all.add(m);
            }
        } catch (Exception e) { }
    }

    void applyFilter() {
        shown = new ArrayList<>();
        long now = System.currentTimeMillis();
        for (Media m : all) {
            boolean ok;
            switch (filter) {
                case "Library": case "Collections": ok = true; break;
                case "Recently Saved": ok = now - m.date < 30L * 86400000L; break;
                case "Videos": ok = m.video; break;
                case "Screenshots": ok = m.bucket.toLowerCase(Locale.ROOT).contains("screenshot"); break;
                default: ok = m.bucket.equals(filter);
            }
            if (ok) shown.add(m);
        }
        rebuild();
    }

    void rebuild() {
        if (ad == null || contentW <= 0) return;
        rows.clear();
        int cols = Math.max(2, (contentW - a.dp(12)) / a.dp(colDp));
        if (mode.equals("Years")) cols = cols * 3 / 2 + 1;
        String last = null;
        Row cur = null;
        SimpleDateFormat f = new SimpleDateFormat(mode.equals("Months") ? "MMMM yyyy" : "yyyy", Locale.getDefault());
        for (Media m : shown) {
            String key = mode.equals("All Photos") ? "" : f.format(new Date(m.date));
            if (!key.equals(last)) {
                last = key;
                if (!key.isEmpty()) { Row h = new Row(); h.header = key; rows.add(h); }
                cur = null;
            }
            if (cur == null || cur.tiles.size() >= cols) { cur = new Row(); cur.tiles = new ArrayList<>(); rows.add(cur); }
            cur.tiles.add(m);
        }
        ad.notifyDataSetChanged();
        tileW = (contentW - a.dp(12) - cols * a.dp(4)) / cols;
        ad.notifyDataSetChanged();
    }

    int tileW = 100;

    View rowView(Row r) {
        if (r.header != null) {
            TextView t = Ui.tv(a, r.header, 15, Theme.fg(), true);
            t.setPadding(a.dp(6), a.dp(12), 0, a.dp(4));
            return t;
        }
        LinearLayout l = new LinearLayout(a);
        for (final Media m : r.tiles) {
            FrameLayout f = new FrameLayout(a);
            f.setBackgroundColor(Theme.dark ? 0xFF2C2C2E : 0xFFE5E5EA);
            final ImageView iv = new ImageView(a);
            iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
            f.addView(iv, new FrameLayout.LayoutParams(MainActivity.MATCH, MainActivity.MATCH));
            iv.setTag(m.id);
            Bitmap b = cache.get(m.id);
            if (b != null) iv.setImageBitmap(b);
            else ex.execute(() -> {
                final Bitmap t = thumb(m);
                if (t != null) { cache.put(m.id, t); a.runOnUiThread(() -> { if (iv.getTag() != null && iv.getTag().equals(m.id)) iv.setImageBitmap(t); }); }
            });
            if (m.video) {
                long s = m.dur / 1000;
                TextView d = Ui.tv(a, (s / 60) + ":" + String.format(Locale.US, "%02d", s % 60), 10, 0xFFFFFFFF, true);
                d.setShadowLayer(3, 0, 1, 0xAA000000);
                f.addView(d, new FrameLayout.LayoutParams(MainActivity.WRAP, MainActivity.WRAP, Gravity.BOTTOM | Gravity.RIGHT));
            }
            f.setOnClickListener(v -> view(m));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(tileW, tileW);
            lp.setMargins(a.dp(2), a.dp(2), a.dp(2), a.dp(2));
            l.addView(f, lp);
        }
        return l;
    }

    Bitmap thumb(Media m) {
        try {
            ContentResolver cr = a.getContentResolver();
            if (Build.VERSION.SDK_INT >= 29) return cr.loadThumbnail(m.uri, new Size(256, 256), null);
            if (m.video) return MediaStore.Video.Thumbnails.getThumbnail(cr, m.id, MediaStore.Video.Thumbnails.MINI_KIND, null);
            return MediaStore.Images.Thumbnails.getThumbnail(cr, m.id, MediaStore.Images.Thumbnails.MINI_KIND, null);
        } catch (Exception e) { return null; }
    }

    // ---------- viewer ----------
    void view(final Media m) {
        if (m.video) {
            android.content.Intent i = new android.content.Intent(android.content.Intent.ACTION_VIEW);
            i.setDataAndType(m.uri, "video/*");
            a.startExternal(i);
            return;
        }
        final FrameLayout v = new FrameLayout(a);
        v.setBackgroundColor(0xFF000000);
        v.setClickable(true);
        final ImageView iv = new ImageView(a);
        iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
        v.addView(iv, new FrameLayout.LayoutParams(MainActivity.MATCH, MainActivity.MATCH));
        TextView x = Ui.tv(a, "×", 24, 0xFFFFFFFF, true);
        x.setGravity(Gravity.CENTER);
        x.setOnClickListener(c -> body.removeView(v));
        v.addView(x, new FrameLayout.LayoutParams(a.dp(44), a.dp(44), Gravity.TOP | Gravity.LEFT));
        final int idx = shown.indexOf(m);
        TextView pv = Ui.tv(a, "‹", 30, 0xFFFFFFFF, true), nx = Ui.tv(a, "›", 30, 0xFFFFFFFF, true);
        pv.setGravity(Gravity.CENTER); nx.setGravity(Gravity.CENTER);
        pv.setOnClickListener(c -> { body.removeView(v); if (idx > 0) view(shown.get(idx - 1)); });
        nx.setOnClickListener(c -> { body.removeView(v); if (idx >= 0 && idx < shown.size() - 1) view(shown.get(idx + 1)); });
        v.addView(pv, new FrameLayout.LayoutParams(a.dp(44), MainActivity.MATCH, Gravity.LEFT));
        v.addView(nx, new FrameLayout.LayoutParams(a.dp(44), MainActivity.MATCH, Gravity.RIGHT));
        body.addView(v, new FrameLayout.LayoutParams(MainActivity.MATCH, MainActivity.MATCH));
        new Thread(() -> {
            try {
                BitmapFactory.Options o = new BitmapFactory.Options();
                o.inJustDecodeBounds = true;
                InputStream in = a.getContentResolver().openInputStream(m.uri);
                BitmapFactory.decodeStream(in, null, o);
                in.close();
                int ss = 1;
                while (Math.max(o.outWidth, o.outHeight) / ss > 2048) ss *= 2;
                o = new BitmapFactory.Options();
                o.inSampleSize = ss;
                in = a.getContentResolver().openInputStream(m.uri);
                final Bitmap b = BitmapFactory.decodeStream(in, null, o);
                in.close();
                a.runOnUiThread(() -> iv.setImageBitmap(b));
            } catch (Exception e) { }
        }).start();
    }

    // ---------- chrome ----------
    void drawSeg() {
        seg.removeAllViews();
        for (final String n : new String[]{"Years", "Months", "All Photos"}) {
            boolean sel = mode.equals(n);
            TextView t = Ui.tv(a, n, 12, Theme.fg(), sel);
            t.setGravity(Gravity.CENTER);
            t.setPadding(a.dp(12), 0, a.dp(12), 0);
            if (sel) t.setBackground(Ui.rr(Theme.dark ? 0x59FFFFFF : 0xFFFFFFFF, a.dp(13)));
            t.setOnClickListener(v -> { mode = n; drawSeg(); rebuild(); });
            seg.addView(t, new LinearLayout.LayoutParams(MainActivity.WRAP, MainActivity.MATCH));
        }
    }

    void drawSide() {
        if (sideList == null) return;
        sideList.removeAllViews();
        side("Library", true);
        side("Collections", true);
        head("Pinned");
        side("Recently Saved", true);
        side("Videos", true);
        side("Screenshots", true);
        Set<String> b = new LinkedHashSet<>();
        for (Media m : all) b.add(m.bucket);
        List<String> bl = new ArrayList<>(b);
        Collections.sort(bl, String.CASE_INSENSITIVE_ORDER);
        if (!bl.isEmpty()) head("Albums");
        for (String n : bl) side(n, false);
    }

    void head(String s) {
        TextView t = Ui.tv(a, s, 10.5f, Theme.sub(), true);
        t.setPadding(a.dp(6), a.dp(12), 0, a.dp(3));
        sideList.addView(t);
    }

    void side(final String n, boolean sys) {
        TextView t = Ui.tv(a, n, 12.5f, filter.equals(n) ? Theme.accent : Theme.fg(), filter.equals(n));
        t.setSingleLine(true);
        t.setPadding(a.dp(8), a.dp(6), a.dp(8), a.dp(6));
        if (filter.equals(n)) t.setBackground(Ui.rr(Theme.hover(), a.dp(8)));
        t.setOnClickListener(v -> {
            filter = n;
            if (n.equals("Collections")) mode = "Months"; else if (n.equals("Library")) mode = "All Photos";
            drawSeg(); drawSide(); applyFilter();
        });
        sideList.addView(t);
    }
}
