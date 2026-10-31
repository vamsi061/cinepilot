# Jio STB Remote Protocol Implementation Plan

## Gate: do not implement guessed protocol

CinePilot must not write to a GATT characteristic, open an undocumented socket, or replay a candidate HTTP/WebSocket message until all of these are true:

1. The path is observed during an authorized user-owned JioHome session.
2. Pairing/authorization requirements are understood and obtained through the supported flow.
3. At least one command is correlated with one individual physical button press.
4. The command is replayed only through a controlled, non-guessing test against the user's own STB.
5. The expected effect is independently verified.

## Proposed architecture after verification

```text
CommandRouter
  -> typed RemoteCommand
  -> JioStbRemoteAdapter : STBController
       -> JioStbTransport interface
            -> verified BLE / Bluetooth Classic / local network implementation
       -> PairingStateStore
       -> ConnectionSupervisor
```

### Typed command boundary

The AI intent layer maps language to a closed enum/sealed type. It must never construct arbitrary bytes, URLs, headers, UUIDs, or socket messages. Candidate commands include `DPAD_UP`, `DPAD_DOWN`, `DPAD_LEFT`, `DPAD_RIGHT`, `OK`, `BACK`, `HOME`, `VOLUME_UP`, `VOLUME_DOWN`, `MUTE`, and playback operations only after verification.

### Transport interface

The transport should expose operations such as:

- `discover()`
- `pairOrAuthorize()`
- `connect()`
- `send(RemoteCommand)`
- `observeState()` / acknowledgement where the protocol supports it
- `disconnect()`

The adapter owns reconnection, bounded backoff, cancellation, connection-loss reporting, and persistence of only the identifiers/tokens that are authorized and necessary. Secrets must not be logged.

### Verification result semantics

- `Success`: transport accepted the verified command and the expected effect was observed.
- `NoResponse`: the command was sent through a verified path but no expected response/effect arrived within the bounded timeout.
- `Unsupported`: no verified path/capability exists.
- `Failure`: permissions, pairing, connection, protocol, or device error.

The existing `ControlResult` and `STBController` contracts already express this fail-closed behavior.

## Test plan

Before physical integration:

- Fake transport tests for pairing, reconnect, connection loss, bounded retry, cancellation, and typed command mapping.
- Unit tests proving arbitrary payloads cannot enter the adapter through AI input.
- Existing BLE diagnostics tests remain read-only and continue to pass.

On the physical device:

- Initial pairing.
- Automatic reconnect after process restart / temporary disconnect.
- One verified DPAD command.
- Back and OK independently.
- Connection loss and recovery.
- Negative test: no success when no physical effect is observed.

## Fallback

`JioRemoteIntentController` remains a separate fallback for launching the Jio remote UI. It must not be mixed with direct transport capability reporting.
