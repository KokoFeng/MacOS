package com.macos.launcher;

import android.Manifest;
import android.content.ContentUris;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Build;
import android.provider.MediaStore;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Size;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;

/** Music: plays the audio files on the phone, with a library list, search, shuffle and repeat. */
public class MusicApp {
    static class Song { long id; String title, artist; long dur; Uri uri; }
    static MusicApp cur;

    final MainActivity a;
    final List<Song> all = new ArrayList<>();
    final List<Song> shown = new ArrayList<>();
    MediaPlayer mp;
    int idx = -1;
    boolean shuffle, repeat, closed, seeking;
    String query = "";
    FrameLayout host;
    LinearLayout list;
    TextView title, artist, tCur, tTot, playBtn;
    ImageView art;
    SeekBar bar;

    public static void open(MainActivity a) { cur = new MusicApp(a); }

    public static void onPermission(int req, int[] res) {
        if (req == 13 && cur != null && !cur.closed && res.length > 0 && res[0] == PackageManager.PERMISSION_GRANTED) cur.start();
    }

    MusicApp(MainActivity act) {
        a = act;
        host = new FrameLayout(a);
        MacWindow w = new MacWindow(a, "Music", null, "Music", host);
        w.onClose = () -> { closed = true; if (mp != null) { try { mp.release(); } catch (Exception e) { } mp = null; } if (cur == this) cur = null; };
        a.openWindow(w, 700, 340);
        start();
    }

    String perm() { return Build.VERSION.SDK_INT >= 33 ? Manifest.permission.READ_MEDIA_AUDIO : Manifest.permission.READ_EXTERNAL_STORAGE; }

