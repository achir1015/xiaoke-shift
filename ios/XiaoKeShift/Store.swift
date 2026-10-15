import SwiftUI
#if canImport(WidgetKit)
import WidgetKit
#endif

/// 班表資料。存成 App「文件」裡的 data.json，格式就是 Android 版備份的 data.json（可互通）。
/// 多份班表（profile）：me＝我的班表、job2～job5＝第二～第五班表、f:姓名＝好友。
///   data[profile].days：日期 "yyyy-MM-dd" → 班別代號原文（從工作站匯入的保留原代號，如「D/洗」）
///   data[profile].notes：日期 → 備註（舊格式純文字，或 {text, mood, links, images, places}）
///   extra.ot / extra.to / extra.allow：我的加班時數、借休時數、日津貼
///   holidays：自訂特殊假日（日期 → 名稱）
/// 原代號用 classify() 歸到班別，規則與病房助理工作站月班表系統相同。
final class Store: ObservableObject {
    static let ME = "me"
    static let JOBS = ["me", "job2", "job3", "job4", "job5"]
    static let JOB_NAMES = ["我的班表", "第二班表", "第三班表", "第四班表", "第五班表"]
    static let APP = "小柯班表助手"
    /// App Group（App 與桌面小工具共用資料）。朋友用 SideStore/AltStore 以自己的 Apple ID 重新簽名時，
    /// 群組名稱會被改掉，新名稱寫在 Info.plist 的 ALTAppGroups（小工具則看所屬 App 的 Info.plist）。
    static let GROUP: String = {
        func alt(_ b: Bundle?) -> String? { (b?.object(forInfoDictionaryKey: "ALTAppGroups") as? [String])?.first }
        let app = Bundle.main.bundleURL.pathExtension == "appex"
            ? Bundle(url: Bundle.main.bundleURL.deletingLastPathComponent().deletingLastPathComponent()) : nil
        return alt(Bundle.main) ?? alt(app) ?? "group.com.achir.xiaokeshift"
    }()
    /// 給桌面小工具讀的設定（App 存檔時複製一份到 App Group）。
    static let widgetPrefs = ["firstDow", "weekendRed", "showHolidays", "hideOff", "payMode", "payDay", "payStart", "payPeriod"]

    static func isJob(_ id: String) -> Bool { JOBS.contains(id) }

    @Published var types: [ShiftType] = []
    @Published var patterns: [Pattern] = []
    @Published private(set) var data: [String: Any] = [:]
    @Published private(set) var extra: [String: Any] = [:]
    @Published private(set) var holidays: [String: String] = [:]
    @Published private(set) var profiles: [String: String] = [:]

    var prefs = UserDefaults.standard
    private let file: URL

    init() {
        let docs = FileManager.default.urls(for: .documentDirectory, in: .userDomainMask)[0].appendingPathComponent("data.json")
        if let g = FileManager.default.containerURL(forSecurityApplicationGroupIdentifier: Self.GROUP) {
            file = g.appendingPathComponent("data.json")
            // 舊版存在 App 的「文件」裡：搬到 App Group，桌面小工具才讀得到
            if !FileManager.default.fileExists(atPath: file.path) && FileManager.default.fileExists(atPath: docs.path) {
                try? FileManager.default.copyItem(at: docs, to: file)
            }
        } else {
            file = docs
        }
        if let d = try? Data(contentsOf: file), let o = (try? JSONSerialization.jsonObject(with: d)) as? [String: Any] {
            apply(o)
        } else {
            types = Self.defaultTypes()
            ensureJobs()
        }
    }

    static func defaultTypes() -> [ShiftType] {
        [ShiftType(code: "D", name: "白班", color: ShiftType.argb(0xFFFB8C00), start: "08:00", end: "16:00", off: false),
         ShiftType(code: "E", name: "小夜", color: ShiftType.argb(0xFF1E88E5), start: "16:00", end: "00:00", off: false),
         ShiftType(code: "N", name: "大夜", color: ShiftType.argb(0xFF5E35B1), start: "00:00", end: "08:00", off: false),
         ShiftType(code: "OFF", name: "休假", color: ShiftType.argb(0xFF43A047), start: "00:00", end: "00:00", off: true),
         ShiftType(code: "例", name: "例假", color: ShiftType.argb(0xFF00897B), start: "00:00", end: "00:00", off: true)]
    }

    private func ensureJobs() {
        for (i, id) in Self.JOBS.enumerated() where profiles[id] == nil { profiles[id] = Self.JOB_NAMES[i] }
    }

    // MARK: - 存檔 / 備份格式

    /// 與 Android 版 Store.exportAll() 相同的結構。
    func exportAll() -> [String: Any] {
        ["app": Self.APP, "version": 2, "types": types.map(\.json), "patterns": patterns.map(\.json),
         "data": data, "extra": extra, "holidays": holidays, "profiles": profiles]
    }

