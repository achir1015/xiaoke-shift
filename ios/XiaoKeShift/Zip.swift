import Foundation
import Compression

/// 最小的 ZIP 讀寫（不需第三方套件）。
/// 讀：依中央目錄讀檔，支援 store 與 deflate（Android ZipOutputStream 產生的就是 deflate＋data descriptor）。
/// 寫：store 模式（不壓縮；JPEG 本來就壓過了）。
enum Zip {
    struct Entry { let name: String; let data: Data }

    // MARK: 讀

    static func read(_ z: Data) throws -> [Entry] {
        let b = [UInt8](z)
        func u16(_ o: Int) -> Int { Int(b[o]) | Int(b[o + 1]) << 8 }
        func u32(_ o: Int) -> Int { u16(o) | u16(o + 2) << 16 }
        // 找中央目錄結尾（EOCD）
        guard b.count >= 22 else { throw err("檔案太小，不是 ZIP") }
        var eocd = -1
        var i = b.count - 22
        while i >= max(0, b.count - 22 - 65535) {
            if b[i] == 0x50 && b[i + 1] == 0x4B && b[i + 2] == 5 && b[i + 3] == 6 { eocd = i; break }
            i -= 1
        }
        guard eocd >= 0 else { throw err("找不到 ZIP 目錄") }
        let count = u16(eocd + 10)
        var p = u32(eocd + 16)
        var out: [Entry] = []
        for _ in 0..<count {
            guard p + 46 <= b.count, u32(p) == 0x02014B50 else { throw err("ZIP 目錄損壞") }
            let method = u16(p + 10)
            let csize = u32(p + 20), usize = u32(p + 24)
            let nlen = u16(p + 28), xlen = u16(p + 30), clen = u16(p + 32)
            let local = u32(p + 42)
            let name = String(decoding: b[(p + 46)..<(p + 46 + nlen)], as: UTF8.self)
            p += 46 + nlen + xlen + clen
            if name.hasSuffix("/") { continue }
            guard local + 30 <= b.count, u32(local) == 0x04034B50 else { throw err("ZIP 檔案損壞") }
            let start = local + 30 + u16(local + 26) + u16(local + 28)
            guard start + csize <= b.count else { throw err("ZIP 檔案不完整") }
            let raw = Data(b[start..<(start + csize)])
            switch method {
            case 0: out.append(Entry(name: name, data: raw))
            case 8: out.append(Entry(name: name, data: try inflate(raw, size: usize)))
            default: throw err("不支援的壓縮方式（\(method)）")
            }
        }
        return out
    }

    private static func inflate(_ src: Data, size: Int) throws -> Data {
        if size == 0 { return Data() }
        var dst = Data(count: size)
        let n = dst.withUnsafeMutableBytes { d in
            src.withUnsafeBytes { s in
                compression_decode_buffer(d.bindMemory(to: UInt8.self).baseAddress!, size,
                                          s.bindMemory(to: UInt8.self).baseAddress!, src.count, nil, COMPRESSION_ZLIB)
            }
        }
        guard n == size else { throw err("解壓縮失敗") }
        return dst
    }

    // MARK: 寫

    static func write(_ entries: [Entry]) -> Data {
        var out = Data(), dir = Data()
        func le16(_ v: Int, _ d: inout Data) { d.append(UInt8(v & 0xFF)); d.append(UInt8(v >> 8 & 0xFF)) }
        func le32(_ v: Int, _ d: inout Data) { le16(v & 0xFFFF, &d); le16(v >> 16 & 0xFFFF, &d) }
        for e in entries {
            let name = Data(e.name.utf8), crc = Int(crc32(e.data)), off = out.count
            le32(0x04034B50, &out); le16(20, &out); le16(0x0800, &out); le16(0, &out)   // UTF-8 檔名、store
            le16(0, &out); le16(0x21, &out)                                          // 時間 00:00、日期 1980-01-01
            le32(crc, &out); le32(e.data.count, &out); le32(e.data.count, &out)
            le16(name.count, &out); le16(0, &out)
            out.append(name); out.append(e.data)

            le32(0x02014B50, &dir); le16(20, &dir); le16(20, &dir); le16(0x0800, &dir); le16(0, &dir)
            le16(0, &dir); le16(0x21, &dir)
            le32(crc, &dir); le32(e.data.count, &dir); le32(e.data.count, &dir)
            le16(name.count, &dir); le16(0, &dir); le16(0, &dir); le16(0, &dir); le16(0, &dir)
            le32(0, &dir); le32(off, &dir)
            dir.append(name)
        }
        let dirOff = out.count
        out.append(dir)
        le32(0x06054B50, &out); le16(0, &out); le16(0, &out)
        le16(entries.count, &out); le16(entries.count, &out)
        le32(dir.count, &out); le32(dirOff, &out); le16(0, &out)
        return out
    }

    private static let table: [UInt32] = (0..<256).map { n -> UInt32 in
        var c = UInt32(n)
        for _ in 0..<8 { c = c & 1 != 0 ? 0xEDB88320 ^ (c >> 1) : c >> 1 }
        return c
    }

    static func crc32(_ d: Data) -> UInt32 {
        var c: UInt32 = 0xFFFF_FFFF
        for x in d { c = table[Int((c ^ UInt32(x)) & 0xFF)] ^ (c >> 8) }
        return c ^ 0xFFFF_FFFF
    }

    private static func err(_ s: String) -> NSError {
        NSError(domain: "Zip", code: 1, userInfo: [NSLocalizedDescriptionKey: s])
    }
}
