package com.achir.xiaokeshift;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.GridLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class MainActivity extends Activity {
    static final int REQ_CAL = 1, REQ_EXPORT = 2, REQ_IMPORT = 3, REQ_SOUND = 4, REQ_NOTIFY = 5;

    Store st;
    MonthView month;
    private TextView title, card, editInfo;
    private LinearLayout editBar;
    private Spinner profileSpinner;
    private final List<String> profileIds = new ArrayList<>();
    Calendar shown = Calendar.getInstance(), selected = Calendar.getInstance();
    String profile = Store.ME;
    final Handler ui = new Handler(Looper.getMainLooper());
    private Runnable afterCalPermission;
    float dp;
    private boolean unlocked;

    @Override
    protected void onCreate(Bundle b) {
        st = Store.get(this);
        if (st.flag("dark", false)) setTheme(android.R.style.Theme_Material);
        super.onCreate(b);
        dp = getResources().getDisplayMetrics().density;
        setTitle("小柯班表助手");
        String o = st.pref("orientation", "auto");
        setRequestedOrientation(o.equals("portrait") ? ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                : o.equals("landscape") ? ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE : ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
        if (st.flag("autoDelete", false)) {
            Calendar c = Calendar.getInstance();
            c.add(Calendar.MONTH, -3);
            st.deleteOlderThan(Store.key(c));
            st.save();
        }
        build();
        XiaoKeServer.ensureStarted(this);
        AlarmService.channel(this);
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQ_NOTIFY);
        refresh();
        if (st.num("seenHelp", 0) < 1) { st.setNum("seenHelp", 1); MoreUi.help(this); }
    }

    private void build() {
        boolean dk = st.flag("dark", false);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(dk ? 0xFF101522 : 0xFFF1F3F6);
        int pad = (int) (8 * dp);
        root.setPadding(pad, pad, pad, pad);

        // 班表選擇（我的／第二班表／好友）
        profileSpinner = new Spinner(this);
        profileSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            public void onItemSelected(AdapterView<?> a, View v, int pos, long id) {
                if (pos < profileIds.size() && !profileIds.get(pos).equals(profile)) {
                    profile = profileIds.get(pos);
                    refresh();
                }
            }
            public void onNothingSelected(AdapterView<?> a) { }
        });
        root.addView(profileSpinner);

        LinearLayout bar = new LinearLayout(this);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        Button prev = smallButton("‹"), next = smallButton("›");
        title = new TextView(this);
        title.setTextSize(20);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setTextColor(dk ? Color.WHITE : 0xFF101522);
        title.setGravity(Gravity.CENTER);
        bar.addView(prev);
        bar.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
        Button go = smallButton("今");
        go.setTextSize(15);
        bar.addView(go);
        bar.addView(next);
        prev.setOnClickListener(v -> moveMonth(-1));
        next.setOnClickListener(v -> moveMonth(1));
        go.setOnClickListener(v -> { shown = Calendar.getInstance(); selected = Calendar.getInstance(); refresh(); });
        title.setOnClickListener(v -> MoreUi.pickMonth(this));      // 「前往」某年某月
        root.addView(bar);

        month = new MonthView(this);
        month.setOnDayClick(day -> {
            if (day == null) { updateEditInfo(); return; }
            selected = day;
            refresh();
            editDay(day);
        });
        root.addView(month);

        // 批次編輯列
        editBar = new LinearLayout(this);
        editBar.setOrientation(LinearLayout.VERTICAL);
        editBar.setVisibility(View.GONE);
        editInfo = new TextView(this);
        editInfo.setTextColor(dk ? Color.WHITE : 0xFF263238);
        editInfo.setPadding(0, (int) (6 * dp), 0, 0);
        editBar.addView(editInfo);
        LinearLayout eb = new LinearLayout(this);
        String[] labels = {"設定班別", "清除", "複製成樣式", "完成"};
        for (int i = 0; i < labels.length; i++) {
            Button bt = new Button(this);
            bt.setText(labels[i]);
            bt.setAllCaps(false);
            final int idx = i;
            bt.setOnClickListener(v -> editAction(idx));
            eb.addView(bt, new LinearLayout.LayoutParams(0, -2, 1));
        }
        editBar.addView(eb);
        root.addView(editBar);

        card = new TextView(this);
        card.setTextSize(14);
        card.setTextColor(dk ? 0xFFECEFF1 : 0xFF263238);
        card.setLineSpacing(0, 1.2f);
        card.setPadding((int) (14 * dp), (int) (12 * dp), (int) (14 * dp), (int) (12 * dp));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(dk ? 0xFF1E2433 : Color.WHITE);
        bg.setCornerRadius(12 * dp);
        card.setBackground(bg);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = (int) (8 * dp);
        root.addView(card, lp);

        ScrollView sv = new ScrollView(this);
        sv.addView(root);
        setContentView(sv);
    }

    @Override
    protected void onResume() {
        super.onResume();
        String pw = st.pref("password", "");
        if (!pw.isEmpty() && !unlocked) askPassword(pw);
        refresh();
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (!isChangingConfigurations()) unlocked = false;
    }

    // ================================================================ 密碼（原版「設定密碼」）
    static String hash(String s) {
        try {
            byte[] d = MessageDigest.getInstance("SHA-256").digest(("xks:" + s).getBytes("UTF-8"));
            StringBuilder b = new StringBuilder();
            for (byte x : d) b.append(String.format("%02x", x));
            return b.toString();
        } catch (Exception e) { return s; }
    }

    private void askPassword(final String pw) {
        final EditText et = new EditText(this);
        et.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        new AlertDialog.Builder(this).setTitle("請輸入密碼").setView(et).setCancelable(false)
                .setPositiveButton("確定", (d, w) -> {
                    if (hash(et.getText().toString()).equals(pw)) unlocked = true;
                    else { toast("密碼錯誤"); finish(); }
                })
                .setNegativeButton("離開", (d, w) -> finish()).show();
    }

    // ================================================================ 畫面
    private Button smallButton(String t) {
        Button b = new Button(this);
        b.setText(t);
        b.setTextSize(22);
        b.setMinWidth((int) (48 * dp));
        b.setMinimumWidth((int) (48 * dp));
        return b;
    }

    void moveMonth(int delta) {
        shown.set(Calendar.DAY_OF_MONTH, 1);
        shown.add(Calendar.MONTH, delta);
        refresh();
    }

    int y() { return shown.get(Calendar.YEAR); }
    int m() { return shown.get(Calendar.MONTH) + 1; }

    void refresh() {
        if (title == null) return;
        profileIds.clear();
        profileIds.addAll(st.profileIds());
        List<String> names = new ArrayList<>();
        for (String id : profileIds) names.add(st.profileName(id));
        if (!profileIds.contains(profile)) profile = Store.ME;
        profileSpinner.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, names));
        profileSpinner.setSelection(profileIds.indexOf(profile));

        title.setText(String.format(Locale.US, "%d 年 %d 月", y(), m()));
        month.setProfile(profile);
        month.setMonth(y(), m() - 1);
        month.setSelected(Store.key(selected));
        card.setText(dayDetail(selected, false));
        loadEventDots();
        AlarmScheduler.schedule(this);
        XiaoKeWidget.refresh(this);
        MonthWidget.refresh(this);
    }

    /** 月曆上的 Google 行事曆事件藍點（設定可關）。 */
    private void loadEventDots() {
        if (!st.flag("eventDots", false) || !hasCalPermission()) { month.eventDays = new HashSet<>(); return; }
        final int yy = y(), mm = m();
        new Thread(() -> {
            final Set<String> s = new HashSet<>();
            Calendar c = Calendar.getInstance();
            c.clear();
            c.set(yy, mm - 1, 1);
            int max = c.getActualMaximum(Calendar.DAY_OF_MONTH);
            for (int d = 1; d <= max; d++) {
                c.set(Calendar.DAY_OF_MONTH, d);
                if (!GCalSync.eventsOn(getContentResolver(), c).isEmpty()) s.add(Store.key(c));
            }
            ui.post(() -> { month.eventDays = s; month.invalidate(); });
        }).start();
    }

    /** 選取日的班別＋農民曆＋朋友今日班別。 */
    String dayDetail(Calendar c, boolean withEvents) {
        String k = Store.key(c);
        StringBuilder s = new StringBuilder();
        s.append(c.get(Calendar.YEAR)).append("年").append(c.get(Calendar.MONTH) + 1).append("月")
                .append(c.get(Calendar.DAY_OF_MONTH)).append("日 星期").append(XiaoKeServer.WEEK[c.get(Calendar.DAY_OF_WEEK) - 1]);
        String hol = Holidays.name(st, k);
        if (!hol.isEmpty()) s.append("　").append(hol);
        if (Holidays.isPayday(st, k)) s.append("　💰發薪日");
        s.append("\n").append(st.profileName(profile)).append("：").append(st.describe(profile, k));
        ShiftType t = st.classify(st.code(profile, k));
        if (t != null && !t.desc.isEmpty()) s.append("\n班別說明：").append(t.desc);
        String note = st.note(profile, k);
        if (!note.isEmpty()) s.append("\n備註：").append(note);
        if (profile.equals(Store.ME)) {
            float ot = st.extra("ot", k), to = st.extra("to", k), al = st.extra("allow", k);
            if (ot != 0) s.append("\n加班：").append(ot).append(" 小時");
            if (to != 0) s.append("\n借休：").append(to).append(" 小時");
            if (al != 0) s.append("\n日津貼：").append(al);
            // 朋友今日的班別（休假不顯示）
            StringBuilder f = new StringBuilder();
            for (String id : st.profileIds()) {
                if (id.equals(Store.ME)) continue;
                String raw = st.code(id, k);
                ShiftType ft = st.classify(raw);
                if (raw.isEmpty() || (ft != null && ft.off)) continue;
                f.append("\n　").append(st.profileName(id)).append("：").append(st.describe(id, k));
            }
            if (f.length() > 0) s.append("\n\n【朋友的班別】").append(f);
        }
        Almanac a = Almanac.compute(c);
        s.append("\n\n【農民曆】農曆").append(Almanac.lunarString(c));
        if (a.ok) {
            s.append("\n").append(a.yearGZ).append("年（").append(a.zodiac).append("）").append(a.monthGZ).append("月 ")
                    .append(a.dayGZ).append("日　").append(a.jianchu).append("日");
            if (!a.termToday.isEmpty()) s.append("　今日").append(a.termToday);
            s.append("\n宜：").append(a.yi).append("\n忌：").append(a.ji)
                    .append("\n").append(a.chong).append("　煞").append(a.sha).append("\n下一節氣：").append(a.nextTerm);
        }
        if (withEvents && hasCalPermission()) {
            List<String> ev = GCalSync.eventsOn(getContentResolver(), c);
            s.append("\n\n【Google 行事曆】");
            if (ev.isEmpty()) s.append("\n沒有任何事件");
            for (String e : ev) s.append("\n").append(e);
        }
        return s.toString();
    }

    // ================================================================ 編輯某一天
    void editDay(final Calendar day) {
        final String k = Store.key(day);
        final String[] pick = {st.explicit(profile, k)};
        final boolean me = profile.equals(Store.ME);
        LinearLayout box = Ui.box(this);
        final TextView cur = new TextView(this);
        cur.setTextSize(15);
        cur.setTypeface(Typeface.DEFAULT_BOLD);
        box.addView(cur);

        final List<Button> buttons = new ArrayList<>();
        final Runnable mark = () -> {
            ShiftType t = st.classify(pick[0]);
            String auto = st.code(profile, k);
            cur.setText("選擇：" + (pick[0].isEmpty() ? (auto.isEmpty() ? "（不排班）" : "（樣式自動：" + auto + "）")
                    : pick[0] + (t != null && !t.code.equalsIgnoreCase(pick[0]) ? " → " + t.name : "")));
            for (Button bt : buttons) bt.setAlpha(String.valueOf(bt.getTag()).equalsIgnoreCase(pick[0]) ? 1f : 0.55f);
        };
        box.addView(shiftGrid(buttons, code -> { pick[0] = code; mark.run(); }, () -> pick[0], mark));

        final EditText note = new EditText(this);
        note.setHint("備註（例如：換班、教學、會議）");
        note.setText(st.note(profile, k));
        box.addView(note);
        final EditText ot = me ? Ui.field(box, "加班（小時）", num(st.extra("ot", k)), Ui.DECIMAL) : null;
        final EditText to = me ? Ui.field(box, "借休（小時）", num(st.extra("to", k)), Ui.DECIMAL) : null;
        final EditText al = me ? Ui.field(box, "日津貼（金額）", num(st.extra("allow", k)), Ui.DECIMAL) : null;
        final EditText hol = Ui.field(box, "自訂特殊假日名稱（空白＝無）", st.customHoliday(k), InputType.TYPE_CLASS_TEXT);

        TextView detail = new TextView(this);
        detail.setTextSize(13);
        detail.setTextColor(0xFF546E7A);
        detail.setPadding(0, (int) (8 * dp), 0, (int) (8 * dp));
        detail.setText(dayDetail(day, true).replaceFirst("(?s)^.*?【農民曆】", "【農民曆】"));
        box.addView(detail);
        mark.run();

        new AlertDialog.Builder(this)
                .setTitle((day.get(Calendar.MONTH) + 1) + "月" + day.get(Calendar.DAY_OF_MONTH) + "日（"
                        + XiaoKeServer.WEEK[day.get(Calendar.DAY_OF_WEEK) - 1] + "）農曆" + Almanac.lunarString(day))
                .setView(Ui.scroll(this, box))
                .setPositiveButton("儲存", (d, w) -> {
                    st.setCode(profile, k, pick[0]);
                    st.setNote(profile, k, note.getText().toString());
                    if (me) {
                        st.setExtra("ot", k, Ui.f(ot));
                        st.setExtra("to", k, Ui.f(to));
                        st.setExtra("allow", k, Ui.f(al));
                    }
                    st.setCustomHoliday(k, hol.getText().toString());
                    st.save();
                    refresh();
                })
                .setNeutralButton("批次編輯", (d, w) -> startMulti(k))
                .setNegativeButton("取消", null).show();
    }

    static String num(float v) { return v == 0 ? "" : (v == (int) v ? String.valueOf((int) v) : String.valueOf(v)); }

    interface Pick { void on(String code); }
    interface Current { String get(); }

    /** 班別按鈕格（隱藏的班別不顯示），含「自訂代號」「清除」。 */
    GridLayout shiftGrid(final List<Button> buttons, final Pick pick, final Current current, final Runnable mark) {
        GridLayout grid = new GridLayout(this);
        grid.setColumnCount(4);
        for (final ShiftType t : st.types) {
            if (t.hidden) continue;
            Button bt = Ui.colorButton(this, t.code + "\n" + t.name, t.color);
            bt.setTag(t.code);
            bt.setOnClickListener(v -> pick.on(t.code));
            buttons.add(bt);
            grid.addView(bt);
        }
        Button custom = Ui.colorButton(this, "自訂\n代號", 0xFF9E9E9E);
        custom.setTag("\u0000");
        custom.setOnClickListener(v -> {
            final EditText et = new EditText(this);
            et.setHint("例如 D/洗、傷照");
            String c = current.get();
            et.setText(st.type(c) == null ? c : "");
            new AlertDialog.Builder(this).setTitle("自訂班別代號").setView(et)
                    .setPositiveButton("確定", (d, w) -> pick.on(et.getText().toString().trim()))
                    .setNegativeButton("取消", null).show();
        });
        grid.addView(custom);
        Button clear = Ui.colorButton(this, "清除", 0xFF757575);
        clear.setTag("");
        clear.setOnClickListener(v -> pick.on(""));
        buttons.add(clear);
        grid.addView(clear);
        return grid;
    }

    // ================================================================ 批次編輯（原版「編輯模式」大範圍選取）
    void startMulti(String firstKey) {
        month.setMulti(true);
        if (firstKey != null) month.picked.add(firstKey);
        editBar.setVisibility(View.VISIBLE);
        updateEditInfo();
        toast("點選多天；長按另一天可選取整段範圍");
    }

    private void updateEditInfo() {
        editInfo.setText("批次編輯：已選 " + month.picked.size() + " 天（長按可選範圍）");
    }

    private List<String> pickedSorted() {
        List<String> l = new ArrayList<>(month.picked);
        java.util.Collections.sort(l);
        return l;
    }

    private void editAction(int idx) {
        final List<String> keys = pickedSorted();
        if (idx == 3) { month.setMulti(false); editBar.setVisibility(View.GONE); refresh(); return; }
        if (keys.isEmpty()) { toast("請先選日子"); return; }
        if (idx == 0) {
            final AlertDialog[] dlg = new AlertDialog[1];
            List<Button> bs = new ArrayList<>();
            GridLayout g = shiftGrid(bs, code -> {
                for (String k : keys) st.setCode(profile, k, code);
                st.save();
                if (dlg[0] != null) dlg[0].dismiss();
                month.picked.clear();
                updateEditInfo();
                refresh();
            }, () -> "", null);
            LinearLayout box = Ui.box(this);
            box.addView(g);
            dlg[0] = new AlertDialog.Builder(this).setTitle("把 " + keys.size() + " 天設成").setView(box)
                    .setNegativeButton("取消", null).show();
        } else if (idx == 1) {
            for (String k : keys) st.setCode(profile, k, "");
            st.save();
            month.picked.clear();
            updateEditInfo();
            refresh();
        } else if (idx == 2) {
            ShiftUi.copyAsPattern(this, keys);
        }
    }

    // ================================================================ 選單
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        String[] items = {"批次編輯", "班別設定", "班表樣式（複製／貼上／自動重覆）", "匯入病房助理工作站月班表",
                "同步到 Google 行事曆", "開啟 Google 日曆", "小柯機器人連線", "鬧鐘設定", "統計（工時／加班／薪資）",
                "查看所有備註", "特殊假日", "發薪日", "班表管理（五份班表／改名／好友）", "傳送班表", "設定", "備份班表", "還原班表", "使用說明"};
        for (int i = 0; i < items.length; i++) menu.add(0, i + 1, 0, items[i]);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case 1: startMulti(null); return true;
            case 2: ShiftUi.editTypes(this); return true;
            case 3: ShiftUi.patterns(this); return true;
            case 4: MoreUi.importWard(this, profile); return true;
            case 5: withCalPermission(() -> MoreUi.syncGoogle(this)); return true;
            case 6: MoreUi.openGoogleCalendar(this); return true;
            case 7: MoreUi.showXiaoKe(this); return true;
            case 8: MoreUi.alarmSettings(this); return true;
            case 9: MoreUi.stats(this); return true;
            case 10: MoreUi.allNotes(this); return true;
            case 11: MoreUi.holidays(this); return true;
            case 12: MoreUi.payday(this); return true;
            case 13: MoreUi.friends(this); return true;
            case 14: MoreUi.share(this); return true;
            case 15: MoreUi.settings(this); return true;
            case 16: startDoc(Intent.ACTION_CREATE_DOCUMENT, REQ_EXPORT); return true;
            case 17: startDoc(Intent.ACTION_OPEN_DOCUMENT, REQ_IMPORT); return true;
            case 18: MoreUi.help(this); return true;
        }
        return super.onOptionsItemSelected(item);
    }

    void toast(String s) { Toast.makeText(this, s, Toast.LENGTH_LONG).show(); }

    // ================================================================ 權限
    boolean hasCalPermission() {
        return checkSelfPermission(Manifest.permission.WRITE_CALENDAR) == PackageManager.PERMISSION_GRANTED
                && checkSelfPermission(Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED;
    }

    void withCalPermission(Runnable r) {
        if (hasCalPermission()) { r.run(); return; }
        afterCalPermission = r;
        requestPermissions(new String[]{Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR}, REQ_CAL);
    }

    @Override
    public void onRequestPermissionsResult(int req, String[] perms, int[] res) {
        if (req == REQ_CAL) {
            if (hasCalPermission() && afterCalPermission != null) afterCalPermission.run();
            else if (!hasCalPermission()) toast("您必須開啟「日曆」權限才能使用 Google 行事曆功能");
            afterCalPermission = null;
            refresh();
        }
    }

    // ================================================================ 備份 / 還原（可選 Google 雲端硬碟或手機儲存空間）
    void startDoc(String action, int req) {
        Intent i = new Intent(action);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType(req == REQ_IMPORT ? "*/*" : "application/json");
        if (req == REQ_EXPORT) i.putExtra(Intent.EXTRA_TITLE, "小柯班表備份_" + Store.key(Calendar.getInstance()) + ".json");
        startActivityForResult(i, req);
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        if (res != RESULT_OK || data == null) return;
        if (req == REQ_SOUND) {
            Uri u = data.getParcelableExtra(android.media.RingtoneManager.EXTRA_RINGTONE_PICKED_URI);
            st.setPref("alarmSound", u == null ? "" : u.toString());
            toast("新的鬧鈴聲音已經被設定");
            return;
        }
        Uri uri = data.getData();
        if (uri == null) return;
        try {
            if (req == REQ_EXPORT) {
                try (OutputStream out = getContentResolver().openOutputStream(uri)) {
                    out.write(st.exportAll().toString(2).getBytes("UTF-8"));
                }
                st.setPref("lastBackup", Store.key(Calendar.getInstance()));
                toast("備份成功");
            } else if (req == REQ_IMPORT) {
                StringBuilder sb = new StringBuilder();
                try (BufferedReader r = new BufferedReader(new InputStreamReader(getContentResolver().openInputStream(uri), "UTF-8"))) {
                    for (String line; (line = r.readLine()) != null; ) sb.append(line).append('\n');
                }
                final JSONObject o = new JSONObject(sb.toString());
                new AlertDialog.Builder(this).setTitle("還原").setMessage("注意：現有資料會被備份資料所取代。確定要還原？")
                        .setPositiveButton("還原", (d, w) -> {
                            try { st.importAll(o); refresh(); toast("還原成功"); } catch (Exception e) { toast("還原失敗：" + e.getMessage()); }
                        }).setNegativeButton("取消", null).show();
            }
        } catch (Exception e) {
            toast("失敗：" + e.getMessage());
        }
    }
}
