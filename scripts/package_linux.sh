#!/usr/bin/env bash
# ============================================================================
# Organiza Linux Packaging Script
# Builds release binary and bundles it into an AppDir / tarball package
# ============================================================================
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"
BUILD_DIR="${ROOT_DIR}/build/linux/x64/release/bundle"
DIST_DIR="${ROOT_DIR}/dist/linux"

echo "=== Building Flutter Linux Release ==="
cd "${ROOT_DIR}"
flutter build linux --release

echo "=== Preparing Distribution Directory ==="
rm -rf "${DIST_DIR}"
mkdir -p "${DIST_DIR}/AppDir/usr/bin"
mkdir -p "${DIST_DIR}/AppDir/usr/lib"
mkdir -p "${DIST_DIR}/AppDir/usr/share/applications"
mkdir -p "${DIST_DIR}/AppDir/usr/share/metainfo"
mkdir -p "${DIST_DIR}/AppDir/usr/share/icons/hicolor/256x256/apps"

echo "=== Copying Bundle Files ==="
cp -r "${BUILD_DIR}/"* "${DIST_DIR}/AppDir/usr/bin/"
cp "${ROOT_DIR}/linux/packaging/app.organiza.organiza.desktop" "${DIST_DIR}/AppDir/usr/share/applications/"
cp "${ROOT_DIR}/linux/packaging/app.organiza.organiza.metainfo.xml" "${DIST_DIR}/AppDir/usr/share/metainfo/"

# Copy icon if available, otherwise create placeholder SVG/PNG
if [ -f "${ROOT_DIR}/assets/icons/app_icon.png" ]; then
  cp "${ROOT_DIR}/assets/icons/app_icon.png" "${DIST_DIR}/AppDir/usr/share/icons/hicolor/256x256/apps/app.organiza.organiza.png"
  cp "${ROOT_DIR}/assets/icons/app_icon.png" "${DIST_DIR}/AppDir/app.organiza.organiza.png"
fi

# Create AppRun entrypoint
cat << 'EOF' > "${DIST_DIR}/AppDir/AppRun"
#!/usr/bin/env bash
SELF=$(readlink -f "$0")
HERE=${SELF%/*}
export PATH="${HERE}/usr/bin:${PATH}"
export LD_LIBRARY_PATH="${HERE}/usr/lib:${HERE}/usr/bin/lib:${LD_LIBRARY_PATH:-}"
exec "${HERE}/usr/bin/organiza" "$@"
EOF
chmod +x "${DIST_DIR}/AppDir/AppRun"

echo "=== Creating Portable Release Archive ==="
cd "${DIST_DIR}"
tar -czf "organiza-linux-x86_64.tar.gz" -C AppDir .

echo "=== Packaging complete: ${DIST_DIR}/organiza-linux-x86_64.tar.gz ==="
