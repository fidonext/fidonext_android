#!/bin/bash
#
# Download pre-built libcabi_rust_libp2p Android binary and C header from
# https://github.com/fidonext/fidonext-core/releases
#
# Requires: curl
# Usage: run from project root, or pass FIDONEXT_ANDROID_ROOT.

set -e

RELEASE_TAG="${FIDONEXT_CORE_RELEASE:-v0.0.4}"
BASE_URL="https://github.com/fidonext/fidonext-core/releases/download/${RELEASE_TAG}"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
JNI_LIBS="$PROJECT_ROOT/app/src/main/jniLibs"
CPP_DIR="$PROJECT_ROOT/app/src/main/cpp"
ABI="arm64-v8a"

echo "Downloading fidonext-core ${RELEASE_TAG} (Android ${ABI} + header)..."
mkdir -p "$JNI_LIBS/$ABI"

# Android shared library (release naming: libcabi_rust_libp2p-android-arm64-v8a.so)
# We place it as libcabi_rust_libp2p.so for CMake/jniLibs layout.
SO_URL="${BASE_URL}/libcabi_rust_libp2p-android-${ABI}.so"
SO_DEST="$JNI_LIBS/$ABI/libcabi_rust_libp2p.so"
if ! curl -sfL -o "$SO_DEST" "$SO_URL"; then
  echo "Error: failed to download $SO_URL" >&2
  exit 1
fi
echo "  -> $SO_DEST"

# C header (for reference; JNI code uses forward declarations)
H_URL="${BASE_URL}/cabi-rust-libp2p.h"
H_DEST="$CPP_DIR/cabi-rust-libp2p.h"
if ! curl -sfL -o "$H_DEST" "$H_URL"; then
  echo "Error: failed to download $H_URL" >&2
  exit 1
fi
echo "  -> $H_DEST"

echo "Done. Pre-built libcabi_rust_libp2p is ready for arm64-v8a."
