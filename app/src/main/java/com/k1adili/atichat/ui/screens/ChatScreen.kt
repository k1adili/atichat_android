package com.k1adili.atichat.ui.screens

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.k1adili.atichat.core.Jalali
import com.k1adili.atichat.core.Protocol
import com.k1adili.atichat.data.MessageEntity
import com.k1adili.atichat.ui.components.Avatar
import com.k1adili.atichat.ui.components.GroupAvatar
import com.k1adili.atichat.ui.theme.MessageTextStyle
import com.k1adili.atichat.ui.theme.OnlineGreen
import com.k1adili.atichat.ui.theme.Spacing
import com.k1adili.atichat.util.PickedFile
import com.k1adili.atichat.util.ReceivedFiles
import com.k1adili.atichat.util.ShareHelper
import com.k1adili.atichat.util.Thumbnails
import com.k1adili.atichat.util.UriFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate
import java.time.ZoneId

private sealed interface ChatItem {
    data class Day(val label: String) : ChatItem
    data class Msg(val message: MessageEntity) : ChatItem
}

private fun dayLabel(day: LocalDate, today: LocalDate): String = when (day) {
    today -> "امروز"
    today.minusDays(1) -> "دیروز"
    else -> {
        val j = Jalali.fromGregorian(day)
        Jalali.toPersianDigits("%04d/%02d/%02d".format(java.util.Locale.ROOT, j.year, j.month, j.day))
    }
}

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
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    var text by rememberSaveable(peerId) { mutableStateOf("") }
    var menuOpen by remember { mutableStateOf(false) }
    var confirmClear by remember { mutableStateOf(false) }
    var pendingFile by remember { mutableStateOf<PickedFile?>(null) }
    var viewing by remember { mutableStateOf<MessageEntity?>(null) }
    val canWrite = peerId == null || online

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            scope.launch { pendingFile = resolveFile(uri) }
        }
    }

    val items = remember(messages) {
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        buildList<ChatItem> {
            var lastDay: LocalDate? = null
            for (message in messages) {
                val day = Protocol.parseTimestamp(message.timestamp).atZone(zone).toLocalDate()
                if (day != lastDay) {
                    add(ChatItem.Day(dayLabel(day, today)))
                    lastDay = day
                }
                add(ChatItem.Msg(message))
            }
        }
    }

    LaunchedEffect(items.size) {
        if (items.isNotEmpty()) listState.animateScrollToItem(items.lastIndex)
    }

    fun fileUri(message: MessageEntity): Uri? {
        val uri = ReceivedFiles.uriFor(context, downloadsDir, message.fileName)
        if (uri == null) Toast.makeText(context, "این فایل دیگر در دسترس نیست.", Toast.LENGTH_SHORT).show()
        return uri
    }

    fun openFile(message: MessageEntity) {
        val uri = fileUri(message) ?: return
        if (!ShareHelper.openFile(context, uri, ReceivedFiles.mimeOf(message.body))) {
            Toast.makeText(context, "برنامه‌ای برای بازکردن این فایل نصب نیست.", Toast.LENGTH_SHORT).show()
        }
    }

    fun shareFile(message: MessageEntity) {
        val uri = fileUri(message) ?: return
        ShareHelper.shareFile(context, uri, ReceivedFiles.mimeOf(message.body))
    }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "بازگشت") }
                },
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                        if (peerId == null) GroupAvatar(size = 40.dp)
                        else Avatar(encoded = avatar, name = title, size = 40.dp, online = online)
                        Column {
                            Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                text = when {
                                    typing -> "در حال نوشتن…"
                                    peerId == null -> "همهٔ کاربران شبکهٔ محلی"
                                    online -> "آنلاین"
                                    else -> "آفلاین"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = if (typing || online) OnlineGreen else MaterialTheme.colorScheme.onSurfaceVariant,
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
            if (items.isEmpty()) {
                Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text(
                        text = if (canWrite) "هنوز پیامی نیست. اولین پیام را بفرستید." else "این کاربر آنلاین نیست.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentPadding = PaddingValues(Spacing.lg),
                    verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    itemsIndexed(
                        items,
                        key = { index, item ->
                            when (item) {
                                is ChatItem.Day -> "d$index"
                                is ChatItem.Msg -> "m${item.message.id}"
                            }
                        },
                    ) { _, item ->
                        when (item) {
                            is ChatItem.Day -> DayChip(item.label)
                            is ChatItem.Msg -> MessageBubble(
                                message = item.message,
                                showSender = peerId == null,
                                onOpenFile = { openFile(item.message) },
                                onViewImage = { viewing = item.message },
                                onShareFile = { shareFile(item.message) },
                                onCopy = { clipboard.setText(AnnotatedString(item.message.body)) },
                                onShareText = { ShareHelper.shareText(context, item.message.body) },
                                onDelete = { onDeleteMessage(item.message.messageId) },
                            )
                        }
                    }
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
                TextField(
                    value = text,
                    onValueChange = {
                        text = it
                        onTyping(it)
                    },
                    modifier = Modifier.weight(1f),
                    enabled = canWrite,
                    placeholder = { Text(if (canWrite) "پیام…" else "ارسال فقط وقتی مخاطب آنلاین است ممکن است") },
                    textStyle = MaterialTheme.typography.bodyMedium.merge(MessageTextStyle),
                    maxLines = 5,
                    shape = RoundedCornerShape(24.dp),
                    colors = TextFieldDefaults.colors(
                        focusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                        unfocusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                        disabledIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                        disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    ),
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

    viewing?.let { message ->
        val hasOriginal = !message.outgoing && message.fileName.isNotEmpty()
        ImageViewer(
            file = Thumbnails.file(context, message.messageId),
            title = message.body,
            onClose = { viewing = null },
            onShare = if (hasOriginal) ({ shareFile(message) }) else null,
            onOpen = if (hasOriginal) ({ openFile(message) }) else null,
        )
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

@Composable
private fun DayChip(label: String) {
    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.xs),
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MessageBubble(
    message: MessageEntity,
    showSender: Boolean,
    onOpenFile: () -> Unit,
    onViewImage: () -> Unit,
    onShareFile: () -> Unit,
    onCopy: () -> Unit,
    onShareText: () -> Unit,
    onDelete: () -> Unit,
) {
    val context = LocalContext.current
    val outgoing = message.outgoing
    val isFile = message.kind == "file"
    val isImage = isFile && ReceivedFiles.isImage(message.body)
    val hasOriginal = isFile && !outgoing && message.fileName.isNotEmpty()
    var menuOpen by remember { mutableStateOf(false) }
    // The small corner points at the sender's side.
    val shape = if (outgoing) RoundedCornerShape(16.dp, 16.dp, 4.dp, 16.dp) else RoundedCornerShape(16.dp, 16.dp, 16.dp, 4.dp)

    val thumbnail by produceState<ImageBitmap?>(null, message.messageId, isImage) {
        value = if (isImage) {
            withContext(Dispatchers.IO) { Thumbnails.decode(Thumbnails.file(context, message.messageId), 720) }
        } else {
            null
        }
    }

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
                        onClick = {
                            when {
                                thumbnail != null -> onViewImage()
                                hasOriginal -> onOpenFile()
                            }
                        },
                        onLongClick = { menuOpen = true },
                    ),
            ) {
                Column(modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.sm)) {
                    if (showSender && !outgoing) {
                        Text(message.senderName, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    }
                    val preview = thumbnail
                    if (preview != null) {
                        Image(
                            bitmap = preview,
                            contentDescription = message.body,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .widthIn(max = 260.dp)
                                .heightIn(max = 300.dp)
                                .clip(RoundedCornerShape(12.dp)),
                        )
                    } else if (isFile) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                            Icon(Icons.Default.AttachFile, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Text(
                                text = message.body,
                                style = MaterialTheme.typography.bodyMedium.merge(MessageTextStyle),
                                textDecoration = if (hasOriginal) TextDecoration.Underline else null,
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
                if (!isFile) {
                    DropdownMenuItem(text = { Text("کپی متن") }, onClick = { menuOpen = false; onCopy() })
                    DropdownMenuItem(text = { Text("اشتراک‌گذاری") }, onClick = { menuOpen = false; onShareText() })
                }
                if (hasOriginal) {
                    DropdownMenuItem(text = { Text("باز کردن") }, onClick = { menuOpen = false; onOpenFile() })
                    DropdownMenuItem(text = { Text("اشتراک‌گذاری") }, onClick = { menuOpen = false; onShareFile() })
                }
                DropdownMenuItem(text = { Text("حذف از این دستگاه") }, onClick = { menuOpen = false; onDelete() })
            }
        }
    }
}
