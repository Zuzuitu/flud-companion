from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def read(path: str) -> str:
    return (ROOT / path).read_text(encoding="utf-8")


def write(path: str, content: str) -> None:
    (ROOT / path).write_text(content, encoding="utf-8")


def replace_once(path: str, old: str, new: str) -> None:
    data = read(path)
    if old not in data:
        raise SystemExit(f"Expected source block not found in {path}: {old[:120]!r}")
    if data.count(old) != 1:
        raise SystemExit(f"Expected exactly one source block in {path}, found {data.count(old)}")
    write(path, data.replace(old, new, 1))


# Android package/version bump.
replace_once(
    "app/build.gradle.kts",
    '        versionCode = 30\n        versionName = "0.24.0"',
    '        versionCode = 31\n        versionName = "0.24.1"',
)

# Keep Bridge/relay-reported Android version aligned with the APK version.
for source in [
    "app/src/main/java/media/alexlab/fludremote/BridgeHttpServer.kt",
    "app/src/main/java/media/alexlab/fludremote/CloudRelayClient.kt",
]:
    data = read(source)
    if '0.24.0' not in data:
        raise SystemExit(f"Expected 0.24.0 version string in {source}")
    write(source, data.replace('0.24.0', '0.24.1'))

# Remember the most recently handed-off magnet so the Accessibility helper can make
# exactly one controlled re-handoff after a very slow/frozen Flud cold start.
launcher = "app/src/main/java/media/alexlab/fludremote/FludLauncher.kt"
replace_once(
    launcher,
    '    private val packages = listOf(FREE_PACKAGE, PAID_PACKAGE)\n',
    '    private val packages = listOf(FREE_PACKAGE, PAID_PACKAGE)\n    @Volatile private var lastMagnet: String? = null\n    @Volatile private var lastMagnetAt = 0L\n',
)
replace_once(
    launcher,
    '                    context.startActivity(intent)\n                    return Result(true, pkg, "Magnet handed to Flud")',
    '                    context.startActivity(intent)\n                    lastMagnet = magnet\n                    lastMagnetAt = System.currentTimeMillis()\n                    return Result(true, pkg, "Magnet handed to Flud")',
)
replace_once(
    launcher,
    '    fun openApp(context: Context): Result {',
    '''    fun relaunchLastMagnet(context: Context, maxAgeMs: Long = 30_000L): Result? {
        val magnet = lastMagnet ?: return null
        val age = System.currentTimeMillis() - lastMagnetAt
        if (age < 0L || age > maxAgeMs) return null
        return launchMagnet(context, magnet)
    }

    fun openApp(context: Context): Result {''',
)

# Auto-start v3: never use D-pad fallback on Flud's main screen, identify the real
# Add torrent screen, click its top-right confirmation action, and make one controlled
# re-handoff if a cold/frozen Flud start swallowed the original magnet intent.
auto = "app/src/main/java/media/alexlab/fludremote/FludAutoStartService.kt"
replace_once(
    auto,
    '''        private const val REQUEST_WINDOW_MS = 20_000L
        private const val SEMANTIC_GRACE_MS = 3_200L
        private const val FALLBACK_GRACE_MS = 7_000L
        private const val RETRY_DELAY_MS = 360L
        private const val CLICK_DELAY_MS = 190L
        private const val MAX_SCAN_NODES = 220
        private const val SEMANTIC_SCORE_THRESHOLD = 8
        private const val STRATEGY = "semantic-v2+guarded-fallback"''',
    '''        private const val REQUEST_WINDOW_MS = 30_000L
        private const val REHANDOFF_GRACE_MS = 10_500L
        private const val CONFIRMATION_FALLBACK_GRACE_MS = 900L
        private const val RETRY_DELAY_MS = 360L
        private const val CLICK_DELAY_MS = 190L
        private const val MAX_SCAN_NODES = 220
        private const val SEMANTIC_SCORE_THRESHOLD = 8
        private const val STRATEGY = "semantic-v3+screen-gated-fallback+single-rehandoff"''',
)
replace_once(
    auto,
    '''        @Volatile private var filePickerRecoveries = 0
        private val attemptScheduled = AtomicBoolean(false)''',
    '''        @Volatile private var filePickerRecoveries = 0
        @Volatile private var confirmationSeenAt = 0L
        @Volatile private var rehandoffAttempts = 0
        private val attemptScheduled = AtomicBoolean(false)''',
)
replace_once(
    auto,
    '''            filePickerRecoveries = 0
            lastStatus = "Armed — waiting for Flud magnet confirmation"''',
    '''            filePickerRecoveries = 0
            confirmationSeenAt = 0L
            rehandoffAttempts = 0
            lastStatus = "Armed - waiting for Flud magnet confirmation"''',
)
replace_once(
    auto,
    '''            filePickerRecoveries = 0
            lastStatus = status''',
    '''            filePickerRecoveries = 0
            confirmationSeenAt = 0L
            rehandoffAttempts = 0
            lastStatus = status''',
)

old_attempt = '''        val screenText = screenSummary(root)
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

        attemptFocusFallback(root)'''

