# Dependency Note: cabi-rust-libp2p

## Current Implementation

This project currently **builds from source** using code from the [fidonext-core](https://github.com/fidonext/fidonext-core) repository.

## Why Not Using Release Binaries?

The latest release (v0.0.3) from fidonext-core provides these binaries:
- `libcabi_rust_libp2p-linux-aarch64.so` (Linux/glibc)
- `libcabi_rust_libp2p-linux-x86_64.so` (Linux/glibc)
- `cabi_rust_libp2p-windows-x86_64.dll` (Windows)

### The Problem

The Linux aarch64 binary is built against **glibc** and requires `libgcc_s.so.1`, which is **not available on Android**. Android uses **Bionic libc** instead of glibc, making these binaries incompatible.

### Error When Using Linux Binary on Android
```
java.lang.UnsatisfiedLinkError: dlopen failed: library "libgcc_s.so.1" not found:
needed by /data/app/.../libcabi_rust_libp2p.so in namespace clns-9
```

## Current Approach

The `rust/` directory contains the source code from fidonext-core (`c-abi-libp2p/`), which is:
1. Compiled specifically for Android using Android NDK toolchain
2. Built against Android's Bionic libc (no glibc dependency)
3. Generates Android-compatible `.so` files for multiple architectures:
   - `arm64-v8a` (64-bit ARM)
   - `armeabi-v7a` (32-bit ARM)
   - `x86` (32-bit x86)
   - `x86_64` (64-bit x86)

## Build Process

```bash
cd rust
./build.sh
```

This script:
1. Uses cargo with Android NDK toolchain
2. Cross-compiles for each Android ABI
3. Copies binaries to `app/src/main/jniLibs/{abi}/`

## Future: Using Pre-built Android Binaries

To use release binaries instead of building from source, the fidonext-core project would need to:

1. **Add Android release builds** to their CI/CD pipeline
2. **Cross-compile** using Android NDK for these targets:
   - `aarch64-linux-android` → arm64-v8a
   - `armv7-linux-androideabi` → armeabi-v7a
   - `i686-linux-android` → x86
   - `x86_64-linux-android` → x86_64

3. **Publish** Android-specific binaries with each release:
   - `libcabi_rust_libp2p-android-arm64-v8a.so`
   - `libcabi_rust_libp2p-android-armeabi-v7a.so`
   - `libcabi_rust_libp2p-android-x86.so`
   - `libcabi_rust_libp2p-android-x86_64.so`

## Using Release Binaries (When Available)

Once Android binaries are available in releases, use the `download_libp2p_binary.sh` script:

```bash
./download_libp2p_binary.sh
```

This will download the pre-built binaries and place them in the correct locations, eliminating the need to build from source.

## References

- [fidonext-core repository](https://github.com/fidonext/fidonext-core)
- [fidonext-core releases](https://github.com/fidonext/fidonext-core/releases)
- [Android NDK Documentation](https://developer.android.com/ndk)
- [Rust Android Documentation](https://mozilla.github.io/firefox-browser-architecture/experiments/2017-09-21-rust-on-android.html)
