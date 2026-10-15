import SwiftUI
import EventKit

/// 行事曆：用 EventKit 讀寫 iPhone 行事曆。先在 iPhone「設定 → 行事曆 → 帳號」加入 Google 帳號，就會同步到 Google 行事曆。
/// 本 App 寫入的事件在備註加 TAG，重新同步時先刪同月份帶 TAG 的事件再寫入，不會動到其他行程（同 Android）。
enum CalendarSync {
    static let TAG = "#小柯班表"
    static let store = EKEventStore()

    static var authorized: Bool { EKEventStore.authorizationStatus(for: .event) == .fullAccess }

    static func requestAccess() async -> Bool {
        if authorized { return true }
        return (try? await store.requestFullAccessToEvents()) ?? false
    }

    /// 可寫入的行事曆，Google 帳號排前面。
    static func calendars() -> [EKCalendar] {
        store.calendars(for: .event).filter(\.allowsContentModifications).sorted { a, b in
            let ga = a.source.title.localizedCaseInsensitiveContains("gmail") || a.source.title.localizedCaseInsensitiveContains("google")
            let gb = b.source.title.localizedCaseInsensitiveContains("gmail") || b.source.title.localizedCaseInsensitiveContains("google")
            return ga && !gb
        }
    }

    private static func date(_ d: Day, minutes: Int = 0) -> Date {
        Calendar.current.date(from: DateComponents(year: d.y, month: d.m, day: d.d))!.addingTimeInterval(TimeInterval(minutes * 60))
    }

    /// 把某月「我的班表」寫入行事曆，回傳寫入筆數。
    static func syncMonth(_ st: Store, _ cal: EKCalendar, _ y: Int, _ m: Int) throws -> Int {
        let first = Day(y, m, 1), next = first.adding(Day.daysIn(y, m))
        // 1. 刪掉本月舊的（只刪帶 TAG 的）
        let pred = store.predicateForEvents(withStart: date(first.adding(-1)), end: date(next.adding(1)), calendars: [cal])
        for e in store.events(matching: pred) where e.notes?.contains(TAG) == true {
            let d = Calendar.current.dateComponents([.year, .month], from: e.startDate)
            if d.year == y && d.month == m { try store.remove(e, span: .thisEvent, commit: false) }
        }
        // 2. 逐日寫入
        var n = 0
        for k in st.monthKeys(Store.ME, y, m) {
            guard let day = Day(key: k) else { continue }
            let raw = st.code(Store.ME, k)
            let t = st.classify(raw)
            let e = EKEvent(eventStore: store)
            e.calendar = cal
            let title = t.map { $0.name + (raw.caseInsensitiveCompare($0.code) == .orderedSame ? "" : "（\(raw)）") } ?? raw
            e.title = "【班】" + title
            let note = st.note(Store.ME, k)
            e.notes = (note.isEmpty ? "" : note + "\n") + TAG
            if let t, !t.off {
                let s = ShiftType.minutes(t.start)
                var en = ShiftType.minutes(t.end)
                if en <= s { en += 1440 }
                e.startDate = date(day, minutes: max(0, s))
                e.endDate = date(day, minutes: en)
            } else {
                e.isAllDay = true
                e.startDate = date(day)
                e.endDate = date(day.adding(1))
            }
            try store.save(e, span: .thisEvent, commit: false)
            n += 1
        }
        try store.commit()
        return n
    }

    /// 某天在所有行事曆上的行程（不含本 App 寫入的班）。
    static func eventsOn(_ day: Day) -> [String] {
        guard authorized else { return [] }
        let pred = store.predicateForEvents(withStart: date(day), end: date(day.adding(1)), calendars: nil)
        return store.events(matching: pred).filter { $0.notes?.contains(TAG) != true }.sorted { $0.startDate < $1.startDate }.map { e in
            if e.isAllDay { return "全天 " + (e.title ?? "") }
            let c = Calendar.current.dateComponents([.hour, .minute], from: e.startDate)
            return String(format: "%02d:%02d ", c.hour!, c.minute!) + (e.title ?? "")
        }
    }

