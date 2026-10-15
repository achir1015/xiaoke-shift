import SwiftUI

/// 班表樣式：循環代號、起算日、自動重覆 from～to、貼上 N 天或到某日、重新讀取月曆、刪除。
struct PatternsView: View {
    @EnvironmentObject var st: Store
    @Environment(\.dismiss) private var dismiss
    let profile: String
    let selected: Day
    @State private var editing: Pattern?

    var body: some View {
        NavigationStack {
            List {
                Section(footer: Text("例如 D,D,E,E,N,N,OFF,OFF。勾「自動重覆」後，期間內沒手動排班的日子自動套用（月曆上顏色較淡）。批次編輯選好日子後也可「複製成樣式」。")) {
                    ForEach(st.patterns) { p in
                        Button { editing = p } label: {
                            VStack(alignment: .leading, spacing: 2) {
                                Text(p.name + (p.auto ? "　🔁自動重覆" : "")).foregroundStyle(.primary)
                                Text("\(p.seq.joined(separator: ","))（\(p.seq.count) 天）　\(st.profileName(p.profile))")
                                    .font(.caption).foregroundStyle(.secondary).lineLimit(2)
                            }
                        }
                    }
                    .onDelete { st.patterns.remove(atOffsets: $0); st.save() }
                    Button("＋ 建立班表樣式") {
                        var p = Pattern()
                        p.name = "我的班表樣式"
                        p.profile = profile
                        p.start = selected.key
                        p.seq = st.pref("pattern", "D,D,E,E,N,N,OFF,OFF").split(separator: ",").map(String.init)
                        editing = p
                    }
                }
            }
            .navigationTitle("班表樣式")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar { ToolbarItem(placement: .confirmationAction) { Button("完成") { dismiss() } } }
            .sheet(item: $editing) { p in PatternEditor(pattern: p, selected: selected) }
        }
    }
}

struct PatternEditor: View {
    @EnvironmentObject var st: Store
    @Environment(\.dismiss) private var dismiss
    @State var pattern: Pattern
    let selected: Day
    @State private var seqText = ""
    @State private var start = Date()
    @State private var useFrom = false
    @State private var useTo = false
    @State private var from = Date()
    @State private var to = Date()
    @State private var pasteEnd = Date()
    @State private var message = ""

    private var isNew: Bool { !st.patterns.contains { $0.id == pattern.id } }

