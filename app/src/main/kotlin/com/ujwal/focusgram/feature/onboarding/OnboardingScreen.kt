package com.ujwal.focusgram.feature.onboarding

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.view.HapticFeedbackConstants
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.HourglassBottom
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ujwal.focusgram.R
import com.ujwal.focusgram.core.datastore.SettingsRepository
import com.ujwal.focusgram.core.model.FocusSettings
import com.ujwal.focusgram.core.ui.components.CupertinoWheelPicker
import com.ujwal.focusgram.core.utils.AppIconManager
import com.ujwal.focusgram.core.ui.theme.DarkBorder
import com.ujwal.focusgram.core.ui.theme.DarkCard
import com.ujwal.focusgram.core.ui.theme.DarkCardElevated
import com.ujwal.focusgram.core.ui.theme.FocusGold
import com.ujwal.focusgram.core.ui.theme.FocusGreen
import com.ujwal.focusgram.core.ui.theme.InstagramBlue
import com.ujwal.focusgram.core.ui.theme.InstagramPink
import com.ujwal.focusgram.core.ui.theme.PureBlack
import com.ujwal.focusgram.core.ui.theme.TextMuted
import com.ujwal.focusgram.core.ui.theme.TextPrimary
import com.ujwal.focusgram.core.ui.theme.TextSecondary
import com.ujwal.focusgram.core.utils.TimeFormatters
import kotlinx.coroutines.launch

@Composable
fun OnboardingScreen(
    settings: FocusSettings,
    settingsRepository: SettingsRepository,
    onComplete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    val totalPages = 6
    val pagerState = rememberPagerState(pageCount = { totalPages })

    var notificationGranted by remember { mutableStateOf(false) }
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        notificationGranted = isGranted
        view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(PureBlack)
            .padding(WindowInsets.statusBars.asPaddingValues())
            .padding(WindowInsets.navigationBars.asPaddingValues())
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${pagerState.currentPage + 1} of $totalPages",
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = TextMuted,
                        fontWeight = FontWeight.Medium,
                        letterSpacing = 0.5.sp
                    )
                )

                if (pagerState.currentPage < totalPages - 1) {
                    TextButton(
                        onClick = {
                            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                            scope.launch {
                                settingsRepository.setFirstRunCompleted()
                                onComplete()
                            }
                        }
                    ) {
                        Text(
                            text = "Skip",
                            style = MaterialTheme.typography.labelMedium.copy(
                                color = TextMuted,
                                fontWeight = FontWeight.SemiBold
                            )
                        )
                    }
                } else {
                    Spacer(modifier = Modifier.width(48.dp))
                }
            }

            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) { page ->
                when (page) {
                    0 -> WelcomeSlide()
                    1 -> TimeIntentSlide(settings = settings, repository = settingsRepository)
                    2 -> CalmControlsSlide(settings = settings, repository = settingsRepository)
                    3 -> LinkHandlingSlide()
                    4 -> NotificationSlide(
                        isGranted = notificationGranted,
                        onRequestPermission = {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            } else {
                                notificationGranted = true
                            }
                        }
                    )
                    5 -> LoginSecuritySlide()
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(bottom = 20.dp)
                ) {
                    repeat(totalPages) { index ->
                        val isSelected = pagerState.currentPage == index
                        val width by animateDpAsState(
                            targetValue = if (isSelected) 22.dp else 7.dp,
                            animationSpec = tween(durationMillis = 250),
                            label = "dot_width"
                        )
                        Box(
                            modifier = Modifier
                                .padding(horizontal = 3.dp)
                                .height(7.dp)
                                .width(width)
                                .clip(RoundedCornerShape(4.dp))
                                .background(
                                    if (isSelected) InstagramBlue else DarkCardElevated
                                )
                        )
                    }
                }

                val isLastPage = pagerState.currentPage == totalPages - 1
                Button(
                    onClick = {
                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        scope.launch {
                            if (isLastPage) {
                                settingsRepository.setFirstRunCompleted()
                                onComplete()
                            } else {
                                pagerState.animateScrollToPage(
                                    page = pagerState.currentPage + 1,
                                    animationSpec = tween(durationMillis = 350)
                                )
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = InstagramBlue),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text(
                        text = if (isLastPage) "Get Started" else "Continue",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = PureBlack
                        )
                    )
                }
            }
        }
    }
}

