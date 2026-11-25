package com.macos.launcher;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.GestureDetector;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.Random;

/** 2048: swipe to merge tiles. */
public class Game2048 {
    static final int[] COLORS = {0xFFCDC1B4, 0xFFEEE4DA, 0xFFEDE0C8, 0xFFF2B179, 0xFFF59563, 0xFFF67C5F, 0xFFF65E3B, 0xFFEDCF72, 0xFFEDCC61, 0xFFEDC850, 0xFFEDC53F, 0xFFEDC22E};
    final MainActivity a;
    final int[][] g = new int[4][4];
    final Random rnd = new Random();
    int score, best;
    boolean over;
    BoardView bv;
    TextView scoreTv, bestTv;

    public static void open(MainActivity a) { new Game2048(a); }

    Game2048(MainActivity act) {
        a = act;
        best = a.prefs.getInt("g2048_best", 0);
        LinearLayout col = new LinearLayout(a);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setBackgroundColor(Theme.dark ? 0xFF1C1C1E : 0xFFFAF8EF);
        col.setGravity(Gravity.CENTER_HORIZONTAL);
        LinearLayout top = new LinearLayout(a);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setPadding(a.dp(14), a.dp(8), a.dp(14), a.dp(4));
        TextView t = Ui.tv(a, "2048", 26, 0xFF776E65, true);
        top.addView(t, new LinearLayout.LayoutParams(0, -2, 1f));
        scoreTv = box("SCORE", "0", top);
        bestTv = box("BEST", String.valueOf(best), top);
        TextView nb = Ui.tv(a, "New", 12.5f, Color.WHITE, true);
        nb.setGravity(Gravity.CENTER);
        nb.setBackground(Ui.rr(0xFF8F7A66, a.dp(8)));
        nb.setOnClickListener(v -> reset());
        LinearLayout.LayoutParams nl = new LinearLayout.LayoutParams(a.dp(54), a.dp(30));
        nl.leftMargin = a.dp(8);
        top.addView(nb, nl);
        col.addView(top, new LinearLayout.LayoutParams(-1, -2));
        bv = new BoardView();
        col.addView(bv, new LinearLayout.LayoutParams(-1, 0, 1f));
        TextView hint = Ui.tv(a, "Swipe to move the tiles", 11.5f, Theme.sub(), false);
        hint.setPadding(0, 0, 0, a.dp(6));
        col.addView(hint);
        a.openWindow(new MacWindow(a, "2048", null, "2048", col), 330, 400);
        reset();
    }

    TextView box(String label, String val, LinearLayout parent) {
        LinearLayout b = new LinearLayout(a);
        b.setOrientation(LinearLayout.VERTICAL);
        b.setGravity(Gravity.CENTER);
        b.setBackground(Ui.rr(0xFFBBADA0, a.dp(8)));
        b.addView(Ui.tv(a, label, 9.5f, 0xFFEEE4DA, true));
        TextView v = Ui.tv(a, val, 15, Color.WHITE, true);
        b.addView(v);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(a.dp(62), a.dp(36));
        lp.leftMargin = a.dp(8);
        parent.addView(b, lp);
        return v;
    }

    void reset() {
        for (int[] r : g) java.util.Arrays.fill(r, 0);
        score = 0; over = false;
        spawn(); spawn();
        sync();
    }

    void spawn() {
        int n = 0;
        for (int[] r : g) for (int v : r) if (v == 0) n++;
        if (n == 0) return;
        int k = rnd.nextInt(n);
        for (int y = 0; y < 4; y++) for (int x = 0; x < 4; x++) if (g[y][x] == 0 && k-- == 0) { g[y][x] = rnd.nextInt(10) == 0 ? 4 : 2; return; }
    }

    void sync() {
        if (score > best) { best = score; a.prefs.edit().putInt("g2048_best", best).apply(); }
        scoreTv.setText(String.valueOf(score));
        bestTv.setText(String.valueOf(best));
        bv.invalidate();
    }

