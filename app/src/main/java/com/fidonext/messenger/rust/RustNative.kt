package com.fidonext.messenger.rust

/**
 * Kotlin wrapper for the C-ABI libp2p Rust library.
 * This provides a type-safe interface to the FidoNext networking layer.
 */
object Libp2pNative {

    // Status codes
    const val STATUS_SUCCESS = 0
    const val STATUS_NULL_POINTER = 1
    const val STATUS_INVALID_ARGUMENT = 2
    const val STATUS_INTERNAL_ERROR = 3
    const val STATUS_QUEUE_EMPTY = 4
    const val STATUS_BUFFER_TOO_SMALL = 5
    const val STATUS_TIMEOUT = 6
    const val STATUS_NOT_FOUND = 7

    // AutoNAT status
    const val AUTONAT_UNKNOWN = 0
    const val AUTONAT_PRIVATE = 1
    const val AUTONAT_PUBLIC = 2

    // E2EE message kinds
    const val E2EE_MESSAGE_KIND_UNKNOWN = 0
    const val E2EE_MESSAGE_KIND_PREKEY = 1
    const val E2EE_MESSAGE_KIND_SESSION = 2

    // Discovery event kinds (Kademlia find_peer / get_closest_peers)
    const val DISCOVERY_EVENT_ADDRESS = 0
    const val DISCOVERY_EVENT_FINISHED = 1

    /**
     * Initialize tracing for the library
     */
    external fun cabiInitTracing(): Int

    /**
     * Get AutoNAT status for a node
     */
    external fun cabiAutonatStatus(handle: Long): Int

    /**
     * Loads an identity profile from disk or creates one when missing.
     * Returns a map with:
     * - accountId (String)
     * - deviceId (String)
     * - libp2pSeed (ByteArray, 32 bytes)
     * - signalSeed (ByteArray, 32 bytes)
     */
    external fun cabiIdentityLoadOrCreate(profilePath: String): IdentityProfile?

    data class IdentityProfile(
        val accountId: String,
        val deviceId: String,
        val libp2pSeed: ByteArray,
        val signalSeed: ByteArray,
    )

    /**
     * Create a new libp2p node
     * @param privateKeyBase64 Base64-encoded Ed25519 private key (optional, pass null to generate)
     * @param bootstrapPeers Array of bootstrap peer multiaddresses
     * @return Node handle as Long, or 0 on failure
     */
    external fun cabiNodeNew(
        privateKeyBase64: String?,
        bootstrapPeers: Array<String>
    ): Long

    /**
     * Creates a new libp2p node using an explicit 32-byte identity seed.
     * Mirrors the upstream C-ABI `cabi_node_new(..., identity_seed_ptr, identity_seed_len)`.
     */
    external fun cabiNodeNewWithSeed(
        useQuic: Boolean,
        enableRelayHop: Boolean,
        bootstrapPeers: Array<String>,
        identitySeed: ByteArray?,
    ): Long

    /**
     * Get the local peer ID for a node
     * @return Peer ID as string
     */
    external fun cabiNodeLocalPeerId(handle: Long): String?

    /**
     * Start listening on an address
     */
    external fun cabiNodeListen(handle: Long, address: String): Int

    /**
     * Dial a remote peer
     */
    external fun cabiNodeDial(handle: Long, address: String): Int

    /**
     * Find a peer in the DHT
     * @return Request ID for tracking the query, or 0 on failure
     */
    external fun cabiNodeFindPeer(handle: Long, peerId: String): Long

    /**
     * Get closest peers to a peer ID
     * @return Request ID for tracking the query
     */
    external fun cabiNodeGetClosestPeers(handle: Long, peerId: String): Long

    /**
     * Dequeue a discovery result from a Kademlia find_peer / get_closest_peers query.
     * @return DiscoveryEvent, or null when queue is empty
     */
    external fun cabiNodeDequeueDiscoveryEvent(handle: Long): DiscoveryEvent?

    data class DiscoveryEvent(
        val eventKind: Int,
        val requestId: Long,
        val statusCode: Int,
        val peerId: String,
        val address: String,
    )

    /**
     * Stores a binary key/value record in Kademlia DHT.
     * ttlSeconds = 0 means "node default".
     */
    external fun cabiNodeDhtPutRecord(handle: Long, key: ByteArray, value: ByteArray, ttlSeconds: Long): Int

    /**
     * Resolves a binary value by key from Kademlia DHT.
     * Returns null when missing or on error.
     */
    external fun cabiNodeDhtGetRecord(handle: Long, key: ByteArray): ByteArray?

    /**
     * Enqueue a message to be published via gossipsub
     */
    external fun cabiNodeEnqueueMessage(handle: Long, message: ByteArray): Int

    /**
     * Dequeue a received message
     * @return Message as ByteArray, or null if queue is empty
     */
    external fun cabiNodeDequeueMessage(handle: Long): ByteArray?

    /**
     * Builds a signed pre-key bundle JSON document from local signal state.
     */
    external fun cabiE2eeBuildPrekeyBundle(profilePath: String, oneTimePrekeyCount: Int, ttlSeconds: Long): ByteArray?

    /**
     * Validates a signed pre-key bundle JSON document. Returns status code.
     */
    external fun cabiE2eeValidatePrekeyBundle(payload: ByteArray, nowUnix: Long): Int

    /**
     * Builds an outbound E2EE payload automatically (prekey or session).
     */
    external fun cabiE2eeBuildMessageAuto(
        profilePath: String,
        recipientPrekeyBundle: ByteArray,
        plaintext: ByteArray,
        aad: ByteArray,
    ): ByteArray?

    /**
     * Decrypts incoming E2EE payload automatically.
     * Returns a pair(kind, plaintext) or null on failure.
     */
    external fun cabiE2eeDecryptMessageAuto(profilePath: String, payload: ByteArray): DecryptedE2eeMessage?

    data class DecryptedE2eeMessage(
        val kind: Int,
        val plaintext: ByteArray,
    )

    /**
     * Free a node handle and shutdown the node
     */
    external fun cabiNodeFree(handle: Long)
}
