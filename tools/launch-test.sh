#!/bin/bash
APK=$(ls out/apk/release/*universal*.apk | head -1)
adb install -r "$APK" || exit 1
adb logcat -c
adb shell am start -W -n com.muyassir.tools/com.muyassir.app.MainActivity; sleep 12
for id in pdf_merge vat gpa qr_make notes; do adb shell am start -W -n com.muyassir.tools/com.muyassir.app.MainActivity --es tool $id; sleep 3; done
adb logcat -d -s AndroidRuntime:E > crash.txt
if grep -q FATAL crash.txt; then echo "::error::Crash on API $(adb shell getprop ro.build.version.sdk)"; sed -n '1,60p' crash.txt; exit 1; fi
echo "OK on API $(adb shell getprop ro.build.version.sdk)"
