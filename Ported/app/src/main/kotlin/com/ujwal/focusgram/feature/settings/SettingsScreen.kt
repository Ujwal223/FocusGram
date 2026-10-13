package com.ujwal.focusgram.feature.settings

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
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
import com.ujwal.focusgram.core.ui.components.CupertinoOptionWheelPicker
import com.ujwal.focusgram.core.ui.components.IOSBg
import com.ujwal.focusgram.core.ui.components.IOSBlue
import com.ujwal.focusgram.core.ui.components.IOSDivider
import com.ujwal.focusgram.core.ui.components.IOSGreen
import com.ujwal.focusgram.core.ui.components.IOSGroupBg
import com.ujwal.focusgram.core.ui.components.IOSGroupedCard
import com.ujwal.focusgram.core.ui.components.IOSOrange
import com.ujwal.focusgram.core.ui.components.IOSPurple
import com.ujwal.focusgram.core.ui.components.IOSRed
import com.ujwal.focusgram.core.ui.components.IOSSectionTitle
import com.ujwal.focusgram.core.ui.components.IOSSettingsRow
import com.ujwal.focusgram.core.ui.components.IOSTeal
import com.ujwal.focusgram.core.ui.components.IOSTextPrimary
import com.ujwal.focusgram.core.ui.components.IOSTextSecondary
import com.ujwal.focusgram.feature.session.AppSessionPicker
import kotlinx.coroutines.launch

enum class SettingsSubpage {
    MAIN,
    FOCUS,
    GUARDRAILS,
    EXTRAS,
    APPEARANCE,
    NOTIFICATIONS,
    GHOST
}

