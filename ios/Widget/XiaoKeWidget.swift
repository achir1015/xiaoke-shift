import WidgetKit
import SwiftUI

/// 桌面小工具：「小柯今日班表」（小柯臉＋今天/明天班別＋農曆）、「小柯月班表」（整月）。
/// 資料讀 App Group 裡的 data.json（App 每次存檔都會通知小工具更新）。
@main
struct XiaoKeWidgets: WidgetBundle {
    var body: some Widget {
        TodayWidget()
        MonthWidget()
    }
}

struct Entry: TimelineEntry { let date: Date }

struct Provider: TimelineProvider {
    func placeholder(in context: Context) -> Entry { Entry(date: Date()) }
    func getSnapshot(in context: Context, completion: @escaping (Entry) -> Void) { completion(Entry(date: Date())) }
    func getTimeline(in context: Context, completion: @escaping (Timeline<Entry>) -> Void) {
        // 過了午夜換一天
        let midnight = Calendar.current.startOfDay(for: Date()).addingTimeInterval(86400 + 5)
        completion(Timeline(entries: [Entry(date: Date())], policy: .after(midnight)))
    }
}

private func loadStore() -> Store {
    let st = Store()
    if let g = UserDefaults(suiteName: Store.GROUP) { st.prefs = g }
    return st
}

private let navy = Color(argb: ShiftType.argb(0xFF101522))

/// 小柯臉（同 App 圖示）：白色彎月笑眼、粉紅腮紅、微笑。
struct XiaoKeFace: View {
    var body: some View {
        Canvas { ctx, size in
            let s = min(size.width, size.height) / 512
            ctx.translateBy(x: (size.width - 512 * s) / 2, y: (size.height - 512 * s) / 2)
            ctx.scaleBy(x: s, y: s)
            var eyes = Path()
            for cx in [180.0, 332.0] { eyes.addArc(center: CGPoint(x: cx, y: 250), radius: 60, startAngle: .degrees(180), endAngle: .degrees(0), clockwise: false) }
            ctx.stroke(eyes, with: .color(.white), style: StrokeStyle(lineWidth: 28, lineCap: .round))
            ctx.fill(Path(ellipseIn: CGRect(x: 84, y: 298, width: 88, height: 44)), with: .color(Color(red: 1, green: 0.44, blue: 0.57).opacity(0.5)))
            ctx.fill(Path(ellipseIn: CGRect(x: 340, y: 298, width: 88, height: 44)), with: .color(Color(red: 1, green: 0.44, blue: 0.57).opacity(0.5)))
            var smile = Path()
            smile.move(to: CGPoint(x: 206, y: 330))
            smile.addQuadCurve(to: CGPoint(x: 306, y: 330), control: CGPoint(x: 256, y: 376))
            ctx.stroke(smile, with: .color(.white), style: StrokeStyle(lineWidth: 20, lineCap: .round))
        }
    }
}

// MARK: - 小柯今日班表

struct TodayWidget: Widget {
    var body: some WidgetConfiguration {
        StaticConfiguration(kind: "XiaoKeToday", provider: Provider()) { _ in TodayView() }
            .configurationDisplayName("小柯今日班表")
            .description("今天、明天的班別與農曆。")
            .supportedFamilies([.systemSmall, .systemMedium])
    }
}

struct TodayView: View {
    @Environment(\.widgetFamily) private var family
    private let st = loadStore()

    var body: some View {
        let today = Day.today, tomorrow = today.adding(1)
        Group {
            if family == .systemMedium {
                HStack(spacing: 12) {
                    XiaoKeFace().frame(width: 90, height: 90)
                    VStack(alignment: .leading, spacing: 6) { lines(today, tomorrow) }
                    Spacer(minLength: 0)
                }
            } else {
                VStack(alignment: .leading, spacing: 4) {
                    XiaoKeFace().frame(width: 44, height: 44)
                    lines(today, tomorrow)
                    Spacer(minLength: 0)
                }
            }
        }
        .foregroundStyle(.white)
        .containerBackground(navy, for: .widget)
    }

