package com.k1adili.atichat

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.k1adili.atichat.core.Peer
import com.k1adili.atichat.core.Protocol
import com.k1adili.atichat.data.ChatEngine
import com.k1adili.atichat.data.MessageEntity
import com.k1adili.atichat.data.ProfileEntity
import com.k1adili.atichat.util.AvatarEncoder
import com.k1adili.atichat.util.PickedFile
import com.k1adili.atichat.util.UriFile
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface Route {
    data object Home : Route

    /** [peerId] == null is the public chat. */
    data class Chat(val peerId: String?, val title: String) : Route

    data object Settings : Route
}

data class ChatRow(
    val key: String,
    val peerId: String?,
    val name: String,
    val avatar: String,
    val online: Boolean,
    val unread: Int,
)

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as AtiChatApp
    val engine: ChatEngine = app.engine

    private val _route = MutableStateFlow<Route>(Route.Home)
    val route: StateFlow<Route> = _route.asStateFlow()

    private val _toasts = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val toasts: SharedFlow<String> = _toasts.asSharedFlow()

    val profile: StateFlow<ProfileEntity?> =
        engine.profile.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val peers: StateFlow<Map<String, Peer>> = engine.peers
    val status: StateFlow<String?> = engine.status
    val typingPeer: StateFlow<String?> = engine.typingPeer
    val running: StateFlow<Boolean> = engine.running

    @OptIn(ExperimentalCoroutinesApi::class)
    private val contacts = profile.flatMapLatest { p ->
        if (p == null) flowOf(emptyList()) else engine.observeContacts(p.userId)
    }

    /** Public chat first, then online users, then offline contacts that have history. */
    val chatRows: StateFlow<List<ChatRow>> =
        combine(engine.peers, contacts, engine.unread) { online, history, unread ->
            val onlineRows = online.values.sortedBy { it.username }.map {
                ChatRow(it.userId, it.userId, it.username, it.avatar, true, unread[it.userId] ?: 0)
            }
            val offlineRows = history.filter { it.userId !in online }.map {
                ChatRow(it.userId, it.userId, it.username, "", false, unread[it.userId] ?: 0)
            }
            listOf(
                ChatRow(ChatEngine.PUBLIC_KEY, null, "گفتگوی عمومی", "", true, unread[ChatEngine.PUBLIC_KEY] ?: 0),
            ) + onlineRows + offlineRows
        }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val messages: StateFlow<List<MessageEntity>> =
        combine(_route, profile) { route, p -> route to p }
            .flatMapLatest { (route, p) ->
                if (route is Route.Chat && p != null) engine.observeMessages(route.peerId, p.userId) else flowOf(emptyList())
            }
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    // ------------------------------------------------------------------------------------ navigation

    fun openChat(peerId: String?, title: String) {
        val key = peerId ?: ChatEngine.PUBLIC_KEY
        engine.activeChat = key
        engine.clearUnread(key)
        _route.value = Route.Chat(peerId, title)
    }

    fun openChatFromNotification(chatKey: String, title: String) {
        openChat(if (chatKey == ChatEngine.PUBLIC_KEY) null else chatKey, title)
    }

    fun openSettings() {
        engine.activeChat = null
        _route.value = Route.Settings
    }

    fun back() {
        engine.activeChat = null
        _route.value = Route.Home
    }

    // ------------------------------------------------------------------------------------ actions

    private fun toast(text: String) {
        _toasts.tryEmit(text)
    }

    fun send(peerId: String?, text: String, onSent: () -> Unit) {
        val body = text.trim()
        if (body.isEmpty()) return
        if (body.codePointCount(0, body.length) > Protocol.MAX_MESSAGE_LENGTH) {
            toast("پیام طولانی است؛ حداکثر ${Protocol.MAX_MESSAGE_LENGTH} نویسه.")
            return
        }
        viewModelScope.launch {
            try {
                if (peerId == null) engine.sendPublic(body) else engine.sendDirect(peerId, body)
                onSent()
            } catch (error: Exception) {
                toast(error.message ?: "پیام ارسال نشد.")
            }
        }
    }

    fun typing(peerId: String?, text: String) {
        if (peerId != null && text.isNotBlank()) engine.sendTyping(peerId)
    }

    /** Resolves the picked document so the UI can ask for a caption before sending. */
    suspend fun resolveFile(uri: Uri): PickedFile? = withContext(Dispatchers.IO) {
        try {
            UriFile.resolve(getApplication(), uri)
        } catch (error: Exception) {
            toast("خواندن این فایل ممکن نیست.")
            null
        }
    }

    fun sendFile(peerId: String, file: PickedFile, caption: String) {
        viewModelScope.launch {
            try {
                engine.sendFile(peerId, file, caption)
            } catch (error: Exception) {
                toast(error.message ?: "ارسال فایل ناموفق بود.")
            }
        }
    }

    fun deleteMessage(messageId: String) {
        viewModelScope.launch { engine.deleteMessage(messageId) }
    }

    fun clearConversation(peerId: String?) {
        viewModelScope.launch {
            engine.clearConversation(peerId)
            toast("گفتگو فقط در همین دستگاه پاک شد.")
        }
    }

    fun clearAllHistory() {
        viewModelScope.launch {
            engine.clearAllHistory()
            toast("همهٔ تاریخچه‌ها در همین دستگاه پاک شد.")
        }
    }

    fun refreshUsers() {
        engine.refresh()
        toast("درخواست به‌روزرسانی کاربران ارسال شد.")
    }

    fun saveProfile(name: String, avatar: String) {
        if (name.isBlank()) {
            toast("نام نمی‌تواند خالی باشد.")
            return
        }
        viewModelScope.launch {
            engine.updateProfile(name, avatar)
            toast("نمایه ذخیره شد.")
        }
    }

    /** Returns the encoded avatar, or null (with a toast) if the picture could not be used. */
    suspend fun encodeAvatar(uri: Uri): String? = withContext(Dispatchers.IO) {
        try {
            AvatarEncoder.encode(getApplication(), uri)
        } catch (error: Exception) {
            toast("تصویر ساده‌تر یا کم‌حجم‌تری انتخاب کنید.")
            null
        }
    }

    fun exportBackup(uri: Uri) {
        viewModelScope.launch {
            try {
                engine.exportBackup(uri)
                toast("پشتیبان ذخیره شد.")
            } catch (error: Exception) {
                toast("ذخیرهٔ پشتیبان ناموفق بود.")
            }
        }
    }

    fun importBackup(uri: Uri) {
        viewModelScope.launch {
            try {
                val count = engine.importBackup(uri)
                toast("پشتیبان بازیابی شد (${count} پیام).")
            } catch (error: Exception) {
                toast("این فایل پشتیبان معتبر AtiChat نیست.")
            }
        }
    }
}
