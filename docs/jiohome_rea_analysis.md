# JioHome APK REA Analysis

## Scope and artifact identity

- Target: `/Users/vamsi/Desktop/Testing/Jio_AI/JioHome.apk`
- Package: `com.jio.home`
- Version: `2.1.107`, version code `251120674`
- APK SHA-256: `45e09b8b5a574efc202fa31a01a699b34b8fd83983b1fffb62216aadc6fef0a3`
- REA archive Evidence: `ev_4649386d4e72a4cd3d47bf4df2dd7e976ff755573b98bdca353e3934e7b60600`
- REA retained archive parent Evidence: `ev_7d9238c52d8d9580a0bb09a8f8501174704f7a98bb6d8ff41ec8d6b6033d368d`
- REA artifact bundle: `.rea-evidence/jiohome-artifact.json`

The supplied artifact is a standalone APK, not an APK set. Its archive contains seven DEX files (`classes.dex` through `classes7.dex`). No separate base/split APKs were present in `/Users/vamsi/Desktop/Testing/Jio_AI` or `/Users/vamsi/Desktop/Testing`.

## REA availability boundary

The installed REA CLI is version 6.8.0. Archive inventory completed. Android code/manifest operations were attempted but are unavailable in this session because `REA_JADX_MCP_JAR` is not configured for the caller-supplied `jadx-headless-mcp` 0.7.1 provider. No JAR was installed or fetched. Therefore this report separates REA Evidence from bounded local APK observations.

## Manifest and component observations

These are decoded with Android build-tools `aapt` from the supplied APK; they are static observations, not execution evidence. Full raw output is in `.rea-evidence/jiohome-manifest-aapt.txt`.

- Package identity and permissions: INTERNET, ACCESS_WIFI_STATE, ACCESS_NETWORK_STATE, CHANGE_NETWORK_STATE, Bluetooth classic and BLE permissions, NEARBY_WIFI_DEVICES, CHANGE_WIFI_MULTICAST_STATE, CHANGE_WIFI_STATE, RECORD_AUDIO, and other application permissions.
- Required hardware: Bluetooth and Bluetooth LE; Wi-Fi is declared not required; microphone is required.
- Launcher: `com.jio.home.cleanarchitecture.common.screen.SplashActivity`.
- Remote entry point: exported `com.jio.home.cleanarchitecture.common.screen.RemoteShortcutActivity`, `noHistory=true`, with action `com.jio.btremoteapp.action.OPEN_BT_REMOTE` and DEFAULT category.
- Main remote UI: exported `com.jio.remote.shared.ui.RemoteMainActivity`; also accepts `ACTION_SEND` with `text/plain`.
- Metadata: `bt-library-version=1.7.3`.
- Firebase messaging service and notification receivers are declared; this does not establish a remote command path.
- `com.jio.stbpushservice.SubscriberIdProvider2` appears as a provider authority/permission. Its role in remote control is unknown from current evidence.
- Deep-link hosts include `api.jiohome.jio.com`, `sit-api.jiohome.jio.com`, and a JioHome living page-link host.

### Important negative finding

The manifest proves an exported UI entry point, not direct key injection. CinePilot's existing `JioRemoteIntentController` correctly keeps this mechanism as a UI fallback and does not claim `RemoteKey` capabilities.

## Static package/string observations

Bounded local inspection of the seven DEX files found these candidate symbols and literals. These are candidates only until JADX method-level evidence or authorized runtime correlation confirms their behavior:

