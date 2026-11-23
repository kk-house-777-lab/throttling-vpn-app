package okoge.house.throttling_app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.VpnService
import android.os.Build
import android.util.Log

class ThrottleVpnService : VpnService() {

    internal var tunnelEngine: TunnelEngine = GoTunnelEngine
    private val holder get() = (application as ThrottlingApplication).vpnStatusHolder
    private val tunnel by lazy { VpnTunnel(holder, tunnelEngine) }

    companion object {
        private const val TAG = "ThrottleVpnService"
        const val NOTIFICATION_CHANNEL_ID = "throttle_vpn"
        const val NOTIFICATION_ID = 1
        const val ACTION_STOP = "okoge.house.throttling_app.STOP_VPN"
        const val ACTION_SET_SPEED = "okoge.house.throttling_app.SET_SPEED"
        const val EXTRA_SPEED_KBPS = "speed_kbps"
        const val EXTRA_TARGET_APPS = "target_apps"
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent == null) {
            // System re-created the service after process kill; stop since there's no valid config.
            stopSelf()
            return START_NOT_STICKY
        }
        when (intent.action) {
            ACTION_STOP -> {
                stopVpn()
                return START_NOT_STICKY
            }
            ACTION_SET_SPEED -> {
                // VPN が動いていないのに速度変更だけで起こされた場合、フォアグラウンドでない Service を残さない
                if (holder.status !is VpnStatus.Running) {
                    stopSelf()
                    return START_NOT_STICKY
                }
                val speedKbps = intent.getIntExtra(EXTRA_SPEED_KBPS, 0)
                Log.i(TAG, "Changing speed to $speedKbps KB/s")
                tunnel.setSpeed(speedKbps)
                updateNotification(speedKbps)
                return START_STICKY
            }
        }
        val speedKbps = intent.getIntExtra(EXTRA_SPEED_KBPS, 0)
        // すでに動作中なら開始は何もしないので、通知も実際に動いている速度で出す
        val notification = buildNotification((holder.status as? VpnStatus.Running)?.speedKBps ?: speedKbps)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SYSTEM_EXEMPTED)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
        val targetApps = intent.getStringArrayListExtra(EXTRA_TARGET_APPS) ?: arrayListOf()
        Log.i(TAG, "Starting tun2socks (speed=${speedKbps}KB/s, apps=${targetApps.size})")
        tunnel.start(speedKbps) { establishTun(targetApps) }
            .onFailure { e ->
                Log.w(TAG, "Failed to start VPN", e)
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
        return START_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        stopVpn()
    }

    override fun onDestroy() {
        stopVpn()
        super.onDestroy()
    }

    private fun establishTun(targetApps: List<String>): Int? {
        val builder = Builder()
            .setSession("ThrottleVPN")
            .addAddress("10.0.0.1", 32)
            .addRoute("0.0.0.0", 0)
            .addDnsServer("8.8.8.8")
            .setMtu(1500)

        for (packageName in targetApps) {
            try {
                builder.addAllowedApplication(packageName)
                Log.i(TAG, "Added target app: $packageName")
            } catch (e: Exception) {
                Log.w(TAG, "Failed to add app: $packageName", e)
            }
        }

        return builder.establish()?.detachFd()
    }

    private fun stopVpn() {
        Log.i(TAG, "Stopping tun2socks")
        tunnel.stop()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            NOTIFICATION_CHANNEL_ID,
            "Throttle VPN",
            NotificationManager.IMPORTANCE_LOW,
        )
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    private fun updateNotification(speedKBps: Int) {
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, buildNotification(speedKBps))
    }

    private fun buildNotification(speedKBps: Int): Notification {
        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, openIntent, PendingIntent.FLAG_IMMUTABLE,
        )

        val modeText = when {
            speedKBps < 0 -> "Mode: Block"
            speedKBps == 0 -> "Mode: Unlimited"
            else -> "Mode: Throttle"
        }

        return Notification.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("Throttle VPN Active")
            .setContentText(modeText)
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }
}
