import SwiftUI
import Network

/// 小柯機器人區網 API（同 Android 版 XiaoKeServer，8090 埠），讓 ESP32 / LX04 小柯查班表：
///   GET /api/say?day=today|tomorrow|yyyy-MM-dd  → 一句話純文字，直接給 TTS 唸
///   GET /api/day?date=yyyy-MM-dd                → JSON（班別、時間、農曆、宜忌、備註）
///   GET /api/month?ym=yyyy-MM                   → JSON（整月班表＋統計）
///   GET /                                       → 說明頁
/// iOS 限制：App 在前景時才會回應（切到背景就停，回到前景自動再開）。
final class XiaoKeServer {
    static let PORT: UInt16 = 8090
    static let shared = XiaoKeServer()
    weak var store: Store?
    private let q = DispatchQueue(label: "xiaoke.shift.server")
    private var listener: NWListener?

    func start(_ st: Store) {
        store = st
        guard listener == nil, let l = try? NWListener(using: .tcp, on: NWEndpoint.Port(rawValue: Self.PORT)!) else { return }
        l.newConnectionHandler = { [weak self, q] conn in
            conn.start(queue: q)
            conn.receive(minimumIncompleteLength: 1, maximumLength: 16384) { data, _, _, _ in
                let line = data.flatMap { String(data: $0, encoding: .utf8) }?.components(separatedBy: "\r\n").first ?? ""
                let resp = self?.respond(line) ?? Data()
                conn.send(content: resp, completion: .contentProcessed { _ in conn.cancel() })
            }
        }
        l.stateUpdateHandler = { [weak self] st in
            if case .failed = st { self?.q.async { self?.listener?.cancel(); self?.listener = nil } }
        }
        l.start(queue: q)
        listener = l
    }

    func stop() {
        listener?.cancel()
        listener = nil
    }

    var running: Bool { listener != nil }

    // MARK: - HTTP

    private func respond(_ requestLine: String) -> Data {
        let p = requestLine.split(separator: " ")
        let target = p.count > 1 ? String(p[1]) : "/"
        let comps = URLComponents(string: "http://x" + target)
        let path = comps?.path ?? "/"
        func param(_ n: String) -> String { comps?.queryItems?.first { $0.name == n }?.value ?? "" }

        var type = "application/json; charset=utf-8", status = 200
        // 資料在主執行緒
        let body: String = DispatchQueue.main.sync {
            guard let st = store else { return "{}" }
            switch path {
            case "/api/say":
                type = "text/plain; charset=utf-8"
                return sayText(st, Self.dayOf(param("day")))
            case "/api/day":
                return Self.json(Self.dayJson(st, Self.dayOf(param("date"))))
            case "/api/month":
                return Self.json(Self.monthJson(st, param("ym")))
            case "/":
                type = "text/html; charset=utf-8"
                return "<meta charset=utf-8><meta name=viewport content='width=device-width'><h3>小柯班表助手 API（iPhone）</h3><ul>"
                    + "<li><a href='/api/say?day=today'>/api/say?day=today</a>（一句話，給小柯唸）"
                    + "<li><a href='/api/say?day=tomorrow'>/api/say?day=tomorrow</a>"
                    + "<li><a href='/api/day'>/api/day?date=yyyy-MM-dd</a>"
                    + "<li><a href='/api/month'>/api/month?ym=yyyy-MM</a></ul>"
            default:
                status = 404
                return "{\"error\":\"not found\"}"
            }
        }
        let b = Data(body.utf8)
        var out = Data(("HTTP/1.1 \(status) \(status == 200 ? "OK" : "Not Found")\r\nContent-Type: \(type)\r\n"
                        + "Content-Length: \(b.count)\r\nAccess-Control-Allow-Origin: *\r\nConnection: close\r\n\r\n").utf8)
        out.append(b)
        return out
    }

    static func dayOf(_ v: String) -> Day {
        switch v {
        case "", "today": return Day.today
        case "tomorrow": return Day.today.adding(1)
        case "yesterday": return Day.today.adding(-1)
        default: return Day(key: v) ?? Day.today
        }
    }

    private static func json(_ o: [String: Any]) -> String {
        (try? JSONSerialization.data(withJSONObject: o, options: [.sortedKeys])).flatMap { String(data: $0, encoding: .utf8) } ?? "{}"
    }

    static func dayJson(_ st: Store, _ c: Day) -> [String: Any] {
        let k = c.key, raw = st.code(Store.ME, k)
        let t = st.classify(raw)
        let a = Almanac.compute(c)
        var o: [String: Any] = [
            "date": k, "weekday": c.weekdayName, "code": raw, "shift": t?.name ?? raw, "off": t?.off ?? false,
            "start": t == nil || t!.off ? "" : t!.start, "end": t == nil || t!.off ? "" : t!.end,
            "note": st.note(Store.ME, k), "lunar": Almanac.lunarString(c), "text": sayText(st, c)]
        if a.ok {
            o["almanac"] = ["year": "\(a.yearGZ)（\(a.zodiac)）", "month": a.monthGZ, "day": a.dayGZ, "yi": a.yi, "ji": a.ji,
                            "chong": a.chong, "sha": a.sha, "term": a.termToday, "nextTerm": a.nextTerm]
        }
        return o
    }