- Remote packages: `com.jio.remote.remote.*`, `com.jio.remote.connection.*`, `com.jio.remote.shared.*`, `com.jio.remote.cms.*`, and `com.jio.remote.jiogames.*`.
- Connection/pairing candidates: `connectToSTBUsingBluetoothDevice`, `handlePairedDeviceFoundInRsnScan`, `pairingUseCase`, `sendPairingPin`, `registerPair`, `findNextPair`, `PAIR_SUCCESS`, `UNBOND_REASON_REMOTE_DEVICE_DOWN`, `observeScanScreenDisconnect`, and `bluetoothConnectionStateReceiver`.
- Remote command/UI candidates: `sendMiniRemoteEvents`, `REMOTE_EVENT`, `REMOTE_VALUE`, `remoteInputs`, `remote_power`, `remote_ch_up`, `handleDPadUp`, `nativeVolume`, `QUERY_VOLUME`, and `PlaybackInfo`.
- Transport candidates: Ktor HTTP client/socket classes and WebSocket classes/literals, including `newWebSocket`, `WebSocket`, `RawWebSocket`, and `io.ktor.websocket.Frame$Binary`.
- Standard BLE UUID strings and many unrelated UUID/hex literals are present. No proprietary service/characteristic UUID is accepted as protocol evidence from string extraction alone.
- Candidate cloud/API base URL: `https://api.jiohome.jio.com`, plus `/auth/v2/refreshToken` and other JioHome service references. Presence in the APK does not establish that remote button commands use these endpoints.

Raw bounded outputs:

- `.rea-evidence/jiohome-strings-network-remote.txt`
- `.rea-evidence/jiohome-remote-class-strings.txt`
- `.rea-evidence/jiohome-dexdump-candidates.txt`
- `.rea-evidence/jiohome-dex-disassembly-hits.txt`

## Method-level evidence table

The following declarations were extracted from `.rea-evidence/jiohome-dex-fields-methods.txt` using bounded DEX metadata inspection. DEX metadata establishes class/member names and signatures; it does not establish runtime call order, serialized bytes, or physical-device effects.

