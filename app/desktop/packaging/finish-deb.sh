#!/bin/sh
# Adds to the .deb what jpackage cannot: the homepage, AppStream metadata and the launcher's window class.
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
install -Dm644 "$here/org.organiza.Organiza.metainfo.xml" "$work/usr/share/metainfo/org.organiza.Organiza.metainfo.xml"
dpkg-deb --root-owner-group --build "$work" "$deb" > /dev/null
