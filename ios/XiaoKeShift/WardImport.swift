import SwiftUI

/// 病房助理工作站「月班表」匯入（Firebase Firestore REST，免金鑰讀取）。與 Android 版 WardImport 讀同一份文件：
///   projects/monthlyschedule-a5dea/.../documents/schedules/{assistant|nurse}-YYYY-MM
///   fields.employees.arrayValue.values[].mapValue.fields = { name, shifts{ "1": "D1", ... } }
enum WardImport {
    static let PROJECT = "monthlyschedule-a5dea"
    static let CATS = ["assistant", "nurse"]
    static let LABELS = ["病房助理", "護理師"]

    /// 一位員工一個月的班：日 → 原代號。
    struct Employee: Hashable {
        var name: String
        var shifts: [Int: String]
    }

    static func fetch(_ cat: String, _ y: Int, _ m: Int) async throws -> [Employee] {
        let doc = String(format: "%@-%04d-%02d", cat, y, m)
        let url = URL(string: "https://firestore.googleapis.com/v1/projects/\(PROJECT)/databases/(default)/documents/schedules/\(doc)")!
        var req = URLRequest(url: url)
        req.timeoutInterval = 15
        let (body, resp) = try await URLSession.shared.data(for: req)
        let code = (resp as? HTTPURLResponse)?.statusCode ?? 0
        if code == 404 { throw msg("\(y)年\(m)月班表尚未建立") }
        if code != 200 { throw msg("讀取失敗（HTTP \(code)）") }

        let root = (try? JSONSerialization.jsonObject(with: body)) as? [String: Any] ?? [:]
        let emps = root.obj("fields").obj("employees").obj("arrayValue")["values"] as? [[String: Any]] ?? []
        return emps.compactMap { v in
            let f = v.obj("mapValue").obj("fields")
            let name = f.obj("name").str("stringValue").trimmingCharacters(in: .whitespaces)
            let days = f.obj("shifts").obj("mapValue").obj("fields")
            var shifts: [Int: String] = [:]
            for d in 1...31 {
                let s = days.obj(String(d)).str("stringValue").trimmingCharacters(in: .whitespaces)
                if !s.isEmpty { shifts[d] = s }
            }
            return name.isEmpty ? nil : Employee(name: name, shifts: shifts)
        }
    }

    private static func msg(_ s: String) -> NSError {
        NSError(domain: "WardImport", code: 1, userInfo: [NSLocalizedDescriptionKey: s])
    }

    /// 寫入班表；overwrite=false 時只填空白的日子。回傳寫入天數。
    @discardableResult
    static func apply(_ s: Store, _ profile: String, _ e: Employee, _ y: Int, _ m: Int, overwrite: Bool) -> Int {
        var n = 0
        for (d, code) in e.shifts where d <= Day.daysIn(y, m) {
            let k = Day(y, m, d).key
            if !overwrite && !s.explicit(profile, k).isEmpty { continue }
            s.setCode(profile, k, code)
            n += 1
        }
        s.save()
        return n
    }

    /// 每份班表各自記住上次匯入的類別與姓名（我的班表沿用舊的 key，同 Android）。
    static func prefKey(_ base: String, _ profile: String) -> String { profile == Store.ME ? base : base + ":" + profile }
}

/// 匯入工作站月班表：類別 → 背景讀名單 → 姓名下拉選單 → 匯入到目前的班表。
struct WardImportView: View {
    @EnvironmentObject var st: Store
    @Environment(\.dismiss) private var dismiss
    let year: Int, month: Int
    let profile: String
    let done: (String) -> Void

    @State private var cat = "assistant"
    @State private var list: [WardImport.Employee] = []
    @State private var name = ""
    @State private var status = ""
    @State private var loading = false
    @State private var overwrite = true