| Class / method | Parameters and return | Static evidence | Classification | Remaining boundary |
|---|---|---|---|---|
| `com.jio.remote.cms.recommendations.data.network.JHCExplorerService$JHCDeviceApi.sendMiniRemoteEvents` | Six `String` parameters, `MiniRemoteRequestDto`, coroutine `Continuation`; generated return `Object` | Structured service API method exists, plus `$default` overload | **Confirmed statically** as a method; **strongly indicated** as an API/network operation by its owner | Endpoint, verb, headers, serialization, token source, callers, and whether it controls the physical STB are unresolved |
| `MiniRemoteRequestDto` | `action: String`, `clientId: String`, `user: String`, `data: KeyDataDto` | DTO constructor and fields are present | **Confirmed statically** | No wire encoding or call-site values established |
| `KeyDataDto` | `keyCode: Int`, `rmn: String` | Nested DTO carries an integer key code and an `rmn` string | **Confirmed statically** | Key-code mapping and meaning of `rmn` are unresolved |
| `com.jio.remote.remote.data.repository.RemoteRepositoryImpl.sendRemoteEvent(String)` | One `String`; `void` | Repository exposes a named remote-event operation | **Confirmed statically** | Exact callers, event vocabulary, and mapping to packet fields are unresolved |
| `RemoteRepositoryImpl.sendPressEvent(String)` / `sendReleaseEvent(String)` | One `String`; `void` | Separate press and release operations exist | **Confirmed statically**; strongly indicates button lifecycle semantics | Exact event representation and packet encoding are unresolved |
| `RemoteRepositoryImpl.sendPressEvent(S)` / `sendReleaseEvent(S)` | One obfuscated type `S`; `void` | Typed overloads coexist with string overloads | **Confirmed statically** | Obfuscated type identity and mapping are unresolved |
| `RemoteRepositoryImpl.sendPacket(Lk9/vx)` | One obfuscated packet-like type `Lk9/vx`; `void` | Repository has an internal packet abstraction and `REMOTE_EVENT_KEY_DELAY_MS = 150` | **Confirmed statically**; **strongly indicated** as lower dispatch boundary | Packet fields, serializer, acknowledgement behavior, and concrete transport call are unresolved |
| `RemoteRepositoryImpl.getConnectionManager()` | No parameters; `ConnectionManager` | Repository exposes its connection manager | **Confirmed statically**; strongly indicates repository-to-connection layering | Concrete implementation and send operation require method-body/caller evidence |
| `com.jio.remote.connection.manager.BTConnectionManager` | Implements `ConnectionDataSource`, `ScanListener`, `PairConnectionInterface`, `ReceiverDataSource` | Fields include `BluetoothAdapter`, `BTClassicSource`, `PairingManager`, device and connection/feature/keypad/volume/power state flows | **Confirmed statically**; Bluetooth Classic is a transport candidate | Whether command packets use Classic, BLE GATT, both, or an internal library remains unresolved |
| `BTConnectionManager.handleDisconnectCaseForRetry(ConnectionState, Continuation)` / `retryConnect(Context, CustomBluetoothDevice, Continuation)` | Connection state/device plus coroutine continuation; generated return `Object` | Retry/reconnect methods are declared | **Confirmed statically**; strongly indicates bounded connection recovery | Exact retry policy and send-path relationship are unresolved |
| `com.jio.remote.connection.manager.PairingManager.bondStateChanged(DeviceBondState)` | Bond state; `void` | Pairing manager implements `PairingDataSource`, owns bond receiver and pair-connection interface | **Confirmed statically**; strongly indicates Android bond-state integration | PIN exchange, persistence, and authorization message format are unresolved |
| `PairingManager` timing constants | `PAIRING_IDLE_THRESHOLD_MS = 20000`, `BOND_CAUSE_POLL_MS = 50`, `BOND_CAUSE_WAIT_MS = 1500` | Pairing failure cause/reason fields and bounded timing constants exist | **Confirmed statically**; strongly indicates bounded pairing-state handling | Timing does not identify the underlying channel or credential format |
| `JioHomeScanViewModelDataExtKt.handlePairedDeviceFoundInRsnScan` | `JioHomeScanViewModel`, `DeviceDetails`, `Context`, coroutine continuation; generated return `Object` | Scan/view-model helper with paired-device-specific name | **Confirmed statically**; strongly indicates reconnect candidate | Exact calls to connection/pairing methods require method-body/reference evidence |
| `JioHomeScanViewModelDataExtKt.connectToDevice` | `JioHomeScanViewModel`, `DeviceDetails`, `Context`; `void` | Device connection helper exists beside scan/reconnect helpers | **Confirmed statically** | Concrete transport operation and state checks unresolved |

### Most likely current command-dispatch path

The strongest static layering is:

```text
Remote UI button event
  -> RemoteRepositoryImpl.sendRemoteEvent(String)
  -> sendPressEvent / sendReleaseEvent
  -> sendPacket(Lk9/vx)
  -> ConnectionManager
  -> BTConnectionManager
  -> BTClassicSource candidate / other internal Bluetooth transport
  -> physical STB operation (not yet identified)
```

This is the most likely direct command path because the remote repository owns explicit press/release operations, an internal packet-like parameter, a connection-manager accessor, and a remote-specific connection package. The lowest identifiable communication layer is the repository's `sendPacket(Lk9/vx)` boundary followed by the `ConnectionManager` abstraction. No byte-level Bluetooth operation, GATT write, socket send, or serialized packet was recovered.

A separate, non-equivalent candidate remains:

```text
CMS/explorer mini-remote operation
  -> JHCExplorerService$JHCDeviceApi.sendMiniRemoteEvents(...)
  -> MiniRemoteRequestDto(action, clientId, user, KeyDataDto)
  -> KeyDataDto(keyCode, rmn)
  -> unresolved JHC/API transport and role
```

The APK evidence does not yet connect this API method to `RemoteRepositoryImpl`, `BTConnectionManager`, or the physical STB. It must not be treated as the direct command protocol merely because it contains `keyCode`.

## Required command-flow evidence still missing

Not established by this pass:

