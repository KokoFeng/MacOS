package com.macos.launcher;

import android.content.Context;
import android.graphics.drawable.ColorDrawable;
import android.os.Environment;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.GridView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public class FinderApp {
    final MainActivity a;
    final File root = Environment.getExternalStorageDirectory();
    File cur;
    final ArrayList<File> hist = new ArrayList<>();
    int hi = -1;
    TextView title, status;
    final List<File> items = new ArrayList<>();
    BaseAdapter ad;

    public static void open(MainActivity a) { new FinderApp(a); }

    FinderApp(MainActivity act) {
        a = act;
        final int M = MainActivity.MATCH, W = MainActivity.WRAP;
        LinearLayout tb = new LinearLayout(a);
        tb.setGravity(Gravity.CENTER_VERTICAL);
        TextView back = Ui.circleBtn(a, "‹"), fwd = Ui.circleBtn(a, "›"), plus = Ui.circleBtn(a, "+");
        back.setOnClickListener(v -> { if (hi > 0) { hi--; go(hist.get(hi), false); } });
        fwd.setOnClickListener(v -> { if (hi < hist.size() - 1) { hi++; go(hist.get(hi), false); } });
        plus.setOnClickListener(v -> a.prompt("New Folder", "untitled folder", s -> {
            if (!new File(cur, s).mkdirs()) Toast.makeText(a, "Gagal membuat folder", Toast.LENGTH_SHORT).show();
            refresh();
        }));
        tb.addView(back, new LinearLayout.LayoutParams(a.dp(30), a.dp(30)));
        LinearLayout.LayoutParams fl = new LinearLayout.LayoutParams(a.dp(30), a.dp(30));
        fl.leftMargin = a.dp(6);
        tb.addView(fwd, fl);
        title = Ui.tv(a, "Macintosh HD", 14, Theme.fg(), true);
        title.setPadding(a.dp(12), 0, 0, 0);
        tb.addView(title, new LinearLayout.LayoutParams(0, W, 1f));
        tb.addView(plus, new LinearLayout.LayoutParams(a.dp(30), a.dp(30)));

        LinearLayout body = new LinearLayout(a);
        body.setBackgroundColor(Theme.bg());
        if (!a.hasStorage()) {
            body.setOrientation(LinearLayout.VERTICAL);
            body.setGravity(Gravity.CENTER);
            TextView t = Ui.tv(a, "Finder butuh izin akses semua file.", 14, Theme.fg(), false);
            TextView b = Ui.tv(a, "Beri Akses", 14, 0xFFFFFFFF, true);
            b.setPadding(a.dp(18), a.dp(8), a.dp(18), a.dp(8));
            b.setBackground(Ui.rr(Theme.accent, a.dp(16)));
            b.setOnClickListener(v -> a.requestStorage());
            body.addView(t);
            LinearLayout.LayoutParams bl = new LinearLayout.LayoutParams(W, W);
            bl.topMargin = a.dp(12);
            body.addView(b, bl);
            TextView n = Ui.tv(a, "Setelah diizinkan, tutup lalu buka Finder lagi.", 11, Theme.sub(), false);
            LinearLayout.LayoutParams nl = new LinearLayout.LayoutParams(W, W);
            nl.topMargin = a.dp(8);
            body.addView(n, nl);
            a.openWindow(new MacWindow(a, "Finder", tb, null, body), 560, 300);
            return;
        }
        body.setOrientation(LinearLayout.HORIZONTAL);

        // sidebar
        LinearLayout side = new LinearLayout(a);
        side.setOrientation(LinearLayout.VERTICAL);
        side.setPadding(a.dp(10), a.dp(4), a.dp(10), a.dp(10));
        side.addView(head("Favourites"));
        String[][] fav = {{"Downloads", Environment.DIRECTORY_DOWNLOADS}, {"Documents", Environment.DIRECTORY_DOCUMENTS},
                {"Pictures", Environment.DIRECTORY_PICTURES}, {"Camera", Environment.DIRECTORY_DCIM},
                {"Music", Environment.DIRECTORY_MUSIC}, {"Movies", Environment.DIRECTORY_MOVIES}};
        for (final String[] f : fav) side.addView(sideRow(f[0], () -> go(new File(root, f[1]), true)));
        side.addView(head("Locations"));
        side.addView(sideRow("Macintosh HD", () -> go(root, true)));
        side.addView(sideRow("Applications", () -> a.showLaunchpad(null)));
        ScrollView sv = new ScrollView(a);
        sv.setBackgroundColor(Theme.side());
        sv.setVerticalScrollBarEnabled(false);
        sv.addView(side);
        body.addView(sv, new LinearLayout.LayoutParams(a.dp(140), M));

        // content
        LinearLayout content = new LinearLayout(a);
        content.setOrientation(LinearLayout.VERTICAL);
        GridView gv = new GridView(a);
        gv.setNumColumns(GridView.AUTO_FIT);
        gv.setColumnWidth(a.dp(92));
        gv.setStretchMode(GridView.STRETCH_COLUMN_WIDTH);
        gv.setVerticalSpacing(a.dp(10));
        gv.setPadding(a.dp(12), a.dp(10), a.dp(12), a.dp(8));
        gv.setSelector(new ColorDrawable(0));
        ad = new BaseAdapter() {
            @Override public int getCount() { return items.size(); }
            @Override public Object getItem(int i) { return items.get(i); }
            @Override public long getItemId(int i) { return i; }
            @Override public View getView(int i, View cv, ViewGroup p) {
                final File f = items.get(i);
                LinearLayout c = new LinearLayout(a);
                c.setOrientation(LinearLayout.VERTICAL);
                c.setGravity(Gravity.CENTER_HORIZONTAL);
                View ic;
                if (f.isDirectory()) ic = new Ui.FolderIcon(a);
                else {
                    String n = f.getName();
                    int d = n.lastIndexOf('.');
                    ic = new Ui.FileIcon(a, d >= 0 ? n.substring(d + 1) : "");
                }
                c.addView(ic, new LinearLayout.LayoutParams(a.dp(52), a.dp(52)));
                TextView t = Ui.tv(a, f.getName(), 11, Theme.fg(), false);
                t.setGravity(Gravity.CENTER);
                t.setMaxLines(2);
                t.setEllipsize(TextUtils.TruncateAt.END);
                LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(M, W);
                tl.topMargin = a.dp(4);
                c.addView(t, tl);
                c.setOnClickListener(v -> { if (f.isDirectory()) go(f, true); else a.openFile(f); });
                c.setOnLongClickListener(v -> {
                    a.showPopup(v, new String[]{"Open", "Rename…", "Delete"}, new Runnable[]{
                        () -> { if (f.isDirectory()) go(f, true); else a.openFile(f); },
                        () -> a.prompt("Rename", f.getName(), s -> { f.renameTo(new File(f.getParentFile(), s)); refresh(); }),
                        () -> a.confirm("Hapus \"" + f.getName() + "\"?", () -> { del(f); refresh(); })});
                    return true;
                });
                return c;
            }
        };
        gv.setAdapter(ad);
        content.addView(gv, new LinearLayout.LayoutParams(M, 0, 1f));
        status = Ui.tv(a, "", 11, Theme.sub(), false);
        status.setGravity(Gravity.CENTER);
        status.setPadding(0, a.dp(3), 0, a.dp(5));
        content.addView(status, new LinearLayout.LayoutParams(M, W));
        body.addView(content, new LinearLayout.LayoutParams(0, M, 1f));

        a.openWindow(new MacWindow(a, "Finder", tb, null, body), 600, 310);
        go(root, true);
    }

    TextView head(String s) {
        TextView t = Ui.tv(a, s, 10.5f, Theme.sub(), true);
        t.setPadding(a.dp(6), a.dp(10), 0, a.dp(3));
        return t;
    }

    View sideRow(String label, final Runnable act) {
        LinearLayout r = new LinearLayout(a);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setPadding(a.dp(6), a.dp(5), a.dp(6), a.dp(5));
        r.addView(new Ui.FolderIcon(a), new LinearLayout.LayoutParams(a.dp(18), a.dp(18)));
        TextView t = Ui.tv(a, label, 12.5f, Theme.fg(), false);
        t.setPadding(a.dp(8), 0, 0, 0);
        t.setSingleLine(true);
        r.addView(t);
        r.setOnClickListener(v -> act.run());
        return r;
    }

    void go(File d, boolean push) {
        if (!d.exists()) d.mkdirs();
        if (push) {
            while (hist.size() > hi + 1) hist.remove(hist.size() - 1);
            hist.add(d);
            hi = hist.size() - 1;
        }
        cur = d;
        refresh();
    }

    void refresh() {
        title.setText(cur.equals(root) ? "Macintosh HD" : cur.getName());
        items.clear();
        File[] fs = cur.listFiles();
        if (fs != null) for (File f : fs) if (!f.getName().startsWith(".")) items.add(f);
        Collections.sort(items, (x, y) -> {
            if (x.isDirectory() != y.isDirectory()) return x.isDirectory() ? -1 : 1;
            return x.getName().toLowerCase(Locale.ROOT).compareTo(y.getName().toLowerCase(Locale.ROOT));
        });
        ad.notifyDataSetChanged();
        status.setText(items.size() + " items");
    }

    static void del(File f) {
        if (f.isDirectory()) { File[] c = f.listFiles(); if (c != null) for (File x : c) del(x); }
        f.delete();
    }
}
