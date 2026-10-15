import SwiftUI
import PhotosUI
import WebKit

/// 備註記錄（某一天）：心情圖示、文字說明（網址可點）、超連結、插入圖片、插入 Google 地圖。
/// 圖片縮到長邊 1600 px 存在 App 的 Documents/notes/（與 Android 備份 ZIP 的 notes/ 相同檔名）。
/// 地圖用 Google 地圖嵌入網址（不需金鑰）顯示，點一下開 Google 地圖。
struct NoteView: View {
    static let MOODS = ["😀", "😊", "🥰", "😌", "😐", "😴", "😢", "😡", "🤒", "😷", "🥳", "💪", "☕", "🎉"]

    @EnvironmentObject var st: Store
    @Environment(\.dismiss) private var dismiss
    @Environment(\.openURL) private var openURL
    let day: Day
    let profile: String

    @State private var mood = ""
    @State private var text = ""
    @State private var links: [[String: Any]] = []
    @State private var images: [String] = []
    @State private var places: [[String: Any]] = []
    @State private var rest: [String: Any] = [:]     // 其他未知欄位原樣保留
    @State private var photoItems: [PhotosPickerItem] = []
    @State private var bigImage: String?
    @State private var addingLink = false
    @State private var linkTitle = ""
    @State private var linkURL = "https://"
    @State private var addingPlace = false
    @State private var placeName = ""
    @State private var loaded = false

    static var imageDir: URL {
        let d = FileManager.default.urls(for: .documentDirectory, in: .userDomainMask)[0].appendingPathComponent("notes")
        try? FileManager.default.createDirectory(at: d, withIntermediateDirectories: true)
        return d
    }

    var body: some View {
        Form {
            Section {
                Text("\(st.profileName(profile))：\(st.describe(profile, day.key))\n農曆\(Almanac.lunarString(day))")
                    .font(.footnote).foregroundStyle(.secondary)
            }
            Section("😊 心情") {
                ScrollView(.horizontal, showsIndicators: false) {
                    HStack(spacing: 4) {
                        ForEach(Self.MOODS, id: \.self) { m in moodButton(m) }
                    }
                }
            }
            Section("📝 文字說明（網址可直接點開）") {
                TextField("寫點什麼…", text: $text, axis: .vertical).lineLimit(4...)
                ForEach(urls(in: text), id: \.self) { u in
                    Button(u.absoluteString) { openURL(u) }.font(.footnote)
                }
            }
            Section("🔗 超連結（左滑刪除）") {
                ForEach(links.indices, id: \.self) { i in
                    let o = links[i]
                    Button {
                        if let u = URL(string: o.str("url")) { openURL(u) }
                    } label: {
                        VStack(alignment: .leading) {
                            Text("🔗 " + (o.str("title").isEmpty ? o.str("url") : o.str("title")))
                            if !o.str("title").isEmpty { Text(o.str("url")).font(.caption).foregroundStyle(.secondary) }
                        }
                    }
                }
                .onDelete { links.remove(atOffsets: $0) }
                Button("＋ 加入連結") { linkTitle = ""; linkURL = "https://"; addingLink = true }
            }
            Section("🖼️ 圖片（點一下放大，長按刪除）") {
                if !images.isEmpty {
                    ScrollView(.horizontal, showsIndicators: false) {
                        HStack(spacing: 6) {
                            ForEach(images, id: \.self) { name in
                                thumb(name)
                                    .onTapGesture { bigImage = name }
                                    .contextMenu {
                                        Button("刪除這張圖片", role: .destructive) { deleteImage(name) }
                                    }
                            }
                        }
                    }
                }
                PhotosPicker(selection: $photoItems, matching: .images) { Text("＋ 插入圖片") }
            }
            Section("🗺️ Google 地圖（點地圖開啟導航，長按刪除）") {
                ForEach(places.indices, id: \.self) { i in
                    let name = places[i].str("name")
                    VStack(alignment: .leading, spacing: 6) {
                        Text("📍 \(name)　（點這裡開 Google 地圖）").foregroundStyle(Color.accentColor)
                        MapEmbed(name: name).frame(height: 200).allowsHitTesting(false)
                            .clipShape(RoundedRectangle(cornerRadius: 8))
                    }
                    .contentShape(Rectangle())
                    .onTapGesture { openMap(name) }
                    .contextMenu {
                        Button("刪除這個地點", role: .destructive) { places.remove(at: i) }
                    }
                }
                Button("＋ 插入地圖") { placeName = ""; addingPlace = true }
            }
        }
        .navigationTitle("備註記錄　\(day.m)/\(day.d)（\(day.weekdayName)）")
        .navigationBarTitleDisplayMode(.inline)
        .navigationBarBackButtonHidden()
        .toolbar {
            ToolbarItem(placement: .cancellationAction) { Button("取消") { dismiss() } }
            ToolbarItem(placement: .confirmationAction) { Button("儲存") { save(); dismiss() }.bold() }
        }
        .alert("加入連結", isPresented: $addingLink) {
            TextField("標題（可空白）", text: $linkTitle)
            TextField("網址", text: $linkURL).keyboardType(.URL).textInputAutocapitalization(.never)
            Button("加入") {
                var u = linkURL.trimmingCharacters(in: .whitespaces)
                if u.isEmpty || u == "https://" { return }
                if !u.contains("://") { u = "https://" + u }
                links.append(["title": linkTitle.trimmingCharacters(in: .whitespaces), "url": u])
            }
            Button("取消", role: .cancel) { }
        }
        .alert("插入地圖", isPresented: $addingPlace) {
            TextField("地名或地址（例如：台北車站）", text: $placeName)
            Button("插入") {
                let n = placeName.trimmingCharacters(in: .whitespaces)
                if !n.isEmpty { places.append(["name": n]) }
            }
            Button("取消", role: .cancel) { }
        }
        .fullScreenCover(item: Binding(get: { bigImage.map { ImageName(name: $0) } }, set: { bigImage = $0?.name })) { img in
            ZStack(alignment: .topTrailing) {
                Color.black.ignoresSafeArea()
                if let ui = UIImage(contentsOfFile: Self.imageDir.appendingPathComponent(img.name).path) {
                    Image(uiImage: ui).resizable().scaledToFit().frame(maxWidth: .infinity, maxHeight: .infinity)
                }
                Button("關閉") { bigImage = nil }.padding().foregroundStyle(.white)
            }
        }
        .onChange(of: photoItems) { _, items in
            guard !items.isEmpty else { return }
            Task {
                for it in items {
                    if let d = try? await it.loadTransferable(type: Data.self), let name = Self.saveImage(d) { images.append(name) }
                }
                photoItems = []
            }
        }
        .onAppear(perform: load)
    }

