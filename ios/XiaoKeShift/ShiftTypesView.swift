import SwiftUI

/// 班別設定：點一下編輯、左滑刪除、編輯模式拖曳排序、新增。
struct ShiftTypesView: View {
    @EnvironmentObject var st: Store
    @Environment(\.dismiss) private var dismiss
    @State private var editing: ShiftType?
    @State private var adding = false

    var body: some View {
        NavigationStack {
            List {
                Section(footer: Text("刪除班別後，已排了該代號的日子會改顯示灰色原代號。")) {
                    ForEach(st.types) { t in
                        Button { editing = t } label: {
                            HStack {
                                Text(t.code).font(.headline).foregroundStyle(.white)
                                    .frame(minWidth: 44, minHeight: 32)
                                    .background(RoundedRectangle(cornerRadius: 6).fill(t.swiftColor))
                                VStack(alignment: .leading) {
                                    Text(t.name + (t.hidden ? "（隱藏）" : "")).foregroundStyle(.primary)
                                    Text(t.timeText + (t.alarmOn ? "　⏰\(t.alarmTime)" : "")).font(.caption).foregroundStyle(.secondary)
                                }
                            }
                        }
                    }
                    .onDelete { st.types.remove(atOffsets: $0); st.save() }
                    .onMove { st.types.move(fromOffsets: $0, toOffset: $1); st.save() }
                    Button("＋ 新增班別") { adding = true }
                }
            }
            .navigationTitle("班別設定")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarLeading) { EditButton() }
                ToolbarItem(placement: .confirmationAction) { Button("完成") { dismiss() } }
            }
            .sheet(item: $editing) { t in ShiftTypeEditor(original: t) }
            .sheet(isPresented: $adding) { ShiftTypeEditor(original: nil) }
        }
    }
}

struct ShiftTypeEditor: View {
    @EnvironmentObject var st: Store
    @Environment(\.dismiss) private var dismiss
    let original: ShiftType?
    @State private var t = ShiftType(code: "", name: "", color: 0, start: "08:00", end: "16:00", off: false)
    @State private var pay = ""
    @State private var error = ""

    var body: some View {
        NavigationStack {
            Form {
                Section {
                    TextField("代號（例如 D）", text: $t.code)
                    TextField("名稱（例如 白班）", text: $t.name)
                    Toggle("休假類（不算工時）", isOn: $t.off)
                    if !t.off {
                        TimeField(label: "開始時間", hhmm: $t.start)
                        TimeField(label: "結束時間（跨夜可小於開始）", hhmm: $t.end)
                    }
                } footer: { Text("工作站匯入的代號（如 D/洗）會依規則自動歸類到這裡的班別。") }
                Section("顏色") {
                    LazyVGrid(columns: Array(repeating: GridItem(.flexible()), count: 6), spacing: 10) {
                        ForEach(ShiftType.palette, id: \.self) { c in
                            let v = ShiftType.argb(c)
                            Circle().fill(Color(argb: v)).frame(width: 36, height: 36)
                                .overlay(Circle().stroke(Color.primary, lineWidth: t.color == v ? 3 : 0))
                                .onTapGesture { t.color = v }
                        }
                    }
                    .padding(.vertical, 4)
                }
                Section {
                    HStack {
                        Text("班別薪資")
                        TextField("0", text: $pay).keyboardType(.decimalPad).multilineTextAlignment(.trailing)
                    }
                    TextField("班別備註／描述", text: $t.desc)
                    Toggle("不顯示於班別選擇按鈕", isOn: $t.hidden)
                }
                Section {
                    Toggle("⏰ 啟用鬧鐘", isOn: $t.alarmOn)
                    if t.alarmOn {
                        TimeField(label: "鬧鐘時間", hhmm: $t.alarmTime)
                        Toggle("前日響（例：大夜 00:00 上班，鬧鐘 23:00 在前一天響）", isOn: $t.alarmBefore)
                    }
                } header: { Text("鬧鐘（這個班別的日子會響）") }
                if !error.isEmpty { Text(error).foregroundStyle(.red) }
            }
            .navigationTitle(original == nil ? "新增班別" : "編輯班別")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("取消") { dismiss() } }
                ToolbarItem(placement: .confirmationAction) { Button("儲存") { save() }.bold() }
            }
        }
        .onAppear {
            if let o = original { t = o } else {
                t.color = ShiftType.argb(ShiftType.palette[st.types.count % ShiftType.palette.count])
            }
            pay = num(t.pay)
        }
    }

    private func save() {
        t.code = t.code.trimmingCharacters(in: .whitespaces)
        if t.code.isEmpty { error = "代號不能空白"; return }
        if st.types.contains(where: { $0.id != t.id && $0.code.caseInsensitiveCompare(t.code) == .orderedSame }) {
            error = "已經有「\(t.code)」這個代號了"; return
        }
        t.name = t.name.trimmingCharacters(in: .whitespaces).isEmpty ? t.code : t.name.trimmingCharacters(in: .whitespaces)
        t.desc = t.desc.trimmingCharacters(in: .whitespaces)
        t.pay = Double(pay) ?? 0
        if let i = st.types.firstIndex(where: { $0.id == t.id }) { st.types[i] = t } else { st.types.append(t) }
        st.save()
        dismiss()
    }
}

/// "HH:mm" 字串用時間滾輪編輯。
struct TimeField: View {
    let label: String
    @Binding var hhmm: String

    var body: some View {
        DatePicker(label, selection: Binding(get: {
            let m = max(0, ShiftType.minutes(hhmm))
            return Calendar.current.date(bySettingHour: m / 60 % 24, minute: m % 60, second: 0, of: Date()) ?? Date()
        }, set: { d in
            let c = Calendar.current.dateComponents([.hour, .minute], from: d)
            hhmm = String(format: "%02d:%02d", c.hour ?? 0, c.minute ?? 0)
        }), displayedComponents: .hourAndMinute)
        .environment(\.locale, Locale(identifier: "en_GB"))   // 24 小時制
    }
}
