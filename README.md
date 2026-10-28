# 小柯班表助手（XiaoKe Shift）
<img width="568" height="931" alt="image" src="https://github.com/user-attachments/assets/886bfe0d-58a5-4a74-8980-fca5bc7fd6f7" />

影音撥放網址
https://github.com/user-attachments/assets/cb08328b-99ed-4308-8686-ba1c95cf4373
## 📲 下載安裝（Android 7.0 以上）

**[⬇️ 下載 XiaoKeShift.apk（v1.2.0）](https://github.com/achir1015/xiaoke-shift/raw/main/release/XiaoKeShift.apk)**

<img src="release/qr.png" width="180" alt="手機掃描下載 APK">

1. 用手機開上面的連結（或掃 QR Code）下載 APK。
2. 點下載完成的檔案 → 若出現「為了安全起見，你的手機目前無法安裝來自這個來源的應用程式」，按「設定」→ 開啟「允許來自這個來源的應用程式」→ 返回再按「安裝」。
3. 三星手機若被 Play Protect 擋下，選「仍要安裝」。
4. 更新新版時直接安裝覆蓋即可，班表資料會保留（建議先「⋮ → 備份班表」）。

## 📱 iPad／iPhone 安裝（和小柯麻將、象棋、跳棋相同）

| 平台 | 安裝方式 |
| --- | --- |
| iPad／iPhone | `xiaoke-shift-ios.ipa`：用 **Safari** 開 [安裝頁](https://achir1015.github.io/xiaoke-shift/ios/) 按「安裝到這台 iPad／iPhone」（App Store 上架前限開發者登記過的裝置；第一次要在「設定 → 隱私權與安全性」開啟「開發者模式」） |

**[⬇️ 開啟安裝頁](https://achir1015.github.io/xiaoke-shift/ios/)**　電腦上可用 iPad／iPhone 相機掃：

<img src="docs/ios/qr.png" width="160" alt="iPad／iPhone 掃描開啟安裝頁">

1. 用 Safari 開安裝頁，按藍色的「安裝到這台 iPad／iPhone」，跳出視窗再按「安裝」（不用另外的 App）。
2. 回到主畫面，等「小柯班表」圖示的進度跑完。
3. 第一次打開若顯示「需要開發者模式」：「設定 → 隱私權與安全性」最下面打開「開發者模式」→ 重新啟動 → 開機後按「開啟」。每台裝置只要做一次。
4. 以後有新版本：App 右上角「⋯ → 檢查更新」會回到安裝頁，再按一次安裝即可，資料會保留（建議先「⋯ → 備份班表」）。

- iPad 版：左邊大月曆、右邊當天說明卡，四個方向都可以，支援分割畫面；桌面可加「特大」月班表小工具。
- 鬧鐘像「時鐘」App 一樣響需要 **iOS 26** 以上；較舊的 iOS 用通知提醒。
- Android 的備份 ZIP 可以在 iPad／iPhone「⋯ → 還原班表」直接還原。

<img width="501" height="937" alt="image" src="https://github.com/user-attachments/assets/51140f83-bf1d-4dbe-a7d5-39436168232d" />

影音撥放網址
https://github.com/user-attachments/assets/8d87f459-3ed5-4ae3-baff-85bb0e56c393

<details>
<summary>其他方式：沒有登記的 iPhone 用 SideStore／AltStore（自己的 Apple ID 簽名，7 天要續簽）</summary>

每次 iPhone 程式更新，GitHub 會自動編好未簽名版放到 [Releases](https://github.com/achir1015/xiaoke-shift/releases)，SideStore 會提示更新。

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

</details>

## 🤖 小柯機器人連線（Android／iPhone 都有）

小柯機器人（ESP32 / LX04）跟手機連**同一個 Wi-Fi**，就能向小柯班表助手查班表，用語音唸給你聽。

**設定步驟**
1. 手機打開小柯班表助手 →（Android「⋮」／iPhone「⋯」選單）→「**小柯機器人連線**」，畫面會顯示手機的網址，例如 `http://192.168.1.23:8090`。
2. 把這個網址填到小柯機器人的班表查詢設定。
3. iPhone 第一次開啟時若跳出「要允許在區域網路上尋找裝置嗎？」→ 按「**允許**」（沒允許小柯連不進來）。
4. 測試：iPhone 在連線頁按「測試連線」，出現 `HTTP 200：今天…` 就成功；Android 按「用瀏覽器測試」。也可以用同一個 Wi-Fi 的電腦瀏覽器打開網址看看。

**API**（兩個平台格式相同，埠號 `8090`）

| 網址 | 回傳 |
| --- | --- |
| `GET /api/say?day=today` | 一句話純文字，直接給 TTS 唸（`day` 可用 `today`、`tomorrow`、`yesterday`、`yyyy-MM-dd`） |
| `GET /api/day?date=yyyy-MM-dd` | 當天 JSON：班別代號、名稱、上下班時間、是否休假、備註、農曆、干支、宜忌、沖煞、節氣 |
| `GET /api/month?ym=yyyy-MM` | 整月 JSON：每天的班別、上班天數、休假天數、總工時 |
| `GET /` | 說明頁 |

`/api/say?day=today` 的回應範例：

```
今天10月23日星期五，農曆九月十四，你上白班，08:00 到 16:00。今天是霜降。宜開市、嫁娶、入學、出行，忌訴訟。
```

**注意**
- **App 要開著**小柯才查得到。iPhone 切到背景會暫停服務，回到 App 自動恢復（iOS 限制）；連線頁可開「App 開著時螢幕不要自動關閉」。
- 手機的 IP 可能因重新連 Wi-Fi 而改變；建議在路由器把手機設成固定 IP。
- 只有同一個 Wi-Fi 的裝置連得到，外面網路連不進來。
- 鬧鐘關閉後，手機上的小柯也會用語音唸出今天的班＋農民曆（設定可關）。

輪班班表 Android App。參考「班表小幫手（Shift Calendar）」的功能重新實作，另外加上：

- **農民曆**：離線計算農曆、干支、節氣、建除宜忌、沖煞（移植自小柯 LX04 / ESP32 版）
- **Google 行事曆**：透過系統 CalendarContract 寫入手機上的 Google 帳號行事曆，並在 App 內顯示當天行程
- **小柯機器人**：區網 API（App 開著時 `:8090`），鬧鐘關閉後小柯用語音唸出今天的班（見上方「🤖 小柯機器人連線」）
- **病房助理工作站月班表匯入**：先選「公司／部門」並輸入該單位的管理者密碼（與月班表系統相同），通過後才讀 Firestore 該單位的 `schedules/[單位ID__]{assistant|nurse}-YYYY-MM`，代號規則同月班表系統（D 白班、E 小夜、N 大夜、OFF/國/休 休假、例 例假）
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
