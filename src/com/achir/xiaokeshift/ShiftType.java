package com.achir.xiaokeshift;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * 班別：代號、名稱、顏色、上下班時間。off=true 表示休假類（不算工時、行事曆用全天事件）。
 * 另含原版「班表小幫手」的班別薪資、班別說明、是否顯示於選擇清單、鬧鐘（可設前一天響）。
 */
final class ShiftType {
    String code, name, start, end, desc = "";
    int color;
    boolean off, hidden;
    float pay;                       // 班別薪資（含班別津貼）
    boolean alarmOn, alarmBefore;    // alarmBefore：鬧鐘在前一天響（例如大夜 00:00 上班，前一天 23:00 叫）
    String alarmTime = "06:30";

    ShiftType(String code, String name, int color, String start, String end, boolean off) {
        this.code = code; this.name = name; this.color = color;
        this.start = start; this.end = end; this.off = off;
    }

    static int minutes(String hhmm) {
        try {
            String[] p = hhmm.split(":");
            return Integer.parseInt(p[0].trim()) * 60 + Integer.parseInt(p[1].trim());
        } catch (Exception e) { return -1; }
    }

    /** 工時（小時）；下班時間 ≤ 上班時間視為跨夜。 */
    float hours() {
        if (off) return 0;
        int s = minutes(start), e = minutes(end);
        if (s < 0 || e < 0) return 0;
        if (e <= s) e += 1440;
        return (e - s) / 60f;
    }

    String timeText() { return off ? "全天" : start + "–" + end; }

    JSONObject toJson() throws JSONException {
        return new JSONObject().put("code", code).put("name", name).put("color", color)
                .put("start", start).put("end", end).put("off", off).put("desc", desc).put("hidden", hidden)
                .put("pay", pay).put("alarmOn", alarmOn).put("alarmBefore", alarmBefore).put("alarmTime", alarmTime);
    }

    static ShiftType fromJson(JSONObject o) {
        ShiftType t = new ShiftType(o.optString("code"), o.optString("name"), o.optInt("color", 0xFF9E9E9E),
                o.optString("start", "08:00"), o.optString("end", "16:00"), o.optBoolean("off"));
        t.desc = o.optString("desc", "");
        t.hidden = o.optBoolean("hidden");
        t.pay = (float) o.optDouble("pay", 0);
        t.alarmOn = o.optBoolean("alarmOn");
        t.alarmBefore = o.optBoolean("alarmBefore");
        t.alarmTime = o.optString("alarmTime", "06:30");
        return t;
    }
}
