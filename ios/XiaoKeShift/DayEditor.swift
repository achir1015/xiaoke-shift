import SwiftUI

/// 單日編輯：班別按鈕（隱藏的不顯示）、自訂代號、清除、備註、加班/借休/日津貼（只有我的班表）、自訂特殊假日。
struct DayEditor: View {
    @EnvironmentObject var st: Store
    @Environment(\.dismiss) private var dismiss
    let day: Day
    let profile: String
    var onBatch: ((Day) -> Void)? = nil

    @State private var pick = ""
    @State private var note = ""
    @State private var ot = ""
    @State private var to = ""
    @State private var allow = ""
    @State private var holiday = ""
    @State private var askCustom = false
    @State private var customText = ""
    @State private var showNote = false

    private var me: Bool { profile == Store.ME }

    var body: some View {
        NavigationStack {
            Form {
                Section {
                    Text(current).font(.headline)
                    ShiftButtons(selected: pick) { pick = $0 } custom: {
                        customText = st.type(pick) == nil ? pick : ""
                        askCustom = true
                    }
                }
                Section("備註") {
                    TextField("備註（例如：換班、教學、會議）", text: $note, axis: .vertical)
                    Button { saveFields(); showNote = true } label: { Text(richLabel) }
                }
                if me {
                    Section("我的加班／借休／日津貼") {
                        numberField("加班（小時）", $ot)
                        numberField("借休（小時）", $to)
                        numberField("日津貼（金額）", $allow)
                    }
                }
                Section("自訂特殊假日") {
                    TextField("假日名稱（空白＝無）", text: $holiday)
                }
                if let onBatch {
                    Section {
                        Button("批次編輯（從這天開始選多天）") { dismiss(); onBatch(day) }
                    }
                }
                Section {
                    Text(almanacText(day)).font(.footnote).foregroundStyle(.secondary)
                }
                if CalendarSync.authorized {
                    Section("行事曆") {
                        let ev = CalendarSync.eventsOn(day)
                        if ev.isEmpty { Text("沒有任何事件").font(.footnote).foregroundStyle(.secondary) }
                        ForEach(ev, id: \.self) { Text($0).font(.footnote) }
                    }
                }
            }
            .navigationTitle("\(day.m)月\(day.d)日（\(day.weekdayName)）農曆\(Almanac.lunarString(day))")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("取消") { dismiss() } }
                ToolbarItem(placement: .confirmationAction) { Button("儲存") { save() }.bold() }
            }
            .alert("自訂班別代號", isPresented: $askCustom) {
                TextField("例如 D/洗、傷照", text: $customText)
                Button("確定") { pick = customText.trimmingCharacters(in: .whitespaces) }
                Button("取消", role: .cancel) { }
            }
            .navigationDestination(isPresented: $showNote) { NoteView(day: day, profile: profile) }
            .onChange(of: showNote) { _, on in if !on { note = st.noteText(profile, day.key) } }
        }
        .onAppear(perform: load)
    }

    private var current: String {
        if pick.isEmpty {
            let auto = st.code(profile, day.key)
            return "選擇：" + (auto.isEmpty ? "（不排班）" : "（樣式自動：\(auto)）")
        }
        if let t = st.classify(pick), t.code.caseInsensitiveCompare(pick) != .orderedSame { return "選擇：\(pick) → \(t.name)" }
        return "選擇：" + pick
    }

    private func numberField(_ label: String, _ v: Binding<String>) -> some View {
        HStack {
            Text(label)
            TextField("0", text: v).keyboardType(.decimalPad).multilineTextAlignment(.trailing)
        }
    }

    private func load() {
        let k = day.key
        pick = st.explicit(profile, k)
        note = st.noteText(profile, k)
        ot = num(st.extra("ot", k))
        to = num(st.extra("to", k))
        allow = num(st.extra("allow", k))
        holiday = st.customHoliday(k)
    }

    private var richLabel: String {
        let o = st.noteObj(profile, day.key), mood = st.mood(profile, day.key)
        var s = "📝 備註記錄（心情、連結、圖片、地圖）"
        if !mood.isEmpty { s += "　" + mood }
        if Store.count(o, "images") > 0 { s += "　🖼️\(Store.count(o, "images"))" }
        if Store.count(o, "links") > 0 { s += "　🔗\(Store.count(o, "links"))" }
        if Store.count(o, "places") > 0 { s += "　📍\(Store.count(o, "places"))" }
        return s
    }

    private func save() {
        saveFields()
        dismiss()
    }

    /// 先存這個畫面的內容（進備註記錄頁前也會呼叫）。
    private func saveFields() {
        let k = day.key
        st.setCode(profile, k, pick)
        st.setNote(profile, k, note)
        if me {
            st.setExtra("ot", k, Double(ot) ?? 0)
            st.setExtra("to", k, Double(to) ?? 0)
            st.setExtra("allow", k, Double(allow) ?? 0)
        }
        st.setCustomHoliday(k, holiday)
        st.save()
    }
}

/// 班別按鈕格（隱藏的班別不顯示），含「自訂代號」「清除」。
struct ShiftButtons: View {
    @EnvironmentObject var st: Store
    let selected: String
    let onPick: (String) -> Void
    let custom: () -> Void
    var plain = false     // true：不標示目前選的（一次設定多天用）

    var body: some View {
        LazyVGrid(columns: Array(repeating: GridItem(.flexible(), spacing: 8), count: 4), spacing: 8) {
            ForEach(st.types.filter { !$0.hidden }) { t in
                button(t.code, t.name, t.swiftColor, on: t.code.caseInsensitiveCompare(selected) == .orderedSame) { onPick(t.code) }
            }
            button("自訂", "代號", .gray, on: !selected.isEmpty && st.type(selected) == nil, action: custom)
            button("清除", "", Color(white: 0.45), on: selected.isEmpty) { onPick("") }
        }
        .padding(.vertical, 4)
    }

    private func button(_ top: String, _ bottom: String, _ color: Color, on: Bool, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            VStack(spacing: 1) {
                Text(top).font(.system(size: 16, weight: .bold)).lineLimit(1).minimumScaleFactor(0.5)
                if !bottom.isEmpty { Text(bottom).font(.caption2).lineLimit(1) }
            }
            .foregroundStyle(.white)
            .frame(maxWidth: .infinity, minHeight: 48)
            .background(RoundedRectangle(cornerRadius: 8).fill(color.opacity(on || plain ? 1 : 0.5)))
            .overlay(RoundedRectangle(cornerRadius: 8).stroke(on && !plain ? Color.primary : .clear, lineWidth: 2.5))
        }
        .buttonStyle(.plain)
    }
}
