package media.alexlab.fludremote

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder

class BridgeService : Service() {
    companion object {
        const val PORT = 8765
        const val ACTION_STOP = "media.alexlab.fludremote.STOP"
        const val CHANNEL_ID = "flud_remote_bridge"
        const val NOTIFICATION_ID = 1001

        @Volatile
        var isRunning: Boolean = false
            private set
    }

    private var server: BridgeHttpServer? = null
    private var overlay: OverlayController? = null
    private var cloudRelay: CloudRelayClient? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification())

        overlay = OverlayController(applicationContext).also { it.startIfAllowed() }
        val httpServer = BridgeHttpServer(
            context = applicationContext,
            port = PORT,
            tokenProvider = { BridgePreferences.token(applicationContext) }
        )
        if (httpServer.start()) {
            server = httpServer
            isRunning = true
            cloudRelay = CloudRelayClient(applicationContext).also { it.start() }
        } else {
            isRunning = false
            stopSelf()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        return START_STICKY
    }

    override fun onDestroy() {
        isRunning = false
        cloudRelay?.stop()
        cloudRelay = null
        server?.stop()
        server = null
        overlay?.stop()
        overlay = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Flud Companion",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Keeps Flud Companion available over LAN and the user-owned Remote relay"
            }
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        val openIntent = Intent(this, MainActivity::class.java)
        val openPendingIntent = PendingIntent.getActivity(
            this,
            0,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, BridgeService::class.java).apply { action = ACTION_STOP }
        val stopPendingIntent = PendingIntent.getService(
            this,
            1,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }

        return builder
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle("Flud Companion is running")
            .setContentText("LAN :$PORT + user-owned Remote relay")
            .setContentIntent(openPendingIntent)
            .setOngoing(true)
            .addAction(
                Notification.Action.Builder(
                    android.R.drawable.ic_menu_close_clear_cancel,
                    "Stop",
                    stopPendingIntent
                ).build()
            )
            .build()
    }
}
