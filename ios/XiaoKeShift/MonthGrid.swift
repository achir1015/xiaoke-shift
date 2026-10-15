import SwiftUI

/// 月曆格子：日期、農曆／節氣／假日、班別色塊（原代號）、心情、備註點、發薪日。
/// 依設定：每週第一天、今日樣式、顯示班別時間、隱藏休假、週末紅字。
/// 按壓滑動可選一段日期（橫向滑，或長按 0.45 秒後往任何方向拖曳）；批次編輯模式任何方向滑都會選範圍。
struct MonthGrid: View {
    @EnvironmentObject var st: Store
    let year: Int, month: Int
    let profile: String
    let selected: Day
    var multi = false
    var picked: Set<String> = []
    var eventDays: Set<String> = []     // 行事曆有行程的日子（藍點）
    var interactive = true          // false：給截圖用（不加觸控層）
    let onTap: (Day) -> Void
    var onRange: ([String]) -> Void = { _ in }
    var onDragging: (Bool) -> Void = { _ in }

    @State private var width: CGFloat = 0
    @State private var dragStart: Day?
    @State private var dragRange: Set<String> = []

    private static let head: CGFloat = 18, gap: CGFloat = 3, cellH: CGFloat = 66

    @AppStorage("firstDow") private var firstDow = 1          // 1＝星期日、2＝星期一
    @AppStorage("weekendRed") private var weekendRed = true
    @AppStorage("todayStyle") private var todayStyle = "border"
    @AppStorage("showTime") private var showTime = false
    @AppStorage("hideOff") private var hideOff = false
    @AppStorage("showHolidays") private var showHolidays = true   // 只為了設定改變時重畫
    @AppStorage("fontScale") private var fontScale = 100
    @AppStorage("bigNoteDot") private var bigNoteDot = false
    @AppStorage("noteColor") private var noteColor = "#E53935"
    private var sc: CGFloat { CGFloat(fontScale) / 100 }

    static let red = Color(argb: ShiftType.argb(0xFFE53935))
    static let green = Color(argb: ShiftType.argb(0xFF2E7D32))
    static let blue = Color(argb: ShiftType.argb(0xFF1E88E5))

    private var lead: Int { (Day(year, month, 1).weekday - firstDow + 7) % 7 }
    private var count: Int { Day.daysIn(year, month) }
    private var rows: Int { (lead + count + 6) / 7 }

    var body: some View {
        let today = Day.today
        VStack(spacing: 3) {
            HStack(spacing: 3) {
                ForEach(0..<7, id: \.self) { i in
                    let dow = (firstDow - 1 + i) % 7 + 1
                    Text(["日", "一", "二", "三", "四", "五", "六"][dow - 1])
                        .font(.footnote)
                        .foregroundStyle((dow == 1 || dow == 7) && weekendRed ? Self.red : .secondary)
                        .frame(maxWidth: .infinity)
                }
            }
            .frame(height: Self.head)
            ForEach(0..<rows, id: \.self) { r in
                HStack(spacing: 3) {
                    ForEach(0..<7, id: \.self) { c in
                        let d = r * 7 + c - lead + 1
                        if d >= 1 && d <= count {
                            let day = Day(year, month, d)
                            cell(day, today: today)
                        } else {
                            Color.clear.frame(maxWidth: .infinity, minHeight: Self.cellH, maxHeight: Self.cellH)
                        }
                    }
                }
            }
        }
        .background(GeometryReader { g in
            Color.clear.onAppear { width = g.size.width }.onChange(of: g.size.width) { _, w in width = w }
        })
        .overlay {
            if interactive { touch }
        }
    }

    private var touch: some View {
            GridTouch(multi: multi) { p in
                if let d = dayAt(p) { onTap(d) }
            } began: { p in
                if let d = dayAt(p) { begin(d) }
            } moved: { p in
                if let d = dayAt(p) { extend(d) }
            } ended: { cancelled in
                if cancelled { dragStart = nil; dragRange = []; onDragging(false) } else { finish() }
            }
    }

