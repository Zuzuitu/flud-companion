from pathlib import Path

service = Path('app/src/main/java/media/alexlab/fludremote/FludAutoStartService.kt')
gradle = Path('app/build.gradle.kts')

s = service.read_text()
orig = s

# More conservative cold-start timing: wait for Flud to become quiet instead of retrying on a fixed early timer.
s = s.replace('private const val REHANDOFF_GRACE_MS = 36_000L\n        private const val SECOND_REHANDOFF_GRACE_MS = 58_000L\n        private const val MAX_REHANDOFF_ATTEMPTS = 2',
'''private const val MIN_REHANDOFF_ELAPSED_MS = 50_000L
        private const val FORCE_REHANDOFF_ELAPSED_MS = 80_000L
        private const val FLUD_QUIET_BEFORE_REHANDOFF_MS = 5_000L
        private const val MAX_REHANDOFF_ATTEMPTS = 1''')
s = s.replace('private const val CONFIRMATION_FALLBACK_GRACE_MS = 900L', 'private const val CONFIRMATION_FALLBACK_GRACE_MS = 3_000L')
s = s.replace('private const val GESTURE_FALLBACK_GRACE_MS = 1_600L', 'private const val GESTURE_FALLBACK_GRACE_MS = 4_500L')
s = s.replace('private const val GESTURE_VERIFY_DELAY_MS = 650L', 'private const val GESTURE_VERIFY_DELAY_MS = 900L\n        private const val CONFIRMATION_QUIET_MS = 2_500L')
s = s.replace('private const val STRATEGY = "semantic-v6+120s-cold-start+confirmation-latch+screen-gated-gesture"',
              'private const val STRATEGY = "semantic-v7+quiet-gated-cold-start+strict-confirmation+navigation-lock"')

# Track the last real Flud accessibility event so retries only happen after the app settles.
s = s.replace('@Volatile private var rehandoffAttempts = 0\n        private val attemptScheduled',
'''@Volatile private var rehandoffAttempts = 0
        @Volatile private var lastFludEventAt = 0L
        private val attemptScheduled''')
s = s.replace('rehandoffAttempts = 0\n            gestureFallbackInFlight.set(false)\n            lastStatus = "Armed',
'''rehandoffAttempts = 0
            lastFludEventAt = pendingSince
            gestureFallbackInFlight.set(false)
            lastStatus = "Armed''')
s = s.replace('rehandoffAttempts = 0\n            gestureFallbackInFlight.set(false)\n            lastStatus = status',
'''rehandoffAttempts = 0
            lastFludEventAt = 0L
            gestureFallbackInFlight.set(false)
            lastStatus = status''')

old_event = '''        val pkg = event?.packageName?.toString()
        val expected = pendingPackage
        if (!expected.isNullOrBlank() && !pkg.isNullOrBlank() && pkg != expected) return
        scheduleAttempt(220L)'''
new_event = '''        val pkg = event?.packageName?.toString()
        val expected = pendingPackage
        if (!expected.isNullOrBlank() && !pkg.isNullOrBlank() && pkg != expected) return
        if (!pkg.isNullOrBlank() && (expected.isNullOrBlank() || pkg == expected)) {
            lastFludEventAt = System.currentTimeMillis()
        }
        scheduleAttempt(220L)'''
if old_event not in s:
    raise SystemExit('Accessibility event anchor not found')
s = s.replace(old_event, new_event, 1)

# File-picker recovery should only go Back once. Do not immediately re-handoff while Flud is still loading.
old_picker = '''                lastStatus = "Wrong Flud file picker detected - returning to magnet flow"
                lastDiagnostic = "Detected .torrent file picker before Add torrent; sent Back and scheduled one controlled magnet re-handoff"
                try { performGlobalAction(GLOBAL_ACTION_BACK) } catch (_: Exception) { }
                handler.postDelayed({
                    if (!hasPendingRequest() || confirmationSeenAt > 0L) return@postDelayed
                    val retry = FludLauncher.relaunchLastMagnet(this, REQUEST_WINDOW_MS)
                    if (retry?.success == true) {
                        retarget(retry.packageName)
                        lastStatus = "Recovered from file picker - magnet handed to Flud again"
                        lastDiagnostic = "Early file-picker recovery re-handoff completed before confirmation lock"
                    } else {
                        lastStatus = "File picker closed - waiting for Flud magnet screen"
                    }
                    scheduleAttempt(900L)
                }, 2_500L)'''
new_picker = '''                lastStatus = "Wrong Flud file picker detected - returning to Flud and waiting for it to settle"
                lastDiagnostic = "Detected .torrent file picker before Add torrent; sent Back once. Re-handoff is deferred until Flud is quiet."
                try { performGlobalAction(GLOBAL_ACTION_BACK) } catch (_: Exception) { }
                lastFludEventAt = System.currentTimeMillis()
                scheduleAttempt(900L)'''
if old_picker not in s:
    raise SystemExit('File picker recovery anchor not found')
s = s.replace(old_picker, new_picker, 1)

# Never run generic semantic clicking outside the real Add torrent screen.
old_semantic = '''        val candidates = semanticCandidates(root)
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
'''
new_semantic = '''        if (confirmationScreen) {
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
'''
if old_semantic not in s:
    raise SystemExit('Semantic block anchor not found')
s = s.replace(old_semantic, new_semantic, 1)

