package com.macos.launcher;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.GestureDetector;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.Random;

/** Blocks: falling-tetromino puzzle with touch controls. */
public class GameBlocks {
    static final int W = 10, H = 20;
    static final int[][][] SHAPES = {
            {{0, 0}, {1, 0}, {2, 0}, {3, 0}}, {{0, 0}, {1, 0}, {0, 1}, {1, 1}}, {{1, 0}, {0, 1}, {1, 1}, {2, 1}},
            {{0, 0}, {1, 0}, {2, 0}, {2, 1}}, {{0, 0}, {1, 0}, {2, 0}, {0, 1}}, {{1, 0}, {2, 0}, {0, 1}, {1, 1}}, {{0, 0}, {1, 0}, {1, 1}, {2, 1}}};
    static final int[] COL = {0xFF2CC7E8, 0xFFF5D90A, 0xFFB45CF0, 0xFF3B82F6, 0xFFFF9F0A, 0xFF30D158, 0xFFFF453A};

    final MainActivity a;
    final int[][] grid = new int[H][W];
    final Random rnd = new Random();
    int[][] cur, nxt;
    int curC, nxtC, px, py, score, lines, level = 1;
    boolean over, paused, closed;
    BoardView bv;
    TextView info;

    public static void open(MainActivity a) { new GameBlocks(a); }

    GameBlocks(MainActivity act) {
        a = act;
        LinearLayout col = new LinearLayout(a);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setBackgroundColor(0xFF15151A);
        info = Ui.tv(a, "", 12.5f, Color.WHITE, true);
        info.setGravity(Gravity.CENTER);
        info.setPadding(0, a.dp(6), 0, a.dp(2));
        col.addView(info, new LinearLayout.LayoutParams(-1, -2));
        bv = new BoardView();
        col.addView(bv, new LinearLayout.LayoutParams(-1, 0, 1f));
        LinearLayout bar = new LinearLayout(a);
        bar.setGravity(Gravity.CENTER);
        String[] lb = {"\u25C0", "\u21BB", "\u25B6", "\u25BC", "\u23EC"};
        for (int i = 0; i < lb.length; i++) {
            final int k = i;
            TextView b = Ui.tv(a, lb[i], 16, Color.WHITE, true);
            b.setGravity(Gravity.CENTER);
            b.setBackground(Ui.rr(0x33FFFFFF, a.dp(10)));
            b.setOnClickListener(v -> act(k));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(a.dp(46), a.dp(34));
            lp.setMargins(a.dp(4), a.dp(4), a.dp(4), a.dp(6));
            bar.addView(b, lp);
        }
        TextView np = Ui.tv(a, "New", 12, Color.WHITE, true);
        np.setGravity(Gravity.CENTER);
        np.setBackground(Ui.rr(Theme.accent, a.dp(10)));
        np.setOnClickListener(v -> reset());
        LinearLayout.LayoutParams nl = new LinearLayout.LayoutParams(a.dp(46), a.dp(34));
        nl.setMargins(a.dp(4), a.dp(4), a.dp(4), a.dp(6));
        bar.addView(np, nl);
        col.addView(bar);
        MacWindow w = new MacWindow(a, "Blocks", null, "Blocks", col);
        w.onClose = () -> closed = true;
        a.openWindow(w, 300, 420);
        reset();
        tick.run();
    }

    final Runnable tick = new Runnable() {
        @Override public void run() {
            if (closed) return;
            if (!over && !paused) { if (!step()) lockPiece(); bv.invalidate(); }
            a.h.postDelayed(this, Math.max(90, 650 - (level - 1) * 55));
        }
    };

    void reset() {
        for (int[] r : grid) java.util.Arrays.fill(r, 0);
        score = 0; lines = 0; level = 1; over = false;
        nxt = null;
        spawn();
        updateInfo();
        bv.invalidate();
    }

    void updateInfo() { info.setText("Score " + score + "   Lines " + lines + "   Level " + level + (over ? "   GAME OVER" : "")); }

    void spawn() {
        if (nxt == null) { int i = rnd.nextInt(7); nxt = copy(SHAPES[i]); nxtC = i; }
        cur = nxt; curC = nxtC;
        int i = rnd.nextInt(7);
        nxt = copy(SHAPES[i]); nxtC = i;
        px = 3; py = 0;
        if (hit(cur, px, py)) { over = true; updateInfo(); }
    }

    static int[][] copy(int[][] s) { int[][] r = new int[s.length][2]; for (int i = 0; i < s.length; i++) { r[i][0] = s[i][0]; r[i][1] = s[i][1]; } return r; }

    boolean hit(int[][] s, int x, int y) {
        for (int[] c : s) {
            int cx = x + c[0], cy = y + c[1];
            if (cx < 0 || cx >= W || cy >= H) return true;
            if (cy >= 0 && grid[cy][cx] != 0) return true;
        }
        return false;
    }

