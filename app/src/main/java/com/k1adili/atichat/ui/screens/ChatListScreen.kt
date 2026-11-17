package com.k1adili.atichat.ui.screens

import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.k1adili.atichat.ChatRow
import com.k1adili.atichat.core.Jalali
import com.k1adili.atichat.data.ProfileEntity
import com.k1adili.atichat.ui.components.Avatar
import com.k1adili.atichat.ui.components.GroupAvatar
import com.k1adili.atichat.ui.theme.OnlineGreen
import com.k1adili.atichat.ui.theme.Spacing

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatListScreen(
    profile: ProfileEntity?,
    rows: List<ChatRow>,
    status: String?,
    running: Boolean,
    darkTheme: Boolean,
    onOpen: (ChatRow) -> Unit,
    onRefresh: () -> Unit,
    onToggleTheme: () -> Unit,
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
                    IconButton(onClick = onToggleTheme) {
                        Icon(
                            imageVector = if (darkTheme) Icons.Default.LightMode else Icons.Default.DarkMode,
                            contentDescription = if (darkTheme) "تم روشن" else "تم شب",
                        )
                    }
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
            if (profile != null) {
                item { MyProfileCard(profile, running, online.size, onSettings) }
            }
            if (status != null) {
                item {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                        Text(
                            text = status,
                            modifier = Modifier.padding(Spacing.lg),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                        )
                    }
                }
            }
            if (publicRow != null) {
                item { SectionHeader("همه") }
                item { ChatRowItem(publicRow, onOpen) }
            }

            item { SectionHeader("آنلاین (${Jalali.toPersianDigits(online.size.toString())})") }
            if (online.isEmpty()) {
                item {
                    Text(
                        text = "کسی آنلاین نیست. مطمئن شوید هر دو دستگاه به یک Wi‑Fi وصل‌اند و فایروال ویندوز اجازهٔ AtiChat را داده است؛ سپس دکمهٔ به‌روزرسانی را بزنید.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = Spacing.sm),
                    )
                }
            }
            items(online, key = { it.key }) { ChatRowItem(it, onOpen) }

            if (offline.isNotEmpty()) {
                item { SectionHeader("آفلاین") }
                items(offline, key = { it.key }) { ChatRowItem(it, onOpen) }
            }
        }
    }
}

@Composable
private fun MyProfileCard(profile: ProfileEntity, running: Boolean, onlineCount: Int, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
    ) {
        Row(
            modifier = Modifier.padding(Spacing.lg),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            Avatar(encoded = profile.avatar, name = profile.displayName, size = 48.dp, online = running)
            Column(modifier = Modifier.weight(1f)) {
                Text(profile.displayName, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    text = if (running) "شما آنلاین هستید • ${Jalali.toPersianDigits(onlineCount.toString())} نفر در شبکه" else "اتصال شبکه برقرار نیست",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (running) OnlineGreen else MaterialTheme.colorScheme.error,
                )
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
private fun ChatRowItem(row: ChatRow, onOpen: (ChatRow) -> Unit) {
    val isPublic = row.peerId == null
    val colors = MaterialTheme.colorScheme
    // Online rows are strong (tinted card, bold name); offline rows recede (flat, dimmed text).
    val container = when {
        isPublic -> colors.tertiaryContainer.copy(alpha = 0.6f)
        row.online -> colors.primaryContainer
        else -> colors.surfaceVariant.copy(alpha = 0.3f)
    }
    val titleColor = if (row.online) colors.onSurface else colors.onSurfaceVariant
    Card(
        onClick = { onOpen(row) },
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = container),
    ) {
        Row(
            modifier = Modifier.padding(Spacing.lg),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            if (isPublic) GroupAvatar(size = 48.dp)
            else Avatar(encoded = row.avatar, name = row.name, size = 48.dp, online = row.online)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = row.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = if (row.online) FontWeight.Bold else FontWeight.Normal,
                    color = titleColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = row.preview.ifEmpty { if (isPublic) "همهٔ کاربران شبکهٔ محلی" else if (row.online) "آنلاین" else "آفلاین" },
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                if (row.time.isNotEmpty()) {
                    Text(row.time, style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
                }
                if (row.unread > 0) {
                    Surface(shape = CircleShape, color = colors.primary) {
                        Box(modifier = Modifier.size(24.dp), contentAlignment = Alignment.Center) {
                            Text(
                                text = if (row.unread > 99) "۹۹+" else Jalali.toPersianDigits(row.unread.toString()),
                                style = MaterialTheme.typography.labelSmall,
                                color = colors.onPrimary,
                                maxLines = 1,
                            )
                        }
                    }
                }
            }
        }
    }
}
