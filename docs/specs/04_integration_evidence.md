# CinePilot Integration Evidence

## 1. Scope and safety
This document records what is supported by evidence for direct control of a Jio set-top box (STB) from CinePilot. CinePilot does **not** transmit undocumented commands and does **not** claim direct STB control works until a real key press has been demonstrated on physical hardware.

## 2. Verified mechanisms

### 2.1 Launch Jio remote UI (verified)
- JioHome package: `com.jio.home`.
- Remote launch action: `com.jio.btremoteapp.action.OPEN_BT_REMOTE`.
- JioHome manifest declares activity `com.jio.home.cleanarchitecture.common.screen.RemoteShortcutActivity` with an intent-filter for that action and category `DEFAULT`.
- On the test device, `PackageManager.queryIntentActivities(...)` resolves the action to `com.jio.home/.cleanarchitecture.common.screen.RemoteShortcutActivity`.
- CinePilot implements this via `JioRemoteIntentController.launchRemoteUi()` using an explicit `ComponentName`. This opens the Jio remote app UI. It does **not** send a key to the STB and `capabilities()` is intentionally empty.

### 2.2 Bluetooth LE discovery (verified, read-only)
- `BluetoothDiscovery` scans advertisements and records name, address, RSSI, and advertised service UUIDs.
- No filters are applied; the user must explicitly choose a device to inspect.
- No writes are performed during discovery or inspection.

### 2.3 Read-only GATT diagnostic (verified, read-only)
- `GattDiagnosticWorkflow` connects to a user-selected device, runs service discovery, and enumerates services and characteristic property bitmasks.
- Handles connection failure, disconnection, timeout (bounded, default 15s), and missing permissions (`SecurityException` / `hasPermissions()`).
- Output is formatted by `GattReportFormatter`; MAC addresses are partially redacted by `BleRedactor`; UUIDs are labelled but not redacted (standard identifiers).
- No characteristic reads or writes are performed beyond enumeration.

## 3. Publicly inspected JioHome / STB interfaces

### 3.1 JioHome marketing and help (public)
- JioHome advertises a "soft remote" for the Jio Set Top Box inside the app: switch channels, browse apps, control OTT platforms, and a soft gamepad for JioGames.
- Jio STB supports pairing standard Bluetooth accessories (speakers, headphones, soundbars, gaming controllers, remotes) through `Settings -> Remote and Bluetooth -> Add Bluetooth Accessories`.
- Jio developer portal exposes STB app publishing tools (JioStoreSTB, JioPayments SDK, JioAds SDK, JioVoiceAssistant STB integration, emulator/device specs) but no documented phone-to-STB remote-control API or protocol.

### 3.2 Conclusions from public evidence
- The only verified, supported integration surface for CinePilot is the Jio remote intent above.
- JioHome's remote feature is an in-app UI; its transport to the STB is not publicly documented. It may use local network, cloud relay, or another proprietary channel. This is **not established** by available evidence.
- BLE is required by JioHome (`android.hardware.bluetooth_le`) and the STB pairs Bluetooth accessories, but this alone does **not** establish that BLE GATT carries remote key commands. Presence in scan results is not evidence of a control protocol.

## 4. What remains uncertain
- Whether JioHome sends keys over BLE GATT, classic Bluetooth HID, local Wi-Fi, or a cloud relay.
- The GATT service/characteristic UUIDs (if any) used for remote commands, and their write formats.
- Pairing/authentication requirements between phone and STB (bonding, tokens, account linkage).
- Whether the STB exposes a standard HID-over-GATT or HID host interface usable by a third-party app.
- Whether any Jio SDK/API exists for direct STB control (none found in public developer docs).

## 5. Missing information needed
To implement direct STB control safely, the following are required:
1. A documented protocol or SDK from Jio, or a legitimately obtained integration specification.
2. A captured, authorized GATT table from a phone paired as a Jio remote (services, characteristics, descriptors, properties) plus observed write payloads for known key presses.
3. The pairing/authentication flow (bonding, encryption, account/token requirements).
4. Confirmation from Jio whether third-party apps may use any documented interface.

Without (1) and (2), command bytes cannot be derived and must not be guessed.

## 6. Recommended implementation path
1. Keep the verified intent launch as the primary, supported control path.
2. Use the read-only GATT diagnostic to collect evidence from devices the user explicitly selects; record and compare service tables.
3. Do not add any write path until a documented protocol and a demonstrated key press on physical hardware exist.
4. If inspection shows a standard Bluetooth HID profile (classic or BLE HID), evaluate Android's Bluetooth HID APIs (`BluetoothHidDevice` / `BluetoothHidHost`) only as a separate, explicitly scoped feature with its own verification.
5. Re-run this evidence review whenever Jio publishes new developer documentation or SDKs.

## 7. Test status
- Mocked JVM unit tests cover workflow timeout, error classification, permission handling, formatting, and redaction. These do **not** exercise real Bluetooth hardware.
## 8. Additional evidence reviewed (this pass)

### 8.1 Physical Jio remote hardware (teardown, third-party)
- A teardown of a Jio STB remote shows a Realtek MCU driving an IR LED and an RF transceiver, plus a microphone. Commands to the STB are sent over IR/RF, not over BLE GATT from the phone.
- This is evidence for the *physical* remote's transport, not for JioHome's soft-remote transport. It does not establish how JioHome sends keys.

### 8.2 Router/local-network APIs (not STB control)
- Public community documentation describes JioFiber router HTTP endpoints (`GET_CGI`/`SET_CGI` on `192.168.29.1`) for router settings. These are router-management APIs, not STB remote-control APIs, and require authentication/cookies.
- No publicly documented STB remote-control endpoint was found.

### 8.3 Static analysis availability
- The JioHome APK was not available for inspection in this environment. Without it, manifest components, service bindings, and code references for remote-control transport cannot be verified. Any prior manifest claims in `01_feasibility_report.md` should be treated as unverified unless re-confirmed from an APK under our control.

## 9. Updated protocol assessment
- **Verified:** Jio remote intent launch; BLE discovery; read-only GATT enumeration; physical remote uses IR/RF.
- **Hypothesis (unverified):** JioHome soft remote communicates with the STB over the local network or a cloud relay using proprietary, authenticated protocols. BLE may be used for discovery/pairing but is not established as the key channel.
- **No authorized interface for direct key injection** has been identified. Jio developer docs expose STB app publishing/ads/payment/voice-assistant SDKs, but no phone-to-STB remote-control API.

## 10. Exact missing evidence and next experiment
1. Obtain the JioHome APK legitimately and inspect its manifest/components/resources for transports (BLE GATT UUIDs, sockets, HTTP/gRPC endpoints, Firebase/FCM topics, pairing flows).
2. Capture an authorized remote-control session (phone + STB on the same network) and record network traffic / BLE HCI logs to identify the actual transport and payloads.
3. Extract the STB's advertised GATT table and compare with a phone paired as a Jio remote; identify any write-capable characteristic whose changes correlate with key presses.
4. Confirm authentication/bonding requirements and whether third-party use is permitted.

Until (1)-(3) are completed, CinePilot must not add write operations or guessed payloads.
- Hardware verification requires a physical Android device, a paired/available Jio STB, and is tracked separately. Direct STB control remains **unverified**.