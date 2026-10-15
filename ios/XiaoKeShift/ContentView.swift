import SwiftUI

/// 主畫面：班表下拉、月份切換（‹ 今 ›，點標題選年月）、月曆、選取日說明卡（班別、備註、朋友班別、農民曆）。
struct ContentView: View {
    @EnvironmentObject var st: Store
    @State private var shownY = Day.today.y
    @State private var shownM = Day.today.m
    @State private var selected = Day.today
    @State private var profile = Store.ME
    @State private var editing: Day?
    @State private var pickingMonth = false
    @State private var panel: Panel?
    @State private var exportFile: ZipFile?
    @State private var importing = false
    @State private var pending: Backup.Pending?
    @State private var eventDays: Set<String> = []
    @AppStorage("eventDots") private var eventDots = false
    @State private var toast = ""
    @State private var multi = false
    @State private var picked: Set<String> = []
    @State private var dragging = false
    @State private var rangeKeys: RangeKeys?

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(spacing: 8) {
                    profileMenu
                    monthBar
                    MonthGrid(year: shownY, month: shownM, profile: profile, selected: selected,
                              multi: multi, picked: picked, eventDays: eventDays) { day in
                        if multi {
                            if picked.contains(day.key) { picked.remove(day.key) } else { picked.insert(day.key) }
                        } else {
                            selected = day
                            editing = day
                        }
                    } onRange: { keys in
                        if multi { picked.formUnion(keys) } else { rangeKeys = RangeKeys(keys: keys) }
                    } onDragging: { dragging = $0 }
                    if multi { batchBar }
                    Text(dayDetail(selected))
                        .font(.subheadline)
                        .lineSpacing(3)
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .padding(14)
                        .background(RoundedRectangle(cornerRadius: 12).fill(Color(.secondarySystemGroupedBackground)))
                }
                .padding(8)
            }
            .scrollDisabled(multi)
            .background(Color(.systemGroupedBackground))
            .navigationTitle("小柯班表助手")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) {
                    Menu {
                        Section {
                            Button("批次編輯", systemImage: "checklist") { startMulti(nil) }
                            Button("班別設定", systemImage: "square.grid.2x2") { panel = .types }
                            Button("班表樣式（複製／貼上／自動重覆）", systemImage: "repeat") { panel = .patterns }
                            Button("匯入病房助理工作站月班表", systemImage: "square.and.arrow.down") { panel = .ward }
                        }
                        Section {
                            Button("同步到行事曆（Google）", systemImage: "calendar.badge.plus") { panel = .calendar }
                            Button("開啟行事曆", systemImage: "calendar") { CalendarSync.open(selected) }
                            Button("鬧鐘設定", systemImage: "alarm") { panel = .alarms }
                            Button("小柯機器人連線", systemImage: "wifi") { panel = .xiaoke }
                        }
                        Section {
                            Button("統計（工時／加班／薪資）", systemImage: "chart.bar") { panel = .stats }
                            Button("查看所有備註記錄", systemImage: "note.text") { panel = .notes }
                            Button("特殊假日", systemImage: "flag") { panel = .holidays }
                            Button("發薪日", systemImage: "dollarsign.circle") { panel = .payday }
                            Button("班表管理（五份班表／改名／好友）", systemImage: "person.2") { panel = .profiles }
                        }
                        Section {
                            Menu("傳送班表", systemImage: "square.and.arrow.up") {
                                Button("純文字") { ShareHelper.present([ShareHelper.text(st, profile, shownY, shownM)]) }
                                Button("班表截圖") {
                                    if let img = ShareHelper.image(st, profile, shownY, shownM) { ShareHelper.present([img]) }
                                }
                            }
                            Button("備份班表", systemImage: "externaldrive") { exportBackup() }
                            Button("還原班表", systemImage: "arrow.counterclockwise") { importing = true }
                        }
                        Section {
                            Button("設定", systemImage: "gearshape") { panel = .settings }
                            Button("使用說明", systemImage: "questionmark.circle") { panel = .help }
                        }
                    } label: { Image(systemName: "ellipsis.circle") }
                }
            }
            .sheet(item: $editing) { day in
                DayEditor(day: day, profile: profile) { startMulti($0) }
            }
            .sheet(item: $rangeKeys) { r in
                SetShiftSheet(keys: r.keys, profile: profile) {
                    if multi { picked = [] }
                }
                .presentationDetents([.medium, .large])
            }
            .sheet(isPresented: $pickingMonth) {
                MonthPicker(year: $shownY, month: $shownM).presentationDetents([.height(300)])
            }
            .sheet(item: $panel) { p in
                switch p {
                case .types: ShiftTypesView()
                case .settings: SettingsView()
                case .ward: WardImportView(year: shownY, month: shownM, profile: profile) { toast = $0 }
                case .patterns: PatternsView(profile: profile, selected: selected)
                case .stats: StatsView(year: shownY, month: shownM, profile: profile)
                case .notes: AllNotesView(profile: profile) { go($0) }
                case .holidays: HolidaysView(year: shownY)
                case .payday: PaydayView(selected: selected)
                case .profiles: ProfilesView(profile: $profile, year: shownY, month: shownM) { toast = $0 }
                case .help: HelpView()
                case .calendar: CalendarSyncView(year: shownY, month: shownM) { toast = $0; loadEvents() }
                case .alarms: AlarmSettingsView()
                case .xiaoke: XiaoKeConnectView()
                }
            }
            .task(id: "\(shownY)-\(shownM)-\(eventDots)") { loadEvents() }
            .fileExporter(isPresented: Binding(get: { exportFile != nil }, set: { if !$0 { exportFile = nil } }),
                          document: exportFile, contentType: .zip,
                          defaultFilename: "小柯班表備份_\(Day.today.key).zip") { r in
                if case .failure(let e) = r { toast = "備份失敗：" + e.localizedDescription }
                else { st.set("lastBackup", Day.today.key); toast = "備份成功" }
            }
            .fileImporter(isPresented: $importing, allowedContentTypes: [.zip, .json, .data]) { r in
                do { pending = try Backup.read(r.get()) } catch { toast = "讀取失敗：" + error.localizedDescription }
            }
            .alert("還原", isPresented: Binding(get: { pending != nil }, set: { if !$0 { pending = nil } })) {
                Button("還原", role: .destructive) {
                    guard let p = pending else { return }
                    do { try Backup.apply(st, p); toast = "還原成功" } catch { toast = "還原失敗：" + error.localizedDescription }
                }
                Button("取消", role: .cancel) { }
            } message: {
                Text("備份檔含 \(pending?.images.count ?? 0) 張備註圖片。\n注意：現有資料會被備份資料所取代。確定要還原？")
            }
            .alert(toast, isPresented: Binding(get: { !toast.isEmpty }, set: { if !$0 { toast = "" } })) {
                Button("好") { }
            }
        }
    }

    private var profileMenu: some View {
        Menu {
            Picker("班表", selection: $profile) {
                ForEach(st.profileIds, id: \.self) { Text(st.profileName($0)).tag($0) }
            }
        } label: {
            HStack {
                Image(systemName: "person.crop.circle")
                Text(st.profileName(profile)).fontWeight(.semibold)
                Image(systemName: "chevron.up.chevron.down").font(.caption)
                Spacer()
            }
            .padding(.horizontal, 4)
        }
    }

    private var batchBar: some View {
        VStack(alignment: .leading, spacing: 6) {
            Text("批次編輯：已選 \(picked.count) 天（點選多天，或按住滑動選範圍）").font(.subheadline)
            HStack {
                Button("設定班別") { if !picked.isEmpty { rangeKeys = RangeKeys(keys: picked.sorted()) } }
                    .buttonStyle(.borderedProminent).disabled(picked.isEmpty)
                Button("清除") {
                    for k in picked { st.setCode(profile, k, "") }
                    st.save()
                    picked = []
                }
                .buttonStyle(.bordered).disabled(picked.isEmpty)
                Spacer()
                Button("複製成樣式") { toast = st.copyAsPattern(profile, picked.sorted()) }
                    .buttonStyle(.bordered).disabled(picked.isEmpty)
                Button("完成") { multi = false; picked = [] }.buttonStyle(.bordered)
            }
        }
        .padding(10)
        .background(RoundedRectangle(cornerRadius: 12).fill(Color(.secondarySystemGroupedBackground)))
    }

    private func loadEvents() {
        eventDays = eventDots ? CalendarSync.eventDays(shownY, shownM) : []
    }

    private func go(_ d: Day) {
        shownY = d.y; shownM = d.m; selected = d
    }

    private func exportBackup() {
        do {
            let (zip, _) = try Backup.export(st)
            exportFile = ZipFile(data: zip)
        } catch { toast = "備份失敗：" + error.localizedDescription }
    }

    private func startMulti(_ first: Day?) {
        multi = true
        picked = first.map { [$0.key] } ?? []
    }

    private var monthBar: some View {
        HStack {
            Button { move(-1) } label: { Image(systemName: "chevron.left").font(.title2).frame(width: 44, height: 36) }
            Spacer()
            Button { pickingMonth = true } label: {
                Text("\(String(shownY)) 年 \(shownM) 月").font(.title2.bold()).foregroundStyle(.primary)
            }
            Spacer()
            Button("今") {
                shownY = Day.today.y; shownM = Day.today.m; selected = Day.today
            }
            .font(.title3).frame(width: 44, height: 36)
            Button { move(1) } label: { Image(systemName: "chevron.right").font(.title2).frame(width: 44, height: 36) }
        }
    }

    private func move(_ delta: Int) {
        let t = shownY * 12 + (shownM - 1) + delta
        shownY = t / 12
        shownM = t % 12 + 1
    }

    /// 選取日的班別＋農民曆＋朋友今日班別。
    private func dayDetail(_ c: Day) -> String {
        let k = c.key
        var s = "\(c.y)年\(c.m)月\(c.d)日 星期\(c.weekdayName)"
        let hol = Holidays.name(st, c)
        if !hol.isEmpty { s += "　" + hol }
        if Holidays.isPayday(st, c) { s += "　💰發薪日" }
        s += "\n\(st.profileName(profile))：\(st.describe(profile, k))"
        if let t = st.classify(st.code(profile, k)), !t.desc.isEmpty { s += "\n班別說明：" + t.desc }
        let note = st.note(profile, k)
        if !note.isEmpty { s += "\n備註：" + note }
        if profile == Store.ME {
            let ot = st.extra("ot", k), to = st.extra("to", k), al = st.extra("allow", k)
            if ot != 0 { s += "\n加班：\(num(ot)) 小時" }
            if to != 0 { s += "\n借休：\(num(to)) 小時" }
            if al != 0 { s += "\n日津貼：\(num(al))" }
            // 朋友今日的班別（休假不顯示）
            var f = ""
            for id in st.profileIds where id != Store.ME {
                let raw = st.code(id, k)
                if raw.isEmpty || st.classify(raw)?.off == true { continue }
                f += "\n　\(st.profileName(id))：\(st.describe(id, k))"
            }
            if !f.isEmpty { s += "\n\n【朋友的班別】" + f }
        }
        s += "\n\n" + almanacText(c)
        return s
    }
}

