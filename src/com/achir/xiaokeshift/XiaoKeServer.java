package com.achir.xiaokeshift;

import android.content.Context;
import android.net.wifi.WifiManager;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URLDecoder;
import java.util.Calendar;
import java.util.Collections;
import java.util.Locale;

/**
 * 小柯機器人區網 API（App 開著時在 8090 埠服務），讓 ESP32 / LX04 小柯查班表：
 *   GET /api/say?day=today|tomorrow|yyyy-MM-dd  → 一句話純文字，直接給 TTS 唸
 *   GET /api/day?date=yyyy-MM-dd                → JSON（班別、時間、農曆、宜忌、備註）
 *   GET /api/month?ym=yyyy-MM                   → JSON（整月班表＋統計）
 *   GET /                                       → 說明頁
 */
final class XiaoKeServer extends Thread {
    static final int PORT = 8090;
    private static XiaoKeServer running;
    private final Context ctx;
    private ServerSocket server;

    private XiaoKeServer(Context c) {
        super("XiaoKeServer");
        ctx = c.getApplicationContext();
        setDaemon(true);
    }

    static synchronized void ensureStarted(Context c) {
        if (running != null && running.isAlive()) return;
        running = new XiaoKeServer(c);
        running.start();
    }

    @Override
    public void run() {
        try {
            server = new ServerSocket(PORT);
            while (!isInterrupted()) {
                final Socket s = server.accept();
                new Thread(() -> handle(s)).start();
            }
        } catch (Exception ignored) { }
    }

    private void handle(Socket s) {
        try {
            s.setSoTimeout(8000);
            BufferedReader in = new BufferedReader(new InputStreamReader(s.getInputStream(), "UTF-8"));
            String line = in.readLine();
            if (line == null) { s.close(); return; }
            String[] p = line.split(" ");
            String target = p.length > 1 ? p[1] : "/";
            String path = target, query = "";
            int q = target.indexOf('?');
            if (q >= 0) { path = target.substring(0, q); query = target.substring(q + 1); }

            String type = "application/json; charset=utf-8", body;
            int status = 200;
            Store st = Store.get(ctx);
            if (path.equals("/api/say")) {
                type = "text/plain; charset=utf-8";
                body = say(st, dayOf(param(query, "day")));
            } else if (path.equals("/api/day")) {
                body = dayJson(st, dayOf(param(query, "date"))).toString();
            } else if (path.equals("/api/month")) {
                body = monthJson(st, param(query, "ym")).toString();
            } else if (path.equals("/")) {
                type = "text/html; charset=utf-8";
                body = "<meta name=viewport content='width=device-width'><h3>小柯班表助手 API</h3><ul>"
                        + "<li><a href='/api/say?day=today'>/api/say?day=today</a>（一句話，給小柯唸）"
                        + "<li><a href='/api/say?day=tomorrow'>/api/say?day=tomorrow</a>"
                        + "<li><a href='/api/day'>/api/day?date=yyyy-MM-dd</a>"
                        + "<li><a href='/api/month'>/api/month?ym=yyyy-MM</a></ul>";
            } else {
                status = 404;
                body = "{\"error\":\"not found\"}";
            }
            byte[] b = body.getBytes("UTF-8");
            OutputStream out = s.getOutputStream();
            out.write(("HTTP/1.1 " + status + (status == 200 ? " OK" : " Not Found") + "\r\nContent-Type: " + type
                    + "\r\nContent-Length: " + b.length + "\r\nAccess-Control-Allow-Origin: *\r\nConnection: close\r\n\r\n").getBytes("UTF-8"));
            out.write(b);
            out.flush();
            s.close();
        } catch (Exception e) {
            try { s.close(); } catch (Exception ignored) { }
        }
    }

    private static String param(String query, String name) {
        for (String kv : query.split("&")) {
            int i = kv.indexOf('=');
            if (i > 0 && kv.substring(0, i).equals(name)) {
                try { return URLDecoder.decode(kv.substring(i + 1), "UTF-8"); } catch (Exception e) { return ""; }
            }
        }
        return "";
    }

    static Calendar dayOf(String v) {
        Calendar c = Calendar.getInstance();
        if (v == null || v.isEmpty() || v.equals("today")) return c;
        if (v.equals("tomorrow")) { c.add(Calendar.DAY_OF_MONTH, 1); return c; }
        if (v.equals("yesterday")) { c.add(Calendar.DAY_OF_MONTH, -1); return c; }
        Calendar p = Store.parse(v);
        return p != null ? p : c;
    }

