package com.fidonext.messenger.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.tooling.preview.Preview
import com.fidonext.messenger.R
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
                        SvgIcon(
                            path = "icons/back.svg",
                            contentDescription = "Back",
                            tint = Color(0xFF007AFF) // iOS Blue
                        )
                    }
                },
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        // User Avatar
                        Image(
                            painter = painterResource(id = R.drawable.ic_launcher_foreground), // Placeholder
                            contentDescription = "Avatar",
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(Color.Gray),
                            contentScale = ContentScale.Crop
                        )
                        
                        Spacer(modifier = Modifier.width(12.dp))
                        
                        Column {
                            val userName = if (activeRecipient != null) {
                                "Peer ${activeRecipient.take(8)}"
                            } else {
                                "Inessa" // Matches screenshot name
                            }
                            Text(
                                text = userName,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.Black
                            )
                            Text(
                                text = if (connectionStatus == "Connected") "last seen a week ago" else connectionStatus,
                                fontSize = 12.sp,
                                color = Color.Gray
                            )
                        }
                    }
                },
                actions = {
                    IconButton(onClick = { /* TODO: More actions */ }) {
                        SvgIcon(
                            path = "icons/more.svg",
                            contentDescription = "More",
                            tint = Color.Black
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.White,
                    titleContentColor = Color.Black
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(Color(0xFFF2F2F7)) // iOS Background Gray
        ) {
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                state = listState,
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        Surface(
                            color = Color(0x808E8E93), // iOS System Gray 
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(
                                text = "Friday, 18",
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                                fontSize = 12.sp,
                                color = Color.White
                            )
                        }
                    }
                }
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
        Color(0xFFD1E1FF) // Light Blue for sent
    else
        Color(0xFFE9E9EB) // Light Gray for received
    
    val textColor = Color.Black

    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = alignment
    ) {
        Row(
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = if (message.isSent) Arrangement.End else Arrangement.Start,
            modifier = Modifier.fillMaxWidth(0.85f)
        ) {
            if (message.isSent) {
                Text(
                    text = message.timestamp,
                    fontSize = 11.sp,
                    color = Color.Gray,
                    modifier = Modifier.padding(end = 8.dp, bottom = 4.dp)
                )
            }

            Surface(
                shape = RoundedCornerShape(
                    topStart = 18.dp,
                    topEnd = 18.dp,
                    bottomStart = if (message.isSent) 18.dp else 4.dp,
                    bottomEnd = if (message.isSent) 4.dp else 18.dp
                ),
                color = backgroundColor,
                modifier = Modifier.widthIn(max = 280.dp)
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = message.content,
                        color = textColor,
                        fontSize = 16.sp,
                        lineHeight = 20.sp
                    )
                    if (message.encrypted) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(top = 2.dp)
                        ) {
                            SvgIcon(
                                path = "icons/lock.svg",
                                contentDescription = "Encrypted",
                                modifier = Modifier.size(10.dp),
                                tint = Color.Gray
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Encrypted",
                                color = Color.Gray,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Light
                            )
                        }
                    }
                }
            }

            if (!message.isSent) {
                Text(
                    text = message.timestamp,
                    fontSize = 11.sp,
                    color = Color.Gray,
                    modifier = Modifier.padding(start = 8.dp, bottom = 4.dp)
                )
            }
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
        color = Color.White,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 8.dp, vertical = 8.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { /* TODO: Add media */ }) {
                SvgIcon(
                    path = "icons/plus-circle.svg",
                    contentDescription = "Add",
                    tint = Color.Gray
                )
            }

            Surface(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 40.dp, max = 120.dp),
                color = Color(0xFFF2F2F7),
                shape = RoundedCornerShape(20.dp),
                border = null
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .padding(vertical = 8.dp)
                    ) {
                        if (messageText.isEmpty()) {
                            Text(
                                text = "Message",
                                color = Color.Gray,
                                fontSize = 16.sp
                            )
                        }
                        BasicTextField(
                            value = messageText,
                            onValueChange = onMessageTextChange,
                            modifier = Modifier.fillMaxWidth(),
                            textStyle = androidx.compose.ui.text.TextStyle(
                                fontSize = 16.sp,
                                color = Color.Black
                            )
                        )
                    }
                    
                    IconButton(
                        onClick = { /* TODO: Emojis */ },
                        modifier = Modifier.size(32.dp)
                    ) {
                        SvgIcon(
                            path = "icons/emoji.svg",
                            contentDescription = "Emoji",
                            tint = Color.Gray,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            IconButton(
                onClick = onSendClick,
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(if (messageText.isNotEmpty()) Color(0xFF007AFF) else Color.Transparent)
            ) {
                if (messageText.isEmpty()) {
                    SvgIcon(
                        path = "icons/microphone.svg",
                        contentDescription = "Voice",
                        tint = Color.Gray
                    )
                } else {
                    SvgIcon(
                        path = "icons/send.svg",
                        contentDescription = "Send",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
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