@Composable
private fun WelcomeSlide() {
    val context = LocalContext.current
    val scrollState = rememberScrollState()
    val appIconBitmap = remember(context) {
        AppIconManager.getIconBitmap(context, R.mipmap.ic_launcher)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(horizontal = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(92.dp)
                .clip(RoundedCornerShape(22.dp))
                .background(DarkCard)
                .border(1.5.dp, DarkBorder, RoundedCornerShape(22.dp)),
            contentAlignment = Alignment.Center
        ) {
            if (appIconBitmap != null) {
                Image(
                    bitmap = appIconBitmap.asImageBitmap(),
                    contentDescription = "FocusGram App Logo",
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(22.dp))
                )
            } else {
                Icon(
                    imageVector = Icons.Default.HourglassBottom,
                    contentDescription = "FocusGram App Logo",
                    tint = FocusGold,
                    modifier = Modifier.size(44.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(28.dp))

        Text(
            text = "Welcome to FocusGram",
            style = MaterialTheme.typography.headlineMedium.copy(
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            ),
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(10.dp))

        Text(
            text = "Use Instagram with healthy boundaries, zero tracking algorithms, and complete digital wellness.",
            style = MaterialTheme.typography.bodyMedium.copy(
                color = TextSecondary,
                lineHeight = 22.sp
            ),
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(32.dp))

        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            ValuePropRow(
                icon = Icons.Default.HourglassBottom,
                tint = FocusGold,
                title = "Timed Reel Sessions",
                subtitle = "Watch Reels intentionally with timers instead of infinite feeds."
            )
            ValuePropRow(
                icon = Icons.Default.VisibilityOff,
                tint = InstagramBlue,
                title = "Calm Feed & Explore",
                subtitle = "Blur or hide addictive feeds, recommendations, and explore grids."
            )
            ValuePropRow(
                icon = Icons.Default.Shield,
                tint = FocusGreen,
                title = "100% Client-Side Privacy",
                subtitle = "Zero external servers, zero telemetry, zero credential interception."
            )
        }
    }
}

@Composable
private fun LoginSecuritySlide() {
    val scrollState = rememberScrollState()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(80.dp)
                .clip(CircleShape)
                .background(FocusGreen.copy(alpha = 0.14f))
                .border(1.5.dp, FocusGreen.copy(alpha = 0.5f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Security,
                contentDescription = null,
                tint = FocusGreen,
                modifier = Modifier.size(40.dp)
            )
        }

        Spacer(modifier = Modifier.height(22.dp))

        Text(
            text = "Official Instagram Login",
            style = MaterialTheme.typography.headlineMedium.copy(
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            ),
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Direct, secure, and 100% private",
            style = MaterialTheme.typography.bodySmall.copy(
                color = TextSecondary,
                fontSize = 13.sp
            ),
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(24.dp))

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SecurityCard(
                icon = Icons.Default.Lock,
                title = "Direct Official Login",
                body = "You sign in straight on instagram.com inside isolated GeckoView. FocusGram never touches your credentials."
            )

            SecurityCard(
                icon = Icons.Default.Shield,
                title = "Complete Black Box",
                body = "The login interface is an isolated web container. Passwords and 2FA credentials can never be intercepted or seen."
            )

            SecurityCard(
                icon = Icons.Default.CheckCircle,
                title = "Zero Off-Device Data",
                body = "100% on-device and open source. Zero analytics, zero trackers, zero external servers."
            )
        }
    }
}

@Composable
private fun TimeIntentSlide(
    settings: FocusSettings,
    repository: SettingsRepository
) {
    val scrollState = rememberScrollState()
    val scope = rememberCoroutineScope()
    val view = LocalView.current
    val dailyLimitOptions = remember { listOf(15, 30, 45, 60, 75, 90, 120, 150, 180, 240) }
    val selectedIndex = remember(settings.dailyLimitMinutes) {
        val exact = dailyLimitOptions.indexOf(settings.dailyLimitMinutes)
        if (exact >= 0) exact
        else {
            val closest = dailyLimitOptions.indexOfFirst { it >= settings.dailyLimitMinutes }
            if (closest >= 0) closest else dailyLimitOptions.lastIndex
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(80.dp)
                .clip(CircleShape)
                .background(FocusGold.copy(alpha = 0.15f))
                .border(1.5.dp, FocusGold.copy(alpha = 0.5f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.HourglassBottom,
                contentDescription = null,
                tint = FocusGold,
                modifier = Modifier.size(40.dp)
            )
        }

        Spacer(modifier = Modifier.height(22.dp))

        Text(
            text = "Time With Intent",
            style = MaterialTheme.typography.headlineMedium.copy(
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            ),
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Set daily limits and calm pause gates to prevent mindless scrolling.",
            style = MaterialTheme.typography.bodyMedium.copy(color = TextSecondary),
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(20.dp))

        Text(
            text = "Daily Screen Time Limit: ${TimeFormatters.formatMinutes(settings.dailyLimitMinutes)}",
            style = MaterialTheme.typography.labelLarge.copy(
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary
            )
        )

        Spacer(modifier = Modifier.height(8.dp))

        CupertinoWheelPicker(
            items = dailyLimitOptions,
            selectedIndex = selectedIndex,
            onItemSelected = { idx ->
                val chosen = dailyLimitOptions.getOrElse(idx) { 60 }
                scope.launch { repository.setDailyLimitMinutes(chosen) }
            },
            labelProvider = { TimeFormatters.formatMinutes(it) },
            itemHeight = 44.dp,
            visibleItemsCount = 5,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp)
        )

        Spacer(modifier = Modifier.height(16.dp))

        ToggleRow(
            title = "Mindfulness Breath Gate",
            subtitle = "A brief 10-second pause before opening the app to reset intention.",
            checked = settings.showBreathGate,
            onCheckedChange = { checked ->
                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                scope.launch { repository.setShowBreathGate(checked) }
            }
        )
    }
}

