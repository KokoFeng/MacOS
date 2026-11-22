package com.macos.launcher;

import android.animation.ObjectAnimator;
import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.media.AudioManager;
import android.net.Uri;
import android.net.wifi.WifiManager;
import android.bluetooth.BluetoothAdapter;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.view.animation.DecelerateInterpolator;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.GridView;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class MainActivity extends Activity {
    static class App { String label; ComponentName cn; Drawable icon; }

    static final int MATCH = ViewGroup.LayoutParams.MATCH_PARENT, WRAP = ViewGroup.LayoutParams.WRAP_CONTENT;
    static final String CAT = "cat:android.intent.category.";
    static final String[][] DOCK = {
        {"Finder", "mac_finder", "finder"}, {"Launchpad", "mac_apps", "launchpad"},
        {"Safari", "mac_safari", CAT + "APP_BROWSER"}, {"Messages", "mac_messages", CAT + "APP_MESSAGING"},
        {"Mail", "mac_mail", CAT + "APP_EMAIL"}, {"Maps", "mac_maps", CAT + "APP_MAPS"},
        {"Photos", "mac_photos", CAT + "APP_GALLERY"}, {"FaceTime", "mac_facetime", "none"},
        {"Phone", "mac_phone", "dial"}, {"Calendar", "mac_calendar", CAT + "APP_CALENDAR"},
        {"Contacts", "mac_contacts", CAT + "APP_CONTACTS"}, {"Reminders", "mac_reminders", "none"},
        {"Notes", "mac_notes", "none"}, {"TV", "mac_tv", "none"}, {"Music", "mac_music", CAT + "APP_MUSIC"},
        {"App Store", "mac_app_store", "market"}, {"iPhone Mirroring", "mac_iphone_mirroring", "cast"},
        {"System Settings", "mac_system_settings", "settings"}};

    FrameLayout root, overlay;
    WallpaperView wall;
    LinearLayout menuBar;
    View widget;
    TextView clockTv, battTv;
    IconView battIc;
    SharedPreferences prefs;
    List<App> apps;
    long appsTs;
    float den;
    int sw, sh, topInset = 0;
    final Handler h = new Handler(Looper.getMainLooper());

    int dp(float v) { return Math.round(v * den); }
    int mbH() { return dp(26) + topInset; }
    int res(String n) { return getResources().getIdentifier(n, "drawable", getPackageName()); }

    @Override protected void onCreate(android.os.Bundle b) {
        super.onCreate(b);
        den = getResources().getDisplayMetrics().density;
        sw = getResources().getDisplayMetrics().widthPixels;
        sh = getResources().getDisplayMetrics().heightPixels;
        prefs = getSharedPreferences("mac", 0);
        if (Build.VERSION.SDK_INT >= 28) {
            android.view.WindowManager.LayoutParams wl0 = getWindow().getAttributes();
            wl0.layoutInDisplayCutoutMode = android.view.WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
            getWindow().setAttributes(wl0);
        }
        immersive();
        root = new FrameLayout(this);
        setContentView(root);
        wall = new WallpaperView(this);
        root.addView(wall, new FrameLayout.LayoutParams(MATCH, MATCH));
        wall.setOnLongClickListener(v -> {
            Intent i = new Intent(Intent.ACTION_GET_CONTENT);
            i.setType("image/*");
            startActivityForResult(i, 77);
            return true;
        });
        buildWidget();
        buildDock();
        buildMenuBar();
        root.setOnApplyWindowInsetsListener((v, ins) -> {
            int t = ins.getSystemWindowInsetTop(), l = ins.getSystemWindowInsetLeft(), r = ins.getSystemWindowInsetRight();
            if (Build.VERSION.SDK_INT >= 28 && ins.getDisplayCutout() != null) {
                t = Math.max(t, ins.getDisplayCutout().getSafeInsetTop());
                l = Math.max(l, ins.getDisplayCutout().getSafeInsetLeft());
                r = Math.max(r, ins.getDisplayCutout().getSafeInsetRight());
            }
            topInset = t;
            menuBar.setPadding(Math.max(dp(14), l), t, Math.max(dp(14), r), 0);
            menuBar.getLayoutParams().height = mbH();
            ((FrameLayout.LayoutParams) widget.getLayoutParams()).topMargin = mbH() + dp(14);
            ((FrameLayout.LayoutParams) widget.getLayoutParams()).leftMargin = Math.max(dp(16), l);
            root.requestLayout();
            return ins;
        });
        root.requestApplyInsets();
        new Thread(() -> {
            final List<App> l = loadApps();
            runOnUiThread(() -> { apps = l; appsTs = System.currentTimeMillis(); });
        }).start();
    }

    void immersive() {
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
    }
    @Override public void onWindowFocusChanged(boolean f) { super.onWindowFocusChanged(f); if (f) immersive(); }
    @Override protected void onNewIntent(Intent i) { super.onNewIntent(i); dismissOverlays(); }
    @Override public void onBackPressed() { dismissOverlays(); }
    @Override protected void onResume() { super.onResume(); h.removeCallbacks(tick); h.post(tick); }
    @Override protected void onPause() { super.onPause(); h.removeCallbacks(tick); }

    @Override protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req != 77 || data == null || data.getData() == null) return;
        try {
            Uri u = data.getData();
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inJustDecodeBounds = true;
            InputStream in = getContentResolver().openInputStream(u);
            BitmapFactory.decodeStream(in, null, o);
            in.close();
            int ss = 1;
            while (Math.max(o.outWidth, o.outHeight) / ss > 2600) ss *= 2;
            o = new BitmapFactory.Options();
            o.inSampleSize = ss;
            in = getContentResolver().openInputStream(u);
            Bitmap bm = BitmapFactory.decodeStream(in, null, o);
            in.close();
            FileOutputStream fo = new FileOutputStream(new File(getFilesDir(), "wall.jpg"));
            bm.compress(Bitmap.CompressFormat.JPEG, 92, fo);
            fo.close();
            recreate();
        } catch (Exception e) { Toast.makeText(this, "Gagal memuat wallpaper", Toast.LENGTH_SHORT).show(); }
    }

    // ===== clock / battery =====
    final Runnable tick = new Runnable() {
        @Override public void run() {
            clockTv.setText(new SimpleDateFormat("EEE MMM d  h:mm a", Locale.US).format(new Date()));
            Intent b = registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
            if (b != null) {
                int l = b.getIntExtra(BatteryManager.EXTRA_LEVEL, -1), s = b.getIntExtra(BatteryManager.EXTRA_SCALE, 100);
                if (l >= 0) { battTv.setText((l * 100 / s) + "%"); battIc.setLevel(l / (float) s); }
            }
            h.postDelayed(this, 20000);
        }
    };

    // ===== small view helpers =====
    TextView tv(String s, float sp, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(s); t.setTextSize(sp); t.setTextColor(color);
        t.setTypeface(Typeface.create(bold ? "sans-serif-medium" : "sans-serif", Typeface.NORMAL));
        if (bold) t.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        return t;
    }
    GradientDrawable rr(int color, float r) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color); g.setCornerRadius(r);
        return g;
    }

    // ===== menu bar =====
    void buildMenuBar() {
        menuBar = new LinearLayout(this);
        menuBar.setOrientation(LinearLayout.HORIZONTAL);
        menuBar.setGravity(Gravity.CENTER_VERTICAL);
        IconView apple = new IconView(this, IconView.APPLE, Color.WHITE);
        LinearLayout.LayoutParams al = new LinearLayout.LayoutParams(dp(18), dp(18));
        al.rightMargin = dp(6);
        menuBar.addView(apple, al);
        apple.setOnClickListener(v -> showMenu("apple", v));
        String[] names = {"Finder", "File", "Edit", "View", "Go", "Window", "Help"};
        boolean wide = sw / den >= 560;
        for (int i = 0; i < names.length; i++) {
            if (i > 0 && !wide) break;
            final String n = names[i];
            TextView t = tv(n, 13, Color.WHITE, i == 0);
            t.setShadowLayer(dp(2), 0, 0, 0x55000000);
            t.setPadding(dp(8), dp(4), dp(8), dp(4));
            t.setOnClickListener(v -> showMenu(n, v));
            menuBar.addView(t);
        }
        View sp = new View(this);
        menuBar.addView(sp, new LinearLayout.LayoutParams(0, 1, 1f));
        battTv = tv("", 12, Color.WHITE, false);
        battTv.setShadowLayer(dp(2), 0, 0, 0x55000000);
        menuBar.addView(battTv);
        battIc = new IconView(this, IconView.BATT, Color.WHITE);
        menuBar.addView(battIc, new LinearLayout.LayoutParams(dp(26), dp(18)));
        menuBar.addView(new IconView(this, IconView.WIFI, Color.WHITE), new LinearLayout.LayoutParams(dp(22), dp(18)));
        IconView se = new IconView(this, IconView.SEARCH, Color.WHITE);
        se.setOnClickListener(v -> showSpotlight());
        menuBar.addView(se, new LinearLayout.LayoutParams(dp(22), dp(20)));
        IconView cc = new IconView(this, IconView.CC, Color.WHITE);
        cc.setOnClickListener(v -> showControlCenter());
        cc.setBackground(rr(0x33FFFFFF, dp(8)));
        LinearLayout.LayoutParams cl = new LinearLayout.LayoutParams(dp(30), dp(20));
        cl.leftMargin = dp(2); cl.rightMargin = dp(4);
        menuBar.addView(cc, cl);
        clockTv = tv("", 13, Color.WHITE, false);
        clockTv.setShadowLayer(dp(2), 0, 0, 0x55000000);
        clockTv.setPadding(dp(4), 0, 0, 0);
        menuBar.addView(clockTv);
        root.addView(menuBar, new FrameLayout.LayoutParams(MATCH, dp(26), Gravity.TOP));
    }

    String[] menuItems(String n) {
        switch (n) {
            case "apple": return new String[]{"About This Mac", "-", "System Settings…", "App Store…", "-", "Recent Items", "-", "Force Quit…", "-", "Sleep", "Restart…", "Shut Down…", "-", "Lock Screen", "Log Out"};
            case "Finder": return new String[]{"About Finder", "-", "Settings…", "-", "Empty Trash…", "-", "Services", "-", "Hide Finder", "Hide Others", "Show All"};
            case "File": return new String[]{"New Finder Window", "New Folder", "Open", "-", "Get Info", "Rename", "-", "Close Window"};
            case "Edit": return new String[]{"Undo", "Redo", "-", "Cut", "Copy", "Paste", "Select All"};
            case "View": return new String[]{"as Icons", "as List", "as Columns", "as Gallery", "-", "Show Path Bar", "Show Status Bar"};
            case "Go": return new String[]{"Back", "Forward", "Enclosing Folder", "-", "Recents", "Documents", "Desktop", "Downloads", "Applications"};
            case "Window": return new String[]{"Minimize", "Zoom", "-", "Bring All to Front"};
            default: return new String[]{"Search", "-", "macOS Help"};
        }
    }

    void showMenu(String name, View anchor) {
        dismissOverlays();
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(6), dp(6), dp(6), dp(6));
        box.setClickable(true);
        box.setBackground(new GlassDrawable(box, dp(12), dp(0.8f), 0xCCF4F4F6));
        for (final String it : menuItems(name)) {
            if (it.equals("-")) {
                View d = new View(this);
                d.setBackgroundColor(0x26000000);
                LinearLayout.LayoutParams dl = new LinearLayout.LayoutParams(MATCH, 1);
                dl.setMargins(dp(8), dp(4), dp(8), dp(4));
                box.addView(d, dl);
                continue;
            }
            final TextView t = tv(it, 13.5f, 0xFF1D1D1F, false);
            t.setPadding(dp(12), dp(5), dp(36), dp(5));
            t.setOnTouchListener((v, e) -> {
                int a = e.getActionMasked();
                if (a == MotionEvent.ACTION_DOWN) { t.setBackground(rr(0xFF0A84FF, dp(6))); t.setTextColor(Color.WHITE); }
                else if (a == MotionEvent.ACTION_UP || a == MotionEvent.ACTION_CANCEL) { t.setBackground(null); t.setTextColor(0xFF1D1D1F); }
                return false;
            });
            t.setOnClickListener(v -> {
                dismissOverlays();
                if (it.startsWith("System Settings") || it.startsWith("Settings")) startActivity(new Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                else if (it.equals("Applications")) showLaunchpad(null);
                else if (it.startsWith("App Store")) openDock(DOCK[15], v);
            });
            box.addView(t);
        }
        int[] loc = new int[2];
        anchor.getLocationInWindow(loc);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(WRAP, WRAP);
        lp.leftMargin = Math.max(dp(6), loc[0] - dp(4));
        lp.topMargin = mbH() + dp(2);
        FrameLayout sc = scrim();
        sc.addView(box, lp);
        box.setPivotX(0); box.setPivotY(0); box.setScaleX(.96f); box.setScaleY(.96f); box.setAlpha(0);
        box.animate().scaleX(1).scaleY(1).alpha(1).setDuration(140).start();
    }

    // ===== overlays =====
    FrameLayout scrim() {
        FrameLayout sc = new FrameLayout(this);
        sc.setOnClickListener(v -> dismissOverlays());
        root.addView(sc, new FrameLayout.LayoutParams(MATCH, MATCH));
        overlay = sc;
        return sc;
    }
    void dismissOverlays() {
        if (overlay == null) return;
        final FrameLayout o = overlay;
        overlay = null;
        InputMethodManager im = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        im.hideSoftInputFromWindow(o.getWindowToken(), 0);
        o.animate().alpha(0).setDuration(130).withEndAction(() -> root.removeView(o)).start();
    }

    // ===== calendar widget =====
    void buildWidget() {
        Calendar cal = Calendar.getInstance();
        int today = cal.get(Calendar.DAY_OF_MONTH);
        Calendar f = (Calendar) cal.clone();
        f.set(Calendar.DAY_OF_MONTH, 1);
        int off = f.get(Calendar.DAY_OF_WEEK) - 1, dim = cal.getActualMaximum(Calendar.DAY_OF_MONTH);
        LinearLayout w = new LinearLayout(this);
        w.setOrientation(LinearLayout.VERTICAL);
        w.setPadding(dp(12), dp(10), dp(12), dp(8));
        w.setBackground(new GlassDrawable(w, dp(22), dp(0.8f), 0xD9FFFFFF));
        w.addView(tv(new SimpleDateFormat("MMMM", Locale.US).format(cal.getTime()).toUpperCase(Locale.US), 9.5f, 0xFFFF3B30, true));
        String[] dn = {"S", "M", "T", "W", "T", "F", "S"};
        LinearLayout hr = new LinearLayout(this);
        for (String d : dn) { TextView t = tv(d, 8.5f, 0xFF8E8E93, true); t.setGravity(Gravity.CENTER); hr.addView(t, new LinearLayout.LayoutParams(0, dp(15), 1f)); }
        LinearLayout.LayoutParams hl = new LinearLayout.LayoutParams(MATCH, WRAP);
        hl.topMargin = dp(3);
        w.addView(hr, hl);
        int d = 1 - off;
        for (int r = 0; r < 6; r++) {
            LinearLayout row = new LinearLayout(this);
            for (int c = 0; c < 7; c++, d++) {
                FrameLayout cell = new FrameLayout(this);
                if (d >= 1 && d <= dim) {
                    TextView t = tv(String.valueOf(d), 8.5f, d == today ? Color.WHITE : (c == 0 || c == 6 ? 0xFF8E8E93 : 0xFF1C1C1E), d == today);
                    t.setGravity(Gravity.CENTER);
                    if (d == today) { GradientDrawable g = new GradientDrawable(); g.setShape(GradientDrawable.OVAL); g.setColor(0xFFFF3B30); t.setBackground(g); }
                    cell.addView(t, new FrameLayout.LayoutParams(dp(15), dp(15), Gravity.CENTER));
                }
                row.addView(cell, new LinearLayout.LayoutParams(0, dp(15), 1f));
            }
            w.addView(row);
        }
        widget = w;
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(dp(158), WRAP, Gravity.TOP | Gravity.LEFT);
        lp.topMargin = dp(40); lp.leftMargin = dp(16);
        root.addView(w, lp);
    }

    // ===== dock =====
    void buildDock() {
        final int sz = dp(50);
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(8), dp(6), dp(8), dp(6));
        for (final String[] it : DOCK) {
            ImageView iv = new ImageView(this);
            iv.setImageResource(res(it[1]));
            iv.setOnTouchListener((v, e) -> {
                int a = e.getActionMasked();
                if (a == MotionEvent.ACTION_DOWN) v.animate().scaleX(1.25f).scaleY(1.25f).translationY(-dp(6)).setDuration(120).start();
                else if (a == MotionEvent.ACTION_UP || a == MotionEvent.ACTION_CANCEL) v.animate().scaleX(1f).scaleY(1f).translationY(0).setDuration(160).start();
                return false;
            });
            iv.setOnClickListener(v -> openDock(it, v));
            iv.setOnLongClickListener(v -> { showLaunchpad(it[0]); return true; });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(sz, sz);
            lp.setMargins(dp(2.5f), 0, dp(2.5f), 0);
            row.addView(iv, lp);
        }
        View sep = new View(this);
        sep.setBackgroundColor(0x4DFFFFFF);
        LinearLayout.LayoutParams sl = new LinearLayout.LayoutParams(dp(1), dp(34));
        sl.setMargins(dp(5), 0, dp(5), 0);
        row.addView(sep, sl);
        FrameLayout trash = new FrameLayout(this);
        trash.setBackground(new GlassDrawable(trash, dp(12), dp(1), 0x40FFFFFF));
        trash.addView(new IconView(this, IconView.TRASH, Color.WHITE), new FrameLayout.LayoutParams(dp(28), dp(28), Gravity.CENTER));
        LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(sz - dp(4), sz - dp(4));
        tl.setMargins(dp(4), 0, dp(2), 0);
        row.addView(trash, tl);
        HorizontalScrollView hs = new HorizontalScrollView(this);
        hs.setHorizontalScrollBarEnabled(false);
        hs.setOverScrollMode(View.OVER_SCROLL_NEVER);
        hs.setPadding(0, dp(24), 0, 0);
        hs.setClipToPadding(false);
        hs.addView(row);
        View pill = new View(this);
        pill.setBackground(new GlassDrawable(pill, dp(24), dp(1), 0x3DFFFFFF));
        FrameLayout box = new FrameLayout(this);
        box.addView(pill, new FrameLayout.LayoutParams(MATCH, dp(62), Gravity.BOTTOM));
        box.addView(hs, new FrameLayout.LayoutParams(WRAP, WRAP, Gravity.BOTTOM));
        FrameLayout.LayoutParams bl = new FrameLayout.LayoutParams(WRAP, WRAP, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        bl.bottomMargin = dp(6); bl.leftMargin = dp(8); bl.rightMargin = dp(8);
        root.addView(box, bl);
    }

    void bounce(View v) {
        ObjectAnimator a = ObjectAnimator.ofFloat(v, "translationY", 0, -dp(20), 0);
        a.setDuration(380); a.setRepeatCount(2); a.setInterpolator(new DecelerateInterpolator());
        a.start();
    }

    void tip() { Toast.makeText(this, "Tahan lama ikon untuk memilih aplikasi", Toast.LENGTH_SHORT).show(); }

    void openDock(String[] it, View v) {
        bounce(v);
        String saved = prefs.getString("map_" + it[0], null);
        if (saved != null) {
            Intent li = getPackageManager().getLaunchIntentForPackage(saved);
            if (li != null) { li.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); startActivity(li); return; }
        }
        String k = it[2];
        try {
            if (k.equals("launchpad")) showLaunchpad(null);
            else if (k.equals("settings")) startActivity(new Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            else if (k.equals("finder")) {
                Intent i = new Intent(Intent.ACTION_VIEW);
                i.setDataAndType(Uri.parse("content://com.android.externalstorage.documents/root/primary"), "vnd.android.document/root");
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(i);
            }
            else if (k.equals("dial")) startActivity(new Intent(Intent.ACTION_DIAL).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            else if (k.equals("market")) startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("market://search?q=a")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            else if (k.equals("cast")) startActivity(new Intent(Settings.ACTION_CAST_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            else if (k.startsWith("cat:")) {
                Intent i = Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, k.substring(4));
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(i);
            } else tip();
        } catch (Exception e) { tip(); }
    }

    // ===== apps =====
    List<App> loadApps() {
        PackageManager pm = getPackageManager();
        Intent in = new Intent(Intent.ACTION_MAIN);
        in.addCategory(Intent.CATEGORY_LAUNCHER);
        List<App> out = new ArrayList<>();
        for (ResolveInfo r : pm.queryIntentActivities(in, 0)) {
            if (r.activityInfo.packageName.equals(getPackageName())) continue;
            App a = new App();
            a.label = r.loadLabel(pm).toString();
            a.cn = new ComponentName(r.activityInfo.packageName, r.activityInfo.name);
            a.icon = r.loadIcon(pm);
            out.add(a);
        }
        Collections.sort(out, (x, y) -> x.label.compareToIgnoreCase(y.label));
        return out;
    }
    void ensureApps() {
        if (apps == null || System.currentTimeMillis() - appsTs > 60000) { apps = loadApps(); appsTs = System.currentTimeMillis(); }
    }
    void launch(App a) {
        try {
            Intent i = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setComponent(a.cn);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
            startActivity(i);
        } catch (Exception e) { }
    }

    // ===== Launchpad =====
    void showLaunchpad(final String assignKey) {
        dismissOverlays();
        ensureApps();
        final FrameLayout lp = new FrameLayout(this);
        lp.setBackground(new Backdrop(0x4D000000));
        lp.setOnClickListener(v -> dismissOverlays());
        root.addView(lp, new FrameLayout.LayoutParams(MATCH, MATCH));
        overlay = lp;
        final List<App> shown = new ArrayList<>(apps);
        final int cols = Math.max(4, Math.min(9, (int) (sw / den / 96)));
        final GridView gv = new GridView(this);
        gv.setNumColumns(cols);
        gv.setVerticalSpacing(dp(18));
        gv.setSelector(new ColorDrawable(0));
        gv.setPadding(dp(24), mbH() + dp(70), dp(24), dp(24));
        gv.setClipToPadding(false);
        gv.setVerticalScrollBarEnabled(false);
        final BaseAdapter ad = new BaseAdapter() {
            @Override public int getCount() { return shown.size(); }
            @Override public Object getItem(int i) { return shown.get(i); }
            @Override public long getItemId(int i) { return i; }
            @Override public View getView(int i, View cv, ViewGroup p) {
                LinearLayout c;
                if (cv == null) {
                    c = new LinearLayout(MainActivity.this);
                    c.setOrientation(LinearLayout.VERTICAL);
                    c.setGravity(Gravity.CENTER_HORIZONTAL);
                    c.addView(new ImageView(MainActivity.this), new LinearLayout.LayoutParams(dp(58), dp(58)));
                    TextView t = tv("", 11, Color.WHITE, false);
                    t.setGravity(Gravity.CENTER); t.setMaxLines(1); t.setEllipsize(android.text.TextUtils.TruncateAt.END);
                    t.setShadowLayer(dp(2), 0, 1, 0x66000000);
                    LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(MATCH, WRAP);
                    tl.topMargin = dp(6);
                    c.addView(t, tl);
                } else c = (LinearLayout) cv;
                final App a = shown.get(i);
                ((ImageView) c.getChildAt(0)).setImageDrawable(a.icon);
                ((TextView) c.getChildAt(1)).setText(a.label);
                c.setOnClickListener(v -> {
                    if (assignKey != null) {
                        prefs.edit().putString("map_" + assignKey, a.cn.getPackageName()).apply();
                        Toast.makeText(MainActivity.this, assignKey + " → " + a.label, Toast.LENGTH_SHORT).show();
                        dismissOverlays();
                    } else { dismissOverlays(); launch(a); }
                });
                return c;
            }
        };
        gv.setAdapter(ad);
        lp.addView(gv, new FrameLayout.LayoutParams(MATCH, MATCH));
        // search pill
        final LinearLayout sb = new LinearLayout(this);
        sb.setGravity(Gravity.CENTER_VERTICAL);
        sb.setPadding(dp(12), 0, dp(12), 0);
        sb.setClickable(true);
        sb.setBackground(new GlassDrawable(sb, dp(17), dp(0.8f), 0x40FFFFFF));
        sb.addView(new IconView(this, IconView.SEARCH, 0xCCFFFFFF), new LinearLayout.LayoutParams(dp(16), dp(16)));
        final EditText et = new EditText(this);
        et.setBackground(null); et.setSingleLine(true); et.setTextSize(14); et.setTextColor(Color.WHITE);
        et.setHintTextColor(0xB3FFFFFF);
        et.setHint(assignKey != null ? "Pilih aplikasi untuk " + assignKey : "Search");
        et.setPadding(dp(8), 0, 0, 0);
        et.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            public void onTextChanged(CharSequence s, int a, int b, int c) {}
            public void afterTextChanged(Editable e) {
                String q = e.toString().toLowerCase(Locale.getDefault()).trim();
                shown.clear();
                for (App a : apps) if (q.isEmpty() || a.label.toLowerCase(Locale.getDefault()).contains(q)) shown.add(a);
                ad.notifyDataSetChanged();
            }
        });
        sb.addView(et, new LinearLayout.LayoutParams(0, MATCH, 1f));
        FrameLayout.LayoutParams sl = new FrameLayout.LayoutParams(Math.min(dp(260), sw - dp(48)), dp(34), Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        sl.topMargin = mbH() + dp(14);
        lp.addView(sb, sl);
        lp.setScaleX(1.12f); lp.setScaleY(1.12f); lp.setAlpha(0);
        lp.animate().scaleX(1).scaleY(1).alpha(1).setDuration(260).setInterpolator(new DecelerateInterpolator(1.6f)).start();
    }

    // ===== Spotlight =====
    void showSpotlight() {
        dismissOverlays();
        ensureApps();
        FrameLayout sc = scrim();
        sc.setBackgroundColor(0x1A000000);
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(18), dp(12), dp(18), dp(12));
        box.setClickable(true);
        box.setBackground(new GlassDrawable(box, dp(26), dp(1), 0xB8F4F4F6));
        LinearLayout sr = new LinearLayout(this);
        sr.setGravity(Gravity.CENTER_VERTICAL);
        sr.addView(new IconView(this, IconView.SEARCH, 0xFF6E6E73), new LinearLayout.LayoutParams(dp(24), dp(24)));
        final EditText et = new EditText(this);
        et.setBackground(null); et.setSingleLine(true); et.setTextSize(21); et.setTextColor(0xFF1D1D1F);
        et.setHint("Spotlight Search"); et.setHintTextColor(0xFF8E8E93);
        et.setImeOptions(EditorInfo.IME_ACTION_GO);
        et.setPadding(dp(10), dp(6), 0, dp(6));
        sr.addView(et, new LinearLayout.LayoutParams(0, WRAP, 1f));
        box.addView(sr);
        HorizontalScrollView cs = new HorizontalScrollView(this);
        cs.setHorizontalScrollBarEnabled(false);
        LinearLayout chips = new LinearLayout(this);
        for (String n : new String[]{"Calendar", "Applications", "Actions", "Messages", "Help", "Mail", "Settings", "Contacts"}) {
            TextView t = tv(n, 12, 0xFF6E6E73, false);
            t.setPadding(dp(10), dp(4), dp(10), dp(4));
            t.setBackground(rr(0xB3FFFFFF, dp(12)));
            LinearLayout.LayoutParams cl = new LinearLayout.LayoutParams(WRAP, WRAP);
            cl.rightMargin = dp(6);
            chips.addView(t, cl);
        }
        cs.addView(chips);
        LinearLayout.LayoutParams csl = new LinearLayout.LayoutParams(MATCH, WRAP);
        csl.topMargin = dp(10); csl.bottomMargin = dp(6);
        box.addView(cs, csl);
        final LinearLayout res = new LinearLayout(this);
        res.setOrientation(LinearLayout.VERTICAL);
        box.addView(res);
        et.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            public void onTextChanged(CharSequence s, int a, int b, int c) {}
            public void afterTextChanged(Editable e) {
                res.removeAllViews();
                String q = e.toString().toLowerCase(Locale.getDefault()).trim();
                if (q.isEmpty()) return;
                int n = 0;
                for (final App a : apps) {
                    if (!a.label.toLowerCase(Locale.getDefault()).contains(q)) continue;
                    LinearLayout row = new LinearLayout(MainActivity.this);
                    row.setGravity(Gravity.CENTER_VERTICAL);
                    row.setPadding(dp(8), dp(5), dp(8), dp(5));
                    if (n == 0) row.setBackground(rr(0x26000000, dp(10)));
                    ImageView iv = new ImageView(MainActivity.this);
                    iv.setImageDrawable(a.icon);
                    row.addView(iv, new LinearLayout.LayoutParams(dp(32), dp(32)));
                    TextView t = tv(a.label, 15, 0xFF1D1D1F, false);
                    t.setPadding(dp(12), 0, 0, 0);
                    row.addView(t);
                    row.setOnClickListener(v -> { dismissOverlays(); launch(a); });
                    res.addView(row);
                    if (++n >= 6) break;
                }
            }
        });
        et.setOnEditorActionListener((v, id, ev) -> {
            if (res.getChildCount() > 0) res.getChildAt(0).performClick();
            return true;
        });
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(Math.min(dp(560), sw - dp(24)), WRAP, Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        lp.topMargin = Math.max(mbH() + dp(16), (int) (sh * .16f));
        sc.addView(box, lp);
        box.setScaleX(.95f); box.setScaleY(.95f); box.setAlpha(0);
        box.animate().scaleX(1).scaleY(1).alpha(1).setDuration(180).start();
        et.requestFocus();
        h.postDelayed(() -> ((InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE)).showSoftInput(et, 0), 200);
    }

    // ===== Control Center =====
    FrameLayout tile(FrameLayout p, int col, int row, int cw, int ch, int u, int g, float rad) {
        FrameLayout t = new FrameLayout(this);
        int w = cw * u + (cw - 1) * g, hh = ch * u + (ch - 1) * g;
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(w, hh);
        lp.leftMargin = col * (u + g); lp.topMargin = row * (u + g);
        t.setBackground(new GlassDrawable(t, rad, dp(1), 0x33FFFFFF));
        t.setClickable(true);
        p.addView(t, lp);
        return t;
    }
    void ctl(FrameLayout t, int icon, String title, String sub, boolean on, Runnable a) {
        LinearLayout l = new LinearLayout(this);
        l.setGravity(Gravity.CENTER_VERTICAL);
        l.setPadding(dp(11), 0, dp(6), 0);
        FrameLayout c = new FrameLayout(this);
        GradientDrawable gd = new GradientDrawable();
        gd.setShape(GradientDrawable.OVAL);
        gd.setColor(on ? 0xFF0A84FF : 0x59FFFFFF);
        c.setBackground(gd);
        c.addView(new IconView(this, icon, Color.WHITE), new FrameLayout.LayoutParams(dp(20), dp(20), Gravity.CENTER));
        l.addView(c, new LinearLayout.LayoutParams(dp(38), dp(38)));
        LinearLayout tx = new LinearLayout(this);
        tx.setOrientation(LinearLayout.VERTICAL);
        tx.setPadding(dp(8), 0, 0, 0);
        tx.addView(tv(title, 12.5f, Color.WHITE, true));
        if (sub != null) tx.addView(tv(sub, 11, 0xCCFFFFFF, false));
        l.addView(tx);
        t.addView(l, new FrameLayout.LayoutParams(MATCH, MATCH));
        t.setOnClickListener(v -> a.run());
    }
    void small(FrameLayout t, int icon, Runnable a) {
        t.addView(new IconView(this, icon, Color.WHITE), new FrameLayout.LayoutParams(dp(22), dp(22), Gravity.CENTER));
        t.setOnClickListener(v -> a.run());
    }
    void sys(String action) {
        try { startActivity(new Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); } catch (Exception e) { }
    }
    void mediaKey(int code) {
        AudioManager am = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        am.dispatchMediaKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, code));
        am.dispatchMediaKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, code));
    }
    String wifiState() {
        try { return ((WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE)).isWifiEnabled() ? "On" : "Off"; }
        catch (Throwable t) { return null; }
    }
    String btState() {
        try { BluetoothAdapter a = BluetoothAdapter.getDefaultAdapter(); return a != null && a.isEnabled() ? "On" : "Off"; }
        catch (Throwable t) { return null; }
    }

    void showControlCenter() {
        dismissOverlays();
        final int u = dp(62), g = dp(8), W = 4 * u + 3 * g, hS = dp(56);
        FrameLayout p = new FrameLayout(this);
        FrameLayout wifi = tile(p, 0, 0, 2, 1, u, g, u / 2f);
        String ws = wifiState();
        ctl(wifi, IconView.WIFI, "Wi-Fi", ws, !"Off".equals(ws), () -> {
            if (Build.VERSION.SDK_INT >= 29) sys(Settings.Panel.ACTION_INTERNET_CONNECTIVITY); else sys(Settings.ACTION_WIFI_SETTINGS);
        });
        FrameLayout np = tile(p, 2, 0, 2, 2, u, g, dp(30));
        LinearLayout nl = new LinearLayout(this);
        nl.setOrientation(LinearLayout.VERTICAL);
        nl.setPadding(dp(12), dp(12), dp(12), dp(10));
        View art = new View(this);
        art.setBackground(rr(0x40FFFFFF, dp(10)));
        nl.addView(art, new LinearLayout.LayoutParams(dp(44), dp(44)));
        nl.addView(new View(this), new LinearLayout.LayoutParams(1, 0, 1f));
        nl.addView(tv("Not Playing", 12, Color.WHITE, true));
        LinearLayout ctr = new LinearLayout(this);
        ctr.setGravity(Gravity.CENTER_VERTICAL);
        int[][] keys = {{IconView.PREV, KeyEvent.KEYCODE_MEDIA_PREVIOUS}, {IconView.PLAY, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE}, {IconView.NEXT, KeyEvent.KEYCODE_MEDIA_NEXT}};
        for (final int[] k : keys) {
            IconView iv = new IconView(this, k[0], Color.WHITE);
            iv.setOnClickListener(v -> mediaKey(k[1]));
            ctr.addView(iv, new LinearLayout.LayoutParams(0, dp(30), 1f));
        }
        LinearLayout.LayoutParams cl = new LinearLayout.LayoutParams(MATCH, WRAP);
        cl.topMargin = dp(2);
        nl.addView(ctr, cl);
        np.addView(nl, new FrameLayout.LayoutParams(MATCH, MATCH));
        FrameLayout bt = tile(p, 0, 1, 2, 1, u, g, u / 2f);
        String bs = btState();
        ctl(bt, IconView.BT, "Bluetooth", bs, !"Off".equals(bs), () -> sys(Settings.ACTION_BLUETOOTH_SETTINGS));
        FrameLayout ad = tile(p, 0, 2, 2, 1, u, g, u / 2f);
        ctl(ad, IconView.AIRDROP, "AirDrop", "Everyone", true, () -> Toast.makeText(this, "AirDrop: Everyone", Toast.LENGTH_SHORT).show());
        small(tile(p, 2, 2, 1, 1, u, g, u / 2f), IconView.STAGE, () -> showLaunchpad(null));
        small(tile(p, 3, 2, 1, 1, u, g, u / 2f), IconView.MIRROR, () -> sys(Settings.ACTION_CAST_SETTINGS));
        small(tile(p, 0, 3, 1, 1, u, g, u / 2f), IconView.CONTRAST, () -> sys(Settings.ACTION_DISPLAY_SETTINGS));
        small(tile(p, 1, 3, 1, 1, u, g, u / 2f), IconView.SHOT, () -> Toast.makeText(this, "Gunakan tombol screenshot perangkat", Toast.LENGTH_SHORT).show());
        FrameLayout fc = tile(p, 2, 3, 2, 1, u, g, u / 2f);
        ctl(fc, IconView.MOON, "Focus", null, false, () -> sys(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS));
        int y = 4 * (u + g);
        float br = getWindow().getAttributes().screenBrightness;
        if (br < 0) {
            try { br = Settings.System.getInt(getContentResolver(), Settings.System.SCREEN_BRIGHTNESS) / 255f; } catch (Exception e) { br = .6f; }
        }
        sliderTile(p, "Display", y, W, hS, br, v -> {
            android.view.WindowManager.LayoutParams wl = getWindow().getAttributes();
            wl.screenBrightness = Math.max(.02f, v);
            getWindow().setAttributes(wl);
        });
        final AudioManager am = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        final int mx = Math.max(1, am.getStreamMaxVolume(AudioManager.STREAM_MUSIC));
        sliderTile(p, "Sound", y + hS + g, W, hS, am.getStreamVolume(AudioManager.STREAM_MUSIC) / (float) mx,
                v -> am.setStreamVolume(AudioManager.STREAM_MUSIC, Math.round(v * mx), 0));
        TextView ed = tv("Edit Controls", 12, Color.WHITE, false);
        ed.setPadding(dp(14), dp(5), dp(14), dp(5));
        ed.setBackground(new GlassDrawable(ed, dp(14), dp(1), 0x33FFFFFF));
        ed.setClickable(true);
        FrameLayout.LayoutParams el = new FrameLayout.LayoutParams(WRAP, WRAP, Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        el.topMargin = y + 2 * (hS + g) + dp(2);
        p.addView(ed, el);
        FrameLayout sc = scrim();
        FrameLayout.LayoutParams pl = new FrameLayout.LayoutParams(W, y + 2 * (hS + g) + dp(34), Gravity.TOP | Gravity.RIGHT);
        pl.topMargin = mbH() + dp(4); pl.rightMargin = dp(10);
        p.setClickable(true);
        sc.addView(p, pl);
        p.setPivotX(W); p.setPivotY(0); p.setScaleX(.9f); p.setScaleY(.9f); p.setAlpha(0);
        p.animate().scaleX(1).scaleY(1).alpha(1).setDuration(220).setInterpolator(new DecelerateInterpolator(1.5f)).start();
    }

    void sliderTile(FrameLayout p, String label, int y, int W, int hS, float val, SliderView.Listener l) {
        FrameLayout t = new FrameLayout(this);
        t.setBackground(new GlassDrawable(t, dp(18), dp(1), 0x33FFFFFF));
        TextView lb = tv(label, 12, Color.WHITE, true);
        lb.setPadding(dp(14), dp(8), 0, 0);
        t.addView(lb);
        FrameLayout.LayoutParams sl = new FrameLayout.LayoutParams(MATCH, dp(28), Gravity.BOTTOM);
        sl.setMargins(dp(10), 0, dp(10), dp(4));
        t.addView(new SliderView(this, Math.max(0f, Math.min(1f, val)), l), sl);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(W, hS);
        lp.topMargin = y;
        p.addView(t, lp);
    }
}
