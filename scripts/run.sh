#!/bin/bash

# FidoNext Android Run Script
# This script runs two instances of the app in two emulators for messaging testing

set -e  # Exit on error

echo "🚀 Starting FidoNext on two emulators..."

# Find Android SDK path
if [ -n "$ANDROID_HOME" ]; then
    ANDROID_SDK="$ANDROID_HOME"
elif [ -n "$ANDROID_SDK_ROOT" ]; then
    ANDROID_SDK="$ANDROID_SDK_ROOT"
elif [ -d "$HOME/Library/Android/sdk" ]; then
    ANDROID_SDK="$HOME/Library/Android/sdk"
elif [ -d "$HOME/Android/Sdk" ]; then
    ANDROID_SDK="$HOME/Android/Sdk"
else
    echo "❌ Android SDK not found. Please set ANDROID_HOME or ANDROID_SDK_ROOT"
    exit 1
fi

echo "📂 Using Android SDK: $ANDROID_SDK"

# Set paths to Android tools
EMULATOR_CMD="$ANDROID_SDK/emulator/emulator"
ADB_CMD="$ANDROID_SDK/platform-tools/adb"

# Check if tools exist
if [ ! -f "$EMULATOR_CMD" ]; then
    echo "❌ Emulator not found at: $EMULATOR_CMD"
    exit 1
fi

if [ ! -f "$ADB_CMD" ]; then
    echo "❌ ADB not found at: $ADB_CMD"
    exit 1
fi

# Check if APK exists
APK_PATH="app/build/outputs/apk/debug/app-debug.apk"
if [ ! -f "$APK_PATH" ]; then
    echo "❌ APK not found. Please run ./scripts/build.sh first"
    exit 1
fi

# Check if emulators exist
echo "📋 Checking available emulators..."
AVAILABLE_EMULATORS=$("$EMULATOR_CMD" -list-avds)
echo "$AVAILABLE_EMULATORS"

# Get first two available emulators
EMULATOR1=$(echo "$AVAILABLE_EMULATORS" | sed -n '1p')
EMULATOR2=$(echo "$AVAILABLE_EMULATORS" | sed -n '2p')

if [ -z "$EMULATOR1" ] || [ -z "$EMULATOR2" ]; then
    echo "❌ Need at least 2 emulators. Found:"
    echo "$AVAILABLE_EMULATORS"
    echo ""
    echo "Create emulators using Android Studio AVD Manager or:"
    echo "  avdmanager create avd -n Pixel_API_34 -k 'system-images;android-34;google_apis;x86_64'"
    exit 1
fi

# Start first emulator
echo ""
echo "📱 Starting first emulator: $EMULATOR1..."
"$EMULATOR_CMD" -avd "$EMULATOR1" &
EMULATOR1_PID=$!
echo "Started emulator with PID: $EMULATOR1_PID"

# Wait for first emulator to boot
echo "⏳ Waiting for first emulator to boot..."
"$ADB_CMD" wait-for-device
sleep 10
DEVICE1=$("$ADB_CMD" devices | grep emulator | head -n 1 | awk '{print $1}')
echo "✅ First emulator ready: $DEVICE1"

# Start second emulator
echo ""
echo "📱 Starting second emulator: $EMULATOR2..."
"$EMULATOR_CMD" -avd "$EMULATOR2" &
EMULATOR2_PID=$!
echo "Started emulator with PID: $EMULATOR2_PID"

# Wait for second emulator to boot
echo "⏳ Waiting for second emulator to boot..."
sleep 15
DEVICE2=$("$ADB_CMD" devices | grep emulator | tail -n 1 | awk '{print $1}')
echo "✅ Second emulator ready: $DEVICE2"

# Install app on first emulator
echo ""
echo "📦 Installing app on first emulator ($DEVICE1)..."
"$ADB_CMD" -s "$DEVICE1" install -r "$APK_PATH"
echo "✅ Installed on $DEVICE1"

# Install app on second emulator
echo ""
echo "📦 Installing app on second emulator ($DEVICE2)..."
"$ADB_CMD" -s "$DEVICE2" install -r "$APK_PATH"
echo "✅ Installed on $DEVICE2"

# Launch app on both emulators
PACKAGE_NAME="com.fidonext.messenger"
ACTIVITY_NAME=".MainActivity"

echo ""
echo "🚀 Launching app on first emulator..."
"$ADB_CMD" -s "$DEVICE1" shell am start -n "$PACKAGE_NAME/$ACTIVITY_NAME"
sleep 2

echo "🚀 Launching app on second emulator..."
"$ADB_CMD" -s "$DEVICE2" shell am start -n "$PACKAGE_NAME/$ACTIVITY_NAME"
sleep 2

# Grant runtime permissions for both devices
echo ""
echo "🔐 Granting permissions..."
"$ADB_CMD" -s "$DEVICE1" shell pm grant "$PACKAGE_NAME" android.permission.POST_NOTIFICATIONS 2>/dev/null || true
"$ADB_CMD" -s "$DEVICE2" shell pm grant "$PACKAGE_NAME" android.permission.POST_NOTIFICATIONS 2>/dev/null || true

# Get network info for both emulators
echo ""
echo "🌐 Network Information:"
echo "Device 1 ($DEVICE1):"
DEVICE1_IP=$("$ADB_CMD" -s "$DEVICE1" shell ip addr show wlan0 2>/dev/null | grep "inet " | awk '{print $2}' | cut -d'/' -f1 || echo "N/A")
echo "  IP: $DEVICE1_IP"

echo "Device 2 ($DEVICE2):"
DEVICE2_IP=$("$ADB_CMD" -s "$DEVICE2" shell ip addr show wlan0 2>/dev/null | grep "inet " | awk '{print $2}' | cut -d'/' -f1 || echo "N/A")
echo "  IP: $DEVICE2_IP"

echo ""
echo "✅ Both app instances are running and ready for messaging!"
echo "📱 Device 1: $DEVICE1"
echo "📱 Device 2: $DEVICE2"
echo ""
echo "💬 The apps can now discover and message each other via libp2p"
echo ""
echo "Useful commands:"
echo "  View Device 1 logs: \"$ADB_CMD\" -s $DEVICE1 logcat | grep -E \"FidoNext|Libp2p\""
echo "  View Device 2 logs: \"$ADB_CMD\" -s $DEVICE2 logcat | grep -E \"FidoNext|Libp2p\""
echo "  Stop Device 1: \"$ADB_CMD\" -s $DEVICE1 emu kill"
echo "  Stop Device 2: \"$ADB_CMD\" -s $DEVICE2 emu kill"
echo ""
echo "Press Ctrl+C to keep emulators running in background"
