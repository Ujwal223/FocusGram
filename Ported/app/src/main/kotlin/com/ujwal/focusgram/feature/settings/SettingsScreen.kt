package com.ujwal.focusgram.feature.settings

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.ujwal.focusgram.R
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
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import kotlinx.coroutines.delay
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
import androidx.compose.material.icons.filled.StayCurrentPortrait
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
import androidx.compose.material.icons.filled.Person
import com.ujwal.focusgram.core.database.ScreenTimeDao
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
import com.ujwal.focusgram.feature.history.ReelHistory
import com.ujwal.focusgram.feature.session.AppSessionPicker
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class SettingsSubpage {
    MAIN,
    FOCUS,
    GUARDRAILS,
    EXTRAS,
    APPEARANCE,
    NOTIFICATIONS,
    GHOST,
    LICENSES
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
    onNavigateAppLock: () -> Unit,
    onWatchAd: () -> Unit = {},
    onWatchAdWithReward: (((suspend () -> Unit) -> Unit))? = null,
    onNavigateUrl: (String) -> Unit = {},
    screenTimeDao: ScreenTimeDao? = null,
    reelHistory: ReelHistory? = null,
    currentSubpage: SettingsSubpage = SettingsSubpage.MAIN,
    onSubpageChange: (SettingsSubpage) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val settings by repository.settingsFlow.collectAsState(initial = FocusSettings())
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    val mainListState = rememberSaveable(saver = LazyListState.Saver) { LazyListState() }
    var activeSubpage by remember(currentSubpage) { mutableStateOf(currentSubpage) }
    val setSubpage: (SettingsSubpage) -> Unit = { page ->
        activeSubpage = page
        onSubpageChange(page)
    }
    var showLegalDialog by remember { mutableStateOf(false) }
    var showDeveloperDialog by remember { mutableStateOf(false) }

    var pendingRewardAction by remember { mutableStateOf<(suspend () -> Unit)?>(null) }
    var adDialogReason by remember { mutableStateOf<String?>(null) }
    var showSupportAdDialog by remember { mutableStateOf(false) }

    val onRequestAdReward: (action: suspend () -> Unit, reason: String) -> Unit = { action, reason ->
        pendingRewardAction = action
        adDialogReason = reason
        showSupportAdDialog = true
    }

    // Intercept back presses: go back to MAIN subpage first, or pop screen
    BackHandler {
        if (activeSubpage != SettingsSubpage.MAIN) {
            setSubpage(SettingsSubpage.MAIN)
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

    // Developer Dialog
    if (showDeveloperDialog) {
        AlertDialog(
            onDismissRequest = { showDeveloperDialog = false },
            containerColor = Color(0xFF1C1C1E),
            icon = {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF2C2C2E)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(28.dp)
                    )
                }
            },
            title = {
                Text(
                    text = "Developer Profile",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Ujwal",
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 16.sp
                    )
                    Text(
                        text = "Creator and maintainer of FocusGram. Independent open-source developer focused on privacy, digital wellbeing, and user-centric software.",
                        color = Color(0xFF8E8E93),
                        fontSize = 13.sp,
                        lineHeight = 18.sp
                    )
                    Text(
                        text = "GitHub: @Ujwal223",
                        color = IOSBlue,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/Ujwal223"))
                        context.startActivity(intent)
                        showDeveloperDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.White,
                        contentColor = Color.Black
                    ),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Text("GitHub Profile", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { showDeveloperDialog = false },
                    border = BorderStroke(1.dp, Color(0xFF3A3A3C)),
                    shape = RoundedCornerShape(20.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = Color.White
                    )
                ) {
                    Text("Close")
                }
            }
        )
    }

    // Support Ad Dialog
    if (showSupportAdDialog && adDialogReason != null) {
        AlertDialog(
            onDismissRequest = {
                showSupportAdDialog = false
                pendingRewardAction = null
            },
            containerColor = Color(0xFF1C1C1E),
            title = {
                Text(
                    text = "Support FocusGram ❤️",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = adDialogReason ?: "Please watch an ad in order to toggle this setting.",
                        color = Color.White,
                        fontWeight = FontWeight.Medium,
                        fontSize = 14.sp
                    )
                    Text(
                        text = "Your 20 seconds can provide a huge contribution to the continuous development and improvement of FocusGram.\n\nThank you for supporting this project! ❤️",
                        color = Color(0xFF8E8E93),
                        fontSize = 13.sp,
                        lineHeight = 18.sp
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showSupportAdDialog = false
                        val action = pendingRewardAction
                        pendingRewardAction = null
                        if (action != null) {
                            if (onWatchAdWithReward != null) {
                                onWatchAdWithReward(action)
                            } else {
                                scope.launch { action() }
                                onWatchAd()
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.White,
                        contentColor = Color.Black
                    ),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Text("Watch Ad & Continue", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = {
                        showSupportAdDialog = false
                        pendingRewardAction = null
                    },
                    border = BorderStroke(1.dp, Color(0xFF3A3A3C)),
                    shape = RoundedCornerShape(20.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
                ) {
                    Text("Cancel")
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
                if (activeSubpage != SettingsSubpage.MAIN) {
                    setSubpage(SettingsSubpage.MAIN)
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
                text = when (activeSubpage) {
                    SettingsSubpage.MAIN -> "Settings and activity"
                    SettingsSubpage.FOCUS -> "Focus Mode"
                    SettingsSubpage.GUARDRAILS -> "Time Control & Guardrails"
                    SettingsSubpage.EXTRAS -> "Extras"
                    SettingsSubpage.APPEARANCE -> "Appearance"
                    SettingsSubpage.NOTIFICATIONS -> "Privacy & Notifications"
                    SettingsSubpage.GHOST -> "Ghost Mode"
                    SettingsSubpage.LICENSES -> "Open Source & Credits"
                },
                color = IOSTextPrimary,
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(start = 4.dp)
            )
        }

        // Subpage Router
        when (activeSubpage) {
            SettingsSubpage.MAIN -> {
                MainSettingsContent(
                    settings = settings,
                    listState = mainListState,
                    onNavigateSubpage = { setSubpage(it) },
                    onNavigateAppLock = onNavigateAppLock,
                    onNavigateScreenTime = onNavigateScreenTime,
                    onNavigateReelsHistory = onNavigateReelsHistory,
                    onWatchAd = onWatchAd,
                    onShowLegal = { showLegalDialog = true },
                    onShowDeveloper = { showDeveloperDialog = true },
                    screenTimeDao = screenTimeDao,
                    reelHistory = reelHistory
                )
            }
            SettingsSubpage.FOCUS -> {
                FocusModeSubpage(
                    repository = repository,
                    settings = settings,
                    onRequestAdReward = onRequestAdReward
                )
            }
            SettingsSubpage.GUARDRAILS -> {
                GuardrailsSubpage(
                    repository = repository,
                    settings = settings,
                    screenTimeDao = screenTimeDao,
                    onWatchAd = onWatchAd
                )
            }
            SettingsSubpage.EXTRAS -> {
                ExtrasSubpage(
                    repository = repository,
                    settings = settings,
                    onRequestAdReward = onRequestAdReward,
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
            SettingsSubpage.LICENSES -> {
                LicensesSubpage()
            }
        }
    }
}

// ─── MAIN SETTINGS SCREEN ───────────────────────────────────────────────────

@Composable
private fun MainSettingsContent(
    settings: FocusSettings,
    listState: LazyListState,
    onNavigateSubpage: (SettingsSubpage) -> Unit,
    onNavigateAppLock: () -> Unit,
    onNavigateScreenTime: () -> Unit,
    onNavigateReelsHistory: () -> Unit,
    onWatchAd: () -> Unit,
    onShowLegal: () -> Unit,
    onShowDeveloper: () -> Unit,
    screenTimeDao: ScreenTimeDao? = null,
    reelHistory: ReelHistory? = null
) {
    val context = LocalContext.current
    val dateFormat = remember { SimpleDateFormat("yyyy-MM-dd", Locale.US) }
    val todayStr = remember { dateFormat.format(Date()) }
    val todayRecord by (screenTimeDao?.getForDate(todayStr) ?: flowOf(null)).collectAsState(initial = null)
    val todayWatchedFromHistory by (reelHistory?.todayCount ?: flowOf(0)).collectAsState(initial = 0)

    val todaySeconds = todayRecord?.seconds ?: (settings.instaMinutes * 60)
    val todayReelsSeconds = todayRecord?.reelsSeconds ?: (settings.reelsMinutes * 60)
    val todayReelsWatched = if (reelHistory != null) todayWatchedFromHistory else (todayRecord?.reelsWatched ?: 0)

    val appTimeText = when {
        todaySeconds < 60 -> "${todaySeconds}s"
        todaySeconds % 60 == 0 -> "${todaySeconds / 60}m"
        else -> "${todaySeconds / 60}m ${todaySeconds % 60}s"
    }
    val reelsTimeText = when {
        todayReelsSeconds < 60 -> "${todayReelsSeconds}s"
        todayReelsSeconds % 60 == 0 -> "${todayReelsSeconds / 60}m"
        else -> "${todayReelsSeconds / 60}m ${todayReelsSeconds % 60}s"
    }

    LazyColumn(
        state = listState,
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
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceAround,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(text = appTimeText, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Text(text = "App Time", color = Color(0xFF8E8E93), fontSize = 11.sp)
                    }
                    Box(modifier = Modifier.width(1.dp).height(24.dp).background(Color(0xFF2C2C2E)))
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(text = reelsTimeText, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Text(text = "Reels Time", color = Color(0xFF8E8E93), fontSize = 11.sp)
                    }
                    Box(modifier = Modifier.width(1.dp).height(24.dp).background(Color(0xFF2C2C2E)))
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(text = "$todayReelsWatched", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Text(text = "Reels Watched", color = Color(0xFF8E8E93), fontSize = 11.sp)
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
                    subtitle = "Startup launch page, media downloader",
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
                    title = "Developer",
                    subtitle = "Ujwal (@Ujwal223)",
                    icon = Icons.Default.Person,
                    iconBgColor = Color(0xFF3A3A3C),
                    onClick = onShowDeveloper
                )
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
                    title = "Open Source License & Credits",
                    subtitle = "AGPL-3.0 and third-party dependencies",
                    icon = Icons.Default.Security,
                    iconBgColor = Color(0xFF3A3A3C),
                    trailingText = "v2.0.0",
                    onClick = { onNavigateSubpage(SettingsSubpage.LICENSES) },
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
    settings: FocusSettings,
    onRequestAdReward: (action: suspend () -> Unit, reason: String) -> Unit = { _, _ -> }
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
                    subtitle = "Only DMs and your Profile are accessible. Reels, Explore, and feed posts are strictly disabled. Friends' sent reels work in DMs via Reel Lock; posts sent by friends will not load.",
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
                    title = "Blur Home Feed",
                    subtitle = "Blur all posts, videos, and multi-image carousels on the home feed",
                    icon = Icons.Default.Visibility,
                    iconBgColor = IOSTeal,
                    checked = settings.blurFeed,
                    onCheckedChange = { scope.launch { repository.setBlurFeed(it) } }
                )
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
                    title = "Block Reels Swiping in DMs",
                    subtitle = "Watch reels sent to you in DMs without doomscrolling into suggested reels feeds",
                    icon = Icons.Default.Lock,
                    iconBgColor = IOSPurple,
                    checked = settings.blockDmReelsSwiping,
                    onCheckedChange = { scope.launch { repository.setBlockDmReelsSwiping(it) } }
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
                    onCheckedChange = { willEnable ->
                        if (willEnable) {
                            onRequestAdReward(
                                { repository.setNoAds(true) },
                                "Please watch an ad in order to re-enable Ad Blocking."
                            )
                        } else {
                            scope.launch { repository.setNoAds(false) }
                        }
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

@Composable
private fun GuardrailsSubpage(
    repository: SettingsRepository,
    settings: FocusSettings,
    screenTimeDao: ScreenTimeDao? = null,
    onWatchAd: () -> Unit = {}
) {
    val scope = rememberCoroutineScope()
    val dateFormat = remember { SimpleDateFormat("yyyy-MM-dd", Locale.US) }
    val todayStr = remember { dateFormat.format(Date()) }
    val todayRecord by (screenTimeDao?.getForDate(todayStr) ?: flowOf(null)).collectAsState(initial = null)
    val isDailyLimitExhausted = settings.dailyLimitMinutes > 0 && ((todayRecord?.seconds ?: 0) >= settings.dailyLimitMinutes * 60)

    val lastSessionEndTimestamp by repository.lastSessionEndTimestampFlow.collectAsState(initial = 0L)
    val isCooldownActive = settings.cooldownMinutes > 0 && lastSessionEndTimestamp > 0L &&
            (System.currentTimeMillis() - lastSessionEndTimestamp < settings.cooldownMinutes * 60 * 1000L)

    var showDailyLimitModal by remember { mutableStateOf(false) }
    var showCooldownModal by remember { mutableStateOf(false) }
    var showBreathModal by remember { mutableStateOf(false) }
    var showFrictionModal by remember { mutableStateOf(false) }
    var showDailyLimitExhaustedDialog by remember { mutableStateOf(false) }
    var showCooldownActiveDialog by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        // SESSION LIMITS
        item {
            IOSSectionTitle(title = "SESSION LIMITS")
            IOSGroupedCard {
                IOSSettingsRow(
                    title = "Daily Limit Timer",
                    subtitle = if (isDailyLimitExhausted) "Limit exhausted for today (Locked)" else "Max session duration before Instagram locks",
                    icon = Icons.Default.Timer,
                    iconBgColor = if (isDailyLimitExhausted) Color(0xFF636366) else IOSOrange,
                    trailingText = if (settings.dailyLimitMinutes > 0) "${settings.dailyLimitMinutes} min" else "Off",
                    onClick = {
                        if (isDailyLimitExhausted) {
                            showDailyLimitExhaustedDialog = true
                        } else {
                            showDailyLimitModal = true
                        }
                    }
                )
                IOSSettingsRow(
                    title = "Cooldown Between Sessions",
                    subtitle = if (isCooldownActive) "Cooldown currently active (Locked)" else "Mandatory resting pause between sessions",
                    icon = Icons.Default.Timer,
                    iconBgColor = if (isCooldownActive) Color(0xFF636366) else IOSBlue,
                    trailingText = if (settings.cooldownMinutes > 0) "${settings.cooldownMinutes} min" else "Off",
                    onClick = {
                        if (isCooldownActive) {
                            showCooldownActiveDialog = true
                        } else {
                            showCooldownModal = true
                        }
                    },
                    showDivider = false
                )
            }
        }

        // MINDFUL GATES
        item {
            IOSSectionTitle(title = "MINDFUL GATES")
            IOSGroupedCard {
                IOSSettingsRow(
                    title = "Mindful Breath Gate",
                    subtitle = "Deep breath meditation before Instagram opens",
                    icon = Icons.Default.Notifications,
                    iconBgColor = IOSTeal,
                    trailingText = if (settings.showBreathGate && settings.breathGateSeconds > 0) "${settings.breathGateSeconds}s" else "Off",
                    onClick = { showBreathModal = true }
                )
                IOSSettingsRow(
                    title = "Effort Friction Challenge",
                    subtitle = "Type a mindful reflection phrase before unlock",
                    icon = Icons.Default.Lock,
                    iconBgColor = IOSPurple,
                    trailingText = if (settings.effortFrictionEnabled && settings.wordChallengeCount > 0) "${settings.wordChallengeCount} words" else "Off",
                    onClick = { showFrictionModal = true },
                    showDivider = false
                )
            }
        }

        item {
            Spacer(modifier = Modifier.height(40.dp))
        }
    }

    if (showDailyLimitModal) {
        val dailyLimitOptions = remember { listOf(0, 15, 30, 45, 60, 90, 120, 180, 240) }
        val selectedDailyIndex = remember(settings.dailyLimitMinutes) {
            dailyLimitOptions.indexOf(settings.dailyLimitMinutes).takeIf { it >= 0 }
                ?: dailyLimitOptions.indexOfFirst { it >= settings.dailyLimitMinutes }.coerceAtLeast(0)
        }
        WheelPickerModalDialog(
            title = "Daily Limit Timer",
            subtitle = "Choose maximum daily allowed usage time before Instagram locks",
            options = dailyLimitOptions,
            initialIndex = selectedDailyIndex,
            onDismiss = { showDailyLimitModal = false },
            onConfirm = { mins -> scope.launch { repository.setDailyLimitMinutes(mins) } },
            labelProvider = { mins ->
                when {
                    mins == 0 -> "Off"
                    mins < 60 -> "$mins min"
                    mins % 60 == 0 -> "${mins / 60} hour" + (if (mins / 60 > 1) "s" else "")
                    else -> "${mins / 60}h ${mins % 60}m"
                }
            }
        )
    }

    if (showCooldownModal) {
        val cooldownOptions = remember { listOf(0, 1, 2, 5, 10, 15, 30, 45, 60) }
        val selectedCooldownIndex = remember(settings.cooldownMinutes) {
            cooldownOptions.indexOf(settings.cooldownMinutes).takeIf { it >= 0 }
                ?: cooldownOptions.indexOfFirst { it >= settings.cooldownMinutes }.coerceAtLeast(0)
        }
        WheelPickerModalDialog(
            title = "Cooldown Between Sessions",
            subtitle = "Choose mandatory resting period after reaching limits",
            options = cooldownOptions,
            initialIndex = selectedCooldownIndex,
            onDismiss = { showCooldownModal = false },
            onConfirm = { mins -> scope.launch { repository.setCooldownMinutes(mins) } },
            labelProvider = { mins ->
                when {
                    mins == 0 -> "Off"
                    mins < 60 -> "$mins min"
                    else -> "1 hour"
                }
            }
        )
    }

    if (showBreathModal) {
        val breathOptions = remember { listOf(0, 3, 5, 10, 15, 20, 30, 60) }
        val currentBreathVal = if (settings.showBreathGate) settings.breathGateSeconds else 0
        val selectedBreathIndex = remember(currentBreathVal) {
            breathOptions.indexOf(currentBreathVal).takeIf { it >= 0 }
                ?: breathOptions.indexOfFirst { it >= currentBreathVal }.coerceAtLeast(0)
        }
        WheelPickerModalDialog(
            title = "Mindful Breath Gate",
            subtitle = "Choose breath pause length before opening Instagram",
            options = breathOptions,
            initialIndex = selectedBreathIndex,
            onDismiss = { showBreathModal = false },
            onConfirm = { sec ->
                scope.launch {
                    if (sec == 0) repository.setBreathGate(false, 0)
                    else repository.setBreathGate(true, sec)
                }
            },
            labelProvider = { sec -> if (sec == 0) "Off" else "$sec seconds" }
        )
    }

    if (showFrictionModal) {
        val frictionOptions = remember { listOf(0, 10, 15, 20, 30, 50) }
        val currentFrictionVal = if (settings.effortFrictionEnabled) settings.wordChallengeCount else 0
        val selectedFrictionIndex = remember(currentFrictionVal) {
            frictionOptions.indexOf(currentFrictionVal).takeIf { it >= 0 }
                ?: frictionOptions.indexOfFirst { it >= currentFrictionVal }.coerceAtLeast(0)
        }
        WheelPickerModalDialog(
            title = "Effort Friction Challenge",
            subtitle = "Choose phrase word challenge length to bypass lock",
            options = frictionOptions,
            initialIndex = selectedFrictionIndex,
            onDismiss = { showFrictionModal = false },
            onConfirm = { words ->
                scope.launch {
                    if (words == 0) repository.setEffortFriction(false, 0)
                    else repository.setEffortFriction(true, words)
                }
            },
            labelProvider = { words -> if (words == 0) "Off" else "$words words" }
        )
    }

    if (showDailyLimitExhaustedDialog) {
        AlertDialog(
            onDismissRequest = { showDailyLimitExhaustedDialog = false },
            containerColor = Color(0xFF1C1C1E),
            title = {
                Text(
                    text = "Daily Limit Exhausted",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Your limit is already exhausted, you cannot change this setting today.",
                        color = Color(0xFF8E8E93),
                        fontSize = 14.sp,
                        lineHeight = 20.sp
                    )
                    if (settings.dailyLimitAdClaimedDate == todayStr) {
                        Text(
                            text = "Daily +5m extension already claimed today.",
                            color = IOSOrange,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                    } else {
                        Text(
                            text = "You may watch a voluntary sponsored ad to extend your daily limit by 5 minutes (once today).",
                            color = IOSBlue,
                            fontSize = 13.sp
                        )
                    }
                }
            },
            confirmButton = {
                if (settings.dailyLimitAdClaimedDate != todayStr) {
                    Button(
                        onClick = {
                            showDailyLimitExhaustedDialog = false
                            scope.launch {
                                repository.extendDailyLimit5Minutes(todayStr)
                            }
                            onWatchAd()
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.White,
                            contentColor = Color.Black
                        ),
                        shape = RoundedCornerShape(20.dp)
                    ) {
                        Text("Watch Ad (+5 min)", fontWeight = FontWeight.Bold)
                    }
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { showDailyLimitExhaustedDialog = false },
                    border = BorderStroke(1.dp, Color(0xFF3A3A3C)),
                    shape = RoundedCornerShape(20.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
                ) {
                    Text("Close")
                }
            }
        )
    }

    if (showCooldownActiveDialog) {
        AlertDialog(
            onDismissRequest = { showCooldownActiveDialog = false },
            containerColor = Color(0xFF1C1C1E),
            title = {
                Text(
                    text = "Cooldown Active",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
            },
            text = {
                Text(
                    text = "A mandatory resting pause between sessions is currently active. You cannot change this setting until the active cooldown expires.",
                    color = Color(0xFF8E8E93),
                    fontSize = 14.sp,
                    lineHeight = 20.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = { showCooldownActiveDialog = false },
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
}

@Composable
private fun <T> WheelPickerModalDialog(
    title: String,
    subtitle: String,
    options: List<T>,
    initialIndex: Int,
    onDismiss: () -> Unit,
    onConfirm: (T) -> Unit,
    labelProvider: (T) -> String
) {
    var selectedIdx by remember { mutableStateOf(initialIndex.coerceIn(0, options.lastIndex)) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF1C1C1E),
        title = {
            Column {
                Text(
                    text = title,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = subtitle,
                    color = Color(0xFF8E8E93),
                    fontSize = 12.sp,
                    lineHeight = 16.sp
                )
            }
        },
        text = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                CupertinoOptionWheelPicker(
                    options = options,
                    selectedIndex = selectedIdx,
                    onOptionSelected = { idx, _ -> selectedIdx = idx },
                    labelProvider = labelProvider,
                    visibleItemsCount = 3,
                    itemHeight = 40.dp,
                    showGradientShadows = false
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onConfirm(options[selectedIdx])
                    onDismiss()
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.White,
                    contentColor = Color.Black
                ),
                shape = RoundedCornerShape(20.dp)
            ) {
                Text("Save", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onDismiss,
                border = BorderStroke(1.dp, Color(0xFF3A3A3C)),
                shape = RoundedCornerShape(20.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = Color.White
                )
            ) {
                Text("Cancel")
            }
        }
    )
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
    onRequestAdReward: (action: suspend () -> Unit, reason: String) -> Unit = { _, _ -> },
    onNavigateUrl: (String) -> Unit = {}
) {
    val scope = rememberCoroutineScope()
    var showLaunchPageModal by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        // STARTUP PAGE
        item {
            IOSSectionTitle(title = "STARTUP")
            IOSGroupedCard {
                val isCustomLaunch = settings.startupPage != "home"
                IOSSettingsRow(
                    title = "Launch Page",
                    subtitle = if (isCustomLaunch) "Start on ${when (settings.startupPage) { "following" -> "Following Feed"; "favorites" -> "Favorites Feed"; "direct" -> "Direct Messages"; else -> "Home Feed" }}" else "Opens default Instagram Home",
                    icon = Icons.AutoMirrored.Filled.OpenInNew,
                    iconBgColor = IOSBlue,
                    checked = isCustomLaunch,
                    onCheckedChange = { isChecked ->
                        scope.launch {
                            if (isChecked) {
                                repository.setStartupPage("following")
                                showLaunchPageModal = true
                            } else {
                                repository.setStartupPage("home")
                            }
                        }
                    },
                    showDivider = isCustomLaunch
                )
                if (isCustomLaunch) {
                    IOSSettingsRow(
                        title = "Selected Target",
                        subtitle = "Choose screen to open upon launch",
                        trailingText = when (settings.startupPage) {
                            "following" -> "Following Feed"
                            "favorites" -> "Favorites Feed"
                            "direct" -> "Direct Messages"
                            else -> "Home Feed"
                        },
                        onClick = { showLaunchPageModal = true },
                        showDivider = false
                    )
                }
            }
        }

        // MEDIA
        item {
            IOSSectionTitle(title = "MEDIA")
            IOSGroupedCard {
                IOSSettingsRow(
                    title = "Media Downloader",
                    subtitle = "Download media from Feed, Reels, and Profiles",
                    icon = Icons.Default.Star,
                    iconBgColor = IOSOrange,
                    checked = settings.videoDownloadEnabled,
                    onCheckedChange = { newState ->
                        val actionDesc = if (newState) "enable" else "disable"
                        onRequestAdReward(
                            { repository.setVideoDownloadEnabled(newState) },
                            "Please watch an ad in order to $actionDesc the Media Downloader."
                        )
                    },
                    showDivider = false
                )
            }
        }

        // DISPLAY
        item {
            IOSSectionTitle(title = "DISPLAY")
            IOSGroupedCard {
                IOSSettingsRow(
                    title = "Portrait Only",
                    subtitle = "Lock app orientation to portrait mode",
                    icon = Icons.Default.StayCurrentPortrait,
                    iconBgColor = IOSBlue,
                    checked = settings.portraitOnly,
                    onCheckedChange = { isChecked ->
                        scope.launch {
                            repository.setPortraitOnly(isChecked)
                        }
                    },
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

    if (showLaunchPageModal) {
        val launchPages = remember {
            listOf(
                "home" to "Home Feed",
                "following" to "Following Feed",
                "favorites" to "Favorites Feed",
                "direct" to "Direct Messages"
            )
        }
        val currentIdx = remember(settings.startupPage) {
            launchPages.indexOfFirst { it.first == settings.startupPage }.coerceAtLeast(0)
        }
        WheelPickerModalDialog(
            title = "Launch Page",
            subtitle = "Choose which page opens when you launch FocusGram",
            options = launchPages,
            initialIndex = currentIdx,
            onDismiss = { showLaunchPageModal = false },
            onConfirm = { option ->
                scope.launch { repository.setStartupPage(option.first) }
            },
            labelProvider = { it.second }
        )
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
    val context = LocalContext.current
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { _ -> }

    val checkAndRequestNotificationPermission = { onGranted: () -> Unit ->
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    context,
                    android.Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                permissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
            } else {
                onGranted()
            }
        } else {
            onGranted()
        }
    }

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
                    onCheckedChange = { willEnable ->
                        if (willEnable) {
                            checkAndRequestNotificationPermission {
                                scope.launch { repository.setNotifySessionEnd(true) }
                            }
                            scope.launch { repository.setNotifySessionEnd(true) }
                        } else {
                            scope.launch { repository.setNotifySessionEnd(false) }
                        }
                    }
                )
                IOSSettingsRow(
                    title = "Persistent Status Notification",
                    subtitle = "Display live session timer countdown in Android notification shade",
                    icon = Icons.Default.Timer,
                    iconBgColor = IOSBlue,
                    checked = settings.notifyPersistent,
                    onCheckedChange = { willEnable ->
                        if (willEnable) {
                            checkAndRequestNotificationPermission {
                                scope.launch { repository.setNotifyPersistent(true) }
                            }
                            scope.launch { repository.setNotifyPersistent(true) }
                        } else {
                            scope.launch { repository.setNotifyPersistent(false) }
                        }
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

// ─── LICENSES SUBPAGE ────────────────────────────────────────────────────────

@Composable
private fun LicensesSubpage() {
    val context = LocalContext.current

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        item {
            IOSSectionTitle(title = "FOCUSGRAM LICENSE")
            IOSGroupedCard {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "GNU Affero General Public License v3.0 (AGPL-3.0)",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "FocusGram is 100% free and open-source software. You are entitled to inspect, modify, study, and redistribute the application code under the terms of AGPL-3.0.\n\n" +
                                "Core Privacy Guarantees:\n" +
                                "• 100% Client-side execution\n" +
                                "• Zero analytics trackers, telemetry, or profiling\n" +
                                "• Zero proprietary ad SDKs or background tracking",
                        color = Color(0xFF8E8E93),
                        fontSize = 13.sp,
                        lineHeight = 18.sp
                    )
                }
            }
        }

        item {
            IOSSectionTitle(title = "DEVELOPER")
            IOSGroupedCard {
                IOSSettingsRow(
                    title = "Ujwal (@Ujwal223)",
                    subtitle = "Creator & maintainer of FocusGram · Independent open-source developer",
                    icon = Icons.Default.Person,
                    iconUrl = "https://avatars.githubusercontent.com/u/74640103?s=120",
                    iconBgColor = IOSPurple,
                    onClick = {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/Ujwal223"))
                        context.startActivity(intent)
                    },
                    showDivider = false
                )
            }
        }

        item {
            IOSSectionTitle(title = "THIRD-PARTY OPEN SOURCE CREDITS")
            IOSGroupedCard {
                IOSSettingsRow(
                    title = "Mozilla GeckoView",
                    subtitle = "Mozilla Public License 2.0 · Independent privacy-focused Android browser engine",
                    iconRes = R.drawable.ic_credit_geckoview,
                    iconBgColor = Color(0xFF008787),
                    onClick = {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://mozilla.github.io/geckoview/"))
                        context.startActivity(intent)
                    }
                )
                IOSSettingsRow(
                    title = "uBlock Origin",
                    subtitle = "GPLv3 · High-performance content filtering and declarative rule evaluation",
                    iconRes = R.drawable.ic_credit_ublock,
                    iconBgColor = Color(0xFF800000),
                    onClick = {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/gorhill/uBlock"))
                        context.startActivity(intent)
                    }
                )
                IOSSettingsRow(
                    title = "Ghostify",
                    subtitle = "MIT License · Groundbreaking research & telemetry suppression architecture",
                    icon = Icons.Default.VisibilityOff,
                    iconUrl = "https://lh3.googleusercontent.com/or_aQHbJ4j41UjtaQ9rsNZk93tPH9Xw_YASD3l-27SW3A_z8Qu1va8JOEcRKyDCRpcVdo1MncJta70iMqSBTmypRbtQ=s60",
                    iconBgColor = IOSPurple,
                    onClick = {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/Hendrizzzz/Ghostify"))
                        context.startActivity(intent)
                    }
                )
                IOSSettingsRow(
                    title = "Iconify",
                    subtitle = "Apache 2.0 · Universal icon framework powering bottom nav and system icons",
                    iconRes = R.drawable.ic_credit_iconify,
                    iconBgColor = Color(0xFF026C9C),
                    onClick = {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://icon-sets.iconify.design/"))
                        context.startActivity(intent)
                    }
                )
                IOSSettingsRow(
                    title = "Android Jetpack & Compose",
                    subtitle = "Apache 2.0 · Declarative UI framework, Navigation, and Lifecycle libraries",
                    iconRes = R.drawable.ic_credit_jetpack_compose,
                    iconBgColor = Color(0xFF1C1C1E),
                    onClick = {}
                )
                IOSSettingsRow(
                    title = "Room Database & SQLite",
                    subtitle = "Apache 2.0 · On-device SQLite persistence and screen time tracking",
                    iconRes = R.drawable.ic_credit_sqlite,
                    iconBgColor = Color(0xFFE8F4FC),
                    onClick = {}
                )
                IOSSettingsRow(
                    title = "Jetpack DataStore & Coroutines",
                    subtitle = "Apache 2.0 · Reactive asynchronous persistence and state management",
                    icon = Icons.Default.Star,
                    iconBgColor = IOSBlue,
                    onClick = {},
                    showDivider = false
                )
            }
        }

        item {
            Spacer(modifier = Modifier.height(40.dp))
        }
    }
}