    private func dayAt(_ p: CGPoint) -> Day? {
        guard width > 0 else { return nil }
        let colW = (width - Self.gap * 6) / 7
        let col = min(6, max(0, Int(p.x / (colW + Self.gap))))
        let y = p.y - Self.head - Self.gap
        if y < 0 { return nil }
        let row = Int(y / (Self.cellH + Self.gap))
        let d = row * 7 + col - lead + 1
        return d >= 1 && d <= count ? Day(year, month, d) : nil
    }

    private func begin(_ s: Day) {
        dragStart = s
        dragRange = [s.key]
        onDragging(true)
        UIImpactFeedbackGenerator(style: .medium).impactOccurred()
    }

    private func extend(_ k: Day) {
        guard let s = dragStart else { return }
        let a = min(s, k), b = max(s, k)
        dragRange = Set((a.ordinal...b.ordinal).map { Day(ordinal: $0).key })
    }

    private func finish() {
        guard dragStart != nil else { return }
        let keys = dragRange.sorted()
        dragStart = nil
        dragRange = []
        onDragging(false)
        if !keys.isEmpty { onRange(keys) }
    }

    @ViewBuilder
    private func cell(_ day: Day, today: Day) -> some View {
        let k = day.key
        let hol = Holidays.name(st, day)
        let isToday = day == today
        let raw = st.code(profile, k)
        let t = st.classify(raw)
        let showShift = !raw.isEmpty && !(hideOff && t?.off == true)
        let auto = st.explicit(profile, k).isEmpty      // 自動重覆樣式產生的，畫淡一點
        let mood = st.mood(profile, k)
        let hasNote = !st.note(profile, k).isEmpty
        let payday = Holidays.isPayday(st, day)

        VStack(alignment: .leading, spacing: 1) {
            HStack(alignment: .firstTextBaseline, spacing: 2) {
                dayNumber(day, isToday: isToday, red: (day.isWeekend && weekendRed) || !hol.isEmpty)
                Spacer(minLength: 0)
                lunarText(day, holiday: hol)
            }
            HStack(spacing: 3) {
                if hasNote { Circle().fill(Color(hex: noteColor)).frame(width: bigNoteDot ? 10 : 6, height: bigNoteDot ? 10 : 6) }
                if payday { Text("$").font(.system(size: 10, weight: .bold)).foregroundStyle(Self.green) }
                if eventDays.contains(k) { Circle().fill(Self.blue).frame(width: 6, height: 6) }
                Spacer(minLength: 0)
                if !mood.isEmpty { Text(mood).font(.system(size: 12 * sc)) }
            }
            .frame(height: 13)
            Spacer(minLength: 0)
            if showShift {
                let label = showTime && t != nil && !t!.off ? "\(raw) \(t!.start)" : raw
                Text(label)
                    .font(.system(size: 13 * sc, weight: .bold))
                    .lineLimit(1)
                    .minimumScaleFactor(0.5)
                    .foregroundStyle(.white)
                    .frame(maxWidth: .infinity, minHeight: 22)
                    .background(RoundedRectangle(cornerRadius: 5)
                        .fill((t?.swiftColor ?? .gray).opacity(auto ? 0.6 : 1)))
            }
        }
        .padding(.horizontal, 3)
        .padding(.vertical, 3)
        .frame(maxWidth: .infinity, minHeight: Self.cellH, maxHeight: Self.cellH, alignment: .top)
        .background(RoundedRectangle(cornerRadius: 6)
            .fill(picked.contains(k) || dragRange.contains(k) ? Color(argb: ShiftType.argb(0xFFFFE082))
                  : day == selected && !multi ? Color.accentColor.opacity(0.18) : Color(.secondarySystemGroupedBackground)))
        .overlay {
            if isToday && todayStyle == "border" {
                RoundedRectangle(cornerRadius: 6).stroke(Self.blue, lineWidth: 2)
            }
        }
    }

