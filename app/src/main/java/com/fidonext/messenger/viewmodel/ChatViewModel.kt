package com.fidonext.messenger.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fidonext.messenger.ILibp2pService
import com.fidonext.messenger.config.BootstrapConfig
import com.fidonext.messenger.data.Message
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*

class ChatViewModel : ViewModel() {
    private val _messages = MutableStateFlow<List<Message>>(emptyList())
    val messages: StateFlow<List<Message>> = _messages.asStateFlow()

    private val _connectionStatus = MutableStateFlow("Disconnected")
    val connectionStatus: StateFlow<String> = _connectionStatus.asStateFlow()

    private var messageIdCounter = 0L
    private val dateFormat = SimpleDateFormat("HH:mm", Locale.getDefault())

    private var libp2pService: ILibp2pService? = null
    private var messagePollingJob: Job? = null
    private var connectionStatusPollJob: Job? = null
    private var appContext: Context? = null
    @Volatile
    private var isConnectingToRecipient = false
    private val _activeRecipient = MutableStateFlow<String?>(null)
    val activeRecipient: StateFlow<String?> = _activeRecipient.asStateFlow()

    fun bindService(service: ILibp2pService, context: Context) {
        libp2pService = service
        appContext = context.applicationContext
        _connectionStatus.value = "Connecting..."
        initializeNode()
        startMessagePolling()
        startConnectionStatusPolling()
    }

    fun unbindService() {
        messagePollingJob?.cancel()
        connectionStatusPollJob?.cancel()
        connectionStatusPollJob = null
        libp2pService = null
        appContext = null
        _connectionStatus.value = "Disconnected"
    }

    private fun startConnectionStatusPolling() {
        connectionStatusPollJob?.cancel()
        connectionStatusPollJob = viewModelScope.launch {
            while (isActive) {
                delay(1500)
                if (!isConnectingToRecipient) {
                    val status = try {
                        libp2pService?.getConnectionStatus() ?: "Disconnected"
                    } catch (_: Exception) {
                        "Disconnected"
                    }
                    withContext(Dispatchers.Main) {
                        _connectionStatus.value = status
                    }
                }
            }
        }
    }

    private fun initializeNode() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val bootstrapPeers = appContext?.let { ctx ->
                    BootstrapConfig.loadBootstrapPeers(ctx)
                } ?: BootstrapConfig.getDefaultBootstrapPeers()

                val success = libp2pService?.initializeNode(bootstrapPeers) ?: false
                if (!success) {
                    withContext(Dispatchers.Main) {
                        _connectionStatus.value = "Failed to initialize"
                    }
                }
                // Connection status comes from service getConnectionStatus() (polling)
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    _connectionStatus.value = "Error: ${e.message}"
                }
            }
        }
    }

    private fun startMessagePolling() {
        messagePollingJob = viewModelScope.launch(Dispatchers.IO) {
            while (isActive) {
                try {
                    val decryptedJson = libp2pService?.receiveDecryptedMessage()
                    if (!decryptedJson.isNullOrBlank()) {
                        val obj = JSONObject(decryptedJson)
                        val content = obj.optString("text")
                        val fromPeer = obj.optString("from_peer_id").take(12)
                        val message = Message(
                            id = messageIdCounter++,
                            content = if (fromPeer.isNotBlank()) "[$fromPeer] $content" else content,
                            timestamp = dateFormat.format(Date()),
                            isSent = false,
                            encrypted = true
                        )

                        withContext(Dispatchers.Main) {
                            _messages.value = _messages.value + message
                        }
                    }
                } catch (_: Exception) {
                    // Ignore errors during polling
                }

                delay(100) // Poll every 100ms
            }
        }
    }

    /**
     * Resolve recipient (peer_id or account_id), dial via directory or find_peer discovery, then set as active.
     * Shows "Connecting..." and retries dial to give DHT/relay time (both apps must be open).
     * Mirrors Python/Rust examples: connect, wait for DHT propagation, then fetch prekey.
     */
    fun connectToRecipient(identifier: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val id = identifier.trim()
            if (id.isBlank()) return@launch
            isConnectingToRecipient = true
            withContext(Dispatchers.Main) {
                _connectionStatus.value = "Connecting…"
            }
            val maxAttempts = 2
            var dialOk = false
            for (attempt in 1..maxAttempts) {
                dialOk = libp2pService?.lookupAndDial(id) ?: false
                if (dialOk) break
                if (attempt < maxAttempts) {
                    withContext(Dispatchers.Main) {
                        _connectionStatus.value = "Connecting… (attempt $attempt/$maxAttempts)"
                    }
                    delay(3000L)
                }
            }
            if (!dialOk) {
                isConnectingToRecipient = false
                withContext(Dispatchers.Main) {
                    _activeRecipient.value = null
                    _connectionStatus.value = "Failed to connect. Ensure both apps are open and on same network/relay."
                }
                return@launch
            }
            // Wait for DHT to propagate prekey records (Rust example: 2s after dial, Python: retry fetches)
            // Increase to 5s to give more time for DHT propagation through relay
            withContext(Dispatchers.Main) {
                _connectionStatus.value = "Connected, fetching encryption keys…"
            }
            delay(5000L)
            val setOk = libp2pService?.setActiveRecipient(id) ?: false
            isConnectingToRecipient = false
            withContext(Dispatchers.Main) {
                _activeRecipient.value = if (setOk) id else null
                _connectionStatus.value = libp2pService?.getConnectionStatus() ?: "Disconnected"
            }
        }
    }

    fun setActiveRecipient(identifier: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val ok = libp2pService?.setActiveRecipient(identifier) ?: false
            withContext(Dispatchers.Main) {
                _activeRecipient.value = if (ok) identifier else null
                if (!ok) _connectionStatus.value = "Recipient not found: $identifier"
            }
        }
    }

    fun sendMessage(content: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                // Add sent message to UI
                val message = Message(
                    id = messageIdCounter++,
                    content = content,
                    timestamp = dateFormat.format(Date()),
                    isSent = true,
                    encrypted = true
                )

                withContext(Dispatchers.Main) {
                    _messages.value = _messages.value + message
                }

                // Send through libp2p (E2EE via libcabi_rust_libp2p)
                val success = libp2pService?.sendEncryptedMessage(content) ?: false
                if (!success) {
                    withContext(Dispatchers.Main) {
                        _connectionStatus.value = "Send failed. Both apps must be open on same relay; wait 10–15s after connecting, then retry."
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    _connectionStatus.value = "Error: ${e.message}"
                }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        unbindService()
    }
}