    /// 某月有行程的日子（月曆藍點）。
    static func eventDays(_ y: Int, _ m: Int) -> Set<String> {
        guard authorized else { return [] }
        let first = Day(y, m, 1)
        let pred = store.predicateForEvents(withStart: date(first), end: date(first.adding(Day.daysIn(y, m))), calendars: nil)
        var out = Set<String>()
        for e in store.events(matching: pred) where e.notes?.contains(TAG) != true {
            let d = Calendar.current.dateComponents([.year, .month, .day], from: e.startDate)
            let endD = Calendar.current.dateComponents([.year, .month, .day], from: e.endDate.addingTimeInterval(-1))
            var cur = Day(d.year!, d.month!, d.day!)
            let last = Day(endD.year!, endD.month!, endD.day!)
            while cur <= last && cur.ordinal - first.ordinal < 40 {
                if cur.y == y && cur.m == m { out.insert(cur.key) }
                cur = cur.adding(1)
            }
        }
        return out
    }

    /// 開啟 iPhone「行事曆」App 到某一天。
    static func open(_ day: Day) {
        let t = date(day).timeIntervalSinceReferenceDate
        if let u = URL(string: "calshow:\(t)") { UIApplication.shared.open(u) }
    }
}

struct CalendarSyncView: View {
    @EnvironmentObject var st: Store
    @Environment(\.dismiss) private var dismiss
    let year: Int, month: Int
    let done: (String) -> Void
    @State private var cals: [EKCalendar] = []
    @State private var pick = ""
    @State private var status = ""
    @State private var denied = false

    var body: some View {
        NavigationStack {
            Form {
                if denied {
                    Section {
                        Text("需要「行事曆」完整取用權限。請到 iPhone「設定 → 小柯班表 → 行事曆」改成「完整取用」。")
                        Button("開啟設定") { UIApplication.shared.open(URL(string: UIApplication.openSettingsURLString)!) }
                    }
                } else {
                    Section {
                        Picker("寫到哪個行事曆", selection: $pick) {
                            ForEach(cals, id: \.calendarIdentifier) { c in
                                Text("\(c.title)（\(c.source.title)）").tag(c.calendarIdentifier)
                            }
                        }
                        .pickerStyle(.inline)
                    } header: { Text("同步 \(month) 月「我的班表」") } footer: {
                        Text("事件標題「【班】白班（D/洗）」，重複同步會先清掉這個月舊的班表事件，不會動到其他行程。要同步到 Google 行事曆，請先在 iPhone「設定 → 行事曆 → 帳號」加入 Google 帳號，再選它底下的行事曆。")
                    }
                    if !status.isEmpty { Section { Text(status).foregroundStyle(.red) } }
                }
            }
            .navigationTitle("同步到行事曆")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("取消") { dismiss() } }
                ToolbarItem(placement: .confirmationAction) {
                    Button("同步") { sync() }.bold().disabled(pick.isEmpty)
                }
            }
            .task {
                guard await CalendarSync.requestAccess() else { denied = true; return }
                cals = CalendarSync.calendars()
                let last = st.pref("calId", "")
                pick = cals.first { $0.calendarIdentifier == last }?.calendarIdentifier ?? cals.first?.calendarIdentifier ?? ""
                if cals.isEmpty { status = "找不到可寫入的行事曆" }
            }
        }
    }

    private func sync() {
        guard let cal = cals.first(where: { $0.calendarIdentifier == pick }) else { return }
        st.set("calId", pick)
        do {
            let n = try CalendarSync.syncMonth(st, cal, year, month)
            done("已寫入 \(n) 筆班表到「\(cal.title)」（重複同步會先清掉舊的）")
            dismiss()
        } catch { status = "同步失敗：" + error.localizedDescription }
    }
}
