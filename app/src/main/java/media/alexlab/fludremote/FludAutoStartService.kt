package media.alexlab.fludremote

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityManager
import android.view.accessibility.AccessibilityNodeInfo
import java.text.Normalizer
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Optional helper used only after an explicit LAN or Remote command requests auto-start.
 *
 * v0.20.0 guarded strategy:
 *  1. Arm the helper before the magnet intent is launched, so slow/cold Flud starts do not
 *     lose the first accessibility events.
 *  2. Wait up to 20 seconds and keep scanning for a high-confidence confirmation control.
 *  3. Never treat Flud's generic main-screen Add/FAB as a confirmation button. This avoids
 *     accidentally opening the ".torrent file" picker while a magnet is still loading.
 *  4. If the torrent-file picker is detected during an armed request, back out once and
 *     continue waiting for the magnet confirmation screen.
 *  5. Use Right -> Right -> OK only as a late fallback, after Flud has had ample time to
 *     render the magnet UI.
 */
class FludAutoStartService : AccessibilityService() {
    companion object {
        private const val REQUEST_WINDOW_MS = 20_000L
        private const val SEMANTIC_GRACE_MS = 3_200L
        private const val FALLBACK_GRACE_MS = 7_000L
        private const val RETRY_DELAY_MS = 360L
        private const val CLICK_DELAY_MS = 190L
        private const val MAX_SCAN_NODES = 220
        private const val SEMANTIC_SCORE_THRESHOLD = 8
        private const val STRATEGY = "semantic-v2+guarded-fallback"

        @Volatile private var pendingUntil = 0L
        @Volatile private var pendingSince = 0L
        @Volatile private var pendingPackage: String? = null
        @Volatile private var lastStatus = "Idle"
        @Volatile private var lastDiagnostic = "No auto-start attempt yet"
        @Volatile private var activeService: FludAutoStartService? = null
        @Volatile private var filePickerRecoveries = 0
        private val attemptScheduled = AtomicBoolean(false)

        fun request(packageName: String?) {
            pendingPackage = packageName
            pendingSince = System.currentTimeMillis()
            pendingUntil = pendingSince + REQUEST_WINDOW_MS
            filePickerRecoveries = 0
            lastStatus = "Armed — waiting for Flud magnet confirmation"
            lastDiagnostic = "Guarded semantic scan pending"
            activeService?.scheduleAttempt(180L)
        }

        fun cancel(reason: String = "Auto-start cancelled") {
            clear(reason, reason)
        }

        fun retarget(packageName: String?) {
            if (!packageName.isNullOrBlank()) pendingPackage = packageName
        }

        fun isEnabled(context: Context): Boolean {
            val manager = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
            return manager.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
                .any {
                    val info = it.resolveInfo?.serviceInfo
                    info?.packageName == context.packageName && info.name == FludAutoStartService::class.java.name
                }
        }

        fun status(): String = lastStatus
        fun diagnostic(): String = lastDiagnostic
        fun strategy(): String = STRATEGY

        private fun clear(status: String, diagnostic: String? = null) {
            pendingUntil = 0L
            pendingSince = 0L
            pendingPackage = null
            filePickerRecoveries = 0
            lastStatus = status
            if (!diagnostic.isNullOrBlank()) lastDiagnostic = diagnostic
            attemptScheduled.set(false)
        }
    }

    private data class Candidate(
        val node: AccessibilityNodeInfo,
        val clickable: AccessibilityNodeInfo,
        val score: Int,
        val label: String,
        val viewId: String,
        val className: String,
        val bounds: Rect
    )

    private val handler = Handler(Looper.getMainLooper())

    override fun onServiceConnected() {
        super.onServiceConnected()
        activeService = this
        lastStatus = "Ready — guarded semantic confirmation"
    }