    @ViewBuilder
    private func dayNumber(_ day: Day, isToday: Bool, red: Bool) -> some View {
        if isToday && todayStyle == "circle" {
            Text("\(day.d)").font(.system(size: 14 * sc, weight: .bold)).foregroundStyle(.white)
                .frame(minWidth: 20, minHeight: 20).background(Circle().fill(Self.blue))
        } else {
            Text("\(day.d)").font(.system(size: 14 * sc, weight: .bold)).foregroundStyle(red ? Self.red : .primary)
        }
    }

    /// 假日紅字取代農曆；節氣綠字；初一顯示「X月」藍字；其他灰字。
    private func lunarText(_ day: Day, holiday: String) -> some View {
        let s = holiday.isEmpty ? Almanac.cellText(day) : holiday
        let color: Color = !holiday.isEmpty ? Self.red
            : !Almanac.termOn(day).isEmpty ? Self.green
            : s.hasSuffix("月") ? Self.blue : .secondary
        return Text(s).font(.system(size: 10 * sc)).foregroundStyle(color).lineLimit(1).minimumScaleFactor(0.6)
    }
}

extension Color {
    /// "#RRGGBB" 或 "#AARRGGBB"
    init(hex: String) {
        var s = hex.trimmingCharacters(in: .whitespaces)
        if s.hasPrefix("#") { s.removeFirst() }
        var v = UInt32(s, radix: 16) ?? 0xE53935
        if s.count <= 6 { v |= 0xFF00_0000 }
        self.init(argb: ShiftType.argb(v))
    }
}

/// 月曆的觸控（用 UIKit 手勢，才能和外層捲動和平共處）：
/// 點一下＝點日期；橫向滑（批次模式任何方向）或長按 0.45 秒後拖曳＝選一段日期；直向滑讓頁面捲動。
struct GridTouch: UIViewRepresentable {
    var multi: Bool
    var tap: (CGPoint) -> Void
    var began: (CGPoint) -> Void
    var moved: (CGPoint) -> Void
    var ended: (Bool) -> Void

    func makeCoordinator() -> Coordinator { Coordinator() }

    func makeUIView(context: Context) -> UIView {
        let v = UIView()
        v.backgroundColor = .clear
        let c = context.coordinator
        let t = UITapGestureRecognizer(target: c, action: #selector(Coordinator.onTap(_:)))
        let pan = UIPanGestureRecognizer(target: c, action: #selector(Coordinator.onDrag(_:)))
        pan.delegate = c
        let lp = UILongPressGestureRecognizer(target: c, action: #selector(Coordinator.onDrag(_:)))
        lp.minimumPressDuration = 0.45
        lp.delegate = c
        c.pan = pan
        [t, pan, lp].forEach(v.addGestureRecognizer)
        return v
    }

    func updateUIView(_ v: UIView, context: Context) { context.coordinator.parent = self }

    final class Coordinator: NSObject, UIGestureRecognizerDelegate {
        var parent: GridTouch?
        weak var pan: UIPanGestureRecognizer?
        private var active = false

        @objc func onTap(_ g: UITapGestureRecognizer) { parent?.tap(g.location(in: g.view)) }

        @objc func onDrag(_ g: UIGestureRecognizer) {
            let p = g.location(in: g.view)
            switch g.state {
            case .began:
                if active { return }     // 另一個手勢已經在選
                active = true
                if let pan = g as? UIPanGestureRecognizer {
                    // 從按下的位置開始選
                    let t = pan.translation(in: pan.view)
                    parent?.began(CGPoint(x: p.x - t.x, y: p.y - t.y))
                } else {
                    parent?.began(p)
                }
                parent?.moved(p)
            case .changed:
                if active { parent?.moved(p) }
            case .ended:
                if active { active = false; parent?.ended(false) }
            case .cancelled, .failed:
                if active { active = false; parent?.ended(true) }
            default: break
            }
        }

        func gestureRecognizerShouldBegin(_ g: UIGestureRecognizer) -> Bool {
            guard let pan = g as? UIPanGestureRecognizer, g === self.pan else { return true }
            let v = pan.velocity(in: pan.view)
            return parent?.multi == true || abs(v.x) > abs(v.y)     // 直向滑交給頁面捲動
        }
    }
}
