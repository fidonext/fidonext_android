#include <stdarg.h>
#include <stdbool.h>
#include <stdint.h>
#include <stdlib.h>

/**
 * Operation completed successfully.
 */
#define CABI_STATUS_SUCCESS 0

/**
 * One of the provided pointers was null.
 */
#define CABI_STATUS_NULL_POINTER 1

/**
 * Invalid argument supplied (e.g. malformed multiaddr).
 */
#define CABI_STATUS_INVALID_ARGUMENT 2

/**
 * Internal runtime error – check logs for details.
 */
#define CABI_STATUS_INTERNAL_ERROR 3

/**
 * No message available in the internal queue.
 */
#define CABI_STATUS_QUEUE_EMPTY -1

/**
 * Provided buffer is too small to fit the dequeued message.
 */
#define CABI_STATUS_BUFFER_TOO_SMALL -2

/**
 * The discovery query timed out.
 */
#define CABI_STATUS_TIMEOUT 6

/**
 * The target peer could not be located in the DHT.
 */
#define CABI_STATUS_NOT_FOUND 7



/**
 * Unknown decrypted E2EE message kind.
 */
#define CABI_E2EE_MESSAGE_KIND_UNKNOWN 0

/**
 * Decrypted E2EE message was a prekey message.
 */
#define CABI_E2EE_MESSAGE_KIND_PREKEY 1

/**
 * Decrypted E2EE message was a session message.
 */
#define CABI_E2EE_MESSAGE_KIND_SESSION 2

/**
 * AutoNAT status has not yet been determined.
 */
#define CABI_AUTONAT_UNKNOWN 0

/**
 * AutoNAT reports the node as privately reachable only.
 */
#define CABI_AUTONAT_PRIVATE 1

/**
 * AutoNAT reports the node as publicly reachable.
 */
#define CABI_AUTONAT_PUBLIC 2

/**
 * Discovery event carries an address for a peer.
 */
#define CABI_DISCOVERY_EVENT_ADDRESS 0

/**
 * Discovery query has finished.
 */
#define CABI_DISCOVERY_EVENT_FINISHED 1

#define DEFAULT_DELIVERY_TTL_SECONDS 300

#define MIN_DELIVERY_TTL_SECONDS 10

#define MAX_DELIVERY_TTL_SECONDS 86400

#define DEFAULT_MAILBOX_FETCH_LIMIT 64

/**
 * Default capacity for the message queue.
 */
#define DEFAULT_MESSAGE_QUEUE_CAPACITY 64

/**
 * Default capacity for the discovery event queue.
 */
#define DEFAULT_DISCOVERY_QUEUE_CAPACITY 64

/**
 * Opaque handle that callers treat as an identifier for a running node.
 */
typedef struct CabiNodeHandle {
  uint8_t _private[0];
} CabiNodeHandle;

/**
 * C-ABI. Inits tracing for the library in order to give more proper info on networking
 */
int cabi_init_tracing(void);

/**
 * C-ABI. Loads an identity profile from disk or creates one when missing.
 *
 * Returns account and device identifiers along with fixed-size identity seeds
 * used for deterministic libp2p/device bootstrap and Signal identity setup.
 */
int cabi_identity_load_or_create(const char *profile_path,
                                 char *account_id_buffer,
                                 uintptr_t account_id_buffer_len,
                                 uintptr_t *account_id_written_len,
                                 char *device_id_buffer,
                                 uintptr_t device_id_buffer_len,
                                 uintptr_t *device_id_written_len,
                                 uint8_t *libp2p_seed_buffer,
                                 uintptr_t libp2p_seed_buffer_len,
                                 uint8_t *signal_identity_seed_buffer,
                                 uintptr_t signal_identity_seed_buffer_len);

/**
 * C-ABI. Builds a signed key update document for the local profile.
 *
 * The output is a UTF-8 JSON document written to `out_buffer`.
 */
int cabi_e2ee_build_key_update(const char *profile_path,
                               const char *peer_id,
                               uint64_t revision,
                               uint64_t ttl_seconds,
                               uint8_t *out_buffer,
                               uintptr_t out_buffer_len,
                               uintptr_t *written_len);

