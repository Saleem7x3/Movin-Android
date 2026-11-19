package com.movin.app.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ServiceItem(
    val id: String,
    val name: String,
    val port: Int,
    val running: Boolean,
    val description: String = ""
)

@Serializable
data class ServerStatusResponse(
    @SerialName("lan_ip") val lanIp: String = "127.0.0.1",
    @SerialName("media_dir") val mediaDir: String = "/sdcard/moving",
    @SerialName("cpu_percent") val cpuPercent: Int = 0,
    @SerialName("ram_used_mb") val ramUsedMb: Int = 0,
    @SerialName("ram_total_mb") val ramTotalMb: Int = 1,
    @SerialName("disk_free_gb") val diskFreeGb: Double = 0.0,
    @SerialName("disk_total_gb") val diskTotalGb: Double = 0.0,
    val services: List<ServiceItem> = defaultServices()
)

@Serializable
data class ServiceActionResponse(
    val ok: Boolean = false,
    val message: String = ""
)

@Serializable
data class ServiceLogsResponse(
    val service: String = "all",
    val lines: List<String> = emptyList()
)

@Serializable
data class MovinConfig(
    @SerialName("media_dir") val mediaDir: String = "/sdcard/moving",
    val ports: Map<String, Int> = mapOf(
        "dashboard" to 8080,
        "jellyfin" to 8096,
        "copyparty" to 3923,
        "openssh" to 8022
    )
)

enum class ThemeMode(val label: String) {
    SYSTEM("System"),
    APPLE_WHITE("Apple White"),
    OLED_DARK("OLED Dark")
}

data class BootstrapStepState(
    val id: String,
    val title: String,
    val completed: Boolean = false,
    val inProgress: Boolean = false,
    val error: String? = null
)

data class SetupProgress(
    val steps: List<BootstrapStepState>,
    val progress: Float,
    val currentLog: String,
    val isFinished: Boolean = false,
    val error: String? = null
)

fun defaultServices(): List<ServiceItem> = listOf(
    ServiceItem("jellyfin", "Jellyfin Media Server", 8096, false, "Local movie, TV, and music streaming"),
    ServiceItem("copyparty", "Copyparty File Hub", 3923, false, "WebDAV, uploads, and LAN file sharing"),
    ServiceItem("openssh", "OpenSSH Server", 8022, false, "Remote shell and SFTP access")
)
