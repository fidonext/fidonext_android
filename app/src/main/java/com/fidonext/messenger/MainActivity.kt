package com.fidonext.messenger

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.fidonext.messenger.service.Libp2pService
import androidx.core.content.ContextCompat
import androidx.compose.runtime.*
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.fidonext.messenger.ui.CallScreen
import com.fidonext.messenger.ui.ChatListScreen
import com.fidonext.messenger.ui.SettingsScreen
import com.fidonext.messenger.ui.theme.FidoNextTheme
import com.fidonext.messenger.viewmodel.PeerListViewModel

class MainActivity : ComponentActivity() {

    private var libp2pService: ILibp2pService? = null
    private var serviceBound = false

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            libp2pService = ILibp2pService.Stub.asInterface(service)
            serviceBound = true
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            libp2pService = null
            serviceBound = false
        }
    }

    private fun getServiceIfBound(): ILibp2pService? = if (serviceBound) libp2pService else null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Load native libraries
        System.loadLibrary("cabi_rust_libp2p")
        System.loadLibrary("libp2p_jni")

        // Start and bind to libp2p service
        val serviceIntent = Intent(this, Libp2pService::class.java)
        ContextCompat.startForegroundService(this, serviceIntent)
        bindService(serviceIntent, serviceConnection, Context.BIND_AUTO_CREATE)

        setContent {
            FidoNextTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val navController = rememberNavController()
                    val peerListViewModel: PeerListViewModel = viewModel()

                    LaunchedEffect(serviceBound) {
                        if (serviceBound) {
                            getServiceIfBound()?.let { service ->
                                peerListViewModel.bindService(service, this@MainActivity)
                            }
                        }
                    }

                    NavHost(navController = navController, startDestination = "chat_list") {
                        composable("chat_list") {
                            ChatListScreen(
                                onChatClick = { peerIdentifier ->
                                    startActivity(ChatActivity.createIntent(this@MainActivity, peerIdentifier))
                                },
                                onCallsClick = { navController.navigate("calls") },
                                onSettingsClick = {
                                    navController.navigate("settings")
                                }
                            )
                        }
                        composable("calls") {
                            CallScreen(
                                onChatsClick = { navController.navigate("chat_list") { popUpTo("calls") { inclusive = true } } },
                                onContactsClick = { },
                                onSettingsClick = { navController.navigate("settings") },
                                onSearchClick = { }
                            )
                        }
                        composable("settings") {
                            SettingsScreen(
                                viewModel = peerListViewModel,
                                onBackClick = {
                                    navController.popBackStack()
                                },
                                onPeerClick = { peerIdentifier ->
                                    startActivity(ChatActivity.createIntent(this@MainActivity, peerIdentifier))
                                }
                            )
                        }
                    }
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
}
