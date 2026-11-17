package com.k1adili.atichat

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.core.content.ContextCompat
import androidx.core.content.IntentCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.k1adili.atichat.service.ChatService
import com.k1adili.atichat.service.Notifications
import com.k1adili.atichat.ui.AtiChatRoot
import com.k1adili.atichat.ui.theme.AtiChatTheme
import com.k1adili.atichat.util.ThemeMode

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()

    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* the service works either way */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        ContextCompat.startForegroundService(this, Intent(this, ChatService::class.java))

        if (savedInstanceState == null) handleIntent(intent)
        setContent {
            val mode by viewModel.themeMode.collectAsStateWithLifecycle()
            val dark = when (mode) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }
            // Keep status/navigation bar icons readable for the theme chosen inside the app.
            DisposableEffect(dark) {
                enableEdgeToEdge(
                    statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { dark },
                    navigationBarStyle = SystemBarStyle.auto(
                        Color.argb(0xe6, 0xff, 0xff, 0xff),
                        Color.argb(0x80, 0x1b, 0x1b, 0x1b),
                    ) { dark },
                )
                onDispose {}
            }
            AtiChatTheme(darkTheme = dark) {
                AtiChatRoot(viewModel = viewModel, darkTheme = dark, onExit = ::exitCompletely)
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
        when (intent?.action) {
            Intent.ACTION_SEND -> {
                val uri = IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
                viewModel.startShare(listOfNotNull(uri), intent.getStringExtra(Intent.EXTRA_TEXT))
                return
            }
            Intent.ACTION_SEND_MULTIPLE -> {
                val uris = IntentCompat.getParcelableArrayListExtra(intent, Intent.EXTRA_STREAM, Uri::class.java).orEmpty()
                viewModel.startShare(uris, intent.getStringExtra(Intent.EXTRA_TEXT))
                return
            }
        }
        val key = intent?.getStringExtra(Notifications.EXTRA_CHAT_KEY) ?: return
        val name = intent.getStringExtra(Notifications.EXTRA_CHAT_NAME) ?: ""
        viewModel.openChatFromNotification(key, name)
    }

    private fun exitCompletely() {
        startService(Intent(this, ChatService::class.java).setAction(ChatService.ACTION_STOP))
        finishAndRemoveTask()
    }
}
