package com.movin.app.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.OpenInBrowser
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.QrCode2
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.movin.app.data.model.ServiceItem
import com.movin.app.ui.MovinUiState
import com.movin.app.util.SystemHelpers

@Composable
fun DashboardScreen(
    state: MovinUiState,
    onToggleMaster: (Boolean) -> Unit,
    onServiceAction: (String, String) -> Unit
) {
    val context = LocalContext.current
    var qrUrl by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Master Status Header Card
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = if (state.serverOnline) "Server Online" else "Server Offline",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = if (state.serverOnline) "Wake-Lock active · LAN reachable" else "Toggle to start background daemon",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (state.serverOnline) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(checked = state.serverOnline, onCheckedChange = onToggleMaster)
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.background,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                    ) {
                        Text(
                            text = state.status.lanIp,
                            fontFamily = FontFamily.Monospace,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }
                    Row {
                        IconButton(onClick = { copyToClipboard(context, "LAN IP", state.status.lanIp) }) {
                            Icon(Icons.Rounded.ContentCopy, contentDescription = "Copy IP")
                        }
                        IconButton(onClick = { qrUrl = "http://${state.status.lanIp}:8096" }) {
                            Icon(Icons.Rounded.QrCode2, contentDescription = "Show QR")
                        }
                    }
                }
            }
        }

        // 3-Column Telemetry Strip
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            TelemetryTile("CPU", "${state.status.cpuPercent}%", Modifier.weight(1f))
            TelemetryTile("RAM", "${state.status.ramUsedMb} MB", Modifier.weight(1f))
            TelemetryTile("FREE", "${state.status.diskFreeGb} GB", Modifier.weight(1f))
        }

        // Service Cards
        state.status.services.forEach { service ->
            ServiceCard(
                service = service,
                lanIp = state.status.lanIp,
                onAction = { action -> onServiceAction(service.id, action) },
                onShowQr = { url -> qrUrl = url }
            )
        }

        Spacer(Modifier.height(12.dp))
    }

    qrUrl?.let { url ->
        val bmp = remember(url) { SystemHelpers.generateQrBitmap(url) }
        AlertDialog(
            onDismissRequest = { qrUrl = null },
            title = { Text("Scan to Open") },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    bmp?.let {
                        Image(
                            bitmap = it.asImageBitmap(),
                            contentDescription = "QR Code",
                            modifier = Modifier.size(210.dp)
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(url, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = {
                TextButton(onClick = { qrUrl = null }) { Text("Close") }
            }
        )
    }
}

@Composable
private fun TelemetryTile(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, fontFamily = FontFamily.Monospace)
        }
    }
}

@Composable
private fun ServiceCard(
    service: ServiceItem,
    lanIp: String,
    onAction: (String) -> Unit,
    onShowQr: (String) -> Unit
) {
    val context = LocalContext.current
    val statusColor = if (service.running) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(service.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(service.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Surface(
                    shape = RoundedCornerShape(50),
                    color = statusColor.copy(alpha = 0.14f)
                ) {
                    Text(
                        text = if (service.running) "Running · :${service.port}" else "Stopped · :${service.port}",
                        color = statusColor,
                        fontFamily = FontFamily.Monospace,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalIconButton(onClick = { onAction(if (service.running) "stop" else "start") }) {
                        Icon(
                            if (service.running) Icons.Rounded.Stop else Icons.Rounded.PlayArrow,
                            contentDescription = if (service.running) "Stop" else "Start"
                        )
                    }
                    FilledTonalIconButton(onClick = { onAction("restart") }) {
                        Icon(Icons.Rounded.Refresh, contentDescription = "Restart")
                    }
                }

                if (service.id == "openssh") {
                    val sshCmd = "ssh -p ${service.port} $lanIp"
                    OutlinedButton(onClick = { copyToClipboard(context, "SSH Command", sshCmd) }) {
                        Icon(Icons.Rounded.ContentCopy, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.size(6.dp))
                        Text("Copy SSH", fontFamily = FontFamily.Monospace)
                    }
                } else {
                    val url = "http://$lanIp:${service.port}"
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        IconButton(onClick = { onShowQr(url) }) {
                            Icon(Icons.Rounded.QrCode2, contentDescription = "QR")
                        }
                        OutlinedButton(onClick = {
                            runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
                        }) {
                            Icon(Icons.Rounded.OpenInBrowser, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.size(6.dp))
                            Text(if (service.id == "jellyfin") "Open Web UI" else "Open Files")
                        }
                    }
                }
            }
        }
    }
}

private fun copyToClipboard(context: Context, label: String, text: String) {
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    cm.setPrimaryClip(ClipData.newPlainText(label, text))
}
