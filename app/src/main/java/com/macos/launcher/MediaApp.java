package com.macos.launcher;

import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Typeface;
import android.media.MediaPlayer;
import android.net.Uri;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.VideoView;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;

/** Plays video/audio, shows images and text files inside the launcher, in its own window. */
public class MediaApp {
    /** kind: "video", "audio", "image", "text" or null when only another app can open it. */
    public static String kind(String name, String mime) {
        String n = name == null ? "" : name.toLowerCase();
        String m = mime == null ? "" : mime;
        if (m.startsWith("video/") || n.matches(".*\\.(mp4|mkv|webm|3gp|mov|avi|m4v|ts)$")) return "video";
        if (m.startsWith("audio/") || n.matches(".*\\.(mp3|m4a|aac|wav|ogg|opus|flac|amr)$")) return "audio";
        if (m.startsWith("image/") && !m.contains("svg") || n.matches(".*\\.(png|jpg|jpeg|gif|webp|bmp)$")) return "image";
        if (m.startsWith("text/") || m.equals("application/json") || m.equals("application/xml")
                || n.matches(".*\\.(txt|md|json|xml|csv|log|java|kt|py|js|html|css|c|cpp|h|sh|yml|yaml|ini|conf|gradle|properties)$")) return "text";
        return null;
    }

    public static boolean open(MainActivity a, Uri u, String name, String mime) {
        String k = kind(name, mime);
        if (k == null) return false;
        switch (k) {
            case "video": case "audio": player(a, u, name, k.equals("video")); break;
            case "image": image(a, u, name); break;
            default: text(a, u, name);
        }
        return true;
    }

    static String fmt(int ms) {
        int s = Math.max(0, ms / 1000);
        return (s / 60) + ":" + (s % 60 < 10 ? "0" : "") + (s % 60);
    }

    // ------------------------------------------------------------------ video / audio
    static class PV extends VideoView {
        PV(android.content.Context c) { super(c); }
        @Override protected void onDetachedFromWindow() { try { stopPlayback(); } catch (Exception e) { } super.onDetachedFromWindow(); }
    }

    static void player(final MainActivity a, Uri u, String name, boolean video) {
        final FrameLayout root = new FrameLayout(a);
        root.setBackgroundColor(Color.BLACK);
        final PV vv = new PV(a);
        root.addView(vv, new FrameLayout.LayoutParams(-1, -1, Gravity.CENTER));
        if (!video) {
            LinearLayout c = new LinearLayout(a);
            c.setOrientation(LinearLayout.VERTICAL);
            c.setGravity(Gravity.CENTER);
            TextView note = Ui.tv(a, "\u266B", 64, 0xFFFFFFFF, true);
            note.setGravity(Gravity.CENTER);
            c.addView(note);
            TextView t = Ui.tv(a, name == null ? "" : name, 14, 0xCCFFFFFF, true);
            t.setGravity(Gravity.CENTER);
            t.setPadding(a.dp(20), 0, a.dp(20), 0);
            c.addView(t);
            root.addView(c, new FrameLayout.LayoutParams(-1, -1, Gravity.CENTER));
        }
        // controls
        final LinearLayout bar = new LinearLayout(a);
        bar.setOrientation(LinearLayout.VERTICAL);
        bar.setBackground(Ui.rr(0xB31C1C1E, a.dp(18)));
        bar.setPadding(a.dp(14), a.dp(8), a.dp(14), a.dp(8));
        final SeekBar sb = new SeekBar(a);
        sb.setMax(1000);
        bar.addView(sb, new LinearLayout.LayoutParams(-1, a.dp(26)));
        LinearLayout row = new LinearLayout(a);
        row.setGravity(Gravity.CENTER_VERTICAL);
        final TextView cur = Ui.tv(a, "0:00", 11, 0xFFFFFFFF, false);
        final TextView play = Ui.tv(a, "\u275A\u275A", 18, 0xFFFFFFFF, true);
        play.setGravity(Gravity.CENTER);
        final TextView tot = Ui.tv(a, "0:00", 11, 0xFFFFFFFF, false);
        tot.setGravity(Gravity.RIGHT);
        TextView back = Ui.tv(a, "\u23EA 10", 12, 0xFFFFFFFF, true);
        TextView fwd = Ui.tv(a, "10 \u23E9", 12, 0xFFFFFFFF, true);
        row.addView(cur, new LinearLayout.LayoutParams(a.dp(44), -2));
        row.addView(new View(a), new LinearLayout.LayoutParams(0, 1, 1f));
        row.addView(back, new LinearLayout.LayoutParams(-2, -2));
        LinearLayout.LayoutParams pl = new LinearLayout.LayoutParams(a.dp(56), a.dp(30));
        row.addView(play, pl);
        row.addView(fwd, new LinearLayout.LayoutParams(-2, -2));
        row.addView(new View(a), new LinearLayout.LayoutParams(0, 1, 1f));
        row.addView(tot, new LinearLayout.LayoutParams(a.dp(44), -2));
        bar.addView(row);
        FrameLayout.LayoutParams bl = new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM);
        bl.setMargins(a.dp(12), 0, a.dp(12), a.dp(12));
        root.addView(bar, bl);

