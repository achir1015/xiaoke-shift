# iPhone 版交接文件（給 Mac 上的 Claude Code）

> 這份文件由 Windows 筆電上的 Claude Code 寫好，交給 MacBook Pro 上的 Claude Code 接手。
> 兩台電腦的 Claude 記憶與檔案**不會**自動同步，一切以這個 repo 為準。

## 0. 背景

- 使用者：achir1015（吳玉柱，病房助理）。溝通一律用**繁體中文**。
- Android 版「小柯班表助手」已完成並實機驗證，原始碼在本 repo 的 `src/`（Java，不用 Gradle，`build.py` 打包）。
  它是參考「班表小幫手 Shift Calendar 1.79.8（lincyu.shifttable）」的功能重新實作（用 REA＋jadx 解析），另外加上農民曆、Google 行事曆、小柯機器人、病房助理工作站月班表匯入。
- **這一期目標：做 iPhone 版**，功能跟 Android 版一致，資料格式相容（Android 的備份 ZIP 要能直接還原到 iPhone）。
- 使用者**沒有** Apple 開發者計畫（US$99/年）。先用 **Xcode＋免費 Apple ID** 直接裝到使用者的 iPhone（7 天到期要重裝）；之後可考慮 GitHub Actions 編 IPA＋SideStore/AltStore 給同事。
- 使用者有在 GitHub 網頁直接改 README 的習慣：push 前先 `git pull --rebase`。
  commit 身分：`git -c user.name=achir1015 -c user.email=achir1015@gmail.com commit ...`

## 1. 建議技術選擇

- Swift + **SwiftUI**，最低 iOS 17（使用者的 iPhone 版本開工前先確認；鬧鐘若要用 AlarmKit 需 iOS 26）。
- 專案放在 repo 的 `ios/` 資料夾。可用 Xcode 建專案，或用 XcodeGen（`brew install xcodegen`，`project.yml` 好版控）。
- 套件 ID 建議：`com.achir.xiaokeshift`（與 Android 同名）；免費 Apple ID 若衝突就加後綴。
- 不需第三方套件；網路用 `URLSession`、行事曆用 `EventKit`、小工具用 `WidgetKit`、地圖用 `MapKit`、語音用 `AVSpeechSynthesizer`、ZIP 可用 `Apple Archive` 或簡單自寫 ZIP（store 模式）—— 需能讀 Android 產生的標準 ZIP（deflate）。

## 2. 功能清單（Android 版已有，全部要做）

**月曆主畫面**
- 月份切換（‹ 今 ›，點標題選年月）；上方下拉切換班表（五份自己的班表＋好友）。
- 格子：日期、農曆日（初一顯示「X月」、節氣綠字、國定假日紅字取代農曆）、班別色塊（顯示原代號）、心情圖示、備註紅點、發薪日 `$`、Google 事件藍點。
- 點一下 → 單日編輯；**按住滑過一段日期**（橫向滑或長按 0.45 秒後拖曳）→ 放開跳出「把 N 天設成」一次設定。
- 下方卡片：選取日的班別說明、備註、加班/借休/日津貼、朋友當天班別（休假不顯示）、農民曆（干支、建除、宜忌、沖煞、下一節氣）。

**單日編輯**：班別按鈕（隱藏的不顯示）、自訂代號、清除、備註文字、加班/借休/日津貼（只有「我的班表」）、自訂特殊假日、「📝 備註記錄」按鈕、「批次編輯」。

**備註記錄頁**：心情（😀😊🥰😌😐😴😢😡🤒😷🥳💪☕🎉）、文字（網址可點）、超連結（標題＋網址）、圖片（縮到長邊 1600px JPEG 存 App 內）、Google 地圖（輸入地名，嵌入地圖，點了開 Google/Apple 地圖）。

**其他**：班別設定（代號/名稱/時間/顏色/休假類/薪資/說明/隱藏/排序/鬧鐘＋前日響）、班表樣式（循環代號、起算日、貼上 N 天或到某日、自動重覆 from～to、重新讀取月曆、從批次選取複製成樣式）、批次編輯、工作站月班表匯入、Google 行事曆同步、小柯連線、鬧鐘設定（貪睡分鐘、音量、震動、全螢幕、翻面關閉、關閉後小柯唸班表、只響我的班表）、統計、所有備註記錄、特殊假日、發薪日（每月固定日／固定週期、貨幣）、班表管理（五份班表改名、清除、好友新增/刪除、從工作站匯入同事）、傳送（文字／截圖）、設定（週首日、今日樣式、字型大小、週數、週末紅字、顯示時間、隱藏休假、假日、事件點、備註點大小顏色、深色、方向、小工具按鈕、自動清除三個月前、密碼）、備份/還原 ZIP、使用說明。

**桌面小工具**：「小柯今日班表」（小柯臉＋今天/明天班別＋農曆）、「小柯月班表」（整月）。

**App 圖示**：小柯臉（深藍底 `#101522`、白色彎月笑眼、粉紅腮紅、微笑，見 `res/mipmap/ic_launcher.xml`；原始 SVG 概念見 XiaoKe_App 的 icon.svg）＋右上角小月曆。

## 3. 資料格式（必須相容 Android 備份）

備份 ZIP = `data.json` ＋ `notes/<uuid>.jpg`（備註圖片）。舊版也可能是純 JSON 檔。

