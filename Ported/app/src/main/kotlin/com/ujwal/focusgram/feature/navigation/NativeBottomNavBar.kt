package com.ujwal.focusgram.feature.navigation

import android.view.HapticFeedbackConstants
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import com.ujwal.focusgram.core.model.FocusSettings
import com.ujwal.focusgram.core.model.IslandMode
import com.ujwal.focusgram.core.ui.theme.DarkBorder
import com.ujwal.focusgram.core.ui.theme.tactilePress

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
 * Authentic OLED navigation island featuring official Instagram outline/filled iconography.
 * Implements Senior Blueprint:
 * - Edge-to-edge floating island overlay over full-screen GeckoView.
 * - Route-driven visibility (NORMAL, FLOATING for Reels, HIDDEN for DMs/Comments/Stories/IME).
 * - React SPA client-side anchor click delegation to prevent full-page reloads.
 */
@Composable
fun NativeBottomNavBar(
    currentUrl: String,
    settings: FocusSettings,
    islandMode: IslandMode = IslandMode.NORMAL,
    isWebInputFocused: Boolean = false,
    isModalActive: Boolean = false,
    onNavClick: ((String) -> Unit)? = null,
    onNavigate: (String) -> Unit,
    onOpenProfile: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val view = LocalView.current
    val density = LocalDensity.current
    val isImeVisible = WindowInsets.ime.getBottom(density) > 0
    val lowerUrl = currentUrl.lowercase()
    val isInDirect = lowerUrl.contains("/direct")

    val activeTabs = listOf(
        NavTab.HOME,
        NavTab.SEARCH,
        NavTab.REELS,
        NavTab.MESSAGES,
        NavTab.PROFILE
    )

    val navShape = RoundedCornerShape(32.dp)

    // The island is visible on main pages (Home, Explore, Reels, Profile)
    // Automatically slides away when soft keyboard is up, or user is in DMs, comments, or modal forms
    val isVisible = !isImeVisible && !isInDirect && !isWebInputFocused && !isModalActive && islandMode != IslandMode.HIDDEN

    val islandBackground = if (islandMode == IslandMode.FLOATING) {
        Color(0x99141416) // Translucent floating pill for Reels
    } else {
        Color(0xD9141416) // Liquid Glass dark translucent background
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
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .clip(navShape)
                    .background(islandBackground)
                    .border(BorderStroke(1.dp, Color(0x33FFFFFF)), navShape) // Frosted glass highlight border
                    .padding(horizontal = 6.dp, vertical = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceAround,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    activeTabs.forEach { tab ->
                        val isSelected = when (tab) {
                            NavTab.HOME -> lowerUrl == "https://www.instagram.com/" || lowerUrl.endsWith("instagram.com/") || lowerUrl == "/"
                            NavTab.SEARCH -> lowerUrl.contains("/explore")
                            NavTab.REELS -> lowerUrl.contains("/reel")
                            NavTab.MESSAGES -> lowerUrl.contains("/direct") || lowerUrl.contains("/inbox")
                            NavTab.PROFILE -> !lowerUrl.contains("/explore") && !lowerUrl.contains("/reel") && !lowerUrl.contains("/direct") && (lowerUrl.contains("/profile") || (!lowerUrl.endsWith("instagram.com/") && !lowerUrl.endsWith("instagram.com") && lowerUrl != "/"))
                            NavTab.SETTINGS -> false
                        }

                        val iconToDisplay = if (isSelected) tab.filledIcon else tab.outlineIcon
                        val iconColor = if (isSelected) Color.White else Color.White.copy(alpha = 0.65f)

                        Box(
                            modifier = Modifier
                                .height(44.dp)
                                .clip(RoundedCornerShape(22.dp))
                                .background(if (isSelected) Color(0xFF2C2C30) else Color.Transparent)
                                .padding(horizontal = if (isSelected) 14.dp else 8.dp)
                                .tactilePress {
                                    view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                                    when (tab) {
                                        NavTab.SETTINGS -> onOpenSettings()
                                        NavTab.PROFILE -> {
                                            if (onNavClick != null) onNavClick("profile") else onOpenProfile()
                                        }
                                        else -> {
                                            if (onNavClick != null) onNavClick(tab.targetKey) else onNavigate(tab.targetPath)
                                        }
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = iconToDisplay,
                                contentDescription = tab.title,
                                tint = iconColor,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
