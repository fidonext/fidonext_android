# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.

# Keep Rust native methods
-keepclasseswithmembernames class * {
    native <methods>;
}

-keep class com.fidonext.messenger.rust.RustNative { *; }
