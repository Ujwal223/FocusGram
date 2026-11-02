package com.ujwal.focusgram.feature.guide

import android.view.HapticFeedbackConstants
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ujwal.focusgram.core.ui.theme.FocusGreen
import com.ujwal.focusgram.core.ui.theme.InstagramBlue
import kotlinx.coroutines.delay

/**
 * State machine representing progression of the initial interactive gesture onboarding guide.
 */
enum class GestureGuideStep {
    IDLE,
    DOUBLE_TAP_REFRESH,
    LONG_PRESS_PROFILE,
    SETTINGS_WAIT,
    SETTINGS_PRESS_BACK,
    COMPLETED
}

/**
 * Target direction for the thought bubble pointer tail on the bottom navigation bar.
 */
enum class GuideTailTarget {
    HOME,
    PROFILE,
    NONE
}

/**
 * Interactive, high-craft tooltip overlay guiding first-time users through the core FocusGram gestures:
 * 1. Double tap any tab icon to refresh the page (thought bubble pointing to Home icon on the left).
 * 2. Press & hold profile icon to open Settings (thought bubble pointing to Profile icon on the right).
 * 3. Wait 3 seconds inside Settings (with visual feedback).
 * 4. Instruct user to press device back button to return to home feed.
 */
@Composable
fun GestureGuideOverlay(
    currentStep: GestureGuideStep,
    onStepAdvanced: (GestureGuideStep) -> Unit,
    modifier: Modifier = Modifier
) {
    if (currentStep == GestureGuideStep.IDLE) return

    val view = LocalView.current
    val navBarInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    val settingsWaitProgress = remember { Animatable(0f) }

    // Handle 10-second delay on Settings page with animated progress bar
    LaunchedEffect(currentStep) {
        if (currentStep == GestureGuideStep.SETTINGS_WAIT) {
            settingsWaitProgress.snapTo(0f)
            settingsWaitProgress.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 10000, easing = LinearEasing)
            )
            view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
            onStepAdvanced(GestureGuideStep.SETTINGS_PRESS_BACK)
        } else if (currentStep == GestureGuideStep.COMPLETED) {
            delay(2200L)
            onStepAdvanced(GestureGuideStep.IDLE)
        }
    }

    // Height offset adjusted so thought-bubble pointer reaches comfortably above bottom nav bar
    val bottomOffset = if (currentStep == GestureGuideStep.SETTINGS_PRESS_BACK || currentStep == GestureGuideStep.SETTINGS_WAIT) {
        36.dp + navBarInset
    } else {
        74.dp + navBarInset
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(bottom = bottomOffset),
        contentAlignment = Alignment.BottomCenter
    ) {
        AnimatedVisibility(
            visible = currentStep == GestureGuideStep.DOUBLE_TAP_REFRESH,
            enter = slideInVertically(tween(350)) { it / 2 } + fadeIn(tween(300)),
            exit = slideOutVertically(tween(250)) { it / 2 } + fadeOut(tween(200))
        ) {
            GuideFloatingCard(
                icon = Icons.Default.Refresh,
                iconColor = InstagramBlue,
                title = "Double-tap to Refresh",
                subtitle = "Double-tap any icon on the bar to refresh the page.",
                tailTarget = GuideTailTarget.HOME
            )
        }

        AnimatedVisibility(
            visible = currentStep == GestureGuideStep.LONG_PRESS_PROFILE,
            enter = slideInVertically(tween(350)) { it / 2 } + fadeIn(tween(300)),
            exit = slideOutVertically(tween(250)) { it / 2 } + fadeOut(tween(200))
        ) {
            GuideFloatingCard(
                icon = Icons.Default.TouchApp,
                iconColor = Color(0xFFFFB74D),
                title = "Hold Profile for Settings",
                subtitle = "Press and hold your profile icon to open FocusGram settings.",
                tailTarget = GuideTailTarget.PROFILE
            )
        }

        AnimatedVisibility(
            visible = currentStep == GestureGuideStep.SETTINGS_WAIT,
            enter = slideInVertically(tween(350)) { it / 2 } + fadeIn(tween(300)),
            exit = slideOutVertically(tween(250)) { it / 2 } + fadeOut(tween(200))
        ) {
            GuideFloatingCard(
                icon = Icons.Default.Tune,
                iconColor = FocusGreen,
                title = "Settings Unlocked",
                subtitle = "Take a moment to explore your wellness settings...",
                progress = settingsWaitProgress.value,
                tailTarget = GuideTailTarget.NONE
            )
        }

        AnimatedVisibility(
            visible = currentStep == GestureGuideStep.SETTINGS_PRESS_BACK,
            enter = slideInVertically(tween(350)) { it / 2 } + fadeIn(tween(300)),
            exit = slideOutVertically(tween(250)) { it / 2 } + fadeOut(tween(200))
        ) {
            GuideFloatingCard(
                icon = Icons.AutoMirrored.Filled.ArrowBack,
                iconColor = InstagramBlue,
                title = "Return to Instagram",
                subtitle = "Press back to return to your feed.",
                tailTarget = GuideTailTarget.NONE
            )
        }

        AnimatedVisibility(
            visible = currentStep == GestureGuideStep.COMPLETED,
            enter = slideInVertically(tween(350)) { it / 2 } + fadeIn(tween(300)),
            exit = slideOutVertically(tween(300)) { it / 2 } + fadeOut(tween(250))
        ) {
            val isDark = androidx.compose.foundation.isSystemInDarkTheme()
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(22.dp))
                    .background(if (isDark) Color(0xE6121814) else Color(0xF2E8F5E9))
                    .border(1.dp, FocusGreen.copy(alpha = if (isDark) 0.5f else 0.8f), RoundedCornerShape(22.dp))
                    .padding(horizontal = 24.dp, vertical = 14.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        tint = FocusGreen,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "You're all set!",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = FocusGreen
                        )
                    )
                }
            }
        }
    }
}