/// 【農民曆】區塊（主畫面卡片與單日編輯共用）。
func almanacText(_ c: Day) -> String {
    let a = Almanac.compute(c)
    var s = "【農民曆】農曆" + Almanac.lunarString(c)
    if a.ok {
        s += "\n\(a.yearGZ)年（\(a.zodiac)）\(a.monthGZ)月 \(a.dayGZ)日　\(a.jianchu)日"
        if !a.termToday.isEmpty { s += "　今日" + a.termToday }
        s += "\n宜：\(a.yi)\n忌：\(a.ji)\n\(a.chong)　煞\(a.sha)\n下一節氣：\(a.nextTerm)"
    }
    return s
}

func num(_ v: Double) -> String {
    v == 0 ? "" : v == v.rounded() ? String(Int(v)) : String(v)
}

/// 點標題「前往」某年某月。
struct MonthPicker: View {
    @Binding var year: Int
    @Binding var month: Int
    @Environment(\.dismiss) private var dismiss
    @State private var y = 2026
    @State private var m = 1

    var body: some View {
        NavigationStack {
            HStack(spacing: 0) {
                Picker("年", selection: $y) { ForEach(2000...2099, id: \.self) { Text("\(String($0)) 年").tag($0) } }
                    .pickerStyle(.wheel)
                Picker("月", selection: $m) { ForEach(1...12, id: \.self) { Text("\($0) 月").tag($0) } }
                    .pickerStyle(.wheel)
            }
            .navigationTitle("前往")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("取消") { dismiss() } }
                ToolbarItem(placement: .confirmationAction) { Button("前往") { year = y; month = m; dismiss() } }
            }
        }
        .onAppear { y = year; m = month }
    }
}

