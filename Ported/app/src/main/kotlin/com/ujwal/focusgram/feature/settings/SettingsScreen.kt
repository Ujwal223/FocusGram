package com.ujwal.focusgram.feature.settings

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RemoveRedEye
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ujwal.focusgram.core.datastore.SettingsRepository
import com.ujwal.focusgram.core.model.FocusSettings
import com.ujwal.focusgram.feature.session.AppSessionPicker
import com.ujwal.focusgram.core.ui.components.IOSBg
import com.ujwal.focusgram.core.ui.components.IOSBlue
import com.ujwal.focusgram.core.ui.components.IOSDivider
import com.ujwal.focusgram.core.ui.components.IOSGreen
import com.ujwal.focusgram.core.ui.components.IOSGroupedCard
import com.ujwal.focusgram.core.ui.components.IOSOrange
import com.ujwal.focusgram.core.ui.components.IOSPurple
import com.ujwal.focusgram.core.ui.components.IOSRed
import com.ujwal.focusgram.core.ui.components.IOSSectionTitle
import com.ujwal.focusgram.core.ui.components.IOSSettingsRow
import com.ujwal.focusgram.core.ui.components.IOSTeal
import com.ujwal.focusgram.core.ui.components.IOSTextPrimary
import com.ujwal.focusgram.core.ui.components.IOSTextSecondary
import kotlinx.coroutines.launch

/**
 * SettingsScreen
 * Redesigned to match the official iOS Instagram "Settings and activity" interface.
 * Features authentic iOS grouped tables, category headers, smooth iOS switches,
 * Ghost Mode (adapted from Ghostify), and Apple Clock session pickers.
 */
