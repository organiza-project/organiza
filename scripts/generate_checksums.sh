#!/usr/bin/env bash
# ============================================================================
# Generate SHA-256 Checksums for Release Artifacts
# ============================================================================
set -euo pipefail

TARGET_DIR="${1:-dist}"
OUTPUT_FILE="${TARGET_DIR}/organiza-checksums.txt"

if [ ! -d "${TARGET_DIR}" ]; then
  echo "Target directory ${TARGET_DIR} does not exist."
  exit 1
fi

echo "=== Generating SHA-256 checksums in ${TARGET_DIR} ==="
cd "${TARGET_DIR}"
find . -maxdepth 1 -type f ! -name "organiza-checksums.txt" -exec sha256sum {} + | sort -k 2 > "organiza-checksums.txt"

echo "=== Checksums written to ${OUTPUT_FILE} ==="
cat "organiza-checksums.txt"