```jsonc
{
  "app": "小柯班表助手",            // 還原時檢查
  "version": 2,
  "types": [ { "code":"D", "name":"白班", "color":-291840 /*ARGB int*/, "start":"08:00", "end":"16:00",
               "off":false, "desc":"", "hidden":false, "pay":0, "alarmOn":false, "alarmBefore":false, "alarmTime":"06:30" } ],
  "patterns": [ { "name":"…", "profile":"me", "start":"2026-10-14", "from":"", "to":"", "auto":true, "seq":["D","D","E","E","N","N","OFF","OFF"] } ],
  "profiles": { "me":"我的班表", "job2":"第二班表", "job3":"…", "job4":"…", "job5":"…", "f:王小明":"王小明" },
  "data": { "me": { "days": { "2026-10-14":"N" },
                    "notes": { "2026-10-14": "純文字（舊格式）" 或 { "text":"", "mood":"😊",
                               "links":[{"title":"","url":""}], "images":["uuid.jpg"], "places":[{"name":"台北車站"}] } } } },
  "extra": { "ot": {"2026-10-14": 2}, "to": {}, "allow": {} },   // 我的班表：加班、借休、日津貼
  "holidays": { "2026-10-20": "自訂假日名稱" }
}
```

- 預設班別：D 白班 `#FB8C00` 08–16、E 小夜 `#1E88E5` 16–00、N 大夜 `#5E35B1` 00–08、OFF 休假 `#43A047`（off）、例 例假 `#00897B`（off）。
- 工時：下班 ≤ 上班視為跨夜。
- 實際班別 = 手動排的（`days`）優先，否則看同 profile 的自動重覆樣式：`seq[(日期 - start) mod len]`，限 from～to 且 ≥ start。
- 好友 profile id = `f:` + 姓名；`me`、`job2`～`job5` 固定存在。

**代號歸類規則（與病房助理工作站月班表系統相同）**：完全符合班別代號優先；否則
含 `OFF`／國／休 → 休假類；含「例」→ 例假；含 `D` 或「白值」→ 白班；含 `E` → 小夜；含 `N` 或「夜8」→ 大夜；其他（如傷照）照原代號灰色顯示。

## 4. 病房助理工作站月班表匯入

- Firestore REST（免金鑰讀取）：
  `GET https://firestore.googleapis.com/v1/projects/monthlyschedule-a5dea/databases/(default)/documents/schedules/{assistant|nurse}-YYYY-MM`
- 404 = 該月尚未建立。
- `fields.employees.arrayValue.values[].mapValue.fields` → `name.stringValue`、`shifts.mapValue.fields["1".."31"].stringValue`。
- UI：類別（病房助理／護理師）→ 背景讀名單 → **姓名下拉選單** → 匯入（可勾覆蓋已排的日子）。每份班表各自記住上次的類別與姓名。
- 匯入到 job2～job5 時，**班表名稱改成所選姓名**。也可勾選多位同事匯入成好友。
- ⚠️ 該文件含其他同事的班表（個資），開發時**不要用 curl 把內容讀出來看**，請使用者在手機上驗證。

## 5. 農民曆、假日（直接移植 `src/com/achir/xiaokeshift/Almanac.java`、`Holidays.java`）

- 農曆表 2000–2099（`L_CNY`、`L_BITS`、`L_LEAP`），節氣 21 世紀近似公式，干支、生肖、建除十二神宜忌、沖煞。實機核對過：2026-10-14 = 九月初五、丙午（馬）年 戊戌月 辛酉日、閉日、下一節氣霜降 10/23。
- 國定假日：元旦、和平紀念日、兒童節、清明、勞動節、教師節 9/28、國慶日、光復節 10/25、行憲紀念日 12/25；農曆：除夕、春節初一～初三、端午、中秋。補假不自動算（使用者用自訂假日補）。

## 6. iOS 上的對應與限制

| Android | iOS 做法 |
| --- | --- |
| CalendarContract 寫入 Google 行事曆 | EventKit；使用者先在「設定→行事曆→帳號」加入 Google。事件標題「【班】白班（D/洗）」、描述含 `#小柯班表`，重同步先刪同月帶此標記的事件 |
| AlarmManager＋前景服務鬧鐘 | iOS 26+ 用 **AlarmKit**；較舊 iOS 用 `UNUserNotificationCenter` 本地通知（時間敏感），點通知進 App 後小柯（AVSpeechSynthesizer, zh-TW）唸班表。**免費 Apple ID 可能無法使用部分 entitlement，需實測** |
| 區網 API `:8090`（給小柯機器人） | iOS App 不能背景常駐伺服器 → 前景時可提供；長期方案是上傳到 Firebase 讓小柯讀（需使用者同意與寫入規則）。先不做或只做前景 |
| AppWidget | WidgetKit（需 App Group 共享資料；免費帳號可能受限，需實測） |
| WebView 嵌入 Google 地圖 | MapKit + `MKLocalSearch`，或 WKWebView 嵌入網址；點了開 Google 地圖 App（`comgooglemaps://`）或 Apple 地圖 |
| SAF 備份 | `fileExporter`/`fileImporter`（可存到「檔案」或 iCloud 雲碟／Google 雲端硬碟） |

## 7. 開發步驟建議

1. 確認 Mac：macOS 版本、Xcode 已安裝（App Store），`xcode-select --install`。iPhone 用線接 Mac，iPhone 開「設定→隱私權與安全性→開發者模式」，Xcode 登入免費 Apple ID（Settings → Accounts）。
2. 在 `ios/` 建 SwiftUI 專案；先做資料層（相容上面的 JSON）＋月曆＋單日編輯＋農民曆，實機安裝驗證。
3. 備份還原（先用 Android 的 ZIP 測還原）、班別設定、樣式、批次/滑動選取、統計。
4. 工作站匯入、EventKit、備註記錄、通知/鬧鐘、小工具。
5. README 加 iPhone 版說明；之後評估 GitHub Actions＋SideStore 給同事安裝。

每個階段完成就請使用者在 iPhone 上實測（遠端自動點擊不可靠，UI 請使用者親手驗證），通過後 commit＋push。
