package com.macos.launcher;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.location.Location;
import android.location.LocationManager;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONObject;

/** Maps: OpenStreetMap / satellite tiles in a WebView, Nominatim search, recents and pins. */
public class MapsApp {
    static class Place { String name; double lat, lon; }
    static MapsApp cur;

    static final String HTML = "<!doctype html><html><head><meta name=viewport content='width=device-width,initial-scale=1,maximum-scale=1'>"
            + "<link rel=stylesheet href='https://unpkg.com/leaflet@1.9.4/dist/leaflet.css'><style>html,body,#m{margin:0;height:100%;background:#e9e6df}"
            + ".leaflet-control-attribution{font-size:9px;opacity:.7}</style></head><body><div id=m></div>"
            + "<script src='https://unpkg.com/leaflet@1.9.4/dist/leaflet.js'></script><script>"
            + "var map=L.map('m',{zoomControl:false}).setView([-6.2,106.82],11);"
            + "var std=L.tileLayer('https://tile.openstreetmap.org/{z}/{x}/{y}.png',{maxZoom:19,attribution:'\u00a9 OpenStreetMap'});"
            + "var sat=L.tileLayer('https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/{z}/{y}/{x}',{maxZoom:19,attribution:'Esri'});"
            + "std.addTo(map);var mk=null,me=null;"
            + "function setSat(b){if(b){map.removeLayer(std);sat.addTo(map);}else{map.removeLayer(sat);std.addTo(map);}}"
            + "function go(la,lo,z,n){map.flyTo([la,lo],z||15,{duration:.8});if(mk)map.removeLayer(mk);mk=L.marker([la,lo]).addTo(map);if(n)mk.bindPopup(n).openPopup();}"
            + "function zoom(d){map.setZoom(map.getZoom()+d);}"
            + "function locate(la,lo){if(me)map.removeLayer(me);me=L.circleMarker([la,lo],{radius:8,color:'#fff',weight:3,fillColor:'#0A84FF',fillOpacity:1}).addTo(map);map.flyTo([la,lo],16,{duration:.8});}"
            + "</script></body></html>";

    final MainActivity a;
    final List<Place> recents = new ArrayList<>(), pins = new ArrayList<>();
    String lib = "Recents";
    Place shown;
    boolean sat;
    WebView web;
    LinearLayout listBox;
    TextView libHeader;

    public static void open(MainActivity a) { cur = new MapsApp(a); }

    public static void onPermission(int req, int[] res) {
        if (req == 8 && cur != null && res.length > 0 && res[0] == PackageManager.PERMISSION_GRANTED) cur.locate();
    }

