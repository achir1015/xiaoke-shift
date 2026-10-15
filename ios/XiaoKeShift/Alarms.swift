import SwiftUI
import AVFoundation
import AppIntents
import UserNotifications
#if canImport(AlarmKit)
import AlarmKit
#endif

/// 班別鬧鐘：往後找 30 天，依每個班別的鬧鐘設定排好（可設前一天響）。每次班表或設定改變都重排。
/// iOS 26 起用 AlarmKit（像「時鐘」App 的鬧鐘一樣響，可貪睡）；較舊的 iOS 用本地通知。
enum Alarms {
    struct Item { let at: Date; let day: Day; let profile: String; let type: ShiftType }

    static func upcoming(_ st: Store, days: Int = 30) -> [Item] {
        let now = Date()
        var out: [Item] = []
        let today = Day.today
        for i in -1...days {                         // 「前日」鬧鐘：明天的班可能今天就要響
            let d = today.adding(i)
            for prof in Store.JOBS {
                if prof != Store.ME && st.flag("ignoreJob2Alarm", false) { continue }
                guard let t = st.classify(st.code(prof, d.key)), t.alarmOn else { continue }
                let min = ShiftType.minutes(t.alarmTime)
                if min < 0 { continue }
                let ring = t.alarmBefore ? d.adding(-1) : d
                guard let at = Calendar.current.date(from: DateComponents(year: ring.y, month: ring.m, day: ring.d,
                                                                          hour: min / 60, minute: min % 60)),
                      at > now.addingTimeInterval(1) else { continue }
                if !out.contains(where: { $0.at == at }) { out.append(Item(at: at, day: d, profile: prof, type: t)) }
            }
        }
        return out.sorted { $0.at < $1.at }
    }

    static func format(_ d: Date) -> String {
        let c = Calendar.current.dateComponents([.month, .day, .hour, .minute, .weekday], from: d)
        return String(format: "%d/%d（%@）%02d:%02d", c.month!, c.day!, ["日", "一", "二", "三", "四", "五", "六"][c.weekday! - 1], c.hour!, c.minute!)
    }

    private static let idsKey = "alarmIDs"

