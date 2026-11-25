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
  grep -qE "الأقسام|ابحث بالاسم أو الوظيفة|نرتب لك يومك|مهامك اليوم|وش تبي تسوي اليوم|اختصاراتك" "shots/$1.xml" || { echo "::error::Home screen content missing ($1)"; FAIL=1; }
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
echo "== tabs, dark mode and tablet layout"
shot() { adb shell am force-stop $PKG; adb shell am start -W -n $PKG/.MainActivity "$@" >/dev/null; sleep 4; }
for t in search assistant activity settings; do shot --es tab $t; adb exec-out screencap -p > "shots/tab-$t.png"; done
shot --es theme dark; adb exec-out screencap -p > shots/dark-home.png
adb shell input swipe 500 1700 500 500 400; sleep 1; adb exec-out screencap -p > shots/dark-home-scrolled.png
shot --es cat files; adb exec-out screencap -p > shots/dark-cat-files.png
shot --es tool vat; adb exec-out screencap -p > shots/dark-vat.png
shot --es tool pdf_merge; adb exec-out screencap -p > shots/dark-pdf_merge.png
shot --es tab settings; adb exec-out screencap -p > shots/dark-settings.png
shot --es theme light
echo "== Plus: paywall, limits, gates (debug test states)"
shot --es plus free --es paywall Menu; adb exec-out screencap -p > shots/plus-paywall.png
adb shell input swipe 500 1700 500 500 400; sleep 1; adb exec-out screencap -p > shots/plus-paywall-scrolled.png
shot --es plus free-used --es paywall Daily; adb exec-out screencap -p > shots/plus-paywall-daily.png
shot --es plus off --es paywall Menu; adb exec-out screencap -p > shots/plus-paywall-soon.png
shot --es plus free --es tool pdf_compress; adb exec-out screencap -p > shots/plus-free-counter.png
shot --es plus free --es tool invoice; adb shell input swipe 500 1800 500 300 300; sleep 1; adb shell input swipe 500 1800 500 300 300; sleep 1; adb exec-out screencap -p > shots/plus-templates.png
shot --es plus free --es tab settings; adb exec-out screencap -p > shots/plus-settings.png
shot --es plus plus --es tab settings; adb exec-out screencap -p > shots/plus-settings-subscribed.png
shot --es plus free --es paywall Menu --es theme dark; adb exec-out screencap -p > shots/plus-paywall-dark.png
shot --es theme light --es plus off
adb shell wm size 1600x2560; adb shell wm density 320; sleep 2
shot; adb exec-out screencap -p > shots/tablet-home.png
shot --es cat study; adb exec-out screencap -p > shots/tablet-cat-study.png
shot --es tool pdf_compress; adb exec-out screencap -p > shots/tablet-pdf_compress.png
shot --es tab search; adb exec-out screencap -p > shots/tablet-search.png
adb shell wm size reset; adb shell wm density reset; sleep 2
adb shell input swipe 500 1600 500 500 400; sleep 1; adb exec-out screencap -p > shots/99-home-scrolled.png
adb logcat -d -s AndroidRuntime:E > shots/crashes.txt
adb logcat -d -s Muyassir:E > shots/tool-errors.txt; head -60 shots/tool-errors.txt
if grep -q FATAL shots/crashes.txt; then echo "::error::A tool screen crashed"; sed -n '1,80p' shots/crashes.txt; FAIL=1; fi
echo "screens: $n" | tee shots/summary.txt
exit $FAIL
