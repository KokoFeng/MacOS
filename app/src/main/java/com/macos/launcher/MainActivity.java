package com.macos.launcher;

import android.animation.ObjectAnimator;
import android.app.ActivityOptions;
import android.app.AlertDialog;
import android.appwidget.AppWidgetHost;
import android.appwidget.AppWidgetHostView;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProviderInfo;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.Rect;
import android.os.Environment;
import android.webkit.MimeTypeMap;
import android.widget.ScrollView;
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
import android.view.animation.PathInterpolator;
import android.os.SystemClock;
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
    static class App { String label, builtin; ComponentName cn; Drawable icon; int cat; }
    static final String[][] BUILTIN = {{"Finder", "mac_finder", "0"}, {"Safari", "mac_safari", "3"}, {"Photos", "mac_photos", "3"},
        {"Terminal", "mac_terminal", "0"}, {"System Settings", "mac_system_settings", "0"}, {"Calculator", "mac_calculator", "0"},
        {"Photo Booth", "mac_photo_booth", "3"}, {"News", "mac_news", "3"}};

    static final int MATCH = ViewGroup.LayoutParams.MATCH_PARENT, WRAP = ViewGroup.LayoutParams.WRAP_CONTENT;
    static final String CAT = "cat:android.intent.category.";
    static final String[][] DOCK = {
        {"Finder", "mac_finder", "finder"}, {"Launchpad", "mac_apps", "launchpad"},
        {"Safari", "mac_safari", CAT + "APP_BROWSER"}, {"Messages", "mac_messages", CAT + "APP_MESSAGING"},
        {"Mail", "mac_mail", CAT + "APP_EMAIL"}, {"Maps", "mac_maps", "maps"},
        {"Photos", "mac_photos", "photos"}, {"Photo Booth", "mac_photo_booth", "camera"}, {"FaceTime", "mac_facetime", "none"},
        {"Phone", "mac_phone", "dial"}, {"Calendar", "mac_calendar", "calendar"},
        {"Contacts", "mac_contacts", "contacts"}, {"Reminders", "mac_reminders", "reminders"},
        {"Notes", "mac_notes", "notes"}, {"TV", "mac_tv", "none"}, {"Music", "mac_music", CAT + "APP_MUSIC"},
        {"News", "mac_news", "news"}, {"App Store", "mac_app_store", "market"}, {"iPhone Mirroring", "mac_iphone_mirroring", "cast"},
        {"System Settings", "mac_system_settings", "settings"}, {"Terminal", "mac_terminal", "terminal"},
        {"Calculator", "mac_calculator", "calculator"}};

    FrameLayout screen, root, overlay, winLayer, widgetLayer, iconLayer;
    final List<View> dockKids = new ArrayList<>();
    final List<Boolean> dockMagOk = new ArrayList<>();
    LinearLayout dockRow;
    HorizontalScrollView dockScroll;
    View hotStrip, topStrip;
    Boolean lastMb;
    boolean menuPeek;
    float[] magS = new float[0];
    float magPx;
    boolean magOn, magLoop, dockPeek;
    long magLast;
    Boolean lastAway;
    View dockBox;
    TouchpadLayer tp;
    boolean blocked;
    static boolean creditShown;
    LockScreen lock;
    long lastTouch = SystemClock.elapsedRealtime();
    TextView appMenuTv;
    AppWidgetManager awm;
    AppWidgetHost awh;
    int pendingId;
    WidgetBox editingBox;
    float lastX, lastY;
    int cascade = 0;
    final List<MacWindow> wins = new ArrayList<>();
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

    @Override protected void attachBaseContext(Context base) {
        float k = base.getSharedPreferences("mac", 0).getFloat("ui_scale", 1f);
        if (Math.abs(k - 1f) > 0.01f) {
            android.content.res.Configuration c = new android.content.res.Configuration(base.getResources().getConfiguration());
            c.densityDpi = Math.round(c.densityDpi * k);
            base = base.createConfigurationContext(c);
        }
        super.attachBaseContext(base);
    }

    @Override protected void onCreate(android.os.Bundle b) {
        super.onCreate(b);
        den = getResources().getDisplayMetrics().density;
        if (!Guard.ok(this)) { Guard.block(this); return; }
        android.util.DisplayMetrics rm = new android.util.DisplayMetrics();
        getWindowManager().getDefaultDisplay().getRealMetrics(rm);
        sw = Math.max(rm.widthPixels, rm.heightPixels);
        sh = Math.min(rm.widthPixels, rm.heightPixels);
        prefs = getSharedPreferences("mac", 0);
        Theme.load(this);
        if (Build.VERSION.SDK_INT >= 28) {
            android.view.WindowManager.LayoutParams wl0 = getWindow().getAttributes();
            wl0.layoutInDisplayCutoutMode = android.view.WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
            getWindow().setAttributes(wl0);
        }
        immersive();
        IntentFilter sf = new IntentFilter(Intent.ACTION_SCREEN_ON);
        sf.addAction(Intent.ACTION_SCREEN_OFF);
        registerReceiver(screenRx, sf);
        float br = prefs.getFloat("brightness", -1f);
        if (br >= 0) { android.view.WindowManager.LayoutParams bl0 = getWindow().getAttributes(); bl0.screenBrightness = br; getWindow().setAttributes(bl0); }
        screen = new FrameLayout(this);
        root = new FrameLayout(this);
        root.setClipChildren(false);
        screen.addView(root, new FrameLayout.LayoutParams(MATCH, MATCH));
        setContentView(screen);
        wall = new WallpaperView(this);
        root.addView(wall, new FrameLayout.LayoutParams(MATCH, MATCH));
        wall.setOnTouchListener((v, e) -> {
            if (e.getActionMasked() == MotionEvent.ACTION_DOWN) { lastX = e.getX(); lastY = e.getY(); exitWidgetEdit(); }
            return false;
        });
        wall.setOnLongClickListener(v -> {
            showPopup(lastX, lastY, new String[]{"Edit Widgets…", "Change Wallpaper…", "Open Finder"},
                    new Runnable[]{() -> showWidgetPicker(), () -> SettingsApp.open(this, "Wallpaper"), () -> openFinder()});
            return true;
        });
        initWidgets();
        iconLayer = new FrameLayout(this);
        root.addView(iconLayer, new FrameLayout.LayoutParams(MATCH, MATCH));
        buildWidget();
        winLayer = new FrameLayout(this);
        root.addView(winLayer, new FrameLayout.LayoutParams(MATCH, MATCH));
        buildDock();
        buildMenuBar();
        tp = new TouchpadLayer(this);
        tp.setEnabled(prefs.getBoolean("tp_on", false));
        lock = new LockScreen(this);
        h.post(() -> { if (!prefs.getBoolean("setup_done", false)) SetupAssistant.show(this); else afterSetup(); });
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
            if (widget != null) {
                ((FrameLayout.LayoutParams) widget.getLayoutParams()).topMargin = mbH() + dp(14);
                ((FrameLayout.LayoutParams) widget.getLayoutParams()).leftMargin = Math.max(dp(16), l);
            }
            root.requestLayout();
            buildDesktopIcons();
            return ins;
        });
        root.requestApplyInsets();
        String ro = prefs.getString("reopen", null);
        if (ro != null) {
            prefs.edit().remove("reopen").apply();
            final String pn = ro.startsWith("Settings:") ? ro.substring(9) : null;
            root.post(() -> SettingsApp.open(this, pn));
        }
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
    @Override public void onBackPressed() { if (lock != null && lock.locked) { lock.onBack(); return; } dismissOverlays(); }
    @Override public boolean dispatchTouchEvent(MotionEvent e) { lastTouch = SystemClock.elapsedRealtime(); return super.dispatchTouchEvent(e); }
    @Override protected void onResume() { super.onResume(); if (blocked) return; if (!Guard.ok(this)) { Guard.block(this); return; } h.removeCallbacks(tick); h.post(tick); if (lock != null) lock.onResume(); }
    @Override protected void onStart() { super.onStart(); try { awh.startListening(); } catch (Throwable t) { } }
    @Override protected void onStop() { super.onStop(); try { awh.stopListening(); } catch (Throwable t) { } }
    @Override protected void onDestroy() { super.onDestroy(); try { unregisterReceiver(screenRx); } catch (Throwable t) { } }
    @Override protected void onPause() { super.onPause(); if (blocked) return; h.removeCallbacks(tick); if (lock != null) lock.onPause(); }

    @Override public void onRequestPermissionsResult(int req, String[] perms, int[] res) {
        super.onRequestPermissionsResult(req, perms, res);
        if (req == 91) restartWith("Settings:Wi-Fi");
        ContactsApp.onPermission(req, res);
        CameraApp.onPermission(req, res);
        MapsApp.onPermission(req, res);
    }

    @Override protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req == 78) { if (res == RESULT_OK) afterBind(pendingId); else awh.deleteAppWidgetId(pendingId); return; }
        if (req == 79) { if (res == RESULT_OK) placeWidget(pendingId); else awh.deleteAppWidgetId(pendingId); return; }
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
            restartWith("Settings:Wallpaper");
        } catch (Exception e) { Toast.makeText(this, "Gagal memuat wallpaper", Toast.LENGTH_SHORT).show(); }
    }

    // ===== clock / battery =====
    boolean screenOn = true;
    long screenFlush = SystemClock.elapsedRealtime();
    void flushScreen() {
        long now = SystemClock.elapsedRealtime();
        if (screenOn) BattLog.addScreen(prefs, (now - screenFlush) / 1000);
        screenFlush = now;
    }
    final android.content.BroadcastReceiver screenRx = new android.content.BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) {
            flushScreen();
            screenOn = Intent.ACTION_SCREEN_ON.equals(i.getAction());
            if (lock != null) { if (screenOn) lock.onScreenOn(); else lock.onScreenOff(); }
            screenFlush = SystemClock.elapsedRealtime();
        }
    };

    final Runnable tick = new Runnable() {
        @Override public void run() {
            clockTv.setText(new SimpleDateFormat("EEE MMM d  h:mm a", Locale.US).format(new Date()));
            Intent b = registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
            if (b != null) {
                int l = b.getIntExtra(BatteryManager.EXTRA_LEVEL, -1), s = b.getIntExtra(BatteryManager.EXTRA_SCALE, 100);
                if (l >= 0) { battTv.setText((l * 100 / s) + "%"); battIc.setLevel(l / (float) s); BattLog.sample(prefs, l * 100 / s); }
                flushScreen();
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
            final boolean first = i == 0;
            if (first) appMenuTv = t;
            t.setShadowLayer(dp(2), 0, 0, 0x55000000);
            t.setPadding(dp(8), dp(4), dp(8), dp(4));
            t.setOnClickListener(v -> showMenu(first ? appMenuTv.getText().toString() : n, v));
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
        clockTv.setOnClickListener(v -> showNotificationCenter());
        menuBar.addView(clockTv);
        root.addView(menuBar, new FrameLayout.LayoutParams(MATCH, dp(26), Gravity.TOP));
        mbPeekRef = new Runnable() { @Override public void run() { mbPeekOff.run(); } };
        topStrip = new View(this);
        topStrip.setVisibility(View.GONE);
        topStrip.setOnTouchListener((v, e) -> {
            if (!menuPeek) { menuPeek = true; lastMb = null; updateDock(); }
            h.removeCallbacks(mbPeekRef);
            h.postDelayed(mbPeekRef, 3500);
            return false;
        });
        root.addView(topStrip, new FrameLayout.LayoutParams(MATCH, dp(12), Gravity.TOP));
    }

    String[] menuItems(String n) {
        switch (n) {
            case "apple": return new String[]{"About This Mac", "-", "System Settings…", "App Store…", "-", "Recent Items", "-", "Force Quit…", "-", "Sleep", "Restart…", "Shut Down…", "-", "Lock Screen", "Log Out"};
            case "Finder": return new String[]{"About Finder", "-", "Settings…", "-", "Empty Trash…", "-", "Services", "-", "Hide Finder", "Hide Others", "Show All"};
            case "File":
                if (appMenuTv != null && "Safari".equals(appMenuTv.getText().toString()))
                    return new String[]{"New Window", "New Tab", "-", "Open Location…", "-", "Close Window", "Close Tab", "-", "Add to Dock…", "-", "Share…"};
                return new String[]{"New Finder Window", "New Folder", "Open", "-", "Get Info", "Rename", "-", "Close Window"};
            case "Edit": return new String[]{"Undo", "Redo", "-", "Cut", "Copy", "Paste", "Select All"};
            case "View": return new String[]{"as Icons", "as List", "as Columns", "as Gallery", "-", "Show Path Bar", "Show Status Bar"};
            case "Go": return new String[]{"Back", "Forward", "Enclosing Folder", "-", "Recents", "Documents", "Desktop", "Downloads", "Applications"};
            case "Window": return new String[]{"Minimize", "Zoom", "-", "Bring All to Front"};
            case "Help": return new String[]{"Search", "-", "macOS Help"};
            default: return new String[]{"About " + n, "-", "Settings…", "-", "Hide " + n, "Hide Others", "Show All"};
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
                if (it.startsWith("System Settings") || it.startsWith("Settings")) SettingsApp.open(this, null);
                else if (it.equals("About This Mac")) SettingsApp.open(this, "General");
                else if (it.equals("Applications")) showLaunchpad(null);
                else if (it.startsWith("App Store")) openDock(DOCK[15], v);
                else menuAction(it);
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
        if (tp != null) tp.toFront();
        return sc;
    }
    void dismissOverlays() {
        if (overlay == null) return;
        if (lock == null || !lock.locked) getWindow().setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING);
        final FrameLayout o = overlay;
        overlay = null;
        InputMethodManager im = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        im.hideSoftInputFromWindow(o.getWindowToken(), 0);
        o.animate().alpha(0).setDuration(130).withEndAction(() -> root.removeView(o)).start();
    }

    // ===== calendar widget =====
    void buildWidget() {
        if (!Theme.showCal) return;
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
    List<Store.Item> dockList() {
        List<Store.Item> l = Store.load(prefs, "dock_items", Store.defaults());
        if (!prefs.getBoolean("dock_v8", false)) {
            prefs.edit().putBoolean("dock_v8", true).apply();
            if (prefs.getString("dock_items", null) != null) {
                String[] add = {"Photo Booth", "Photos", "News", "Music"};
                for (int k = 0; k < add.length; k += 2) {
                    boolean has = false;
                    int at = l.size();
                    for (int i = 0; i < l.size(); i++) { if (l.get(i).name.equals(add[k])) has = true; if (l.get(i).name.equals(add[k + 1])) at = i + 1; }
                    if (!has) l.add(Math.min(at, l.size()), new Store.Item("b", add[k], ""));
                }
                Store.save(prefs, "dock_items", l);
            }
        }
        return l;
    }

    boolean dockAway() {
        if (prefs.getBoolean("dock_auto", false)) return true;
        MacWindow t = topWindow();
        return t != null && t.isZoomed();
    }

    Runnable mbPeekRef = null;
    final Runnable mbPeekOff = () -> {
        if (overlay != null) { h.postDelayed(mbPeekRef, 2500); return; }
        menuPeek = false; lastMb = null; updateDock();
    };

    void updateMenuBarHide() {
        if (menuBar == null) return;
        MacWindow t = topWindow();
        boolean zt = t != null && t.isZoomed();
        boolean hide = zt && !menuPeek;
        if (topStrip != null) topStrip.setVisibility(zt ? View.VISIBLE : View.GONE);
        if (lastMb == null || lastMb != hide) {
            lastMb = hide;
            menuBar.animate().translationY(hide ? -(mbH() + dp(4)) : 0).setDuration(260)
                    .setInterpolator(new PathInterpolator(.2f, .9f, .25f, 1f)).start();
        }
    }

    void updateDock() {
        updateMenuBarHide();
        if (dockBox == null) return;
        boolean away = dockAway();
        boolean hide = away && !dockPeek;
        if (hotStrip != null) hotStrip.setVisibility(away ? View.VISIBLE : View.GONE);
        if (lastAway == null || lastAway != hide) {
            lastAway = hide;
            float ty = hide ? (dockBox.getHeight() > 0 ? dockBox.getHeight() : dp(150)) + dp(16) : 0;
            dockBox.animate().translationY(ty).setDuration(280).setInterpolator(new PathInterpolator(.2f, .9f, .25f, 1f)).start();
            if (hide) { magOn = false; startMag(); }
        }
        refreshDots();
    }

    final Runnable peekOff = () -> { dockPeek = false; lastAway = null; updateDock(); };

    void refreshDots() {
        boolean on = prefs.getBoolean("dock_dots", true);
        for (View k : dockKids) {
            Object t = k.getTag();
            View dot = k.findViewWithTag("dot");
            if (dot == null || !(t instanceof Store.Item)) continue;
            Store.Item it = (Store.Item) t;
            boolean open = on && !it.type.equals("a") && findWindow(it.name) != null;
            dot.setVisibility(open ? View.VISIBLE : View.INVISIBLE);
        }
    }

    boolean magEnabled() { return prefs.getBoolean("dock_mag", true); }

    /** Pointer (finger or trackpad cursor) position, drives Dock magnification and Dock reveal. */
    void dockPointer(float x, float y, boolean act) {
        if (dockBox == null || dockKids.isEmpty()) return;
        if (dockAway()) {
            if (act && y > sh - dp(14)) {
                if (!dockPeek) { dockPeek = true; lastAway = null; updateDock(); }
                h.removeCallbacks(peekOff); h.postDelayed(peekOff, 4000);
            } else if (dockPeek && (!act || y < sh - dockBox.getHeight() - dp(30))) {
                h.removeCallbacks(peekOff); peekOff.run();
            }
        }
        boolean visible = !dockAway() || dockPeek;
        magOn = act && visible && magEnabled() && y > sh - dockBox.getHeight() - dp(40);
        magPx = x;
        startMag();
    }

    void startMag() {
        if (magLoop || dockRow == null) return;
        magLoop = true;
        magLast = SystemClock.uptimeMillis();
        dockRow.postOnAnimation(magStep);
    }

    final Runnable magStep = new Runnable() {
        @Override public void run() {
            if (dockRow == null || dockScroll == null) { magLoop = false; return; }
            long now = SystemClock.uptimeMillis();
            float dt = Math.min(48, now - magLast);
            magLast = now;
            float k = 1f - (float) Math.exp(-dt / 42f);
            int n = dockKids.size();
            if (magS.length != n) { magS = new float[n]; java.util.Arrays.fill(magS, 1f); }
            float sz = dp(Theme.dockSize);
            float maxS = 1.25f + prefs.getFloat("dock_mag_v", .55f) * 0.9f;
            float sigma = sz * 0.85f;
            int[] hl = new int[2];
            dockScroll.getLocationInWindow(hl);
            float origin = hl[0] + dockScroll.getPaddingLeft() + dockRow.getLeft() - dockScroll.getScrollX();
            float pxRow = magPx - origin;
            boolean busy = false;
            float[] slotS = new float[n], slotW = new float[n];
            for (int i = 0; i < n; i++) {
                View v = dockKids.get(i);
                LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) v.getLayoutParams();
                slotS[i] = v.getLeft() - lp.leftMargin;
                slotW[i] = v.getWidth() + lp.leftMargin + lp.rightMargin;
                float target = 1f;
                if (magOn && dockMagOk.get(i)) {
                    float d = Math.abs(pxRow - (v.getLeft() + v.getWidth() / 2f));
                    target = 1f + (maxS - 1f) * (float) Math.exp(-(d * d) / (2f * sigma * sigma));
                }
                magS[i] += (target - magS[i]) * k;
                if (Math.abs(target - magS[i]) > 0.003f) busy = true; else magS[i] = target;
            }
            float run = n > 0 ? slotS[0] : 0, anchor = pxRow;
            float[] ms = new float[n];
            boolean found = false;
            for (int i = 0; i < n; i++) {
                ms[i] = run;
                if (!found && pxRow >= slotS[i] && pxRow < slotS[i] + slotW[i]) { anchor = run + (pxRow - slotS[i]) * magS[i]; found = true; }
                run += slotW[i] * magS[i];
            }
            float shift = pxRow - anchor;
            for (int i = 0; i < n; i++) {
                View v = dockKids.get(i);
                v.setPivotX(v.getWidth() / 2f);
                v.setPivotY(v.getHeight());
                v.setScaleX(magS[i]); v.setScaleY(magS[i]);
                v.setTranslationX(ms[i] + magS[i] * slotW[i] / 2f + shift - (slotS[i] + slotW[i] / 2f));
            }
            if (magOn || busy) dockRow.postOnAnimation(this); else magLoop = false;
        }
    };

    float[] dockTarget(String name) {
        for (View k : dockKids) {
            Object t = k.getTag();
            if (t instanceof Store.Item && ((Store.Item) t).name.equals(name)) {
                int[] l = new int[2];
                k.getLocationInWindow(l);
                return new float[]{l[0] + k.getWidth() / 2f, Math.min(l[1] + k.getHeight() * 0.3f, sh - dp(26))};
            }
        }
        return new float[]{sw / 2f, sh - dp(30)};
    }

    void buildDock() {
        int idx = -1;
        if (dockBox != null) { idx = root.indexOfChild(dockBox); root.removeView(dockBox); }
        if (hotStrip != null) { root.removeView(hotStrip); hotStrip = null; }
        dockKids.clear(); dockMagOk.clear(); magS = new float[0]; lastAway = null;
        final int sz = dp(Theme.dockSize);
        final LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(8), dp(6), dp(8), dp(6));
        row.setClipChildren(false);
        row.setClipToPadding(false);
        for (final Store.Item it : dockList()) {
            FrameLayout cell = new FrameLayout(this);
            cell.setClipChildren(false);
            cell.setTag(it);
            ImageView iv = new ImageView(this);
            iv.setImageDrawable(Store.icon(this, it));
            styleIcon(iv);
            cell.addView(iv, new FrameLayout.LayoutParams(MATCH, MATCH));
            View dot = new View(this);
            dot.setTag("dot");
            GradientDrawable dg = new GradientDrawable();
            dg.setShape(GradientDrawable.OVAL);
            dg.setColor(Theme.dark ? 0xE6FFFFFF : 0xB31D1D1F);
            dot.setBackground(dg);
            dot.setVisibility(View.INVISIBLE);
            FrameLayout.LayoutParams dl = new FrameLayout.LayoutParams(dp(4), dp(4), Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
            dl.bottomMargin = -dp(5);
            cell.addView(dot, dl);
            cell.setOnClickListener(v -> openItem(it, v));
            cell.setOnLongClickListener(v -> { dockPopup(it, v); return true; });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(sz, sz);
            lp.setMargins(dp(2.5f), 0, dp(2.5f), 0);
            row.addView(cell, lp);
            dockKids.add(cell); dockMagOk.add(true);
        }
        View sep = new View(this);
        sep.setBackgroundColor(0x4DFFFFFF);
        LinearLayout.LayoutParams sl = new LinearLayout.LayoutParams(dp(1), dp(34));
        sl.setMargins(dp(5), 0, dp(5), 0);
        row.addView(sep, sl);
        dockKids.add(sep); dockMagOk.add(false);
        FrameLayout trash = new FrameLayout(this);
        trash.setBackground(new GlassDrawable(trash, dp(12), dp(1), 0x40FFFFFF));
        trash.addView(new IconView(this, IconView.TRASH, Color.WHITE), new FrameLayout.LayoutParams(dp(28), dp(28), Gravity.CENTER));
        LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(sz - dp(4), sz - dp(4));
        tl.setMargins(dp(4), 0, dp(2), 0);
        row.addView(trash, tl);
        dockKids.add(trash); dockMagOk.add(true);
        dockRow = row;
        HorizontalScrollView hs = new HorizontalScrollView(this);
        hs.setHorizontalScrollBarEnabled(false);
        hs.setOverScrollMode(View.OVER_SCROLL_NEVER);
        hs.setPadding(0, dp(24), 0, 0);
        hs.setClipToPadding(false);
        hs.setClipChildren(false);
        hs.addView(row);
        dockScroll = hs;
        View pill = new View(this);
        pill.setBackground(new GlassDrawable(pill, dp(24), dp(1), 0x33FFFFFF));
        FrameLayout box = new FrameLayout(this) {
            @Override public boolean dispatchTouchEvent(MotionEvent e) {
                if (tp == null || !tp.injecting) {
                    int ac = e.getActionMasked();
                    if (ac == MotionEvent.ACTION_DOWN || ac == MotionEvent.ACTION_MOVE) dockPointer(e.getRawX(), e.getRawY(), true);
                    else if (ac == MotionEvent.ACTION_UP || ac == MotionEvent.ACTION_CANCEL) dockPointer(0, 0, false);
                }
                return super.dispatchTouchEvent(e);
            }
        };
        box.setClipChildren(false);
        box.setOnHoverListener((v, e) -> {
            int ac = e.getActionMasked();
            if (ac == MotionEvent.ACTION_HOVER_EXIT) dockPointer(0, 0, false); else dockPointer(e.getRawX(), e.getRawY(), true);
            return false;
        });
        box.addView(pill, new FrameLayout.LayoutParams(MATCH, sz + dp(12), Gravity.BOTTOM));
        box.addView(hs, new FrameLayout.LayoutParams(WRAP, WRAP, Gravity.BOTTOM));
        FrameLayout.LayoutParams bl = new FrameLayout.LayoutParams(WRAP, WRAP, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        bl.bottomMargin = dp(6); bl.leftMargin = dp(8); bl.rightMargin = dp(8);
        dockBox = box;
        if (idx >= 0) root.addView(box, idx, bl); else root.addView(box, bl);
        // thin strip at the bottom edge: touching it reveals a hidden Dock
        hotStrip = new View(this);
        hotStrip.setVisibility(View.GONE);
        hotStrip.setOnTouchListener((v, e) -> {
            if (e.getActionMasked() == MotionEvent.ACTION_DOWN) dockPointer(e.getRawX(), sh, true);
            return true;
        });
        root.addView(hotStrip, new FrameLayout.LayoutParams(MATCH, dp(14), Gravity.BOTTOM));
        box.post(() -> { lastAway = null; updateDock(); });
    }

    void bounce(View v) {
        if (v == null || !prefs.getBoolean("dock_anim", true)) return;
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
            if (li != null) { startExternal(li); return; }
        }
        String k = it[2];
        try {
            if (k.equals("launchpad")) showLaunchpad(null);
            else if (k.equals("settings")) SettingsApp.open(this, null);
            else if (k.equals("finder")) openFinder();
            else if (k.equals("photos")) openOrFocus("Photos", () -> PhotosApp.open(this));
            else if (k.equals("terminal")) openOrFocus("Terminal", () -> TerminalApp.open(this));
            else if (k.equals("calculator")) openOrFocus("Calculator", () -> CalcApp.open(this));
            else if (k.equals("camera")) openOrFocus("Photo Booth", () -> CameraApp.open(this));
            else if (k.equals("news")) openOrFocus("News", () -> NewsApp.open(this));
            else if (k.equals("maps")) openOrFocus("Maps", () -> MapsApp.open(this));
            else if (k.equals("notes")) openOrFocus("Notes", () -> NotesApp.open(this));
            else if (k.equals("calendar")) openOrFocus("Calendar", () -> CalendarApp.open(this));
            else if (k.equals("contacts")) openOrFocus("Contacts", () -> ContactsApp.open(this));
            else if (k.equals("reminders")) openOrFocus("Reminders", () -> RemindersApp.open(this));
            else if (it[0].equals("Safari")) {
                MacWindow w = findWindow("Safari");
                if (w != null) { w.restore(); focusWindow(w); } else SafariApp.open(this, null);
            }
            else if (k.equals("dial")) startExternal(new Intent(Intent.ACTION_DIAL));
            else if (k.equals("market")) startExternal(new Intent(Intent.ACTION_VIEW, Uri.parse("market://search?q=a")));
            else if (k.equals("cast")) startExternal(new Intent(Settings.ACTION_CAST_SETTINGS));
            else if (k.startsWith("cat:")) startExternal(Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, k.substring(4)));
            else tip();
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
            a.cat = catOf(pm, r.activityInfo.packageName);
            out.add(a);
        }
        for (String[] b : BUILTIN) {
            App a = new App();
            a.label = b[0];
            a.builtin = b[0];
            a.icon = getDrawable(res(b[1]));
            a.cat = Integer.parseInt(b[2]);
            out.add(a);
        }
        Collections.sort(out, (x, y) -> x.label.compareToIgnoreCase(y.label));
        return out;
    }
    void ensureApps() {
        if (apps == null || System.currentTimeMillis() - appsTs > 60000) { apps = loadApps(); appsTs = System.currentTimeMillis(); }
    }
    void launch(App a) {
        if (a.builtin != null) { openBuiltin(a.builtin); return; }
        try {
            Intent i = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setComponent(a.cn);
            i.addFlags(Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
            startExternal(i);
        } catch (Exception e) { }
    }

    // ===== Launchpad (Tahoe "Applications" panel) =====
    static final String[] CATS = {"Utilities", "Social", "Productivity & Finance", "Entertainment", "Information & Reading"};

    void showLaunchpad(final String assignKey) {
        dismissOverlays();
        ensureApps();
        final FrameLayout sc = scrim();
        sc.setBackgroundColor(0x26000000);
        final int pw = Math.min(dp(560), sw - dp(60)), ph = Math.min(dp(320), sh - mbH() - dp(96));
        final LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setClickable(true);
        panel.setPadding(dp(18), dp(12), dp(18), dp(8));
        panel.setBackground(new GlassDrawable(panel, dp(28), dp(1), 0x66FFFFFF));
        LinearLayout hd = new LinearLayout(this);
        hd.setGravity(Gravity.CENTER_VERTICAL);
        ImageView ai = new ImageView(this);
        ai.setImageResource(res("mac_apps"));
        hd.addView(ai, new LinearLayout.LayoutParams(dp(24), dp(24)));
        TextView ti = tv(assignKey != null ? "Pilih aplikasi untuk " + assignKey : "Applications", 17, 0xFF1D1D1F, false);
        ti.setPadding(dp(10), 0, 0, 0);
        hd.addView(ti, new LinearLayout.LayoutParams(0, WRAP, 1f));
        final EditText et = new EditText(this);
        et.setBackground(rr(0x40FFFFFF, dp(14)));
        et.setSingleLine(true);
        et.setTextSize(12.5f);
        et.setTextColor(0xFF1D1D1F);
        et.setHint("Search");
        et.setHintTextColor(0xFF6E6E73);
        et.setPadding(dp(12), 0, dp(12), 0);
        hd.addView(et, new LinearLayout.LayoutParams(dp(130), dp(28)));
        panel.addView(hd);

        final int[] sel = {-1};
        final List<App> shown = new ArrayList<>();
        final List<TextView> chipViews = new ArrayList<>();
        final GridView gv = new GridView(this);
        final BaseAdapter ad = new BaseAdapter() {
            @Override public int getCount() { return shown.size(); }
            @Override public Object getItem(int i) { return shown.get(i); }
            @Override public long getItemId(int i) { return i; }
            @Override public View getView(int i, View cv, ViewGroup p) {
                final App ap = shown.get(i);
                LinearLayout c = new LinearLayout(MainActivity.this);
                c.setOrientation(LinearLayout.VERTICAL);
                c.setGravity(Gravity.CENTER_HORIZONTAL);
                ImageView iv = new ImageView(MainActivity.this);
                iv.setImageDrawable(ap.icon);
                c.addView(iv, new LinearLayout.LayoutParams(dp(54), dp(54)));
                TextView t = tv(ap.label, 11, 0xFF1D1D1F, false);
                t.setGravity(Gravity.CENTER);
                t.setSingleLine(true);
                t.setEllipsize(android.text.TextUtils.TruncateAt.END);
                LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(MATCH, WRAP);
                tl.topMargin = dp(4);
                c.addView(t, tl);
                c.setOnClickListener(v -> {
                    if (assignKey != null) {
                        if (ap.cn != null) prefs.edit().putString("map_" + assignKey, ap.cn.getPackageName()).apply();
                        Toast.makeText(MainActivity.this, assignKey + " → " + ap.label, Toast.LENGTH_SHORT).show();
                        dismissOverlays();
                    } else { dismissOverlays(); launch(ap); }
                });
                c.setOnLongClickListener(v -> { if (assignKey == null) appPopup(ap, v); return true; });
                return c;
            }
        };
        final Runnable refresh = () -> {
            shown.clear();
            String q = et.getText().toString().toLowerCase(Locale.getDefault()).trim();
            for (App ap : apps) {
                if (sel[0] >= 0 && ap.cat != sel[0]) continue;
                if (!q.isEmpty() && !ap.label.toLowerCase(Locale.getDefault()).contains(q)) continue;
                shown.add(ap);
            }
            ad.notifyDataSetChanged();
            for (int i = 0; i < chipViews.size(); i++) {
                boolean on = sel[0] == i;
                chipViews.get(i).setBackground(rr(on ? 0xCCFFFFFF : 0x66FFFFFF, dp(12)));
                chipViews.get(i).setTextColor(on ? 0xFF1D1D1F : 0xFF5A5A5F);
            }
        };
        HorizontalScrollView cs = new HorizontalScrollView(this);
        cs.setHorizontalScrollBarEnabled(false);
        LinearLayout chips = new LinearLayout(this);
        for (int i = 0; i < CATS.length; i++) {
            final int k = i;
            TextView t = tv(CATS[i], 12, 0xFF5A5A5F, false);
            t.setPadding(dp(10), dp(4), dp(10), dp(4));
            t.setOnClickListener(v -> { sel[0] = sel[0] == k ? -1 : k; ((Runnable) v.getTag()).run(); });
            chipViews.add(t);
            LinearLayout.LayoutParams cl = new LinearLayout.LayoutParams(WRAP, WRAP);
            cl.rightMargin = dp(6);
            chips.addView(t, cl);
        }
        for (TextView t : chipViews) t.setTag(refresh);
        cs.addView(chips);
        LinearLayout.LayoutParams csl = new LinearLayout.LayoutParams(MATCH, WRAP);
        csl.topMargin = dp(8); csl.bottomMargin = dp(8);
        panel.addView(cs, csl);

        gv.setNumColumns(Math.max(4, (pw - dp(36)) / dp(86)));
        gv.setVerticalSpacing(dp(14));
        gv.setSelector(new ColorDrawable(0));
        gv.setVerticalScrollBarEnabled(false);
        gv.setAdapter(ad);
        panel.addView(gv, new LinearLayout.LayoutParams(MATCH, 0, 1f));
        et.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int x, int y, int z) {}
            public void onTextChanged(CharSequence s, int x, int y, int z) {}
            public void afterTextChanged(Editable e) { refresh.run(); }
        });
        refresh.run();
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(pw, ph, Gravity.CENTER);
        lp.topMargin = mbH() / 2;
        sc.addView(panel, lp);
        panel.setPivotX(pw / 2f);
        panel.setPivotY(ph);
        panel.setScaleX(.9f); panel.setScaleY(.9f); panel.setAlpha(0);
        panel.animate().scaleX(1).scaleY(1).alpha(1).setDuration(240).setInterpolator(new DecelerateInterpolator(1.6f)).start();
    }

    void appPopup(final App ap, View v) {
        ArrayList<String> L = new ArrayList<>();
        ArrayList<Runnable> R = new ArrayList<>();
        L.add("#" + ap.label); R.add(null);
        L.add("Open"); R.add(() -> launch(ap));
        L.add("-"); R.add(null);
        L.add("Keep in Dock"); R.add(() -> pinApp(ap, true));
        L.add("Add to Desktop"); R.add(() -> pinApp(ap, false));
        if (ap.builtin == null && ap.cn != null) {
            final String pkg = ap.cn.getPackageName();
            L.add("-"); R.add(null);
            L.add("App Info"); R.add(() -> startExternal(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + pkg))));
            L.add("Uninstall…"); R.add(() -> startExternal(new Intent(Intent.ACTION_DELETE, Uri.parse("package:" + pkg))));
        }
        showPopup(v, L.toArray(new String[0]), R.toArray(new Runnable[0]));
    }

    // ===== Spotlight (Tahoe style) =====
    View spHead(String s) {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        TextView t = tv(s, 12, 0xFF6E6E73, true);
        t.setPadding(dp(8), dp(10), 0, dp(4));
        l.addView(t);
        View d = new View(this);
        d.setBackgroundColor(Theme.dark ? 0x33FFFFFF : 0x26000000);
        l.addView(d, new LinearLayout.LayoutParams(MATCH, 1));
        return l;
    }

    View spRow(Drawable ic, View icView, String title, String sub, boolean hl, boolean arrow, final Runnable r, int fg) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(8), dp(6), dp(10), dp(6));
        if (hl) row.setBackground(rr(Theme.dark ? 0x44FFFFFF : 0x33000000, dp(10)));
        if (icView == null) { ImageView iv = new ImageView(this); iv.setImageDrawable(ic); icView = iv; }
        row.addView(icView, new LinearLayout.LayoutParams(dp(32), dp(32)));
        LinearLayout tx = new LinearLayout(this);
        tx.setOrientation(LinearLayout.VERTICAL);
        tx.setPadding(dp(12), 0, 0, 0);
        TextView t = tv(title, 16, fg, false);
        t.setSingleLine(true);
        t.setEllipsize(android.text.TextUtils.TruncateAt.END);
        tx.addView(t);
        if (sub != null) { TextView s = tv(sub, 12, 0xFF6E6E73, false); s.setSingleLine(true); tx.addView(s); }
        row.addView(tx, new LinearLayout.LayoutParams(0, WRAP, 1f));
        if (arrow) row.addView(tv("↗", 14, 0xFF6E6E73, false));
        row.setOnClickListener(v -> r.run());
        return row;
    }

    void showSpotlight() {
        dismissOverlays();
        ensureApps();
        final FrameLayout sc = scrim();
        sc.setBackgroundColor(0x1A000000);
        final LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setClickable(true);
        box.setBackground(new GlassDrawable(box, dp(26), dp(1), Theme.dark ? 0xE02A2A2D : 0xE6DCDCE0));
        final int fg = Theme.dark ? 0xFFF2F2F7 : 0xFF1D1D1F;
        LinearLayout sr = new LinearLayout(this);
        sr.setGravity(Gravity.CENTER_VERTICAL);
        sr.setPadding(dp(20), dp(12), dp(20), dp(12));
        sr.addView(new IconView(this, IconView.SEARCH, 0xFF6E6E73), new LinearLayout.LayoutParams(dp(26), dp(26)));
        final EditText et = new EditText(this);
        et.setBackground(null);
        et.setSingleLine(true);
        et.setTextSize(26);
        et.setTextColor(fg);
        et.setHint("Spotlight Search");
        et.setHintTextColor(0xFF8E8E93);
        et.setHighlightColor(0xFF9DB9E0);
        et.setImeOptions(EditorInfo.IME_ACTION_GO);
        et.setPadding(dp(12), 0, 0, 0);
        sr.addView(et, new LinearLayout.LayoutParams(0, WRAP, 1f));
        box.addView(sr);
        final View div = new View(this);
        div.setBackgroundColor(Theme.dark ? 0x33FFFFFF : 0x26000000);
        div.setVisibility(View.GONE);
        box.addView(div, new LinearLayout.LayoutParams(MATCH, 1));
        final LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(dp(8), dp(4), dp(8), dp(8));
        MaxScroll ms = new MaxScroll(this, (int) (sh * 0.5f));
        ms.addView(list);
        box.addView(ms);
        final long[] seq = {0};
        et.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int x, int y, int z) {}
            public void onTextChanged(CharSequence s, int x, int y, int z) {}
            public void afterTextChanged(Editable e) {
                final String q = e.toString().trim();
                final long my = ++seq[0];
                list.removeAllViews();
                div.setVisibility(q.isEmpty() ? View.GONE : View.VISIBLE);
                if (q.isEmpty()) return;
                String ql = q.toLowerCase(Locale.getDefault());
                int n = 0;
                for (final App ap : apps) {
                    if (!ap.label.toLowerCase(Locale.getDefault()).contains(ql)) continue;
                    list.addView(spRow(ap.icon, null, ap.label, null, n == 0, false, () -> { dismissOverlays(); launch(ap); }, fg));
                    if (++n >= 3) break;
                }
                list.addView(spHead("Suggestions"));
                list.addView(spRow(getDrawable(res("mac_safari")), null, q, null, false, true, () -> { dismissOverlays(); SafariApp.open(MainActivity.this, q); }, fg));
                if (!hasStorage()) return;
                new Thread(() -> {
                    final List<Object[]> out = new ArrayList<>();
                    try {
                        android.os.Bundle b = new android.os.Bundle();
                        b.putString(android.content.ContentResolver.QUERY_ARG_SQL_SELECTION, "_display_name LIKE ?");
                        b.putStringArray(android.content.ContentResolver.QUERY_ARG_SQL_SELECTION_ARGS, new String[]{"%" + q + "%"});
                        b.putString(android.content.ContentResolver.QUERY_ARG_SQL_SORT_ORDER, "date_modified DESC");
                        b.putInt(android.content.ContentResolver.QUERY_ARG_LIMIT, 4);
                        try (android.database.Cursor c = getContentResolver().query(android.provider.MediaStore.Files.getContentUri("external"),
                                new String[]{"_display_name", "_size", "date_modified", "_data"}, b, null)) {
                            while (c != null && c.moveToNext()) {
                                String path = c.getString(3);
                                if (path == null) continue;
                                File f = new File(path);
                                if (f.isFile()) out.add(new Object[]{f, c.getLong(1), c.getLong(2) * 1000L});
                            }
                        }
                    } catch (Exception ex) { }
                    runOnUiThread(() -> {
                        if (seq[0] != my || out.isEmpty()) return;
                        list.addView(spHead("Files"));
                        for (Object[] o : out) {
                            final File f = (File) o[0];
                            String nm = f.getName();
                            int d = nm.lastIndexOf('.');
                            String ext = d >= 0 ? nm.substring(d + 1) : "";
                            long sz = (Long) o[1];
                            String size = sz > 1048576 ? (sz / 1048576) + " MB" : (sz / 1024) + " KB";
                            String sub = size + " · " + (ext.isEmpty() ? "File" : ext.toUpperCase(Locale.ROOT)) + " · Modified "
                                    + new SimpleDateFormat("MMM d, yyyy", Locale.US).format(new Date((Long) o[2]));
                            list.addView(spRow(null, new Ui.FileIcon(MainActivity.this, ext), nm, sub, false, false, () -> { dismissOverlays(); openFile(f); }, fg));
                        }
                    });
                }).start();
            }
        });
        et.setOnEditorActionListener((v, id, ev) -> {
            if (list.getChildCount() > 0) list.getChildAt(0).performClick();
            return true;
        });
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(Math.min(dp(640), sw - dp(24)), WRAP, Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        lp.topMargin = Math.max(mbH() + dp(12), (int) (sh * .12f));
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
        MaxScroll csv = new MaxScroll(this, sh - mbH() - dp(16));
        csv.setOverScrollMode(View.OVER_SCROLL_NEVER);
        csv.setClipChildren(false);
        csv.setClipToPadding(false);
        csv.setClickable(true);
        csv.addView(p, new FrameLayout.LayoutParams(W, y + 2 * (hS + g) + dp(34)));
        FrameLayout.LayoutParams pl = new FrameLayout.LayoutParams(W, WRAP, Gravity.TOP | Gravity.RIGHT);
        pl.topMargin = mbH() + dp(4); pl.rightMargin = dp(10);
        p.setClickable(true);
        sc.addView(csv, pl);
        csv.setPivotX(W); csv.setPivotY(0); csv.setScaleX(.9f); csv.setScaleY(.9f); csv.setAlpha(0);
        csv.animate().scaleX(1).scaleY(1).alpha(1).setDuration(220).setInterpolator(new DecelerateInterpolator(1.5f)).start();
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

    // ===== windows =====
    void openWindow(MacWindow w, int wdp, int hdp) {
        int ww = Math.min(dp(wdp), sw - dp(40)), hh = Math.min(dp(hdp), sh - mbH() - dp(84));
        winLayer.addView(w, new FrameLayout.LayoutParams(ww, hh, Gravity.TOP | Gravity.LEFT));
        float x = (sw - ww) / 2f + (cascade % 5) * dp(22) - dp(44), y = mbH() + dp(10) + (cascade % 5) * dp(16);
        cascade++;
        w.setX(Math.max(0, x));
        w.setY(y);
        w.playOpen(ww, hh);
        wins.add(w);
        focusWindow(w);
    }

    void afterSetup() {
        if (creditShown) return;
        creditShown = true;
        MacAlert.show(this, screen, "Feng iOS on Droid", "This app was created by Feng-Dev / Feng iOS on Droid.",
                new String[]{"OK"}, null, true);
    }

    void focusWindow(MacWindow w) {
        w.bringToFront();
        wins.remove(w);
        wins.add(w);
        for (MacWindow o : wins) o.setActive(o == w);
        if (appMenuTv != null) appMenuTv.setText(w.appName);
        updateDock();
    }

    void onWindowClosed(MacWindow w) {
        wins.remove(w);
        h.post(this::updateDock);
        MacWindow top = null;
        for (int i = wins.size() - 1; i >= 0; i--) if (!wins.get(i).minimized) { top = wins.get(i); break; }
        if (top != null) focusWindow(top);
        else if (appMenuTv != null) appMenuTv.setText("Finder");
    }

    void onWindowMinimized(MacWindow w) { onWindowClosed2(w); }
    void onWindowClosed2(MacWindow w) {
        h.post(this::updateDock);
        MacWindow top = null;
        for (int i = wins.size() - 1; i >= 0; i--) if (!wins.get(i).minimized) { top = wins.get(i); break; }
        if (top != null) focusWindow(top);
        else if (appMenuTv != null) appMenuTv.setText("Finder");
    }

    MacWindow findWindow(String app) {
        for (MacWindow w : wins) if (w.appName.equals(app)) return w;
        return null;
    }

    void openFinder() {
        MacWindow w = findWindow("Finder");
        if (w != null) { w.restore(); focusWindow(w); } else FinderApp.open(this);
    }

    // ===== popup / dialogs =====
    interface TextCb { void ok(String s); }

    void showPopup(View anchor, String[] labels, Runnable[] acts) {
        int[] loc = new int[2];
        anchor.getLocationInWindow(loc);
        float y = loc[1] + anchor.getHeight() / 2f;
        if (loc[1] > sh * 0.7f) y = loc[1] - (labels.length * dp(30) + dp(14));
        showPopup(loc[0] + anchor.getWidth() / 2f - dp(40), y, labels, acts);
    }

    void showPopup(float x, float y, String[] labels, final Runnable[] acts) {
        dismissOverlays();
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(6), dp(6), dp(6), dp(6));
        box.setClickable(true);
        box.setBackground(new GlassDrawable(box, dp(12), dp(0.8f), Theme.dark ? 0xD92A2A2D : 0xCCF4F4F6));
        final int fg = Theme.dark ? 0xFFF2F2F7 : 0xFF1D1D1F;
        for (int i = 0; i < labels.length; i++) {
            final int k = i;
            String lb = labels[i];
            if (lb.equals("-")) {
                View d = new View(this);
                d.setBackgroundColor(Theme.dark ? 0x33FFFFFF : 0x26000000);
                LinearLayout.LayoutParams dl = new LinearLayout.LayoutParams(MATCH, 1);
                dl.setMargins(dp(8), dp(4), dp(8), dp(4));
                box.addView(d, dl);
                continue;
            }
            if (lb.startsWith("#")) {
                TextView hh = tv(lb.substring(1), 11.5f, 0xFF8E8E93, true);
                hh.setPadding(dp(12), dp(5), dp(30), dp(3));
                box.addView(hh);
                continue;
            }
            final TextView t = tv(lb, 13.5f, fg, false);
            t.setPadding(dp(12), dp(6), dp(30), dp(6));
            t.setOnTouchListener((v, e) -> {
                int ac = e.getActionMasked();
                if (ac == MotionEvent.ACTION_DOWN) { t.setBackground(rr(Theme.accent, dp(6))); t.setTextColor(Color.WHITE); }
                else if (ac == MotionEvent.ACTION_UP || ac == MotionEvent.ACTION_CANCEL) { t.setBackground(null); t.setTextColor(fg); }
                return false;
            });
            t.setOnClickListener(v -> { dismissOverlays(); if (acts[k] != null) acts[k].run(); });
            box.addView(t);
        }
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(WRAP, WRAP);
        lp.leftMargin = (int) Math.max(dp(6), Math.min(x, sw - dp(200)));
        lp.topMargin = (int) Math.max(mbH(), Math.min(y, sh - labels.length * dp(30) - dp(20)));
        FrameLayout sc = scrim();
        sc.addView(box, lp);
        box.setPivotX(0); box.setPivotY(0); box.setScaleX(.94f); box.setScaleY(.94f); box.setAlpha(0);
        box.animate().scaleX(1).scaleY(1).alpha(1).setDuration(130).start();
    }

    void prompt(String title, String init, final TextCb cb) {
        final EditText et = new EditText(this);
        et.setText(init);
        et.setSingleLine(true);
        et.setSelectAllOnFocus(true);
        new AlertDialog.Builder(this).setTitle(title).setView(et)
                .setPositiveButton("OK", (d, w) -> { String s = et.getText().toString().trim(); if (!s.isEmpty()) cb.ok(s); })
                .setNegativeButton("Batal", null).show();
    }

    void confirm(String msg, final Runnable yes) {
        new AlertDialog.Builder(this).setMessage(msg)
                .setPositiveButton("Hapus", (d, w) -> yes.run()).setNegativeButton("Batal", null).show();
    }

    // ===== storage / files / external =====
    boolean hasStorage() {
        if (Build.VERSION.SDK_INT >= 30) return Environment.isExternalStorageManager();
        return checkSelfPermission(android.Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
    }

    void requestStorage() {
        if (Build.VERSION.SDK_INT >= 30) {
            try { startActivity(new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:" + getPackageName()))); }
            catch (Exception e) { startActivity(new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)); }
        } else requestPermissions(new String[]{android.Manifest.permission.READ_EXTERNAL_STORAGE, android.Manifest.permission.WRITE_EXTERNAL_STORAGE}, 5);
    }

    void openFile(File f) {
        try {
            String n = f.getName();
            int d = n.lastIndexOf('.');
            String mime = d >= 0 ? MimeTypeMap.getSingleton().getMimeTypeFromExtension(n.substring(d + 1).toLowerCase(Locale.ROOT)) : null;
            Intent i = new Intent(Intent.ACTION_VIEW);
            i.setDataAndType(FileProv.uri(f), mime == null ? "*/*" : mime);
            i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startExternal(i);
        } catch (Exception e) { Toast.makeText(this, "Tidak ada aplikasi untuk membuka file ini", Toast.LENGTH_SHORT).show(); }
    }

    void startExternal(Intent i) {
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            if (Theme.floatApps && Build.VERSION.SDK_INT >= 24) {
                ActivityOptions o = ActivityOptions.makeBasic();
                int w = Math.min(sw - dp(80), dp(560)), h = Math.min(sh - mbH() - dp(90), dp(320));
                int l = (sw - w) / 2, t = mbH() + dp(10);
                o.setLaunchBounds(new Rect(l, t, l + w, t + h));
                startActivity(i, o.toBundle());
            } else startActivity(i);
        } catch (Exception e) { Toast.makeText(this, "Aplikasi tidak ditemukan", Toast.LENGTH_SHORT).show(); }
    }

    void styleIcon(ImageView iv) { styleIcon(iv, Theme.iconStyle); }

    void styleIcon(ImageView iv, String style) {
        if (style.equals("Dark")) iv.setColorFilter(new PorterDuffColorFilter(0x99000000, PorterDuff.Mode.SRC_ATOP));
        else if (style.equals("Clear")) {
            ColorMatrix cm = new ColorMatrix();
            cm.setSaturation(0f);
            iv.setColorFilter(new ColorMatrixColorFilter(cm));
            iv.setAlpha(.85f);
        } else if (style.equals("Tinted")) iv.setColorFilter(new PorterDuffColorFilter((Theme.accent & 0x00FFFFFF) | 0x88000000, PorterDuff.Mode.SRC_ATOP));
    }

    void restartWith(String what) {
        prefs.edit().putString("reopen", what).apply();
        recreate();
    }

    void pickWallpaper() {
        Intent i = new Intent(Intent.ACTION_GET_CONTENT);
        i.setType("image/*");
        startActivityForResult(i, 77);
    }

    void resetWallpaper() {
        new File(getFilesDir(), "wall.jpg").delete();
        restartWith("Settings:Wallpaper");
    }

    // ===== widgets =====
    void initWidgets() {
        awm = AppWidgetManager.getInstance(this);
        awh = new AppWidgetHost(this, 1024);
        widgetLayer = new FrameLayout(this);
        root.addView(widgetLayer, new FrameLayout.LayoutParams(MATCH, MATCH));
        for (String e : prefs.getString("widgets", "").split(",")) {
            if (e.isEmpty()) continue;
            try {
                String[] p = e.split(":");
                addBox(Integer.parseInt(p[0]), Integer.parseInt(p[1]), Integer.parseInt(p[2]), Integer.parseInt(p[3]), Integer.parseInt(p[4]));
            } catch (Exception ex) { }
        }
    }

    void addBox(int id, int x, int y, int w, int h) {
        AppWidgetProviderInfo inf = awm.getAppWidgetInfo(id);
        if (inf == null) return;
        AppWidgetHostView hv = awh.createView(this, id, inf);
        hv.setAppWidget(id, inf);
        WidgetBox b = new WidgetBox(this, hv, id);
        widgetLayer.addView(b, new FrameLayout.LayoutParams(w, h, Gravity.TOP | Gravity.LEFT));
        b.setX(x);
        b.setY(y);
    }

    void saveWidgets() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < widgetLayer.getChildCount(); i++) {
            View c = widgetLayer.getChildAt(i);
            if (c instanceof WidgetBox)
                sb.append(((WidgetBox) c).appWidgetId).append(':').append((int) c.getX()).append(':').append((int) c.getY())
                        .append(':').append(c.getWidth()).append(':').append(c.getHeight()).append(',');
        }
        prefs.edit().putString("widgets", sb.toString()).apply();
    }

    void setEditingWidget(WidgetBox b) { if (editingBox != null && editingBox != b) editingBox.exitEdit(); editingBox = b; }
    void exitWidgetEdit() { if (editingBox != null) { editingBox.exitEdit(); editingBox = null; saveWidgets(); } }

    void removeWidget(WidgetBox b) {
        widgetLayer.removeView(b);
        try { awh.deleteAppWidgetId(b.appWidgetId); } catch (Exception e) { }
        editingBox = null;
        saveWidgets();
    }

    void clearWidgets() {
        for (int i = widgetLayer.getChildCount() - 1; i >= 0; i--) {
            View c = widgetLayer.getChildAt(i);
            if (c instanceof WidgetBox) { try { awh.deleteAppWidgetId(((WidgetBox) c).appWidgetId); } catch (Exception e) { } }
        }
        widgetLayer.removeAllViews();
        saveWidgets();
    }

    void showWidgetPicker() {
        dismissOverlays();
        final PackageManager pm = getPackageManager();
        List<AppWidgetProviderInfo> ps = new ArrayList<>(awm.getInstalledProviders());
        Collections.sort(ps, (x, y) -> x.loadLabel(pm).compareToIgnoreCase(y.loadLabel(pm)));
        FrameLayout sc = scrim();
        sc.setBackgroundColor(0x33000000);
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setClickable(true);
        box.setBackground(rr(Theme.bg(), dp(22)));
        TextView ti = tv("Widgets", 16, Theme.fg(), true);
        ti.setPadding(dp(18), dp(14), dp(18), dp(8));
        box.addView(ti);
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        for (final AppWidgetProviderInfo p : ps) {
            LinearLayout r = new LinearLayout(this);
            r.setGravity(Gravity.CENTER_VERTICAL);
            r.setPadding(dp(18), dp(7), dp(18), dp(7));
            ImageView iv = new ImageView(this);
            try { iv.setImageDrawable(p.loadIcon(this, 0)); } catch (Exception e) { }
            r.addView(iv, new LinearLayout.LayoutParams(dp(34), dp(34)));
            TextView t = tv(p.loadLabel(pm), 13.5f, Theme.fg(), false);
            t.setPadding(dp(12), 0, 0, 0);
            r.addView(t);
            r.setOnClickListener(v -> { dismissOverlays(); addWidget(p); });
            list.addView(r);
        }
        ScrollView sv = new ScrollView(this);
        sv.addView(list);
        box.addView(sv, new LinearLayout.LayoutParams(MATCH, 0, 1f));
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(Math.min(dp(420), sw - dp(40)), Math.min(dp(300), sh - mbH() - dp(40)), Gravity.CENTER);
        sc.addView(box, lp);
        box.setScaleX(.95f); box.setScaleY(.95f); box.setAlpha(0);
        box.animate().scaleX(1).scaleY(1).alpha(1).setDuration(180).start();
    }

    void addWidget(AppWidgetProviderInfo info) {
        pendingId = awh.allocateAppWidgetId();
        if (awm.bindAppWidgetIdIfAllowed(pendingId, info.provider)) afterBind(pendingId);
        else {
            Intent i = new Intent(AppWidgetManager.ACTION_APPWIDGET_BIND);
            i.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, pendingId);
            i.putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, info.provider);
            startActivityForResult(i, 78);
        }
    }

    void afterBind(int id) {
        AppWidgetProviderInfo inf = awm.getAppWidgetInfo(id);
        if (inf != null && inf.configure != null) awh.startAppWidgetConfigureActivityForResult(this, id, 0, 79, null);
        else placeWidget(id);
    }

    void placeWidget(int id) {
        AppWidgetProviderInfo inf = awm.getAppWidgetInfo(id);
        if (inf == null) return;
        int w = Math.max(inf.minWidth, dp(150)), h = Math.max(inf.minHeight, dp(110));
        int n = widgetLayer.getChildCount() % 4;
        addBox(id, dp(190) + n * dp(16), mbH() + dp(14) + n * dp(16), w, h);
        saveWidgets();
    }

    // ===== Stage 3 helpers =====
    int catOf(PackageManager pm, String pkg) {
        try {
            switch (pm.getApplicationInfo(pkg, 0).category) {
                case android.content.pm.ApplicationInfo.CATEGORY_SOCIAL: return 1;
                case android.content.pm.ApplicationInfo.CATEGORY_PRODUCTIVITY: return 2;
                case android.content.pm.ApplicationInfo.CATEGORY_GAME: case android.content.pm.ApplicationInfo.CATEGORY_AUDIO:
                case android.content.pm.ApplicationInfo.CATEGORY_VIDEO: case android.content.pm.ApplicationInfo.CATEGORY_IMAGE: return 3;
                case android.content.pm.ApplicationInfo.CATEGORY_NEWS: case android.content.pm.ApplicationInfo.CATEGORY_MAPS: return 4;
            }
        } catch (Exception e) { }
        return 0;
    }

    void openOrFocus(String app, Runnable create) {
        MacWindow w = findWindow(app);
        if (w != null) { w.restore(); focusWindow(w); } else create.run();
    }

    void openBuiltin(String n) {
        switch (n) {
            case "Finder": openFinder(); break;
            case "Safari": openOrFocus("Safari", () -> SafariApp.open(this, null)); break;
            case "Photos": openOrFocus("Photos", () -> PhotosApp.open(this)); break;
            case "Terminal": openOrFocus("Terminal", () -> TerminalApp.open(this)); break;
            case "Calculator": openOrFocus("Calculator", () -> CalcApp.open(this)); break;
            case "Photo Booth": openOrFocus("Photo Booth", () -> CameraApp.open(this)); break;
            case "News": openOrFocus("News", () -> NewsApp.open(this)); break;
            case "Maps": openOrFocus("Maps", () -> MapsApp.open(this)); break;
            case "Notes": openOrFocus("Notes", () -> NotesApp.open(this)); break;
            case "Calendar": openOrFocus("Calendar", () -> CalendarApp.open(this)); break;
            case "Contacts": openOrFocus("Contacts", () -> ContactsApp.open(this)); break;
            case "Reminders": openOrFocus("Reminders", () -> RemindersApp.open(this)); break;
            default: SettingsApp.open(this, null);
        }
    }

    MacWindow topWindow() {
        for (int i = wins.size() - 1; i >= 0; i--) if (!wins.get(i).minimized) return wins.get(i);
        return null;
    }

    void menuAction(String it) {
        MacWindow top = topWindow();
        switch (it) {
            case "Close Window": if (top != null) top.close(); break;
            case "Minimize": if (top != null) top.minimize(); break;
            case "Zoom": if (top != null) top.toggleZoom(); break;
            case "Lock Screen": case "Sleep": case "Log Out": lock.lock(true); break;
            case "New Finder Window": FinderApp.open(this); break;
            case "New Window": SafariApp.open(this, null); break;
            case "New Tab": case "Close Tab": case "Add to Dock…": case "Open Location…": {
                MacWindow w = findWindow("Safari");
                if (w != null && w.getTag() instanceof SafariApp) ((SafariApp) w.getTag()).menuAction(it);
                break;
            }
            default: break;
        }
    }

    // ----- dock / desktop items -----
    void addToList(String key, Store.Item it) {
        List<Store.Item> l = Store.load(prefs, key, key.equals("dock_items") ? Store.defaults() : new ArrayList<Store.Item>());
        for (Store.Item x : l) if (x.same(it)) return;
        l.add(it);
        Store.save(prefs, key, l);
    }

    void removeFromList(String key, Store.Item it) {
        List<Store.Item> l = Store.load(prefs, key, key.equals("dock_items") ? Store.defaults() : new ArrayList<Store.Item>());
        for (int i = 0; i < l.size(); i++) if (l.get(i).same(it)) { l.remove(i); break; }
        Store.save(prefs, key, l);
    }

    void pinApp(App ap, boolean dock) {
        Store.Item it = ap.builtin != null ? new Store.Item("b", ap.builtin, "") : new Store.Item("a", ap.label, ap.cn.flattenToString());
        addToList(dock ? "dock_items" : "desk_items", it);
        if (dock) buildDock(); else buildDesktopIcons();
        Toast.makeText(this, dock ? "Ditambahkan ke Dock" : "Ditambahkan ke Desktop", Toast.LENGTH_SHORT).show();
    }

    void addWebApp(String name, final String url, boolean toDock, boolean toHome) {
        Store.Item it = new Store.Item("w", name, url);
        if (toDock) addToList("dock_items", it);
        if (toHome) addToList("desk_items", it);
        buildDock();
        buildDesktopIcons();
        Store.fetchFavicon(this, url, () -> { buildDock(); buildDesktopIcons(); });
        Toast.makeText(this, "\"" + name + "\" ditambahkan", Toast.LENGTH_SHORT).show();
    }

    void openItem(Store.Item it, View v) {
        if (it.type.equals("b")) {
            for (String[] d : DOCK) if (d[0].equals(it.name)) { openDock(d, v); return; }
        } else if (it.type.equals("a")) {
            bounce(v);
            try {
                Intent i = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setComponent(ComponentName.unflattenFromString(it.extra));
                i.addFlags(Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
                startExternal(i);
            } catch (Exception e) { Toast.makeText(this, "Aplikasi tidak ditemukan", Toast.LENGTH_SHORT).show(); }
        } else if (it.type.equals("w")) {
            bounce(v);
            MacWindow w = findWindow(it.name);
            if (w != null) { w.restore(); focusWindow(w); } else SafariApp.openWebApp(this, it.name, it.extra);
        }
    }

    void dockPopup(final Store.Item it, View v) {
        ArrayList<String> L = new ArrayList<>();
        ArrayList<Runnable> R = new ArrayList<>();
        L.add("#" + it.name); R.add(null);
        L.add("Open"); R.add(() -> openItem(it, null));
        final MacWindow w = it.type.equals("a") ? null : findWindow(it.name);
        if (w != null) {
            L.add("Show All Windows"); R.add(() -> { w.restore(); focusWindow(w); });
            L.add("Hide"); R.add(() -> w.minimize());
            L.add("Quit"); R.add(() -> w.close());
        }
        L.add("-"); R.add(null);
        L.add("Remove from Dock"); R.add(() -> { removeFromList("dock_items", it); buildDock(); });
        if (it.type.equals("b")) { L.add("Choose App…"); R.add(() -> showLaunchpad(it.name)); }
        if (it.type.equals("w")) { L.add("Add to Desktop"); R.add(() -> { addToList("desk_items", it); buildDesktopIcons(); }); }
        if (it.type.equals("a")) {
            final String pkg = ComponentName.unflattenFromString(it.extra).getPackageName();
            L.add("-"); R.add(null);
            L.add("App Info"); R.add(() -> startExternal(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + pkg))));
            L.add("Uninstall…"); R.add(() -> startExternal(new Intent(Intent.ACTION_DELETE, Uri.parse("package:" + pkg))));
        }
        showPopup(v, L.toArray(new String[0]), R.toArray(new Runnable[0]));
    }

    void buildDesktopIcons() {
        if (iconLayer == null) return;
        iconLayer.removeAllViews();
        List<Store.Item> l = Store.load(prefs, "desk_items", new ArrayList<Store.Item>());
        int cell = dp(84);
        int rowsN = Math.max(1, (sh - mbH() - dp(130)) / cell);
        for (int i = 0; i < l.size(); i++) {
            final Store.Item it = l.get(i);
            LinearLayout c = new LinearLayout(this);
            c.setOrientation(LinearLayout.VERTICAL);
            c.setGravity(Gravity.CENTER_HORIZONTAL);
            ImageView iv = new ImageView(this);
            iv.setImageDrawable(Store.icon(this, it));
            c.addView(iv, new LinearLayout.LayoutParams(dp(50), dp(50)));
            TextView t = tv(it.name, 11, Color.WHITE, false);
            t.setGravity(Gravity.CENTER);
            t.setMaxLines(2);
            t.setEllipsize(android.text.TextUtils.TruncateAt.END);
            t.setShadowLayer(dp(3), 0, 1, 0xAA000000);
            c.addView(t, new LinearLayout.LayoutParams(MATCH, WRAP));
            c.setOnClickListener(v -> openItem(it, v));
            c.setOnLongClickListener(v -> {
                showPopup(v, new String[]{"#" + it.name, "Open", "-", "Remove from Desktop"}, new Runnable[]{null, () -> openItem(it, null), null,
                    () -> { removeFromList("desk_items", it); buildDesktopIcons(); }});
                return true;
            });
            iconLayer.addView(c, new FrameLayout.LayoutParams(dp(78), WRAP, Gravity.TOP | Gravity.LEFT));
            c.setX(sw - dp(88) - (i / rowsN) * cell);
            c.setY(mbH() + dp(16) + (i % rowsN) * cell);
        }
    }

    // ===== Notification Center =====
    void showNotificationCenter() {
        dismissOverlays();
        FrameLayout sc = scrim();
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setClickable(true);
        fillNc(col);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(Math.min(dp(330), sw / 2), WRAP, Gravity.TOP | Gravity.RIGHT);
        lp.topMargin = mbH() + dp(6);
        lp.rightMargin = dp(8);
        MaxScroll nsv = new MaxScroll(this, sh - mbH() - dp(14));
        nsv.setOverScrollMode(View.OVER_SCROLL_NEVER);
        nsv.setClickable(true);
        nsv.addView(col, new FrameLayout.LayoutParams(MATCH, WRAP));
        sc.addView(nsv, lp);
        nsv.setAlpha(0);
        nsv.setTranslationX(dp(30));
        nsv.animate().alpha(1).translationX(0).setDuration(200).start();
    }

    View ncCard(Drawable icon, String title, String text, final Runnable click, final Runnable dismiss) {
        LinearLayout c = new LinearLayout(this);
        c.setGravity(Gravity.CENTER_VERTICAL);
        c.setPadding(dp(12), dp(10), dp(10), dp(10));
        c.setBackground(new GlassDrawable(c, dp(18), dp(0.8f), Theme.dark ? 0xD92A2A2D : 0xCCF4F4F6));
        if (icon != null) {
            ImageView iv = new ImageView(this);
            iv.setImageDrawable(icon);
            c.addView(iv, new LinearLayout.LayoutParams(dp(30), dp(30)));
        }
        LinearLayout tx = new LinearLayout(this);
        tx.setOrientation(LinearLayout.VERTICAL);
        tx.setPadding(icon != null ? dp(10) : 0, 0, 0, 0);
        int fg = Theme.dark ? 0xFFF2F2F7 : 0xFF1D1D1F;
        TextView t = tv(title, 12.5f, fg, true);
        t.setSingleLine(true);
        tx.addView(t);
        if (text != null && !text.isEmpty()) { TextView s = tv(text, 12, 0xFF6E6E73, false); s.setMaxLines(2); s.setEllipsize(android.text.TextUtils.TruncateAt.END); tx.addView(s); }
        c.addView(tx, new LinearLayout.LayoutParams(0, WRAP, 1f));
        if (dismiss != null) {
            TextView x = tv("×", 15, 0xFF8E8E93, true);
            x.setPadding(dp(8), 0, dp(4), 0);
            x.setOnClickListener(v -> dismiss.run());
            c.addView(x);
        }
        c.setOnClickListener(v -> click.run());
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(MATCH, WRAP);
        lp.bottomMargin = dp(8);
        c.setLayoutParams(lp);
        return c;
    }

    void fillNc(final LinearLayout col) {
        col.removeAllViews();
        LinearLayout hd = new LinearLayout(this);
        hd.setGravity(Gravity.CENTER_VERTICAL);
        hd.setPadding(dp(6), dp(2), dp(2), dp(8));
        TextView t = tv("Notification Center", 14, Color.WHITE, true);
        t.setShadowLayer(dp(2), 0, 0, 0x66000000);
        hd.addView(t, new LinearLayout.LayoutParams(0, WRAP, 1f));
        TextView x = tv("×", 16, Color.WHITE, true);
        x.setGravity(Gravity.CENTER);
        x.setBackground(rr(0x66000000, dp(11)));
        x.setOnClickListener(v -> {
            NotifService n = NotifService.inst;
            if (n != null) { try { n.cancelAllNotifications(); } catch (Exception e) { } }
            h.postDelayed(() -> fillNc(col), 250);
        });
        hd.addView(x, new LinearLayout.LayoutParams(dp(22), dp(22)));
        col.addView(hd);
        final LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        final NotifService ns = NotifService.inst;
        if (ns == null) {
            list.addView(ncCard(null, "Akses notifikasi belum aktif", "Ketuk untuk mengizinkan launcher membaca notifikasi.",
                    () -> { dismissOverlays(); startExternal(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)); }, null));
        } else {
            android.service.notification.StatusBarNotification[] arr;
            try { arr = ns.getActiveNotifications(); } catch (Exception e) { arr = new android.service.notification.StatusBarNotification[0]; }
            List<android.service.notification.StatusBarNotification> ls = new ArrayList<>();
            if (arr != null) for (android.service.notification.StatusBarNotification n : arr)
                if ((n.getNotification().flags & android.app.Notification.FLAG_ONGOING_EVENT) == 0 && !n.getPackageName().equals(getPackageName())) ls.add(n);
            Collections.sort(ls, (p, q) -> Long.compare(q.getPostTime(), p.getPostTime()));
            PackageManager pm = getPackageManager();
            int cnt = 0;
            for (final android.service.notification.StatusBarNotification n : ls) {
                if (cnt++ >= 12) break;
                android.os.Bundle ex = n.getNotification().extras;
                CharSequence ti = ex.getCharSequence(android.app.Notification.EXTRA_TITLE), tx = ex.getCharSequence(android.app.Notification.EXTRA_TEXT);
                String app = n.getPackageName();
                Drawable ic = null;
                try { app = pm.getApplicationLabel(pm.getApplicationInfo(n.getPackageName(), 0)).toString(); ic = pm.getApplicationIcon(n.getPackageName()); } catch (Exception e) { }
                final String key = n.getKey();
                list.addView(ncCard(ic, ti == null ? app : ti.toString(), tx == null ? app : tx.toString(), () -> {
                    dismissOverlays();
                    try { if (n.getNotification().contentIntent != null) n.getNotification().contentIntent.send(); } catch (Exception e) { }
                }, () -> { try { ns.cancelNotification(key); } catch (Exception e) { } h.postDelayed(() -> fillNc(col), 200); }));
            }
            if (ls.isEmpty()) list.addView(ncCard(null, "Tidak ada notifikasi", null, () -> { }, null));
        }
        col.addView(list);
        Calendar cal = Calendar.getInstance();
        LinearLayout dc = new LinearLayout(this);
        dc.setOrientation(LinearLayout.VERTICAL);
        dc.setPadding(dp(16), dp(10), dp(16), dp(10));
        dc.setBackground(new GlassDrawable(dc, dp(18), dp(0.8f), Theme.dark ? 0xD92A2A2D : 0xCCF4F4F6));
        dc.addView(tv(new SimpleDateFormat("EEEE", Locale.getDefault()).format(cal.getTime()).toUpperCase(Locale.getDefault()), 11, 0xFFFF3B30, true));
        dc.addView(tv(String.valueOf(cal.get(Calendar.DAY_OF_MONTH)), 30, Theme.dark ? 0xFFF2F2F7 : 0xFF1D1D1F, true));
        LinearLayout.LayoutParams dl = new LinearLayout.LayoutParams(MATCH, WRAP);
        dl.bottomMargin = dp(8);
        col.addView(dc, dl);
        TextView ew = tv("Edit Widgets", 12, Color.WHITE, false);
        ew.setPadding(dp(14), dp(5), dp(14), dp(5));
        ew.setBackground(new GlassDrawable(ew, dp(14), dp(1), 0x40FFFFFF));
        ew.setOnClickListener(v -> { dismissOverlays(); showWidgetPicker(); });
        LinearLayout.LayoutParams el = new LinearLayout.LayoutParams(WRAP, WRAP);
        el.gravity = Gravity.CENTER_HORIZONTAL;
        col.addView(ew, el);
    }

}
