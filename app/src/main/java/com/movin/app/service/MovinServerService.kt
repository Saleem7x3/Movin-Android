package com.movin.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.wifi.WifiManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import com.movin.app.MainActivity
import com.movin.app.data.BootstrapManager
import com.movin.app.util.MdnsAdvertiser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.io.File

/**
 * Persistent ForegroundService that holds a PARTIAL_WAKE_LOCK and WifiLock,
 * launches Movin's localhost control plane (movin_bridge.py on :8080), and
 * keeps Jellyfin (:8096), Copyparty (:3923), and OpenSSH (:8022) alive.
 */
class MovinServerService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var wakeLock: PowerManager.WakeLock? = null
    private var wifiLock: WifiManager.WifiLock? = null
    private var bridgeProcess: Process? = null
    private var mdnsAdvertiser: MdnsAdvertiser? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        mdnsAdvertiser = MdnsAdvertiser(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopServerStack()
            stopSelf()
            return START_NOT_STICKY
        }

        startForeground(NOTIFICATION_ID, buildNotification("Movin server stack active"))
        acquireLocks()
        mdnsAdvertiser?.register(8080)
        startBridgeDaemon()
        _isRunning.value = true
        return START_STICKY
    }

    private fun startBridgeDaemon() {
        if (bridgeProcess?.isAlive == true) return
        scope.launch {
            val bootstrap = BootstrapManager(applicationContext)
            if (!bootstrap.isBootstrapInstalled()) return@launch

            val pythonBin = File(bootstrap.prefixDir, "bin/python3").absolutePath
            val logDir = File(bootstrap.movinDir, "logs").apply { mkdirs() }
            val bridgeLog = File(logDir, "bridge.log")

            val pb = ProcessBuilder(pythonBin, bootstrap.bridgeScript.absolutePath)
            pb.directory(bootstrap.movinDir)
            pb.environment().putAll(bootstrap.buildLinuxEnvironment())
            pb.redirectErrorStream(true)
            pb.redirectOutput(ProcessBuilder.Redirect.appendTo(bridgeLog))

            runCatching {
                bridgeProcess = pb.start()
            }
        }
    }

    private fun stopServerStack() {
        _isRunning.value = false
        mdnsAdvertiser?.unregister()
        runCatching { bridgeProcess?.destroy() }
        bridgeProcess = null
        releaseLocks()
    }

    private fun acquireLocks() {
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        if (wakeLock?.isHeld != true) {
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Movin::ServerWakeLock").apply {
                acquire()
            }
        }
        val wm = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        if (wifiLock?.isHeld != true) {
            @Suppress("DEPRECATION")
            val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                WifiManager.WIFI_MODE_FULL_LOW_LATENCY
            } else {
                WifiManager.WIFI_MODE_FULL_HIGH_PERF
            }
            wifiLock = wm.createWifiLock(mode, "Movin::WifiLock").apply { acquire() }
        }
    }

    private fun releaseLocks() {
        runCatching { if (wakeLock?.isHeld == true) wakeLock?.release() }
        runCatching { if (wifiLock?.isHeld == true) wifiLock?.release() }
        wakeLock = null
        wifiLock = null
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Movin Server Daemon",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Keeps Movin home server services alive in the background"
            }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun buildNotification(statusText: String): Notification {
        val openIntent = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val stopIntent = PendingIntent.getService(
            this, 1, Intent(this, MovinServerService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Movin Portable Server")
            .setContentText(statusText)
            .setSmallIcon(android.R.drawable.stat_sys_upload_done)
            .setOngoing(true)
            .setContentIntent(openIntent)
            .addAction(android.R.drawable.ic_media_pause, "Stop All", stopIntent)
            .build()
    }

    override fun onDestroy() {
        stopServerStack()
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        const val CHANNEL_ID = "movin_server_channel"
        const val NOTIFICATION_ID = 7001
        const val ACTION_START = "com.movin.app.ACTION_START"
        const val ACTION_STOP = "com.movin.app.ACTION_STOP"

        private val _isRunning = MutableStateFlow(false)
        val isRunning: StateFlow<Boolean> = _isRunning

        fun start(context: Context) {
            val intent = Intent(context, MovinServerService::class.java).setAction(ACTION_START)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, MovinServerService::class.java).setAction(ACTION_STOP)
            context.startService(intent)
        }
    }
}
