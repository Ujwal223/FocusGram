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
 * ScreenTimeService
 * Android Foreground Service tracking active Instagram usage, updating persistent notifications,
 * draining credit minutes, and triggering session limit gates.
 */
class ScreenTimeService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.Default)
    private var trackingJob: Job? = null

    private var sessionStartTime = 0L
    private var sessionSeconds = 0
    private var sessionType = "INSTAGRAM" // "INSTAGRAM" or "REELS"
    private var isReelsSession = false

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                isReelsSession = intent.getBooleanExtra(EXTRA_IS_REELS, false)
                sessionType = if (isReelsSession) "REELS" else "INSTAGRAM"
                startTracking()
            }
            ACTION_UPDATE_ROUTE -> {
                val wasReels = isReelsSession
                isReelsSession = intent.getBooleanExtra(EXTRA_IS_REELS, false)
                sessionType = if (isReelsSession) "REELS" else "INSTAGRAM"
                if (wasReels != isReelsSession && trackingJob?.isActive == true) {
                    val minutes = sessionSeconds / 60
                    val secs = sessionSeconds % 60
                    val timeStr = String.format("%02d:%02d", minutes, secs)
                    val title = if (isReelsSession) "Reels Focus: $timeStr" else "Instagram Focus: $timeStr"
                    updateNotification(title, "Discipline active. Stay intentional.")
                }
            }
            ACTION_STOP -> {
                stopTracking()
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    private fun startTracking() {
        if (trackingJob?.isActive == true) return

        sessionStartTime = System.currentTimeMillis()
        sessionSeconds = 0

        val initialNotification = buildNotification("Focus Session Active", "Tracking active session time...")
        startForeground(NOTIFICATION_ID, initialNotification)

        trackingJob = serviceScope.launch {
            val app = FocusGramApp.instance
            val db = app.database
            val settingsRepo = app.settingsRepository
            val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)

            var secondsAccumulator = 0

            while (isActive) {
                delay(1000L)
                sessionSeconds++
                secondsAccumulator++

                // Update notification every 5 seconds
                if (sessionSeconds % 5 == 0) {
                    val minutes = sessionSeconds / 60
                    val secs = sessionSeconds % 60
                    val timeStr = String.format("%02d:%02d", minutes, secs)
                    val title = if (isReelsSession) "Reels Focus: $timeStr" else "Instagram Focus: $timeStr"
                    updateNotification(title, "Discipline active. Stay intentional.")
                }

                // Every 60 seconds: Drain credits & check daily limits
                if (secondsAccumulator >= 60) {
                    // Drain credit if applicable
                    if (isReelsSession) {
                        settingsRepo.deductReelsMinute()
                    } else {
                        settingsRepo.deductInstaMinute()
                    }

                    // Check daily limit
                    val settings = settingsRepo.settingsFlow.first()
                    val today = dateFormat.format(Date())
                    val todayRecord = db.screenTimeDao().getForDateSync(today)
                    val todayTotalSeconds = todayRecord?.seconds ?: 0
                    val dailyLimitSeconds = settings.dailyLimitMinutes * 60

                    if (dailyLimitSeconds in 1..todayTotalSeconds) {
                        Log.i(TAG, "Daily screen time limit reached ($dailyLimitSeconds s). Triggering session limit broadcast.")
                        val limitIntent = Intent(ACTION_LIMIT_EXCEEDED).apply {
                            setPackage(packageName)
                        }
                        sendBroadcast(limitIntent)
                    }

                    secondsAccumulator = 0
                }
            }
        }
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
                    // Insert completed session record
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

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(content)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun updateNotification(title: String, content: String) {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, buildNotification(title, content))
    }

    override fun onDestroy() {
        stopTracking()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val TAG = "ScreenTimeService"
        const val CHANNEL_ID = "focusgram_session_timer"
        const val NOTIFICATION_ID = 1001

        const val ACTION_START = "com.ujwal.focusgram.action.START_SESSION"
        const val ACTION_STOP = "com.ujwal.focusgram.action.STOP_SESSION"
        const val ACTION_UPDATE_ROUTE = "com.ujwal.focusgram.action.UPDATE_ROUTE"
        const val ACTION_LIMIT_EXCEEDED = "com.ujwal.focusgram.action.LIMIT_EXCEEDED"
        const val EXTRA_IS_REELS = "extra_is_reels"

        fun startSession(context: Context, isReels: Boolean = false) {
            val intent = Intent(context, ScreenTimeService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_IS_REELS, isReels)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
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
            val intent = Intent(context, ScreenTimeService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }
}
