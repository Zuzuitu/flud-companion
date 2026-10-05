package media.alexlab.fludremote

internal class AccessibilityConnectionGate(
    private val timeoutMs: Long = 20_000L
) {
    enum class Decision { READY, WAIT, DISABLED, TIMEOUT }

    fun observe(elapsedMs: Long, enabledInSettings: Boolean, connected: Boolean): Decision {
        if (!enabledInSettings) return Decision.DISABLED
        if (connected) return Decision.READY
        return if (elapsedMs >= timeoutMs) Decision.TIMEOUT else Decision.WAIT
    }
}
