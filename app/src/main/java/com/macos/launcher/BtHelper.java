package com.macos.launcher;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Build;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/** Lists paired and nearby Bluetooth devices; pairs and connects from inside the launcher. */
public class BtHelper {
    static boolean has(MainActivity a) {
        if (Build.VERSION.SDK_INT >= 31)
            return a.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
                    && a.checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED
                    && WifiHelper.hasLoc(a);
        return WifiHelper.hasLoc(a);
    }

    static String[] perms() {
        if (Build.VERSION.SDK_INT >= 31)
            return new String[]{Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.ACCESS_FINE_LOCATION};
        return new String[]{Manifest.permission.ACCESS_FINE_LOCATION};
    }

    @SuppressWarnings("MissingPermission")
    static String name(BluetoothDevice d) {
        try { String n = d.getName(); return n == null || n.isEmpty() ? d.getAddress() : n; } catch (Exception e) { return "Bluetooth"; }
    }

    /** Best effort: asks the A2DP / headset / HID profiles to connect (hidden API; falls back to system settings). */
    @SuppressWarnings({"MissingPermission", "JavaReflectionMemberAccess", "DiscouragedPrivateApi"})
    static void connect(final MainActivity a, final BluetoothDevice d, final BluetoothAdapter ad) {
        final int[] profiles = {BluetoothProfile.A2DP, BluetoothProfile.HEADSET};
        final boolean[] ok = {false};
        final int[] left = {profiles.length};
        for (int p : profiles) {
            try {
                ad.getProfileProxy(a, new BluetoothProfile.ServiceListener() {
                    @Override public void onServiceConnected(int profile, BluetoothProfile proxy) {
                        try {
                            java.lang.reflect.Method m = proxy.getClass().getMethod("connect", BluetoothDevice.class);
                            m.setAccessible(true);
                            if (Boolean.TRUE.equals(m.invoke(proxy, d))) ok[0] = true;
                        } catch (Throwable t) { }
                        ad.closeProfileProxy(profile, proxy);
                        if (--left[0] == 0) a.runOnUiThread(() -> {
                            if (ok[0]) Toast.makeText(a, L.t("Connecting") + " " + name(d) + "\u2026", Toast.LENGTH_SHORT).show();
                            else { Toast.makeText(a, L.t("Opening Bluetooth settings"), Toast.LENGTH_SHORT).show(); a.startExternal(new Intent(Settings.ACTION_BLUETOOTH_SETTINGS)); }
                        });
                    }
                    @Override public void onServiceDisconnected(int profile) { }
                }, p);
            } catch (Throwable t) { left[0]--; }
        }
    }

    @SuppressWarnings("MissingPermission")
    public static View listView(final MainActivity a, final int fg, final int sub, final int accent, final int rowBg) {
        final LinearLayout root = new LinearLayout(a);
        root.setOrientation(LinearLayout.VERTICAL);
        BluetoothManager bm = (BluetoothManager) a.getSystemService(Context.BLUETOOTH_SERVICE);
        final BluetoothAdapter ad = bm == null ? null : bm.getAdapter();
        final Map<String, BluetoothDevice> found = new LinkedHashMap<>();
        final Runnable[] refresh = new Runnable[1];
        final BroadcastReceiver rc = new BroadcastReceiver() {
            @Override public void onReceive(Context c, Intent i) {
                String act = i.getAction();
                if (BluetoothDevice.ACTION_FOUND.equals(act)) {
                    BluetoothDevice d = i.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                    if (d != null) found.put(d.getAddress(), d);
                }
                refresh[0].run();
            }
        };
        refresh[0] = () -> {
            root.removeAllViews();
            if (ad == null) { root.addView(WifiHelper.msg(a, "This device has no Bluetooth.", sub)); return; }
            if (!has(a)) {
                root.addView(WifiHelper.msg(a, "Android needs permission to find nearby Bluetooth devices.", sub));
                root.addView(WifiHelper.btn(a, "Allow", accent, () -> { a.onPerm = refresh[0]; a.requestPermissions(perms(), 94); }));
                return;
            }
            if (!ad.isEnabled()) {
                root.addView(WifiHelper.msg(a, "Bluetooth is turned off.", sub));
                root.addView(WifiHelper.btn(a, "Turn On Bluetooth", accent, () -> a.startExternal(new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))));
                return;
            }
            Set<BluetoothDevice> bonded = ad.getBondedDevices();
            root.addView(head(a, "My Devices", sub));
            if (bonded == null || bonded.isEmpty()) root.addView(WifiHelper.msg(a, "No paired devices", sub));
            else for (final BluetoothDevice d : bonded) root.addView(item(a, name(d), "Connect", fg, sub, accent, rowBg, () -> connect(a, d, ad)));
            root.addView(head(a, "Nearby Devices", sub));
            int n = 0;
            for (final BluetoothDevice d : new ArrayList<>(found.values())) {
                if (d.getBondState() == BluetoothDevice.BOND_BONDED) continue;
                n++;
                root.addView(item(a, name(d), d.getBondState() == BluetoothDevice.BOND_BONDING ? "Pairing\u2026" : "Pair", fg, sub, accent, rowBg, () -> {
                    try { d.createBond(); } catch (Exception e) { }
                }));
            }
            if (n == 0) root.addView(WifiHelper.msg(a, "Searching\u2026", sub));
        };
        root.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
            @Override public void onViewAttachedToWindow(View v) {
                IntentFilter f = new IntentFilter();
                f.addAction(BluetoothDevice.ACTION_FOUND);
                f.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
                f.addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED);
                f.addAction(BluetoothAdapter.ACTION_STATE_CHANGED);
                try { a.registerReceiver(rc, f); } catch (Exception e) { }
                try { if (ad != null && has(a) && ad.isEnabled() && !ad.isDiscovering()) ad.startDiscovery(); } catch (Exception e) { }
                refresh[0].run();
            }
            @Override public void onViewDetachedFromWindow(View v) {
                try { a.unregisterReceiver(rc); } catch (Exception e) { }
                try { if (ad != null && ad.isDiscovering()) ad.cancelDiscovery(); } catch (Exception e) { }
            }
        });
        return root;
    }

    static TextView head(MainActivity a, String s, int sub) {
        TextView t = Ui.tv(a, s, 11, sub, true);
        t.setPadding(a.dp(4), a.dp(10), 0, a.dp(2));
        return t;
    }

    static View item(MainActivity a, String name, String action, int fg, int sub, int accent, int bg, Runnable r) {
        LinearLayout row = new LinearLayout(a);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(a.dp(12), 0, a.dp(8), 0);
        row.setBackground(Ui.rr(bg, a.dp(10)));
        TextView n = Ui.tv(a, name, 13.5f, fg, false);
        n.setSingleLine(true);
        row.addView(n, new LinearLayout.LayoutParams(0, -2, 1f));
        TextView b = Ui.tv(a, action, 12, 0xFFFFFFFF, true);
        b.setGravity(Gravity.CENTER);
        b.setBackground(Ui.rr(accent, a.dp(12)));
        b.setPadding(a.dp(12), 0, a.dp(12), 0);
        b.setOnClickListener(v -> r.run());
        row.addView(b, new LinearLayout.LayoutParams(-2, a.dp(26)));
        LinearLayout.LayoutParams l = new LinearLayout.LayoutParams(-1, a.dp(38));
        l.topMargin = a.dp(5);
        row.setLayoutParams(l);
        return row;
    }
}