    /// 重排所有鬧鐘；回傳說明文字。
    @MainActor
    static func schedule(_ st: Store) async -> String {
        let items = upcoming(st)
        #if canImport(AlarmKit)
        if #available(iOS 26.0, *) {
            let m = AlarmManager.shared
            for s in st.prefs.stringArray(forKey: idsKey) ?? [] { if let id = UUID(uuidString: s) { try? m.cancel(id: id) } }
            st.prefs.set([String](), forKey: idsKey)
            if items.isEmpty { return "沒有設定鬧鐘的班別" }
            if m.authorizationState == .notDetermined { _ = try? await m.requestAuthorization() }
            guard m.authorizationState == .authorized else { return "請到 iPhone「設定 → 小柯班表 → 鬧鐘」允許使用鬧鐘" }
            var ids: [String] = []
            for it in items {
                let id = UUID()
                do {
                    _ = try await m.schedule(id: id, configuration: config(st, it))
                    ids.append(id.uuidString)
                } catch { break }
            }
            st.prefs.set(ids, forKey: idsKey)
            return "下次喚醒時間：" + format(items[0].at) + "（已排 \(ids.count) 個）"
        }
        #endif
        return await notifications(st, items)
    }

    #if canImport(AlarmKit)
    @available(iOS 26.0, *)
    struct Meta: AlarmMetadata { }

    @available(iOS 26.0, *)
    private static func config(_ st: Store, _ it: Item) -> AlarmManager.AlarmConfiguration<Meta> {
        let title = "小柯班表：\(st.describe(it.profile, it.day.key))"
        let stop = AlarmButton(text: "關閉", textColor: .white, systemImageName: "stop.circle")
        let snoozeMin = max(1, st.num("snoozeMin", 5))
        let alert: AlarmPresentation.Alert
        if st.flag("disableSnooze", false) {
            alert = AlarmPresentation.Alert(title: LocalizedStringResource(stringLiteral: title), stopButton: stop)
        } else {
            let snooze = AlarmButton(text: "貪睡 \(snoozeMin) 分", textColor: .white, systemImageName: "zzz")
            alert = AlarmPresentation.Alert(title: LocalizedStringResource(stringLiteral: title), stopButton: stop,
                                            secondaryButton: snooze, secondaryButtonBehavior: .countdown)
        }
        let attrs = AlarmAttributes<Meta>(presentation: AlarmPresentation(alert: alert), tintColor: it.type.swiftColor)
        return AlarmManager.AlarmConfiguration(
            countdownDuration: st.flag("disableSnooze", false) ? nil : Alarm.CountdownDuration(preAlert: nil, postAlert: TimeInterval(snoozeMin * 60)),
            schedule: .fixed(it.at), attributes: attrs,
            stopIntent: StopAlarmIntent(day: it.day.key))
    }
    #endif

    /// 較舊的 iOS：用本地通知（有聲音）。
    @MainActor
    private static func notifications(_ st: Store, _ items: [Item]) async -> String {
        let c = UNUserNotificationCenter.current()
        c.removePendingNotificationRequests(withIdentifiers: (st.prefs.stringArray(forKey: idsKey) ?? []))
        if items.isEmpty { return "沒有設定鬧鐘的班別" }
        _ = try? await c.requestAuthorization(options: [.alert, .sound])
        var ids: [String] = []
        for it in items.prefix(60) {
            let n = UNMutableNotificationContent()
            n.title = "⏰ 小柯班表"
            n.body = st.describe(it.profile, it.day.key)
            n.sound = .defaultCritical
            n.interruptionLevel = .timeSensitive
            n.userInfo = ["day": it.day.key]
            let comps = Calendar.current.dateComponents([.year, .month, .day, .hour, .minute], from: it.at)
            let id = UUID().uuidString
            try? await c.add(UNNotificationRequest(identifier: id, content: n,
                                                   trigger: UNCalendarNotificationTrigger(dateMatching: comps, repeats: false)))
            ids.append(id)
        }
        st.prefs.set(ids, forKey: idsKey)
        return "下次提醒時間：" + format(items[0].at) + "（用通知提醒；iOS 26 以上才能像鬧鐘一樣響）"
    }

    /// 測試：1 分鐘後響。
    @MainActor
    static func test(_ st: Store) async -> String {
        let at = Date().addingTimeInterval(60)
        let t = st.classify(st.code(Store.ME, Day.today.key)) ?? st.types[0]
        let it = Item(at: at, day: Day.today, profile: Store.ME, type: t)
        #if canImport(AlarmKit)
        if #available(iOS 26.0, *) {
            let m = AlarmManager.shared
            if m.authorizationState == .notDetermined { _ = try? await m.requestAuthorization() }
            guard m.authorizationState == .authorized else { return "請到 iPhone「設定 → 小柯班表 → 鬧鐘」允許使用鬧鐘" }
            do { _ = try await m.schedule(id: UUID(), configuration: config(st, it)) } catch { return "測試失敗：\(error.localizedDescription)" }
            return "1 分鐘後會響（\(format(at))）"
        }
        #endif
        _ = await notifications(st, [it])
        return "1 分鐘後會跳出通知"
    }
}

/// 關閉鬧鐘時執行：小柯唸出今天的班＋農民曆（設定可關）。
struct StopAlarmIntent: LiveActivityIntent {
    static var title: LocalizedStringResource = "關閉小柯班表鬧鐘"
    @Parameter(title: "日期") var day: String

    init() { }
    init(day: String) { self.day = day }

    @MainActor
    func perform() async throws -> some IntentResult {
        let st = Store()
        if st.flag("xiaokeSpeak", true), let d = Day(key: day) { await Speaker.shared.speak(sayText(st, d)) }
        return .result()
    }
}

