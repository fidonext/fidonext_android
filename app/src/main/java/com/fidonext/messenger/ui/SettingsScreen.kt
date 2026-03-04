package com.fidonext.messenger.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.ui.graphics.Color
import com.fidonext.messenger.ui.SvgIcon
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.tooling.preview.Preview
import com.fidonext.messenger.ui.theme.FidoNextTheme
import com.fidonext.messenger.viewmodel.DiscoveredPeer
import com.fidonext.messenger.viewmodel.PeerListViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    peers: List<DiscoveredPeer>,
    connectionStatus: String,
    localPeerId: String?,
    localAccountId: String?,
    onBackClick: () -> Unit,
    onRefreshPeers: () -> Unit,
    onAddPeerManually: (String) -> Unit,
    onPeerClick: (String) -> Unit
) {
    var showAddPeerDialog by remember { mutableStateOf(false) }
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Settings")
                        Text(
                            text = connectionStatus,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Light
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(
                        onClick = onRefreshPeers,
                        modifier = Modifier.padding(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Refresh peer list"
                        )
                    }
                }
            )
        },
        bottomBar = {
            val accentBlue = Color(0xFF007AFF)
            NavigationBar(
                containerColor = Color(0xFFF8F8F8),
                tonalElevation = 0.dp
            ) {
                NavigationBarItem(
                    selected = false,
                    onClick = onBackClick,
                    icon = { SvgIcon("icons/chats.svg", "Chats", tint = Color.Gray) },
                    label = { Text("Chats", color = Color.Gray) }
                )
                NavigationBarItem(
                    selected = false,
                    onClick = { },
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
                    selected = true,
                    onClick = { },
                    icon = { SvgIcon("icons/settings.svg", "Settings", tint = accentBlue) },
                    label = { Text("Settings", color = accentBlue) },
                    colors = NavigationBarItemDefaults.colors(
                        indicatorColor = Color(0xFFF8F8F8)
                    )
                )
                NavigationBarItem(
                    selected = false,
                    onClick = { },
                    icon = { SvgIcon("icons/search.svg", "Search", tint = Color.Gray) },
                    label = { Text("Search", color = Color.Gray) }
                )
            }
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddPeerDialog = true },
                containerColor = MaterialTheme.colorScheme.secondary,
                contentColor = MaterialTheme.colorScheme.onSecondary
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add peer")
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // My identity — always show when connected so user can copy and share their ID
            if (localPeerId != null || localAccountId != null) {
                MyIdentityCard(
                    peerId = localPeerId,
                    accountId = localAccountId,
                    onCopy = { text, label ->
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText(label, text))
                    }
                )
            }

            Text(
                text = "Discovered Peers",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(16.dp)
            )

            if (peers.isEmpty()) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "No peers in list",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Share your Peer ID above; tap + to add the other person's Peer ID",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(peers) { peer ->
                        PeerListItemSettings(
                            peer = peer,
                            onClick = { onPeerClick(peer.identifier) }
                        )
                    }
                }
            }
        }
    }

    if (showAddPeerDialog) {
        AddPeerDialog(
            onDismiss = { showAddPeerDialog = false },
            onAdd = { id ->
                onAddPeerManually(id)
                showAddPeerDialog = false
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: PeerListViewModel,
    onBackClick: () -> Unit,
    onPeerClick: (String) -> Unit
) {
    val peers by viewModel.peers.collectAsState()
    val connectionStatus by viewModel.connectionStatus.collectAsState()
    val localPeerId by viewModel.localPeerId.collectAsState()
    val localAccountId by viewModel.localAccountId.collectAsState()

    SettingsScreen(
        peers = peers,
        connectionStatus = connectionStatus,
        localPeerId = localPeerId,
        localAccountId = localAccountId,
        onBackClick = onBackClick,
        onRefreshPeers = { viewModel.refreshPeers() },
        onAddPeerManually = { viewModel.addPeerManually(it) },
        onPeerClick = onPeerClick
    )
}

@Composable
private fun MyIdentityCard(
    peerId: String?,
    accountId: String?,
    onCopy: (text: String, label: String) -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.primaryContainer,
        tonalElevation = 1.dp,
        shape = RoundedCornerShape(0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = "My identity — share this so others can add you",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Spacer(modifier = Modifier.height(12.dp))
            peerId?.let { id ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = id,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(
                        onClick = { onCopy(id, "Peer ID") },
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Person, // Fallback
                            contentDescription = "Copy Peer ID",
                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
                Text(
                    text = "Peer ID",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                )
                Spacer(modifier = Modifier.height(8.dp))
            }
            accountId?.let { id ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = id,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(
                        onClick = { onCopy(id, "Account ID") },
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Person, // Fallback
                            contentDescription = "Copy Account ID",
                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
                Text(
                    text = "Account ID",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddPeerDialog(
    onDismiss: () -> Unit,
    onAdd: (String) -> Unit
) {
    var text by remember { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add peer") },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text("Peer ID or multiaddress") },
                placeholder = { Text("12D3KooW... or /ip4/.../p2p/...") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester)
            )
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (text.isNotBlank()) onAdd(text.trim())
                }
            ) {
                Text("Add")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }
}

@Composable
fun PeerListItemSettings(
    peer: DiscoveredPeer,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        tonalElevation = 1.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Person,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(40.dp)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = peer.displayName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = peer.identifier.take(56).let { if (it.length == 56) "$it…" else it },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun MyIdentityCardPreview() {
    FidoNextTheme {
        MyIdentityCard(
            peerId = "12D3KooWK3QtiFSR9PmhNMdJGcn3LpDp7hKPiGoa361sYpQ8p1c7",
            accountId = "FIDO_ACCOUNT_123456",
            onCopy = { _, _ -> }
        )
    }
}

@Preview(showBackground = true)
@Composable
fun PeerListItemSettingsPreview() {
    FidoNextTheme {
        PeerListItemSettings(
            peer = DiscoveredPeer(
                displayName = "Alex",
                identifier = "12D3KooWK3QtiFSR9PmhNMdJGcn3LpDp7hKPiGoa361sYpQ8p1c7",
                isBootstrap = false
            ),
            onClick = {}
        )
    }
}

@Preview(showBackground = true)
@Composable
fun SettingsScreenPreview() {
    FidoNextTheme {
        SettingsScreen(
            peers = listOf(
                DiscoveredPeer("Henry", "12D3KooWSNqRTV2JccvWYTxtDtXUk9Y1m7EcGLy65eRN1qSrDkdp", true),
                DiscoveredPeer("Alex", "12D3KooWK3QtiFSR9PmhNMdJGcn3LpDp7hKPiGoa361sYpQ8p1c7", false)
            ),
            connectionStatus = "Connected (2 peers)",
            localPeerId = "12D3KooWSAPjpepMckV2W9AJ2rsTM6mdTdqXHgscjx327E2rT1ag",
            localAccountId = "ACCOUNT_MYSELF",
            onBackClick = {},
            onRefreshPeers = {},
            onAddPeerManually = {},
            onPeerClick = {}
        )
    }
}