    override fun onDestroy() {
        if (activeService === this) activeService = null
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (!hasPendingRequest()) return
        val pkg = event?.packageName?.toString()
        val expected = pendingPackage
        if (!expected.isNullOrBlank() && !pkg.isNullOrBlank() && pkg != expected) return
        scheduleAttempt(220L)
    }

    override fun onInterrupt() {
        lastStatus = "Accessibility helper interrupted"
    }

    private fun hasPendingRequest(): Boolean {
        val until = pendingUntil
        if (until <= 0L) return false
        if (System.currentTimeMillis() > until) {
            clear("Auto-start timed out", lastDiagnostic)
            return false
        }
        return true
    }

    private fun scheduleAttempt(delayMs: Long) {
        if (!hasPendingRequest()) return
        if (!attemptScheduled.compareAndSet(false, true)) return
        handler.postDelayed({
            attemptScheduled.set(false)
            attemptAutoStart()
        }, delayMs)
    }

    private fun attemptAutoStart() {
        if (!hasPendingRequest()) return
        val root = rootInActiveWindow
        if (root == null) {
            lastStatus = "Waiting for Flud window"
            scheduleAttempt(RETRY_DELAY_MS)
            return
        }

        val expected = pendingPackage
        val rootPackage = root.packageName?.toString()
        if (!expected.isNullOrBlank() && !rootPackage.isNullOrBlank() && rootPackage != expected) {
            lastStatus = "Waiting for Flud to become active"
            scheduleAttempt(RETRY_DELAY_MS)
            return
        }

        val screenText = screenSummary(root)
        if (looksLikeTorrentFilePicker(screenText)) {
            if (filePickerRecoveries < 1) {
                filePickerRecoveries += 1
                lastStatus = "Wrong Flud file picker detected — returning to magnet flow"
                lastDiagnostic = "Detected .torrent file picker during auto-start; sent Back and kept request armed"
                try { performGlobalAction(GLOBAL_ACTION_BACK) } catch (_: Exception) { }
                scheduleAttempt(750L)
            } else {
                lastStatus = "Waiting for Flud magnet screen after file-picker recovery"
                scheduleAttempt(RETRY_DELAY_MS)
            }
            return
        }

        val candidates = semanticCandidates(root)
        lastDiagnostic = diagnosticSummary(candidates)
        val best = candidates.firstOrNull { it.score >= SEMANTIC_SCORE_THRESHOLD }
        if (best != null) {
            val clicked = try {
                best.clickable.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            } catch (_: Exception) {
                false
            }
            if (clicked) {
                val readable = best.label.ifBlank { best.viewId.ifBlank { best.className } }
                clear(
                    "Auto-start completed semantically",
                    "Clicked guarded confirmation: ${readable.take(120)} (score ${best.score})"
                )
                return
            }
            lastStatus = "Confirmation found; waiting for it to become clickable"
        } else {
            lastStatus = "Waiting for Flud magnet confirmation controls"
        }

        val elapsed = System.currentTimeMillis() - pendingSince
        if (elapsed < SEMANTIC_GRACE_MS) {
            scheduleAttempt(RETRY_DELAY_MS)
            return
        }

        // Do not use D-pad navigation early. On a slow warm/cold start the main Flud screen
        // can be visible for several seconds, and Right -> Right -> OK there may open Add file.
        if (elapsed < FALLBACK_GRACE_MS) {
            lastStatus = "Flud is still settling — guarded fallback delayed"
            scheduleAttempt(RETRY_DELAY_MS)
            return
        }

        attemptFocusFallback(root)
    }

