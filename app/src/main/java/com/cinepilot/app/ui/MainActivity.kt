package com.cinepilot.app.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
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
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private lateinit var app: CinePilotApplication
    private lateinit var voiceProcessor: VoiceProcessor
    private lateinit var jioController: JioRemoteIntentController
    private lateinit var discovery: BluetoothDiscovery
    private var scanJob: Job? = null

    // UI state, owned by the activity and rendered by Compose.
    private var voiceStatus by mutableStateOf("Tap the microphone and speak")
    private var transcript by mutableStateOf("—")
    private var intentText by mutableStateOf("—")
    private var controlLog by mutableStateOf("No control attempted yet")
    private var devices by mutableStateOf<List<DiscoveredDevice>>(emptyList())
    private var scanning by mutableStateOf(false)
    private var diagnosticReport by mutableStateOf("No device inspected yet")
    private var inspectingAddress by mutableStateOf<String?>(null)

    private val micPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            voiceProcessor.startListening()
        } else {
            voiceStatus = "Microphone permission denied"
        }
    }

    private val blePermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        if (result.values.all { it }) {
            startScan()
        } else {
            controlLog = "Bluetooth permission denied"
        }
    }

    override fun onCreate(savedInstanceState: android.os.Bundle?) {
        super.onCreate(savedInstanceState)
        app = application as CinePilotApplication
        jioController = JioRemoteIntentController(this)
        discovery = BluetoothDiscovery(this)
        voiceProcessor = VoiceProcessor(this, app.geminiClient)

        voiceProcessor.setCallbacks(
            VoiceProcessor.Callbacks(
                onListening = { voiceStatus = "Listening…" },
                onProcessing = { voiceStatus = "Understanding…" },
                onTranscript = {
                    transcript = it
                    voiceStatus = "Heard"
                },
                onIntent = { response ->
                    intentText = "${response.intentType} · confidence ${"%.2f".format(response.confidence)}"
                    controlLog = describe(CommandRouter.route(response))
                    voiceStatus = "Done"
                },
                onError = {
                    voiceStatus = it
                }
            )
        )

        // Keep the device list in sync with the scanner.
        lifecycleScope.launch {
            discovery.devices.collect { devices = it }
        }

        setContent {
            CinePilotTheme {
                CinePilotScreen(
                    voiceStatus = voiceStatus,
                    transcript = transcript,
                    intentText = intentText,
                    controlLog = controlLog,
                    devices = devices,
                    scanning = scanning,
                    diagnosticReport = diagnosticReport,
                    inspectingAddress = inspectingAddress,
                    onMic = ::onMicClicked,
                    onLaunchRemote = ::onLaunchRemoteClicked,
                    onScan = ::onScanClicked,
                    onStopScan = ::onStopScanClicked,
                    onInspectDevice = ::onInspectDeviceClicked
                )
            }
        }
    }

    private fun onMicClicked() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            == PackageManager.PERMISSION_GRANTED
        ) {
            voiceProcessor.startListening()
        } else {
            micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    private fun onLaunchRemoteClicked() {
        val result = jioController.launchRemoteUi()
        controlLog = when (result) {
            is ControlResult.Success -> "Intent launched: ${result.detail}"
            is ControlResult.Unsupported -> "Not launched: ${result.detail}"
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
            controlLog = "Bluetooth is turned off"
            return
        }
        discovery.clearDevices()
        scanning = true
        controlLog = "Scanning for nearby Bluetooth LE devices…"
        scanJob?.cancel()
        scanJob = lifecycleScope.launch {
            try {
                discovery.scan().collect { /* devices arrive through discovery.devices */ }
            } catch (e: Exception) {
                controlLog = "Scan failed: ${e.message}"
            } finally {
                scanning = false
            }
        }
    }

    private fun onStopScanClicked() {
        scanJob?.cancel()
        scanJob = null
        discovery.stopScan()
        scanning = false
        controlLog = "Scan stopped. Found ${devices.size} device(s)."
    }

    /**
     * Inspect a single, explicitly user-selected device.
     * We stop the scanner first so the radio is not shared between scan and connect,
     * and we never iterate over the device list automatically.
     */
    private fun onInspectDeviceClicked(device: DiscoveredDevice) {
        if (inspectingAddress != null) {
            diagnosticReport = "An inspection is already in progress."
            return
        }
        scanJob?.cancel()
        scanJob = null
        discovery.stopScan()
        scanning = false
        inspectingAddress = device.address
        diagnosticReport = "Connecting to ${device.name ?: device.address}…"

        lifecycleScope.launch {
            val workflow = GattDiagnosticWorkflow(discovery)
            val result = workflow.run(device.address, device.name)
            diagnosticReport = GattReportFormatter.format(result)
            inspectingAddress = null
            if (result !is GattDiagnosticResult.Success) {
                controlLog = "GATT inspection did not complete: ${result::class.simpleName}"
            } else {
                controlLog = "GATT inspection complete: ${result.services.size} service(s) listed."
            }
        }
    }

    override fun onDestroy() {
        scanJob?.cancel()
        voiceProcessor.destroy()
        super.onDestroy()
    }

    private fun describe(command: Command): String = when (command) {
        is Command.SearchMovies ->
            "Search: \"${command.query}\" (catalog lookup not implemented)"
        is Command.PlayTitle ->
            "Play: \"${command.title}\" (no verified playback mechanism)"
        Command.TransportToggle -> "Transport toggle (no verified key mechanism)"
        Command.TransportStop -> "Stop (no verified key mechanism)"
        Command.NavigateHome -> "Home (no verified key mechanism)"
        is Command.OpenStreamingApp -> "Open ${command.service} (not implemented)"
        Command.VolumeUp -> "Volume up (no verified key mechanism)"
        Command.VolumeDown -> "Volume down (no verified key mechanism)"
        is Command.Unsupported -> "Unsupported: ${command.reason}"
    }

    companion object {
        @Suppress("unused")
        private val bleApiHint: Int = Build.VERSION_CODES.S
    }
}

