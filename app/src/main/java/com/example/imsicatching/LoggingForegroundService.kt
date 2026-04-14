package com.example.imsicatching

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat

class LoggingForegroundService : Service() {
    private lateinit var logger: TelephonyLogger

    override fun onCreate() {
        super.onCreate()
        logger = TelephonyLogger(applicationContext) { line ->
            sendBroadcast(Intent(ACTION_LOG_LINE).putExtra(EXTRA_LINE, line))
        }
        ensureChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                logger.stop()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
            else -> {
                startForeground(NOTIFICATION_ID, notification())
                logger.start()
                sendBroadcast(Intent(ACTION_STATUS).putExtra(EXTRA_RUNNING, true))
                return START_STICKY
            }
        }
    }

    override fun onDestroy() {
        logger.stop()
        sendBroadcast(Intent(ACTION_STATUS).putExtra(EXTRA_RUNNING, false))
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Telephony Logging",
            NotificationManager.IMPORTANCE_LOW
        )
        manager.createNotificationChannel(channel)
    }

    private fun notification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentTitle("IMSI Catching Logger")
            .setContentText("Continuous logging is active")
            .setOngoing(true)
            .build()
    }

    companion object {
        const val ACTION_START = "com.example.imsicatching.action.START"
        const val ACTION_STOP = "com.example.imsicatching.action.STOP"
        const val ACTION_LOG_LINE = "com.example.imsicatching.action.LOG_LINE"
        const val ACTION_STATUS = "com.example.imsicatching.action.STATUS"
        const val EXTRA_LINE = "line"
        const val EXTRA_RUNNING = "running"
        private const val CHANNEL_ID = "telemetry_logger_channel"
        private const val NOTIFICATION_ID = 1001
    }
}