    void start() {
        host.removeAllViews();
        if (a.checkSelfPermission(perm()) != PackageManager.PERMISSION_GRANTED) {
            LinearLayout c = new LinearLayout(a);
            c.setOrientation(LinearLayout.VERTICAL);
            c.setGravity(Gravity.CENTER);
            c.addView(Sf.img(a, "music.note", Theme.sub()), new LinearLayout.LayoutParams(a.dp(40), a.dp(40)));
            TextView t = Ui.tv(a, "Music needs access to the songs on your phone.", 14, Theme.fg(), false);
            t.setPadding(0, a.dp(10), 0, a.dp(10));
            c.addView(t);
            TextView b = Ui.tv(a, "Allow Access", 14, Color.WHITE, true);
            b.setPadding(a.dp(18), a.dp(8), a.dp(18), a.dp(8));
            b.setBackground(Ui.rr(Theme.accent, a.dp(16)));
            b.setOnClickListener(v -> a.requestPermissions(new String[]{perm()}, 13));
            c.addView(b);
            host.addView(c, new FrameLayout.LayoutParams(-2, -2, Gravity.CENTER));
            return;
        }
        LinearLayout body = new LinearLayout(a);
        LinearLayout left = new LinearLayout(a);
        left.setOrientation(LinearLayout.VERTICAL);
        left.setBackgroundColor(Theme.side());
        EditText search = new EditText(a);
        search.setHint("Search");
        search.setSingleLine(true);
        search.setTextSize(13);
        search.setTextColor(Theme.fg());
        search.setHintTextColor(Theme.sub());
        Emoji.apply(search);
        search.setBackground(Ui.rr(Theme.dark ? 0x2EFFFFFF : 0x12000000, a.dp(10)));
        search.setPadding(a.dp(12), 0, a.dp(12), 0);
        search.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int x, int y, int z) {}
            public void onTextChanged(CharSequence s, int x, int y, int z) {}
            public void afterTextChanged(Editable e) { query = e.toString().toLowerCase(Locale.ROOT).trim(); filter(); }
        });
        LinearLayout.LayoutParams sl = new LinearLayout.LayoutParams(-1, a.dp(32));
        sl.setMargins(a.dp(10), a.dp(8), a.dp(10), a.dp(6));
        left.addView(search, sl);
        list = new LinearLayout(a);
        list.setOrientation(LinearLayout.VERTICAL);
        ScrollView sv = new ScrollView(a);
        sv.setVerticalScrollBarEnabled(false);
        sv.addView(list);
        left.addView(sv, new LinearLayout.LayoutParams(-1, 0, 1f));
        body.addView(left, new LinearLayout.LayoutParams(a.dp(260), -1));

        LinearLayout right = new LinearLayout(a);
        right.setOrientation(LinearLayout.VERTICAL);
        right.setGravity(Gravity.CENTER);
        right.setPadding(a.dp(20), a.dp(10), a.dp(20), a.dp(10));
        art = new ImageView(a);
        art.setScaleType(ImageView.ScaleType.CENTER_CROP);
        art.setBackground(Ui.rr(Theme.dark ? 0xFF2C2C2E : 0xFFE5E5EA, a.dp(14)));
        art.setClipToOutline(true);
        art.setImageDrawable(Sf.get(a, "music.note", Theme.sub()));
        art.setPadding(a.dp(40), a.dp(40), a.dp(40), a.dp(40));
        right.addView(art, new LinearLayout.LayoutParams(a.dp(150), a.dp(150)));
        title = Ui.tv(a, "Not Playing", 16, Theme.fg(), true);
        title.setSingleLine(true);
        title.setPadding(0, a.dp(10), 0, 0);
        right.addView(title);
        artist = Ui.tv(a, "", 13, Theme.sub(), false);
        right.addView(artist);
        bar = new SeekBar(a);
        bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar s, int p, boolean user) { if (user && mp != null) tCur.setText(fmt(p)); }
            public void onStartTrackingTouch(SeekBar s) { seeking = true; }
            public void onStopTrackingTouch(SeekBar s) { seeking = false; if (mp != null) mp.seekTo(s.getProgress()); }
        });
        LinearLayout.LayoutParams bl = new LinearLayout.LayoutParams(-1, -2);
        bl.topMargin = a.dp(8);
        right.addView(bar, bl);
        LinearLayout tt = new LinearLayout(a);
        tCur = Ui.tv(a, "0:00", 11, Theme.sub(), false);
        tTot = Ui.tv(a, "0:00", 11, Theme.sub(), false);
        tt.addView(tCur, new LinearLayout.LayoutParams(0, -2, 1f));
        tt.addView(tTot);
        right.addView(tt, new LinearLayout.LayoutParams(-1, -2));
        LinearLayout ctl = new LinearLayout(a);
        ctl.setGravity(Gravity.CENTER);
        ctl.addView(ctlBtn("\uD83D\uDD00", () -> { shuffle = !shuffle; }), btnLp());
        ctl.addView(ctlBtn("\u23EE", () -> next(-1)), btnLp());
        playBtn = ctlBtn("\u25B6", this::toggle);
        ctl.addView(playBtn, btnLp());
        ctl.addView(ctlBtn("\u23ED", () -> next(1)), btnLp());
        ctl.addView(ctlBtn("\uD83D\uDD01", () -> { repeat = !repeat; }), btnLp());
        right.addView(ctl);
        body.addView(right, new LinearLayout.LayoutParams(0, -1, 1f));
        host.addView(body, new FrameLayout.LayoutParams(-1, -1));
        new Thread(() -> {
            final List<Song> got = query();
            a.runOnUiThread(() -> { all.clear(); all.addAll(got); filter(); });
        }).start();
        a.h.post(progress);
    }

    LinearLayout.LayoutParams btnLp() {
        LinearLayout.LayoutParams l = new LinearLayout.LayoutParams(a.dp(44), a.dp(44));
        l.setMargins(a.dp(4), a.dp(6), a.dp(4), 0);
        return l;
    }

    TextView ctlBtn(String s, final Runnable r) {
        TextView b = Ui.tv(a, s, 18, Theme.fg(), false);
        b.setGravity(Gravity.CENTER);
        b.setBackground(Ui.rr(Theme.dark ? 0x2EFFFFFF : 0x12000000, a.dp(22)));
        b.setOnClickListener(v -> r.run());
        return b;
    }

    List<Song> query() {
        List<Song> l = new ArrayList<>();
        try (Cursor c = a.getContentResolver().query(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                new String[]{MediaStore.Audio.Media._ID, MediaStore.Audio.Media.TITLE, MediaStore.Audio.Media.ARTIST, MediaStore.Audio.Media.DURATION},
                MediaStore.Audio.Media.IS_MUSIC + "!=0", null, MediaStore.Audio.Media.TITLE + " COLLATE NOCASE ASC")) {
            while (c != null && c.moveToNext()) {
                Song s = new Song();
                s.id = c.getLong(0); s.title = c.getString(1); s.artist = c.getString(2); s.dur = c.getLong(3);
                s.uri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, s.id);
                if (s.artist == null || s.artist.equals("<unknown>")) s.artist = "Unknown Artist";
                l.add(s);
            }
        } catch (Exception e) { }
        return l;
    }

    void filter() {
        shown.clear();
        for (Song s : all) if (query.isEmpty() || s.title.toLowerCase(Locale.ROOT).contains(query) || s.artist.toLowerCase(Locale.ROOT).contains(query)) shown.add(s);
        list.removeAllViews();
        if (shown.isEmpty()) {
            TextView e = Ui.tv(a, "No songs found", 13, Theme.sub(), false);
            e.setPadding(a.dp(14), a.dp(10), 0, 0);
            list.addView(e);
        }
        for (int i = 0; i < shown.size(); i++) {
            final int k = i;
            Song s = shown.get(i);
            LinearLayout r = new LinearLayout(a);
            r.setOrientation(LinearLayout.VERTICAL);
            r.setPadding(a.dp(14), a.dp(6), a.dp(10), a.dp(6));
            boolean on = idx >= 0 && idx < shown.size() && shown.get(idx) == s;
            if (on) r.setBackground(Ui.rr(Theme.dark ? 0x40FF375F : 0x26FF375F, a.dp(9)));
            TextView t = Ui.tv(a, s.title, 13, on ? 0xFFFF375F : Theme.fg(), false);
            t.setSingleLine(true);
            r.addView(t);
            TextView sb = Ui.tv(a, s.artist + "  \u00B7  " + fmt((int) s.dur), 11.5f, Theme.sub(), false);
            sb.setSingleLine(true);
            r.addView(sb);
            r.setOnClickListener(v -> play(k));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
            lp.setMargins(a.dp(6), 0, a.dp(6), 0);
            list.addView(r, lp);
        }
    }

    static String fmt(int ms) { int s = ms / 1000; return String.format(Locale.US, "%d:%02d", s / 60, s % 60); }

    void play(int i) {
        if (i < 0 || i >= shown.size()) return;
        idx = i;
        Song s = shown.get(i);
        try {
            if (mp != null) mp.release();
            mp = new MediaPlayer();
            mp.setAudioAttributes(new AudioAttributes.Builder().setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).setUsage(AudioAttributes.USAGE_MEDIA).build());
            mp.setDataSource(a, s.uri);
            mp.setOnCompletionListener(m -> { if (repeat) { m.seekTo(0); m.start(); } else next(1); });
            mp.prepare();
            mp.start();
            bar.setMax(mp.getDuration());
            tTot.setText(fmt(mp.getDuration()));
            title.setText(s.title);
            artist.setText(s.artist);
            playBtn.setText("\u23F8");
            loadArt(s);
            filter();
        } catch (Exception e) { title.setText("Can\u2019t play this song"); }
    }

    void loadArt(final Song s) {
        art.setImageDrawable(Sf.get(a, "music.note", Theme.sub()));
        art.setPadding(a.dp(40), a.dp(40), a.dp(40), a.dp(40));
        if (Build.VERSION.SDK_INT < 29) return;
        new Thread(() -> {
            try {
                final Bitmap b = a.getContentResolver().loadThumbnail(s.uri, new Size(400, 400), null);
                a.runOnUiThread(() -> { if (idx >= 0 && shown.get(idx) == s) { art.setPadding(0, 0, 0, 0); art.setImageBitmap(b); } });
            } catch (Exception e) { }
        }).start();
    }

    void toggle() {
        if (mp == null) { play(Math.max(idx, 0)); return; }
        if (mp.isPlaying()) { mp.pause(); playBtn.setText("\u25B6"); } else { mp.start(); playBtn.setText("\u23F8"); }
    }

    void next(int d) {
        if (shown.isEmpty()) return;
        int n = shuffle ? new Random().nextInt(shown.size()) : (idx + d + shown.size()) % shown.size();
        play(n);
    }

    final Runnable progress = new Runnable() {
        @Override public void run() {
            if (closed) return;
            try { if (mp != null && mp.isPlaying() && !seeking) { bar.setProgress(mp.getCurrentPosition()); tCur.setText(fmt(mp.getCurrentPosition())); } } catch (Exception e) { }
            a.h.postDelayed(this, 500);
        }
    };
}
