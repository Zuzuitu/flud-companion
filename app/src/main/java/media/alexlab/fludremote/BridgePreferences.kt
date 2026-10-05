package media.alexlab.fludremote

import android.content.Context
import android.util.Base64
import java.security.SecureRandom

object BridgePreferences {
    private const val PREFS = "bridge_prefs"
    private const val KEY_TOKEN = "api_token"
    private const val KEY_AUTO_START = "auto_start"
    private const val KEY_LAST_COMMAND_AT = "last_command_at"
    private const val KEY_LAST_COMMAND_MESSAGE = "last_command_message"
    private const val KEY_LAST_COMMAND_SUCCESS = "last_command_success"
    private const val KEY_CLOUD_ENABLED = "cloud_enabled"
    private const val KEY_CLOUD_BASE_URL = "cloud_base_url"
    private const val KEY_CLOUD_DEVICE_ID = "cloud_device_id"
    private const val KEY_CLOUD_TOKEN = "cloud_token"
    private const val KEY_LAST_BOOT_AT = "last_boot_at"
    private const val KEY_LAST_BOOT_ACTION = "last_boot_action"
    private const val KEY_LAST_BOOT_SUCCESS = "last_boot_success"
    private const val KEY_LAST_BOOT_DETAIL = "last_boot_detail"

    data class LastCommand(
        val atMillis: Long,
        val message: String,
        val success: Boolean
    )

    data class LastBootStart(
        val atMillis: Long,
        val action: String,
        val success: Boolean,
        val detail: String
    )

    fun token(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val existing = prefs.getString(KEY_TOKEN, null)
        if (!existing.isNullOrBlank()) return existing
        return regenerateToken(context)
    }

    fun regenerateToken(context: Context): String {
        val generated = randomUrlSafe(24)
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_TOKEN, generated)
            .commit()
        return generated
    }

    fun autoStart(context: Context): Boolean {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_AUTO_START, true)
    }

    fun setAutoStart(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_AUTO_START, enabled)
            .apply()
    }

    fun cloudEnabled(context: Context): Boolean {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_CLOUD_ENABLED, false)
    }

    fun setCloudEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_CLOUD_ENABLED, enabled)
            .apply()
    }

    fun cloudBaseUrl(context: Context): String {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_CLOUD_BASE_URL, "")
            .orEmpty()
            .trim()
            .trimEnd('/')
    }

    fun setCloudBaseUrl(context: Context, url: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_CLOUD_BASE_URL, url.trim().trimEnd('/'))
            .apply()
    }

    fun cloudDeviceId(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val existing = prefs.getString(KEY_CLOUD_DEVICE_ID, null)
        if (!existing.isNullOrBlank()) return existing
        val generated = randomUrlSafe(16)
        prefs.edit().putString(KEY_CLOUD_DEVICE_ID, generated).commit()
        return generated
    }

    fun cloudToken(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val existing = prefs.getString(KEY_CLOUD_TOKEN, null)
        if (!existing.isNullOrBlank()) return existing
        val generated = randomUrlSafe(32)
        prefs.edit().putString(KEY_CLOUD_TOKEN, generated).commit()
        return generated
    }

    fun resetCloudIdentity(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_CLOUD_DEVICE_ID, randomUrlSafe(16))
            .putString(KEY_CLOUD_TOKEN, randomUrlSafe(32))
            .commit()
    }

    fun recordLastCommand(context: Context, message: String, success: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putLong(KEY_LAST_COMMAND_AT, System.currentTimeMillis())
            .putString(KEY_LAST_COMMAND_MESSAGE, message)
            .putBoolean(KEY_LAST_COMMAND_SUCCESS, success)
            .apply()
    }

    fun lastCommand(context: Context): LastCommand? {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val at = prefs.getLong(KEY_LAST_COMMAND_AT, 0L)
        val message = prefs.getString(KEY_LAST_COMMAND_MESSAGE, null)
        if (at <= 0L || message.isNullOrBlank()) return null
        return LastCommand(
            atMillis = at,
            message = message,
            success = prefs.getBoolean(KEY_LAST_COMMAND_SUCCESS, false)
        )
    }

    fun recordBootStart(context: Context, action: String, success: Boolean, detail: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putLong(KEY_LAST_BOOT_AT, System.currentTimeMillis())
            .putString(KEY_LAST_BOOT_ACTION, action)
            .putBoolean(KEY_LAST_BOOT_SUCCESS, success)
            .putString(KEY_LAST_BOOT_DETAIL, detail.take(240))
            .apply()
    }

    fun lastBootStart(context: Context): LastBootStart? {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val at = prefs.getLong(KEY_LAST_BOOT_AT, 0L)
        if (at <= 0L) return null
        return LastBootStart(
            atMillis = at,
            action = prefs.getString(KEY_LAST_BOOT_ACTION, "").orEmpty(),
            success = prefs.getBoolean(KEY_LAST_BOOT_SUCCESS, false),
            detail = prefs.getString(KEY_LAST_BOOT_DETAIL, "").orEmpty()
        )
    }

    private fun randomUrlSafe(byteCount: Int): String {
        val bytes = ByteArray(byteCount)
        SecureRandom().nextBytes(bytes)
        return Base64.encodeToString(
            bytes,
            Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING
        )
    }
}
