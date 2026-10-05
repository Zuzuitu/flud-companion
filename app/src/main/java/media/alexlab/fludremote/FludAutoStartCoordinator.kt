package media.alexlab.fludremote

import android.content.Context
import android.os.Handler
import android.os.Looper

/**
 * Safe Auto-start coordinator.
 *
 * v11 readiness rule:
 * - Never dispatch merely because the first torrent title appeared.
 * - Track the structural fingerprint of visible torrent rows and require it to stop changing.
 * - Warm Flud gets a short structural check; cold/restoring Flud gets a longer adaptive gate.
 * - If Flud disappears before handoff, reopen it safely because no payload has been sent yet.
 * - After handoff, exactly one payload handoff remains the hard invariant.
 */
object FludAutoStartCoordinator {
    private const val PREPARE_TIMEOUT_MS = 180_000L
    private const val CHECK_INTERVAL_MS = 400L
    private const val FINAL_VERIFY_MS = 450L

    private val handler = Handler(Looper.getMainLooper())
    private val lock = Any()
    private val gate = FludPreflightGate()
    private val helperGate = AccessibilityConnectionGate()

    private sealed class PendingPayload {
        data class Magnet(val value: String) : PendingPayload()
        data class Torrent(val value: TorrentFileSupport.StoredTorrent) : PendingPayload()

        fun label(): String = when (this) {
            is Magnet -> "magnet"
            is Torrent -> ".torrent file"
        }

        fun sameExplicitRetry(other: PendingPayload): Boolean =
            this is Magnet && other is Magnet && value == other.value
    }

    @Volatile private var queuedPayload: PendingPayload? = null
    @Volatile private var queuedPackage: String? = null
    @Volatile private var queuedAt = 0L
    @Volatile private var waitingForHelperConnection = false
    @Volatile private var generation = 0L

    fun submit(context: Context, magnet: String): FludLauncher.Result {
        if (!magnet.startsWith("magnet:?", ignoreCase = true)) {
            return FludLauncher.Result(false, message = "Invalid magnet URI")
        }
        return submitPayload(context.applicationContext, PendingPayload.Magnet(magnet))
    }

    fun submitTorrent(
        context: Context,
        torrent: TorrentFileSupport.StoredTorrent
    ): FludLauncher.Result = submitPayload(context.applicationContext, PendingPayload.Torrent(torrent))

    private fun submitPayload(context: Context, payload: PendingPayload): FludLauncher.Result {
        val pkg = FludLauncher.installedPackage(context)
            ?: return FludLauncher.Result(false, message = "Flud or Flud+ is not installed")

        synchronized(lock) {
            val existing = queuedPayload
            if (existing != null && !existing.sameExplicitRetry(payload)) {
                return FludLauncher.Result(false, pkg, "Another Auto-start payload is still preparing Flud")
            }

            val now = System.currentTimeMillis()

            generation += 1L
            val myGeneration = generation
            queuedPayload = payload
            queuedPackage = pkg
            queuedAt = now
            waitingForHelperConnection = !FludAutoStartService.isConnected()

            if (waitingForHelperConnection) {
                gate.reset(now, cold = true, initialOpenRequested = false)
                FludAutoStartService.cancel("Waiting for Accessibility helper before ${payload.label()} handoff")
                FludAutoStartService.report(
                    "Waiting for Auto-start helper connection",
                    "Accessibility is enabled in Android settings but the service is not connected; ${payload.label()} is still local and unsent"
                )
                scheduleCheck(context, myGeneration, CHECK_INTERVAL_MS)
                return FludLauncher.Result(
                    true,
                    pkg,
                    "Auto-start helper is reconnecting; ${payload.label()} is held locally until Accessibility is live"
                )
            }

            val started = beginFludPreflightLocked(context, pkg, payload, now, existing?.sameExplicitRetry(payload) == true)
            if (!started.success) {
                clearQueueLocked(discardPayload = true)
                return started
            }

            scheduleCheck(context, myGeneration, CHECK_INTERVAL_MS)
            return started
        }
    }

    private fun beginFludPreflightLocked(
        context: Context,
        pkg: String,
        payload: PendingPayload,
        now: Long,
        explicitRetry: Boolean
    ): FludLauncher.Result {
        val foreground = FludAutoStartService.isFludForeground(pkg)
        val snapshot = if (foreground) FludAutoStartService.torrentListSnapshot(pkg) else null
        val cold = !(foreground && snapshot?.readyCandidate == true)

        var openRequested = false
        if (!foreground) {
            val opened = FludAutoStartService.openFludForPreflight(context, pkg)
            if (!opened.success) return opened
            openRequested = true
        }

        gate.reset(now, cold = cold, initialOpenRequested = openRequested)
        FludAutoStartService.cancel("Preparing Flud before ${payload.label()} handoff")
        FludAutoStartService.report(
            if (explicitRetry) "Retrying Flud preflight" else "Preparing Flud",
            if (cold) {
                "Waiting for torrent-list structure to finish restoring; ${payload.label()} has not been sent"
            } else {
                "Flud is already open; verifying the torrent-list structure before ${payload.label()} handoff"
            }
        )

        return FludLauncher.Result(
            true,
            pkg,
            if (explicitRetry) {
                "Pending Auto-start retry refreshed; payload is still held locally until Flud is structurally ready"
            } else if (cold) {
                "Flud is preparing; ${payload.label()} is held locally until its torrent list stops changing"
            } else {
                "Flud is open; verifying torrent-list stability before Auto-start ${payload.label()} handoff"
            }
        )
    }

