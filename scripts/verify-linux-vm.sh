#!/usr/bin/env bash
# End-to-end checks of the Linux daemon inside a throwaway Ubuntu 24.04 VM (ADR-0013).
# The daemon runs as root and edits DNS and ends processes, so it is never tested on this machine.
# Needs qemu with KVM, xorriso and ssh. The cloud image (about 620 MB) is cached in
# ~/.cache/organiza-vm. Usage: scripts/verify-linux-vm.sh   (after ./gradlew :platform:linux:installDist)
set -u
CACHE="${XDG_CACHE_HOME:-$HOME/.cache}/organiza-vm"
IMAGE_URL=https://cloud-images.ubuntu.com/noble/current/noble-server-cloudimg-amd64.img
PORT=2299
DIST=platform/linux/build/install/organiza
WORK=$(mktemp -d)
passed=0
failed=0

check() { if [[ "$3" == *"$2"* ]]; then echo "PASS  $1"; passed=$((passed + 1)); else echo "FAIL  $1: expected '$2' in '$3'"; failed=$((failed + 1)); fi; }
check_not() { if [[ "$3" != *"$2"* ]]; then echo "PASS  $1"; passed=$((passed + 1)); else echo "FAIL  $1: did not expect '$2' in '$3'"; failed=$((failed + 1)); fi; }
check_pid() { if [[ "$2" =~ ^[0-9]+$ ]]; then echo "PASS  $1"; passed=$((passed + 1)); else echo "FAIL  $1: expected a process ID, got '$2'"; failed=$((failed + 1)); fi; }
check_eq() { if [[ "$3" == "$2" ]]; then echo "PASS  $1"; passed=$((passed + 1)); else echo "FAIL  $1: expected exactly '$2', got '$3'"; failed=$((failed + 1)); fi; }

mkdir -p "$CACHE"
if [[ ! -f "$CACHE/base.img" ]]; then
  curl -sSfL -o "$CACHE/base.img.part" "$IMAGE_URL" && mv "$CACHE/base.img.part" "$CACHE/base.img"
fi
[[ -f "$CACHE/id_ed25519" ]] || ssh-keygen -q -t ed25519 -N '' -f "$CACHE/id_ed25519"
qemu-img create -q -f qcow2 -b "$CACHE/base.img" -F qcow2 "$WORK/disk.qcow2" 12G

mkdir -p "$WORK/seed"
printf 'instance-id: organiza-test\nlocal-hostname: organiza-test\n' > "$WORK/seed/meta-data"
cat > "$WORK/seed/user-data" <<USER
#cloud-config
users:
  - name: tester
    shell: /bin/bash
    sudo: ALL=(ALL) NOPASSWD:ALL
    ssh_authorized_keys: ["$(cat "$CACHE/id_ed25519.pub")"]
  - name: other
    shell: /bin/bash
packages: [openjdk-17-jre-headless]
USER
xorriso -as mkisofs -quiet -V cidata -J -r -o "$WORK/seed.iso" "$WORK/seed"

qemu-system-x86_64 -enable-kvm -m 2048 -smp 2 -display none -daemonize -pidfile "$WORK/qemu.pid" \
  -drive file="$WORK/disk.qcow2",if=virtio -drive file="$WORK/seed.iso",media=cdrom \
  -netdev user,id=net,hostfwd=tcp:127.0.0.1:$PORT-:22 -device virtio-net-pci,netdev=net
# Stops this run's VM and deletes only this run's temporary folder.
cleanup() { kill "$(cat "$WORK/qemu.pid" 2>/dev/null)" 2>/dev/null; rm -rf "$WORK"; }
trap cleanup EXIT

OPTS=(-q -i "$CACHE/id_ed25519" -o StrictHostKeyChecking=no -o UserKnownHostsFile=/dev/null -o ConnectTimeout=5)
vm() { ssh "${OPTS[@]}" -p $PORT tester@127.0.0.1 "$@" 2>&1; }
put() { scp "${OPTS[@]}" -P $PORT "$@" tester@127.0.0.1: ; }
for _ in $(seq 1 90); do vm true >/dev/null && break; sleep 5; done
vm "cloud-init status --wait" >/dev/null
echo "VM ready: $(vm 'lsb_release -ds; java -version 2>&1 | head -1' | tr '\n' ' ')"

tar -C "$(dirname "$DIST")" -czf "$WORK/dist.tgz" organiza
tar -czf "$WORK/packaging.tgz" packaging/linux
put "$WORK/dist.tgz" "$WORK/packaging.tgz"
vm "tar xzf dist.tgz && tar xzf packaging.tgz && cp /etc/hosts hosts.before"
as_tester() { vm "sg organiza -c '$*'"; }
pid_of() { vm "pgrep -u $1 -x $2 | head -1"; }