@Composable
private fun CalmControlsSlide(
    settings: FocusSettings,
    repository: SettingsRepository
) {
    val scrollState = rememberScrollState()
    val scope = rememberCoroutineScope()
    val view = LocalView.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(80.dp)
                .clip(CircleShape)
                .background(InstagramPink.copy(alpha = 0.15f))
                .border(1.5.dp, InstagramPink.copy(alpha = 0.5f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.VisibilityOff,
                contentDescription = null,
                tint = InstagramPink,
                modifier = Modifier.size(40.dp)
            )
        }

        Spacer(modifier = Modifier.height(22.dp))

        Text(
            text = "Declutter Your Mind",
            style = MaterialTheme.typography.headlineMedium.copy(
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            ),
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Choose which addictive parts of Instagram to quiet down by default:",
            style = MaterialTheme.typography.bodyMedium.copy(color = TextSecondary),
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(24.dp))

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            ToggleRow(
                title = "Hide Feed Posts",
                subtitle = "Removes main feed posts so you only open Instagram with purpose.",
                checked = settings.hideFeedPosts,
                onCheckedChange = { checked ->
                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    scope.launch { repository.setHideFeedPosts(checked) }
                }
            )

            ToggleRow(
                title = "Block Video Autoplay",
                subtitle = "Stops videos and reels from playing automatically.",
                checked = settings.noAutoplay,
                onCheckedChange = { checked ->
                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    scope.launch { repository.setNoAutoplay(checked) }
                }
            )

            ToggleRow(
                title = "Hide Stories Tray",
                subtitle = "Hides the top stories bar to prevent distraction.",
                checked = settings.noStories,
                onCheckedChange = { checked ->
                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    scope.launch { repository.setNoStories(checked) }
                }
            )
        }
    }
}

@Composable
private fun LinkHandlingSlide() {
    val context = LocalContext.current
    val view = LocalView.current
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(80.dp)
                .clip(CircleShape)
                .background(InstagramBlue.copy(alpha = 0.15f))
                .border(1.5.dp, InstagramBlue.copy(alpha = 0.5f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Link,
                contentDescription = null,
                tint = InstagramBlue,
                modifier = Modifier.size(40.dp)
            )
        }

        Spacer(modifier = Modifier.height(22.dp))

        Text(
            text = "Open Links in FocusGram",
            style = MaterialTheme.typography.headlineMedium.copy(
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            ),
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Handle instagram.com links here instead of launching the tracking-heavy official app.",
            style = MaterialTheme.typography.bodyMedium.copy(color = TextSecondary),
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(24.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .border(
                    BorderStroke(
                        1.dp,
                        Brush.verticalGradient(listOf(Color(0x38FFFFFF), Color(0x10FFFFFF)))
                    ),
                    RoundedCornerShape(16.dp)
                )
                .background(
                    Brush.verticalGradient(listOf(Color(0xEB1E1E22), Color(0xD9161619)))
                )
                .padding(18.dp)
        ) {
            Column {
                Text(
                    text = "Recommended Setup",
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "1. Tap \"Configure App Links\" below\n2. Select \"Open by default\"\n3. Tap \"Add link\" and check all instagram.com domains",
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = TextSecondary,
                        lineHeight = 20.sp
                    )
                )
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedButton(
                    onClick = {
                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        openDefaultLinkSettings(context)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = "Configure App Links",
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontWeight = FontWeight.Bold,
                            color = InstagramBlue
                        )
                    )
                }
            }
        }
    }
}

