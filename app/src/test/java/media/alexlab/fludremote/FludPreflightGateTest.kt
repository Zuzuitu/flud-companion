package media.alexlab.fludremote

import org.junit.Assert.assertEquals
import org.junit.Test

class FludPreflightGateTest {
    @Test
    fun coldStartResetsWhenTorrentStructureChanges() {
        val gate = FludPreflightGate()
        gate.reset(nowMs = 0L, cold = true, initialOpenRequested = true)

        assertEquals(FludPreflightGate.Decision.WAIT, gate.observe(400L, true, true, "rows:a:1"))
        assertEquals(FludPreflightGate.Decision.WAIT, gate.observe(1_200L, true, true, "rows:ab:2"))
        assertEquals(FludPreflightGate.Decision.WAIT, gate.observe(2_000L, true, true, "rows:abc:3"))
        assertEquals(FludPreflightGate.Decision.WAIT, gate.observe(4_800L, true, true, "rows:abc:3"))
        assertEquals(FludPreflightGate.Decision.READY, gate.observe(5_600L, true, true, "rows:abc:3"))
    }

    @Test
    fun warmStartDoesNotPayColdStartPenalty() {
        val gate = FludPreflightGate()
        gate.reset(nowMs = 0L, cold = false, initialOpenRequested = false)

        assertEquals(FludPreflightGate.Decision.WAIT, gate.observe(0L, true, true, "rows:ready:4"))
        assertEquals(FludPreflightGate.Decision.WAIT, gate.observe(500L, true, true, "rows:ready:4"))
        assertEquals(FludPreflightGate.Decision.READY, gate.observe(900L, true, true, "rows:ready:4"))
    }

    @Test
    fun foregroundLossBeforeHandoffRequestsSafeReopen() {
        val gate = FludPreflightGate()
        gate.reset(nowMs = 0L, cold = true, initialOpenRequested = true)

        assertEquals(FludPreflightGate.Decision.WAIT, gate.observe(500L, true, false, null))
        assertEquals(FludPreflightGate.Decision.WAIT, gate.observe(1_000L, false, false, null))
        assertEquals(FludPreflightGate.Decision.REOPEN, gate.observe(2_500L, false, false, null))

        gate.markRecoveryOpen(2_500L)
        assertEquals(FludPreflightGate.Decision.WAIT, gate.observe(3_000L, true, true, "rows:stable:2"))
    }

    @Test
    fun initialLaunchThatNeverBecomesForegroundIsRetried() {
        val gate = FludPreflightGate()
        gate.reset(nowMs = 1_000L, cold = true, initialOpenRequested = true)

        assertEquals(FludPreflightGate.Decision.WAIT, gate.observe(8_500L, false, false, null))
        assertEquals(FludPreflightGate.Decision.REOPEN, gate.observe(9_000L, false, false, null))
    }

    @Test
    fun transientReadyCandidateNeverDispatches() {
        val gate = FludPreflightGate()
        gate.reset(nowMs = 0L, cold = true, initialOpenRequested = true)

        assertEquals(FludPreflightGate.Decision.WAIT, gate.observe(500L, true, true, "rows:a:1"))
        assertEquals(FludPreflightGate.Decision.WAIT, gate.observe(1_000L, true, false, null))
        assertEquals(FludPreflightGate.Decision.WAIT, gate.observe(5_000L, true, true, "rows:a:1"))
    }
}