    var body: some View {
        NavigationStack {
            Form {
                Section {
                    TextField("樣式名稱", text: $pattern.name)
                    TextField("循環班別（逗號分隔）", text: $seqText, axis: .vertical)
                        .textInputAutocapitalization(.characters).autocorrectionDisabled()
                } footer: {
                    Text("可用代號：" + st.types.map { "\($0.code)=\($0.name)" }.joined(separator: "　"))
                }
                Section {
                    DatePicker("樣式起算日", selection: $start, displayedComponents: .date)
                    Toggle("🔁 自動重覆", isOn: $pattern.auto)
                    if pattern.auto {
                        Toggle("指定自動重覆起始日", isOn: $useFrom)
                        if useFrom { DatePicker("起始日", selection: $from, displayedComponents: .date) }
                        Toggle("指定自動重覆結束日", isOn: $useTo)
                        if useTo { DatePicker("結束日", selection: $to, displayedComponents: .date) }
                    }
                } footer: { Text("套用到：\(st.profileName(pattern.profile))。沒指定起始日＝從起算日；沒指定結束日＝無限。") }

                Section("貼上（覆蓋月曆上已排的班）") {
                    Button("從 \(selected.key) 貼上 1 輪（\(seq.count) 天）") { paste(days: seq.count) }
                    DatePicker("貼到", selection: $pasteEnd, displayedComponents: .date)
                    Button("從 \(selected.key) 貼上到上面的日期") {
                        let end = day(pasteEnd)
                        if end < selected { message = "結束日不正確"; return }
                        paste(days: end.ordinal - selected.ordinal + 1)
                    }
                    Button("重新讀取月曆上的班別（從起算日起 \(seq.count) 天）") { reread() }
                }
                if !message.isEmpty { Section { Text(message).foregroundStyle(.blue) } }
                if !isNew {
                    Section {
                        Button("刪除這個樣式", role: .destructive) {
                            st.patterns.removeAll { $0.id == pattern.id }
                            st.save()
                            dismiss()
                        }
                    }
                }
            }
            .navigationTitle(isNew ? "建立班表樣式" : "編輯班表樣式")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("取消") { dismiss() } }
                ToolbarItem(placement: .confirmationAction) { Button("儲存") { save(); dismiss() }.bold() }
            }
        }
        .onAppear {
            seqText = pattern.seq.joined(separator: ",")
            start = date(Day(key: pattern.start) ?? selected)
            useFrom = !pattern.from.isEmpty
            useTo = !pattern.to.isEmpty
            from = date(Day(key: pattern.from) ?? selected)
            to = date(Day(key: pattern.to) ?? selected.adding(90))
            let m = selected.m == 12 ? Day(selected.y + 1, 1, 1) : Day(selected.y, selected.m + 1, 1)
            pasteEnd = date(Day(m.y, m.m, min(selected.d, Day.daysIn(m.y, m.m))))
        }
    }

    private var seq: [String] {
        seqText.replacingOccurrences(of: "，", with: ",").split(separator: ",")
            .map { $0.trimmingCharacters(in: .whitespaces) }.filter { !$0.isEmpty }
    }

    private func day(_ d: Date) -> Day {
        let c = Calendar.current.dateComponents([.year, .month, .day], from: d)
        return Day(c.year!, c.month!, c.day!)
    }

    private func date(_ d: Day) -> Date {
        Calendar.current.date(from: DateComponents(year: d.y, month: d.m, day: d.d)) ?? Date()
    }

    /// 存回 st.patterns（儲存與貼上前都會呼叫）。
    private func save() {
        pattern.name = pattern.name.trimmingCharacters(in: .whitespaces).isEmpty ? "未命名" : pattern.name
        pattern.seq = seq
        pattern.start = day(start).key
        pattern.from = pattern.auto && useFrom ? day(from).key : ""
        pattern.to = pattern.auto && useTo ? day(to).key : ""
        if let i = st.patterns.firstIndex(where: { $0.id == pattern.id }) { st.patterns[i] = pattern } else { st.patterns.append(pattern) }
        st.set("pattern", pattern.seq.joined(separator: ","))
        st.save()
        if pattern.auto && overlap() { message = "注意：至少兩個班表樣式的自動重複期間是重疊的" }
    }

    private func overlap() -> Bool {
        let p = pattern
        for q in st.patterns where q.id != p.id && q.auto && q.profile == p.profile {
            let a1 = p.from.isEmpty ? p.start : p.from, a2 = p.to.isEmpty ? "9999" : p.to
            let b1 = q.from.isEmpty ? q.start : q.from, b2 = q.to.isEmpty ? "9999" : q.to
            if a1 <= b2 && b1 <= a2 { return true }
        }
        return false
    }

    private func paste(days: Int) {
        save()
        guard !seq.isEmpty else { message = "請先輸入循環班別"; return }
        for i in 0..<days { st.setCode(pattern.profile, selected.adding(i).key, seq[i % seq.count]) }
        st.save()
        message = "已貼上 \(selected.key) ～ \(selected.adding(days - 1).key)"
    }

    private func reread() {
        let n = max(seq.count, 1)
        let s = day(start)
        seqText = (0..<n).map { i -> String in
            let c = st.explicit(pattern.profile, s.adding(i).key)
            return c.isEmpty ? "OFF" : c
        }.joined(separator: ",")
        save()
        message = "已用月曆上的班別更新樣式：" + seqText
    }
}

extension Store {
    /// 批次編輯選取的日子 → 複製成樣式。
    func copyAsPattern(_ profile: String, _ keys: [String]) -> String {
        guard let first = keys.first.flatMap({ Day(key: $0) }), let last = keys.last.flatMap({ Day(key: $0) }) else { return "" }
        var p = Pattern()
        p.profile = profile
        p.start = first.key
        let n = last.ordinal - first.ordinal + 1
        p.seq = (0..<n).map { i in
            let c = explicit(profile, first.adding(i).key)
            return c.isEmpty ? "OFF" : c
        }
        p.name = "\(n) 天的班表樣式（\(first.key.dropFirst(5))～\(last.key.dropFirst(5))）"
        patterns.append(p)
        save()
        return "已複製成「\(p.name)」。選一個起始日後，到「班表樣式」點它 → 貼上"
    }
}