    private fun semanticCandidates(root: AccessibilityNodeInfo): List<Candidate> {
        val rootBounds = Rect().also { root.getBoundsInScreen(it) }
        val nodes = ArrayList<AccessibilityNodeInfo>()
        collectNodes(root, nodes)
        val result = ArrayList<Candidate>()

        for (node in nodes) {
            val clickable = nearestClickable(node) ?: continue
            if (!clickable.isEnabled) continue

            val labelRaw = listOfNotNull(
                node.text?.toString(),
                node.contentDescription?.toString(),
                clickable.text?.toString(),
                clickable.contentDescription?.toString()
            ).firstOrNull { it.isNotBlank() }.orEmpty().trim()
            val viewId = (node.viewIdResourceName ?: clickable.viewIdResourceName ?: "").trim()
            val className = (node.className ?: clickable.className ?: "").toString()
            val label = normalize(labelRaw)
            val id = normalize(viewId)

            if (isNegative(label, id)) continue

            var score = 0
            if (isExactPositive(label)) score += 7
            else if (containsPositive(label)) score += 3
            if (containsPositiveId(id)) score += 6
            if (className.lowercase(Locale.US).contains("button")) score += 2
            if (clickable.isClickable) score += 2
            if (clickable.isEnabled) score += 1
            if (clickable.isFocusable) score += 1

            val bounds = Rect().also { clickable.getBoundsInScreen(it) }
            if (!rootBounds.isEmpty && !bounds.isEmpty && bounds.centerX() > rootBounds.centerX()) score += 1

            if (score > 0) {
                result += Candidate(node, clickable, score, labelRaw, viewId, className, bounds)
            }
        }

        return result.sortedWith(compareByDescending<Candidate> { it.score }.thenByDescending { it.bounds.centerX() })
    }

    private fun collectNodes(node: AccessibilityNodeInfo?, out: MutableList<AccessibilityNodeInfo>) {
        if (node == null || out.size >= MAX_SCAN_NODES) return
        out += node
        for (i in 0 until node.childCount) {
            if (out.size >= MAX_SCAN_NODES) break
            collectNodes(node.getChild(i), out)
        }
    }

    private fun screenSummary(root: AccessibilityNodeInfo): String {
        val nodes = ArrayList<AccessibilityNodeInfo>()
        collectNodes(root, nodes)
        return nodes.asSequence().flatMap { node ->
            sequenceOf(
                node.text?.toString(),
                node.contentDescription?.toString(),
                node.viewIdResourceName
            )
        }.filterNotNull().filter { it.isNotBlank() }.take(120).joinToString(" | ") { normalize(it) }.take(3500)
    }

    private fun looksLikeTorrentFilePicker(summary: String): Boolean {
        val phrases = listOf(
            "select a torrent file to add",
            "select torrent file",
            "choose a torrent file",
            "choose torrent file",
            "seleziona un file torrent da aggiungere",
            "seleziona file torrent",
            "scegli un file torrent",
            "selecteaza un fisier torrent",
            "alege un fisier torrent"
        )
        return phrases.any { summary.contains(it) }
    }

    private fun normalize(value: String): String {
        val ascii = Normalizer.normalize(value, Normalizer.Form.NFD)
            .replace("\\p{Mn}+".toRegex(), "")
        return ascii.lowercase(Locale.US)
            .replace("[^a-z0-9_]+".toRegex(), " ")
            .trim()
    }

    private fun isExactPositive(label: String): Boolean {
        // Deliberately no bare "add" here: Flud's main Add/FAB opens the .torrent picker.
        return label in setOf(
            "add torrent", "ok", "start", "start torrent", "confirm", "confirm add",
            "aggiungi torrent", "avvia", "conferma", "si",
            "adauga torrent", "porneste", "confirma", "da",
            "yes"
        )
    }

    private fun containsPositive(label: String): Boolean {
        if (label.isBlank()) return false
        val phrases = listOf(
            "add torrent", "confirm add", "start torrent",
            "aggiungi torrent", "conferma", "avvia",
            "adauga torrent", "confirma", "porneste"
        )
        return phrases.any { label.contains(it) }
    }

    private fun containsPositiveId(id: String): Boolean {
        if (id.isBlank()) return false
        // Avoid generic add/fab ids: those belong to Flud's main-screen file picker action.
        val tokens = listOf(
            "button_ok", "btn_ok", "positive_button", "button_positive", "confirm_button",
            "button_confirm", "start_button", "button_start", "add_torrent_button",
            "button_add_torrent", "confirm_add"
        )
        return tokens.any { id.contains(it) }
    }

