package com.ujwal.focusgram.feature.screentime

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import android.content.pm.ServiceInfo
import com.ujwal.focusgram.FocusGramApp
import com.ujwal.focusgram.MainActivity
import com.ujwal.focusgram.R
import com.ujwal.focusgram.core.database.ScreenTimeRecord
import com.ujwal.focusgram.core.database.SessionRecord
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Foreground service that tracks active session duration, updates the persistent notification,
 * and broadcasts [ACTION_LIMIT_EXCEEDED] when daily limits are exhausted.
 */
class ScreenTimeService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.Default)
    private var trackingJob: Job? = null

    private var sessionStartTime = 0L
    private var sessionSeconds = 0
    private var sessionDurationMinutes = 15
    private var sessionType = "INSTAGRAM" // "INSTAGRAM" or "REELS"
    private var isReelsSession = false

    private fun getSessionSubtitle(isPaused: Boolean = false): String {
        val durationPart = if (sessionDurationMinutes > 0) "${sessionDurationMinutes}m" else "Stay intentional"
        return if (isPaused) {
            "Paused outside Instagram • $durationPart"
        } else {
            "Session in progress • $durationPart"
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        createIntervalNotificationChannel()
        val minutes = sessionSeconds / 60
        val secs = sessionSeconds % 60
        val timeStr = String.format("%02d:%02d", minutes, secs)
        val initialTitle = "Session Timer: $timeStr"
        val initialNotification = buildNotification(initialTitle, getSessionSubtitle(false))
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                ServiceCompat.startForeground(
                    this,
                    NOTIFICATION_ID,
                    initialNotification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                )
            } else {
                startForeground(NOTIFICATION_ID, initialNotification)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Primary startForeground failed in onCreate: ${e.message}", e)
            try {
                startForeground(NOTIFICATION_ID, initialNotification)
            } catch (fallbackEx: Exception) {
                Log.e(TAG, "Fallback startForeground failed: ${fallbackEx.message}", fallbackEx)
                stopSelf()
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                isReelsSession = intent.getBooleanExtra(EXTRA_IS_REELS, false)
                val initSecs = intent.getIntExtra(EXTRA_INITIAL_SECONDS, 0)
                if (initSecs > 0) {
                    sessionSeconds = initSecs
                }
                val durMins = intent.getIntExtra(EXTRA_SESSION_DURATION_MINUTES, 0)
                if (durMins > 0) {
                    sessionDurationMinutes = durMins
                }
                sessionType = if (isReelsSession) "REELS" else "INSTAGRAM"
                startTracking()
            }
            ACTION_UPDATE_ROUTE -> {
                val wasReels = isReelsSession
                isReelsSession = intent.getBooleanExtra(EXTRA_IS_REELS, false)
                sessionType = if (isReelsSession) "REELS" else "INSTAGRAM"
                if (wasReels != isReelsSession && trackingJob?.isActive == true) {
                    updateSessionNotification()
                }
            }
            ACTION_PAUSE -> {
                val initSecs = intent.getIntExtra(EXTRA_INITIAL_SECONDS, -1)
                if (initSecs >= 0) {
                    sessionSeconds = initSecs
                }
                val durMins = intent.getIntExtra(EXTRA_SESSION_DURATION_MINUTES, 0)
                if (durMins > 0) {
                    sessionDurationMinutes = durMins
                }
                pauseTracking()
            }
            ACTION_RESUME -> {
                val initSecs = intent.getIntExtra(EXTRA_INITIAL_SECONDS, 0)
                if (initSecs > 0) {
                    sessionSeconds = initSecs
                }
                val durMins = intent.getIntExtra(EXTRA_SESSION_DURATION_MINUTES, 0)
                if (durMins > 0) {
                    sessionDurationMinutes = durMins
                }
                startTracking()
            }
            ACTION_STOP -> {
                stopTracking()
                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                        stopForeground(STOP_FOREGROUND_REMOVE)
                    } else {
                        @Suppress("DEPRECATION")
                        stopForeground(true)
                    }
                } catch (_: Exception) {}
                try {
                    val manager = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                    manager?.cancel(NOTIFICATION_ID)
                } catch (_: Exception) {}
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        stopTracking()
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                stopForeground(STOP_FOREGROUND_REMOVE)
            } else {
                @Suppress("DEPRECATION")
                stopForeground(true)
            }
        } catch (_: Exception) {}
        try {
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            manager?.cancel(NOTIFICATION_ID)
        } catch (_: Exception) {}
    }

    private fun startTracking() {
        if (trackingJob?.isActive == true) return

        sessionStartTime = System.currentTimeMillis() - (sessionSeconds * 1000L)
        updateSessionNotification()

        trackingJob = serviceScope.launch {
            val app = FocusGramApp.instance
            val settingsRepo = app.settingsRepository

            while (isActive) {
                delay(1000L)
                sessionSeconds++
                updateSessionNotification()

                // Check 5-minute interval alert
                if (sessionSeconds > 0 && sessionSeconds % 300 == 0) {
                    try {
                        val settings = settingsRepo.settingsFlow.first()
                        if (settings.notifyEvery5Minutes) {
                            sendIntervalAlert(sessionSeconds / 60)
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Error checking interval notification: ${e.message}")
                    }
                }
            }
        }
    }

    private fun updateSessionNotification() {
        val minutes = sessionSeconds / 60
        val secs = sessionSeconds % 60
        val timeStr = String.format("%02d:%02d", minutes, secs)
        val title = "Session Timer: $timeStr"
        updateNotification(title, getSessionSubtitle(false))
    }

    private fun pauseTracking() {
        trackingJob?.cancel()
        trackingJob = null
        val minutes = sessionSeconds / 60
        val secs = sessionSeconds % 60
        val timeStr = String.format("%02d:%02d", minutes, secs)
        updateNotification("Session Timer: $timeStr (Paused)", getSessionSubtitle(true))
    }

    private fun stopTracking() {
        trackingJob?.cancel()
        trackingJob = null

        val endTime = System.currentTimeMillis()
        val duration = ((endTime - sessionStartTime) / 1000).toInt()

        if (duration > 5) {
            serviceScope.launch {
                try {
                    val app = FocusGramApp.instance
                    app.database.sessionRecordDao().insert(
                        SessionRecord(
                            sessionType = sessionType,
                            startTime = sessionStartTime,
                            endTime = endTime,
                            durationSeconds = duration,
                            completed = true
                        )
                    )
                } catch (e: Exception) {
                    Log.e(TAG, "Error saving session record on stop: ${e.message}", e)
                }
            }
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "FocusGram Session Timer",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows active Instagram usage session duration and limits."
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun createIntervalNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_INTERVAL_ID,
                "5-Minute Session Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Periodic heads-up alerts every 5 minutes during an active session."
                enableLights(true)
                enableVibration(true)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun sendIntervalAlert(minutesElapsed: Int) {
        try {
            val launchIntent = Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
            val pendingIntent = PendingIntent.getActivity(
                this,
                minutesElapsed,
                launchIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val alert = NotificationCompat.Builder(this, CHANNEL_INTERVAL_ID)
                .setContentTitle("Session Alert: $minutesElapsed min")
                .setContentText("You've been using Instagram for $minutesElapsed minutes. Stay mindful.")
                .setSmallIcon(R.drawable.ic_notification)
                .setContentIntent(pendingIntent)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setDefaults(NotificationCompat.DEFAULT_ALL)
                .setAutoCancel(true)
                .build()

            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.notify(INTERVAL_NOTIFICATION_BASE_ID + minutesElapsed, alert)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to send 5-minute interval notification: ${e.message}")
        }
    }

    private fun buildNotification(title: String, content: String): Notification {
        val launchIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(content)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setAutoCancel(false)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
        notification.flags = notification.flags or Notification.FLAG_ONGOING_EVENT or Notification.FLAG_NO_CLEAR
        return notification
    }

    private fun updateNotification(title: String, content: String) {
        try {
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.notify(NOTIFICATION_ID, buildNotification(title, content))
        } catch (e: Exception) {
            Log.w(TAG, "Failed to update notification: ${e.message}")
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val TAG = "ScreenTimeService"
        const val CHANNEL_ID = "focusgram_session_timer"
        const val CHANNEL_INTERVAL_ID = "focusgram_interval_alerts"
        const val NOTIFICATION_ID = 1001
        const val INTERVAL_NOTIFICATION_BASE_ID = 2000

        const val ACTION_START = "com.ujwal.focusgram.action.START_SESSION"
        const val ACTION_PAUSE = "com.ujwal.focusgram.action.PAUSE_SESSION"
        const val ACTION_RESUME = "com.ujwal.focusgram.action.RESUME_SESSION"
        const val ACTION_STOP = "com.ujwal.focusgram.action.STOP_SESSION"
        const val ACTION_UPDATE_ROUTE = "com.ujwal.focusgram.action.UPDATE_ROUTE"
        const val ACTION_LIMIT_EXCEEDED = "com.ujwal.focusgram.action.LIMIT_EXCEEDED"
        const val EXTRA_IS_REELS = "extra_is_reels"
        const val EXTRA_INITIAL_SECONDS = "extra_initial_seconds"
        const val EXTRA_SESSION_DURATION_MINUTES = "extra_session_duration_minutes"

        fun startSession(context: Context, isReels: Boolean = false, initialSeconds: Int = 0, sessionDurationMinutes: Int = 15) {
            try {
                val intent = Intent(context, ScreenTimeService::class.java).apply {
                    action = ACTION_START
                    putExtra(EXTRA_IS_REELS, isReels)
                    putExtra(EXTRA_INITIAL_SECONDS, initialSeconds)
                    putExtra(EXTRA_SESSION_DURATION_MINUTES, sessionDurationMinutes)
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to startSession: ${e.message}", e)
            }
        }

        fun pauseSession(context: Context, elapsedSeconds: Int = -1, sessionDurationMinutes: Int = 0) {
            try {
                val intent = Intent(context, ScreenTimeService::class.java).apply {
                    action = ACTION_PAUSE
                    if (elapsedSeconds >= 0) {
                        putExtra(EXTRA_INITIAL_SECONDS, elapsedSeconds)
                    }
                    if (sessionDurationMinutes > 0) {
                        putExtra(EXTRA_SESSION_DURATION_MINUTES, sessionDurationMinutes)
                    }
                }
                context.startService(intent)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to pause ScreenTimeService: ${e.message}")
            }
        }

        fun resumeSession(context: Context, elapsedSeconds: Int = 0, sessionDurationMinutes: Int = 0) {
            try {
                val intent = Intent(context, ScreenTimeService::class.java).apply {
                    action = ACTION_RESUME
                    putExtra(EXTRA_INITIAL_SECONDS, elapsedSeconds)
                    if (sessionDurationMinutes > 0) {
                        putExtra(EXTRA_SESSION_DURATION_MINUTES, sessionDurationMinutes)
                    }
                }
                context.startService(intent)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to resume ScreenTimeService: ${e.message}")
            }
        }

        fun updateRoute(context: Context, isReels: Boolean) {
            val intent = Intent(context, ScreenTimeService::class.java).apply {
                action = ACTION_UPDATE_ROUTE
                putExtra(EXTRA_IS_REELS, isReels)
            }
            try {
                context.startService(intent)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to send route update to ScreenTimeService: ${e.message}")
            }
        }

        fun stopSession(context: Context) {
            try {
                val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                manager?.cancel(NOTIFICATION_ID)
            } catch (_: Exception) {}
            val intent = Intent(context, ScreenTimeService::class.java).apply {
                action = ACTION_STOP
            }
            try {
                context.startService(intent)
            } catch (_: Exception) {
                try {
                    context.stopService(Intent(context, ScreenTimeService::class.java))
                } catch (_: Exception) {}
            }
        }
    }
}
