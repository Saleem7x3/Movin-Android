package com.movin.app.data

import android.content.Context
import android.os.Build
import com.movin.app.BuildConfig
import com.movin.app.data.model.BootstrapStepState
import com.movin.app.data.model.SetupProgress
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.BufferedReader
import java.io.File
import java.io.FileOutputStream
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit
import java.util.zip.ZipInputStream

/**
 * Downloads and extracts the Termux ARM64/ARMv7/x86_64 bootstrap into filesDir/usr,
 * fixes symlinks & executable permissions, installs Movin packages (python, ffmpeg,
 * jellyfin-server, openssh, flask, copyparty), and deploys movin_bridge.py.
 */
class BootstrapManager(private val context: Context) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .build()

    val prefixDir: File get() = File(BuildConfig.LINUX_PREFIX)
    val homeDir: File get() = File(BuildConfig.LINUX_HOME)
    val movinDir: File get() = File(homeDir, "movin")
    val bridgeScript: File get() = File(movinDir, "movin_bridge.py")

    fun isBootstrapInstalled(): Boolean {
        val bash = File(prefixDir, "bin/bash")
        return bash.exists() && bash.canExecute() && bridgeScript.exists()
    }

    fun buildLinuxEnvironment(): Map<String, String> {
        val prefix = prefixDir.absolutePath
        val home = homeDir.absolutePath
        val tmp = File(prefixDir, "tmp").apply { mkdirs() }.absolutePath
        return mapOf(
            "PREFIX" to prefix,
            "HOME" to home,
            "TMPDIR" to tmp,
            "PATH" to "$prefix/bin:$prefix/bin/applets:/system/bin:/system/xbin",
            "LD_LIBRARY_PATH" to "$prefix/lib",
            "LANG" to "en_US.UTF-8",
            "TERM" to "xterm-256color"
        )
    }

    private fun bootstrapArchiveUrl(): String {
        val abi = Build.SUPPORTED_ABIS.firstOrNull() ?: "arm64-v8a"
        val arch = when (abi) {
            "arm64-v8a" -> "aarch64"
            "armeabi-v7a" -> "arm"
            "x86_64" -> "x86_64"
            else -> "aarch64"
        }
        return "https://github.com/termux/termux-packages/releases/latest/download/bootstrap-$arch.zip"
    }

    fun runInitialSetup(): Flow<SetupProgress> = flow {
        val steps = mutableListOf(
            BootstrapStepState("rootfs", "Extracting Linux runtime"),
            BootstrapStepState("bridge", "Deploying Movin control plane"),
            BootstrapStepState("packages", "Installing Jellyfin, FFmpeg & OpenSSH"),
            BootstrapStepState("python", "Installing Flask & Copyparty")
        )

        fun snapshot(idx: Int, progress: Float, log: String, finished: Boolean = false, err: String? = null): SetupProgress {
            val updated = steps.mapIndexed { i, s ->
                when {
                    i < idx -> s.copy(completed = true, inProgress = false)
                    i == idx -> s.copy(inProgress = !finished && err == null, completed = finished, error = err)
                    else -> s
                }
            }
            return SetupProgress(updated, progress, log, finished, err)
        }

        try {
            // Step 0: Download & extract Termux bootstrap
            emit(snapshot(0, 0.1f, "Preparing ${prefixDir.absolutePath}..."))
            prefixDir.mkdirs()
            homeDir.mkdirs()
            movinDir.mkdirs()

            if (!File(prefixDir, "bin/bash").exists()) {
                val url = bootstrapArchiveUrl()
                emit(snapshot(0, 0.18f, "Downloading Termux bootstrap ($url)..."))
                val zipFile = File(context.cacheDir, "termux-bootstrap.zip")
                downloadFile(url, zipFile)
                emit(snapshot(0, 0.35f, "Extracting Linux userland & symlinks..."))
                extractBootstrapZip(zipFile, prefixDir)
                zipFile.delete()
            }

            // Step 1: Deploy movin_bridge.py from APK assets
            emit(snapshot(1, 0.48f, "Copying Movin control plane bridge..."))
            context.assets.open("movin_bridge.py").use { input ->
                FileOutputStream(bridgeScript).use { output -> input.copyTo(output) }
            }
            bridgeScript.setExecutable(true, false)

            // Step 2: Install native Linux packages via pkg
            emit(snapshot(2, 0.60f, "Updating package repositories & installing Jellyfin, FFmpeg, OpenSSH..."))
            val pkgCmd = "pkg update -y && pkg install -y python python-pip ffmpeg jellyfin-server openssh iproute2 procps git curl"
            runShellCommand(pkgCmd) { line ->
                emit(snapshot(2, 0.72f, line))
            }

            // Step 3: Install Python dependencies (Flask + Copyparty)
            emit(snapshot(3, 0.85f, "Installing Flask and Copyparty via pip..."))
            runShellCommand("pip install --no-cache-dir flask copyparty") { line ->
                emit(snapshot(3, 0.94f, line))
            }

            emit(snapshot(3, 1.0f, "Movin Linux runtime initialized successfully.", finished = true))
        } catch (e: Exception) {
            emit(snapshot(0, 0f, "Setup failed: ${e.message}", finished = false, err = e.message))
        }
    }.flowOn(Dispatchers.IO)

    private fun downloadFile(url: String, dest: File) {
        val request = Request.Builder().url(url).build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("HTTP ${response.code} downloading bootstrap")
            response.body?.byteStream()?.use { input ->
                FileOutputStream(dest).use { output -> input.copyTo(output) }
            } ?: error("Empty response body")
        }
    }

    private fun extractBootstrapZip(zipFile: File, targetDir: File) {
        val symlinks = mutableListOf<Pair<String, String>>()
        ZipInputStream(zipFile.inputStream()).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                if (entry.name == "SYMLINKS.txt") {
                    val lines = BufferedReader(InputStreamReader(zis)).readLines()
                    for (line in lines) {
                        val parts = line.split("←")
                        if (parts.size == 2) symlinks.add(parts[0] to parts[1])
                    }
                } else {
                    val outFile = File(targetDir, entry.name)
                    if (entry.isDirectory) {
                        outFile.mkdirs()
                    } else {
                        outFile.parentFile?.mkdirs()
                        FileOutputStream(outFile).use { zis.copyTo(it) }
                        if (entry.name.startsWith("bin/") || entry.name.startsWith("libexec/") || entry.name.startsWith("lib/apt/methods/")) {
                            outFile.setExecutable(true, false)
                        }
                    }
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }
        // Recreate symlinks via Os.symlink
        for ((target, linkPath) in symlinks) {
            val linkFile = File(targetDir, linkPath)
            linkFile.parentFile?.mkdirs()
            if (linkFile.exists()) linkFile.delete()
            runCatching { android.system.Os.symlink(target, linkFile.absolutePath) }
        }
    }

    suspend fun runShellCommand(command: String, onLine: suspend (String) -> Unit): Int {
        val bash = File(prefixDir, "bin/bash").absolutePath
        val pb = ProcessBuilder(bash, "-lc", command)
        pb.directory(homeDir)
        pb.environment().putAll(buildLinuxEnvironment())
        pb.redirectErrorStream(true)
        val process = pb.start()
        BufferedReader(InputStreamReader(process.inputStream)).use { reader ->
            var line = reader.readLine()
            while (line != null) {
                onLine(line)
                line = reader.readLine()
            }
        }
        return process.waitFor()
    }
}
