package media.alexlab.fludremote

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri

object FludLauncher {
    const val FREE_PACKAGE = "com.delphicoder.flud"
    const val PAID_PACKAGE = "com.delphicoder.flud.paid"
    private val packages = listOf(FREE_PACKAGE, PAID_PACKAGE)
    @Volatile private var lastMagnet: String? = null
    @Volatile private var lastMagnetAt = 0L

    data class Result(
        val success: Boolean,
        val packageName: String? = null,
        val message: String
    )

    fun installedPackage(context: Context): String? {
        for (pkg in packages) {
            try {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(pkg, 0)
                return pkg
            } catch (_: Exception) {
                // Try the next package.
            }
        }
        return null
    }

    fun launchMagnet(context: Context, magnet: String): Result {
        if (!magnet.startsWith("magnet:?", ignoreCase = true)) {
            return Result(false, message = "Invalid magnet URI")
        }

        for (pkg in packages) {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(magnet)).apply {
                setPackage(pkg)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            try {
                val resolved = intent.resolveActivity(context.packageManager)
                if (resolved != null) {
                    context.startActivity(intent)
                    lastMagnet = magnet
                    lastMagnetAt = System.currentTimeMillis()
                    return Result(true, pkg, "Magnet handed to Flud")
                }
            } catch (_: ActivityNotFoundException) {
                // Try the next package.
            } catch (e: SecurityException) {
                return Result(false, pkg, "Android blocked the Flud launch: ${e.message ?: "security restriction"}")
            } catch (e: Exception) {
                return Result(false, pkg, "Could not launch Flud: ${e.message ?: e.javaClass.simpleName}")
            }
        }

        return Result(false, message = "Flud or Flud+ was not found, or neither app accepts magnet URIs")
    }

    fun relaunchLastMagnet(context: Context, maxAgeMs: Long = 120_000L): Result? {
        val magnet = lastMagnet ?: return null
        val age = System.currentTimeMillis() - lastMagnetAt
        if (age < 0L || age > maxAgeMs) return null
        return launchMagnet(context, magnet)
    }

    fun openApp(context: Context): Result {
        for (pkg in packages) {
            val launchIntent = context.packageManager.getLaunchIntentForPackage(pkg) ?: continue
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            return try {
                context.startActivity(launchIntent)
                Result(true, pkg, "Opened Flud")
            } catch (e: Exception) {
                Result(false, pkg, "Could not open Flud: ${e.message ?: e.javaClass.simpleName}")
            }
        }
        return Result(false, message = "Flud or Flud+ is not installed")
    }
}
