package media.alexlab.fludremote

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.WindowManager

/**
 * Optional tiny overlay used only after the user explicitly grants
 * "Display over other apps". It makes background activity launching more
 * reliable on modern Android. The first LAN test can be done
 * without this permission while MainActivity remains visible.
 */
class OverlayController(private val context: Context) {
    private var view: View? = null
    private var windowManager: WindowManager? = null

    fun startIfAllowed(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(context)) {
            return false
        }
        if (view != null) return true

        return try {
            val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            }

            val params = WindowManager.LayoutParams(
                2,
                2,
                type,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.START
                x = 0
                y = 0
            }

            val overlay = View(context).apply {
                // Effectively invisible, but still a real overlay window.
                setBackgroundColor(Color.argb(3, 0, 0, 0))
            }
            wm.addView(overlay, params)
            windowManager = wm
            view = overlay
            true
        } catch (_: Exception) {
            false
        }
    }

    fun stop() {
        val overlay = view ?: return
        try {
            windowManager?.removeView(overlay)
        } catch (_: Exception) {
            // Already removed.
        }
        view = null
        windowManager = null
    }
}
