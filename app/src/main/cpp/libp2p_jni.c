#include <jni.h>
#include <stdlib.h>
#include <string.h>
#include <stdbool.h>

// Forward declarations of C-ABI functions from Rust library
// These will be linked from libcabi_rust_libp2p.so

extern int cabi_init_tracing(void);
extern int cabi_autonat_status(void* handle);
extern int cabi_identity_load_or_create(
    const char* profile_path,
    char* account_id_buffer,
    size_t account_id_buffer_len,
    size_t* account_id_written_len,
    char* device_id_buffer,
    size_t device_id_buffer_len,
    size_t* device_id_written_len,
    unsigned char* libp2p_seed_buffer,
    size_t libp2p_seed_buffer_len,
    unsigned char* signal_identity_seed_buffer,
    size_t signal_identity_seed_buffer_len
);

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
extern int cabi_node_dht_put_record(
    void* handle,
    const unsigned char* key_ptr,
    size_t key_len,
    const unsigned char* value_ptr,
    size_t value_len,
    unsigned long long ttl_seconds
);
extern int cabi_node_dht_get_record(
    void* handle,
    const unsigned char* key_ptr,
    size_t key_len,
    unsigned char* out_buffer,
    size_t buffer_len,
    size_t* written_len
);
extern int cabi_node_enqueue_message(void* handle, const unsigned char* data, size_t data_len);
extern int cabi_node_dequeue_message(void* handle, unsigned char* out_buffer, size_t buffer_len, size_t* written_len);
extern int cabi_node_dequeue_discovery_event(void* handle,
                                            int* event_kind,
                                            unsigned long long* request_id,
                                            int* status_code,
                                            char* peer_id_buffer,
                                            size_t peer_id_buffer_len,
                                            size_t* peer_id_written_len,
                                            char* address_buffer,
                                            size_t address_buffer_len,
                                            size_t* address_written_len);
extern void cabi_node_free(void* handle);

#define CABI_STATUS_QUEUE_EMPTY (-1)

extern int cabi_e2ee_build_prekey_bundle(
    const char* profile_path,
    size_t one_time_prekey_count,
    unsigned long long ttl_seconds,
    unsigned char* out_buffer,
    size_t out_buffer_len,
    size_t* written_len
);
extern int cabi_e2ee_validate_prekey_bundle(
    const unsigned char* payload_ptr,
    size_t payload_len,
    unsigned long long now_unix
);
extern int cabi_e2ee_build_message_auto(
    const char* profile_path,
    const unsigned char* recipient_prekey_bundle_ptr,
    size_t recipient_prekey_bundle_len,
    const unsigned char* plaintext_ptr,
    size_t plaintext_len,
    const unsigned char* aad_ptr,
    size_t aad_len,
    unsigned char* out_buffer,
    size_t out_buffer_len,
    size_t* written_len
);
extern int cabi_e2ee_decrypt_message_auto(
    const char* profile_path,
    const unsigned char* payload_ptr,
    size_t payload_len,
    unsigned char* out_plaintext_buffer,
    size_t out_plaintext_buffer_len,
    size_t* written_len,
    int* message_kind
);

static jbyteArray make_jbyte_array(JNIEnv* env, const unsigned char* data, size_t len) {
    if (data == NULL || len == 0) {
        return NULL;
    }
    jbyteArray out = (*env)->NewByteArray(env, (jsize)len);
    if (out == NULL) return NULL;
    (*env)->SetByteArrayRegion(env, out, 0, (jsize)len, (const jbyte*)data);
    return out;
}

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

