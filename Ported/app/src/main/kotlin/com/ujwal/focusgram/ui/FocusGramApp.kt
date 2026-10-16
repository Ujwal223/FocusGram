package com.ujwal.focusgram.ui

import android.app.Activity
import android.net.Uri
import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.ActivityInfo
import android.os.Build
import android.provider.MediaStore
import androidx.core.app.NotificationCompat
import androidx.core.content.FileProvider
import java.io.File
import java.util.concurrent.atomic.AtomicReference
import com.ujwal.focusgram.MainActivity
import com.ujwal.focusgram.R
import com.ujwal.focusgram.core.utils.HapticHelper
import com.ujwal.focusgram.core.utils.MediaDownloadHelper
import com.ujwal.focusgram.feature.screentime.ScreenTimeService
import androidx.compose.ui.draw.blur
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Surface
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.ujwal.focusgram.core.database.FocusGramDatabase
import com.ujwal.focusgram.core.datastore.SettingsRepository
import com.ujwal.focusgram.core.engine.FocusGramNavigationDelegate
import com.ujwal.focusgram.core.engine.FocusGramPermissionDelegate
import com.ujwal.focusgram.core.engine.FocusGramPromptDelegate
import com.ujwal.focusgram.core.engine.GeckoRuntimeManager
import com.ujwal.focusgram.core.engine.GeckoSessionManager
import com.ujwal.focusgram.core.engine.UserScriptManager
import com.ujwal.focusgram.core.model.FocusSettings
import com.ujwal.focusgram.core.model.IslandMode
import com.ujwal.focusgram.core.security.EncryptedStorage
import com.ujwal.focusgram.core.ui.theme.PureBlack
import com.ujwal.focusgram.feature.applock.AppLockScreen
import com.ujwal.focusgram.feature.applock.AppLockSettingsPage
import com.ujwal.focusgram.feature.applock.AppLockSetupScreen
import com.ujwal.focusgram.feature.diagnostics.DebugMenuScreen
import com.ujwal.focusgram.feature.history.ReelsHistoryRepository
import com.ujwal.focusgram.feature.history.ReelsHistoryScreen
import com.ujwal.focusgram.feature.navigation.NativeBottomNavBar
import com.ujwal.focusgram.feature.navigation.ReelsTopBar
import com.ujwal.focusgram.feature.session.AppSessionPicker
import com.ujwal.focusgram.feature.session.SessionExhaustedScreen
import com.ujwal.focusgram.feature.snapshots.SnapshotManagerScreen
import com.ujwal.focusgram.feature.webview.GeckoViewContainer
import com.ujwal.focusgram.feature.screentime.ScreenTimeScreen
import com.ujwal.focusgram.feature.wellness.AdPreloader
import com.ujwal.focusgram.feature.wellness.AdsterraAdScreen
import com.ujwal.focusgram.feature.wellness.BreathGateScreen
import com.ujwal.focusgram.feature.wellness.CooldownGateScreen
import com.ujwal.focusgram.feature.wellness.EffortFrictionGate
import com.ujwal.focusgram.feature.wellness.GuardrailsDashboardScreen
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.max

enum class AppDestination {
    MAIN_WEBVIEW,
    SETTINGS,
    REELS_HISTORY,
    SNAPSHOTS,
    SCREEN_TIME,
    GUARDRAILS,
    APP_LOCK_SETUP,
    APP_LOCK_SETTINGS,
    DEBUG_MENU,
    ADSTERRA_REWARDED
}

/**
 * FocusGramApp
 * Primary Jetpack Compose application composable.
 * Orchestrates Mozilla GeckoView, built-in content blocker, mindful intention timers,
 * App Lock biometric security, and native bottom navigation.
 */
