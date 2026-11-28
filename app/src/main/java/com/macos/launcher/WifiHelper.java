package com.macos.launcher;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiManager;
import android.net.wifi.WifiNetworkSpecifier;
import android.os.Build;
import android.provider.Settings;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.View.OnAttachStateChangeListener;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;

/** Shows every nearby Wi-Fi network and lets the user join one from inside the launcher. */
public class WifiHelper {
    static boolean hasLoc(MainActivity a) { return a.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED; }

    static boolean secured(ScanResult sr) {
        String c = sr.capabilities == null ? "" : sr.capabilities;
        return c.contains("WPA") || c.contains("WEP") || c.contains("SAE");
    }

    @SuppressWarnings("deprecation")
    static String current(MainActivity a) {
        try {
            WifiManager wm = (WifiManager) a.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
            if (wm == null || !wm.isWifiEnabled() || !hasLoc(a)) return null;
            String s = wm.getConnectionInfo().getSSID();
            if (s != null && !s.contains("unknown")) return s.replace("\"", "");
        } catch (Exception e) { }
        return null;
    }

    public static void openPanel(MainActivity a) {
        try {
            if (Build.VERSION.SDK_INT >= 29) a.startExternal(new Intent(Settings.Panel.ACTION_WIFI));
            else a.startExternal(new Intent(Settings.ACTION_WIFI_SETTINGS));
        } catch (Exception e) { a.startExternal(new Intent(Settings.ACTION_WIFI_SETTINGS)); }
    }