        final Runnable hide = () -> bar.animate().alpha(0f).setDuration(250).start();
        final Runnable[] tick = new Runnable[1];
        final boolean[] seeking = {false};
        tick[0] = () -> {
            if (!root.isAttachedToWindow()) return;
            try {
                int d = vv.getDuration(), p = vv.getCurrentPosition();
                if (d > 0 && !seeking[0]) sb.setProgress((int) (1000L * p / d));
                cur.setText(fmt(p));
                tot.setText(fmt(d));
                play.setText(vv.isPlaying() ? "\u275A\u275A" : "\u25B6");
            } catch (Exception e) { }
            root.postDelayed(tick[0], 400);
        };
        play.setOnClickListener(v -> { if (vv.isPlaying()) vv.pause(); else vv.start(); root.removeCallbacks(hide); root.postDelayed(hide, 3500); });
        back.setOnClickListener(v -> vv.seekTo(Math.max(0, vv.getCurrentPosition() - 10000)));
        fwd.setOnClickListener(v -> vv.seekTo(Math.min(vv.getDuration(), vv.getCurrentPosition() + 10000)));
        sb.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar s, int p, boolean user) { if (user && vv.getDuration() > 0) vv.seekTo((int) (vv.getDuration() * (long) p / 1000L)); }
            @Override public void onStartTrackingTouch(SeekBar s) { seeking[0] = true; }
            @Override public void onStopTrackingTouch(SeekBar s) { seeking[0] = false; }
        });
        root.setOnClickListener(v -> {
            bar.animate().cancel();
            if (bar.getAlpha() < .5f) { bar.animate().alpha(1f).setDuration(150).start(); root.removeCallbacks(hide); root.postDelayed(hide, 3500); }
            else bar.animate().alpha(0f).setDuration(200).start();
        });
        vv.setOnPreparedListener((MediaPlayer mp) -> { vv.start(); root.removeCallbacks(hide); root.postDelayed(hide, 3500); });
        vv.setOnErrorListener((mp, w, e) -> {
            Toast.makeText(a, L.t("This file can't be played"), Toast.LENGTH_SHORT).show();
            return true;
        });
        vv.setOnCompletionListener(mp -> { bar.animate().alpha(1f).setDuration(150).start(); play.setText("\u25B6"); });
        vv.setVideoURI(u);
        MacWindow w = new MacWindow(a, "Media", null, name == null ? "Media" : name, root, Color.BLACK);
        a.openWindow(w, video ? 560 : 420, video ? 330 : 240);
        root.post(tick[0]);
    }

    // ------------------------------------------------------------------ image
    static void image(final MainActivity a, Uri u, String name) {
        final ImageView iv = new ImageView(a);
        iv.setScaleType(ImageView.ScaleType.MATRIX);
        iv.setBackgroundColor(0xFF111113);
        try {
            InputStream in = a.getContentResolver().openInputStream(u);
            android.graphics.BitmapFactory.Options o = new android.graphics.BitmapFactory.Options();
            o.inSampleSize = 1;
            o.inJustDecodeBounds = true;
            android.graphics.BitmapFactory.decodeStream(in, null, o);
            in.close();
            int ss = 1;
            while (o.outWidth / ss > 2400 || o.outHeight / ss > 2400) ss *= 2;
            o.inJustDecodeBounds = false;
            o.inSampleSize = ss;
            in = a.getContentResolver().openInputStream(u);
            final android.graphics.Bitmap bmp = android.graphics.BitmapFactory.decodeStream(in, null, o);
            in.close();
            iv.setImageBitmap(bmp);
            final Matrix m = new Matrix();
            final float[] base = {1f};
            final Runnable fit = () -> {
                if (bmp == null || iv.getWidth() == 0) return;
                base[0] = Math.min(iv.getWidth() / (float) bmp.getWidth(), iv.getHeight() / (float) bmp.getHeight());
                m.reset();
                m.postScale(base[0], base[0]);
                m.postTranslate((iv.getWidth() - bmp.getWidth() * base[0]) / 2f, (iv.getHeight() - bmp.getHeight() * base[0]) / 2f);
                iv.setImageMatrix(m);
            };
            iv.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> fit.run());
            final ScaleGestureDetector sg = new ScaleGestureDetector(a, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
                @Override public boolean onScale(ScaleGestureDetector d) { m.postScale(d.getScaleFactor(), d.getScaleFactor(), d.getFocusX(), d.getFocusY()); iv.setImageMatrix(m); return true; }
            });
            iv.setOnTouchListener(new View.OnTouchListener() {
                float lx, ly;
                long lastTap;
                @Override public boolean onTouch(View v, MotionEvent e) {
                    sg.onTouchEvent(e);
                    if (v.getParent() != null) v.getParent().requestDisallowInterceptTouchEvent(true);
                    switch (e.getActionMasked()) {
                        case MotionEvent.ACTION_DOWN:
                            lx = e.getX(); ly = e.getY();
                            long now = System.currentTimeMillis();
                            if (now - lastTap < 300) fit.run();
                            lastTap = now;
                            break;
                        case MotionEvent.ACTION_MOVE:
                            if (!sg.isInProgress() && e.getPointerCount() == 1) { m.postTranslate(e.getX() - lx, e.getY() - ly); iv.setImageMatrix(m); }
                            lx = e.getX(); ly = e.getY();
                            break;
                    }
                    return true;
                }
            });
        } catch (Exception e) {
            Toast.makeText(a, L.t("This file can't be opened"), Toast.LENGTH_SHORT).show();
            return;
        }
        MacWindow w = new MacWindow(a, "Preview", null, name == null ? "Preview" : name, iv, 0xFF111113);
        a.openWindow(w, 520, 340);
    }

    // ------------------------------------------------------------------ text
    static void text(final MainActivity a, Uri u, String name) {
        String s;
        try {
            InputStream in = a.getContentResolver().openInputStream(u);
            byte[] buf = new byte[1024 * 1024];
            int n = 0, r;
            while (n < buf.length && (r = in.read(buf, n, buf.length - n)) > 0) n += r;
            in.close();
            s = new String(buf, 0, n, "UTF-8");
        } catch (Exception e) {
            Toast.makeText(a, L.t("This file can't be opened"), Toast.LENGTH_SHORT).show();
            return;
        }
        ScrollView sv = new ScrollView(a);
        TextView t = new TextView(a);
        t.setText(s);
        t.setTextIsSelectable(true);
        t.setTypeface(Typeface.MONOSPACE);
        t.setTextSize(12);
        t.setTextColor(Theme.fg());
        t.setPadding(a.dp(14), a.dp(10), a.dp(14), a.dp(14));
        sv.addView(t);
        MacWindow w = new MacWindow(a, "TextEdit", null, name == null ? "TextEdit" : name, sv);
        a.openWindow(w, 520, 340);
    }
}
