package com.fidonext.messenger.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.fidonext.messenger.ILibp2pService
import com.fidonext.messenger.R
import com.fidonext.messenger.rust.Libp2pNative
import kotlinx.coroutines.*
import org.json.JSONObject
import java.nio.charset.StandardCharsets
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/**
 * Android service that runs cabi_rust_libp2p as a foreground service.
 * Provides messaging functionality through AIDL interface.
 */
class Libp2pService : Service() {

    companion object {
        private const val TAG = "Libp2pService"
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "libp2p_service_channel"
        private const val HEALTH_CHECK_INTERVAL_MS = 5000L
        private const val MESSAGE_POLL_INTERVAL_MS = 100L
        /** Re-announce directory+prekey to DHT periodically (mirrors Python _announce_loop) */
        private const val DIRECTORY_REANNOUNCE_INTERVAL_MS = 10 * 60 * 1000L

        init {
            // Load native libraries
            System.loadLibrary("cabi_rust_libp2p")
            System.loadLibrary("libp2p_jni")
        }
    }

    private var nodeHandle: Long = 0
    private val isRunning = AtomicBoolean(false)
    private val lastHealthCheck = AtomicLong(0)
    private val serviceScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    /** Bootstrap peers used at init; restored on health-check restart */
    private var lastBootstrapPeers: Array<String> = emptyArray()
    private var profilePath: String? = null
    private var localAccountId: String? = null
    private var localDeviceId: String? = null
    private var localPeerId: String? = null
    private var activeRecipientPeerId: String? = null
    /** Cached prekey bundle per peer_id (filled when opening chat / setActiveRecipient), used when sending. */
    private val recipientPrekeyCache = ConcurrentHashMap<String, ByteArray>()

    /** Returns cached account ID; use from binder to avoid getter name clash with getLocalAccountId(). */
    private fun cachedAccountId(): String? = localAccountId

    /** Returns cached device ID; use from binder to avoid getter name clash with getLocalDeviceId(). */
    private fun cachedDeviceId(): String? = localDeviceId

