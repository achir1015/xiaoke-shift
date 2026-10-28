#!/bin/bash
# 編出未簽名的 IPA（給 SideStore / AltStore 用朋友自己的 Apple ID 簽名安裝），並產生 SideStore/AltStore 來源檔 source.json。
#   用法：ios/tools/build_ipa.sh <組建號>      → ios/build/XiaoKeShift.ipa、ios/build/source.json
#   GitHub Actions（.github/workflows/ios.yml）每次 push 都會跑，把結果放到 GitHub Releases。
set -euo pipefail
cd "$(dirname "$0")/.."
BUILD="${1:-0}"
# 版本號跟 project.yml 的 MARKETING_VERSION 一致（和安裝頁、Android 同一個版號）；組建號用 GitHub Actions 的 run_number
VER=$(grep -m1 'MARKETING_VERSION:' project.yml | sed -E 's/.*"([^"]+)".*//')
REPO="achir1015/xiaoke-shift"
TAG="latest"
OUT=build
rm -rf "$OUT/dd" "$OUT/Payload" "$OUT/XiaoKeShift.ipa"
mkdir -p "$OUT"

xcodegen generate
xcodebuild -project XiaoKeShift.xcodeproj -scheme XiaoKeShift -configuration Release -destination 'generic/platform=iOS' \
  -derivedDataPath "$OUT/dd" CODE_SIGNING_ALLOWED=NO MARKETING_VERSION="$VER" CURRENT_PROJECT_VERSION="$BUILD" \
  build | grep -E "error|warning: .*\.swift|BUILD" || true
APP="$OUT/dd/Build/Products/Release-iphoneos/XiaoKeShift.app"
[ -d "$APP" ] || { echo "編譯失敗"; exit 1; }

xattr -cr "$APP"     # 專案在 iCloud「文件」裡時會帶附加屬性，不清會簽章失敗
# 臨時簽名並寫入 entitlements（SideStore/AltStore 靠它知道要幫 App 和小工具註冊 App Group）
codesign -f -s - --entitlements Widget/Widget.entitlements "$APP/PlugIns/XiaoKeWidget.appex"
codesign -f -s - --entitlements XiaoKeShift/XiaoKeShift.entitlements "$APP"

mkdir -p "$OUT/Payload"
cp -R "$APP" "$OUT/Payload/"
(cd "$OUT" && zip -qry XiaoKeShift.ipa Payload)
SIZE=$(stat -f%z "$OUT/XiaoKeShift.ipa" 2>/dev/null || stat -c%s "$OUT/XiaoKeShift.ipa")

python3 - "$VER" "$BUILD" "$SIZE" "$REPO" "$TAG" > "$OUT/source.json" <<'EOF'
import json, sys, datetime
ver, build, size, repo, tag = sys.argv[1:]
icon = f"https://raw.githubusercontent.com/{repo}/main/ios/XiaoKeShift/Assets.xcassets/AppIcon.appiconset/icon-1024.png"
print(json.dumps({
    "name": "小柯班表助手",
    "identifier": "com.achir.xiaokeshift.source",
    "sourceURL": f"https://github.com/{repo}/releases/latest/download/source.json",
    "iconURL": icon,
    "website": f"https://github.com/{repo}",
    "tintColor": "#1E88E5",
    "apps": [{
        "name": "小柯班表助手",
        "bundleIdentifier": "com.achir.xiaokeshift",
        "developerName": "achir1015",
        "subtitle": "輪班班表＋農民曆＋小柯",
        "localizedDescription": "輪班班表 App（iPhone／iPad 版）：月曆排班、農民曆、班表樣式、病房助理工作站月班表匯入、備註記錄、統計、行事曆同步、鬧鐘、桌面小工具。資料格式與 Android 版相容。",
        "iconURL": icon,
        "tintColor": "#1E88E5",
        "versions": [{
            "version": ver,
            "buildVersion": build,
            "date": datetime.datetime.now(datetime.timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ"),
            "localizedDescription": f"iPhone／iPad 版 {ver}（組建 {build}）",
            "downloadURL": f"https://github.com/{repo}/releases/latest/download/XiaoKeShift.ipa",
            "size": int(size),
            "minOSVersion": "17.0"
        }],
        "appPermissions": {
            "entitlements": ["com.apple.security.application-groups"],
            "privacy": {
                "NSCalendarsFullAccessUsageDescription": "把班表寫到行事曆（可同步到 Google 行事曆），並在月曆上顯示你的行程。",
                "NSAlarmKitUsageDescription": "依照班別設定的時間叫你起床上班。",
                "NSPhotoLibraryUsageDescription": "在備註記錄插入照片。"
            }
        }
    }],
    "news": []
}, ensure_ascii=False, indent=2))
EOF
echo "完成：${OUT}/XiaoKeShift.ipa（${VER}，${SIZE} bytes）、${OUT}/source.json"