    boolean step() {
        if (hit(cur, px, py + 1)) return false;
        py++;
        return true;
    }

    void lockPiece() {
        for (int[] c : cur) { int cy = py + c[1]; if (cy >= 0) grid[cy][px + c[0]] = curC + 1; }
        int cleared = 0;
        for (int y = H - 1; y >= 0; y--) {
            boolean full = true;
            for (int x = 0; x < W; x++) if (grid[y][x] == 0) { full = false; break; }
            if (full) {
                cleared++;
                for (int yy = y; yy > 0; yy--) grid[yy] = grid[yy - 1].clone();
                grid[0] = new int[W];
                y++;
            }
        }
        if (cleared > 0) {
            lines += cleared;
            score += new int[]{0, 100, 300, 500, 800}[cleared] * level;
            level = 1 + lines / 10;
        }
        spawn();
        updateInfo();
    }

    void act(int k) {
        if (over) return;
        switch (k) {
            case 0: if (!hit(cur, px - 1, py)) px--; break;
            case 2: if (!hit(cur, px + 1, py)) px++; break;
            case 3: if (step()) score++; break;
            case 1: rotate(); break;
            case 4: while (step()) score += 2; lockPiece(); break;
        }
        updateInfo();
        bv.invalidate();
    }

    void rotate() {
        int maxY = 0;
        for (int[] c : cur) maxY = Math.max(maxY, c[1]);
        int[][] r = new int[cur.length][2];
        int minX = 99, minY = 99;
        for (int i = 0; i < cur.length; i++) { r[i][0] = maxY - cur[i][1]; r[i][1] = cur[i][0]; minX = Math.min(minX, r[i][0]); minY = Math.min(minY, r[i][1]); }
        for (int[] c : r) { c[0] -= minX; c[1] -= minY; }
        for (int dx : new int[]{0, -1, 1, -2, 2}) if (!hit(r, px + dx, py)) { cur = r; px += dx; return; }
    }

    class BoardView extends View {
        final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        final GestureDetector gd;
        float acc;

        BoardView() {
            super(a);
            gd = new GestureDetector(a, new GestureDetector.SimpleOnGestureListener() {
                @Override public boolean onDown(MotionEvent e) { acc = 0; return true; }
                @Override public boolean onSingleTapUp(MotionEvent e) { act(1); return true; }
                @Override public boolean onScroll(MotionEvent e1, MotionEvent e2, float dx, float dy) {
                    float cell = getHeight() / (float) H;
                    acc -= dx;
                    while (acc > cell) { act(2); acc -= cell; }
                    while (acc < -cell) { act(0); acc += cell; }
                    if (dy < -cell * .6f) act(3);
                    return true;
                }
                @Override public boolean onFling(MotionEvent e1, MotionEvent e2, float vx, float vy) {
                    if (vy > 2500 && Math.abs(vy) > Math.abs(vx)) act(4);
                    return true;
                }
            });
        }

        @Override public boolean onTouchEvent(MotionEvent e) { return gd.onTouchEvent(e); }

        @Override protected void onDraw(Canvas c) {
            float cell = Math.min(getHeight() / (float) H, getWidth() * .62f / W);
            float ox = a.dp(8), oy = (getHeight() - cell * H) / 2f;
            p.setColor(0xFF22222A);
            c.drawRoundRect(new RectF(ox - 2, oy - 2, ox + cell * W + 2, oy + cell * H + 2), 6, 6, p);
            for (int y = 0; y < H; y++) for (int x = 0; x < W; x++) if (grid[y][x] != 0) block(c, ox, oy, cell, x, y, COL[grid[y][x] - 1]);
            if (cur != null && !over) {
                for (int[] b : cur) block(c, ox, oy, cell, px + b[0], py + b[1], COL[curC]);
                int gy = py;
                while (!hit(cur, px, gy + 1)) gy++;
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(2);
                p.setColor(0x66FFFFFF);
                for (int[] b : cur) c.drawRect(ox + (px + b[0]) * cell + 2, oy + (gy + b[1]) * cell + 2, ox + (px + b[0] + 1) * cell - 2, oy + (gy + b[1] + 1) * cell - 2, p);
                p.setStyle(Paint.Style.FILL);
            }
            float nx = ox + cell * W + a.dp(14);
            p.setColor(0xFFFFFFFF);
            p.setTextSize(a.dp(12));
            p.setTypeface(Emoji.font(true));
            c.drawText("NEXT", nx, oy + a.dp(14), p);
            if (nxt != null) for (int[] b : nxt) block(c, nx, oy + a.dp(22), cell * .8f, b[0], b[1], COL[nxtC]);
        }

        void block(Canvas c, float ox, float oy, float cell, int x, int y, int color) {
            p.setColor(color);
            c.drawRoundRect(new RectF(ox + x * cell + 1, oy + y * cell + 1, ox + (x + 1) * cell - 1, oy + (y + 1) * cell - 1), cell * .18f, cell * .18f, p);
        }
    }
}
