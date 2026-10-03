package media.alexlab.fludremote

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper

object TorrentFileLauncher {
    private const val CLEANUP_AFTER_MS = 30 * 60 * 1000L

    fun launch(
        context: Context,
        torrent: TorrentFileSupport.StoredTorrent,
        preferredPackage: String? = null
    ): FludLauncher.Result {
        val pkg = preferredPackage ?: FludLauncher.installedPackage(context)
            ?: return FludLauncher.Result(false, message = "Flud or Flud+ is not installed")
        val uri = TorrentFileSupport.contentUri(context, torrent)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/x-bittorrent")
            setPackage(pkg)
            clipData = ClipData.newUri(context.contentResolver, torrent.displayName, uri)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        return try {
            if (intent.resolveActivity(context.packageManager) == null) {
                return FludLauncher.Result(false, pkg, "Flud does not expose a .torrent file handler")
            }
            context.grantUriPermission(pkg, uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            context.startActivity(intent)
            Handler(Looper.getMainLooper()).postDelayed({
                try {
                    context.revokeUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                } catch (_: Exception) {
                }
                TorrentFileSupport.delete(torrent)
            }, CLEANUP_AFTER_MS)
            FludLauncher.Result(true, pkg, "Torrent file handed to Flud")
        } catch (_: ActivityNotFoundException) {
            FludLauncher.Result(false, pkg, "Flud could not open the .torrent file")
        } catch (e: SecurityException) {
            FludLauncher.Result(false, pkg, "Android blocked the .torrent handoff: ${e.message ?: "security restriction"}")
        } catch (e: Exception) {
            FludLauncher.Result(false, pkg, "Could not hand .torrent file to Flud: ${e.message ?: e.javaClass.simpleName}")
        }
    }
}
