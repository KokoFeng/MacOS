package com.macos.launcher;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.provider.ContactsContract;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Contacts: reads the device address book (needs permission), list on the left, card on the right. */
public class ContactsApp {
    static class C { String id, name; List<String[]> phones = new ArrayList<>(); List<String> mails = new ArrayList<>(); }
    static ContactsApp cur;
    static final int[][] AV = {{0xFFFF9F0A, 0xFFE8574F}, {0xFF64D2FF, 0xFF0A84FF}, {0xFF30D158, 0xFF1FAE5B}, {0xFFBF5AF2, 0xFF8E4EC6}, {0xFFFF6482, 0xFFD6335C}};

    final MainActivity a;
    final List<C> all = new ArrayList<>();
    C sel;
    String query = "";
    FrameLayout holder;
    LinearLayout list, detail;

    public static void open(MainActivity a) { cur = new ContactsApp(a); }

    public static void onPermission(int req, int[] res) {
        if (req == 7 && cur != null && res.length > 0 && res[0] == PackageManager.PERMISSION_GRANTED) cur.start();
    }

    ContactsApp(MainActivity act) {
        a = act;
        TextView add = Ui.sfBtn(a, "plus", 13);
        add.setOnClickListener(v -> {
            Intent i = new Intent(ContactsContract.Intents.Insert.ACTION);
            i.setType(ContactsContract.RawContacts.CONTENT_TYPE);
            a.startExternal(i);
        });
        LinearLayout tb = new LinearLayout(a);
        tb.setGravity(Gravity.CENTER_VERTICAL);
        tb.addView(new View(a), new LinearLayout.LayoutParams(0, 1, 1f));
        tb.addView(add, new LinearLayout.LayoutParams(a.dp(32), a.dp(32)));
        holder = new FrameLayout(a);
        a.openWindow(new MacWindow(a, "Contacts", tb, null, holder), 680, 330);
        start();
    }

    boolean granted() { return a.checkSelfPermission(Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED; }

    void start() {
        holder.removeAllViews();
        if (!granted()) {
            LinearLayout c = new LinearLayout(a);
            c.setOrientation(LinearLayout.VERTICAL);
            c.setGravity(Gravity.CENTER);
            c.addView(Sf.img(a, "person.crop.circle", Theme.sub()), new LinearLayout.LayoutParams(a.dp(48), a.dp(48)));
            TextView t = Ui.tv(a, "Contacts needs access to your address book.", 14, Theme.fg(), false);
            t.setPadding(0, a.dp(10), 0, a.dp(10));
            c.addView(t);
            TextView b = Ui.tv(a, "Allow Access", 14, Color.WHITE, true);
            b.setPadding(a.dp(18), a.dp(8), a.dp(18), a.dp(8));
            b.setBackground(Ui.rr(Theme.accent, a.dp(16)));
            b.setOnClickListener(v -> a.requestPermissions(new String[]{Manifest.permission.READ_CONTACTS}, 7));
            c.addView(b);
            holder.addView(c, new FrameLayout.LayoutParams(-2, -2, Gravity.CENTER));
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
        search.setBackground(Ui.rr(Theme.dark ? 0x2EFFFFFF : 0x12000000, a.dp(10)));
        search.setPadding(a.dp(12), 0, a.dp(12), 0);
        search.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int x, int y, int z) {}
            public void onTextChanged(CharSequence s, int x, int y, int z) {}
            public void afterTextChanged(Editable e) { query = e.toString().toLowerCase(Locale.ROOT).trim(); drawList(); }
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
        body.addView(left, new LinearLayout.LayoutParams(a.dp(220), -1));
        detail = new LinearLayout(a);
        detail.setOrientation(LinearLayout.VERTICAL);
        detail.setGravity(Gravity.CENTER_HORIZONTAL);
        ScrollView ds = new ScrollView(a);
        ds.setVerticalScrollBarEnabled(false);
        ds.addView(detail, new FrameLayout.LayoutParams(-1, -2));
        body.addView(ds, new LinearLayout.LayoutParams(0, -1, 1f));
        holder.addView(body, new FrameLayout.LayoutParams(-1, -1));
        new Thread(() -> {
            final List<C> got = query();
            a.runOnUiThread(() -> { all.clear(); all.addAll(got); if (!all.isEmpty()) sel = all.get(0); drawList(); drawDetail(); });
        }).start();
    }

    List<C> query() {
        Map<String, C> m = new LinkedHashMap<>();
        try (Cursor c = a.getContentResolver().query(ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                new String[]{ContactsContract.CommonDataKinds.Phone.CONTACT_ID, ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                        ContactsContract.CommonDataKinds.Phone.NUMBER, ContactsContract.CommonDataKinds.Phone.TYPE}, null, null,
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " COLLATE NOCASE ASC")) {
            while (c != null && c.moveToNext()) {
                String id = c.getString(0);
                C x = m.get(id);
                if (x == null) { x = new C(); x.id = id; x.name = c.getString(1) == null ? "?" : c.getString(1); m.put(id, x); }
                String num = c.getString(2);
                boolean dup = false;
                for (String[] p : x.phones) if (p[1].replaceAll("\\D", "").equals(num == null ? "" : num.replaceAll("\\D", ""))) dup = true;
                if (!dup && num != null) {
                    int ty = c.getInt(3);
                    x.phones.add(new String[]{ty == 1 ? "home" : ty == 3 ? "work" : "mobile", num});
                }
            }
        } catch (Exception e) { }
        try (Cursor c = a.getContentResolver().query(ContactsContract.CommonDataKinds.Email.CONTENT_URI,
                new String[]{ContactsContract.CommonDataKinds.Email.CONTACT_ID, ContactsContract.CommonDataKinds.Email.ADDRESS}, null, null, null)) {
            while (c != null && c.moveToNext()) {
                C x = m.get(c.getString(0));
                if (x != null && !x.mails.contains(c.getString(1))) x.mails.add(c.getString(1));
            }
        } catch (Exception e) { }
        return new ArrayList<>(m.values());
    }

    static String initials(String n) {
        String[] p = n.trim().split("\\s+");
        String s = p.length > 0 && !p[0].isEmpty() ? p[0].substring(0, 1) : "?";
        if (p.length > 1 && !p[p.length - 1].isEmpty()) s += p[p.length - 1].substring(0, 1);
        return s.toUpperCase();
    }

    View avatar(C c, int sizeDp) {
        int[] col = AV[Math.abs(c.name.hashCode()) % AV.length];
        TextView t = Ui.tv(a, initials(c.name), sizeDp * .38f, Color.WHITE, true);
        t.setGravity(Gravity.CENTER);
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, col);
        g.setShape(GradientDrawable.OVAL);
        t.setBackground(g);
        return t;
    }

