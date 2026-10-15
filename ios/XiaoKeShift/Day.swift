import Foundation

/// 一天（國曆年月日），不帶時區。鍵值格式與 Android 版相同："yyyy-MM-dd"。
struct Day: Hashable, Comparable, Identifiable {
    let y: Int, m: Int, d: Int

    init(_ y: Int, _ m: Int, _ d: Int) { self.y = y; self.m = m; self.d = d }

    init?(key: String) {
        let p = key.split(separator: "-").compactMap { Int($0.trimmingCharacters(in: .whitespaces)) }
        guard p.count == 3, (1...12).contains(p[1]), p[2] >= 1, p[2] <= Day.daysIn(p[0], p[1]) else { return nil }
        self.init(p[0], p[1], p[2])
    }

    static var today: Day {
        let c = Calendar(identifier: .gregorian).dateComponents([.year, .month, .day], from: Date())
        return Day(c.year!, c.month!, c.day!)
    }

    var id: String { key }
    var key: String { String(format: "%04d-%02d-%02d", y, m, d) }

    /// 距 2000-01-01 的天數（Howard Hinnant 演算法）
    var ordinal: Int {
        let yy = y - (m <= 2 ? 1 : 0)
        let era = (yy >= 0 ? yy : yy - 399) / 400
        let yoe = yy - era * 400
        let doy = (153 * (m + (m > 2 ? -3 : 9)) + 2) / 5 + d - 1
        let doe = yoe * 365 + yoe / 4 - yoe / 100 + doy
        return era * 146097 + doe - 719468 - 10957
    }

    init(ordinal: Int) {
        let z = ordinal + 10957 + 719468
        let era = (z >= 0 ? z : z - 146096) / 146097
        let doe = z - era * 146097
        let yoe = (doe - doe / 1460 + doe / 36524 - doe / 146096) / 365
        let doy = doe - (365 * yoe + yoe / 4 - yoe / 100)
        let mp = (5 * doy + 2) / 153
        let dd = doy - (153 * mp + 2) / 5 + 1
        let mm = mp < 10 ? mp + 3 : mp - 9
        self.init(yoe + era * 400 + (mm <= 2 ? 1 : 0), mm, dd)
    }

    func adding(_ days: Int) -> Day { Day(ordinal: ordinal + days) }

    /// 星期：1＝星期日 … 7＝星期六（同 Calendar.DAY_OF_WEEK）。2000-01-01 是星期六。
    var weekday: Int { ((ordinal + 6) % 7 + 7) % 7 + 1 }
    var weekdayName: String { ["日", "一", "二", "三", "四", "五", "六"][weekday - 1] }
    var isWeekend: Bool { weekday == 1 || weekday == 7 }

    static func daysIn(_ y: Int, _ m: Int) -> Int {
        switch m {
        case 2: return (y % 4 == 0 && y % 100 != 0) || y % 400 == 0 ? 29 : 28
        case 4, 6, 9, 11: return 30
        default: return 31
        }
    }

    static func between(_ a: String, _ b: String) -> Int {
        guard let x = Day(key: a), let y = Day(key: b) else { return 0 }
        return y.ordinal - x.ordinal
    }

    static func < (a: Day, b: Day) -> Bool { a.ordinal < b.ordinal }
}
