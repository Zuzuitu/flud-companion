package media.alexlab.fludremote

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import java.io.File
import java.io.FileNotFoundException

class TorrentContentProvider : ContentProvider() {
    override fun onCreate(): Boolean = true

    override fun getType(uri: Uri): String = "application/x-bittorrent"

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        if (mode != "r") throw FileNotFoundException("Read-only provider")
        return ParcelFileDescriptor.open(resolve(uri), ParcelFileDescriptor.MODE_READ_ONLY)
    }

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?
    ): Cursor {
        val file = resolve(uri)
        val columns = projection?.filter {
            it == OpenableColumns.DISPLAY_NAME || it == OpenableColumns.SIZE
        }?.toTypedArray() ?: arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE)
        return MatrixCursor(columns, 1).apply {
            addRow(columns.map {
                when (it) {
                    OpenableColumns.DISPLAY_NAME -> file.name
                    OpenableColumns.SIZE -> file.length()
                    else -> null
                }
            })
        }
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri? =
        throw UnsupportedOperationException("Read-only provider")

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?
    ): Int = throw UnsupportedOperationException("Read-only provider")

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int =
        throw UnsupportedOperationException("Read-only provider")

    private fun resolve(uri: Uri): File {
        val ctx = context ?: throw FileNotFoundException("Provider unavailable")
        if (uri.authority != "${ctx.packageName}.torrents") throw FileNotFoundException("Invalid authority")
        val parts = uri.pathSegments
        if (parts.size != 2) throw FileNotFoundException("Invalid torrent URI")
        val token = parts[0]
        val name = parts[1]
        if (!token.matches(Regex("^[A-Za-z0-9-]{8,80}$"))) throw FileNotFoundException("Invalid token")
        if (name.isBlank() || name.contains('/') || name.contains('\\')) throw FileNotFoundException("Invalid filename")

        val root = File(ctx.cacheDir, "flud-torrents").canonicalFile
        val file = File(File(root, token), name).canonicalFile
        if (!file.path.startsWith(root.path + File.separator) || !file.isFile) {
            throw FileNotFoundException("Torrent cache entry not found")
        }
        return file
    }
}
