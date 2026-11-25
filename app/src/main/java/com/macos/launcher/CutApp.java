package com.macos.launcher;

import android.content.ContentValues;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Paint;
import android.graphics.SurfaceTexture;
import android.media.MediaMetadataRetriever;
import android.media.MediaPlayer;
import android.media.PlaybackParams;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import android.text.SpannableString;
import android.text.style.AbsoluteSizeSpan;
import android.text.style.ForegroundColorSpan;
import android.view.Gravity;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.media3.common.Effect;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.audio.AudioProcessor;
import androidx.media3.common.audio.SonicAudioProcessor;
import androidx.media3.effect.Brightness;
import androidx.media3.effect.Contrast;
import androidx.media3.effect.HslAdjustment;
import androidx.media3.effect.OverlayEffect;
import androidx.media3.effect.OverlaySettings;
import androidx.media3.effect.Presentation;
import androidx.media3.effect.RgbAdjustment;
import androidx.media3.effect.RgbFilter;
import androidx.media3.effect.ScaleAndRotateTransformation;
import androidx.media3.effect.SpeedChangeEffect;
import androidx.media3.effect.TextOverlay;
import androidx.media3.effect.TextureOverlay;
import androidx.media3.transformer.Composition;
import androidx.media3.transformer.EditedMediaItem;
import androidx.media3.transformer.EditedMediaItemSequence;
import androidx.media3.transformer.Effects;
import androidx.media3.transformer.ExportException;
import androidx.media3.transformer.ExportResult;
import androidx.media3.transformer.ProgressHolder;
import androidx.media3.transformer.Transformer;
import com.google.common.collect.ImmutableList;
import java.io.File;
import java.io.FileInputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Cut: a video editor. Multiple clips, trim, split, reorder, speed, filters, colour adjustments, text,
 * rotate, aspect ratio and export up to 1080p. Every feature is unlocked.
 */
public class CutApp {
    static class Clip {
        Uri uri;
        long dur, start, end;
        float speed = 1f;
        int filter, bright, contrast, sat, rot, textPos = 2, textSize = 1;
        String text = "";
        Bitmap thumb;
    }

    static final String[] FILTERS = {"Original", "Black & White", "Sepia", "Vivid", "Cool", "Warm", "Fade", "Noir", "Invert"};
    static final float[] SPEEDS = {.25f, .5f, .75f, 1f, 1.5f, 2f, 3f, 4f};
    static final String[] TOOLS = {"Trim", "Split", "Speed", "Filter", "Adjust", "Text", "Rotate", "Move \u25C0", "Move \u25B6", "Delete", "Mute", "Aspect", "Export"};
    static CutApp cur;

    final MainActivity a;
    final List<Clip> clips = new ArrayList<>();
    Clip sel;
    String tool = "Trim";
    boolean muted, fill = true, closed, exporting;
    String aspect = "16:9";
    int quality = 720;
    MediaPlayer mp;
    boolean prepared, playing, seeking;
    FrameLayout previewBox;
    TextureView tex;
    TextView overlayText, timeTv, status;
    SeekBar pos;
    TextView playBtn;
    LinearLayout timeline, panel, toolsRow;
    ProgressBar expBar;
    Transformer transformer;
    File outFile;

    public static void open(MainActivity a) { cur = new CutApp(a); }

