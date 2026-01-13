#!/bin/bash

# Build script for c-abi-libp2p Rust library for Android

set -e

# Set Android NDK path
export ANDROID_NDK_HOME="$HOME/Library/Android/sdk/ndk/28.2.13676358"
export NDK_HOME="$ANDROID_NDK_HOME"

# Android NDK targets
TARGETS=(
    "aarch64-linux-android"
    "armv7-linux-androideabi"
    "i686-linux-android"
    "x86_64-linux-android"
)

# Corresponding Android ABI names
ABIS=(
    "arm64-v8a"
    "armeabi-v7a"
    "x86"
    "x86_64"
)

echo "Building c-abi-libp2p Rust library for Android..."

# Add Android targets if not already added
for target in "${TARGETS[@]}"; do
    rustup target add "$target" 2>/dev/null || true
done

# Build for each target
for i in "${!TARGETS[@]}"; do
    target="${TARGETS[$i]}"
    abi="${ABIS[$i]}"

    echo "Building for $target ($abi)..."

    # Set CC and AR environment variables for cross-compilation
    export AR="$ANDROID_NDK_HOME/toolchains/llvm/prebuilt/darwin-x86_64/bin/llvm-ar"

    case "$target" in
        aarch64-linux-android)
            export CC_aarch64_linux_android="$ANDROID_NDK_HOME/toolchains/llvm/prebuilt/darwin-x86_64/bin/aarch64-linux-android34-clang"
            export AR_aarch64_linux_android="$AR"
            ;;
        armv7-linux-androideabi)
            export CC_armv7_linux_androideabi="$ANDROID_NDK_HOME/toolchains/llvm/prebuilt/darwin-x86_64/bin/armv7a-linux-androideabi34-clang"
            export AR_armv7_linux_androideabi="$AR"
            ;;
        i686-linux-android)
            export CC_i686_linux_android="$ANDROID_NDK_HOME/toolchains/llvm/prebuilt/darwin-x86_64/bin/i686-linux-android34-clang"
            export AR_i686_linux_android="$AR"
            ;;
        x86_64-linux-android)
            export CC_x86_64_linux_android="$ANDROID_NDK_HOME/toolchains/llvm/prebuilt/darwin-x86_64/bin/x86_64-linux-android34-clang"
            export AR_x86_64_linux_android="$AR"
            ;;
    esac

    cargo build --target "$target" --release

    # Create jniLibs directory structure
    mkdir -p "../app/src/main/jniLibs/$abi"

    # Copy the built library
    cp "target/$target/release/libcabi_rust_libp2p.so" "../app/src/main/jniLibs/$abi/libcabi_rust_libp2p.so"
done

# Generate C header
echo "Generating C header..."
cargo build --release
cp cabi-rust-libp2p.h ../app/src/main/cpp/

echo "Build complete! Libraries copied to app/src/main/jniLibs/"
