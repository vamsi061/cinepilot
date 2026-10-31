package com.cinepilot.app.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.cinepilot.app.CinePilotApplication
import com.cinepilot.app.device.BluetoothDiscovery
import com.cinepilot.app.device.ControlResult
import com.cinepilot.app.device.DiscoveredDevice
import com.cinepilot.app.device.GattDiagnosticResult
import com.cinepilot.app.device.GattDiagnosticWorkflow
import com.cinepilot.app.device.GattReportFormatter
import com.cinepilot.app.device.JioRemoteIntentController
import com.cinepilot.app.domain.Command
import com.cinepilot.app.domain.CommandRouter
import com.cinepilot.app.domain.VoiceProcessor
import com.cinepilot.app.ui.theme.CinePilotTheme
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

private enum class Destination { HOME, TV, NETWORK, REMOTE, MORE }
private enum class AddDeviceOption { WIFI, STB, HUB }

class MainActivity : ComponentActivity() {
    private lateinit var app: CinePilotApplication
    private lateinit var voiceProcessor: VoiceProcessor
    private lateinit var jioController: JioRemoteIntentController
    private lateinit var discovery: BluetoothDiscovery
    private var scanJob: Job? = null

    private var voiceStatus by mutableStateOf("Tap the microphone and speak")
    private var transcript by mutableStateOf("—")
    private var intentText by mutableStateOf("—")
    private var controlLog by mutableStateOf("No control attempted yet")
    private var devices by mutableStateOf<List<DiscoveredDevice>>(emptyList())
    private var scanning by mutableStateOf(false)
    private var diagnosticReport by mutableStateOf("No device inspected yet")
    private var inspectingAddress by mutableStateOf<String?>(null)
    private var destination by mutableStateOf(Destination.HOME)
    private var addSheetVisible by mutableStateOf(false)
    private var selectedOption by mutableStateOf<AddDeviceOption?>(null)
    private var rebootDialogVisible by mutableStateOf(false)
    private var notificationPanelVisible by mutableStateOf(false)
    private var statusMessage by mutableStateOf<String?>(null)
    private var remoteConnectionState by mutableStateOf<com.cinepilot.app.STBConnectionState>(com.cinepilot.app.STBConnectionState.Disconnected)

