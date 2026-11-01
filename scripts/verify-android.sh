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
# Quoted so the whole line reaches the app's shell. Unquoted, adb's remote shell splits it
# and sh -c sees only the first word, which makes checks pass without testing anything.
in_app() { "$ADB" shell "run-as $APP sh -c '$1'" 2>&1 | tr -d '\r'; }
app_pid() { "$ADB" shell pidof "$APP" 2>&1 | tr -d '\r'; }
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

# Start from a real clean install. An active admin blocks uninstall, so install this build
# over any older one first and have it remove its own admin.
"$ADB" install -r -t "$APK" >/dev/null
shell am broadcast -n "$RECEIVER" --es command removeadmin >/dev/null
sleep 2
"$ADB" uninstall "$APP" >/dev/null 2>&1
if shell pm list packages | grep -q "package:$APP\$"; then
  echo "ABORT: could not uninstall $APP, so this run would inherit old data"
  exit 2
fi
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

echo "--- encrypted storage"
db="databases/organiza.db"
session stop
shell am broadcast -n "$RECEIVER" --es command rule --es id marker --es name ZZPLAINTEXTMARKER --es tag marker >/dev/null; sleep 2
check "the rule was saved" "Rule saved" "$(debug_state)"
header=$(shell run-as "$APP" head -c 15 "$db")
check_not "database has no plain SQLite header" "SQLite format 3" "$header"
check "database file exists" "organiza.db" "$(shell run-as "$APP" ls databases)"
in_app "echo ZZPLAINTEXTMARKER > files_control" >/dev/null
check "control: the grep can find the marker" "1" "$(in_app "cat files_control | grep -c ZZPLAINTEXTMARKER")"
in_app "rm files_control" >/dev/null
db_bytes=$(in_app "cat databases/organiza.db* | wc -c")
check_not "control: the database files are not empty" "0" "${db_bytes// /}x"
check "plaintext does not reach the database" "0" "$(in_app "cat databases/organiza.db* | grep -c ZZPLAINTEXTMARKER")"
check "the wrapped key file exists" "database.key" "$(shell run-as "$APP" ls no_backup)"

echo "--- sessions survive a process kill"
kill_now() {
  local pid; pid=$(app_pid)
  in_app "kill -9 $pid" >/dev/null
  sleep 1
  check_not "control: process $pid was really killed" "$pid" "$(app_pid)x"
}
kill_app() { kill_now; sleep 8; }
fresh_settings
session start --es packages com.android.settings
kill_app
open_settings
check_not "blocked app is still sent home after a kill" "com.android.settings" "$(top_app)"
check "the session is still active" "active=true" "$(debug_state)"
session stop

echo "--- calendar scheduling"
ics() { # uid tag startSeconds lengthSeconds
  # The device clock decides when sessions run, and it can differ from this machine's.
  local now start end
  now=$(shell date +%s)
  start=$(date -u -d "@$(( now + $3 ))" +%Y%m%dT%H%M%SZ)
  end=$(date -u -d "@$(( now + $3 + $4 ))" +%Y%m%dT%H%M%SZ)
  printf 'BEGIN:VCALENDAR\r\nBEGIN:VEVENT\r\nUID:%s\r\nSUMMARY:work #%s\r\nDTSTART:%s\r\nDTEND:%s\r\nEND:VEVENT\r\nEND:VCALENDAR\r\n' "$1" "$2" "$start" "$end"
}
import_ics() { shell am broadcast -n "$RECEIVER" --es command ics --es base64 "$(printf '%s' "$1" | base64 -w0)" >/dev/null; sleep 2; }
shell am broadcast -n "$RECEIVER" --es command rule --es id calapp --es name calapp --es tag calapp --es packages com.android.settings >/dev/null; sleep 1
shell am broadcast -n "$RECEIVER" --es command rule --es id caldns --es name caldns --es tag caldns --es domains reddit.com >/dev/null; sleep 1
fresh_settings
import_ics "$(ics running calapp -60 600)"
check "import reports the event" "1 added" "$(debug_state)"
open_settings
check_not "a tagged running event blocks its apps" "com.android.settings" "$(top_app)"
import_ics "BEGIN:VCALENDAR"
open_settings
check_not "deleting the event does not end the session" "com.android.settings" "$(top_app)"
session stop
import_ics "$(ics timed caldns 12 25)"
check "the session has not started yet" "active=false" "$(debug_state)"
check_not "not blocked before the start time" "unknown host" "$(shell ping -c 1 -W 2 old.reddit.com)"
sleep 16
check "the session starts on time with no prompting" "active=true" "$(debug_state)"
check "the vpn starts from the background" "VPN" "$(debug_state)"
sleep 2
check "dns is blocked by the scheduled session" "unknown host" "$(shell ping -c 1 -W 3 old.reddit.com)"
sleep 22
state=$(debug_state)
check "the session ends on time" "active=false" "$state"
check_not "the vpn stops with it" "VPN" "$state"
check_not "dns recovers when it ends" "unknown host" "$(shell ping -c 1 -W 2 old.reddit.com)"

echo "--- strict sessions cannot be stopped"
session start --es packages com.example.none --ez strict true
shell am broadcast -n "$RECEIVER" --es command userstop >/dev/null; sleep 2
state=$(debug_state)
check "stopping is refused" "cannot be stopped early" "$state"
check "the session is still active" "active=true" "$state"
session stop

