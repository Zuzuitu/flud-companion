package media.alexlab.fludremote

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.accessibilityservice.GestureDescription
import android.content.ComponentName
import android.content.Context
import android.graphics.Path
import android.graphics.Rect
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
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
        private const val REQUEST_WINDOW_MS = 120_000L
        private const val MIN_REHANDOFF_ELAPSED_MS = 50_000L
        private const val FORCE_REHANDOFF_ELAPSED_MS = 80_000L
        private const val FLUD_QUIET_BEFORE_REHANDOFF_MS = 5_000L
        private const val MAX_REHANDOFF_ATTEMPTS = 1
        private const val CONFIRMATION_FALLBACK_GRACE_MS = 3_000L
        private const val GESTURE_FALLBACK_GRACE_MS = 4_500L
        private const val GESTURE_VERIFY_DELAY_MS = 900L
        private const val CONFIRMATION_QUIET_MS = 2_500L
        private const val RETRY_DELAY_MS = 360L
        private const val CLICK_DELAY_MS = 190L
        private const val MAX_SCAN_NODES = 220
        private const val SEMANTIC_SCORE_THRESHOLD = 8
        private const val STRATEGY = "semantic-v7+quiet-gated-cold-start+strict-confirmation+navigation-lock"

        @Volatile private var pendingUntil = 0L
        @Volatile private var pendingSince = 0L
        @Volatile private var pendingPackage: String? = null
        @Volatile private var lastStatus = "Idle"
        @Volatile private var lastDiagnostic = "No auto-start attempt yet"
        @Volatile private var activeService: FludAutoStartService? = null
        @Volatile private var filePickerRecoveries = 0
        @Volatile private var confirmationSeenAt = 0L
        @Volatile private var rehandoffAttempts = 0
        @Volatile private var lastFludEventAt = 0L
        private val attemptScheduled = AtomicBoolean(false)
        private val gestureFallbackInFlight = AtomicBoolean(false)

        fun request(packageName: String?) {
            pendingPackage = packageName
            pendingSince = System.currentTimeMillis()
            pendingUntil = pendingSince + REQUEST_WINDOW_MS
            filePickerRecoveries = 0
            confirmationSeenAt = 0L
            rehandoffAttempts = 0
            lastFludEventAt = pendingSince
            gestureFallbackInFlight.set(false)
            lastStatus = "Armed - waiting for Flud magnet confirmation"
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
            if (activeService != null) return true

            val expected = ComponentName(context, FludAutoStartService::class.java)
            val manager = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager

            val listed = try {
                manager.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
                    .any { entry ->
                        val info = entry.resolveInfo?.serviceInfo ?: return@any false
                        val rawName = info.name.orEmpty()
                        val fullName = when {
                            rawName.startsWith(".") -> info.packageName + rawName
                            rawName.contains('.') -> rawName
                            rawName.isNotBlank() -> info.packageName + "." + rawName
                            else -> ""
                        }
                        info.packageName == expected.packageName && fullName == expected.className
                    }
            } catch (_: Exception) {
                false
            }
            if (listed) return true

            // Android TV builds can keep the service enabled in Settings while the
            // AccessibilityManager list is stale or reports a relative service name.
            // Fall back to the authoritative secure setting used by the system UI.
            return try {
                val accessibilityOn = Settings.Secure.getInt(
                    context.contentResolver,
                    Settings.Secure.ACCESSIBILITY_ENABLED,
                    0
                ) == 1
                if (!accessibilityOn) return false

                val enabled = Settings.Secure.getString(
                    context.contentResolver,
                    Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
                ).orEmpty()

                enabled.split(':')
                    .mapNotNull { ComponentName.unflattenFromString(it.trim()) }
                    .any { component ->
                        component.packageName == expected.packageName &&
                            component.className == expected.className
                    }
            } catch (_: Exception) {
                false
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
            confirmationSeenAt = 0L
            rehandoffAttempts = 0
            lastFludEventAt = 0L
            gestureFallbackInFlight.set(false)
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
        if (!pkg.isNullOrBlank() && (expected.isNullOrBlank() || pkg == expected)) {
            lastFludEventAt = System.currentTimeMillis()
        }
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
            // Once the real Add torrent screen has been observed, the request is navigation-locked.
            // A late/stale retry must never press Back or hand the magnet to Flud again because that
            // can reopen Add torrent and then kick the user out of the app flow.
            if (confirmationSeenAt > 0L) {
                lastStatus = "Late file picker ignored after Add torrent was already detected"
                lastDiagnostic = "Navigation lock active - no Back and no re-handoff after confirmation"
                scheduleAttempt(RETRY_DELAY_MS)
                return
            }

            if (filePickerRecoveries < 1) {
                filePickerRecoveries += 1
                lastStatus = "Wrong Flud file picker detected - returning to Flud and waiting for it to settle"
                lastDiagnostic = "Detected .torrent file picker before Add torrent; sent Back once. Re-handoff is deferred until Flud is quiet."
                try { performGlobalAction(GLOBAL_ACTION_BACK) } catch (_: Exception) { }
                lastFludEventAt = System.currentTimeMillis()
                scheduleAttempt(900L)
            } else {
                lastStatus = "Waiting for Flud magnet screen after file-picker recovery"
                scheduleAttempt(RETRY_DELAY_MS)
            }
            return
        }

        val now = System.currentTimeMillis()
        val confirmationScreen = looksLikeMagnetConfirmation(screenText)
        if (confirmationScreen && confirmationSeenAt <= 0L) {
            confirmationSeenAt = now
            // Navigation lock: from here on this request may only confirm the existing Add torrent
            // screen. No more re-handoff and no Back recovery are allowed.
            rehandoffAttempts = MAX_REHANDOFF_ATTEMPTS
            lastDiagnostic = "Add torrent detected - navigation locked; only final confirmation is allowed"
        }

        if (confirmationScreen) {
            val action = confirmationActionCandidate(root)
            if (action != null) {
                val clicked = try {
                    action.clickable.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                } catch (_: Exception) {
                    false
                }
                if (clicked) {
                    val readable = action.label.ifBlank { action.viewId.ifBlank { action.className } }
                    clear(
                        "Auto-start completed on Add torrent screen",
                        "Clicked top-right Add torrent confirmation: ${readable.take(120)} (score ${action.score})"
                    )
                    return
                }
            }
        }

        if (confirmationScreen) {
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
            }
        }

        if (!confirmationScreen) {
            if (confirmationSeenAt > 0L) {
                lastStatus = "Add torrent was already detected - waiting without navigation retries"
                scheduleAttempt(RETRY_DELAY_MS)
                return
            }
            val elapsed = now - pendingSince
            val quietFor = (now - lastFludEventAt).coerceAtLeast(0L)
            val settledEnough = elapsed >= MIN_REHANDOFF_ELAPSED_MS && quietFor >= FLUD_QUIET_BEFORE_REHANDOFF_MS
            val forceLateRetry = elapsed >= FORCE_REHANDOFF_ELAPSED_MS
            if (rehandoffAttempts < MAX_REHANDOFF_ATTEMPTS && (settledEnough || forceLateRetry)) {
                rehandoffAttempts += 1
                val retry = FludLauncher.relaunchLastMagnet(this, REQUEST_WINDOW_MS)
                if (retry?.success == true) {
                    retarget(retry.packageName)
                    lastFludEventAt = System.currentTimeMillis()
                    lastStatus = "Flud settled - magnet handed to Flud again once"
                    lastDiagnostic = "No Add torrent screen after ${elapsed}ms; quiet for ${quietFor}ms before the single guarded re-handoff"
                    scheduleAttempt(1_500L)
                    return
                }
            }
            lastStatus = when {
                elapsed < MIN_REHANDOFF_ELAPSED_MS -> "Flud is still loading its torrent list - waiting"
                rehandoffAttempts < MAX_REHANDOFF_ATTEMPTS && quietFor < FLUD_QUIET_BEFORE_REHANDOFF_MS -> "Flud is still changing - waiting for the torrent list to settle"
                rehandoffAttempts < MAX_REHANDOFF_ATTEMPTS -> "Waiting for a safe cold-start re-handoff"
                else -> "Waiting for the real Flud Add torrent screen"
            }
            scheduleAttempt(RETRY_DELAY_MS)
            return
        }

        val confirmationElapsed = now - confirmationSeenAt
        val confirmationQuietFor = (now - lastFludEventAt).coerceAtLeast(0L)
        if (confirmationElapsed < CONFIRMATION_FALLBACK_GRACE_MS || confirmationQuietFor < CONFIRMATION_QUIET_MS) {
            lastStatus = "Add torrent detected - waiting for metadata and controls to settle"
            scheduleAttempt(RETRY_DELAY_MS)
            return
        }

        // D-pad/gesture fallback is allowed only after the real Add torrent screen is stable.
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
        }.filterNotNull().filter { it.isNotBlank() }.take(200).joinToString(" | ") { normalize(it) }.take(6000)
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

    private fun looksLikeMagnetConfirmation(summary: String): Boolean {
        val titles = listOf(
            "add torrent",
            "aggiungi torrent",
            "adauga torrent",
            "ajouter un torrent",
            "torrent hinzufugen"
        )
        val titleSeen = titles.any { summary.contains(it) }

        // On a slow Shield cold start Flud sometimes exposes the Add torrent title only
        // briefly (or not at all) through Accessibility. The INFORMATION + FILES tab pair
        // plus one torrent-detail marker is a stable signature of the real confirmation
        // screen and does not match Flud's main torrent-list screen.
        val tabPairs = listOf(
            "information" to "files",
            "informazioni" to "file",
            "informatii" to "fisiere",
            "informations" to "fichiers",
            "informationen" to "dateien"
        )
        val tabPairSeen = tabPairs.any { (left, right) -> summary.contains(left) && summary.contains(right) }
        val detailMarkers = listOf(
            "storage path", "torrent settings", "download path", "hash", "size",
            "percorso", "impostazioni torrent", "dimensione",
            "cale stocare", "setari torrent", "dimensiune",
            "emplacement", "parametres torrent", "taille",
            "speicher", "torrent einstellungen", "grosse"
        )
        val detailCount = detailMarkers.count { summary.contains(it) }

        // The main Flud screen may expose an Add torrent action while the torrent list is
        // still loading. Never treat that as confirmation. The real magnet confirmation
        // must expose the INFORMATION/FILES tab pair plus at least one torrent detail.
        return tabPairSeen && detailCount >= 1
    }

    private fun confirmationActionCandidate(root: AccessibilityNodeInfo): Candidate? {
        val rootBounds = Rect().also { root.getBoundsInScreen(it) }
        if (rootBounds.isEmpty || rootBounds.width() <= 0 || rootBounds.height() <= 0) return null
        val nodes = ArrayList<AccessibilityNodeInfo>()
        collectNodes(root, nodes)
        val result = ArrayList<Candidate>()
        val rightGate = rootBounds.left + (rootBounds.width() * 0.66f).toInt()
        val topGate = rootBounds.top + (rootBounds.height() * 0.24f).toInt()
        val farRight = rootBounds.left + (rootBounds.width() * 0.82f).toInt()
        val farTop = rootBounds.top + (rootBounds.height() * 0.16f).toInt()

        for (node in nodes) {
            val clickable = nearestClickable(node) ?: continue
            if (!clickable.isEnabled) continue
            val bounds = Rect().also { clickable.getBoundsInScreen(it) }
            if (bounds.isEmpty || bounds.centerX() < rightGate || bounds.centerY() > topGate) continue
            if (bounds.width() > rootBounds.width() / 3 || bounds.height() > rootBounds.height() / 3) continue

            val labelRaw = listOfNotNull(
                node.text?.toString(), node.contentDescription?.toString(),
                clickable.text?.toString(), clickable.contentDescription?.toString()
            ).firstOrNull { it.isNotBlank() }.orEmpty().trim()
            val viewId = (node.viewIdResourceName ?: clickable.viewIdResourceName ?: "").trim()
            val className = (node.className ?: clickable.className ?: "").toString()
            val label = normalize(labelRaw)
            val id = normalize(viewId)
            if (isNegative(label, id)) continue

            var score = 0
            if (label in setOf(
                    "add", "add torrent", "ok", "done", "save", "confirm",
                    "aggiungi", "aggiungi torrent", "conferma",
                    "adauga", "adauga torrent", "confirma",
                    "ajouter", "valider", "hinzufugen", "bestatigen"
                )) score += 10
            if (listOf("add", "confirm", "positive", "done", "save", "fab", "action").any { id.contains(it) }) score += 8
            val lowerClass = className.lowercase(Locale.US)
            if (lowerClass.contains("imagebutton") || lowerClass.contains("button")) score += 3
            if (clickable.isClickable) score += 2
            if (clickable.isEnabled) score += 1
            if (bounds.centerX() >= farRight) score += 2
            if (bounds.centerY() <= farTop) score += 2

            if (score >= 8) result += Candidate(node, clickable, score, labelRaw, viewId, className, bounds)
        }

        return result.maxByOrNull { it.score }
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
        if (!looksLikeMagnetConfirmation(screenSummary(root))) {
            lastStatus = "D-pad fallback blocked outside Add torrent screen"
            scheduleAttempt(RETRY_DELAY_MS)
            return
        }

        val current = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
            ?: root.findFocus(AccessibilityNodeInfo.FOCUS_ACCESSIBILITY)
            ?: findFocusedNode(root)

        if (current == null) {
            lastStatus = "No D-pad focus exposed - trying guarded top-right fallback"
            if (attemptScreenTapFallback(root, "no current D-pad focus")) return
            scheduleAttempt(RETRY_DELAY_MS)
            return
        }

        val firstRight = try { current.focusSearch(View.FOCUS_RIGHT) } catch (_: Exception) { null }
        val secondRight = try { firstRight?.focusSearch(View.FOCUS_RIGHT) } catch (_: Exception) { null }

        if (secondRight == null) {
            lastStatus = "No Right -> Right target - trying guarded top-right fallback"
            if (attemptScreenTapFallback(root, "no Right -> Right target")) return
            scheduleAttempt(RETRY_DELAY_MS)
            return
        }

        val focused = try { secondRight.performAction(AccessibilityNodeInfo.ACTION_FOCUS) } catch (_: Exception) { false }
        if (!focused) {
            lastStatus = "Could not move D-pad focus - trying guarded top-right fallback"
            if (attemptScreenTapFallback(root, "D-pad target refused focus")) return
            scheduleAttempt(RETRY_DELAY_MS)
            return
        }

        lastStatus = "Using guarded Right -> Right -> OK fallback"
        handler.postDelayed({
            if (!hasPendingRequest()) return@postDelayed
            val freshRoot = rootInActiveWindow
            if (freshRoot == null || !looksLikeMagnetConfirmation(screenSummary(freshRoot))) {
                lastStatus = "Add torrent screen changed before fallback click - waiting"
                scheduleAttempt(RETRY_DELAY_MS)
                return@postDelayed
            }
            if (looksLikeTorrentFilePicker(screenSummary(freshRoot))) {
                lastStatus = "File picker appeared after Add torrent - navigation lock kept it untouched"
                lastDiagnostic = "$lastDiagnostic | no Back after confirmation lock"
                scheduleAttempt(RETRY_DELAY_MS)
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

    private fun attemptScreenTapFallback(root: AccessibilityNodeInfo, reason: String): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return false
        if (!looksLikeMagnetConfirmation(screenSummary(root))) return false

        val seenAt = confirmationSeenAt
        val now = System.currentTimeMillis()
        if (seenAt <= 0L || now - seenAt < GESTURE_FALLBACK_GRACE_MS) return false
        if (now - lastFludEventAt < CONFIRMATION_QUIET_MS) return false
        if (!gestureFallbackInFlight.compareAndSet(false, true)) return true

        val bounds = Rect().also { root.getBoundsInScreen(it) }
        if (bounds.isEmpty || bounds.width() <= 0 || bounds.height() <= 0) {
            gestureFallbackInFlight.set(false)
            return false
        }

        // Flud's Add torrent confirmation is the compact '+' action in the top-right.
        // This coordinate fallback is screen-gated and only becomes eligible after the
        // real Add torrent UI has remained visible long enough to avoid the main-screen FAB.
        val x = bounds.left + bounds.width() * 0.965f
        val y = bounds.top + bounds.height() * 0.055f
        val path = Path().apply { moveTo(x, y) }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0L, 80L))
            .build()

        lastStatus = "Using guarded top-right Add torrent tap fallback"
        lastDiagnostic = "$lastDiagnostic | gesture fallback: $reason"

        val dispatched = try {
            dispatchGesture(gesture, object : GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription?) {
                    gestureFallbackInFlight.set(false)
                    handler.postDelayed({
                        if (!hasPendingRequest()) return@postDelayed
                        val freshRoot = rootInActiveWindow
                        if (freshRoot == null) {
                            clear(
                                "Auto-start completed with guarded screen tap",
                                "$lastDiagnostic | Add torrent window closed after top-right tap"
                            )
                            return@postDelayed
                        }
                        val freshSummary = screenSummary(freshRoot)
                        if (looksLikeTorrentFilePicker(freshSummary)) {
                            lastStatus = "Unexpected file picker after guarded tap - waiting"
                            scheduleAttempt(RETRY_DELAY_MS)
                            return@postDelayed
                        }
                        if (!looksLikeMagnetConfirmation(freshSummary)) {
                            clear(
                                "Auto-start completed with guarded screen tap",
                                "$lastDiagnostic | top-right Add torrent tap accepted"
                            )
                        } else {
                            lastStatus = "Top-right tap sent but Add torrent is still visible - retrying"
                            scheduleAttempt(RETRY_DELAY_MS)
                        }
                    }, GESTURE_VERIFY_DELAY_MS)
                }

                override fun onCancelled(gestureDescription: GestureDescription?) {
                    gestureFallbackInFlight.set(false)
                    lastStatus = "Guarded top-right tap was cancelled - retrying"
                    scheduleAttempt(RETRY_DELAY_MS)
                }
            }, null)
        } catch (_: Exception) {
            false
        }

        if (!dispatched) gestureFallbackInFlight.set(false)
        return dispatched
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
