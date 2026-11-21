#!/bin/bash
# 1) instrumented engine tests, 2) open every tool screen, photograph it and fail on any crash.
set -u
PKG=com.muyassir.app
mkdir -p shots
adb install -r -g out/apk/debug/*universal*debug.apk || exit 1
adb install -r -g out/apk/androidTest/debug/*.apk || exit 1
adb logcat -c

echo "== instrumented engine tests"
adb shell am instrument -w -r $PKG.test/androidx.test.runner.AndroidJUnitRunner | tee shots/instrumented.txt
grep -q "FAILURES!!!\|INSTRUMENTATION_FAILED\|Process crashed" shots/instrumented.txt && FAIL=1 || FAIL=0
grep -q "^OK (" shots/instrumented.txt || FAIL=1

echo "== every tool screen"
adb shell settings put system font_scale 1.0
# Emulator system apps (e.g. the launcher) can raise "isn't responding" dialogs; keep them out of the screenshots.
adb shell settings put global hide_error_dialogs 1
home() { # name: cold start on the home screen, fail if its content is missing
  adb shell am broadcast -a android.intent.action.CLOSE_SYSTEM_DIALOGS >/dev/null 2>&1
  adb shell am force-stop $PKG; adb shell am start -W -n $PKG/.MainActivity >/dev/null; sleep 8
  adb shell am broadcast -a android.intent.action.CLOSE_SYSTEM_DIALOGS >/dev/null 2>&1; sleep 1
  adb exec-out screencap -p > "shots/$1.png"
  adb shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1; adb exec-out cat /sdcard/ui.xml > "shots/$1.xml"
  grep -q "التصنيفات" "shots/$1.xml" || { echo "::error::Home screen content missing ($1)"; FAIL=1; }
}
home 00-home
echo "== every category screen"
for c in $(grep -o 'Category("[a-z]*"' app/src/main/java/com/muyassir/app/core/Tools.kt | cut -d'"' -f2); do
  adb shell am force-stop $PKG; adb shell am start -W -n $PKG/.MainActivity --es cat "$c" >/dev/null; sleep 3
  adb exec-out screencap -p > "shots/cat-$c.png"
done
IDS=$(grep -o 'Tool("[a-z_]*"' app/src/main/java/com/muyassir/app/core/Tools.kt | cut -d'"' -f2)
n=0
for id in $IDS; do
  n=$((n+1))
  adb shell am force-stop $PKG
  adb shell am start -W -n $PKG/.MainActivity --es tool "$id" >/dev/null
  sleep 3
  adb exec-out screencap -p > "shots/$(printf %02d $n)-$id.png"
done
home 99-home-after
adb shell input swipe 500 1600 500 500 400; sleep 1; adb exec-out screencap -p > shots/99-home-scrolled.png
adb logcat -d -s AndroidRuntime:E > shots/crashes.txt
adb logcat -d -s Muyassir:E > shots/tool-errors.txt; head -60 shots/tool-errors.txt
if grep -q FATAL shots/crashes.txt; then echo "::error::A tool screen crashed"; sed -n '1,80p' shots/crashes.txt; FAIL=1; fi
echo "screens: $n" | tee shots/summary.txt
exit $FAIL
