package com.k1adili.atichat.data

import com.k1adili.atichat.core.Json

/** Portable JSON backup of the profile and message history (received files themselves are not included). */
object BackupManager {
    const val FORMAT_VERSION = 1
    private val KINDS = setOf("public", "private", "file")

    data class Backup(val profile: ProfileEntity?, val messages: List<MessageEntity>)

    fun toJson(profile: ProfileEntity, messages: List<MessageEntity>, exportedAt: String): String =
        Json.encode(
            linkedMapOf(
                "app" to "AtiChat",
                "format" to FORMAT_VERSION,
                "exported_at" to exportedAt,
                "profile" to linkedMapOf(
                    "user_id" to profile.userId,
                    "display_name" to profile.displayName,
                    "avatar" to profile.avatar,
                ),
                "messages" to messages.map { m ->
                    linkedMapOf(
                        "message_id" to m.messageId,
                        "kind" to m.kind,
                        "sender_id" to m.senderId,
                        "sender_name" to m.senderName,
                        "recipient_id" to m.recipientId,
                        "recipient_name" to m.recipientName,
                        "body" to m.body,
                        "file_name" to m.fileName,
                        "timestamp" to m.timestamp,
                        "outgoing" to m.outgoing,
                    )
                },
            ),
        )

    /** Throws IllegalArgumentException if [text] is not an AtiChat backup. */
    fun parse(text: String): Backup {
        val root = Json.parseObject(text)
        require(root["app"] == "AtiChat") { "not an AtiChat backup" }
        val profileMap = root["profile"] as? Map<*, *>
        val profile = profileMap?.let {
            val userId = it["user_id"] as? String
            val name = it["display_name"] as? String
            if (userId.isNullOrBlank() || name.isNullOrBlank()) null
            else ProfileEntity(userId = userId, displayName = name, avatar = it["avatar"] as? String ?: "")
        }
        val messages = (root["messages"] as? List<*>).orEmpty().mapNotNull { item ->
            val m = item as? Map<*, *> ?: return@mapNotNull null
            val kind = m["kind"] as? String ?: return@mapNotNull null
            if (kind !in KINDS) return@mapNotNull null
            MessageEntity(
                messageId = m["message_id"] as? String ?: return@mapNotNull null,
                kind = kind,
                senderId = m["sender_id"] as? String ?: return@mapNotNull null,
                senderName = m["sender_name"] as? String ?: "",
                recipientId = m["recipient_id"] as? String ?: "",
                recipientName = m["recipient_name"] as? String ?: "",
                body = m["body"] as? String ?: "",
                fileName = m["file_name"] as? String ?: "",
                timestamp = m["timestamp"] as? String ?: return@mapNotNull null,
                outgoing = m["outgoing"] as? Boolean ?: false,
            )
        }
        return Backup(profile, messages)
    }
}
