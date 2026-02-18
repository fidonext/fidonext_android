#!/bin/bash

# FidoNext Android Build Script
# This script builds the app and prepares it to run

set -e  # Exit on error

echo "🔨 Building FidoNext Android App..."

# Download pre-built libp2p binary from fidonext-core releases (no Rust build)
echo "📥 Downloading libp2p binary from fidonext-core releases..."
./scripts/download_libcabi_rust_libp2p.sh

# Clean previous builds
echo "📦 Cleaning previous builds..."
./gradlew clean

# Build the debug APK
echo "🏗️  Building debug APK..."
./gradlew assembleDebug

# Check if build was successful
if [ -f "app/build/outputs/apk/debug/app-debug.apk" ]; then
    echo "✅ Build successful!"
    echo "📱 APK location: app/build/outputs/apk/debug/app-debug.apk"
    echo ""
    echo "To install on a connected device, run:"
    echo "  adb install -r app/build/outputs/apk/debug/app-debug.apk"
else
    echo "❌ Build failed - APK not found"
    exit 1
fi
