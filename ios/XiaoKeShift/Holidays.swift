import Foundation

/// 特殊假日：離線計算台灣國定假日＋使用者自訂。
/// 國曆：元旦、和平紀念日、兒童節、清明（民族掃墓節）、勞動節、教師節、國慶日、光復節、行憲紀念日。
/// 農曆：除夕、春節（初一～初三）、端午、中秋（用 Almanac 的農曆表換算）。
/// 補假依行政院人事行政總處每年公告，未自動計入，可用「自訂假日」補上。
enum Holidays {
    private static var cache: [Int: [String: String]] = [:]

    static func year(_ y: Int) -> [String: String] {
        if let m = cache[y] { return m }
        var m: [String: String] = [
            Day(y, 1, 1).key: "元旦", Day(y, 2, 28).key: "和平紀念日", Day(y, 4, 4).key: "兒童節",
            Day(y, 5, 1).key: "勞動節", Day(y, 9, 28).key: "教師節", Day(y, 10, 10).key: "國慶日",
            Day(y, 10, 25).key: "光復節", Day(y, 12, 25).key: "行憲紀念日"]
        var c = Day(y, 1, 1)
        var prev: String?
        while c.y == y {
            let k = c.key, lunar = Almanac.lunarString(c)
            if lunar == "正月初一" {
                m[k] = "春節"
                if let p = prev { m[p] = "除夕" }
            } else if lunar == "正月初二" || lunar == "正月初三" { m[k] = "春節" }
            else if lunar == "五月初五" { m[k] = "端午節" }
            else if lunar == "八月十五" { m[k] = "中秋節" }
            if Almanac.termOn(c) == "清明" { m[k] = m[k].map { $0 + "、清明" } ?? "清明節" }
            prev = k
            c = c.adding(1)
        }
        cache[y] = m
        return m
    }

    /// 某天的假日名稱（自訂優先），沒有就回空字串。
    static func name(_ s: Store, _ day: Day) -> String {
        let custom = s.customHoliday(day.key)
        if !custom.isEmpty { return custom }
        if !s.flag("showHolidays", true) { return "" }
        return year(day.y)[day.key] ?? ""
    }

    /// 發薪日：每月固定日期，或從起始日起固定週期。
    static func isPayday(_ s: Store, _ day: Day) -> Bool {
        switch s.pref("payMode", "none") {
        case "day":
            return day.d == min(s.num("payDay", 5), Day.daysIn(day.y, day.m))
        case "period":
            let start = s.pref("payStart", "")
            let period = max(1, s.num("payPeriod", 14))
            if start.isEmpty || day.key < start { return false }
            return Day.between(start, day.key) % period == 0
        default:
            return false
        }
    }
}
