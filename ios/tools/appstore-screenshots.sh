#!/bin/bash
# App Store 截圖：iPhone 6.3 吋（iPhone 17 Pro，1206×2622）與 iPad 13 吋（iPad Pro 13-inch (M5)，2064×2752）。
# 用法：bash ios/tools/appstore-screenshots.sh <示範 data.json>   → ios/build/shots/*.png
# 示範資料全部是虛構姓名；用開發版的啟動參數 -shot 直接打開指定畫面。
set -e
cd "$(dirname "$0")/.."
DEMO="$1"
export PATH=/usr/local/bin:/opt/homebrew/bin:$PATH
xcodegen generate -q
xcodebuild -project XiaoKeShift.xcodeproj -scheme XiaoKeShift -configuration Debug -sdk iphonesimulator \
  -derivedDataPath build/sim CODE_SIGNING_ALLOWED=NO build 2>&1 | grep -E "error:|BUILD (SUCCEEDED|FAILED)"
APP=build/sim/Build/Products/Debug-iphonesimulator/XiaoKeShift.app
OUT=build/shots; mkdir -p "$OUT"
BID=com.achir.xiaokeshift
shoot() {   # $1 裝置名稱 $2 檔名前綴
  D=$(xcrun simctl list devices available | grep -F "$1 (" | head -1 | grep -oE '[0-9A-F-]{36}')
  echo "== $1 $D"
  xcrun simctl boot "$D" 2>/dev/null || true
  xcrun simctl bootstatus "$D" -b >/dev/null
  xcrun simctl ui "$D" appearance light
  xcrun simctl status_bar "$D" override --time 9:41 --batteryState charged --batteryLevel 100 --wifiBars 3 --cellularMode active --cellularBars 4
  xcrun simctl terminate "$D" $BID 2>/dev/null || true
  xcrun simctl install "$D" "$APP"
  C=$(xcrun simctl get_app_container "$D" $BID data)
  mkdir -p "$C/Documents"; cp "$DEMO" "$C/Documents/data.json"
  xcrun simctl spawn "$D" defaults write $BID payMode day
  xcrun simctl spawn "$D" defaults write $BID payDay -int 25
  for s in main day stats note patterns; do
    xcrun simctl terminate "$D" $BID 2>/dev/null || true
    if [ "$s" = main ]; then xcrun simctl launch "$D" $BID >/dev/null; else xcrun simctl launch "$D" $BID -shot $s >/dev/null; fi
    sleep 7
    xcrun simctl io "$D" screenshot "$OUT/$2-$s.png" >/dev/null 2>&1
  done
  xcrun simctl ui "$D" appearance dark
  xcrun simctl terminate "$D" $BID 2>/dev/null || true
  xcrun simctl launch "$D" $BID >/dev/null; sleep 7
  xcrun simctl io "$D" screenshot "$OUT/$2-dark.png" >/dev/null 2>&1
  xcrun simctl ui "$D" appearance light
  xcrun simctl terminate "$D" $BID 2>/dev/null || true
}
# 第二個參數 iphone／ipad 可以只重拍其中一種
[ "${2:-}" != ipad ] && shoot "iPhone 17 Pro" iphone
[ "${2:-}" != iphone ] && shoot "iPad Pro 13-inch (M5)" ipad
for f in "$OUT"/*.png; do echo "$(basename "$f") $(sips -g pixelWidth -g pixelHeight "$f" | awk '/pixel/{printf "%s ",$2}')"; done
