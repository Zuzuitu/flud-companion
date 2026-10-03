package media.alexlab.fludremote

import android.content.Context
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.min

class CloudRelayClient(context: Context) {
    enum class State { DISABLED, CONNECTING, CONNECTED, DISCONNECTED, STOPPED }
    data class Snapshot(val state: State, val detail: String)

    companion object {
        private val BRIDGE_VERSION: String = BuildConfig.VERSION_NAME
        private const val POLL_SECONDS = 2L
        @Volatile private var currentState: State = State.STOPPED
        @Volatile private var currentDetail: String = "Not started"
        fun snapshot(): Snapshot = Snapshot(currentState, currentDetail)
        private fun setState(state: State, detail: String) { currentState = state; currentDetail = detail }
    }

    private val appContext = context.applicationContext
    private val shouldRun = AtomicBoolean(false)
    private val scheduler: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor()
    private val jsonType = "application/json; charset=utf-8".toMediaType()
    private val client = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .callTimeout(25, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()
    @Volatile private var reconnectAttempt = 0

    fun start() {
        if (!BridgePreferences.cloudEnabled(appContext)) { setState(State.DISABLED, "Remote relay disabled"); return }
        val base = BridgePreferences.cloudBaseUrl(appContext)
        if (!isValidRelayUrl(base)) { setState(State.DISABLED, "Relay URL not configured"); return }
        if (!shouldRun.compareAndSet(false, true)) return
        setState(State.CONNECTING, "Connecting to self-hosted relay")
        schedulePoll(0)
    }

    fun stop() {
        shouldRun.set(false)
        scheduler.shutdownNow()
        client.dispatcher.cancelAll()
        setState(State.STOPPED, "Stopped")
    }

    private fun schedulePoll(delaySeconds: Long) {
        if (!shouldRun.get() || scheduler.isShutdown) return
        try { scheduler.schedule({ pollOnce() }, delaySeconds, TimeUnit.SECONDS) } catch (_: Exception) {}
    }

    private fun pollOnce() {
        if (!shouldRun.get()) return
        val base = BridgePreferences.cloudBaseUrl(appContext)
        if (!isValidRelayUrl(base)) { setState(State.DISCONNECTED, "Relay URL not configured"); return }
        val deviceId = BridgePreferences.cloudDeviceId(appContext)
        val token = BridgePreferences.cloudToken(appContext)
        val request = Request.Builder()
            .url("$base/bridge/poll/$deviceId")
            .header("Authorization", "Bearer $token")
            .header("X-Flud-Bridge-Version", BRIDGE_VERSION)
            .header("X-Flud-AutoStart", if (FludAutoStartService.isEnabled(appContext)) "ready" else "off")
            .header("X-Flud-AutoStart-Mode", FludAutoStartService.strategy())
            .get().build()
        try {
            client.newCall(request).execute().use { response ->
                val bodyText = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    val detail = try { JSONObject(bodyText).optString("error", "HTTP ${response.code}") } catch (_: Exception) { "HTTP ${response.code}" }
                    setState(State.DISCONNECTED, detail); scheduleReconnect(); return
                }
                val payload = try { JSONObject(bodyText) } catch (_: Exception) { setState(State.DISCONNECTED, "Invalid response from relay"); scheduleReconnect(); return }
                reconnectAttempt = 0
                setState(State.CONNECTED, "Connected to $base — HTTPS polling")
                payload.optJSONObject("command")?.let { handleCommand(base, deviceId, token, it) }
                schedulePoll(POLL_SECONDS)
            }
        } catch (t: Throwable) {
            if (!shouldRun.get()) return
            setState(State.DISCONNECTED, "Connection failed: ${t.message ?: t.javaClass.simpleName}")
            scheduleReconnect()
        }
    }

    private fun scheduleReconnect() {
        if (!shouldRun.get() || scheduler.isShutdown) return
        reconnectAttempt += 1
        val delays = intArrayOf(2, 5, 10, 20, 30, 45, 60)
        schedulePoll(delays[min(reconnectAttempt - 1, delays.lastIndex)].toLong())
    }

    private fun handleCommand(base: String, deviceId: String, token: String, command: JSONObject) {
        val type = command.optString("type")
        val id = command.optString("id")
        if (id.isBlank()) return

        when (type) {
            "magnet" -> handleMagnet(base, deviceId, token, id, command)
            "torrent" -> handleTorrent(base, deviceId, token, id, command)
            else -> postResult(base, deviceId, token, id, false, "Unsupported remote command", null)
        }
    }

