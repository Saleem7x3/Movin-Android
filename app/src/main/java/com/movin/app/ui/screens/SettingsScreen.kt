package com.movin.app.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.movin.app.BuildConfig
import com.movin.app.data.model.ThemeMode
import com.movin.app.ui.MovinUiState
import com.movin.app.util.SystemHelpers

@Composable
fun SettingsScreen(
    state: MovinUiState,
    onSelectTheme: (ThemeMode) -> Unit,
    onToggleAutostart: (Boolean) -> Unit,
    onToggleMdns: (Boolean) -> Unit,
    onReinstallRuntime: () -> Unit
) {
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Appearance Card
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Appearance", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    ThemeMode.entries.forEachIndexed { idx, mode ->
                        SegmentedButton(
                            selected = state.themeMode == mode,
                            onClick = { onSelectTheme(mode) },
                            shape = SegmentedButtonDefaults.itemShape(index = idx, count = ThemeMode.entries.size)
                        ) {
                            Text(mode.label, maxLines = 1)
                        }
                    }
                }
            }
        }

        // Background & Network Card
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Background & Network", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Auto-start on boot", style = MaterialTheme.typography.bodyMedium)
                        Text("Launch Movin server after reboot", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(checked = state.autostartOnBoot, onCheckedChange = onToggleAutostart)
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("mDNS Local Discovery", style = MaterialTheme.typography.bodyMedium)
                        Text("Broadcast Movin on local Wi-Fi", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(checked = state.mdnsEnabled, onCheckedChange = onToggleMdns)
                }
                OutlinedButton(
                    onClick = { SystemHelpers.requestIgnoreBatteryOptimizations(context) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        if (SystemHelpers.isBatteryOptimizationIgnored(context))
                            "Battery Optimization: Unrestricted"
                        else
                            "Disable Battery Optimization"
                    )
                }
            }
        }

        // Runtime Info & Maintenance
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Linux Runtime", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    "Flavor: ${if (BuildConfig.IS_NATIVE_TERMUX_PREFIX) "Native Termux (com.termux)" else "Standalone (com.movin.app)"}\nPrefix: ${BuildConfig.LINUX_PREFIX}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedButton(onClick = onReinstallRuntime, modifier = Modifier.fillMaxWidth()) {
                    Text("Re-run Runtime Bootstrap")
                }
            }
        }
    }
}