@Composable
private fun CinePilotScreen(
    voiceStatus: String,
    transcript: String,
    intentText: String,
    controlLog: String,
    devices: List<DiscoveredDevice>,
    scanning: Boolean,
    diagnosticReport: String,
    inspectingAddress: String?,
    onMic: () -> Unit,
    onLaunchRemote: () -> Unit,
    onScan: () -> Unit,
    onStopScan: () -> Unit,
    onInspectDevice: (DiscoveredDevice) -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "CinePilot",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = "Integration proof of concept",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // Voice section
            SectionCard(title = "Voice command") {
                Text(voiceStatus, color = MaterialTheme.colorScheme.onSurface)
                Spacer(Modifier.height(12.dp))
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(84.dp)
                            .background(
                                brush = Brush.horizontalGradient(
                                    listOf(Color(0xFF0061A4), Color(0xFF00B4D8))
                                ),
                                shape = CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Button(
                            onClick = onMic,
                            shape = CircleShape,
                            modifier = Modifier.size(84.dp)
                        ) {
                            Text("MIC", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                LabeledValue("Transcript", transcript)
                LabeledValue("Intent", intentText)
            }

            // TV control section
            SectionCard(title = "TV control test") {
                Button(
                    onClick = onLaunchRemote,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Launch Jio remote app")
                }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = if (scanning) onStopScan else onScan,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(if (scanning) "Stop scan" else "Scan for STB (BLE)")
                }
                Spacer(Modifier.height(12.dp))
                Text(
                    text = controlLog,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Discovered devices
            SectionCard(title = "Discovered devices (${devices.size})") {
                if (devices.isEmpty()) {
                    Text(
                        "No devices yet. Scanning only lists advertisements; it does not connect or send commands.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Text(
                        "CinePilot never connects on its own. Inspect one device at a time; " +
                            "inspection reads the GATT table and writes nothing.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                    devices.take(25).forEach { device ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = device.name ?: "(no name)",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "${device.address} · ${device.rssi} dBm",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            val busy = inspectingAddress == device.address
                            OutlinedButton(
                                onClick = { onInspectDevice(device) },
                                enabled = inspectingAddress == null,
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(if (busy) "…" else "Inspect", fontSize = 12.sp)
                            }
                        }
                        Spacer(Modifier.height(4.dp))
                    }
                }
            }

            // GATT diagnostic output for the single device the user chose
            SectionCard(title = "GATT diagnostic report") {
                Text(
                    text = diagnosticReport,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                )
            }

            // Limitations, stated plainly
            SectionCard(title = "Known limitations") {
                Text(
                    text = "• The Jio remote intent opens the remote app UI; it does not send keys to the STB.\n" +
                        "• BLE discovery enumerates advertisements only; no command bytes are sent.\n" +
                        "• GATT inspection is read-only: services and properties are listed, nothing is written.\n" +
                        "• Seeing a device in a scan is not evidence that BLE carries the remote protocol.\n" +
                        "• Direct STB control is unverified; see docs/specs/04_integration_evidence.md.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun SectionCard(title: String, content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(8.dp))
            content()
        }
    }
}

@Composable
private fun LabeledValue(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "$label: ",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(88.dp)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}