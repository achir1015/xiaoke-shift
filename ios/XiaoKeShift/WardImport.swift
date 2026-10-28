import SwiftUI
import CryptoKit

/// 病房助理工作站「月班表」匯入（Firebase Firestore REST，免金鑰讀取）。與 Android 版 WardImport 相同：
/// 月班表系統（achir1015/rcw-month）分成「公司＋部門」單位，每個單位有自己的管理者密碼：
///   orgs/{單位ID} = { company, department, passwordHash = SHA-256("rcw-org:" + 密碼) 小寫十六進位 }
///   原始單位 default（忠孝院區／呼吸照護病房）文件 ID 不加前綴：schedules/{assistant|nurse}-YYYY-MM
///   其他單位：schedules/{單位ID}__{assistant|nurse}-YYYY-MM
///   fields.employees.arrayValue.values[].mapValue.fields = { name, shifts{ "1": "D1", ... } }
/// 選單位並通過密碼後才能匯入。
enum WardImport {
    static let PROJECT = "monthlyschedule-a5dea"
    static let BASE = "https://firestore.googleapis.com/v1/projects/\(PROJECT)/databases/(default)/documents/"
    static let CATS = ["assistant", "nurse"]
    static let LABELS = ["病房助理", "護理師"]
    static let LEGACY_ORG = "default"

    /// 一位員工一個月的班：日 → 原代號。
    struct Employee: Hashable {
        var name: String
        var shifts: [Int: String]
    }

    /// 單位（公司＋部門）。
    struct Org: Hashable, Identifiable {
        var id: String
        var company: String
        var department: String
        var passwordHash: String
        var isLegacy: Bool { id == WardImport.LEGACY_ORG }
        var label: String { "\(company)／\(department)" }
    }

    /// 與月班表系統相同的密碼雜湊：SHA-256("rcw-org:" + 密碼)，小寫十六進位。
    static func hash(_ pw: String) -> String {
        SHA256.hash(data: Data(("rcw-org:" + pw).utf8)).map { String(format: "%02x", $0) }.joined()
    }

    private static func get(_ url: String) async throws -> (Data, Int) {
        var req = URLRequest(url: URL(string: url)!)
        req.timeoutInterval = 15
        let (body, resp) = try await URLSession.shared.data(for: req)
        return (body, (resp as? HTTPURLResponse)?.statusCode ?? 0)
    }

    /// 單位清單（orgs 集合），原始單位排第一。清單是空的代表月班表系統還沒建立單位。
    static func orgs() async throws -> [Org] {
        var out: [Org] = []
        var token = ""
        repeat {
            var url = BASE + "orgs?pageSize=300"
            if !token.isEmpty { url += "&pageToken=" + (token.addingPercentEncoding(withAllowedCharacters: .alphanumerics) ?? token) }
            let (body, code) = try await get(url)
            if code != 200 { throw msg("讀取單位清單失敗（HTTP \(code)）") }
            let root = (try? JSONSerialization.jsonObject(with: body)) as? [String: Any] ?? [:]
            for d in root["documents"] as? [[String: Any]] ?? [] {
                let f = d.obj("fields")
                let g = Org(id: String(d.str("name").split(separator: "/").last ?? ""),
                            company: f.obj("company").str("stringValue"),
                            department: f.obj("department").str("stringValue"),
                            passwordHash: f.obj("passwordHash").str("stringValue"))
                if g.isLegacy { out.insert(g, at: 0) } else { out.append(g) }
            }
            token = root.str("nextPageToken")
        } while !token.isEmpty
        if out.isEmpty { throw msg("月班表系統尚未建立單位，請先登入月班表網頁一次") }
        return out
    }

    static func docId(_ org: Org, _ cat: String, _ y: Int, _ m: Int) -> String {
        let base = String(format: "%@-%04d-%02d", cat, y, m)
        return org.isLegacy ? base : org.id + "__" + base
    }

