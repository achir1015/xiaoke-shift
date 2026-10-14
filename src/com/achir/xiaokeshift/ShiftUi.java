package com.achir.xiaokeshift;

import android.app.AlertDialog;
import android.text.InputType;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;

import java.util.Calendar;
import java.util.List;

/** 班別設定與班表樣式（原版 新增/刪除班別、ShiftPatternActivity、PatternEditActivity、複製/貼上）。 */
final class ShiftUi {
    private ShiftUi() { }

    // ================================================================ 班別
    static void editTypes(final MainActivity a) {
        final Store st = a.st;
        final String[] items = new String[st.types.size() + 1];
        for (int i = 0; i < st.types.size(); i++) {
            ShiftType t = st.types.get(i);
            items[i] = t.code + "　" + t.name + "　" + t.timeText() + (t.alarmOn ? "　⏰" + t.alarmTime : "") + (t.hidden ? "　（隱藏）" : "");
        }
        items[items.length - 1] = "＋ 新增班別";
        new AlertDialog.Builder(a).setTitle("班別設定（點一下編輯）")
                .setItems(items, (d, w) -> editType(a, w < st.types.size() ? st.types.get(w) : null))
                .setPositiveButton("完成", null).show();
    }

    static void editType(final MainActivity a, final ShiftType orig) {
        final Store st = a.st;
        LinearLayout box = Ui.box(a);
        final EditText code = Ui.field(box, "代號（例如 D；工作站匯入的代號會依規則自動歸類）", orig == null ? "" : orig.code, InputType.TYPE_CLASS_TEXT);
        final EditText name = Ui.field(box, "名稱（例如 白班）", orig == null ? "" : orig.name, InputType.TYPE_CLASS_TEXT);
        final EditText start = Ui.field(box, "開始時間 HH:mm", orig == null ? "08:00" : orig.start, Ui.TIME);
        final EditText end = Ui.field(box, "結束時間 HH:mm（跨夜可小於開始）", orig == null ? "16:00" : orig.end, Ui.TIME);
        final CheckBox off = Ui.check(box, "休假類（不算工時，行事曆用全天事件）", orig != null && orig.off);
        final EditText pay = Ui.field(box, "班別薪資（含班別津貼，統計用）", orig == null ? "" : MainActivity.num(orig.pay), Ui.DECIMAL);
        final EditText desc = Ui.field(box, "班別備註／描述", orig == null ? "" : orig.desc, InputType.TYPE_CLASS_TEXT);
        final CheckBox hidden = Ui.check(box, "不顯示於「班別選擇」按鈕", orig != null && orig.hidden);
        Ui.label(box, "顏色");
        final int[] color = {orig == null ? Ui.PALETTE[st.types.size() % Ui.PALETTE.length] : orig.color};
        box.addView(Ui.palette(a, color));
        Ui.label(box, "\n⏰ 鬧鐘（這個班別的日子會響）");
        final CheckBox alarm = Ui.check(box, "啟用鬧鐘", orig != null && orig.alarmOn);
        final EditText atime = Ui.field(box, "鬧鐘時間 HH:mm", orig == null ? "06:30" : orig.alarmTime, Ui.TIME);
        final CheckBox before = Ui.check(box, "前日響（例：大夜 00:00 上班，鬧鐘 23:00 在前一天響）", orig != null && orig.alarmBefore);

        AlertDialog.Builder b = new AlertDialog.Builder(a).setTitle(orig == null ? "新增班別" : "編輯班別").setView(Ui.scroll(a, box))
                .setPositiveButton("儲存", (d, w) -> {
                    String cd = code.getText().toString().trim();
                    if (cd.isEmpty()) { a.toast("代號不能空白"); return; }
                    if (ShiftType.minutes(atime.getText().toString()) < 0 && alarm.isChecked()) { a.toast("鬧鐘時間格式應為 HH:mm"); return; }
                    ShiftType t = orig != null ? orig : new ShiftType(cd, "", 0, "", "", false);
                    t.code = cd;
                    t.name = name.getText().toString().trim().isEmpty() ? cd : name.getText().toString().trim();
                    t.start = start.getText().toString().trim();
                    t.end = end.getText().toString().trim();
                    t.off = off.isChecked();
                    t.pay = Ui.f(pay);
                    t.desc = desc.getText().toString().trim();
                    t.hidden = hidden.isChecked();
                    t.color = color[0];
                    t.alarmOn = alarm.isChecked();
                    t.alarmTime = atime.getText().toString().trim();
                    t.alarmBefore = before.isChecked();
                    if (orig == null) st.types.add(t);
                    st.save();
                    a.refresh();
                    if (t.alarmOn) a.toast(AlarmScheduler.schedule(a));
                })
                .setNegativeButton("取消", null);
        if (orig != null) {
            b.setNeutralButton("刪除／上移", (d, w) -> new AlertDialog.Builder(a).setTitle(orig.code + " " + orig.name)
                    .setItems(new String[]{"上移（改變順序）", "刪除這個班別"}, (d2, w2) -> {
                        int i = st.types.indexOf(orig);
                        if (w2 == 0 && i > 0) {
                            st.types.remove(i);
                            st.types.add(i - 1, orig);
                            st.save();
                            a.refresh();
                            editTypes(a);
                        } else if (w2 == 1) {
                            new AlertDialog.Builder(a).setTitle("刪除班別")
                                    .setMessage("已經排了「" + orig.code + "」的日子會改顯示灰色原代號。確定要刪除？")
                                    .setPositiveButton("刪除", (d3, w3) -> { st.types.remove(orig); st.save(); a.refresh(); })
                                    .setNegativeButton("取消", null).show();
                        }
                    }).show());
        }
        b.show();
    }