    func exportData() throws -> Data {
        try JSONSerialization.data(withJSONObject: exportAll(), options: [.prettyPrinted, .sortedKeys])
    }

    /// 還原（Android 備份的 data.json 或舊版純 JSON）。
    func importAll(_ json: Data) throws {
        guard let o = (try? JSONSerialization.jsonObject(with: json)) as? [String: Any], o.str("app") == Self.APP else {
            throw NSError(domain: "XiaoKeShift", code: 1, userInfo: [NSLocalizedDescriptionKey: "這不是小柯班表助手的備份檔"])
        }
        apply(o)
        save()
    }

    private func apply(_ o: [String: Any]) {
        types = (o["types"] as? [[String: Any]] ?? []).map(ShiftType.init(json:))
        if types.isEmpty { types = Self.defaultTypes() }
        patterns = (o["patterns"] as? [[String: Any]] ?? []).map(Pattern.init(json:))
        var d = o.obj("data")
        if let days = o["days"] as? [String: Any] {     // 第 1 版備份：只有我的班表
            d[Self.ME] = ["days": days, "notes": o.obj("notes")]
        }
        data = d
        extra = o.obj("extra")
        holidays = o.obj("holidays").compactMapValues { $0 as? String }
        profiles = o.obj("profiles").compactMapValues { $0 as? String }
        ensureJobs()
    }

    func save() {
        do { try exportData().write(to: file, options: .atomic) } catch { print("存檔失敗：\(error)") }
        if let g = UserDefaults(suiteName: Self.GROUP) {
            for k in Self.widgetPrefs { g.set(prefs.object(forKey: k), forKey: k) }
        }
        #if canImport(WidgetKit)
        WidgetCenter.shared.reloadAllTimelines()
        #endif
    }

    // MARK: - 多份班表

    var profileIds: [String] { Self.JOBS + friendIds }
    var friendIds: [String] { profiles.keys.filter { !Self.isJob($0) }.sorted { profileName($0) < profileName($1) } }
    func profileName(_ id: String) -> String { profiles[id] ?? id }

    func renameProfile(_ id: String, _ name: String) {
        let n = name.trimmingCharacters(in: .whitespacesAndNewlines)
        if !n.isEmpty { profiles[id] = n; save() }
    }

    @discardableResult
    func addFriend(_ name: String) -> String {
        let id = "f:" + name
        profiles[id] = name
        save()
        return id
    }

    /// 第一～第五班表：清除資料；好友：連同名字一起刪除。
    func removeProfile(_ id: String) {
        if !Self.isJob(id) { profiles[id] = nil }
        data[id] = nil
        if id != Self.ME { patterns.removeAll { $0.profile == id } }
        save()
    }

    private func section(_ profile: String, _ name: String) -> [String: Any] {
        (data[profile] as? [String: Any])?.obj(name) ?? [:]
    }

    private func setSection(_ profile: String, _ name: String, _ s: [String: Any]) {
        var p = data[profile] as? [String: Any] ?? [:]
        p[name] = s
        data[profile] = p
    }

    /// 手動排的班（不含自動重覆樣式）。
    func explicit(_ profile: String, _ key: String) -> String { section(profile, "days").str(key) }

    /// 實際的班：手動排的優先，否則看自動重覆的樣式。
    func code(_ profile: String, _ key: String) -> String {
        let c = explicit(profile, key)
        if !c.isEmpty { return c }
        for p in patterns where p.profile == profile {
            let v = p.codeOn(key)
            if !v.isEmpty { return v }
        }
        return ""
    }

    func setCode(_ profile: String, _ key: String, _ code: String) {
        var d = section(profile, "days")
        d[key] = code.isEmpty ? nil : code
        setSection(profile, "days", d)
    }

    // MARK: - 備註

    /// 備註記錄：{ text, mood, links:[{title,url}], images:[檔名], places:[{name}] }。舊版只存字串，讀取時轉成 {text}。
    func noteObj(_ profile: String, _ key: String) -> [String: Any] {
        let v = section(profile, "notes")[key]
        if let o = v as? [String: Any] { return o }
        if let s = v as? String, !s.isEmpty { return ["text": s] }
        return [:]
    }

    static func count(_ o: [String: Any], _ k: String) -> Int { (o[k] as? [Any])?.count ?? 0 }

    static func noteEmpty(_ o: [String: Any]) -> Bool {
        o.str("text").trimmingCharacters(in: .whitespacesAndNewlines).isEmpty && o.str("mood").isEmpty
            && count(o, "links") == 0 && count(o, "images") == 0 && count(o, "places") == 0
    }

    func setNoteObj(_ profile: String, _ key: String, _ o: [String: Any]) {
        var d = section(profile, "notes")
        d[key] = Self.noteEmpty(o) ? nil : o
        setSection(profile, "notes", d)
    }

