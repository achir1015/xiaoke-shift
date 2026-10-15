import SwiftUI
import UniformTypeIdentifiers

/// 備份／還原：ZIP＝data.json＋notes/ 備註記錄的圖片（與 Android 版相同）。還原也接受第 1 版的純 JSON 備份。
enum Backup {
    static func export(_ st: Store) throws -> (Data, Int) {
        var entries = [Zip.Entry(name: "data.json", data: try st.exportData())]
        let dir = NoteView.imageDir
        let files = (try? FileManager.default.contentsOfDirectory(at: dir, includingPropertiesForKeys: nil)) ?? []
        for f in files.sorted(by: { $0.lastPathComponent < $1.lastPathComponent }) where !f.lastPathComponent.hasPrefix(".") {
            if let d = try? Data(contentsOf: f) { entries.append(Zip.Entry(name: "notes/" + f.lastPathComponent, data: d)) }
        }
        return (Zip.write(entries), entries.count - 1)
    }

    /// 讀好但還沒套用的備份。
    struct Pending {
        let json: Data
        let images: [Zip.Entry]
    }

    static func read(_ url: URL) throws -> Pending {
        let ok = url.startAccessingSecurityScopedResource()
        defer { if ok { url.stopAccessingSecurityScopedResource() } }
        let raw = try Data(contentsOf: url)
        if raw.starts(with: [0x50, 0x4B]) {
            let entries = try Zip.read(raw)
            guard let json = entries.first(where: { $0.name == "data.json" })?.data else {
                throw NSError(domain: "Backup", code: 1, userInfo: [NSLocalizedDescriptionKey: "這不是小柯班表助手的備份檔"])
            }
            // 只取 notes/ 底下的檔名，避免路徑跳脫
            let imgs = entries.compactMap { e -> Zip.Entry? in
                guard e.name.hasPrefix("notes/") else { return nil }
                let base = (e.name as NSString).lastPathComponent
                return base.isEmpty || base.hasPrefix(".") ? nil : Zip.Entry(name: base, data: e.data)
            }
            return Pending(json: json, images: imgs)
        }
        return Pending(json: raw, images: [])      // 第 1 版純 JSON
    }

    /// 套用：資料取代現有資料，圖片寫回 notes/（同名覆蓋）。
    static func apply(_ st: Store, _ p: Pending) throws {
        try st.importAll(p.json)
        let dir = NoteView.imageDir
        for e in p.images { try e.data.write(to: dir.appendingPathComponent(e.name), options: .atomic) }
    }
}

/// 給 fileExporter 用的 ZIP 檔。
struct ZipFile: FileDocument {
    static var readableContentTypes: [UTType] { [.zip] }
    var data: Data
    init(data: Data) { self.data = data }
    init(configuration: ReadConfiguration) throws { data = configuration.file.regularFileContents ?? Data() }
    func fileWrapper(configuration: WriteConfiguration) throws -> FileWrapper { FileWrapper(regularFileWithContents: data) }
}