    private val micPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) voiceProcessor.startListening() else voiceStatus = "Microphone permission denied"
    }

    private val blePermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        if (result.values.all { it }) startScan() else controlLog = "Bluetooth permission denied"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        app = application as CinePilotApplication
        jioController = JioRemoteIntentController(this)
        discovery = BluetoothDiscovery(this)
        voiceProcessor = VoiceProcessor(this, app.geminiClient)
        voiceProcessor.setCallbacks(VoiceProcessor.Callbacks(
            onListening = { voiceStatus = "Listening…" },
            onProcessing = { voiceStatus = "Understanding…" },
            onTranscript = { transcript = it; voiceStatus = "Heard" },
            onIntent = { response ->
                intentText = "${response.intentType} · confidence ${"%.2f".format(response.confidence)}"
                controlLog = describe(CommandRouter.route(response))
                voiceStatus = "Done"
            },
            onError = { voiceStatus = it }
        ))
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                discovery.devices.collect { devices = it }
            }
        }
        setContent {
            CinePilotTheme {
                CinePilotApp(
                    destination = destination,
                    voiceStatus = voiceStatus,
                    transcript = transcript,
                    intentText = intentText,
                    controlLog = controlLog,
                    devices = devices,
                    scanning = scanning,
                    diagnosticReport = diagnosticReport,
                    inspectingAddress = inspectingAddress,
                    addSheetVisible = addSheetVisible,
                    selectedOption = selectedOption,
                    rebootDialogVisible = rebootDialogVisible,
                    onDestination = { destination = it },
                    onMic = ::onMicClicked,
                    onOpenAdd = { addSheetVisible = true; selectedOption = null },
                    onCloseAdd = ::closeAddSheet,
                    onSelectOption = { selectedOption = it },
                    onLaunchRemote = ::onLaunchRemoteClicked,
                    onOpenRemote = { destination = Destination.REMOTE },
                    onScan = ::onScanClicked,
                    onStopScan = ::onStopScanClicked,
                    onInspectDevice = ::onInspectDeviceClicked,
                    onReboot = { rebootDialogVisible = true },
                    onDismissReboot = { rebootDialogVisible = false },
                    notificationPanelVisible = notificationPanelVisible,
                    remoteConnectionState = remoteConnectionState,
                    remoteCapabilities = jioController.capabilities(),
                    onReconnectRemote = { statusMessage = "Reconnect unavailable: no verified direct STB transport is configured." },
                    onOpenNotifications = { notificationPanelVisible = true },
                    onCloseNotifications = { notificationPanelVisible = false },
                    statusMessage = statusMessage,
                    onJioPcLaunch = { statusMessage = "JioPC is unavailable: no verified installed-app integration was found." },
                    onDismissStatus = { statusMessage = null }
                )
            }
        }
    }

    private fun closeAddSheet() {
        onStopScanClicked()
        addSheetVisible = false
        selectedOption = null
    }

    private fun onMicClicked() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            voiceProcessor.startListening()
        } else micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
    }

    private fun onLaunchRemoteClicked() {
        controlLog = when (val result = jioController.launchRemoteUi()) {
            is ControlResult.Success -> "Jio remote UI opened. CinePilot did not send an STB command."
            is ControlResult.Unsupported -> "Unavailable: ${result.detail}"
            is ControlResult.NoResponse -> "No response: ${result.detail}"
            is ControlResult.Failure -> "Failed: ${result.detail}"
        }
    }

    private fun onScanClicked() {
        val missing = discovery.requiredPermissions().filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isEmpty()) startScan() else blePermissionLauncher.launch(missing.toTypedArray())
    }

    private fun startScan() {
        if (!discovery.isBluetoothEnabled()) {
            controlLog = "Bluetooth is turned off. Enable it, then try again."
            return
        }
        discovery.clearDevices()
        scanning = true
        controlLog = "Scanning for nearby BLE advertisements…"
        scanJob?.cancel()
        scanJob = lifecycleScope.launch {
            try {
                discovery.scan().collect { }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                controlLog = "Scan failed: ${e.message ?: "unknown error"}"
            } finally {
                scanning = false
            }
        }
    }

    private fun onStopScanClicked() {
        scanJob?.cancel(); scanJob = null; discovery.stopScan(); scanning = false
    }

    private fun onInspectDeviceClicked(device: DiscoveredDevice) {
        if (inspectingAddress != null) return
        onStopScanClicked()
        inspectingAddress = device.address
        diagnosticReport = "Inspecting services for ${device.name ?: device.address}…"
        lifecycleScope.launch {
            val result = GattDiagnosticWorkflow(discovery).run(device.address, device.name)
            diagnosticReport = GattReportFormatter.format(result)
            inspectingAddress = null
            controlLog = if (result is GattDiagnosticResult.Success) {
                "Services inspected read-only. This does not pair or enable remote control."
            } else "GATT inspection did not complete: ${result::class.simpleName}"
        }
    }

    override fun onDestroy() {
        scanJob?.cancel(); voiceProcessor.destroy(); super.onDestroy()
    }

    private fun describe(command: Command): String = when (command) {
        is Command.SearchMovies -> "Search: \"${command.query}\" (catalog unavailable)"
        is Command.PlayTitle -> "Play: \"${command.title}\" (no verified playback mechanism)"
        Command.TransportToggle -> "Transport toggle (no verified key mechanism)"
        Command.TransportStop -> "Stop (no verified key mechanism)"
        Command.NavigateHome -> "Home (no verified key mechanism)"
        is Command.OpenStreamingApp -> "Open ${command.service} (not implemented)"
        Command.VolumeUp -> "Volume up (no verified key mechanism)"
        Command.VolumeDown -> "Volume down (no verified key mechanism)"
        is Command.Unsupported -> "Unsupported: ${command.reason}"
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CinePilotApp(
    destination: Destination,
    voiceStatus: String,
    transcript: String,
    intentText: String,
    controlLog: String,
    devices: List<DiscoveredDevice>,
    scanning: Boolean,
    diagnosticReport: String,
    inspectingAddress: String?,
    addSheetVisible: Boolean,
    selectedOption: AddDeviceOption?,
    rebootDialogVisible: Boolean,
    onDestination: (Destination) -> Unit,
    onMic: () -> Unit,
    onOpenAdd: () -> Unit,
    onCloseAdd: () -> Unit,
    onSelectOption: (AddDeviceOption) -> Unit,
    onLaunchRemote: () -> Unit,
    onOpenRemote: () -> Unit,
    onScan: () -> Unit,
    onStopScan: () -> Unit,
    onInspectDevice: (DiscoveredDevice) -> Unit,
    onReboot: () -> Unit,
    onDismissReboot: () -> Unit,
    notificationPanelVisible: Boolean,
    remoteConnectionState: com.cinepilot.app.STBConnectionState,
    remoteCapabilities: Set<com.cinepilot.app.device.RemoteKey>,
    onReconnectRemote: () -> Unit,
    onOpenNotifications: () -> Unit,
    onCloseNotifications: () -> Unit,
    statusMessage: String?,
    onJioPcLaunch: () -> Unit,
    onDismissStatus: () -> Unit
) {
    BackHandler(enabled = addSheetVisible) { onCloseAdd() }
    Surface(Modifier.fillMaxSize(), color = Color(0xFFF9FAFC)) {
        Column(Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxWidth()) {
                when (destination) {
                    Destination.HOME -> HomeDashboard(onOpenAdd, onReboot, onOpenRemote, voiceStatus, transcript, intentText, controlLog, onMic, onOpenNotifications, onJioPcLaunch, statusMessage)
                    Destination.TV -> UnavailablePage("TV", "No authorized entertainment content provider is configured.")
                    Destination.NETWORK -> NetworkPage(devices, diagnosticReport, inspectingAddress, scanning, onScan, onStopScan, onInspectDevice)
                    Destination.REMOTE -> RemoteScreen(
                        connectionState = remoteConnectionState,
                        capabilities = remoteCapabilities,
                        onOpenOfficialRemote = onLaunchRemote,
                        onReconnect = onReconnectRemote
                    )
                    Destination.MORE -> MorePage(onLaunchRemote)
                }
            }
            BottomNavigation(destination, onDestination)
            // Remote is also reachable from the Remote action card on Home.
        }
    }
    if (addSheetVisible) {
        ModalBottomSheet(onDismissRequest = onCloseAdd, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false), shape = RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp)) {
            AddDeviceSheet(selectedOption, onSelectOption, onCloseAdd, onLaunchRemote, onScan, onStopScan, scanning, devices, diagnosticReport, inspectingAddress, onInspectDevice)
        }
    }
    if (notificationPanelVisible) {
        AlertDialog(
            onDismissRequest = onCloseNotifications,
            title = { Text("Notifications") },
            text = { Text("No application, pairing, connection, or device-status notifications are available.") },
            confirmButton = { TextButton(onClick = onCloseNotifications) { Text("Close") } }
        )
    }
    if (statusMessage != null) {
        AlertDialog(
            onDismissRequest = onDismissStatus,
            title = { Text("Unavailable") },
            text = { Text(statusMessage) },
            confirmButton = { TextButton(onClick = onDismissStatus) { Text("Close") } }
        )
    }
    if (rebootDialogVisible) {
        AlertDialog(
            onDismissRequest = onDismissReboot,
            title = { Text("Reboot Jio router?") },
            text = { Text("CinePilot has no verified router-management API configured, so it cannot send a reboot request.") },
            confirmButton = { TextButton(onClick = onDismissReboot) { Text("Got it") } },
            dismissButton = { TextButton(onClick = onDismissReboot) { Text("Cancel") } }
        )
    }
}

