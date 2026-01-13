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

    /**
     * Initialize tracing for the library
     */
    external fun cabiInitTracing(): Int

    /**
     * Get AutoNAT status for a node
     */
    external fun cabiAutonatStatus(handle: Long): Int

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
     * @return Request ID for tracking the query
     */
    external fun cabiNodeFindPeer(handle: Long, peerId: String): Long

    /**
     * Get closest peers to a peer ID
     * @return Request ID for tracking the query
     */
    external fun cabiNodeGetClosestPeers(handle: Long, peerId: String): Long

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
     * Free a node handle and shutdown the node
     */
    external fun cabiNodeFree(handle: Long)
}
