#!/bin/sh
# Adds to the .deb what jpackage cannot: the homepage, AppStream metadata, a themed icon and the window class.
set -eu
deb=$1
here=$(dirname "$0")
work=$(mktemp -d)
trap 'rm -rf "$work"' EXIT
dpkg-deb -R "$deb" "$work"
grep -q '^Homepage:' "$work/DEBIAN/control" ||
    sed -i '/^Description:/i Homepage: https://github.com/organiza-project/organiza' "$work/DEBIAN/control"
# GNOME then puts the window under the launcher's name and icon.
desktop="$work/opt/organiza/lib/organiza-Organiza.desktop"
grep -q '^StartupWMClass=' "$desktop" || echo 'StartupWMClass=organiza-Organiza' >> "$desktop"
# A themed icon name, not a file path: GNOME keeps the picture it first loaded for a path, even after an upgrade.
install -Dm644 "$here/organiza.png" "$work/usr/share/icons/hicolor/512x512/apps/organiza.png"
install -Dm644 "$here/../../../docs/brand/icon.svg" "$work/usr/share/icons/hicolor/scalable/apps/organiza.svg"
sed -i 's|^Icon=.*|Icon=organiza|' "$desktop"
install -Dm644 "$here/org.organiza.Organiza.metainfo.xml" "$work/usr/share/metainfo/org.organiza.Organiza.metainfo.xml"
dpkg-deb --root-owner-group --build "$work" "$deb" > /dev/null