@Composable
private fun HomeDashboard(onOpenAdd: () -> Unit, onReboot: () -> Unit, onOpenRemote: () -> Unit, voiceStatus: String, transcript: String, intentText: String, controlLog: String, onMic: () -> Unit, onOpenNotifications: () -> Unit, onJioPcLaunch: () -> Unit, statusMessage: String?) {
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            // Decorative glyph: hide from screen readers so the header isn't
            // announced as a navigation element (it previously said "Network").
            Text("⌂", color = Color(0xFF1479C9), fontSize = 28.sp, modifier = Modifier.clearAndSetSemantics {})
            Spacer(Modifier.width(10.dp)); Column(Modifier.weight(1f)) { Text("CinePilot", fontWeight = FontWeight.Bold, fontSize = 18.sp); Text("Selected device · status unavailable", fontSize = 12.sp, color = Color.Gray) }
            ActionCircle("+", "Add device", onOpenAdd); Spacer(Modifier.width(8.dp)); ActionCircle("!", "Notifications", onOpenNotifications)
        }
        SummaryCard()
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            ActionCard("Remote", "Open remote", Color(0xFFE7F3FF), Modifier.weight(1f), onOpenRemote)
            ActionCard("Reboot", "Router (unavailable)", Color(0xFFE7F3FF), Modifier.weight(1f), onReboot)
        }
        VoiceCard(voiceStatus, transcript, intentText, controlLog, onMic)
        PromoCard(onJioPcLaunch)
        // No legitimate content source is configured, so these sections are
        // deliberately de-emphasized rather than filled with fabricated data.
        SmallUnavailableRow("TV on the Go", "No live content source is configured.")
        SmallUnavailableRow("Top News Channels", "No authorized channel catalog is configured.")
    }
}