    private fun isNegative(label: String, id: String): Boolean {
        val negative = listOf(
            "cancel", "annulla", "anuleaza", "close", "chiudi", "inchide", "delete", "elimina",
            "remove", "sterge", "stop", "pause", "folder", "directory", "path", "location",
            "files", "file picker", "select a torrent file", "select torrent file",
            "settings", "impostazioni", "setari"
        )
        return negative.any { label.contains(it) || id.contains(it.replace(' ', '_')) }
    }

    private fun diagnosticSummary(candidates: List<Candidate>): String {
        if (candidates.isEmpty()) return "No guarded confirmation candidate exposed by Flud yet"
        return candidates.take(4).joinToString(" | ") { c ->
            val name = c.label.ifBlank { c.viewId.ifBlank { c.className } }.take(70)
            "$name [${c.score}]"
        }.take(420)
    }

    private fun attemptFocusFallback(root: AccessibilityNodeInfo) {
        val current = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
            ?: root.findFocus(AccessibilityNodeInfo.FOCUS_ACCESSIBILITY)
            ?: findFocusedNode(root)

        if (current == null) {
            lastStatus = "Waiting for a safe D-pad fallback focus"
            scheduleAttempt(RETRY_DELAY_MS)
            return
        }

        val firstRight = try { current.focusSearch(View.FOCUS_RIGHT) } catch (_: Exception) { null }
        val secondRight = try { firstRight?.focusSearch(View.FOCUS_RIGHT) } catch (_: Exception) { null }

        if (secondRight == null) {
            lastStatus = "Waiting for Right -> Right fallback target"
            scheduleAttempt(RETRY_DELAY_MS)
            return
        }

        val focused = try { secondRight.performAction(AccessibilityNodeInfo.ACTION_FOCUS) } catch (_: Exception) { false }
        if (!focused) {
            lastStatus = "Could not move D-pad focus to fallback target"
            scheduleAttempt(RETRY_DELAY_MS)
            return
        }

        lastStatus = "Using guarded Right -> Right -> OK fallback"
        handler.postDelayed({
            if (!hasPendingRequest()) return@postDelayed
            val freshRoot = rootInActiveWindow
            if (freshRoot != null && looksLikeTorrentFilePicker(screenSummary(freshRoot))) {
                lastStatus = "File picker appeared before OK — recovering"
                if (filePickerRecoveries < 1) {
                    filePickerRecoveries += 1
                    try { performGlobalAction(GLOBAL_ACTION_BACK) } catch (_: Exception) { }
                }
                scheduleAttempt(750L)
                return@postDelayed
            }
            val focusedNow = freshRoot?.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
                ?: freshRoot?.findFocus(AccessibilityNodeInfo.FOCUS_ACCESSIBILITY)
                ?: secondRight
            val clickable = nearestClickable(focusedNow)
            val clicked = try { clickable?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true } catch (_: Exception) { false }

            if (clicked) {
                clear("Auto-start completed with guarded D-pad fallback", "$lastDiagnostic | fallback: Right -> Right -> OK")
            } else {
                lastStatus = "Fallback target found but OK was not accepted — retrying"
                scheduleAttempt(RETRY_DELAY_MS)
            }
        }, CLICK_DELAY_MS)
    }

    private fun findFocusedNode(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        if (node == null) return null
        if (node.isFocused) return node
        for (i in 0 until node.childCount) {
            val found = findFocusedNode(node.getChild(i))
            if (found != null) return found
        }
        return null
    }

    private fun nearestClickable(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        var current = node
        var depth = 0
        while (current != null && depth < 6) {
            if (current.isClickable || current.actionList.any { it.id == AccessibilityNodeInfo.ACTION_CLICK }) return current
            current = current.parent
            depth += 1
        }
        return null
    }
}
