package com.ujwal.focusgram.feature.wellness

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
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
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ujwal.focusgram.core.ui.theme.FocusGold
import com.ujwal.focusgram.core.ui.theme.InstagramBlue
import com.ujwal.focusgram.core.ui.theme.PureBlack
import com.ujwal.focusgram.core.ui.theme.PureWhite
import com.ujwal.focusgram.core.utils.HapticHelper
import kotlinx.coroutines.delay

/**
 * Interstitial countdown screen enforcing a mandatory breathing delay before app access.
 */
@Composable
fun BreathGateScreen(
    durationSeconds: Int = 10,
    onFinish: () -> Unit
) {
    val context = LocalContext.current
    val initialSeconds = durationSeconds.coerceIn(3, 60)
    var secondsRemaining by remember { mutableIntStateOf(initialSeconds) }
    val canContinue = secondsRemaining <= 0

    LaunchedEffect(Unit) {
        while (secondsRemaining > 0) {
            delay(1000L)
            secondsRemaining--
        }
        HapticHelper.performSuccessPulse(context)
    }

    val infiniteTransition = rememberInfiniteTransition(label = "breath_circle_transition")
    val breathScale by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = 1.55f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "breath_scale"
    )

    val isDark = isSystemInDarkTheme()

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = if (isDark) PureBlack else PureWhite
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .padding(horizontal = 36.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "Are you sure you want to open Instagram?",
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.Light,
                    fontSize = 21.sp,
                    lineHeight = 28.sp
                ),
                color = if (isDark) Color.White else Color(0xFF111115)
            )

            Spacer(modifier = Modifier.height(72.dp))

            // Animated Mindful Breathing Pulse Circle
            Box(
                modifier = Modifier
                    .size(110.dp)
                    .scale(breathScale)
                    .background(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                InstagramBlue.copy(alpha = 0.9f),
                                InstagramBlue.copy(alpha = 0.3f),
                                Color.Transparent
                            )
                        ),
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(45.dp)
                        .background((if (isDark) Color.White else PureBlack).copy(alpha = 0.25f), CircleShape)
                )
            }

            Spacer(modifier = Modifier.height(72.dp))

            Text(
                text = if (canContinue) "Breathed." else "Take a deep breath for $secondsRemaining seconds...",
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontStyle = FontStyle.Italic,
                    color = if (isDark) Color.White.copy(alpha = 0.75f) else Color.Black.copy(alpha = 0.75f)
                ),
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(48.dp))

            Button(
                onClick = onFinish,
                enabled = canContinue,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(26.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isDark) Color.White else PureBlack,
                    contentColor = if (isDark) Color.Black else PureWhite,
                    disabledContainerColor = if (isDark) Color.White.copy(alpha = 0.12f) else Color.Black.copy(alpha = 0.08f),
                    disabledContentColor = if (isDark) Color.White.copy(alpha = 0.35f) else Color.Black.copy(alpha = 0.35f)
                )
            ) {
                Text(
                    text = "Continue to Instagram",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp,
                    color = if (canContinue) (if (isDark) Color.Black else PureWhite) else (if (isDark) Color.White.copy(alpha = 0.35f) else Color.Black.copy(alpha = 0.35f))
                )
            }

            if (com.ujwal.focusgram.BuildConfig.DEBUG) {
                Spacer(modifier = Modifier.height(14.dp))
                TextButton(
                    onClick = onFinish,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "⚡ Skip Breathing (Debug)",
                        color = FocusGold,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }
            }
        }
    }
}
