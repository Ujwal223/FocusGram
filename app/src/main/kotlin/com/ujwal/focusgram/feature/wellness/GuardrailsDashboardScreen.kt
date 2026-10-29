package com.ujwal.focusgram.feature.wellness

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ujwal.focusgram.core.datastore.SettingsRepository
import com.ujwal.focusgram.core.model.FocusSettings
import com.ujwal.focusgram.core.ui.components.FocusGramCard
import com.ujwal.focusgram.core.ui.components.FocusGramSectionHeader
import com.ujwal.focusgram.core.ui.components.FocusGramToggleRow
import com.ujwal.focusgram.core.ui.components.FocusGramTopBar
import com.ujwal.focusgram.core.ui.theme.DarkCard
import com.ujwal.focusgram.core.ui.theme.DarkCardElevated
import com.ujwal.focusgram.core.ui.theme.InstagramBlue
import com.ujwal.focusgram.core.ui.theme.PureBlack
import com.ujwal.focusgram.core.ui.theme.TextPrimary
import com.ujwal.focusgram.core.ui.theme.TextSecondary
import kotlinx.coroutines.launch

import com.ujwal.focusgram.core.database.ScreenTimeDao
import com.ujwal.focusgram.core.utils.TimeFormatters
import kotlinx.coroutines.flow.flowOf
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.compose.runtime.remember

/**
 * Settings dashboard for configuring daily usage caps, cooldown intervals, and strict Reels blocking.
 */
@Composable
fun GuardrailsDashboardScreen(
    repository: SettingsRepository,
    onBack: () -> Unit,
    screenTimeDao: ScreenTimeDao? = null,
    modifier: Modifier = Modifier
) {
    val settings by repository.settingsFlow.collectAsState(initial = FocusSettings())
    val scope = rememberCoroutineScope()

    val dateFormat = remember { SimpleDateFormat("yyyy-MM-dd", Locale.US) }
    val todayStr = remember { dateFormat.format(Date()) }
    val todayRecord by (screenTimeDao?.getForDate(todayStr) ?: flowOf(null)).collectAsState(initial = null)
    val todaySeconds = todayRecord?.seconds ?: 0
    val baseDailyLimitSec = settings.dailyLimitMinutes * 60
    val isDailyLimitHit = settings.dailyLimitMinutes > 0 && todaySeconds >= baseDailyLimitSec
    val isNearDailyLimit = settings.dailyLimitMinutes > 15 && (baseDailyLimitSec - todaySeconds) in 0..300
    val isDailyLimitLocked = settings.dailyLimitMinutes > 0 && (isDailyLimitHit || isNearDailyLimit)

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(PureBlack)
            .padding(WindowInsets.statusBars.asPaddingValues())
    ) {
        FocusGramTopBar(
            title = "Guardrails & Time Limits",
            onBack = onBack
        )

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                FocusGramSectionHeader(
                    title = "Daily Usage Cap",
                    subtitle = "Instagram shuts down for the day when limit is reached"
                )
            }

            item {
                FocusGramCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Daily Limit",
                                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                                color = TextPrimary
                            )
                            Text(
                                text = if (isDailyLimitLocked) {
                                    "${TimeFormatters.formatMinutes(settings.dailyLimitMinutes)} (Locked)"
                                } else {
                                    TimeFormatters.formatMinutes(settings.dailyLimitMinutes)
                                },
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = if (isDailyLimitLocked) TextSecondary else InstagramBlue
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Slider(
                            value = settings.dailyLimitMinutes.toFloat(),
                            onValueChange = { 
                                if (!isDailyLimitLocked) {
                                    scope.launch { repository.setDailyLimitMinutes(it.toInt()) }
                                }
                            },
                            enabled = !isDailyLimitLocked,
                            valueRange = 15f..180f,
                            steps = 10,
                            colors = SliderDefaults.colors(
                                thumbColor = if (isDailyLimitLocked) TextSecondary else InstagramBlue,
                                activeTrackColor = if (isDailyLimitLocked) TextSecondary else InstagramBlue,
                                inactiveTrackColor = DarkCardElevated
                            )
                        )
                    }
                }
            }

            item {
                FocusGramSectionHeader(
                    title = "Session Cooldowns",
                    subtitle = "Compulsory break intervals between app sessions"
                )
            }

            item {
                FocusGramCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Cooldown Interval",
                                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                                color = TextPrimary
                            )
                            Text(
                                text = TimeFormatters.formatMinutes(settings.cooldownMinutes),
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = InstagramBlue
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Slider(
                            value = settings.cooldownMinutes.toFloat(),
                            onValueChange = { scope.launch { repository.setCooldownMinutes(it.toInt()) } },
                            valueRange = 5f..60f,
                            steps = 10,
                            colors = SliderDefaults.colors(
                                thumbColor = InstagramBlue,
                                activeTrackColor = InstagramBlue,
                                inactiveTrackColor = DarkCardElevated
                            )
                        )
                    }
                }
            }

            item {
                FocusGramSectionHeader(
                    title = "Reels Strict Enforcement",
                    subtitle = "Hard block or require challenge for short-form video consumption"
                )
            }

            item {
                FocusGramToggleRow(
                    title = "Completely Block Reels",
                    subtitle = "Disables all Reels tabs and URL access without session overrides",
                    checked = settings.noReels,
                    onCheckedChange = { scope.launch { repository.setNoReels(it) } }
                )
            }

            item {
                Spacer(modifier = Modifier.height(30.dp))
            }
        }
    }
}
