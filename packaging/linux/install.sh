#!/usr/bin/env bash
# Installs the Organiza daemon from a built distribution. Run as root:
#   sudo packaging/linux/install.sh platform/linux/build/install/organiza
set -euo pipefail
dist="${1:?usage: install.sh <path to the built distribution>}"
[[ $EUID -eq 0 ]] || { echo "Run this as root (sudo)." >&2; exit 1; }
command -v java >/dev/null || { echo "Java 17 or later is required (for example: apt install openjdk-17-jre-headless)." >&2; exit 1; }

groupadd -f organiza
if [[ -n "${SUDO_USER:-}" && "$SUDO_USER" != root ]]; then
  usermod -aG organiza "$SUDO_USER"
  echo "Added $SUDO_USER to the organiza group. Log in again for it to take effect."
fi

# Replaces only Organiza's own install directory.
rm -rf /opt/organiza
mkdir -p /opt/organiza
cp -r "$dist"/. /opt/organiza/
ln -sf /opt/organiza/bin/organiza /usr/local/bin/organiza

# The database key is made once and sealed. It never exists unencrypted on disk.
install -d -m 0700 /etc/organiza
if [[ ! -f /etc/organiza/db-key.cred ]]; then
  head -c 32 /dev/urandom | od -An -vtx1 | tr -d ' \n' |
    systemd-creds encrypt --name=organiza-db-key - /etc/organiza/db-key.cred
  chmod 0600 /etc/organiza/db-key.cred
fi

install -m 0644 "$(dirname "$0")/organiza.service" /etc/systemd/system/organiza.service
systemctl daemon-reload
systemctl enable --now organiza
echo "Organiza is running. Try: organiza status"
