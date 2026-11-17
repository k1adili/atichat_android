package com.k1adili.atichat.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.k1adili.atichat.BuildConfig
import com.k1adili.atichat.core.Protocol
import com.k1adili.atichat.data.ProfileEntity
import com.k1adili.atichat.ui.components.ActionButton
import com.k1adili.atichat.ui.components.Avatar
import com.k1adili.atichat.ui.components.ButtonKind
import com.k1adili.atichat.ui.theme.Spacing
import com.k1adili.atichat.util.ThemeMode
import kotlinx.coroutines.launch
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    profile: ProfileEntity,
    themeMode: ThemeMode,
    onThemeMode: (ThemeMode) -> Unit,
    onBack: () -> Unit,
    encodeAvatar: suspend (Uri) -> String?,
    onSaveProfile: (name: String, avatar: String) -> Unit,
    onExportBackup: (Uri) -> Unit,
    onImportBackup: (Uri) -> Unit,
    onClearAllHistory: () -> Unit,
    onRefreshUsers: () -> Unit,
    onExit: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var name by remember(profile.displayName) { mutableStateOf(profile.displayName) }
    var avatar by remember(profile.avatar) { mutableStateOf(profile.avatar) }
    var confirmClear by remember { mutableStateOf(false) }
    var pendingImport by remember { mutableStateOf<Uri?>(null) }

    val avatarPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) scope.launch { encodeAvatar(uri)?.let { avatar = it } }
    }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) onExportBackup(uri)
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) pendingImport = uri
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("نمایه و تنظیمات", style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "بازگشت") }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier.padding(padding).verticalScroll(rememberScrollState()).padding(Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.lg),
        ) {
            SectionCard("نمایه") {
                Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Avatar(encoded = avatar, name = name, size = 88.dp)
                }
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = Protocol.limitCodePoints(it, Protocol.MAX_NAME_LENGTH) },
                    label = { Text("نام نمایشی") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                ActionButton("انتخاب تصویر", onClick = { avatarPicker.launch("image/*") })
                if (avatar.isNotEmpty()) ActionButton("حذف تصویر", onClick = { avatar = "" })
                ActionButton("ذخیرهٔ نمایه", onClick = { onSaveProfile(name, avatar) }, kind = ButtonKind.Primary)
            }

            SectionCard("ظاهر برنامه") {
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), modifier = Modifier.fillMaxWidth()) {
                    ThemeChoice("خودکار", ThemeMode.SYSTEM, themeMode, onThemeMode, Modifier.weight(1f))
                    ThemeChoice("روشن", ThemeMode.LIGHT, themeMode, onThemeMode, Modifier.weight(1f))
                    ThemeChoice("شب", ThemeMode.DARK, themeMode, onThemeMode, Modifier.weight(1f))
                }
            }

            SectionCard("پشتیبان‌گیری") {
                Text(
                    "فایل پشتیبان شامل نمایه و تاریخچهٔ پیام‌هاست (نه خود فایل‌های دریافتی).",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                ActionButton("ذخیرهٔ پشتیبان", onClick = { exportLauncher.launch("atichat-backup-${LocalDate.now()}.json") })
                ActionButton("بازیابی پشتیبان", onClick = { importLauncher.launch(arrayOf("*/*")) })
            }

            SectionCard("شبکه و تاریخچه") {
                ActionButton("به‌روزرسانی کاربران", onClick = onRefreshUsers)
                ActionButton("پاک‌کردن همهٔ تاریخچه", onClick = { confirmClear = true }, kind = ButtonKind.Danger)
                ActionButton("خروج کامل (آفلاین شدن)", onClick = onExit, kind = ButtonKind.Danger)
            }

            HorizontalDivider()
            Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("کیوان عدیلی", style = MaterialTheme.typography.titleSmall, textAlign = TextAlign.Center)
                Text(
                    "نسخهٔ \u2066${BuildConfig.VERSION_NAME}\u2069",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("پاک‌کردن همهٔ تاریخچه‌ها") },
            text = { Text("همهٔ پیام‌ها فقط در همین دستگاه پاک می‌شوند و فایل‌های دریافت‌شده حذف نمی‌شوند.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmClear = false
                    onClearAllHistory()
                }) { Text("پاک‌کردن", maxLines = 1) }
            },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("لغو", maxLines = 1) } },
        )
    }

    pendingImport?.let { uri ->
        AlertDialog(
            onDismissRequest = { pendingImport = null },
            title = { Text("بازیابی پشتیبان") },
            text = { Text("شناسه، نام، تصویر و پیام‌های فایل پشتیبان بازیابی می‌شوند و پیام‌های تکراری نادیده گرفته می‌شوند. ادامه می‌دهید؟") },
            confirmButton = {
                TextButton(onClick = {
                    pendingImport = null
                    onImportBackup(uri)
                }) { Text("بازیابی", maxLines = 1) }
            },
            dismissButton = { TextButton(onClick = { pendingImport = null }) { Text("لغو", maxLines = 1) } },
        )
    }
}

@Composable
private fun ThemeChoice(
    label: String,
    mode: ThemeMode,
    current: ThemeMode,
    onSelect: (ThemeMode) -> Unit,
    modifier: Modifier,
) {
    ActionButton(
        text = label,
        onClick = { onSelect(mode) },
        modifier = modifier,
        kind = if (mode == current) ButtonKind.Primary else ButtonKind.Secondary,
    )
}

@Composable
private fun SectionCard(title: String, content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
    ) {
        Column(modifier = Modifier.padding(Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            content()
        }
    }
}
