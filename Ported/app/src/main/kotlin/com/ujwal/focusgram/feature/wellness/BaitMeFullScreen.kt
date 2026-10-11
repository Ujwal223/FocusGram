package com.ujwal.focusgram.feature.wellness

import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ujwal.focusgram.core.ui.components.FocusGramCard
import com.ujwal.focusgram.core.ui.theme.AlertRed
import com.ujwal.focusgram.core.ui.theme.DarkCard
import com.ujwal.focusgram.core.ui.theme.DarkCardElevated
import com.ujwal.focusgram.core.ui.theme.FocusGold
import com.ujwal.focusgram.core.ui.theme.FocusGreen
import com.ujwal.focusgram.core.ui.theme.PureBlack
import com.ujwal.focusgram.core.ui.theme.TextMuted
import com.ujwal.focusgram.core.ui.theme.TextPrimary
import com.ujwal.focusgram.core.ui.theme.TextSecondary
import kotlinx.coroutines.launch

/**
 * BaitMeFullScreen
 * Fullscreen gamification spin wheel screen.
 */
@Composable
fun BaitMeFullScreen(
    baitEngine: BaitEngine,
    onClose: () -> Unit,
    onResetSession: () -> Unit,
    onEndSession: () -> Unit,
    onOpenUrl: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    val rotation = remember { Animatable(0f) }
    var isSpinning by remember { mutableStateOf(false) }
    var done by remember { mutableStateOf(false) }
    var lastOutcome by remember { mutableStateOf<BaitOutcome?>(null) }
    val view = LocalView.current

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(PureBlack)
            .padding(28.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = if (done) "🎲 Result!" else "🎲 Bait Me Wheel",
                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                color = TextPrimary
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = if (done) {
                    lastOutcome?.subtext ?: ""
                } else {
                    "Test your discipline luck. Risk session minutes for higher rewards!"
                },
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary
            )

            Spacer(modifier = Modifier.height(36.dp))

            // Big animated wheel icon
            Box(
                modifier = Modifier
                    .size(130.dp)
                    .clip(CircleShape)
                    .background(
                        if (done) FocusGreen.copy(alpha = 0.15f) else FocusGold.copy(alpha = 0.15f)
                    )
                    .border(
                        3.dp,
                        if (done) FocusGreen else FocusGold,
                        CircleShape
                    )
                    .rotate(rotation.value),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (done) Icons.Default.CheckCircle else Icons.Default.Star,
                    contentDescription = null,
                    tint = if (done) FocusGreen else FocusGold,
                    modifier = Modifier.size(60.dp)
                )
            }

            Spacer(modifier = Modifier.height(36.dp))

            // Outcome card if completed
            if (done && lastOutcome != null) {
                FocusGramCard(
                    modifier = Modifier.fillMaxWidth(),
                    backgroundColor = DarkCardElevated
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = lastOutcome!!.label,
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                            color = if (lastOutcome == BaitOutcome.ADD_TEN_MINUTES) FocusGreen else AlertRed
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = lastOutcome!!.subtext,
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary
                        )
                    }
                }
                Spacer(modifier = Modifier.height(24.dp))
            }

            // Main CTA Button
            Button(
                onClick = {
                    if (done) {
                        onClose()
                    } else if (!isSpinning) {
                        view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                        isSpinning = true
                        scope.launch {
                            rotation.animateTo(
                                targetValue = rotation.value + 1800f,
                                animationSpec = tween(durationMillis = 1800)
                            )
                            val outcome = baitEngine.activate(
                                onResetSession = onResetSession,
                                onEndSession = onEndSession,
                                onOpenUrl = onOpenUrl
                            )
                            isSpinning = false
                            done = true
                            lastOutcome = outcome
                            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (done) FocusGreen else FocusGold
                ),
                shape = RoundedCornerShape(16.dp),
                enabled = !isSpinning
            ) {
                Text(
                    text = when {
                        isSpinning -> "Rolling..."
                        done -> "Done — Close"
                        else -> "🎲 Spin the Wheel!"
                    },
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = PureBlack
                )
            }

            if (!done) {
                Spacer(modifier = Modifier.height(14.dp))
                TextButton(onClick = onClose) {
                    Text("Not now", color = TextMuted)
                }
            }
        }
    }
}