    /** Joins a network. API 29+ uses the system "join" prompt and binds the launcher to that network. */
    @SuppressWarnings("deprecation")
    public static void connect(final MainActivity a, final ScanResult sr, final String pass, final Runnable done) {
        final String ssid = sr.SSID;
        try {
            if (Build.VERSION.SDK_INT >= 29) {
                WifiNetworkSpecifier.Builder b = new WifiNetworkSpecifier.Builder().setSsid(ssid);
                if (secured(sr)) {
                    if (sr.capabilities.contains("SAE") && !sr.capabilities.contains("PSK")) b.setWpa3Passphrase(pass);
                    else b.setWpa2Passphrase(pass);
                }
                NetworkRequest req = new NetworkRequest.Builder().addTransportType(NetworkCapabilities.TRANSPORT_WIFI).setNetworkSpecifier(b.build()).build();
                final ConnectivityManager cm = (ConnectivityManager) a.getSystemService(Context.CONNECTIVITY_SERVICE);
                cm.requestNetwork(req, new ConnectivityManager.NetworkCallback() {
                    @Override public void onAvailable(Network n) {
                        cm.bindProcessToNetwork(n);
                        a.prefs.edit().putString("wifi_last", ssid).apply();
                        a.runOnUiThread(() -> { Toast.makeText(a, L.t("Connected to") + " " + ssid, Toast.LENGTH_SHORT).show(); if (done != null) done.run(); });
                    }
                    @Override public void onUnavailable() {
                        a.runOnUiThread(() -> Toast.makeText(a, L.t("Could not join the network"), Toast.LENGTH_SHORT).show());
                    }
                }, 30000);
            } else {
                WifiManager wm = (WifiManager) a.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
                WifiConfiguration c = new WifiConfiguration();
                c.SSID = "\"" + ssid + "\"";
                if (secured(sr)) c.preSharedKey = "\"" + pass + "\""; else c.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.NONE);
                int id = wm.addNetwork(c);
                wm.disconnect();
                wm.enableNetwork(id, true);
                wm.reconnect();
                a.prefs.edit().putString("wifi_last", ssid).apply();
                if (done != null) done.run();
            }
        } catch (Exception e) {
            Toast.makeText(a, L.t("Could not join the network"), Toast.LENGTH_SHORT).show();
        }
    }

    /** A scrollable-friendly list of networks. fg/sub = text colors, bg = row background tint. */
    @SuppressWarnings("deprecation")
    public static View listView(final MainActivity a, final int fg, final int sub, final int accent, final int rowBg, final Runnable onJoined) {
        final LinearLayout root = new LinearLayout(a);
        root.setOrientation(LinearLayout.VERTICAL);
        final WifiManager wm = (WifiManager) a.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        final BroadcastReceiver[] rc = new BroadcastReceiver[1];
        final Runnable[] refresh = new Runnable[1];
        refresh[0] = () -> {
            root.removeAllViews();
            if (!hasLoc(a)) {
                root.addView(msg(a, "Android needs location permission to scan for Wi-Fi networks.", sub));
                root.addView(btn(a, "Allow", accent, () -> { a.onPerm = refresh[0]; a.requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, 93); }));
                return;
            }
            if (wm == null || !wm.isWifiEnabled()) {
                root.addView(msg(a, "Wi-Fi is turned off.", sub));
                root.addView(btn(a, "Turn On Wi-Fi", accent, () -> openPanel(a)));
                return;
            }
            String cur = current(a);
            if (cur != null) root.addView(row(a, "\u2713  " + cur, fg, rowBg, null));
            List<ScanResult> res = new ArrayList<>();
            try { res = wm.getScanResults(); } catch (Exception e) { }
            Collections.sort(res, (x, y) -> y.level - x.level);
            HashSet<String> seen = new HashSet<>();
            int n = 0;
            for (final ScanResult sr : res) {
                if (sr.SSID == null || sr.SSID.isEmpty() || !seen.add(sr.SSID) || sr.SSID.equals(cur)) continue;
                n++;
                final boolean sec = secured(sr);
                final LinearLayout box = new LinearLayout(a);
                box.setOrientation(LinearLayout.VERTICAL);
                box.setBackground(Ui.rr(rowBg, a.dp(10)));
                LinearLayout top = new LinearLayout(a);
                top.setGravity(Gravity.CENTER_VERTICAL);
                top.setPadding(a.dp(12), 0, a.dp(12), 0);
                TextView nm = Ui.tv(a, sr.SSID, 13.5f, fg, false);
                nm.setSingleLine(true);
                top.addView(nm, new LinearLayout.LayoutParams(0, -2, 1f));
                if (sec) top.addView(Ui.tv(a, "\uD83D\uDD12", 11, sub, false));
                IconView wi = new IconView(a, IconView.WIFI, fg);
                wi.setAlpha(sr.level > -60 ? 1f : sr.level > -75 ? .65f : .35f);
                LinearLayout.LayoutParams wl = new LinearLayout.LayoutParams(a.dp(20), a.dp(16));
                wl.leftMargin = a.dp(8);
                top.addView(wi, wl);
                box.addView(top, new LinearLayout.LayoutParams(-1, a.dp(38)));
                final LinearLayout pw = new LinearLayout(a);
                pw.setGravity(Gravity.CENTER_VERTICAL);
                pw.setPadding(a.dp(12), 0, a.dp(8), a.dp(8));
                pw.setVisibility(View.GONE);
                final EditText et = new EditText(a);
                et.setHint(L.t("Password"));
                et.setHintTextColor(sub);
                et.setTextColor(fg);
                et.setTextSize(13);
                et.setSingleLine(true);
                et.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
                et.setBackground(Ui.rr(0x26808080, a.dp(8)));
                et.setPadding(a.dp(10), 0, a.dp(10), 0);
                pw.addView(et, new LinearLayout.LayoutParams(0, a.dp(34), 1f));
                TextView join = Ui.tv(a, "Join", 12.5f, 0xFFFFFFFF, true);
                join.setGravity(Gravity.CENTER);
                join.setBackground(Ui.rr(accent, a.dp(12)));
                join.setPadding(a.dp(14), 0, a.dp(14), 0);
                join.setOnClickListener(v -> connect(a, sr, et.getText().toString(), () -> { refresh[0].run(); if (onJoined != null) onJoined.run(); }));
                LinearLayout.LayoutParams jl = new LinearLayout.LayoutParams(-2, a.dp(30));
                jl.leftMargin = a.dp(8);
                pw.addView(join, jl);
                box.addView(pw);
                top.setOnClickListener(v -> {
                    if (!sec) connect(a, sr, "", () -> { refresh[0].run(); if (onJoined != null) onJoined.run(); });
                    else pw.setVisibility(pw.getVisibility() == View.VISIBLE ? View.GONE : View.VISIBLE);
                });
                LinearLayout.LayoutParams bl = new LinearLayout.LayoutParams(-1, -2);
                bl.topMargin = a.dp(5);
                root.addView(box, bl);
                if (n >= 30) break;
            }
            if (n == 0 && cur == null) root.addView(msg(a, "Searching\u2026", sub));
            try { wm.startScan(); } catch (Exception e) { }
        };
        rc[0] = new BroadcastReceiver() {
            @Override public void onReceive(Context c, Intent i) {
                // do not rebuild while the user is typing a password
                View f = root.findFocus();
                if (f == null) refresh[0].run();
            }
        };
        root.addOnAttachStateChangeListener(new OnAttachStateChangeListener() {
            @Override public void onViewAttachedToWindow(View v) {
                try { a.registerReceiver(rc[0], new IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION)); } catch (Exception e) { }
                refresh[0].run();
            }
            @Override public void onViewDetachedFromWindow(View v) { try { a.unregisterReceiver(rc[0]); } catch (Exception e) { } }
        });
        return root;
    }

    static TextView msg(MainActivity a, String s, int c) {
        TextView t = Ui.tv(a, s, 12.5f, c, false);
        t.setPadding(a.dp(4), a.dp(8), a.dp(4), a.dp(8));
        return t;
    }

    static View row(MainActivity a, String s, int fg, int bg, Runnable r) {
        TextView t = Ui.tv(a, s, 13.5f, fg, true);
        t.setGravity(Gravity.CENTER_VERTICAL);
        t.setPadding(a.dp(12), 0, a.dp(12), 0);
        t.setBackground(Ui.rr(bg, a.dp(10)));
        t.setLayoutParams(new LinearLayout.LayoutParams(-1, a.dp(38)));
        if (r != null) t.setOnClickListener(v -> r.run());
        return t;
    }

    static View btn(MainActivity a, String s, int accent, Runnable r) {
        TextView t = Ui.tv(a, s, 12.5f, 0xFFFFFFFF, true);
        t.setGravity(Gravity.CENTER);
        t.setBackground(Ui.rr(accent, a.dp(14)));
        t.setPadding(a.dp(16), 0, a.dp(16), 0);
        t.setOnClickListener(v -> r.run());
        LinearLayout.LayoutParams l = new LinearLayout.LayoutParams(-2, a.dp(30));
        l.topMargin = a.dp(4);
        t.setLayoutParams(l);
        return t;
    }
}
