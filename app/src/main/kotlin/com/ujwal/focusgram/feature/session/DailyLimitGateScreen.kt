package com.ujwal.focusgram.feature.session

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.material.icons.filled.HourglassBottom
import androidx.compose.material.icons.filled.Lock
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
import com.ujwal.focusgram.core.ui.theme.AlertRed
import com.ujwal.focusgram.core.ui.theme.PureBlack
import com.ujwal.focusgram.core.utils.HapticHelper
import com.ujwal.focusgram.core.utils.TimeFormatters

/**
 * Fullscreen gate shown when the user's global daily screen time limit is reached.
 * Allows up to 2 extensions (5 minutes each). Once exhausted, blocks app access for today.
 */
@Composable
fun DailyLimitGateScreen(
    dailyLimitMinutes: Int,
    todayMinutesUsed: Int,
    extensionsRemaining: Int,
    onExit: () -> Unit,
    onWatchAdToExtend: () -> Unit,
    onDebugBypass: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        HapticHelper.performSessionCompletionAlert(context)
    }

    // Intercepts back navigation: closes the app so user cannot bypass
    BackHandler(enabled = true) {
        onExit()
    }

    val isBlockedForToday = extensionsRemaining <= 0

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(PureBlack)
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
                    .size(68.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF1C1C1E))
                    .border(1.dp, if (isBlockedForToday) AlertRed.copy(alpha = 0.5f) else Color(0xFF2C2C2E), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isBlockedForToday) Icons.Default.Lock else Icons.Default.HourglassBottom,
                    contentDescription = null,
                    tint = if (isBlockedForToday) AlertRed else Color.White,
                    modifier = Modifier.size(32.dp)
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = if (isBlockedForToday) "Daily Limit Reached" else "Daily Limit Reached",
                color = Color.White,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-0.5).sp
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = if (isBlockedForToday) {
                    "You have reached your daily Instagram screen time limit and used all extensions. FocusGram is locked for the rest of today."
                } else {
                    "You have reached your planned daily Instagram limit of ${TimeFormatters.formatMinutes(dailyLimitMinutes)}. Take a break or extend."
                },
                color = Color(0xFF8E8E93),
                fontSize = 15.sp,
                textAlign = TextAlign.Center,
                lineHeight = 21.sp,
                modifier = Modifier.padding(horizontal = 16.dp)
            )

            if (com.ujwal.focusgram.BuildConfig.DEBUG && onDebugBypass != null) {
                Spacer(modifier = Modifier.height(16.dp))
                androidx.compose.material3.TextButton(
                    onClick = onDebugBypass
                ) {
                    Text(
                        text = "Skip Daily Limit (Debug)",
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
                    .background(Color(0xFF1C1C1E))
                    .border(0.5.dp, Color(0xFF2C2C2E), RoundedCornerShape(16.dp))
                    .padding(horizontal = 16.dp, vertical = 14.dp)
            ) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Today's Usage",
                            color = Color(0xFF8E8E93),
                            fontSize = 14.sp
                        )
                        Text(
                            text = TimeFormatters.formatMinutes(todayMinutesUsed),
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    HorizontalDivider(
                        color = Color(0xFF2C2C2E),
                        thickness = 0.5.dp,
                        modifier = Modifier.padding(vertical = 12.dp)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Daily Limit Set",
                            color = Color(0xFF8E8E93),
                            fontSize = 14.sp
                        )
                        Text(
                            text = TimeFormatters.formatMinutes(dailyLimitMinutes),
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    HorizontalDivider(
                        color = Color(0xFF2C2C2E),
                        thickness = 0.5.dp,
                        modifier = Modifier.padding(vertical = 12.dp)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Daily Extensions",
                            color = Color(0xFF8E8E93),
                            fontSize = 14.sp
                        )
                        Text(
                            text = if (isBlockedForToday) "0 / 2 remaining (Locked)" else "$extensionsRemaining / 2 remaining",
                            color = if (extensionsRemaining > 0) Color.White else AlertRed,
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
                    containerColor = Color.White,
                    contentColor = Color.Black
                )
            ) {
                Text(
                    text = "Close Instagram",
                    color = Color.Black,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            if (!isBlockedForToday) {
                Spacer(modifier = Modifier.height(12.dp))

                OutlinedButton(
                    onClick = onWatchAdToExtend,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = RoundedCornerShape(26.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF2C2C2E)),
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
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Watch Ad to Extend (+5m)",
                            color = Color.White,
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
                        text = "Skip Daily Limit (Debug)",
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
