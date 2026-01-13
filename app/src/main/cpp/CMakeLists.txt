cmake_minimum_required(VERSION 3.22.1)

project("libp2p_jni")

# Add the JNI wrapper library
add_library(libp2p_jni SHARED libp2p_jni.c)

# Find the log library
find_library(log-lib log)

# Link against the pre-built Rust library
set(RUST_LIB_PATH ${CMAKE_SOURCE_DIR}/../jniLibs/${ANDROID_ABI}/libcabi_rust_libp2p.so)

target_link_libraries(libp2p_jni
    ${log-lib}
    ${RUST_LIB_PATH}
)