/**
 * C-ABI. Validates a signed key update JSON document.
 *
 * `now_unix = 0` uses current UNIX timestamp for expiry checks.
 */
int cabi_e2ee_validate_key_update(const uint8_t *payload_ptr,
                                  uintptr_t payload_len,
                                  uint64_t now_unix);

/**
 * C-ABI. Builds an encrypted envelope JSON document.
 *
 * This function only wraps encrypted bytes + metadata. Actual encryption is
 * expected to happen in the caller/libsignal layer.
 */
int cabi_e2ee_build_envelope(const char *sender_account_id,
                             const char *sender_device_id,
                             const char *recipient_account_id,
                             const char *recipient_device_id,
                             const uint8_t *ciphertext_ptr,
                             uintptr_t ciphertext_len,
                             const uint8_t *aad_ptr,
                             uintptr_t aad_len,
                             uint8_t *out_buffer,
                             uintptr_t out_buffer_len,
                             uintptr_t *written_len);

/**
 * C-ABI. Validates an encrypted envelope JSON document.
 */
int cabi_e2ee_validate_envelope(const uint8_t *payload_ptr, uintptr_t payload_len);

/**
 * C-ABI. Builds a signed pre-key bundle JSON document from local signal state.
 *
 * The profile file controls where both identity and signal state are stored.
 */
int cabi_e2ee_build_prekey_bundle(const char *profile_path,
                                  uintptr_t one_time_prekey_count,
                                  uint64_t ttl_seconds,
                                  uint8_t *out_buffer,
                                  uintptr_t out_buffer_len,
                                  uintptr_t *written_len);

/**
 * C-ABI. Validates a signed pre-key bundle JSON document.
 *
 * `now_unix = 0` uses current UNIX timestamp for expiry checks.
 */
int cabi_e2ee_validate_prekey_bundle(const uint8_t *payload_ptr,
                                     uintptr_t payload_len,
                                     uint64_t now_unix);

/**
 * C-ABI legacy endpoint kept for ABI compatibility.
 *
 * Explicit prekey-message APIs are disabled; use `cabi_e2ee_build_message_auto`.
 */
int cabi_e2ee_build_prekey_message(const char *profile_path,
                                   const uint8_t *recipient_prekey_bundle_ptr,
                                   uintptr_t recipient_prekey_bundle_len,
                                   const uint8_t *plaintext_ptr,
                                   uintptr_t plaintext_len,
                                   const uint8_t *aad_ptr,
                                   uintptr_t aad_len,
                                   uint8_t *out_buffer,
                                   uintptr_t out_buffer_len,
                                   uintptr_t *written_len);

/**
 * C-ABI. Validates prekey message envelope and metadata.
 */
int cabi_e2ee_validate_prekey_message(const uint8_t *payload_ptr, uintptr_t payload_len);

/**
 * C-ABI legacy endpoint kept for ABI compatibility.
 *
 * Explicit prekey-decrypt APIs are disabled; use `cabi_e2ee_decrypt_message_auto`.
 */
int cabi_e2ee_decrypt_prekey_message(const char *profile_path,
                                     const uint8_t *payload_ptr,
                                     uintptr_t payload_len,
                                     uint8_t *out_plaintext_buffer,
                                     uintptr_t out_plaintext_buffer_len,
                                     uintptr_t *written_len);

/**
 * C-ABI legacy endpoint kept for ABI compatibility.
 *
 * Explicit session-message APIs are disabled; use `cabi_e2ee_build_message_auto`.
 */
int cabi_e2ee_build_session_message(const char *profile_path,
                                    const char *session_id,
                                    const uint8_t *plaintext_ptr,
                                    uintptr_t plaintext_len,
                                    const uint8_t *aad_ptr,
                                    uintptr_t aad_len,
                                    uint8_t *out_buffer,
                                    uintptr_t out_buffer_len,
                                    uintptr_t *written_len);

/**
 * C-ABI. Validates session message envelope and metadata.
 */
int cabi_e2ee_validate_session_message(const uint8_t *payload_ptr, uintptr_t payload_len);

