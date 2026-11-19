package com.movin.app.ui.screens

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.movin.app.ui.MovinUiState
import com.movin.app.util.SystemHelpers

@Composable
fun OnboardingScreen(
    state: MovinUiState,
    onInitialize: () -> Unit,
    onSkip: () -> Unit
) {
    val context = LocalContext.current
    val hasStorage = SystemHelpers.hasAllFilesAccess()
    val hasBattery = SystemHelpers.isBatteryOptimizationIgnored(context)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Dns,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(12.dp).size(28.dp)
                )
            }
            Column {
                Text(
                    text = "Movin Server",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "Portable Jellyfin, Copyparty & OpenSSH suite",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Permissions Card
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("System Permissions", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("All Files Access (/sdcard/moving)", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            if (hasStorage) "Granted" else "Required for media library",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    OutlinedButton(onClick = { SystemHelpers.requestAllFilesAccess(context) }) {
                        Text(if (hasStorage) "Done" else "Grant")
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Ignore Battery Optimization", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            if (hasBattery) "Unrestricted" else "Prevents Android from killing services",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    OutlinedButton(onClick = { SystemHelpers.requestIgnoreBatteryOptimizations(context) }) {
                        Text(if (hasBattery) "Done" else "Allow")
                    }
                }
            }
        }

        // Setup Checklist Card
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Linux Runtime Setup", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
                LinearProgressIndicator(
                    progress = { state.setupProgress.progress },
                    modifier = Modifier.fillMaxWidth().height(6.dp)
                )
                state.setupProgress.steps.forEach { step ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        when {
                            step.error != null -> Icon(Icons.Rounded.ErrorOutline, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(20.dp))
                            step.completed -> Icon(Icons.Rounded.CheckCircle, null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(20.dp))
                            step.inProgress -> CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                            else -> Icon(Icons.Rounded.RadioButtonUnchecked, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
                        }
                        Text(step.title, style = MaterialTheme.typography.bodyMedium)
                    }
                }
                Text(
                    text = state.setupProgress.currentLog,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3
                )
            }
        }

        Spacer(Modifier.weight(1f))

        Button(
            onClick = onInitialize,
            enabled = !state.isSettingUp,
            shape = RoundedCornerShape(50),
            modifier = Modifier.fillMaxWidth().height(52.dp)
        ) {
            Text(if (state.isSettingUp) "Initializing Runtime..." else "Initialize Server")
        }

        TextButton(
            onClick = onSkip,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        ) {
            Text("Open Dashboard Anyway")
        }
    }
}
