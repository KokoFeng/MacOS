package com.macos.launcher;

import android.app.DatePickerDialog;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import org.json.JSONArray;
import org.json.JSONObject;

/** Reminders: smart lists (Today / Scheduled / All / Flagged / Completed) + user lists. */
public class RemindersApp {
    static class Item { long id; int list; String text, due = ""; boolean done, flag; }
    static class RList { String name; int color; }

    static final String[] SMART = {"Today", "Scheduled", "All", "Flagged", "Completed"};
    static final int[] SMART_C = {0xFF3D8BFD, 0xFFE8574F, 0xFF4B4B4F, 0xFFF0A05A, 0xFF8A8D94};
    static final String[] SMART_I = {"calendar", "calendar", "tray.fill", "flag.fill", "checkmark"};
    static final int[] LIST_C = {0xFF0A84FF, 0xFFFF375F, 0xFFFF9F0A, 0xFF30D158, 0xFFBF5AF2, 0xFF64D2FF};

    final MainActivity a;
    final List<Item> items = new ArrayList<>();
    final List<RList> lists = new ArrayList<>();
    String sel = "Today";           // smart name or "L:<index>"
    LinearLayout side, main;
    final SimpleDateFormat iso = new SimpleDateFormat("yyyy-MM-dd", Locale.US);

    public static void open(MainActivity a) { new RemindersApp(a); }

    RemindersApp(MainActivity act) {
        a = act;
        load();
        if (lists.isEmpty()) { RList l = new RList(); l.name = "Reminders"; l.color = LIST_C[0]; lists.add(l); save(); }
        TextView add = Ui.sfBtn(a, "plus", 13);
        add.setOnClickListener(v -> addItem());
        LinearLayout tb = new LinearLayout(a);
        tb.setGravity(Gravity.CENTER_VERTICAL);
        tb.addView(new View(a), new LinearLayout.LayoutParams(0, 1, 1f));
        tb.addView(add, new LinearLayout.LayoutParams(a.dp(32), a.dp(32)));

        LinearLayout body = new LinearLayout(a);
        side = new LinearLayout(a);
        side.setOrientation(LinearLayout.VERTICAL);
        side.setPadding(a.dp(10), a.dp(6), a.dp(10), a.dp(10));
        ScrollView sv = new ScrollView(a);
        sv.setBackgroundColor(Theme.side());
        sv.setVerticalScrollBarEnabled(false);
        sv.addView(side);
        body.addView(sv, new LinearLayout.LayoutParams(a.dp(218), -1));
        main = new LinearLayout(a);
        main.setOrientation(LinearLayout.VERTICAL);
        ScrollView ms = new ScrollView(a);
        ms.setVerticalScrollBarEnabled(false);
        ms.addView(main);
        body.addView(ms, new LinearLayout.LayoutParams(0, -1, 1f));
        a.openWindow(new MacWindow(a, "Reminders", tb, null, body), 700, 340);
        refresh();
    }

    // ---------- data ----------
    void load() {
        try {
            JSONObject o = new JSONObject(a.prefs.getString("reminders", "{}"));
            JSONArray ls = o.optJSONArray("lists"), is = o.optJSONArray("items");
            if (ls != null) for (int i = 0; i < ls.length(); i++) {
                JSONObject j = ls.getJSONObject(i);
                RList l = new RList(); l.name = j.getString("n"); l.color = j.getInt("c"); lists.add(l);
            }
            if (is != null) for (int i = 0; i < is.length(); i++) {
                JSONObject j = is.getJSONObject(i);
                Item it = new Item(); it.id = j.getLong("id"); it.list = j.getInt("l"); it.text = j.getString("t");
                it.due = j.optString("due", ""); it.done = j.optBoolean("d"); it.flag = j.optBoolean("f");
                items.add(it);
            }
        } catch (Exception e) { }
    }

    void save() {
        try {
            JSONArray ls = new JSONArray(), is = new JSONArray();
            for (RList l : lists) ls.put(new JSONObject().put("n", l.name).put("c", l.color));
            for (Item i : items) is.put(new JSONObject().put("id", i.id).put("l", i.list).put("t", i.text).put("due", i.due).put("d", i.done).put("f", i.flag));
            a.prefs.edit().putString("reminders", new JSONObject().put("lists", ls).put("items", is).toString()).apply();
        } catch (Exception e) { }
    }

    String today() { return iso.format(new Date()); }

    boolean inSmart(Item i, String s) {
        switch (s) {
            case "Today": return !i.done && !i.due.isEmpty() && i.due.compareTo(today()) <= 0;
            case "Scheduled": return !i.done && !i.due.isEmpty();
            case "All": return !i.done;
            case "Flagged": return !i.done && i.flag;
            default: return i.done;
        }
    }

    int count(String s) { int n = 0; for (Item i : items) if (inSmart(i, s)) n++; return n; }
    int countList(int l) { int n = 0; for (Item i : items) if (i.list == l && !i.done) n++; return n; }

    // ---------- ui ----------
    void refresh() { drawSide(); drawMain(); }

