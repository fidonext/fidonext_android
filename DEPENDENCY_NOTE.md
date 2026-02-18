# Dependency: libcabi_rust_libp2p

This project uses **pre-built Android binaries only** from [fidonext-core releases](https://github.com/fidonext/fidonext-core/releases). There is no Rust or native libp2p source code in this repository.

## What we use

- **Android**: `libcabi_rust_libp2p-android-arm64-v8a.so` (arm64-v8a only)
- **C header**: `cabi-rust-libp2p.h` (for reference; JNI uses forward declarations)

## How it works

1. **Download**: `./scripts/download_libcabi_rust_libp2p.sh` or the Gradle `preBuild` task (when the library is missing).
2. **Build**: The app links against `app/src/main/jniLibs/arm64-v8a/libcabi_rust_libp2p.so`.

Version is controlled by `FIDONEXT_CORE_RELEASE` (default: `v0.0.4`).

## References

- [fidonext-core repository](https://github.com/fidonext/fidonext-core)
- [fidonext-core releases](https://github.com/fidonext/fidonext-core/releases)
