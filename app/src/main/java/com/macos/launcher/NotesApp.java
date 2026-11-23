package com.macos.launcher;

import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import org.json.JSONArray;
import org.json.JSONObject;

/** Notes: list of notes grouped by recency + free-text editor, stored locally. */
public class NotesApp {
    static class Note { long id, ts; String text = ""; }

    final MainActivity a;
    final List<Note> notes = new ArrayList<>();
    Note cur;
    String query = "";
    LinearLayout list;
    EditText editor;
    TextView empty;
    boolean binding;

    public static void open(MainActivity a) { new NotesApp(a); }

    NotesApp(MainActivity act) {
        a = act;
        load();
        TextView add = Ui.sfBtn(a, "square.and.pencil", 14), del = Ui.sfBtn(a, "trash", 14);
        add.setOnClickListener(v -> newNote());
        del.setOnClickListener(v -> { if (cur != null) a.confirm("Delete this note?", this::deleteCur); });
        LinearLayout tb = new LinearLayout(a);
        tb.setGravity(Gravity.CENTER_VERTICAL);
        TextView t = Ui.tv(a, "All Notes", 13.5f, Theme.fg(), true);
        t.setPadding(a.dp(12), 0, 0, 0);
        tb.addView(t, new LinearLayout.LayoutParams(0, -2, 1f));
        tb.addView(del, new LinearLayout.LayoutParams(a.dp(32), a.dp(32)));
        LinearLayout.LayoutParams al = new LinearLayout.LayoutParams(a.dp(32), a.dp(32));
        al.leftMargin = a.dp(6);
        tb.addView(add, al);

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
            public void afterTextChanged(Editable e) { query = e.toString().toLowerCase(Locale.ROOT).trim(); rebuild(); }
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
        body.addView(left, new LinearLayout.LayoutParams(a.dp(210), -1));
        body.addView(Ui.sepLine(a), new LinearLayout.LayoutParams(1, -1));

        android.widget.FrameLayout right = new android.widget.FrameLayout(a);
        editor = new EditText(a);
        editor.setBackground(null);
        editor.setGravity(Gravity.TOP | Gravity.START);
        editor.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        editor.setTextColor(Theme.fg());
        editor.setHintTextColor(Theme.sub());
        editor.setHint("Start typing\u2026");
        editor.setTextSize(14.5f);
        editor.setLineSpacing(0, 1.12f);
        editor.setPadding(a.dp(18), a.dp(14), a.dp(18), a.dp(14));
        editor.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int x, int y, int z) {}
            public void onTextChanged(CharSequence s, int x, int y, int z) {}
            public void afterTextChanged(Editable e) {
                if (binding || cur == null) return;
                cur.text = e.toString();
                cur.ts = System.currentTimeMillis();
                save();
                rebuild();
            }
        });
        right.addView(editor, new android.widget.FrameLayout.LayoutParams(-1, -1));
        empty = Ui.tv(a, "No Notes", 15, Theme.sub(), false);
        right.addView(empty, new android.widget.FrameLayout.LayoutParams(-2, -2, Gravity.CENTER));
        body.addView(right, new LinearLayout.LayoutParams(0, -1, 1f));

        if (notes.isEmpty()) newNoteSilent();
        if (cur == null && !notes.isEmpty()) cur = notes.get(0);
        a.openWindow(new MacWindow(a, "Notes", tb, null, body), 700, 340);
        rebuild();
        bind();
    }

    void load() {
        try {
            JSONArray ar = new JSONArray(a.prefs.getString("notes", "[]"));
            for (int i = 0; i < ar.length(); i++) {
                JSONObject o = ar.getJSONObject(i);
                Note n = new Note();
                n.id = o.getLong("id"); n.ts = o.getLong("ts"); n.text = o.optString("t", "");
                notes.add(n);
            }
        } catch (Exception e) { }
        sort();
    }

    void save() {
        try {
            JSONArray ar = new JSONArray();
            for (Note n : notes) ar.put(new JSONObject().put("id", n.id).put("ts", n.ts).put("t", n.text));
            a.prefs.edit().putString("notes", ar.toString()).apply();
        } catch (Exception e) { }
    }

    void sort() {
        Collections.sort(notes, new Comparator<Note>() {
            public int compare(Note x, Note y) { return Long.compare(y.ts, x.ts); }
        });
    }

    void newNoteSilent() {
        Note n = new Note();
        n.id = System.nanoTime();
        n.ts = System.currentTimeMillis();
        notes.add(0, n);
        cur = n;
    }

    void newNote() {
        if (cur != null && cur.text.trim().isEmpty()) { bind(); editor.requestFocus(); return; }
        newNoteSilent();
        save(); rebuild(); bind();
        editor.requestFocus();
    }

    void deleteCur() {
        notes.remove(cur);
        cur = notes.isEmpty() ? null : notes.get(0);
        if (cur == null) newNoteSilent();
        save(); rebuild(); bind();
    }

    void bind() {
        binding = true;
        editor.setText(cur == null ? "" : cur.text);
        editor.setSelection(editor.getText().length());
        binding = false;
        empty.setVisibility(View.GONE);
    }

    static String title(Note n) {
        String t = n.text.trim();
        if (t.isEmpty()) return "New Note";
        int i = t.indexOf('\n');
        return i < 0 ? t : t.substring(0, i);
    }

    static String preview(Note n) {
        String t = n.text.trim();
        int i = t.indexOf('\n');
        if (i < 0) return "No additional text";
        String r = t.substring(i + 1).trim().replace('\n', ' ');
        return r.isEmpty() ? "No additional text" : r;
    }

    void rebuild() {
        list.removeAllViews();
        sort();
        Calendar c = Calendar.getInstance();
        c.set(Calendar.HOUR_OF_DAY, 0); c.set(Calendar.MINUTE, 0); c.set(Calendar.SECOND, 0); c.set(Calendar.MILLISECOND, 0);
        long today = c.getTimeInMillis(), wk = today - 6L * 86400000L, mo = today - 29L * 86400000L;
        String last = null;
        SimpleDateFormat day = new SimpleDateFormat("d/M/yy", Locale.getDefault()), hm = new SimpleDateFormat("HH:mm", Locale.getDefault());
        for (final Note n : notes) {
            if (!query.isEmpty() && !n.text.toLowerCase(Locale.ROOT).contains(query)) continue;
            String g = n.ts >= today ? "Today" : n.ts >= wk ? "Previous 7 Days" : n.ts >= mo ? "Previous 30 Days" : "Older";
            if (!g.equals(last)) {
                last = g;
                TextView h = Ui.tv(a, g, 13, Theme.fg(), true);
                h.setPadding(a.dp(14), a.dp(10), a.dp(10), a.dp(4));
                list.addView(h);
            }
            LinearLayout r = new LinearLayout(a);
            r.setOrientation(LinearLayout.VERTICAL);
            r.setPadding(a.dp(12), a.dp(7), a.dp(10), a.dp(7));
            if (n == cur) r.setBackground(Ui.rr(Theme.dark ? 0x40FFD60A : 0x66FFD60A, a.dp(10)));
            TextView t = Ui.tv(a, title(n), 13, Theme.fg(), true);
            t.setSingleLine(true);
            r.addView(t);
            TextView s = Ui.tv(a, (n.ts >= today ? hm.format(new Date(n.ts)) : day.format(new Date(n.ts))) + "  " + preview(n), 11.5f, Theme.sub(), false);
            s.setSingleLine(true);
            r.addView(s);
            r.setOnClickListener(v -> { cur = n; rebuild(); bind(); });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
            lp.setMargins(a.dp(6), 0, a.dp(6), 0);
            list.addView(r, lp);
        }
    }
}