    private fun scheduleCheck(context: Context, expectedGeneration: Long, delayMs: Long) {
        handler.postDelayed({ checkAndDispatch(context, expectedGeneration) }, delayMs)
    }

    private fun checkAndDispatch(context: Context, expectedGeneration: Long) {
        val payload: PendingPayload
        val pkg: String
        val startedAt: Long
        synchronized(lock) {
            if (expectedGeneration != generation) return
            payload = queuedPayload ?: return
            pkg = queuedPackage ?: return
            startedAt = queuedAt
        }

        val now = System.currentTimeMillis()
        val elapsed = now - startedAt
        if (elapsed >= PREPARE_TIMEOUT_MS) {
            synchronized(lock) {
                if (expectedGeneration != generation) return
                clearQueueLocked(discardPayload = true)
            }
            FludAutoStartService.report(
                "Flud preflight timed out",
                "No ${payload.label()} was sent because the torrent list never reached a stable structural state"
            )
            BridgePreferences.recordLastCommand(context, "Auto-start: Flud never became structurally ready", false)
            return
        }

        if (waitingForHelperConnection) {
            when (
                helperGate.observe(
                    elapsedMs = elapsed,
                    enabledInSettings = FludAutoStartService.isEnabled(context),
                    connected = FludAutoStartService.isConnected()
                )
            ) {
                AccessibilityConnectionGate.Decision.DISABLED -> {
                    synchronized(lock) {
                        if (expectedGeneration != generation) return
                        clearQueueLocked(discardPayload = true)
                    }
                    FludAutoStartService.report(
                        "Auto-start helper was disabled before handoff",
                        "No ${payload.label()} was sent; retry is safe"
                    )
                    BridgePreferences.recordLastCommand(context, "Auto-start: Accessibility helper disabled before handoff", false)
                    return
                }

                AccessibilityConnectionGate.Decision.TIMEOUT -> {
                    synchronized(lock) {
                        if (expectedGeneration != generation) return
                        clearQueueLocked(discardPayload = true)
                    }
                    FludAutoStartService.report(
                        "Auto-start helper did not reconnect",
                        "Android still lists Accessibility as enabled, but the service never connected; no ${payload.label()} was sent"
                    )
                    BridgePreferences.recordLastCommand(context, "Auto-start: Accessibility helper enabled but not connected", false)
                    return
                }

                AccessibilityConnectionGate.Decision.WAIT -> {
                    FludAutoStartService.report(
                        "Waiting for Auto-start helper connection",
                        "Accessibility is enabled but not live yet; ${payload.label()} remains local and unsent, elapsed=${elapsed / 1000}s"
                    )
                    scheduleCheck(context, expectedGeneration, CHECK_INTERVAL_MS)
                    return
                }

                AccessibilityConnectionGate.Decision.READY -> {
                    val startResult = synchronized(lock) {
                        if (expectedGeneration != generation || queuedPayload != payload || queuedPackage != pkg) return
                        waitingForHelperConnection = false
                        beginFludPreflightLocked(context, pkg, payload, now, false)
                    }
                    if (!startResult.success) {
                        synchronized(lock) {
                            if (expectedGeneration != generation) return
                            clearQueueLocked(discardPayload = true)
                        }
                        FludAutoStartService.report("Could not start Flud preflight", startResult.message)
                        BridgePreferences.recordLastCommand(context, "Auto-start: ${startResult.message}", false)
                        return
                    }
                    scheduleCheck(context, expectedGeneration, CHECK_INTERVAL_MS)
                    return
                }
            }
        }

        val foreground = FludAutoStartService.isFludForeground(pkg)
        val snapshot = if (foreground) FludAutoStartService.torrentListSnapshot(pkg) else null
        val decision = gate.observe(
            nowMs = now,
            foreground = foreground,
            readyCandidate = snapshot?.readyCandidate == true,
            listSignature = snapshot?.signature
        )

        when (decision) {
            FludPreflightGate.Decision.REOPEN -> {
                val reopened = FludAutoStartService.openFludForPreflight(context, pkg)
                if (!reopened.success) {
                    synchronized(lock) {
                        if (expectedGeneration != generation) return
                        clearQueueLocked(discardPayload = true)
                    }
                    FludAutoStartService.report("Could not reopen Flud during preflight", reopened.message)
                    BridgePreferences.recordLastCommand(context, "Auto-start: ${reopened.message}", false)
                    return
                }
                gate.markRecoveryOpen(now)
                FludAutoStartService.report(
                    "Flud disappeared before handoff - reopened safely",
                    "Recovery open ${gate.recoveryOpenCount()}; ${payload.label()} is still local and has not been sent"
                )
                scheduleCheck(context, expectedGeneration, 700L)
                return
            }

            FludPreflightGate.Decision.FAIL -> {
                synchronized(lock) {
                    if (expectedGeneration != generation) return
                    clearQueueLocked(discardPayload = true)
                }
                FludAutoStartService.report(
                    "Flud could not stay open during preflight",
                    "No ${payload.label()} was sent; retry is safe"
                )
                BridgePreferences.recordLastCommand(context, "Auto-start: Flud did not remain available before handoff", false)
                return
            }

            FludPreflightGate.Decision.READY -> {
                val expectedSignature = snapshot?.signature ?: run {
                    gate.invalidate()
                    scheduleCheck(context, expectedGeneration, CHECK_INTERVAL_MS)
                    return
                }
                FludAutoStartService.report(
                    "Torrent list structurally stable",
                    "${gate.modeLabel()} preflight stable for ${gate.stableFor(now)}ms across ${gate.stableSampleCount()} checks; final verification pending"
                )
                handler.postDelayed({
                    finalVerifyAndDispatch(context, expectedGeneration, pkg, payload, expectedSignature)
                }, FINAL_VERIFY_MS)
                return
            }

            FludPreflightGate.Decision.WAIT -> {
                val diagnostic = snapshot?.diagnostic
                    ?: if (foreground) "Flud main window visible but torrent rows are not structurally ready" else "Flud is not currently foreground"
                FludAutoStartService.report(
                    "Waiting for Flud structural readiness",
                    "$diagnostic; mode=${gate.modeLabel()}, stable=${gate.stableFor(now)}ms/${gate.stableSampleCount()} samples, recoveryOpens=${gate.recoveryOpenCount()}, elapsed=${elapsed / 1000}s"
                )
            }
        }

        scheduleCheck(context, expectedGeneration, CHECK_INTERVAL_MS)
    }

