package com.ujwal.focusgram.feature.navigation

import android.os.SystemClock
import android.view.HapticFeedbackConstants
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import com.ujwal.focusgram.core.model.FocusSettings
import com.ujwal.focusgram.core.model.IslandMode
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
 * NativeBottomNavBar
 * Authentic OLED navigation island with iOS-style fluid physics and Instagram iconography.
 * Features:
 * - Real-time light/dark theme adaptation from in-page GeckoView state.
 * - Sliding capsule pill indicator and spring icon bounce for fluid tab transitions.
 * - Double-click detection on Home & Reels to refresh in-place and scroll to top.
 * - Profile long-press (1100ms) with clockwise perimeter loading bar starting above the Home icon.
 * - Auto-hide in DMs, comments, search modals, and soft keyboard appearances.
 */
@Composable
fun NativeBottomNavBar(
    currentUrl: String,
    settings: FocusSettings,
    islandMode: IslandMode = IslandMode.NORMAL,
    instagramTheme: String = "auto",
    isWebInputFocused: Boolean = false,
    isModalActive: Boolean = false,
    onNavClick: ((String) -> Unit)? = null,
    onNavigate: (String) -> Unit,
    onOpenProfile: () -> Unit,
    onOpenSettings: () -> Unit,
    onRefreshHome: () -> Unit = {},
    onRefreshReels: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val view = LocalView.current
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val isImeVisible = WindowInsets.ime.getBottom(density) > 0
    val lowerUrl = currentUrl.lowercase()
    val isInDirect = lowerUrl.contains("/direct/t/")

    val activeTabs = remember(settings.noReels, settings.noDMs) {
        listOfNotNull(
            NavTab.HOME,
            if (!settings.noReels) NavTab.REELS else null,
            if (!settings.noDMs) NavTab.MESSAGES else null,
            NavTab.SEARCH,
            NavTab.PROFILE
        )
    }

    val navShape = RoundedCornerShape(32.dp)
    val isVisible = !isImeVisible && !isInDirect && !isWebInputFocused && !isModalActive && islandMode != IslandMode.HIDDEN

    // Real-time animated color palette matching Instagram's web theme
    val isSystemDark = isSystemInDarkTheme()
    val isLight = when (instagramTheme.lowercase()) {
        "light" -> true
        "dark" -> false
        else -> !isSystemDark
    }
    val islandBackground by animateColorAsState(
        targetValue = if (islandMode == IslandMode.FLOATING) {
            if (isLight) Color(0xF2FFFFFF) else Color(0xD9101012)
        } else {
            if (isLight) Color(0xF5FFFFFF) else Color(0xF2141416)
        },
        animationSpec = tween(300),
        label = "islandBackground"
    )
    val borderColor by animateColorAsState(
        targetValue = if (isLight) Color(0x1F000000) else Color(0x33FFFFFF),
        animationSpec = tween(300),
        label = "borderColor"
    )
    val selectedTabBackground by animateColorAsState(
        targetValue = if (isLight) Color(0xFFE5E5EA) else Color(0xFF2C2C30),
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

    // Determine currently active tab index
    val selectedTabIndex = activeTabs.indexOfFirst { tab ->
        when (tab) {
            NavTab.HOME -> lowerUrl == "https://www.instagram.com/" || lowerUrl.endsWith("instagram.com/") || lowerUrl == "/"
            NavTab.SEARCH -> lowerUrl.contains("/explore")
            NavTab.REELS -> lowerUrl.contains("/reel")
            NavTab.MESSAGES -> lowerUrl.contains("/direct") || lowerUrl.contains("/inbox")
            NavTab.PROFILE -> !lowerUrl.contains("/explore") && !lowerUrl.contains("/reel") && !lowerUrl.contains("/direct") &&
                    (lowerUrl.contains("/profile") || (!lowerUrl.endsWith("instagram.com/") && !lowerUrl.endsWith("instagram.com") && lowerUrl != "/"))
            NavTab.SETTINGS -> false
        }
    }.coerceAtLeast(0)

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
                        elevation = if (isLight) 8.dp else 4.dp,
                        shape = navShape,
                        spotColor = if (isLight) Color(0x33000000) else Color.Transparent
                    )
                    .clip(navShape)
                    .background(islandBackground)
                    .border(BorderStroke(1.dp, borderColor), navShape),
                contentAlignment = Alignment.Center
            ) {
                val totalWidthPx = constraints.maxWidth.toFloat()
                val totalHeightPx = constraints.maxHeight.toFloat()
                val tabCount = activeTabs.size
                val tabWidthDp = maxWidth / tabCount
                val startXPx = (totalWidthPx / tabCount) / 2f
                val cornerRadiusPx = with(density) { min(32.dp.toPx(), totalHeightPx / 2f) }

                // 1. Sliding Capsule Indicator (iOS-style physics)
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

                // 2. Tab Items Row
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
                                                    view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
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
                                                    view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
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
                                            view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)

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

                // 3. Profile Long-Press Animated Clockwise Perimeter Border Indicator
                val currentProgress = longPressProgressAnim.value
                if (currentProgress > 0f) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val fullPath = Path().apply {
                            moveTo(startXPx, 0f)
                            // Top edge to top-right
                            lineTo(totalWidthPx - cornerRadiusPx, 0f)
                            // Top-right corner arc
                            arcTo(
                                rect = Rect(totalWidthPx - 2 * cornerRadiusPx, 0f, totalWidthPx, 2 * cornerRadiusPx),
                                startAngleDegrees = 270f,
                                sweepAngleDegrees = 90f,
                                forceMoveTo = false
                            )
                            // Right edge going down
                            lineTo(totalWidthPx, totalHeightPx - cornerRadiusPx)
                            // Bottom-right corner arc
                            arcTo(
                                rect = Rect(totalWidthPx - 2 * cornerRadiusPx, totalHeightPx - 2 * cornerRadiusPx, totalWidthPx, totalHeightPx),
                                startAngleDegrees = 0f,
                                sweepAngleDegrees = 90f,
                                forceMoveTo = false
                            )
                            // Bottom edge to bottom-left
                            lineTo(cornerRadiusPx, totalHeightPx)
                            // Bottom-left corner arc
                            arcTo(
                                rect = Rect(0f, totalHeightPx - 2 * cornerRadiusPx, 2 * cornerRadiusPx, totalHeightPx),
                                startAngleDegrees = 90f,
                                sweepAngleDegrees = 90f,
                                forceMoveTo = false
                            )
                            // Left edge going up
                            lineTo(0f, cornerRadiusPx)
                            // Top-left corner arc
                            arcTo(
                                rect = Rect(0f, 0f, 2 * cornerRadiusPx, 2 * cornerRadiusPx),
                                startAngleDegrees = 180f,
                                sweepAngleDegrees = 90f,
                                forceMoveTo = false
                            )
                            // Top edge returning to Home start point
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
                                    Color(0xFF833AB4), // Instagram Purple
                                    Color(0xFFFD1D1D), // Instagram Red
                                    Color(0xFFF77737), // Instagram Orange
                                    Color(0xFFFFDC80)  // Instagram Yellow
                                )
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
