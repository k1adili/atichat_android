package com.k1adili.atichat.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.exifinterface.media.ExifInterface
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream

/** Preview pictures for image messages, kept in private storage (never backed up) and keyed by message id. */
object Thumbnails {
    private const val MAX_SIDE = 1280

    fun file(context: Context, messageId: String): File =
        File(File(context.filesDir, "thumbs").apply { mkdirs() }, "$messageId.jpg")

    fun delete(context: Context, messageId: String) {
        file(context, messageId).delete()
    }

    fun deleteAll(context: Context) {
        File(context.filesDir, "thumbs").listFiles()?.forEach { it.delete() }
    }

    /** Builds the preview from a freshly opened stream each time [open] is called. Returns false if it is not a readable image. */
    fun create(context: Context, messageId: String, open: () -> InputStream?): Boolean {
        return try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            open()?.use { BitmapFactory.decodeStream(it, null, bounds) }
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return false

            var sample = 1
            while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= MAX_SIDE) sample *= 2
            val decoded = open()?.use {
                BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
            } ?: return false

            val orientation = open()?.use {
                ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
            } ?: ExifInterface.ORIENTATION_NORMAL

            val matrix = Matrix()
            when (orientation) {
                ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
                ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
                ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
                ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
                ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
            }
            val scale = minOf(1f, MAX_SIDE.toFloat() / maxOf(decoded.width, decoded.height))
            matrix.postScale(scale, scale)
            val transformed = Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)

            // JPEG has no alpha: flatten onto white so transparent PNGs do not turn black.
            val flat = Bitmap.createBitmap(transformed.width, transformed.height, Bitmap.Config.ARGB_8888)
            Canvas(flat).apply {
                drawColor(Color.WHITE)
                drawBitmap(transformed, 0f, 0f, null)
            }
            FileOutputStream(file(context, messageId)).use { flat.compress(Bitmap.CompressFormat.JPEG, 85, it) }
            true
        } catch (_: Exception) {
            false
        }
    }

    /** Decodes [file] down to roughly [targetPx] on its longest side. */
    fun decode(file: File, targetPx: Int): ImageBitmap? {
        if (!file.isFile) return null
        return try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.path, bounds)
            if (bounds.outWidth <= 0) return null
            var sample = 1
            while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= targetPx) sample *= 2
            BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample })?.asImageBitmap()
        } catch (_: Exception) {
            null
        }
    }
}