/**
 * C-ABI legacy endpoint kept for ABI compatibility.
 *
 * Explicit session-decrypt APIs are disabled; use `cabi_e2ee_decrypt_message_auto`.
 */
int cabi_e2ee_decrypt_session_message(const char *profile_path,
                                      const uint8_t *payload_ptr,
                                      uintptr_t payload_len,
                                      uint8_t *out_plaintext_buffer,
                                      uintptr_t out_plaintext_buffer_len,
                                      uintptr_t *written_len);

/**
 * C-ABI. Builds and publishes the latest prekey bundle to DHT for local account/device.
 */
int cabi_e2ee_publish_prekey_bundle(struct CabiNodeHandle *handle,
                                    const char *profile_path,
                                    uintptr_t one_time_prekey_count,
                                    uint64_t bundle_ttl_seconds,
                                    uint64_t dht_ttl_seconds);

/**
 * C-ABI. Fetches and validates a prekey bundle from DHT by account/device id.
 */
int cabi_e2ee_fetch_prekey_bundle(struct CabiNodeHandle *handle,
                                  const char *account_id,
                                  const char *device_id,
                                  uint8_t *out_buffer,
                                  uintptr_t out_buffer_len,
                                  uintptr_t *written_len);

/**
 * C-ABI. Builds and publishes key-update document to DHT for local account/device.
 */
int cabi_e2ee_publish_key_update(struct CabiNodeHandle *handle,
                                 const char *profile_path,
                                 uint64_t revision,
                                 uint64_t key_update_ttl_seconds,
                                 uint64_t dht_ttl_seconds);

/**
 * C-ABI. Fetches and validates latest key-update document from DHT by account/device id.
 */
int cabi_e2ee_fetch_key_update(struct CabiNodeHandle *handle,
                               const char *account_id,
                               const char *device_id,
                               uint8_t *out_buffer,
                               uintptr_t out_buffer_len,
                               uintptr_t *written_len);

/**
 * C-ABI. Legacy device-directory validation API (disabled in single-device mode).
 */
int cabi_e2ee_validate_device_directory(const uint8_t *payload_ptr,
                                        uintptr_t payload_len,
                                        uint64_t now_unix);

/**
 * C-ABI. Legacy device-directory fetch API (disabled in single-device mode).
 */
int cabi_e2ee_fetch_device_directory(struct CabiNodeHandle *handle,
                                     const char *account_id,
                                     uint8_t *out_buffer,
                                     uintptr_t out_buffer_len,
                                     uintptr_t *written_len);

/**
 * C-ABI. Probe that executes an in-memory official libsignal roundtrip.
 */
int cabi_e2ee_libsignal_probe(void);

/**
 * C-ABI. Builds an outbound E2EE payload automatically:
 * - prekey message when no session exists for recipient account/device,
 * - session message when local session already exists.
 */
int cabi_e2ee_build_message_auto(const char *profile_path,
                                 const uint8_t *recipient_prekey_bundle_ptr,
                                 uintptr_t recipient_prekey_bundle_len,
                                 const uint8_t *plaintext_ptr,
                                 uintptr_t plaintext_len,
                                 const uint8_t *aad_ptr,
                                 uintptr_t aad_len,
                                 uint8_t *out_buffer,
                                 uintptr_t out_buffer_len,
                                 uintptr_t *written_len);

/**
 * C-ABI. Automatically decrypts incoming E2EE payload and returns plaintext.
 *
 * `message_kind` is set to one of:
 * - [`CABI_E2EE_MESSAGE_KIND_PREKEY`]
 * - [`CABI_E2EE_MESSAGE_KIND_SESSION`]
 */
int cabi_e2ee_decrypt_message_auto(const char *profile_path,
                                   const uint8_t *payload_ptr,
                                   uintptr_t payload_len,
                                   uint8_t *out_plaintext_buffer,
                                   uintptr_t out_plaintext_buffer_len,
                                   uintptr_t *written_len,
                                   int *message_kind);

/**
 * C-ABI. Returns the latest AutoNAT status observed for the node.
 * Use it to detect the node is public or not, which can be a signal to recreate
 * node as relay also
 */
int cabi_autonat_status(struct CabiNodeHandle *handle);