    void drawSide() {
        side.removeAllViews();
        LinearLayout grid = new LinearLayout(a);
        grid.setOrientation(LinearLayout.VERTICAL);
        for (int r = 0; r < 3; r++) {
            LinearLayout row = new LinearLayout(a);
            for (int c = 0; c < 2; c++) {
                int k = r * 2 + c;
                if (k >= SMART.length) { row.addView(new View(a), new LinearLayout.LayoutParams(0, 1, 1f)); continue; }
                row.addView(tile(k), tileLp(c));
            }
            grid.addView(row, new LinearLayout.LayoutParams(-1, a.dp(52)));
        }
        side.addView(grid);
        TextView h = Ui.tv(a, "My Lists", 12, Theme.sub(), true);
        h.setPadding(a.dp(4), a.dp(14), 0, a.dp(4));
        side.addView(h);
        for (int i = 0; i < lists.size(); i++) {
            final int k = i;
            final RList l = lists.get(i);
            LinearLayout r = new LinearLayout(a);
            r.setGravity(Gravity.CENTER_VERTICAL);
            r.setPadding(a.dp(8), a.dp(6), a.dp(8), a.dp(6));
            if (sel.equals("L:" + i)) r.setBackground(Ui.rr(Theme.accent, a.dp(10)));
            boolean on = sel.equals("L:" + i);
            FrameLayout dot = new FrameLayout(a);
            GradientDrawable g = new GradientDrawable();
            g.setShape(GradientDrawable.OVAL);
            g.setColor(l.color);
            dot.setBackground(g);
            dot.addView(Sf.img(a, "list.bullet", Color.WHITE), new FrameLayout.LayoutParams(a.dp(12), a.dp(12), Gravity.CENTER));
            r.addView(dot, new LinearLayout.LayoutParams(a.dp(24), a.dp(24)));
            TextView n = Ui.tv(a, l.name, 13, on ? Color.WHITE : Theme.fg(), false);
            n.setPadding(a.dp(8), 0, 0, 0);
            n.setSingleLine(true);
            r.addView(n, new LinearLayout.LayoutParams(0, -2, 1f));
            r.addView(Ui.tv(a, String.valueOf(countList(i)), 12, on ? 0xCCFFFFFF : Theme.sub(), false));
            r.setOnClickListener(v -> { sel = "L:" + k; refresh(); });
            r.setOnLongClickListener(v -> { listMenu(k); return true; });
            side.addView(r, new LinearLayout.LayoutParams(-1, -2));
        }
        TextView addL = Ui.tv(a, "+  Add List", 13, Theme.accent, true);
        addL.setPadding(a.dp(8), a.dp(10), 0, 0);
        addL.setOnClickListener(v -> a.prompt("New List", "", s -> {
            RList l = new RList(); l.name = s; l.color = LIST_C[lists.size() % LIST_C.length]; lists.add(l); sel = "L:" + (lists.size() - 1); save(); refresh();
        }));
        side.addView(addL);
    }

    LinearLayout.LayoutParams tileLp(int c) {
        LinearLayout.LayoutParams l = new LinearLayout.LayoutParams(0, -1, 1f);
        l.setMargins(c == 0 ? 0 : a.dp(3), a.dp(3), c == 0 ? a.dp(3) : 0, a.dp(3));
        return l;
    }

