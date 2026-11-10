#!/bin/bash
# 封存 App Store 版（Release、自動簽章），完成後在 Xcode Organizer 打開，由使用者按「Distribute App → App Store Connect」上傳。
# （指令列 exportArchive 上傳曾遇到 session expired，所以上傳改用 Organizer；見 E:\Apple Developer app\07）
# 要在 Mac 的登入畫面裡（終端機 App）執行，鑰匙圈才解得開：
#   osascript ios/tools/run-in-terminal.applescript "bash ~/Developer/xiaoke-shift/ios/tools/build-appstore-archive.sh"
# 完成留下 ios/build/appstore.done（ok 或 fail），進度 ios/build/appstore.log
cd "$(dirname "$0")/.."
mkdir -p build
rm -f build/appstore.done
exec > >(tee build/appstore.log) 2>&1
set -o pipefail
export PATH=/usr/local/bin:/opt/homebrew/bin:$PATH
fail() { echo "fail" > build/appstore.done; exit 1; }
xcodegen generate || fail
rm -rf build/XiaoKeShift-AppStore.xcarchive
xcodebuild -project XiaoKeShift.xcodeproj -scheme XiaoKeShift -configuration Release \
  -destination 'generic/platform=iOS' -archivePath build/XiaoKeShift-AppStore.xcarchive \
  -allowProvisioningUpdates archive | grep -E 'error|warning: .*sign|ARCHIVE' || fail
# 在 Xcode Organizer 打開這個封存檔
open build/XiaoKeShift-AppStore.xcarchive
echo "ok" > build/appstore.done