    var body: some View {
        NavigationStack {
            Form {
                Section {
                    Text("讀取 \(String(year)) 年 \(month) 月（目前顯示的月份）\n匯入到：\(st.profileName(profile))")
                        .font(.subheadline)
                }
                Section {
                    Picker("類別", selection: $cat) {
                        ForEach(0..<2, id: \.self) { Text(WardImport.LABELS[$0]).tag(WardImport.CATS[$0]) }
                    }
                    .pickerStyle(.segmented)
                    if loading {
                        HStack { ProgressView(); Text("讀取名單中…").foregroundStyle(.secondary) }
                    } else if list.isEmpty {
                        Text(status.isEmpty ? "這個月的班表沒有人員資料" : status).foregroundStyle(.red)
                    } else {
                        Picker("姓名", selection: $name) {
                            ForEach(list, id: \.name) { Text($0.name).tag($0.name) }
                        }
                    }
                    Toggle("覆蓋已排的日子", isOn: $overwrite)
                } footer: {
                    if profile != Store.ME && Store.isJob(profile) { Text("匯入後，這份班表的名稱會改成所選的姓名。") }
                }
            }
            .navigationTitle("匯入工作站月班表")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("取消") { dismiss() } }
                ToolbarItem(placement: .confirmationAction) {
                    Button("匯入") { importNow() }.bold().disabled(loading || list.isEmpty)
                }
            }
            .task(id: cat) { await load() }
        }
        .onAppear { cat = st.pref(WardImport.prefKey("wardCat", profile), "assistant") }
    }

    private func load() async {
        loading = true
        status = ""
        list = []
        do {
            let got = try await WardImport.fetch(cat, year, month)
            list = got
            let last = st.pref(WardImport.prefKey("wardName", profile), "")
            name = got.first { $0.name == last }?.name
                ?? got.first { last.isEmpty && $0.name == st.profileName(profile) }?.name
                ?? got.first?.name ?? ""
        } catch is CancellationError {
            return
        } catch {
            if (error as NSError).code == NSURLErrorCancelled { return }
            status = error.localizedDescription
        }
        loading = false
    }

    private func importNow() {
        guard let e = list.first(where: { $0.name == name }) else { return }
        st.prefs.set(cat, forKey: WardImport.prefKey("wardCat", profile))
        st.prefs.set(e.name, forKey: WardImport.prefKey("wardName", profile))
        let n = WardImport.apply(st, profile, e, year, month, overwrite: overwrite)
        // 第二～第五班表：以匯入的姓名當班表名稱（我的班表維持原名）
        if Store.isJob(profile) && profile != Store.ME { st.renameProfile(profile, e.name) }
        done("已匯入 \(e.name) \(month) 月班表 \(n) 天")
        dismiss()
    }
}

/// 從工作站名單勾選同事，加成好友並匯入本月班表。
struct WardFriendsView: View {
    @EnvironmentObject var st: Store
    @Environment(\.dismiss) private var dismiss
    let year: Int, month: Int
    let done: (String) -> Void

    @State private var cat = "assistant"
    @State private var list: [WardImport.Employee] = []
    @State private var on: Set<String> = []
    @State private var status = ""
    @State private var loading = false

    var body: some View {
        NavigationStack {
            List {
                Section {
                    Picker("類別", selection: $cat) {
                        ForEach(0..<2, id: \.self) { Text(WardImport.LABELS[$0]).tag(WardImport.CATS[$0]) }
                    }
                    .pickerStyle(.segmented)
                }
                Section("勾選要加入的好友（\(month) 月）") {
                    if loading {
                        HStack { ProgressView(); Text("讀取名單中…").foregroundStyle(.secondary) }
                    } else if list.isEmpty {
                        Text(status.isEmpty ? "這個月的班表沒有人員資料" : status).foregroundStyle(.red)
                    }
                    ForEach(list, id: \.name) { e in
                        Button {
                            if on.contains(e.name) { on.remove(e.name) } else { on.insert(e.name) }
                        } label: {
                            HStack {
                                Image(systemName: on.contains(e.name) ? "checkmark.circle.fill" : "circle")
                                    .foregroundStyle(on.contains(e.name) ? Color.accentColor : .secondary)
                                Text(e.name).foregroundStyle(.primary)
                            }
                        }
                    }
                }
            }
            .navigationTitle("從工作站匯入同事")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("取消") { dismiss() } }
                ToolbarItem(placement: .confirmationAction) {
                    Button("匯入") { importNow() }.bold().disabled(loading || on.isEmpty)
                }
            }
            .task(id: cat) { await load() }
        }
    }

    private func load() async {
        loading = true
        status = ""
        list = []
        do {
            list = try await WardImport.fetch(cat, year, month)
            on = Set(list.map(\.name).filter { st.friendIds.contains("f:" + $0) })
        } catch is CancellationError {
            return
        } catch {
            if (error as NSError).code == NSURLErrorCancelled { return }
            status = error.localizedDescription
        }
        loading = false
    }

    private func importNow() {
        var n = 0
        for e in list where on.contains(e.name) {
            let id = st.addFriend(e.name)
            WardImport.apply(st, id, e, year, month, overwrite: true)
            n += 1
        }
        done("已匯入 \(n) 位同事的班表，可在上方下拉選單切換")
        dismiss()
    }
}
