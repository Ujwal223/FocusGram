package com.ujwal.focusgram.feature.wellness

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ujwal.focusgram.core.datastore.SettingsRepository
import com.ujwal.focusgram.core.model.FocusSettings
import com.ujwal.focusgram.core.ui.components.FocusGramCard
import com.ujwal.focusgram.core.ui.components.FocusGramSectionHeader
import com.ujwal.focusgram.core.ui.components.FocusGramTopBar
import com.ujwal.focusgram.core.ui.theme.AlertRed
import com.ujwal.focusgram.core.ui.theme.DarkBorder
import com.ujwal.focusgram.core.ui.theme.DarkCard
import com.ujwal.focusgram.core.ui.theme.DarkCardElevated
import com.ujwal.focusgram.core.ui.theme.FocusGold
import com.ujwal.focusgram.core.ui.theme.FocusGreen
import com.ujwal.focusgram.core.ui.theme.InstagramBlue
import com.ujwal.focusgram.core.ui.theme.PureBlack
import com.ujwal.focusgram.core.ui.theme.TextMuted
import com.ujwal.focusgram.core.ui.theme.TextPrimary
import com.ujwal.focusgram.core.ui.theme.TextSecondary
import com.ujwal.focusgram.core.ui.theme.tactilePress

data class LevelFeature(
    val name: String,
    val requiredLevel: Int,
    val description: String
)

val ALL_LEVEL_FEATURES = listOf(
    LevelFeature("Core Content Hider", 1, "Hide Stories, Reels tab, and Explore grid"),
    LevelFeature("Autoplay Blocker", 1, "Prevent endless video auto-looping"),
    LevelFeature("Grayscale Filter", 2, "Hardware-accelerated monochrome mode"),
    LevelFeature("Effort Friction Mode", 3, "Typing challenge and ad-supported focus credits"),
    LevelFeature("Cooldown Gates", 4, "Compulsory waiting periods between sessions"),
    LevelFeature("Offline Snapshots", 5, "Cached offline browsing with zero network calls")
)

/**
 * LevelPanelScreen
 * Visualizes user mindfulness streak, discipline level, and earned XP.
 */
@Composable
fun LevelPanelScreen(
    repository: SettingsRepository,
    onBack: () -> Unit,
    onWatchAdForXp: () -> Unit,
    modifier: Modifier = Modifier
) {
    val settings by repository.settingsFlow.collectAsState(initial = FocusSettings())
    val userLevel = settings.userLevel.coerceAtLeast(1)
    val userXp = settings.userXP
    val xpInLevel = userXp % 100
    val progress = xpInLevel / 100f

    val levelTitle = when (userLevel) {
        1 -> "Beginner"
        2 -> "Mindful Scroller"
        3 -> "Disciplined"
        4 -> "Focus Master"
        else -> "Digital Monk"
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(PureBlack)
            .padding(WindowInsets.statusBars.asPaddingValues())
    ) {
        FocusGramTopBar(
            title = "Your Journey",
            onBack = onBack
        )

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Level Hero Header
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(
                            Brush.linearGradient(
                                colors = listOf(
                                    DarkCardElevated,
                                    Color(0xFF1B2A4A)
                                )
                            )
                        )
                        .border(1.dp, InstagramBlue.copy(alpha = 0.3f), RoundedCornerShape(20.dp))
                        .padding(24.dp)
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        // Level Badge Circle
                        Box(
                            modifier = Modifier
                                .size(76.dp)
                                .clip(CircleShape)
                                .background(InstagramBlue.copy(alpha = 0.2f))
                                .border(3.dp, InstagramBlue, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "$userLevel",
                                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                                color = TextPrimary
                            )
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        Text(
                            text = levelTitle,
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                            color = TextPrimary
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        LinearProgressIndicator(
                            progress = { progress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp)),
                            color = InstagramBlue,
                            trackColor = DarkCardElevated,
                            strokeCap = StrokeCap.Round
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = "$xpInLevel / 100 XP to Level ${userLevel + 1}",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                    }
                }
            }

            // Watch Ad CTA for XP
            item {
                Button(
                    onClick = onWatchAdForXp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .tactilePress(onClick = onWatchAdForXp),
                    colors = ButtonDefaults.buttonColors(containerColor = FocusGold),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = null,
                        tint = PureBlack,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Watch Ad to Earn +10 XP & 2 Reels Min",
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                        color = PureBlack
                    )
                }
            }

            // Feature Unlocks Table
            item {
                FocusGramSectionHeader(
                    title = "Discipline Progression",
                    subtitle = "Features automatically unlock as your mindfulness level advances"
                )
            }

            items(ALL_LEVEL_FEATURES.size) { index ->
                val feat = ALL_LEVEL_FEATURES[index]
                val unlocked = userLevel >= feat.requiredLevel

                FocusGramCard(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (unlocked) Icons.Default.CheckCircle else Icons.Default.Lock,
                            contentDescription = null,
                            tint = if (unlocked) FocusGreen else TextMuted,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = feat.name,
                                style = MaterialTheme.typography.bodyLarge.copy(
                                    fontWeight = if (unlocked) FontWeight.SemiBold else FontWeight.Normal
                                ),
                                color = if (unlocked) TextPrimary else TextMuted
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = feat.description,
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary
                            )
                        }
                        Text(
                            text = if (unlocked) "Unlocked" else "Level ${feat.requiredLevel}",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                            color = if (unlocked) FocusGreen else TextMuted
                        )
                    }
                }
            }

            // How to Earn XP
            item {
                FocusGramSectionHeader(
                    title = "How to Earn XP",
                    subtitle = "Conscious habits reward your focus journey"
                )
            }

            item {
                FocusGramCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        XpRuleItem(rule = "Watch a rewarded ad", reward = "+10 XP")
                        XpRuleItem(rule = "Stay below daily Reels limit", reward = "+15 XP / day")
                        XpRuleItem(rule = "Complete mindful typing challenge", reward = "+5 XP")
                        XpRuleItem(rule = "Daily mindful check-in", reward = "+1 XP")
                    }
                }
            }

            // Degradation / Backslide Notice
            item {
                FocusGramCard(
                    modifier = Modifier.fillMaxWidth(),
                    borderColor = AlertRed.copy(alpha = 0.3f),
                    backgroundColor = DarkCard
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = null,
                            tint = AlertRed,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "Exceeding screen time limits for 3 consecutive days deducts XP. Consistency preserves unlocked tiers.",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(30.dp))
            }
        }
    }
}

@Composable
private fun XpRuleItem(rule: String, reward: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = rule,
            style = MaterialTheme.typography.bodyMedium,
            color = TextPrimary
        )
        Text(
            text = reward,
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
            color = FocusGreen
        )
    }
}