@Composable
fun SettingsScreen(
    repository: SettingsRepository,
    onBack: () -> Unit,
    onNavigateReelsHistory: () -> Unit,
    onNavigateScreenTime: () -> Unit,
    onNavigateLevelPanel: () -> Unit = {},
    onNavigateAppLock: () -> Unit,
    onWatchAd: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val settings by repository.settingsFlow.collectAsState(initial = FocusSettings())
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var showTimePickerModal by remember { mutableStateOf(false) }

    if (showTimePickerModal) {
        AppSessionPicker(
            onSessionSelected = { mins: Int ->
                scope.launch { repository.setDailyLimitMinutes(mins) }
                showTimePickerModal = false
            },
            onDismiss = { showTimePickerModal = false }
        )
        return
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(IOSBg)
            .padding(WindowInsets.statusBars.asPaddingValues())
    ) {
        // iOS Navigation Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = IOSTextPrimary
                )
            }
            Text(
                text = "Settings and activity",
                color = IOSTextPrimary,
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(start = 4.dp)
            )
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
        ) {
            // Voluntary Support Banner with Watch Ad & Donate options
            item {
                IOSGroupedCard(modifier = Modifier.padding(top = 10.dp)) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(42.dp)
                                    .clip(CircleShape)
                                    .background(IOSOrange.copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Favorite,
                                    contentDescription = null,
                                    tint = IOSOrange,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Support FocusGram",
                                    color = IOSTextPrimary,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "100% free and open-source. Help support development voluntarily!",
                                    color = IOSTextSecondary,
                                    fontSize = 12.sp,
                                    lineHeight = 16.sp
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Button(
                                onClick = onWatchAd,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(42.dp),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = IOSBlue,
                                    contentColor = Color.White
                                )
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.PlayArrow,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp),
                                        tint = Color.White
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "Watch Ad",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = Color.White
                                    )
                                }
                            }

                            OutlinedButton(
                                onClick = {
                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://buymemomo.com/ujwal"))
                                    context.startActivity(intent)
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(42.dp),
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, IOSOrange),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = IOSOrange
                                )
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Favorite,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp),
                                        tint = IOSOrange
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "Donate",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = IOSOrange
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // SECTION 1: GHOST MODE & PRIVACY (from Ghostify)
            item {
                IOSSectionTitle(title = "Ghost Mode & Stealth (Ghostify)")
                IOSGroupedCard {
                    IOSSettingsRow(
                        title = "Ghost Mode Master",
                        subtitle = "Enable comprehensive stealth browsing on Instagram",
                        icon = Icons.Default.VisibilityOff,
                        iconBgColor = IOSPurple,
                        checked = settings.ghostModeEnabled,
                        onCheckedChange = { scope.launch { repository.setGhostMode(it) } }
                    )
                    IOSSettingsRow(
                        title = "Anonymous Story Views",
                        subtitle = "View stories without sending view receipts",
                        icon = Icons.Default.RemoveRedEye,
                        iconBgColor = IOSPurple,
                        checked = settings.ghostHideStorySeen,
                        onCheckedChange = { scope.launch { repository.setGhostHideStorySeen(it) } }
                    )
                    IOSSettingsRow(
                        title = "Incognito Direct Messages",
                        subtitle = "Read DMs without triggering 'Seen' status",
                        icon = Icons.Default.Security,
                        iconBgColor = IOSPurple,
                        checked = settings.ghostHideDirectSeen,
                        onCheckedChange = { scope.launch { repository.setGhostHideDirectSeen(it) } }
                    )
                    IOSSettingsRow(
                        title = "Invisible Typing",
                        subtitle = "Suppress 'typing...' indicators while chatting",
                        icon = Icons.Default.Lock,
                        iconBgColor = IOSPurple,
                        checked = settings.ghostHideTypingIndicator,
                        onCheckedChange = { scope.launch { repository.setGhostHideTypingIndicator(it) } },
                        showDivider = false
                    )
                }
            }

            // SECTION 2: HOW YOU USE INSTAGRAM (Focus & Mindfulness)
            item {
                IOSSectionTitle(title = "How you use Instagram")
                IOSGroupedCard {
                    IOSSettingsRow(
                        title = "Daily Limit Timer",
                        subtitle = "Set intention timer using Apple Clock wheel",
                        icon = Icons.Default.Timer,
                        iconBgColor = IOSOrange,
                        trailingText = "${settings.dailyLimitMinutes} min",
                        onClick = { showTimePickerModal = true }
                    )
                    IOSSettingsRow(
                        title = "Mindful Breath Gate",
                        subtitle = "Take a breath pause before entering",
                        icon = Icons.Default.Notifications,
                        iconBgColor = IOSTeal,
                        checked = settings.showBreathGate,
                        onCheckedChange = { scope.launch { repository.setBreathGate(it, settings.breathGateSeconds) } }
                    )
                    IOSSettingsRow(
                        title = "Effort Friction Challenge",
                        subtitle = "Require typing focus words before unlock",
                        icon = Icons.Default.Lock,
                        iconBgColor = IOSBlue,
                        checked = settings.effortFrictionEnabled,
                        onCheckedChange = { scope.launch { repository.setEffortFriction(it, settings.wordChallengeCount) } }
                    )
                    IOSSettingsRow(
                        title = "Grayscale Mode",
                        subtitle = "Strip high-dopamine feed colors",
                        icon = Icons.Default.DarkMode,
                        iconBgColor = Color(0xFF636366),
                        checked = settings.grayscaleEnabled,
                        onCheckedChange = { scope.launch { repository.setGrayscale(it) } },
                        showDivider = false
                    )
                }
            }

            // SECTION 3: WHAT YOU SEE (Feed & Content)
            item {
                IOSSectionTitle(title = "What you see")
                IOSGroupedCard {
                    IOSSettingsRow(
                        title = "Ad Blocker (uBlock Origin)",
                        subtitle = "Block sponsored ads and tracking banners",
                        icon = Icons.Default.Block,
                        iconBgColor = IOSGreen,
                        checked = settings.noAds,
                        onCheckedChange = { scope.launch { repository.setNoAds(it) } }
                    )
                    IOSSettingsRow(
                        title = "Hide Stories Tray",
                        subtitle = "Eliminate stories carousel at the top of the feed",
                        icon = Icons.Default.PlayArrow,
                        iconBgColor = IOSRed,
                        checked = settings.noStories,
                        onCheckedChange = { scope.launch { repository.setNoStories(it) } }
                    )
                    IOSSettingsRow(
                        title = "Block Reels & Short Videos",
                        subtitle = "Remove addictive algorithmic short clips",
                        icon = Icons.Default.Block,
                        iconBgColor = IOSRed,
                        checked = settings.noReels,
                        onCheckedChange = { scope.launch { repository.setNoReels(it) } }
                    )
                    IOSSettingsRow(
                        title = "Hide Suggested Posts",
                        subtitle = "Only show posts from accounts you follow",
                        icon = Icons.Default.Star,
                        iconBgColor = IOSOrange,
                        checked = settings.hideSuggestedPosts,
                        onCheckedChange = { scope.launch { repository.setHideSuggestedPosts(it) } }
                    )
                    IOSSettingsRow(
                        title = "Blur Explore Grid",
                        subtitle = "Blur explore thumbnails until tapped",
                        icon = Icons.Default.Visibility,
                        iconBgColor = IOSTeal,
                        checked = settings.blurExplore,
                        onCheckedChange = { scope.launch { repository.setBlurExplore(it) } }
                    )
                    IOSSettingsRow(
                        title = "Block Video Autoplay",
                        subtitle = "Stop videos from automatically streaming",
                        icon = Icons.Default.PlayArrow,
                        iconBgColor = IOSBlue,
                        checked = settings.noAutoplay,
                        onCheckedChange = { scope.launch { repository.setNoAutoplay(it) } },
                        showDivider = false
                    )
                }
            }

            // SECTION 4: WHAT OTHERS SEE & VANITY METRICS
            item {
                IOSSectionTitle(title = "What others see & Vanity metrics")
                IOSGroupedCard {
                    IOSSettingsRow(
                        title = "Hide Like Counts",
                        subtitle = "Remove like numbers on posts and reels",
                        icon = Icons.Default.Favorite,
                        iconBgColor = IOSRed,
                        checked = settings.hideLikeCounts,
                        onCheckedChange = { scope.launch { repository.setHideLikeCounts(it) } }
                    )
                    IOSSettingsRow(
                        title = "Hide Follower Counts",
                        subtitle = "Conceal follower statistics on profile views",
                        icon = Icons.Default.Star,
                        iconBgColor = IOSOrange,
                        checked = settings.hideFollowerCounts,
                        onCheckedChange = { scope.launch { repository.setHideFollowerCounts(it) } }
                    )
                    IOSSettingsRow(
                        title = "Hide Shop Tab",
                        subtitle = "Remove consumer shopping suggestions",
                        icon = Icons.Default.ShoppingBag,
                        iconBgColor = IOSBlue,
                        checked = settings.hideShopTab,
                        onCheckedChange = { scope.launch { repository.setHideShopTab(it) } }
                    )
                    IOSSettingsRow(
                        title = "Minimal Mode",
                        subtitle = "Ultra-distraction-free barebones layout",
                        icon = Icons.Default.DarkMode,
                        iconBgColor = Color(0xFF636366),
                        checked = settings.minimalModeEnabled,
                        onCheckedChange = { scope.launch { repository.setMinimalMode(it) } },
                        showDivider = false
                    )
                }
            }

            // SECTION 5: SECURITY & APP DATA
            item {
                IOSSectionTitle(title = "Security & Insights")
                IOSGroupedCard {
                    IOSSettingsRow(
                        title = "App Lock & Biometrics",
                        subtitle = "Require PIN or Fingerprint on app open",
                        icon = Icons.Default.Fingerprint,
                        iconBgColor = IOSGreen,
                        trailingText = if (settings.appLockEnabled) "Enabled" else "Off",
                        onClick = onNavigateAppLock
                    )
                    IOSSettingsRow(
                        title = "Reels Watch History",
                        subtitle = "View and export reels you previously viewed",
                        icon = Icons.Default.History,
                        iconBgColor = IOSBlue,
                        onClick = onNavigateReelsHistory,
                        showDivider = false
                    )
                }
            }

            // SECTION 6: ABOUT & LEGAL
            item {
                IOSSectionTitle(title = "About FocusGram")
                IOSGroupedCard {
                    IOSSettingsRow(
                        title = "License & Legal",
                        subtitle = "AGPL-3.0 Free and Open Source Software",
                        icon = Icons.Default.Info,
                        iconBgColor = Color(0xFF636366),
                        trailingText = "v2.0.0",
                        onClick = {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/Ujwal223/FocusGram"))
                            context.startActivity(intent)
                        },
                        showDivider = false
                    )
                }
            }

            item {
                Spacer(modifier = Modifier.height(36.dp))
            }
        }
    }
}