/**
 * C-ABI. Creates a new node instance and returns its handle with optional relay hop mode, bootstrap peers,
 * and a fixed Ed25519 identity seed.
 */
struct CabiNodeHandle *cabi_node_new(bool use_quic,
                                     bool enable_relay_hop,
                                     const char *const *bootstrap_peers,
                                     uintptr_t bootstrap_peers_len,
                                     const uint8_t *identity_seed_ptr,
                                     uintptr_t identity_seed_len);

/**
 * C-ABI. Writes the local PeerId into the provided buffer as a UTF-8 string.
 */
int cabi_node_local_peer_id(struct CabiNodeHandle *handle,
                            char *out_buffer,
                            uintptr_t buffer_len,
                            uintptr_t *written_len);

/**
 * C-ABI. Requests a circuit-relay reservation on the given relay address.
 */
int cabi_node_reserve_relay(struct CabiNodeHandle *handle, const char *address);

/**
 * C-ABI. Inits listening on the given address
 */
int cabi_node_listen(struct CabiNodeHandle *handle, const char *address);

/**
 * C-ABI. Inits a dial to the outbound peer with the specified address
 */
int cabi_node_dial(struct CabiNodeHandle *handle, const char *address);

/**
 * C-ABI. Starts a find_peer query for the given PeerId and returns a request identifier.
 */
int cabi_node_find_peer(struct CabiNodeHandle *handle, const char *peer_id, uint64_t *request_id);

/**
 * C-ABI. Starts a get_closest_peers query for the given PeerId and returns a request identifier.
 */
int cabi_node_get_closest_peers(struct CabiNodeHandle *handle,
                                const char *peer_id,
                                uint64_t *request_id);

/**
 * C-ABI. Stores a binary key/value record in Kademlia DHT.
 *
 * `ttl_seconds = 0` means "node default / no explicit TTL override".
 */
int cabi_node_dht_put_record(struct CabiNodeHandle *handle,
                             const uint8_t *key_ptr,
                             uintptr_t key_len,
                             const uint8_t *value_ptr,
                             uintptr_t value_len,
                             uint64_t ttl_seconds);

/**
 * C-ABI. Resolves a binary value by key from Kademlia DHT.
 */
int cabi_node_dht_get_record(struct CabiNodeHandle *handle,
                             const uint8_t *key_ptr,
                             uintptr_t key_len,
                             uint8_t *out_buffer,
                             uintptr_t buffer_len,
                             uintptr_t *written_len);

/**
 * C-ABI. Enqueues a binary payload into the node's internal message queue.
 */
int cabi_node_enqueue_message(struct CabiNodeHandle *handle,
                              const uint8_t *data_ptr,
                              uintptr_t data_len);

/**
 * C-ABI. Attempts to dequeue the next message into the provided buffer.
 *
 * Returns [`CABI_STATUS_QUEUE_EMPTY`] if no message is currently available,
 * and [`CABI_STATUS_BUFFER_TOO_SMALL`] when the provided buffer is not large
 * enough to hold the message (in that case `written_len` is set to the
 * required length).
 */
int cabi_node_dequeue_message(struct CabiNodeHandle *handle,
                              uint8_t *out_buffer,
                              uintptr_t buffer_len,
                              uintptr_t *written_len);

/**
 * C-ABI. Attempts to dequeue a discovery result produced by a Kademlia query.
 */
int cabi_node_dequeue_discovery_event(struct CabiNodeHandle *handle,
                                      int *event_kind,
                                      uint64_t *request_id,
                                      int *status_code,
                                      char *peer_id_buffer,
                                      uintptr_t peer_id_buffer_len,
                                      uintptr_t *peer_id_written_len,
                                      char *address_buffer,
                                      uintptr_t address_buffer_len,
                                      uintptr_t *address_written_len);

int cabi_node_get_addrs_snapshot(struct CabiNodeHandle *handle,
                                 uint64_t *out_version,
                                 char *out_buf,
                                 uintptr_t out_buf_len,
                                 uintptr_t *out_written);

/**
 * C-ABI. Frees node with specified handle
 */
void cabi_node_free(struct CabiNodeHandle *handle);
