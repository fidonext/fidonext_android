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
            if (nodeHandle == 0L) return false
            val toPeerId = activeRecipientPeerId ?: return false
            val profile = profilePath ?: return false
            val fromPeerId = Libp2pNative.cabiNodeLocalPeerId(nodeHandle) ?: return false

            val prekeyBundle = fetchRecipientPrekeyBundle(toPeerId) ?: run {
                Log.w(TAG, "No recipient prekey bundle available for peer_id=$toPeerId")
                return false
            }

            val encrypted = Libp2pNative.cabiE2eeBuildMessageAuto(
                profilePath = profile,
                recipientPrekeyBundle = prekeyBundle,
                plaintext = (plaintext ?: "").toByteArray(StandardCharsets.UTF_8),
                aad = ByteArray(0),
            ) ?: return false

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
            return status == Libp2pNative.STATUS_SUCCESS
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
            for (peer in bootstrapPeers) {
                if (peer.isNotBlank()) {
                    Log.d(TAG, "Dialing bootstrap peer: $peer")
                    val dialResult = Libp2pNative.cabiNodeDial(nodeHandle, peer)
                    if (dialResult == Libp2pNative.STATUS_SUCCESS) {
                        Log.i(TAG, "Successfully dialed bootstrap peer: $peer")
                    } else {
                        Log.w(TAG, "Failed to dial bootstrap peer $peer: $dialResult")
                    }
                }
            }

            // Publish directory + prekey records to DHT (like fidonext_chat_client.py).
            announceSelf()

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
        val peerId = localPeerId ?: return false
        val accountId = localAccountId ?: return false
        val deviceId = localDeviceId ?: return false
        val profile = profilePath ?: return false

        val directoryCard = JSONObject().apply {
            put("schema", "fidonext-directory-v1")
            put("updated_at_unix", System.currentTimeMillis() / 1000L)
            put("peer_id", peerId)
            put("account_id", accountId)
            put("device_id", deviceId)
            // We don’t have a reliable externally reachable listen multiaddr here (tcp/0),
            // so we publish an empty address list; peers can still connect via directory+find_peer in future.
            put("addresses", org.json.JSONArray())
        }.toString().toByteArray(StandardCharsets.UTF_8)

        val bundle = Libp2pNative.cabiE2eeBuildPrekeyBundle(
            profilePath = profile,
            oneTimePrekeyCount = 32,
            ttlSeconds = 24 * 60 * 60L,
        ) ?: return false

        val prekeyCard = JSONObject().apply {
            put("schema", "fidonext-prekey-bundle-v1")
            put("updated_at_unix", System.currentTimeMillis() / 1000L)
            put("peer_id", peerId)
            put("account_id", accountId)
            put("device_id", deviceId)
            put("bundle_b64", android.util.Base64.encodeToString(bundle, android.util.Base64.NO_WRAP))
        }.toString().toByteArray(StandardCharsets.UTF_8)

        val ok1 = Libp2pNative.cabiNodeDhtPutRecord(handle, directoryKeyForPeer(peerId), directoryCard, 30 * 60L) == Libp2pNative.STATUS_SUCCESS
        val ok2 = Libp2pNative.cabiNodeDhtPutRecord(handle, directoryKeyForAccount(accountId), directoryCard, 30 * 60L) == Libp2pNative.STATUS_SUCCESS
        val ok3 = Libp2pNative.cabiNodeDhtPutRecord(handle, prekeyKeyForPeer(peerId), prekeyCard, 24 * 60 * 60L) == Libp2pNative.STATUS_SUCCESS
        val ok4 = Libp2pNative.cabiNodeDhtPutRecord(handle, prekeyKeyForAccount(accountId), prekeyCard, 24 * 60 * 60L) == Libp2pNative.STATUS_SUCCESS
        return ok1 && ok2 && ok3 && ok4
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
        // 2) Resolve via find_peer + discovery events, then dial.
        val discoveredAddrs = resolvePeerAddresses(peerId, 12_000L)
        for (addr in discoveredAddrs) {
            if (Libp2pNative.cabiNodeDial(handle, addr) == Libp2pNative.STATUS_SUCCESS) {
                Log.i(TAG, "Dialed via discovery: $addr")
                return true
            }
        }
        Log.w(TAG, "lookupAndDial: no dialable address for peer_id=$peerId (dir=${directoryAddrs.size}, discovered=${discoveredAddrs.size})")
        return false
    }

    private fun fetchRecipientPrekeyBundle(identifier: String): ByteArray? {
        val handle = nodeHandle
        val raw = Libp2pNative.cabiNodeDhtGetRecord(handle, prekeyKeyForPeer(identifier))
            ?: Libp2pNative.cabiNodeDhtGetRecord(handle, prekeyKeyForAccount(identifier))
            ?: return null
        return try {
            val card = JSONObject(String(raw, StandardCharsets.UTF_8))
            if (card.optString("schema") != "fidonext-prekey-bundle-v1") return null
            val bundleB64 = card.optString("bundle_b64")
            if (bundleB64.isNullOrBlank()) return null
            val bundle = android.util.Base64.decode(bundleB64, android.util.Base64.DEFAULT)
            val status = Libp2pNative.cabiE2eeValidatePrekeyBundle(bundle, 0L)
            if (status == Libp2pNative.STATUS_SUCCESS) bundle else null
        } catch (_: Exception) {
            null
        }
    }

    private fun tryDecryptChatPacket(profile: String, payload: ByteArray): String? {
        val local = localPeerId ?: return null
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
