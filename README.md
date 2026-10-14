# 小柯班表助手（XiaoKe Shift）

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