    private val binder = object : ILibp2pService.Stub() {
        override fun initializeNode(bootstrapPeers: Array<String>?): Boolean {
            return this@Libp2pService.initializeNode(bootstrapPeers ?: emptyArray())
        }

        override fun getLocalPeerId(): String? {
            if (nodeHandle == 0L) return null
            // Do not read the outer property 'localPeerId' here: it would resolve to this getter
            // and cause StackOverflowError. Call native only.
            return Libp2pNative.cabiNodeLocalPeerId(nodeHandle)
        }

        override fun getLocalAccountId(): String? = this@Libp2pService.cachedAccountId()

        override fun getLocalDeviceId(): String? = this@Libp2pService.cachedDeviceId()

        override fun listen(address: String?): Boolean {
            if (nodeHandle == 0L || address == null) return false
            val result = Libp2pNative.cabiNodeListen(nodeHandle, address)
            return result == Libp2pNative.STATUS_SUCCESS
        }

        override fun dial(address: String?): Boolean {
            if (nodeHandle == 0L || address == null) return false
            val result = Libp2pNative.cabiNodeDial(nodeHandle, address)
            return result == Libp2pNative.STATUS_SUCCESS
        }

        override fun lookupAndDial(identifier: String?): Boolean {
            if (nodeHandle == 0L || identifier.isNullOrBlank()) return false
            return this@Libp2pService.lookupAndDial(identifier.trim())
        }

        override fun setActiveRecipient(identifier: String?): Boolean {
            if (nodeHandle == 0L || identifier.isNullOrBlank()) return false
            val resolvedPeerId = this@Libp2pService.resolvePeerId(identifier.trim()) ?: return false
            activeRecipientPeerId = resolvedPeerId
            this@Libp2pService.prefetchRecipientPrekey(resolvedPeerId)
            return true
        }

        override fun sendMessage(message: ByteArray?): Boolean {
            if (nodeHandle == 0L || message == null) return false
            val result = Libp2pNative.cabiNodeEnqueueMessage(nodeHandle, message)
            return result == Libp2pNative.STATUS_SUCCESS
        }

        override fun receiveMessage(): ByteArray? {
            return if (nodeHandle != 0L) {
                Libp2pNative.cabiNodeDequeueMessage(nodeHandle)
            } else null
        }

        override fun sendEncryptedMessage(plaintext: String?): Boolean {
            if (nodeHandle == 0L) {
                Log.w(TAG, "sendEncryptedMessage failed: node not initialized")
                return false
            }
            val toPeerId = activeRecipientPeerId
            if (toPeerId == null) {
                Log.w(TAG, "sendEncryptedMessage failed: no active recipient set")
                return false
            }
            val profile = profilePath
            if (profile == null) {
                Log.w(TAG, "sendEncryptedMessage failed: no profile path")
                return false
            }
            val fromPeerId = Libp2pNative.cabiNodeLocalPeerId(nodeHandle)
            if (fromPeerId == null) {
                Log.w(TAG, "sendEncryptedMessage failed: could not get local peer id")
                return false
            }

            val prekeyBundle = this@Libp2pService.getOrFetchRecipientPrekeyBundle(toPeerId)
            if (prekeyBundle == null) {
                Log.w(TAG, "sendEncryptedMessage failed: no prekey bundle for peer_id=$toPeerId (DHT lookup failed)")
                return false
            }

            val encrypted = Libp2pNative.cabiE2eeBuildMessageAuto(
                profilePath = profile,
                recipientPrekeyBundle = prekeyBundle,
                plaintext = (plaintext ?: "").toByteArray(StandardCharsets.UTF_8),
                aad = ByteArray(0),
            )
            if (encrypted == null) {
                Log.w(TAG, "sendEncryptedMessage failed: cabiE2eeBuildMessageAuto returned null for peer_id=$toPeerId")
                return false
            }

            val packet = JSONObject().apply {
                put("schema", "fidonext-chat-v1")
                put("message_id", UUID.randomUUID().toString().replace("-", ""))
                put("created_at_unix", System.currentTimeMillis() / 1000L)
                put("from_peer_id", fromPeerId)
                put("to_peer_id", toPeerId)
                put("payload_type", "libsignal")
                put("payload_b64", android.util.Base64.encodeToString(encrypted, android.util.Base64.NO_WRAP))
            }

            val bytes = packet.toString().toByteArray(StandardCharsets.UTF_8)
            val status = Libp2pNative.cabiNodeEnqueueMessage(nodeHandle, bytes)
            if (status != Libp2pNative.STATUS_SUCCESS) {
                Log.w(TAG, "sendEncryptedMessage failed: cabiNodeEnqueueMessage returned $status")
                return false
            }
            return true
        }

        override fun receiveDecryptedMessage(): String? {
            if (nodeHandle == 0L) return null
            val profile = profilePath ?: return null
            val payload = Libp2pNative.cabiNodeDequeueMessage(nodeHandle) ?: return null
            return this@Libp2pService.tryDecryptChatPacket(profile, payload)
        }

        override fun getAutonatStatus(): Int {
            return if (nodeHandle != 0L) {
                Libp2pNative.cabiAutonatStatus(nodeHandle)
            } else Libp2pNative.AUTONAT_UNKNOWN
        }

        override fun isHealthy(): Boolean {
            val now = System.currentTimeMillis()
            lastHealthCheck.set(now)
            return nodeHandle != 0L && isRunning.get()
        }

        override fun getDiscoveredPeers(): Array<String> {
            return this@Libp2pService.getDiscoveredPeers()
        }
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Service created")

        // Initialize tracing
        Libp2pNative.cabiInitTracing()

        createNotificationChannel()
        startForeground(NOTIFICATION_ID, createNotification())

        // Start health monitoring
        startHealthMonitoring()
        startPeriodicReannounce()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "Service started")
        return START_STICKY // Restart service if killed
    }

    override fun onBind(intent: Intent?): IBinder {
        return binder
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "Service destroyed")

        isRunning.set(false)
        serviceScope.cancel()

        if (nodeHandle != 0L) {
            Libp2pNative.cabiNodeFree(nodeHandle)
            nodeHandle = 0
        }
    }

    private fun initializeNode(bootstrapPeers: Array<String>): Boolean {
        return try {
            if (nodeHandle != 0L) {
                Log.w(TAG, "Node already initialized")
                return true
            }

            Log.d(TAG, "Initializing node with ${bootstrapPeers.size} bootstrap peers")
            lastBootstrapPeers = bootstrapPeers

            // Mirror upstream python examples: stable profile -> identity seeds -> node_new(identity_seed)
            val profile = java.io.File(filesDir, "fidonext.profile.json").absolutePath
            profilePath = profile
            val identity = Libp2pNative.cabiIdentityLoadOrCreate(profile)
            if (identity == null || identity.libp2pSeed.size != 32) {
                Log.e(TAG, "Failed to load/create identity profile")
                return false
            }
            localAccountId = identity.accountId
            localDeviceId = identity.deviceId

            nodeHandle = Libp2pNative.cabiNodeNewWithSeed(
                useQuic = false,
                enableRelayHop = false,
                bootstrapPeers = bootstrapPeers,
                identitySeed = identity.libp2pSeed
            )

            if (nodeHandle == 0L) {
                Log.e(TAG, "Failed to create node")
                return false
            }

            isRunning.set(true)

            val peerId = Libp2pNative.cabiNodeLocalPeerId(nodeHandle)
            localPeerId = peerId
            Log.i(TAG, "Node initialized successfully. Peer ID: $peerId, accountId=$localAccountId, deviceId=$localDeviceId")

            // Start listening
            val listenAddr = "/ip4/0.0.0.0/tcp/0"
            val listenResult = Libp2pNative.cabiNodeListen(nodeHandle, listenAddr)
            if (listenResult == Libp2pNative.STATUS_SUCCESS) {
                Log.i(TAG, "Started listening on $listenAddr")
            } else {
                Log.w(TAG, "Failed to start listening: $listenResult")
            }

            // Dial bootstrap peers
            var connectedToRelay = false
            for (peer in bootstrapPeers) {
                if (peer.isNotBlank()) {
                    Log.d(TAG, "Dialing bootstrap peer: $peer")
                    val dialResult = Libp2pNative.cabiNodeDial(nodeHandle, peer)
                    if (dialResult == Libp2pNative.STATUS_SUCCESS) {
                        Log.i(TAG, "Successfully dialed bootstrap peer: $peer")
                        connectedToRelay = true
                    } else {
                        Log.w(TAG, "Failed to dial bootstrap peer $peer: $dialResult")
                    }
                }
            }

            if (!connectedToRelay) {
                Log.w(TAG, "Warning: Not connected to any bootstrap relay - DHT operations may fail")
            }

            // Wait for DHT to stabilize after connecting (mirrors Rust example line 280: sleep 2s after dial)
            Log.d(TAG, "Waiting 3s for DHT connection to stabilize...")
            Thread.sleep(3000)

            // Test DHT read/write capability before announcing
            val testKey = "fidonext/test/${UUID.randomUUID()}".toByteArray(StandardCharsets.UTF_8)
            val testValue = "test".toByteArray(StandardCharsets.UTF_8)
            val putStatus = Libp2pNative.cabiNodeDhtPutRecord(nodeHandle, testKey, testValue, 60L)
            if (putStatus == Libp2pNative.STATUS_SUCCESS) {
                Log.d(TAG, "DHT write test: SUCCESS")
                Thread.sleep(1000) // Allow propagation
                val retrieved = Libp2pNative.cabiNodeDhtGetRecord(nodeHandle, testKey)
                if (retrieved != null && retrieved.contentEquals(testValue)) {
                    Log.i(TAG, "DHT read/write test: SUCCESS - DHT is functional")
                } else {
                    Log.w(TAG, "DHT read/write test: FAILED - wrote successfully but read returned: ${retrieved?.size ?: 0} bytes")
                }
            } else {
                Log.e(TAG, "DHT write test: FAILED with status $putStatus - DHT may not be working!")
            }

            // Publish directory + prekey records to DHT (like fidonext_chat_client.py).
            val announceOk = announceSelf()
            if (announceOk) {
                Log.i(TAG, "Initial DHT announce succeeded")
                // Verify we can read our own record back
                Thread.sleep(1000)
                val selfTest = Libp2pNative.cabiNodeDhtGetRecord(nodeHandle, prekeyKeyForPeer(localPeerId!!))
                if (selfTest != null) {
                    Log.i(TAG, "DHT self-verification: Can read own prekey record (${selfTest.size} bytes)")
                } else {
                    Log.w(TAG, "DHT self-verification: Cannot read own prekey record - DHT may be isolated!")
                }
            } else {
                Log.w(TAG, "Initial DHT announce failed - will retry in periodic re-announce")
            }

            true
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing node", e)
            false
        }
    }

    private fun directoryKeyForPeer(peerId: String): ByteArray =
        "fidonext/directory/v1/peer/$peerId".toByteArray(StandardCharsets.UTF_8)

    private fun directoryKeyForAccount(accountId: String): ByteArray =
        "fidonext/directory/v1/account/$accountId".toByteArray(StandardCharsets.UTF_8)

    private fun prekeyKeyForPeer(peerId: String): ByteArray =
        "fidonext/prekey/v1/peer/$peerId".toByteArray(StandardCharsets.UTF_8)

    private fun prekeyKeyForAccount(accountId: String): ByteArray =
        "fidonext/prekey/v1/account/$accountId".toByteArray(StandardCharsets.UTF_8)

    private fun announceSelf(): Boolean {
        val handle = nodeHandle
        val peerId = localPeerId ?: run {
            Log.w(TAG, "announceSelf: localPeerId is null")
            return false
        }
        val accountId = localAccountId ?: run {
            Log.w(TAG, "announceSelf: localAccountId is null")
            return false
        }
        val deviceId = localDeviceId ?: run {
            Log.w(TAG, "announceSelf: localDeviceId is null")
            return false
        }
        val profile = profilePath ?: run {
            Log.w(TAG, "announceSelf: profilePath is null")
            return false
        }

        val directoryCard = JSONObject().apply {
            put("schema", "fidonext-directory-v1")
            put("updated_at_unix", System.currentTimeMillis() / 1000L)
            put("peer_id", peerId)
            put("account_id", accountId)
            put("device_id", deviceId)
            // We don't have a reliable externally reachable listen multiaddr here (tcp/0),
            // so we publish an empty address list; peers can still connect via directory+find_peer in future.
            put("addresses", org.json.JSONArray())
        }.toString().toByteArray(StandardCharsets.UTF_8)

        val bundle = Libp2pNative.cabiE2eeBuildPrekeyBundle(
            profilePath = profile,
            oneTimePrekeyCount = 32,
            ttlSeconds = 24 * 60 * 60L,
        ) ?: run {
            Log.e(TAG, "announceSelf: cabiE2eeBuildPrekeyBundle returned null")
            return false
        }

        val prekeyCard = JSONObject().apply {
            put("schema", "fidonext-prekey-bundle-v1")
            put("updated_at_unix", System.currentTimeMillis() / 1000L)
            put("peer_id", peerId)
            put("account_id", accountId)
            put("device_id", deviceId)
            put("bundle_b64", android.util.Base64.encodeToString(bundle, android.util.Base64.NO_WRAP))
        }.toString().toByteArray(StandardCharsets.UTF_8)

        Log.d(TAG, "announceSelf: Publishing DHT records for peer_id=$peerId account_id=$accountId")
        val ok1 = Libp2pNative.cabiNodeDhtPutRecord(handle, directoryKeyForPeer(peerId), directoryCard, 30 * 60L) == Libp2pNative.STATUS_SUCCESS
        val ok2 = Libp2pNative.cabiNodeDhtPutRecord(handle, directoryKeyForAccount(accountId), directoryCard, 30 * 60L) == Libp2pNative.STATUS_SUCCESS
        val ok3 = Libp2pNative.cabiNodeDhtPutRecord(handle, prekeyKeyForPeer(peerId), prekeyCard, 24 * 60 * 60L) == Libp2pNative.STATUS_SUCCESS
        val ok4 = Libp2pNative.cabiNodeDhtPutRecord(handle, prekeyKeyForAccount(accountId), prekeyCard, 24 * 60 * 60L) == Libp2pNative.STATUS_SUCCESS

        if (ok1 && ok2 && ok3 && ok4) {
            Log.i(TAG, "announceSelf: Successfully published all 4 DHT records (directory x2, prekey x2)")
            return true
        } else {
            Log.w(TAG, "announceSelf: DHT publish partial failure - directory_peer=$ok1 directory_account=$ok2 prekey_peer=$ok3 prekey_account=$ok4")
            return false
        }
    }

    private fun resolvePeerId(identifier: String): String? {
        // If it's already a peer id (heuristic), use it.
        if (identifier.startsWith("12D3") || identifier.startsWith("Qm")) return identifier
        val handle = nodeHandle
        val raw = Libp2pNative.cabiNodeDhtGetRecord(handle, directoryKeyForAccount(identifier))
            ?: Libp2pNative.cabiNodeDhtGetRecord(handle, directoryKeyForPeer(identifier))
            ?: return null
        return try {
            val card = JSONObject(String(raw, StandardCharsets.UTF_8))
            card.optString("peer_id").takeIf { it.isNotBlank() }
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Get addresses from DHT directory card for peer_id or account_id, if present.
     */
    private fun getDirectoryAddresses(identifier: String): List<String> {
        val handle = nodeHandle
        val raw = Libp2pNative.cabiNodeDhtGetRecord(handle, directoryKeyForAccount(identifier))
            ?: Libp2pNative.cabiNodeDhtGetRecord(handle, directoryKeyForPeer(identifier))
            ?: return emptyList()
        return try {
            val card = JSONObject(String(raw, StandardCharsets.UTF_8))
            val arr = card.optJSONArray("addresses") ?: return emptyList()
            (0 until arr.length()).mapNotNull { i ->
                arr.optString(i).takeIf { it.isNotBlank() }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    /**
     * Resolve peer to dialable addresses via Kademlia find_peer and discovery event queue.
     * Mirrors Python resolve_peer_addresses.
     */
    private fun resolvePeerAddresses(peerId: String, timeoutMs: Long): List<String> {
        val handle = nodeHandle
        val requestId = Libp2pNative.cabiNodeFindPeer(handle, peerId)
        if (requestId == 0L) {
            Log.w(TAG, "find_peer failed for peer_id=$peerId")
            return emptyList()
        }
        val deadline = System.currentTimeMillis() + timeoutMs.coerceAtLeast(1000L)
        val addresses = mutableListOf<String>()
        while (System.currentTimeMillis() < deadline) {
            val event = Libp2pNative.cabiNodeDequeueDiscoveryEvent(handle)
            if (event == null) {
                Thread.sleep(100)
                continue
            }
            if (event.requestId != requestId) continue
            when (event.eventKind) {
                Libp2pNative.DISCOVERY_EVENT_ADDRESS -> {
                    val addr = event.address.trim()
                    if (addr.isNotEmpty() && addr !in addresses) addresses.add(addr)
                }
                Libp2pNative.DISCOVERY_EVENT_FINISHED -> {
                    Log.d(TAG, "Discovery finished for request_id=$requestId status=${event.statusCode}")
                    break
                }
            }
        }
        return addresses
    }

    /**
     * Extract peer_id from multiaddr (e.g. /ip4/.../p2p/12D3Koo... -> 12D3Koo...).
     */
    private fun peerIdFromMultiaddr(multiaddr: String): String? {
        val p2p = "/p2p/"
        val idx = multiaddr.indexOf(p2p)
        if (idx < 0) return null
        return multiaddr.substring(idx + p2p.length).trim().takeIf { it.isNotEmpty() }
    }

    /**
     * Run get_closest_peers for one target and collect peer IDs until deadline or FINISHED.
     */
    private fun collectClosestPeers(handle: Long, targetPeerId: String, excludePeerId: String?, deadlineMs: Long): Set<String> {
        val requestId = Libp2pNative.cabiNodeGetClosestPeers(handle, targetPeerId)
        if (requestId == 0L) return emptySet()
        val result = mutableSetOf<String>()
        while (System.currentTimeMillis() < deadlineMs) {
            val event = Libp2pNative.cabiNodeDequeueDiscoveryEvent(handle)
            if (event == null) {
                Thread.sleep(60)
                continue
            }
            if (event.requestId != requestId) continue
            when (event.eventKind) {
                Libp2pNative.DISCOVERY_EVENT_ADDRESS -> {
                    val pid = event.peerId?.trim() ?: ""
                    if (pid.isNotEmpty() && pid != excludePeerId) result.add(pid)
                }
                Libp2pNative.DISCOVERY_EVENT_FINISHED -> break
            }
        }
        return result
    }

    /**
     * Discover peers via DHT: get_closest_peers(self) and get_closest_peers(bootstrap relay).
     * Returns peer_id strings excluding our own. Blocks for up to ~30s (longer timeout for relay/NAT).
     * Mirrors Python resolve_peer_addresses timeout of 12s + Rust discovery patterns.
     */
    private fun getDiscoveredPeers(discoveryTimeoutMs: Long = 15_000L): Array<String> {
        val handle = nodeHandle
        val me = localPeerId
        if (handle == 0L || me.isNullOrBlank()) {
            Log.w(TAG, "getDiscoveredPeers: nodeHandle or localPeerId invalid")
            return emptyArray()
        }
        val all = mutableSetOf<String>()
        // 1) Peers close to us (longer timeout - examples show DHT can be slow behind relay)
        val deadline1 = System.currentTimeMillis() + discoveryTimeoutMs.coerceAtLeast(10_000L)
        Log.d(TAG, "getDiscoveredPeers: querying closest peers to self ($me)")
        all.addAll(collectClosestPeers(handle, me, me, deadline1))
        Log.d(TAG, "getDiscoveredPeers: found ${all.size} peers close to self")

        // 2) Peers close to each bootstrap relay (increases chance to see other clients via same relay)
        for (multiaddr in lastBootstrapPeers) {
            val relayPeerId = peerIdFromMultiaddr(multiaddr) ?: continue
            if (relayPeerId == me) continue
            val deadline2 = System.currentTimeMillis() + 10_000L
            Log.d(TAG, "getDiscoveredPeers: querying closest peers to relay ($relayPeerId)")
            val relayPeers = collectClosestPeers(handle, relayPeerId, me, deadline2)
            Log.d(TAG, "getDiscoveredPeers: found ${relayPeers.size} peers close to relay")
            all.addAll(relayPeers)
        }
        Log.d(TAG, "getDiscoveredPeers: total found ${all.size} peers")
        return all.toTypedArray()
    }

    private fun lookupAndDial(identifier: String): Boolean {
        val handle = nodeHandle
        // Direct multiaddr: dial immediately.
        if (identifier.startsWith("/")) {
            val ok = Libp2pNative.cabiNodeDial(handle, identifier) == Libp2pNative.STATUS_SUCCESS
            if (ok) Log.i(TAG, "Dialed multiaddr: $identifier")
            return ok
        }
        // Resolve identifier to peer_id (directory or treat as peer_id).
        val peerId = resolvePeerId(identifier) ?: run {
            Log.w(TAG, "Could not resolve peer_id for identifier=$identifier")
            return false
        }
        // 1) Try directory card addresses first (e.g. from Python client that publishes listen addr).
        val directoryAddrs = getDirectoryAddresses(identifier) + getDirectoryAddresses(peerId)
        for (addr in directoryAddrs.distinct()) {
            if (Libp2pNative.cabiNodeDial(handle, addr) == Libp2pNative.STATUS_SUCCESS) {
                Log.i(TAG, "Dialed via directory: $addr")
                return true
            }
        }
        // 2) Resolve via find_peer + discovery events, then dial (longer timeout for DHT behind NAT/relay).
        val discoveredAddrs = resolvePeerAddresses(peerId, 25_000L)
        for (addr in discoveredAddrs) {
            if (Libp2pNative.cabiNodeDial(handle, addr) == Libp2pNative.STATUS_SUCCESS) {
                Log.i(TAG, "Dialed via discovery: $addr")
                return true
            }
        }
        // 3) Fallback: dial target via bootstrap relay (p2p-circuit). Both peers must use the same relay.
        for (bootstrap in lastBootstrapPeers) {
            if (bootstrap.isBlank()) continue
            val circuitAddr = buildCircuitAddr(bootstrap, peerId) ?: continue
            if (Libp2pNative.cabiNodeDial(handle, circuitAddr) == Libp2pNative.STATUS_SUCCESS) {
                Log.i(TAG, "Dialed via relay circuit: $circuitAddr")
                return true
            }
        }
        Log.w(TAG, "lookupAndDial: no dialable address for peer_id=$peerId (dir=${directoryAddrs.size}, discovered=${discoveredAddrs.size})")
        return false
    }

    /** Build libp2p circuit relay addr: relay_multiaddr/p2p-circuit/p2p/dest_peer_id */
    private fun buildCircuitAddr(relayMultiaddr: String, destPeerId: String): String? {
        val trimmed = relayMultiaddr.trim()
        if (trimmed.isEmpty() || destPeerId.isBlank()) return null
        return "$trimmed/p2p-circuit/p2p/$destPeerId"
    }

    /** Get account_id from DHT directory card for peer_id (mirrors Python lookup_directory). */
    private fun getAccountIdFromDirectory(peerId: String): String? {
        val handle = nodeHandle
        val raw = Libp2pNative.cabiNodeDhtGetRecord(handle, directoryKeyForPeer(peerId))
            ?: Libp2pNative.cabiNodeDhtGetRecord(handle, directoryKeyForAccount(peerId))
            ?: return null
        return try {
            val card = JSONObject(String(raw, StandardCharsets.UTF_8))
            if (card.optString("schema") != "fidonext-directory-v1") return null
            card.optString("account_id").takeIf { it.isNotBlank() }
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Fetch prekey bundle from DHT. Tries peer_id then account_id (from directory) like Python lookup_prekey_bundle.
     */
    private fun fetchRecipientPrekeyBundle(identifier: String): ByteArray? {
        fun tryFetch(id: String, label: String): ByteArray? {
            val handle = nodeHandle
            Log.d(TAG, "fetchRecipientPrekeyBundle: trying DHT lookup for $label=$id")
            val rawPeer = Libp2pNative.cabiNodeDhtGetRecord(handle, prekeyKeyForPeer(id))
            val rawAccount = if (rawPeer == null) {
                Libp2pNative.cabiNodeDhtGetRecord(handle, prekeyKeyForAccount(id))
            } else null
            val raw = rawPeer ?: rawAccount
            if (raw == null) {
                Log.d(TAG, "fetchRecipientPrekeyBundle: DHT returned null for $label=$id (checked peer and account keys)")
                return null
            }
            return try {
                val card = JSONObject(String(raw, StandardCharsets.UTF_8))
                if (card.optString("schema") != "fidonext-prekey-bundle-v1") {
                    Log.w(TAG, "fetchRecipientPrekeyBundle: invalid schema in DHT record for $label=$id")
                    return null
                }
                val bundleB64 = card.optString("bundle_b64")
                if (bundleB64.isNullOrBlank()) {
                    Log.w(TAG, "fetchRecipientPrekeyBundle: empty bundle_b64 in DHT record for $label=$id")
                    return null
                }
                val bundle = android.util.Base64.decode(bundleB64, android.util.Base64.DEFAULT)
                val status = Libp2pNative.cabiE2eeValidatePrekeyBundle(bundle, 0L)
                if (status == Libp2pNative.STATUS_SUCCESS) {
                    Log.d(TAG, "fetchRecipientPrekeyBundle: Successfully fetched and validated bundle for $label=$id")
                    bundle
                } else {
                    Log.w(TAG, "fetchRecipientPrekeyBundle: bundle validation failed for $label=$id status=$status")
                    null
                }
            } catch (e: Exception) {
                Log.w(TAG, "fetchRecipientPrekeyBundle: exception parsing DHT record for $label=$id", e)
                null
            }
        }
        tryFetch(identifier, "identifier")?.let { return it }
        if (identifier.startsWith("12D3") || identifier.startsWith("Qm")) {
            val accountId = getAccountIdFromDirectory(identifier)
            if (accountId != null) {
                Log.d(TAG, "fetchRecipientPrekeyBundle: resolved peer_id=$identifier to account_id=$accountId, trying account lookup")
                tryFetch(accountId, "account_id")?.let { return it }
            } else {
                Log.d(TAG, "fetchRecipientPrekeyBundle: could not resolve peer_id=$identifier to account_id from directory")
            }
        }
        Log.w(TAG, "fetchRecipientPrekeyBundle: all DHT lookups failed for identifier=$identifier")
        return null
    }

    /**
     * Fetch recipient prekey with retries to allow DHT propagation after dialing.
     * The other peer publishes their prekey on startup; it may take several seconds to be visible.
     * Mirrors Python _ensure_contact_prekey_bundle retry behavior.
     */
    private fun fetchRecipientPrekeyBundleWithRetry(
        identifier: String,
        maxAttempts: Int = 8,
        delayBetweenAttemptsMs: Long = 2500L
    ): ByteArray? {
        for (attempt in 1..maxAttempts) {
            val bundle = fetchRecipientPrekeyBundle(identifier)
            if (bundle != null) return bundle
            if (attempt < maxAttempts) {
                Log.d(TAG, "Prekey not found for $identifier (attempt $attempt/$maxAttempts), retrying in ${delayBetweenAttemptsMs}ms...")
                Thread.sleep(delayBetweenAttemptsMs)
            }
        }
        Log.w(TAG, "Prekey bundle not found after $maxAttempts attempts for $identifier")
        return null
    }

    /**
     * Send prekey bundle request to peer. The peer will respond with their bundle via gossipsub.
     */
    private fun requestPrekeyBundle(peerId: String): Boolean {
        val handle = nodeHandle
        val myPeerId = localPeerId ?: return false
        val profile = profilePath ?: return false

        // Build our own prekey bundle to send
        val myBundle = Libp2pNative.cabiE2eeBuildPrekeyBundle(
            profilePath = profile,
            oneTimePrekeyCount = 32,
            ttlSeconds = 24 * 60 * 60L,
        ) ?: return false

        val requestPacket = JSONObject().apply {
            put("schema", "fidonext-prekey-exchange-v1")
            put("type", "request")
            put("from_peer_id", myPeerId)
            put("to_peer_id", peerId)
            put("my_bundle_b64", android.util.Base64.encodeToString(myBundle, android.util.Base64.NO_WRAP))
            put("timestamp", System.currentTimeMillis() / 1000L)
        }.toString().toByteArray(StandardCharsets.UTF_8)

        val status = Libp2pNative.cabiNodeEnqueueMessage(handle, requestPacket)
        return status == Libp2pNative.STATUS_SUCCESS
    }

    /**
     * Prefetch recipient prekey when opening chat (mirrors Python _ensure_contact_prekey_bundle).
     * First tries DHT, then falls back to direct prekey exchange via gossipsub.
     * Fills recipientPrekeyCache so the first send can use it. Runs on serviceScope to avoid blocking binder.
     */
    private fun prefetchRecipientPrekey(peerId: String) {
        recipientPrekeyCache.remove(peerId)
        serviceScope.launch(Dispatchers.IO) {
            // Try DHT first (works if both peers are on public DHT)
            var bundle = fetchRecipientPrekeyBundleWithRetry(peerId, maxAttempts = 2, delayBetweenAttemptsMs = 1500L)
            if (bundle != null) {
                recipientPrekeyCache[peerId] = bundle
                Log.i(TAG, "Prefetched prekey from DHT for peer_id=$peerId")
                return@launch
            }

            // DHT failed - request prekey bundle directly via gossipsub
            Log.d(TAG, "DHT lookup failed, requesting prekey bundle directly from peer $peerId")
            val requestOk = requestPrekeyBundle(peerId)
            if (!requestOk) {
                Log.w(TAG, "Failed to send prekey bundle request to peer $peerId")
                return@launch
            }

            // Wait for response (will be handled by message receiver and cached)
            delay(5000L)
            if (recipientPrekeyCache.containsKey(peerId)) {
                Log.i(TAG, "Received prekey bundle via direct exchange for peer $peerId")
            } else {
                Log.w(TAG, "No prekey bundle response received from peer $peerId after 5s")
            }
        }
    }

    /**
     * Get recipient prekey: use cache if available, else fetch with retry and cache (mirrors Python send flow).
     */
    private fun getOrFetchRecipientPrekeyBundle(peerId: String): ByteArray? {
        recipientPrekeyCache[peerId]?.let { return it }
        val bundle = fetchRecipientPrekeyBundleWithRetry(peerId)
        if (bundle != null) recipientPrekeyCache[peerId] = bundle
        return bundle
    }

    /**
     * Handle prekey exchange messages (request/response) to bypass DHT for isolated networks.
     * Returns true if message was handled (prekey exchange), false if it should be processed normally.
     */
    private fun tryHandlePrekeyExchange(payload: ByteArray): Boolean {
        val local = localPeerId ?: return false
        val profile = profilePath ?: return false
        try {
            val packet = JSONObject(String(payload, StandardCharsets.UTF_8))
            if (packet.optString("schema") != "fidonext-prekey-exchange-v1") return false

            val type = packet.optString("type")
            val fromPeerId = packet.optString("from_peer_id")
            val toPeerId = packet.optString("to_peer_id")

            if (toPeerId != local) return true // Not for us, but still a prekey exchange message

            when (type) {
                "request" -> {
                    // Peer is requesting our prekey and sent theirs
                    Log.d(TAG, "Received prekey bundle request from $fromPeerId")

                    // Cache their bundle
                    val theirBundleB64 = packet.optString("my_bundle_b64")
                    if (theirBundleB64.isNotBlank()) {
                        val theirBundle = android.util.Base64.decode(theirBundleB64, android.util.Base64.DEFAULT)
                        val valid = Libp2pNative.cabiE2eeValidatePrekeyBundle(theirBundle, 0L) == Libp2pNative.STATUS_SUCCESS
                        if (valid) {
                            recipientPrekeyCache[fromPeerId] = theirBundle
                            Log.i(TAG, "Cached prekey bundle from $fromPeerId via direct exchange")
                        }
                    }

                    // Send our bundle in response
                    val myBundle = Libp2pNative.cabiE2eeBuildPrekeyBundle(profile, 32, 24 * 60 * 60L)
                    if (myBundle != null) {
                        val responsePacket = JSONObject().apply {
                            put("schema", "fidonext-prekey-exchange-v1")
                            put("type", "response")
                            put("from_peer_id", local)
                            put("to_peer_id", fromPeerId)
                            put("my_bundle_b64", android.util.Base64.encodeToString(myBundle, android.util.Base64.NO_WRAP))
                            put("timestamp", System.currentTimeMillis() / 1000L)
                        }.toString().toByteArray(StandardCharsets.UTF_8)
                        Libp2pNative.cabiNodeEnqueueMessage(nodeHandle, responsePacket)
                        Log.d(TAG, "Sent prekey bundle response to $fromPeerId")
                    }
                    return true
                }
                "response" -> {
                    // Peer sent their prekey in response to our request
                    Log.d(TAG, "Received prekey bundle response from $fromPeerId")
                    val theirBundleB64 = packet.optString("my_bundle_b64")
                    if (theirBundleB64.isNotBlank()) {
                        val theirBundle = android.util.Base64.decode(theirBundleB64, android.util.Base64.DEFAULT)
                        val valid = Libp2pNative.cabiE2eeValidatePrekeyBundle(theirBundle, 0L) == Libp2pNative.STATUS_SUCCESS
                        if (valid) {
                            recipientPrekeyCache[fromPeerId] = theirBundle
                            Log.i(TAG, "Cached prekey bundle from $fromPeerId via direct exchange")
                        } else {
                            Log.w(TAG, "Received invalid prekey bundle from $fromPeerId")
                        }
                    }
                    return true
                }
            }
        } catch (e: Exception) {
            Log.d(TAG, "Not a prekey exchange message or parse error", e)
        }
        return false
    }

    private fun tryDecryptChatPacket(profile: String, payload: ByteArray): String? {
        val local = localPeerId ?: return null

        // First check if this is a prekey exchange message
        if (tryHandlePrekeyExchange(payload)) {
            return null // Handled as prekey exchange, don't process as chat
        }

        return try {
            val packet = JSONObject(String(payload, StandardCharsets.UTF_8))
            if (packet.optString("schema") != "fidonext-chat-v1") return null
            if (packet.optString("payload_type") != "libsignal") return null
            val toPeerId = packet.optString("to_peer_id")
            if (toPeerId != local) return null
            val fromPeerId = packet.optString("from_peer_id")
            val encryptedB64 = packet.optString("payload_b64")
            val encrypted = android.util.Base64.decode(encryptedB64, android.util.Base64.DEFAULT)
            val decrypted = Libp2pNative.cabiE2eeDecryptMessageAuto(profile, encrypted) ?: return null
            val kindName = when (decrypted.kind) {
                Libp2pNative.E2EE_MESSAGE_KIND_PREKEY -> "prekey"
                Libp2pNative.E2EE_MESSAGE_KIND_SESSION -> "session"
                else -> "unknown"
            }
            JSONObject().apply {
                put("from_peer_id", fromPeerId)
                put("to_peer_id", toPeerId)
                put("kind", kindName)
                put("text", String(decrypted.plaintext, StandardCharsets.UTF_8))
            }.toString()
        } catch (_: Exception) {
            null
        }
    }

    private fun startHealthMonitoring() {
        serviceScope.launch {
            while (isActive) {
                delay(HEALTH_CHECK_INTERVAL_MS)
                performHealthCheck()
            }
        }
    }

    /** Re-announce directory+prekey to DHT periodically (mirrors Python fidonext_chat_client _announce_loop). */
    private fun startPeriodicReannounce() {
        serviceScope.launch {
            while (isActive) {
                delay(DIRECTORY_REANNOUNCE_INTERVAL_MS)
                if (nodeHandle != 0L && isRunning.get()) {
                    try {
                        val ok = announceSelf()
                        if (ok) Log.d(TAG, "Periodic re-announce: directory+prekey published to DHT")
                    } catch (e: Exception) {
                        Log.w(TAG, "Periodic re-announce failed", e)
                    }
                }
            }
        }
    }

    private fun performHealthCheck() {
        try {
            if (nodeHandle == 0L) {
                Log.w(TAG, "Health check failed: Node not initialized")
                return
            }

            // Check if node is still responsive
            val status = Libp2pNative.cabiAutonatStatus(nodeHandle)
            Log.d(TAG, "Health check passed. AutoNAT status: $status")

            // Update notification with status
            val notification = createNotification()
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager?.notify(NOTIFICATION_ID, notification)

        } catch (e: Exception) {
            Log.e(TAG, "Health check failed", e)
            restartNode()
        }
    }

    private fun restartNode() {
        Log.w(TAG, "Attempting to restart node...")

        if (nodeHandle != 0L) {
            try {
                Libp2pNative.cabiNodeFree(nodeHandle)
            } catch (e: Exception) {
                Log.e(TAG, "Error freeing node during restart", e)
            }
            nodeHandle = 0
        }

        isRunning.set(false)

        // Wait a bit before restarting
        val peersToRestore = lastBootstrapPeers
        serviceScope.launch {
            delay(2000)
            initializeNode(peersToRestore)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "FidoNext Networking Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Keeps FidoNext networking running in the background"
            }

            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager?.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        val statusText = if (nodeHandle != 0L) {
            val autonatStatus = try {
                when (Libp2pNative.cabiAutonatStatus(nodeHandle)) {
                    Libp2pNative.AUTONAT_PUBLIC -> "Public"
                    Libp2pNative.AUTONAT_PRIVATE -> "Private"
                    else -> "Unknown"
                }
            } catch (e: Exception) {
                "Error"
            }
            "Running • Status: $autonatStatus"
        } else {
            "Not initialized"
        }

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("FidoNext Messenger")
            .setContentText(statusText)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()
    }
}
