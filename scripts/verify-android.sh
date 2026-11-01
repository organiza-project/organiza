#!/usr/bin/env bash
# End-to-end enforcement checks against a booted emulator or a test device.
# Usage: scripts/verify-android.sh   (expects exactly one adb device)
# This grants permissions and changes settings, so do not point it at a daily phone.
set -u

ADB="${ANDROID_HOME:-$HOME/Android/Sdk}/platform-tools/adb"
APP=org.organiza.app
RECEIVER="$APP/org.organiza.android.DebugSessionReceiver"
SERVICE="$APP/org.organiza.android.OrganizaAccessibilityService"
ADMIN="$APP/org.organiza.android.OrganizaDeviceAdminReceiver"
APK=app/android/build/outputs/apk/debug/android-debug.apk
passed=0
failed=0

check() { # name expected actual
  if [[ "$3" == *"$2"* ]]; then
    echo "PASS  $1"; passed=$((passed + 1))
  else
    echo "FAIL  $1: expected '$2' in '$3'"; failed=$((failed + 1))
  fi
}

check_not() { # name unexpected actual
  if [[ "$3" != *"$2"* ]]; then
    echo "PASS  $1"; passed=$((passed + 1))
  else
    echo "FAIL  $1: did not expect '$2' in '$3'"; failed=$((failed + 1))
  fi
}

shell() { "$ADB" shell "$@" 2>&1 | tr -d '\r'; }
top_app() { shell dumpsys activity activities | grep -m1 topResumedActivity; }
session() { shell am broadcast -n "$RECEIVER" --es command "$@" >/dev/null; sleep 2; }
debug_state() {
  "$ADB" logcat -c
  shell am broadcast -n "$RECEIVER" >/dev/null
  sleep 1
  "$ADB" logcat -d -s OrganizaDebug:I | tr -d '\r' | tail -1
}
open_settings() { shell am start -W -a android.settings.SETTINGS >/dev/null; sleep 2; }
fresh_settings() { shell am force-stop com.android.settings >/dev/null; }

"$ADB" uninstall "$APP" >/dev/null 2>&1
"$ADB" install -r -t "$APK" >/dev/null
shell settings put secure enabled_accessibility_services "$SERVICE" >/dev/null
shell settings put secure accessibility_enabled 1 >/dev/null
shell appops set "$APP" ACTIVATE_VPN allow >/dev/null
shell dpm set-active-admin "$ADMIN" >/dev/null
shell am start -W -n "$APP/org.organiza.android.SetupActivity" >/dev/null
# A fresh install can race the accessibility manager, which then drops the binding.
# Wait until the service is really bound and stays bound.
bound() { shell dumpsys accessibility | grep -q 'Bound services:{Service\[label=Organiza'; }
for _ in 1 2 3 4 5 6; do
  sleep 4
  bound && { sleep 3; bound && break; }
  shell settings put secure enabled_accessibility_services null >/dev/null
  shell settings put secure enabled_accessibility_services "$SERVICE" >/dev/null
  shell settings put secure accessibility_enabled 1 >/dev/null
done
shell input keyevent KEYCODE_HOME >/dev/null
sleep 1

echo "--- capabilities"
state=$(debug_state)
check "accessibility bound" "ACCESSIBILITY" "$state"
check "device admin active" "DEVICE_ADMIN" "$state"

echo "--- idle"
open_settings
check "idle session blocks nothing" "settings" "$(top_app)"
shell input keyevent KEYCODE_HOME >/dev/null; sleep 1

echo "--- app blocking"
session start --es packages com.android.settings
open_settings
top=$(top_app)
check_not "blocked app is sent home" "com.android.settings" "$top"
windows=$(shell dumpsys window windows | grep -c 'package=org.organiza.app appop=CREATE_ACCESSIBILITY_OVERLAY')
check "overlay is gone once home" "0" "$windows"
session stop
open_settings
check "stopping the session unblocks" "settings" "$(top_app)"
shell input keyevent KEYCODE_HOME >/dev/null; sleep 1

echo "--- block applies to the app already open"
open_settings
session start --es packages com.android.settings
check_not "app on screen is sent home" "com.android.settings" "$(top_app)"
session stop

echo "--- lockout protection"
launcher=$(shell cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME | tail -1 | cut -d/ -f1)
session start --es packages "$launcher"
shell input keyevent KEYCODE_HOME >/dev/null; sleep 2
windows=$(shell dumpsys window windows | grep -c 'package=org.organiza.app appop=CREATE_ACCESSIBILITY_OVERLAY')
check "blocking the launcher ($launcher) shows no overlay" "0" "$windows"
check "launcher stays usable" "$launcher" "$(top_app)"
session stop

echo "--- dns sinkhole"
session start --es domains reddit.com
state=$(debug_state)
check "vpn is up" "VPN" "$state"
sleep 2
blocked=$(shell ping -c 1 -W 3 old.reddit.com)
check "blocked subdomain does not resolve" "unknown host" "$blocked"
allowed=$(shell ping -c 1 -W 5 example.com)
check_not "allowed domain still resolves" "unknown host" "$allowed"
session stop
state=$(debug_state)
check_not "vpn stops with the session" "VPN" "$state"

echo "--- browser url bar"
# Skip Chrome's first-run screens, which have no URL bar. Chrome promo dialogs also hide
# it, because they are separate windows, so dismiss any by hand if this section fails.
shell 'echo "chrome --disable-fre --no-default-browser-check --no-first-run" > /data/local/tmp/chrome-command-line' >/dev/null
shell am set-debug-app --persistent com.android.chrome >/dev/null
shell am force-stop com.android.chrome >/dev/null
session start --es domains reddit.com
shell am start -W -a android.intent.action.VIEW -d https://example.com/ com.android.chrome >/dev/null; sleep 6
check "allowed site stays open in chrome" "com.android.chrome" "$(top_app)"
shell am start -W -a android.intent.action.VIEW -d https://old.reddit.com/ com.android.chrome >/dev/null; sleep 6
check_not "blocked site in chrome is sent home" "com.android.chrome" "$(top_app)"
session stop
shell am clear-debug-app >/dev/null
shell input keyevent KEYCODE_HOME >/dev/null; sleep 1

echo "--- strict settings guard"
fresh_settings
session start --es packages com.example.none --ez strict true
shell am start -W -a android.settings.APPLICATION_DETAILS_SETTINGS -d "package:$APP" >/dev/null; sleep 3
check_not "Organiza's settings page is blocked" "com.android.settings" "$(top_app)"
fresh_settings
shell am start -W -a android.settings.WIFI_SETTINGS >/dev/null; sleep 3
check "other settings pages stay open" "settings" "$(top_app)"
shell input keyevent KEYCODE_HOME >/dev/null; sleep 1
session stop

echo "--- uninstall protection"
check "admin blocks uninstall" "DELETE_FAILED" "$(shell pm uninstall "$APP")"

echo "--- tamper detection"
session start --es packages com.example.none
shell settings put secure enabled_accessibility_services "''" >/dev/null
sleep 3
check "losing accessibility is recorded" "tamper=ACCESSIBILITY" "$(debug_state)"
session stop

shell dpm remove-active-admin "$ADMIN" >/dev/null
echo
echo "passed=$passed failed=$failed"
[[ "$failed" -eq 0 ]]