@Composable
private fun GuideFloatingCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconColor: Color,
    title: String,
    subtitle: String,
    progress: Float? = null,
    tailTarget: GuideTailTarget = GuideTailTarget.NONE,
    onSkip: (() -> Unit)? = null
) {
    val isDark = androidx.compose.foundation.isSystemInDarkTheme()
    val cardBackground = if (isDark) Color(0xF216161A) else Color(0xFAFFFFFF)
    val cardBorder = if (isDark) Color(0x2EFFFFFF) else Color(0x26000000)
    val titleColor = if (isDark) Color.White else Color(0xFF111115)
    val subtitleColor = if (isDark) Color(0xFFC8C8D2) else Color(0xFF4A4A58)

    val infiniteTransition = rememberInfiniteTransition(label = "thought_tail_pulse")
    val tailBob by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 3f,
        animationSpec = infiniteRepeatable(
            animation = tween(1100, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "tail_bob"
    )

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Main thought bubble card container (increased height and padding)
        Box(
            modifier = Modifier
                .padding(horizontal = 20.dp)
                .shadow(
                    elevation = 16.dp,
                    shape = RoundedCornerShape(26.dp),
                    ambientColor = Color.Black.copy(alpha = if (isDark) 0.6f else 0.2f),
                    spotColor = Color.Black.copy(alpha = if (isDark) 0.85f else 0.35f)
                )
                .clip(RoundedCornerShape(26.dp))
                .background(cardBackground)
                .border(1.dp, cardBorder, RoundedCornerShape(26.dp))
                .padding(horizontal = 20.dp, vertical = 18.dp)
        ) {
            Column {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Start
                ) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(iconColor.copy(alpha = 0.14f))
                            .border(1.dp, iconColor.copy(alpha = 0.35f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = iconColor,
                            modifier = Modifier.size(26.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(16.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = titleColor,
                                fontSize = 17.sp,
                                letterSpacing = 0.2.sp
                            )
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                color = subtitleColor,
                                fontSize = 14.sp,
                                lineHeight = 19.sp
                            )
                        )
                    }

                    if (onSkip != null) {
                        IconButton(
                            onClick = onSkip,
                            modifier = Modifier.size(44.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Skip guide",
                                tint = if (isDark) Color(0xFF888894) else Color(0xFF8E8E93),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }

                if (progress != null) {
                    Spacer(modifier = Modifier.height(14.dp))
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp)),
                        color = iconColor,
                        trackColor = if (isDark) Color(0x33FFFFFF) else Color(0x22000000)
                    )
                }
            }
        }

        // Thought bubble tail / pointer extending towards bottom navigation bar icons
        if (tailTarget != GuideTailTarget.NONE) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .offset(y = tailBob.dp),
                contentAlignment = if (tailTarget == GuideTailTarget.HOME) Alignment.TopStart else Alignment.TopEnd
            ) {
                Column(
                    modifier = Modifier.padding(
                        start = if (tailTarget == GuideTailTarget.HOME) 26.dp else 0.dp,
                        end = if (tailTarget == GuideTailTarget.PROFILE) 26.dp else 0.dp
                    ),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Downward beak triangle pointer merging into card base
                    DownwardPointerTriangle(
                        color = cardBackground,
                        borderColor = cardBorder
                    )

                    Spacer(modifier = Modifier.height(3.dp))

                    // Thought bubble primary circle
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(cardBackground)
                            .border(1.dp, cardBorder, CircleShape)
                    )

                    Spacer(modifier = Modifier.height(2.5.dp))

                    // Thought bubble tip accent circle closest to tab icon
                    Box(
                        modifier = Modifier
                            .size(5.dp)
                            .clip(CircleShape)
                            .background(iconColor.copy(alpha = 0.85f))
                    )
                }
            }
        }
    }
}

@Composable
private fun DownwardPointerTriangle(
    color: Color,
    borderColor: Color,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier.size(width = 16.dp, height = 9.dp)) {
        val path = Path().apply {
            moveTo(0f, 0f)
            lineTo(size.width, 0f)
            lineTo(size.width / 2f, size.height)
            close()
        }
        drawPath(path, color = color)

        // Left and right angled borders
        drawLine(
            color = borderColor,
            start = Offset(0f, 0f),
            end = Offset(size.width / 2f, size.height),
            strokeWidth = 1.dp.toPx()
        )
        drawLine(
            color = borderColor,
            start = Offset(size.width, 0f),
            end = Offset(size.width / 2f, size.height),
            strokeWidth = 1.dp.toPx()
        )
    }
}
