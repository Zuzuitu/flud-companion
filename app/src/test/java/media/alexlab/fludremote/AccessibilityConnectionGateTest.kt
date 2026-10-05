package media.alexlab.fludremote

import org.junit.Assert.assertEquals
import org.junit.Test

class AccessibilityConnectionGateTest {
    @Test
    fun connectedServiceIsReadyImmediately() {
        val gate = AccessibilityConnectionGate(timeoutMs = 20_000L)
        assertEquals(
            AccessibilityConnectionGate.Decision.READY,
            gate.observe(elapsedMs = 0L, enabledInSettings = true, connected = true)
        )
    }

    @Test
    fun enabledButDisconnectedWaitsWithoutPretendingReady() {
        val gate = AccessibilityConnectionGate(timeoutMs = 20_000L)
        assertEquals(
            AccessibilityConnectionGate.Decision.WAIT,
            gate.observe(elapsedMs = 5_000L, enabledInSettings = true, connected = false)
        )
    }

    @Test
    fun enabledButNeverConnectedTimesOutFailSafe() {
        val gate = AccessibilityConnectionGate(timeoutMs = 20_000L)
        assertEquals(
            AccessibilityConnectionGate.Decision.TIMEOUT,
            gate.observe(elapsedMs = 20_000L, enabledInSettings = true, connected = false)
        )
    }

    @Test
    fun disabledHelperFailsImmediately() {
        val gate = AccessibilityConnectionGate(timeoutMs = 20_000L)
        assertEquals(
            AccessibilityConnectionGate.Decision.DISABLED,
            gate.observe(elapsedMs = 100L, enabledInSettings = false, connected = false)
        )
    }
}
