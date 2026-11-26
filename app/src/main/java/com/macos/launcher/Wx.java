package com.macos.launcher;

import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.*;
import android.location.Geocoder;
import android.location.Location;
import android.location.LocationManager;
import android.view.View;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONObject;

/** Weather data (Open-Meteo, free, no key) shared by the Weather app and the desktop widgets. */
public class Wx {
    public static class City {
        public String name;
        public double lat, lon;
        public boolean here;
        public City(String n, double la, double lo, boolean h) { name = n; lat = la; lon = lo; here = h; }
    }

    public interface Cb { void on(JSONObject forecast, JSONObject air); }
    public interface Found { void on(List<City> res); }

    static boolean fahr(MainActivity a) { return a.prefs.getBoolean("wx_f", false); }

    static String http(String u) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(u).openConnection();
        c.setConnectTimeout(8000);
        c.setReadTimeout(10000);
        c.setRequestProperty("User-Agent", "FengLauncher/1.0");
        BufferedReader r = new BufferedReader(new InputStreamReader(c.getInputStream(), "UTF-8"));
        StringBuilder sb = new StringBuilder();
        String l;
        while ((l = r.readLine()) != null) sb.append(l);
        r.close();
        return sb.toString();
    }

    // ----- saved cities -----
    public static List<City> cities(MainActivity a) {
        List<City> l = new ArrayList<>();
        l.add(new City("My Location", a.prefs.getFloat("wx_hlat", 0f), a.prefs.getFloat("wx_hlon", 0f), true));
        l.get(0).name = a.prefs.getString("wx_hname", "My Location");
        for (String e : a.prefs.getString("wx_cities", "").split(";")) {
            if (e.isEmpty()) continue;
            String[] p = e.split("\\|");
            try { l.add(new City(p[0], Double.parseDouble(p[1]), Double.parseDouble(p[2]), false)); } catch (Exception ex) { }
        }
        return l;
    }

    public static void addCity(MainActivity a, City c) {
        String s = a.prefs.getString("wx_cities", "");
        s += c.name.replace("|", " ").replace(";", " ") + "|" + c.lat + "|" + c.lon + ";";
        a.prefs.edit().putString("wx_cities", s).apply();
    }

    public static void removeCity(MainActivity a, int index) {
        List<City> l = cities(a);
        StringBuilder sb = new StringBuilder();
        for (int i = 1; i < l.size(); i++) {
            if (i == index) continue;
            City c = l.get(i);
            sb.append(c.name.replace("|", " ").replace(";", " ")).append('|').append(c.lat).append('|').append(c.lon).append(';');
        }
        a.prefs.edit().putString("wx_cities", sb.toString()).apply();
    }

    // ----- location of this device -----
    public static void locate(final MainActivity a, final Runnable done) {
        new Thread(() -> {
            double lat = 0, lon = 0;
            String name = null;
            boolean ok = false;
            try {
                if (a.checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                    LocationManager lm = (LocationManager) a.getSystemService(Context.LOCATION_SERVICE);
                    Location best = null;
                    for (String pr : lm.getProviders(true)) {
                        Location x = lm.getLastKnownLocation(pr);
                        if (x != null && (best == null || x.getTime() > best.getTime())) best = x;
                    }
                    if (best != null) {
                        lat = best.getLatitude(); lon = best.getLongitude(); ok = true;
                        try {
                            List<android.location.Address> ad = new Geocoder(a).getFromLocation(lat, lon, 1);
                            if (ad != null && !ad.isEmpty()) {
                                android.location.Address d = ad.get(0);
                                name = d.getLocality() != null ? d.getLocality() : d.getSubAdminArea() != null ? d.getSubAdminArea() : d.getAdminArea();
                            }
                        } catch (Exception e) { }
                    }
                }
            } catch (Exception e) { }
            if (!ok || name == null) {
                try {
                    JSONObject j = new JSONObject(http("https://get.geojs.io/v1/ip/geo.json"));
                    if (!ok) { lat = Double.parseDouble(j.getString("latitude")); lon = Double.parseDouble(j.getString("longitude")); ok = true; }
                    if (name == null) name = j.optString("city", "");
                    if (name.isEmpty()) name = j.optString("region", "My Location");
                } catch (Exception e) { }
            }
            if (ok) {
                a.prefs.edit().putFloat("wx_hlat", (float) lat).putFloat("wx_hlon", (float) lon).putString("wx_hname", name == null ? "My Location" : name).apply();
            }
            if (done != null) a.runOnUiThread(done);
        }).start();
    }

    // ----- forecast -----
    static String key(MainActivity a, City c) { return "wx_" + Math.round(c.lat * 100) + "_" + Math.round(c.lon * 100) + (fahr(a) ? "f" : "c"); }

    /** Cached forecast (any age) or null. */
    public static JSONObject cached(MainActivity a, City c) {
        try {
            String s = a.prefs.getString(key(a, c), null);
            if (s != null) return new JSONObject(s);
        } catch (Exception e) { }
        return null;
    }

    public static JSONObject cachedAir(MainActivity a, City c) {
        try {
            String s = a.prefs.getString(key(a, c) + "a", null);
            if (s != null) return new JSONObject(s);
        } catch (Exception e) { }
        return null;
    }

    public static void fetch(final MainActivity a, final City c, final boolean force, final Cb cb) {
        final JSONObject old = cached(a, c);
        final long age = old == null ? Long.MAX_VALUE : System.currentTimeMillis() - a.prefs.getLong(key(a, c) + "t", 0);
        if (!force && old != null && age < 600000) { cb.on(old, cachedAir(a, c)); return; }
        if (c.lat == 0 && c.lon == 0) { cb.on(old, null); return; }
        new Thread(() -> {
            JSONObject f = null, aq = null;
            try {
                String u = "https://api.open-meteo.com/v1/forecast?latitude=" + c.lat + "&longitude=" + c.lon
                        + "&current=temperature_2m,apparent_temperature,relative_humidity_2m,weather_code,wind_speed_10m,wind_direction_10m,wind_gusts_10m,pressure_msl,is_day,visibility,uv_index"
                        + "&hourly=temperature_2m,weather_code,precipitation_probability,is_day"
                        + "&daily=weather_code,temperature_2m_max,temperature_2m_min,sunrise,sunset,precipitation_probability_max,precipitation_sum,uv_index_max"
                        + "&timezone=auto&forecast_days=10"
                        + (fahr(a) ? "&temperature_unit=fahrenheit&wind_speed_unit=mph" : "&wind_speed_unit=kmh");
                f = new JSONObject(http(u));
                a.prefs.edit().putString(key(a, c), f.toString()).putLong(key(a, c) + "t", System.currentTimeMillis()).apply();
            } catch (Exception e) { f = old; }
            try {
                aq = new JSONObject(http("https://air-quality-api.open-meteo.com/v1/air-quality?latitude=" + c.lat + "&longitude=" + c.lon + "&current=us_aqi"));
                a.prefs.edit().putString(key(a, c) + "a", aq.toString()).apply();
            } catch (Exception e) { aq = cachedAir(a, c); }
            final JSONObject ff = f, aa = aq;
            a.runOnUiThread(() -> cb.on(ff, aa));
        }).start();
    }

    public static void search(final String q, final Found cb, final MainActivity a) {
        new Thread(() -> {
            final List<City> out = new ArrayList<>();
            try {
                JSONObject j = new JSONObject(http("https://geocoding-api.open-meteo.com/v1/search?count=8&name=" + URLEncoder.encode(q, "UTF-8")));
                JSONArray r = j.optJSONArray("results");
                if (r != null) for (int i = 0; i < r.length(); i++) {
                    JSONObject o = r.getJSONObject(i);
                    String nm = o.getString("name");
                    String ad = o.optString("admin1", "");
                    String co = o.optString("country", "");
                    out.add(new City(nm + (ad.isEmpty() ? "" : ", " + ad) + (co.isEmpty() ? "" : ", " + co), o.getDouble("latitude"), o.getDouble("longitude"), false));
                }
            } catch (Exception e) { }
            a.runOnUiThread(() -> cb.on(out));
        }).start();
    }

    // ----- helpers -----
    public static String temp(double t) { return Math.round(t) + "\u00b0"; }

    /** kind: 0 clear, 1 partly, 2 cloudy, 3 fog, 4 rain, 5 snow, 6 storm */
    public static int kind(int c) {
        if (c == 0) return 0;
        if (c == 1 || c == 2) return 1;
        if (c == 3) return 2;
        if (c == 45 || c == 48) return 3;
        if ((c >= 51 && c <= 67) || (c >= 80 && c <= 82)) return 4;
        if ((c >= 71 && c <= 77) || c == 85 || c == 86) return 5;
        if (c >= 95) return 6;
        return 2;
    }

    public static String cond(int c, boolean day) {
        switch (c) {
            case 0: return day ? "Sunny" : "Clear";
            case 1: return day ? "Mostly Sunny" : "Mostly Clear";
            case 2: return "Partly Cloudy";
            case 3: return "Cloudy";
            case 45: case 48: return "Fog";
            case 51: case 53: case 55: case 56: case 57: return "Drizzle";
            case 61: case 63: case 65: case 66: case 67: return "Rain";
            case 71: case 73: case 75: case 77: case 85: case 86: return "Snow";
            case 80: case 81: case 82: return "Showers";
            case 95: case 96: case 99: return "Thunderstorms";
            default: return "Cloudy";
        }
    }

    /** Sky gradient like the Apple Weather app (top, bottom). */
    public static int[] sky(int code, boolean day) {
        int k = kind(code);
        if (!day) {
            if (k == 0 || k == 1) return new int[]{0xFF0B1A3E, 0xFF2A3F73};
            return new int[]{0xFF1F2733, 0xFF3A4556};
        }
        switch (k) {
            case 0: return new int[]{0xFF1D6FD1, 0xFF6FB3F0};
            case 1: return new int[]{0xFF3A7BC8, 0xFF8DB6DE};
            case 2: return new int[]{0xFF6F8294, 0xFF9AA8B5};
            case 3: return new int[]{0xFF8B9AA6, 0xFFB4BEC6};
            case 4: return new int[]{0xFF4C5E70, 0xFF7C8B99};
            case 5: return new int[]{0xFF7D93A8, 0xFFB7C6D3};
            default: return new int[]{0xFF39434F, 0xFF626F7C};
        }
    }

    public static String compass(double deg) {
        String[] d = {"N", "NNE", "NE", "ENE", "E", "ESE", "SE", "SSE", "S", "SSW", "SW", "WSW", "W", "WNW", "NW", "NNW"};
        return d[(int) Math.round(((deg % 360) + 360) % 360 / 22.5) % 16];
    }

    // ===== icon =====
    public static class Icon extends View {
        int kind;
        boolean day;
        public int special;   // 1 = sunset, 2 = sunrise
        final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);

        public Icon(Context c, int code, boolean day) { super(c); set(code, day); }

        public void set(int code, boolean d) { kind = kind(code); day = d; invalidate(); }

        void sun(Canvas cv, float cx, float cy, float r) {
            p.setStyle(Paint.Style.FILL);
            p.setShader(new RadialGradient(cx, cy, r * 1.9f, 0x55FFD60A, 0x00FFD60A, Shader.TileMode.CLAMP));
            cv.drawCircle(cx, cy, r * 1.9f, p);
            p.setShader(new LinearGradient(cx, cy - r, cx, cy + r, 0xFFFFE066, 0xFFFFB300, Shader.TileMode.CLAMP));
            cv.drawCircle(cx, cy, r, p);
            p.setShader(null);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeCap(Paint.Cap.ROUND);
            p.setStrokeWidth(r * .2f);
            p.setColor(0xFFFFC107);
            for (int i = 0; i < 8; i++) {
                double an = Math.PI / 4 * i;
                cv.drawLine((float) (cx + Math.cos(an) * r * 1.3f), (float) (cy + Math.sin(an) * r * 1.3f),
                        (float) (cx + Math.cos(an) * r * 1.6f), (float) (cy + Math.sin(an) * r * 1.6f), p);
            }
            p.setStyle(Paint.Style.FILL);
        }

        void moon(Canvas cv, float cx, float cy, float r) {
            Path a = new Path(), b = new Path();
            a.addCircle(cx, cy, r, Path.Direction.CW);
            b.addCircle(cx + r * .55f, cy - r * .3f, r * .9f, Path.Direction.CW);
            a.op(b, Path.Op.DIFFERENCE);
            p.setStyle(Paint.Style.FILL);
            p.setShader(new LinearGradient(cx - r, cy - r, cx + r, cy + r, 0xFFF4F1D8, 0xFFD9D4A8, Shader.TileMode.CLAMP));
            cv.drawPath(a, p);
            p.setShader(null);
        }

        void cloud(Canvas cv, float x, float y, float w, int top, int bot) {
            float h = w * .62f;
            Path c = new Path();
            c.addCircle(x + w * .30f, y + h * .60f, h * .36f, Path.Direction.CW);
            c.addCircle(x + w * .52f, y + h * .40f, h * .46f, Path.Direction.CW);
            c.addCircle(x + w * .76f, y + h * .62f, h * .34f, Path.Direction.CW);
            c.addRoundRect(new RectF(x + w * .18f, y + h * .58f, x + w * .90f, y + h * .96f), h * .19f, h * .19f, Path.Direction.CW);
            p.setStyle(Paint.Style.FILL);
            p.setShader(new LinearGradient(0, y, 0, y + h, top, bot, Shader.TileMode.CLAMP));
            cv.drawPath(c, p);
            p.setShader(null);
        }

        @Override protected void onDraw(Canvas cv) {
            float w = getWidth(), h = getHeight(), s = Math.min(w, h);
            float ox = (w - s) / 2f, oy = (h - s) / 2f;
            cv.save();
            cv.translate(ox, oy);
            int white = 0xFFFFFFFF, grey = 0xFFE3E8EE, dark = 0xFF9AA4AF;
            if (special != 0) {
                float cx = s * .5f, hy = s * .62f, r = s * .2f;
                cv.save();
                cv.clipRect(0, 0, s, hy);
                sun(cv, cx, hy, r);
                cv.restore();
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeCap(Paint.Cap.ROUND);
                p.setStrokeWidth(s * .05f);
                p.setColor(0xCCFFFFFF);
                cv.drawLine(s * .12f, hy, s * .88f, hy, p);
                float ay = special == 1 ? s * .18f : s * .06f;
                float ty = special == 1 ? s * .06f : s * .18f;
                cv.drawLine(cx, ay, cx, ty + (special == 1 ? s * .14f : -s * .0f), p);
                p.setStyle(Paint.Style.FILL);
                cv.restore();
                return;
            }
            switch (kind) {
                case 0:
                    if (day) sun(cv, s * .5f, s * .5f, s * .24f); else moon(cv, s * .5f, s * .5f, s * .3f);
                    break;
                case 1:
                    if (day) sun(cv, s * .36f, s * .36f, s * .19f); else moon(cv, s * .34f, s * .36f, s * .22f);
                    cloud(cv, s * .12f, s * .34f, s * .8f, white, grey);
                    break;
                case 2:
                    cloud(cv, s * .08f, s * .24f, s * .84f, day ? white : 0xFFC9D0D8, day ? grey : 0xFF9AA4AF);
                    break;
                case 3:
                    cloud(cv, s * .1f, s * .14f, s * .8f, white, grey);
                    p.setColor(0xFFDDE3EA);
                    p.setStrokeCap(Paint.Cap.ROUND);
                    p.setStrokeWidth(s * .06f);
                    for (int i = 0; i < 3; i++) cv.drawLine(s * (.2f + i * .05f), s * (.66f + i * .1f), s * (.8f - i * .05f), s * (.66f + i * .1f), p);
                    break;
                case 4:
                    cloud(cv, s * .08f, s * .12f, s * .84f, 0xFFD3DAE2, dark);
                    p.setColor(0xFF4FC3F7);
                    p.setStrokeCap(Paint.Cap.ROUND);
                    p.setStrokeWidth(s * .06f);
                    for (int i = 0; i < 3; i++) cv.drawLine(s * (.3f + i * .17f), s * .66f, s * (.25f + i * .17f), s * .84f, p);
                    break;
                case 5:
                    cloud(cv, s * .08f, s * .12f, s * .84f, 0xFFE3E8EE, 0xFFAEB8C2);
                    p.setColor(0xFFFFFFFF);
                    p.setStyle(Paint.Style.FILL);
                    for (int i = 0; i < 3; i++) cv.drawCircle(s * (.3f + i * .2f), s * (i == 1 ? .85f : .74f), s * .045f, p);
                    break;
                default:
                    cloud(cv, s * .08f, s * .1f, s * .84f, 0xFFA9B2BC, 0xFF6F7B88);
                    Path b = new Path();
                    b.moveTo(s * .54f, s * .54f); b.lineTo(s * .38f, s * .78f); b.lineTo(s * .5f, s * .78f);
                    b.lineTo(s * .44f, s * .94f); b.lineTo(s * .64f, s * .68f); b.lineTo(s * .52f, s * .68f); b.close();
                    p.setStyle(Paint.Style.FILL);
                    p.setColor(0xFFFFD60A);
                    cv.drawPath(b, p);
            }
            cv.restore();
        }
    }
}