    private fun finalVerifyAndDispatch(
        context: Context,
        expectedGeneration: Long,
        pkg: String,
        payload: PendingPayload,
        expectedSignature: String
    ) {
        synchronized(lock) {
            if (expectedGeneration != generation || queuedPayload != payload || queuedPackage != pkg) return
        }

        val foreground = FludAutoStartService.isFludForeground(pkg)
        val snapshot = if (foreground) FludAutoStartService.torrentListSnapshot(pkg) else null
        if (!foreground || snapshot?.readyCandidate != true || snapshot.signature != expectedSignature) {
            gate.invalidate()
            FludAutoStartService.report(
                "Flud changed during final readiness check",
                "Preflight was reset before handoff; ${payload.label()} is still local and unsent"
            )
            scheduleCheck(context, expectedGeneration, CHECK_INTERVAL_MS)
            return
        }

        synchronized(lock) {
            if (expectedGeneration != generation || queuedPayload != payload || queuedPackage != pkg) return
            clearQueueLocked()
        }

        val result = dispatchNow(context, pkg, payload, "stable torrent-list fingerprint verified twice")
        BridgePreferences.recordLastCommand(context, "Auto-start: ${result.message}", result.success)
    }

    private fun dispatchNow(
        context: Context,
        pkg: String,
        payload: PendingPayload,
        reason: String
    ): FludLauncher.Result {
        FludAutoStartService.request(pkg)
        val result = when (payload) {
            is PendingPayload.Magnet -> FludAutoStartService.handoffMagnet(context, pkg, payload.value)
            is PendingPayload.Torrent -> FludAutoStartService.handoffTorrent(context, pkg, payload.value)
        }
        if (result.success) {
            FludAutoStartService.retarget(result.packageName)
            FludAutoStartService.report(
                "${payload.label()} handed to structurally ready Flud - waiting for Add torrent",
                "$reason; exactly one ${payload.label()} handoff was issued"
            )
        } else {
            if (payload is PendingPayload.Torrent) TorrentFileSupport.delete(payload.value)
            FludAutoStartService.cancel("Flud ${payload.label()} handoff failed; auto-start cancelled")
        }
        return if (result.success) {
            result.copy(message = "${result.message}; Auto-start handoff after structural readiness")
        } else result
    }

    private fun clearQueueLocked(discardPayload: Boolean = false) {
        val payload = queuedPayload
        queuedPayload = null
        queuedPackage = null
        queuedAt = 0L
        waitingForHelperConnection = false
        generation += 1L
        if (discardPayload && payload is PendingPayload.Torrent) {
            TorrentFileSupport.delete(payload.value)
        }
    }
}
