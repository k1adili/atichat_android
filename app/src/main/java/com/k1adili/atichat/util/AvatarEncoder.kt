package com.k1adili.atichat.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.net.Uri
import android.util.Base64
import com.k1adili.atichat.core.Protocol
import java.io.ByteArrayOutputStream
import java.io.IOException

/** Same recipe as the Windows app: fit in 48x48, JPEG quality 75, base64, at most 16,000 characters. */
object AvatarEncoder {
    private const val SIZE = 48

    @Throws(IOException::class)
    fun encode(context: Context, uri: Uri): String {
        val resolver = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) throw IOException("invalid image")

        var sample = 1
        while (bounds.outWidth / (sample * 2) >= SIZE && bounds.outHeight / (sample * 2) >= SIZE) sample *= 2
        val decoded = resolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        } ?: throw IOException("invalid image")

        val ratio = minOf(SIZE.toFloat() / decoded.width, SIZE.toFloat() / decoded.height)
        val width = maxOf(1, (decoded.width * ratio).toInt())
        val height = maxOf(1, (decoded.height * ratio).toInt())
        val scaled = Bitmap.createScaledBitmap(decoded, width, height, true)

        // JPEG has no alpha: flatten onto white so transparent PNGs do not turn black.
        val flat = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        Canvas(flat).apply {
            drawColor(Color.WHITE)
            drawBitmap(scaled, 0f, 0f, null)
        }
        val bytes = ByteArrayOutputStream().also { flat.compress(Bitmap.CompressFormat.JPEG, 75, it) }.toByteArray()
        val encoded = Base64.encodeToString(bytes, Base64.NO_WRAP)
        if (encoded.length > Protocol.MAX_AVATAR_LENGTH) throw IOException("avatar too large")
        return encoded
    }
}
