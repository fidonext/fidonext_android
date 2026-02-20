# Connectivity status and retry behaviour

## Problem

When the app was started with Internet disabled (e.g. Airplane mode), it could still show **Connected** in the header because that was the previous state. The user could believe they were online when they were not.

## Solution

The libcabi_rust_libp2p service now:

1. **Verifies** real network connectivity (DHT reachable via bootstrap), not just “node is running”.
2. **Retries** with a fixed schedule to save battery.
3. **Exposes** the current status to the UI so the header always reflects actual connectivity.

## Behaviour

### Status values

- **Connected** – Connectivity check passed (node can reach DHT via bootstrap).
- **Connecting...** – Retrying to connect (up to 3 attempts, 5 s apart).
- **Disabled** – No connection after 3 attempts; service pauses for 30 s before the next cycle.

### Retry schedule (battery-friendly)

- **3 attempts** with **5 seconds** between attempts (status: **Connecting...**).
- If all fail → status **Disabled** for **30 seconds**, then the cycle repeats.
- When connected → status stays **Connected**; re-check every **5 seconds** in the background without changing the status. Only if that re-check fails do we switch to **Connecting...** and the retry cycle.

So the app never shows “Connected” until connectivity is verified, and when offline it cycles: Connecting... (3×5 s) → Disabled (30 s) → repeat.

## Implementation summary

### Service (`Libp2pService.kt`)

- **`connectionStatus`** – Atomic state: `"Connected"` | `"Connecting..."` | `"Disabled"`.
- **`getConnectionStatus()`** – AIDL method used by the UI to read the current status.
- **`verifyConnectivity()`** – Dials first bootstrap peer, waits 2 s, runs a DHT put/get test; returns `true` only if the test succeeds.
- **`startConnectivityLoop()`** – Coroutine that:
  - Waits until the node is initialized (`nodeHandle != 0`).
  - If status is already **Connected**, runs a silent `verifyConnectivity()` (no status change); only if it fails do we start reconnecting.
  - Otherwise sets **Connecting...**, tries `verifyConnectivity()` up to 3 times with 5 s delay.
  - On success → **Connected**, then silent re-check every 5 s (status unchanged).
  - On failure → **Disabled** for 30 s, then repeat.
- **`initializeNode()`** – At the end of a successful init, sets status to **Connecting...** so the UI never shows a stale “Connected” from a previous run; the loop then updates it.

Constants: `CONNECTIVITY_ATTEMPTS = 3`, `CONNECTIVITY_ATTEMPT_INTERVAL_MS = 5000`, `CONNECTIVITY_DISABLED_PAUSE_MS = 30_000`, `CONNECTIVITY_VERIFY_WAIT_MS = 2000`, `CONNECTIVITY_RECHECK_WHEN_CONNECTED_MS = 5000`.

### AIDL (`ILibp2pService.aidl`)

- **`String getConnectionStatus();`** – Returns the current status string for the header.

### ViewModels

- **PeerListViewModel** – On bind: sets status to **Connecting...**, calls `initializeNode()`, starts **polling** `getConnectionStatus()` every 1.5 s. Does not set “Connected” from init; status comes only from the service. On unbind: stops polling, sets **Disconnected**.
- **ChatViewModel** – Same polling of `getConnectionStatus()` every 1.5 s for the header. Does not set “Connected” from `initializeNode()`. While `connectToRecipient()` is in progress, polling does not overwrite the status so dial messages still show; when the flow ends, the next poll restores the service status.

## Files touched

- `app/src/main/aidl/com/fidonext/messenger/ILibp2pService.aidl` – `getConnectionStatus()`.
- `app/src/main/java/com/fidonext/messenger/service/Libp2pService.kt` – State, `verifyConnectivity()`, connectivity loop, `getConnectionStatus()` implementation.
- `app/src/main/java/com/fidonext/messenger/viewmodel/PeerListViewModel.kt` – Polling, no “Connected” from init.
- `app/src/main/java/com/fidonext/messenger/viewmodel/ChatViewModel.kt` – Polling, init and recipient-dial behaviour.