new_attempt = '''        val screenText = screenSummary(root)
        if (looksLikeTorrentFilePicker(screenText)) {
            if (filePickerRecoveries < 1) {
                filePickerRecoveries += 1
                lastStatus = "Wrong Flud file picker detected - returning to magnet flow"
                lastDiagnostic = "Detected .torrent file picker during auto-start; sent Back and scheduled one controlled magnet re-handoff"
                try { performGlobalAction(GLOBAL_ACTION_BACK) } catch (_: Exception) { }
                handler.postDelayed({
                    if (!hasPendingRequest() || rehandoffAttempts >= 1) return@postDelayed
                    rehandoffAttempts += 1
                    val retry = FludLauncher.relaunchLastMagnet(this)
                    if (retry?.success == true) {
                        retarget(retry.packageName)
                        lastStatus = "Recovered from file picker - magnet handed to Flud again once"
                        lastDiagnostic = "File-picker recovery used one controlled magnet re-handoff"
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

        val now = System.currentTimeMillis()
        val confirmationScreen = looksLikeMagnetConfirmation(screenText)
        if (confirmationScreen && confirmationSeenAt <= 0L) confirmationSeenAt = now

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

        if (!confirmationScreen) {
            val elapsed = now - pendingSince
            if (elapsed >= REHANDOFF_GRACE_MS && rehandoffAttempts < 1) {
                rehandoffAttempts += 1
                val retry = FludLauncher.relaunchLastMagnet(this)
                if (retry?.success == true) {
                    retarget(retry.packageName)
                    lastStatus = "Slow Flud start detected - magnet handed to Flud again once"
                    lastDiagnostic = "No Add torrent screen after ${elapsed}ms; used one controlled magnet re-handoff"
                    scheduleAttempt(900L)
                    return
                }
            }
            lastStatus = if (elapsed < REHANDOFF_GRACE_MS) {
                "Flud is still settling - waiting for the real Add torrent screen"
            } else {
                "Waiting for the real Flud Add torrent screen"
            }
            scheduleAttempt(RETRY_DELAY_MS)
            return
        }

        val confirmationElapsed = now - confirmationSeenAt
        if (confirmationElapsed < CONFIRMATION_FALLBACK_GRACE_MS) {
            lastStatus = "Add torrent screen detected - waiting for confirmation control"
            scheduleAttempt(RETRY_DELAY_MS)
            return
        }

        // D-pad fallback is now allowed ONLY after the real Add torrent screen is detected.
        attemptFocusFallback(root)'''

replace_once(auto, old_attempt, new_attempt)

picker_func = '''    private fun looksLikeTorrentFilePicker(summary: String): Boolean {
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
'''

confirmation_funcs = picker_func + '''
    private fun looksLikeMagnetConfirmation(summary: String): Boolean {
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
'''
replace_once(auto, picker_func, confirmation_funcs)

replace_once(
    auto,
    '''    private fun attemptFocusFallback(root: AccessibilityNodeInfo) {
        val current = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)''',
    '''    private fun attemptFocusFallback(root: AccessibilityNodeInfo) {
        if (!looksLikeMagnetConfirmation(screenSummary(root))) {
            lastStatus = "D-pad fallback blocked outside Add torrent screen"
            scheduleAttempt(RETRY_DELAY_MS)
            return
        }

        val current = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)''',
)
replace_once(
    auto,
    '''            val freshRoot = rootInActiveWindow
            if (freshRoot != null && looksLikeTorrentFilePicker(screenSummary(freshRoot))) {''',
    '''            val freshRoot = rootInActiveWindow
            if (freshRoot == null || !looksLikeMagnetConfirmation(screenSummary(freshRoot))) {
                lastStatus = "Add torrent screen changed before fallback click - waiting"
                scheduleAttempt(RETRY_DELAY_MS)
                return@postDelayed
            }
            if (looksLikeTorrentFilePicker(screenSummary(freshRoot))) {''',
)

# The previous insertion makes the file-picker branch unreachable after the confirmation guard;
# keep the explicit recovery code for diagnostics but avoid a duplicate summary call later.
# This is intentionally harmless and preserves the guarded safety net.

