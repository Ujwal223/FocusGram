package com.ujwal.focusgram.feature.session

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ujwal.focusgram.core.ui.theme.DarkBorder
import com.ujwal.focusgram.core.ui.theme.DarkCard
import com.ujwal.focusgram.core.ui.theme.DarkCardElevated
import com.ujwal.focusgram.core.ui.theme.InstagramBlue
import com.ujwal.focusgram.core.ui.theme.PureBlack
import com.ujwal.focusgram.core.ui.theme.TextMuted
import com.ujwal.focusgram.core.ui.theme.TextPrimary
import com.ujwal.focusgram.core.ui.theme.TextSecondary

val REEL_SESSION_PRESETS = listOf(1, 3, 5, 10, 15, 20)

/**
 * SessionModal
 * Bottom modal container to initiate a timed Reels session.
 */
@Composable
fun SessionModal(
    dailyRemainingMinutes: Int,
    isCooldownActive: Boolean,
    cooldownRemainingMinutes: Int,
    onStartSession: (Int) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    var customMinutes by remember { mutableFloatStateOf(5f) }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
            .background(DarkCard)
            .padding(24.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Start Reel Session",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = TextPrimary
                )
                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = TextSecondary
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "Daily Remaining: ${dailyRemainingMinutes}m",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary
            )

            if (isCooldownActive) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Cooldown active: ${cooldownRemainingMinutes}m left",
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                    color = InstagramBlue
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = "Presets",
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                color = TextPrimary
            )

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                REEL_SESSION_PRESETS.forEach { m ->
                    val isSelected = customMinutes.toInt() == m
                    val shape = RoundedCornerShape(10.dp)
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(shape)
                            .background(if (isSelected) InstagramBlue else DarkCardElevated)
                            .border(1.dp, if (isSelected) InstagramBlue else DarkBorder, shape)
                            .clickable(enabled = !isCooldownActive) { customMinutes = m.toFloat() }
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "${m}m",
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.SemiBold
                            ),
                            color = if (isSelected) PureBlack else TextPrimary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Custom Duration",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = TextPrimary
                )
                Text(
                    text = "${customMinutes.toInt()} minutes",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color = InstagramBlue
                )
            }

            Slider(
                value = customMinutes,
                onValueChange = { customMinutes = it },
                valueRange = 1f..30f,
                steps = 28,
                enabled = !isCooldownActive,
                colors = SliderDefaults.colors(
                    thumbColor = InstagramBlue,
                    activeTrackColor = InstagramBlue,
                    inactiveTrackColor = DarkCardElevated
                )
            )

            Spacer(modifier = Modifier.height(20.dp))

            Button(
                onClick = { onStartSession(customMinutes.toInt()) },
                enabled = !isCooldownActive && dailyRemainingMinutes > 0,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                colors = ButtonDefaults.buttonColors(containerColor = InstagramBlue),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    text = "Start Session (${customMinutes.toInt()}m)",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = PureBlack
                )
            }
        }
    }
}