@Composable
private fun SummaryCard() {
    Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFF0F2F5)), shape = RoundedCornerShape(22.dp)) {
        Row(Modifier.fillMaxWidth().padding(vertical = 18.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
            SummaryValue("WiFi", "Unknown"); VerticalDivider(); SummaryValue("Speed", "Unavailable"); VerticalDivider(); SummaryValue("Devices", "Unavailable")
        }
    }
}

@Composable private fun SummaryValue(label: String, value: String) { Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.widthIn(min = 80.dp)) { Text(label, color = Color(0xFF1479C9), fontSize = 13.sp); Text(value, fontWeight = FontWeight.Bold, fontSize = 15.sp) } }
@Composable private fun VerticalDivider() { Box(Modifier.width(1.dp).height(38.dp).background(Color(0xFFD5D9E0))) }

@Composable private fun ActionCircle(label: String, description: String, onClick: () -> Unit) { Button(onClick = onClick, modifier = Modifier.size(46.dp).semantics { contentDescription = description }, shape = CircleShape, contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)) { Text(label, fontSize = 22.sp) } }
@Composable private fun ActionCard(title: String, subtitle: String, color: Color, modifier: Modifier, onClick: () -> Unit) { Card(onClick = onClick, modifier = modifier, colors = CardDefaults.cardColors(containerColor = color), shape = RoundedCornerShape(20.dp)) { Column(Modifier.padding(16.dp)) { Text(title, fontWeight = FontWeight.Bold, fontSize = 17.sp); Text(subtitle, color = Color.DarkGray, fontSize = 13.sp) } } }

