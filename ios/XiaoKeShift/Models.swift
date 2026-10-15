import SwiftUI

/// 班別：代號、名稱、顏色、上下班時間。off=true 表示休假類（不算工時、行事曆用全天事件）。
/// 另含班別薪資、班別說明、是否顯示於選擇清單、鬧鐘（可設前一天響）。JSON 欄位與 Android 版 ShiftType 相同。
struct ShiftType: Identifiable, Equatable {
    let id = UUID()
    var code: String
    var name: String
    var color: Int            // ARGB（Android 的 int，可能是負數）
    var start: String
    var end: String
    var off: Bool
    var desc = ""
    var hidden = false
    var pay: Double = 0       // 班別薪資（含班別津貼）
    var alarmOn = false
    var alarmBefore = false   // 鬧鐘在前一天響（例如大夜 00:00 上班，前一天 23:00 叫）
    var alarmTime = "06:30"

    static let palette: [UInt32] = [0xFFFB8C00, 0xFF1E88E5, 0xFF5E35B1, 0xFF43A047, 0xFF00897B, 0xFFE53935,
                                    0xFFD81B60, 0xFF6D4C41, 0xFF546E7A, 0xFFFDD835, 0xFF00ACC1, 0xFF7CB342]

    static func argb(_ v: UInt32) -> Int { Int(Int32(bitPattern: v)) }

    static func minutes(_ hhmm: String) -> Int {
        let p = hhmm.split(separator: ":").map { Int($0.trimmingCharacters(in: .whitespaces)) }
        guard p.count == 2, let h = p[0], let m = p[1] else { return -1 }
        return h * 60 + m
    }

    /// 工時（小時）；下班時間 ≤ 上班時間視為跨夜。
    var hours: Double {
        if off { return 0 }
        let s = Self.minutes(start)
        var e = Self.minutes(end)
        if s < 0 || e < 0 { return 0 }
        if e <= s { e += 1440 }
        return Double(e - s) / 60
    }

    var timeText: String { off ? "全天" : "\(start)–\(end)" }
    var swiftColor: Color { Color(argb: color) }

    var json: [String: Any] {
        ["code": code, "name": name, "color": color, "start": start, "end": end, "off": off, "desc": desc,
         "hidden": hidden, "pay": pay, "alarmOn": alarmOn, "alarmBefore": alarmBefore, "alarmTime": alarmTime]
    }

    init(code: String, name: String, color: Int, start: String, end: String, off: Bool) {
        self.code = code; self.name = name; self.color = color; self.start = start; self.end = end; self.off = off
    }

    init(json o: [String: Any]) {
        self.init(code: o.str("code"), name: o.str("name"), color: o.int("color", Self.argb(0xFF9E9E9E)),
                  start: o.str("start", "08:00"), end: o.str("end", "16:00"), off: o.bool("off"))
        desc = o.str("desc")
        hidden = o.bool("hidden")
        pay = o.double("pay")
        alarmOn = o.bool("alarmOn")
        alarmBefore = o.bool("alarmBefore")
        alarmTime = o.str("alarmTime", "06:30")
    }
}

/// 班表樣式：一串循環的班別代號，從 start 日起算。
/// auto=true 時在 from～to 期間「自動重覆」：那幾天沒手動排班就顯示樣式的班。
struct Pattern: Identifiable {
    let id = UUID()
    var name = "樣式"
    var profile = Store.ME
    var start = Day.today.key
    var from = ""
    var to = ""
    var auto = false
    var seq: [String] = []

    func codeOn(_ key: String) -> String {
        if !auto || seq.isEmpty { return "" }
        if !from.isEmpty && key < from { return "" }
        if !to.isEmpty && key > to { return "" }
        if key < start { return "" }
        return seq[Day.between(start, key) % seq.count]
    }

    var json: [String: Any] {
        ["name": name, "profile": profile, "start": start, "from": from, "to": to, "auto": auto, "seq": seq]
    }

    init() { }

    init(json o: [String: Any]) {
        name = o.str("name", "樣式")
        profile = o.str("profile", Store.ME)
        start = o.str("start", Day.today.key)
        from = o.str("from")
        to = o.str("to")
        auto = o.bool("auto")
        seq = (o["seq"] as? [Any])?.map { "\($0)" } ?? []
    }
}

// MARK: - JSON 小工具（行為同 org.json 的 optXxx）

extension Dictionary where Key == String, Value == Any {
    func str(_ k: String, _ def: String = "") -> String {
        guard let v = self[k], !(v is NSNull) else { return def }
        return v as? String ?? "\(v)"
    }
    func bool(_ k: String) -> Bool {
        if let b = self[k] as? Bool { return b }
        if let s = self[k] as? String { return s.lowercased() == "true" }
        return false
    }
    func double(_ k: String, _ def: Double = 0) -> Double {
        if let n = self[k] as? NSNumber { return n.doubleValue }
        if let s = self[k] as? String, let d = Double(s) { return d }
        return def
    }
    func int(_ k: String, _ def: Int) -> Int {
        if let n = self[k] as? NSNumber { return Int(truncatingIfNeeded: n.int64Value) }
        if let s = self[k] as? String, let d = Int(s) { return d }
        return def
    }
    func obj(_ k: String) -> [String: Any] { self[k] as? [String: Any] ?? [:] }
}

extension Color {
    init(argb: Int) {
        let v = UInt32(truncatingIfNeeded: argb)
        self.init(.sRGB, red: Double((v >> 16) & 0xFF) / 255, green: Double((v >> 8) & 0xFF) / 255,
                  blue: Double(v & 0xFF) / 255, opacity: Double((v >> 24) & 0xFF) / 255)
    }
}