JNIEXPORT jlong JNICALL
Java_com_fidonext_messenger_rust_Libp2pNative_cabiNodeNewWithSeed(JNIEnv *env, jobject obj,
                                                                  jboolean useQuic,
                                                                  jboolean enableRelayHop,
                                                                  jobjectArray bootstrapPeers,
                                                                  jbyteArray identitySeed) {
    int peer_count = 0;
    const char** peers = NULL;

    if (bootstrapPeers != NULL) {
        peer_count = (*env)->GetArrayLength(env, bootstrapPeers);
        peers = (const char**)malloc(peer_count * sizeof(char*));
        if (peers == NULL) return 0;
        for (int i = 0; i < peer_count; i++) {
            jstring peer_str = (jstring)(*env)->GetObjectArrayElement(env, bootstrapPeers, i);
            peers[i] = (*env)->GetStringUTFChars(env, peer_str, NULL);
        }
    }

    const unsigned char* seed_ptr = NULL;
    size_t seed_len = 0;
    jbyte* seed_bytes = NULL;
    if (identitySeed != NULL) {
        jsize len = (*env)->GetArrayLength(env, identitySeed);
        if (len > 0) {
            seed_len = (size_t)len;
            seed_bytes = (*env)->GetByteArrayElements(env, identitySeed, NULL);
            seed_ptr = (const unsigned char*)seed_bytes;
        }
    }

    void* handle = cabi_node_new(
        (bool)useQuic,
        (bool)enableRelayHop,
        peers,
        (size_t)peer_count,
        seed_ptr,
        seed_len
    );

    if (seed_bytes != NULL) {
        (*env)->ReleaseByteArrayElements(env, identitySeed, seed_bytes, JNI_ABORT);
    }

    if (peers != NULL) {
        for (int i = 0; i < peer_count; i++) {
            jstring peer_str = (jstring)(*env)->GetObjectArrayElement(env, bootstrapPeers, i);
            (*env)->ReleaseStringUTFChars(env, peer_str, peers[i]);
        }
        free(peers);
    }

    return (jlong)handle;
}

