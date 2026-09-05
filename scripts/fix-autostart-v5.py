from pathlib import Path

service = Path('app/src/main/java/media/alexlab/fludremote/FludAutoStartService.kt')
launcher = Path('app/src/main/java/media/alexlab/fludremote/FludLauncher.kt')
cloud = Path('app/src/main/java/media/alexlab/fludremote/CloudRelayClient.kt')
http = Path('app/src/main/java/media/alexlab/fludremote/BridgeHttpServer.kt')
gradle = Path('app/build.gradle.kts')

s = service.read_text()
orig = s

s = s.replace('import android.content.Context\n', 'import android.content.ComponentName\nimport android.content.Context\n')
s = s.replace('import android.os.Looper\n', 'import android.os.Looper\nimport android.provider.Settings\n')

s = s.replace('private const val REQUEST_WINDOW_MS = 30_000L', 'private const val REQUEST_WINDOW_MS = 120_000L')
s = s.replace('private const val REHANDOFF_GRACE_MS = 10_500L', 'private const val REHANDOFF_GRACE_MS = 36_000L\n        private const val SECOND_REHANDOFF_GRACE_MS = 58_000L\n        private const val MAX_REHANDOFF_ATTEMPTS = 2')
s = s.replace('private const val STRATEGY = "semantic-v4+screen-gated-gesture+single-rehandoff"', 'private const val STRATEGY = "semantic-v5+120s-cold-start+screen-gated-gesture+staged-rehandoff"')

old_enabled = '''        fun isEnabled(context: Context): Boolean {
            val manager = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
            return manager.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
                .any {
                    val info = it.resolveInfo?.serviceInfo
                    info?.packageName == context.packageName && info.name == FludAutoStartService::class.java.name
                }
        }
'''
new_enabled = '''        fun isEnabled(context: Context): Boolean {
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
'''
if old_enabled not in s:
    raise SystemExit('isEnabled anchor not found')
s = s.replace(old_enabled, new_enabled, 1)

old_picker = '''                handler.postDelayed({
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
'''
new_picker = '''                handler.postDelayed({
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
'''
if old_picker not in s:
    raise SystemExit('file picker recovery anchor not found')
s = s.replace(old_picker, new_picker, 1)

old_rehandoff = '''            if (elapsed >= REHANDOFF_GRACE_MS && rehandoffAttempts < 1) {
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
'''
new_rehandoff = '''            val nextRehandoffAt = if (rehandoffAttempts == 0) REHANDOFF_GRACE_MS else SECOND_REHANDOFF_GRACE_MS
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
            }
'''
if old_rehandoff not in s:
    raise SystemExit('slow rehandoff anchor not found')
s = s.replace(old_rehandoff, new_rehandoff, 1)

if s == orig:
    raise SystemExit('No FludAutoStartService changes applied')
for needle in ('REQUEST_WINDOW_MS = 120_000L', 'SECOND_REHANDOFF_GRACE_MS = 58_000L', 'Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES', 'semantic-v5+120s-cold-start'):
    if needle not in s:
        raise SystemExit('Missing service marker: ' + needle)
service.write_text(s)

l = launcher.read_text()
l = l.replace('fun relaunchLastMagnet(context: Context, maxAgeMs: Long = 30_000L): Result?', 'fun relaunchLastMagnet(context: Context, maxAgeMs: Long = 120_000L): Result?')
if 'maxAgeMs: Long = 120_000L' not in l:
    raise SystemExit('Launcher max age patch failed')
launcher.write_text(l)

c = cloud.read_text().replace('private const val BRIDGE_VERSION = "0.24.1"', 'private const val BRIDGE_VERSION = "0.24.2"')
if 'BRIDGE_VERSION = "0.24.2"' not in c:
    raise SystemExit('Cloud version patch failed')
cloud.write_text(c)

h = http.read_text().replace('const val VERSION = "0.24.1"', 'const val VERSION = "0.24.2"')
if 'const val VERSION = "0.24.2"' not in h:
    raise SystemExit('HTTP version patch failed')
http.write_text(h)

g = gradle.read_text()
g = g.replace('versionCode = 32', 'versionCode = 33')
g = g.replace('versionName = "0.24.1"', 'versionName = "0.24.2"')
if 'versionCode = 33' not in g or 'versionName = "0.24.2"' not in g:
    raise SystemExit('Gradle version patch failed')
gradle.write_text(g)

print('Auto-start v5 patch prepared successfully.')
