#!/bin/bash

# NOTE: This script is currently NOT FUNCTIONAL for Android!
#
# The release binaries from fidonext-core are built for Linux (glibc) and are
# NOT compatible with Android (which uses Bionic libc).
#
# Attempting to use the Linux aarch64 binary results in:
# "dlopen failed: library libgcc_s.so.1 not found"
#
# To use this project, you must BUILD FROM SOURCE using:
#   cd rust && ./build.sh
#
# See DEPENDENCY_NOTE.md for full explanation.

echo "⚠️  WARNING: Release binaries are NOT compatible with Android!"
echo ""
echo "The Linux aarch64 binary requires glibc (libgcc_s.so.1)."
echo "Android uses Bionic libc and cannot load these binaries."
echo ""
echo "Please build from source instead:"
echo "  cd rust && ./build.sh"
echo ""
echo "See DEPENDENCY_NOTE.md for detailed information."
exit 1
