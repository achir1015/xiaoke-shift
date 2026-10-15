import SwiftUI
import CryptoKit

// MARK: - 統計

struct StatsView: View {
    @EnvironmentObject var st: Store
    @Environment(\.dismiss) private var dismiss
    let year: Int, month: Int, profile: String

    var body: some View {
        NavigationStack {
            ScrollView {
                Text(text).frame(maxWidth: .infinity, alignment: .leading).padding().textSelection(.enabled)
            }
            .navigationTitle("\(String(year)) 年 \(month) 月統計")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar { ToolbarItem(placement: .confirmationAction) { Button("關閉") { dismiss() } } }
        }
    }

    private var text: String {
        var order: [String] = [], count: [String: Int] = [:], hrs: [String: Double] = [:]
        var hours = 0.0, pay = 0.0, ot = 0.0, to = 0.0, allow = 0.0
        var work = 0, off = 0
        for k in st.monthKeys(profile, year, month) {
            let raw = st.code(profile, k)
            let t = st.classify(raw)
            let label = t?.name ?? raw
            if count[label] == nil { order.append(label) }
            count[label, default: 0] += 1
            if let t {
                if t.off { off += 1 } else {
                    work += 1
                    hours += t.hours
                    pay += t.pay
                    hrs[label, default: 0] += t.hours
                }
            }
        }
        if profile == Store.ME {
            for d in 1...Day.daysIn(year, month) {
                let k = Day(year, month, d).key
                ot += st.extra("ot", k); to += st.extra("to", k); allow += st.extra("allow", k)
            }
        }
        let cur = st.pref("currency", "NT$")
        var s = "\(st.profileName(profile))\n上班 \(work) 天，休假 \(off) 天\n" + String(format: "工時合計 %.1f 小時\n", hours + ot - to)
        if profile == Store.ME {
            s += String(format: "（班表 %.1f＋加班 %.1f－借休 %.1f）\n", hours, ot, to)
            s += String(format: "薪資（含班別津貼）%@ %.0f\n日津貼 %@ %.0f\n合計 %@ %.0f\n", cur, pay, cur, allow, cur, pay + allow)
        }
        s += "\n"
        for l in order {
            s += "\(l)：\(count[l]!) 天"
            if let h = hrs[l] { s += String(format: "（%.1f 小時）", h) }
            s += "\n"
        }
        return s
    }
}

// MARK: - 所有備註記錄

struct AllNotesView: View {
    @EnvironmentObject var st: Store
    @Environment(\.dismiss) private var dismiss
    let profile: String
    let go: (Day) -> Void

    var body: some View {
        NavigationStack {
            List {
                let keys = st.noteKeys(profile)
                if keys.isEmpty { Text("列表是空的").foregroundStyle(.secondary) }
                ForEach(keys.reversed(), id: \.self) { k in
                    NavigationLink {
                        NoteView(day: Day(key: k)!, profile: profile)
                    } label: {
                        VStack(alignment: .leading, spacing: 2) {
                            Text("\(k)　\(st.code(profile, k))　\(st.mood(profile, k))").font(.subheadline.bold())
                            Text(st.note(profile, k)).font(.subheadline).foregroundStyle(.secondary).lineLimit(3)
                        }
                    }
                    .swipeActions(edge: .leading) {
                        Button("跳到這天") { go(Day(key: k)!); dismiss() }.tint(.blue)
                    }
                }
            }
            .navigationTitle("所有備註記錄（\(st.profileName(profile))）")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar { ToolbarItem(placement: .confirmationAction) { Button("關閉") { dismiss() } } }
        }
    }
}

// MARK: - 特殊假日

struct HolidaysView: View {
    @EnvironmentObject var st: Store
    @Environment(\.dismiss) private var dismiss
    let year: Int
    @AppStorage("showHolidays") private var showHolidays = true

    var body: some View {
        NavigationStack {
            List {
                Section { Toggle("於月曆顯示國定假日", isOn: $showHolidays) }
                Section("\(String(year)) 年國定假日（離線計算，補假請自行加入）") {
                    ForEach(Holidays.year(year).sorted { $0.key < $1.key }, id: \.key) { k, v in
                        HStack { Text(String(k.dropFirst(5))).monospacedDigit(); Text(v) }
                    }
                }
                Section("自訂特殊假日（點月曆某天即可設定，左滑刪除）") {
                    if st.customHolidayKeys.isEmpty { Text("（無）").foregroundStyle(.secondary) }
                    ForEach(st.customHolidayKeys, id: \.self) { k in
                        HStack { Text(k).monospacedDigit(); Text(st.customHoliday(k)) }
                    }
                    .onDelete { idx in
                        let ks = st.customHolidayKeys
                        for i in idx { st.setCustomHoliday(ks[i], "") }
                        st.save()
                    }
                }
            }
            .navigationTitle("特殊假日")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar { ToolbarItem(placement: .confirmationAction) { Button("完成") { dismiss() } } }
        }
    }
}

