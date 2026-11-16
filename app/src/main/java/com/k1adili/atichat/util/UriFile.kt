package com.k1adili.atichat.util

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import java.io.IOException

/** Name and size of a document picked with the system file picker. */
data class PickedFile(val uri: Uri, val name: String, val size: Long)

object UriFile {
    @Throws(IOException::class)
    fun resolve(context: Context, uri: Uri): PickedFile {
        var name: String? = null
        var size: Long = -1
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)
            ?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (nameIndex >= 0 && !cursor.isNull(nameIndex)) name = cursor.getString(nameIndex)
                    if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) size = cursor.getLong(sizeIndex)
                }
            }
        if (size < 0) {
            // Some providers do not report a size: count the bytes once.
            size = context.contentResolver.openInputStream(uri)?.use { input ->
                val buffer = ByteArray(64 * 1024)
                var total = 0L
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    total += read
                }
                total
            } ?: throw IOException("unreadable file")
        }
        val fallback = uri.lastPathSegment?.substringAfterLast('/') ?: "file"
        return PickedFile(uri, name?.takeIf { it.isNotBlank() } ?: fallback, size)
    }

    fun humanSize(bytes: Long): String {
        val mb = bytes / (1024.0 * 1024.0)
        return when {
            mb >= 1 -> String.format(java.util.Locale.ROOT, "%.1f MB", mb)
            bytes >= 1024 -> String.format(java.util.Locale.ROOT, "%d KB", bytes / 1024)
            else -> "$bytes B"
        }
    }
}