    // ================================================================ 班表樣式
    static void patterns(final MainActivity a) {
        final Store st = a.st;
        final String[] items = new String[st.patterns.size() + 1];
        for (int i = 0; i < st.patterns.size(); i++) {
            Pattern p = st.patterns.get(i);
            items[i] = p.name + "（" + p.seq.size() + " 天）" + (p.auto ? "　🔁自動重覆" : "") + "　" + st.profileName(p.profile);
        }
        items[items.length - 1] = "＋ 建立班表樣式";
        new AlertDialog.Builder(a).setTitle("班表樣式（點選可編輯／貼上）")
                .setItems(items, (d, w) -> editPattern(a, w < st.patterns.size() ? st.patterns.get(w) : null))
                .setPositiveButton("關閉", null).show();
    }

    static void editPattern(final MainActivity a, final Pattern orig) {
        final Store st = a.st;
        final Pattern p = orig != null ? orig : new Pattern();
        if (orig == null) {
            p.name = "我的班表樣式";
            p.profile = a.profile;
            p.start = Store.key(a.selected);
            p.setSeq(st.pref("pattern", "D,D,E,E,N,N,OFF,OFF"));
        }
        StringBuilder codes = new StringBuilder();
        for (ShiftType t : st.types) codes.append(t.code).append("=").append(t.name).append("　");
        LinearLayout box = Ui.box(a);
        final EditText name = Ui.field(box, "樣式名稱", p.name, InputType.TYPE_CLASS_TEXT);
        final EditText seq = Ui.field(box, "循環班別（逗號分隔）\n可用代號：" + codes, p.seqText(), InputType.TYPE_CLASS_TEXT);
        final EditText start = Ui.field(box, "樣式起算日 yyyy-MM-dd", p.start, InputType.TYPE_CLASS_DATETIME);
        final CheckBox auto = Ui.check(box, "🔁 自動重覆（期間內沒手動排班的日子自動套用）", p.auto);
        final EditText from = Ui.field(box, "自動重複起始日（空白＝從起算日）", p.from, InputType.TYPE_CLASS_DATETIME);
        final EditText to = Ui.field(box, "自動重複結束日（空白＝無限）", p.to, InputType.TYPE_CLASS_DATETIME);
        Ui.label(box, "套用到：" + st.profileName(p.profile));

        final Runnable save = () -> {
            p.name = name.getText().toString().trim().isEmpty() ? "未命名" : name.getText().toString().trim();
            p.setSeq(seq.getText().toString());
            if (Store.parse(start.getText().toString().trim()) != null) p.start = start.getText().toString().trim();
            p.auto = auto.isChecked();
            p.from = Store.parse(from.getText().toString().trim()) != null ? from.getText().toString().trim() : "";
            p.to = Store.parse(to.getText().toString().trim()) != null ? to.getText().toString().trim() : "";
            if (!st.patterns.contains(p)) st.patterns.add(p);
            st.setPref("pattern", p.seqText());
            if (p.auto && overlap(st, p)) a.toast("注意：至少兩個班表樣式的自動重複期間是重疊的");
            st.save();
            a.refresh();
        };
        AlertDialog.Builder b = new AlertDialog.Builder(a).setTitle(orig == null ? "建立班表樣式" : "編輯班表樣式")
                .setView(Ui.scroll(a, box))
                .setPositiveButton("儲存", (d, w) -> save.run())
                .setNeutralButton("貼上…", (d, w) -> { save.run(); paste(a, p); })
                .setNegativeButton("取消", null);
        b.show();
        if (orig != null)
            a.toast("「貼上…」可從選取日開始貼上；也可在那裡「重新讀取月曆上的班別」或刪除樣式");
    }