    CutApp(MainActivity act) {
        a = act;
        LinearLayout col = new LinearLayout(a);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setBackgroundColor(Theme.dark ? 0xFF161618 : 0xFFF2F2F5);
        LinearLayout top = new LinearLayout(a);
        previewBox = new FrameLayout(a);
        previewBox.setBackgroundColor(0xFF000000);
        tex = new TextureView(a);
        previewBox.addView(tex, new FrameLayout.LayoutParams(-1, -1, Gravity.CENTER));
        overlayText = Ui.tv(a, "", 16, Color.WHITE, true);
        overlayText.setShadowLayer(a.dp(4), 0, 0, 0xCC000000);
        overlayText.setGravity(Gravity.CENTER);
        previewBox.addView(overlayText, new FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL) {{ bottomMargin = a.dp(14); }});
        tex.setSurfaceTextureListener(new TextureView.SurfaceTextureListener() {
            public void onSurfaceTextureAvailable(SurfaceTexture s, int w, int h) { if (sel != null) loadClip(); }
            public void onSurfaceTextureSizeChanged(SurfaceTexture s, int w, int h) {}
            public boolean onSurfaceTextureDestroyed(SurfaceTexture s) { return true; }
            public void onSurfaceTextureUpdated(SurfaceTexture s) {}
        });
        top.addView(previewBox, new LinearLayout.LayoutParams(0, -1, 3f));
        panel = new LinearLayout(a);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(a.dp(12), a.dp(6), a.dp(12), a.dp(8));
        ScrollView ps = new ScrollView(a);
        ps.setBackgroundColor(Theme.side());
        ps.setVerticalScrollBarEnabled(false);
        ps.addView(panel);
        top.addView(ps, new LinearLayout.LayoutParams(0, -1, 2f));
        col.addView(top, new LinearLayout.LayoutParams(-1, 0, 1f));