    View tile(final int k) {
        FrameLayout t = new FrameLayout(a);
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{SettingsApp.lighten(SMART_C[k], .18f), SMART_C[k]});
        g.setCornerRadius(a.dp(12));
        if (sel.equals(SMART[k])) g.setStroke(a.dp(2), Theme.dark ? 0xFFFFFFFF : 0xFF1D1D1F);
        t.setBackground(g);
        t.addView(Sf.img(a, SMART_I[k], Color.WHITE), new FrameLayout.LayoutParams(a.dp(14), a.dp(14), Gravity.TOP | Gravity.LEFT) {{ leftMargin = a.dp(8); topMargin = a.dp(7); }});
        TextView n = Ui.tv(a, String.valueOf(count(SMART[k])), 18, Color.WHITE, true);
        t.addView(n, new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.RIGHT) {{ rightMargin = a.dp(8); topMargin = a.dp(3); }});
        TextView l = Ui.tv(a, SMART[k], 11.5f, Color.WHITE, true);
        t.addView(l, new FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM | Gravity.LEFT) {{ leftMargin = a.dp(8); bottomMargin = a.dp(5); }});
        t.setOnClickListener(v -> { sel = SMART[k]; refresh(); });
        return t;
    }

    int selColor() {
        if (sel.startsWith("L:")) { int i = Integer.parseInt(sel.substring(2)); return i < lists.size() ? lists.get(i).color : Theme.accent; }
        for (int i = 0; i < SMART.length; i++) if (SMART[i].equals(sel)) return SMART_C[i];
        return Theme.accent;
    }

    void drawMain() {
        main.removeAllViews();
        main.setPadding(a.dp(20), a.dp(12), a.dp(20), a.dp(16));
        List<Item> shown = new ArrayList<>();
        String name;
        if (sel.startsWith("L:")) {
            int li = Integer.parseInt(sel.substring(2));
            name = lists.get(li).name;
            for (Item i : items) if (i.list == li && !i.done) shown.add(i);
        } else {
            name = sel;
            for (Item i : items) if (inSmart(i, sel)) shown.add(i);
        }
        LinearLayout head = new LinearLayout(a);
        head.setGravity(Gravity.CENTER_VERTICAL);
        TextView t = Ui.tv(a, name, 28, selColor(), true);
        t.setTypeface(Emoji.font(true));
        head.addView(t, new LinearLayout.LayoutParams(0, -2, 1f));
        head.addView(Ui.tv(a, String.valueOf(shown.size()), 28, selColor(), true));
        main.addView(head);
        main.addView(Ui.sepLine(a), new LinearLayout.LayoutParams(-1, 1) {{ topMargin = a.dp(8); }});
        if (shown.isEmpty()) {
            TextView e = Ui.tv(a, "No Reminders", 14, Theme.sub(), false);
            e.setPadding(0, a.dp(24), 0, 0);
            main.addView(e);
        }
        for (final Item it : shown) main.addView(row(it));
    }

    View row(final Item it) {
        LinearLayout r = new LinearLayout(a);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setPadding(0, a.dp(8), 0, a.dp(8));
        FrameLayout cb = new FrameLayout(a);
        GradientDrawable g = new GradientDrawable();
        g.setShape(GradientDrawable.OVAL);
        g.setStroke(a.dp(1.5f), it.done ? selColor() : Theme.sub());
        if (it.done) g.setColor(selColor());
        cb.setBackground(g);
        if (it.done) cb.addView(Sf.img(a, "checkmark", Color.WHITE), new FrameLayout.LayoutParams(a.dp(11), a.dp(11), Gravity.CENTER));
        cb.setOnClickListener(v -> { it.done = !it.done; save(); refresh(); });
        r.addView(cb, new LinearLayout.LayoutParams(a.dp(22), a.dp(22)));
        LinearLayout tx = new LinearLayout(a);
        tx.setOrientation(LinearLayout.VERTICAL);
        tx.setPadding(a.dp(12), 0, a.dp(8), 0);
        TextView t = Ui.tv(a, it.text, 14, it.done ? Theme.sub() : Theme.fg(), false);
        if (it.done) t.setPaintFlags(t.getPaintFlags() | android.graphics.Paint.STRIKE_THRU_TEXT_FLAG);
        tx.addView(t);
        if (!it.due.isEmpty()) tx.addView(Ui.tv(a, it.due, 11.5f, it.due.compareTo(today()) < 0 && !it.done ? 0xFFFF3B30 : Theme.sub(), false));
        r.addView(tx, new LinearLayout.LayoutParams(0, -2, 1f));
        ImageViewHolder.add(a, r, it, this);
        r.setOnClickListener(v -> a.prompt("Edit Reminder", it.text, s -> { it.text = s; save(); refresh(); }));
        r.setOnLongClickListener(v -> { a.confirm("Delete this reminder?", () -> { items.remove(it); save(); refresh(); }); return true; });
        return r;
    }

    /** trailing flag button */
    static class ImageViewHolder {
        static void add(MainActivity a, LinearLayout r, final Item it, final RemindersApp app) {
            android.widget.ImageView f = Sf.img(a, it.flag ? "flag.fill" : "flag", it.flag ? 0xFFFF9F0A : Theme.sub());
            f.setOnClickListener(v -> { it.flag = !it.flag; app.save(); app.refresh(); });
            r.addView(f, new LinearLayout.LayoutParams(a.dp(18), a.dp(18)));
        }
    }

    void listMenu(final int k) {
        if (lists.size() <= 1) return;
        a.confirm("Delete list \u201C" + lists.get(k).name + "\u201D and its reminders?", () -> {
            lists.remove(k);
            List<Item> keep = new ArrayList<>();
            for (Item i : items) { if (i.list == k) continue; if (i.list > k) i.list--; keep.add(i); }
            items.clear(); items.addAll(keep);
            sel = "Today"; save(); refresh();
        });
    }

    void addItem() {
        final int li = sel.startsWith("L:") ? Integer.parseInt(sel.substring(2)) : 0;
        a.prompt("New Reminder", "", s -> {
            final Item it = new Item();
            it.id = System.nanoTime(); it.list = li; it.text = s;
            if (sel.equals("Today")) it.due = today();
            if (sel.equals("Flagged")) it.flag = true;
            items.add(it);
            Calendar c = Calendar.getInstance();
            DatePickerDialog d = new DatePickerDialog(a, (p, y, m, day) -> { it.due = String.format(Locale.US, "%04d-%02d-%02d", y, m + 1, day); save(); refresh(); },
                    c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH));
            d.setButton(android.content.DialogInterface.BUTTON_NEGATIVE, "No Date", (x, w) -> { save(); refresh(); });
            d.setOnCancelListener(x -> { save(); refresh(); });
            save(); refresh();
            d.show();
        });
    }
}