echo "--- install"
check "the installer finishes" "Organiza is running" "$(vm "sudo packaging/linux/install.sh organiza")"
sleep 6
check "the service is active" "active" "$(vm 'systemctl is-active organiza')"
check "a member of the organiza group can ask for status" "No session is running" "$(as_tester organiza status)"
check "anyone else is refused" "Permission denied" "$(vm "sudo -u other /usr/local/bin/organiza status")"

echo "--- encrypted storage"
check_not "the database has no plain SQLite header" "SQLite format 3" "$(vm 'sudo head -c 15 /var/lib/organiza/organiza.db')"
check_eq "the key is stored sealed, never as plain hex" "0" "$(vm "sudo grep -cE '^[0-9a-f]{64}$' /etc/organiza/db-key.cred")"

echo "--- process blocking"
vm "nohup sleep 900 >/dev/null 2>&1 &"
vm "sudo nohup sleep 901 >/dev/null 2>&1 &"
sleep 1
check_pid "control: the user's program is running before the session" "$(pid_of tester sleep)"
check "a session starts" "Session started" "$(as_tester organiza start --minutes 10 --block sleep --site reddit.com)"
sleep 6
check_eq "the user's blocked program was ended" "" "$(pid_of tester sleep)"
check_pid "root's process of the same name was left alone" "$(pid_of root sleep)"
vm "nohup sleep 902 >/dev/null 2>&1 &"
sleep 4
check_eq "a blocked program started later is ended too" "" "$(pid_of tester sleep)"
as_tester organiza stop >/dev/null
check "a shell can be listed" "Session started" "$(as_tester organiza start --minutes 5 --block bash)"
sleep 4
check "the ssh session survives it" "alive" "$(vm 'echo alive')"
as_tester organiza stop >/dev/null
as_tester organiza start --minutes 10 --site reddit.com >/dev/null
sleep 6

echo "--- website blocking"
check "a blocked domain resolves to nothing" "0.0.0.0" "$(vm 'getent ahostsv4 reddit.com | head -1')"
check "its www form is blocked too" "0.0.0.0" "$(vm 'getent ahostsv4 www.reddit.com | head -1')"
check "control: other names still resolve" "localhost" "$(vm 'getent hosts localhost')"
check_eq "the original hosts lines are untouched" "same" "$(vm 'head -n "$(wc -l < hosts.before)" /etc/hosts | diff -q - hosts.before >/dev/null && echo same')"

echo "--- stopping"
check "a normal session can be stopped" "Session stopped" "$(as_tester organiza stop)"
sleep 6
check_eq "the hosts file is restored exactly" "same" "$(vm 'diff -q /etc/hosts hosts.before >/dev/null && echo same')"

echo "--- strict sessions"
check "a strict session starts" "Session started" "$(as_tester organiza start --minutes 10 --site x.com --strict)"
check "it cannot be stopped from the CLI" "cannot be stopped early" "$(as_tester organiza stop)"
daemon=$(vm "systemctl show -p MainPID --value organiza")
vm "sudo kill -9 $daemon"
sleep 8
check "a killed daemon is restarted by systemd" "active" "$(vm 'systemctl is-active organiza')"
check_not "it came back as a new process" "x$daemon" "x$(vm "systemctl show -p MainPID --value organiza")"
vm "sudo systemctl stop organiza"
sleep 40
vm "sudo systemctl start organiza"
sleep 8
check "a stop during a strict session is recorded on return" "Tamper events: 1" "$(as_tester organiza status)"
check "blocking is still in force after the restart" "0.0.0.0" "$(vm 'getent ahostsv4 x.com | head -1')"

echo "--- calendar rules"
check "a tagged rule is saved" "Saved the rule for #deepwork" "$(as_tester organiza rule --tag deepwork --site news.example)"
now=$(vm 'date -u +%s')
stamp() { date -u -d "@$1" +%Y%m%dT%H%M%SZ; }
printf 'BEGIN:VCALENDAR\r\nBEGIN:VEVENT\r\nUID:vm-1\r\nSUMMARY:Write #deepwork\r\nDTSTART:%s\r\nDTEND:%s\r\nEND:VEVENT\r\nEND:VCALENDAR\r\n' \
  "$(stamp $((now - 60)))" "$(stamp $((now + 1800)))" > "$WORK/cal.ics"
put "$WORK/cal.ics"
check "the calendar import adds the event" "1 added" "$(as_tester organiza import cal.ics)"
sleep 7
check "the tagged event blocks its site" "0.0.0.0" "$(vm 'getent ahostsv4 news.example | head -1')"

echo "--- uninstall"
check "uninstall keeps data by default" "still in /var/lib/organiza" "$(vm 'sudo packaging/linux/uninstall.sh')"
check_eq "uninstall removes the hosts block" "0" "$(vm 'grep -c "organiza block" /etc/hosts')"

echo
echo "passed=$passed failed=$failed"
[[ $failed -eq 0 ]]
