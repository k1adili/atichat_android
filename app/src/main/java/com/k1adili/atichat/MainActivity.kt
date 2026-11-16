package com.k1adili.atichat

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import com.k1adili.atichat.service.ChatService
import com.k1adili.atichat.service.Notifications
import com.k1adili.atichat.ui.AtiChatRoot
import com.k1adili.atichat.ui.theme.AtiChatTheme

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()

    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* the service works either way */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        ContextCompat.startForegroundService(this, Intent(this, ChatService::class.java))

        if (savedInstanceState == null) handleIntent(intent)
        setContent {
            AtiChatTheme {
                AtiChatRoot(viewModel = viewModel, onExit = ::exitCompletely)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    override fun onStart() {
        super.onStart()
        val engine = viewModel.engine
        engine.appVisible = true
        // Messages that arrived while the app was hidden are already visible in the open chat.
        engine.activeChat?.let { engine.clearUnread(it) }
    }

    override fun onStop() {
        viewModel.engine.appVisible = false
        super.onStop()
    }

    private fun handleIntent(intent: Intent?) {
        val key = intent?.getStringExtra(Notifications.EXTRA_CHAT_KEY) ?: return
        val name = intent.getStringExtra(Notifications.EXTRA_CHAT_NAME) ?: ""
        viewModel.openChatFromNotification(key, name)
    }

    private fun exitCompletely() {
        startService(Intent(this, ChatService::class.java).setAction(ChatService.ACTION_STOP))
        finishAndRemoveTask()
    }
}
