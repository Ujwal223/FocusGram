package com.ujwal.focusgram.feature.wellness

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ujwal.focusgram.core.ui.theme.DarkCard
import com.ujwal.focusgram.core.ui.theme.PureBlack
import com.ujwal.focusgram.core.ui.theme.TextMuted
import com.ujwal.focusgram.core.ui.theme.TextSecondary
import com.ujwal.focusgram.core.utils.HapticHelper
import kotlinx.coroutines.delay

/**
 * Fullscreen lock requiring the user to wait out a cooldown period between sessions.
 */
@Composable
fun CooldownGateScreen(
    remainingSeconds: Int = 0,
    remainingSecondsInitial: Int = remainingSeconds,
    onCooldownExpired: () -> Unit,
    onExit: () -> Unit,
    onDebugBypass: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val effectiveRemaining = (if (remainingSeconds > 0) remainingSeconds else remainingSecondsInitial).coerceAtLeast(0)
    val debugBypassAction = onDebugBypass ?: onCooldownExpired

    val quotes = remember {
        listOf(
            "\"The discipline you show offline shapes the clarity you experience online.\"",
            "\"Every moment away from the screen is a moment given back to yourself.\"",
            "\"Boredom is the birthplace of creativity. Let it breathe.\"",
            "\"Your attention is your most valuable asset. Spend it wisely.\"",
            "\"Rest is not wasted time. It is the foundation of focused action.\""
        )
    }

    val quote = remember { quotes.random() }

    LaunchedEffect(effectiveRemaining) {
        if (effectiveRemaining <= 0) {
            HapticHelper.performSuccessPulse(context)
            onCooldownExpired()
        }
    }

    val minutes = effectiveRemaining / 60
    val seconds = effectiveRemaining % 60
    val formattedTime = String.format("%02d:%02d", minutes, seconds)

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = PureBlack
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFFF9800).copy(alpha = 0.12f))
                    .border(1.5.dp, Color(0xFFFF9800).copy(alpha = 0.4f), CircleShape)
                    .then(
                        if (com.ujwal.focusgram.BuildConfig.DEBUG) {
                            Modifier.clickable { debugBypassAction() }
                        } else {
                            Modifier
                        }
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.HourglassTop,
                    contentDescription = null,
                    tint = Color(0xFFFF9800),
                    modifier = Modifier.size(38.dp)
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            Text(
                text = "Take a Break",
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.5).sp
                ),
                color = Color.White
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "Your session has ended.\nCome back when the cooldown timer expires.",
                style = MaterialTheme.typography.bodyLarge.copy(lineHeight = 22.sp),
                color = TextSecondary,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(40.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(DarkCard)
                    .border(1.dp, Color(0xFF262626), RoundedCornerShape(20.dp))
                    .padding(vertical = 24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "COOLDOWN REMAINING",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFFFF9800),
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = formattedTime,
                        fontSize = 44.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        letterSpacing = 2.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(36.dp))

            Text(
                text = quote,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontStyle = FontStyle.Italic,
                    lineHeight = 22.sp
                ),
                color = TextMuted,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(48.dp))

            OutlinedButton(
                onClick = onExit,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(26.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
            ) {
                Text(
                    text = "Close App & Rest",
                    fontWeight = FontWeight.SemiBold
                )
            }

            if (com.ujwal.focusgram.BuildConfig.DEBUG) {
                Spacer(modifier = Modifier.height(12.dp))

                TextButton(
                    onClick = debugBypassAction
                ) {
                    Text(
                        text = "Skip Cooldown (Debug)",
                        color = Color(0xFFFF9800),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}
