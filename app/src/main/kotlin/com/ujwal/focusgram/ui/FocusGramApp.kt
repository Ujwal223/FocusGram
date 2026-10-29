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
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import android.widget.Toast
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
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
import androidx.compose.runtime.rememberUpdatedState
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
import kotlinx.coroutines.isActive
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
import com.ujwal.focusgram.feature.applock.ChatLockScreen
import com.ujwal.focusgram.feature.diagnostics.DebugMenuScreen
import com.ujwal.focusgram.feature.history.ReelsHistoryRepository
import com.ujwal.focusgram.feature.history.ReelsHistoryScreen
import com.ujwal.focusgram.feature.navigation.NativeBottomNavBar
import com.ujwal.focusgram.feature.navigation.ReelsTopBar
import com.ujwal.focusgram.feature.session.AppSessionPicker
import com.ujwal.focusgram.feature.session.DailyLimitGateScreen
import com.ujwal.focusgram.feature.session.SessionExhaustedScreen
import com.ujwal.focusgram.core.utils.TimeFormatters
import com.ujwal.focusgram.feature.snapshots.SnapshotManagerScreen
import com.ujwal.focusgram.feature.webview.GeckoViewContainer
import com.ujwal.focusgram.feature.screentime.ScreenTimeScreen
import com.ujwal.focusgram.feature.wellness.AdPreloader
import com.ujwal.focusgram.feature.wellness.AdsterraAdScreen
import com.ujwal.focusgram.feature.wellness.BreathGateScreen
import com.ujwal.focusgram.feature.wellness.CooldownGateScreen
import com.ujwal.focusgram.feature.wellness.EffortFrictionGate
import com.ujwal.focusgram.feature.wellness.GuardrailsDashboardScreen
import com.ujwal.focusgram.feature.onboarding.OnboardingScreen
import com.ujwal.focusgram.feature.update.UpdateBanner
import com.ujwal.focusgram.feature.update.UpdateCheckResult
import com.ujwal.focusgram.feature.update.UpdateCheckerService
import com.ujwal.focusgram.feature.update.UpdateInfo
import com.ujwal.focusgram.feature.notice.RemoteNotice
import com.ujwal.focusgram.feature.notice.RemoteNoticeService
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
    CHAT_LOCK_SETUP,
    DEBUG_MENU,
    ADSTERRA_REWARDED,
    ONBOARDING
}

data class PendingChatUnlock(
    val threadId: String,
    val username: String?,
    val targetUrl: String?
)