/// 給小柯唸的一句話（同 Android XiaoKeServer.say），例如「今天10月14日星期三，農曆九月初三，你上白班 08:00 到 16:00。宜…」
func sayText(_ st: Store, _ c: Day) -> String {
    let k = c.key
    let when = c == Day.today ? "今天" : c == Day.today.adding(1) ? "明天" : ""
    var s = "\(when)\(c.m)月\(c.d)日星期\(c.weekdayName)，農曆\(Almanac.lunarString(c))，"
    let raw = st.code(Store.ME, k)
    let t = st.classify(raw)
    if raw.isEmpty { s += "還沒有排班。" }
    else if let t { s += t.off ? "你\(t.name)，好好休息。" : "你上\(t.name)，\(t.start) 到 \(t.end)。" }
    else { s += "班別是「\(raw)」。" }
    let note = st.note(Store.ME, k)
    if !note.isEmpty { s += "備註：\(note)。" }
    let a = Almanac.compute(c)
    if a.ok {
        if !a.termToday.isEmpty { s += "今天是\(a.termToday)。" }
        let hol = Holidays.name(st, c)
        if !hol.isEmpty { s += "是\(hol)。" }
        s += "宜\(a.yi)，忌\(a.ji)。"
    }
    return s
}

/// 小柯說話（AVSpeechSynthesizer，台灣中文）。
@MainActor
final class Speaker: NSObject, AVSpeechSynthesizerDelegate {
    static let shared = Speaker()
    private let synth = AVSpeechSynthesizer()
    private var done: CheckedContinuation<Void, Never>?

    func speak(_ text: String) async {
        try? AVAudioSession.sharedInstance().setCategory(.playback, mode: .spokenAudio, options: [.duckOthers])
        try? AVAudioSession.sharedInstance().setActive(true)
        synth.delegate = self
        let u = AVSpeechUtterance(string: text)
        u.voice = AVSpeechSynthesisVoice(language: "zh-TW")
        await withCheckedContinuation { c in
            done?.resume()
            done = c
            synth.stopSpeaking(at: .immediate)
            synth.speak(u)
        }
    }

    nonisolated func speechSynthesizer(_ s: AVSpeechSynthesizer, didFinish u: AVSpeechUtterance) {
        Task { @MainActor in done?.resume(); done = nil }
    }
}

/// 鬧鐘設定。每個班別的鬧鐘時間在「班別設定」裡設。
struct AlarmSettingsView: View {
    @EnvironmentObject var st: Store
    @Environment(\.dismiss) private var dismiss
    @AppStorage("snoozeMin") private var snoozeMin = 5
    @AppStorage("disableSnooze") private var disableSnooze = false
    @AppStorage("xiaokeSpeak") private var speak = true
    @AppStorage("ignoreJob2Alarm") private var ignoreJob2 = false
    @State private var status = "…"

    var body: some View {
        NavigationStack {
            Form {
                Section {
                    Text(status)
                    ForEach(Array(Alarms.upcoming(st).prefix(5).enumerated()), id: \.offset) { _, it in
                        Text("⏰ \(Alarms.format(it.at))　\(st.profileName(it.profile))：\(st.describe(it.profile, it.day.key))")
                            .font(.footnote).foregroundStyle(.secondary)
                    }
                } footer: { Text("每個班別的鬧鐘時間（及「前日響」）在「班別設定」裡設。") }
                Section {
                    Toggle("不顯示貪睡按鈕", isOn: $disableSnooze)
                    if !disableSnooze { Stepper("貪睡時間 \(snoozeMin) 分鐘", value: $snoozeMin, in: 1...30) }
                    Toggle("關閉鬧鐘後，小柯唸出今天的班＋農民曆", isOn: $speak)
                    Toggle("只啟動「我的班表」的鬧鐘（第二～第五班表不響）", isOn: $ignoreJob2)
                }
                Section {
                    Button("重新排鬧鐘") { Task { status = await Alarms.schedule(st) } }
                    Button("測試鬧鐘（1 分鐘後響）") { Task { status = await Alarms.test(st) } }
                    Button("請小柯唸今天的班") { Task { await Speaker.shared.speak(sayText(st, Day.today)) } }
                }
            }
            .navigationTitle("鬧鐘設定")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .confirmationAction) {
                    Button("完成") { Task { _ = await Alarms.schedule(st) }; dismiss() }
                }
            }
            .task { status = await Alarms.schedule(st) }
        }
    }
}