    /// 備註文字；沒有文字但有心情／附件時回簡短說明（讓月曆備註點也看得到）。
    func note(_ profile: String, _ key: String) -> String {
        let o = noteObj(profile, key)
        let t = o.str("text").trimmingCharacters(in: .whitespacesAndNewlines)
        if !t.isEmpty || Self.noteEmpty(o) { return t }
        var s = o.str("mood")
        if Self.count(o, "images") > 0 { s += "［圖片\(Self.count(o, "images"))］" }
        if Self.count(o, "links") > 0 { s += "［連結\(Self.count(o, "links"))］" }
        if let p = (o["places"] as? [[String: Any]])?.first { s += "［地點：\(p.str("name"))］" }
        return s
    }

    func noteText(_ profile: String, _ key: String) -> String { noteObj(profile, key).str("text") }
    func mood(_ profile: String, _ key: String) -> String { noteObj(profile, key).str("mood") }

    /// 只改文字，保留心情、連結、圖片、地點。
    func setNote(_ profile: String, _ key: String, _ text: String) {
        var o = noteObj(profile, key)
        o["text"] = text.trimmingCharacters(in: .whitespacesAndNewlines)
        setNoteObj(profile, key, o)
    }

    /// 某份班表的所有備註（日期排序）。
    func noteKeys(_ profile: String) -> [String] {
        section(profile, "notes").keys.filter { !Self.noteEmpty(noteObj(profile, $0)) }.sorted()
    }

    /// 某月有排班（含自動重覆）的日期。
    func monthKeys(_ profile: String, _ y: Int, _ m: Int) -> [String] {
        (1...Day.daysIn(y, m)).map { Day(y, m, $0).key }.filter { !code(profile, $0).isEmpty }
    }

    /// 自動清除某日以前的舊班表與備註（設定「自動清除三個月前的舊班表」）。
    func deleteOlderThan(_ key: String) {
        for (id, v) in data {
            guard var p = v as? [String: Any] else { continue }
            for sec in ["days", "notes"] {
                p[sec] = p.obj(sec).filter { $0.key >= key }
            }
            data[id] = p
        }
        save()
    }

    // MARK: - 加班 / 借休 / 日津貼（我的班表）

    func extra(_ kind: String, _ key: String) -> Double { extra.obj(kind).double(key) }

    func setExtra(_ kind: String, _ key: String, _ v: Double) {
        var o = extra.obj(kind)
        o[key] = v == 0 ? nil : v
        extra[kind] = o
    }

    // MARK: - 自訂特殊假日

    func customHoliday(_ key: String) -> String { holidays[key] ?? "" }
    var customHolidayKeys: [String] { holidays.keys.sorted() }

    func setCustomHoliday(_ key: String, _ name: String) {
        let n = name.trimmingCharacters(in: .whitespacesAndNewlines)
        holidays[key] = n.isEmpty ? nil : n
    }

    // MARK: - 設定（UserDefaults，鍵名同 Android 版）

    func flag(_ k: String, _ def: Bool) -> Bool { prefs.object(forKey: k) as? Bool ?? def }
    func pref(_ k: String, _ def: String) -> String { prefs.string(forKey: k) ?? def }
    func num(_ k: String, _ def: Int) -> Int { prefs.object(forKey: k) as? Int ?? def }
    func set(_ k: String, _ v: Any?) { prefs.set(v, forKey: k); objectWillChange.send() }

    // MARK: - 班別歸類

    func type(_ code: String) -> ShiftType? { types.first { $0.code.caseInsensitiveCompare(code) == .orderedSame } }

    private func firstOf(_ off: Bool, _ codes: String...) -> ShiftType? {
        for c in codes { if let t = type(c) { return t } }
        return types.first { $0.off == off }
    }

    /// 原代號 → 班別（月班表系統規則）：含 D 或「白值」= 白班；含 E = 小夜；含 N 或「夜8」= 大夜；
    /// 含 OFF、國、休 = 休假；例 = 例假；其他（如傷照）→ nil，畫面照原代號顯示灰色。
    func classify(_ raw: String) -> ShiftType? {
        if raw.isEmpty { return nil }
        if let exact = type(raw) { return exact }
        let u = raw.uppercased()
        if u.contains("OFF") || raw.contains("國") || raw.contains("休") { return firstOf(true, "OFF") }
        if raw.contains("例") { return firstOf(true, "例", "OFF") }
        if u.contains("D") || raw.contains("白值") { return firstOf(false, "D") }
        if u.contains("E") { return firstOf(false, "E") }
        if u.contains("N") || raw.contains("夜8") { return firstOf(false, "N") }
        return nil
    }

    /// 某天班別的完整說明，例如「白班（D/洗）08:00–16:00」。
    func describe(_ profile: String, _ key: String) -> String {
        let raw = code(profile, key)
        if raw.isEmpty { return "未排班" }
        guard let t = classify(raw) else { return raw }
        var s = t.name
        if raw.caseInsensitiveCompare(t.code) != .orderedSame { s += "（\(raw)）" }
        return t.off ? s : s + " " + t.timeText
    }
}
