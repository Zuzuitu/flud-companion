package media.alexlab.fludremote

/** Pure decision: never mutate Android's protected Accessibility settings. */
internal object AccessibilityBindingStatus {
    fun requiresAction(enabled: Boolean, connected: Boolean, elapsedMs: Long, graceMs: Long): Boolean =
        enabled && !connected && elapsedMs >= graceMs
}
