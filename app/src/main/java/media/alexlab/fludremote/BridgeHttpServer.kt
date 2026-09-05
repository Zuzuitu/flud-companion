package media.alexlab.fludremote

import android.content.Context
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import java.nio.charset.StandardCharsets
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

class BridgeHttpServer(
    private val context: Context,
    private val port: Int,
    private val tokenProvider: () -> String
) {
    companion object {
        private const val MAX_HEADER_BYTES = 16 * 1024
        private const val MAX_BODY_BYTES = 16 * 1024
        const val VERSION = "0.24.2"
    }

    private val running = AtomicBoolean(false)
    private val clients = Executors.newCachedThreadPool()
    private var serverSocket: ServerSocket? = null
    private var acceptThread: Thread? = null

    fun start(): Boolean {
        if (!running.compareAndSet(false, true)) return true

        val server = try {
            ServerSocket().apply {
                reuseAddress = true
                bind(InetSocketAddress(port))
            }
        } catch (e: Exception) {
            e.printStackTrace()
            running.set(false)
            return false
        }
        serverSocket = server

        acceptThread = Thread({
            while (running.get()) {
                try {
                    val socket = server.accept()
                    clients.execute { handleClient(socket) }
                } catch (e: SocketException) {
                    if (running.get()) e.printStackTrace()
                } catch (e: Exception) {
                    if (running.get()) e.printStackTrace()
                }
            }
        }, "flud-bridge-http").apply {
            isDaemon = true
            start()
        }
        return true
    }

    fun stop() {
        running.set(false)
        try {
            serverSocket?.close()
        } catch (_: Exception) {
        }
        serverSocket = null
        clients.shutdownNow()
    }

    private fun handleClient(socket: Socket) {
        socket.use { client ->
            client.soTimeout = 7000
            val input = BufferedInputStream(client.getInputStream())
            val output = BufferedOutputStream(client.getOutputStream())

            try {
                val requestLine = readLine(input, MAX_HEADER_BYTES)
                    ?: return respond(output, 400, json("error" to "Missing request line"))

                val parts = requestLine.split(' ')
                if (parts.size < 2) {
                    return respond(output, 400, json("error" to "Malformed request line"))
                }

                val method = parts[0].uppercase(Locale.US)
                val target = parts[1]
                val path = target.substringBefore('?')

                val headers = linkedMapOf<String, String>()
                var headerBytes = requestLine.length
                while (true) {
                    val line = readLine(input, MAX_HEADER_BYTES - headerBytes)
                        ?: return respond(output, 400, json("error" to "Malformed headers"))
                    headerBytes += line.length
                    if (line.isEmpty()) break
                    val idx = line.indexOf(':')
                    if (idx > 0) {
                        val name = line.substring(0, idx).trim().lowercase(Locale.US)
                        val value = line.substring(idx + 1).trim()
                        headers[name] = value
                    }
                }

                val contentLength = headers["content-length"]?.toIntOrNull() ?: 0
                if (contentLength < 0 || contentLength > MAX_BODY_BYTES) {
                    return respond(output, 413, json("error" to "Request body too large"))
                }

                val body = if (contentLength > 0) {
                    val bytes = ByteArray(contentLength)
                    var offset = 0
                    while (offset < contentLength) {
                        val count = input.read(bytes, offset, contentLength - offset)
                        if (count < 0) break
                        offset += count
                    }
                    String(bytes, 0, offset, StandardCharsets.UTF_8)
                } else {
                    ""
                }

                when {
                    method == "GET" && (path == "/app" || path == "/app/") -> {
                        respond(output, 200, WebUi.localHtml(VERSION), "text/html; charset=utf-8")
                    }

                    method == "GET" && (path == "/icon-512.png" || path == "/companion-mark-v0191.png") -> {
                        val bytes = context.resources.openRawResource(R.drawable.companion_mark_v0200).use { it.readBytes() }
                        respondBytes(output, 200, bytes, "image/png", "no-store, max-age=0")
                    }

                    method == "GET" && path == "/" -> {
                        respond(output, 200, json(
                            "name" to "Flud Companion",
                            "version" to VERSION,
                            "status" to "ok",
                            "magnetEndpoint" to "/api/v1/magnet"
                        ))
                    }

                    method == "GET" && path == "/api/v1/health" -> {
                        respond(output, 200, json(
                            "status" to "ok",
                            "version" to VERSION,
                            "fludPackage" to (FludLauncher.installedPackage(context) ?: JSONObject.NULL)
                        ))
                    }

                    method == "GET" && path == "/api/v1/capabilities" -> {
                        if (!isAuthorized(headers)) {
                            return respond(output, 401, json("error" to "Unauthorized"))
                        }
                        respond(output, 200, json(
                            "version" to VERSION,
                            "magnet" to true,
                            "torrentUpload" to false,
                            "cloudRelay" to true,
                            "cloudRelayUrl" to BridgePreferences.cloudBaseUrl(context),
                            "cloudDeviceId" to BridgePreferences.cloudDeviceId(context),
                            "cloudEnabled" to BridgePreferences.cloudEnabled(context),
                            "autoStart" to BridgePreferences.autoStart(context),
                            "autoStartHelper" to FludAutoStartService.isEnabled(context),
                            "autoStartStrategy" to FludAutoStartService.strategy(),
                            "autoStartDiagnostic" to FludAutoStartService.diagnostic(),
                            "remoteAutoStartHelper" to FludAutoStartService.isEnabled(context),
                            "remoteAutoStartStrategy" to FludAutoStartService.strategy(),
                            "remoteAutoStartDiagnostic" to FludAutoStartService.diagnostic(),
                            "fludPackage" to (FludLauncher.installedPackage(context) ?: JSONObject.NULL)
                        ))
                    }

                    method == "GET" && path == "/api/v1/status" -> {
                        if (!isAuthorized(headers)) {
                            return respond(output, 401, json("error" to "Unauthorized"))
                        }
                        val last = BridgePreferences.lastCommand(context)
                        val lastObject = if (last == null) {
                            JSONObject.NULL
                        } else {
                            JSONObject()
                                .put("atMillis", last.atMillis)
                                .put("message", last.message)
                                .put("success", last.success)
                        }
                        respond(output, 200, json(
                            "status" to "ok",
                            "version" to VERSION,
                            "autoStart" to BridgePreferences.autoStart(context),
                            "autoStartHelper" to FludAutoStartService.isEnabled(context),
                            "autoStartStatus" to FludAutoStartService.status(),
                            "autoStartStrategy" to FludAutoStartService.strategy(),
                            "autoStartDiagnostic" to FludAutoStartService.diagnostic(),
                            "remoteAutoStartHelper" to FludAutoStartService.isEnabled(context),
                            "remoteAutoStartStatus" to FludAutoStartService.status(),
                            "remoteAutoStartStrategy" to FludAutoStartService.strategy(),
                            "remoteAutoStartDiagnostic" to FludAutoStartService.diagnostic(),
                            "lastCommand" to lastObject,
                            "cloudRelayUrl" to BridgePreferences.cloudBaseUrl(context),
                            "cloudDeviceId" to BridgePreferences.cloudDeviceId(context),
                            "cloudEnabled" to BridgePreferences.cloudEnabled(context),
                            "cloudState" to CloudRelayClient.snapshot().state.name.lowercase(Locale.US),
                            "cloudDetail" to CloudRelayClient.snapshot().detail
                        ))
                    }

                    method == "POST" && path == "/api/v1/magnet" -> {
                        if (!isAuthorized(headers)) {
                            return respond(output, 401, json("error" to "Unauthorized"))
                        }

                        val magnet = extractMagnet(body, headers["content-type"])
                        if (magnet == null || !magnet.startsWith("magnet:?", ignoreCase = true)) {
                            BridgePreferences.recordLastCommand(context, "Rejected invalid magnet URI", false)
                            return respond(output, 400, json("error" to "Body must contain a valid magnet URI"))
                        }
                        if (magnet.length > 12_000) {
                            BridgePreferences.recordLastCommand(context, "Rejected oversized magnet URI", false)
                            return respond(output, 413, json("error" to "Magnet URI too long"))
                        }

                        val autoStart = extractAutoStart(body, headers["content-type"])
                        val helperReady = autoStart && FludAutoStartService.isEnabled(context)
                        if (helperReady) {
                            // Arm BEFORE launching Flud so a cold/slow start cannot race past the helper.
                            FludAutoStartService.request(FludLauncher.installedPackage(context))
                        }
                        val result = FludLauncher.launchMagnet(context, magnet.trim())
                        if (helperReady) {
                            if (result.success) FludAutoStartService.retarget(result.packageName)
                            else FludAutoStartService.cancel("Flud launch failed; auto-start cancelled")
                        }
                        val resultMessage = if (result.success && autoStart) {
                            if (helperReady) {
                                "${result.message}; guarded auto-start armed before Flud launch"
                            } else {
                                "${result.message}; auto-start requested but the accessibility helper is not enabled"
                            }
                        } else {
                            result.message
                        }
                        BridgePreferences.recordLastCommand(context, "LAN: $resultMessage", result.success)
                        if (result.success) {
                            respond(output, 200, json(
                                "ok" to true,
                                "message" to resultMessage,
                                "autoStart" to autoStart,
                                "autoStartHelper" to FludAutoStartService.isEnabled(context),
                                "package" to (result.packageName ?: JSONObject.NULL)
                            ))
                        } else {
                            respond(output, 409, json(
                                "ok" to false,
                                "error" to resultMessage,
                                "autoStart" to autoStart,
                                "package" to (result.packageName ?: JSONObject.NULL)
                            ))
                        }
                    }

                    else -> respond(output, 404, json("error" to "Not found"))
                }
            } catch (e: Exception) {
                try {
                    respond(output, 500, json("error" to (e.message ?: e.javaClass.simpleName)))
                } catch (_: Exception) {
                }
            }
        }
    }

    private fun isAuthorized(headers: Map<String, String>): Boolean {
        val header = headers["authorization"] ?: return false
        val expected = "Bearer ${tokenProvider()}"
        return constantTimeEquals(header, expected)
    }

    private fun extractMagnet(body: String, contentType: String?): String? {
        val trimmed = body.trim()
        if (trimmed.isEmpty()) return null

        return if (contentType?.lowercase(Locale.US)?.contains("application/json") == true || trimmed.startsWith("{")) {
            try {
                JSONObject(trimmed).optString("magnet").ifBlank { null }
            } catch (_: Exception) {
                null
            }
        } else {
            trimmed
        }
    }

    private fun extractAutoStart(body: String, contentType: String?): Boolean {
        val trimmed = body.trim()
        if (trimmed.isEmpty()) return false
        val looksJson = contentType?.lowercase(Locale.US)?.contains("application/json") == true || trimmed.startsWith("{")
        if (!looksJson) return false
        return try {
            JSONObject(trimmed).optBoolean("autoStart", false)
        } catch (_: Exception) {
            false
        }
    }

    private fun constantTimeEquals(a: String, b: String): Boolean {
        val aa = a.toByteArray(StandardCharsets.UTF_8)
        val bb = b.toByteArray(StandardCharsets.UTF_8)
        var diff = aa.size xor bb.size
        val max = maxOf(aa.size, bb.size)
        for (i in 0 until max) {
            val av = if (i < aa.size) aa[i].toInt() else 0
            val bv = if (i < bb.size) bb[i].toInt() else 0
            diff = diff or (av xor bv)
        }
        return diff == 0
    }

    private fun readLine(input: BufferedInputStream, remainingBudget: Int): String? {
        if (remainingBudget <= 0) return null
        val bytes = ArrayList<Byte>()
        var previous = -1

        while (bytes.size < remainingBudget) {
            val current = input.read()
            if (current < 0) return if (bytes.isEmpty()) null else String(bytes.toByteArray(), StandardCharsets.ISO_8859_1)

            if (previous == '\r'.code && current == '\n'.code) {
                if (bytes.isNotEmpty()) bytes.removeAt(bytes.lastIndex)
                return String(bytes.toByteArray(), StandardCharsets.ISO_8859_1)
            }

            bytes.add(current.toByte())
            previous = current
        }
        return null
    }

    private fun ArrayList<Byte>.toByteArray(): ByteArray {
        val result = ByteArray(size)
        for (i in indices) result[i] = this[i]
        return result
    }

    private fun json(vararg pairs: Pair<String, Any?>): String {
        val obj = JSONObject()
        for ((key, value) in pairs) obj.put(key, value)
        return obj.toString()
    }

    private fun respondBytes(
        output: BufferedOutputStream,
        code: Int,
        bytes: ByteArray,
        contentType: String,
        cacheControl: String = "no-store"
    ) {
        val reason = when (code) {
            200 -> "OK"
            400 -> "Bad Request"
            401 -> "Unauthorized"
            404 -> "Not Found"
            409 -> "Conflict"
            413 -> "Payload Too Large"
            else -> "Internal Server Error"
        }
        val headers = buildString {
            append("HTTP/1.1 $code $reason\r\n")
            append("Content-Type: $contentType\r\n")
            append("Content-Length: ${bytes.size}\r\n")
            append("Cache-Control: $cacheControl\r\n")
            append("Connection: close\r\n")
            append("\r\n")
        }.toByteArray(StandardCharsets.ISO_8859_1)

        output.write(headers)
        output.write(bytes)
        output.flush()
    }

    private fun respond(
        output: BufferedOutputStream,
        code: Int,
        body: String,
        contentType: String = "application/json; charset=utf-8"
    ) {
        val reason = when (code) {
            200 -> "OK"
            400 -> "Bad Request"
            401 -> "Unauthorized"
            404 -> "Not Found"
            409 -> "Conflict"
            413 -> "Payload Too Large"
            else -> "Internal Server Error"
        }
        val bytes = body.toByteArray(StandardCharsets.UTF_8)
        val headers = buildString {
            append("HTTP/1.1 $code $reason\r\n")
            append("Content-Type: $contentType\r\n")
            append("Content-Length: ${bytes.size}\r\n")
            append("Cache-Control: no-store\r\n")
            append("Connection: close\r\n")
            append("\r\n")
        }.toByteArray(StandardCharsets.ISO_8859_1)

        output.write(headers)
        output.write(bytes)
        output.flush()
    }
}