# Remote PWA v0.24.1: iOS/iPadOS Home Screen web apps receive cookies at install time,
# but not Safari/Chrome localStorage. Use a short-lived, path-scoped Secure bootstrap cookie
# to transfer pairing into the installed PWA, then delete it after the first standalone load.
relay = "selfhost/relay/src/index.js"
replace_once(relay, 'const VERSION = "0.24.0-selfhost";', 'const VERSION = "0.24.1-selfhost";')
bootstrap = r'''const PAIRING_BOOTSTRAP_SCRIPT = `<script>(()=>{const D='fludRemoteDeviceId',T='fludRemoteCloudToken',C='fludPwaPairBootstrapV1';function valid(d,t){return d.length>=16&&t.length>=20}function setPair(d,t){if(!valid(d,t))return;const v=encodeURIComponent(JSON.stringify({d:d,t:t}));document.cookie=C+'='+v+'; Max-Age=86400; Path=/app; Secure; SameSite=Strict'}function getPair(){const p=document.cookie.split('; ').find(x=>x.startsWith(C+'='));if(!p)return null;try{return JSON.parse(decodeURIComponent(p.slice(C.length+1)))}catch(e){return null}}function clearPair(){document.cookie=C+'=; Max-Age=0; Path=/app; Secure; SameSite=Strict'}const q=new URLSearchParams(location.hash.replace(/^#/,'')),hd=(q.get('device')||'').trim(),ht=(q.get('token')||'').trim();if(valid(hd,ht)){localStorage.setItem(D,hd);localStorage.setItem(T,ht);setPair(hd,ht)}else if(!localStorage.getItem(D)||!localStorage.getItem(T)){const p=getPair();if(p&&valid(String(p.d||''),String(p.t||''))){localStorage.setItem(D,String(p.d));localStorage.setItem(T,String(p.t));clearPair()}}const s=document.getElementById('save'),f=document.getElementById('forget');if(s)s.addEventListener('click',()=>setTimeout(()=>setPair(document.getElementById('device').value.trim(),document.getElementById('token').value.trim()),0));if(f)f.addEventListener('click',clearPair)})();</script>`;

'''
replace_once(relay, 'const APP_HTML = `<!doctype html>', bootstrap + 'const APP_HTML = `<!doctype html>')
replace_once(relay, '<script>(()=>{const T=', '${PAIRING_BOOTSTRAP_SCRIPT}<script>(()=>{const T=')
replace_once(relay, "const C='flud-companion-v0240';", "const C='flud-companion-v0241';")

replace_once(
    "selfhost/relay/package.json",
    '  "version": "0.24.0",',
    '  "version": "0.24.1",',
)

# Prepare the signed Beta 2 release workflow, but do NOT create its READY marker here.
release_workflow = ".github/workflows/release-beta.yml"
data = read(release_workflow)
if data.count("0.24.0-beta.1") < 6:
    raise SystemExit("release-beta.yml no longer matches the Beta 1 template")
data = data.replace("0.24.0-beta.1", "0.24.1-beta.2")
data = data.replace("Flud Companion 0.24.0 Beta 1", "Flud Companion 0.24.1 Beta 2")
write(release_workflow, data)

# Changelog and release notes.
changelog = read("CHANGELOG.md")
entry = '''## 0.24.1\n\n- Fix iPhone/iPad Home Screen PWA pairing transfer by using a short-lived Secure bootstrap cookie, then moving credentials into the installed PWA's own local storage.\n- Harden Android TV cold/frozen-start magnet handling for Flud launches that need roughly 7-8 seconds to become fully interactive.\n- Never run the D-pad confirmation fallback on Flud's main screen.\n- Detect the real Add torrent screen before confirmation and target the top-right add action, including an unlabeled image button.\n- Recover once from an accidental .torrent file picker and allow one controlled magnet re-handoff when a slow start swallows the first intent.\n- Extend the guarded Accessibility request window from 20 to 30 seconds.\n\n'''
if "## 0.24.1" in changelog:
    raise SystemExit("CHANGELOG already contains 0.24.1")
write("CHANGELOG.md", changelog.replace("# Changelog\n\n", "# Changelog\n\n" + entry, 1))

notes_path = ROOT / "docs/releases/v0.24.1-beta.2.md"
if notes_path.exists():
    raise SystemExit("Beta 2 release notes already exist")
notes_path.write_text('''# Flud Companion 0.24.1 Beta 2\n\nBug-fix beta focused on iPhone PWA pairing persistence and slow Android TV / NVIDIA Shield Flud starts.\n\n## Fixed\n\n- Remote pairing now survives Add to Home Screen on iPhone/iPad by transferring the QR pairing through a short-lived Secure cookie and then storing it inside the installed PWA.\n- Auto-start no longer uses its D-pad fallback while Flud is still on the main screen, preventing accidental entry into Select a .torrent file to add.\n- The Accessibility helper now recognizes the real Add torrent screen and can confirm the top-right add action even when Flud exposes it as an unlabeled image button.\n- A slow/frozen Flud cold start gets a longer guarded window and, if necessary, one controlled magnet re-handoff after Flud has had time to settle.\n- If the .torrent file picker is detected, Companion backs out and performs at most one controlled recovery re-handoff.\n\n## Validation target\n\nPlease test two specific cases before this prerelease is promoted further:\n\n1. Scan a fresh Remote QR in an iPhone browser, add the Remote PWA to the Home Screen, launch it from the icon, and confirm Device ID + token are already present.\n2. Leave Flud unused long enough to require a slow 7-8 second cold/frozen start, then send a magnet with Auto-start enabled and confirm it reaches Add torrent and is finally added without opening the .torrent file picker.\n\n## Security and privacy\n\n- The PWA bootstrap cookie is Secure, SameSite=Strict, limited to `/app`, expires after 24 hours, and is deleted after it restores pairing in a fresh installed PWA.\n- No project-owned account or shared relay is introduced.\n- Pairing tokens, QR codes and signing material must remain private.\n\nFlud Companion remains an independent, unofficial alexlab.media project and is not affiliated with Delphi Softwares.\n''', encoding="utf-8")

print("Beta 2 patch applied deterministically.")
