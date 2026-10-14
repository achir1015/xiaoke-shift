package com.achir.xiaokeshift;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

/**
 * 班表樣式（原版 ShiftPattern）：一串循環的班別代號，從 start 日起算。
 * auto=true 時在 from～to 期間「自動重覆」：那幾天沒手動排班就顯示樣式的班。
 */
final class Pattern {
    String name, profile = Store.ME, start, from = "", to = "";
    final List<String> seq = new ArrayList<>();
    boolean auto;

    String codeOn(String key) {
        if (!auto || seq.isEmpty() || start == null) return "";
        if (!from.isEmpty() && key.compareTo(from) < 0) return "";
        if (!to.isEmpty() && key.compareTo(to) > 0) return "";
        if (key.compareTo(start) < 0) return "";
        long d = Store.daysBetween(start, key);
        return seq.get((int) (d % seq.size()));
    }

    String seqText() {
        StringBuilder s = new StringBuilder();
        for (String c : seq) s.append(s.length() == 0 ? "" : ",").append(c);
        return s.toString();
    }

    void setSeq(String text) {
        seq.clear();
        for (String c : text.replace('，', ',').split(",")) if (!c.trim().isEmpty()) seq.add(c.trim());
    }

    /** 從 start 日起貼上 days 天（覆蓋）。 */
    void paste(Store s, String startKey, int days) {
        Calendar c = Store.parse(startKey);
        if (c == null || seq.isEmpty()) return;
        for (int i = 0; i < days; i++) {
            s.setCode(profile, Store.key(c), seq.get(i % seq.size()));
            c.add(Calendar.DAY_OF_MONTH, 1);
        }
    }

    JSONObject toJson() throws JSONException {
        JSONArray a = new JSONArray();
        for (String c : seq) a.put(c);
        return new JSONObject().put("name", name).put("profile", profile).put("start", start)
                .put("from", from).put("to", to).put("auto", auto).put("seq", a);
    }

    static Pattern fromJson(JSONObject o) {
        Pattern p = new Pattern();
        p.name = o.optString("name", "樣式");
        p.profile = o.optString("profile", Store.ME);
        p.start = o.optString("start", Store.key(Calendar.getInstance()));
        p.from = o.optString("from", "");
        p.to = o.optString("to", "");
        p.auto = o.optBoolean("auto");
        JSONArray a = o.optJSONArray("seq");
        if (a != null) for (int i = 0; i < a.length(); i++) p.seq.add(a.optString(i));
        return p;
    }
}