    static final String[] WEEK = {"日", "一", "二", "三", "四", "五", "六"};

    /** 給小柯唸的一句話，例如「今天10月14日星期三，農曆九月初三，你上白班 08:00 到 16:00。宜…」 */
    static String say(Store st, Calendar c) {
        Calendar today = Calendar.getInstance();
        String k = Store.key(c);
        String when = k.equals(Store.key(today)) ? "今天" : "";
        Calendar tm = (Calendar) today.clone();
        tm.add(Calendar.DAY_OF_MONTH, 1);
        if (k.equals(Store.key(tm))) when = "明天";
        String s = when + (c.get(Calendar.MONTH) + 1) + "月" + c.get(Calendar.DAY_OF_MONTH) + "日星期"
                + WEEK[c.get(Calendar.DAY_OF_WEEK) - 1] + "，農曆" + Almanac.lunarString(c) + "，";
        String raw = st.code(Store.ME, k);
        ShiftType t = st.classify(raw);
        if (raw.isEmpty()) s += "還沒有排班。";
        else if (t == null) s += "班別是「" + raw + "」。";
        else if (t.off) s += "你" + t.name + "，好好休息。";
        else s += "你上" + t.name + "，" + t.start + " 到 " + t.end + "。";
        String note = st.note(Store.ME, k);
        if (!note.isEmpty()) s += "備註：" + note + "。";
        Almanac a = Almanac.compute(c);
        if (a.ok) {
            if (!a.termToday.isEmpty()) s += "今天是" + a.termToday + "。";
            String hol = Holidays.name(st, k);
            if (!hol.isEmpty()) s += "是" + hol + "。";
            s += "宜" + a.yi + "，忌" + a.ji + "。";
        }
        return s;
    }

    static JSONObject dayJson(Store st, Calendar c) throws Exception {
        String k = Store.key(c), raw = st.code(Store.ME, k);
        ShiftType t = st.classify(raw);
        Almanac a = Almanac.compute(c);
        JSONObject o = new JSONObject().put("date", k).put("weekday", WEEK[c.get(Calendar.DAY_OF_WEEK) - 1])
                .put("code", raw).put("shift", t == null ? raw : t.name).put("off", t != null && t.off)
                .put("start", t == null || t.off ? "" : t.start).put("end", t == null || t.off ? "" : t.end)
                .put("note", st.note(Store.ME, k)).put("lunar", Almanac.lunarString(c)).put("text", say(st, c));
        if (a.ok) o.put("almanac", new JSONObject().put("year", a.yearGZ + "（" + a.zodiac + "）")
                .put("month", a.monthGZ).put("day", a.dayGZ).put("yi", a.yi).put("ji", a.ji)
                .put("chong", a.chong).put("sha", a.sha).put("term", a.termToday).put("nextTerm", a.nextTerm));
        return o;
    }

    static JSONObject monthJson(Store st, String ym) throws Exception {
        Calendar c = Calendar.getInstance();
        int y = c.get(Calendar.YEAR), m = c.get(Calendar.MONTH) + 1;
        try {
            String[] p = ym.split("-");
            y = Integer.parseInt(p[0]);
            m = Integer.parseInt(p[1]);
        } catch (Exception ignored) { }
        JSONArray days = new JSONArray();
        float hours = 0;
        int work = 0, off = 0;
        for (String k : st.monthKeys(Store.ME, y, m)) {
            String raw = st.code(Store.ME, k);
            ShiftType t = st.classify(raw);
            days.put(new JSONObject().put("date", k).put("code", raw).put("shift", t == null ? raw : t.name));
            if (t != null && t.off) off++;
            else if (t != null) { work++; hours += t.hours(); }
        }
        return new JSONObject().put("ym", String.format(Locale.US, "%04d-%02d", y, m)).put("days", days)
                .put("workDays", work).put("offDays", off).put("hours", hours);
    }

    /** 手機 Wi-Fi 的 IPv4 位址。 */
    static String ip(Context c) {
        try {
            WifiManager wm = (WifiManager) c.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
            int i = wm == null ? 0 : wm.getConnectionInfo().getIpAddress();
            if (i != 0) return (i & 255) + "." + (i >> 8 & 255) + "." + (i >> 16 & 255) + "." + (i >> 24 & 255);
            for (NetworkInterface n : Collections.list(NetworkInterface.getNetworkInterfaces()))
                for (InetAddress a : Collections.list(n.getInetAddresses()))
                    if (!a.isLoopbackAddress() && a instanceof Inet4Address) return a.getHostAddress();
        } catch (Exception ignored) { }
        return "（未連線）";
    }
}
