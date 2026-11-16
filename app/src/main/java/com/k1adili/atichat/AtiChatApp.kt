package com.k1adili.atichat

import android.app.Application
import com.k1adili.atichat.data.AppDatabase
import com.k1adili.atichat.data.ChatEngine
import com.k1adili.atichat.service.Notifications

class AtiChatApp : Application() {
    val database: AppDatabase by lazy { AppDatabase.build(this) }

    val engine: ChatEngine by lazy {
        ChatEngine(this, database).also { engine ->
            engine.notifier = com.k1adili.atichat.data.IncomingNotifier { title, text, chatKey, chatName ->
                Notifications.showMessage(this, title, text, chatKey, chatName)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        Notifications.createChannels(this)
    }
}