# Replace staged fixed-timer retries with one quiet-gated retry and a late safety fallback.
old_retry = '''            val elapsed = now - pendingSince
            val nextRehandoffAt = if (rehandoffAttempts == 0) REHANDOFF_GRACE_MS else SECOND_REHANDOFF_GRACE_MS
            if (elapsed >= nextRehandoffAt && rehandoffAttempts < MAX_REHANDOFF_ATTEMPTS) {
                rehandoffAttempts += 1
                val retry = FludLauncher.relaunchLastMagnet(this, REQUEST_WINDOW_MS)
                if (retry?.success == true) {
                    retarget(retry.packageName)
                    lastStatus = "Slow Flud start detected - staged magnet re-handoff ${rehandoffAttempts}/${MAX_REHANDOFF_ATTEMPTS}"
                    lastDiagnostic = "No Add torrent screen after ${elapsed}ms; staged re-handoff ${rehandoffAttempts}/${MAX_REHANDOFF_ATTEMPTS}"
                    scheduleAttempt(1_200L)
                    return
                }
            }
            lastStatus = when {
                elapsed < REHANDOFF_GRACE_MS -> "Flud is still loading - waiting before cold-start recovery"
                rehandoffAttempts < MAX_REHANDOFF_ATTEMPTS -> "Flud is still settling - waiting for the next guarded re-handoff"
                else -> "Waiting for the real Flud Add torrent screen"
            }'''
new_retry = '''            val elapsed = now - pendingSince
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
            }'''
if old_retry not in s:
    raise SystemExit('Retry block anchor not found')
s = s.replace(old_retry, new_retry, 1)

# Wait for the actual confirmation screen to become quiet before using D-pad/gesture fallbacks.
old_fallback_gate = '''        val confirmationElapsed = now - confirmationSeenAt
        if (confirmationElapsed < CONFIRMATION_FALLBACK_GRACE_MS) {
            lastStatus = "Add torrent screen detected - waiting for confirmation control"
            scheduleAttempt(RETRY_DELAY_MS)
            return
        }

        // D-pad fallback is now allowed ONLY after the real Add torrent screen is detected.
        attemptFocusFallback(root)'''
new_fallback_gate = '''        val confirmationElapsed = now - confirmationSeenAt
        val confirmationQuietFor = (now - lastFludEventAt).coerceAtLeast(0L)
        if (confirmationElapsed < CONFIRMATION_FALLBACK_GRACE_MS || confirmationQuietFor < CONFIRMATION_QUIET_MS) {
            lastStatus = "Add torrent detected - waiting for metadata and controls to settle"
            scheduleAttempt(RETRY_DELAY_MS)
            return
        }

        // D-pad/gesture fallback is allowed only after the real Add torrent screen is stable.
        attemptFocusFallback(root)'''
if old_fallback_gate not in s:
    raise SystemExit('Fallback gate anchor not found')
s = s.replace(old_fallback_gate, new_fallback_gate, 1)

# Strict confirmation signature: INFORMATION/FILES plus a strong torrent-detail marker.
old_detail = '''        val detailMarkers = listOf(
            "storage path", "torrent settings", "hash", "name", "download path",
            "percorso", "impostazioni torrent", "nome",
            "cale stocare", "setari torrent", "nume",
            "emplacement", "parametres torrent", "nom",
            "speicher", "torrent einstellungen", "name"
        )
        val detailCount = detailMarkers.count { summary.contains(it) }

        return (titleSeen && (tabPairSeen || detailCount >= 1)) ||
            (tabPairSeen && detailCount >= 1)'''
new_detail = '''        val detailMarkers = listOf(
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
        return tabPairSeen && detailCount >= 1'''
if old_detail not in s:
    raise SystemExit('Confirmation signature anchor not found')
s = s.replace(old_detail, new_detail, 1)

# Gesture fallback also requires a quiet confirmation screen.
old_gesture_gate = '''        val seenAt = confirmationSeenAt
        if (seenAt <= 0L || System.currentTimeMillis() - seenAt < GESTURE_FALLBACK_GRACE_MS) return false
        if (!gestureFallbackInFlight.compareAndSet(false, true)) return true'''
new_gesture_gate = '''        val seenAt = confirmationSeenAt
        val now = System.currentTimeMillis()
        if (seenAt <= 0L || now - seenAt < GESTURE_FALLBACK_GRACE_MS) return false
        if (now - lastFludEventAt < CONFIRMATION_QUIET_MS) return false
        if (!gestureFallbackInFlight.compareAndSet(false, true)) return true'''
if old_gesture_gate not in s:
    raise SystemExit('Gesture gate anchor not found')
s = s.replace(old_gesture_gate, new_gesture_gate, 1)

if s == orig:
    raise SystemExit('No v7 service changes applied')
for needle in [
    'semantic-v7+quiet-gated-cold-start+strict-confirmation+navigation-lock',
    'FLUD_QUIET_BEFORE_REHANDOFF_MS',
    'lastFludEventAt',
    'return tabPairSeen && detailCount >= 1',
    'if (confirmationScreen) {\n            val candidates = semanticCandidates(root)'
]:
    if needle not in s:
        raise SystemExit('Missing v7 marker: ' + needle)
service.write_text(s)

g = gradle.read_text()
if 'versionCode = 34' not in g or 'versionName = "0.24.3"' not in g:
    raise SystemExit('Unexpected current Android version')
g = g.replace('versionCode = 34', 'versionCode = 35')
g = g.replace('versionName = "0.24.3"', 'versionName = "0.24.4"')
gradle.write_text(g)

print('Applied auto-start v7 quiet-gated cold-start patch.')
