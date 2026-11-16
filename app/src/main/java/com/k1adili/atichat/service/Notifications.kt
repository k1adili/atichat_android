package com.k1adili.atichat.service

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.k1adili.atichat.MainActivity
import com.k1adili.atichat.R

object Notifications {
    const val CHANNEL_PRESENCE = "presence"
    const val CHANNEL_MESSAGES = "messages"
    const val EXTRA_CHAT_KEY = "chat_key"
    const val EXTRA_CHAT_NAME = "chat_name"
    const val PRESENCE_ID = 1

    fun createChannels(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_PRESENCE, context.getString(R.string.channel_presence), NotificationManager.IMPORTANCE_LOW)
                .apply { setShowBadge(false) },
        )
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_MESSAGES, context.getString(R.string.channel_messages), NotificationManager.IMPORTANCE_HIGH),
        )
    }

    fun presence(context: Context): Notification {
        val open = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val exit = PendingIntent.getService(
            context, 1,
            Intent(context, ChatService::class.java).setAction(ChatService.ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(context, CHANNEL_PRESENCE)
            .setSmallIcon(R.drawable.ic_stat_chat)
            .setContentTitle("AtiChat برخط است")
            .setContentText("کاربران شبکهٔ محلی شما را می‌بینند")
            .setContentIntent(open)
            .addAction(0, "خروج", exit)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    fun showMessage(context: Context, title: String, text: String, chatKey: String, chatName: String) {
        if (Build.VERSION.SDK_INT >= 33 &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        val intent = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            .putExtra(EXTRA_CHAT_KEY, chatKey)
            .putExtra(EXTRA_CHAT_NAME, chatName)
        val pending = PendingIntent.getActivity(
            context, chatKey.hashCode(), intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_MESSAGES)
            .setSmallIcon(R.drawable.ic_stat_chat)
            .setContentTitle("AtiChat — $title")
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(pending)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .build()
        // The permission was verified above on Android 13+, so the lint check for notify() is satisfied.
        @Suppress("MissingPermission")
        NotificationManagerCompat.from(context).notify(chatKey.hashCode(), notification)
    }
}
