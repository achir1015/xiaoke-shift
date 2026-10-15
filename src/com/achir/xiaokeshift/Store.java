package com.achir.xiaokeshift;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;

/**
 * 班表資料（SharedPreferences 存 JSON）。
 * 多份班表（profile）：me＝我的班表、job2＝第二班表、f:姓名＝好友（離線好友或從工作站匯入的同事）。
 *   data[profile].days：日期 "yyyy-MM-dd" → 班別代號原文（從工作站匯入的保留原代號，如「D/洗」）
 *   data[profile].notes：日期 → 備註
 *   extra.ot / extra.to / extra.allow：我的加班時數、借休時數、日津貼
 *   holidays：自訂特殊假日（日期 → 名稱）
 * 原代號用 classify() 歸到班別，規則與病房助理工作站月班表系統相同。
 */
final class Store {
    static final String ME = "me", JOB2 = "job2";
    /** 五份自己的班表（原版第一／第二班表擴充成五份），其餘 profile 是好友。 */
    static final String[] JOBS = {"me", "job2", "job3", "job4", "job5"};
    static final String[] JOB_NAMES = {"我的班表", "第二班表", "第三班表", "第四班表", "第五班表"};

    static boolean isJob(String id) { return java.util.Arrays.asList(JOBS).contains(id); }
    private static Store inst;
    private final SharedPreferences sp;
    final List<ShiftType> types = new ArrayList<>();
    final List<Pattern> patterns = new ArrayList<>();
    private JSONObject data, extra, holidays, profiles;

    static synchronized Store get(Context c) {
        if (inst == null) inst = new Store(c.getApplicationContext());
        return inst;
    }

    private Store(Context c) {
        sp = c.getSharedPreferences("shift", Context.MODE_PRIVATE);
        load();
    }

    private void load() {
        types.clear();
        patterns.clear();
        try {
            JSONArray a = new JSONArray(sp.getString("types", "[]"));
            for (int i = 0; i < a.length(); i++) types.add(ShiftType.fromJson(a.getJSONObject(i)));
            JSONArray p = new JSONArray(sp.getString("patterns", "[]"));
            for (int i = 0; i < p.length(); i++) patterns.add(Pattern.fromJson(p.getJSONObject(i)));
        } catch (Exception ignored) { }
        if (types.isEmpty()) defaultTypes();
        data = obj(sp.getString("data", "{}"));
        extra = obj(sp.getString("extra", "{}"));
        holidays = obj(sp.getString("holidays", "{}"));
        profiles = obj(sp.getString("profiles", "{}"));   // id → 顯示名稱
        ensureJobs();
    }

    private void ensureJobs() {
        for (int i = 0; i < JOBS.length; i++) if (!profiles.has(JOBS[i])) put(profiles, JOBS[i], JOB_NAMES[i]);
    }

    private static JSONObject obj(String s) {
        try { return new JSONObject(s); } catch (Exception e) { return new JSONObject(); }
    }

    private static void put(JSONObject o, String k, Object v) {
        try { o.put(k, v); } catch (Exception ignored) { }
    }

    private void defaultTypes() {
        types.add(new ShiftType("D", "白班", 0xFFFB8C00, "08:00", "16:00", false));
        types.add(new ShiftType("E", "小夜", 0xFF1E88E5, "16:00", "00:00", false));
        types.add(new ShiftType("N", "大夜", 0xFF5E35B1, "00:00", "08:00", false));
        types.add(new ShiftType("OFF", "休假", 0xFF43A047, "00:00", "00:00", true));
        types.add(new ShiftType("例", "例假", 0xFF00897B, "00:00", "00:00", true));
    }

    synchronized void save() {
        JSONArray a = new JSONArray(), p = new JSONArray();
        try {
            for (ShiftType t : types) a.put(t.toJson());
            for (Pattern x : patterns) p.put(x.toJson());
        } catch (Exception ignored) { }
        sp.edit().putString("types", a.toString()).putString("patterns", p.toString())
                .putString("data", data.toString()).putString("extra", extra.toString())
                .putString("holidays", holidays.toString()).putString("profiles", profiles.toString()).apply();
    }