    private fun handleMagnet(base: String, deviceId: String, token: String, id: String, command: JSONObject) {
        val magnet = command.optString("magnet")
        val autoStart = command.optBoolean("autoStart", false)
        val validMagnet = magnet.startsWith("magnet:?", ignoreCase = true) && magnet.length <= 12_000
        val helperReady = validMagnet && autoStart && FludAutoStartService.isEnabled(appContext)
        val result = when {
            !validMagnet -> FludLauncher.Result(false, message = "Invalid magnet URI from relay")
            helperReady -> FludAutoStartCoordinator.submit(appContext, magnet)
            else -> FludLauncher.launchMagnet(appContext, magnet)
        }
        val resultMessage = if (result.success && autoStart) {
            if (helperReady) result.message
            else "${result.message}; auto-start requested but the Flud Companion accessibility helper is not enabled"
        } else result.message
        BridgePreferences.recordLastCommand(appContext, "Remote: $resultMessage", result.success)
        postResult(base, deviceId, token, id, result.success, resultMessage, result.packageName)
    }

    private fun handleTorrent(base: String, deviceId: String, token: String, id: String, command: JSONObject) {
        val filename = command.optString("filename", "download.torrent")
        val declaredSize = command.optLong("size", -1L)
        val autoStart = command.optBoolean("autoStart", false)
        if (declaredSize <= 0L || declaredSize > TorrentFileSupport.MAX_TORRENT_BYTES) {
            val message = "Invalid remote .torrent size"
            BridgePreferences.recordLastCommand(appContext, "Remote torrent: $message", false)
            postResult(base, deviceId, token, id, false, message, null)
            return
        }

        val bytes = downloadTorrent(base, deviceId, token, id, declaredSize)
        if (bytes == null) {
            val message = "Could not download queued .torrent file from relay"
            BridgePreferences.recordLastCommand(appContext, "Remote torrent: $message", false)
            postResult(base, deviceId, token, id, false, message, null)
            return
        }

        val stored = try {
            TorrentFileSupport.store(appContext, bytes, filename)
        } catch (e: Exception) {
            val message = e.message ?: "Invalid .torrent file from relay"
            BridgePreferences.recordLastCommand(appContext, "Remote torrent: $message", false)
            postResult(base, deviceId, token, id, false, message, null)
            return
        }

        val helperReady = autoStart && FludAutoStartService.isEnabled(appContext)
        val result = if (helperReady) {
            FludAutoStartCoordinator.submitTorrent(appContext, stored)
        } else {
            TorrentFileLauncher.launch(appContext, stored)
        }
        if (!result.success) TorrentFileSupport.delete(stored)

        val resultMessage = if (result.success && autoStart) {
            if (helperReady) result.message
            else "${result.message}; auto-start requested but the Flud Companion accessibility helper is not enabled"
        } else result.message
        BridgePreferences.recordLastCommand(appContext, "Remote torrent: $resultMessage", result.success)
        postResult(base, deviceId, token, id, result.success, resultMessage, result.packageName)
    }

    private fun downloadTorrent(
        base: String,
        deviceId: String,
        token: String,
        commandId: String,
        declaredSize: Long
    ): ByteArray? {
        val request = Request.Builder()
            .url("$base/bridge/file/$deviceId/$commandId")
            .header("Authorization", "Bearer $token")
            .get()
            .build()
        return try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                val length = response.body?.contentLength() ?: -1L
                if (length > TorrentFileSupport.MAX_TORRENT_BYTES || (length >= 0L && length != declaredSize)) return null
                val bytes = response.body?.bytes() ?: return null
                if (bytes.isEmpty() || bytes.size > TorrentFileSupport.MAX_TORRENT_BYTES || bytes.size.toLong() != declaredSize) null else bytes
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun postResult(
        base: String,
        deviceId: String,
        token: String,
        id: String,
        ok: Boolean,
        message: String?,
        packageName: String?
    ) {
        val payload = JSONObject().put("id", id).put("ok", ok)
        if (!message.isNullOrBlank()) payload.put("message", message)
        if (!packageName.isNullOrBlank()) payload.put("package", packageName)
        val request = Request.Builder()
            .url("$base/bridge/result/$deviceId")
            .header("Authorization", "Bearer $token")
            .post(payload.toString().toRequestBody(jsonType))
            .build()
        try { client.newCall(request).execute().use { } } catch (_: Exception) {}
    }

    private fun isValidRelayUrl(value: String): Boolean {
        if (!value.startsWith("https://", ignoreCase = true)) return false
        val hostPart = value.removePrefix("https://").substringBefore('/').trim()
        return hostPart.isNotBlank() && !hostPart.contains(' ')
    }
}
