from pathlib import Path

p = Path('app/src/main/java/media/alexlab/fludremote/FludAutoStartService.kt')
s = p.read_text()
orig = s

# Strategy label.
s = s.replace(
    'private const val STRATEGY = "semantic-v5+120s-cold-start+screen-gated-gesture+staged-rehandoff"',
    'private const val STRATEGY = "semantic-v6+120s-cold-start+confirmation-latch+screen-gated-gesture"'
)

# File picker recovery must never navigate backwards after Add torrent was seen once.
old_picker = '''        if (looksLikeTorrentFilePicker(screenText)) {
            if (filePickerRecoveries < 1) {
                filePickerRecoveries += 1
                lastStatus = "Wrong Flud file picker detected - returning to magnet flow"
                lastDiagnostic = "Detected .torrent file picker during auto-start; sent Back and scheduled one controlled magnet re-handoff"
                try { performGlobalAction(GLOBAL_ACTION_BACK) } catch (_: Exception) { }
                handler.postDelayed({
                    if (!hasPendingRequest()) return@postDelayed
                    // This early recovery retry does not consume the later staged cold-start
                    // retries. On Shield a file picker may appear long before Flud has finished
                    // loading its torrent list, so we still keep retries available at 36s/58s.
                    val retry = FludLauncher.relaunchLastMagnet(this, REQUEST_WINDOW_MS)
                    if (retry?.success == true) {
                        retarget(retry.packageName)
                        lastStatus = "Recovered from file picker - magnet handed to Flud again"
                        lastDiagnostic = "File-picker recovery sent one early re-handoff; staged cold-start retries remain available"
                    } else {
                        lastStatus = "File picker closed - waiting for Flud magnet screen"
                    }
                    scheduleAttempt(900L)
                }, 2_500L)
            } else {
                lastStatus = "Waiting for Flud magnet screen after file-picker recovery"
                scheduleAttempt(RETRY_DELAY_MS)
            }
            return
        }
'''
new_picker = '''        if (looksLikeTorrentFilePicker(screenText)) {
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
                lastStatus = "Wrong Flud file picker detected - returning to magnet flow"
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
                }, 2_500L)
            } else {
                lastStatus = "Waiting for Flud magnet screen after file-picker recovery"
                scheduleAttempt(RETRY_DELAY_MS)
            }
            return
        }
'''
if old_picker not in s:
    raise SystemExit('file picker block anchor not found')
s = s.replace(old_picker, new_picker, 1)

# Lock navigation immediately on the first real confirmation sighting.
old_seen = '''        val confirmationScreen = looksLikeMagnetConfirmation(screenText)
        if (confirmationScreen && confirmationSeenAt <= 0L) confirmationSeenAt = now
'''
new_seen = '''        val confirmationScreen = looksLikeMagnetConfirmation(screenText)
        if (confirmationScreen && confirmationSeenAt <= 0L) {
            confirmationSeenAt = now
            // Navigation lock: from here on this request may only confirm the existing Add torrent
            // screen. No more re-handoff and no Back recovery are allowed.
            rehandoffAttempts = MAX_REHANDOFF_ATTEMPTS
            lastDiagnostic = "Add torrent detected - navigation locked; only final confirmation is allowed"
        }
'''
if old_seen not in s:
    raise SystemExit('confirmation seen anchor not found')
s = s.replace(old_seen, new_seen, 1)

# Never staged-rehandoff after the confirmation latch has ever fired, even if a later frame
# temporarily fails screen recognition while Flud is still rendering.
old_branch = '''        if (!confirmationScreen) {
            val elapsed = now - pendingSince
            val nextRehandoffAt = if (rehandoffAttempts == 0) REHANDOFF_GRACE_MS else SECOND_REHANDOFF_GRACE_MS
'''
new_branch = '''        if (!confirmationScreen) {
            if (confirmationSeenAt > 0L) {
                lastStatus = "Add torrent was already detected - waiting without navigation retries"
                scheduleAttempt(RETRY_DELAY_MS)
                return
            }
            val elapsed = now - pendingSince
            val nextRehandoffAt = if (rehandoffAttempts == 0) REHANDOFF_GRACE_MS else SECOND_REHANDOFF_GRACE_MS
'''
if old_branch not in s:
    raise SystemExit('rehandoff branch anchor not found')
s = s.replace(old_branch, new_branch, 1)

# Guard D-pad fallback's emergency Back as well.
old_fallback_picker = '''            if (looksLikeTorrentFilePicker(screenSummary(freshRoot))) {
                lastStatus = "File picker appeared before OK — recovering"
                if (filePickerRecoveries < 1) {
                    filePickerRecoveries += 1
                    try { performGlobalAction(GLOBAL_ACTION_BACK) } catch (_: Exception) { }
                }
                scheduleAttempt(750L)
                return@postDelayed
            }
'''
new_fallback_picker = '''            if (looksLikeTorrentFilePicker(screenSummary(freshRoot))) {
                lastStatus = "File picker appeared after Add torrent - navigation lock kept it untouched"
                lastDiagnostic = "$lastDiagnostic | no Back after confirmation lock"
                scheduleAttempt(RETRY_DELAY_MS)
                return@postDelayed
            }
'''
if old_fallback_picker not in s:
    raise SystemExit('fallback picker anchor not found')
s = s.replace(old_fallback_picker, new_fallback_picker, 1)

if s == orig:
    raise SystemExit('no changes applied')
for needle in (
    'semantic-v6+120s-cold-start+confirmation-latch+screen-gated-gesture',
    'confirmationSeenAt > 0L',
    'Navigation lock',
    'rehandoffAttempts = MAX_REHANDOFF_ATTEMPTS',
):
    if needle not in s:
        raise SystemExit('missing v6 marker: ' + needle)

p.write_text(s)
print('Applied auto-start v6 confirmation navigation lock.')
