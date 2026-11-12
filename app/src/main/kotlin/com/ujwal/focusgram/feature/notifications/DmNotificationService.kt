package com.ujwal.focusgram.feature.notifications

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.media.ImageReader
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import android.view.Surface
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.ujwal.focusgram.FocusGramApp
import com.ujwal.focusgram.MainActivity
import com.ujwal.focusgram.R
import com.ujwal.focusgram.core.engine.GeckoRuntimeManager
import com.ujwal.focusgram.core.engine.GeckoSessionManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.mozilla.geckoview.AllowOrDeny
import org.mozilla.geckoview.GeckoDisplay
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoSessionSettings

/**
 * Foreground service managing a headless [GeckoSession] to receive real-time direct
 * message events from Instagram's live web client socket connection.
 *
 * Automatically manages persistent notification visibility:
 * - Hides notification while FocusGram is open in foreground (matches Live Session counter UX).
 * - Surfaces notification instantly when the app is minimized, backgrounded, or closed.
 * - Safely drains graphics buffers on headless SurfaceTexture to prevent compositor deadlocks.
 */
class DmNotificationService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.Main)
    private val mainHandler = Handler(Looper.getMainLooper())
    private var heartbeatJob: Job? = null

    @Volatile
    private var isDestroyed = false

    @Volatile
    private var isAppInForeground = false

    private var session: GeckoSession? = null
    private var isSessionOpen = false
    private var connectivityManager: ConnectivityManager? = null
    private var networkCallback: ConnectivityManager.NetworkCallback? = null

    private var headlessImageReader: ImageReader? = null
    private var headlessSurface: Surface? = null
    private var headlessDisplay: GeckoDisplay? = null
    private var imageThread: android.os.HandlerThread? = null
    private var imageHandler: Handler? = null

    @Volatile
    private var isInitialNetworkCallback = true
    private var serviceStartTime = 0L

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "Creating DmNotificationService foreground listener...")
        isDestroyed = false
        serviceStartTime = System.currentTimeMillis()

        imageThread = android.os.HandlerThread("DmImageConsumer").apply { start() }
        imageHandler = Handler(imageThread!!.looper)

        DmNotificationManager.getInstance(this).createNotificationChannels()

        // Satisfy Android startForeground contract
        val inForeground = DmNotificationManager.getInstance(this).isAppInForeground
        isAppInForeground = inForeground
        showPersistentNotification()

        setupNetworkMonitoring()

        if (!isAppInForeground) {
            startHeadlessSession()
        } else {
            Log.d(TAG, "App is in foreground at service creation; postponing headless session until background.")
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        serviceScope.launch {
            try {
                val settings = FocusGramApp.instance.settingsRepository.settingsFlow.first()
                if (!settings.dmNotificationsEnabled) {
                    Log.d(TAG, "DM notifications disabled in settings; stopping DmNotificationService.")
                    stopForegroundAndRemove()
                    stopSelf()
                }
            } catch (_: Exception) {}
        }

        when (intent?.action) {
            ACTION_STOP -> {
                Log.i(TAG, "Received ACTION_STOP. Terminating DmNotificationService...")
                stopForegroundAndRemove()
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_APP_FOREGROUND -> {
                Log.d(TAG, "App is visible. Stopping DM listener and removing its persistent notification.")
                isAppInForeground = true
                DmNotificationManager.getInstance(this).isAppInForeground = true
                stopHeadlessSession()
                stopForegroundAndRemove()
                stopSelf()
            }
            ACTION_APP_BACKGROUND -> {
                Log.d(TAG, "App transitioned to background. Starting headless GeckoSession for incoming DM monitoring.")
                isAppInForeground = false
                DmNotificationManager.getInstance(this).isAppInForeground = false
                showPersistentNotification()
                if (!isDestroyed) {
                    startHeadlessSession()
                }
            }
            ACTION_RESTORE_NOTIFICATION -> {
                Log.d(TAG, "Notification was dismissed; updating persistent notification status.")
                showPersistentNotification()
            }
            ACTION_RELOAD -> {
                reloadInbox()
            }
            null -> {
                showPersistentNotification()
            }
        }
        return START_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        Log.i(TAG, "App task removed from Recents. Maintaining DmNotificationService in background.")
        isAppInForeground = false
        DmNotificationManager.getInstance(this).isAppInForeground = false
        showPersistentNotification()
        if (!isDestroyed) {
            startHeadlessSession()
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        Log.i(TAG, "Destroying DmNotificationService...")
        isDestroyed = true

        serviceScope.cancel()
        mainHandler.removeCallbacksAndMessages(null)
        unregisterNetworkCallback()

        stopHeadlessSession()

        try {
            imageThread?.quitSafely()
        } catch (_: Exception) {}
        imageThread = null
        imageHandler = null

        super.onDestroy()
    }

    private fun showPersistentNotification() {
        if (isDestroyed) return
        val notification = buildPersistentNotification()
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                ServiceCompat.startForeground(
                    this,
                    NOTIFICATION_ID_SERVICE,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                )
            } else {
                startForeground(NOTIFICATION_ID_SERVICE, notification)
            }
            Log.d(TAG, "Persistent notification displayed in foreground mode.")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to display persistent notification: ${e.message}")
        }
    }

    private fun hidePersistentNotification() {
        try {
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        } catch (_: Exception) {}
        try {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            nm?.cancel(NOTIFICATION_ID_SERVICE)
        } catch (_: Exception) {}
    }

    private fun stopForegroundAndRemove() {
        try {
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        } catch (_: Exception) {}
        try {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            nm?.cancel(NOTIFICATION_ID_SERVICE)
        } catch (_: Exception) {}
    }

    private fun buildPersistentNotification(): Notification {
        val targetUrl = "https://www.instagram.com/direct/inbox/"
        val intent = Intent(this, MainActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            data = Uri.parse(targetUrl)
            putExtra(DmNotificationManager.EXTRA_TARGET_URL, targetUrl)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        val pendingIntent = PendingIntent.getActivity(
            this,
            NOTIFICATION_ID_SERVICE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val deleteIntent = PendingIntent.getService(
            this,
            NOTIFICATION_ID_SERVICE + 1,
            Intent(this, DmNotificationService::class.java).apply {
                action = ACTION_RESTORE_NOTIFICATION
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, DmNotificationManager.CHANNEL_ID_SERVICE)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Direct Messages")
            .setContentText("Checking for new messages in the background")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .setDeleteIntent(deleteIntent)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    private fun initHeadlessSession() {
        serviceScope.launch(Dispatchers.Main) {
            if (isDestroyed) {
                Log.d(TAG, "Service is already destroyed; aborting headless session initialization.")
                return@launch
            }
            try {
                val manager = GeckoRuntimeManager.getInstance(this@DmNotificationService)
                manager.extensionManager.initializeContentBlocker()
                initHeadlessSessionInternal(manager.runtime, manager.extensionManager)
            } catch (e: Exception) {
                Log.e(TAG, "Failed initializing runtime or extension for headless session: ${e.message}", e)
            }
        }
    }

    private fun initHeadlessSessionInternal(
        runtime: org.mozilla.geckoview.GeckoRuntime,
        extensionManager: com.ujwal.focusgram.core.engine.ExtensionManager
    ) {
        if (isDestroyed) return
        try {
            val settings = GeckoSessionSettings.Builder()
                .useTrackingProtection(false)
                .suspendMediaWhenInactive(true)
                .allowJavascript(true)
                .userAgentMode(GeckoSessionSettings.USER_AGENT_MODE_MOBILE)
                .userAgentOverride(GeckoSessionManager.IOS_USER_AGENT)
                .usePrivateMode(false)
                .build()

            val newSession = GeckoSession(settings)

            newSession.progressDelegate = object : GeckoSession.ProgressDelegate {
                override fun onPageStart(session: GeckoSession, url: String) {
                    Log.d(TAG, "Headless DM session started loading")
                }

                override fun onPageStop(session: GeckoSession, success: Boolean) {
                    Log.i(TAG, "Headless DM session page stopped: success=$success")
                }
            }

            newSession.promptDelegate = object : GeckoSession.PromptDelegate {
                override fun onAlertPrompt(session: GeckoSession, prompt: GeckoSession.PromptDelegate.AlertPrompt): GeckoResult<GeckoSession.PromptDelegate.PromptResponse> {
                    return GeckoResult.fromValue(prompt.dismiss())
                }
                override fun onButtonPrompt(session: GeckoSession, prompt: GeckoSession.PromptDelegate.ButtonPrompt): GeckoResult<GeckoSession.PromptDelegate.PromptResponse> {
                    return GeckoResult.fromValue(prompt.dismiss())
                }
            }

            newSession.permissionDelegate = object : GeckoSession.PermissionDelegate {
                override fun onContentPermissionRequest(session: GeckoSession, perm: GeckoSession.PermissionDelegate.ContentPermission): GeckoResult<Int> {
                    return GeckoResult.fromValue(GeckoSession.PermissionDelegate.ContentPermission.VALUE_DENY)
                }
            }

            newSession.navigationDelegate = object : GeckoSession.NavigationDelegate {
                override fun onLoadRequest(session: GeckoSession, request: GeckoSession.NavigationDelegate.LoadRequest): GeckoResult<AllowOrDeny> {
                    val rawUri = request.uri ?: ""
                    val parsed = try { Uri.parse(rawUri) } catch (_: Exception) { null }
                    val host = parsed?.host?.lowercase() ?: ""
                    val scheme = parsed?.scheme?.lowercase() ?: ""
                    val isInstagram = host == "instagram.com" || host.endsWith(".instagram.com")
                    val isSafeScheme = scheme == "about" || scheme == "blob" || scheme == "data"
                    return if (isInstagram || isSafeScheme) {
                        GeckoResult.fromValue(AllowOrDeny.ALLOW)
                    } else {
                        Log.w(TAG, "Blocked external navigation in headless listener")
                        GeckoResult.fromValue(AllowOrDeny.DENY)
                    }
                }

                override fun onLocationChange(
                    session: GeckoSession,
                    url: String?,
                    permissions: List<GeckoSession.PermissionDelegate.ContentPermission>,
                    hasUserGesture: Boolean
                ) {
                    Log.d(TAG, "Headless DM session location changed")
                    if (url?.contains("/accounts/login") == true) {
                        Log.w(TAG, "Headless DM session redirected to login page. Session may need re-authentication.")
                    }
                }
            }

            newSession.open(runtime)
            isSessionOpen = true
            // dm_watcher.js runs as a content script. GeckoView requires its native
            // message delegate on this specific session, not only on the extension.
            extensionManager.attachContentMessageDelegate(newSession)

            // Provide off-screen surface with ImageReader frame consumer to prevent buffer queue deadlocks without requiring OpenGL
            try {
                val reader = ImageReader.newInstance(390, 844, PixelFormat.RGBA_8888, 2)
                reader.setOnImageAvailableListener({ r ->
                    try {
                        r.acquireLatestImage()?.close()
                    } catch (_: Exception) {}
                }, imageHandler ?: mainHandler)
                headlessImageReader = reader
                val surf = reader.surface
                headlessSurface = surf
                val disp = newSession.acquireDisplay()
                headlessDisplay = disp
                disp.surfaceChanged(
                    GeckoDisplay.SurfaceInfo.Builder(surf)
                        .size(390, 844)
                        .build()
                )
            } catch (surfaceEx: Exception) {
                Log.w(TAG, "Headless display surface initialization skipped: ${surfaceEx.message}")
            }

            newSession.setActive(true)
            newSession.setPriorityHint(GeckoSession.PRIORITY_HIGH)

            newSession.loadUri(INBOX_URL)
            session = newSession
            Log.i(TAG, "Headless GeckoSession opened and navigating to $INBOX_URL")
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing headless GeckoSession: ${e.message}", e)
        }
    }

    private fun startHeadlessSession() {
        mainHandler.post {
            if (isDestroyed || isAppInForeground) {
                Log.d(TAG, "Skipping headless session start: isDestroyed=$isDestroyed, isAppInForeground=$isAppInForeground")
                return@post
            }
            if (session != null && isSessionOpen) {
                Log.d(TAG, "Headless session is already active.")
                return@post
            }
            Log.i(TAG, "Starting headless GeckoSession for direct message monitoring...")
            initHeadlessSession()
            startHeartbeatLoop()
        }
    }

    private fun stopHeadlessSession() {
        mainHandler.post {
            heartbeatJob?.cancel()
            heartbeatJob = null

            try {
                session?.let { s ->
                    if (isSessionOpen) {
                        s.close()
                        isSessionOpen = false
                    }
                    s.progressDelegate = null
                    s.navigationDelegate = null
                    s.promptDelegate = null
                    s.permissionDelegate = null
                }
            } catch (e: Exception) {
                Log.w(TAG, "Error closing headless GeckoSession: ${e.message}")
            }
            session = null

            try {
                headlessDisplay?.let { disp ->
                    try {
                        session?.releaseDisplay(disp)
                    } catch (_: Exception) {}
                    disp.surfaceDestroyed()
                }
            } catch (_: Exception) {}
            headlessDisplay = null

            try {
                headlessSurface?.release()
            } catch (_: Exception) {}
            headlessSurface = null

            try {
                headlessImageReader?.close()
            } catch (_: Exception) {}
            headlessImageReader = null

            Log.i(TAG, "✓ Headless GeckoSession stopped and resources released.")
        }
    }

    private fun reloadInbox() {
        mainHandler.post {
            if (isDestroyed || isAppInForeground) return@post
            try {
                session?.let { s ->
                    if (isSessionOpen) {
                        Log.i(TAG, "Reloading headless inbox session: $INBOX_URL")
                        s.loadUri(INBOX_URL)
                    }
                } ?: run {
                    startHeadlessSession()
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to reload headless session: ${e.message}")
            }
        }
    }

    private fun startHeartbeatLoop() {
        heartbeatJob?.cancel()
        heartbeatJob = serviceScope.launch {
            while (isActive) {
                delay(HEARTBEAT_INTERVAL_MS)
                if (isAppInForeground) {
                    Log.d(TAG, "App is in foreground; heartbeat skipping reload.")
                    break
                }
                Log.d(TAG, "Heartbeat interval reached. Triggering safe soft reload of DM inbox...")
                reloadInbox()
            }
        }
    }

    private fun setupNetworkMonitoring() {
        try {
            connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            val request = NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build()

            networkCallback = object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    // Suppress redundant reload on immediate service startup callback
                    if (isInitialNetworkCallback) {
                        isInitialNetworkCallback = false
                        if (System.currentTimeMillis() - serviceStartTime < 10000L) {
                            Log.d(TAG, "Ignoring initial network callback right after startup.")
                            return
                        }
                    }
                    if (isAppInForeground) {
                        Log.d(TAG, "Network became available, but app is in foreground; skipping headless reload.")
                        return
                    }
                    Log.i(TAG, "Network connection became available. Reconnecting headless inbox...")
                    reloadInbox()
                }

                override fun onLost(network: Network) {
                    Log.d(TAG, "Network connection lost.")
                }
            }

            networkCallback?.let {
                connectivityManager?.registerNetworkCallback(request, it)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to register network callback: ${e.message}")
        }
    }

    private fun unregisterNetworkCallback() {
        try {
            networkCallback?.let {
                connectivityManager?.unregisterNetworkCallback(it)
            }
        } catch (_: Exception) {}
        networkCallback = null
        connectivityManager = null
    }

    companion object {
        private const val TAG = "DmNotificationService"
        const val NOTIFICATION_ID_SERVICE = 90211
        const val INBOX_URL = "https://www.instagram.com/direct/inbox/?focusgram_bg_dm=1"

        private const val HEARTBEAT_INTERVAL_MS = 2 * 60 * 1000L

        const val ACTION_STOP = "com.ujwal.focusgram.action.STOP_DM_SERVICE"
        const val ACTION_RELOAD = "com.ujwal.focusgram.action.RELOAD_DM_SERVICE"
        const val ACTION_APP_FOREGROUND = "com.ujwal.focusgram.action.DM_APP_FOREGROUND"
        const val ACTION_APP_BACKGROUND = "com.ujwal.focusgram.action.DM_APP_BACKGROUND"
        const val ACTION_RESTORE_NOTIFICATION = "com.ujwal.focusgram.action.RESTORE_DM_NOTIFICATION"

        fun reload(context: Context) {
            try {
                val intent = Intent(context, DmNotificationService::class.java).apply {
                    action = ACTION_RELOAD
                }
                context.startService(intent)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to dispatch reload: ${e.message}")
            }
        }

        fun start(context: Context) {
            try {
                if (DmNotificationManager.getInstance(context).isAppInForeground) {
                    Log.d(TAG, "Not starting DM listener while the interactive app is visible.")
                    return
                }
                val intent = Intent(context, DmNotificationService::class.java)
                ContextCompat.startForegroundService(context, intent)
                Log.i(TAG, "Dispatched startForegroundService for DmNotificationService.")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start DmNotificationService: ${e.message}", e)
            }
        }

        fun stop(context: Context) {
            try {
                val intent = Intent(context, DmNotificationService::class.java).apply {
                    action = ACTION_STOP
                }
                context.startService(intent)
            } catch (_: Exception) {
                try {
                    context.stopService(Intent(context, DmNotificationService::class.java))
                } catch (_: Exception) {}
            }
        }

        fun onAppForegroundChanged(context: Context, isForeground: Boolean) {
            try {
                if (isForeground) {
                    // Do not leave a second Gecko session or persistent listener
                    // notification alive while the interactive app is visible.
                    // stopService does not enqueue a new start command, which is
                    // crucial if Android is still creating an FGS from an older
                    // request.
                    context.stopService(Intent(context, DmNotificationService::class.java))
                    return
                }
                DmNotificationManager.getInstance(context).isAppInForeground = false
                val intent = Intent(context, DmNotificationService::class.java).apply {
                    action = ACTION_APP_BACKGROUND
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    ContextCompat.startForegroundService(context, intent)
                } else {
                    context.startService(intent)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to dispatch onAppForegroundChanged ($isForeground): ${e.message}")
            }
        }
    }
}