        LinearLayout ctl = new LinearLayout(a);
        ctl.setGravity(Gravity.CENTER_VERTICAL);
        ctl.setPadding(a.dp(10), a.dp(2), a.dp(10), a.dp(2));
        playBtn = Ui.tv(a, "\u25B6", 16, Theme.fg(), false);
        playBtn.setGravity(Gravity.CENTER);
        playBtn.setOnClickListener(v -> togglePlay());
        ctl.addView(playBtn, new LinearLayout.LayoutParams(a.dp(34), a.dp(34)));
        pos = new SeekBar(a);
        pos.setMax(1000);
        pos.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar s, int p, boolean u) {
                if (u && sel != null && prepared) { mp.seekTo((int) (sel.start + (sel.end - sel.start) * p / 1000L)); }
            }
            public void onStartTrackingTouch(SeekBar s) { seeking = true; }
            public void onStopTrackingTouch(SeekBar s) { seeking = false; }
        });
        ctl.addView(pos, new LinearLayout.LayoutParams(0, -2, 1f));
        timeTv = Ui.tv(a, "0:00 / 0:00", 11.5f, Theme.sub(), false);
        ctl.addView(timeTv);
        col.addView(ctl, new LinearLayout.LayoutParams(-1, -2));

        HorizontalScrollView th = new HorizontalScrollView(a);
        th.setHorizontalScrollBarEnabled(false);
        th.setBackgroundColor(Theme.dark ? 0xFF1E1E21 : 0xFFE8E8EC);
        timeline = new LinearLayout(a);
        timeline.setGravity(Gravity.CENTER_VERTICAL);
        timeline.setPadding(a.dp(8), a.dp(5), a.dp(8), a.dp(5));
        th.addView(timeline);
        col.addView(th, new LinearLayout.LayoutParams(-1, a.dp(68)));

        HorizontalScrollView tsv = new HorizontalScrollView(a);
        tsv.setHorizontalScrollBarEnabled(false);
        toolsRow = new LinearLayout(a);
        toolsRow.setPadding(a.dp(8), a.dp(5), a.dp(8), a.dp(5));
        tsv.addView(toolsRow);
        col.addView(tsv, new LinearLayout.LayoutParams(-1, a.dp(40)));

        MacWindow w = new MacWindow(a, "Feng Cut", null, "Feng Cut", col);
        w.onClose = () -> { closed = true; releasePlayer(); if (transformer != null) { try { transformer.cancel(); } catch (Exception e) { } } if (cur == this) cur = null; };
        a.openWindow(w, 800, 370);
        drawTools();
        drawTimeline();
        drawPanel();
        a.h.post(tick);
    }

    TextView chip(String s, boolean on) {
        TextView t = Ui.tv(a, s, 12, on ? Color.WHITE : Theme.fg(), on);
        t.setGravity(Gravity.CENTER);
        t.setBackground(Ui.rr(on ? Theme.accent : (Theme.dark ? 0x2EFFFFFF : 0x14000000), a.dp(14)));
        return t;
    }

    // ---------- tools ----------
    void drawTools() {
        toolsRow.removeAllViews();
        for (final String t : TOOLS) {
            boolean on = t.equals(tool) || (t.equals("Mute") && muted);
            TextView c = chip(t.equals("Mute") ? (muted ? "Muted" : "Mute") : t, on);
            c.setPadding(a.dp(14), 0, a.dp(14), 0);
            c.setOnClickListener(v -> toolTap(t));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, a.dp(30));
            lp.rightMargin = a.dp(6);
            toolsRow.addView(c, lp);
        }
    }

    void toolTap(String t) {
        switch (t) {
            case "Rotate": if (need()) { sel.rot = (sel.rot + 90) % 360; layoutPreview(); } break;
            case "Delete": if (need()) { int i = clips.indexOf(sel); clips.remove(sel); sel = clips.isEmpty() ? null : clips.get(Math.min(i, clips.size() - 1)); releasePlayer(); if (sel != null) loadClip(); drawTimeline(); } break;
            case "Move \u25C0": if (need()) move(-1); break;
            case "Move \u25B6": if (need()) move(1); break;
            case "Mute": muted = !muted; break;
            case "Split": if (need()) split(); break;
            default: tool = t;
        }
        drawTools();
        drawPanel();
    }

    boolean need() {
        if (sel == null) { Toast.makeText(a, "Add a clip first", Toast.LENGTH_SHORT).show(); return false; }
        return true;
    }

    void move(int d) {
        int i = clips.indexOf(sel), j = i + d;
        if (j < 0 || j >= clips.size()) return;
        clips.remove(i);
        clips.add(j, sel);
        drawTimeline();
    }

    void split() {
        if (!prepared) return;
        long p = mp.getCurrentPosition();
        if (p <= sel.start + 300 || p >= sel.end - 300) { Toast.makeText(a, "Move the playhead inside the clip to split", Toast.LENGTH_SHORT).show(); return; }
        Clip n = copy(sel);
        n.start = p;
        sel.end = p;
        clips.add(clips.indexOf(sel) + 1, n);
        drawTimeline();
    }

    Clip copy(Clip c) {
        Clip n = new Clip();
        n.uri = c.uri; n.dur = c.dur; n.start = c.start; n.end = c.end; n.speed = c.speed; n.filter = c.filter; n.bright = c.bright;
        n.contrast = c.contrast; n.sat = c.sat; n.rot = c.rot; n.textPos = c.textPos; n.textSize = c.textSize; n.text = c.text; n.thumb = c.thumb;
        return n;
    }

    // ---------- panel ----------
    interface IntCb { void on(int v); }

    void slider(String name, int min, int max, int val, final IntCb cb) {
        TextView t = Ui.tv(a, name, 12, Theme.fg(), false);
        t.setPadding(0, a.dp(6), 0, 0);
        panel.addView(t);
        SeekBar s = new SeekBar(a);
        s.setMax(max - min);
        s.setProgress(val - min);
        final int mn = min;
        s.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar sb, int p, boolean u) { if (u) cb.on(p + mn); }
            public void onStartTrackingTouch(SeekBar sb) {}
            public void onStopTrackingTouch(SeekBar sb) {}
        });
        panel.addView(s);
    }

    TextView pbtn(String s, boolean on, Runnable r) {
        TextView t = chip(s, on);
        t.setOnClickListener(v -> r.run());
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, a.dp(30));
        lp.topMargin = a.dp(5);
        panel.addView(t, lp);
        return t;
    }

    void drawPanel() {
        panel.removeAllViews();
        TextView h = Ui.tv(a, tool, 14, Theme.fg(), true);
        panel.addView(h);
        if (clips.isEmpty() && !tool.equals("Export") && !tool.equals("Aspect")) {
            TextView t = Ui.tv(a, "Tap + in the timeline to add videos from your phone.", 12.5f, Theme.sub(), false);
            t.setPadding(0, a.dp(8), 0, 0);
            panel.addView(t);
            return;
        }
        switch (tool) {
            case "Trim":
                slider("Start  " + MusicApp.fmt((int) sel.start), 0, (int) sel.dur, (int) sel.start, v -> { sel.start = Math.min(v, sel.end - 300); seekTo(sel.start); drawPanel(); drawTimeline(); });
                slider("End  " + MusicApp.fmt((int) sel.end), 0, (int) sel.dur, (int) sel.end, v -> { sel.end = Math.max(v, sel.start + 300); seekTo(sel.end - 300); drawPanel(); drawTimeline(); });
                break;
            case "Speed":
                for (final float f : SPEEDS) pbtn(f + "x", sel.speed == f, () -> { sel.speed = f; applySpeed(); drawPanel(); });
                break;
            case "Filter":
                for (int i = 0; i < FILTERS.length; i++) { final int k = i; pbtn(FILTERS[i], sel.filter == i, () -> { sel.filter = k; applyColor(); drawPanel(); }); }
                break;
            case "Adjust":
                slider("Brightness", -100, 100, sel.bright, v -> { sel.bright = v; applyColor(); });
                slider("Contrast", -100, 100, sel.contrast, v -> { sel.contrast = v; applyColor(); });
                slider("Saturation", -100, 100, sel.sat, v -> { sel.sat = v; applyColor(); });
                break;
            case "Text":
                pbtn(sel.text.isEmpty() ? "Add text" : "Edit text", true, () -> a.prompt("Text on this clip", sel.text, s -> { sel.text = s; applyText(); drawPanel(); }));
                String[] pn = {"Top", "Middle", "Bottom"};
                for (int i = 0; i < 3; i++) { final int k = i; pbtn(pn[i], sel.textPos == i, () -> { sel.textPos = k; applyText(); drawPanel(); }); }
                String[] sn = {"Small", "Medium", "Large"};
                for (int i = 0; i < 3; i++) { final int k = i; pbtn("Size: " + sn[i], sel.textSize == i, () -> { sel.textSize = k; applyText(); drawPanel(); }); }
                break;
            case "Aspect":
                for (final String s : new String[]{"Original", "16:9", "9:16", "1:1"}) pbtn(s, aspect.equals(s), () -> { aspect = s; drawPanel(); });
                pbtn(fill ? "Fill frame (crop)" : "Fit frame (bars)", false, () -> { fill = !fill; drawPanel(); });
                break;
            case "Export":
                for (final int q : new int[]{480, 720, 1080}) pbtn(q + "p", quality == q, () -> { quality = q; drawPanel(); });
                pbtn(exporting ? "Exporting\u2026" : "Export video", true, this::export);
                expBar = new ProgressBar(a, null, android.R.attr.progressBarStyleHorizontal);
                expBar.setMax(100);
                expBar.setVisibility(exporting ? View.VISIBLE : View.GONE);
                panel.addView(expBar, new LinearLayout.LayoutParams(-1, a.dp(10)));
                status = Ui.tv(a, "", 12, Theme.sub(), false);
                panel.addView(status);
                break;
            default:
                break;
        }
    }

    // ---------- timeline ----------
    void drawTimeline() {
        timeline.removeAllViews();
        for (final Clip c : clips) {
            FrameLayout f = new FrameLayout(a);
            f.setBackground(Ui.rr(c == sel ? Theme.accent : 0xFF3A3A3E, a.dp(8)));
            f.setPadding(a.dp(2), a.dp(2), a.dp(2), a.dp(2));
            ImageView iv = new ImageView(a);
            iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
            iv.setBackgroundColor(0xFF222226);
            iv.setClipToOutline(true);
            if (c.thumb != null) iv.setImageBitmap(c.thumb);
            f.addView(iv, new FrameLayout.LayoutParams(a.dp(88), a.dp(54)));
            TextView d = Ui.tv(a, MusicApp.fmt((int) ((c.end - c.start) / c.speed)), 10.5f, Color.WHITE, true);
            d.setBackground(Ui.rr(0x99000000, a.dp(6)));
            d.setPadding(a.dp(5), 0, a.dp(5), 0);
            f.addView(d, new FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM | Gravity.RIGHT) {{ rightMargin = a.dp(5); bottomMargin = a.dp(5); }});
            f.setOnClickListener(v -> { if (sel != c) { sel = c; loadClip(); drawTimeline(); drawPanel(); } });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
            lp.rightMargin = a.dp(6);
            timeline.addView(f, lp);
        }
        TextView add = Ui.tv(a, "+", 26, Theme.fg(), false);
        add.setGravity(Gravity.CENTER);
        add.setBackground(Ui.rr(Theme.dark ? 0x2EFFFFFF : 0x14000000, a.dp(8)));
        add.setOnClickListener(v -> a.pick("video/*", true, this::addClips));
        timeline.addView(add, new LinearLayout.LayoutParams(a.dp(58), a.dp(58)));
    }

    void addClips(final List<Uri> uris) {
        new Thread(() -> {
            final List<Clip> got = new ArrayList<>();
            for (Uri u : uris) {
                try {
                    MediaMetadataRetriever r = new MediaMetadataRetriever();
                    r.setDataSource(a, u);
                    String d = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
                    Clip c = new Clip();
                    c.uri = u; c.dur = d == null ? 0 : Long.parseLong(d); c.start = 0; c.end = c.dur;
                    Bitmap b = r.getFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC);
                    if (b != null) c.thumb = Bitmap.createScaledBitmap(b, 176, Math.max(1, 176 * b.getHeight() / b.getWidth()), true);
                    r.release();
                    if (c.dur > 500) got.add(c);
                } catch (Exception e) { }
            }
            a.runOnUiThread(() -> {
                if (closed) return;
                if (got.isEmpty()) { Toast.makeText(a, "Couldn\u2019t read those videos", Toast.LENGTH_SHORT).show(); return; }
                clips.addAll(got);
                sel = got.get(0);
                loadClip();
                drawTimeline();
                drawPanel();
            });
        }).start();
    }

    // ---------- preview ----------
    void releasePlayer() {
        playing = false; prepared = false;
        if (mp != null) { try { mp.release(); } catch (Exception e) { } mp = null; }
    }

    void loadClip() {
        releasePlayer();
        if (sel == null || !tex.isAvailable()) return;
        try {
            mp = new MediaPlayer();
            mp.setDataSource(a, sel.uri);
            mp.setSurface(new Surface(tex.getSurfaceTexture()));
            mp.setOnVideoSizeChangedListener((m, w, h) -> layoutPreview());
            mp.setOnPreparedListener(m -> { prepared = true; m.seekTo((int) sel.start); applyColor(); applyText(); layoutPreview(); playBtn.setText("\u25B6"); });
            mp.prepareAsync();
        } catch (Exception e) { Toast.makeText(a, "Can\u2019t preview this video", Toast.LENGTH_SHORT).show(); }
    }

    void layoutPreview() {
        if (mp == null || sel == null) return;
        int vw = mp.getVideoWidth(), vh = mp.getVideoHeight();
        if (vw == 0 || vh == 0) return;
        boolean odd = sel.rot % 180 != 0;
        float cw = previewBox.getWidth(), ch = previewBox.getHeight();
        float dw = odd ? vh : vw, dh = odd ? vw : vh;
        float k = Math.min(cw / dw, ch / dh);
        float bw = dw * k, bh = dh * k;
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(Math.round(odd ? bh : bw), Math.round(odd ? bw : bh), Gravity.CENTER);
        tex.setLayoutParams(lp);
        tex.setRotation(sel.rot);
    }

    ColorMatrix colorMatrix(Clip c) {
        ColorMatrix cm = new ColorMatrix();
        cm.setSaturation(1f + c.sat / 100f);
        float k = 1f + c.contrast / 100f, t = (1f - k) * 128f + c.bright * 2.55f;
        cm.postConcat(new ColorMatrix(new float[]{k, 0, 0, 0, t, 0, k, 0, 0, t, 0, 0, k, 0, t, 0, 0, 0, 1, 0}));
        ColorMatrix f = new ColorMatrix();
        switch (c.filter) {
            case 1: f.setSaturation(0); break;
            case 2: f.set(new float[]{.393f, .769f, .189f, 0, 0, .349f, .686f, .168f, 0, 0, .272f, .534f, .131f, 0, 0, 0, 0, 0, 1, 0}); break;
            case 3: f.setSaturation(1.6f); break;
            case 4: f.set(new float[]{.9f, 0, 0, 0, 0, 0, 1, 0, 0, 0, 0, 0, 1.15f, 0, 8, 0, 0, 0, 1, 0}); break;
            case 5: f.set(new float[]{1.15f, 0, 0, 0, 8, 0, 1.02f, 0, 0, 0, 0, 0, .85f, 0, 0, 0, 0, 0, 1, 0}); break;
            case 6: f.setSaturation(.7f); f.postConcat(new ColorMatrix(new float[]{.85f, 0, 0, 0, 30, 0, .85f, 0, 0, 30, 0, 0, .85f, 0, 30, 0, 0, 0, 1, 0})); break;
            case 7: f.setSaturation(0); f.postConcat(new ColorMatrix(new float[]{1.5f, 0, 0, 0, -60, 0, 1.5f, 0, 0, -60, 0, 0, 1.5f, 0, -60, 0, 0, 0, 1, 0})); break;
            case 8: f.set(new float[]{-1, 0, 0, 0, 255, 0, -1, 0, 0, 255, 0, 0, -1, 0, 255, 0, 0, 0, 1, 0}); break;
            default: break;
        }
        cm.postConcat(f);
        return cm;
    }

    void applyColor() {
        if (sel == null) return;
        Paint p = new Paint();
        p.setColorFilter(new ColorMatrixColorFilter(colorMatrix(sel)));
        tex.setLayerType(View.LAYER_TYPE_HARDWARE, p);
    }

    void applyText() {
        if (sel == null) return;
        overlayText.setText(sel.text);
        overlayText.setTextSize(new float[]{12, 17, 24}[sel.textSize]);
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) overlayText.getLayoutParams();
        lp.gravity = (sel.textPos == 0 ? Gravity.TOP : sel.textPos == 1 ? Gravity.CENTER_VERTICAL : Gravity.BOTTOM) | Gravity.CENTER_HORIZONTAL;
        overlayText.setLayoutParams(lp);
    }

    void applySpeed() {
        if (mp != null && playing) {
            try { PlaybackParams pp = new PlaybackParams(); pp.setSpeed(sel.speed); mp.setPlaybackParams(pp); } catch (Exception e) { }
        }
        drawTimeline();
    }

    void seekTo(long ms) { if (prepared && mp != null) mp.seekTo((int) Math.max(0, ms)); }

    void togglePlay() {
        if (!prepared || mp == null || sel == null) { if (clips.isEmpty()) Toast.makeText(a, "Add a clip first", Toast.LENGTH_SHORT).show(); return; }
        if (playing) { mp.pause(); playing = false; playBtn.setText("\u25B6"); return; }
        if (mp.getCurrentPosition() >= sel.end - 100) mp.seekTo((int) sel.start);
        try { PlaybackParams pp = new PlaybackParams(); pp.setSpeed(sel.speed); mp.setPlaybackParams(pp); } catch (Exception e) { }
        mp.start();
        playing = true;
        playBtn.setText("\u23F8");
    }

    final Runnable tick = new Runnable() {
        @Override public void run() {
            if (closed) return;
            try {
                if (prepared && mp != null && sel != null) {
                    int p = mp.getCurrentPosition();
                    if (playing && p >= sel.end) { mp.pause(); mp.seekTo((int) sel.start); playing = false; playBtn.setText("\u25B6"); p = (int) sel.start; }
                    long len = Math.max(1, sel.end - sel.start);
                    if (!seeking) pos.setProgress((int) (Math.max(0, Math.min(len, p - sel.start)) * 1000 / len));
                    timeTv.setText(MusicApp.fmt((int) Math.max(0, p - sel.start)) + " / " + MusicApp.fmt((int) len));
                }
                if (exporting && transformer != null) {
                    ProgressHolder ph = new ProgressHolder();
                    if (transformer.getProgress(ph) == Transformer.PROGRESS_STATE_AVAILABLE && expBar != null) { expBar.setProgress(ph.progress); if (status != null) status.setText(ph.progress + "%"); }
                }
            } catch (Exception e) { }
            a.h.postDelayed(this, 120);
        }
    };

    // ---------- export ----------
    int[] outSize() {
        float ar = aspect.equals("16:9") ? 16f / 9 : aspect.equals("9:16") ? 9f / 16 : 1f;
        if (aspect.equals("Original")) return null;
        int h = quality, w;
        if (aspect.equals("16:9")) { w = Math.round(h * 16f / 9) / 2 * 2; }
        else if (aspect.equals("9:16")) { w = h; h = Math.round(quality * 16f / 9) / 2 * 2; }
        else w = h;
        return new int[]{w, h};
    }

    EditedMediaItem buildItem(Clip c) {
        MediaItem mi = new MediaItem.Builder().setUri(c.uri)
                .setClippingConfiguration(new MediaItem.ClippingConfiguration.Builder().setStartPositionMs(c.start).setEndPositionMs(c.end).build()).build();
        List<Effect> v = new ArrayList<>();
        List<AudioProcessor> au = new ArrayList<>();
        if (c.rot != 0) v.add(new ScaleAndRotateTransformation.Builder().setRotationDegrees(-c.rot).build());
        if (c.speed != 1f) {
            v.add(new SpeedChangeEffect(c.speed));
            SonicAudioProcessor sp = new SonicAudioProcessor();
            sp.setSpeed(c.speed);
            sp.setPitch(1f);
            au.add(sp);
        }
        switch (c.filter) {
            case 1: case 7: v.add(RgbFilter.createGrayscaleFilter()); break;
            case 2: v.add(RgbFilter.createGrayscaleFilter()); v.add(new RgbAdjustment.Builder().setRedScale(1.15f).setGreenScale(1f).setBlueScale(.75f).build()); break;
            case 3: v.add(new HslAdjustment.Builder().adjustSaturation(45).build()); break;
            case 4: v.add(new RgbAdjustment.Builder().setRedScale(.88f).setBlueScale(1.15f).build()); break;
            case 5: v.add(new RgbAdjustment.Builder().setRedScale(1.15f).setBlueScale(.85f).build()); break;
            case 6: v.add(new Contrast(-.3f)); v.add(new Brightness(.1f)); break;
            case 8: v.add(RgbFilter.createInvertedFilter()); break;
            default: break;
        }
        if (c.filter == 7) v.add(new Contrast(.45f));
        if (c.bright != 0) v.add(new Brightness(c.bright / 100f));
        if (c.contrast != 0) v.add(new Contrast(c.contrast / 100f));
        if (c.sat != 0) v.add(new HslAdjustment.Builder().adjustSaturation(c.sat).build());
        if (!c.text.trim().isEmpty()) {
            SpannableString ss = new SpannableString(c.text);
            ss.setSpan(new ForegroundColorSpan(Color.WHITE), 0, ss.length(), 0);
            ss.setSpan(new AbsoluteSizeSpan(new int[]{34, 54, 80}[c.textSize]), 0, ss.length(), 0);
            OverlaySettings os = new OverlaySettings.Builder().setBackgroundFrameAnchor(0f, c.textPos == 0 ? .8f : c.textPos == 1 ? 0f : -.8f).build();
            v.add(new OverlayEffect(ImmutableList.<TextureOverlay>of(TextOverlay.createStaticTextOverlay(ss, os))));
        }
        int[] sz = outSize();
        if (sz != null) v.add(Presentation.createForWidthAndHeight(sz[0], sz[1], fill ? Presentation.LAYOUT_SCALE_TO_FIT_WITH_CROP : Presentation.LAYOUT_SCALE_TO_FIT));
        else v.add(Presentation.createForHeight(quality));
        return new EditedMediaItem.Builder(mi).setRemoveAudio(muted).setEffects(new Effects(au, v)).build();
    }

    void export() {
        if (exporting) return;
        if (clips.isEmpty()) { Toast.makeText(a, "Add a clip first", Toast.LENGTH_SHORT).show(); return; }
        try {
            releasePlayer();
            List<EditedMediaItem> items = new ArrayList<>();
            for (Clip c : clips) items.add(buildItem(c));
            Composition comp = new Composition.Builder(ImmutableList.of(new EditedMediaItemSequence(items))).build();
            outFile = new File(a.getCacheDir(), "cut_" + System.currentTimeMillis() + ".mp4");
            transformer = new Transformer.Builder(a)
                    .setVideoMimeType(MimeTypes.VIDEO_H264)
                    .setAudioMimeType(MimeTypes.AUDIO_AAC)
                    .addListener(new Transformer.Listener() {
                        @Override public void onCompleted(Composition composition, ExportResult result) { finishExport(true, null); }
                        @Override public void onError(Composition composition, ExportResult result, ExportException e) { finishExport(false, e.getMessage()); }
                    }).build();
            exporting = true;
            drawPanel();
            transformer.start(comp, outFile.getAbsolutePath());
        } catch (Throwable t) {
            exporting = false;
            Toast.makeText(a, "Export failed: " + t.getMessage(), Toast.LENGTH_LONG).show();
            drawPanel();
        }
    }

    void finishExport(final boolean ok, final String err) {
        exporting = false;
        if (closed) return;
        if (!ok) { Toast.makeText(a, "Export failed" + (err == null ? "" : ": " + err), Toast.LENGTH_LONG).show(); drawPanel(); if (sel != null) loadClip(); return; }
        final File f = outFile;
        new Thread(() -> {
            Uri u = null;
            try {
                String name = "Feng Cut " + new SimpleDateFormat("yyyy-MM-dd HH.mm.ss", Locale.US).format(new Date()) + ".mp4";
                ContentValues v = new ContentValues();
                v.put(MediaStore.Video.Media.DISPLAY_NAME, name);
                v.put(MediaStore.Video.Media.MIME_TYPE, "video/mp4");
                if (Build.VERSION.SDK_INT >= 29) v.put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/Feng Cut");
                else {
                    File dir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES), "Feng Cut");
                    dir.mkdirs();
                    v.put(MediaStore.Video.Media.DATA, new File(dir, name).getAbsolutePath());
                }
                u = a.getContentResolver().insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, v);
                try (OutputStream os = a.getContentResolver().openOutputStream(u); FileInputStream in = new FileInputStream(f)) {
                    byte[] buf = new byte[65536]; int n;
                    while ((n = in.read(buf)) > 0) os.write(buf, 0, n);
                }
                f.delete();
            } catch (Exception e) { u = null; }
            final boolean saved = u != null;
            a.runOnUiThread(() -> {
                Toast.makeText(a, saved ? "Saved to Photos" : "Couldn\u2019t save the video", Toast.LENGTH_LONG).show();
                if (!closed) { drawPanel(); if (sel != null) loadClip(); }
            });
        }).start();
    }
}
