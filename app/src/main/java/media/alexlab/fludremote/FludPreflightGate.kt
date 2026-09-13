package media.alexlab.fludremote

/**
 * Pure state machine for the pre-handoff phase of Auto-start.
 *
 * It reasons about structural list stability instead of a fixed startup delay.
 * If Flud disappears before any magnet handoff, the caller may reopen it safely
 * because no torrent command has reached Flud yet.
 */
internal class FludPreflightGate(
    private val warmStableMs: Long = 900L,
    private val coldStableMs: Long = 3_500L,
    private val minStableSamples: Int = 3,
    private val foregroundLossBeforeReopenMs: Long = 1_500L,
    private val initialLaunchRetryMs: Long = 8_000L,
    private val failAfterExhaustedMs: Long = 12_000L,
    private val maxRecoveryOpens: Int = 2
) {
    enum class Decision { WAIT, READY, REOPEN, FAIL }

    private var coldStart = true
    private var seenForeground = false
    private var signature: String? = null
    private var signatureSince = 0L
    private var stableSamples = 0
    private var foregroundLostSince = 0L
    private var lastOpenRequestedAt = 0L
    private var recoveryOpens = 0

    fun reset(nowMs: Long, cold: Boolean, initialOpenRequested: Boolean) {
        coldStart = cold
        seenForeground = false
        signature = null
        signatureSince = 0L
        stableSamples = 0
        foregroundLostSince = 0L
        lastOpenRequestedAt = if (initialOpenRequested) nowMs else 0L
        recoveryOpens = 0
    }

    fun observe(
        nowMs: Long,
        foreground: Boolean,
        readyCandidate: Boolean,
        listSignature: String?
    ): Decision {
        if (!foreground) {
            invalidateSignature()

            if (seenForeground) {
                if (foregroundLostSince <= 0L) foregroundLostSince = nowMs
                val lostFor = nowMs - foregroundLostSince
                if (lostFor >= foregroundLossBeforeReopenMs) {
                    if (recoveryOpens < maxRecoveryOpens) return Decision.REOPEN
                    if (lostFor >= failAfterExhaustedMs) return Decision.FAIL
                }
                return Decision.WAIT
            }

            if (lastOpenRequestedAt > 0L) {
                val waitingForLaunch = nowMs - lastOpenRequestedAt
                if (waitingForLaunch >= initialLaunchRetryMs) {
                    if (recoveryOpens < maxRecoveryOpens) return Decision.REOPEN
                    if (waitingForLaunch >= failAfterExhaustedMs) return Decision.FAIL
                }
            }
            return Decision.WAIT
        }

        seenForeground = true
        foregroundLostSince = 0L

        if (!readyCandidate || listSignature.isNullOrBlank()) {
            invalidateSignature()
            return Decision.WAIT
        }

        if (signature != listSignature) {
            signature = listSignature
            signatureSince = nowMs
            stableSamples = 1
            return Decision.WAIT
        }

        stableSamples += 1
        val requiredStableMs = if (coldStart) coldStableMs else warmStableMs
        val stableFor = nowMs - signatureSince
        return if (stableFor >= requiredStableMs && stableSamples >= minStableSamples) {
            Decision.READY
        } else {
            Decision.WAIT
        }
    }

    fun markRecoveryOpen(nowMs: Long) {
        recoveryOpens += 1
        lastOpenRequestedAt = nowMs
        seenForeground = false
        foregroundLostSince = 0L
        invalidateSignature()
    }

    fun invalidate() {
        invalidateSignature()
    }

    fun modeLabel(): String = if (coldStart) "cold" else "warm"
    fun stableFor(nowMs: Long): Long = if (signatureSince > 0L) (nowMs - signatureSince).coerceAtLeast(0L) else 0L
    fun stableSampleCount(): Int = stableSamples
    fun recoveryOpenCount(): Int = recoveryOpens

    private fun invalidateSignature() {
        signature = null
        signatureSince = 0L
        stableSamples = 0
    }
}
