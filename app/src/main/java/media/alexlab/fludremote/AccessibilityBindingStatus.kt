package media.alexlab.fludremote

/** Pure decision: never mutate Android's protected Accessibility settings. */
internal object AccessibilityBindingStatus {
    fun requiresAction(enabled: Boolean, connected: Boolean, elapsedMs: Long, graceMs: Long): Boolean =
        enabled && !connected && elapsedMs >= graceMs

    /** Final pre-handoff gate: disconnection is a terminal failure, not a list-restoration retry. */
    enum class FinalGate { DISCONNECTED, NOT_READY, READY }

    fun finalGate(
        connected: Boolean,
        foreground: Boolean,
        readyCandidate: Boolean,
        signatureMatches: Boolean
    ): FinalGate = when {
        !connected -> FinalGate.DISCONNECTED
        !foreground || !readyCandidate || !signatureMatches -> FinalGate.NOT_READY
        else -> FinalGate.READY
    }
}