@Composable
private fun NotificationSlide(
    isGranted: Boolean,
    onRequestPermission: () -> Unit
) {
    val scrollState = rememberScrollState()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(80.dp)
                .clip(CircleShape)
                .background(FocusGreen.copy(alpha = 0.15f))
                .border(1.5.dp, FocusGreen.copy(alpha = 0.5f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.NotificationsActive,
                contentDescription = null,
                tint = FocusGreen,
                modifier = Modifier.size(40.dp)
            )
        }

        Spacer(modifier = Modifier.height(22.dp))

        Text(
            text = "Useful Alerts Only",
            style = MaterialTheme.typography.headlineMedium.copy(
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            ),
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "FocusGram only notifies you when your session timer expires or when cooldown completes. Never any engagement hooks or spam.",
            style = MaterialTheme.typography.bodyMedium.copy(
                color = TextSecondary,
                lineHeight = 22.sp
            ),
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(28.dp))

        if (isGranted) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(FocusGreen.copy(alpha = 0.14f))
                    .border(1.dp, FocusGreen.copy(alpha = 0.4f), RoundedCornerShape(14.dp))
                    .padding(14.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    tint = FocusGreen,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Notifications Enabled",
                    style = MaterialTheme.typography.labelLarge.copy(
                        color = FocusGreen,
                        fontWeight = FontWeight.Bold
                    )
                )
            }
        } else {
            OutlinedButton(
                onClick = onRequestPermission,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
            ) {
                Text(
                    text = "Enable Session Notifications",
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontWeight = FontWeight.Bold,
                        color = FocusGreen
                    )
                )
            }
        }
    }
}

@Composable
private fun ValuePropRow(
    icon: ImageVector,
    tint: Color,
    title: String,
    subtitle: String
) {
    val shape = RoundedCornerShape(14.dp)
    val glassBorderBrush = Brush.verticalGradient(
        colors = listOf(Color(0x38FFFFFF), Color(0x10FFFFFF))
    )
    val glassBgBrush = Brush.verticalGradient(
        colors = listOf(Color(0xEB1E1E22), Color(0xD9161619))
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .border(BorderStroke(1.dp, glassBorderBrush), shape)
            .background(glassBgBrush)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(tint.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
        }
        Spacer(modifier = Modifier.width(14.dp))
        Column {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall.copy(
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary
                )
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall.copy(
                    color = TextSecondary,
                    fontSize = 12.sp,
                    lineHeight = 16.sp
                )
            )
        }
    }
}

@Composable
private fun SecurityCard(
    icon: ImageVector,
    title: String,
    body: String
) {
    val shape = RoundedCornerShape(14.dp)
    val glassBorderBrush = Brush.verticalGradient(
        colors = listOf(Color(0x38FFFFFF), Color(0x10FFFFFF))
    )
    val glassBgBrush = Brush.verticalGradient(
        colors = listOf(Color(0xEB1E1E22), Color(0xD9161619))
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .border(BorderStroke(1.dp, glassBorderBrush), shape)
            .background(glassBgBrush)
            .padding(14.dp),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(FocusGreen.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = FocusGreen, modifier = Modifier.size(18.dp))
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall.copy(
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary
                )
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = body,
                style = MaterialTheme.typography.bodySmall.copy(
                    color = TextSecondary,
                    fontSize = 12.sp,
                    lineHeight = 17.sp
                )
            )
        }
    }
}

@Composable
private fun ToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    val shape = RoundedCornerShape(14.dp)
    val glassBorderBrush = Brush.verticalGradient(
        colors = listOf(Color(0x38FFFFFF), Color(0x10FFFFFF))
    )
    val glassBgBrush = Brush.verticalGradient(
        colors = listOf(Color(0xEB1E1E22), Color(0xD9161619))
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .border(BorderStroke(1.dp, glassBorderBrush), shape)
            .background(glassBgBrush)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall.copy(
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary
                )
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall.copy(
                    color = TextSecondary,
                    fontSize = 12.sp
                )
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = PureBlack,
                checkedTrackColor = InstagramBlue,
                uncheckedThumbColor = TextMuted,
                uncheckedTrackColor = DarkCardElevated
            )
        )
    }
}

private fun openDefaultLinkSettings(context: Context) {
    try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val intent = Intent(
                Settings.ACTION_APP_OPEN_BY_DEFAULT_SETTINGS,
                Uri.parse("package:${context.packageName}")
            ).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } else {
            val intent = Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.parse("package:${context.packageName}")
            ).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }
    } catch (_: Exception) {
        val intent = Intent(Settings.ACTION_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }
}
