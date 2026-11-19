package com.movin.app.data

import com.movin.app.data.model.MovinConfig
import com.movin.app.data.model.ServerStatusResponse
import com.movin.app.data.model.ServiceActionResponse
import com.movin.app.data.model.ServiceLogsResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

class MovinApiClient(
    private val baseUrl: String = "http://127.0.0.1:8080"
) {
    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(3, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .build()

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    suspend fun fetchStatus(): Result<ServerStatusResponse> = withContext(Dispatchers.IO) {
        runCatching {
            val req = Request.Builder().url("$baseUrl/api/status").get().build()
            client.newCall(req).execute().use { res ->
                if (!res.isSuccessful) error("HTTP ${res.code}")
                val body = res.body?.string() ?: error("Empty response")
                json.decodeFromString<ServerStatusResponse>(body)
            }
        }
    }

    suspend fun controlService(serviceId: String, action: String): Result<ServiceActionResponse> =
        withContext(Dispatchers.IO) {
            runCatching {
                val req = Request.Builder()
                    .url("$baseUrl/api/services/$serviceId/$action")
                    .post("{}".toRequestBody(jsonMediaType))
                    .build()
                client.newCall(req).execute().use { res ->
                    val body = res.body?.string() ?: "{}"
                    json.decodeFromString<ServiceActionResponse>(body)
                }
            }
        }

    suspend fun fetchLogs(serviceId: String, lines: Int = 120): Result<ServiceLogsResponse> =
        withContext(Dispatchers.IO) {
            runCatching {
                val req = Request.Builder()
                    .url("$baseUrl/api/logs/$serviceId?lines=$lines")
                    .get()
                    .build()
                client.newCall(req).execute().use { res ->
                    if (!res.isSuccessful) error("HTTP ${res.code}")
                    val body = res.body?.string() ?: "{}"
                    json.decodeFromString<ServiceLogsResponse>(body)
                }
            }
        }

    suspend fun updateConfig(config: MovinConfig): Result<Boolean> = withContext(Dispatchers.IO) {
        runCatching {
            val payload = json.encodeToString(config)
            val req = Request.Builder()
                .url("$baseUrl/api/config")
                .post(payload.toRequestBody(jsonMediaType))
                .build()
            client.newCall(req).execute().use { it.isSuccessful }
        }
    }
}