    /** dir: 0 left, 1 up, 2 right, 3 down */
    void move(int dir) {
        if (over) return;
        boolean moved = false;
        for (int i = 0; i < 4; i++) {
            int[] line = new int[4];
            for (int k = 0; k < 4; k++) line[k] = get(dir, i, k);
            int[] out = new int[4];
            int w = 0; boolean merged = false;
            for (int k = 0; k < 4; k++) {
                if (line[k] == 0) continue;
                if (w > 0 && out[w - 1] == line[k] && !merged) { out[w - 1] *= 2; score += out[w - 1]; merged = true; }
                else { out[w++] = line[k]; merged = false; }
            }
            for (int k = 0; k < 4; k++) { if (line[k] != out[k]) moved = true; set(dir, i, k, out[k]); }
        }
        if (moved) spawn();
        if (!canMove()) over = true;
        sync();
    }

    int get(int dir, int i, int k) {
        switch (dir) { case 0: return g[i][k]; case 2: return g[i][3 - k]; case 1: return g[k][i]; default: return g[3 - k][i]; }
    }

    void set(int dir, int i, int k, int v) {
        switch (dir) { case 0: g[i][k] = v; break; case 2: g[i][3 - k] = v; break; case 1: g[k][i] = v; break; default: g[3 - k][i] = v; }
    }

    boolean canMove() {
        for (int y = 0; y < 4; y++) for (int x = 0; x < 4; x++) {
            if (g[y][x] == 0) return true;
            if (x < 3 && g[y][x] == g[y][x + 1]) return true;
            if (y < 3 && g[y][x] == g[y + 1][x]) return true;
        }
        return false;
    }

    class BoardView extends View {
        final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        final GestureDetector gd;

        BoardView() {
            super(a);
            gd = new GestureDetector(a, new GestureDetector.SimpleOnGestureListener() {
                @Override public boolean onDown(MotionEvent e) { return true; }
                @Override public boolean onFling(MotionEvent e1, MotionEvent e2, float vx, float vy) {
                    if (Math.abs(vx) > Math.abs(vy)) move(vx > 0 ? 2 : 0); else move(vy > 0 ? 3 : 1);
                    return true;
                }
            });
        }

        @Override public boolean onTouchEvent(MotionEvent e) { return gd.onTouchEvent(e); }

        @Override protected void onDraw(Canvas c) {
            float s = Math.min(getWidth(), getHeight()) - a.dp(20);
            float ox = (getWidth() - s) / 2f, oy = (getHeight() - s) / 2f, gap = a.dp(6), cell = (s - gap * 5) / 4f;
            p.setColor(0xFFBBADA0);
            c.drawRoundRect(new RectF(ox, oy, ox + s, oy + s), a.dp(10), a.dp(10), p);
            p.setTextAlign(Paint.Align.CENTER);
            p.setTypeface(Emoji.font(true));
            for (int y = 0; y < 4; y++) for (int x = 0; x < 4; x++) {
                float l = ox + gap + x * (cell + gap), t = oy + gap + y * (cell + gap);
                int v = g[y][x];
                int idx = v == 0 ? 0 : Math.min(COLORS.length - 1, Integer.numberOfTrailingZeros(v));
                p.setColor(v == 0 ? 0xFFCDC1B4 : COLORS[idx]);
                c.drawRoundRect(new RectF(l, t, l + cell, t + cell), a.dp(7), a.dp(7), p);
                if (v != 0) {
                    p.setColor(v <= 4 ? 0xFF776E65 : Color.WHITE);
                    p.setTextSize(cell * (v < 100 ? .46f : v < 1000 ? .38f : .3f));
                    c.drawText(String.valueOf(v), l + cell / 2f, t + cell / 2f - (p.ascent() + p.descent()) / 2f, p);
                }
            }
            if (over) {
                p.setColor(0xB3FAF8EF);
                c.drawRoundRect(new RectF(ox, oy, ox + s, oy + s), a.dp(10), a.dp(10), p);
                p.setColor(0xFF776E65);
                p.setTextSize(s * .12f);
                c.drawText("Game over!", getWidth() / 2f, getHeight() / 2f, p);
            }
        }
    }
}
