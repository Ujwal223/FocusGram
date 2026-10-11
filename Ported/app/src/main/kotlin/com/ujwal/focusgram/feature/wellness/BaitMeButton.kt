package com.ujwal.focusgram.feature.wellness

import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import com.ujwal.focusgram.core.ui.theme.AlertRed
import com.ujwal.focusgram.core.ui.theme.DarkCard
import com.ujwal.focusgram.core.ui.theme.FocusGold
import com.ujwal.focusgram.core.ui.theme.FocusGreen
import com.ujwal.focusgram.core.ui.theme.PureBlack
import com.ujwal.focusgram.core.ui.theme.TextMuted
import com.ujwal.focusgram.core.ui.theme.TextPrimary
import com.ujwal.focusgram.core.ui.theme.TextSecondary
import kotlinx.coroutines.launch

/**
 * BaitMeButton
 * Floating gamified overlay button with rotation animation, haptics, and cooldown badge.
 */
@Composable
fun BaitMeButton(
    baitEngine: BaitEngine,
    userLevel: Int,
    onResetSession: () -> Unit,
    onEndSession: () -> Unit,
    onOpenUrl: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    if (userLevel < 3) return // Unlocks at Level 3

    val scope = rememberCoroutineScope()
    val rotation = remember { Animatable(0f) }
    var isSpinning by remember { mutableStateOf(false) }
    var activeOutcome by remember { mutableStateOf<BaitOutcome?>(null) }
    val view = LocalView.current
    val isOnCooldown = baitEngine.isOnCooldown

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
    ) {
        Box(contentAlignment = Alignment.Center) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(CircleShape)
                    .background(
                        if (isOnCooldown) DarkCard else FocusGold.copy(alpha = 0.18f)
                    )
                    .border(
                        2.dp,
                        if (isOnCooldown) TextMuted else FocusGold,
                        CircleShape
                    )
                    .rotate(rotation.value)
                    .clickable(enabled = !isOnCooldown && !isSpinning) {
                        view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                        isSpinning = true
                        scope.launch {
                            rotation.animateTo(
                                targetValue = rotation.value + 1080f,
                                animationSpec = tween(durationMillis = 1200)
                            )
                            val outcome = baitEngine.activate(
                                onResetSession = onResetSession,
                                onEndSession = onEndSession,
                                onOpenUrl = onOpenUrl
                            )
                            isSpinning = false
                            activeOutcome = outcome
                            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Star,
                    contentDescription = "Bait Me",
                    tint = if (isOnCooldown) TextMuted else FocusGold,
                    modifier = Modifier.size(22.dp)
                )
            }

            // Cooldown indicator badge
            if (isOnCooldown) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .clip(RoundedCornerShape(6.dp))
                        .background(PureBlack)
                        .padding(horizontal = 4.dp, vertical = 1.dp)
                ) {
                    Text(
                        text = "${baitEngine.remainingCooldownMinutes}m",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        color = FocusGold
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(2.dp))

        Text(
            text = "Bait Me",
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
            color = if (isOnCooldown) TextMuted else TextSecondary
        )
    }

    // Outcome Result Dialog
    activeOutcome?.let { outcome ->
        AlertDialog(
            onDismissRequest = { activeOutcome = null },
            title = {
                Text(
                    text = outcome.label,
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = if (outcome == BaitOutcome.ADD_TEN_MINUTES) FocusGreen else AlertRed
                )
            },
            text = {
                Text(
                    text = outcome.subtext,
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )
            },
            containerColor = DarkCard,
            confirmButton = {
                Button(
                    onClick = { activeOutcome = null },
                    colors = ButtonDefaults.buttonColors(containerColor = FocusGold)
                ) {
                    Text("OK", color = PureBlack, fontWeight = FontWeight.Bold)
                }
            }
        )
    }
}
