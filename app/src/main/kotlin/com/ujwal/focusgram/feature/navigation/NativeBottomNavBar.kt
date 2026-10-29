package com.ujwal.focusgram.feature.navigation

import android.os.SystemClock
import android.view.HapticFeedbackConstants
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.shadow
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import com.ujwal.focusgram.core.model.FocusSettings
import com.ujwal.focusgram.core.model.IslandMode
import com.ujwal.focusgram.core.utils.HapticHelper
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.min

enum class NavTab(
    val title: String,
    val outlineIcon: ImageVector,
    val filledIcon: ImageVector,
    val targetPath: String,
    val targetKey: String
) {
    HOME("Home", InstagramIcons.HomeOutline, InstagramIcons.HomeFilled, "https://www.instagram.com/", "home"),
    SEARCH("Search", InstagramIcons.SearchOutline, InstagramIcons.SearchFilled, "https://www.instagram.com/explore/", "search"),
    REELS("Reels", InstagramIcons.ReelsOutline, InstagramIcons.ReelsFilled, "https://www.instagram.com/reels/", "reels"),
    MESSAGES("DMs", InstagramIcons.DirectOutline, InstagramIcons.DirectFilled, "https://www.instagram.com/direct/inbox/", "dm"),
    PROFILE("Profile", InstagramIcons.ProfileOutline, InstagramIcons.ProfileFilled, "https://www.instagram.com/", "profile"),
    SETTINGS("Settings", InstagramIcons.SettingsOutline, InstagramIcons.SettingsFilled, "", "settings")
}

/**
 * Navigation bar island rendered as a floating capsule with animated tab transitions.
 *
 * Supports double-tap on Home/Reels to refresh, and profile long-press to open Settings
 * with a perimeter sweep progress animation starting above the Home icon.
 */
