#!/usr/bin/env bash
# ==============================================================================
# PicoPOSSUM - Release Artifacts SHA256 Checksum Generator
# ==============================================================================
# Scans built release bundles and archives, generating deterministic SHA256SUMS.
# ==============================================================================

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"
BUNDLE_DIR="${ROOT_DIR}/src-tauri/target/release/bundle"
OUTPUT_FILE="${ROOT_DIR}/SHA256SUMS"

echo "[*] Generating release checksums for PicoPOSSUM..."

if [ ! -d "${BUNDLE_DIR}" ]; then
  echo "[!] Bundle directory not found at: ${BUNDLE_DIR}"
  echo "    Build the release packages first using: npm run tauri build"
  exit 1
fi

TEMP_FILE="$(mktemp)"

find "${BUNDLE_DIR}" -type f \( \
  -name "*.AppImage" -o \
  -name "*.deb" -o \
  -name "*.rpm" -o \
  -name "*.dmg" -o \
  -name "*.msi" -o \
  -name "*.exe" -o \
  -name "*.tar.gz" -o \
  -name "*.zip" \
\) -exec sha256sum {} + | while read -r hash path; do
  filename="$(basename "${path}")"
  echo "${hash}  ${filename}"
done | sort -k2 > "${TEMP_FILE}"

if [ ! -s "${TEMP_FILE}" ]; then
  echo "[!] No release artifacts found in ${BUNDLE_DIR}."
  rm -f "${TEMP_FILE}"
  exit 1
fi

mv "${TEMP_FILE}" "${OUTPUT_FILE}"
echo "[✓] SHA256SUMS generated successfully at: ${OUTPUT_FILE}"
cat "${OUTPUT_FILE}"
