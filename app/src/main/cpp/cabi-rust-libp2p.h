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
#define CABI_STATUS_QUEUE_EMPTY 4

/**
 * Provided buffer is too small to fit the dequeued message.
 */
#define CABI_STATUS_BUFFER_TOO_SMALL 5

/**
 * The discovery query timed out.
 */
#define CABI_STATUS_TIMEOUT 6

/**
 * The target peer could not be located in the DHT.
 */
#define CABI_STATUS_NOT_FOUND 7

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

/**
 * C-ABI. Frees node with specified handle
 */
void cabi_node_free(struct CabiNodeHandle *handle);
