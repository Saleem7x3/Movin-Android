package com.movin.app.ui

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.movin.app.data.BootstrapManager
import com.movin.app.data.MovinApiClient
import com.movin.app.data.model.BootstrapStepState
import com.movin.app.data.model.MovinConfig
import com.movin.app.data.model.ServerStatusResponse
import com.movin.app.data.model.SetupProgress
import com.movin.app.data.model.ThemeMode
import com.movin.app.service.MovinServerService
import com.movin.app.util.SystemHelpers
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class MovinUiState(
    val isBootstrapped: Boolean = false,
    val serverOnline: Boolean = false,
    val status: ServerStatusResponse = ServerStatusResponse(lanIp = SystemHelpers.getDeviceLanIp()),
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val autostartOnBoot: Boolean = false,
    val mdnsEnabled: Boolean = true,
    val selectedLogService: String = "all",
    val logLines: List<String> = listOf("Waiting for Movin server logs..."),
    val shellLines: List<String> = listOf("Movin Linux Shell ready. Type a command below."),
    val setupProgress: SetupProgress = SetupProgress(
        steps = listOf(
            BootstrapStepState("rootfs", "Extracting Linux runtime"),
            BootstrapStepState("bridge", "Deploying Movin control plane"),
            BootstrapStepState("packages", "Installing Jellyfin, FFmpeg & OpenSSH"),
            BootstrapStepState("python", "Installing Flask & Copyparty")
        ),
        progress = 0f,
        currentLog = "Ready to initialize Movin runtime."
    ),
    val isSettingUp: Boolean = false,
    val snackbarMessage: String? = null
)

class MovinViewModel(application: Application) : AndroidViewModel(application) {

    private val context = application.applicationContext
    private val prefs = context.getSharedPreferences("movin_prefs", Context.MODE_PRIVATE)
    private val bootstrapManager = BootstrapManager(context)
    private val apiClient = MovinApiClient()

    private val _uiState = MutableStateFlow(
        MovinUiState(
            isBootstrapped = bootstrapManager.isBootstrapInstalled(),
            themeMode = ThemeMode.entries.getOrNull(prefs.getInt("theme_mode", 0)) ?: ThemeMode.SYSTEM,
            autostartOnBoot = prefs.getBoolean("autostart_on_boot", false),
            mdnsEnabled = prefs.getBoolean("mdns_enabled", true)
        )
    )
    val uiState: StateFlow<MovinUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            MovinServerService.isRunning.collect { running ->
                _uiState.update { it.copy(serverOnline = running) }
            }
        }
        startPollingLoop()
    }

    private fun startPollingLoop() {
        viewModelScope.launch {
            while (isActive) {
                if (_uiState.value.serverOnline) {
                    apiClient.fetchStatus().onSuccess { status ->
                        _uiState.update { it.copy(status = status) }
                    }
                    apiClient.fetchLogs(_uiState.value.selectedLogService).onSuccess { res ->
                        _uiState.update { it.copy(logLines = res.lines) }
                    }
                }
                delay(3000)
            }
        }
    }

    fun startBootstrap() {
        if (_uiState.value.isSettingUp) return
        viewModelScope.launch {
            _uiState.update { it.copy(isSettingUp = true) }
            bootstrapManager.runInitialSetup().collect { progress ->
                _uiState.update {
                    it.copy(
                        setupProgress = progress,
                        isSettingUp = !progress.isFinished && progress.error == null,
                        isBootstrapped = progress.isFinished || it.isBootstrapped
                    )
                }
                if (progress.isFinished) {
                    MovinServerService.start(context)
                }
            }
        }
    }

    fun skipOnboardingForPreview() {
        _uiState.update { it.copy(isBootstrapped = true) }
    }

    fun toggleMasterServer(enable: Boolean) {
        if (enable) {
            MovinServerService.start(context)
        } else {
            MovinServerService.stop(context)
        }
    }

    fun controlService(serviceId: String, action: String) {
        viewModelScope.launch {
            if (!_uiState.value.serverOnline && action == "start") {
                MovinServerService.start(context)
                delay(1200)
            }
            apiClient.controlService(serviceId, action)
                .onSuccess { res ->
                    _uiState.update { it.copy(snackbarMessage = res.message.ifBlank { "$serviceId: $action" }) }
                    apiClient.fetchStatus().onSuccess { st -> _uiState.update { it.copy(status = st) } }
                }
                .onFailure { err ->
                    _uiState.update { it.copy(snackbarMessage = "Bridge offline: ${err.message}") }
                }
        }
    }

    fun selectLogFilter(serviceId: String) {
        _uiState.update { it.copy(selectedLogService = serviceId) }
        viewModelScope.launch {
            apiClient.fetchLogs(serviceId).onSuccess { res ->
                _uiState.update { it.copy(logLines = res.lines) }
            }
        }
    }

    fun runShellCommand(command: String) {
        if (command.isBlank()) return
        _uiState.update { it.copy(shellLines = (it.shellLines + "$ $command").takeLast(200)) }
        viewModelScope.launch(Dispatchers.IO) {
            if (!bootstrapManager.isBootstrapInstalled()) {
                _uiState.update {
                    it.copy(shellLines = (it.shellLines + "[movin] Linux runtime not initialized yet.").takeLast(200))
                }
                return@launch
            }
            runCatching {
                bootstrapManager.runShellCommand(command) { line ->
                    _uiState.update { it.copy(shellLines = (it.shellLines + line).takeLast(200)) }
                }
            }.onFailure { err ->
                _uiState.update { it.copy(shellLines = (it.shellLines + "[error] ${err.message}").takeLast(200)) }
            }
        }
    }

    fun updateMediaDir(newDir: String) {
        if (newDir.isBlank()) return
        viewModelScope.launch {
            val updated = MovinConfig(mediaDir = newDir)
            apiClient.updateConfig(updated)
            _uiState.update {
                it.copy(
                    status = it.status.copy(mediaDir = newDir),
                    snackbarMessage = "Media folder set to $newDir"
                )
            }
        }
    }

    fun setThemeMode(mode: ThemeMode) {
        prefs.edit().putInt("theme_mode", mode.ordinal).apply()
        _uiState.update { it.copy(themeMode = mode) }
    }

    fun setAutostartOnBoot(enabled: Boolean) {
        prefs.edit().putBoolean("autostart_on_boot", enabled).apply()
        _uiState.update { it.copy(autostartOnBoot = enabled) }
    }

    fun setMdnsEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("mdns_enabled", enabled).apply()
        _uiState.update { it.copy(mdnsEnabled = enabled) }
    }

    fun clearSnackbar() {
        _uiState.update { it.copy(snackbarMessage = null) }
    }
}
