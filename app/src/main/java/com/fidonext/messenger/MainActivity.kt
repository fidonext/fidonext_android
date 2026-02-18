package com.fidonext.messenger

import android.content.*
import android.os.Bundle
import android.os.IBinder
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.fidonext.messenger.data.Message
import com.fidonext.messenger.service.Libp2pService
import com.fidonext.messenger.ui.theme.FidoNextTheme
import com.fidonext.messenger.viewmodel.ChatViewModel

class MainActivity : ComponentActivity() {

    private var libp2pService: ILibp2pService? = null
    private var serviceBound = false
    private var chatViewModel: ChatViewModel? = null

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            libp2pService = ILibp2pService.Stub.asInterface(service)
            serviceBound = true
            chatViewModel?.let {
                it.bindService(libp2pService!!, this@MainActivity)
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            libp2pService = null
            serviceBound = false
            chatViewModel?.unbindService()
        }
    }

    fun getServiceIfBound(): ILibp2pService? = if (serviceBound) libp2pService else null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Load native libraries
        System.loadLibrary("cabi_rust_libp2p")
        System.loadLibrary("libp2p_jni")

        // Start and bind to libp2p service
        val serviceIntent = Intent(this, Libp2pService::class.java)
        startForegroundService(serviceIntent)
        bindService(serviceIntent, serviceConnection, Context.BIND_AUTO_CREATE)

        setContent {
            FidoNextTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    ChatScreen(
                        onViewModelCreated = { chatViewModel = it },
                        activity = this@MainActivity
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
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    viewModel: ChatViewModel = viewModel(),
    onViewModelCreated: (ChatViewModel) -> Unit = {},
    activity: MainActivity? = null
) {
    LaunchedEffect(viewModel) {
        onViewModelCreated(viewModel)
        // Try to bind service if already available
        activity?.getServiceIfBound()?.let { service ->
            viewModel.bindService(service, activity)
        }
    }

    val messages by viewModel.messages.collectAsState()
    val connectionStatus by viewModel.connectionStatus.collectAsState()
    val activeRecipient by viewModel.activeRecipient.collectAsState()
    val listState = rememberLazyListState()
    var messageText by remember { mutableStateOf("") }
    var recipientText by remember { mutableStateOf(activeRecipient ?: "") }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("FidoNext Messenger")
                        Text(
                            text = connectionStatus,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Light
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextField(
                    value = recipientText,
                    onValueChange = { recipientText = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Recipient peer_id or account_id") },
                    singleLine = true,
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent
                    ),
                    shape = RoundedCornerShape(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                IconButton(
                    onClick = {
                        if (recipientText.isNotBlank()) {
                            viewModel.connectToRecipient(recipientText.trim())
                        }
                    },
                    modifier = Modifier
                        .size(48.dp)
                        .background(
                            color = MaterialTheme.colorScheme.secondary,
                            shape = RoundedCornerShape(24.dp)
                        )
                ) {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = "Connect to recipient",
                        tint = MaterialTheme.colorScheme.onSecondary
                    )
                }
            }

            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                state = listState,
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(messages) { message ->
                    MessageItem(message = message)
                }
            }

            MessageInput(
                messageText = messageText,
                onMessageTextChange = { messageText = it },
                onSendClick = {
                    if (messageText.isNotBlank()) {
                        viewModel.sendMessage(messageText)
                        messageText = ""
                    }
                }
            )
        }
    }
}

@Composable
fun MessageItem(message: Message) {
    val alignment = if (message.isSent) Alignment.CenterEnd else Alignment.CenterStart
    val backgroundColor = if (message.isSent)
        MaterialTheme.colorScheme.primary
    else
        MaterialTheme.colorScheme.surfaceVariant
    val textColor = if (message.isSent)
        MaterialTheme.colorScheme.onPrimary
    else
        MaterialTheme.colorScheme.onSurfaceVariant

    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = alignment
    ) {
        Column {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = backgroundColor,
                modifier = Modifier.widthIn(max = 280.dp)
            ) {
                Column(
                    modifier = Modifier.padding(12.dp)
                ) {
                    Text(
                        text = message.content,
                        color = textColor,
                        fontSize = 16.sp
                    )
                    if (message.encrypted) {
                        Text(
                            text = "🔒 Encrypted",
                            color = textColor.copy(alpha = 0.7f),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Light,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            }
            Text(
                text = message.timestamp,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
            )
        }
    }
}

@Composable
fun MessageInput(
    messageText: String,
    onMessageTextChange: (String) -> Unit,
    onSendClick: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 8.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextField(
                value = messageText,
                onValueChange = onMessageTextChange,
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 8.dp),
                placeholder = { Text("Type a message...") },
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent
                ),
                shape = RoundedCornerShape(24.dp)
            )

            IconButton(
                onClick = onSendClick,
                modifier = Modifier
                    .size(48.dp)
                    .background(
                        color = MaterialTheme.colorScheme.primary,
                        shape = RoundedCornerShape(24.dp)
                    )
            ) {
                Icon(
                    imageVector = Icons.Default.Send,
                    contentDescription = "Send",
                    tint = MaterialTheme.colorScheme.onPrimary
                )
            }
        }
    }
}
