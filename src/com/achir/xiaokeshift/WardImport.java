package com.achir.xiaokeshift;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 病房助理工作站「月班表」匯入（Firebase Firestore REST，免金鑰讀取）。
 * 月班表系統（achir1015/rcw-month）分成「公司＋部門」單位，每個單位有自己的管理者密碼：
 *   orgs/{單位ID} = { company, department, passwordHash = SHA-256("rcw-org:" + 密碼) 十六進位 }
 *   原始單位 default（忠孝院區／呼吸照護病房）文件 ID 不加前綴：schedules/{assistant|nurse}-YYYY-MM
 *   其他單位：schedules/{單位ID}__{assistant|nurse}-YYYY-MM
 *   fields.employees.arrayValue.values[].mapValue.fields = { name, shifts{ "1": "D1", ... } }
 * 選單位並通過密碼後才能匯入。在背景執行緒呼叫。
 */
final class WardImport {
    static final String PROJECT = "monthlyschedule-a5dea";
    static final String BASE = "https://firestore.googleapis.com/v1/projects/" + PROJECT + "/databases/(default)/documents/";
    static final String[] CATS = {"assistant", "nurse"};
    static final String[] LABELS = {"病房助理", "護理師"};
    static final String LEGACY_ORG = "default";

    /** 一位員工一個月的班：日 → 原代號。 */
    static final class Employee {
        String name;
        final Map<Integer, String> shifts = new LinkedHashMap<>();
    }

    /** 單位（公司＋部門）。 */
    static final class Org {
        String id, company, department, passwordHash;
        boolean isLegacy() { return LEGACY_ORG.equals(id); }
        String label() { return company + "／" + department; }
        @Override public String toString() { return label(); }
    }

    static final class NotFound extends Exception {
        NotFound(String m) { super(m); }
    }

    /** 與月班表系統相同的密碼雜湊：SHA-256("rcw-org:" + 密碼)，小寫十六進位。 */
    static String hash(String pw) {
        try {
            byte[] d = MessageDigest.getInstance("SHA-256").digest(("rcw-org:" + pw).getBytes("UTF-8"));
            StringBuilder b = new StringBuilder();
            for (byte x : d) b.append(String.format("%02x", x));
            return b.toString();
        } catch (Exception e) { return ""; }
    }

    private static String get(String url) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(15000);
        c.setReadTimeout(15000);
        int code = c.getResponseCode();
        if (code == 404) { c.disconnect(); throw new NotFound("404"); }
        if (code != 200) { c.disconnect(); throw new Exception("讀取失敗（HTTP " + code + "）"); }
        String body = read(c.getInputStream());
        c.disconnect();
        return body;
    }

    private static String field(JSONObject fields, String k) {
        JSONObject v = fields == null ? null : fields.optJSONObject(k);
        return v == null ? "" : v.optString("stringValue", "");
    }

    /** 單位清單（orgs 集合）；原始單位排第一。清單是空的代表月班表系統還沒建立單位。 */
    static List<Org> orgs() throws Exception {
        List<Org> out = new ArrayList<>();
        String token = "";
        do {
            String body = get(BASE + "orgs?pageSize=300" + (token.isEmpty() ? "" : "&pageToken=" + URLEncoder.encode(token, "UTF-8")));
            JSONObject o = new JSONObject(body);
            JSONArray docs = o.optJSONArray("documents");
            if (docs != null)
                for (int i = 0; i < docs.length(); i++) {
                    JSONObject d = docs.getJSONObject(i);
                    String name = d.optString("name");
                    JSONObject f = d.optJSONObject("fields");
                    Org g = new Org();
                    g.id = name.substring(name.lastIndexOf('/') + 1);
                    g.company = field(f, "company");
                    g.department = field(f, "department");
                    g.passwordHash = field(f, "passwordHash");
                    if (g.isLegacy()) out.add(0, g); else out.add(g);
                }
            token = o.optString("nextPageToken", "");
        } while (!token.isEmpty());
        if (out.isEmpty()) throw new Exception("月班表系統尚未建立單位，請先登入月班表網頁一次");
        return out;
    }

    static String docId(Org org, String cat, int y, int m) {
        String base = String.format(Locale.US, "%s-%04d-%02d", cat, y, m);
        return org == null || org.isLegacy() ? base : org.id + "__" + base;
    }

    static List<Employee> fetch(Org org, String cat, int y, int m) throws Exception {
        String body;
        try {
            body = get(BASE + "schedules/" + docId(org, cat, y, m));
        } catch (NotFound e) {
            throw new NotFound((org == null ? "" : org.department + " ") + y + "年" + m + "月班表尚未建立");
        }
        List<Employee> out = new ArrayList<>();
        JSONObject root = new JSONObject(body).optJSONObject("fields");
        JSONObject emp = root == null ? null : root.optJSONObject("employees");
        JSONObject av = emp == null ? null : emp.optJSONObject("arrayValue");
        JSONArray emps = av == null ? null : av.optJSONArray("values");
        if (emps == null) return out;
        for (int i = 0; i < emps.length(); i++) {
            JSONObject f = emps.getJSONObject(i).getJSONObject("mapValue").getJSONObject("fields");
            Employee e = new Employee();
            e.name = field(f, "name").trim();
            JSONObject sh = f.optJSONObject("shifts");
            JSONObject days = sh == null ? null : sh.optJSONObject("mapValue");
            days = days == null ? null : days.optJSONObject("fields");
            if (days != null)
                for (int d = 1; d <= 31; d++) {
                    JSONObject v = days.optJSONObject(String.valueOf(d));
                    String s = v == null ? "" : v.optString("stringValue", "").trim();
                    if (!s.isEmpty()) e.shifts.put(d, s);
                }
            if (!e.name.isEmpty()) out.add(e);
        }
        return out;
    }

    private static String read(InputStream in) throws Exception {
        ByteArrayOutputStream bo = new ByteArrayOutputStream();
        byte[] b = new byte[8192];
        for (int n; (n = in.read(b)) > 0; ) bo.write(b, 0, n);
        in.close();
        return bo.toString("UTF-8");
    }

    /** 寫入班表；overwrite=false 時只填空白的日子。回傳寫入天數。 */
    static int apply(Store s, String profile, Employee e, int y, int m, boolean overwrite) {
        int n = 0;
        for (Map.Entry<Integer, String> en : e.shifts.entrySet()) {
            String k = Store.key(y, m, en.getKey());
            if (!overwrite && !s.explicit(profile, k).isEmpty()) continue;
            s.setCode(profile, k, en.getValue());
            n++;
        }
        s.save();
        return n;
    }
}
