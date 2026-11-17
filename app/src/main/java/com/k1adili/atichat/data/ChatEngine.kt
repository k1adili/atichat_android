package com.k1adili.atichat.data

import android.app.Application
import android.net.Uri
import com.k1adili.atichat.core.LanNode
import com.k1adili.atichat.core.NetEvent
import com.k1adili.atichat.core.Peer
import com.k1adili.atichat.core.Protocol
import com.k1adili.atichat.util.CertStore
import com.k1adili.atichat.util.ReceivedFiles
import com.k1adili.atichat.util.Thumbnails
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.time.Instant
import java.util.UUID

fun interface IncomingNotifier {
    fun notify(title: String, text: String, chatKey: String, chatName: String)
}

/**
 * Owns the [LanNode], persists traffic in Room and exposes the live state the UI observes.
 * Lives in the Application so it survives the Activity; started and stopped by ChatService.
 */
class ChatEngine(private val app: Application, db: AppDatabase) {
    private val dao = db.dao()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val nodeMutex = Mutex()
    private val profileMutex = Mutex()

    @Volatile private var node: LanNode? = null

    val downloadsDir: File by lazy {
        (app.getExternalFilesDir("AtiChat") ?: File(app.filesDir, "downloads")).also { it.mkdirs() }
    }

    val profile = dao.observeProfile()

    private val _peers = MutableStateFlow<Map<String, Peer>>(emptyMap())
    val peers: StateFlow<Map<String, Peer>> = _peers.asStateFlow()

    private val _unread = MutableStateFlow<Map<String, Int>>(emptyMap())
    val unread: StateFlow<Map<String, Int>> = _unread.asStateFlow()

    private val _status = MutableStateFlow<String?>(null)
    val status: StateFlow<String?> = _status.asStateFlow()

    private val _typingPeer = MutableStateFlow<String?>(null)
    val typingPeer: StateFlow<String?> = _typingPeer.asStateFlow()

    private val _running = MutableStateFlow(false)
    val running: StateFlow<Boolean> = _running.asStateFlow()

    /** Chat currently open on screen (peer id or [PUBLIC_KEY]); used to skip unread counters and notifications. */
    @Volatile var activeChat: String? = null
    @Volatile var appVisible: Boolean = false
    @Volatile var notifier: IncomingNotifier? = null

    @Volatile private var statusJob: Job? = null
    @Volatile private var typingJob: Job? = null
    private var lastTypingSentAt = 0L

    init {
        scope.launch { ensureProfile() }
    }

    // ------------------------------------------------------------------------------------------ lifecycle

    fun start() {
        scope.launch { nodeMutex.withLock { startLocked() } }
    }

    fun stop() {
        scope.launch { nodeMutex.withLock { stopLocked() } }
    }

    /** Re-creates the node, e.g. after the identity changed through a backup restore. */
    suspend fun restart() {
        nodeMutex.withLock {
            stopLocked()
            startLocked()
        }
    }

    private suspend fun startLocked() {
        if (node != null) return
        try {
            val profile = ensureProfile()
            val (cert, key) = CertStore.loadOrCreate(File(app.filesDir, "tls"))
            val created = LanNode(
                userId = profile.userId,
                username = profile.displayName,
                avatar = profile.avatar,
                certDer = cert,
                keyPkcs8 = key,
                downloadsDir = downloadsDir,
                listener = ::onEvent,
            )
            created.start()
            node = created
            _running.value = true
        } catch (error: Exception) {
            setStatus("AtiChat نتوانست پورت‌های شبکه را باز کند. بررسی کنید برنامهٔ دیگری از پورت‌های ۳۷۰۲۰ و ۳۷۰۲۱ استفاده نکند.")
        }
    }

    private fun stopLocked() {
        node?.stop()
        node = null
        _peers.value = emptyMap()
        _typingPeer.value = null
        _running.value = false
    }

    /** Announce presence now and ask everyone on the LAN to answer (the "refresh users" button). */
    fun refresh() {
        scope.launch { node?.announcePresence() }
    }

    // ------------------------------------------------------------------------------------------ profile

    suspend fun ensureProfile(): ProfileEntity = profileMutex.withLock {
        dao.getProfile() ?: ProfileEntity(
            userId = UUID.randomUUID().toString(),
            displayName = DEFAULT_NAME,
            avatar = "",
        ).also { dao.saveProfile(it) }
    }

    suspend fun updateProfile(displayName: String, avatar: String) {
        val name = Protocol.limitCodePoints(displayName.trim(), Protocol.MAX_NAME_LENGTH).ifBlank { DEFAULT_NAME }
        val current = ensureProfile()
        dao.saveProfile(current.copy(displayName = name, avatar = avatar))
        node?.setProfile(name, avatar)
    }