    static func monthJson(_ st: Store, _ ym: String) -> [String: Any] {
        let p = ym.split(separator: "-").compactMap { Int($0) }
        let y = p.count == 2 ? p[0] : Day.today.y, m = p.count == 2 && (1...12).contains(p[1]) ? p[1] : Day.today.m
        var days: [[String: Any]] = [], hours = 0.0, work = 0, off = 0
        for k in st.monthKeys(Store.ME, y, m) {
            let raw = st.code(Store.ME, k)
            let t = st.classify(raw)
            days.append(["date": k, "code": raw, "shift": t?.name ?? raw])
            if let t { if t.off { off += 1 } else { work += 1; hours += t.hours } }
        }
        return ["ym": String(format: "%04d-%02d", y, m), "days": days, "workDays": work, "offDays": off, "hours": hours]
    }

    /// Wi-Fi（en0）的 IPv4 位址。
    static func wifiIP() -> String? {
        var addrs: UnsafeMutablePointer<ifaddrs>?
        guard getifaddrs(&addrs) == 0, let first = addrs else { return nil }
        defer { freeifaddrs(addrs) }
        for ptr in sequence(first: first, next: { $0.pointee.ifa_next }) {
            let ifa = ptr.pointee
            guard let sa = ifa.ifa_addr, sa.pointee.sa_family == UInt8(AF_INET), String(cString: ifa.ifa_name) == "en0" else { continue }
            var host = [CChar](repeating: 0, count: Int(NI_MAXHOST))
            if getnameinfo(sa, socklen_t(sa.pointee.sa_len), &host, socklen_t(host.count), nil, 0, NI_NUMERICHOST) == 0 {
                return String(cString: host)
            }
        }
        return nil
    }
}

/// 「小柯機器人連線」說明頁：顯示網址、小柯現在會說的話、測試連線。
struct XiaoKeConnectView: View {
    @EnvironmentObject var st: Store
    @Environment(\.dismiss) private var dismiss
    @AppStorage("keepAwake") private var keepAwake = false
    @State private var test = ""

    var body: some View {
        let ip = XiaoKeServer.wifiIP()
        let base = "http://\(ip ?? "（未連上 Wi-Fi）"):\(XiaoKeServer.PORT)"
        NavigationStack {
            Form {
                Section {
                    Text("小柯機器人跟 iPhone 連同一個 Wi-Fi，**本 App 開著（在前景）時**可以查班表。切到背景會暫停，回到 App 自動恢復。")
                        .font(.subheadline)
                    Toggle("App 開著時螢幕不要自動關閉", isOn: $keepAwake)
                }
                Section("網址（點一下複製）") {
                    ForEach(["/api/say?day=today", "/api/say?day=tomorrow", "/api/day?date=yyyy-MM-dd", "/api/month?ym=yyyy-MM"], id: \.self) { p in
                        Button { UIPasteboard.general.string = base + p } label: {
                            Text(base + p).font(.system(.footnote, design: .monospaced)).foregroundStyle(.primary)
                        }
                    }
                }
                Section("小柯現在會這樣說") {
                    Text("「\(sayText(st, Day.today))」").font(.subheadline)
                    Button("請小柯（iPhone）唸一次") { Task { await Speaker.shared.speak(sayText(st, Day.today)) } }
                }
                Section {
                    Button("測試連線（從 iPhone 自己讀 /api/say）") { Task { await runTest() } }
                    if !test.isEmpty { Text(test).font(.footnote).foregroundStyle(.secondary) }
                } footer: {
                    Text(XiaoKeServer.shared.running ? "伺服器運作中（\(XiaoKeServer.PORT) 埠）。" : "伺服器沒有啟動，請關掉 App 再開一次。")
                }
            }
            .navigationTitle("小柯機器人連線")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar { ToolbarItem(placement: .confirmationAction) { Button("完成") { dismiss() } } }
        }
    }

    private func runTest() async {
        test = "連線中…"
        guard let u = URL(string: "http://127.0.0.1:\(XiaoKeServer.PORT)/api/say?day=today") else { return }
        do {
            let (d, r) = try await URLSession.shared.data(from: u)
            test = "HTTP \((r as? HTTPURLResponse)?.statusCode ?? 0)：" + (String(data: d, encoding: .utf8) ?? "")
        } catch { test = "失敗：" + error.localizedDescription }
    }
}
