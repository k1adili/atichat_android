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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import com.k1adili.atichat.ui.components.Avatar
import com.k1adili.atichat.ui.theme.Spacing
import kotlinx.coroutines.launch
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    profile: ProfileEntity,
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
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.lg)) {
                    Avatar(encoded = avatar, name = name, size = 72.dp)
                    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm), modifier = Modifier.weight(1f)) {
                        OutlinedButton(onClick = { avatarPicker.launch("image/*") }, modifier = Modifier.fillMaxWidth()) {
                            Text("انتخاب تصویر", maxLines = 1)
                        }
                        if (avatar.isNotEmpty()) {
                            TextButton(onClick = { avatar = "" }, modifier = Modifier.fillMaxWidth()) {
                                Text("حذف تصویر", maxLines = 1)
                            }
                        }
                    }
                }
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = Protocol.limitCodePoints(it, Protocol.MAX_NAME_LENGTH) },
                    label = { Text("نام نمایشی") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Button(onClick = { onSaveProfile(name, avatar) }, modifier = Modifier.fillMaxWidth()) {
                    Text("ذخیرهٔ نمایه", maxLines = 1)
                }
            }

            SectionCard("پشتیبان‌گیری") {
                Text(
                    "فایل پشتیبان شامل نمایه و تاریخچهٔ پیام‌هاست (نه خود فایل‌های دریافتی).",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(
                        onClick = { exportLauncher.launch("atichat-backup-${LocalDate.now()}.json") },
                        modifier = Modifier.weight(1f),
                    ) { Text("ذخیرهٔ پشتیبان", maxLines = 1) }
                    OutlinedButton(
                        onClick = { importLauncher.launch(arrayOf("*/*")) },
                        modifier = Modifier.weight(1f),
                    ) { Text("بازیابی پشتیبان", maxLines = 1) }
                }
            }

            SectionCard("شبکه و تاریخچه") {
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(onClick = onRefreshUsers, modifier = Modifier.weight(1f)) {
                        Text("به‌روزرسانی کاربران", maxLines = 1)
                    }
                    OutlinedButton(
                        onClick = { confirmClear = true },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    ) { Text("پاک‌کردن تاریخچه", maxLines = 1) }
                }
                Button(
                    onClick = onExit,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer,
                    ),
                ) { Text("خروج کامل (آفلاین شدن)", maxLines = 1) }
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
