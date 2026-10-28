#!/bin/bash
# 封存並匯出 Ad Hoc 簽章的 .ipa（iPhone／iPad 都能裝，限開發者帳號登記過的裝置；GitHub Pages 安裝頁用這個）。
# 和 build_ipa.sh（未簽名，給 SideStore/AltStore）不同。要在 Mac 的登入畫面裡（終端機 App）執行：
#   osascript ios/tools/run-in-terminal.applescript "bash ~/Developer/xiaoke-shift/ios/tools/build-adhoc-ipa.sh 1.2.0 3"
# 參數：版本號 組建號。產出 ios/build/xiaoke-shift-ios.ipa；進度 ios/build/ipa.log；完成留下 ios/build/ipa.done（ok 或 fail）
cd "$(dirname "$0")/.."
VER="${1:-1.0.0}"
BUILD="${2:-1}"
mkdir -p build
rm -f build/ipa.done
exec > >(tee build/ipa.log) 2>&1
set -o pipefail
export PATH=/usr/local/bin:/opt/homebrew/bin:$PATH
fail() { echo "fail" > build/ipa.done; exit 1; }
xcodegen generate || fail
rm -rf build/XiaoKeShift.xcarchive build/export
xcodebuild -project XiaoKeShift.xcodeproj -scheme XiaoKeShift -configuration Release \
  -destination 'generic/platform=iOS' -archivePath build/XiaoKeShift.xcarchive \
  MARKETING_VERSION="$VER" CURRENT_PROJECT_VERSION="$BUILD" \
  -allowProvisioningUpdates archive | grep -E 'error|warning: .*sign|ARCHIVE' || fail
xcodebuild -exportArchive -archivePath build/XiaoKeShift.xcarchive -exportPath build/export \
  -exportOptionsPlist tools/ExportOptions.plist -allowProvisioningUpdates || fail
IPA=$(ls build/export/*.ipa | head -1)
[ -f "$IPA" ] || fail
cp "$IPA" build/xiaoke-shift-ios.ipa
# 列出這個 .ipa 可以安裝的裝置（UDID）
unzip -p build/xiaoke-shift-ios.ipa 'Payload/*.app/embedded.mobileprovision' | security cms -D | plutil -extract ProvisionedDevices json -o - - || true
ls -l build/xiaoke-shift-ios.ipa
echo "$VER $BUILD" > build/ipa.version
echo "ok" > build/ipa.done