    static func fetch(_ org: Org, _ cat: String, _ y: Int, _ m: Int) async throws -> [Employee] {
        let (body, code) = try await get(BASE + "schedules/" + docId(org, cat, y, m))
        if code == 404 { throw msg("\(org.department) \(y)年\(m)月班表尚未建立") }
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

/// 選擇單位（公司＋部門）＋管理者密碼。通過才呼叫 onPass。
/// 「記住密碼」＝把通過時的雜湊存在 orgOk:<單位ID>；雲端雜湊沒變就直接通過，管理者改密碼後會重新詢問（同 Android）。
struct OrgGate: View {
    @EnvironmentObject var st: Store
    let profile: String
    var force = false
    let onPass: (WardImport.Org) -> Void

    @State private var orgs: [WardImport.Org] = []
    @State private var orgId = ""
    @State private var password = ""
    @State private var remember = true
    @State private var status = ""
    @State private var loading = true

    var body: some View {
        Form {
            Section {
                Text("請選擇公司／部門，並輸入該單位的管理者密碼（與月班表系統相同）").font(.subheadline)
            }
            if loading {
                HStack { ProgressView(); Text("讀取單位清單中…").foregroundStyle(.secondary) }
            } else if orgs.isEmpty {
                Text(status).foregroundStyle(.red)
            } else {
                Section {
                    Picker("單位", selection: $orgId) {
                        ForEach(orgs) { Text($0.label).tag($0.id) }
                    }
                    SecureField("管理者密碼", text: $password).textContentType(.password).onSubmit(enter)
                    Toggle("記住密碼（這支手機下次不用再輸入）", isOn: $remember)
                } footer: {
                    if !status.isEmpty { Text(status).foregroundStyle(.red) }
                }
                Section { Button("進入", action: enter).bold().frame(maxWidth: .infinity) }
            }
        }
        .task { await load() }
    }

    private func load() async {
        do {
            let got = try await WardImport.orgs()
            let last = st.pref(WardImport.prefKey("wardOrg", profile), st.pref("wardOrg", WardImport.LEGACY_ORG))
            if !force, let g = got.first(where: { $0.id == last }), !g.passwordHash.isEmpty,
               g.passwordHash.caseInsensitiveCompare(st.pref("orgOk:" + g.id, "")) == .orderedSame {
                onPass(g)
                return
            }
            orgs = got
            orgId = got.first(where: { $0.id == last })?.id ?? got[0].id
        } catch is CancellationError {
            return
        } catch {
            status = error.localizedDescription
        }
        loading = false
    }

    private func enter() {
        guard let g = orgs.first(where: { $0.id == orgId }) else { return }
        guard !g.passwordHash.isEmpty, WardImport.hash(password).caseInsensitiveCompare(g.passwordHash) == .orderedSame else {
            status = "密碼錯誤"
            password = ""
            return
        }
        st.prefs.set(remember ? g.passwordHash : "", forKey: "orgOk:" + g.id)
        st.prefs.set(g.id, forKey: WardImport.prefKey("wardOrg", profile))
        st.prefs.set(g.id, forKey: "wardOrg")
        onPass(g)
    }
}

/// 匯入工作站月班表：選單位＋密碼 → 類別 → 背景讀名單 → 姓名下拉選單 → 匯入到目前的班表。
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
    @State private var org: WardImport.Org?
    @State private var forceOrg = false

    var body: some View {
        NavigationStack {
            Group {
                if let org { form(org) } else { OrgGate(profile: profile, force: forceOrg) { org = $0 } }
            }
            .navigationTitle(org == nil ? "選擇單位" : "匯入工作站月班表")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("取消") { dismiss() } }
                if org != nil {
                    ToolbarItem(placement: .confirmationAction) {
                        Button("匯入") { importNow() }.bold().disabled(loading || list.isEmpty)
                    }
                }
            }
            .task(id: "\(org?.id ?? "")|\(cat)") { if org != nil { await load() } }
        }
        .onAppear { cat = st.pref(WardImport.prefKey("wardCat", profile), "assistant") }
    }

    private func form(_ org: WardImport.Org) -> some View {
            Form {
                Section {
                    Text("單位：\(org.label)\n讀取 \(String(year)) 年 \(month) 月（目前顯示的月份）\n匯入到：\(st.profileName(profile))")
                        .font(.subheadline)
                    Button("切換單位") { forceOrg = true; self.org = nil; list = [] }
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
    }

    private func load() async {
        guard let org else { return }
        loading = true
        status = ""
        list = []
        do {
            let got = try await WardImport.fetch(org, cat, year, month)
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
    @State private var org: WardImport.Org?
    @State private var forceOrg = false

    var body: some View {
        NavigationStack {
            Group {
                if let org { listView(org) } else { OrgGate(profile: "friends", force: forceOrg) { org = $0 } }
            }
            .navigationTitle(org == nil ? "選擇單位" : "從工作站匯入同事")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("取消") { dismiss() } }
                if org != nil {
                    ToolbarItem(placement: .confirmationAction) {
                        Button("匯入") { importNow() }.bold().disabled(loading || on.isEmpty)
                    }
                }
            }
            .task(id: "\(org?.id ?? "")|\(cat)") { if org != nil { await load() } }
        }
    }

    private func listView(_ org: WardImport.Org) -> some View {
            List {
                Section {
                    Text("單位：\(org.label)").font(.subheadline)
                    Button("切換單位") { forceOrg = true; self.org = nil; list = [] }
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
    }

    private func load() async {
        guard let org else { return }
        loading = true
        status = ""
        list = []
        do {
            list = try await WardImport.fetch(org, cat, year, month)
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
