import SwiftUI

/// 設定（第一階段：月曆顯示相關）。鍵名與 Android 版相同。
struct SettingsView: View {
    @Environment(\.dismiss) private var dismiss
    @AppStorage("firstDow") private var firstDow = 1
    @AppStorage("todayStyle") private var todayStyle = "border"
    @AppStorage("weekendRed") private var weekendRed = true
    @AppStorage("showHolidays") private var showHolidays = true
    @AppStorage("showTime") private var showTime = false
    @AppStorage("hideOff") private var hideOff = false
    @AppStorage("fontScale") private var fontScale = 100
    @AppStorage("bigNoteDot") private var bigNoteDot = false
    @AppStorage("noteColor") private var noteColor = "#E53935"
    @AppStorage("dark") private var dark = false
    @AppStorage("eventDots") private var eventDots = false
    @AppStorage("autoDelete") private var autoDelete = false
    @AppStorage("password") private var password = ""
    @State private var newPw = ""
    @State private var pwMsg = ""

    var body: some View {
        NavigationStack {
            Form {
                Section("月曆") {
                    Picker("每週第一天", selection: $firstDow) {
                        Text("星期日").tag(1)
                        Text("星期一").tag(2)
                    }
                    Picker("今日樣式", selection: $todayStyle) {
                        Text("藍框").tag("border")
                        Text("藍色圓圈").tag("circle")
                        Text("不標示").tag("none")
                    }
                    Toggle("週末紅字", isOn: $weekendRed)
                    Toggle("顯示國定假日", isOn: $showHolidays)
                    Toggle("班別色塊顯示上班時間", isOn: $showTime)
                    Toggle("隱藏休假類班別", isOn: $hideOff)
                    Picker("月曆上的字型大小", selection: $fontScale) {
                        Text("小").tag(85)
                        Text("正常").tag(100)
                        Text("大").tag(120)
                    }
                    Toggle("於月曆顯示行事曆事件（藍點）", isOn: $eventDots)
                        .onChange(of: eventDots) { _, on in if on { Task { _ = await CalendarSync.requestAccess() } } }
                    Toggle("「備註點」大一點", isOn: $bigNoteDot)
                    Picker("「備註點」的顏色", selection: $noteColor) {
                        Text("紅色").tag("#E53935")
                        Text("藍色").tag("#1E88E5")
                        Text("綠色").tag("#43A047")
                        Text("橘色").tag("#FB8C00")
                        Text("粉紅").tag("#D81B60")
                    }
                }
                Section("介面") {
                    Toggle("永遠使用深色模式（關閉＝跟隨 iPhone）", isOn: $dark)
                }
                Section {
                    Toggle("自動清除三個月前的舊班表", isOn: $autoDelete)
                    SecureField(password.isEmpty ? "設定密碼（數字）" : "更改密碼（輸入 0000 清除）", text: $newPw)
                        .keyboardType(.numberPad)
                    Button("儲存密碼") {
                        let p = newPw.trimmingCharacters(in: .whitespaces)
                        if p == "0000" { password = ""; pwMsg = "密碼已清除" }
                        else if !p.isEmpty { password = Password.hash(p); pwMsg = "密碼設定成功，下次開啟 App 需要輸入" }
                        newPw = ""
                    }
                    .disabled(newPw.isEmpty)
                    if !pwMsg.isEmpty { Text(pwMsg).foregroundStyle(.blue) }
                } header: { Text("系統") } footer: { Text(password.isEmpty ? "目前沒有密碼。" : "已設定密碼。") }
                Section {
                    Text("版本 \(Bundle.main.object(forInfoDictionaryKey: "CFBundleShortVersionString") as? String ?? "")")
                        .foregroundStyle(.secondary)
                }
            }
            .navigationTitle("設定")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar { ToolbarItem(placement: .confirmationAction) { Button("完成") { dismiss() } } }
        }
    }
}
