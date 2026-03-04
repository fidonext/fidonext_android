package com.fidonext.messenger.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.tooling.preview.Preview
import com.fidonext.messenger.ui.theme.FidoNextTheme

data class ChatSummary(
    val peerId: String,
    val name: String,
    val lastMessage: String,
    val time: String,
    val unreadCount: Int = 0,
    val isOnline: Boolean = false,
    val hasPin: Boolean = false
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatListScreen(
    onChatClick: (String) -> Unit,
    onCallsClick: () -> Unit = {},
    onSettingsClick: () -> Unit
) {
    // Mock data based on the screenshot
    val chats = listOf(
        ChatSummary("H", "Henry", "I don't know what you're doing", "3:44 pm", 1, true, true),
        ChatSummary("A", "Alex", "Lorem ipsum dolor sit amet, consectetuer adipiscing elit...", "9:00 am", 0, false, true),
        ChatSummary("I", "Inessa", "Kisses! 😘", "Friday", 0, false, true),
        ChatSummary("L", "Luna", "Lorem ipsum dolor sit amet, consectetuer adipiscing elit...", "8:34 am", 1, false, false),
        ChatSummary("M", "Milly", "Lorem ipsum dolor sit amet, consectetuer adipiscing elit...", "6:00 am", 0, false, false),
        ChatSummary("An", "Andrew", "Lorem ipsum dolor sit amet, consectetuer adipiscing elit...", "Yesterday", 0, false, false)
    )

    val lightBlue = Color(0xFFE9F2FF)
    val accentBlue = Color(0xFF007AFF)

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(onClick = { /* Edit */ }) {
                            Text("Edit", color = accentBlue, fontSize = 18.sp)
                        }
                        Text(
                            "Unread",
                            color = Color.Gray,
                            fontSize = 18.sp,
                            modifier = Modifier.padding(start = 16.dp)
                        )
                        Spacer(modifier = Modifier.weight(1f))
                        IconButton(onClick = { /* Bookmark */ }) {
                            SvgIcon("icons/saved.svg", "Saved", tint = Color.Gray)
                        }
                        Button(
                            onClick = { /* New Chat */ },
                            colors = ButtonDefaults.buttonColors(containerColor = accentBlue),
                            shape = RoundedCornerShape(20.dp),
                            modifier = Modifier
                                .height(40.dp)
                                .padding(horizontal = 16.dp),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                        ) {
                            SvgIcon("icons/plus-circle2.svg", null, modifier = Modifier.size(18.dp), tint = Color.White)
                            Spacer(Modifier.width(4.dp))
                            Text("New", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = Color(0xFFF8F8F8),
                tonalElevation = 0.dp
            ) {
                NavigationBarItem(
                    selected = true,
                    onClick = { },
                    icon = { SvgIcon("icons/chats.svg", "Chats", tint = accentBlue) },
                    label = { Text("Chats", color = accentBlue) },
                    colors = NavigationBarItemDefaults.colors(
                        indicatorColor = Color(0xFFF8F8F8)
                    )
                )
                NavigationBarItem(
                    selected = false,
                    onClick = onCallsClick,
                    icon = { SvgIcon("icons/phone.svg", "Calls", tint = Color.Gray) },
                    label = { Text("Calls", color = Color.Gray) }
                )
                NavigationBarItem(
                    selected = false,
                    onClick = { },
                    icon = { SvgIcon("icons/contacts.svg", "Contacts", tint = Color.Gray) },
                    label = { Text("Contacts", color = Color.Gray) }
                )
                NavigationBarItem(
                    selected = false,
                    onClick = onSettingsClick,
                    icon = { SvgIcon("icons/settings.svg", "Settings", tint = Color.Gray) },
                    label = { Text("Settings", color = Color.Gray) }
                )
                NavigationBarItem(
                    selected = false,
                    onClick = { },
                    icon = { SvgIcon("icons/search.svg", "Search", tint = Color.Gray) },
                    label = { Text("Search", color = Color.Gray) }
                )
            }
        },
        containerColor = Color.White
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(Color.White)
        ) {
            val (pinnedChats, otherChats) = chats.partition { it.hasPin }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 16.dp)
            ) {
                // Pinned Chats Group
                if (pinnedChats.isNotEmpty()) {
                    item {
                        Column(
                            modifier = Modifier
                                .padding(vertical = 8.dp)
                                .clip(RoundedCornerShape(24.dp))
                                .background(lightBlue)
                        ) {
                            pinnedChats.forEachIndexed { index, chat ->
                                ChatItem(chat, onChatClick)
                                if (index < pinnedChats.size - 1) {
                                    Divider(
                                        modifier = Modifier.padding(start = 72.dp),
                                        color = Color.LightGray.copy(alpha = 0.3f),
                                        thickness = 0.5.dp
                                    )
                                }
                            }
                        }
                    }
                }

                // Regular Chats Group
                items(otherChats) { chat ->
                    ChatItem(chat, onChatClick)
                    if (chat != otherChats.last()) {
                        Divider(
                            modifier = Modifier.padding(start = 72.dp),
                            color = Color.LightGray.copy(alpha = 0.3f),
                            thickness = 0.5.dp
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ChatItem(chat: ChatSummary, onClick: (String) -> Unit) {
    val accentBlue = Color(0xFF007AFF)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick(chat.peerId) }
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Avatar
        Box(modifier = Modifier.size(56.dp)) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color(0xFFD9D9D9)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    chat.name.take(1),
                    fontSize = 24.sp,
                    color = Color.Gray,
                    fontWeight = FontWeight.Normal
                )
            }
            if (chat.isOnline) {
                Box(
                    modifier = Modifier
                        .size(14.dp)
                        .align(Alignment.BottomStart)
                        .offset(x = (-4).dp, y = 4.dp)
                        .clip(CircleShape)
                        .background(Color.White)
                        .padding(2.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(CircleShape)
                            .background(Color(0xFF00FF00))
                    )
                }
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    chat.name,
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp,
                    color = Color.Black
                )
                if (chat.hasPin) {
                    Spacer(modifier = Modifier.width(4.dp))
                    SvgIcon(
                        "icons/pin.svg",
                        contentDescription = "Pinned",
                        modifier = Modifier.size(14.dp),
                        tint = Color.Gray
                    )
                }
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    chat.time,
                    fontSize = 12.sp,
                    color = Color.Gray
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    chat.lastMessage,
                    fontSize = 15.sp,
                    color = Color.Gray,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                if (chat.unreadCount > 0) {
                    Box(
                        modifier = Modifier
                            .size(20.dp)
                            .clip(CircleShape)
                            .background(accentBlue),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            chat.unreadCount.toString(),
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                } else if (chat.hasPin) {
                   Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF8E8E93).copy(alpha = 0.6f))
                    )
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun ChatItemPreview() {
    FidoNextTheme {
        ChatItem(
            chat = ChatSummary(
                peerId = "1",
                name = "Henry",
                lastMessage = "I don't know what you're doing",
                time = "3:44 pm",
                unreadCount = 1,
                isOnline = true,
                hasPin = true
            ),
            onClick = {}
        )
    }
}

@Preview(showBackground = true)
@Composable
fun ChatListScreenPreview() {
    FidoNextTheme {
        ChatListScreen(
            onChatClick = {},
            onSettingsClick = {}
        )
    }
}
