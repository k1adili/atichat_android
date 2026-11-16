package com.k1adili.atichat.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.k1adili.atichat.ChatRow
import com.k1adili.atichat.core.Jalali
import com.k1adili.atichat.ui.components.Avatar
import com.k1adili.atichat.ui.theme.Spacing

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatListScreen(
    rows: List<ChatRow>,
    status: String?,
    running: Boolean,
    onOpen: (ChatRow) -> Unit,
    onRefresh: () -> Unit,
    onSettings: () -> Unit,
) {
    val publicRow = rows.firstOrNull { it.peerId == null }
    val online = rows.filter { it.peerId != null && it.online }
    val offline = rows.filter { it.peerId != null && !it.online }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("AtiChat", style = MaterialTheme.typography.titleLarge) },
                actions = {
                    IconButton(onClick = onRefresh) { Icon(Icons.Default.Refresh, contentDescription = "به‌روزرسانی کاربران") }
                    IconButton(onClick = onSettings) { Icon(Icons.Default.Settings, contentDescription = "نمایه و تنظیمات") }
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
            if (!running || status != null) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                    ) {
                        Text(
                            text = status ?: "اتصال شبکه برقرار نیست.",
                            modifier = Modifier.padding(Spacing.lg),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                        )
                    }
                }
            }
            if (publicRow != null) item { ChatRowItem(publicRow, "همهٔ کاربران شبکهٔ محلی", onOpen) }

            item { SectionHeader("کاربران برخط (${Jalali.toPersianDigits(online.size.toString())})") }
            if (online.isEmpty()) {
                item {
                    Text(
                        text = "کاربری پیدا نشد. مطمئن شوید هر دو دستگاه به یک Wi‑Fi وصل‌اند و فایروال ویندوز دسترسی AtiChat را مجاز کرده است؛ سپس دکمهٔ به‌روزرسانی را بزنید.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = Spacing.sm),
                    )
                }
            }
            items(online, key = { it.key }) { ChatRowItem(it, "برخط", onOpen) }

            if (offline.isNotEmpty()) {
                item { SectionHeader("مخاطبین آفلاین") }
                items(offline, key = { it.key }) { ChatRowItem(it, "آفلاین", onOpen) }
            }
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = Spacing.md, start = Spacing.sm),
    )
}

@Composable
private fun ChatRowItem(row: ChatRow, subtitle: String, onOpen: (ChatRow) -> Unit) {
    Card(
        onClick = { onOpen(row) },
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
    ) {
        Row(
            modifier = Modifier.padding(Spacing.lg),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            Avatar(encoded = row.avatar, name = row.name, size = 48.dp)
            Column(modifier = Modifier.weight(1f)) {
                Text(row.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (row.unread > 0) {
                Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primary) {
                    Box(modifier = Modifier.size(28.dp), contentAlignment = Alignment.Center) {
                        Text(
                            text = if (row.unread > 99) "۹۹+" else Jalali.toPersianDigits(row.unread.toString()),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimary,
                            maxLines = 1,
                        )
                    }
                }
            }
        }
    }
}
