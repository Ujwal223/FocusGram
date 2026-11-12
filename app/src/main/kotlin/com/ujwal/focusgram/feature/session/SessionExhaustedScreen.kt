package com.ujwal.focusgram.feature.session

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ujwal.focusgram.core.ui.theme.PureBlack
import com.ujwal.focusgram.core.ui.theme.PureWhite
import com.ujwal.focusgram.core.utils.HapticHelper
import com.ujwal.focusgram.core.utils.TimeFormatters

/**
 * Lockdown gate presented when a timed session expires.
 *
 * Consumes back gestures and clicks to prevent bypassing the limit without exiting or extending.
 */
@Composable
fun SessionExhaustedScreen(
    elapsedMinutes: Int,
    extensionsRemaining: Int,
    onExit: () -> Unit,
    onWatchAdToExtend: () -> Unit,
    modifier: Modifier = Modifier,
    adRewardsEnabled: Boolean = true,
    onDebugBypass: (() -> Unit)? = null
) {
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        HapticHelper.performSessionCompletionAlert(context)
    }

    // Intercepts back navigation: exits the app rather than returning to the active session
    BackHandler(enabled = true) {
        onExit()
    }

    val isDark = isSystemInDarkTheme()

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(if (isDark) PureBlack else PureWhite)
            // Consumes all touches so clicks never bleed through to GeckoView underneath
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {},
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
        ) {
            Spacer(modifier = Modifier.height(32.dp))

            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(if (isDark) Color(0xFF1C1C1E) else Color(0xFFF2F2F7))
                    .border(1.dp, if (isDark) Color(0xFF2C2C2E) else Color(0xFFE5E5EA), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.HourglassTop,
                    contentDescription = null,
                    tint = if (isDark) Color.White else PureBlack,
                    modifier = Modifier.size(30.dp)
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = "Time Reached",
                color = if (isDark) Color.White else Color(0xFF111115),
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-0.5).sp
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Your planned Instagram session has ended.\nTake a mindful break from screens.",
                color = if (isDark) Color(0xFF8E8E93) else Color(0xFF6C6C70),
                fontSize = 15.sp,
                textAlign = TextAlign.Center,
                lineHeight = 20.sp,
                modifier = Modifier.padding(horizontal = 16.dp)
            )

            if (com.ujwal.focusgram.BuildConfig.DEBUG && onDebugBypass != null) {
                Spacer(modifier = Modifier.height(16.dp))
                androidx.compose.material3.TextButton(
                    onClick = onDebugBypass
                ) {
                    Text(
                        text = "Skip Session Limit (Debug)",
                        color = Color(0xFFFF9800),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(if (isDark) Color(0xFF1C1C1E) else Color(0xFFF2F2F7))
                    .border(0.5.dp, if (isDark) Color(0xFF2C2C2E) else Color(0xFFE5E5EA), RoundedCornerShape(16.dp))
                    .padding(horizontal = 16.dp, vertical = 14.dp)
            ) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Session Duration",
                            color = if (isDark) Color(0xFF8E8E93) else Color(0xFF6C6C70),
                            fontSize = 14.sp
                        )
                        Text(
                            text = "${TimeFormatters.formatMinutes(elapsedMinutes)} completed",
                            color = if (isDark) Color.White else Color(0xFF111115),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    HorizontalDivider(
                        color = if (isDark) Color(0xFF2C2C2E) else Color(0xFFE5E5EA),
                        thickness = 0.5.dp,
                        modifier = Modifier.padding(vertical = 12.dp)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Extensions Available",
                            color = if (isDark) Color(0xFF8E8E93) else Color(0xFF6C6C70),
                            fontSize = 14.sp
                        )
                        Text(
                            text = "$extensionsRemaining / 3 remaining",
                            color = if (extensionsRemaining > 0) (if (isDark) Color.White else Color(0xFF111115)) else Color(0xFFFF453A),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(36.dp))

            Button(
                onClick = onExit,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(26.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isDark) Color.White else PureBlack,
                    contentColor = if (isDark) Color.Black else PureWhite
                )
            ) {
                Text(
                    text = "Close Instagram",
                    color = if (isDark) Color.Black else PureWhite,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            if (adRewardsEnabled && extensionsRemaining > 0) {
                Spacer(modifier = Modifier.height(12.dp))

                OutlinedButton(
                    onClick = onWatchAdToExtend,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = RoundedCornerShape(26.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, if (isDark) Color(0xFF2C2C2E) else Color(0xFFE5E5EA)),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = if (isDark) Color(0xFF1C1C1E) else Color(0xFFF2F2F7),
                        contentColor = if (isDark) Color.White else PureBlack
                    )
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = null,
                            tint = if (isDark) Color.White else PureBlack,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Watch Ad to Extend (+3 min)",
                            color = if (isDark) Color.White else PureBlack,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            if (com.ujwal.focusgram.BuildConfig.DEBUG && onDebugBypass != null) {
                Spacer(modifier = Modifier.height(16.dp))
                androidx.compose.material3.TextButton(
                    onClick = onDebugBypass
                ) {
                    Text(
                        text = "Skip Session Limit (Debug)",
                        color = Color(0xFFFF9800),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}
