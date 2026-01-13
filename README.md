# FidoNext Messenger

An Android messenger application built with Kotlin and Jetpack Compose, featuring Rust native library integration for secure message processing.

## Features

- 🎨 Modern UI with Jetpack Compose
- 🔒 Message encryption using Rust native library
- 💬 Real-time chat interface
- 📱 Material Design 3
- ⚡ High-performance message processing

## Architecture

### Android (Kotlin)
- **UI Layer**: Jetpack Compose with Material 3
- **ViewModel**: MVVM architecture with StateFlow
- **JNI Bridge**: Native interface to Rust library

### Rust Native Library
- **Encryption**: Message encryption/decryption
- **Processing**: High-performance message processing
- **Hashing**: SHA-256 message hashing

## Prerequisites

- Android Studio (latest version)
- Android SDK 24+
- Rust toolchain (1.70+)
- Android NDK (r26+)
- cargo-ndk or manual NDK setup

## Setup

1. **Install Rust**:
   ```bash
   curl --proto '=https' --tlsv1.2 -sSf https://sh.rustup.rs | sh
   ```

2. **Add Android targets**:
   ```bash
   rustup target add aarch64-linux-android armv7-linux-androideabi i686-linux-android x86_64-linux-android
   ```

3. **Set up Android NDK**:
   - Install NDK via Android Studio SDK Manager
   - Set `ANDROID_NDK_HOME` environment variable

4. **Configure NDK paths**:
   Create `local.properties` in project root:
   ```properties
   sdk.dir=/path/to/Android/sdk
   ndk.dir=/path/to/Android/sdk/ndk/26.1.10909125
   ```

5. **Build Rust library**:
   ```bash
   cd rust
   chmod +x build.sh
   ./build.sh
   ```

6. **Build Android app**:
   ```bash
   ./gradlew assembleDebug
   ```

## Project Structure

```
fidonext_android/
├── app/
│   ├── src/main/
│   │   ├── java/com/fidonext/messenger/
│   │   │   ├── MainActivity.kt           # Main UI
│   │   │   ├── data/
│   │   │   │   └── Message.kt            # Data models
│   │   │   ├── rust/
│   │   │   │   └── RustNative.kt         # JNI interface
│   │   │   ├── ui/theme/                 # Compose theme
│   │   │   └── viewmodel/
│   │   │       └── ChatViewModel.kt      # Business logic
│   │   ├── jniLibs/                      # Compiled Rust libraries
│   │   └── AndroidManifest.xml
│   └── build.gradle.kts
├── rust/
│   ├── src/
│   │   └── lib.rs                        # Rust implementation
│   ├── Cargo.toml
│   ├── build.sh                          # Build script
│   └── .cargo/config.toml                # NDK configuration
└── build.gradle.kts

```

## Building Rust Library

The Rust library provides native functions for message encryption and processing:

### Manual Build (macOS/Linux):
```bash
cd rust
export ANDROID_NDK_HOME=/path/to/ndk
./build.sh
```

### Using cargo-ndk:
```bash
cd rust
cargo install cargo-ndk
cargo ndk -t armeabi-v7a -t arm64-v8a -t x86 -t x86_64 -o ../app/src/main/jniLibs build --release
```

## Rust Native Functions

The `RustNative` object exposes these methods:

- `encryptMessage(message: String): String` - Encrypts a message
- `decryptMessage(encrypted: String): String` - Decrypts a message
- `processMessage(message: String): String` - Processes a message
- `getVersion(): String` - Returns library version

## Development

### Running the app:
```bash
./gradlew installDebug
```

### Testing Rust code:
```bash
cd rust
cargo test
```

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
