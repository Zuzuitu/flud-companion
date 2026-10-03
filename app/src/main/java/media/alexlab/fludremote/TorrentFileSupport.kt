package media.alexlab.fludremote

import android.content.Context
import android.net.Uri
import java.io.File
import java.util.UUID

object TorrentFileSupport {
    const val MAX_TORRENT_BYTES = 5 * 1024 * 1024
    private const val STALE_FILE_MS = 6 * 60 * 60 * 1000L
    private const val DIRECTORY = "flud-torrents"

    data class StoredTorrent(
        val token: String,
        val displayName: String,
        val file: File
    )

    fun store(context: Context, bytes: ByteArray, requestedName: String?): StoredTorrent {
        require(bytes.isNotEmpty()) { "Torrent file is empty" }
        require(bytes.size <= MAX_TORRENT_BYTES) { "Torrent file exceeds 5 MB" }
        require(TorrentMetainfoValidator.isValid(bytes)) { "Invalid .torrent metainfo" }

        cleanupStale(context)

        val token = UUID.randomUUID().toString()
        val displayName = sanitizeName(requestedName)
        val directory = File(context.cacheDir, "$DIRECTORY/$token")
        check(directory.mkdirs() || directory.isDirectory) { "Could not create torrent cache directory" }
        val file = File(directory, displayName)
        file.writeBytes(bytes)
        return StoredTorrent(token, displayName, file)
    }

    fun contentUri(context: Context, stored: StoredTorrent): Uri =
        Uri.Builder()
            .scheme("content")
            .authority("${context.packageName}.torrents")
            .appendPath(stored.token)
            .appendPath(stored.displayName)
            .build()

    fun delete(stored: StoredTorrent) {
        try {
            stored.file.delete()
            stored.file.parentFile?.delete()
        } catch (_: Exception) {
        }
    }

    fun cleanupStale(context: Context, nowMs: Long = System.currentTimeMillis()) {
        val root = File(context.cacheDir, DIRECTORY)
        val children = root.listFiles() ?: return
        for (child in children) {
            val age = nowMs - child.lastModified()
            if (age >= STALE_FILE_MS) {
                try {
                    child.deleteRecursively()
                } catch (_: Exception) {
                }
            }
        }
    }

    internal fun sanitizeName(requestedName: String?): String {
        val original = requestedName.orEmpty().substringAfterLast('/').substringAfterLast('\\').trim()
        val cleaned = original
            .replace(Regex("[\\u0000-\\u001f\\u007f]"), "")
            .replace(Regex("[^A-Za-z0-9._()\\[\\] -]"), "_")
            .trim()
            .take(180)
            .ifBlank { "download.torrent" }
        return if (cleaned.endsWith(".torrent", ignoreCase = true)) cleaned else "$cleaned.torrent"
    }
}

internal object TorrentMetainfoValidator {
    fun isValid(bytes: ByteArray): Boolean {
        if (bytes.size < 8 || bytes.first() != 'd'.code.toByte()) return false
        return try {
            Parser(bytes).parseRoot()
        } catch (_: IllegalArgumentException) {
            false
        } catch (_: IndexOutOfBoundsException) {
            false
        }
    }

    private class Parser(private val bytes: ByteArray) {
        private var index = 0
        private var nodes = 0

        fun parseRoot(): Boolean {
            expect('d')
            var infoSeen = false
            while (peek() != 'e') {
                val key = parseString(asKey = true)
                if (key == "info") infoSeen = true
                parseValue(1)
            }
            expect('e')
            return infoSeen && index == bytes.size
        }

        private fun parseValue(depth: Int) {
            require(depth <= 64) { "Bencode nesting too deep" }
            nodes += 1
            require(nodes <= 200_000) { "Too many bencode nodes" }
            when (peek()) {
                'i' -> parseInteger()
                'l' -> parseList(depth)
                'd' -> parseDictionary(depth)
                in '0'..'9' -> parseString(asKey = false)
                else -> throw IllegalArgumentException("Invalid bencode token")
            }
        }

        private fun parseInteger() {
            expect('i')
            val start = index
            if (peek() == '-') index += 1
            val digitStart = index
            while (peekOrNull()?.isDigit() == true) index += 1
            require(index > digitStart) { "Invalid integer" }
            require(index - start <= 24) { "Integer too long" }
            expect('e')
        }

        private fun parseList(depth: Int) {
            expect('l')
            while (peek() != 'e') parseValue(depth + 1)
            expect('e')
        }

        private fun parseDictionary(depth: Int) {
            expect('d')
            while (peek() != 'e') {
                parseString(asKey = true)
                parseValue(depth + 1)
            }
            expect('e')
        }

        private fun parseString(asKey: Boolean): String? {
            val lengthStart = index
            while (peekOrNull()?.isDigit() == true) index += 1
            require(index > lengthStart) { "Missing string length" }
            require(index - lengthStart <= 9) { "String length too large" }
            val length = bytes.copyOfRange(lengthStart, index).toString(Charsets.US_ASCII).toIntOrNull()
                ?: throw IllegalArgumentException("Invalid string length")
            expect(':')
            require(length >= 0 && index + length <= bytes.size) { "Truncated string" }
            val value = if (asKey && length <= 256) {
                bytes.copyOfRange(index, index + length).toString(Charsets.UTF_8)
            } else {
                null
            }
            index += length
            return value
        }

        private fun expect(char: Char) {
            require(index < bytes.size && bytes[index] == char.code.toByte()) { "Expected $char" }
            index += 1
        }

        private fun peek(): Char {
            require(index < bytes.size) { "Unexpected end of bencode" }
            return bytes[index].toInt().toChar()
        }

        private fun peekOrNull(): Char? =
            if (index < bytes.size) bytes[index].toInt().toChar() else null
    }
}
