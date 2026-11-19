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
# The native top bar menu (IndicatorTray); without them the plain AWT tray is used.
grep -q '^Recommends:' "$work/DEBIAN/control" ||
    sed -i '/^Description:/i Recommends: python3-gi, gir1.2-ayatanaappindicator3-0.1' "$work/DEBIAN/control"
# GNOME then puts the window under the launcher's name and icon.
desktop="$work/opt/organiza/lib/organiza-Organiza.desktop"
grep -q '^StartupWMClass=' "$desktop" || echo 'StartupWMClass=organiza-Organiza' >> "$desktop"
# A themed icon name, not a file path: GNOME keeps the picture it first loaded for a path, even after an upgrade.
install -Dm644 "$here/organiza.png" "$work/usr/share/icons/hicolor/512x512/apps/organiza.png"
install -Dm644 "$here/../../../docs/brand/icon.svg" "$work/usr/share/icons/hicolor/scalable/apps/organiza.svg"
sed -i 's|^Icon=.*|Icon=organiza|' "$desktop"
install -Dm644 "$here/org.organiza.Organiza.metainfo.xml" "$work/usr/share/metainfo/org.organiza.Organiza.metainfo.xml"
# A running Organiza keeps the old code, and every launch hands off to it. Ending it after an upgrade lets
# the autostart service (Restart=on-failure) start the new version within seconds; a removal stops it.
grep -q pkill "$work/DEBIAN/postinst" ||
    sed -i "s|^xdg-desktop-menu install .*|&\n    pkill -TERM -f '^/opt/organiza/bin/Organiza' \|\| true|" "$work/DEBIAN/postinst"
grep -q pkill "$work/DEBIAN/prerm" ||
    sed -i "s|^xdg-desktop-menu uninstall .*|&\n    [ \"\$1\" != remove ] \|\| pkill -TERM -f '^/opt/organiza/bin/Organiza' \|\| true|" "$work/DEBIAN/prerm"
dpkg-deb --root-owner-group --build "$work" "$deb" > /dev/null
