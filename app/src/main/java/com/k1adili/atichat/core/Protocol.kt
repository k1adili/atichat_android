package com.k1adili.atichat.core

import java.io.File
import java.time.Instant
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

/** A user discovered on the LAN through a UDP presence packet. */
data class Peer(
    val userId: String,
    val username: String,
    val avatar: String,
    val ip: String,
    val tcpPort: Int,
    val fingerprint: String,
    val lastSeenNanos: Long,
)

/** Events produced by [LanNode]. Delivered on network threads. */
sealed interface NetEvent {
    data class PeerOnline(val peer: Peer) : NetEvent
    data class PeerUpdated(val peer: Peer) : NetEvent
    data class PeerOffline(val peer: Peer) : NetEvent
    data class Typing(val userId: String, val username: String) : NetEvent
    data class PublicMessage(
        val userId: String,
        val username: String,
        val messageId: String,
        val timestamp: String,
        val body: String,
    ) : NetEvent

    data class DirectMessage(
        val userId: String,
        val username: String,
        val messageId: String,
        val timestamp: String,
        val body: String,
        val ip: String,
    ) : NetEvent

    data class FileReceived(
        val userId: String,
        val username: String,
        val messageId: String,
        val timestamp: String,
        /** Display name the sender used (sanitised). */
        val displayName: String,
        /** Name of the stored file inside the downloads directory. */
        val storedName: String,
        val file: File,
        val size: Long,
        val ip: String,
    ) : NetEvent

    data class FileSent(
        val peerId: String,
        val peerName: String,
        val messageId: String,
        val timestamp: String,
        val fileName: String,
    ) : NetEvent

    data class Failure(
        val message: String,
        val messageId: String? = null,
        val peerId: String? = null,
        /** "file", "direct_message" or null for general network errors. */
        val transferType: String? = null,
    ) : NetEvent
}

object Protocol {
    // Must match the Windows app (network.py).
    const val UDP_PORT = 37020
    const val TCP_PORT = 37021
    const val HEARTBEAT_SECONDS = 3L
    const val PEER_TIMEOUT_SECONDS = 10L
    const val MAX_UDP_PACKET = 60_000
    const val MAX_TCP_HEADER = 16_384
    const val MAX_MESSAGE_LENGTH = 12_000
    const val MAX_FILE_SIZE = 100L * 1024 * 1024
    const val FILE_CHUNK_SIZE = 64 * 1024
    const val MAX_AVATAR_LENGTH = 16_000
    const val MAX_NAME_LENGTH = 64
    const val TRANSFER_ACK: Int = 0x06
    const val TLS_PEER_NAME = "K1chat LAN peer"

    /** Longest stored file name in UTF-8 bytes (Android file systems cap names at 255 bytes). */
    const val MAX_STORED_NAME_BYTES = 200

    private val FINGERPRINT = Regex("[a-f0-9]{64}")
    private val UNSAFE_NAME_CHARS = Regex("[<>:\"/\\\\|?*\\u0000-\\u001f]")
    private val TIMESTAMP_FORMAT: DateTimeFormatter =
        DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSSSS").withLocale(java.util.Locale.ROOT).withZone(ZoneOffset.UTC)

    /** Same shape as Python's `datetime.now(utc).isoformat(timespec="microseconds")`. */
    fun nowTimestamp(): String = TIMESTAMP_FORMAT.format(Instant.now()) + "+00:00"

    fun parseTimestamp(text: String): Instant = try {
        OffsetDateTime.parse(text).toInstant()
    } catch (_: DateTimeParseException) {
        try {
            LocalDateTime.parse(text).toInstant(ZoneOffset.UTC)
        } catch (_: DateTimeParseException) {
            Instant.now()
        }
    }

    fun limitCodePoints(text: String, maxCodePoints: Int): String {
        if (text.codePointCount(0, text.length) <= maxCodePoints) return text
        return text.substring(0, text.offsetByCodePoints(0, maxCodePoints))
    }

    fun sha256Hex(data: ByteArray): String =
        java.security.MessageDigest.getInstance("SHA-256").digest(data)
            .joinToString("") { "%02x".format(it.toInt() and 0xff) }

    fun presencePacket(
        userId: String,
        username: String,
        avatar: String,
        tcpPort: Int,
        fingerprint: String,
    ): Map<String, Any?> = linkedMapOf(
        "type" to "presence",
        "user_id" to userId,
        "username" to limitCodePoints(username, MAX_NAME_LENGTH),
        "avatar" to avatar.take(MAX_AVATAR_LENGTH),
        "tcp_port" to tcpPort,
        "fingerprint" to fingerprint,
    )

    /** Validates a presence packet exactly like the Windows app does; null when invalid. */
    fun parsePresence(packet: Map<String, Any?>, ip: String, nowNanos: Long): Peer? {
        val userId = packet["user_id"] as? String ?: return null
        val username = packet["username"] as? String
        val tcpPort = packet["tcp_port"] as? Long
        val fingerprint = packet["fingerprint"] as? String
        val avatarValue = if (packet.containsKey("avatar")) packet["avatar"] else ""
        val avatar = avatarValue as? String ?: return null
        if (username == null || username.isBlank()) return null
        if (tcpPort == null || tcpPort !in 1L..65535L) return null
        if (fingerprint == null || !FINGERPRINT.matches(fingerprint)) return null
        if (avatar.length > MAX_AVATAR_LENGTH) return null
        return Peer(
            userId = userId,
            username = limitCodePoints(username, MAX_NAME_LENGTH),
            avatar = avatar,
            ip = ip,
            tcpPort = tcpPort.toInt(),
            fingerprint = fingerprint,
            lastSeenNanos = nowNanos,
        )
    }

    /**
     * Mirrors the Windows sanitiser (strip directories, replace reserved characters) and additionally
     * keeps the name within [MAX_STORED_NAME_BYTES] UTF-8 bytes while preserving the extension.
     */
    fun sanitizeFileName(filename: String): String {
        val trimmed = filename.trimEnd('/', '\\')
        val base = trimmed.substringAfterLast('/').substringAfterLast('\\')
        var safe = UNSAFE_NAME_CHARS.replace(base, "_").trim(' ', '.')
        if (safe.isEmpty()) safe = "received-file"
        return truncateUtf8(safe, MAX_STORED_NAME_BYTES)
    }

    fun truncateUtf8(name: String, maxBytes: Int): String {
        if (name.toByteArray(Charsets.UTF_8).size <= maxBytes) return name
        val dot = name.lastIndexOf('.')
        val extension = if (dot > 0 && name.length - dot <= 16) name.substring(dot) else ""
        var stem = if (extension.isEmpty()) name else name.substring(0, dot)
        val budget = maxBytes - extension.toByteArray(Charsets.UTF_8).size
        while (stem.isNotEmpty() && stem.toByteArray(Charsets.UTF_8).size > budget) {
            stem = stem.substring(0, stem.offsetByCodePoints(stem.length, -1))
        }
        return (stem + extension).ifEmpty { "received-file" }
    }
}