    // ------------------------------------------------------------------------------------------ sending

    suspend fun sendPublic(body: String) = withContext(Dispatchers.IO) {
        val lan = requireNode()
        val me = ensureProfile()
        val messageId = UUID.randomUUID().toString()
        val timestamp = Protocol.nowTimestamp()
        lan.sendPublic(body, messageId, timestamp)
        dao.insertMessage(
            MessageEntity(
                messageId = messageId, kind = "public", senderId = me.userId, senderName = me.displayName,
                body = body, timestamp = timestamp, outgoing = true,
            ),
        )
    }

    suspend fun sendDirect(peerId: String, body: String) = withContext(Dispatchers.IO) {
        val lan = requireNode()
        val me = ensureProfile()
        val peer = _peers.value[peerId] ?: throw IllegalStateException(OFFLINE_MESSAGE)
        val messageId = UUID.randomUUID().toString()
        val timestamp = Protocol.nowTimestamp()
        lan.sendDirect(peerId, body, messageId, timestamp)
        dao.insertMessage(
            MessageEntity(
                messageId = messageId, kind = "private", senderId = me.userId, senderName = me.displayName,
                recipientId = peerId, recipientName = peer.username, body = body, timestamp = timestamp, outgoing = true,
            ),
        )
    }

    /** Sends the optional caption as a private message first, then the file, like the Windows app. */
    suspend fun sendFile(peerId: String, file: com.k1adili.atichat.util.PickedFile, caption: String) =
        withContext(Dispatchers.IO) {
            val lan = requireNode()
            val peer = _peers.value[peerId] ?: throw IllegalStateException(OFFLINE_MESSAGE)
            if (file.size > Protocol.MAX_FILE_SIZE) {
                throw IllegalArgumentException("حداکثر اندازهٔ فایل ${Protocol.MAX_FILE_SIZE / (1024 * 1024)} مگابایت است.")
            }
            if (caption.isNotBlank()) sendDirect(peerId, caption.trim())
            val me = ensureProfile()
            val resolver = app.contentResolver
            val transfer = lan.sendFile(peerId, file.name, file.size) {
                resolver.openInputStream(file.uri) ?: throw IOException("unreadable file")
            }
            if (ReceivedFiles.isImage(file.name)) {
                Thumbnails.create(app, transfer.messageId) { resolver.openInputStream(file.uri) }
            }
            dao.insertMessage(
                MessageEntity(
                    messageId = transfer.messageId, kind = "file", senderId = me.userId, senderName = me.displayName,
                    recipientId = peerId, recipientName = peer.username, body = file.name, fileName = "",
                    timestamp = transfer.timestamp, outgoing = true,
                ),
            )
        }

    /** Throttled to one packet per second, like the Windows app. */
    fun sendTyping(peerId: String) {
        val now = System.nanoTime()
        if (now - lastTypingSentAt < 1_000_000_000L) return
        lastTypingSentAt = now
        scope.launch { node?.sendTyping(peerId) }
    }

    private fun requireNode(): LanNode = node ?: throw IllegalStateException("شبکه هنوز آماده نیست.")

    // ------------------------------------------------------------------------------------------ history

    suspend fun deleteMessage(messageId: String) = withContext(Dispatchers.IO) {
        Thumbnails.delete(app, messageId)
        dao.deleteMessage(messageId)
    }

    suspend fun clearConversation(peerId: String?) = withContext(Dispatchers.IO) {
        if (peerId == null) dao.deletePublic() else dao.deletePrivate(ensureProfile().userId, peerId)
    }

    suspend fun clearAllHistory() = withContext(Dispatchers.IO) {
        Thumbnails.deleteAll(app)
        dao.deleteAll()
    }

    fun observeMessages(peerId: String?, me: String) =
        if (peerId == null) dao.observePublic() else dao.observePrivate(me, peerId)

    fun observeContacts(me: String) = dao.observeContacts(me)

    fun observeLastMessages(me: String) = dao.observeLastMessages(me)

    fun clearUnread(chatKey: String) = _unread.update { it - chatKey }

    // ------------------------------------------------------------------------------------------ backup

    suspend fun exportBackup(uri: Uri) = withContext(Dispatchers.IO) {
        val json = BackupManager.toJson(ensureProfile(), dao.allMessages(), Instant.now().toString())
        val output = app.contentResolver.openOutputStream(uri, "wt") ?: throw IOException("cannot write")
        output.use { it.write(json.toByteArray(Charsets.UTF_8)) }
    }

