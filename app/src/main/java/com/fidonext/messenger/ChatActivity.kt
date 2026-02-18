package com.fidonext.messenger

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.IBinder
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.fidonext.messenger.service.Libp2pService
import com.fidonext.messenger.ui.ChatScreen
import com.fidonext.messenger.ui.theme.FidoNextTheme
import com.fidonext.messenger.viewmodel.ChatViewModel

const val EXTRA_PEER_ID = "com.fidonext.messenger.EXTRA_PEER_ID"

class ChatActivity : ComponentActivity() {

    private var libp2pService: ILibp2pService? = null
    private var serviceBound = false
    private var chatViewModel: ChatViewModel? = null

    private val serviceConnection = object : android.content.ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            libp2pService = ILibp2pService.Stub.asInterface(service)
            serviceBound = true
            chatViewModel?.let {
                it.bindService(libp2pService!!, this@ChatActivity)
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            libp2pService = null
            serviceBound = false
            chatViewModel?.unbindService()
        }
    }

    private fun getServiceIfBound(): ILibp2pService? = if (serviceBound) libp2pService else null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val peerId = intent.getStringExtra(EXTRA_PEER_ID)

        // Bind to the already-running libp2p service
        val serviceIntent = Intent(this, Libp2pService::class.java)
        bindService(serviceIntent, serviceConnection, Context.BIND_AUTO_CREATE)

        setContent {
            FidoNextTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = androidx.compose.material3.MaterialTheme.colorScheme.background
                ) {
                    ChatScreen(
                        viewModel = viewModel(),
                        initialPeerId = peerId,
                        onViewModelCreated = { chatViewModel = it },
                        onServiceBound = {
                            getServiceIfBound()?.let { service ->
                                chatViewModel?.bindService(service, this@ChatActivity)
                            }
                        },
                        onBackClick = { finish() }
                    )
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (serviceBound) {
            unbindService(serviceConnection)
            serviceBound = false
        }
    }

    companion object {
        fun createIntent(context: Context, peerIdentifier: String): Intent {
            return Intent(context, ChatActivity::class.java).apply {
                putExtra(EXTRA_PEER_ID, peerIdentifier)
            }
        }
    }
}