@Composable
fun FocusGramApp() {
    val context = LocalContext.current
    val activity = remember(context) {
        var ctx = context
        while (ctx is ContextWrapper) {
            if (ctx is Activity) break
            ctx = ctx.baseContext
        }
        ctx as? Activity
    }
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()

    // Core Managers & Repositories
    val settingsRepository = remember { SettingsRepository(context) }
    val encryptedStorage = remember { EncryptedStorage(context) }
    val database = remember { FocusGramDatabase.getInstance(context) }
    val reelsRepository = remember { ReelsHistoryRepository(database.reelsHistoryDao()) }

    val geckoManager = remember { GeckoRuntimeManager.getInstance(context) }
    val runtime = geckoManager.runtime
    val extensionManager = geckoManager.extensionManager
    val reelHistory = remember { geckoManager.reelHistory }
    val reelsWatchedSessionCount by reelHistory.sessionCount.collectAsState()
    val userScriptManager = remember { UserScriptManager(context) }
    val sessionManager = remember {
        GeckoSessionManager(runtime).apply {
            this.userScriptManager = userScriptManager
        }
    }

    val initialSettings = remember { settingsRepository.getSettingsSync() }
    val settings by settingsRepository.settingsFlow.collectAsState(initial = initialSettings)
    val currentUrl by sessionManager.currentUrl.collectAsState()
    var isAppInForeground by remember { mutableStateOf(true) }
    var destination by remember { mutableStateOf(AppDestination.MAIN_WEBVIEW) }
    val isViewingReels = destination == AppDestination.MAIN_WEBVIEW && currentUrl.lowercase().contains("/reel")
    val hasPin = remember { encryptedStorage.hasPin() }
    var isAppUnlocked by remember { mutableStateOf(false) }
    val shouldShowLock = settings.appLockEnabled && hasPin && !isAppUnlocked
    var hasPassedSessionPicker by remember { mutableStateOf(false) }
    var hasPassedBreathGate by remember { mutableStateOf(!initialSettings.showBreathGate || initialSettings.breathGateSeconds <= 0) }
    var hasPassedEffortFriction by remember { mutableStateOf(!initialSettings.effortFrictionEnabled || initialSettings.wordChallengeCount <= 0) }
    val lastSessionEndTimestamp by settingsRepository.lastSessionEndTimestampFlow.collectAsState(initial = 0L)
    var hasPassedCooldownGate by remember { mutableStateOf(false) }

    val dateFormat = remember { SimpleDateFormat("yyyy-MM-dd", Locale.US) }
    val today = remember { dateFormat.format(Date()) }
    val todayRecord by database.screenTimeDao().getForDate(today).collectAsState(initial = null)
    val isDailyLimitExhausted = settings.dailyLimitMinutes > 0 && ((todayRecord?.seconds ?: 0) >= settings.dailyLimitMinutes * 60)

    val remainingCooldownSeconds = remember(lastSessionEndTimestamp, settings.cooldownMinutes) {
        if (settings.cooldownMinutes <= 0 || lastSessionEndTimestamp <= 0L) {
            0
        } else {
            val elapsedMillis = System.currentTimeMillis() - lastSessionEndTimestamp
            val cooldownMillis = settings.cooldownMinutes * 60 * 1000L
            val remainingMillis = cooldownMillis - elapsedMillis
            if (remainingMillis > 0) (remainingMillis / 1000).toInt() else 0
        }
    }

    var isWebInputFocused by remember { mutableStateOf(false) }
    var isModalActive by remember { mutableStateOf(false) }
    var isUserAuthenticated by remember { mutableStateOf(false) }
    var isAuthPage by remember { mutableStateOf(false) }

    // Session intention countdown timer state
    var sessionDurationMinutes by remember { mutableIntStateOf(15) }
    var sessionRemainingSeconds by remember { mutableIntStateOf(15 * 60) }
    var totalElapsedSeconds by remember { mutableIntStateOf(0) }
    var adExtensionsUsed by remember { mutableIntStateOf(0) }
    var isSessionExhausted by remember { mutableStateOf(false) }
    var adRewardAction by remember { mutableStateOf<(suspend () -> Unit)?>(null) }
    var settingsSubpage by rememberSaveable { mutableStateOf(com.ujwal.focusgram.feature.settings.SettingsSubpage.MAIN) }

    // Evaluates whether any barrier, lock, breath pause, effort friction, cooldown, or intention picker is active
    val isGateActive = shouldShowLock ||
            ((isDailyLimitExhausted || isSessionExhausted) && destination != AppDestination.ADSTERRA_REWARDED) ||
            (remainingCooldownSeconds > 0 && !hasPassedCooldownGate) ||
            (settings.showBreathGate && settings.breathGateSeconds > 0 && !hasPassedBreathGate) ||
            (settings.effortFrictionEnabled && settings.wordChallengeCount > 0 && !hasPassedEffortFriction) ||
            !hasPassedSessionPicker

    // System File Chooser / Camera / Gallery Launchers for Story, Post, DMs & Profile Uploads
    val pendingFilePickerCallback = remember { AtomicReference<((List<Uri>) -> Unit)?>(null) }
    val pendingCameraCaptureUri = remember { AtomicReference<Uri?>(null) }

    // 1. Android Modern Photo & Video Pickers (Official AndroidX contracts, zero permissions needed)
    val singlePhotoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        val callback = pendingFilePickerCallback.getAndSet(null)
        Log.i("FocusGramApp", "Single PhotoPicker result: $uri")
        callback?.invoke(if (uri != null) listOf(uri) else emptyList())
    }

    val multiPhotoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia()
    ) { uris ->
        val callback = pendingFilePickerCallback.getAndSet(null)
        Log.i("FocusGramApp", "Multi PhotoPicker result: ${uris.size} URIs: ${uris.joinToString()}")
        callback?.invoke(uris)
    }

    // 2. Direct Camera Capture Launcher (Used when HTML input requests camera capture)
    val takePictureLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        val callback = pendingFilePickerCallback.getAndSet(null)
        val uri = pendingCameraCaptureUri.getAndSet(null)
        Log.i("FocusGramApp", "TakePicture result: success=$success, uri=$uri")
        if (success && uri != null) {
            callback?.invoke(listOf(uri))
        } else {
            callback?.invoke(emptyList())
        }
    }

    // 3. System Document/Gallery Fallback Launcher
    val fallbackFilePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val uris = mutableListOf<Uri>()
        val callback = pendingFilePickerCallback.getAndSet(null)
        if (result.resultCode == Activity.RESULT_OK) {
            val data = result.data
            val clipData = data?.clipData
            val dataUri = data?.data
            if (clipData != null && clipData.itemCount > 0) {
                for (i in 0 until clipData.itemCount) {
                    clipData.getItemAt(i).uri?.let { uris.add(it) }
                }
            } else if (dataUri != null) {
                uris.add(dataUri)
            }
        }
        Log.i("FocusGramApp", "Fallback file picker result code: ${result.resultCode}, selected: ${uris.size} URIs")
        callback?.invoke(uris)
    }

    // Android Runtime Permissions Launcher (Camera, Audio, Storage)
    val pendingPermissionCallback = remember { AtomicReference<((Boolean) -> Unit)?>(null) }
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val allGranted = results.values.all { it }
        val callback = pendingPermissionCallback.getAndSet(null)
        callback?.invoke(allGranted)
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        Log.i("FocusGramApp", "Notification permission result: $isGranted")
    }

    LaunchedEffect(settings.notifyPersistent, settings.notifySessionEnd) {
        if (settings.notifyPersistent || settings.notifySessionEnd) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                if (androidx.core.content.ContextCompat.checkSelfPermission(
                        context,
                        android.Manifest.permission.POST_NOTIFICATIONS
                    ) != android.content.pm.PackageManager.PERMISSION_GRANTED
                ) {
                    notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                }
            }
        }
    }

    val filePickerHandler = remember {
        object : FocusGramPromptDelegate.FilePickerHandler {
            var launchChooser: ((Array<String>, Boolean, Int) -> Unit)? = null

            override fun onPickFiles(
                mimeTypes: Array<String>,
                allowMultiple: Boolean,
                capture: Int,
                onResult: (List<Uri>) -> Unit
            ) {
                pendingFilePickerCallback.set(onResult)
                try {
                    launchChooser?.invoke(mimeTypes, allowMultiple, capture) ?: run {
                        Log.w("FocusGramApp", "launchChooser was null, returning empty list")
                        pendingFilePickerCallback.getAndSet(null)?.invoke(emptyList())
                    }
                } catch (e: Exception) {
                    Log.e("FocusGramApp", "Error invoking launchChooser: ${e.message}", e)
                    pendingFilePickerCallback.getAndSet(null)?.invoke(emptyList())
                }
            }
        }
    }

    filePickerHandler.launchChooser = { mimes, allowMultiple, capture ->
        val mimeMap = android.webkit.MimeTypeMap.getSingleton()
        val cleanMimes = mimes
            .flatMap { it.split(",") }
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .mapNotNull { token ->
                if (token.startsWith(".")) {
                    mimeMap.getMimeTypeFromExtension(token.removePrefix("."))
                } else if (token.contains("/")) {
                    token
                } else {
                    null
                }
            }
            .distinct()
            .toTypedArray()

        Log.i("FocusGramApp", "Launching media picker for cleanMimes: ${cleanMimes.joinToString()}, allowMultiple: $allowMultiple, capture: $capture")

        val isAnyMedia = cleanMimes.isEmpty() || cleanMimes.contains("*/*")
        val hasImages = isAnyMedia || cleanMimes.any { it.startsWith("image/") }
        val hasVideos = isAnyMedia || cleanMimes.any { it.startsWith("video/") }

        val currentUrlLower = sessionManager.currentUrl.value.lowercase()
        val isProfilePictureEdit = currentUrlLower.contains("/accounts/edit")

        // In Instagram, DMs (/direct/), Stories, and Feed Posts all support videos (MP4, MOV, WebM).
        // Unless explicitly updating an account avatar/profile picture, always allow both Photos and Videos.
        val allowsVideos = hasVideos || (!isProfilePictureEdit && hasImages)
        val allowsImages = hasImages || isAnyMedia

        var launched = false

        // 1. Direct Camera Capture Mode (Explicitly requested by web page via capture attribute)
        if (capture != org.mozilla.geckoview.GeckoSession.PromptDelegate.FilePrompt.Capture.NONE) {
            try {
                val cameraDir = File(context.cacheDir, "camera").apply { mkdirs() }
                val photoFile = File(cameraDir, "camera_capture.jpg")
                if (photoFile.exists()) photoFile.delete()
                val photoUri = FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    photoFile
                )
                pendingCameraCaptureUri.set(photoUri)
                takePictureLauncher.launch(photoUri)
                launched = true
            } catch (eCamera: Exception) {
                Log.w("FocusGramApp", "Direct camera launch failed: ${eCamera.message}, trying photo picker", eCamera)
            }
        }

        // 2. Primary Media Picker: Modern Android Photo & Video Picker (PickVisualMedia)
        // Works seamlessly on Android 11+ and Samsung Galaxy OneUI without any permissions
        if (!launched) {
            try {
                val visualMediaType = when {
                    allowsImages && !allowsVideos -> ActivityResultContracts.PickVisualMedia.ImageOnly
                    allowsVideos && !allowsImages -> ActivityResultContracts.PickVisualMedia.VideoOnly
                    else -> ActivityResultContracts.PickVisualMedia.ImageAndVideo
                }
                val request = PickVisualMediaRequest.Builder()
                    .setMediaType(visualMediaType)
                    .build()

                if (allowMultiple) {
                    multiPhotoPickerLauncher.launch(request)
                } else {
                    singlePhotoPickerLauncher.launch(request)
                }
                launched = true
            } catch (ePhotoPicker: Exception) {
                Log.w("FocusGramApp", "PickVisualMedia failed: ${ePhotoPicker.message}, attempting ACTION_GET_CONTENT", ePhotoPicker)
            }
        }

        // 3. Fallback 1: ACTION_GET_CONTENT (with CATEGORY_OPENABLE for Samsung & AOSP DocumentsUI)
        if (!launched) {
            try {
                val getContentIntent = Intent(Intent.ACTION_GET_CONTENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    if (allowMultiple) {
                        putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
                    }
                    if (allowsImages && !allowsVideos) {
                        type = "image/*"
                        if (cleanMimes.isNotEmpty() && !cleanMimes.contains("image/*")) {
                            putExtra(Intent.EXTRA_MIME_TYPES, cleanMimes)
                        }
                    } else if (allowsVideos && !allowsImages) {
                        type = "video/*"
                        if (cleanMimes.isNotEmpty() && !cleanMimes.contains("video/*")) {
                            putExtra(Intent.EXTRA_MIME_TYPES, cleanMimes)
                        }
                    } else {
                        type = "*/*"
                        val fallbackTypes = if (cleanMimes.isNotEmpty() && allowsVideos && !hasVideos) {
                            (cleanMimes.toList() + listOf("video/*", "video/mp4")).toTypedArray()
                        } else if (cleanMimes.isNotEmpty()) {
                            cleanMimes
                        } else {
                            arrayOf("image/*", "video/*")
                        }
                        putExtra(Intent.EXTRA_MIME_TYPES, fallbackTypes)
                    }
                }
                fallbackFilePickerLauncher.launch(getContentIntent)
                launched = true
            } catch (eGetContent: Exception) {
                Log.w("FocusGramApp", "ACTION_GET_CONTENT failed: ${eGetContent.message}, attempting ACTION_PICK", eGetContent)
            }
        }

        // 4. Fallback 2: ACTION_PICK from MediaStore
        if (!launched) {
            try {
                val pickIntent = if (allowsVideos && !allowsImages) {
                    Intent(Intent.ACTION_PICK, MediaStore.Video.Media.EXTERNAL_CONTENT_URI)
                } else {
                    Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
                }.apply {
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                fallbackFilePickerLauncher.launch(pickIntent)
                launched = true
            } catch (ePick: Exception) {
                Log.e("FocusGramApp", "All file picker fallbacks failed: ${ePick.message}", ePick)
                pendingFilePickerCallback.getAndSet(null)?.invoke(emptyList())
            }
        }
    }

    // Pre-warm Rewarded Ad container and cache in background (isolated from GeckoView)
    LaunchedEffect(Unit) {
        AdPreloader.prewarm(context)
    }

    // Foreground Session Limit Ticker & Real-Time Screen / Reels Time Tracker
    LaunchedEffect(hasPassedSessionPicker, isSessionExhausted, isAppInForeground) {
        if (hasPassedSessionPicker && !isSessionExhausted && isAppInForeground) {
            val dateFormat = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
            var unsavedSeconds = 0
            var unsavedReelsSeconds = 0

            try {
                while (sessionRemainingSeconds > 0) {
                    delay(1000L)
                    totalElapsedSeconds += 1
                    sessionRemainingSeconds -= 1

                    val onReels = destination == AppDestination.MAIN_WEBVIEW &&
                            sessionManager.currentUrl.value.lowercase().contains("/reel")

                    unsavedSeconds += 1
                    if (onReels) {
                        unsavedReelsSeconds += 1
                    }

                    // Persist to Room SQLite every second for real-time responsiveness
                    if (unsavedSeconds >= 1) {
                        val todayStr = dateFormat.format(java.util.Date())
                        database.screenTimeDao().addSeconds(todayStr, unsavedSeconds, unsavedReelsSeconds)
                        unsavedSeconds = 0
                        unsavedReelsSeconds = 0
                    }

                    // Every 60 seconds: deduct minute credits & enforce daily limits
                    if (totalElapsedSeconds % 60 == 0) {
                        if (onReels) {
                            settingsRepository.deductReelsMinute()
                        } else {
                            settingsRepository.deductInstaMinute()
                        }

                        if (settings.dailyLimitMinutes > 0) {
                            val today = dateFormat.format(java.util.Date())
                            val todayRecord = database.screenTimeDao().getForDateSync(today)
                            val totalTodaySeconds = (todayRecord?.seconds ?: 0) + unsavedSeconds
                            if (totalTodaySeconds >= settings.dailyLimitMinutes * 60) {
                                isSessionExhausted = true
                                settingsRepository.recordSessionEnd()
                                HapticHelper.performSessionCompletionAlert(context)
                                break
                            }
                        }
                    }

                    if (sessionRemainingSeconds <= 0) {
                        isSessionExhausted = true
                        settingsRepository.recordSessionEnd()
                        HapticHelper.performSessionCompletionAlert(context)
                        if (settings.notifySessionEnd) {
                            postSessionExpiredNotification(context)
                        }
                        break
                    }
                }
            } finally {
                // Ensure any pending seconds are atomically written to Room on cancel or pause
                if (unsavedSeconds > 0) {
                    withContext(NonCancellable) {
                        val today = dateFormat.format(java.util.Date())
                        database.screenTimeDao().addSeconds(today, unsavedSeconds, unsavedReelsSeconds)
                    }
                }
            }
        }
    }

    // Update persistent notification route state if ScreenTimeService is running
    LaunchedEffect(isViewingReels, settings.notifyPersistent, hasPassedSessionPicker, isSessionExhausted) {
        if (settings.notifyPersistent && hasPassedSessionPicker && !isSessionExhausted) {
            ScreenTimeService.updateRoute(context, isViewingReels)
        }
    }

    // Manage persistent countdown notification via ScreenTimeService
    DisposableEffect(hasPassedSessionPicker, isSessionExhausted, settings.notifyPersistent) {
        if (hasPassedSessionPicker && !isSessionExhausted && settings.notifyPersistent) {
            ScreenTimeService.startSession(context, isViewingReels)
        }
        onDispose {
            ScreenTimeService.stopSession(context)
        }
    }

    // Media pausing: pause media when leaving webview or when barriers/gates are active, resume when returning
    LaunchedEffect(destination, isGateActive) {
        if (destination != AppDestination.MAIN_WEBVIEW || isGateActive) {
            sessionManager.pauseMedia()
        } else {
            sessionManager.resumeMedia()
        }
    }

    // Lifecycle observer for foreground/background state, media pausing, and auto-lock
    DisposableEffect(lifecycleOwner, settings.appLockEnabled, settings.autoLockOnBackground) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                isAppInForeground = false
                sessionManager.pauseMedia()
                if (hasPassedSessionPicker && totalElapsedSeconds >= 15) {
                    scope.launch { settingsRepository.recordSessionEnd() }
                }
                if (settings.appLockEnabled && (settings.autoLockOnBackground || !hasPassedSessionPicker)) {
                    isAppUnlocked = false
                }
            } else if (event == Lifecycle.Event.ON_START) {
                isAppInForeground = true
                if (destination == AppDestination.MAIN_WEBVIEW && !isGateActive) {
                    sessionManager.resumeMedia()
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // Connect WebExtension event callbacks
    LaunchedEffect(extensionManager) {
        extensionManager.onOpenSettings = {
            destination = AppDestination.SETTINGS
        }
        extensionManager.onInputFocused = { focused ->
            isWebInputFocused = focused
        }
        extensionManager.onUrlChanged = { newUrl ->
            val mode = IslandMode.fromUrl(newUrl)
            sessionManager.updateCurrentUrl(newUrl)
            sessionManager.updateIslandMode(mode)
            extensionManager.setIslandMode(mode)
            isWebInputFocused = false
            isModalActive = mode == IslandMode.HIDDEN
        }
        extensionManager.onModalOrChatActive = { active ->
            isModalActive = active
            val currentMode = IslandMode.fromUrl(sessionManager.currentUrl.value)
            // On Reels (FLOATING), maintain FLOATING layout so the comments sheet sits above the bottom nav bar
            val mode = if (active && currentMode != IslandMode.FLOATING) IslandMode.HIDDEN else currentMode
            sessionManager.updateIslandMode(mode)
            extensionManager.setIslandMode(mode)
        }
        extensionManager.onThemeChanged = { theme ->
            sessionManager.updateTheme(theme)
        }
        extensionManager.onReelViewed = { url, title, author, thumb ->
            val code = if (url.contains("/reel/")) {
                url.substringAfter("/reel/").substringBefore("/").substringBefore("?")
            } else if (url.contains("/reels/")) {
                url.substringAfter("/reels/").substringBefore("/").substringBefore("?")
            } else {
                url.trimEnd('/').substringAfterLast('/')
            }
            val cleanCode = code.trim().removePrefix("/").removeSuffix("/")
            val ignoredCodes = setOf("reels", "reel", "videos", "audio", "clips", "home", "explore", "direct", "stories")
            if (cleanCode.length >= 5 && cleanCode.lowercase() !in ignoredCodes) {
                reelHistory.onWatched(cleanCode, author, title)
            }
            scope.launch {
                reelsRepository.addReel(url, title, thumb)
            }
        }
        extensionManager.onReelMeta = { url, title, author ->
            val code = if (url.contains("/reel/")) {
                url.substringAfter("/reel/").substringBefore("/").substringBefore("?")
            } else if (url.contains("/reels/")) {
                url.substringAfter("/reels/").substringBefore("/").substringBefore("?")
            } else {
                url.trimEnd('/').substringAfterLast('/')
            }
            val cleanCode = code.trim().removePrefix("/").removeSuffix("/")
            val ignoredCodes = setOf("reels", "reel", "videos", "audio", "clips", "home", "explore", "direct", "stories")
            if (cleanCode.length >= 5 && cleanCode.lowercase() !in ignoredCodes) {
                if (title.isNotBlank() || author.isNotBlank()) {
                    reelHistory.onMeta(cleanCode, author, title)
                }
            }
        }
        extensionManager.onDownloadStarted = { filename ->
            MediaDownloadHelper.startIndeterminateProgress(filename)
        }
        extensionManager.onDownloadRequested = { url, filename ->
            MediaDownloadHelper.download(context, url, filename)
        }
        extensionManager.onDownloadError = { error ->
            MediaDownloadHelper.triggerDownloadFailure(context, error)
        }
        extensionManager.onAuthStateChanged = { authenticated, authPage ->
            isUserAuthenticated = authenticated
            isAuthPage = authPage
            sessionManager.setAuthenticated(authenticated)
        }
    }

    LaunchedEffect(reelHistory, database) {
        val todayStr = dateFormat.format(java.util.Date())
        reelHistory.todayCount.collect { count ->
            database.screenTimeDao().syncReelsWatched(todayStr, count)
        }
    }

    // Mount Content Blocker extension and configure delegates
    LaunchedEffect(Unit) {
        extensionManager.initializeUBlockOrigin()

        sessionManager.session.navigationDelegate = FocusGramNavigationDelegate(
            context = context,
            sessionManager = sessionManager,
            onRouteChanged = { newUrl ->
                val mode = IslandMode.fromUrl(newUrl)
                val lower = newUrl.lowercase()
                if (lower.contains("/accounts") || lower.contains("/login") || lower.contains("/signup")) {
                    isAuthPage = true
                }
                sessionManager.updateCurrentUrl(newUrl)
                sessionManager.updateIslandMode(mode)
                extensionManager.setIslandMode(mode)
                isWebInputFocused = false
                isModalActive = mode == IslandMode.HIDDEN
            }
        )

        sessionManager.session.promptDelegate = FocusGramPromptDelegate(
            context = context,
            filePickerHandler = filePickerHandler
        )

        sessionManager.session.permissionDelegate = FocusGramPermissionDelegate(
            context = context,
            onAndroidPermissionRequest = { perms, callback ->
                pendingPermissionCallback.set(callback)
                permissionLauncher.launch(perms)
            }
        )

        val initialUrl = when {
            settings.minimalModeEnabled -> "https://www.instagram.com/direct/inbox/"
            settings.startupPage == "following" -> "https://www.instagram.com/?variant=following"
            settings.startupPage == "favorites" -> "https://www.instagram.com/?variant=favorites"
            settings.startupPage == "direct" -> "https://www.instagram.com/direct/inbox/"
            else -> GeckoSessionManager.DEFAULT_URL
        }
        sessionManager.openAndLoad(initialUrl)
    }

    // Reactively update web environment and extension when settings change
    var previousReloadSettings by remember { mutableStateOf<FocusSettings?>(null) }
    LaunchedEffect(settings) {
        sessionManager.updateSettings(settings)
        extensionManager.broadcastSettings(settings)
        extensionManager.setRevealMode(settings.unblurMode)
        extensionManager.setUBlockEnabled(settings.noAds)

        val prev = previousReloadSettings
        if (prev != null) {
            val needsReload = prev.grayscaleEnabled != settings.grayscaleEnabled ||
                    prev.blurExplore != settings.blurExplore ||
                    prev.blurReels != settings.blurReels ||
                    prev.blurFeed != settings.blurFeed ||
                    prev.unblurMode != settings.unblurMode ||
                    prev.hideLikeCounts != settings.hideLikeCounts ||
                    prev.hideFollowerCounts != settings.hideFollowerCounts ||
                    prev.noAutoplay != settings.noAutoplay ||
                    prev.blockDmReelsSwiping != settings.blockDmReelsSwiping ||
                    prev.videoDownloadEnabled != settings.videoDownloadEnabled ||
                    prev.minimalModeEnabled != settings.minimalModeEnabled ||
                    prev.hideSuggestedPosts != settings.hideSuggestedPosts ||
                    prev.noAds != settings.noAds
            if (needsReload) {
                sessionManager.session.reload()
            }
        }
        previousReloadSettings = settings
    }

    // Dynamic screen orientation lock: enforce portrait-only when enabled
    LaunchedEffect(settings.portraitOnly, activity) {
        activity?.requestedOrientation = if (settings.portraitOnly) {
            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        } else {
            ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    // When any barrier, gate, lock, or intention picker is active, pressing back exits the app cleanly
    BackHandler(enabled = isGateActive && destination != AppDestination.ADSTERRA_REWARDED) {
        activity?.finish()
    }

    // Predictive back gesture: when at the root destination and cannot go back in WebView,
    // disabling BackHandler allows Android's predictive back-to-home animation to trigger cleanly.
    val canHandleBack = !isGateActive && (
            destination != AppDestination.MAIN_WEBVIEW ||
            sessionManager.canGoBack.value ||
            sessionManager.currentUrl.value.lowercase().let {
                it.contains("/direct") || (!it.endsWith("instagram.com/") && !it.endsWith("instagram.com"))
            }
    )

    BackHandler(enabled = canHandleBack) {
        when {
            destination == AppDestination.APP_LOCK_SETUP -> {
                destination = AppDestination.APP_LOCK_SETTINGS
            }
            destination in listOf(
                AppDestination.APP_LOCK_SETTINGS,
                AppDestination.SCREEN_TIME,
                AppDestination.REELS_HISTORY,
                AppDestination.SNAPSHOTS,
                AppDestination.GUARDRAILS,
                AppDestination.DEBUG_MENU
            ) -> {
                destination = AppDestination.SETTINGS
            }
            destination == AppDestination.ADSTERRA_REWARDED -> {
                if (isDailyLimitExhausted || isSessionExhausted) {
                    destination = AppDestination.MAIN_WEBVIEW
                } else {
                    destination = AppDestination.SETTINGS
                }
            }
            destination == AppDestination.SETTINGS -> {
                destination = AppDestination.MAIN_WEBVIEW
            }
            sessionManager.canGoBack.value -> {
                isWebInputFocused = false
                isModalActive = false
                sessionManager.goBack()
            }
            else -> {
                val currentUrlStr = sessionManager.currentUrl.value.lowercase()
                if (currentUrlStr.contains("/direct") || (!currentUrlStr.endsWith("instagram.com/") && !currentUrlStr.endsWith("instagram.com"))) {
                    isWebInputFocused = false
                    isModalActive = false
                    sessionManager.goBack()
                }
            }
        }
    }

    val isSystemDark = isSystemInDarkTheme()
    val instagramTheme by sessionManager.instagramTheme.collectAsState()
    val isAppLight = when (instagramTheme) {
        "light" -> true
        "dark" -> false
        else -> !isSystemDark
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = if (isAppLight) Color.White else PureBlack
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            val islandMode by sessionManager.islandMode.collectAsState()
            val density = LocalDensity.current
            val isImeVisible = WindowInsets.ime.getBottom(density) > 0
            val isKeyboardOrInputActive = isImeVisible || isWebInputFocused

            val navBarBottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
            val navBarTotalHeight = 64.dp + navBarBottomInset
            val bottomNavShift = if (islandMode == IslandMode.FLOATING) {
                if (!isKeyboardOrInputActive) navBarTotalHeight else 16.dp
            } else 0.dp

            // Persistent GeckoView layer: keeps GPU compositor surface alive through all transitions.
            // On FLOATING (Reels): viewport ends cleanly above the bottom nav bar with ambient light below.
            // On NORMAL routes: statusBarsPadding at top.
            GeckoViewContainer(
                sessionManager = sessionManager,
                grayscale = settings.grayscaleEnabled,
                modifier = Modifier
                    .fillMaxSize()
                    .then(
                        if (islandMode != IslandMode.FLOATING) {
                            Modifier.statusBarsPadding()
                        } else {
                            Modifier.padding(bottom = bottomNavShift)
                        }
                    )
            )

            // Instagram Reels Native Top Header with watched counter and media download
            ReelsTopBar(
                isVisible = !isGateActive &&
                        destination == AppDestination.MAIN_WEBVIEW &&
                        islandMode == IslandMode.FLOATING &&
                        !isModalActive &&
                        !isWebInputFocused,
                watchedCount = reelsWatchedSessionCount,
                isDownloadEnabled = settings.videoDownloadEnabled,
                onReelsClick = {
                    sessionManager.refreshReels()
                },
                onDownloadClick = {
                    MediaDownloadHelper.startIndeterminateProgress("Preparing Reel download...")
                    extensionManager.triggerReelDownload()
                    sessionManager.userScriptManager?.evaluate(
                        sessionManager.session,
                        "if (typeof window.__fgDownloadActiveReel === 'function') { window.__fgDownloadActiveReel(); } else { window.dispatchEvent(new CustomEvent('focusgram:download-reel', { bubbles: true })); }"
                    )
                },
                modifier = Modifier.align(Alignment.TopCenter)
            )

            // Ambient background behind the floating nav bar on Reels (seamless gradient, no harsh border line)
            if (!isGateActive && destination == AppDestination.MAIN_WEBVIEW && islandMode == IslandMode.FLOATING && !isKeyboardOrInputActive) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(navBarTotalHeight)
                        .blur(18.dp)
                        .background(
                            Brush.verticalGradient(
                                colors = if (isAppLight) {
                                    listOf(
                                        Color(0x1F000000),
                                        Color(0x0A000000),
                                        Color.Transparent
                                    )
                                } else {
                                    listOf(
                                        Color(0x40181820),
                                        Color(0x80101014),
                                        Color(0xCC08080C),
                                        Color(0xFF040406)
                                    )
                                }
                            )
                        )
                )
            }

            // Native Bottom Navigation Bar (OLED floating island)
            if (!isGateActive && destination == AppDestination.MAIN_WEBVIEW) {
                val instagramTheme by sessionManager.instagramTheme.collectAsState()
                val downloadState by MediaDownloadHelper.downloadProgressFlow.collectAsState()
                NativeBottomNavBar(
                    currentUrl = currentUrl,
                    settings = settings,
                    islandMode = islandMode,
                    instagramTheme = instagramTheme,
                    isWebInputFocused = isWebInputFocused,
                    isModalActive = isModalActive,
                    downloadProgress = downloadState.progress,
                    isDownloading = downloadState.isDownloading,
                    isDownloadCompleted = downloadState.isCompleted,
                    isDownloadFailed = downloadState.isFailed,
                    isAuthenticated = isUserAuthenticated,
                    isAuthPage = isAuthPage,
                    onNavClick = { target ->
                        extensionManager.go(target)
                    },
                    onNavigate = { url ->
                        sessionManager.navigateTo(url)
                    },
                    onOpenProfile = {
                        sessionManager.openProfileSpa()
                    },
                    onOpenSettings = {
                        destination = AppDestination.SETTINGS
                    },
                    onRefreshHome = {
                        sessionManager.refreshHome()
                    },
                    onRefreshReels = {
                        sessionManager.refreshReels()
                    },
                    modifier = Modifier.align(Alignment.BottomCenter)
                )
            }

            // Sub-screen Animated Overlay (Settings, Reels History, App Lock, etc.)
            AnimatedVisibility(
                visible = !isGateActive && destination != AppDestination.MAIN_WEBVIEW,
                enter = slideInHorizontally(animationSpec = tween(280)) { it } + fadeIn(animationSpec = tween(250)),
                exit = slideOutHorizontally(animationSpec = tween(280)) { it } + fadeOut(animationSpec = tween(220))
            ) {
                AnimatedContent(
                    targetState = destination,
                    transitionSpec = {
                        val isBackNav = (targetState == AppDestination.SETTINGS && initialState != AppDestination.MAIN_WEBVIEW) ||
                                (targetState == AppDestination.APP_LOCK_SETTINGS && initialState == AppDestination.APP_LOCK_SETUP)

                        if (isBackNav) {
                            (slideInHorizontally(animationSpec = tween(260)) { -it / 3 } + fadeIn(animationSpec = tween(240)))
                                .togetherWith(slideOutHorizontally(animationSpec = tween(260)) { it } + fadeOut(animationSpec = tween(200)))
                        } else {
                            (slideInHorizontally(animationSpec = tween(260)) { it } + fadeIn(animationSpec = tween(240)))
                                .togetherWith(slideOutHorizontally(animationSpec = tween(260)) { -it / 3 } + fadeOut(animationSpec = tween(200)))
                        }
                    },
                    label = "subscreenTransition"
                ) { currentDestination ->
                    when (currentDestination) {
                        AppDestination.MAIN_WEBVIEW -> {
                            // Rendered by underlying Box
                        }

                        AppDestination.SETTINGS -> {
                            com.ujwal.focusgram.feature.settings.SettingsScreen(
                                repository = settingsRepository,
                                onBack = { destination = AppDestination.MAIN_WEBVIEW },
                                onNavigateReelsHistory = { destination = AppDestination.REELS_HISTORY },
                                onNavigateScreenTime = { destination = AppDestination.SCREEN_TIME },
                                onNavigateAppLock = { destination = AppDestination.APP_LOCK_SETTINGS },
                                onWatchAd = { destination = AppDestination.ADSTERRA_REWARDED },
                                onWatchAdWithReward = { action ->
                                    adRewardAction = action
                                    destination = AppDestination.ADSTERRA_REWARDED
                                },
                                onNavigateUrl = { url ->
                                    destination = AppDestination.MAIN_WEBVIEW
                                    sessionManager.navigateTo(url)
                                },
                                screenTimeDao = database.screenTimeDao(),
                                reelHistory = reelHistory,
                                currentSubpage = settingsSubpage,
                                onSubpageChange = { settingsSubpage = it }
                            )
                        }

                        AppDestination.REELS_HISTORY -> {
                            ReelsHistoryScreen(
                                reelHistory = reelHistory,
                                onBack = { destination = AppDestination.SETTINGS },
                                onOpenReel = { url ->
                                    destination = AppDestination.MAIN_WEBVIEW
                                    sessionManager.loadUrl(url)
                                }
                            )
                        }

                        AppDestination.SNAPSHOTS -> {
                            SnapshotManagerScreen(
                                snapshotDao = database.snapshotDao(),
                                onBack = { destination = AppDestination.SETTINGS },
                                onOpenSnapshot = { url ->
                                    destination = AppDestination.MAIN_WEBVIEW
                                    sessionManager.loadUrl(url)
                                }
                            )
                        }

                        AppDestination.SCREEN_TIME -> {
                            ScreenTimeScreen(
                                screenTimeDao = database.screenTimeDao(),
                                dailyLimitMinutes = settings.dailyLimitMinutes,
                                onBack = { destination = AppDestination.SETTINGS },
                                reelHistory = reelHistory
                            )
                        }

                        AppDestination.GUARDRAILS -> {
                            GuardrailsDashboardScreen(
                                repository = settingsRepository,
                                onBack = { destination = AppDestination.SETTINGS }
                            )
                        }

                        AppDestination.APP_LOCK_SETUP -> {
                            AppLockSetupScreen(
                                encryptedStorage = encryptedStorage,
                                settingsRepository = settingsRepository,
                                onBack = { destination = AppDestination.APP_LOCK_SETTINGS },
                                onSuccess = { destination = AppDestination.APP_LOCK_SETTINGS }
                            )
                        }

                        AppDestination.APP_LOCK_SETTINGS -> {
                            AppLockSettingsPage(
                                repository = settingsRepository,
                                encryptedStorage = encryptedStorage,
                                onBack = { destination = AppDestination.SETTINGS },
                                onChangePin = { destination = AppDestination.APP_LOCK_SETUP }
                            )
                        }

                        AppDestination.DEBUG_MENU -> {
                            DebugMenuScreen(
                                repository = settingsRepository,
                                onBack = { destination = AppDestination.SETTINGS }
                            )
                        }

                        AppDestination.ADSTERRA_REWARDED -> {
                            AdsterraAdScreen(
                                sessionType = if (adRewardAction != null) "setting_toggle" else if (isDailyLimitExhausted || isSessionExhausted) "session_extend" else "voluntary",
                                onRewardClaimed = {
                                    val pendingAction = adRewardAction
                                    adRewardAction = null
                                    if (pendingAction != null) {
                                        scope.launch { pendingAction.invoke() }
                                        destination = AppDestination.SETTINGS
                                    } else if (isDailyLimitExhausted) {
                                        scope.launch { settingsRepository.extendDailyLimit5Minutes(today) }
                                        sessionRemainingSeconds += 5 * 60
                                        sessionDurationMinutes += 5
                                        isSessionExhausted = false
                                        destination = AppDestination.MAIN_WEBVIEW
                                    } else if (isSessionExhausted) {
                                        adExtensionsUsed += 1
                                        sessionRemainingSeconds += 3 * 60
                                        sessionDurationMinutes += 3
                                        isSessionExhausted = false
                                        destination = AppDestination.MAIN_WEBVIEW
                                    } else if (!hasPassedEffortFriction) {
                                        hasPassedEffortFriction = true
                                        destination = AppDestination.MAIN_WEBVIEW
                                    } else {
                                        destination = AppDestination.SETTINGS
                                    }
                                },
                                onCancel = {
                                    adRewardAction = null
                                    if (isDailyLimitExhausted || isSessionExhausted) {
                                        destination = AppDestination.MAIN_WEBVIEW
                                    } else {
                                        destination = AppDestination.SETTINGS
                                    }
                                }
                            )
                        }
                    }
                }
            }

            // Full-screen Gate & Mindful Session Picker Overlays
            // Rendered on top of GeckoView so Instagram preloads seamlessly in the background.
            if (isGateActive) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(PureBlack)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) {}
                ) {
                    if (shouldShowLock) {
                        AppLockScreen(
                            encryptedStorage = encryptedStorage,
                            onUnlockSuccess = { isAppUnlocked = true },
                            isBiometricAllowed = settings.biometricEnabled
                        )
                    } else if ((isDailyLimitExhausted || isSessionExhausted) && destination != AppDestination.ADSTERRA_REWARDED) {
                        val elapsedMins = max(sessionDurationMinutes, (totalElapsedSeconds + 59) / 60)
                        val extensionsLeft = if (isDailyLimitExhausted) {
                            if (settings.dailyLimitAdClaimedDate == today) 0 else 1
                        } else {
                            (3 - adExtensionsUsed).coerceAtLeast(0)
                        }
                        SessionExhaustedScreen(
                            elapsedMinutes = elapsedMins,
                            extensionsRemaining = extensionsLeft,
                            onExit = { activity?.finish() },
                            onWatchAdToExtend = {
                                if (extensionsLeft > 0) {
                                    destination = AppDestination.ADSTERRA_REWARDED
                                }
                            }
                        )
                    } else if (remainingCooldownSeconds > 0 && !hasPassedCooldownGate) {
                        CooldownGateScreen(
                            remainingSecondsInitial = remainingCooldownSeconds,
                            onCooldownExpired = { hasPassedCooldownGate = true },
                            onExit = { activity?.finish() }
                        )
                    } else if (settings.showBreathGate && settings.breathGateSeconds > 0 && !hasPassedBreathGate) {
                        BreathGateScreen(
                            durationSeconds = settings.breathGateSeconds,
                            onFinish = { hasPassedBreathGate = true }
                        )
                    } else if (settings.effortFrictionEnabled && settings.wordChallengeCount > 0 && !hasPassedEffortFriction) {
                        EffortFrictionGate(
                            sessionType = "Instagram",
                            remainingCreditMinutes = settings.instaMinutes,
                            onProceed = { hasPassedEffortFriction = true },
                            onWatchAd = {
                                destination = AppDestination.ADSTERRA_REWARDED
                            },
                            onCancel = { activity?.finish() }
                        )
                    } else if (!hasPassedSessionPicker) {
                        AppSessionPicker(
                            onSessionSelected = { minutes ->
                                sessionDurationMinutes = minutes
                                sessionRemainingSeconds = minutes * 60
                                totalElapsedSeconds = 0
                                adExtensionsUsed = 0
                                isSessionExhausted = false
                                hasPassedSessionPicker = true
                            }
                        )
                    }
                }
            }
        }
    }
}

private fun postSessionExpiredNotification(context: Context) {
    try {
        // Direct physical vibration ensures the user feels the alert regardless of notification channel mutes
        HapticHelper.performSessionCompletionAlert(context)

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "focusgram_session_alerts"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Focus Session Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Alerts when your focus session timer expires"
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 300, 200, 300)
            }
            notificationManager.createNotificationChannel(channel)
        }

        val launchIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, channelId)
            .setContentTitle("Focus Session Expired")
            .setContentText("Your scheduled time limit has finished. Take a breath and step away.")
            .setSmallIcon(R.drawable.ic_notification)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        notificationManager.notify(1002, notification)
    } catch (e: Exception) {
        Log.e("FocusGramApp", "Failed to post session notification: ${e.message}", e)
    }
}