struct RangeKeys: Identifiable {
    let keys: [String]
    var id: String { keys.joined(separator: ",") }
}

/// 把一段日期設成同一個班別（批次編輯「設定班別」與按壓滑動選取共用）。
struct SetShiftSheet: View {
    @EnvironmentObject var st: Store
    @Environment(\.dismiss) private var dismiss
    let keys: [String]
    let profile: String
    var done: () -> Void = { }
    @State private var askCustom = false
    @State private var customText = ""

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 10) {
                    Text(rangeText).font(.subheadline).foregroundStyle(.secondary)
                    ShiftButtons(selected: "", onPick: { apply($0) }, custom: { customText = ""; askCustom = true }, plain: true)
                }
                .padding()
            }
            .navigationTitle("把 \(keys.count) 天設成")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar { ToolbarItem(placement: .cancellationAction) { Button("取消") { dismiss() } } }
            .alert("自訂班別代號", isPresented: $askCustom) {
                TextField("例如 D/洗、傷照", text: $customText)
                Button("確定") {
                    let c = customText.trimmingCharacters(in: .whitespaces)
                    if !c.isEmpty { apply(c) }
                }
                Button("取消", role: .cancel) { }
            }
        }
    }

    private var rangeText: String {
        guard let a = keys.first, let b = keys.last else { return "" }
        let f = { (k: String) in String(k.dropFirst(5)).replacingOccurrences(of: "-", with: "/") }
        return a == b ? f(a) : "\(f(a)) ～ \(f(b))（\(st.profileName(profile))）"
    }

    private func apply(_ code: String) {
        for k in keys { st.setCode(profile, k, code) }
        st.save()
        done()
        dismiss()
    }
}

enum Panel: String, Identifiable {
    case types, settings, ward, patterns, stats, notes, holidays, payday, profiles, help, calendar, alarms, xiaoke
    var id: String { rawValue }
}

struct HelpView: View {
    @Environment(\.dismiss) private var dismiss
    var body: some View {
        NavigationStack {
            ScrollView { Text(helpText).padding() }
                .navigationTitle("小柯班表助手 使用提示")
                .navigationBarTitleDisplayMode(.inline)
                .toolbar { ToolbarItem(placement: .confirmationAction) { Button("知道了") { dismiss() } } }
        }
    }
}
