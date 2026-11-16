package com.k1adili.atichat.ui.components

import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp

fun decodeAvatar(encoded: String): ImageBitmap? {
    if (encoded.isEmpty()) return null
    return runCatching {
        val bytes = Base64.decode(encoded, Base64.DEFAULT)
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
    }.getOrNull()
}

/** Round avatar; shows the first letter of the name when no picture is set. */
@Composable
fun Avatar(encoded: String, name: String, size: Dp, modifier: Modifier = Modifier) {
    val bitmap = remember(encoded) { decodeAvatar(encoded) }
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        if (bitmap != null) {
            Image(bitmap = bitmap, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        } else {
            val initial = name.trim().takeIf { it.isNotEmpty() }?.let { String(Character.toChars(it.codePointAt(0))) } ?: "؟"
            Text(
                text = initial,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                fontSize = (size.value * 0.42f).sp,
                style = MaterialTheme.typography.titleMedium,
            )
        }
    }
}