JNIEXPORT jobject JNICALL
Java_com_fidonext_messenger_rust_Libp2pNative_cabiIdentityLoadOrCreate(JNIEnv *env, jobject obj, jstring profilePath) {
    if (profilePath == NULL) return NULL;
    const char* path = (*env)->GetStringUTFChars(env, profilePath, NULL);
    if (path == NULL) return NULL;

    char account_buf[256];
    char device_buf[256];
    size_t account_written = 0;
    size_t device_written = 0;
    unsigned char libp2p_seed[32];
    unsigned char signal_seed[32];

    memset(account_buf, 0, sizeof(account_buf));
    memset(device_buf, 0, sizeof(device_buf));
    memset(libp2p_seed, 0, sizeof(libp2p_seed));
    memset(signal_seed, 0, sizeof(signal_seed));

    int status = cabi_identity_load_or_create(
        path,
        account_buf, sizeof(account_buf), &account_written,
        device_buf, sizeof(device_buf), &device_written,
        libp2p_seed, sizeof(libp2p_seed),
        signal_seed, sizeof(signal_seed)
    );

    (*env)->ReleaseStringUTFChars(env, profilePath, path);

    if (status != 0) {
        return NULL;
    }

    jclass cls = (*env)->FindClass(env, "com/fidonext/messenger/rust/Libp2pNative$IdentityProfile");
    if (cls == NULL) return NULL;
    jmethodID ctor = (*env)->GetMethodID(env, cls, "<init>", "(Ljava/lang/String;Ljava/lang/String;[B[B)V");
    if (ctor == NULL) return NULL;

    jstring accountId = (*env)->NewStringUTF(env, account_buf);
    jstring deviceId = (*env)->NewStringUTF(env, device_buf);
    jbyteArray libp2pSeed = make_jbyte_array(env, libp2p_seed, sizeof(libp2p_seed));
    jbyteArray signalSeed = make_jbyte_array(env, signal_seed, sizeof(signal_seed));

    if (accountId == NULL || deviceId == NULL || libp2pSeed == NULL || signalSeed == NULL) {
        return NULL;
    }

    return (*env)->NewObject(env, cls, ctor, accountId, deviceId, libp2pSeed, signalSeed);
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

JNIEXPORT jobject JNICALL
Java_com_fidonext_messenger_rust_Libp2pNative_cabiNodeDequeueDiscoveryEvent(JNIEnv *env, jobject obj, jlong handle) {
    if (handle == 0) return NULL;

    int event_kind = 0;
    unsigned long long request_id = 0;
    int status_code = 0;
    char peer_id_buf[256];
    char address_buf[1024];
    size_t peer_id_written = 0;
    size_t address_written = 0;

    memset(peer_id_buf, 0, sizeof(peer_id_buf));
    memset(address_buf, 0, sizeof(address_buf));

    int status = cabi_node_dequeue_discovery_event(
        (void*)handle,
        &event_kind,
        &request_id,
        &status_code,
        peer_id_buf,
        sizeof(peer_id_buf),
        &peer_id_written,
        address_buf,
        sizeof(address_buf),
        &address_written
    );

    if (status == CABI_STATUS_QUEUE_EMPTY) {
        return NULL;
    }
    if (status != 0) {
        return NULL;
    }

    jclass cls = (*env)->FindClass(env, "com/fidonext/messenger/rust/Libp2pNative$DiscoveryEvent");
    if (cls == NULL) return NULL;
    /* (eventKind: Int, requestId: Long, statusCode: Int, peerId: String, address: String) */
    jmethodID ctor = (*env)->GetMethodID(env, cls, "<init>", "(IJILjava/lang/String;Ljava/lang/String;)V");
    if (ctor == NULL) return NULL;

    jstring peerId = (*env)->NewStringUTF(env, peer_id_buf);
    jstring address = (*env)->NewStringUTF(env, address_buf);
    if (peerId == NULL) peerId = (*env)->NewStringUTF(env, "");
    if (address == NULL) address = (*env)->NewStringUTF(env, "");

    return (*env)->NewObject(env, cls, ctor,
                             (jint)event_kind,
                             (jlong)request_id,
                             (jint)status_code,
                             peerId,
                             address);
}

JNIEXPORT jint JNICALL
Java_com_fidonext_messenger_rust_Libp2pNative_cabiNodeDhtPutRecord(JNIEnv *env, jobject obj,
                                                                   jlong handle, jbyteArray key, jbyteArray value, jlong ttlSeconds) {
    if (handle == 0 || key == NULL || value == NULL) return 1;
    jsize key_len = (*env)->GetArrayLength(env, key);
    jsize value_len = (*env)->GetArrayLength(env, value);
    if (key_len <= 0 || value_len <= 0) return 2;

    jbyte* key_bytes = (*env)->GetByteArrayElements(env, key, NULL);
    jbyte* value_bytes = (*env)->GetByteArrayElements(env, value, NULL);
    if (key_bytes == NULL || value_bytes == NULL) {
        if (key_bytes != NULL) (*env)->ReleaseByteArrayElements(env, key, key_bytes, JNI_ABORT);
        if (value_bytes != NULL) (*env)->ReleaseByteArrayElements(env, value, value_bytes, JNI_ABORT);
        return 1;
    }

    int status = cabi_node_dht_put_record(
        (void*)handle,
        (const unsigned char*)key_bytes, (size_t)key_len,
        (const unsigned char*)value_bytes, (size_t)value_len,
        (unsigned long long)ttlSeconds
    );

    (*env)->ReleaseByteArrayElements(env, key, key_bytes, JNI_ABORT);
    (*env)->ReleaseByteArrayElements(env, value, value_bytes, JNI_ABORT);
    return status;
}

JNIEXPORT jbyteArray JNICALL
Java_com_fidonext_messenger_rust_Libp2pNative_cabiNodeDhtGetRecord(JNIEnv *env, jobject obj,
                                                                   jlong handle, jbyteArray key) {
    if (handle == 0 || key == NULL) return NULL;
    jsize key_len = (*env)->GetArrayLength(env, key);
    if (key_len <= 0) return NULL;

    jbyte* key_bytes = (*env)->GetByteArrayElements(env, key, NULL);
    if (key_bytes == NULL) return NULL;

    size_t cap = 64 * 1024;
    unsigned char* buffer = NULL;
    size_t written_len = 0;
    int status = 0;

    for (int attempt = 0; attempt < 4; attempt++) {
        buffer = (unsigned char*)malloc(cap);
        if (buffer == NULL) {
            (*env)->ReleaseByteArrayElements(env, key, key_bytes, JNI_ABORT);
            return NULL;
        }
        written_len = 0;
        status = cabi_node_dht_get_record(
            (void*)handle,
            (const unsigned char*)key_bytes, (size_t)key_len,
            buffer, cap,
            &written_len
        );
        if (status == -2 && written_len > cap) { // CABI_STATUS_BUFFER_TOO_SMALL
            free(buffer);
            buffer = NULL;
            cap = written_len + 1;
            continue;
        }
        break;
    }

    (*env)->ReleaseByteArrayElements(env, key, key_bytes, JNI_ABORT);

    if (status != 0 || written_len == 0) {
        if (buffer != NULL) free(buffer);
        return NULL;
    }

    jbyteArray out = make_jbyte_array(env, buffer, written_len);
    free(buffer);
    return out;
}

JNIEXPORT jbyteArray JNICALL
Java_com_fidonext_messenger_rust_Libp2pNative_cabiE2eeBuildPrekeyBundle(JNIEnv *env, jobject obj,
                                                                        jstring profilePath, jint oneTimePrekeyCount, jlong ttlSeconds) {
    if (profilePath == NULL) return NULL;
    const char* path = (*env)->GetStringUTFChars(env, profilePath, NULL);
    if (path == NULL) return NULL;

    size_t cap = 64 * 1024;
    unsigned char* buffer = (unsigned char*)malloc(cap);
    if (buffer == NULL) {
        (*env)->ReleaseStringUTFChars(env, profilePath, path);
        return NULL;
    }

    size_t written_len = 0;
    int status = cabi_e2ee_build_prekey_bundle(
        path,
        (size_t)(oneTimePrekeyCount > 0 ? oneTimePrekeyCount : 1),
        (unsigned long long)(ttlSeconds > 0 ? ttlSeconds : 1),
        buffer, cap, &written_len
    );

    (*env)->ReleaseStringUTFChars(env, profilePath, path);

    if (status != 0 || written_len == 0) {
        free(buffer);
        return NULL;
    }

    jbyteArray out = make_jbyte_array(env, buffer, written_len);
    free(buffer);
    return out;
}

JNIEXPORT jint JNICALL
Java_com_fidonext_messenger_rust_Libp2pNative_cabiE2eeValidatePrekeyBundle(JNIEnv *env, jobject obj,
                                                                           jbyteArray payload, jlong nowUnix) {
    if (payload == NULL) return 1;
    jsize len = (*env)->GetArrayLength(env, payload);
    if (len <= 0) return 2;

    jbyte* bytes = (*env)->GetByteArrayElements(env, payload, NULL);
    if (bytes == NULL) return 1;

    int status = cabi_e2ee_validate_prekey_bundle(
        (const unsigned char*)bytes,
        (size_t)len,
        (unsigned long long)(nowUnix >= 0 ? nowUnix : 0)
    );

    (*env)->ReleaseByteArrayElements(env, payload, bytes, JNI_ABORT);
    return status;
}

JNIEXPORT jbyteArray JNICALL
Java_com_fidonext_messenger_rust_Libp2pNative_cabiE2eeBuildMessageAuto(JNIEnv *env, jobject obj,
                                                                       jstring profilePath,
                                                                       jbyteArray recipientPrekeyBundle,
                                                                       jbyteArray plaintext,
                                                                       jbyteArray aad) {
    if (profilePath == NULL || recipientPrekeyBundle == NULL || plaintext == NULL || aad == NULL) return NULL;
    const char* path = (*env)->GetStringUTFChars(env, profilePath, NULL);
    if (path == NULL) return NULL;

    jsize bundle_len = (*env)->GetArrayLength(env, recipientPrekeyBundle);
    jsize plaintext_len = (*env)->GetArrayLength(env, plaintext);
    jsize aad_len = (*env)->GetArrayLength(env, aad);
    if (bundle_len <= 0 || plaintext_len < 0 || aad_len < 0) {
        (*env)->ReleaseStringUTFChars(env, profilePath, path);
        return NULL;
    }

    jbyte* bundle_bytes = (*env)->GetByteArrayElements(env, recipientPrekeyBundle, NULL);
    jbyte* plaintext_bytes = (*env)->GetByteArrayElements(env, plaintext, NULL);
    jbyte* aad_bytes = (*env)->GetByteArrayElements(env, aad, NULL);
    if (bundle_bytes == NULL || plaintext_bytes == NULL || aad_bytes == NULL) {
        if (bundle_bytes != NULL) (*env)->ReleaseByteArrayElements(env, recipientPrekeyBundle, bundle_bytes, JNI_ABORT);
        if (plaintext_bytes != NULL) (*env)->ReleaseByteArrayElements(env, plaintext, plaintext_bytes, JNI_ABORT);
        if (aad_bytes != NULL) (*env)->ReleaseByteArrayElements(env, aad, aad_bytes, JNI_ABORT);
        (*env)->ReleaseStringUTFChars(env, profilePath, path);
        return NULL;
    }

    size_t cap = 64 * 1024;
    unsigned char* buffer = (unsigned char*)malloc(cap);
    if (buffer == NULL) {
        (*env)->ReleaseByteArrayElements(env, recipientPrekeyBundle, bundle_bytes, JNI_ABORT);
        (*env)->ReleaseByteArrayElements(env, plaintext, plaintext_bytes, JNI_ABORT);
        (*env)->ReleaseByteArrayElements(env, aad, aad_bytes, JNI_ABORT);
        (*env)->ReleaseStringUTFChars(env, profilePath, path);
        return NULL;
    }

    size_t written_len = 0;
    int status = cabi_e2ee_build_message_auto(
        path,
        (const unsigned char*)bundle_bytes, (size_t)bundle_len,
        (const unsigned char*)plaintext_bytes, (size_t)plaintext_len,
        (const unsigned char*)aad_bytes, (size_t)aad_len,
        buffer, cap,
        &written_len
    );

    (*env)->ReleaseByteArrayElements(env, recipientPrekeyBundle, bundle_bytes, JNI_ABORT);
    (*env)->ReleaseByteArrayElements(env, plaintext, plaintext_bytes, JNI_ABORT);
    (*env)->ReleaseByteArrayElements(env, aad, aad_bytes, JNI_ABORT);
    (*env)->ReleaseStringUTFChars(env, profilePath, path);

    if (status != 0 || written_len == 0) {
        free(buffer);
        return NULL;
    }

    jbyteArray out = make_jbyte_array(env, buffer, written_len);
    free(buffer);
    return out;
}

JNIEXPORT jobject JNICALL
Java_com_fidonext_messenger_rust_Libp2pNative_cabiE2eeDecryptMessageAuto(JNIEnv *env, jobject obj,
                                                                         jstring profilePath, jbyteArray payload) {
    if (profilePath == NULL || payload == NULL) return NULL;
    const char* path = (*env)->GetStringUTFChars(env, profilePath, NULL);
    if (path == NULL) return NULL;

    jsize payload_len = (*env)->GetArrayLength(env, payload);
    if (payload_len <= 0) {
        (*env)->ReleaseStringUTFChars(env, profilePath, path);
        return NULL;
    }

    jbyte* payload_bytes = (*env)->GetByteArrayElements(env, payload, NULL);
    if (payload_bytes == NULL) {
        (*env)->ReleaseStringUTFChars(env, profilePath, path);
        return NULL;
    }

    size_t cap = 64 * 1024;
    unsigned char* buffer = (unsigned char*)malloc(cap);
    if (buffer == NULL) {
        (*env)->ReleaseByteArrayElements(env, payload, payload_bytes, JNI_ABORT);
        (*env)->ReleaseStringUTFChars(env, profilePath, path);
        return NULL;
    }

    size_t written_len = 0;
    int kind = 0;
    int status = cabi_e2ee_decrypt_message_auto(
        path,
        (const unsigned char*)payload_bytes, (size_t)payload_len,
        buffer, cap,
        &written_len,
        &kind
    );

    (*env)->ReleaseByteArrayElements(env, payload, payload_bytes, JNI_ABORT);
    (*env)->ReleaseStringUTFChars(env, profilePath, path);

    if (status != 0 || written_len == 0) {
        free(buffer);
        return NULL;
    }

    jclass cls = (*env)->FindClass(env, "com/fidonext/messenger/rust/Libp2pNative$DecryptedE2eeMessage");
    if (cls == NULL) {
        free(buffer);
        return NULL;
    }
    jmethodID ctor = (*env)->GetMethodID(env, cls, "<init>", "(I[B)V");
    if (ctor == NULL) {
        free(buffer);
        return NULL;
    }
    jbyteArray plaintext = make_jbyte_array(env, buffer, written_len);
    free(buffer);
    if (plaintext == NULL) return NULL;
    return (*env)->NewObject(env, cls, ctor, (jint)kind, plaintext);
}

JNIEXPORT void JNICALL
Java_com_fidonext_messenger_rust_Libp2pNative_cabiNodeFree(JNIEnv *env, jobject obj, jlong handle) {
    cabi_node_free((void*)handle);
}
