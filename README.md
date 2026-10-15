# 小柯班表助手（XiaoKe Shift）
<img width="568" height="931" alt="image" src="https://github.com/user-attachments/assets/886bfe0d-58a5-4a74-8980-fca5bc7fd6f7" />
https://github.com/user-attachments/assets/cb08328b-99ed-4308-8686-ba1c95cf4373

## 📲 下載安裝（Android 7.0 以上）

**[⬇️ 下載 XiaoKeShift.apk（v1.1.0）](https://github.com/achir1015/xiaoke-shift/raw/main/release/XiaoKeShift.apk)**

<img src="release/qr.png" width="180" alt="手機掃描下載 APK">

1. 用手機開上面的連結（或掃 QR Code）下載 APK。
2. 點下載完成的檔案 → 若出現「為了安全起見，你的手機目前無法安裝來自這個來源的應用程式」，按「設定」→ 開啟「允許來自這個來源的應用程式」→ 返回再按「安裝」。
3. 三星手機若被 Play Protect 擋下，選「仍要安裝」。
4. 更新新版時直接安裝覆蓋即可，班表資料會保留（建議先「⋮ → 備份班表」）。

## 📱 iPhone 安裝（iOS 17 以上，用 SideStore / AltStore 從 GitHub 安裝與更新）

iPhone 不能像 Android 一樣直接點連結安裝，要透過 **SideStore**（或 AltStore）用**你自己的 Apple ID**（免費帳號即可）簽名安裝。
每次這個 repo 的 iPhone 程式更新，GitHub 會自動編好新版放到 [Releases](https://github.com/achir1015/xiaoke-shift/releases)，SideStore 會提示更新。

**來源網址（複製這行）：**

```
https://github.com/achir1015/xiaoke-shift/releases/latest/download/source.json
```

1. **第一次：安裝 SideStore**（需要電腦一次）— 依照官方說明 <https://docs.sidestore.io> 安裝。
   （也可以用 AltStore：電腦裝 AltServer，<https://altstore.io>。）
   iPhone 要開「設定 → 隱私權與安全性 → 開發者模式」。
2. 打開 SideStore →「Sources（來源）」→ 右上 **＋** → 貼上上面的來源網址 → 加入。
3. 在來源裡點 **小柯班表助手** → 安裝（用你的 Apple ID 簽名）。
4. 第一次開啟若出現「未受信任的開發者」：「設定 → 一般 → VPN 與裝置管理」→ 信任你的 Apple ID。
5. **更新**：SideStore 的「My Apps」出現新版時按「Update」，班表資料會保留（建議先「⋯ → 備份班表」）。

注意：
- 免費 Apple ID 簽的 App **7 天到期**，要在 SideStore 按「Refresh」（SideStore 可在背景自動續期）；同時最多 3 個自行安裝的 App。
- 鬧鐘像「時鐘」App 一樣響需要 **iOS 26** 以上；較舊的 iOS 用通知提醒。
- 也可以直接下載 IPA 自己用其他工具安裝：[⬇️ 最新版 XiaoKeShift.ipa](https://github.com/achir1015/xiaoke-shift/releases/latest/download/XiaoKeShift.ipa)
- Android 的備份 ZIP 可以在 iPhone「⋯ → 還原班表」直接還原。

有 Mac 的話也可以自己編譯安裝：`brew install xcodegen`，`cd ios && xcodegen generate && open XiaoKeShift.xcodeproj`，
在 Signing & Capabilities 選自己的 Team（Bundle ID 衝突就改 `ios/project.yml` 的 `com.achir.xiaokeshift` 與 App Group），接上 iPhone 按 ▶︎。

輪班班表 Android App。參考「班表小幫手（Shift Calendar）」的功能重新實作，另外加上：

- **農民曆**：離線計算農曆、干支、節氣、建除宜忌、沖煞（移植自小柯 LX04 / ESP32 版）
- **Google 行事曆**：透過系統 CalendarContract 寫入手機上的 Google 帳號行事曆，並在 App 內顯示當天行程
- **小柯機器人**：區網 API（App 開著時 `:8090`），鬧鐘關閉後小柯用語音唸出今天的班
  - `GET /api/say?day=today|tomorrow|yyyy-MM-dd` → 一句話（給 TTS）
  - `GET /api/day?date=yyyy-MM-dd` → JSON
  - `GET /api/month?ym=yyyy-MM` → JSON
- **病房助理工作站月班表匯入**：讀 Firestore `schedules/{assistant|nurse}-YYYY-MM`，代號規則同月班表系統（D 白班、E 小夜、N 大夜、OFF/國/休 休假、例 例假）
- **五份班表**（可改名）＋好友班表（手動或從工作站匯入同事）
- 小柯臉圖示、「今日班表」與「月班表」兩種桌面小工具

原版功能：班別（時間、顏色、薪資、說明、排序、隱藏、鬧鐘含前日）、班表樣式（複製／貼上／自動重覆）、批次編輯（範圍選取）、鬧鐘（鈴聲、貪睡、全螢幕、翻面關閉、開機重排、系統鬧鐘）、統計（工時、加班、借休、日津貼、薪資）、發薪日、特殊假日（台灣國定假日離線計算＋自訂）、傳送（文字／截圖）、所有備註、設定（週首日、週數、今日樣式、字型、深色、密碼、方向、自動清除）、備份還原（可選 Google 雲端硬碟）。

## 編譯

不用 Gradle：`aapt2 → javac → d8 → zipalign → apksigner`。

需要 Python 3.8+、JDK 17、Android SDK（build-tools 34、platforms/android-34）。建立 `local.properties`：

```
sdk.dir=C\:\path\to\android-sdk
jdk.dir=C\:\path\to\jdk-17
```

```bash
python build.py            # 產生 build/XiaoKeShift.apk（第一次會自動建立簽章 xiaokeshift.jks）
python build.py install    # 另外用 adb 安裝並啟動
```

## 原始碼

| 檔案 | 內容 |
| --- | --- |
| `MainActivity` | 月曆主畫面、排班對話框、批次編輯、選單 |
| `MonthView` | 月曆繪製（農曆、假日、班別色塊、備註點、發薪日、事件點） |
| `Store` / `ShiftType` / `Pattern` | 資料（SharedPreferences JSON）、班別、班表樣式 |
| `ShiftUi` / `MoreUi` / `Ui` | 班別與樣式設定、其他功能對話框 |
| `Almanac` / `Holidays` | 農民曆、國定假日、發薪日 |
| `WardImport` | 病房助理工作站月班表匯入 |
| `GCalSync` | Google 行事曆同步 |
| `XiaoKeServer` | 小柯機器人區網 API |
| `AlarmScheduler` / `AlarmReceiver` / `AlarmService` / `AlarmActivity` | 班別鬧鐘 |
| `XiaoKeWidget` / `MonthWidget` | 桌面小工具 |
