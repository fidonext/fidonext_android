# FidoNext Messenger

An Android messenger application built with Kotlin and Jetpack Compose, using the libcabi_rust_libp2p native library from [fidonext-core](https://github.com/fidonext/fidonext-core) (pre-built binaries only; no Rust or native build in this repo).

## Features

- 🎨 Modern UI with Jetpack Compose
- 🔒 Message encryption using native libp2p library
- 💬 Real-time chat interface
- 📱 Material Design 3
- ⚡ High-performance message processing

## Architecture

### Android (Kotlin)
- **UI Layer**: Jetpack Compose with Material 3
- **ViewModel**: MVVM architecture with StateFlow
- **JNI Bridge**: Native interface to libcabi_rust_libp2p (pre-built)

### Native library (libcabi_rust_libp2p)
- Pre-built binaries from [fidonext-core releases](https://github.com/fidonext/fidonext-core/releases)
- Downloaded at build time; JNI in `app/src/main/cpp/` links against the `.so`

## Prerequisites

- Android Studio (latest version)
- Android SDK 24+
- Android NDK (for building the JNI wrapper; installed via SDK Manager)

## Quick Start

1. **Optional**: Add a relay list at `app/src/main/assets/bootstrap_nodes.txt`.
2. **Build** (downloads libp2p binary from fidonext-core if needed, then builds the app):
   ```bash
   ./scripts/build.sh
   ```
   Or with Gradle only:
   ```bash
   ./gradlew assembleDebug
   ```
   The first build will run `scripts/download_libcabi_rust_libp2p.sh` to fetch the pre-built `libcabi_rust_libp2p` for arm64-v8a.
3. **Run**: e.g. `./scripts/run.sh` or install the APK on a device/emulator.

Note: Pre-built library is **arm64-v8a** only (real devices). x86/x86_64 emulators are not supported unless fidonext-core adds those binaries to releases.

## Setup

1. **Android Studio / SDK**: Install Android Studio and NDK (SDK Manager → SDK Tools → NDK).
2. **local.properties** (optional, if not auto-detected):
   ```properties
   sdk.dir=/path/to/Android/sdk
   ```
3. **Build**: Run `./scripts/build.sh` or `./gradlew assembleDebug`. The first run will download the native library from GitHub releases.

## Project Structure

```
fidonext_android/
├── app/
│   ├── src/main/
│   │   ├── java/com/fidonext/messenger/
│   │   │   ├── MainActivity.kt           # Main UI
│   │   │   ├── data/
│   │   │   │   └── Message.kt            # Data models
│   │   │   ├── rust/                     # Kotlin package: JNI bindings to libp2p
│   │   │   │   └── RustNative.kt
│   │   │   ├── ui/theme/                 # Compose theme
│   │   │   └── viewmodel/
│   │   │       └── ChatViewModel.kt      # Business logic
│   │   ├── jniLibs/                      # Pre-built libcabi_rust_libp2p (downloaded)
│   │   └── AndroidManifest.xml
│   └── build.gradle.kts
├── scripts/
│   ├── build.sh                          # Download libp2p + Gradle build
│   └── download_libcabi_rust_libp2p.sh        # Fetch binary from fidonext-core releases
└── build.gradle.kts

```

## Native library (libcabi_rust_libp2p)

Pre-built binaries are downloaded from [fidonext-core releases](https://github.com/fidonext/fidonext-core/releases). To use a different version, set `FIDONEXT_CORE_RELEASE` when running the download script (e.g. `FIDONEXT_CORE_RELEASE=v0.0.3 ./scripts/download_libcabi_rust_libp2p.sh`).

## Development

### Running the app:
```bash
./gradlew installDebug
```

### Testing:
Use Android instrumented/unit tests as usual.

### Building release APK:
```bash
./gradlew assembleRelease
```

## Security Note

⚠️ The current encryption implementation uses a simple XOR cipher for demonstration purposes. For production use, implement proper encryption using libraries like:
- AES-GCM (already included in dependencies)
- ChaCha20-Poly1305
- Or use established protocols like Signal Protocol

## License

MIT License

## Contributing

Contributions are welcome! Please feel free to submit a Pull Request.
