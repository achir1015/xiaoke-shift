package com.achir.xiaokeshift;

import android.app.AlertDialog;
import android.content.ContentValues;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.PowerManager;
import android.provider.AlarmClock;
import android.provider.CalendarContract;
import android.provider.MediaStore;
import android.provider.Settings;
import android.text.InputType;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.NumberPicker;
import android.widget.Spinner;

import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** 其他功能：工作站匯入、Google 行事曆、小柯、鬧鐘設定、統計、備註、假日、發薪日、朋友、傳送、設定、說明。 */
final class MoreUi {
    private MoreUi() { }

    // ================================================================ 前往某年某月
    static void pickMonth(final MainActivity a) {
        LinearLayout box = Ui.box(a);
        box.setOrientation(LinearLayout.HORIZONTAL);
        final NumberPicker y = new NumberPicker(a), m = new NumberPicker(a);
        y.setMinValue(2000); y.setMaxValue(2099); y.setValue(a.y());
        m.setMinValue(1); m.setMaxValue(12); m.setValue(a.m());
        box.addView(y);
        box.addView(m);
        new AlertDialog.Builder(a).setTitle("前往（年／月）").setView(box)
                .setPositiveButton("前往", (d, w) -> {
                    a.shown.set(y.getValue(), m.getValue() - 1, 1);
                    a.refresh();
                }).setNegativeButton("取消", null).show();
    }

    // ================================================================ 病房助理工作站月班表
    /** 每份班表各自記住上次匯入的類別與姓名（我的班表沿用舊的 key）。 */
    private static String wardKey(String base, String profile) {
        return profile.equals(Store.ME) ? base : base + ":" + profile;
    }

