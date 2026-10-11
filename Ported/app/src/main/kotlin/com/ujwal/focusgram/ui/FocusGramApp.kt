package com.ujwal.focusgram.ui

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.ujwal.focusgram.core.database.FocusGramDatabase
import com.ujwal.focusgram.core.datastore.SettingsRepository
import com.ujwal.focusgram.core.engine.ExtensionManager
import com.ujwal.focusgram.core.engine.FocusGramNavigationDelegate
import com.ujwal.focusgram.core.engine.FocusGramPromptDelegate
import com.ujwal.focusgram.core.engine.GeckoRuntimeManager
import com.ujwal.focusgram.core.engine.GeckoSessionManager
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
import com.ujwal.focusgram.feature.session.AppSessionPicker
import com.ujwal.focusgram.feature.snapshots.SnapshotManagerScreen
import com.ujwal.focusgram.feature.webview.GeckoViewContainer
import com.ujwal.focusgram.feature.wellness.AdsterraAdScreen
import com.ujwal.focusgram.feature.wellness.BreathGateScreen
import com.ujwal.focusgram.feature.wellness.CooldownGateScreen
import com.ujwal.focusgram.feature.wellness.GuardrailsDashboardScreen

enum class AppDestination {
    MAIN_WEBVIEW,
    SETTINGS,
    REELS_HISTORY,
    SNAPSHOTS,
    GUARDRAILS,
    APP_LOCK_SETUP,
    APP_LOCK_SETTINGS,
    DEBUG_MENU,
    ADSTERRA_REWARDED
}

/**
 * FocusGramApp
 * Primary Jetpack Compose application composable.
 * Orchestrates Mozilla GeckoView, uBlock Origin WebExtension, mindful intention timers,
 * App Lock biometric security, and native bottom navigation.
 */
@Composable
fun FocusGramApp() {
    val context = LocalContext.current
    val activity = context as? Activity
    val lifecycleOwner = LocalLifecycleOwner.current

    // Core Managers & Repositories
    val settingsRepository = remember { SettingsRepository(context) }
    val encryptedStorage = remember { EncryptedStorage(context) }
    val database = remember { FocusGramDatabase.getInstance(context) }
    val reelsRepository = remember { ReelsHistoryRepository(database.reelsHistoryDao()) }

    val geckoManager = remember { GeckoRuntimeManager.getInstance(context) }
    val runtime = geckoManager.runtime
    val extensionManager = geckoManager.extensionManager
    val sessionManager = remember { GeckoSessionManager(runtime) }

    val settings by settingsRepository.settingsFlow.collectAsState(initial = FocusSettings())

    var destination by remember { mutableStateOf(AppDestination.MAIN_WEBVIEW) }
    val hasPin = remember { encryptedStorage.hasPin() }
    var isAppUnlocked by remember { mutableStateOf(!settings.appLockEnabled || !hasPin) }
    var hasPassedSessionPicker by remember { mutableStateOf(false) }
    var hasPassedBreathGate by remember { mutableStateOf(!settings.showBreathGate) }
    var isWebInputFocused by remember { mutableStateOf(false) }
    var isModalActive by remember { mutableStateOf(false) }

    // Re-lock app when backgrounded and resumed if App Lock is enabled
    DisposableEffect(lifecycleOwner, settings.appLockEnabled) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                if (settings.appLockEnabled && encryptedStorage.hasPin()) {
                    isAppUnlocked = false
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
            val mode = if (active) IslandMode.HIDDEN else IslandMode.fromUrl(sessionManager.currentUrl.value)
            sessionManager.updateIslandMode(mode)
            extensionManager.setIslandMode(mode)
        }
    }

    // Mount uBlock Origin WebExtension and configure delegates
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
            context = context
        )

        sessionManager.openAndLoad(GeckoSessionManager.DEFAULT_URL)
    }

    // Reactively update web environment and uBlock extension when settings change
    LaunchedEffect(settings) {
        extensionManager.broadcastSettings(settings)
        extensionManager.setUBlockEnabled(settings.noAds)
    }

    // App Lock Enforcement Gate
    if (settings.appLockEnabled && encryptedStorage.hasPin() && !isAppUnlocked) {
        AppLockScreen(
            encryptedStorage = encryptedStorage,
            onUnlockSuccess = { isAppUnlocked = true },
            isBiometricAllowed = settings.biometricEnabled
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
                hasPassedSessionPicker = true
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

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = PureBlack
    ) {
        when (destination) {
            AppDestination.MAIN_WEBVIEW -> {
                Box(modifier = Modifier.fillMaxSize()) {
                    GeckoViewContainer(
                        sessionManager = sessionManager,
                        modifier = Modifier
                            .fillMaxSize()
                            .statusBarsPadding()
                    )

                    // Native Bottom Navigation Bar (slides away when keyboard is open, in DMs, comments, or web input focused)
                    val currentUrl by sessionManager.currentUrl.collectAsState()
                    val islandMode by sessionManager.islandMode.collectAsState()
                    NativeBottomNavBar(
                        currentUrl = currentUrl,
                        settings = settings,
                        islandMode = islandMode,
                        isWebInputFocused = isWebInputFocused,
                        isModalActive = isModalActive,
                        onNavClick = { target ->
                            extensionManager.go(target)
                        },
                        onNavigate = { url ->
                            sessionManager.navigateTo(url)
                        },
                        onOpenProfile = {
                            extensionManager.go("profile")
                        },
                        onOpenSettings = {
                            destination = AppDestination.SETTINGS
                        },
                        modifier = Modifier.align(Alignment.BottomCenter)
                    )
                }
            }

            AppDestination.SETTINGS -> {
                com.ujwal.focusgram.feature.settings.SettingsScreen(
                    repository = settingsRepository,
                    onBack = { destination = AppDestination.MAIN_WEBVIEW },
                    onNavigateReelsHistory = { destination = AppDestination.REELS_HISTORY },
                    onNavigateScreenTime = { destination = AppDestination.GUARDRAILS },
                    onNavigateAppLock = { destination = AppDestination.APP_LOCK_SETTINGS },
                    onWatchAd = { destination = AppDestination.ADSTERRA_REWARDED }
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
                    sessionType = "voluntary",
                    onRewardClaimed = {
                        destination = AppDestination.SETTINGS
                    },
                    onCancel = {
                        destination = AppDestination.SETTINGS
                    }
                )
            }
        }
    }
}
