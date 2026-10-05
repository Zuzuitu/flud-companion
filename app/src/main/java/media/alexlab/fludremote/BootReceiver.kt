package media.alexlab.fludremote

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.SystemClock
import android.util.Log

class BootReceiver : BroadcastReceiver() {
    companion object {
        private const val TAG = "FludRemote"
        private const val FIRST_START_WAIT_MS = 2_000L
        private const val RETRY_START_WAIT_MS = 2_500L
        private const val POLL_MS = 100L
    }

    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        val supported = action == Intent.ACTION_BOOT_COMPLETED ||
            action == Intent.ACTION_USER_UNLOCKED ||
            action == Intent.ACTION_MY_PACKAGE_REPLACED
        if (!supported || !BridgePreferences.autoStart(context)) return

        val appContext = context.applicationContext
        val pending = goAsync()
        Thread({
            try {
                val first = requestStart(appContext, action, "initial")
                var running = first && waitUntilRunning(FIRST_START_WAIT_MS)

                if (!running) {
                    val second = requestStart(appContext, action, "retry")
                    running = second && waitUntilRunning(RETRY_START_WAIT_MS)
                }

                BridgePreferences.recordBootStart(
                    appContext,
                    action = action,
                    success = running,
                    detail = if (running) {
                        "Bridge reached RUNNING after $action"
                    } else {
                        "Bridge did not reach RUNNING after bounded retry"
                    }
                )
            } catch (error: Throwable) {
                Log.e(TAG, "Unexpected Bridge auto-start failure after $action", error)
                BridgePreferences.recordBootStart(
                    appContext,
                    action = action,
                    success = false,
                    detail = "Unexpected ${error.javaClass.simpleName}"
                )
            } finally {
                pending.finish()
            }
        }, "flud-bridge-boot").start()
    }

    private fun requestStart(context: Context, action: String, attempt: String): Boolean {
        val serviceIntent = Intent(context, BridgeService::class.java)
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent)
            } else {
                context.startService(serviceIntent)
            }
            true
        } catch (error: Exception) {
            Log.e(TAG, "Could not auto-start Bridge after $action ($attempt)", error)
            BridgePreferences.recordBootStart(
                context,
                action = action,
                success = false,
                detail = "$attempt start threw ${error.javaClass.simpleName}"
            )
            false
        }
    }

    private fun waitUntilRunning(timeoutMs: Long): Boolean {
        val deadline = SystemClock.elapsedRealtime() + timeoutMs
        while (SystemClock.elapsedRealtime() < deadline) {
            if (BridgeService.isRunning) return true
            SystemClock.sleep(POLL_MS)
        }
        return BridgeService.isRunning
    }
}