/**
 * SettingsScreen
 * Redesigned to match the official iOS grouped Settings hierarchy with modular subpages.
 * Provides authentic Apple monochrome aesthetics (black, white, gray variants),
 * grouped category rows, and modular sub-screens ported from Flutter.
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
    onNavigateUrl: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val settings by repository.settingsFlow.collectAsState(initial = FocusSettings())
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    var currentSubpage by remember { mutableStateOf(SettingsSubpage.MAIN) }
    var showLegalDialog by remember { mutableStateOf(false) }
    var showLicenseDialog by remember { mutableStateOf(false) }

    // Intercept back presses: go back to MAIN subpage first, or pop screen
    BackHandler {
        if (currentSubpage != SettingsSubpage.MAIN) {
            currentSubpage = SettingsSubpage.MAIN
        } else {
            onBack()
        }
    }

    // Legal Disclaimer Dialog
    if (showLegalDialog) {
        AlertDialog(
            onDismissRequest = { showLegalDialog = false },
            containerColor = Color(0xFF1C1C1E),
            title = {
                Text(
                    text = "Legal Disclaimer",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
            },
            text = {
                Text(
                    text = "FocusGram is an independent digital wellness and focus tool. " +
                            "It is NOT affiliated with, sponsored by, or endorsed by Meta Platforms, Inc. or Instagram.\n\n" +
                            "FocusGram operates entirely client-side via Android System WebView technology. " +
                            "No private APIs, authentication bypasses, or credentials interception are used. " +
                            "All preferences are stored strictly on-device.",
                    color = Color(0xFF8E8E93),
                    fontSize = 14.sp,
                    lineHeight = 20.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = { showLegalDialog = false },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.White,
                        contentColor = Color.Black
                    ),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Text("Understood", fontWeight = FontWeight.Bold)
                }
            }
        )
    }

    // Licenses Dialog
    if (showLicenseDialog) {
        AlertDialog(
            onDismissRequest = { showLicenseDialog = false },
            containerColor = Color(0xFF1C1C1E),
            title = {
                Text(
                    text = "Open Source License",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
            },
            text = {
                Text(
                    text = "FocusGram is published under the GNU Affero General Public License v3.0 (AGPL-3.0).\n\n" +
                            "You are free to view, audit, study, and modify the source code on GitHub. " +
                            "Contains zero telemetry, zero analytics trackers, and zero proprietary ad SDKs.",
                    color = Color(0xFF8E8E93),
                    fontSize = 14.sp,
                    lineHeight = 20.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = { showLicenseDialog = false },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.White,
                        contentColor = Color.Black
                    ),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Text("OK", fontWeight = FontWeight.Bold)
                }
            }
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(IOSBg)
            .padding(WindowInsets.statusBars.asPaddingValues())
    ) {
        // Navigation Top Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = {
                if (currentSubpage != SettingsSubpage.MAIN) {
                    currentSubpage = SettingsSubpage.MAIN
                } else {
                    onBack()
                }
            }) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = IOSTextPrimary
                )
            }
            Text(
                text = when (currentSubpage) {
                    SettingsSubpage.MAIN -> "Settings and activity"
                    SettingsSubpage.FOCUS -> "Focus Mode"
                    SettingsSubpage.GUARDRAILS -> "Time Control & Guardrails"
                    SettingsSubpage.EXTRAS -> "Extras"
                    SettingsSubpage.APPEARANCE -> "Appearance"
                    SettingsSubpage.NOTIFICATIONS -> "Privacy & Notifications"
                    SettingsSubpage.GHOST -> "Ghost Mode"
                },
                color = IOSTextPrimary,
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(start = 4.dp)
            )
        }

        // Subpage Router
        when (currentSubpage) {
            SettingsSubpage.MAIN -> {
                MainSettingsContent(
                    settings = settings,
                    onNavigateSubpage = { currentSubpage = it },
                    onNavigateAppLock = onNavigateAppLock,
                    onNavigateScreenTime = onNavigateScreenTime,
                    onNavigateLevelPanel = onNavigateLevelPanel,
                    onNavigateReelsHistory = onNavigateReelsHistory,
                    onWatchAd = onWatchAd,
                    onShowLegal = { showLegalDialog = true },
                    onShowLicenses = { showLicenseDialog = true }
                )
            }
            SettingsSubpage.FOCUS -> {
                FocusModeSubpage(repository = repository, settings = settings)
            }
            SettingsSubpage.GUARDRAILS -> {
                GuardrailsSubpage(
                    repository = repository,
                    settings = settings
                )
            }
            SettingsSubpage.EXTRAS -> {
                ExtrasSubpage(
                    repository = repository,
                    settings = settings,
                    onNavigateUrl = { url ->
                        onNavigateUrl(url)
                        onBack()
                    }
                )
            }
            SettingsSubpage.APPEARANCE -> {
                AppearanceSubpage(repository = repository, settings = settings)
            }
            SettingsSubpage.NOTIFICATIONS -> {
                NotificationsSubpage(repository = repository, settings = settings)
            }
            SettingsSubpage.GHOST -> {
                GhostModeSubpage(repository = repository, settings = settings)
            }
        }
    }
}

// ─── MAIN SETTINGS SCREEN ───────────────────────────────────────────────────

@Composable
private fun MainSettingsContent(
    settings: FocusSettings,
    onNavigateSubpage: (SettingsSubpage) -> Unit,
    onNavigateAppLock: () -> Unit,
    onNavigateScreenTime: () -> Unit,
    onNavigateLevelPanel: () -> Unit,
    onNavigateReelsHistory: () -> Unit,
    onWatchAd: () -> Unit,
    onShowLegal: () -> Unit,
    onShowLicenses: () -> Unit
) {
    val context = LocalContext.current

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        // iOS Monochrome Voluntary Support Banner
        item {
            IOSGroupedCard(modifier = Modifier.padding(top = 10.dp)) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF2C2C2E))
                                .border(0.5.dp, Color(0xFF3A3A3C), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Favorite,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(22.dp)
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
                            shape = RoundedCornerShape(21.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color.White,
                                contentColor = Color.Black
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
                                    tint = Color.Black
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Watch Ad",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.Black
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
                            shape = RoundedCornerShape(21.dp),
                            border = BorderStroke(1.dp, Color(0xFF3A3A3C)),
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = Color(0xFF1C1C1E),
                                contentColor = Color.White
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
                                    tint = Color.White
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Donate",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color.White
                                )
                            }
                        }
                    }
                }
            }
        }

        // Quick Stats Badge Row (iOS Monochrome)
        item {
            IOSGroupedCard(modifier = Modifier.padding(top = 10.dp)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceAround,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(text = "${settings.instaMinutes}m", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Text(text = "App Time", color = Color(0xFF8E8E93), fontSize = 11.sp)
                    }
                    Box(modifier = Modifier.width(1.dp).height(24.dp).background(Color(0xFF2C2C2E)))
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(text = "${settings.reelsMinutes}m", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Text(text = "Reels Time", color = Color(0xFF8E8E93), fontSize = 11.sp)
                    }
                }
            }
        }

        // SECTION 1: FOCUS & WELLBEING
        item {
            IOSSectionTitle(title = "FOCUS & DIGITAL WELLBEING")
            IOSGroupedCard {
                IOSSettingsRow(
                    title = "Focus Mode",
                    subtitle = if (settings.minimalModeEnabled) "Minimal mode enabled" else "Distraction blocking, feed blur, content hider",
                    icon = Icons.Default.Block,
                    iconBgColor = IOSRed,
                    onClick = { onNavigateSubpage(SettingsSubpage.FOCUS) }
                )
                IOSSettingsRow(
                    title = "Time Control & Guardrails",
                    subtitle = "Daily limit timer, cooldown pause, mindful gates",
                    icon = Icons.Default.Timer,
                    iconBgColor = IOSOrange,
                    onClick = { onNavigateSubpage(SettingsSubpage.GUARDRAILS) },
                    showDivider = false
                )
            }
        }

        // SECTION 2: EXTRAS & NAVIGATION
        item {
            IOSSectionTitle(title = "EXTRAS")
            IOSGroupedCard {
                IOSSettingsRow(
                    title = "Extras",
                    subtitle = "Startup launch page, download media",
                    icon = Icons.Default.Star,
                    iconBgColor = IOSBlue,
                    onClick = { onNavigateSubpage(SettingsSubpage.EXTRAS) },
                    showDivider = false
                )
            }
        }

        // SECTION 3: APPEARANCE
        item {
            IOSSectionTitle(title = "APPEARANCE")
            IOSGroupedCard {
                IOSSettingsRow(
                    title = "Appearance",
                    subtitle = if (settings.grayscaleEnabled) "Grayscale on" else "Grayscale mode",
                    icon = Icons.Default.Palette,
                    iconBgColor = Color(0xFF636366),
                    onClick = { onNavigateSubpage(SettingsSubpage.APPEARANCE) },
                    showDivider = false
                )
            }
        }

        // SECTION 4: SECURITY & PRIVACY
        item {
            IOSSectionTitle(title = "SECURITY & PRIVACY")
            IOSGroupedCard {
                IOSSettingsRow(
                    title = "App Lock",
                    subtitle = if (settings.appLockEnabled) "PIN & Biometrics enabled" else "Off",
                    icon = Icons.Default.Fingerprint,
                    iconBgColor = IOSGreen,
                    trailingText = if (settings.appLockEnabled) "Enabled" else "Off",
                    onClick = onNavigateAppLock
                )
                IOSSettingsRow(
                    title = "Ghost Mode (Stealth)",
                    subtitle = if (settings.ghostModeEnabled) "Active · Hiding story & DM seen" else "Off",
                    icon = Icons.Default.VisibilityOff,
                    iconBgColor = IOSPurple,
                    trailingText = if (settings.ghostModeEnabled) "On" else "Off",
                    onClick = { onNavigateSubpage(SettingsSubpage.GHOST) }
                )
                IOSSettingsRow(
                    title = "Privacy & Notifications",
                    subtitle = "Link sanitization & session alerts",
                    icon = Icons.Default.Notifications,
                    iconBgColor = IOSTeal,
                    onClick = { onNavigateSubpage(SettingsSubpage.NOTIFICATIONS) },
                    showDivider = false
                )
            }
        }

        // SECTION 5: STATS & INSIGHTS
        item {
            IOSSectionTitle(title = "STATS & HISTORY")
            IOSGroupedCard {
                IOSSettingsRow(
                    title = "Screen Time Dashboard",
                    subtitle = "Daily & weekly usage analysis",
                    icon = Icons.Default.Timer,
                    iconBgColor = IOSGreen,
                    onClick = onNavigateScreenTime
                )
                IOSSettingsRow(
                    title = "Your Journey",
                    subtitle = "Level ${settings.userLevel} · ${settings.userXP} XP",
                    icon = Icons.Default.Star,
                    iconBgColor = IOSOrange,
                    onClick = onNavigateLevelPanel
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

        // SECTION 6: ABOUT & SYSTEM
        item {
            IOSSectionTitle(title = "ABOUT")
            IOSGroupedCard {
                IOSSettingsRow(
                    title = "GitHub Repository",
                    subtitle = "Source code, issues, and releases",
                    icon = Icons.AutoMirrored.Filled.OpenInNew,
                    iconBgColor = Color(0xFF3A3A3C),
                    onClick = {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/Ujwal223/FocusGram"))
                        context.startActivity(intent)
                    }
                )
                IOSSettingsRow(
                    title = "Legal Disclaimer",
                    subtitle = "Client-side non-affiliated focus tool",
                    icon = Icons.Default.Info,
                    iconBgColor = Color(0xFF3A3A3C),
                    onClick = onShowLegal
                )
                IOSSettingsRow(
                    title = "Open Source License",
                    subtitle = "AGPL-3.0 Free and Open Source",
                    icon = Icons.Default.Security,
                    iconBgColor = Color(0xFF3A3A3C),
                    trailingText = "v2.0.0",
                    onClick = onShowLicenses,
                    showDivider = false
                )
            }
        }

        item {
            Spacer(modifier = Modifier.height(40.dp))
        }
    }
}

// ─── FOCUS MODE SUBPAGE ─────────────────────────────────────────────────────

@Composable
private fun FocusModeSubpage(
    repository: SettingsRepository,
    settings: FocusSettings
) {
    val scope = rememberCoroutineScope()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        // MINIMALIST MODE
        item {
            IOSSectionTitle(title = "MINIMALIST EXPERIENCE")
            IOSGroupedCard {
                IOSSettingsRow(
                    title = "Minimal Mode",
                    subtitle = "Ultra-distraction-free layout. Hides feed, stories, reels, explore grid, and blocks infinite scroll",
                    icon = Icons.Default.DarkMode,
                    iconBgColor = Color(0xFF636366),
                    checked = settings.minimalModeEnabled,
                    onCheckedChange = { scope.launch { repository.setMinimalMode(it) } }
                )
                IOSSettingsRow(
                    title = "Hide Feed Posts",
                    subtitle = "Eliminate all posts on the home feed to stop doomscrolling",
                    icon = Icons.Default.VisibilityOff,
                    iconBgColor = IOSRed,
                    checked = settings.hideFeedPosts,
                    onCheckedChange = { scope.launch { repository.setHideFeedPosts(it) } },
                    showDivider = false
                )
            }
        }

        // CONTENT FILTERING
        item {
            IOSSectionTitle(title = "CONTENT FILTERING")
            IOSGroupedCard {
                IOSSettingsRow(
                    title = "Blur Explore Grid",
                    subtitle = "Blur explore grid thumbnails until tapped",
                    icon = Icons.Default.Visibility,
                    iconBgColor = IOSTeal,
                    checked = settings.blurExplore,
                    onCheckedChange = { scope.launch { repository.setBlurExplore(it) } }
                )
                IOSSettingsRow(
                    title = "Blur Reels",
                    subtitle = "Blur reels video thumbnails until tapped",
                    icon = Icons.Default.Visibility,
                    iconBgColor = IOSTeal,
                    checked = settings.blurReels,
                    onCheckedChange = { scope.launch { repository.setBlurReels(it) } }
                )
                IOSSettingsRow(
                    title = "Unblur Mode",
                    subtitle = if (settings.unblurMode == "hold") "Hold 350ms to reveal post (stays revealed)" else "Single tap reveals post (stays revealed)",
                    icon = Icons.Default.Visibility,
                    iconBgColor = IOSBlue,
                    trailingText = if (settings.unblurMode == "hold") "Hold (350ms)" else "Tap",
                    onClick = {
                        val nextMode = if (settings.unblurMode == "hold") "tap" else "hold"
                        scope.launch { repository.setUnblurMode(nextMode) }
                    },
                    showDivider = false
                )
            }
        }

        // DISTRACTION BLOCKERS
        item {
            IOSSectionTitle(title = "DISTRACTION BLOCKERS")
            IOSGroupedCard {
                IOSSettingsRow(
                    title = "Block Video Autoplay",
                    subtitle = "Stop videos and reels from playing automatically",
                    icon = Icons.Default.PlayArrow,
                    iconBgColor = IOSBlue,
                    checked = settings.noAutoplay,
                    onCheckedChange = { scope.launch { repository.setNoAutoplay(it) } }
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
                    subtitle = "Remove addictive short-form algorithmic reels tab & clips",
                    icon = Icons.Default.Block,
                    iconBgColor = IOSRed,
                    checked = settings.noReels,
                    onCheckedChange = { scope.launch { repository.setNoReels(it) } }
                )
                IOSSettingsRow(
                    title = "Hide Suggested Posts",
                    subtitle = "Only show posts from accounts you actually follow",
                    icon = Icons.Default.Star,
                    iconBgColor = IOSOrange,
                    checked = settings.hideSuggestedPosts,
                    onCheckedChange = { scope.launch { repository.setHideSuggestedPosts(it) } }
                )
                IOSSettingsRow(
                    title = "Hide Shop Tab",
                    subtitle = "Remove commercial shopping tab and suggestions",
                    icon = Icons.Default.ShoppingBag,
                    iconBgColor = IOSBlue,
                    checked = settings.hideShopTab,
                    onCheckedChange = { scope.launch { repository.setHideShopTab(it) } }
                )
                IOSSettingsRow(
                    title = "Block Direct Messages",
                    subtitle = "Disable DM access for deep focus sessions",
                    icon = Icons.Default.Block,
                    iconBgColor = IOSRed,
                    checked = settings.noDMs,
                    onCheckedChange = { scope.launch { repository.setNoDMs(it) } },
                    showDivider = false
                )
            }
        }

        // VANITY METRICS
        item {
            IOSSectionTitle(title = "VANITY METRICS")
            IOSGroupedCard {
                IOSSettingsRow(
                    title = "Hide Like Counts",
                    subtitle = "Remove like numbers across posts and reels",
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
                    onCheckedChange = { scope.launch { repository.setHideFollowerCounts(it) } },
                    showDivider = false
                )
            }
        }

        // AD BLOCKING
        item {
            IOSSectionTitle(title = "AD BLOCKING")
            IOSGroupedCard {
                IOSSettingsRow(
                    title = "Ad Blocker",
                    subtitle = "Block sponsored ads, tracking banners, and promo tiles",
                    icon = Icons.Default.Block,
                    iconBgColor = IOSGreen,
                    checked = settings.noAds,
                    onCheckedChange = { scope.launch { repository.setNoAds(it) } },
                    showDivider = false
                )
            }
        }

        item {
            Spacer(modifier = Modifier.height(40.dp))
        }
    }
}

// ─── GUARDRAILS SUBPAGE ─────────────────────────────────────────────────────

@Composable
private fun GuardrailsSubpage(
    repository: SettingsRepository,
    settings: FocusSettings
) {
    val scope = rememberCoroutineScope()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        // SESSION LIMITS
        item {
            IOSSectionTitle(title = "SESSION LIMITS")

            // 1. Daily Limit Timer
            val dailyLimitOptions = remember { listOf(0, 15, 30, 45, 60, 90, 120, 180, 240) }
            val selectedDailyIndex = remember(settings.dailyLimitMinutes) {
                dailyLimitOptions.indexOf(settings.dailyLimitMinutes).takeIf { it >= 0 }
                    ?: dailyLimitOptions.indexOfFirst { it >= settings.dailyLimitMinutes }.coerceAtLeast(0)
            }

            IOSGroupedCard(modifier = Modifier.padding(bottom = 12.dp)) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(IOSOrange),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Timer,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "Daily Limit Timer",
                                    color = Color.White,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "Max session duration before Instagram locks",
                                    color = Color(0xFF8E8E93),
                                    fontSize = 12.sp
                                )
                            }
                        }
                        Text(
                            text = if (settings.dailyLimitMinutes > 0) "${settings.dailyLimitMinutes} min" else "Off",
                            color = if (settings.dailyLimitMinutes > 0) Color.White else Color(0xFF8E8E93),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    CupertinoOptionWheelPicker<Int>(
                        options = dailyLimitOptions,
                        selectedIndex = selectedDailyIndex,
                        onOptionSelected = { _, mins: Int ->
                            scope.launch { repository.setDailyLimitMinutes(mins) }
                        },
                        labelProvider = { mins: Int ->
                            when {
                                mins == 0 -> "Off"
                                mins < 60 -> "$mins min"
                                mins % 60 == 0 -> "${mins / 60} hour" + (if (mins / 60 > 1) "s" else "")
                                else -> "${mins / 60}h ${mins % 60}m"
                            }
                        },
                        visibleItemsCount = 3,
                        itemHeight = 38.dp
                    )
                }
            }

            // 2. Cooldown Between Sessions
            val cooldownOptions = remember { listOf(0, 1, 2, 5, 10, 15, 30, 45, 60) }
            val selectedCooldownIndex = remember(settings.cooldownMinutes) {
                cooldownOptions.indexOf(settings.cooldownMinutes).takeIf { it >= 0 }
                    ?: cooldownOptions.indexOfFirst { it >= settings.cooldownMinutes }.coerceAtLeast(0)
            }

            IOSGroupedCard(modifier = Modifier.padding(bottom = 12.dp)) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(IOSBlue),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Timer,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "Cooldown Between Sessions",
                                    color = Color.White,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "Mandatory resting pause between sessions",
                                    color = Color(0xFF8E8E93),
                                    fontSize = 12.sp
                                )
                            }
                        }
                        Text(
                            text = if (settings.cooldownMinutes > 0) "${settings.cooldownMinutes} min" else "Off",
                            color = if (settings.cooldownMinutes > 0) Color.White else Color(0xFF8E8E93),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    CupertinoOptionWheelPicker<Int>(
                        options = cooldownOptions,
                        selectedIndex = selectedCooldownIndex,
                        onOptionSelected = { _, mins: Int ->
                            scope.launch { repository.setCooldownMinutes(mins) }
                        },
                        labelProvider = { mins: Int ->
                            when {
                                mins == 0 -> "Off"
                                mins < 60 -> "$mins min"
                                else -> "1 hour"
                            }
                        },
                        visibleItemsCount = 3,
                        itemHeight = 38.dp
                    )
                }
            }
        }

        // MINDFUL GATES
        item {
            IOSSectionTitle(title = "MINDFUL GATES")

            // 3. Mindful Breath Gate
            val breathOptions = remember { listOf(0, 3, 5, 10, 15, 20, 30, 60) }
            val currentBreathVal = if (settings.showBreathGate) settings.breathGateSeconds else 0
            val selectedBreathIndex = remember(currentBreathVal) {
                breathOptions.indexOf(currentBreathVal).takeIf { it >= 0 }
                    ?: breathOptions.indexOfFirst { it >= currentBreathVal }.coerceAtLeast(0)
            }

            IOSGroupedCard(modifier = Modifier.padding(bottom = 12.dp)) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(IOSTeal),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Notifications,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "Mindful Breath Gate",
                                    color = Color.White,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "Deep breath meditation before Instagram opens",
                                    color = Color(0xFF8E8E93),
                                    fontSize = 12.sp
                                )
                            }
                        }
                        Text(
                            text = if (settings.showBreathGate && settings.breathGateSeconds > 0) "${settings.breathGateSeconds}s" else "Off",
                            color = if (settings.showBreathGate && settings.breathGateSeconds > 0) Color.White else Color(0xFF8E8E93),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    CupertinoOptionWheelPicker<Int>(
                        options = breathOptions,
                        selectedIndex = selectedBreathIndex,
                        onOptionSelected = { _, sec: Int ->
                            scope.launch {
                                if (sec == 0) {
                                    repository.setBreathGate(false, 0)
                                } else {
                                    repository.setBreathGate(true, sec)
                                }
                            }
                        },
                        labelProvider = { sec: Int ->
                            if (sec == 0) "Off" else "${sec} seconds"
                        },
                        visibleItemsCount = 3,
                        itemHeight = 38.dp
                    )
                }
            }

            // 4. Effort Friction Challenge
            val frictionOptions = remember { listOf(0, 10, 15, 20, 30, 50) }
            val currentFrictionVal = if (settings.effortFrictionEnabled) settings.wordChallengeCount else 0
            val selectedFrictionIndex = remember(currentFrictionVal) {
                frictionOptions.indexOf(currentFrictionVal).takeIf { it >= 0 }
                    ?: frictionOptions.indexOfFirst { it >= currentFrictionVal }.coerceAtLeast(0)
            }

            IOSGroupedCard(modifier = Modifier.padding(bottom = 12.dp)) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(IOSPurple),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Lock,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "Effort Friction Challenge",
                                    color = Color.White,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "Type a mindful reflection phrase before unlock",
                                    color = Color(0xFF8E8E93),
                                    fontSize = 12.sp
                                )
                            }
                        }
                        Text(
                            text = if (settings.effortFrictionEnabled && settings.wordChallengeCount > 0) "${settings.wordChallengeCount} words" else "Off",
                            color = if (settings.effortFrictionEnabled && settings.wordChallengeCount > 0) Color.White else Color(0xFF8E8E93),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    CupertinoOptionWheelPicker<Int>(
                        options = frictionOptions,
                        selectedIndex = selectedFrictionIndex,
                        onOptionSelected = { _, words: Int ->
                            scope.launch {
                                if (words == 0) {
                                    repository.setEffortFriction(false, 0)
                                } else {
                                    repository.setEffortFriction(true, words)
                                }
                            }
                        },
                        labelProvider = { words: Int ->
                            if (words == 0) "Off" else "$words words"
                        },
                        visibleItemsCount = 3,
                        itemHeight = 38.dp
                    )
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(40.dp))
        }
    }
}

// ─── GHOST MODE SUBPAGE ─────────────────────────────────────────────────────

@Composable
private fun GhostModeSubpage(
    repository: SettingsRepository,
    settings: FocusSettings
) {
    val scope = rememberCoroutineScope()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        item {
            IOSSectionTitle(title = "GHOST MODE (STEALTH)")
            IOSGroupedCard {
                IOSSettingsRow(
                    title = "Ghost Mode",
                    subtitle = "Browse Instagram without leaving footprints or read receipts",
                    icon = Icons.Default.VisibilityOff,
                    iconBgColor = IOSPurple,
                    checked = settings.ghostModeEnabled,
                    onCheckedChange = { scope.launch { repository.setGhostMode(it) } },
                    showDivider = settings.ghostModeEnabled
                )

                if (settings.ghostModeEnabled) {
                    IOSSettingsRow(
                        title = "Hide Story Seen",
                        subtitle = "View stories without appearing in creator viewer lists",
                        icon = Icons.Default.VisibilityOff,
                        iconBgColor = IOSBlue,
                        checked = settings.ghostHideStorySeen,
                        onCheckedChange = { scope.launch { repository.setGhostHideStorySeen(it) } }
                    )
                    IOSSettingsRow(
                        title = "Hide DM Read Receipts",
                        subtitle = "Open direct messages without triggering Seen notifications",
                        icon = Icons.Default.VisibilityOff,
                        iconBgColor = IOSTeal,
                        checked = settings.ghostHideDirectSeen,
                        onCheckedChange = { scope.launch { repository.setGhostHideDirectSeen(it) } }
                    )
                    IOSSettingsRow(
                        title = "Suppress Typing Indicator",
                        subtitle = "Conceal typing status while composing in direct messages",
                        icon = Icons.Default.VisibilityOff,
                        iconBgColor = IOSOrange,
                        checked = settings.ghostHideTypingIndicator,
                        onCheckedChange = { scope.launch { repository.setGhostHideTypingIndicator(it) } },
                        showDivider = false
                    )
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = "Ghost Mode silently hooks in-browser dispatchers to prevent seen beacons from reaching Instagram servers. No account modifications or API alterations are performed.",
                color = Color(0xFF8E8E93),
                fontSize = 13.sp,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
            Spacer(modifier = Modifier.height(40.dp))
        }
    }
}

// ─── EXTRAS SUBPAGE ─────────────────────────────────────────────────────────

@Composable
private fun ExtrasSubpage(
    repository: SettingsRepository,
    settings: FocusSettings,
    onNavigateUrl: (String) -> Unit = {}
) {
    val scope = rememberCoroutineScope()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        // STARTUP PAGE
        item {
            IOSSectionTitle(title = "STARTUP")
            IOSGroupedCard {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Text(
                        text = "Launch Page",
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Choose which page opens when you launch FocusGram.",
                        color = Color(0xFF8E8E93),
                        fontSize = 13.sp
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    val pages = listOf(
                        "home" to "Home Feed",
                        "following" to "Following Feed",
                        "favorites" to "Favorites Feed",
                        "direct" to "Direct Messages"
                    )

                    pages.forEach { (key, label) ->
                        val isSelected = settings.startupPage == key
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (isSelected) Color.White.copy(alpha = 0.1f) else Color.Transparent)
                                .clickable { scope.launch { repository.setStartupPage(key) } }
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = label,
                                color = if (isSelected) Color.White else Color(0xFF8E8E93),
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                fontSize = 14.sp
                            )
                            if (isSelected) {
                                Text("✓", color = Color.White, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }

        // MEDIA
        item {
            IOSSectionTitle(title = "MEDIA")
            IOSGroupedCard {
                IOSSettingsRow(
                    title = "Download Media (Feed + Reels)",
                    subtitle = "Adds a download option on posts and reels",
                    icon = Icons.Default.Star,
                    iconBgColor = IOSOrange,
                    checked = settings.videoDownloadEnabled,
                    onCheckedChange = { scope.launch { repository.setVideoDownloadEnabled(it) } },
                    showDivider = false
                )
            }
        }

        // INSTAGRAM ACCOUNT PREFERENCES
        item {
            IOSSectionTitle(title = "INSTAGRAM")
            IOSGroupedCard {
                IOSSettingsRow(
                    title = "Instagram Account Settings",
                    subtitle = "Open official Instagram preferences inside webview",
                    icon = Icons.Default.Settings,
                    iconBgColor = IOSPurple,
                    onClick = {
                        onNavigateUrl("https://www.instagram.com/accounts/settings/?entrypoint=profile")
                    },
                    showDivider = false
                )
            }
        }

        item {
            Spacer(modifier = Modifier.height(40.dp))
        }
    }
}

// ─── APPEARANCE SUBPAGE ─────────────────────────────────────────────────────

@Composable
private fun AppearanceSubpage(
    repository: SettingsRepository,
    settings: FocusSettings
) {
    val scope = rememberCoroutineScope()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        item {
            IOSSectionTitle(title = "VISUAL ERGONOMICS")
            IOSGroupedCard {
                IOSSettingsRow(
                    title = "Grayscale Mode",
                    subtitle = "Strip high-dopamine saturated feed colors to curb addiction",
                    icon = Icons.Default.DarkMode,
                    iconBgColor = Color(0xFF636366),
                    checked = settings.grayscaleEnabled,
                    onCheckedChange = { scope.launch { repository.setGrayscale(it) } },
                    showDivider = false
                )
            }
        }

        item {
            Spacer(modifier = Modifier.height(40.dp))
        }
    }
}

// ─── NOTIFICATIONS SUBPAGE ──────────────────────────────────────────────────

@Composable
private fun NotificationsSubpage(
    repository: SettingsRepository,
    settings: FocusSettings
) {
    val scope = rememberCoroutineScope()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        // LINK PRIVACY
        item {
            IOSSectionTitle(title = "LINK PRIVACY")
            IOSGroupedCard {
                IOSSettingsRow(
                    title = "Sanitize Shared Links",
                    subtitle = "Automatically strip tracking parameters (igsh, utm_*, fbclid) from URLs when opening or sharing",
                    icon = Icons.Default.Security,
                    iconBgColor = IOSGreen,
                    checked = settings.sanitizeLinks,
                    onCheckedChange = { scope.launch { repository.setSanitizeLinks(it) } },
                    showDivider = false
                )
            }
        }

        // SESSION ALERTS
        item {
            IOSSectionTitle(title = "SESSION ALERTS")
            IOSGroupedCard {
                IOSSettingsRow(
                    title = "Session Expired Alerts",
                    subtitle = "Notify you immediately when your focus session timer finishes",
                    icon = Icons.Default.Notifications,
                    iconBgColor = IOSTeal,
                    checked = settings.notifySessionEnd,
                    onCheckedChange = { scope.launch { repository.setNotifySessionEnd(it) } }
                )
                IOSSettingsRow(
                    title = "Persistent Status Notification",
                    subtitle = "Display live session timer countdown in Android notification shade",
                    icon = Icons.Default.Timer,
                    iconBgColor = IOSBlue,
                    checked = settings.notifyPersistent,
                    onCheckedChange = { scope.launch { repository.setNotifyPersistent(it) } },
                    showDivider = false
                )
            }
        }

        item {
            Spacer(modifier = Modifier.height(40.dp))
        }
    }
}