// MARK: - 發薪日

struct PaydayView: View {
    @EnvironmentObject var st: Store
    @Environment(\.dismiss) private var dismiss
    let selected: Day
    @AppStorage("payMode") private var mode = "none"
    @AppStorage("payDay") private var payDay = 5
    @AppStorage("payPeriod") private var period = 14
    @AppStorage("currency") private var currency = "NT$"
    @State private var start = Date()

    var body: some View {
        NavigationStack {
            Form {
                Section {
                    Picker("發薪日", selection: $mode) {
                        Text("無自動重覆").tag("none")
                        Text("每月固定日期").tag("day")
                        Text("固定週期").tag("period")
                    }
                    if mode == "day" { Stepper("每月 \(payDay) 日", value: $payDay, in: 1...31) }
                    if mode == "period" {
                        DatePicker("起始日", selection: $start, displayedComponents: .date)
                        Stepper("每 \(period) 天", value: $period, in: 1...365)
                    }
                } footer: { Text("發薪日在月曆上顯示綠色 $。") }
                Section("貨幣單位（統計用）") { TextField("NT$", text: $currency) }
            }
            .navigationTitle("發薪日")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .confirmationAction) {
                    Button("完成") {
                        let c = Calendar.current.dateComponents([.year, .month, .day], from: start)
                        st.set("payStart", Day(c.year!, c.month!, c.day!).key)
                        dismiss()
                    }
                }
            }
        }
        .onAppear {
            let d = Day(key: st.pref("payStart", "")) ?? selected
            start = Calendar.current.date(from: DateComponents(year: d.y, month: d.m, day: d.d)) ?? Date()
        }
    }
}

// MARK: - 班表管理（五份班表／改名／好友）

struct ProfilesView: View {
    @EnvironmentObject var st: Store
    @Environment(\.dismiss) private var dismiss
    @Binding var profile: String
    let year: Int, month: Int
    let done: (String) -> Void
    @State private var renaming: String?
    @State private var newName = ""
    @State private var addingFriend = false
    @State private var clearing: String?
    @State private var wardFriends = false

    var body: some View {
        NavigationStack {
            List {
                Section("自己的班表（點名稱改名）") {
                    ForEach(Store.JOBS, id: \.self) { id in
                        Button { newName = st.profileName(id); renaming = id } label: {
                            HStack {
                                Text(st.profileName(id)).foregroundStyle(.primary)
                                Spacer()
                                Text(Store.JOB_NAMES[Store.JOBS.firstIndex(of: id)!]).font(.caption).foregroundStyle(.secondary)
                            }
                        }
                        .swipeActions { Button("清除") { clearing = id }.tint(.red) }
                    }
                }
                Section {
                    ForEach(st.friendIds, id: \.self) { id in
                        Button { profile = id; dismiss() } label: { Text(st.profileName(id)).foregroundStyle(.primary) }
                            .swipeActions { Button("刪除") { clearing = id }.tint(.red) }
                    }
                    Button("＋ 新增離線好友（手動輸入班表）") { newName = ""; addingFriend = true }
                    Button("＋ 從病房助理工作站匯入同事班表") { wardFriends = true }
                } header: { Text("好友（點一下切換，左滑刪除）") }
            }
            .navigationTitle("班表管理")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar { ToolbarItem(placement: .confirmationAction) { Button("完成") { dismiss() } } }
            .alert("班表名稱（例如：醫院、兼職、家人）", isPresented: Binding(get: { renaming != nil }, set: { if !$0 { renaming = nil } })) {
                TextField("名稱", text: $newName)
                Button("儲存") { if let id = renaming { st.renameProfile(id, newName) } }
                Button("取消", role: .cancel) { }
            }
            .alert("新增一位好友", isPresented: $addingFriend) {
                TextField("請輸入朋友的名字", text: $newName)
                Button("加入") {
                    let n = newName.trimmingCharacters(in: .whitespaces)
                    if n.isEmpty { return }
                    profile = st.addFriend(n)
                    done("現在可以點月曆編輯「\(n)」的班表")
                    dismiss()
                }
                Button("取消", role: .cancel) { }
            }
            .alert(clearing.map { Store.isJob($0) ? "清除「\(st.profileName($0))」的所有資料（班表、備註）？" : "刪除好友「\(st.profileName($0))」和他的班表？" } ?? "",
                   isPresented: Binding(get: { clearing != nil }, set: { if !$0 { clearing = nil } })) {
                Button("刪除", role: .destructive) {
                    if let id = clearing {
                        if profile == id && !Store.isJob(id) { profile = Store.ME }
                        st.removeProfile(id)
                    }
                }
                Button("取消", role: .cancel) { }
            }
            .sheet(isPresented: $wardFriends) { WardFriendsView(year: year, month: month, done: done) }
        }
    }
}