    void drawList() {
        list.removeAllViews();
        String last = "";
        for (final C c : all) {
            if (!query.isEmpty() && !c.name.toLowerCase(Locale.ROOT).contains(query)) continue;
            String l = c.name.isEmpty() ? "#" : c.name.substring(0, 1).toUpperCase();
            if (!Character.isLetter(l.charAt(0))) l = "#";
            if (!l.equals(last)) {
                last = l;
                TextView h = Ui.tv(a, l, 12, Theme.sub(), true);
                h.setPadding(a.dp(14), a.dp(8), 0, a.dp(2));
                list.addView(h);
            }
            LinearLayout r = new LinearLayout(a);
            r.setGravity(Gravity.CENTER_VERTICAL);
            r.setPadding(a.dp(8), a.dp(5), a.dp(8), a.dp(5));
            if (c == sel) r.setBackground(Ui.rr(Theme.accent, a.dp(10)));
            r.addView(avatar(c, 28), new LinearLayout.LayoutParams(a.dp(28), a.dp(28)));
            TextView n = Ui.tv(a, c.name, 13, c == sel ? Color.WHITE : Theme.fg(), false);
            n.setSingleLine(true);
            n.setPadding(a.dp(8), 0, 0, 0);
            r.addView(n);
            r.setOnClickListener(v -> { sel = c; drawList(); drawDetail(); });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
            lp.setMargins(a.dp(6), 0, a.dp(6), 0);
            list.addView(r, lp);
        }
    }

    void drawDetail() {
        detail.removeAllViews();
        detail.setPadding(a.dp(24), a.dp(20), a.dp(24), a.dp(16));
        if (sel == null) { detail.addView(Ui.tv(a, "No Contacts", 15, Theme.sub(), false)); return; }
        detail.addView(avatar(sel, 84), new LinearLayout.LayoutParams(a.dp(84), a.dp(84)));
        TextView n = Ui.tv(a, sel.name, 22, Theme.fg(), true);
        n.setTypeface(Emoji.font(true));
        n.setPadding(0, a.dp(10), 0, a.dp(10));
        detail.addView(n);
        LinearLayout act = new LinearLayout(a);
        String[] sym = {"message.fill", "phone.fill", "envelope.fill"};
        for (int i = 0; i < 3; i++) {
            final int k = i;
            TextView b = Ui.sfBtn(a, sym[i], 15);
            b.setOnClickListener(v -> {
                if (k == 0 && !sel.phones.isEmpty()) a.startExternal(new Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:" + sel.phones.get(0)[1])));
                else if (k == 1 && !sel.phones.isEmpty()) a.startExternal(new Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + sel.phones.get(0)[1])));
                else if (k == 2 && !sel.mails.isEmpty()) a.startExternal(new Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:" + sel.mails.get(0))));
            });
            LinearLayout.LayoutParams bl = new LinearLayout.LayoutParams(a.dp(38), a.dp(38));
            bl.setMargins(a.dp(6), 0, a.dp(6), 0);
            act.addView(b, bl);
        }
        detail.addView(act);
        LinearLayout card = new LinearLayout(a);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(Ui.rr(Theme.dark ? 0x1FFFFFFF : 0x0F000000, a.dp(14)));
        card.setPadding(a.dp(14), a.dp(6), a.dp(14), a.dp(6));
        for (final String[] p : sel.phones) card.addView(infoRow(p[0], p[1], () -> a.startExternal(new Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + p[1])))));
        for (final String m : sel.mails) card.addView(infoRow("email", m, () -> a.startExternal(new Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:" + m)))));
        LinearLayout.LayoutParams cl = new LinearLayout.LayoutParams(a.dp(320), -2);
        cl.topMargin = a.dp(16);
        detail.addView(card, cl);
    }

    View infoRow(String label, String value, final Runnable tap) {
        LinearLayout r = new LinearLayout(a);
        r.setOrientation(LinearLayout.VERTICAL);
        r.setPadding(0, a.dp(7), 0, a.dp(7));
        r.addView(Ui.tv(a, label, 11, Theme.sub(), false));
        r.addView(Ui.tv(a, value, 14, Theme.accent, false));
        r.setOnClickListener(v -> tap.run());
        return r;
    }
}
