#!/usr/bin/env bash
# Fills a fresh debug install with believable sample data for screenshot reviews (ADR-0012).
# Everything here is sample data: three habits, a calendar day and one challenge with a second
# member. Usage: scripts/seed-screens.sh   (expects one emulator, and the debug APK built)
set -u
ADB="${ANDROID_HOME:-$HOME/Android/Sdk}/platform-tools/adb"
APP=org.organiza.app
RECEIVER="$APP/org.organiza.android.DebugSessionReceiver"
SERVICE="$APP/org.organiza.android.feature.blocker.OrganizaAccessibilityService"
APK=app/android/build/outputs/apk/debug/android-debug.apk
: "${JAVA_HOME:=/usr/lib/jvm/java-17-openjdk-amd64}"; export JAVA_HOME
# adb shell joins arguments with spaces, so each one is quoted for the device's shell.
bc() { "$ADB" shell "am broadcast -n $RECEIVER --es command $(printf '%q ' "$@")" >/dev/null; sleep 0.4; }

"$ADB" install -r -t "$APK" >/dev/null
bc removeadmin; sleep 1
"$ADB" uninstall "$APP" >/dev/null 2>&1
"$ADB" install -r -t "$APK" >/dev/null
"$ADB" shell settings put secure enabled_accessibility_services "$SERVICE"
"$ADB" shell settings put secure accessibility_enabled 1
"$ADB" shell appops set "$APP" ACTIVATE_VPN allow
"$ADB" shell appops set "$APP" GET_USAGE_STATS allow
"$ADB" shell am start -W -n "$APP/org.organiza.android.MainActivity" >/dev/null
sleep 3

# Habits created three weeks ago, with a realistic run of results.
bc clockdays --el days -21
for habit in "Read 20 pages" "Stretch" "Evening run"; do bc addhabit --es name "$habit"; done
bc clockdays --el days 0
for ago in $(seq 1 20); do
  bc habitday --es name "Read 20 pages" --el ago "$ago" --ez done "$([ $((ago % 7)) -eq 3 ] && echo false || echo true)"
  [ $((ago % 2)) -eq 0 ] && bc habitday --es name "Stretch" --el ago "$ago" --ez done true
  [ $((ago % 3)) -eq 1 ] && bc habitday --es name "Evening run" --el ago "$ago" --ez done "$([ "$ago" -eq 1 ] && echo false || echo true)"
done
bc habitday --es name "Read 20 pages" --el ago 0 --ez done true

# Rules with calendar tags, and a day of sessions around now.
bc rule --es id deepwork --es name "Deep work" --es tag deepwork --es packages com.instagram.android,com.zhiliaoapp.musically --es domains reddit.com,x.com
bc rule --es id focus --es name Focus --es tag focus --es packages com.google.android.youtube
now=$("$ADB" shell date +%s | tr -d '\r')
stamp() { date -u -d "@$1" +%Y%m%dT%H%M%SZ; }
event() { printf 'BEGIN:VEVENT\r\nUID:%s\r\nSUMMARY:#%s\r\nDTSTART:%s\r\nDTEND:%s\r\nEND:VEVENT\r\n' "$1" "$2" "$(stamp "$3")" "$(stamp "$4")"; }
ics="BEGIN:VCALENDAR\r\n"
# Command substitution drops the final newline, so each event gets it back explicitly.
ics+="$(event running deepwork $((now - 2400)) $((now + 4800)))\n"
ics+="$(event later focus $((now + 3 * 3600)) $((now + 4 * 3600)))\n"
ics+="$(event tomorrow deepwork $((now + 22 * 3600)) $((now + 24 * 3600)))\n"
ics+="END:VCALENDAR\r\n"
bc ics --es base64 "$(printf "$ics" | base64 -w0)"
sleep 2

# One challenge with a second member, through the reference relay and the peer process.
python3 scripts/arena-relay.py 18081 >/dev/null 2>&1 &
relay_pid=$!
trap 'kill "$relay_pid" 2>/dev/null' EXIT
sleep 1
bc arenacreate --es habit "Read 20 pages" --es title "No Reels November" --es relay http://10.0.2.2:18081 --es name Jack --ei days 30 --ei freezes 2
sleep 3
"$ADB" logcat -c
bc arenainvite
sleep 1
invite=$("$ADB" logcat -d -s OrganizaDebug:I | tr -d '\r' | grep -o 'invite=.*' | tail -1 | cut -d= -f2-)
./gradlew -q :core:arena:arenaPeer "-Ppeer=honest,http://127.0.0.1:18081,$invite" --console=plain >/dev/null 2>&1
bc arenasync
sleep 4
