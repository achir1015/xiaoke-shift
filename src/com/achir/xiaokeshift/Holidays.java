package com.achir.xiaokeshift;

import java.util.Calendar;
import java.util.HashMap;
import java.util.Map;

/**
 * 特殊假日（原版 HolidayActivity 要上網下載；這裡離線計算台灣國定假日＋使用者自訂）。
 * 國曆：元旦、和平紀念日、兒童節、清明（民族掃墓節）、勞動節、教師節、國慶日、光復節、行憲紀念日。
 * 農曆：除夕、春節（初一～初三）、端午、中秋（用 Almanac 的農曆表換算）。
 * 補假依行政院人事行政總處每年公告，未自動計入，可用「自訂假日」補上。
 */
final class Holidays {
    private static final Map<Integer, Map<String, String>> cache = new HashMap<>();

    static synchronized Map<String, String> year(int y) {
        Map<String, String> m = cache.get(y);
        if (m != null) return m;
        m = new HashMap<>();
        m.put(Store.key(y, 1, 1), "元旦");
        m.put(Store.key(y, 2, 28), "和平紀念日");
        m.put(Store.key(y, 4, 4), "兒童節");
        m.put(Store.key(y, 5, 1), "勞動節");
        m.put(Store.key(y, 9, 28), "教師節");
        m.put(Store.key(y, 10, 10), "國慶日");
        m.put(Store.key(y, 10, 25), "光復節");
        m.put(Store.key(y, 12, 25), "行憲紀念日");
        Calendar c = Calendar.getInstance();
        c.clear();
        c.set(y, 0, 1);
        String prev = null;
        while (c.get(Calendar.YEAR) == y) {
            String k = Store.key(c), lunar = Almanac.lunarString(c);
            if (lunar.equals("正月初一")) {
                m.put(k, "春節");
                if (prev != null) m.put(prev, "除夕");
            } else if (lunar.equals("正月初二") || lunar.equals("正月初三")) m.put(k, "春節");
            else if (lunar.equals("五月初五")) m.put(k, "端午節");
            else if (lunar.equals("八月十五")) m.put(k, "中秋節");
            if (Almanac.cellText(c).equals("清明")) m.put(k, m.containsKey(k) ? m.get(k) + "、清明" : "清明節");
            prev = k;
            c.add(Calendar.DAY_OF_MONTH, 1);
        }
        cache.put(y, m);
        return m;
    }

    /** 某天的假日名稱（自訂優先），沒有就回空字串。 */
    static String name(Store s, String key) {
        String custom = s.customHoliday(key);
        if (!custom.isEmpty()) return custom;
        if (!s.flag("showHolidays", true)) return "";
        try {
            String n = year(Integer.parseInt(key.substring(0, 4))).get(key);
            return n == null ? "" : n;
        } catch (Exception e) { return ""; }
    }

    /** 發薪日：每月固定日期，或從起始日起固定週期。 */
    static boolean isPayday(Store s, String key) {
        String mode = s.pref("payMode", "none");
        if (mode.equals("day")) {
            Calendar c = Store.parse(key);
            if (c == null) return false;
            int d = Math.min(s.num("payDay", 5), c.getActualMaximum(Calendar.DAY_OF_MONTH));
            return c.get(Calendar.DAY_OF_MONTH) == d;
        }
        if (mode.equals("period")) {
            String start = s.pref("payStart", "");
            int period = Math.max(1, s.num("payPeriod", 14));
            if (start.isEmpty() || key.compareTo(start) < 0) return false;
            return Store.daysBetween(start, key) % period == 0;
        }
        return false;
    }
}