@Composable
fun NativeBottomNavBar(
    currentUrl: String,
    settings: FocusSettings,
    islandMode: IslandMode = IslandMode.NORMAL,
    instagramTheme: String = "auto",
    isWebInputFocused: Boolean = false,
    isModalActive: Boolean = false,
    downloadProgress: Float = 0f,
    isDownloading: Boolean = false,
    isDownloadCompleted: Boolean = false,
    isDownloadFailed: Boolean = false,
    sessionProgress: Float = 0f,
    isAuthenticated: Boolean = false,
    isAuthPage: Boolean = false,
    onNavClick: ((String) -> Unit)? = null,
    onNavigate: (String) -> Unit,
    onOpenProfile: () -> Unit,
    onOpenSettings: () -> Unit,
    onRefreshHome: () -> Unit = {},
    onRefreshReels: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val view = LocalView.current
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val isImeVisible = WindowInsets.ime.getBottom(density) > 0
    val lowerUrl = currentUrl.lowercase()
    val isInDirect = lowerUrl.contains("/direct")

    val activeTabs = remember(settings.noReels, settings.noDMs, settings.minimalModeEnabled) {
        if (settings.minimalModeEnabled) {
            listOf(NavTab.MESSAGES, NavTab.PROFILE)
        } else {
            listOfNotNull(
                NavTab.HOME,
                if (!settings.noReels) NavTab.REELS else null,
                if (!settings.noDMs) NavTab.MESSAGES else null,
                NavTab.SEARCH,
                NavTab.PROFILE
            )
        }
    }

    val navShape = RoundedCornerShape(32.dp)
    val shouldHideInDirect = if (settings.minimalModeEnabled) {
        lowerUrl.contains("/direct/t/")
    } else {
        isInDirect
    }
    val isExplicitAuthRoute = (lowerUrl.contains("/accounts") && !lowerUrl.contains("/accounts/edit")) ||
            lowerUrl.contains("/login") ||
            lowerUrl.contains("/signup") ||
            lowerUrl.contains("/two_factor") ||
            lowerUrl.contains("/challenge") ||
            lowerUrl.contains("/onetap") ||
            lowerUrl.contains("accountscenter.") ||
            lowerUrl.contains("facebook.com")
    val isAuth = isAuthPage || !isAuthenticated || isExplicitAuthRoute
    val isVisible = !isAuth && !isImeVisible && !shouldHideInDirect && !isWebInputFocused && !isModalActive && islandMode != IslandMode.HIDDEN

    // Real-time animated color palette matching Instagram's web theme
    val isSystemDark = isSystemInDarkTheme()
    val isLight = when (instagramTheme.lowercase()) {
        "light" -> true
        "dark" -> false
        else -> !isSystemDark
    }
    val islandBackground by animateColorAsState(
        targetValue = if (islandMode == IslandMode.FLOATING) {
            if (isLight) Color(0xB0FFFFFF) else Color(0x9E101014)
        } else {
            if (isLight) Color(0xB2FFFFFF) else Color(0xAE141418)
        },
        animationSpec = tween(300),
        label = "islandBackground"
    )
    val borderColor by animateColorAsState(
        targetValue = if (isLight) Color(0x35000000) else Color(0x57FFFFFF),
        animationSpec = tween(300),
        label = "borderColor"
    )
    val glassBorderBrush = remember(isLight) {
        if (isLight) {
            Brush.verticalGradient(
                colors = listOf(
                    Color(0x3E000000), // Top specular rim (+4% glassy)
                    Color(0x20000000)  // Bottom diffused rim
                )
            )
        } else {
            Brush.verticalGradient(
                colors = listOf(
                    Color(0x60FFFFFF), // Top specular glass rim (+4% glassy)
                    Color(0x36FFFFFF)  // Bottom subtle glass rim
                )
            )
        }
    }
    val selectedTabBackground by animateColorAsState(
        targetValue = if (isLight) Color(0x1F000000) else Color(0x33FFFFFF),
        animationSpec = tween(300),
        label = "selectedTabBackground"
    )
    val activeIconColor by animateColorAsState(
        targetValue = if (isLight) Color(0xFF000000) else Color.White,
        animationSpec = tween(300),
        label = "activeIconColor"
    )
    val inactiveIconColor by animateColorAsState(
        targetValue = if (isLight) Color(0x8A000000) else Color.White.copy(alpha = 0.65f),
        animationSpec = tween(300),
        label = "inactiveIconColor"
    )

    // Double-click timestamp trackers
    var lastHomeClickTime by remember { mutableLongStateOf(0L) }
    var lastReelsClickTime by remember { mutableLongStateOf(0L) }

    // Long press perimeter progress animation state
    val longPressProgressAnim = remember { Animatable(0f) }

    // Animated download progress state (smooth sweep)
    val animatedDownloadProgress by animateFloatAsState(
        targetValue = if (isDownloading || downloadProgress > 0f) downloadProgress else 0f,
        animationSpec = if (isDownloadFailed) {
            tween(durationMillis = 60, easing = LinearEasing)
        } else if (downloadProgress >= 1f) {
            tween(durationMillis = 200, easing = LinearEasing)
        } else if (downloadProgress == 0f) {
            tween(durationMillis = 350, easing = FastOutSlowInEasing)
        } else {
            tween(durationMillis = 180, easing = LinearEasing)
        },
        label = "animatedDownloadProgress"
    )

    // Animated active session perimeter progress
    val animatedSessionProgress by animateFloatAsState(
        targetValue = if (settings.showSessionTimeInBottomBar) sessionProgress.coerceIn(0f, 1f) else 0f,
        animationSpec = tween(durationMillis = 1000, easing = LinearEasing),
        label = "animatedSessionProgress"
    )

    // Glow animation for download completion (glow out and glow in 2 times)
    val glowAnim = remember { Animatable(0f) }
    androidx.compose.runtime.LaunchedEffect(isDownloadCompleted) {
        if (isDownloadCompleted) {
            // Pulse 1: glow out (0f -> 1f) and glow in (1f -> 0f)
            glowAnim.animateTo(1f, animationSpec = tween(durationMillis = 260, easing = FastOutSlowInEasing))
            glowAnim.animateTo(0f, animationSpec = tween(durationMillis = 260, easing = FastOutSlowInEasing))
            // Pulse 2: glow out (0f -> 1f) and glow in (1f -> 0f)
            glowAnim.animateTo(1f, animationSpec = tween(durationMillis = 260, easing = FastOutSlowInEasing))
            glowAnim.animateTo(0f, animationSpec = tween(durationMillis = 260, easing = FastOutSlowInEasing))
        } else {
            glowAnim.snapTo(0f)
        }
    }

    // Retain last valid active tab index so custom schemes/downloads never jump to Profile
    var lastValidTabIndex by remember { mutableIntStateOf(0) }

    val cleanPath = remember(currentUrl) {
        val afterScheme = if (currentUrl.contains("://")) currentUrl.substringAfter("://") else currentUrl
        val pathPart = if (afterScheme.contains('/')) afterScheme.substringAfter('/') else ""
        pathPart.substringBefore('?').substringBefore('#').trim('/')
    }
    val isHome = cleanPath.isEmpty()

    val rawTabIndex = activeTabs.indexOfFirst { tab ->
        when (tab) {
            NavTab.HOME -> isHome || lowerUrl.startsWith("https://www.instagram.com/?") || lowerUrl.startsWith("https://instagram.com/?") || lowerUrl == "/"
            NavTab.SEARCH -> lowerUrl.contains("/explore")
            NavTab.REELS -> lowerUrl.contains("/reel")
            NavTab.MESSAGES -> lowerUrl.contains("/direct") || lowerUrl.contains("/inbox")
            NavTab.PROFILE -> {
                val isInstagram = lowerUrl.contains("instagram.com") || lowerUrl.startsWith("/")
                val isExcluded = isHome ||
                        lowerUrl.contains("/explore") ||
                        lowerUrl.contains("/reel") ||
                        lowerUrl.contains("/direct") ||
                        lowerUrl.contains("/inbox") ||
                        lowerUrl.contains("/p/") ||
                        lowerUrl.contains("/stories/") ||
                        lowerUrl.startsWith("focusgram-download:") ||
                        lowerUrl.startsWith("javascript:")

                val segments = cleanPath.trim('/').split('/').filter { it.isNotEmpty() }
                val isSingleUserSegment = segments.size == 1

                !isHome && isInstagram && !isExcluded &&
                        (lowerUrl.contains("/profile") || isSingleUserSegment)
            }
            NavTab.SETTINGS -> false
        }
    }

    val selectedTabIndex = if (rawTabIndex >= 0) {
        lastValidTabIndex = rawTabIndex
        rawTabIndex
    } else {
        lastValidTabIndex.coerceIn(0, activeTabs.lastIndex)
    }

    AnimatedVisibility(
        visible = isVisible,
        enter = slideInVertically(
            initialOffsetY = { it },
            animationSpec = tween(durationMillis = 250)
        ) + fadeIn(animationSpec = tween(durationMillis = 200)),
        exit = slideOutVertically(
            targetOffsetY = { it },
            animationSpec = tween(durationMillis = 200)
        ) + fadeOut(animationSpec = tween(durationMillis = 150)),
        modifier = modifier
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(WindowInsets.navigationBars.asPaddingValues())
                .padding(horizontal = 20.dp, vertical = 8.dp)
        ) {
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .shadow(
                        elevation = if (glowAnim.value > 0f) (8.dp + 12.dp * glowAnim.value) else (if (isLight) 8.dp else 4.dp),
                        shape = navShape,
                        spotColor = if (glowAnim.value > 0f) Color(0xFF00D2FF).copy(alpha = 0.75f * glowAnim.value) else (if (isLight) Color(0x33000000) else Color.Transparent)
                    )
                    .clip(navShape)
                    .border(BorderStroke(1.dp, glassBorderBrush), navShape),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(navShape)
                        .blur(20.dp)
                        .background(islandBackground)
                )

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(navShape)
                        .background(
                            Brush.verticalGradient(
                                colors = if (isLight) {
                                    listOf(
                                        Color(0x24FFFFFF),
                                        Color(0x08FFFFFF),
                                        Color.Transparent
                                    )
                                } else {
                                    listOf(
                                        Color(0x1CFFFFFF),
                                        Color(0x06FFFFFF),
                                        Color.Transparent
                                    )
                                }
                            )
                        )
                )
                val totalWidthPx = constraints.maxWidth.toFloat()
                val totalHeightPx = constraints.maxHeight.toFloat()
                val tabCount = activeTabs.size
                val tabWidthDp = maxWidth / tabCount
                val startXPx = (totalWidthPx / tabCount) / 2f
                val cornerRadiusPx = with(density) { min(32.dp.toPx(), totalHeightPx / 2f) }

                val animatedPillOffset by animateDpAsState(
                    targetValue = tabWidthDp * selectedTabIndex,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessMediumLow
                    ),
                    label = "pillOffset"
                )

                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(tabWidthDp)
                        .offset(x = animatedPillOffset)
                        .padding(horizontal = 6.dp, vertical = 6.dp)
                        .clip(RoundedCornerShape(22.dp))
                        .background(selectedTabBackground)
                        .align(Alignment.CenterStart)
                )

                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 4.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    activeTabs.forEachIndexed { index, tab ->
                        val isSelected = index == selectedTabIndex
                        val iconToDisplay = if (isSelected) tab.filledIcon else tab.outlineIcon
                        val iconColor = if (isSelected) activeIconColor else inactiveIconColor

                        val iconBounceScale by animateFloatAsState(
                            targetValue = if (isSelected) 1.08f else 1.0f,
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                stiffness = Spring.StiffnessMedium
                            ),
                            label = "iconBounceScale"
                        )

                        val tabModifier = if (tab == NavTab.PROFILE) {
                            Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .pointerInput(Unit) {
                                    detectTapGestures(
                                        onPress = {
                                            var settingsOpened = false
                                            val animJob = scope.launch {
                                                try {
                                                    // Must hold for 400ms before starting settings open process/animation
                                                    delay(400L)
                                                    longPressProgressAnim.animateTo(
                                                        targetValue = 1f,
                                                        animationSpec = tween(
                                                            durationMillis = 800,
                                                            easing = LinearEasing
                                                        )
                                                    )
                                                    settingsOpened = true
                                                    HapticHelper.performLongPress(context, view)
                                                    longPressProgressAnim.snapTo(0f)
                                                    onOpenSettings()
                                                } catch (_: CancellationException) {
                                                    longPressProgressAnim.snapTo(0f)
                                                } finally {
                                                    longPressProgressAnim.snapTo(0f)
                                                }
                                            }

                                            val released = tryAwaitRelease()
                                            if (!settingsOpened) {
                                                animJob.cancel()
                                                scope.launch { longPressProgressAnim.snapTo(0f) }
                                                if (released) {
                                                    HapticHelper.performClick(context, view)
                                                    onOpenProfile()
                                                    onNavClick?.invoke("profile")
                                                }
                                            }
                                        }
                                    )
                                }
                        } else {
                            Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .pointerInput(isSelected) {
                                    detectTapGestures(
                                        onTap = {
                                            val now = SystemClock.uptimeMillis()
                                            HapticHelper.performClick(context, view)

                                            when (tab) {
                                                NavTab.HOME -> {
                                                    if (isSelected && (now - lastHomeClickTime < 400L)) {
                                                        onRefreshHome()
                                                        lastHomeClickTime = 0L
                                                    } else {
                                                        lastHomeClickTime = now
                                                        if (!isSelected) {
                                                            onNavigate(tab.targetPath)
                                                            onNavClick?.invoke(tab.targetKey)
                                                        }
                                                    }
                                                }
                                                NavTab.REELS -> {
                                                    if (isSelected && (now - lastReelsClickTime < 400L)) {
                                                        onRefreshReels()
                                                        lastReelsClickTime = 0L
                                                    } else {
                                                        lastReelsClickTime = now
                                                        if (!isSelected) {
                                                            onNavigate(tab.targetPath)
                                                            onNavClick?.invoke(tab.targetKey)
                                                        }
                                                    }
                                                }
                                                NavTab.SETTINGS -> onOpenSettings()
                                                else -> {
                                                    onNavigate(tab.targetPath)
                                                    onNavClick?.invoke(tab.targetKey)
                                                }
                                            }
                                        }
                                    )
                                }
                        }

                        Box(
                            modifier = tabModifier,
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = iconToDisplay,
                                contentDescription = tab.title,
                                tint = iconColor,
                                modifier = Modifier
                                    .size(24.dp)
                                    .scale(iconBounceScale)
                            )
                        }
                    }
                }

                // Profile long-press clockwise perimeter border indicator starting above Home icon
                val currentProgress = longPressProgressAnim.value
                if (currentProgress > 0f) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val fullPath = Path().apply {
                            moveTo(startXPx, 0f)
                            lineTo(totalWidthPx - cornerRadiusPx, 0f)
                            arcTo(
                                rect = Rect(totalWidthPx - 2 * cornerRadiusPx, 0f, totalWidthPx, 2 * cornerRadiusPx),
                                startAngleDegrees = 270f,
                                sweepAngleDegrees = 90f,
                                forceMoveTo = false
                            )
                            lineTo(totalWidthPx, totalHeightPx - cornerRadiusPx)
                            arcTo(
                                rect = Rect(totalWidthPx - 2 * cornerRadiusPx, totalHeightPx - 2 * cornerRadiusPx, totalWidthPx, totalHeightPx),
                                startAngleDegrees = 0f,
                                sweepAngleDegrees = 90f,
                                forceMoveTo = false
                            )
                            lineTo(cornerRadiusPx, totalHeightPx)
                            arcTo(
                                rect = Rect(0f, totalHeightPx - 2 * cornerRadiusPx, 2 * cornerRadiusPx, totalHeightPx),
                                startAngleDegrees = 90f,
                                sweepAngleDegrees = 90f,
                                forceMoveTo = false
                            )
                            lineTo(0f, cornerRadiusPx)
                            arcTo(
                                rect = Rect(0f, 0f, 2 * cornerRadiusPx, 2 * cornerRadiusPx),
                                startAngleDegrees = 180f,
                                sweepAngleDegrees = 90f,
                                forceMoveTo = false
                            )
                            lineTo(startXPx, 0f)
                            close()
                        }

                        val pathMeasure = PathMeasure()
                        pathMeasure.setPath(fullPath, false)
                        val totalLen = pathMeasure.length
                        val segmentPath = Path()
                        pathMeasure.getSegment(0f, totalLen * currentProgress, segmentPath, true)

                        drawPath(
                            path = segmentPath,
                            brush = Brush.linearGradient(
                                colors = listOf(
                                    Color(0xFF833AB4),
                                    Color(0xFFFD1D1D),
                                    Color(0xFFF77737),
                                    Color(0xFFFFDC80)
                                )
                            ),
                            style = Stroke(
                                width = 3.dp.toPx(),
                                cap = StrokeCap.Round
                            )
                        )
                    }
                }

                val currentDlProgress = animatedDownloadProgress
                val currentGlow = glowAnim.value
                if (currentDlProgress > 0f || currentGlow > 0f) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val fullPath = Path().apply {
                            moveTo(startXPx, 0f)
                            lineTo(totalWidthPx - cornerRadiusPx, 0f)
                            arcTo(
                                rect = Rect(totalWidthPx - 2 * cornerRadiusPx, 0f, totalWidthPx, 2 * cornerRadiusPx),
                                startAngleDegrees = 270f,
                                sweepAngleDegrees = 90f,
                                forceMoveTo = false
                            )
                            lineTo(totalWidthPx, totalHeightPx - cornerRadiusPx)
                            arcTo(
                                rect = Rect(totalWidthPx - 2 * cornerRadiusPx, totalHeightPx - 2 * cornerRadiusPx, totalWidthPx, totalHeightPx),
                                startAngleDegrees = 0f,
                                sweepAngleDegrees = 90f,
                                forceMoveTo = false
                            )
                            lineTo(cornerRadiusPx, totalHeightPx)
                            arcTo(
                                rect = Rect(0f, totalHeightPx - 2 * cornerRadiusPx, 2 * cornerRadiusPx, totalHeightPx),
                                startAngleDegrees = 90f,
                                sweepAngleDegrees = 90f,
                                forceMoveTo = false
                            )
                            lineTo(0f, cornerRadiusPx)
                            arcTo(
                                rect = Rect(0f, 0f, 2 * cornerRadiusPx, 2 * cornerRadiusPx),
                                startAngleDegrees = 180f,
                                sweepAngleDegrees = 90f,
                                forceMoveTo = false
                            )
                            lineTo(startXPx, 0f)
                            close()
                        }

                        val blueGradient = Brush.linearGradient(
                            colors = listOf(
                                Color(0xFF00E5FF),
                                Color(0xFF0091FF),
                                Color(0xFF0055FF),
                                Color(0xFF00D2FF)
                            )
                        )
                        val failureGradient = Brush.linearGradient(
                            colors = listOf(
                                Color(0xFFFF3B30),
                                Color(0xFFCC0000),
                                Color(0xFF8B0000),
                                Color(0xFFFF3B30)
                            )
                        )

                        if (currentDlProgress > 0f && currentGlow == 0f) {
                            val pathMeasure = PathMeasure()
                            pathMeasure.setPath(fullPath, false)
                            val totalLen = pathMeasure.length
                            val segmentPath = Path()
                            pathMeasure.getSegment(0f, totalLen * currentDlProgress.coerceIn(0f, 1f), segmentPath, true)

                            drawPath(
                                path = segmentPath,
                                brush = if (isDownloadFailed) failureGradient else blueGradient,
                                style = Stroke(
                                    width = 3.dp.toPx(),
                                    cap = StrokeCap.Round
                                )
                            )
                        }

                        if (currentGlow > 0f) {
                            drawPath(
                                path = fullPath,
                                color = Color(0xFF00D2FF).copy(alpha = 0.6f * currentGlow),
                                style = Stroke(
                                    width = (4.dp + 5.dp * currentGlow).toPx(),
                                    cap = StrokeCap.Round
                                )
                            )
                            drawPath(
                                path = fullPath,
                                brush = Brush.linearGradient(
                                    colors = listOf(
                                        Color(0xFF80EEFF).copy(alpha = currentGlow),
                                        Color(0xFF00B4FF).copy(alpha = currentGlow),
                                        Color(0xFF0066FF).copy(alpha = currentGlow),
                                        Color(0xFF80EEFF).copy(alpha = currentGlow)
                                    )
                                ),
                                style = Stroke(
                                    width = 3.2.dp.toPx(),
                                    cap = StrokeCap.Round
                                )
                            )
                        }
                    }
                }

                // Active session perimeter progress indicator (golden sweep along the perimeter)
                val currentSessionProgress = animatedSessionProgress
                if (settings.showSessionTimeInBottomBar && currentSessionProgress > 0f && currentProgress == 0f && currentDlProgress == 0f && currentGlow == 0f) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val fullPath = Path().apply {
                            moveTo(startXPx, 0f)
                            lineTo(totalWidthPx - cornerRadiusPx, 0f)
                            arcTo(
                                rect = Rect(totalWidthPx - 2 * cornerRadiusPx, 0f, totalWidthPx, 2 * cornerRadiusPx),
                                startAngleDegrees = 270f,
                                sweepAngleDegrees = 90f,
                                forceMoveTo = false
                            )
                            lineTo(totalWidthPx, totalHeightPx - cornerRadiusPx)
                            arcTo(
                                rect = Rect(totalWidthPx - 2 * cornerRadiusPx, totalHeightPx - 2 * cornerRadiusPx, totalWidthPx, totalHeightPx),
                                startAngleDegrees = 0f,
                                sweepAngleDegrees = 90f,
                                forceMoveTo = false
                            )
                            lineTo(cornerRadiusPx, totalHeightPx)
                            arcTo(
                                rect = Rect(0f, totalHeightPx - 2 * cornerRadiusPx, 2 * cornerRadiusPx, totalHeightPx),
                                startAngleDegrees = 90f,
                                sweepAngleDegrees = 90f,
                                forceMoveTo = false
                            )
                            lineTo(0f, cornerRadiusPx)
                            arcTo(
                                rect = Rect(0f, 0f, 2 * cornerRadiusPx, 2 * cornerRadiusPx),
                                startAngleDegrees = 180f,
                                sweepAngleDegrees = 90f,
                                forceMoveTo = false
                            )
                            lineTo(startXPx, 0f)
                            close()
                        }

                        val pathMeasure = PathMeasure()
                        pathMeasure.setPath(fullPath, false)
                        val totalLen = pathMeasure.length
                        val segmentPath = Path()
                        pathMeasure.getSegment(0f, totalLen * currentSessionProgress.coerceIn(0f, 1f), segmentPath, true)

                        val p = currentSessionProgress.coerceIn(0f, 1f)
                        // Sunset effect: as session approaches completion, colors evolve from noon gold into warm sunset reddish-orange
                        val sunsetStart = lerp(Color(0xFFFFE57F), Color(0xFFFF8A65), p)
                        val sunsetMid1 = lerp(Color(0xFFFFD700), Color(0xFFFF7043), p)
                        val sunsetMid2 = lerp(Color(0xFFFFB300), Color(0xFFFF5722), p)
                        val sunsetEnd = lerp(Color(0xFFFFA000), Color(0xFFD84315), p)

                        drawPath(
                            path = segmentPath,
                            brush = Brush.linearGradient(
                                colors = listOf(sunsetStart, sunsetMid1, sunsetMid2, sunsetEnd)
                            ),
                            style = Stroke(
                                width = 3.dp.toPx(),
                                cap = StrokeCap.Round
                            )
                        )
                    }
                }
            }
        }
    }
}
