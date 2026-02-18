package com.fidonext.messenger.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fidonext.messenger.ILibp2pService
import com.fidonext.messenger.config.BootstrapConfig
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Represents a peer that can be selected to open a chat.
 * @param displayName Short label for the list (e.g. peer_id suffix or address).
 * @param identifier Value to pass to lookupAndDial/setActiveRecipient (multiaddr or peer_id or account_id).
 */
data class DiscoveredPeer(
    val displayName: String,
    val identifier: String,
    val isBootstrap: Boolean = false,
    val isManual: Boolean = false
)

class PeerListViewModel : ViewModel() {

    private val _discoveredPeers = MutableStateFlow<List<DiscoveredPeer>>(emptyList())
    private val _bootstrapPeers = MutableStateFlow<List<DiscoveredPeer>>(emptyList())
    private val _manualPeers = MutableStateFlow<List<DiscoveredPeer>>(emptyList())

    val peers: StateFlow<List<DiscoveredPeer>> = combine(
        _discoveredPeers,
        _bootstrapPeers,
        _manualPeers
    ) { d, b, m -> d + b + m }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        emptyList()
    )

    private val _connectionStatus = MutableStateFlow("Disconnected")
    val connectionStatus: StateFlow<String> = _connectionStatus.asStateFlow()

    private val _localPeerId = MutableStateFlow<String?>(null)
    val localPeerId: StateFlow<String?> = _localPeerId.asStateFlow()

    private val _localAccountId = MutableStateFlow<String?>(null)
    val localAccountId: StateFlow<String?> = _localAccountId.asStateFlow()

    private var libp2pService: ILibp2pService? = null
    private var appContext: Context? = null
    private var refreshJob: Job? = null

    fun bindService(service: ILibp2pService, context: Context) {
        libp2pService = service
        appContext = context.applicationContext
        loadBootstrapPeers()
        initializeNode()
    }

    fun unbindService() {
        refreshJob?.cancel()
        refreshJob = null
        libp2pService = null
        appContext = null
        _connectionStatus.value = "Disconnected"
        _localPeerId.value = null
        _localAccountId.value = null
    }

    private fun loadBootstrapPeers() {
        val bootstrap = appContext?.let { BootstrapConfig.loadBootstrapPeers(it) }
            ?: BootstrapConfig.getDefaultBootstrapPeers()
        _bootstrapPeers.value = bootstrap.map { multiaddr ->
            DiscoveredPeer(
                displayName = "Relay: ${peerDisplayName(multiaddr)}",
                identifier = multiaddr,
                isBootstrap = true
            )
        }
    }

    /**
     * Load peers discovered via DHT (get_closest_peers). Runs on IO.
     */
    fun loadDiscoveredPeers() {
        viewModelScope.launch(Dispatchers.IO) {
            val service = libp2pService ?: return@launch
            val peerIds = try {
                service.getDiscoveredPeers() ?: emptyArray()
            } catch (e: Exception) {
                emptyArray()
            }
            val list = peerIds.map { id ->
                DiscoveredPeer(displayName = peerDisplayName(id), identifier = id)
            }
            withContext(Dispatchers.Main) {
                _discoveredPeers.value = list
            }
        }
    }

    /** Call from UI to refresh the list of discovered peers. */
    fun refreshPeers() {
        loadDiscoveredPeers()
    }

    /** Add a peer by peer_id or multiaddr so the other user can be opened for chat (e.g. when DHT doesn't find them). */
    fun addPeerManually(identifier: String) {
        val id = identifier.trim()
        if (id.isBlank()) return
        val existing = _manualPeers.value.any { it.identifier == id }
        if (existing) return
        _manualPeers.value = _manualPeers.value + DiscoveredPeer(
            displayName = peerDisplayName(id),
            identifier = id,
            isManual = true
        )
    }

    /** Start periodic discovery refresh (e.g. every 30s) so the list updates as DHT converges. */
    fun startPeriodicRefresh() {
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            while (true) {
                delay(30_000L)
                loadDiscoveredPeers()
            }
        }
    }

    fun stopPeriodicRefresh() {
        refreshJob?.cancel()
        refreshJob = null
    }

    /**
     * Short display name for a peer_id or multiaddr.
     */
    private fun peerDisplayName(identifier: String): String {
        val p2pPrefix = "/p2p/"
        val idx = identifier.indexOf(p2pPrefix)
        val peerId = if (idx >= 0) identifier.substring(idx + p2pPrefix.length).trim() else identifier
        return if (peerId.length > 20) "${peerId.take(12)}…${peerId.takeLast(4)}" else peerId
    }

    private fun initializeNode() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val bootstrapPeers = appContext?.let { BootstrapConfig.loadBootstrapPeers(it) }
                    ?: BootstrapConfig.getDefaultBootstrapPeers()
                val success = libp2pService?.initializeNode(bootstrapPeers) ?: false
                if (success) {
                    val peerId = libp2pService?.getLocalPeerId()
                    val accountId = libp2pService?.getLocalAccountId()
                    withContext(Dispatchers.Main) {
                        _connectionStatus.value = "Connected: ${peerId?.take(12)}… acct=${accountId?.take(8) ?: "-"}"
                        _localPeerId.value = peerId
                        _localAccountId.value = accountId
                    }
                    // Delay first discovery so DHT has time to bootstrap; then run and start periodic refresh
                    delay(5_000L)
                    loadDiscoveredPeers()
                    startPeriodicRefresh()
                } else {
                    withContext(Dispatchers.Main) {
                        _connectionStatus.value = "Failed to initialize"
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
