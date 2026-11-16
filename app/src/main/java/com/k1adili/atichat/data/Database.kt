package com.k1adili.atichat.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "profile")
data class ProfileEntity(
    @PrimaryKey val singleton: Int = 1,
    val userId: String,
    val displayName: String,
    val avatar: String,
)

/** kind is "public", "private" or "file". [fileName] holds only the stored file name (not a path) so backups survive restores. */
@Entity(
    tableName = "messages",
    indices = [Index(value = ["messageId"], unique = true), Index(value = ["kind", "id"])],
)
data class MessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val messageId: String,
    val kind: String,
    val senderId: String,
    val senderName: String,
    val recipientId: String = "",
    val recipientName: String = "",
    val body: String = "",
    val fileName: String = "",
    val timestamp: String,
    val outgoing: Boolean,
)

data class ContactRow(val userId: String, val username: String, val lastId: Long)

@Dao
interface ChatDao {
    @Query("SELECT * FROM profile WHERE singleton = 1")
    fun observeProfile(): Flow<ProfileEntity?>

    @Query("SELECT * FROM profile WHERE singleton = 1")
    suspend fun getProfile(): ProfileEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveProfile(profile: ProfileEntity)

    /** Returns the new row id, or -1 when the message id already exists (duplicate delivery). */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertMessage(message: MessageEntity): Long

    @Query("DELETE FROM messages WHERE messageId = :messageId")
    suspend fun deleteMessage(messageId: String): Int

    @Query("DELETE FROM messages WHERE kind = 'public'")
    suspend fun deletePublic(): Int

    @Query(
        """
        DELETE FROM messages
        WHERE kind IN ('private', 'file')
          AND ((senderId = :me AND recipientId = :peer) OR (senderId = :peer AND recipientId = :me))
        """,
    )
    suspend fun deletePrivate(me: String, peer: String): Int

    @Query("DELETE FROM messages")
    suspend fun deleteAll(): Int

    @Query("SELECT * FROM (SELECT * FROM messages WHERE kind = 'public' ORDER BY id DESC LIMIT 500) ORDER BY id ASC")
    fun observePublic(): Flow<List<MessageEntity>>

    @Query(
        """
        SELECT * FROM (
            SELECT * FROM messages
            WHERE kind IN ('private', 'file')
              AND ((senderId = :me AND recipientId = :peer) OR (senderId = :peer AND recipientId = :me))
            ORDER BY id DESC LIMIT 500
        ) ORDER BY id ASC
        """,
    )
    fun observePrivate(me: String, peer: String): Flow<List<MessageEntity>>

    @Query(
        """
        SELECT peerId AS userId, peerName AS username, MAX(id) AS lastId FROM (
            SELECT id,
                   CASE WHEN senderId = :me THEN recipientId ELSE senderId END AS peerId,
                   CASE WHEN senderId = :me THEN recipientName ELSE senderName END AS peerName
            FROM messages
            WHERE kind IN ('private', 'file') AND (senderId = :me OR recipientId = :me)
        )
        WHERE peerId != ''
        GROUP BY peerId
        ORDER BY lastId DESC
        """,
    )
    fun observeContacts(me: String): Flow<List<ContactRow>>

    @Query("SELECT * FROM messages ORDER BY id ASC")
    suspend fun allMessages(): List<MessageEntity>
}

@Database(entities = [ProfileEntity::class, MessageEntity::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun dao(): ChatDao

    companion object {
        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, "atichat.db").build()
    }
}