    private static boolean overlap(Store st, Pattern p) {
        for (Pattern q : st.patterns) {
            if (q == p || !q.auto || !q.profile.equals(p.profile)) continue;
            String a1 = p.from.isEmpty() ? p.start : p.from, a2 = p.to.isEmpty() ? "9999" : p.to;
            String b1 = q.from.isEmpty() ? q.start : q.from, b2 = q.to.isEmpty() ? "9999" : q.to;
            if (a1.compareTo(b2) <= 0 && b1.compareTo(a2) <= 0) return true;
        }
        return false;
    }

    private static void paste(final MainActivity a, final Pattern p) {
        final Store st = a.st;
        final String from = Store.key(a.selected);
        new AlertDialog.Builder(a).setTitle(p.name)
                .setItems(new String[]{"從 " + from + " 貼上 1 輪（" + p.seq.size() + " 天）",
                        "從 " + from + " 貼上到…（輸入結束日）",
                        "重新讀取月曆上的班別（從起算日 " + p.start + " 起 " + p.seq.size() + " 天）",
                        "刪除這個樣式"}, (d, w) -> {
                    if (w == 0) {
                        p.paste(st, from, p.seq.size());
                        st.save();
                        a.refresh();
                        a.toast("已貼上");
                    } else if (w == 1) {
                        LinearLayout box = Ui.box(a);
                        Calendar e = (Calendar) a.selected.clone();
                        e.add(Calendar.MONTH, 1);
                        final EditText end = Ui.field(box, "結束日 yyyy-MM-dd", Store.key(e), InputType.TYPE_CLASS_DATETIME);
                        new AlertDialog.Builder(a).setTitle("貼上班表樣式").setView(box)
                                .setPositiveButton("貼上", (d2, w2) -> {
                                    String ek = end.getText().toString().trim();
                                    if (Store.parse(ek) == null || ek.compareTo(from) < 0) { a.toast("結束日不正確"); return; }
                                    p.paste(st, from, (int) Store.daysBetween(from, ek) + 1);
                                    st.save();
                                    a.refresh();
                                    a.toast("已貼上 " + from + " ～ " + ek);
                                }).setNegativeButton("取消", null).show();
                    } else if (w == 2) {
                        Calendar c = Store.parse(p.start);
                        if (c == null) return;
                        int n = p.seq.size();
                        p.seq.clear();
                        for (int i = 0; i < n; i++) {
                            String code = st.explicit(p.profile, Store.key(c));
                            p.seq.add(code.isEmpty() ? "OFF" : code);
                            c.add(Calendar.DAY_OF_MONTH, 1);
                        }
                        st.save();
                        a.toast("已用月曆上的班別更新樣式：" + p.seqText());
                    } else {
                        st.patterns.remove(p);
                        st.save();
                        a.refresh();
                    }
                }).show();
    }

    /** 批次編輯選取的日子 → 複製成樣式（原版「複製/貼上功能」）。 */
    static void copyAsPattern(final MainActivity a, List<String> keys) {
        Pattern p = new Pattern();
        p.profile = a.profile;
        String first = keys.get(0), last = keys.get(keys.size() - 1);
        p.start = first;
        Calendar c = Store.parse(first);
        int n = (int) Store.daysBetween(first, last) + 1;
        for (int i = 0; i < n && c != null; i++) {
            String code = a.st.explicit(a.profile, Store.key(c));
            p.seq.add(code.isEmpty() ? "OFF" : code);
            c.add(Calendar.DAY_OF_MONTH, 1);
        }
        p.name = n + " 天的班表樣式（" + first.substring(5) + "～" + last.substring(5) + "）";
        a.st.patterns.add(p);
        a.st.save();
        a.toast("已複製成「" + p.name + "」。選一個起始日後，到「班表樣式」點它 →「貼上…」");
    }
}