    /** Returns the number of messages in the backup. Restores identity, name and avatar too. */
    suspend fun importBackup(uri: Uri): Int = withContext(Dispatchers.IO) {
        val text = app.contentResolver.openInputStream(uri)?.use { String(it.readBytes(), Charsets.UTF_8) }
            ?: throw IOException("cannot read")
        val backup = BackupManager.parse(text)
        val before = ensureProfile()
        backup.profile?.let { dao.saveProfile(it) }
        backup.messages.forEach { dao.insertMessage(it) }
        if (backup.profile != null && backup.profile.userId != before.userId) restart()
        else if (backup.profile != null) node?.setProfile(backup.profile.displayName, backup.profile.avatar)
        backup.messages.size
    }

    // ------------------------------------------------------------------------------------------ events

    private fun onEvent(event: NetEvent) {
        when (event) {
            is NetEvent.PeerOnline -> _peers.update { it + (event.peer.userId to event.peer) }
            is NetEvent.PeerUpdated -> _peers.update { it + (event.peer.userId to event.peer) }
            is NetEvent.PeerOffline -> _peers.update { it - event.peer.userId }
            is NetEvent.Typing -> showTyping(event.userId)
            is NetEvent.Failure -> setStatus(
                if (event.transferType == "file") "ارسال فایل ناموفق بود: ${event.message}" else event.message,
            )
            is NetEvent.FileSent -> setStatus("فایل ارسال شد: ${event.fileName}")
            is NetEvent.PublicMessage -> scope.launch {
                val inserted = dao.insertMessage(
                    MessageEntity(
                        messageId = event.messageId, kind = "public", senderId = event.userId,
                        senderName = event.username, body = event.body, timestamp = event.timestamp, outgoing = false,
                    ),
                )
                if (inserted != -1L) onIncoming(PUBLIC_KEY, "گفتگوی عمومی", "${event.username}: ${event.body}", "گفتگوی عمومی")
            }
            is NetEvent.DirectMessage -> scope.launch {
                val me = ensureProfile()
                val inserted = dao.insertMessage(
                    MessageEntity(
                        messageId = event.messageId, kind = "private", senderId = event.userId,
                        senderName = event.username, recipientId = me.userId, recipientName = me.displayName,
                        body = event.body, timestamp = event.timestamp, outgoing = false,
                    ),
                )
                if (inserted != -1L) onIncoming(event.userId, event.username, event.body, event.username)
            }
            is NetEvent.FileReceived -> scope.launch {
                val me = ensureProfile()
                if (ReceivedFiles.isImage(event.displayName)) {
                    Thumbnails.create(app, event.messageId) { event.file.inputStream() }
                }
                // Public Downloads/AtiChat folder when possible; otherwise the private copy is kept.
                val storedName = ReceivedFiles.moveToDownloads(app, event.file, event.displayName) ?: event.storedName
                val inserted = dao.insertMessage(
                    MessageEntity(
                        messageId = event.messageId, kind = "file", senderId = event.userId,
                        senderName = event.username, recipientId = me.userId, recipientName = me.displayName,
                        body = event.displayName, fileName = storedName, timestamp = event.timestamp,
                        outgoing = false,
                    ),
                )
                if (inserted != -1L) {
                    val what = if (ReceivedFiles.isImage(event.displayName)) "تصویری" else "فایلی"
                    onIncoming(event.userId, event.username, "$what برایتان فرستاد: ${event.displayName}", event.username)
                    setStatus("فایل ذخیره شد: ${event.displayName}")
                } else {
                    event.file.delete()
                }
            }
        }
    }

    private fun onIncoming(chatKey: String, title: String, text: String, chatName: String) {
        if (appVisible && activeChat == chatKey) return
        _unread.update { it + (chatKey to ((it[chatKey] ?: 0) + 1)) }
        notifier?.notify(title, text, chatKey, chatName)
    }

    private fun showTyping(userId: String) {
        _typingPeer.value = userId
        typingJob?.cancel()
        typingJob = scope.launch {
            delay(2_500)
            _typingPeer.value = null
        }
    }

    fun setStatus(text: String) {
        _status.value = text
        statusJob?.cancel()
        statusJob = scope.launch {
            delay(6_000)
            if (_status.value == text) _status.value = null
        }
    }

    fun clearStatus() {
        _status.value = null
    }

    companion object {
        const val PUBLIC_KEY = "public"
        const val DEFAULT_NAME = "کاربر AtiChat"
        const val OFFLINE_MESSAGE = "این کاربر دیگر آنلاین نیست."
    }
}
