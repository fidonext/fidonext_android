# Project: FidoNext Android App

## Description
- Decentralized peer-to-peer messenger with strong cryptography and privacy protection

## Stack
- Kotlin
- Android (minSdk 24, targetSdk 34)
- Jetpack Compose + Material Design 3
- MVVM with ViewModel and StateFlow
- Kotlin Coroutines
- Precompiled libcabi_rust_libp2p from [fidonext-core](https://github.com/fidonext/fidonext-core) (arm64-v8a; downloaded at build time)
- JNI bridge in `app/src/main/cpp/` (CMake 3.22.1)

## Architecture
- **UI**: Jetpack Compose; entry in `MainActivity.kt`; theme under `app/src/main/java/.../ui/theme/`
- **ViewModel**: `ChatViewModel.kt` — state via StateFlow, binds to Libp2pService
- **Service**: `Libp2pService.kt` — foreground service running libp2p; health checks (~5s), auto-restart on failure; AIDL interface `ILibp2pService`
- **Native**: Kotlin `Libp2pNative` (in `rust/` package) → JNI in `cpp/` → pre-built `libcabi_rust_libp2p.so`
- No Rust or Flutter in this repo; no BLoC or flutter_bloc
- No pure libp2p in this repo, because libcabi_rust_libp2p uses it inside
- libcabi_rust_libp2p as dependency
- libcabi_rust_libp2p as service
- libcabi_rust_libp2p service health monitoring
- No using x86/x86_64 emulators

## Key Files
- `app/src/main/java/com/fidonext/messenger/MainActivity.kt` — entry, Compose, service binding
- `app/src/main/java/com/fidonext/messenger/viewmodel/ChatViewModel.kt` — chat state, service usage
- `app/src/main/java/com/fidonext/messenger/service/Libp2pService.kt` — libcabi_rust_libp2p foreground service
- `app/src/main/java/com/fidonext/messenger/rust/RustNative.kt` — JNI declarations for libcabi_rust_libp2p
- `app/src/main/cpp/` — JNI impl and CMake
- `app/src/main/assets/bootstrap_nodes.txt` — bootstrap peers
- `scripts/build.sh`, `scripts/download_libcabi_rust_libp2p.sh` — build and binary download
- `scripts/run.sh` — run for test

## Conventions
- Prefer English comments
- Follow existing Kotlin/Compose style

## Commands
- `./scripts/download_libcabi_rust_libp2p.sh` — downloads libcabi_rust_libp2p binary)
- `./scripts/build.sh` — build (downloads libp2p binary if missing)
- `./scripts/run.sh` — run (two Android emulators each with APK installed)
- `./gradlew assembleDebug` — build
- `./gradlew installDebug` — install on device
- `./gradlew assembleRelease` — release APK

## Known Issues
- **CMake**: Project uses CMake 3.22.1 (set in `app/build.gradle.kts`). Do not assume CMake 4.x is supported without testing.
- **JDK**: Building requires JDK 17+ (Gradle/AGP). The app compiles to Java 8 bytecode.
- **ABI**: Pre-built lib is **arm64-v8a only**. x86/x86_64 emulators are not supported unless fidonext-core adds those binaries; use a real device or arm64 system image.
- **Encryption**: Message encryption uses a simple XOR cipher for demonstration only. Not suitable for production; use proper crypto (e.g. AES-GCM, ChaCha20-Poly1305, or Signal Protocol) before production.
- **ProGuard**: `app/proguard-rules.pro` keeps `com.fidonext.messenger.rust.RustNative`; the JNI wrapper is the object `Libp2pNative` in that package. If you enable minification for release, ensure the class that declares native methods is kept (e.g. `Libp2pNative`).
- **Native lib loading**: Native libraries are loaded in both `MainActivity.onCreate()` and `Libp2pService` companion `init`. Redundant but safe; service can run without the activity.
- **run.sh**: `scripts/run.sh` expects two AVDs; with arm64-only lib, default x86_64 AVDs will not run the app. Use arm64-v8a system images or two physical devices for two-instance testing.
