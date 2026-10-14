package com.achir.xiaokeshift;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 病房助理工作站「月班表」匯入（Firebase Firestore REST，免金鑰讀取）。
 * 與小柯 ESP32 版 fetchScheduleMonth() 讀同一份文件：
 *   projects/monthlyschedule-a5dea/.../documents/schedules/{assistant|nurse}-YYYY-MM
 *   fields.employees.arrayValue.values[].mapValue.fields = { name, shifts{ "1": "D1", ... } }
 * 在背景執行緒呼叫。
 */
final class WardImport {
    static final String PROJECT = "monthlyschedule-a5dea";
    static final String[] CATS = {"assistant", "nurse"};
    static final String[] LABELS = {"病房助理", "護理師"};

    /** 一位員工一個月的班：日 → 原代號。 */
    static final class Employee {
        String name;
        final Map<Integer, String> shifts = new LinkedHashMap<>();
    }

    static final class NotFound extends Exception {
        NotFound(String m) { super(m); }
    }

    static List<Employee> fetch(String cat, int y, int m) throws Exception {
        String doc = String.format(Locale.US, "%s-%04d-%02d", cat, y, m);
        URL url = new URL("https://firestore.googleapis.com/v1/projects/" + PROJECT
                + "/databases/(default)/documents/schedules/" + doc);
        HttpURLConnection c = (HttpURLConnection) url.openConnection();
        c.setConnectTimeout(15000);
        c.setReadTimeout(15000);
        int code = c.getResponseCode();
        if (code == 404) throw new NotFound(y + "年" + m + "月班表尚未建立");
        if (code != 200) throw new Exception("讀取失敗（HTTP " + code + "）");
        String body = read(c.getInputStream());
        c.disconnect();

        List<Employee> out = new ArrayList<>();
        JSONArray emps = new JSONObject(body).getJSONObject("fields").getJSONObject("employees")
                .getJSONObject("arrayValue").optJSONArray("values");
        if (emps == null) return out;
        for (int i = 0; i < emps.length(); i++) {
            JSONObject f = emps.getJSONObject(i).getJSONObject("mapValue").getJSONObject("fields");
            Employee e = new Employee();
            e.name = f.optJSONObject("name") != null ? f.getJSONObject("name").optString("stringValue") : "";
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