@Composable private fun VoiceCard(status: String, transcript: String, intent: String, log: String, onMic: () -> Unit) { Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(20.dp)) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { Row(verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text("Voice control", fontWeight = FontWeight.Bold, fontSize = 18.sp); Text(status, color = Color.Gray, fontSize = 13.sp) }; Button(onClick = onMic, shape = CircleShape, modifier = Modifier.size(60.dp), contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)) { Text("MIC", fontSize = 12.sp) } }; LabeledValue("Heard", transcript); LabeledValue("Intent", intent); Text(log, color = Color.Gray, fontSize = 12.sp) } } }
@Composable private fun PromoCard(onJioPcLaunch: () -> Unit) { Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFF0E9FF)), shape = RoundedCornerShape(22.dp)) { Row(Modifier.fillMaxWidth().padding(18.dp), verticalAlignment = Alignment.CenterVertically) { Box(Modifier.size(48.dp).background(Color(0xFF7D4DD1), CircleShape), contentAlignment = Alignment.Center) { Text("PC", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp) }; Spacer(Modifier.width(14.dp)); Column(Modifier.weight(1f)) { Text("JioPC", fontWeight = FontWeight.Bold, fontSize = 18.sp); Text("JioPC is not installed or configured.", fontSize = 13.sp, color = Color.DarkGray) }; TextButton(onClick = onJioPcLaunch) { Text("Launch") } } } }
@Composable private fun UnavailableCard(title: String, message: String) { Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(20.dp)) { Column(Modifier.padding(16.dp)) { Text(title, fontWeight = FontWeight.Bold, fontSize = 18.sp); Text(message, color = Color.Gray, fontSize = 13.sp); Text("Unavailable", color = Color(0xFF9A5D00), fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp)) } } }
@Composable private fun SmallUnavailableRow(title: String, message: String) { Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text(title, fontWeight = FontWeight.SemiBold, fontSize = 14.sp); Text(message, color = Color.Gray, fontSize = 12.sp) }; Text("Unavailable", color = Color(0xFF9A5D00), fontSize = 11.sp) } }

@Composable
private fun AddDeviceSheet(selected: AddDeviceOption?, onSelect: (AddDeviceOption) -> Unit, onClose: () -> Unit, onLaunchRemote: () -> Unit, onScan: () -> Unit, onStopScan: () -> Unit, scanning: Boolean, devices: List<DiscoveredDevice>, report: String, inspecting: String?, onInspect: (DiscoveredDevice) -> Unit) {
    Column(Modifier.fillMaxWidth().navigationBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Text("Add device", fontSize = 27.sp, fontWeight = FontWeight.Bold, modifier = Modifier.fillMaxWidth()); TextButton(onClick = onClose) { Text("Close") } }
        Text("Choose a device type. Discovery does not mean pairing or control is available.", color = Color.Gray, fontSize = 14.sp)
        DeviceOption("WiFi Network devices", "Add Extenders, Repeater or Router.", AddDeviceOption.WIFI, selected, onSelect)
        DeviceOption("STB Remote", "Set up and inspect your Set top box.", AddDeviceOption.STB, selected, onSelect)
        DeviceOption("Smart home hub", "Add Smart, Mini or Virtual hub.", AddDeviceOption.HUB, selected, onSelect)
        when (selected) {
            AddDeviceOption.WIFI -> UnavailableCard("WiFi Network devices", "A verified router provisioning API is not configured.")
            AddDeviceOption.HUB -> UnavailableCard("Smart home hub", "Jio hub provisioning is not available in CinePilot.")
            AddDeviceOption.STB -> StbSetupContent(onLaunchRemote, onScan, onStopScan, scanning, devices, report, inspecting, onInspect)
            null -> Unit
        }
        Spacer(Modifier.height(12.dp))
    }
}

@Composable private fun DeviceOption(title: String, description: String, option: AddDeviceOption, selected: AddDeviceOption?, onSelect: (AddDeviceOption) -> Unit) { Card(onClick = { onSelect(option) }, colors = CardDefaults.cardColors(containerColor = if (selected == option) Color(0xFFE7F3FF) else Color(0xFFF2F3F5)), shape = RoundedCornerShape(18.dp)) { Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text(title, fontWeight = FontWeight.Bold, fontSize = 16.sp); Text(description, color = Color.Gray, fontSize = 13.sp) }; Text("›", fontSize = 28.sp, color = Color(0xFF1479C9)) } } }

