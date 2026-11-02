#!/usr/bin/env bash
# Removes the Organiza daemon and its /etc/hosts block. Data stays unless --purge is given.
set -euo pipefail
[[ $EUID -eq 0 ]] || { echo "Run this as root (sudo)." >&2; exit 1; }
systemctl disable --now organiza 2>/dev/null || true
rm -f /etc/systemd/system/organiza.service /usr/local/bin/organiza
systemctl daemon-reload
# Removes only the marked block, leaving every other line of /etc/hosts as it was.
sed -i '/^# BEGIN organiza block/,/^# END organiza block/d' /etc/hosts
rm -rf /opt/organiza
if [[ "${1:-}" == "--purge" ]]; then
  rm -rf /var/lib/organiza /etc/organiza
  echo "Removed Organiza and its data."
else
  echo "Removed Organiza. Your data is still in /var/lib/organiza and its key in /etc/organiza."
fi
