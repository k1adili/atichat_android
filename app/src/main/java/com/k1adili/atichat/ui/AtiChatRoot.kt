package com.k1adili.atichat.ui

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.k1adili.atichat.MainViewModel
import com.k1adili.atichat.Route
import com.k1adili.atichat.ui.screens.ChatListScreen
import com.k1adili.atichat.ui.screens.ChatScreen
import com.k1adili.atichat.ui.screens.SettingsScreen
import com.k1adili.atichat.ui.screens.SharePickerScreen
import com.k1adili.atichat.util.ThemeMode

@Composable
fun AtiChatRoot(viewModel: MainViewModel, darkTheme: Boolean, onExit: () -> Unit) {
    val context = LocalContext.current
    val route by viewModel.route.collectAsStateWithLifecycle()
    val rows by viewModel.chatRows.collectAsStateWithLifecycle()
    val profile by viewModel.profile.collectAsStateWithLifecycle()
    val peers by viewModel.peers.collectAsStateWithLifecycle()
    val status by viewModel.status.collectAsStateWithLifecycle()
    val running by viewModel.running.collectAsStateWithLifecycle()
    val typingPeer by viewModel.typingPeer.collectAsStateWithLifecycle()
    val messages by viewModel.messages.collectAsStateWithLifecycle()
    val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
    val pendingShare by viewModel.pendingShare.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.toasts.collect { Toast.makeText(context, it, Toast.LENGTH_SHORT).show() }
    }
    BackHandler(enabled = route != Route.Home) { viewModel.back() }

    Surface(color = MaterialTheme.colorScheme.background) {
        when (val current = route) {
            Route.Home -> ChatListScreen(
                profile = profile,
                rows = rows,
                status = status,
                running = running,
                darkTheme = darkTheme,
                onOpen = { row -> viewModel.openChat(row.peerId, row.name) },
                onRefresh = viewModel::refreshUsers,
                onToggleTheme = { viewModel.setThemeMode(if (darkTheme) ThemeMode.LIGHT else ThemeMode.DARK) },
                onSettings = viewModel::openSettings,
            )

            is Route.Chat -> {
                val peer = current.peerId?.let { peers[it] }
                val title = peer?.username ?: rows.firstOrNull { it.peerId == current.peerId }?.name ?: current.title
                ChatScreen(
                    title = title,
                    avatar = peer?.avatar ?: "",
                    peerId = current.peerId,
                    online = current.peerId == null || peer != null,
                    typing = current.peerId != null && typingPeer == current.peerId,
                    messages = messages,
                    downloadsDir = viewModel.engine.downloadsDir,
                    onBack = viewModel::back,
                    onSend = { text, onSent -> viewModel.send(current.peerId, text, onSent) },
                    onTyping = { viewModel.typing(current.peerId, it) },
                    resolveFile = viewModel::resolveFile,
                    onSendFile = { file, caption -> current.peerId?.let { viewModel.sendFile(it, file, caption) } },
                    onDeleteMessage = viewModel::deleteMessage,
                    onClearConversation = { viewModel.clearConversation(current.peerId) },
                )
            }

            Route.Settings -> profile?.let { p ->
                SettingsScreen(
                    profile = p,
                    themeMode = themeMode,
                    onThemeMode = viewModel::setThemeMode,
                    onBack = viewModel::back,
                    encodeAvatar = viewModel::encodeAvatar,
                    onSaveProfile = viewModel::saveProfile,
                    onExportBackup = viewModel::exportBackup,
                    onImportBackup = viewModel::importBackup,
                    onClearAllHistory = viewModel::clearAllHistory,
                    onRefreshUsers = viewModel::refreshUsers,
                    onExit = onExit,
                )
            }

            Route.SharePicker -> pendingShare?.let { payload ->
                SharePickerScreen(
                    payload = payload,
                    rows = rows,
                    onPick = { row -> viewModel.sendShared(row.peerId, row.name) },
                    onCancel = viewModel::back,
                )
            }
        }
    }
}
