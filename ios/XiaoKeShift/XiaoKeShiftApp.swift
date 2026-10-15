import SwiftUI
import Combine

@main
struct XiaoKeShiftApp: App {
    @StateObject private var store = Store()
    @Environment(\.scenePhase) private var phase
    @AppStorage("dark") private var dark = false
    @AppStorage("password") private var password = ""
    @AppStorage("keepAwake") private var keepAwake = false
    @State private var unlocked = false

    var body: some Scene {
        WindowGroup {
            ZStack {
                ContentView()
                if !password.isEmpty && !unlocked { LockView(unlocked: $unlocked) }
            }
            .environmentObject(store)
            .preferredColorScheme(dark ? .dark : nil)
            .onAppear(perform: cleanup)
            // 班表或設定改變 2 秒後重排鬧鐘
            .onReceive(store.objectWillChange.debounce(for: .seconds(2), scheduler: RunLoop.main)) { _ in
                Task { _ = await Alarms.schedule(store) }
            }
        }
        .onChange(of: keepAwake) { _, on in UIApplication.shared.isIdleTimerDisabled = on }
        .onChange(of: phase) { _, p in
            if p == .background { unlocked = false; store.save() }   // 順便把設定交給小工具
            if p == .active {
                Task { _ = await Alarms.schedule(store) }
                XiaoKeServer.shared.start(store)          // 小柯機器人區網 API（前景時）
                UIApplication.shared.isIdleTimerDisabled = keepAwake
            }
            if p == .background { XiaoKeServer.shared.stop() }
        }
    }

    /// 設定「自動清除三個月前的舊班表」。
    private func cleanup() {
        guard store.flag("autoDelete", false) else { return }
        let t = Day.today
        let m = t.y * 12 + t.m - 1 - 3
        store.deleteOlderThan(Day(m / 12, m % 12 + 1, 1).key)
    }
}