    static void importWard(final MainActivity a, final String profile) {
        final Store st = a.st;
        final int yy = a.y(), mm = a.m();
        LinearLayout box = Ui.box(a);
        Ui.label(box, "讀取 " + yy + " 年 " + mm + " 月（目前顯示的月份）\n匯入到：" + st.profileName(profile));
        final Spinner cat = new Spinner(a);
        cat.setAdapter(new ArrayAdapter<>(a, android.R.layout.simple_spinner_dropdown_item, WardImport.LABELS));
        cat.setSelection(Math.max(0, java.util.Arrays.asList(WardImport.CATS).indexOf(st.pref(wardKey("wardCat", profile), "assistant"))));
        box.addView(cat);
        Ui.label(box, "姓名");
        final Spinner name = new Spinner(a);
        box.addView(name);
        final android.widget.TextView status = Ui.label(box, "");
        final CheckBox over = Ui.check(box, "覆蓋已排的日子", true);
        final List<WardImport.Employee> list = new ArrayList<>();

        // 選類別 → 背景讀取該月名單 → 填入姓名下拉選單
        final Runnable load = () -> {
            final String c = WardImport.CATS[cat.getSelectedItemPosition()];
            list.clear();
            name.setAdapter(new ArrayAdapter<>(a, android.R.layout.simple_spinner_dropdown_item, new String[]{"讀取名單中…"}));
            status.setText("");
            new Thread(() -> {
                try {
                    final List<WardImport.Employee> got = WardImport.fetch(c, yy, mm);
                    a.ui.post(() -> {
                        if (!c.equals(WardImport.CATS[cat.getSelectedItemPosition()])) return;   // 已切換類別
                        list.clear();
                        list.addAll(got);
                        String[] names = new String[got.size()];
                        int sel = 0;
                        String last = st.pref(wardKey("wardName", profile), "");
                        for (int i = 0; i < names.length; i++) {
                            names[i] = got.get(i).name;
                            if (names[i].equals(last) || (last.isEmpty() && names[i].equals(st.profileName(profile)))) sel = i;
                        }
                        if (names.length == 0) { status.setText("這個月的班表沒有人員資料"); names = new String[]{"（無）"}; }
                        name.setAdapter(new ArrayAdapter<>(a, android.R.layout.simple_spinner_dropdown_item, names));
                        name.setSelection(sel);
                    });
                } catch (final Exception e) {
                    a.ui.post(() -> {
                        name.setAdapter(new ArrayAdapter<>(a, android.R.layout.simple_spinner_dropdown_item, new String[]{"（無法讀取）"}));
                        status.setText(e.getMessage());
                    });
                }
            }).start();
        };
        cat.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            public void onItemSelected(android.widget.AdapterView<?> p, android.view.View v, int pos, long id) { load.run(); }
            public void onNothingSelected(android.widget.AdapterView<?> p) { }
        });

        new AlertDialog.Builder(a).setTitle("匯入工作站月班表").setView(box)
                .setPositiveButton("匯入", (d, w) -> {
                    int i = name.getSelectedItemPosition();
                    if (list.isEmpty() || i < 0 || i >= list.size()) { a.toast("名單還沒讀到，請稍候再試"); return; }
                    WardImport.Employee e = list.get(i);
                    st.setPref(wardKey("wardCat", profile), WardImport.CATS[cat.getSelectedItemPosition()]);
                    st.setPref(wardKey("wardName", profile), e.name);
                    finishImport(a, profile, e, yy, mm, over.isChecked());
                })
                .setNegativeButton("取消", null).show();
    }

    private static void finishImport(MainActivity a, String profile, WardImport.Employee e, int yy, int mm, boolean overwrite) {
        int n = WardImport.apply(a.st, profile, e, yy, mm, overwrite);
        // 第二～第五班表：以匯入的姓名當班表名稱（我的班表維持原名）
        if (Store.isJob(profile) && !profile.equals(Store.ME)) {
            a.st.renameProfile(profile, e.name);
            a.st.save();
        }
        a.refresh();
        a.toast("已匯入 " + e.name + " " + mm + " 月班表 " + n + " 天");
    }

    /** 從工作站名單挑同事，加成好友並匯入本月班表。 */
    private static void importFriendsFromWard(final MainActivity a) {
        final String[] cats = WardImport.LABELS;
        new AlertDialog.Builder(a).setTitle("從工作站匯入同事班表（" + a.m() + " 月）").setItems(cats, (d, w) -> {
            final String cat = WardImport.CATS[w];
            final int yy = a.y(), mm = a.m();
            new Thread(() -> {
                try {
                    final List<WardImport.Employee> list = WardImport.fetch(cat, yy, mm);
                    a.ui.post(() -> {
                        final String[] names = new String[list.size()];
                        final boolean[] on = new boolean[list.size()];
                        for (int i = 0; i < names.length; i++) {
                            names[i] = list.get(i).name;
                            on[i] = a.st.friendIds().contains("f:" + names[i]);
                        }
                        new AlertDialog.Builder(a).setTitle("勾選要加入的好友").setMultiChoiceItems(names, on, (d2, i, c) -> on[i] = c)
                                .setPositiveButton("匯入", (d2, w2) -> {
                                    int n = 0;
                                    for (int i = 0; i < names.length; i++) {
                                        if (!on[i]) continue;
                                        String id = a.st.addFriend(names[i]);
                                        WardImport.apply(a.st, id, list.get(i), yy, mm, true);
                                        n++;
                                    }
                                    a.st.save();
                                    a.refresh();
                                    a.toast("已匯入 " + n + " 位同事的班表，可在上方下拉選單切換");
                                }).setNegativeButton("取消", null).show();
                    });
                } catch (final Exception e) {
                    a.ui.post(() -> a.toast("讀取失敗：" + e.getMessage()));
                }
            }).start();
        }).show();
    }

    // ================================================================ Google 行事曆
    static void syncGoogle(final MainActivity a) {
        final List<GCalSync.Cal> cals = GCalSync.calendars(a.getContentResolver());
        if (cals.isEmpty()) { a.toast("手機上找不到可寫入的行事曆，請先在系統設定登入 Google 帳號"); return; }
        final String[] names = new String[cals.size()];
        int sel = 0;
        for (int i = 0; i < names.length; i++) {
            names[i] = cals.get(i).toString();
            if (cals.get(i).id == a.st.calendarId()) sel = i;
        }
        final int[] pick = {sel};
        new AlertDialog.Builder(a).setTitle("同步 " + a.m() + " 月「我的班表」到哪個行事曆？")
                .setSingleChoiceItems(names, sel, (d, w) -> pick[0] = w)
                .setPositiveButton("同步", (d, w) -> {
                    final long id = cals.get(pick[0]).id;
                    a.st.setCalendarId(id);
                    final int yy = a.y(), mm = a.m();
                    new Thread(() -> {
                        try {
                            final int n = GCalSync.syncMonth(a.getContentResolver(), a.st, id, yy, mm);
                            a.ui.post(() -> a.toast("已寫入 " + n + " 筆班表到 Google 行事曆（重複同步會先清掉舊的）"));
                        } catch (final Exception e) {
                            a.ui.post(() -> a.toast("同步失敗：" + e.getMessage()));
                        }
                    }).start();
                })
                .setNegativeButton("取消", null).show();
    }

    static void openGoogleCalendar(MainActivity a) {
        Uri.Builder b = CalendarContract.CONTENT_URI.buildUpon().appendPath("time");
        android.content.ContentUris.appendId(b, a.selected.getTimeInMillis());
        try { a.startActivity(new Intent(Intent.ACTION_VIEW).setData(b.build())); }
        catch (Exception e) { a.toast("無法開啟系統的月曆"); }
    }

    // ================================================================ 小柯機器人
    static void showXiaoKe(final MainActivity a) {
        String base = "http://" + XiaoKeServer.ip(a) + ":" + XiaoKeServer.PORT;
        String msg = "小柯機器人跟手機連同一個 Wi-Fi，開著本 App 時可以查班表：\n\n"
                + base + "/api/say?day=today\n（今天的班＋農民曆，一句話給小柯唸）\n\n"
                + base + "/api/say?day=tomorrow\n\n"
                + base + "/api/day?date=yyyy-MM-dd\n（JSON）\n\n"
                + base + "/api/month?ym=yyyy-MM\n（整月 JSON）\n\n"
                + "小柯現在會這樣說：\n「" + XiaoKeServer.say(a.st, Calendar.getInstance()) + "」";
        final String url = base + "/";
        new AlertDialog.Builder(a).setTitle("小柯機器人連線").setMessage(msg)
                .setPositiveButton("關閉", null)
                .setNeutralButton("用瀏覽器測試", (d, w) -> a.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url))))
                .show();
    }

    // ================================================================ 鬧鐘設定
    static void alarmSettings(final MainActivity a) {
        final Store st = a.st;
        LinearLayout box = Ui.box(a);
        Ui.label(box, AlarmScheduler.schedule(a) + "\n（每個班別的鬧鐘時間在「班別設定」裡設）");
        final EditText snooze = Ui.field(box, "貪睡時間（分鐘）", String.valueOf(st.num("snoozeMin", 5)), InputType.TYPE_CLASS_NUMBER);
        final EditText vol = Ui.field(box, "鬧鐘音量 %（空白＝不調整）", st.num("alarmVolume", -1) < 0 ? "" : String.valueOf(st.num("alarmVolume", -1)), InputType.TYPE_CLASS_NUMBER);
        final CheckBox noSnooze = Ui.check(box, "不顯示貪睡按鈕", st.flag("disableSnooze", false));
        final CheckBox vib = Ui.check(box, "震動", st.flag("vibrate", true));
        final CheckBox full = Ui.check(box, "鬧鐘響時顯示全螢幕對話框", st.flag("fullScreen", true));
        final CheckBox flip = Ui.check(box, "翻面關閉鬧鐘", st.flag("flipDismiss", false));
        final CheckBox outside = Ui.check(box, "點擊對話框外面亦可關閉鬧鐘", st.flag("tapOutside", false));
        final CheckBox speak = Ui.check(box, "關閉鬧鐘後，小柯唸出今天的班＋農民曆", st.flag("xiaokeSpeak", true));
        final CheckBox job2 = Ui.check(box, "只啟動「我的班表」的鬧鐘（第二～第五班表不響）", st.flag("ignoreJob2Alarm", false));
        Ui.label(box, "\n其他：");
        String[] more = {"設定鬧鈴聲音", "允許「鬧鐘和提醒」（準時響）", "關閉電池最佳化", "也設定到系統鬧鐘（下一次）", "測試鬧鐘（現在響）"};
        for (int i = 0; i < more.length; i++) {
            android.widget.Button b = new android.widget.Button(a);
            b.setText(more[i]);
            b.setAllCaps(false);
            final int idx = i;
            b.setOnClickListener(v -> alarmExtra(a, idx));
            box.addView(b);
        }
        new AlertDialog.Builder(a).setTitle("鬧鐘設定").setView(Ui.scroll(a, box))
                .setPositiveButton("儲存", (d, w) -> {
                    st.setNum("snoozeMin", Math.max(1, Ui.i(snooze, 5)));
                    st.setNum("alarmVolume", vol.getText().toString().trim().isEmpty() ? -1 : Math.min(100, Math.max(0, Ui.i(vol, 80))));
                    st.setFlag("disableSnooze", noSnooze.isChecked());
                    st.setFlag("vibrate", vib.isChecked());
                    st.setFlag("fullScreen", full.isChecked());
                    st.setFlag("flipDismiss", flip.isChecked());
                    st.setFlag("tapOutside", outside.isChecked());
                    st.setFlag("xiaokeSpeak", speak.isChecked());
                    st.setFlag("ignoreJob2Alarm", job2.isChecked());
                    a.toast(AlarmScheduler.schedule(a));
                }).setNegativeButton("取消", null).show();
    }

    private static void alarmExtra(MainActivity a, int idx) {
        try {
            if (idx == 0) {
                Intent i = new Intent(RingtoneManager.ACTION_RINGTONE_PICKER)
                        .putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALARM)
                        .putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, "鬧鈴聲")
                        .putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true);
                String cur = a.st.pref("alarmSound", "");
                if (!cur.isEmpty()) i.putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, Uri.parse(cur));
                a.startActivityForResult(i, MainActivity.REQ_SOUND);
            } else if (idx == 1) {
                if (Build.VERSION.SDK_INT >= 31 && !AlarmScheduler.canExact(a))
                    a.startActivity(new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:" + a.getPackageName())));
                else a.toast("您已經允許使用「鬧鐘與提醒」。");
            } else if (idx == 2) {
                PowerManager pm = (PowerManager) a.getSystemService(android.content.Context.POWER_SERVICE);
                if (pm.isIgnoringBatteryOptimizations(a.getPackageName())) a.toast("您已經關閉小柯班表助手的電池最佳化。");
                else a.startActivity(new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:" + a.getPackageName())));
            } else if (idx == 3) {
                AlarmScheduler.Next n = AlarmScheduler.find(a);
                if (n == null) { a.toast("沒有設定鬧鐘的班別"); return; }
                Calendar c = Calendar.getInstance();
                c.setTimeInMillis(n.at);
                a.startActivity(new Intent(AlarmClock.ACTION_SET_ALARM)
                        .putExtra(AlarmClock.EXTRA_HOUR, c.get(Calendar.HOUR_OF_DAY))
                        .putExtra(AlarmClock.EXTRA_MINUTES, c.get(Calendar.MINUTE))
                        .putExtra(AlarmClock.EXTRA_MESSAGE, "小柯班表：" + a.st.describe(n.profile, n.day)));
            } else {
                Intent s = new Intent(a, AlarmService.class).putExtra("day", Store.key(Calendar.getInstance())).putExtra("profile", Store.ME);
                if (Build.VERSION.SDK_INT >= 26) a.startForegroundService(s); else a.startService(s);
            }
        } catch (Exception e) {
            a.toast("無法開啟：" + e.getMessage());
        }
    }

    // ================================================================ 統計
    static void stats(final MainActivity a) {
        Store st = a.st;
        String prof = a.profile;
        LinkedHashMap<String, Integer> count = new LinkedHashMap<>();
        LinkedHashMap<String, Float> hrs = new LinkedHashMap<>();
        float hours = 0, pay = 0, ot = 0, to = 0, allow = 0;
        int work = 0, off = 0;
        for (String k : st.monthKeys(prof, a.y(), a.m())) {
            String raw = st.code(prof, k);
            ShiftType t = st.classify(raw);
            String label = t == null ? raw : t.name;
            count.put(label, (count.containsKey(label) ? count.get(label) : 0) + 1);
            if (t != null && t.off) off++;
            else if (t != null) {
                work++;
                hours += t.hours();
                pay += t.pay;
                hrs.put(label, (hrs.containsKey(label) ? hrs.get(label) : 0) + t.hours());
            }
        }
        if (prof.equals(Store.ME)) {
            Calendar c = Calendar.getInstance();
            c.clear();
            c.set(a.y(), a.m() - 1, 1);
            int max = c.getActualMaximum(Calendar.DAY_OF_MONTH);
            for (int d = 1; d <= max; d++) {
                String k = Store.key(a.y(), a.m(), d);
                ot += st.extra("ot", k);
                to += st.extra("to", k);
                allow += st.extra("allow", k);
            }
        }
        String cur = st.pref("currency", "NT$");
        StringBuilder s = new StringBuilder();
        s.append(st.profileName(prof)).append("\n上班 ").append(work).append(" 天，休假 ").append(off).append(" 天\n")
                .append(String.format(Locale.US, "工時合計 %.1f 小時\n", hours + ot - to));
        if (prof.equals(Store.ME))
            s.append(String.format(Locale.US, "（班表 %.1f＋加班 %.1f－借休 %.1f）\n", hours, ot, to))
                    .append(String.format(Locale.US, "薪資（含班別津貼）%s %.0f\n日津貼 %s %.0f\n合計 %s %.0f\n", cur, pay, cur, allow, cur, pay + allow));
        s.append("\n");
        for (Map.Entry<String, Integer> e : count.entrySet()) {
            s.append(e.getKey()).append("：").append(e.getValue()).append(" 天");
            if (hrs.containsKey(e.getKey())) s.append(String.format(Locale.US, "（%.1f 小時）", hrs.get(e.getKey())));
            s.append("\n");
        }
        new AlertDialog.Builder(a).setTitle(a.y() + " 年 " + a.m() + " 月統計").setMessage(s.toString())
                .setPositiveButton("關閉", null).show();
    }

    // ================================================================ 所有備註
    static void allNotes(final MainActivity a) {
        final List<String> keys = a.st.noteKeys(a.profile);
        if (keys.isEmpty()) { a.toast("列表是空的"); return; }
        String[] items = new String[keys.size()];
        for (int i = 0; i < items.length; i++) {
            String k = keys.get(i);
            items[i] = k + "　" + a.st.code(a.profile, k) + "\n" + a.st.note(a.profile, k);
        }
        new AlertDialog.Builder(a).setTitle("所有備註（" + a.st.profileName(a.profile) + "）").setItems(items, (d, w) -> {
            Calendar c = Store.parse(keys.get(w));
            if (c == null) return;
            a.shown = (Calendar) c.clone();
            a.selected = c;
            a.refresh();
            a.editDay(c);
        }).setPositiveButton("關閉", null).show();
    }

    // ================================================================ 特殊假日
    static void holidays(final MainActivity a) {
        final Store st = a.st;
        StringBuilder s = new StringBuilder("國定假日（離線計算，補假請自行加入）：\n");
        List<String> ks = new ArrayList<>(Holidays.year(a.y()).keySet());
        java.util.Collections.sort(ks);
        for (String k : ks) s.append(k.substring(5)).append(" ").append(Holidays.year(a.y()).get(k)).append("\n");
        List<String> cs = st.customHolidayKeys();
        s.append("\n自訂特殊假日：\n");
        if (cs.isEmpty()) s.append("（無，點月曆某天即可設定）\n");
        for (String k : cs) s.append(k).append(" ").append(st.customHoliday(k)).append("\n");
        LinearLayout box = Ui.box(a);
        Ui.label(box, s.toString());
        final CheckBox show = Ui.check(box, "於月曆顯示國定假日", st.flag("showHolidays", true));
        new AlertDialog.Builder(a).setTitle(a.y() + " 年特殊假日").setView(Ui.scroll(a, box))
                .setPositiveButton("確定", (d, w) -> { st.setFlag("showHolidays", show.isChecked()); a.refresh(); })
                .show();
    }

    // ================================================================ 發薪日
    static void payday(final MainActivity a) {
        final Store st = a.st;
        LinearLayout box = Ui.box(a);
        final String[] modes = {"none", "day", "period"};
        final Spinner mode = new Spinner(a);
        mode.setAdapter(new ArrayAdapter<>(a, android.R.layout.simple_spinner_dropdown_item,
                new String[]{"無自動重覆", "（自動重覆）每月固定日期", "（自動重覆）固定週期"}));
        mode.setSelection(java.util.Arrays.asList(modes).indexOf(st.pref("payMode", "none")));
        box.addView(mode);
        final EditText day = Ui.field(box, "每月的發薪日", String.valueOf(st.num("payDay", 5)), InputType.TYPE_CLASS_NUMBER);
        final EditText start = Ui.field(box, "固定週期的起始日 yyyy-MM-dd", st.pref("payStart", Store.key(a.selected)), InputType.TYPE_CLASS_DATETIME);
        final EditText period = Ui.field(box, "週期（天）", String.valueOf(st.num("payPeriod", 14)), InputType.TYPE_CLASS_NUMBER);
        final EditText cur = Ui.field(box, "貨幣單位", st.pref("currency", "NT$"), InputType.TYPE_CLASS_TEXT);
        new AlertDialog.Builder(a).setTitle("發薪日圖示 $").setView(Ui.scroll(a, box))
                .setPositiveButton("儲存", (d, w) -> {
                    st.setPref("payMode", modes[mode.getSelectedItemPosition()]);
                    st.setNum("payDay", Math.min(31, Math.max(1, Ui.i(day, 5))));
                    if (Store.parse(start.getText().toString().trim()) != null) st.setPref("payStart", start.getText().toString().trim());
                    st.setNum("payPeriod", Math.max(1, Ui.i(period, 14)));
                    st.setPref("currency", cur.getText().toString().trim());
                    a.refresh();
                }).setNegativeButton("取消", null).show();
    }

    // ================================================================ 朋友管理
    static void friends(final MainActivity a) {
        final Store st = a.st;
        final List<String> ids = st.friendIds();
        String[] items = new String[ids.size() + 4];
        items[0] = "＋ 新增／編輯離線好友（手動輸入班表）";
        items[1] = "＋ 從病房助理工作站匯入同事班表";
        items[2] = "班表改名（第一～第五班表）";
        items[3] = "清除某一份班表的所有資料";
        for (int i = 0; i < ids.size(); i++) items[i + 4] = "刪除好友：" + st.profileName(ids.get(i));
        new AlertDialog.Builder(a).setTitle("班表管理（上方下拉選單可切換）").setItems(items, (d, w) -> {
            if (w == 0) {
                final EditText et = new EditText(a);
                et.setHint("請輸入朋友的名字");
                new AlertDialog.Builder(a).setTitle("新增一位好友").setView(et)
                        .setPositiveButton("加入", (d2, w2) -> {
                            String n = et.getText().toString().trim();
                            if (n.isEmpty()) return;
                            a.profile = st.addFriend(n);
                            st.save();
                            a.refresh();
                            a.toast("離線好友列表已更新，現在可以點月曆編輯「" + n + "」的班表");
                        }).setNegativeButton("取消", null).show();
            } else if (w == 1) {
                importFriendsFromWard(a);
            } else if (w == 2 || w == 3) {
                final boolean rename = w == 2;
                String[] jobs = new String[Store.JOBS.length];
                for (int i = 0; i < jobs.length; i++) jobs[i] = Store.JOB_NAMES[i] + "：" + st.profileName(Store.JOBS[i]);
                new AlertDialog.Builder(a).setTitle(rename ? "要改名的班表" : "要清除的班表").setItems(jobs, (d2, w2) -> {
                    final String id = Store.JOBS[w2];
                    if (rename) {
                        final EditText et = new EditText(a);
                        et.setText(st.profileName(id));
                        new AlertDialog.Builder(a).setTitle("班表名稱（例如：醫院、兼職、家人）").setView(et)
                                .setPositiveButton("儲存", (d3, w3) -> { st.renameProfile(id, et.getText().toString()); st.save(); a.refresh(); })
                                .setNegativeButton("取消", null).show();
                    } else {
                        new AlertDialog.Builder(a).setMessage("刪除「" + st.profileName(id) + "」的所有資料（班表、備註）？")
                                .setPositiveButton("刪除", (d3, w3) -> { st.removeProfile(id); st.save(); a.refresh(); })
                                .setNegativeButton("取消", null).show();
                    }
                }).show();
            } else {
                final String id = ids.get(w - 4);
                new AlertDialog.Builder(a).setMessage("刪除好友「" + st.profileName(id) + "」和他的班表？")
                        .setPositiveButton("刪除", (d2, w2) -> { st.removeProfile(id); st.save(); a.refresh(); })
                        .setNegativeButton("取消", null).show();
            }
        }).show();
    }

    // ================================================================ 傳送班表（文字／螢幕截圖）
    static void share(final MainActivity a) {
        new AlertDialog.Builder(a).setTitle("選擇傳送的形式").setItems(new String[]{"純文字", "班表螢幕截圖"}, (d, w) -> {
            if (w == 0) {
                StringBuilder s = new StringBuilder();
                s.append(a.st.profileName(a.profile)).append(" ").append(a.y()).append(" 年 ").append(a.m()).append(" 月份班表：\n");
                for (String k : a.st.monthKeys(a.profile, a.y(), a.m())) {
                    Calendar c = Store.parse(k);
                    s.append(k.substring(5)).append("（").append(XiaoKeServer.WEEK[c.get(Calendar.DAY_OF_WEEK) - 1]).append("）")
                            .append(a.st.describe(a.profile, k)).append("\n");
                }
                s.append("\n— 小柯班表助手");
                a.startActivity(Intent.createChooser(new Intent(Intent.ACTION_SEND).setType("text/plain")
                        .putExtra(Intent.EXTRA_TEXT, s.toString()), "傳送文字班表，透過："));
            } else shareImage(a);
        }).show();
    }

    private static void shareImage(MainActivity a) {
        if (Build.VERSION.SDK_INT < 29) { a.toast("螢幕截圖無法產生。您可以使用手機內建的螢幕截圖功能。"); return; }
        try {
            int w = 1080, gridH = 1150, head = 120;
            MonthView mv = new MonthView(a);
            mv.setProfile(a.profile);
            mv.setMonth(a.y(), a.m() - 1);
            mv.setSelected("");
            Bitmap grid = mv.render(w, gridH);
            Bitmap out = Bitmap.createBitmap(w, gridH + head, Bitmap.Config.ARGB_8888);
            Canvas cv = new Canvas(out);
            cv.drawColor(a.st.flag("dark", false) ? 0xFF101522 : 0xFFF1F3F6);
            Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
            p.setTextSize(54);
            p.setFakeBoldText(true);
            p.setColor(a.st.flag("dark", false) ? Color.WHITE : 0xFF101522);
            cv.drawText(a.st.profileName(a.profile) + "　" + a.y() + " 年 " + a.m() + " 月", 40, 80, p);
            cv.drawBitmap(grid, 0, head, null);
            ContentValues v = new ContentValues();
            v.put(MediaStore.Images.Media.DISPLAY_NAME, "小柯班表_" + a.y() + "-" + a.m() + ".png");
            v.put(MediaStore.Images.Media.MIME_TYPE, "image/png");
            v.put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/小柯班表");
            Uri uri = a.getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, v);
            try (OutputStream os = a.getContentResolver().openOutputStream(uri)) { out.compress(Bitmap.CompressFormat.PNG, 100, os); }
            a.startActivity(Intent.createChooser(new Intent(Intent.ACTION_SEND).setType("image/png")
                    .putExtra(Intent.EXTRA_STREAM, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION), "傳送螢幕截圖班表，透過："));
        } catch (Exception e) {
            a.toast("螢幕截圖無法產生：" + e.getMessage());
        }
    }

    // ================================================================ 設定
    static void settings(final MainActivity a) {
        final Store st = a.st;
        LinearLayout box = Ui.box(a);
        Ui.label(box, "【月曆設定】");
        final Spinner dow = spinner(a, box, "每週的第一天", new String[]{"星期日", "星期一"}, st.num("firstDow", Calendar.SUNDAY) == Calendar.MONDAY ? 1 : 0);
        final Spinner today = spinner(a, box, "「今日提示」的樣式", new String[]{"外框", "被圓圈起來的字", "不需要特別提示「今日」"},
                java.util.Arrays.asList("border", "circle", "none").indexOf(st.pref("todayStyle", "border")));
        final Spinner font = spinner(a, box, "月曆上的字型大小", new String[]{"小", "正常", "大"},
                st.num("fontScale", 100) < 100 ? 0 : st.num("fontScale", 100) > 100 ? 2 : 1);
        final CheckBox week = Ui.check(box, "顯示週數", st.flag("weekNumber", false));
        final CheckBox red = Ui.check(box, "週末與假日紅色字體", st.flag("weekendRed", true));
        final CheckBox time = Ui.check(box, "在月曆上顯示班表開始時間", st.flag("showTime", false));
        final CheckBox hideOff = Ui.check(box, "月曆上不要顯示「休假」", st.flag("hideOff", false));
        final CheckBox hol = Ui.check(box, "於月曆顯示假日資訊", st.flag("showHolidays", true));
        final CheckBox dots = Ui.check(box, "於月曆顯示 Google 行事曆事件（藍點）", st.flag("eventDots", false));
        final CheckBox bigDot = Ui.check(box, "「備註點」大一點", st.flag("bigNoteDot", false));
        final Spinner noteColor = spinner(a, box, "「備註點」的顏色", new String[]{"紅色", "藍色", "綠色", "橘色", "粉紅"},
                java.util.Arrays.asList("#E53935", "#1E88E5", "#43A047", "#FB8C00", "#D81B60").indexOf(st.pref("noteColor", "#E53935")));
        Ui.label(box, "\n【介面設定】");
        final CheckBox dark = Ui.check(box, "黑色佈景（深色模式）", st.flag("dark", false));
        final Spinner orient = spinner(a, box, "螢幕旋轉設定", new String[]{"根據系統設定", "永遠保持直立", "永遠保持橫擺"},
                java.util.Arrays.asList("auto", "portrait", "landscape").indexOf(st.pref("orientation", "auto")));
        final CheckBox wbtn = Ui.check(box, "於月曆「小工具」上顯示按鈕", st.flag("widgetButtons", true));
        Ui.label(box, "\n【系統設定】");
        final CheckBox del = Ui.check(box, "自動清除三個月前的舊班表", st.flag("autoDelete", false));
        final EditText pw = Ui.field(box, "設定密碼（數字；留空＝不變；輸入 0000 清除）", "",
                InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        new AlertDialog.Builder(a).setTitle("設定").setView(Ui.scroll(a, box))
                .setPositiveButton("儲存", (d, w) -> {
                    boolean themeChanged = dark.isChecked() != st.flag("dark", false);
                    st.setNum("firstDow", dow.getSelectedItemPosition() == 1 ? Calendar.MONDAY : Calendar.SUNDAY);
                    st.setPref("todayStyle", new String[]{"border", "circle", "none"}[today.getSelectedItemPosition()]);
                    st.setNum("fontScale", new int[]{85, 100, 120}[font.getSelectedItemPosition()]);
                    st.setFlag("weekNumber", week.isChecked());
                    st.setFlag("weekendRed", red.isChecked());
                    st.setFlag("showTime", time.isChecked());
                    st.setFlag("hideOff", hideOff.isChecked());
                    st.setFlag("showHolidays", hol.isChecked());
                    st.setFlag("eventDots", dots.isChecked());
                    st.setFlag("bigNoteDot", bigDot.isChecked());
                    st.setPref("noteColor", new String[]{"#E53935", "#1E88E5", "#43A047", "#FB8C00", "#D81B60"}[Math.max(0, noteColor.getSelectedItemPosition())]);
                    st.setFlag("dark", dark.isChecked());
                    st.setPref("orientation", new String[]{"auto", "portrait", "landscape"}[Math.max(0, orient.getSelectedItemPosition())]);
                    st.setFlag("widgetButtons", wbtn.isChecked());
                    st.setFlag("autoDelete", del.isChecked());
                    String p = pw.getText().toString().trim();
                    if (p.equals("0000")) { st.setPref("password", ""); a.toast("密碼已清除"); }
                    else if (!p.isEmpty()) { st.setPref("password", MainActivity.hash(p)); a.toast("密碼設定成功"); }
                    if (dots.isChecked()) a.withCalPermission(() -> { });
                    if (themeChanged) a.recreate(); else a.refresh();
                }).setNegativeButton("取消", null).show();
    }

    private static Spinner spinner(MainActivity a, LinearLayout box, String label, String[] items, int sel) {
        Ui.label(box, label);
        Spinner s = new Spinner(a);
        s.setAdapter(new ArrayAdapter<>(a, android.R.layout.simple_spinner_dropdown_item, items));
        s.setSelection(Math.max(0, sel));
        box.addView(s);
        return s;
    }

    // ================================================================ 使用說明
    static void help(MainActivity a) {
        new AlertDialog.Builder(a).setTitle("小柯班表助手 使用提示").setMessage(
                "(1) 點月曆某一天可排班、寫備註、記加班／借休／日津貼，並看農民曆與 Google 行事曆行程。\n\n"
                        + "(2) 在月曆上按住並滑過一段日期（左右滑，或按住半秒後任意方向拖），放開即可一次設定這些日期的班別。「批次編輯」可點選多天，或複製成班表樣式再貼上。\n\n"
                        + "(3) 「班表樣式」可設定循環班（例如 D,D,E,E,N,N,OFF,OFF）並「自動重覆」。\n\n"
                        + "(4) 「匯入病房助理工作站月班表」直接讀月班表系統；代號規則與月班表系統相同（D 白班、E 小夜、N 大夜、OFF／國／休 休假、例 例假）。\n\n"
                        + "(5) 「同步到 Google 行事曆」寫入手機上的 Google 帳號行事曆，會自動同步到雲端。\n\n"
                        + "(6) 「小柯機器人連線」讓同一個 Wi-Fi 的小柯查詢班表；鬧鐘關掉後小柯會唸出今天的班。\n\n"
                        + "(7) 上方下拉選單可切換五份班表（我的、第二～第五班表，可改名）與好友。\n\n"
                        + "(8) 桌面可加入「小柯今日班表」與「小柯月班表」兩種小工具。")
                .setPositiveButton("知道了", null).show();
    }
}