    @SuppressLint("SetJavaScriptEnabled")
    MapsApp(MainActivity act) {
        a = act;
        loadPlaces("maps_recent", recents);
        loadPlaces("maps_pins", pins);
        LinearLayout body = new LinearLayout(a);
        LinearLayout left = new LinearLayout(a);
        left.setOrientation(LinearLayout.VERTICAL);
        left.setBackgroundColor(Theme.side());
        left.setPadding(a.dp(10), a.dp(8), a.dp(10), a.dp(8));
        final EditText search = new EditText(a);
        search.setHint("Search Maps");
        search.setSingleLine(true);
        search.setTextSize(13);
        search.setTextColor(Theme.fg());
        search.setHintTextColor(Theme.sub());
        search.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        search.setInputType(InputType.TYPE_CLASS_TEXT);
        search.setBackground(Ui.rr(Theme.dark ? 0x2EFFFFFF : 0x12000000, a.dp(10)));
        search.setPadding(a.dp(12), 0, a.dp(12), 0);
        search.setOnEditorActionListener((tv, id, ev) -> { find(search.getText().toString().trim()); return true; });
        left.addView(search, new LinearLayout.LayoutParams(-1, a.dp(32)));
        TextView lh = Ui.tv(a, "Library", 11.5f, Theme.sub(), true);
        lh.setPadding(a.dp(2), a.dp(12), 0, a.dp(4));
        left.addView(lh);
        String[][] libs = {{"Pinned", "pin.fill"}, {"Places", "mappin"}, {"Guides", "map"}, {"Routes", "location"}};
        for (final String[] l : libs) {
            LinearLayout r = new LinearLayout(a);
            r.setGravity(Gravity.CENTER_VERTICAL);
            r.setPadding(a.dp(4), a.dp(5), 0, a.dp(5));
            r.addView(Sf.img(a, l[1], Theme.accent), new LinearLayout.LayoutParams(a.dp(16), a.dp(16)));
            TextView t = Ui.tv(a, l[0], 13, Theme.fg(), false);
            t.setPadding(a.dp(10), 0, 0, 0);
            r.addView(t);
            r.setOnClickListener(v -> { lib = l[0]; drawList(); });
            left.addView(r);
        }
        libHeader = Ui.tv(a, "Recents", 11.5f, Theme.sub(), true);
        libHeader.setPadding(a.dp(2), a.dp(12), 0, a.dp(4));
        left.addView(libHeader);
        listBox = new LinearLayout(a);
        listBox.setOrientation(LinearLayout.VERTICAL);
        ScrollView sv = new ScrollView(a);
        sv.setVerticalScrollBarEnabled(false);
        sv.addView(listBox);
        left.addView(sv, new LinearLayout.LayoutParams(-1, 0, 1f));
        body.addView(left, new LinearLayout.LayoutParams(a.dp(190), -1));

        FrameLayout mapBox = new FrameLayout(a);
        web = new WebView(a);
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        web.setWebViewClient(new WebViewClient());
        web.loadDataWithBaseURL("https://maps.local/", HTML, "text/html", "utf-8", null);
        mapBox.addView(web, new FrameLayout.LayoutParams(-1, -1));
        LinearLayout ctl = new LinearLayout(a);
        ctl.setOrientation(LinearLayout.VERTICAL);
        ctl.addView(ctlBtn("square.3.layers.3d", () -> { sat = !sat; js("setSat(" + sat + ")"); }));
        ctl.addView(ctlBtn("location.fill", this::locate));
        ctl.addView(ctlBtn("plus", () -> js("zoom(1)")));
        ctl.addView(ctlBtn("minus", () -> js("zoom(-1)")));
        ctl.addView(ctlBtn("pin.fill", this::pinCurrent));
        FrameLayout.LayoutParams cp = new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.RIGHT);
        cp.topMargin = a.dp(10); cp.rightMargin = a.dp(10);
        mapBox.addView(ctl, cp);
        body.addView(mapBox, new LinearLayout.LayoutParams(0, -1, 1f));
        a.openWindow(new MacWindow(a, "Maps", null, "Maps", body), 720, 340);
        drawList();
    }

    View ctlBtn(String sym, final Runnable r) {
        TextView b = Ui.sfBtn(a, sym, 14);
        b.setBackground(Ui.rr(Theme.dark ? 0xE62C2C2E : 0xE6FFFFFF, a.dp(17)));
        b.setElevation(a.dp(3));
        b.setOnClickListener(v -> r.run());
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(a.dp(34), a.dp(34));
        lp.bottomMargin = a.dp(7);
        b.setLayoutParams(lp);
        return b;
    }

    void js(String s) { web.evaluateJavascript(s, null); }

    // ---------- places ----------
    void loadPlaces(String key, List<Place> out) {
        try {
            JSONArray ar = new JSONArray(a.prefs.getString(key, "[]"));
            for (int i = 0; i < ar.length(); i++) {
                JSONObject o = ar.getJSONObject(i);
                Place p = new Place(); p.name = o.getString("n"); p.lat = o.getDouble("la"); p.lon = o.getDouble("lo"); out.add(p);
            }
        } catch (Exception e) { }
    }

    void savePlaces(String key, List<Place> l) {
        try {
            JSONArray ar = new JSONArray();
            for (Place p : l) ar.put(new JSONObject().put("n", p.name).put("la", p.lat).put("lo", p.lon));
            a.prefs.edit().putString(key, ar.toString()).apply();
        } catch (Exception e) { }
    }

    void drawList() {
        listBox.removeAllViews();
        List<Place> src = lib.equals("Pinned") ? pins : lib.equals("Places") || lib.equals("Recents") ? recents : new ArrayList<Place>();
        libHeader.setText(lib.equals("Places") ? "Places" : lib);
        if (src.isEmpty()) {
            TextView e = Ui.tv(a, lib.equals("Guides") || lib.equals("Routes") ? "Nothing here yet" : "Nothing saved yet", 12, Theme.sub(), false);
            e.setPadding(a.dp(4), a.dp(4), 0, 0);
            listBox.addView(e);
            return;
        }
        for (final Place p : src) {
            LinearLayout r = new LinearLayout(a);
            r.setGravity(Gravity.CENTER_VERTICAL);
            r.setPadding(a.dp(4), a.dp(6), a.dp(4), a.dp(6));
            FrameLayout ic = new FrameLayout(a);
            ic.setBackground(Ui.rr(0xFFFF6482, a.dp(11)));
            ic.addView(Sf.img(a, "mappin", Color.WHITE), new FrameLayout.LayoutParams(a.dp(12), a.dp(12), Gravity.CENTER));
            r.addView(ic, new LinearLayout.LayoutParams(a.dp(22), a.dp(22)));
            TextView t = Ui.tv(a, p.name, 12.5f, Theme.fg(), false);
            t.setSingleLine(true);
            t.setEllipsize(android.text.TextUtils.TruncateAt.END);
            t.setPadding(a.dp(8), 0, 0, 0);
            r.addView(t, new LinearLayout.LayoutParams(0, -2, 1f));
            r.setOnClickListener(v -> show(p, false));
            listBox.addView(r);
        }
    }

    void show(Place p, boolean remember) {
        shown = p;
        js("go(" + p.lat + "," + p.lon + ",15," + JSONObject.quote(p.name) + ")");
        if (remember) {
            for (int i = recents.size() - 1; i >= 0; i--) if (recents.get(i).name.equals(p.name)) recents.remove(i);
            recents.add(0, p);
            while (recents.size() > 12) recents.remove(recents.size() - 1);
            savePlaces("maps_recent", recents);
            drawList();
        }
    }

    void pinCurrent() {
        if (shown == null) { Toast.makeText(a, "Search for a place first", Toast.LENGTH_SHORT).show(); return; }
        for (Place p : pins) if (p.name.equals(shown.name)) return;
        pins.add(0, shown);
        savePlaces("maps_pins", pins);
        Toast.makeText(a, "Pinned", Toast.LENGTH_SHORT).show();
        drawList();
    }

    void find(final String q) {
        if (q.isEmpty()) return;
        new Thread(() -> {
            try {
                URL u = new URL("https://nominatim.openstreetmap.org/search?format=json&limit=1&q=" + URLEncoder.encode(q, "UTF-8"));
                HttpURLConnection c = (HttpURLConnection) u.openConnection();
                c.setRequestProperty("User-Agent", "FengMacLauncher/1.0");
                c.setConnectTimeout(8000); c.setReadTimeout(8000);
                BufferedReader br = new BufferedReader(new InputStreamReader(c.getInputStream(), "UTF-8"));
                StringBuilder sb = new StringBuilder();
                String ln;
                while ((ln = br.readLine()) != null) sb.append(ln);
                JSONArray ar = new JSONArray(sb.toString());
                if (ar.length() == 0) { a.runOnUiThread(() -> Toast.makeText(a, "No results", Toast.LENGTH_SHORT).show()); return; }
                JSONObject o = ar.getJSONObject(0);
                final Place p = new Place();
                String dn = o.optString("display_name", q);
                p.name = dn.contains(",") ? dn.substring(0, dn.indexOf(',')) : dn;
                p.lat = o.getDouble("lat"); p.lon = o.getDouble("lon");
                a.runOnUiThread(() -> show(p, true));
            } catch (Exception e) {
                a.runOnUiThread(() -> Toast.makeText(a, "Search failed. Check your connection.", Toast.LENGTH_SHORT).show());
            }
        }).start();
    }

    @SuppressLint("MissingPermission")
    void locate() {
        if (a.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            a.requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, 8);
            return;
        }
        LocationManager lm = (LocationManager) a.getSystemService(android.content.Context.LOCATION_SERVICE);
        Location best = null;
        for (String p : lm.getProviders(true)) {
            Location l = lm.getLastKnownLocation(p);
            if (l != null && (best == null || l.getAccuracy() < best.getAccuracy())) best = l;
        }
        if (best == null) { Toast.makeText(a, "Location unavailable", Toast.LENGTH_SHORT).show(); return; }
        js("locate(" + best.getLatitude() + "," + best.getLongitude() + ")");
    }
}
