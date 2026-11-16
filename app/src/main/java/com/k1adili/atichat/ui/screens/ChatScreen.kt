package com.k1adili.atichat.ui.screens

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.k1adili.atichat.core.Jalali
import com.k1adili.atichat.core.Protocol
import com.k1adili.atichat.data.MessageEntity
import com.k1adili.atichat.ui.components.Avatar
import com.k1adili.atichat.ui.theme.MessageTextStyle
import com.k1adili.atichat.ui.theme.Spacing
import com.k1adili.atichat.util.FileOpener
import com.k1adili.atichat.util.PickedFile
import com.k1adili.atichat.util.UriFile
import kotlinx.coroutines.launch
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    title: String,
    avatar: String,
    peerId: String?,
    online: Boolean,
    typing: Boolean,
    messages: List<MessageEntity>,
    downloadsDir: File,
    onBack: () -> Unit,
    onSend: (text: String, onSent: () -> Unit) -> Unit,
    onTyping: (String) -> Unit,
    resolveFile: suspend (Uri) -> PickedFile?,
    onSendFile: (PickedFile, String) -> Unit,
    onDeleteMessage: (String) -> Unit,
    onClearConversation: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    var text by rememberSaveable(peerId) { mutableStateOf("") }
    var menuOpen by remember { mutableStateOf(false) }
    var confirmClear by remember { mutableStateOf(false) }
    var pendingFile by remember { mutableStateOf<PickedFile?>(null) }
    val canWrite = peerId == null || online

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            scope.launch { pendingFile = resolveFile(uri) }
        }
    }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.lastIndex)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "بازگشت") }
                },
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                        Avatar(encoded = avatar, name = title, size = 40.dp)
                        Column {
                            Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                text = when {
                                    typing -> "در حال نوشتن…"
                                    peerId == null -> "همهٔ کاربران شبکهٔ محلی"
                                    online -> "برخط"
                                    else -> "آفلاین"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = if (typing || online) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
                actions = {
                    Box {
                        IconButton(onClick = { menuOpen = true }) { Icon(Icons.Default.MoreVert, contentDescription = "گزینه‌ها") }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(
                                text = { Text("پاک‌کردن این گفتگو") },
                                onClick = {
                                    menuOpen = false
                                    confirmClear = true
                                },
                            )
                        }
                    }
                },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).imePadding()) {
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(Spacing.lg),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                items(messages, key = { it.id }) { message ->
                    MessageBubble(
                        message = message,
                        showSender = peerId == null,
                        onOpenFile = {
                            if (!FileOpener.open(context, downloadsDir, message.fileName)) {
                                Toast.makeText(context, "فایل در دسترس نیست یا برنامه‌ای برای بازکردنش نصب نیست.", Toast.LENGTH_SHORT).show()
                            }
                        },
                        onDelete = { onDeleteMessage(message.messageId) },
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.sm, vertical = Spacing.sm),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                if (peerId != null) {
                    IconButton(onClick = { picker.launch(arrayOf("*/*")) }, enabled = online) {
                        Icon(Icons.Default.AttachFile, contentDescription = "ارسال فایل")
                    }
                }
                OutlinedTextField(
                    value = text,
                    onValueChange = {
                        text = it
                        onTyping(it)
                    },
                    modifier = Modifier.weight(1f),
                    enabled = canWrite,
                    placeholder = {
                        Text(if (canWrite) "پیام…" else "ارسال فقط هنگام برخط بودن مخاطب ممکن است")
                    },
                    textStyle = MaterialTheme.typography.bodyMedium.merge(MessageTextStyle),
                    maxLines = 5,
                    shape = RoundedCornerShape(20.dp),
                )
                FilledIconButton(
                    onClick = { onSend(text) { text = "" } },
                    enabled = canWrite && text.isNotBlank(),
                ) {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "ارسال")
                }
            }
        }
    }

    pendingFile?.let { file ->
        var caption by remember(file) { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { pendingFile = null },
            title = { Text("ارسال فایل") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                    Text(file.name, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(
                        Jalali.toPersianDigits(UriFile.humanSize(file.size)),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (file.size > Protocol.MAX_FILE_SIZE) {
                        Text(
                            "حداکثر اندازهٔ هر فایل ${Jalali.toPersianDigits((Protocol.MAX_FILE_SIZE / (1024 * 1024)).toString())} مگابایت است.",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    OutlinedTextField(
                        value = caption,
                        onValueChange = { caption = it },
                        label = { Text("متن همراه فایل (اختیاری)") },
                        textStyle = MaterialTheme.typography.bodyMedium.merge(MessageTextStyle),
                        maxLines = 4,
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = file.size <= Protocol.MAX_FILE_SIZE,
                    onClick = {
                        onSendFile(file, caption)
                        pendingFile = null
                    },
                ) { Text("ارسال فایل", maxLines = 1) }
            },
            dismissButton = { TextButton(onClick = { pendingFile = null }) { Text("لغو", maxLines = 1) } },
        )
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("پاک‌کردن گفتگو") },
            text = { Text("پیام‌های این گفتگو فقط در همین دستگاه پاک می‌شوند و فایل‌های دریافت‌شده حذف نمی‌شوند.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmClear = false
                    onClearConversation()
                }) { Text("پاک‌کردن", maxLines = 1) }
            },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("لغو", maxLines = 1) } },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MessageBubble(
    message: MessageEntity,
    showSender: Boolean,
    onOpenFile: () -> Unit,
    onDelete: () -> Unit,
) {
    val outgoing = message.outgoing
    val isFile = message.kind == "file"
    val canOpen = isFile && !outgoing && message.fileName.isNotEmpty()
    var menuOpen by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(16.dp)

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (outgoing) Arrangement.Start else Arrangement.End,
    ) {
        Box {
            Surface(
                shape = shape,
                color = if (outgoing) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier
                    .widthIn(max = 300.dp)
                    .clip(shape)
                    .combinedClickable(
                        onClick = { if (canOpen) onOpenFile() },
                        onLongClick = { menuOpen = true },
                    ),
            ) {
                Column(modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.sm)) {
                    if (showSender && !outgoing) {
                        Text(message.senderName, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    }
                    if (isFile) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                            Icon(Icons.Default.AttachFile, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Text(
                                text = message.body,
                                style = MaterialTheme.typography.bodyMedium.merge(MessageTextStyle),
                                textDecoration = if (canOpen) TextDecoration.Underline else null,
                            )
                        }
                    } else {
                        Text(text = message.body, style = MaterialTheme.typography.bodyMedium.merge(MessageTextStyle))
                    }
                    Text(
                        text = Jalali.formatDateTime(Protocol.parseTimestamp(message.timestamp)),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(
                    text = { Text("حذف از این دستگاه") },
                    onClick = {
                        menuOpen = false
                        onDelete()
                    },
                )
            }
        }
    }
}