    // ---------------------------------------------------------------- 日期
    static String key(Calendar c) {
        return String.format(Locale.US, "%04d-%02d-%02d", c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH));
    }

    static String key(int y, int m, int d) { return String.format(Locale.US, "%04d-%02d-%02d", y, m, d); }

    static Calendar parse(String key) {
        try {
            String[] p = key.split("-");
            Calendar c = Calendar.getInstance();
            c.clear();
            c.set(Integer.parseInt(p[0]), Integer.parseInt(p[1]) - 1, Integer.parseInt(p[2]));
            return c;
        } catch (Exception e) { return null; }
    }

    static long daysBetween(String a, String b) {
        Calendar x = parse(a), y = parse(b);
        if (x == null || y == null) return 0;
        long ux = x.getTimeInMillis() + x.get(Calendar.ZONE_OFFSET) + x.get(Calendar.DST_OFFSET);
        long uy = y.getTimeInMillis() + y.get(Calendar.ZONE_OFFSET) + y.get(Calendar.DST_OFFSET);
        return Math.round((uy - ux) / 86400000.0);
    }

    // ---------------------------------------------------------------- 多份班表
    synchronized List<String> profileIds() {
        List<String> out = new ArrayList<>();
        out.addAll(java.util.Arrays.asList(JOBS));
        for (Iterator<String> it = profiles.keys(); it.hasNext(); ) {
            String k = it.next();
            if (!isJob(k)) out.add(k);
        }
        return out;
    }

    synchronized List<String> friendIds() {
        List<String> out = profileIds();
        out.removeAll(java.util.Arrays.asList(JOBS));
        return out;
    }

    synchronized String profileName(String id) { return profiles.optString(id, id); }

    synchronized void renameProfile(String id, String name) {
        if (name != null && !name.trim().isEmpty()) put(profiles, id, name.trim());
    }

    synchronized String addFriend(String name) {
        String id = "f:" + name;
        put(profiles, id, name);
        return id;
    }

    synchronized void removeProfile(String id) {
        if (isJob(id)) data.remove(id);
        else { profiles.remove(id); data.remove(id); }
        for (Iterator<Pattern> it = patterns.iterator(); it.hasNext(); )
            if (it.next().profile.equals(id) && !id.equals(ME)) it.remove();
    }

    private JSONObject section(String profile, String name) {
        JSONObject p = data.optJSONObject(profile);
        if (p == null) { p = new JSONObject(); put(data, profile, p); }
        JSONObject s = p.optJSONObject(name);
        if (s == null) { s = new JSONObject(); put(p, name, s); }
        return s;
    }

    /** 手動排的班（不含自動重覆樣式）。 */
    synchronized String explicit(String profile, String key) { return section(profile, "days").optString(key, ""); }

    /** 實際的班：手動排的優先，否則看自動重覆的樣式。 */
    synchronized String code(String profile, String key) {
        String c = explicit(profile, key);
        if (!c.isEmpty()) return c;
        for (Pattern p : patterns)
            if (p.profile.equals(profile)) {
                String v = p.codeOn(key);
                if (!v.isEmpty()) return v;
            }
        return "";
    }

    synchronized void setCode(String profile, String key, String code) {
        JSONObject d = section(profile, "days");
        if (code == null || code.isEmpty()) d.remove(key); else put(d, key, code);
    }

    /**
     * 備註記錄：{ text, mood, links:[{title,url}], images:[檔名], places:[{name}] }。
     * 舊版只存字串，讀取時自動轉成 {text}。
     */
    synchronized JSONObject noteObj(String profile, String key) {
        Object v = section(profile, "notes").opt(key);
        try {
            if (v instanceof JSONObject) return new JSONObject(v.toString());
            JSONObject o = new JSONObject();
            if (v != null && !v.toString().isEmpty()) o.put("text", v.toString());
            return o;
        } catch (Exception e) { return new JSONObject(); }
    }

    static boolean noteEmpty(JSONObject o) {
        return o.optString("text").trim().isEmpty() && o.optString("mood").isEmpty() && len(o, "links") == 0
                && len(o, "images") == 0 && len(o, "places") == 0;
    }

    static int len(JSONObject o, String k) { JSONArray a = o.optJSONArray(k); return a == null ? 0 : a.length(); }

    synchronized void setNoteObj(String profile, String key, JSONObject o) {
        JSONObject d = section(profile, "notes");
        if (o == null || noteEmpty(o)) d.remove(key); else put(d, key, o);
    }

    /** 備註文字；沒有文字但有心情／附件時回簡短說明（讓月曆備註點、行事曆、小柯也看得到）。 */
    synchronized String note(String profile, String key) {
        JSONObject o = noteObj(profile, key);
        String t = o.optString("text").trim();
        if (!t.isEmpty() || noteEmpty(o)) return t;
        StringBuilder s = new StringBuilder(o.optString("mood"));
        if (len(o, "images") > 0) s.append("［圖片").append(len(o, "images")).append("］");
        if (len(o, "links") > 0) s.append("［連結").append(len(o, "links")).append("］");
        if (len(o, "places") > 0) s.append("［地點：").append(o.optJSONArray("places").optJSONObject(0).optString("name")).append("］");
        return s.toString();
    }

    synchronized String noteText(String profile, String key) { return noteObj(profile, key).optString("text"); }

    synchronized String mood(String profile, String key) { return noteObj(profile, key).optString("mood"); }

    /** 只改文字，保留心情、連結、圖片、地點。 */
    synchronized void setNote(String profile, String key, String note) {
        JSONObject o = noteObj(profile, key);
        put(o, "text", note == null ? "" : note.trim());
        setNoteObj(profile, key, o);
    }

    /** 某份班表的所有備註（日期排序）。 */
    synchronized List<String> noteKeys(String profile) {
        List<String> out = new ArrayList<>();
        for (Iterator<String> it = section(profile, "notes").keys(); it.hasNext(); ) out.add(it.next());
        Collections.sort(out);
        return out;
    }

    // ---------------------------------------------------------------- 加班 / 借休 / 日津貼（我的班表）
    synchronized float extra(String kind, String key) {
        JSONObject o = extra.optJSONObject(kind);
        return o == null ? 0 : (float) o.optDouble(key, 0);
    }

    synchronized void setExtra(String kind, String key, float v) {
        JSONObject o = extra.optJSONObject(kind);
        if (o == null) { o = new JSONObject(); put(extra, kind, o); }
        if (v == 0) o.remove(key); else put(o, key, (double) v);
    }

    // ---------------------------------------------------------------- 自訂特殊假日
    synchronized String customHoliday(String key) { return holidays.optString(key, ""); }

    synchronized void setCustomHoliday(String key, String name) {
        if (name == null || name.trim().isEmpty()) holidays.remove(key); else put(holidays, key, name.trim());
    }

    synchronized List<String> customHolidayKeys() {
        List<String> out = new ArrayList<>();
        for (Iterator<String> it = holidays.keys(); it.hasNext(); ) out.add(it.next());
        Collections.sort(out);
        return out;
    }

    // ---------------------------------------------------------------- 設定
    // 型別不符（舊版存成別的型別）時回預設值，不讓 App 閃退
    String pref(String k, String def) { try { return sp.getString(k, def); } catch (ClassCastException e) { return def; } }
    void setPref(String k, String v) { sp.edit().putString(k, v).apply(); }
    boolean flag(String k, boolean def) { try { return sp.getBoolean(k, def); } catch (ClassCastException e) { return def; } }
    void setFlag(String k, boolean v) { sp.edit().putBoolean(k, v).apply(); }
    int num(String k, int def) { try { return sp.getInt(k, def); } catch (ClassCastException e) { return def; } }
    void setNum(String k, int v) { sp.edit().putInt(k, v).apply(); }
    long calendarId() { return sp.getLong("calId", -1); }
    void setCalendarId(long id) { sp.edit().putLong("calId", id).apply(); }

    // ---------------------------------------------------------------- 班別歸類
    ShiftType type(String code) {
        for (ShiftType t : types) if (t.code.equalsIgnoreCase(code)) return t;
        return null;
    }

    private ShiftType firstOf(boolean off, String... codes) {
        for (String c : codes) { ShiftType t = type(c); if (t != null) return t; }
        for (ShiftType t : types) if (t.off == off) return t;
        return null;
    }

    /**
     * 原代號 → 班別（月班表系統規則）：含 D 或「白值」= 白班；含 E = 小夜；含 N 或「夜8」= 大夜；
     * 含 OFF、國、休 = 休假；例 = 例假；其他（如傷照）→ null，畫面照原代號顯示灰色。
     */
    ShiftType classify(String raw) {
        if (raw == null || raw.isEmpty()) return null;
        ShiftType exact = type(raw);
        if (exact != null) return exact;
        String u = raw.toUpperCase(Locale.US);
        if (u.contains("OFF") || raw.contains("國") || raw.contains("休")) return firstOf(true, "OFF");
        if (raw.contains("例")) return firstOf(true, "例", "OFF");
        if (u.contains("D") || raw.contains("白值")) return firstOf(false, "D");
        if (u.contains("E")) return firstOf(false, "E");
        if (u.contains("N") || raw.contains("夜8")) return firstOf(false, "N");
        return null;
    }

    /** 某天班別的完整說明，例如「白班（D/洗）08:00–16:00」。 */
    String describe(String profile, String key) {
        String raw = code(profile, key);
        if (raw.isEmpty()) return "未排班";
        ShiftType t = classify(raw);
        if (t == null) return raw;
        String s = t.name;
        if (!raw.equalsIgnoreCase(t.code)) s += "（" + raw + "）";
        return t.off ? s : s + " " + t.timeText();
    }

    /** 某月有排班（含自動重覆）的日期。 */
    synchronized List<String> monthKeys(String profile, int y, int m) {
        List<String> out = new ArrayList<>();
        Calendar c = Calendar.getInstance();
        c.clear();
        c.set(y, m - 1, 1);
        int max = c.getActualMaximum(Calendar.DAY_OF_MONTH);
        for (int d = 1; d <= max; d++) {
            String k = key(y, m, d);
            if (!code(profile, k).isEmpty()) out.add(k);
        }
        return out;
    }

    /** 自動清除三個月前的舊班表（原版設定項）。 */
    synchronized void deleteOlderThan(String key) {
        for (Iterator<String> pi = data.keys(); pi.hasNext(); ) {
            JSONObject p = data.optJSONObject(pi.next());
            if (p == null) continue;
            for (String sec : new String[]{"days", "notes"}) {
                JSONObject s = p.optJSONObject(sec);
                if (s == null) continue;
                List<String> old = new ArrayList<>();
                for (Iterator<String> it = s.keys(); it.hasNext(); ) { String k = it.next(); if (k.compareTo(key) < 0) old.add(k); }
                for (String k : old) s.remove(k);
            }
        }
    }

    // ---------------------------------------------------------------- 備份 / 還原
    synchronized JSONObject exportAll() throws Exception {
        JSONArray a = new JSONArray(), p = new JSONArray();
        for (ShiftType t : types) a.put(t.toJson());
        for (Pattern x : patterns) p.put(x.toJson());
        return new JSONObject().put("app", "小柯班表助手").put("version", 2).put("types", a).put("patterns", p)
                .put("data", data).put("extra", extra).put("holidays", holidays).put("profiles", profiles);
    }

    synchronized void importAll(JSONObject o) throws Exception {
        if (!"小柯班表助手".equals(o.optString("app"))) throw new Exception("這不是小柯班表助手的備份檔");
        JSONArray a = o.getJSONArray("types");
        types.clear();
        for (int i = 0; i < a.length(); i++) types.add(ShiftType.fromJson(a.getJSONObject(i)));
        if (types.isEmpty()) defaultTypes();
        patterns.clear();
        JSONArray p = o.optJSONArray("patterns");
        if (p != null) for (int i = 0; i < p.length(); i++) patterns.add(Pattern.fromJson(p.getJSONObject(i)));
        data = o.optJSONObject("data") != null ? o.getJSONObject("data") : new JSONObject();
        if (o.has("days"))   // 第 1 版備份：只有我的班表
            put(data, ME, new JSONObject().put("days", o.getJSONObject("days"))
                    .put("notes", o.optJSONObject("notes") == null ? new JSONObject() : o.getJSONObject("notes")));
        extra = o.optJSONObject("extra") != null ? o.getJSONObject("extra") : new JSONObject();
        holidays = o.optJSONObject("holidays") != null ? o.getJSONObject("holidays") : new JSONObject();
        profiles = o.optJSONObject("profiles") != null ? o.getJSONObject("profiles") : new JSONObject();
        ensureJobs();
        save();
    }
}
