package com.achir.xiaokeshift;

import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.provider.CalendarContract;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.TimeZone;

/**
 * Google 行事曆：透過系統 CalendarContract 讀寫手機上已登入的 Google 帳號行事曆，
 * 由系統自動同步到 Google（不需 OAuth / API 金鑰）。
 * 本 App 寫入的事件在描述加 TAG，重新同步時先刪同月份帶 TAG 的事件再寫入，不會動到其他行程。
 */
final class GCalSync {
    static final String TAG = "#小柯班表";

    static final class Cal {
        long id;
        String name, account;
        public String toString() { return name + "（" + account + "）"; }
    }

    /** 可寫入的行事曆，Google 帳號排前面。 */
    static List<Cal> calendars(ContentResolver cr) {
        List<Cal> out = new ArrayList<>();
        String[] proj = {CalendarContract.Calendars._ID, CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
                CalendarContract.Calendars.ACCOUNT_NAME, CalendarContract.Calendars.ACCOUNT_TYPE};
        String sel = CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL + ">=" + CalendarContract.Calendars.CAL_ACCESS_CONTRIBUTOR;
        try (Cursor c = cr.query(CalendarContract.Calendars.CONTENT_URI, proj, sel, null, null)) {
            if (c == null) return out;
            while (c.moveToNext()) {
                Cal k = new Cal();
                k.id = c.getLong(0);
                k.name = c.getString(1);
                k.account = c.getString(2);
                if ("com.google".equals(c.getString(3))) out.add(0, k); else out.add(k);
            }
        }
        return out;
    }

    private static long millis(Calendar day, int minutes) {
        Calendar c = (Calendar) day.clone();
        c.set(Calendar.HOUR_OF_DAY, 0);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        return c.getTimeInMillis() + minutes * 60000L;
    }

    /** 把某月班表寫入行事曆，回傳寫入筆數。在背景執行緒呼叫。 */
    static int syncMonth(ContentResolver cr, Store s, long calId, int y, int m) {
        Calendar first = Calendar.getInstance();
        first.clear();
        first.set(y, m - 1, 1);
        Calendar next = (Calendar) first.clone();
        next.add(Calendar.MONTH, 1);

        // 1. 刪掉本月舊的（只刪帶 TAG 的）
        String where = CalendarContract.Events.CALENDAR_ID + "=? AND " + CalendarContract.Events.DTSTART + ">=? AND "
                + CalendarContract.Events.DTSTART + "<? AND " + CalendarContract.Events.DESCRIPTION + " LIKE ?";
        // 全天事件以 UTC 午夜存放，範圍放寬一天
        cr.delete(CalendarContract.Events.CONTENT_URI, where, new String[]{String.valueOf(calId),
                String.valueOf(first.getTimeInMillis() - 86400000L), String.valueOf(next.getTimeInMillis() + 86400000L), "%" + TAG + "%"});

        // 2. 逐日寫入
        int n = 0;
        String tz = TimeZone.getDefault().getID();
        for (String k : s.monthKeys(Store.ME, y, m)) {
            String raw = s.code(Store.ME, k);
            Calendar day = Store.parse(k);
            if (day == null) continue;
            ShiftType t = s.classify(raw);
            ContentValues v = new ContentValues();
            v.put(CalendarContract.Events.CALENDAR_ID, calId);
            String title = t == null ? raw : t.name + (raw.equalsIgnoreCase(t.code) ? "" : "（" + raw + "）");
            v.put(CalendarContract.Events.TITLE, "【班】" + title);
            String note = s.note(Store.ME, k);
            v.put(CalendarContract.Events.DESCRIPTION, (note.isEmpty() ? "" : note + "\n") + TAG);
            if (t == null || t.off) {
                Calendar u = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
                u.clear();
                u.set(day.get(Calendar.YEAR), day.get(Calendar.MONTH), day.get(Calendar.DAY_OF_MONTH));
                v.put(CalendarContract.Events.ALL_DAY, 1);
                v.put(CalendarContract.Events.EVENT_TIMEZONE, "UTC");
                v.put(CalendarContract.Events.DTSTART, u.getTimeInMillis());
                v.put(CalendarContract.Events.DTEND, u.getTimeInMillis() + 86400000L);
            } else {
                int st = ShiftType.minutes(t.start), en = ShiftType.minutes(t.end);
                if (en <= st) en += 1440;
                v.put(CalendarContract.Events.EVENT_TIMEZONE, tz);
                v.put(CalendarContract.Events.DTSTART, millis(day, st));
                v.put(CalendarContract.Events.DTEND, millis(day, en));
            }
            if (cr.insert(CalendarContract.Events.CONTENT_URI, v) != null) n++;
        }
        return n;
    }

    /** 某天在所有行事曆上的行程（不含本 App 寫入的班），給日期詳細頁顯示。 */
    static List<String> eventsOn(ContentResolver cr, Calendar day) {
        List<String> out = new ArrayList<>();
        Uri.Builder b = CalendarContract.Instances.CONTENT_URI.buildUpon();
        ContentUris.appendId(b, millis(day, 0));
        ContentUris.appendId(b, millis(day, 1440) - 1);
        String[] proj = {CalendarContract.Instances.TITLE, CalendarContract.Instances.BEGIN,
                CalendarContract.Instances.ALL_DAY, CalendarContract.Instances.DESCRIPTION};
        try (Cursor c = cr.query(b.build(), proj, null, null, CalendarContract.Instances.BEGIN)) {
            if (c == null) return out;
            Calendar t = Calendar.getInstance();
            while (c.moveToNext()) {
                String desc = c.getString(3);
                if (desc != null && desc.contains(TAG)) continue;
                String time = "全天";
                if (c.getInt(2) == 0) {
                    t.setTimeInMillis(c.getLong(1));
                    time = String.format(java.util.Locale.US, "%02d:%02d", t.get(Calendar.HOUR_OF_DAY), t.get(Calendar.MINUTE));
                }
                out.add(time + " " + c.getString(0));
            }
        } catch (SecurityException ignored) { }
        return out;
    }
}
