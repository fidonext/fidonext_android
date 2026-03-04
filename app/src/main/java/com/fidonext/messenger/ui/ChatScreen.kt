package com.fidonext.messenger.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.tooling.preview.Preview
import com.fidonext.messenger.ui.theme.FidoNextTheme
import com.fidonext.messenger.data.Message
import com.fidonext.messenger.viewmodel.ChatViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    messages: List<Message>,
    connectionStatus: String,
    activeRecipient: String?,
    initialPeerId: String?,
    onSendMessage: (String) -> Unit,
    onBackClick: () -> Unit = {}
) {
    val listState = rememberLazyListState()
    var messageText by remember { mutableStateOf("") }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Back to peers",
                            tint = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                },
                title = {
                    Column {
                        Text("FidoNext Messenger")
                        Text(
                            text = (activeRecipient ?: initialPeerId)?.let { "Chat: ${it.take(20)}…" }
                                ?: "Chat",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
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
                        onSendMessage(messageText)
                        messageText = ""
                    }
                }
            )
        }
    }
}

@Composable
fun ChatScreen(
    viewModel: ChatViewModel,
    initialPeerId: String?,
    onViewModelCreated: (ChatViewModel) -> Unit = {},
    onServiceBound: (() -> Unit)? = null,
    onBackClick: () -> Unit = {}
) {
    LaunchedEffect(viewModel) {
        onViewModelCreated(viewModel)
        onServiceBound?.invoke()
    }
    LaunchedEffect(initialPeerId) {
        initialPeerId?.takeIf { it.isNotBlank() }?.let { peerId ->
            viewModel.connectToRecipient(peerId)
        }
    }

    val messages by viewModel.messages.collectAsState()
    val connectionStatus by viewModel.connectionStatus.collectAsState()
    val activeRecipient by viewModel.activeRecipient.collectAsState()

    ChatScreen(
        messages = messages,
        connectionStatus = connectionStatus,
        activeRecipient = activeRecipient,
        initialPeerId = initialPeerId,
        onSendMessage = { viewModel.sendMessage(it) },
        onBackClick = onBackClick
    )
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

@Preview(showBackground = true)
@Composable
fun MessageItemPreview() {
    FidoNextTheme {
        Column(modifier = Modifier.padding(16.dp)) {
            MessageItem(
                message = Message(
                    id = 1,
                    content = "Hello there!",
                    timestamp = "10:30 AM",
                    isSent = false,
                    encrypted = true
                )
            )
            Spacer(modifier = Modifier.height(8.dp))
            MessageItem(
                message = Message(
                    id = 2,
                    content = "Hi! How are you?",
                    timestamp = "10:31 AM",
                    isSent = true,
                    encrypted = true
                )
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun MessageInputPreview() {
    FidoNextTheme {
        MessageInput(
            messageText = "Hello...",
            onMessageTextChange = {},
            onSendClick = {}
        )
    }
}

@Preview(showBackground = true)
@Composable
fun ChatScreenPreview() {
    FidoNextTheme {
        ChatScreen(
            messages = listOf(
                Message(1, "Hello!", "10:00 AM", false, true),
                Message(2, "Hi! This is a test message for the preview.", "10:01 AM", true, true)
            ),
            connectionStatus = "Connected",
            activeRecipient = "12D3KooWK3QtiFSR9PmhNMdJGcn3LpDp7hKPiGoa361sYpQ8p1c7",
            initialPeerId = "12D3KooWK3QtiFSR9PmhNMdJGcn3LpDp7hKPiGoa361sYpQ8p1c7",
            onSendMessage = {}
        )
    }
}