    private func moodButton(_ m: String) -> some View {
        let on = m == mood
        let bg: Color = on ? Color(argb: ShiftType.argb(0xFFFFE082)) : .clear
        return Text(m).font(.system(size: 28)).frame(width: 46, height: 46)
            .background(Circle().fill(bg))
            .onTapGesture { mood = on ? "" : m }
    }

    private struct ImageName: Identifiable { let name: String; var id: String { name } }

    private func thumb(_ name: String) -> some View {
        let ui = UIImage(contentsOfFile: Self.imageDir.appendingPathComponent(name).path)
        return Group {
            if let ui { Image(uiImage: ui).resizable().scaledToFill() } else { Color.gray.overlay(Text("？")) }
        }
        .frame(width: 110, height: 110).clipShape(RoundedRectangle(cornerRadius: 8))
    }

    private func urls(in s: String) -> [URL] {
        guard let det = try? NSDataDetector(types: NSTextCheckingResult.CheckingType.link.rawValue) else { return [] }
        return det.matches(in: s, range: NSRange(s.startIndex..., in: s)).compactMap(\.url)
    }

    /// 讀入圖片、縮到長邊 1600 px、存成 JPEG，回傳檔名。
    static func saveImage(_ data: Data) -> String? {
        guard let img = UIImage(data: data) else { return nil }
        let s = min(1, 1600 / max(img.size.width, img.size.height))
        let size = CGSize(width: (img.size.width * s).rounded(), height: (img.size.height * s).rounded())
        let fmt = UIGraphicsImageRendererFormat()
        fmt.scale = 1
        let out = UIGraphicsImageRenderer(size: size, format: fmt).image { _ in img.draw(in: CGRect(origin: .zero, size: size)) }
        guard let jpg = out.jpegData(compressionQuality: 0.85) else { return nil }
        let name = UUID().uuidString.lowercased() + ".jpg"
        do { try jpg.write(to: imageDir.appendingPathComponent(name)) } catch { return nil }
        return name
    }

    private func deleteImage(_ name: String) {
        try? FileManager.default.removeItem(at: Self.imageDir.appendingPathComponent(name))
        images.removeAll { $0 == name }
    }

    private func openMap(_ name: String) {
        let q = name.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed) ?? name
        if let g = URL(string: "comgooglemaps://?q=\(q)"), UIApplication.shared.canOpenURL(g) {
            openURL(g)
        } else if let u = URL(string: "https://www.google.com/maps/search/?api=1&query=\(q)") {
            openURL(u)
        }
    }

    private func load() {
        guard !loaded else { return }
        loaded = true
        var o = st.noteObj(profile, day.key)
        mood = o.str("mood")
        text = o.str("text")
        links = o["links"] as? [[String: Any]] ?? []
        images = (o["images"] as? [Any])?.map { "\($0)" } ?? []
        places = o["places"] as? [[String: Any]] ?? []
        for k in ["mood", "text", "links", "images", "places"] { o[k] = nil }
        rest = o
    }

    private func save() {
        var o = rest
        o["text"] = text.trimmingCharacters(in: .whitespacesAndNewlines)
        o["mood"] = mood
        o["links"] = links
        o["images"] = images
        o["places"] = places
        st.setNoteObj(profile, day.key, o)
        st.save()
    }
}

/// Google 地圖嵌入（不需金鑰）。
struct MapEmbed: UIViewRepresentable {
    let name: String

    func makeUIView(context: Context) -> WKWebView {
        let w = WKWebView()
        w.scrollView.isScrollEnabled = false
        let q = name.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed) ?? name
        if let u = URL(string: "https://maps.google.com/maps?q=\(q)&z=15&hl=zh-TW&output=embed") { w.load(URLRequest(url: u)) }
        return w
    }

    func updateUIView(_ w: WKWebView, context: Context) { }
}
