package media.alexlab.fludremote

import android.content.Context
import android.os.Handler
import android.os.Looper

/**
 * Safe Auto-start coordinator.
 *
 * v11 readiness rule:
 * - Never dispatch a magnet merely because the first torrent title appeared.
 * - Track the structural fingerprint of visible torrent rows and require it to stop changing.
 * - Warm Flud gets a short structural check; cold/restoring Flud gets a longer adaptive gate.
 * - If Flud disappears before handoff, reopen it safely because no magnet has been sent yet.
 * - After handoff, exactly one magnet handoff remains the hard invariant.
 */
object FludAutoStartCoordinator {
    private const val PREPARE_TIMEOUT_MS = 180_000L
    private const val CHECK_INTERVAL_MS = 400L
    private const val FINAL_VERIFY_MS = 450L

    private val handler = Handler(Looper.getMainLooper())
    private val lock = Any()
    private val gate = FludPreflightGate()

    @Volatile private var queuedMagnet: String? = null
    @Volatile private var queuedPackage: String? = null
    @Volatile private var queuedAt = 0L
    @Volatile private var generation = 0L

    fun submit(context: Context, magnet: String): FludLauncher.Result {
        val appContext = context.applicationContext
        if (!magnet.startsWith("magnet:?", ignoreCase = true)) {
            return FludLauncher.Result(false, message = "Invalid magnet URI")
        }

        val pkg = FludLauncher.installedPackage(appContext)
            ?: return FludLauncher.Result(false, message = "Flud or Flud+ is not installed")

        synchronized(lock) {
            val existing = queuedMagnet
            if (existing != null && existing != magnet) {
                return FludLauncher.Result(false, pkg, "Another Auto-start magnet is still preparing Flud")
            }

            val now = System.currentTimeMillis()
            val foreground = FludAutoStartService.isFludForeground(pkg)
            val snapshot = if (foreground) FludAutoStartService.torrentListSnapshot(pkg) else null
            val cold = !(foreground && snapshot?.readyCandidate == true)

            // A second explicit send of the same magnet is a user retry. queuedMagnet exists
            // only before handoff, so restarting this preflight cannot duplicate a Flud command.
            generation += 1L
            val myGeneration = generation
            queuedMagnet = magnet
            queuedPackage = pkg
            queuedAt = now

            var openRequested = false
            if (!foreground) {
                val opened = FludAutoStartService.openFludForPreflight(appContext, pkg)
                if (!opened.success) {
                    clearQueueLocked()
                    return opened
                }
                openRequested = true
            }

            gate.reset(now, cold = cold, initialOpenRequested = openRequested)
            FludAutoStartService.cancel("Preparing Flud before magnet handoff")
            FludAutoStartService.report(
                if (existing == magnet) "Retrying Flud preflight" else "Preparing Flud",
                if (cold) {
                    "Waiting for torrent-list structure to finish restoring; magnet has not been sent"
                } else {
                    "Flud is already open; verifying the torrent-list structure before handoff"
                }
            )
            scheduleCheck(appContext, myGeneration, CHECK_INTERVAL_MS)

            return FludLauncher.Result(
                true,
                pkg,
                if (existing == magnet) {
                    "Pending Auto-start retry refreshed; magnet is still held locally until Flud is structurally ready"
                } else if (cold) {
                    "Flud is preparing; magnet is held locally until its torrent list stops changing"
                } else {
                    "Flud is open; verifying torrent-list stability before Auto-start handoff"
                }
            )
        }
    }

    private fun scheduleCheck(context: Context, expectedGeneration: Long, delayMs: Long) {
        handler.postDelayed({ checkAndDispatch(context, expectedGeneration) }, delayMs)
    }

    private fun checkAndDispatch(context: Context, expectedGeneration: Long) {
        val magnet: String
        val pkg: String
        val startedAt: Long
        synchronized(lock) {
            if (expectedGeneration != generation) return
            magnet = queuedMagnet ?: return
            pkg = queuedPackage ?: return
            startedAt = queuedAt
        }

        val now = System.currentTimeMillis()
        val elapsed = now - startedAt
        if (elapsed >= PREPARE_TIMEOUT_MS) {
            synchronized(lock) {
                if (expectedGeneration != generation) return
                clearQueueLocked()
            }
            FludAutoStartService.report(
                "Flud preflight timed out",
                "No magnet was sent because the torrent list never reached a stable structural state"
            )
            BridgePreferences.recordLastCommand(context, "Auto-start: Flud never became structurally ready", false)
            return
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
                        clearQueueLocked()
                    }
                    FludAutoStartService.report(
                        "Could not reopen Flud during preflight",
                        reopened.message
                    )
                    BridgePreferences.recordLastCommand(context, "Auto-start: ${reopened.message}", false)
                    return
                }
                gate.markRecoveryOpen(now)
                FludAutoStartService.report(
                    "Flud disappeared before handoff - reopened safely",
                    "Recovery open ${gate.recoveryOpenCount()}; magnet is still local and has not been sent"
                )
                scheduleCheck(context, expectedGeneration, 700L)
                return
            }

            FludPreflightGate.Decision.FAIL -> {
                synchronized(lock) {
                    if (expectedGeneration != generation) return
                    clearQueueLocked()
                }
                FludAutoStartService.report(
                    "Flud could not stay open during preflight",
                    "No magnet was sent; retry is safe"
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
                    finalVerifyAndDispatch(context, expectedGeneration, pkg, magnet, expectedSignature)
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
        magnet: String,
        expectedSignature: String
    ) {
        synchronized(lock) {
            if (expectedGeneration != generation || queuedMagnet != magnet || queuedPackage != pkg) return
        }

        val foreground = FludAutoStartService.isFludForeground(pkg)
        val snapshot = if (foreground) FludAutoStartService.torrentListSnapshot(pkg) else null
        if (!foreground || snapshot?.readyCandidate != true || snapshot.signature != expectedSignature) {
            gate.invalidate()
            FludAutoStartService.report(
                "Flud changed during final readiness check",
                "Preflight was reset before handoff; magnet is still local and unsent"
            )
            scheduleCheck(context, expectedGeneration, CHECK_INTERVAL_MS)
            return
        }

        synchronized(lock) {
            if (expectedGeneration != generation || queuedMagnet != magnet || queuedPackage != pkg) return
            clearQueueLocked()
        }

        val result = dispatchNow(context, pkg, magnet, "stable torrent-list fingerprint verified twice")
        BridgePreferences.recordLastCommand(context, "Auto-start: ${result.message}", result.success)
    }

    private fun dispatchNow(context: Context, pkg: String, magnet: String, reason: String): FludLauncher.Result {
        FludAutoStartService.request(pkg)
        val result = FludAutoStartService.handoffMagnet(context, pkg, magnet)
        if (result.success) {
            FludAutoStartService.retarget(result.packageName)
            FludAutoStartService.report(
                "Magnet handed to structurally ready Flud - waiting for Add torrent",
                "$reason; exactly one magnet handoff was issued"
            )
        } else {
            FludAutoStartService.cancel("Flud magnet handoff failed; auto-start cancelled")
        }
        return if (result.success) {
            result.copy(message = "${result.message}; Auto-start handoff after structural readiness")
        } else result
    }

    private fun clearQueueLocked() {
        queuedMagnet = null
        queuedPackage = null
        queuedAt = 0L
        generation += 1L
    }
}
