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
        private const val BRIDGE_VERSION = "0.24.0"
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
        .readTimeout(12, TimeUnit.SECONDS)
        .callTimeout(15, TimeUnit.SECONDS)
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
        if (type != "magnet") { postResult(base, deviceId, token, id, false, "Unsupported remote command", null); return }
        val magnet = command.optString("magnet")
        val autoStart = command.optBoolean("autoStart", false)
        val validMagnet = magnet.startsWith("magnet:?", ignoreCase = true) && magnet.length <= 12_000
        val helperReady = validMagnet && autoStart && FludAutoStartService.isEnabled(appContext)
        if (helperReady) FludAutoStartService.request(FludLauncher.installedPackage(appContext))
        val result = if (validMagnet) FludLauncher.launchMagnet(appContext, magnet) else FludLauncher.Result(false, message = "Invalid magnet URI from relay")
        if (helperReady) {
            if (result.success) FludAutoStartService.retarget(result.packageName) else FludAutoStartService.cancel("Flud launch failed; auto-start cancelled")
        }
        val resultMessage = if (result.success && autoStart) {
            if (helperReady) "${result.message}; guarded auto-start armed before Flud launch" else "${result.message}; auto-start requested but the Flud Companion accessibility helper is not enabled"
        } else result.message
        BridgePreferences.recordLastCommand(appContext, "Remote: $resultMessage", result.success)
        postResult(base, deviceId, token, id, result.success, resultMessage, result.packageName)
    }

    private fun postResult(base: String, deviceId: String, token: String, id: String, ok: Boolean, message: String?, packageName: String?) {
        val payload = JSONObject().put("id", id).put("ok", ok)
        if (!message.isNullOrBlank()) payload.put("message", message)
        if (!packageName.isNullOrBlank()) payload.put("package", packageName)
        val request = Request.Builder().url("$base/bridge/result/$deviceId").header("Authorization", "Bearer $token").post(payload.toString().toRequestBody(jsonType)).build()
        try { client.newCall(request).execute().use { } } catch (_: Exception) {}
    }

    private fun isValidRelayUrl(value: String): Boolean {
        if (!value.startsWith("https://", ignoreCase = true)) return false
        val hostPart = value.removePrefix("https://").substringBefore('/').trim()
        return hostPart.isNotBlank() && !hostPart.contains(' ')
    }
}