    @ViewBuilder
    private func lines(_ today: Day, _ tomorrow: Day) -> some View {
        Text("\(today.m)/\(today.d)（\(today.weekdayName)）\(Almanac.lunarString(today))")
            .font(.caption2).foregroundStyle(.white.opacity(0.7)).lineLimit(1).minimumScaleFactor(0.7)
        shift("今天", today)
        shift("明天", tomorrow).opacity(0.8)
    }

    private func shift(_ label: String, _ d: Day) -> some View {
        let raw = st.code(Store.ME, d.key)
        let t = st.classify(raw)
        return HStack(spacing: 6) {
            Text(label).font(.caption.bold())
            if raw.isEmpty {
                Text("未排班").font(.caption)
            } else {
                Text(raw).font(.system(size: 13, weight: .bold)).padding(.horizontal, 6).padding(.vertical, 2)
                    .background(RoundedRectangle(cornerRadius: 5).fill(t?.swiftColor ?? .gray))
                Text(t.map { $0.off ? $0.name : "\($0.name) \($0.start)" } ?? "").font(.caption).lineLimit(1).minimumScaleFactor(0.7)
            }
        }
    }
}

// MARK: - 小柯月班表

struct MonthWidget: Widget {
    var body: some WidgetConfiguration {
        StaticConfiguration(kind: "XiaoKeMonth", provider: Provider()) { _ in MonthWidgetView() }
            .configurationDisplayName("小柯月班表")
            .description("整個月的班表。")
            .supportedFamilies([.systemLarge])
    }
}

struct MonthWidgetView: View {
    private let st = loadStore()

    var body: some View {
        let today = Day.today
        let firstDow = st.num("firstDow", 1)
        let lead = (Day(today.y, today.m, 1).weekday - firstDow + 7) % 7
        let count = Day.daysIn(today.y, today.m)
        let rows = (lead + count + 6) / 7
        let red = Color(argb: ShiftType.argb(0xFFE53935))
        VStack(spacing: 3) {
            HStack {
                XiaoKeFace().frame(width: 26, height: 26).background(Circle().fill(navy))
                Text("\(st.profileName(Store.ME))　\(String(today.y)) 年 \(today.m) 月").font(.subheadline.bold())
                Spacer()
            }
            HStack(spacing: 2) {
                ForEach(0..<7, id: \.self) { i in
                    let dow = (firstDow - 1 + i) % 7 + 1
                    Text(["日", "一", "二", "三", "四", "五", "六"][dow - 1]).font(.caption2)
                        .foregroundStyle(dow == 1 || dow == 7 ? red : .secondary).frame(maxWidth: .infinity)
                }
            }
            ForEach(0..<rows, id: \.self) { r in
                HStack(spacing: 2) {
                    ForEach(0..<7, id: \.self) { c in
                        let d = r * 7 + c - lead + 1
                        if d >= 1 && d <= count { cell(Day(today.y, today.m, d), today: today, red: red) }
                        else { Color.clear.frame(maxWidth: .infinity, maxHeight: .infinity) }
                    }
                }
            }
        }
        .containerBackground(Color(.systemBackground), for: .widget)
    }

    private func cell(_ day: Day, today: Day, red: Color) -> some View {
        let raw = st.code(Store.ME, day.key)
        let t = st.classify(raw)
        let hol = Holidays.name(st, day)
        return VStack(spacing: 1) {
            Text("\(day.d)").font(.system(size: 11, weight: .bold))
                .foregroundStyle(day.isWeekend || !hol.isEmpty ? red : .primary)
            if !raw.isEmpty && !(st.flag("hideOff", false) && t?.off == true) {
                Text(raw).font(.system(size: 10, weight: .bold)).lineLimit(1).minimumScaleFactor(0.5)
                    .foregroundStyle(.white).frame(maxWidth: .infinity, minHeight: 14)
                    .background(RoundedRectangle(cornerRadius: 3).fill(t?.swiftColor ?? .gray))
            }
            Spacer(minLength: 0)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .padding(1)
        .background(RoundedRectangle(cornerRadius: 4).stroke(day == today ? Color.blue : .clear, lineWidth: 1.5))
    }
}
