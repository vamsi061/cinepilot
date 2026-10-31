package com.cinepilot.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cinepilot.app.STBConnectionState
import com.cinepilot.app.device.RemoteKey

@Composable
fun RemoteScreen(
    connectionState: STBConnectionState,
    capabilities: Set<RemoteKey>,
    onOpenOfficialRemote: () -> Unit,
    onReconnect: () -> Unit
) {
    val policy = RemotePresentationPolicy(capabilities)
    Column(
        Modifier.fillMaxWidth().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Remote", fontSize = 28.sp)
        Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFE7F2F4))) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(connectionLabel(connectionState), fontSize = 18.sp)
                Text(
                    if (capabilities.isEmpty()) "No verified direct STB transport is configured."
                    else policy.actionLabel,
                    color = Color.DarkGray
                )
            }
        }
        RemotePad(policy)
        OutlinedButton(onClick = onReconnect, modifier = Modifier.fillMaxWidth()) {
            Text("Reconnect")
        }
        Button(onClick = onOpenOfficialRemote, modifier = Modifier.fillMaxWidth()) {
            Text("Open JioHome remote UI")
        }
        Text(
            "The official app action opens its UI. It does not give CinePilot direct key control.",
            color = Color.Gray,
            fontSize = 12.sp
        )
    }
}

@Composable
private fun RemotePad(policy: RemotePresentationPolicy) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Weighted buttons shrink to fit narrow phones (360dp class) without
        // clipping. Disabled keys stay visible so the unavailable capability
        // is explicit, never hidden or faked.
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            RemoteKeyButton("Power", RemoteKey.POWER, policy)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Spacer(Modifier.weight(1f))
            RemoteKeyButton("▲", RemoteKey.DPAD_UP, policy)
            Spacer(Modifier.weight(1f))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            RemoteKeyButton("◀", RemoteKey.DPAD_LEFT, policy)
            RemoteKeyButton("OK", RemoteKey.OK, policy)
            RemoteKeyButton("▶", RemoteKey.DPAD_RIGHT, policy)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Spacer(Modifier.weight(1f))
            RemoteKeyButton("▼", RemoteKey.DPAD_DOWN, policy)
            Spacer(Modifier.weight(1f))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            RemoteKeyButton("Back", RemoteKey.BACK, policy)
            RemoteKeyButton("Home", RemoteKey.HOME, policy)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            RemoteKeyButton("Vol −", RemoteKey.VOLUME_DOWN, policy)
            RemoteKeyButton("Mute", RemoteKey.MUTE, policy)
            RemoteKeyButton("Vol +", RemoteKey.VOLUME_UP, policy)
        }
    }
}

@Composable
private fun RowScope.RemoteKeyButton(label: String, key: RemoteKey, policy: RemotePresentationPolicy) {
    FilledTonalButton(
        onClick = {},
        enabled = policy.isEnabled(key),
        modifier = Modifier.weight(1f).height(52.dp),
        contentPadding = PaddingValues(0.dp)
    ) { Text(label, fontSize = 13.sp) }
}