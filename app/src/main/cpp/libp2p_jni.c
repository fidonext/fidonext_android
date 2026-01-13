#include <jni.h>
#include <stdlib.h>
#include <string.h>
#include <stdbool.h>

// Forward declarations of C-ABI functions from Rust library
// These will be linked from libcabi_rust_libp2p.so

extern int cabi_init_tracing(void);
extern int cabi_autonat_status(void* handle);
extern void* cabi_node_new(
    bool use_quic,
    bool enable_relay_hop,
    const char** bootstrap_peers,
    size_t bootstrap_peers_len,
    const unsigned char* identity_seed_ptr,
    size_t identity_seed_len
);
extern int cabi_node_local_peer_id(void* handle, char* out_buffer, size_t buffer_len, size_t* written_len);
extern int cabi_node_listen(void* handle, const char* address);
extern int cabi_node_dial(void* handle, const char* address);
extern int cabi_node_find_peer(void* handle, const char* peer_id, unsigned long long* request_id);
extern int cabi_node_get_closest_peers(void* handle, const char* peer_id, unsigned long long* request_id);
extern int cabi_node_enqueue_message(void* handle, const unsigned char* data, size_t data_len);
extern int cabi_node_dequeue_message(void* handle, unsigned char* out_buffer, size_t buffer_len, size_t* written_len);
extern void cabi_node_free(void* handle);

JNIEXPORT jint JNICALL
Java_com_fidonext_messenger_rust_Libp2pNative_cabiInitTracing(JNIEnv *env, jobject obj) {
    return cabi_init_tracing();
}

JNIEXPORT jint JNICALL
Java_com_fidonext_messenger_rust_Libp2pNative_cabiAutonatStatus(JNIEnv *env, jobject obj, jlong handle) {
    return cabi_autonat_status((void*)handle);
}

JNIEXPORT jlong JNICALL
Java_com_fidonext_messenger_rust_Libp2pNative_cabiNodeNew(JNIEnv *env, jobject obj,
                                                           jstring privateKeyBase64,
                                                           jobjectArray bootstrapPeers) {
    // For now, use TCP (not QUIC) and no relay hop
    bool use_quic = false;
    bool enable_relay_hop = false;

    int peer_count = 0;
    const char** peers = NULL;

    if (bootstrapPeers != NULL) {
        peer_count = (*env)->GetArrayLength(env, bootstrapPeers);
        peers = (const char**)malloc(peer_count * sizeof(char*));

        for (int i = 0; i < peer_count; i++) {
            jstring peer_str = (jstring)(*env)->GetObjectArrayElement(env, bootstrapPeers, i);
            peers[i] = (*env)->GetStringUTFChars(env, peer_str, NULL);
        }
    }

    // Note: privateKeyBase64 is ignored for now (generate new identity)
    // To support it, would need to decode base64 to 32-byte seed
    void* handle = cabi_node_new(use_quic, enable_relay_hop, peers, peer_count, NULL, 0);

    // Cleanup
    if (peers != NULL) {
        for (int i = 0; i < peer_count; i++) {
            jstring peer_str = (jstring)(*env)->GetObjectArrayElement(env, bootstrapPeers, i);
            (*env)->ReleaseStringUTFChars(env, peer_str, peers[i]);
        }
        free(peers);
    }

    return (jlong)handle;
}

JNIEXPORT jstring JNICALL
Java_com_fidonext_messenger_rust_Libp2pNative_cabiNodeLocalPeerId(JNIEnv *env, jobject obj, jlong handle) {
    char buffer[256];
    size_t written_len = 0;
    int status = cabi_node_local_peer_id((void*)handle, buffer, sizeof(buffer), &written_len);

    if (status == 0) {
        return (*env)->NewStringUTF(env, buffer);
    }
    return NULL;
}

JNIEXPORT jint JNICALL
Java_com_fidonext_messenger_rust_Libp2pNative_cabiNodeListen(JNIEnv *env, jobject obj, 
                                                              jlong handle, jstring address) {
    const char* addr = (*env)->GetStringUTFChars(env, address, NULL);
    int result = cabi_node_listen((void*)handle, addr);
    (*env)->ReleaseStringUTFChars(env, address, addr);
    return result;
}

JNIEXPORT jint JNICALL
Java_com_fidonext_messenger_rust_Libp2pNative_cabiNodeDial(JNIEnv *env, jobject obj,
                                                            jlong handle, jstring address) {
    const char* addr = (*env)->GetStringUTFChars(env, address, NULL);
    int result = cabi_node_dial((void*)handle, addr);
    (*env)->ReleaseStringUTFChars(env, address, addr);
    return result;
}

JNIEXPORT jlong JNICALL
Java_com_fidonext_messenger_rust_Libp2pNative_cabiNodeFindPeer(JNIEnv *env, jobject obj,
                                                                jlong handle, jstring peerId) {
    const char* peer_id = (*env)->GetStringUTFChars(env, peerId, NULL);
    unsigned long long request_id = 0;
    int status = cabi_node_find_peer((void*)handle, peer_id, &request_id);
    (*env)->ReleaseStringUTFChars(env, peerId, peer_id);
    return (status == 0) ? (jlong)request_id : 0;
}

JNIEXPORT jlong JNICALL
Java_com_fidonext_messenger_rust_Libp2pNative_cabiNodeGetClosestPeers(JNIEnv *env, jobject obj,
                                                                        jlong handle, jstring peerId) {
    const char* peer_id = (*env)->GetStringUTFChars(env, peerId, NULL);
    unsigned long long request_id = 0;
    int status = cabi_node_get_closest_peers((void*)handle, peer_id, &request_id);
    (*env)->ReleaseStringUTFChars(env, peerId, peer_id);
    return (status == 0) ? (jlong)request_id : 0;
}

JNIEXPORT jint JNICALL
Java_com_fidonext_messenger_rust_Libp2pNative_cabiNodeEnqueueMessage(JNIEnv *env, jobject obj,
                                                                      jlong handle, jbyteArray message) {
    jsize len = (*env)->GetArrayLength(env, message);
    jbyte* bytes = (*env)->GetByteArrayElements(env, message, NULL);
    
    int result = cabi_node_enqueue_message((void*)handle, (unsigned char*)bytes, len);
    
    (*env)->ReleaseByteArrayElements(env, message, bytes, JNI_ABORT);
    return result;
}

JNIEXPORT jbyteArray JNICALL
Java_com_fidonext_messenger_rust_Libp2pNative_cabiNodeDequeueMessage(JNIEnv *env, jobject obj, jlong handle) {
    unsigned char buffer[65536]; // 64KB buffer
    size_t written_len = 0;

    int status = cabi_node_dequeue_message((void*)handle, buffer, sizeof(buffer), &written_len);

    if (status == 0 && written_len > 0) {
        jbyteArray result = (*env)->NewByteArray(env, written_len);
        (*env)->SetByteArrayRegion(env, result, 0, written_len, (jbyte*)buffer);
        return result;
    }

    return NULL;
}

JNIEXPORT void JNICALL
Java_com_fidonext_messenger_rust_Libp2pNative_cabiNodeFree(JNIEnv *env, jobject obj, jlong handle) {
    cabi_node_free((void*)handle);
}
