# JioHome Communication and Pairing Flows

These diagrams describe the current evidence boundary. Solid boxes are observed facts; dashed boxes are hypotheses or pending evidence. A diagram is not proof of a transport.

## Pairing / first-use flow

```mermaid
flowchart TD
    P[Phone running JioHome] -->|BLE scan observed by user; app declares BLE permissions| S[Nearby Jio STB advertisement]
    S --> C{JioHome remote connection subsystem}
    C -->|candidate symbols: scan, pair, sendPairingPin, registerPair| Q[Pairing / authorization exchange]
    Q -.->|message format and channel unknown| A[Authorized STB session]
    P -->|candidate cloud/API auth and refresh symbols| H[JioHome cloud services]
    H -.->|relationship to STB pairing unknown| Q
```

**Confirmed:** BLE discovery is observed by the user; JioHome declares BLE; pairing/reconnection succeeds in the user's normal app flow.

**Not confirmed:** whether pairing payloads are BLE GATT, Bluetooth Classic, local network, cloud-mediated, or hybrid; PIN/token format; exact persistence location.

## Reconnection flow

```mermaid
flowchart LR
    R[Previously paired phone/STB state] --> J[JioHome startup or remote screen]
    J -->|static method: handlePairedDeviceFoundInRsnScan| D[Device/session lookup]
    D -->|static candidate: connectToDevice / connectToSTBUsingBluetoothDevice| B[Bluetooth connection manager]
    B -->|static fields: BluetoothAdapter + BTClassicSource| C[BTConnectionManager]
    C -->|retryConnect / disconnect retry declarations| U[Usable remote session]
    D -.->|candidate WebSocket/local-network reconnect| W[WebSocket or local socket candidate]
    W -.-> U
    U -.-> V[Physical STB effect: pending per-button capture]
```

`handlePairedDeviceFoundInRsnScan`, `connectToDevice`, `BTConnectionManager`, and reconnect methods are statically present. The `BluetoothAdapter` and `BTClassicSource` fields make Bluetooth Classic a concrete transport candidate, but no method-body evidence yet proves that remote key packets use it. The WebSocket/local-network edge remains a candidate only.

## Remote command flow

### Direct remote repository candidate

```mermaid
sequenceDiagram
    participant UI as JioHome remote UI
    participant R as RemoteRepositoryImpl
    participant P as Internal packet abstraction
    participant M as ConnectionManager
    participant B as BTConnectionManager
    participant T as BTClassicSource or other Bluetooth transport
    participant S as Physical Jio STB

    UI->>R: remote event (event representation unresolved)
    R->>R: sendPressEvent / sendReleaseEvent
    R->>P: sendPacket(Lk9/vx)
    R->>M: connection-manager boundary (exact call unresolved)
    M->>B: connection implementation (exact call unresolved)
    B-.->T: Bluetooth transport candidate
    T-.->S: packet and wire operation unresolved
```

**Static conclusion:** `RemoteRepositoryImpl` is the strongest current command-dispatch owner. It declares `sendRemoteEvent(String)`, separate press/release methods, `sendPacket(Lk9/vx)`, a `ConnectionManager` accessor, and a 150 ms remote-event delay constant. The lowest identifiable layer is the packet/connection-manager boundary; no concrete byte write or socket send is established.

### Separate CMS/explorer mini-remote candidate

```mermaid
sequenceDiagram
    participant X as CMS/explorer caller
    participant A as JHCDeviceApi
    participant D as MiniRemoteRequestDto
    participant K as KeyDataDto
    participant N as Unresolved JHC/API transport

    X->>A: sendMiniRemoteEvents(String x6, MiniRemoteRequestDto)
    A->>D: action, clientId, user, data
    D->>K: keyCode, rmn
    A-.->N: endpoint, verb, serialization, auth, and role unresolved
```

The presence of `keyCode` in this DTO does not prove that the method sends a physical STB command. No static reference currently connects it to `RemoteRepositoryImpl` or `BTConnectionManager`.

```mermaid
sequenceDiagram
    participant C as CinePilot
    participant A as Future JioStbRemoteAdapter
    participant T as Verified transport (TBD)
    participant S as Physical Jio STB

    C->>A: typed RemoteCommand(DPAD_UP | OK | BACK | ...)
    A->>T: verified protocol operation only
    T->>S: verified command message
    S-->>T: verified acknowledgement/state or observable effect
    T-->>A: result
    A-->>C: Success only after expected effect is verified
```

Current status: the adapter is intentionally not implemented because the transport and command message are not sufficiently verified.

## Current path assessment

- BLE: **confirmed for discovery visibility; command use unknown**.
- Bluetooth Classic: **concrete static transport candidate** because `BTConnectionManager` owns a `BTClassicSource`; command use remains unconfirmed.
- Local TCP/UDP: **unknown**.
- Local WebSocket: **candidate only**; no command-chain connection established.
- Cloud HTTP/WebSocket: **candidate API path** through `JHCDeviceApi.sendMiniRemoteEvents`; endpoint, authentication, and physical-STB role remain unresolved.
- Direct command dispatch: **strongest current path is `RemoteRepositoryImpl` press/release → `sendPacket` → `ConnectionManager` → `BTConnectionManager`; lowest concrete wire operation is unresolved**.
- JioHome intent: **confirmed to open UI; not a direct key API**.

## Evidence classification

### Confirmed statically

- `RemoteRepositoryImpl` declares named remote-event, press, release, and packet-send methods.
- `BTConnectionManager` declares Bluetooth connection, pairing, retry, state, and `BTClassicSource` members.
- `PairingManager` integrates bond-state changes and bounded pairing-failure timing.
- `JHCDeviceApi.sendMiniRemoteEvents` and its `MiniRemoteRequestDto`/`KeyDataDto` model exist.

### Strongly indicated

- Remote UI input is transformed into press/release operations and an internal packet abstraction before reaching a connection-manager layer.
- Bluetooth Classic is likely one possible lower transport for the connection subsystem.
- Pairing/reconnection state is managed through Android bond state and connection-manager retry logic.

### Unresolved

- Exact UI call sites and event-to-key mapping for `handleDPadUp`, `nativeVolume`, `remoteInputs`, `REMOTE_EVENT`, `REMOTE_VALUE`, and `QUERY_VOLUME`.
- Packet fields, serialization, authentication/session checks, and the concrete Bluetooth/socket operation.
- Whether the CMS mini-remote API controls the physical STB or serves another feature.
- Physical command effects and any relationship between the API and direct Bluetooth paths.
