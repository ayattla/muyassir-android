#!/bin/bash
# 1) instrumented engine tests, 2) open every tool screen, photograph it and fail on any crash.
set -u
PKG=com.muyassir.app
mkdir -p shots
adb install -r -g out/apk/debug/*debug.apk || exit 1
adb install -r -g out/apk/androidTest/debug/*.apk || exit 1
adb logcat -c

echo "== instrumented engine tests"
adb shell am instrument -w -r $PKG.test/androidx.test.runner.AndroidJUnitRunner | tee shots/instrumented.txt
grep -q "FAILURES!!!\|INSTRUMENTATION_FAILED\|Process crashed" shots/instrumented.txt && FAIL=1 || FAIL=0
grep -q "^OK (" shots/instrumented.txt || FAIL=1

echo "== every tool screen"
adb shell settings put system font_scale 1.0
adb shell am start -W -n $PKG/.MainActivity >/dev/null; sleep 6
adb exec-out screencap -p > shots/00-home.png
IDS=$(grep -o 'Tool("[a-z_]*"' app/src/main/java/com/muyassir/app/core/Tools.kt | cut -d'"' -f2)
n=0
for id in $IDS; do
  n=$((n+1))
  adb shell am force-stop $PKG
  adb shell am start -W -n $PKG/.MainActivity --es tool "$id" >/dev/null
  sleep 3
  adb exec-out screencap -p > "shots/$(printf %02d $n)-$id.png"
done
adb shell am start -W -n $PKG/.MainActivity >/dev/null; sleep 2
adb logcat -d -s AndroidRuntime:E > shots/crashes.txt
if grep -q FATAL shots/crashes.txt; then echo "::error::A tool screen crashed"; sed -n '1,80p' shots/crashes.txt; FAIL=1; fi
echo "screens: $n" | tee shots/summary.txt
exit $FAIL