1. Exact callers and callees for the remote repository methods, including the UI handlers for `handleDPadUp`, `nativeVolume`, and `remoteInputs`.
2. Exact declaration/use and semantics of `REMOTE_EVENT`, `REMOTE_VALUE`, and `QUERY_VOLUME` within the remote subsystem rather than unrelated libraries.
3. Whether remote commands use BLE GATT, Bluetooth Classic, local TCP/UDP, WebSocket, cloud HTTP, or a combination.
4. Proprietary service and characteristic UUID ownership.
5. Pairing PIN/authentication message format and credential/token storage format.
6. Directional, OK, Back, Home, volume, and playback payload bytes or message schema.
7. Endpoint, HTTP verb, headers, serializer, and authentication/session behavior for `sendMiniRemoteEvents`.
8. Whether WebSocket symbols belong to the STB remote transport rather than another app feature.
9. The lowest concrete transport operation below `ConnectionManager` / `BTConnectionManager`.
10. Physical effect of any candidate command.

The current APK contains assets such as `clientkeys.pfx`, CA certificates, and `firmware.json`; their presence alone does not establish that they are required for remote control, and this report does not extract or reuse credentials.

## Communication-path assessment

### Confirmed facts

- The user's phone can control the STB with mobile data disabled while Wi-Fi remains connected.
- CinePilot sees the STB during BLE scanning and can enumerate its GATT table without writing.
- JioHome declares both BLE and network permissions and contains a dedicated remote implementation namespace.
- The Jio intent launches a remote UI, not a directly addressable key operation.

### Strong but unconfirmed hypotheses

- BLE is likely involved in discovery, pairing, authorization, or connection management because the app declares BLE, has pairing/connection candidates, and the user's device is discoverable via BLE.
- A local-network transport may carry commands because mobile data can be disabled while control still works and the APK contains WebSocket/HTTP client candidates. This is not proof of command traffic.
- WebSocket may be relevant to the remote subsystem because candidate symbols occur alongside remote/connection names, but method ownership and runtime correlation are pending.

### Unknowns

No passive traffic capture, Bluetooth HCI capture, or JioHome method-level decompilation has been performed in this session. No command is considered verified. No implementation should send guessed bytes.

## Evidence ledger

| Finding | Evidence | Confidence | Boundary |
|---|---|---:|---|
| APK identity/archive is complete within ZIP central-directory view | REA `ev_4649386...`; bundle `.rea-evidence/jiohome-artifact.json` | High | Does not execute APK or inspect outside ZIP entries |
| APK has seven DEX files | APK archive inventory | High | Local archive observation |
| Remote UI activity/action exists | `.rea-evidence/jiohome-manifest-aapt.txt:330-342` | High | Static manifest only |
| `RemoteMainActivity` exists and is exported | `.rea-evidence/jiohome-manifest-aapt.txt:351-367` | High | Static manifest only |
| BLE/network permissions and bt library metadata | `.rea-evidence/jiohome-manifest-aapt.txt:15-181`, `:366-367` | High | Static manifest only |
| Remote/pairing/WebSocket candidates exist | `.rea-evidence/jiohome-strings-network-remote.txt` and `.rea-evidence/jiohome-dex-disassembly-hits.txt` | Medium for candidate existence; low for semantics | String/disassembly search, not decompilation or execution |
| Intent is UI fallback rather than key transport | Existing CinePilot controller and user-verified behavior | High | No direct key extras or payload established |

## Next authorized steps

1. Supply/configure the caller-owned JADX MCP 0.7.1 JAR, then rerun REA `inspect_android_package`, class search, class inspection, method inspection, and reference tracing for the remote/connection candidates.
2. On the user's own phone and STB, capture separate idle, pairing, reconnect, and individual-button sessions. Keep mobile data disabled in a control run.
3. Correlate BLE HCI/GATT events and local-network packets. Do not transmit guessed payloads.
4. Only after a command's transport, authorization, and physical effect are independently correlated should CinePilot gain a `JioStbRemoteAdapter` implementation.
