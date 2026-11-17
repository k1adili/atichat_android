package com.k1adili.atichat.ui.components

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

enum class ButtonKind { Primary, Secondary, Danger }

/** Full-width rounded rectangle button with a centered label; the one button style used on settings screens. */
@Composable
fun ActionButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    kind: ButtonKind = ButtonKind.Secondary,
) {
    val shape = RoundedCornerShape(12.dp)
    val sized = modifier.fillMaxWidth().heightIn(min = 48.dp)
    val padding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
    val label: @Composable () -> Unit = {
        Text(
            text = text,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.labelLarge,
        )
    }
    when (kind) {
        ButtonKind.Primary -> Button(onClick = onClick, modifier = sized, shape = shape, contentPadding = padding) { label() }
        ButtonKind.Secondary -> OutlinedButton(onClick = onClick, modifier = sized, shape = shape, contentPadding = padding) { label() }
        ButtonKind.Danger -> Button(
            onClick = onClick,
            modifier = sized,
            shape = shape,
            contentPadding = padding,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer,
            ),
        ) { label() }
    }
}