// MARK: - 傳送班表（文字／截圖）

enum ShareHelper {
    static func text(_ st: Store, _ profile: String, _ y: Int, _ m: Int) -> String {
        var s = "\(st.profileName(profile)) \(y) 年 \(m) 月份班表：\n"
        for k in st.monthKeys(profile, y, m) {
            let d = Day(key: k)!
            s += "\(k.dropFirst(5))（\(d.weekdayName)）\(st.describe(profile, k))\n"
        }
        return s + "\n— 小柯班表助手"
    }

    @MainActor
    static func image(_ st: Store, _ profile: String, _ y: Int, _ m: Int) -> UIImage? {
        let v = VStack(alignment: .leading, spacing: 10) {
            Text("\(st.profileName(profile))　\(String(y)) 年 \(m) 月").font(.title2.bold())
            MonthGrid(year: y, month: m, profile: profile, selected: Day(1, 1, 1), interactive: false) { _ in }
        }
        .padding(16)
        .frame(width: 430)
        .background(Color(.systemGroupedBackground))
        .environmentObject(st)
        let r = ImageRenderer(content: v)
        r.scale = 3
        return r.uiImage
    }

    @MainActor
    static func present(_ items: [Any]) {
        guard let root = UIApplication.shared.connectedScenes.compactMap({ $0 as? UIWindowScene })
            .flatMap(\.windows).first(where: \.isKeyWindow)?.rootViewController else { return }
        var top = root
        while let p = top.presentedViewController { top = p }
        let vc = UIActivityViewController(activityItems: items, applicationActivities: nil)
        vc.popoverPresentationController?.sourceView = top.view
        top.present(vc, animated: true)
    }
}

// MARK: - 密碼

enum Password {
    /// 與 Android 版相同：SHA-256("xks:" + 密碼) 十六進位。
    static func hash(_ s: String) -> String {
        SHA256.hash(data: Data(("xks:" + s).utf8)).map { String(format: "%02x", $0) }.joined()
    }
}

struct LockView: View {
    @EnvironmentObject var st: Store
    @Binding var unlocked: Bool
    @State private var pw = ""
    @State private var wrong = false

    var body: some View {
        VStack(spacing: 20) {
            Image(systemName: "lock.fill").font(.system(size: 44)).foregroundStyle(.secondary)
            Text("請輸入密碼").font(.title2.bold())
            SecureField("密碼", text: $pw).keyboardType(.numberPad).textFieldStyle(.roundedBorder).frame(width: 200)
                .multilineTextAlignment(.center)
            if wrong { Text("密碼錯誤").foregroundStyle(.red) }
            Button("確定") {
                if Password.hash(pw) == st.pref("password", "") { unlocked = true } else { wrong = true }
                pw = ""
            }
            .buttonStyle(.borderedProminent)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(Color(.systemBackground))
    }
}

// MARK: - 使用說明

let helpText = """
(1) 點月曆某一天可排班、寫備註、記加班／借休／日津貼，並看農民曆與行事曆行程。

(2) 在月曆上按住並滑過一段日期（左右滑，或按住半秒後任意方向拖），放開即可一次設定這些日期的班別。「批次編輯」可點選多天，或複製成班表樣式再貼上。

(3) 「班表樣式」可設定循環班（例如 D,D,E,E,N,N,OFF,OFF）並「自動重覆」。

(4) 「匯入病房助理工作站月班表」直接讀月班表系統；代號規則與月班表系統相同（D 白班、E 小夜、N 大夜、OFF／國／休 休假、例 例假）。

(5) 「同步到行事曆」寫入 iPhone 行事曆（先在「設定 → 行事曆 → 帳號」加入 Google 帳號，就會同步到 Google 行事曆）。

(6) 「小柯機器人連線」：小柯跟 iPhone 連同一個 Wi-Fi，App 開著時可以查班表（網址在該頁）；鬧鐘關掉後小柯會唸出今天的班。

(7) 上方下拉選單可切換五份班表（我的、第二～第五班表，可改名）與好友。

(8) 「備份班表」產生 ZIP（可存到 iCloud 雲碟或 Google 雲端硬碟），Android 版的備份 ZIP 也能直接還原。
"""
