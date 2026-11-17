package com.k1adili.atichat.util

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.webkit.MimeTypeMap
import androidx.annotation.RequiresApi
import androidx.core.content.FileProvider
import java.io.File
import java.io.IOException

/**
 * Received files end up in the public Downloads/AtiChat folder (Android 10+), so they show up in the
 * Files app and gallery. On older Android versions they stay in the app's own storage.
 */
object ReceivedFiles {
    private const val RELATIVE_PATH = "Download/AtiChat"
    private val IMAGE_EXTENSIONS = setOf("jpg", "jpeg", "png", "webp", "gif", "bmp", "heic", "heif")

    fun isImage(name: String): Boolean = name.substringAfterLast('.', "").lowercase() in IMAGE_EXTENSIONS

    fun mimeOf(name: String): String =
        MimeTypeMap.getSingleton().getMimeTypeFromExtension(name.substringAfterLast('.', "").lowercase())
            ?: "application/octet-stream"

    /**
     * Moves [source] into Downloads/AtiChat and returns the final file name there (the system adds
     * " (1)" when the name already exists), or null when the file has to stay in private storage.
     */
    fun moveToDownloads(context: Context, source: File, displayName: String): String? {
        if (Build.VERSION.SDK_INT < 29) return null
        return try {
            insertIntoDownloads(context, source, displayName)
        } catch (_: Exception) {
            null
        }
    }

    @RequiresApi(29)
    private fun insertIntoDownloads(context: Context, source: File, displayName: String): String? {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
            put(MediaStore.MediaColumns.MIME_TYPE, mimeOf(displayName))
            put(MediaStore.MediaColumns.RELATIVE_PATH, RELATIVE_PATH)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return null
        try {
            val output = resolver.openOutputStream(uri) ?: throw IOException("cannot open output")
            output.use { out -> source.inputStream().use { it.copyTo(out) } }
            resolver.update(uri, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
        } catch (error: Exception) {
            resolver.delete(uri, null, null)
            throw error
        }
        source.delete()
        resolver.query(uri, arrayOf(MediaStore.MediaColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) return cursor.getString(0)
        }
        return displayName
    }

    /** Resolves a stored file name to a readable Uri (private copy first, then Downloads/AtiChat). */
    fun uriFor(context: Context, privateDir: File, storedName: String): Uri? {
        if (storedName.isEmpty()) return null
        val name = File(storedName).name
        val local = File(privateDir, name)
        if (local.isFile) return FileProvider.getUriForFile(context, "${context.packageName}.files", local)
        if (Build.VERSION.SDK_INT >= 29) return findInDownloads(context, name)
        return null
    }

    @RequiresApi(29)
    private fun findInDownloads(context: Context, name: String): Uri? {
        val collection = MediaStore.Downloads.EXTERNAL_CONTENT_URI
        return context.contentResolver.query(
            collection,
            arrayOf(MediaStore.MediaColumns._ID),
            "${MediaStore.MediaColumns.DISPLAY_NAME} = ? AND ${MediaStore.MediaColumns.RELATIVE_PATH} LIKE ?",
            arrayOf(name, "$RELATIVE_PATH%"),
            null,
        )?.use { cursor ->
            if (cursor.moveToFirst()) ContentUris.withAppendedId(collection, cursor.getLong(0)) else null
        }
    }
}
