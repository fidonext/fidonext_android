package com.fidonext.messenger.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.ArrowForward
import androidx.compose.material.icons.outlined.ArrowRightAlt
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.tooling.preview.Preview
import com.fidonext.messenger.ui.theme.FidoNextTheme

enum class CallType { Missed, Outgoing, Incoming }

data class CallEntry(
    val contactName: String,
    val callType: CallType,
    val durationMinutes: Int? = null,
    val timestamp: String,
    val peerId: String = ""
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CallScreen(
    onChatsClick: () -> Unit,
    onContactsClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onSearchClick: () -> Unit,
    onCallInfoClick: (CallEntry) -> Unit = {},
    onNewCallClick: () -> Unit = {}
) {
    var filterAll by remember { mutableStateOf(true) }

    val accentBlue = Color(0xFF007AFF)
    val filterInactiveBg = Color(0xFFE5E5EA)
    val filterInactiveText = Color(0xFF8E8E93)
    val missedRed = Color(0xFFFF3B30)
    val bottomNavGray = Color(0xFF8E8E93)

    val allCalls = listOf(
        CallEntry("May", CallType.Missed, null, "3:44 pm"),
        CallEntry("May", CallType.Outgoing, 15, "3:44 pm"),
        CallEntry("May", CallType.Incoming, 15, "3:44 pm"),
        CallEntry("May", CallType.Outgoing, 15, "yesterday"),
        CallEntry("May", CallType.Outgoing, 15, "last friday")
    )
    val calls = if (filterAll) allCalls else allCalls.filter { it.callType == CallType.Missed }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(end = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            modifier = Modifier
                                .height(32.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(filterInactiveBg),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (filterAll) accentBlue else Color.Transparent)
                                    .clickable { filterAll = true }
                                    .padding(horizontal = 20.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    "All",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = if (filterAll) Color.White else filterInactiveText
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (!filterAll) accentBlue else Color.Transparent)
                                    .clickable { filterAll = false }
                                    .padding(horizontal = 12.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    "Missed",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = if (!filterAll) Color.White else filterInactiveText
                                )
                            }
                        }
                        Button(
                            onClick = onNewCallClick,
                            colors = ButtonDefaults.buttonColors(containerColor = accentBlue),
                            shape = RoundedCornerShape(20.dp),
                            modifier = Modifier.height(34.dp).width(100.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                                tint = Color.White
                            )
                            Spacer(Modifier.width(2.dp))
                            Text("New", fontSize = 15.sp, fontWeight = FontWeight.Medium)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = Color(0xFFFBFBFB),
                tonalElevation = 0.dp,
                modifier = Modifier.height(80.dp)
            ) {
                NavigationBarItem(
                    selected = false,
                    onClick = onChatsClick,
                    icon = { SvgIcon("icons/chats.svg", "Chats", tint = bottomNavGray) },
                    label = { Text("Chats", color = bottomNavGray, fontSize = 10.sp) },
                    colors = NavigationBarItemDefaults.colors(
                        indicatorColor = Color.Transparent,
                        selectedIconColor = accentBlue,
                        unselectedIconColor = bottomNavGray
                    )
                )
                NavigationBarItem(
                    selected = true,
                    onClick = { },
                    icon = { SvgIcon("icons/phone.svg", "Calls", tint = accentBlue) },
                    label = { Text("Calls", color = accentBlue, fontSize = 10.sp) },
                    colors = NavigationBarItemDefaults.colors(
                        indicatorColor = Color(0xFFF8F8F8)
                    )
                )
                NavigationBarItem(
                    selected = false,
                    onClick = onContactsClick,
                    icon = { SvgIcon("icons/contacts.svg", "Contacts", tint = bottomNavGray) },
                    label = { Text("Contacts", color = bottomNavGray, fontSize = 10.sp) },
                    colors = NavigationBarItemDefaults.colors(
                        indicatorColor = Color.Transparent,
                        selectedIconColor = accentBlue,
                        unselectedIconColor = bottomNavGray
                    )
                )
                NavigationBarItem(
                    selected = false,
                    onClick = onSettingsClick,
                    icon = { SvgIcon("icons/settings.svg", "Settings", tint = bottomNavGray) },
                    label = { Text("Settings", color = bottomNavGray, fontSize = 10.sp) },
                    colors = NavigationBarItemDefaults.colors(
                        indicatorColor = Color.Transparent,
                        selectedIconColor = accentBlue,
                        unselectedIconColor = bottomNavGray
                    )
                )
                NavigationBarItem(
                    selected = false,
                    onClick = onSearchClick,
                    icon = { SvgIcon("icons/search.svg", "Search", tint = bottomNavGray) },
                    label = { Text("Search", color = bottomNavGray, fontSize = 10.sp) },
                    colors = NavigationBarItemDefaults.colors(
                        indicatorColor = Color.Transparent,
                        selectedIconColor = accentBlue,
                        unselectedIconColor = bottomNavGray
                    )
                )
            }
        },
        containerColor = Color.White
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(Color.White)
        ) {
            items(calls) { call ->
                CallItem(
                    call = call,
                    accentBlue = accentBlue,
                    missedRed = missedRed,
                    onInfoClick = { onCallInfoClick(call) }
                )
                if (call != calls.last()) {
                    Divider(
                        modifier = Modifier.padding(start = 76.dp),
                        color = Color(0xFFE5E5EA),
                        thickness = 0.5.dp
                    )
                }
            }
        }
    }
}

@Composable
private fun CallItem(
    call: CallEntry,
    accentBlue: Color,
    missedRed: Color,
    onInfoClick: () -> Unit
) {
    val subtitleText = when (call.callType) {
        CallType.Missed -> "Missed call"
        CallType.Outgoing -> "Outgoing call (${call.durationMinutes ?: 0} min)"
        CallType.Incoming -> "Incoming call (${call.durationMinutes ?: 0} min)"
    }
    val subtitleColor = Color(0xFF8E8E93)
    val nameColor = if (call.callType == CallType.Missed) missedRed else Color.Black

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { }
            .padding(vertical = 8.dp, horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(Color(0xFFCECED2)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                call.contactName.take(1).uppercase(),
                fontSize = 20.sp,
                color = Color.White,
                fontWeight = FontWeight.Medium
            )
        }

        Spacer(modifier = Modifier.width(16.dp))

        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    call.contactName,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 17.sp,
                    color = nameColor
                )
                if (call.callType != CallType.Missed) {
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        imageVector = if (call.callType == CallType.Outgoing) Icons.Outlined.ArrowBack else Icons.Outlined.ArrowForward,
                        contentDescription = "Info",
                        modifier = Modifier.size(15.dp).rotate(45.toFloat()),
                        tint = Color(0xFF8E8E93)
                    )

                }
            }
            Text(
                subtitleText,
                fontSize = 13.sp,
                color = subtitleColor
            )
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.End
        ) {
            Text(
                call.timestamp,
                fontSize = 14.sp,
                color = Color(0xFF8E8E93)
            )
            Spacer(modifier = Modifier.width(8.dp))
            IconButton(
                onClick = onInfoClick,
                modifier = Modifier.size(24.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.Info,
                    contentDescription = "Info",
                    modifier = Modifier.size(20.dp),
                    tint = accentBlue
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun CallScreenPreview() {
    FidoNextTheme {
        CallScreen(
            onChatsClick = {},
            onContactsClick = {},
            onSettingsClick = {},
            onSearchClick = {}
        )
    }
}
