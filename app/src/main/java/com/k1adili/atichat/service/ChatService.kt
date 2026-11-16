package com.k1adili.atichat.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.os.IBinder
import androidx.core.app.ServiceCompat
import com.k1adili.atichat.AtiChatApp

/**
 * Keeps AtiChat online in the background (the Windows app hides to the tray and stays online).
 * Holds a multicast lock so Wi-Fi delivers UDP broadcasts while the screen is off.
 */
class ChatService : Service() {
    private var multicastLock: WifiManager.MulticastLock? = null
    private var wifiLock: WifiManager.WifiLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            shutdown()
            return START_NOT_STICKY
        }
        val type = if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0
        ServiceCompat.startForeground(this, Notifications.PRESENCE_ID, Notifications.presence(this), type)
        acquireLocks()
        (application as AtiChatApp).engine.start()
        return START_STICKY
    }

    private fun acquireLocks() {
        val wifi = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        if (multicastLock == null) {
            multicastLock = wifi.createMulticastLock("atichat-multicast").apply {
                setReferenceCounted(false)
                acquire()
            }
        }
        if (wifiLock == null) {
            @Suppress("DEPRECATION")
            val mode = if (Build.VERSION.SDK_INT >= 29) WifiManager.WIFI_MODE_FULL_LOW_LATENCY
            else WifiManager.WIFI_MODE_FULL_HIGH_PERF
            wifiLock = wifi.createWifiLock(mode, "atichat-wifi").apply {
                setReferenceCounted(false)
                acquire()
            }
        }
    }

    private fun releaseLocks() {
        multicastLock?.takeIf { it.isHeld }?.release()
        wifiLock?.takeIf { it.isHeld }?.release()
        multicastLock = null
        wifiLock = null
    }

    private fun shutdown() {
        (application as AtiChatApp).engine.stop()
        releaseLocks()
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        releaseLocks()
        super.onDestroy()
    }

    companion object {
        const val ACTION_STOP = "com.k1adili.atichat.action.STOP"
    }
}