/**
 * Root composable binding Mozilla GeckoView, client-side content blocker rules,
 * biometric gates, usage tracking, and the floating navigation shell.
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

    val settingsRepository = remember { SettingsRepository(context) }
    val initialSettings = remember { settingsRepository.getSettingsSync() }
    val settings by settingsRepository.settingsFlow.collectAsState(initial = initialSettings)
    val encryptedStorage = remember { EncryptedStorage(context) }
    val database = remember { FocusGramDatabase.getInstance(context) }
    val reelsRepository = remember { ReelsHistoryRepository(database.reelsHistoryDao()) }

    val geckoManager = remember { GeckoRuntimeManager.getInstance(context) }
    val runtime = geckoManager.runtime
    val extensionManager = geckoManager.extensionManager
    val reelHistory = remember {
        geckoManager.reelHistory.apply {
            if (initialSettings.activeSessionIsActive && initialSettings.activeSessionRemainingSeconds > 0) {
                restoreSession(initialSettings.activeSessionReelCodes, initialSettings.activeSessionReelsWatched)
            }
        }
    }
    val reelsWatchedSessionCount by reelHistory.sessionCount.collectAsState()
    val userScriptManager = remember { UserScriptManager(context) }
    val sessionManager = remember {
        GeckoSessionManager(runtime).apply {
            this.userScriptManager = userScriptManager
            this.extensionManager = extensionManager
        }
    }

    val currentUrl by sessionManager.currentUrl.collectAsState()
    var isAppInForeground by remember { mutableStateOf(true) }
    var destination by rememberSaveable { mutableStateOf(AppDestination.MAIN_WEBVIEW) }
    val isViewingReels = destination == AppDestination.MAIN_WEBVIEW && currentUrl.lowercase().contains("/reel")
    val hasPin = remember { encryptedStorage.hasPin() }
    var isAppUnlocked by rememberSaveable { mutableStateOf(false) }
    val shouldShowLock = settings.appLockEnabled && hasPin && !isAppUnlocked

    val updateCheckerService = remember { UpdateCheckerService(context, settingsRepository) }
    var activeUpdateInfo by remember { mutableStateOf<UpdateInfo?>(null) }
    val remoteNoticeService = remember { RemoteNoticeService(context) }
    var activeRemoteNotice by remember { mutableStateOf<RemoteNotice?>(null) }

    LaunchedEffect(Unit) {
        when (val res = updateCheckerService.checkForUpdates(ignoreDismissed = false)) {
            is UpdateCheckResult.Available -> {
                activeUpdateInfo = res.updateInfo
            }
            else -> {}
        }
        val notice = remoteNoticeService.fetchNotice()
        if (notice != null && remoteNoticeService.shouldShow(notice)) {
            activeRemoteNotice = notice
        }
    }

    val threadToUsernameMap = remember { androidx.compose.runtime.mutableStateMapOf<String, Set<String>>() }
    var unlockedChatThreadIds by remember { mutableStateOf(emptySet<String>()) }
    var isDirectAllUnlocked by remember { mutableStateOf(false) }
    var pendingUnlockChat by remember { mutableStateOf<PendingChatUnlock?>(null) }

    LaunchedEffect(extensionManager) {
        extensionManager.onThreadUsernameMapped = { threadId, usernames ->
            val cleanThreadId = threadId.lowercase().trim()
            val existing = threadToUsernameMap[cleanThreadId] ?: emptySet()
            threadToUsernameMap[cleanThreadId] = existing + usernames.map { it.lowercase().trim().removePrefix("@") }
            scope.launch {
                usernames.firstOrNull()?.let { user ->
                    settingsRepository.mapThreadUsername(threadId, user)
                }
            }
        }
        extensionManager.onRequestUnlockChat = { threadId, username, targetUrl ->
            val cleanThreadId = threadId.lowercase().trim()
            val cleanUser = username?.lowercase()?.trim()?.removePrefix("@")
                ?: threadToUsernameMap[cleanThreadId]?.firstOrNull()
                ?: settings.lockedChatUsernames[cleanThreadId]
            pendingUnlockChat = PendingChatUnlock(cleanThreadId, cleanUser, targetUrl)
        }
        extensionManager.onToggleChatLock = { threadId, username ->
            scope.launch {
                val isNowLocked = settingsRepository.toggleLockedChat(threadId, username)
                val label = if (!username.isNullOrBlank()) "@$username" else "Conversation"
                val msg = if (isNowLocked) "Locked $label" else "Unlocked $label"
                android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_SHORT).show()
            }
        }
    }

    val currentUri = remember(currentUrl) { try { Uri.parse(currentUrl) } catch (_: Exception) { null } }
    val currentPath = currentUri?.path?.lowercase() ?: ""
    val isDirectRoute = currentPath.startsWith("/direct")
    val currentThreadId = remember(currentPath) {
        if (currentPath.startsWith("/direct/t/")) {
            currentPath.removePrefix("/direct/t/").trim('/').split('/').firstOrNull()?.trim()
        } else null
    }

    var activeThreadId by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(currentThreadId) {
        if (activeThreadId != null && activeThreadId != currentThreadId) {
            val closedThreadId = activeThreadId!!
            unlockedChatThreadIds = unlockedChatThreadIds - closedThreadId
            extensionManager.notifyChatLocked(closedThreadId)
            sessionManager.userScriptManager?.evaluate(sessionManager.session, """
                if (window.__fgUnlockedThreads && Array.isArray(window.__fgUnlockedThreads)) {
                    window.__fgUnlockedThreads = window.__fgUnlockedThreads.filter(function(id) { return id !== '${closedThreadId.lowercase().trim()}'; });
                }
                window.dispatchEvent(new CustomEvent('focusgram:chat-lock-sync', {
                    detail: { unlockedThreads: window.__fgUnlockedThreads || [] }
                }));
            """)
        }
        activeThreadId = currentThreadId
    }

    LaunchedEffect(isDirectRoute) {
        if (!isDirectRoute) {
            isDirectAllUnlocked = false
            unlockedChatThreadIds = emptySet()
            extensionManager.notifyChatLocked("")
            sessionManager.userScriptManager?.evaluate(sessionManager.session, """
                window.__fgUnlockedThreads = [];
                window.dispatchEvent(new CustomEvent('focusgram:chat-lock-sync', {
                    detail: { unlockedThreads: [] }
                }));
            """)
        }
    }

    val isChatLockActive = remember(
        currentUrl,
        currentPath,
        currentThreadId,
        settings,
        unlockedChatThreadIds,
        isDirectAllUnlocked,
        threadToUsernameMap.size,
        pendingUnlockChat,
        settings.lockedChatUsernames.size
    ) {
        if (pendingUnlockChat != null) return@remember true
        if (!isDirectRoute) return@remember false
        if (settings.chatLockMode == "off") return@remember false
        if (settings.chatLockMode == "whole_dm" || settings.chatLockAll) {
            if (isDirectAllUnlocked) return@remember false
            return@remember true
        }
        if (currentThreadId != null) {
            if (unlockedChatThreadIds.contains(currentThreadId)) return@remember false
            val associatedUsers = threadToUsernameMap[currentThreadId] ?: emptySet()
            val mappedName = settings.lockedChatUsernames[currentThreadId]
            val isLocked = settings.lockedChatIds.any { lockedId ->
                val clean = lockedId.trim().lowercase().removePrefix("@")
                clean.equals(currentThreadId, ignoreCase = true) ||
                currentUrl.contains(clean, ignoreCase = true) ||
                associatedUsers.any { it.equals(clean, ignoreCase = true) } ||
                (!mappedName.isNullOrBlank() && clean.equals(mappedName.lowercase().trim().removePrefix("@"), ignoreCase = true))
            }
            return@remember isLocked
        }
        false
    }
    var hasPassedSessionPicker by rememberSaveable {
        mutableStateOf(initialSettings.activeSessionIsActive && initialSettings.activeSessionRemainingSeconds > 0)
    }
    var hasPassedBreathGate by rememberSaveable { mutableStateOf(!initialSettings.showBreathGate || initialSettings.breathGateSeconds <= 0) }
    var hasPassedEffortFriction by rememberSaveable { mutableStateOf(!initialSettings.effortFrictionEnabled || initialSettings.wordChallengeCount <= 0) }
    val lastSessionEndTimestamp by settingsRepository.lastSessionEndTimestampFlow.collectAsState(initial = 0L)
    var hasPassedCooldownGate by rememberSaveable { mutableStateOf(false) }

    val dateFormat = remember { SimpleDateFormat("yyyy-MM-dd", Locale.US) }
    val today = remember { dateFormat.format(Date()) }
    val todayRecord by database.screenTimeDao().getForDate(today).collectAsState(initial = null)
    val todayDailySeconds = todayRecord?.seconds ?: 0
    val dailyExtensionsUsed = if (settings.dailyLimitAdClaimedDate == today) settings.dailyLimitExtensionsCount else 0
    val effectiveDailyLimitMinutes = settings.dailyLimitMinutes + (dailyExtensionsUsed * 5)
    var isDebugDailyLimitBypassed by rememberSaveable { mutableStateOf(false) }
    val isDailyLimitExhausted = !isDebugDailyLimitBypassed && settings.dailyLimitMinutes > 0 && (todayDailySeconds >= effectiveDailyLimitMinutes * 60)
    val dailyExtensionsRemaining = (2 - dailyExtensionsUsed).coerceAtLeast(0)

    val targetExpiryMillis = remember(lastSessionEndTimestamp, settings.cooldownMinutes) {
        if (settings.cooldownMinutes > 0 && lastSessionEndTimestamp > 0L) {
            lastSessionEndTimestamp + (settings.cooldownMinutes.toLong() * 60L * 1000L)
        } else {
            0L
        }
    }

    var remainingCooldownSeconds by remember {
        val initialRemaining = if (targetExpiryMillis > 0L) {
            val rem = targetExpiryMillis - System.currentTimeMillis()
            if (rem > 0) ((rem + 999) / 1000).toInt() else 0
        } else 0
        mutableIntStateOf(initialRemaining)
    }

    LaunchedEffect(targetExpiryMillis, isAppInForeground) {
        if (targetExpiryMillis <= 0L) {
            remainingCooldownSeconds = 0
            return@LaunchedEffect
        }
        while (isActive) {
            val remainingMillis = targetExpiryMillis - System.currentTimeMillis()
            val remainingSec = if (remainingMillis > 0) ((remainingMillis + 999) / 1000).toInt() else 0
            remainingCooldownSeconds = remainingSec
            if (remainingSec <= 0) break
            delay(1000L)
        }
    }

    var isWebInputFocused by remember { mutableStateOf(false) }
    var isBioFocused by remember { mutableStateOf(false) }
    var selectedBioStyle by remember { mutableStateOf("bold") }
    var isModalActive by remember { mutableStateOf(false) }
    var isUserAuthenticated by remember { mutableStateOf(false) }
    var isAuthPage by remember { mutableStateOf(false) }

    var sessionDurationMinutes by rememberSaveable {
        mutableIntStateOf(if (initialSettings.activeSessionIsActive && initialSettings.activeSessionDurationMinutes > 0) initialSettings.activeSessionDurationMinutes else 15)
    }
    var sessionRemainingSeconds by rememberSaveable {
        mutableIntStateOf(if (initialSettings.activeSessionIsActive && initialSettings.activeSessionRemainingSeconds > 0) initialSettings.activeSessionRemainingSeconds else 15 * 60)
    }
    var totalElapsedSeconds by rememberSaveable {
        mutableIntStateOf(if (initialSettings.activeSessionIsActive) initialSettings.activeSessionElapsedSeconds else 0)
    }
    var sessionExtensionsUsed by remember { mutableIntStateOf(0) }
    val sessionExtensionsRemaining = (3 - sessionExtensionsUsed).coerceAtLeast(0)
    var isSessionExhausted by remember { mutableStateOf(false) }
    var adRewardAction by remember { mutableStateOf<(suspend () -> Unit)?>(null) }
    var settingsSubpage by rememberSaveable { mutableStateOf(com.ujwal.focusgram.feature.settings.SettingsSubpage.MAIN) }

    val isGateActive = destination != AppDestination.ADSTERRA_REWARDED && (
            shouldShowLock ||
            isDailyLimitExhausted || isSessionExhausted ||
            (!hasPassedSessionPicker && remainingCooldownSeconds > 0 && !hasPassedCooldownGate) ||
            (settings.showBreathGate && settings.breathGateSeconds > 0 && !hasPassedBreathGate) ||
            (settings.effortFrictionEnabled && settings.wordChallengeCount > 0 && !hasPassedEffortFriction) ||
            !hasPassedSessionPicker
    )

    val pendingFilePickerCallback = remember { AtomicReference<((List<Uri>) -> Unit)?>(null) }
    val pendingCameraCaptureUri = remember { AtomicReference<Uri?>(null) }

    // AndroidX Photo Picker delegates across Android 11+ without storage permissions.
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

    // FileProvider camera capture invoked when input element specifies capture attributes.
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

    // DocumentsUI / SAF fallback for older Android releases or third-party file managers.
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

        // Instagram direct messages, stories, and feed posts accept videos (MP4, MOV, WebM).
        // Profile avatar uploads are the only route strictly limited to images.
        val allowsVideos = hasVideos || (!isProfilePictureEdit && hasImages)
        val allowsImages = hasImages || isAnyMedia

        var launched = false

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

        // PickVisualMedia delegates without storage permission prompts on Android 11+ and Samsung OneUI.
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

        // ACTION_GET_CONTENT with CATEGORY_OPENABLE handles AOSP DocumentsUI and custom ROM pickers.
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

        // Final fallback targeting platform MediaStore URIs when DocumentsUI rejects the intent.
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

    // Pre-loads isolated webview container for voluntary ad credits in background.
    LaunchedEffect(Unit) {
        AdPreloader.prewarm(context)
    }

    // Foreground ticker decrementing session allocation and logging per-second screen time.
    // Pauses immediately when user navigates away from webview (e.g. Settings, Diagnostics).
    LaunchedEffect(hasPassedSessionPicker, isSessionExhausted, isDailyLimitExhausted, isAppInForeground, destination, isGateActive) {
        if (hasPassedSessionPicker && !isSessionExhausted && !isDailyLimitExhausted && isAppInForeground && destination == AppDestination.MAIN_WEBVIEW && !isGateActive) {
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

                    if (unsavedSeconds >= 1) {
                        val todayStr = dateFormat.format(java.util.Date())
                        database.screenTimeDao().addSeconds(todayStr, unsavedSeconds, unsavedReelsSeconds)
                        unsavedSeconds = 0
                        unsavedReelsSeconds = 0
                    }

                    if (totalElapsedSeconds % 5 == 0) {
                        settingsRepository.saveActiveSession(
                            durationMinutes = sessionDurationMinutes,
                            remainingSeconds = sessionRemainingSeconds,
                            elapsedSeconds = totalElapsedSeconds,
                            reelsWatched = reelsWatchedSessionCount,
                            reelCodes = reelHistory.getSessionCodes(),
                            isActive = true
                        )
                    }

                    if (sessionRemainingSeconds <= 0) {
                        isSessionExhausted = true
                        reelHistory.resetSession()
                        settingsRepository.clearActiveSession()
                        HapticHelper.performSessionCompletionAlert(context)
                        if (settings.notifySessionEnd) {
                            postSessionExpiredNotification(context)
                        }
                        break
                    }
                }
            } finally {
                // Flush pending seconds on cancellation or app backgrounding.
                if (unsavedSeconds > 0) {
                    withContext(NonCancellable) {
                        val todayStr = dateFormat.format(java.util.Date())
                        database.screenTimeDao().addSeconds(todayStr, unsavedSeconds, unsavedReelsSeconds)
                    }
                }
                withContext(NonCancellable) {
                    settingsRepository.saveActiveSession(
                        durationMinutes = sessionDurationMinutes,
                        remainingSeconds = sessionRemainingSeconds,
                        elapsedSeconds = totalElapsedSeconds,
                        reelsWatched = reelsWatchedSessionCount,
                        reelCodes = reelHistory.getSessionCodes(),
                        isActive = sessionRemainingSeconds > 0
                    )
                }
            }
        }
    }

    LaunchedEffect(reelsWatchedSessionCount) {
        if (hasPassedSessionPicker && !isSessionExhausted && sessionRemainingSeconds > 0) {
            settingsRepository.saveActiveSession(
                durationMinutes = sessionDurationMinutes,
                remainingSeconds = sessionRemainingSeconds,
                elapsedSeconds = totalElapsedSeconds,
                reelsWatched = reelsWatchedSessionCount,
                reelCodes = reelHistory.getSessionCodes(),
                isActive = true
            )
        }
    }

    LaunchedEffect(isViewingReels, settings.notifyPersistent, hasPassedSessionPicker, isSessionExhausted, isDailyLimitExhausted) {
        if (settings.notifyPersistent && hasPassedSessionPicker && !isSessionExhausted && !isDailyLimitExhausted) {
            ScreenTimeService.updateRoute(context, isViewingReels)
        }
    }

    LaunchedEffect(hasPassedSessionPicker, isSessionExhausted, isDailyLimitExhausted, settings.notifyPersistent, isAppInForeground, destination, isGateActive) {
        if (!settings.notifyPersistent || !hasPassedSessionPicker || isSessionExhausted || isDailyLimitExhausted || !isAppInForeground) {
            ScreenTimeService.stopSession(context)
        } else if (destination == AppDestination.MAIN_WEBVIEW && !isGateActive) {
            ScreenTimeService.startSession(context, isViewingReels, totalElapsedSeconds, sessionDurationMinutes)
        } else {
            ScreenTimeService.pauseSession(context, totalElapsedSeconds, sessionDurationMinutes)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            ScreenTimeService.stopSession(context)
        }
    }

    // Halt HTML5 media players when navigating away from webview or when security gates engage.
    LaunchedEffect(destination, isGateActive) {
        if (destination != AppDestination.MAIN_WEBVIEW || isGateActive) {
            sessionManager.pauseMedia()
        } else if (isAppInForeground) {
            sessionManager.resumeMedia()
        }
    }

    val currentDestination by rememberUpdatedState(destination)
    val currentHasPassedSessionPicker by rememberUpdatedState(hasPassedSessionPicker)
    val currentTotalElapsedSeconds by rememberUpdatedState(totalElapsedSeconds)
    val currentSessionRemainingSeconds by rememberUpdatedState(sessionRemainingSeconds)
    val currentSessionDurationMinutes by rememberUpdatedState(sessionDurationMinutes)
    val currentIsSessionExhausted by rememberUpdatedState(isSessionExhausted)
    val currentIsGateActive by rememberUpdatedState(isGateActive)

    DisposableEffect(lifecycleOwner, settings.appLockEnabled, settings.autoLockOnBackground) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                isAppInForeground = false
                ScreenTimeService.stopSession(context)
                sessionManager.pauseMedia()
                if (currentHasPassedSessionPicker && !currentIsSessionExhausted) {
                    scope.launch(NonCancellable) {
                        settingsRepository.saveActiveSession(
                            durationMinutes = currentSessionDurationMinutes,
                            remainingSeconds = currentSessionRemainingSeconds,
                            elapsedSeconds = currentTotalElapsedSeconds,
                            reelsWatched = reelsWatchedSessionCount,
                            reelCodes = reelHistory.getSessionCodes(),
                            isActive = currentSessionRemainingSeconds > 0
                        )
                    }
                }
                // Avoid relocking if user navigated out to an external browser tab to view an ad.
                if (currentDestination != AppDestination.ADSTERRA_REWARDED) {
                    if (settings.appLockEnabled && (settings.autoLockOnBackground || !currentHasPassedSessionPicker)) {
                        isAppUnlocked = false
                    }
                    if (settings.autoLockOnBackground) {
                        unlockedChatThreadIds = emptySet()
                        isDirectAllUnlocked = false
                    }
                }
            } else if (event == Lifecycle.Event.ON_START) {
                isAppInForeground = true
                if (currentDestination == AppDestination.MAIN_WEBVIEW && !currentIsGateActive) {
                    sessionManager.resumeMedia()
                } else {
                    sessionManager.pauseMedia()
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    LaunchedEffect(extensionManager) {
        extensionManager.onOpenSettings = {
            destination = AppDestination.SETTINGS
        }
        extensionManager.onInputFocused = { focused ->
            isWebInputFocused = focused
        }
        extensionManager.onBioInputFocused = { focused ->
            isBioFocused = focused
        }
        extensionManager.onUrlChanged = { newUrl ->
            val mode = IslandMode.fromUrl(newUrl)
            val isAuthRoute = isAuthUrl(newUrl)
            isAuthPage = isAuthRoute
            if (isAuthRoute) {
                isUserAuthenticated = false
            }
            sessionManager.updateCurrentUrl(newUrl)
            sessionManager.updateIslandMode(mode)
            extensionManager.setIslandMode(mode)
            isWebInputFocused = false
            isBioFocused = false
            isModalActive = mode == IslandMode.HIDDEN
        }
        extensionManager.onModalOrChatActive = { active ->
            isModalActive = active
            val currentMode = IslandMode.fromUrl(sessionManager.currentUrl.value)
            // On Reels, keep FLOATING layout so comments sheets position above the bottom bar.
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
            val targetCode = if (cleanCode.length >= 5 && cleanCode.lowercase() !in ignoredCodes) {
                cleanCode
            } else if (author.isNotBlank()) {
                author.trim().removePrefix("@")
            } else {
                ""
            }
            if (targetCode.length >= 3) {
                reelHistory.onWatched(targetCode, author, title)
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
            val targetCode = if (cleanCode.length >= 5 && cleanCode.lowercase() !in ignoredCodes) {
                cleanCode
            } else if (author.isNotBlank()) {
                author.trim().removePrefix("@")
            } else {
                ""
            }
            if (targetCode.length >= 3 && (title.isNotBlank() || author.isNotBlank())) {
                reelHistory.onMeta(targetCode, author, title)
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
            isAuthPage = authPage || !authenticated || isAuthUrl(sessionManager.currentUrl.value)
            sessionManager.setAuthenticated(authenticated)
        }
    }

    LaunchedEffect(reelHistory, database) {
        val todayStr = dateFormat.format(java.util.Date())
        reelHistory.todayCount.collect { count ->
            database.screenTimeDao().syncReelsWatched(todayStr, count)
        }
    }

    LaunchedEffect(Unit) {
        extensionManager.initializeUBlockOrigin()

        sessionManager.session.navigationDelegate = FocusGramNavigationDelegate(
            context = context,
            sessionManager = sessionManager,
            onRouteChanged = { newUrl ->
                val mode = IslandMode.fromUrl(newUrl)
                val isAuthRoute = isAuthUrl(newUrl)
                isAuthPage = isAuthRoute
                if (isAuthRoute) {
                    isUserAuthenticated = false
                }
                sessionManager.updateCurrentUrl(newUrl)
                sessionManager.updateIslandMode(mode)
                extensionManager.setIslandMode(mode)
                isWebInputFocused = false
                isBioFocused = false
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

    var previousReloadSettings by remember { mutableStateOf<FocusSettings?>(null) }
    LaunchedEffect(settings) {
        sessionManager.updateSettings(settings)
        extensionManager.broadcastSettings(settings)
        extensionManager.setRevealMode(settings.unblurMode)
        extensionManager.setUBlockEnabled(settings.noAds)

        val prev = previousReloadSettings
        if (prev != null) {
            val needsReload = prev.noAds != settings.noAds
            if (needsReload) {
                sessionManager.session.reload()
            }
        }
        previousReloadSettings = settings
    }

    LaunchedEffect(settings.portraitOnly, activity) {
        activity?.requestedOrientation = if (settings.portraitOnly) {
            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        } else {
            ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    // Exit immediately on back press when locked behind an active mindful barrier or PIN gate.
    BackHandler(enabled = isGateActive && destination != AppDestination.ADSTERRA_REWARDED) {
        activity?.finish()
    }

    val currentUrlStr = sessionManager.currentUrl.value.lowercase()
    val cleanCurrentPath = remember(currentUrlStr) {
        IslandMode.extractPath(currentUrlStr).substringBefore('?').substringBefore('#').trimEnd('/').ifEmpty { "/" }
    }
    val isDmThread = cleanCurrentPath.startsWith("/direct/t/") || cleanCurrentPath.startsWith("/direct/threads/") || (cleanCurrentPath.startsWith("/direct/") && cleanCurrentPath != "/direct/inbox" && cleanCurrentPath != "/direct")
    val isDmInbox = cleanCurrentPath == "/direct" || cleanCurrentPath == "/direct/inbox"
    val isRootHome = cleanCurrentPath == "/"

    // Disabling BackHandler at root URL lets the system gesture invoke predictive back-to-home.
    val canHandleBack = !isGateActive && (
            destination != AppDestination.MAIN_WEBVIEW ||
            isDmThread ||
            isDmInbox ||
            (!isRootHome && (sessionManager.canGoBack.value || (!currentUrlStr.endsWith("instagram.com/") && !currentUrlStr.endsWith("instagram.com"))))
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
                AppDestination.DEBUG_MENU,
                AppDestination.ONBOARDING
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
            destination == AppDestination.MAIN_WEBVIEW && isDmThread -> {
                isWebInputFocused = false
                isModalActive = false
                // Override back action in user's DM to navigate strictly to /direct/inbox without history traversal
                sessionManager.navigateTo("https://www.instagram.com/direct/inbox/")
            }
            destination == AppDestination.MAIN_WEBVIEW && isDmInbox -> {
                isWebInputFocused = false
                isModalActive = false
                // Override back action in DM inbox to navigate strictly to home screen without history traversal
                sessionManager.navigateTo("https://www.instagram.com/")
            }
            destination == AppDestination.MAIN_WEBVIEW && !isRootHome -> {
                isWebInputFocused = false
                isModalActive = false
                if (cleanCurrentPath == "/explore" || cleanCurrentPath == "/reels" || (!cleanCurrentPath.contains('/') && cleanCurrentPath.length > 1)) {
                    sessionManager.navigateTo("https://www.instagram.com/")
                } else if (sessionManager.canGoBack.value) {
                    sessionManager.goBack()
                } else {
                    sessionManager.navigateTo("https://www.instagram.com/")
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

    if (settings.isFirstRun) {
        OnboardingScreen(
            settings = settings,
            settingsRepository = settingsRepository,
            onComplete = {
                scope.launch {
                    settingsRepository.setFirstRunCompleted()
                    hasPassedBreathGate = !settings.showBreathGate || settings.breathGateSeconds <= 0
                    hasPassedEffortFriction = true
                    hasPassedSessionPicker = false
                    hasPassedCooldownGate = true
                }
            }
        )
        return
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

            // Keeps the GeckoView GPU compositor surface attached throughout transitions.
            // Under FLOATING mode (Reels), shifts padding so controls avoid bottom bar occlusion.
            val isWebviewVisible = destination == AppDestination.MAIN_WEBVIEW && !isGateActive
            GeckoViewContainer(
                sessionManager = sessionManager,
                grayscale = settings.grayscaleEnabled,
                isVisible = isWebviewVisible,
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

            if (activeUpdateInfo != null && !isGateActive && destination == AppDestination.MAIN_WEBVIEW && !isViewingReels) {
                UpdateBanner(
                    updateInfo = activeUpdateInfo!!,
                    onDismiss = {
                        val ver = activeUpdateInfo!!.latestVersion
                        activeUpdateInfo = null
                        scope.launch {
                            updateCheckerService.dismissVersion(ver)
                        }
                    },
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .statusBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }

            // Overlay lock trigger positioned adjacent to Instagram's native (i) button in DM headers.
            if (!isGateActive && destination == AppDestination.MAIN_WEBVIEW && currentThreadId != null && settings.chatLockMode == "individual") {
                val isChatExplicitlyLocked = remember(currentThreadId, settings.lockedChatIds, threadToUsernameMap.size, settings.lockedChatUsernames.size) {
                    val associatedUsers = threadToUsernameMap[currentThreadId] ?: emptySet()
                    val mappedName = settings.lockedChatUsernames[currentThreadId]
                    settings.lockedChatIds.any { lockedId ->
                        val clean = lockedId.trim().lowercase().removePrefix("@")
                        clean.equals(currentThreadId, ignoreCase = true) ||
                        associatedUsers.any { it.equals(clean, ignoreCase = true) } ||
                        (!mappedName.isNullOrBlank() && clean.equals(mappedName.lowercase().trim().removePrefix("@"), ignoreCase = true))
                    }
                }
                val isCurrentlyLocked = isChatExplicitlyLocked

                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .statusBarsPadding()
                        .padding(end = 52.dp, top = 2.dp)
                        .size(44.dp),
                    contentAlignment = Alignment.Center
                ) {
                    val iconTint = if (isCurrentlyLocked) {
                        Color(0xFFFF3B30)
                    } else if (isAppLight) {
                        Color(0xFF262626)
                    } else {
                        Color.White.copy(alpha = 0.9f)
                    }

                    IconButton(
                        onClick = {
                            var username = threadToUsernameMap[currentThreadId]?.firstOrNull()
                                ?: settings.lockedChatUsernames[currentThreadId]
                            scope.launch {
                                if (username.isNullOrBlank()) {
                                    try {
                                        sessionManager.userScriptManager?.evaluate(
                                             sessionManager.session,
                                            "if (typeof window.__fgGetThreadParticipant === 'function') { window.__fgGetThreadParticipant(); }"
                                        )
                                    } catch (_: Exception) {}
                                    username = threadToUsernameMap[currentThreadId]?.firstOrNull()
                                        ?: settings.lockedChatUsernames[currentThreadId]
                                }
                                val isNowLocked = settingsRepository.toggleLockedChat(currentThreadId, username)
                                if (isNowLocked) {
                                    unlockedChatThreadIds = unlockedChatThreadIds - currentThreadId
                                    extensionManager.notifyChatLocked(currentThreadId)
                                } else {
                                    unlockedChatThreadIds = unlockedChatThreadIds + currentThreadId
                                    extensionManager.notifyChatUnlocked(currentThreadId, isAll = false)
                                }
                                val label = if (!username.isNullOrBlank()) "@$username" else "Conversation"
                                val msg = if (isNowLocked) "Locked $label" else "Unlocked $label"
                                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                HapticHelper.performClick(context)
                            }
                        },
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            painter = androidx.compose.ui.res.painterResource(
                                if (isCurrentlyLocked) com.ujwal.focusgram.R.drawable.ic_chat_lock_closed
                                else com.ujwal.focusgram.R.drawable.ic_chat_lock_open
                            ),
                            contentDescription = if (isCurrentlyLocked) "Unlock conversation" else "Lock conversation",
                            tint = iconTint,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }

            // Gradient fill beneath the floating navigation bar to smooth video edge transitions on Reels.
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

            if (!isGateActive && destination == AppDestination.MAIN_WEBVIEW && !isAuthPage && isUserAuthenticated) {
                val instagramTheme by sessionManager.instagramTheme.collectAsState()
                val downloadState by MediaDownloadHelper.downloadProgressFlow.collectAsState()
                val sessionTotalSec = sessionDurationMinutes * 60
                val sessionProgress = if (sessionTotalSec > 0 && hasPassedSessionPicker && !isSessionExhausted) {
                    (totalElapsedSeconds.toFloat() / sessionTotalSec.toFloat()).coerceIn(0f, 1f)
                } else {
                    0f
                }
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
                    sessionProgress = sessionProgress,
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

            // Font transformation bar anchored above the soft keyboard on profile editing inputs.
            if (settings.customBioFontsEnabled && isBioFocused && !isGateActive && destination == AppDestination.MAIN_WEBVIEW) {
                Surface(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .imePadding()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    shape = RoundedCornerShape(20.dp),
                    color = Color(0xFA181820),
                    border = BorderStroke(1.dp, Color(0x33FFFFFF)),
                    shadowElevation = 8.dp
                ) {
                    Row(
                        modifier = Modifier
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        listOf(
                            "normal" to "Normal",
                            "bold" to "Bold",
                            "sans" to "Sans",
                            "sans_italic" to "Italic",
                            "mono" to "Mono",
                            "gothic" to "Gothic",
                            "script" to "Script"
                        ).forEach { (id, label) ->
                            val isSelected = selectedBioStyle == id
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(if (isSelected) Color.White else Color(0xFF262626))
                                    .border(
                                        width = 1.dp,
                                        color = if (isSelected) Color.White else Color(0x33FFFFFF),
                                        shape = RoundedCornerShape(14.dp)
                                    )
                                    .clickable {
                                        selectedBioStyle = id
                                        extensionManager.setBioStyle(id)
                                        HapticHelper.performClick(context)
                                        sessionManager.session.loadUri("javascript:(function(){window.__fgSetBioStyle&&window.__fgSetBioStyle('$id');})()")
                                    }
                                    .padding(horizontal = 14.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    text = label,
                                    color = if (isSelected) Color.Black else Color(0xFFD1D5DB),
                                    fontSize = 13.sp,
                                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium
                                )
                            }
                        }
                    }
                }
            }

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
                        }

                        AppDestination.SETTINGS -> {
                            com.ujwal.focusgram.feature.settings.SettingsScreen(
                                repository = settingsRepository,
                                inputSettings = settings,
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
                                onSubpageChange = { settingsSubpage = it },
                                encryptedStorage = encryptedStorage,
                                onNavigateChatPinSetup = { destination = AppDestination.CHAT_LOCK_SETUP },
                                onNavigateOnboarding = { destination = AppDestination.ONBOARDING },
                                onNavigateDebugMenu = { destination = AppDestination.DEBUG_MENU }
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
                                screenTimeDao = database.screenTimeDao(),
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
                                onChangePin = { destination = AppDestination.APP_LOCK_SETUP },
                                onChangeChatPin = { destination = AppDestination.CHAT_LOCK_SETUP }
                            )
                        }

                        AppDestination.CHAT_LOCK_SETUP -> {
                            AppLockSetupScreen(
                                encryptedStorage = encryptedStorage,
                                settingsRepository = settingsRepository,
                                onBack = { destination = AppDestination.APP_LOCK_SETTINGS },
                                onSuccess = { destination = AppDestination.APP_LOCK_SETTINGS },
                                title = "Set Chat Lock PIN",
                                subtitle = "Used to unlock protected Direct Messages and conversations.",
                                isChatPinSetup = true
                            )
                        }

                        AppDestination.DEBUG_MENU -> {
                            DebugMenuScreen(
                                repository = settingsRepository,
                                onBack = { destination = AppDestination.SETTINGS },
                                onSimulateUpdate = {
                                    activeUpdateInfo = UpdateCheckerService.getMockUpdateInfo()
                                    destination = AppDestination.MAIN_WEBVIEW
                                },
                                onSimulateNotice = {
                                    activeRemoteNotice = RemoteNoticeService.getMockNotice()
                                },
                                isLimitsBypassed = isDebugDailyLimitBypassed && hasPassedCooldownGate,
                                onToggleLimitsBypass = {
                                    val willBypass = !(isDebugDailyLimitBypassed && hasPassedCooldownGate)
                                    isDebugDailyLimitBypassed = willBypass
                                    hasPassedCooldownGate = willBypass
                                    if (willBypass) {
                                        isSessionExhausted = false
                                    }
                                },
                                currentSessionDurationMinutes = sessionDurationMinutes,
                                currentSessionRemainingSeconds = sessionRemainingSeconds,
                                currentSessionElapsedSeconds = totalElapsedSeconds,
                                onAdjustSessionTime = { newDuration, newRemaining, newElapsed ->
                                    sessionDurationMinutes = newDuration
                                    sessionRemainingSeconds = newRemaining
                                    totalElapsedSeconds = newElapsed
                                    hasPassedSessionPicker = true
                                    isSessionExhausted = newRemaining <= 0
                                    scope.launch {
                                        settingsRepository.saveActiveSession(
                                            durationMinutes = newDuration,
                                            remainingSeconds = newRemaining,
                                            elapsedSeconds = newElapsed,
                                            reelsWatched = reelsWatchedSessionCount,
                                            reelCodes = reelHistory.getSessionCodes(),
                                            isActive = newRemaining > 0
                                        )
                                    }
                                    if (destination == AppDestination.MAIN_WEBVIEW && !isGateActive) {
                                        ScreenTimeService.startSession(context, isViewingReels, newElapsed, newDuration)
                                    } else {
                                        ScreenTimeService.pauseSession(context, newElapsed, newDuration)
                                    }
                                }
                            )
                        }

                        AppDestination.ADSTERRA_REWARDED -> {
                            AdsterraAdScreen(
                                sessionType = when {
                                    adRewardAction != null -> "setting_toggle"
                                    isDailyLimitExhausted || isSessionExhausted -> "session_extend"
                                    else -> "voluntary"
                                },
                                onRewardClaimed = {
                                    val pendingAction = adRewardAction
                                    adRewardAction = null
                                    if (pendingAction != null) {
                                        scope.launch {
                                            try {
                                                pendingAction.invoke()
                                            } catch (e: Exception) {
                                                Log.e("FocusGramApp", "Failed to apply rewarded setting action", e)
                                            } finally {
                                                destination = AppDestination.SETTINGS
                                            }
                                        }
                                    } else if (isDailyLimitExhausted) {
                                        scope.launch { settingsRepository.extendDailyLimit5Minutes(today) }
                                        sessionRemainingSeconds += 5 * 60
                                        sessionDurationMinutes += 5
                                        isSessionExhausted = false
                                        destination = AppDestination.MAIN_WEBVIEW
                                    } else if (isSessionExhausted) {
                                        sessionExtensionsUsed += 1
                                        sessionRemainingSeconds += 3 * 60
                                        sessionDurationMinutes += 3
                                        isSessionExhausted = false
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

                        AppDestination.ONBOARDING -> {
                            OnboardingScreen(
                                settings = settings,
                                settingsRepository = settingsRepository,
                                onComplete = { destination = AppDestination.SETTINGS }
                            )
                        }
                    }
                }
            }

            // Overlays rendered atop GeckoView so web assets continue loading in background.
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
                    } else if (isDailyLimitExhausted && destination != AppDestination.ADSTERRA_REWARDED) {
                        DailyLimitGateScreen(
                            dailyLimitMinutes = settings.dailyLimitMinutes,
                            todayMinutesUsed = (todayDailySeconds + 59) / 60,
                            extensionsRemaining = dailyExtensionsRemaining,
                            onExit = { activity?.finish() },
                            onWatchAdToExtend = {
                                if (dailyExtensionsRemaining > 0) {
                                    destination = AppDestination.ADSTERRA_REWARDED
                                }
                            },
                            onDebugBypass = {
                                isDebugDailyLimitBypassed = true
                            }
                        )
                    } else if (isSessionExhausted && destination != AppDestination.ADSTERRA_REWARDED) {
                        val elapsedMins = max(sessionDurationMinutes, (totalElapsedSeconds + 59) / 60)
                        SessionExhaustedScreen(
                            elapsedMinutes = elapsedMins,
                            extensionsRemaining = sessionExtensionsRemaining,
                            onExit = {
                                isSessionExhausted = false
                                hasPassedSessionPicker = false
                                hasPassedCooldownGate = false
                                reelHistory.resetSession()
                                scope.launch {
                                    settingsRepository.recordSessionEnd()
                                    settingsRepository.clearActiveSession()
                                }
                                activity?.finish()
                            },
                            onWatchAdToExtend = {
                                if (sessionExtensionsRemaining > 0) {
                                    destination = AppDestination.ADSTERRA_REWARDED
                                }
                            },
                            onDebugBypass = {
                                isSessionExhausted = false
                                hasPassedSessionPicker = true
                            }
                        )
                    } else if (remainingCooldownSeconds > 0 && !hasPassedCooldownGate) {
                        CooldownGateScreen(
                            remainingSeconds = remainingCooldownSeconds,
                            onCooldownExpired = { hasPassedCooldownGate = true },
                            onExit = { activity?.finish() },
                            onDebugBypass = { hasPassedCooldownGate = true }
                        )
                    } else if (settings.showBreathGate && settings.breathGateSeconds > 0 && !hasPassedBreathGate) {
                        BreathGateScreen(
                            durationSeconds = settings.breathGateSeconds,
                            onFinish = { hasPassedBreathGate = true }
                        )
                    } else if (settings.effortFrictionEnabled && settings.wordChallengeCount > 0 && !hasPassedEffortFriction) {
                        EffortFrictionGate(
                            sessionType = "Instagram",
                            wordCount = settings.wordChallengeCount,
                            onProceed = { hasPassedEffortFriction = true },
                            onCancel = { activity?.finish() }
                        )
                    } else if (!hasPassedSessionPicker) {
                        AppSessionPicker(
                            onSessionSelected = { minutes ->
                                sessionDurationMinutes = minutes
                                sessionRemainingSeconds = minutes * 60
                                totalElapsedSeconds = 0
                                sessionExtensionsUsed = 0
                                isSessionExhausted = false
                                hasPassedSessionPicker = true
                                reelHistory.resetSession()
                                scope.launch {
                                    settingsRepository.saveActiveSession(
                                        durationMinutes = minutes,
                                        remainingSeconds = minutes * 60,
                                        elapsedSeconds = 0,
                                        reelsWatched = 0,
                                        reelCodes = emptySet(),
                                        isActive = true
                                    )
                                }
                            }
                        )
                    }
                }
            }

            // Veil rendering pure black over DOM conversation content while awaiting biometric or PIN auth.
            if (destination == AppDestination.MAIN_WEBVIEW && !isGateActive && isChatLockActive) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(PureBlack)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) {}
                ) {
                    val activeThread = pendingUnlockChat?.threadId ?: currentThreadId
                    val lockedUsername = pendingUnlockChat?.username
                        ?: activeThread?.let { settings.lockedChatUsernames[it] }
                        ?: activeThread?.let { threadToUsernameMap[it]?.firstOrNull() }
                    val chatTitle = when {
                        settings.chatLockAll && activeThread == null -> "Direct Messages"
                        !lockedUsername.isNullOrBlank() -> "@$lockedUsername"
                        else -> "Locked Conversation"
                    }
                    ChatLockScreen(
                        encryptedStorage = encryptedStorage,
                        isBiometricAllowed = settings.chatBiometricEnabled,
                        title = chatTitle,
                        subtitle = "Enter 4-digit PIN to view conversation",
                        customVerifyPin = if (settings.useSeparateChatPin && encryptedStorage.hasChatPin()) {
                            { pin -> encryptedStorage.verifyChatPin(pin) }
                        } else null,
                        onCancel = {
                            pendingUnlockChat = null
                            if (currentThreadId != null) {
                                unlockedChatThreadIds = unlockedChatThreadIds - currentThreadId
                            }
                            sessionManager.userScriptManager?.evaluate(sessionManager.session, """
                                if (typeof window.__fgRemovePrivacyVeil === 'function') {
                                    window.__fgRemovePrivacyVeil();
                                }
                            """)
                            if (settings.chatLockMode == "whole_dm" || settings.chatLockAll) {
                                sessionManager.navigateTo("https://www.instagram.com/")
                            } else {
                                sessionManager.navigateTo("https://www.instagram.com/direct/inbox/")
                            }
                        },
                        onUnlockSuccess = {
                            val pending = pendingUnlockChat
                            if (pending != null) {
                                pendingUnlockChat = null
                                unlockedChatThreadIds = unlockedChatThreadIds + pending.threadId
                                extensionManager.notifyChatUnlocked(pending.threadId, isAll = false)

                                val cleanId = pending.threadId.lowercase().trim()
                                sessionManager.userScriptManager?.evaluate(sessionManager.session, """
                                    window.__fgUnlockedThreads = window.__fgUnlockedThreads || [];
                                    if (!window.__fgUnlockedThreads.includes('$cleanId')) {
                                        window.__fgUnlockedThreads.push('$cleanId');
                                    }
                                    if (typeof window.__fgRemovePrivacyVeil === 'function') {
                                        window.__fgRemovePrivacyVeil();
                                    }
                                    window.dispatchEvent(new CustomEvent('focusgram:chat-lock-sync', {
                                        detail: { unlockedThreads: window.__fgUnlockedThreads }
                                    }));
                                """)

                                val dest = pending.targetUrl ?: "https://www.instagram.com/direct/t/${pending.threadId}/"
                                val current = sessionManager.currentUrl.value
                                if (!current.contains(pending.threadId)) {
                                    sessionManager.navigateTo(dest)
                                }
                            } else if ((settings.chatLockMode == "whole_dm" || settings.chatLockAll) && currentThreadId == null) {
                                isDirectAllUnlocked = true
                                extensionManager.notifyChatUnlocked("", isAll = true)
                                sessionManager.userScriptManager?.evaluate(sessionManager.session, """
                                    window.__fgUnlockedThreads = window.__fgUnlockedThreads || [];
                                    if (!window.__fgUnlockedThreads.includes('__all__')) {
                                        window.__fgUnlockedThreads.push('__all__');
                                    }
                                    if (typeof window.__fgRemovePrivacyVeil === 'function') {
                                        window.__fgRemovePrivacyVeil();
                                    }
                                    window.dispatchEvent(new CustomEvent('focusgram:chat-lock-sync', {
                                        detail: { unlockedThreads: window.__fgUnlockedThreads }
                                    }));
                                """)
                            } else if (currentThreadId != null) {
                                val cleanId = currentThreadId.lowercase().trim()
                                unlockedChatThreadIds = unlockedChatThreadIds + currentThreadId
                                extensionManager.notifyChatUnlocked(currentThreadId, isAll = false)
                                sessionManager.userScriptManager?.evaluate(sessionManager.session, """
                                    window.__fgUnlockedThreads = window.__fgUnlockedThreads || [];
                                    if (!window.__fgUnlockedThreads.includes('$cleanId')) {
                                        window.__fgUnlockedThreads.push('$cleanId');
                                    }
                                    if (typeof window.__fgRemovePrivacyVeil === 'function') {
                                        window.__fgRemovePrivacyVeil();
                                    }
                                    window.dispatchEvent(new CustomEvent('focusgram:chat-lock-sync', {
                                        detail: { unlockedThreads: window.__fgUnlockedThreads }
                                    }));
                                """)
                            }
                        }
                    )
                }
            }
        }
    }

    if (activeRemoteNotice != null) {
        val notice = activeRemoteNotice!!
        AlertDialog(
            onDismissRequest = {
                remoteNoticeService.markShown(notice.id)
                activeRemoteNotice = null
            },
            containerColor = Color(0xEB1C1C1E),
            modifier = Modifier
                .clip(RoundedCornerShape(24.dp))
                .border(
                    BorderStroke(
                        1.dp,
                        Brush.verticalGradient(listOf(Color(0x38FFFFFF), Color(0x10FFFFFF)))
                    ),
                    RoundedCornerShape(24.dp)
                ),
            title = {
                Text(
                    text = notice.header,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
            },
            text = {
                Text(
                    text = notice.body,
                    color = Color(0xFFC7C7CC),
                    fontSize = 14.sp,
                    lineHeight = 20.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        remoteNoticeService.markShown(notice.id)
                        activeRemoteNotice = null
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.White,
                        contentColor = Color.Black
                    ),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Text(notice.buttonText.ifBlank { "OK" }, fontWeight = FontWeight.Bold)
                }
            }
        )
    }
}

private fun postSessionExpiredNotification(context: Context) {
    try {
        // Trigger haptic feedback directly so alert registers even when system notification sound is muted.
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

private fun isAuthUrl(url: String): Boolean {
    val lower = url.lowercase()
    return (lower.contains("/accounts") && !lower.contains("/accounts/edit")) ||
            lower.contains("/login") ||
            lower.contains("/signup") ||
            lower.contains("/two_factor") ||
            lower.contains("/challenge") ||
            lower.contains("/onetap") ||
            lower.contains("accountscenter.") ||
            lower.contains("facebook.com")
}
