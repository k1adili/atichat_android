package com.k1adili.atichat.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.k1adili.atichat.ChatRow
import com.k1adili.atichat.SharePayload
import com.k1adili.atichat.core.Jalali
import com.k1adili.atichat.ui.components.Avatar
import com.k1adili.atichat.ui.components.GroupAvatar
import com.k1adili.atichat.ui.theme.Spacing

/** Shown when another app shares files or text into AtiChat: pick who gets it. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SharePickerScreen(
    payload: SharePayload,
    rows: List<ChatRow>,
    onPick: (ChatRow) -> Unit,
    onCancel: () -> Unit,
) {
    // Files only go to one person; the public chat is for text.
    val publicAllowed = payload.uris.isEmpty()
    val targets = rows.filter { it.online && (it.peerId != null || publicAllowed) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("ارسال به…", style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    IconButton(onClick = onCancel) { Icon(Icons.Default.Close, contentDescription = "لغو") }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = Spacing.lg,
                end = Spacing.lg,
                top = padding.calculateTopPadding() + Spacing.sm,
                bottom = padding.calculateBottomPadding() + Spacing.lg,
            ),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                    Column(modifier = Modifier.padding(Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                        if (payload.uris.isNotEmpty()) {
                            Text(
                                "${Jalali.toPersianDigits(payload.uris.size.toString())} فایل برای ارسال",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                            )
                        }
                        payload.text?.takeIf { it.isNotBlank() }?.let {
                            Text(
                                it.trim(),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
            if (targets.isEmpty()) {
                item {
                    Text(
                        "کسی آنلاین نیست. چند لحظه صبر کنید یا مطمئن شوید هر دو دستگاه به یک Wi‑Fi وصل‌اند.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(Spacing.sm),
                    )
                }
            }
            items(targets, key = { it.key }) { row ->
                Card(
                    onClick = { onPick(row) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                ) {
                    Row(
                        modifier = Modifier.padding(Spacing.lg),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
                    ) {
                        if (row.peerId == null) GroupAvatar(size = 44.dp)
                        else Avatar(encoded = row.avatar, name = row.name, size = 44.dp, online = true)
                        Text(row.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}
