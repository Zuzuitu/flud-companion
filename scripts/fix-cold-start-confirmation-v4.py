from pathlib import Path

service_path = Path('app/src/main/java/media/alexlab/fludremote/FludAutoStartService.kt')
xml_path = Path('app/src/main/res/xml/flud_autostart_accessibility.xml')

s = service_path.read_text()
original = s

s = s.replace(
    'import android.accessibilityservice.AccessibilityService\nimport android.accessibilityservice.AccessibilityServiceInfo\nimport android.content.Context\nimport android.graphics.Rect\nimport android.os.Handler\nimport android.os.Looper\n',
    'import android.accessibilityservice.AccessibilityService\nimport android.accessibilityservice.AccessibilityServiceInfo\nimport android.accessibilityservice.GestureDescription\nimport android.content.Context\nimport android.graphics.Path\nimport android.graphics.Rect\nimport android.os.Build\nimport android.os.Handler\nimport android.os.Looper\n'
)

s = s.replace(
    '        private const val CONFIRMATION_FALLBACK_GRACE_MS = 900L\n        private const val RETRY_DELAY_MS = 360L\n        private const val CLICK_DELAY_MS = 190L\n        private const val MAX_SCAN_NODES = 220\n        private const val SEMANTIC_SCORE_THRESHOLD = 8\n        private const val STRATEGY = "semantic-v3+screen-gated-fallback+single-rehandoff"\n',
    '        private const val CONFIRMATION_FALLBACK_GRACE_MS = 900L\n        private const val GESTURE_FALLBACK_GRACE_MS = 1_600L\n        private const val GESTURE_VERIFY_DELAY_MS = 650L\n        private const val RETRY_DELAY_MS = 360L\n        private const val CLICK_DELAY_MS = 190L\n        private const val MAX_SCAN_NODES = 220\n        private const val SEMANTIC_SCORE_THRESHOLD = 8\n        private const val STRATEGY = "semantic-v4+screen-gated-gesture+single-rehandoff"\n'
)

s = s.replace(
    '        @Volatile private var confirmationSeenAt = 0L\n        @Volatile private var rehandoffAttempts = 0\n        private val attemptScheduled = AtomicBoolean(false)\n',
    '        @Volatile private var confirmationSeenAt = 0L\n        @Volatile private var rehandoffAttempts = 0\n        private val attemptScheduled = AtomicBoolean(false)\n        private val gestureFallbackInFlight = AtomicBoolean(false)\n'
)

s = s.replace(
    '            rehandoffAttempts = 0\n            lastStatus = "Armed - waiting for Flud magnet confirmation"\n',
    '            rehandoffAttempts = 0\n            gestureFallbackInFlight.set(false)\n            lastStatus = "Armed - waiting for Flud magnet confirmation"\n'
)

s = s.replace(
    '            rehandoffAttempts = 0\n            lastStatus = status\n',
    '            rehandoffAttempts = 0\n            gestureFallbackInFlight.set(false)\n            lastStatus = status\n'
)

s = s.replace(
    '.filterNotNull().filter { it.isNotBlank() }.take(120).joinToString(" | ") { normalize(it) }.take(3500)',
    '.filterNotNull().filter { it.isNotBlank() }.take(200).joinToString(" | ") { normalize(it) }.take(6000)'
)

old_confirmation = '''    private fun looksLikeMagnetConfirmation(summary: String): Boolean {
        val titles = listOf(
            "add torrent",
            "aggiungi torrent",
            "adauga torrent",
            "ajouter un torrent",
            "torrent hinzufugen"
        )
        if (titles.none { summary.contains(it) }) return false
        val markers = listOf(
            "information", "files", "storage path", "torrent settings", "hash", "name",
            "informazioni", "percorso", "impostazioni torrent",
            "informatii", "fisiere", "cale stocare",
            "informations", "fichiers", "emplacement",
            "informationen", "dateien", "speicher"
        )
        return markers.count { summary.contains(it) } >= 2
    }
'''

new_confirmation = '''    private fun looksLikeMagnetConfirmation(summary: String): Boolean {
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
            "storage path", "torrent settings", "hash", "name", "download path",
            "percorso", "impostazioni torrent", "nome",
            "cale stocare", "setari torrent", "nume",
            "emplacement", "parametres torrent", "nom",
            "speicher", "torrent einstellungen", "name"
        )
        val detailCount = detailMarkers.count { summary.contains(it) }

        return (titleSeen && (tabPairSeen || detailCount >= 1)) ||
            (tabPairSeen && detailCount >= 1)
    }
'''

if old_confirmation not in s:
    raise SystemExit('Expected looksLikeMagnetConfirmation block not found')
s = s.replace(old_confirmation, new_confirmation)

s = s.replace(
'''        if (current == null) {
            lastStatus = "Waiting for a safe D-pad fallback focus"
            scheduleAttempt(RETRY_DELAY_MS)
            return
        }
''',
'''        if (current == null) {
            lastStatus = "No D-pad focus exposed - trying guarded top-right fallback"
            if (attemptScreenTapFallback(root, "no current D-pad focus")) return
            scheduleAttempt(RETRY_DELAY_MS)
            return
        }
'''
)

s = s.replace(
'''        if (secondRight == null) {
            lastStatus = "Waiting for Right -> Right fallback target"
            scheduleAttempt(RETRY_DELAY_MS)
            return
        }
''',
'''        if (secondRight == null) {
            lastStatus = "No Right -> Right target - trying guarded top-right fallback"
            if (attemptScreenTapFallback(root, "no Right -> Right target")) return
            scheduleAttempt(RETRY_DELAY_MS)
            return
        }
'''
)

s = s.replace(
'''        if (!focused) {
            lastStatus = "Could not move D-pad focus to fallback target"
            scheduleAttempt(RETRY_DELAY_MS)
            return
        }
''',
'''        if (!focused) {
            lastStatus = "Could not move D-pad focus - trying guarded top-right fallback"
            if (attemptScreenTapFallback(root, "D-pad target refused focus")) return
            scheduleAttempt(RETRY_DELAY_MS)
            return
        }
'''
)

insert_before = '''    private fun findFocusedNode(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
'''

gesture_method = '''    private fun attemptScreenTapFallback(root: AccessibilityNodeInfo, reason: String): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return false
        if (!looksLikeMagnetConfirmation(screenSummary(root))) return false

        val seenAt = confirmationSeenAt
        if (seenAt <= 0L || System.currentTimeMillis() - seenAt < GESTURE_FALLBACK_GRACE_MS) return false
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

'''

if insert_before not in s:
    raise SystemExit('Expected findFocusedNode anchor not found')
s = s.replace(insert_before, gesture_method + insert_before)

if s == original:
    raise SystemExit('No service changes made')
service_path.write_text(s)

x = xml_path.read_text()
if 'android:canPerformGestures="true"' not in x:
    x = x.replace(
        '    android:canRetrieveWindowContent="true"\n',
        '    android:canRetrieveWindowContent="true"\n    android:canPerformGestures="true"\n'
    )
xml_path.write_text(x)

print('Cold-start confirmation v4 patch applied.')
