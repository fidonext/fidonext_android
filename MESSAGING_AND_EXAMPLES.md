# FidoNext Messaging & Examples Reference

This document summarizes how the Python and Rust examples in `fidonext-core/c-abi-libp2p/examples/` handle peer discovery, encryption, and messaging — and how the Android app aligns with them.

## Examples Overview

- **`examples/python/ping_standalone_nodes.py`** — Low-level Node API, E2EE helpers
- **`examples/python/fidonext_chat_client.py`** — Full chat client with DHT directory, prekey discovery, libsignal E2EE
- **`examples/rust/e2ee_local_mesh.rs`** — E2EE test: prekey/session messages, DHT publish/fetch, relay mesh

---

## 1. Peer Discovery

### Python (fidonext_chat_client.py)

- **DHT directory**: `lookup_directory(identifier)` → `dht_get_record(directory_key_for_peer|account)`
- **Addresses from directory**: `card["addresses"]` — multiaddrs published by the peer
- **find_peer**: `node.find_peer(peer_id)` → poll `try_dequeue_discovery_event()` for `DISCOVERY_EVENT_ADDRESS` and `DISCOVERY_EVENT_FINISHED`
- **get_closest_peers**: Not shown in chat client; used conceptually for discovery

### Rust (e2ee_local_mesh.rs)

- **Discovery queue**: `DiscoveryQueue`, `handle.dht_get_record()`, `handle.dial()`
- Peers found via DHT and dial

### Android

- **Directory lookup**: `resolvePeerId(identifier)` uses `directoryKeyForPeer` / `directoryKeyForAccount` (same keys as Python)
- **Address resolution**: `getDirectoryAddresses`, `resolvePeerAddresses` (find_peer + discovery events)
- **Closest peers**: `getClosestPeers` via `cabiNodeGetClosestPeers` + `cabiNodeDequeueDiscoveryEvent`

---

## 2. Peer Lists

### Python

- `state.contacts` — manual contacts
- DHT lookup for peer_id / account_id to get directory card with addresses

### Android

- `getDiscoveredPeers()` — `getClosestPeers(localPeerId)` + `getClosestPeers(relayPeerId)` for each bootstrap
- Manual peers from `addPeerManually(identifier)`
- Directory lookup for `peer_id` / `account_id`

---

## 3. Encryption (LibSignal E2EE)

### Python (ping_standalone_nodes.py)

```python
# Build prekey bundle (for publishing to DHT)
build_prekey_bundle(profile_path, one_time_prekey_count=32, ttl_seconds=...)

# Encrypt (auto: prekey or session)
build_message_auto(profile_path, recipient_prekey_bundle, plaintext, aad)

# Decrypt
decrypt_message_auto(profile_path, payload)  # -> (kind, plaintext)
```

### Rust (e2ee_local_mesh.rs)

```rust
e2ee::build_prekey_bundle(&profile_path, 16, 3600)
e2ee::build_message_auto(&profile_path, &recipient_bundle, &plaintext, b"aad")
e2ee::decrypt_message_auto(&profile_path, &payload)
```

### Android

- `cabiE2eeBuildPrekeyBundle(profilePath, oneTimePrekeyCount, ttlSeconds)`
- `cabiE2eeBuildMessageAuto(profilePath, recipientPrekeyBundle, plaintext, aad)`
- `cabiE2eeDecryptMessageAuto(profilePath, payload)`

---

## 4. Sending Messages

### Python

1. **Connect** first: `lookup_and_dial(identifier)` — directory or find_peer, then `node.dial(addr)`
2. **Ensure prekey bundle**: `_ensure_contact_prekey_bundle(peer_id)` — DHT `lookup_prekey_bundle` or contact cache
3. **Encrypt**: `build_message_auto(profile_path, bundle, text)`
4. **Wrap in chat packet**:
   ```json
   {"schema":"fidonext-chat-v1","from_peer_id":"...","to_peer_id":"...","payload_type":"libsignal","payload_b64":"..."}
   ```
5. **Publish**: `node.send_message(encoded)` → `cabi_node_enqueue_message`

### Android

Same flow:

1. `lookupAndDial(identifier)` — directory, find_peer, or circuit relay
2. `setActiveRecipient(identifier)` — triggers prekey prefetch from DHT
3. `sendEncryptedMessage(plaintext)`:
   - `getOrFetchRecipientPrekeyBundle(peerId)`
   - `cabiE2eeBuildMessageAuto`
   - Build fidonext-chat-v1 JSON
   - `cabiNodeEnqueueMessage`

---

## 5. Receiving Messages

### Python

- `node.try_receive_message()` in a loop
- Parse JSON; if `payload_type == "libsignal"` → `decrypt_message_auto(profile_path, base64_decode(payload_b64))`

### Android

- Poll `receiveDecryptedMessage()` (dequeue + `tryDecryptChatPacket`)
- `tryDecryptChatPacket` validates schema, `to_peer_id == local`, then `cabiE2eeDecryptMessageAuto`

---

## 6. DHT Keys (Must Match Across Clients)

| Purpose         | Key format (Python/Android)                    |
|----------------|-----------------------------------------------|
| Directory/peer | `fidonext/directory/v1/peer/{peer_id}`        |
| Directory/account | `fidonext/directory/v1/account/{account_id}` |
| Prekey/peer    | `fidonext/prekey/v1/peer/{peer_id}`           |
| Prekey/account | `fidonext/prekey/v1/account/{account_id}`     |

---

## 7. "Failed to Send" — Root Causes & Fixes

The error usually means `sendEncryptedMessage` returned false. Typical causes:

1. **Prekey bundle not found** — DHT lookup for recipient prekey failed.
   - Both peers must be connected to the same relay.
   - DHT records can take several seconds to propagate.
   - **Fixes**: Longer prekey retries (8 attempts, 2.5s), periodic re-announce to DHT, 3s delay after dial before setting active recipient.

2. **No active recipient** — `setActiveRecipient` was not called or failed.

3. **Not connected** — `lookupAndDial` failed; no path to the peer.
   - Use the same bootstrap relay on both devices.
   - Ensure both apps are open when connecting and sending.

4. **Enqueue failure** — `cabiNodeEnqueueMessage` returned non-success (rare).

### Changes Made

- **Periodic DHT re-announce** (every 10 min), mirroring Python `_announce_loop`.
- **More prekey retries**: 8 attempts × 2.5 s (up to ~20 s).
- **Post-dial delay**: 3 s after dial before `setActiveRecipient` to allow DHT propagation.
- **Detailed logging** for each failure branch in `sendEncryptedMessage`.

### Usage Tips

- Keep both apps open and connected for 10–15 seconds before sending.
- Use the same bootstrap relay (e.g. `bootstrap_nodes.txt`).
- On first send, the prekey fetch can take 5–20 s; subsequent sends use the cache.