@Composable private fun StbSetupContent(onLaunch: () -> Unit, onScan: () -> Unit, onStop: () -> Unit, scanning: Boolean, devices: List<DiscoveredDevice>, report: String, inspecting: String?, onInspect: (DiscoveredDevice) -> Unit) { Column(verticalArrangement = Arrangement.spacedBy(10.dp)) { Text("STB setup", fontWeight = FontWeight.Bold, fontSize = 19.sp); Text("Scan and GATT inspection are read-only. Direct pairing and key control are unavailable until a protocol is verified.", color = Color.Gray, fontSize = 13.sp); Button(onClick = if (scanning) onStop else onScan, modifier = Modifier.fillMaxWidth()) { Text(if (scanning) "Stop discovery" else "Start discovery") }; if (devices.isEmpty()) Text(if (scanning) "Looking for nearby BLE advertisements…" else "No devices found. Start discovery to begin.", color = Color.Gray, fontSize = 13.sp) else devices.take(25).forEach { device -> Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text(device.name ?: "Unnamed device", fontWeight = FontWeight.SemiBold); Text("${device.address} · ${device.rssi} dBm", color = Color.Gray, fontSize = 12.sp) }; OutlinedButton(onClick = { onInspect(device) }, enabled = inspecting == null) { Text(if (inspecting == device.address) "…" else "Inspect") } } }; Text(report, fontSize = 11.sp, color = Color.DarkGray); OutlinedButton(onClick = onLaunch, modifier = Modifier.fillMaxWidth()) { Text("Open JioHome remote UI") }; Text("This opens the official app UI only; it does not send a CinePilot command.", color = Color.Gray, fontSize = 12.sp) } }

@Composable private fun NetworkPage(devices: List<DiscoveredDevice>, report: String, inspecting: String?, scanning: Boolean, onScan: () -> Unit, onStop: () -> Unit, onInspect: (DiscoveredDevice) -> Unit) { Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) { Text("Network", fontSize = 28.sp, fontWeight = FontWeight.Bold); UnavailableCard("Router status", "No authenticated router API is configured. Connected-device count and speed are unavailable."); Text("BLE diagnostics", fontWeight = FontWeight.Bold, fontSize = 19.sp); StbSetupContent({}, onScan, onStop, scanning, devices, report, inspecting, onInspect) } }
@Composable private fun MorePage(onLaunchRemote: () -> Unit) { Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) { Text("More", fontSize = 28.sp, fontWeight = FontWeight.Bold); UnavailableCard("Direct STB control", "No verified command transport is available."); UnavailableCard("Integrations", "Router, content, hub, and JioPC integrations require supported providers."); OutlinedButton(onClick = onLaunchRemote, modifier = Modifier.fillMaxWidth()) { Text("Open JioHome remote UI") } } }
@Composable private fun UnavailablePage(title: String, message: String) { Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) { Text(title, fontSize = 28.sp, fontWeight = FontWeight.Bold); UnavailableCard(title, message) } }

@Composable private fun BottomNavigation(selected: Destination, onSelect: (Destination) -> Unit) { Surface(shadowElevation = 8.dp, modifier = Modifier.navigationBarsPadding()) { Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.SpaceEvenly) { listOf(Destination.HOME to "Home", Destination.TV to "TV", Destination.NETWORK to "Network", Destination.REMOTE to "Remote", Destination.MORE to "More").forEach { (destination, label) -> TextButton(onClick = { onSelect(destination) }, modifier = Modifier.weight(1f)) { Text(label, color = if (destination == selected) Color(0xFF1479C9) else Color.Gray, fontWeight = if (destination == selected) FontWeight.Bold else FontWeight.Normal) } } } } }

@Composable private fun LabeledValue(label: String, value: String) { Row(Modifier.fillMaxWidth()) { Text("$label: ", color = Color.Gray, fontSize = 12.sp, modifier = Modifier.width(62.dp)); Text(value, fontSize = 12.sp) } }