echo "--- byok ai pipeline"
# Everything goes to a local stand-in server on this machine, never to the real Groq API.
FAKE_DIR=$(mktemp -d)
python3 scripts/fake-groq.py 18080 "$FAKE_DIR/requests.log" "$FAKE_DIR/mode" &
fake_pid=$!
trap 'kill "$fake_pid" 2>/dev/null' EXIT
sleep 1
broadcast() { shell am broadcast -n "$RECEIVER" --es command "$@" >/dev/null; }
b64() { printf '%s' "$1" | base64 -w0; }
sent() { cat "$FAKE_DIR/requests.log" 2>/dev/null; }
test_key="gsk_verify_$(date +%s)_not_a_real_key"
leaky='Prep for Sarah Connor, jane@clinic.example, 415-555-2671, https://x.example/private'
broadcast aiendpoint --es url http://10.0.2.2:18080; sleep 1
broadcast clearkey; sleep 1
broadcast aireview; sleep 3
check "no key means no call" "NO_KEY" "$(debug_state)"
check "control: nothing was sent without a key" "0" "$(sent | wc -l)"
broadcast savekey --es key "$test_key"; sleep 2
check "the key is saved" "API key saved" "$(debug_state)"
check "the key file exists" "groq.key" "$(in_app "ls no_backup")"
check "the key is not stored in the clear" "0" "$(in_app "cat no_backup/* databases/* | grep -c gsk_verify")"
check_not "the key is not logged" "gsk_verify" "$("$ADB" logcat -d | tr -d '\r' | grep -F "$test_key" | grep -v 'savekey')"
session start --es packages com.instagram.android --es domains secretsite.example
echo grant > "$FAKE_DIR/mode"
broadcast aireview; sleep 3
check "weekly review reads the model reply" "Weekly review: " "$(debug_state)"
broadcast aischedule --es base64 "$(b64 "$leaky")"; sleep 3
check "schedule suggestions are shown, not applied" "Suggested (not applied)" "$(debug_state)"
check "the key is sent as a bearer token" "Bearer $test_key" "$(sent)"
check_not "the model is not retired" "llama" "$(sent)"
for secret in Sarah Connor jane clinic 415-555 x.example instagram secretsite "$test_key\"model"; do
  check_not "request bodies do not contain $secret" "$secret" "$(sent | grep '^BODY')"
done
check "control: the request log holds bodies" "focusMinutes" "$(sent | grep '^BODY')"
broadcast aigate --es base64 "$(b64 "$leaky")"; sleep 3
state=$(debug_state)
check "a grant is reported" "granted=true" "$state"
check "a grant does not end the session" "active=true" "$state"
echo garbage > "$FAKE_DIR/mode"
broadcast aigate --es base64 "$(b64 'please')"; sleep 3
check "a malformed reply is a denial" "granted=false" "$(debug_state)"
echo error > "$FAKE_DIR/mode"
broadcast aigate --es base64 "$(b64 'please')"; sleep 3
check "a server error is a denial" "failure=SERVER" "$(debug_state)"
kill "$fake_pid" 2>/dev/null; sleep 1
broadcast aigate --es base64 "$(b64 'please')"; sleep 4
state=$(debug_state)
check "being offline is a denial" "granted=false failure" "${state/reason=null /}"
check "offline is reported" "failure=OFFLINE" "$state"
session stop
broadcast aiendpoint --es url http://10.0.2.2:18080
kill_now; sleep 4
broadcast aiendpoint --es url http://10.0.2.2:1; sleep 1
broadcast aireview; sleep 4
check "the key survives a process kill" "failure" "$(debug_state | tr 'A-Z' 'a-z')"
check_not "control: it is not reported as missing" "NO_KEY" "$(debug_state)"
broadcast clearkey; sleep 1
check_not "removing the key deletes the file" "groq.key" "$(in_app "ls no_backup")"

echo "--- uninstall protection"
check "admin blocks uninstall" "DELETE_FAILED" "$(shell pm uninstall "$APP")"

echo "--- tamper detection"
session start --es packages com.example.none
shell settings put secure enabled_accessibility_services "''" >/dev/null
sleep 3
check "losing accessibility is recorded" "tamper=ACCESSIBILITY" "$(debug_state)"
session stop

echo "--- tamper during a process kill"
enable_accessibility() {
  shell settings put secure enabled_accessibility_services "$SERVICE" >/dev/null
  shell settings put secure accessibility_enabled 1 >/dev/null
  sleep 5
}
enable_accessibility
session start --es packages com.example.none
sleep 6
shell settings put secure enabled_accessibility_services "''" >/dev/null
kill_now
shell am start -W -n "$APP/org.organiza.android.SetupActivity" >/dev/null
sleep 8
check "loss during a kill is recorded after restart" "ACCESSIBILITY,OVERLAY,ACCESSIBILITY" "$(debug_state)"
session stop
shell input keyevent KEYCODE_HOME >/dev/null

echo "--- a damaged key never deletes data"
enable_accessibility
before=$(in_app "wc -c < databases/organiza.db")
check_not "control: the database has content" "0" "${before// /}x"
in_app "echo damaged > no_backup/database.key" >/dev/null
kill_now
shell am start -W -n "$APP/org.organiza.android.SetupActivity" >/dev/null
sleep 5
check "the failure is reported" "storageFailure=KEY_UNREADABLE" "$(debug_state)"
check "the database file is kept" "$before" "$(in_app "wc -c < databases/organiza.db")"
shell input keyevent KEYCODE_HOME >/dev/null

shell am broadcast -n "$RECEIVER" --es command removeadmin >/dev/null
echo
echo "passed=$passed failed=$failed"
[[ "$failed" -eq 0 ]]
