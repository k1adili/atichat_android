package com.k1adili.atichat.util

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.webkit.MimeTypeMap
import androidx.core.content.FileProvider
import java.io.File

object FileOpener {
    /** Opens a received file with another app. [storedName] is reduced to a bare name (a backup could contain anything). */
    fun open(context: Context, downloadsDir: File, storedName: String): Boolean {
        val file = File(downloadsDir, File(storedName).name)
        if (!file.isFile) return false
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        val mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(file.extension.lowercase()) ?: "*/*"
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, mime)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        return try {
            context.startActivity(intent)
            true
        } catch (_: ActivityNotFoundException) {
            false
        }
    }
}
