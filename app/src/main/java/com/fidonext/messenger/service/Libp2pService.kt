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

    private val binder = object : ILibp2pService.Stub() {
        override fun initializeNode(bootstrapPeers: Array<String>?): Boolean {
            return this@Libp2pService.initializeNode(bootstrapPeers ?: emptyArray())
        }

        override fun getLocalPeerId(): String? {
            return if (nodeHandle != 0L) {
                Libp2pNative.cabiNodeLocalPeerId(nodeHandle)
            } else null
        }

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

            nodeHandle = Libp2pNative.cabiNodeNew(
                privateKeyBase64 = null, // Generate new key
                bootstrapPeers = bootstrapPeers
            )

            if (nodeHandle == 0L) {
                Log.e(TAG, "Failed to create node")
                return false
            }

            isRunning.set(true)

            val peerId = Libp2pNative.cabiNodeLocalPeerId(nodeHandle)
            Log.i(TAG, "Node initialized successfully. Peer ID: $peerId")

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

            true
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing node", e)
            false
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
        serviceScope.launch {
            delay(2000)
            // Re-initialize with empty bootstrap peers
            // In production, you'd want to persist and restore the bootstrap peers
            initializeNode(emptyArray())
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
