package com.movin.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Dashboard
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.viewmodel.compose.viewModel
import com.movin.app.ui.MovinViewModel
import com.movin.app.ui.screens.DashboardScreen
import com.movin.app.ui.screens.LogsTerminalScreen
import com.movin.app.ui.screens.OnboardingScreen
import com.movin.app.ui.screens.SettingsScreen
import com.movin.app.ui.screens.StorageScreen
import com.movin.app.ui.theme.MovinTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MovinApp()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MovinApp(viewModel: MovinViewModel = viewModel()) {
    val state by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var selectedTab by remember { mutableIntStateOf(0) }

    LaunchedEffect(state.snackbarMessage) {
        state.snackbarMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearSnackbar()
        }
    }

    MovinTheme(themeMode = state.themeMode) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            if (!state.isBootstrapped) {
                OnboardingScreen(
                    state = state,
                    onInitialize = { viewModel.startBootstrap() },
                    onSkip = { viewModel.skipOnboardingForPreview() }
                )
            } else {
                val tabs = listOf(
                    "Dashboard" to Icons.Rounded.Dashboard,
                    "Storage" to Icons.Rounded.Folder,
                    "Terminal" to Icons.Rounded.Terminal,
                    "Settings" to Icons.Rounded.Settings
                )
                Scaffold(
                    containerColor = MaterialTheme.colorScheme.background,
                    snackbarHost = { SnackbarHost(snackbarHostState) },
                    topBar = {
                        CenterAlignedTopAppBar(
                            title = { Text("Movin", fontWeight = FontWeight.SemiBold) },
                            colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                                containerColor = MaterialTheme.colorScheme.background
                            )
                        )
                    },
                    bottomBar = {
                        NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                            tabs.forEachIndexed { index, (label, icon) ->
                                NavigationBarItem(
                                    selected = selectedTab == index,
                                    onClick = { selectedTab = index },
                                    icon = { Icon(icon, contentDescription = label) },
                                    label = { Text(label) }
                                )
                            }
                        }
                    }
                ) { padding ->
                    Box(modifier = Modifier.padding(padding)) {
                        when (selectedTab) {
                            0 -> DashboardScreen(
                                state = state,
                                onToggleMaster = viewModel::toggleMasterServer,
                                onServiceAction = viewModel::controlService
                            )
                            1 -> StorageScreen(
                                state = state,
                                onUpdateMediaDir = viewModel::updateMediaDir
                            )
                            2 -> LogsTerminalScreen(
                                state = state,
                                onSelectLogFilter = viewModel::selectLogFilter,
                                onRunShellCommand = viewModel::runShellCommand
                            )
                            3 -> SettingsScreen(
                                state = state,
                                onSelectTheme = viewModel::setThemeMode,
                                onToggleAutostart = viewModel::setAutostartOnBoot,
                                onToggleMdns = viewModel::setMdnsEnabled,
                                onReinstallRuntime = { viewModel.startBootstrap() }
                            )
                        }
                    }
                }
            }
        }
    }
}
