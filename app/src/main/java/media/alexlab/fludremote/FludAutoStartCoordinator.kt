package media.alexlab.fludremote

import android.content.Context
import android.os.Handler
import android.os.Looper

/**
 * Safe Auto-start coordinator.
 *
 * v10 readiness rule: do not use a fixed cold-start delay. Flud is ready when its real
 * torrent list is visible to Accessibility (torrent rows/titles, or an explicit empty-list
 * state). If the list is already visible, dispatch immediately. During a cold start, wait
 * only until the list appears and remains present briefly, then hand the magnet over once.
 */
object FludAutoStartCoordinator {
    private const val LIST_READY_DEBOUNCE_MS = 1_200L
    private const val PREPARE_TIMEOUT_MS = 120_000L
    private const val CHECK_INTERVAL_MS = 400L

    private val handler = Handler(Looper.getMainLooper())
    private val lock = Any()

    @Volatile private var queuedMagnet: String? = null
    @Volatile private var queuedPackage: String? = null
    @Volatile private var queuedAt = 0L
    @Volatile private var listReadySince = 0L
    @Volatile private var generation = 0L

    fun submit(context: Context, magnet: String): FludLauncher.Result {
        val appContext = context.applicationContext
        if (!magnet.startsWith("magnet:?", ignoreCase = true)) {
            return FludLauncher.Result(false, message = "Invalid magnet URI")
        }

        val pkg = FludLauncher.installedPackage(appContext)
            ?: return FludLauncher.Result(false, message = "Flud or Flud+ is not installed")

        synchronized(lock) {
            if (queuedMagnet != null) {
                return FludLauncher.Result(false, pkg, "Another Auto-start magnet is already waiting for Flud")
            }

            // Fast path: the user specifically observed that once torrent titles are already
            // visible on the left, Flud accepts a new magnet within seconds. Do not impose any
            // artificial cold-start delay when that readiness signal is already present.
            if (FludAutoStartService.isFludTorrentListReady(pkg)) {
                return dispatchNow(appContext, pkg, magnet, "torrent list already visible")
            }

            val alreadyForeground = FludAutoStartService.isFludForeground(pkg)
            if (!alreadyForeground) {
                val opened = FludLauncher.openApp(appContext)
                if (!opened.success) return opened
            }

            generation += 1L
            val myGeneration = generation
            queuedMagnet = magnet
            queuedPackage = pkg
            queuedAt = System.currentTimeMillis()
            listReadySince = 0L

            FludAutoStartService.cancel("Waiting for Flud torrent list readiness")
            FludAutoStartService.report(
                "Waiting for Flud torrent list",
                "Magnet is queued locally; it will be sent as soon as existing torrent titles become visible"
            )
            scheduleCheck(appContext, myGeneration, CHECK_INTERVAL_MS)

            return FludLauncher.Result(
                true,
                pkg,
                if (alreadyForeground) {
                    "Flud is open but its torrent list is not ready yet; magnet queued briefly"
                } else {
                    "Flud opened normally; magnet queued until its torrent list becomes visible"
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
                "Torrent-list readiness timed out",
                "Magnet was not sent because Flud never exposed its loaded torrent list"
            )
            BridgePreferences.recordLastCommand(context, "Auto-start: Flud torrent list never became ready", false)
            return
        }

        val foreground = FludAutoStartService.isFludForeground(pkg)
        val ready = foreground && FludAutoStartService.isFludTorrentListReady(pkg)
        if (ready) {
            if (listReadySince <= 0L) listReadySince = now
            val stableFor = now - listReadySince
            if (stableFor >= LIST_READY_DEBOUNCE_MS) {
                synchronized(lock) {
                    if (expectedGeneration != generation) return
                    clearQueueLocked()
                }
                val result = dispatchNow(context, pkg, magnet, "torrent list visible and stable")
                BridgePreferences.recordLastCommand(context, "Auto-start: ${result.message}", result.success)
                return
            }
            FludAutoStartService.report(
                "Flud torrent list detected",
                "Existing torrent titles are visible; confirming readiness for ${stableFor}ms"
            )
        } else {
            listReadySince = 0L
            val probe = FludAutoStartService.torrentListDiagnostic(pkg)
            FludAutoStartService.report(
                "Waiting for Flud torrent list",
                "$probe (${elapsed / 1000}s elapsed; magnet not sent yet)"
            )
        }

        scheduleCheck(context, expectedGeneration, CHECK_INTERVAL_MS)
    }

    private fun dispatchNow(context: Context, pkg: String, magnet: String, reason: String): FludLauncher.Result {
        FludAutoStartService.request(pkg)
        val result = FludLauncher.launchMagnet(context, magnet)
        if (result.success) {
            FludAutoStartService.retarget(result.packageName)
            FludAutoStartService.report(
                "Magnet handed to ready Flud - waiting for Add torrent",
                "$reason; exactly one magnet handoff was issued"
            )
        } else {
            FludAutoStartService.cancel("Flud launch failed; auto-start cancelled")
        }
        return if (result.success) {
            result.copy(message = "${result.message}; Auto-start handoff after torrent-list readiness")
        } else result
    }

    private fun clearQueueLocked() {
        queuedMagnet = null
        queuedPackage = null
        queuedAt = 0L
        listReadySince = 0L
        generation += 1L
    }
}
