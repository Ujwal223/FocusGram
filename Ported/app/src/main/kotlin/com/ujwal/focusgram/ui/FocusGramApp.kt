package com.ujwal.focusgram.ui

import android.app.Activity
import android.net.Uri
import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
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
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.ujwal.focusgram.MainActivity
import com.ujwal.focusgram.R
import com.ujwal.focusgram.feature.screentime.ScreenTimeService
import androidx.compose.foundation.background
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.ujwal.focusgram.core.database.FocusGramDatabase
import com.ujwal.focusgram.core.datastore.SettingsRepository
import com.ujwal.focusgram.core.engine.FocusGramNavigationDelegate
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
import com.ujwal.focusgram.feature.wellness.AdsterraAdScreen
import com.ujwal.focusgram.feature.wellness.BreathGateScreen
import com.ujwal.focusgram.feature.wellness.CooldownGateScreen
import com.ujwal.focusgram.feature.wellness.GuardrailsDashboardScreen
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
    val activity = context as? Activity
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
    val userScriptManager = remember { UserScriptManager(context) }
    val sessionManager = remember {
        GeckoSessionManager(runtime).apply {
            this.userScriptManager = userScriptManager
        }
    }

    val settings by settingsRepository.settingsFlow.collectAsState(initial = FocusSettings())

    var destination by remember { mutableStateOf(AppDestination.MAIN_WEBVIEW) }
    val hasPin = remember { encryptedStorage.hasPin() }
    var isAppUnlocked by remember { mutableStateOf(false) }
    val shouldShowLock = settings.appLockEnabled && hasPin && !isAppUnlocked
    var hasPassedSessionPicker by remember { mutableStateOf(false) }
    var hasPassedBreathGate by remember { mutableStateOf(!settings.showBreathGate) }
    val lastSessionEndTimestamp by settingsRepository.lastSessionEndTimestampFlow.collectAsState(initial = 0L)
    var hasPassedCooldownGate by remember { mutableStateOf(false) }

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

    // Session intention countdown timer state
    var sessionDurationMinutes by remember { mutableIntStateOf(15) }
    var sessionRemainingSeconds by remember { mutableIntStateOf(15 * 60) }
    var totalElapsedSeconds by remember { mutableIntStateOf(0) }
    var adExtensionsUsed by remember { mutableIntStateOf(0) }
    var isSessionExhausted by remember { mutableStateOf(false) }

    // System File Chooser / Gallery Launchers for Post & Story uploads
    var pendingFilePickerCallback by remember { mutableStateOf<((List<Uri>) -> Unit)?>(null) }

    val multiFilePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris: List<Uri> ->
        pendingFilePickerCallback?.invoke(uris)
        pendingFilePickerCallback = null
    }

    val singleFilePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        pendingFilePickerCallback?.invoke(if (uri != null) listOf(uri) else emptyList())
        pendingFilePickerCallback = null
    }

    val filePickerHandler = remember {
        object : FocusGramPromptDelegate.FilePickerHandler {
            var launchMulti: ((Array<String>) -> Unit)? = null
            var launchSingle: ((Array<String>) -> Unit)? = null

            override fun onPickFiles(
                mimeTypes: Array<String>,
                allowMultiple: Boolean,
                onResult: (List<Uri>) -> Unit
            ) {
                pendingFilePickerCallback = onResult
                val safeMimes = if (mimeTypes.isEmpty()) arrayOf("*/*") else mimeTypes
                try {
                    if (allowMultiple) {
                        launchMulti?.invoke(safeMimes) ?: onResult(emptyList())
                    } else {
                        launchSingle?.invoke(safeMimes) ?: onResult(emptyList())
                    }
                } catch (e: Exception) {
                    Log.e("FocusGramApp", "Error launching file picker: ${e.message}", e)
                    onResult(emptyList())
                }
            }
        }
    }
    filePickerHandler.launchMulti = { mimes -> multiFilePickerLauncher.launch(mimes) }
    filePickerHandler.launchSingle = { mimes -> singleFilePickerLauncher.launch(mimes) }

    // Foreground Session Limit Ticker
    LaunchedEffect(hasPassedSessionPicker, isSessionExhausted) {
        if (hasPassedSessionPicker && !isSessionExhausted) {
            while (sessionRemainingSeconds > 0) {
                delay(1000L)
                totalElapsedSeconds += 1
                sessionRemainingSeconds -= 1
                if (sessionRemainingSeconds <= 0) {
                    isSessionExhausted = true
                    scope.launch { settingsRepository.recordSessionEnd() }
                    if (settings.notifySessionEnd) {
                        postSessionExpiredNotification(context)
                    }
                    break
                }
            }
        }
    }

    // Manage persistent countdown notification via ScreenTimeService
    DisposableEffect(hasPassedSessionPicker, isSessionExhausted, settings.notifyPersistent) {
        if (hasPassedSessionPicker && !isSessionExhausted && settings.notifyPersistent) {
            ScreenTimeService.startSession(context)
        }
        onDispose {
            ScreenTimeService.stopSession(context)
        }
    }

    // Media pausing: pause media when leaving webview, resume when returning
    LaunchedEffect(destination) {
        if (destination != AppDestination.MAIN_WEBVIEW) {
            sessionManager.pauseMedia()
        } else {
            sessionManager.resumeMedia()
        }
    }

    // Re-lock app when backgrounded and pause media
    DisposableEffect(lifecycleOwner, settings.appLockEnabled, settings.autoLockOnBackground) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                sessionManager.pauseMedia()
                if (hasPassedSessionPicker && totalElapsedSeconds >= 15) {
                    scope.launch { settingsRepository.recordSessionEnd() }
                }
                if (settings.appLockEnabled && (settings.autoLockOnBackground || !hasPassedSessionPicker)) {
                    isAppUnlocked = false
                }
            } else if (event == Lifecycle.Event.ON_START) {
                if (destination == AppDestination.MAIN_WEBVIEW) {
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
            scope.launch {
                reelsRepository.addReel(url, title, thumb)
            }
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

        val initialUrl = when (settings.startupPage) {
            "following" -> "https://www.instagram.com/?variant=following"
            "favorites" -> "https://www.instagram.com/?variant=favorites"
            "direct" -> "https://www.instagram.com/direct/inbox/"
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

    // App Lock Enforcement Gate
    if (shouldShowLock) {
        AppLockScreen(
            encryptedStorage = encryptedStorage,
            onUnlockSuccess = { isAppUnlocked = true },
            isBiometricAllowed = settings.biometricEnabled
        )
        return
    }

    // Cooldown Interval Gate between sessions
    if (remainingCooldownSeconds > 0 && !hasPassedCooldownGate) {
        CooldownGateScreen(
            remainingSecondsInitial = remainingCooldownSeconds,
            onCooldownExpired = { hasPassedCooldownGate = true },
            onExit = { activity?.finish() }
        )
        return
    }

    // Mindfulness Breath Gate
    if (settings.showBreathGate && !hasPassedBreathGate) {
        BreathGateScreen(
            durationSeconds = settings.breathGateSeconds,
            onFinish = { hasPassedBreathGate = true }
        )
        return
    }

    // Cold-start Conscious Session Picker
    if (!hasPassedSessionPicker) {
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
        return
    }

    // Session Limit Exhausted Lockdown Screen
    if (isSessionExhausted && destination != AppDestination.ADSTERRA_REWARDED) {
        val elapsedMins = max(sessionDurationMinutes, (totalElapsedSeconds + 59) / 60)
        val extensionsLeft = (3 - adExtensionsUsed).coerceAtLeast(0)
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
        return
    }

    // Back button handling
    BackHandler(enabled = true) {
        when {
            destination != AppDestination.MAIN_WEBVIEW -> {
                destination = AppDestination.MAIN_WEBVIEW
            }
            sessionManager.canGoBack.value -> {
                isWebInputFocused = false
                isModalActive = false
                sessionManager.goBack()
            }
            else -> {
                val currentUrlStr = sessionManager.currentUrl.value.lowercase()
                if (currentUrlStr.contains("/direct") || !currentUrlStr.endsWith("instagram.com/")) {
                    isWebInputFocused = false
                    isModalActive = false
                    sessionManager.goBack()
                } else {
                    activity?.finish()
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

            // Instagram Reels Native Top Header matching inspiration
            ReelsTopBar(
                isVisible = destination == AppDestination.MAIN_WEBVIEW &&
                        islandMode == IslandMode.FLOATING &&
                        !isModalActive &&
                        !isWebInputFocused,
                onReelsClick = {
                    sessionManager.refreshReels()
                },
                modifier = Modifier.align(Alignment.TopCenter)
            )

            // Ambient background behind the floating nav bar on Reels (seamless gradient, no harsh border line)
            if (destination == AppDestination.MAIN_WEBVIEW && islandMode == IslandMode.FLOATING && !isKeyboardOrInputActive) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(navBarTotalHeight)
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
            if (destination == AppDestination.MAIN_WEBVIEW) {
                val currentUrl by sessionManager.currentUrl.collectAsState()
                val instagramTheme by sessionManager.instagramTheme.collectAsState()
                NativeBottomNavBar(
                    currentUrl = currentUrl,
                    settings = settings,
                    islandMode = islandMode,
                    instagramTheme = instagramTheme,
                    isWebInputFocused = isWebInputFocused,
                    isModalActive = isModalActive,
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
                visible = destination != AppDestination.MAIN_WEBVIEW,
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
                                onNavigateUrl = { url ->
                                    destination = AppDestination.MAIN_WEBVIEW
                                    sessionManager.navigateTo(url)
                                }
                            )
                        }

                        AppDestination.REELS_HISTORY -> {
                            ReelsHistoryScreen(
                                repository = reelsRepository,
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
                                userLevel = settings.userLevel,
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
                                onBack = { destination = AppDestination.SETTINGS }
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
                                sessionType = if (isSessionExhausted) "session_extend" else "voluntary",
                                onRewardClaimed = {
                                    if (isSessionExhausted) {
                                        adExtensionsUsed += 1
                                        sessionRemainingSeconds += 3 * 60
                                        sessionDurationMinutes += 3
                                        isSessionExhausted = false
                                        destination = AppDestination.MAIN_WEBVIEW
                                    } else {
                                        destination = AppDestination.SETTINGS
                                    }
                                },
                                onCancel = {
                                    if (isSessionExhausted) {
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
        }
    }
}

private fun postSessionExpiredNotification(context: Context) {
    try {
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
            .setSmallIcon(R.mipmap.ic_launcher)
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
