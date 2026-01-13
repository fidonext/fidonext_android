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
    private var appContext: Context? = null

    fun bindService(service: ILibp2pService, context: Context) {
        libp2pService = service
        appContext = context.applicationContext
        initializeNode()
        startMessagePolling()
    }

    fun unbindService() {
        messagePollingJob?.cancel()
        libp2pService = null
        appContext = null
        _connectionStatus.value = "Disconnected"
    }

    private fun initializeNode() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                // Load bootstrap peers from config file
                val bootstrapPeers = appContext?.let { ctx ->
                    BootstrapConfig.loadBootstrapPeers(ctx)
                } ?: BootstrapConfig.getDefaultBootstrapPeers()

                val success = libp2pService?.initializeNode(bootstrapPeers) ?: false
                if (success) {
                    val peerId = libp2pService?.getLocalPeerId()
                    _connectionStatus.value = "Connected: ${peerId?.take(12)}..."
                } else {
                    _connectionStatus.value = "Failed to initialize"
                }
            } catch (e: Exception) {
                _connectionStatus.value = "Error: ${e.message}"
            }
        }
    }

    private fun startMessagePolling() {
        messagePollingJob = viewModelScope.launch(Dispatchers.IO) {
            while (isActive) {
                try {
                    val receivedMessage = libp2pService?.receiveMessage()
                    if (receivedMessage != null && receivedMessage.isNotEmpty()) {
                        val content = String(receivedMessage, Charsets.UTF_8)
                        val message = Message(
                            id = messageIdCounter++,
                            content = content,
                            timestamp = dateFormat.format(Date()),
                            isSent = false,
                            encrypted = true
                        )

                        withContext(Dispatchers.Main) {
                            _messages.value = _messages.value + message
                        }
                    }
                } catch (e: Exception) {
                    // Ignore errors during polling
                }

                delay(100) // Poll every 100ms
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

                // Send through libp2p
                val success = libp2pService?.sendMessage(content.toByteArray(Charsets.UTF_8)) ?: false
                if (!success) {
                    withContext(Dispatchers.Main) {
                        _connectionStatus.value = "Failed to send message"
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
