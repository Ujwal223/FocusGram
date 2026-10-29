package com.ujwal.focusgram.feature.diagnostics

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ujwal.focusgram.core.datastore.SettingsRepository
import com.ujwal.focusgram.core.model.FocusSettings
import com.ujwal.focusgram.core.ui.components.FocusGramCard
import com.ujwal.focusgram.core.ui.components.FocusGramSectionHeader
import com.ujwal.focusgram.core.ui.components.FocusGramTopBar
import com.ujwal.focusgram.core.ui.theme.AlertRed
import com.ujwal.focusgram.core.ui.theme.DarkCard
import com.ujwal.focusgram.core.ui.theme.DarkCardElevated
import com.ujwal.focusgram.core.ui.theme.FocusGold
import com.ujwal.focusgram.core.ui.theme.FocusGreen
import com.ujwal.focusgram.core.ui.theme.InstagramBlue
import com.ujwal.focusgram.core.ui.theme.PureBlack
import com.ujwal.focusgram.core.ui.theme.TextMuted
import com.ujwal.focusgram.core.ui.theme.TextPrimary
import com.ujwal.focusgram.core.ui.theme.TextSecondary

/**
 * Developer diagnostics screen for inspecting state, testing simulation, and
 * dynamically adjusting active session time and guardrail bypasses.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DebugMenuScreen(
    repository: SettingsRepository,
    onBack: () -> Unit,
    onSimulateUpdate: () -> Unit = {},
    onSimulateNotice: () -> Unit = {},
    onResetLimits: () -> Unit = {},
    isLimitsBypassed: Boolean = false,
    onToggleLimitsBypass: () -> Unit = onResetLimits,
    currentSessionDurationMinutes: Int = 15,
    currentSessionRemainingSeconds: Int = 15 * 60,
    currentSessionElapsedSeconds: Int = 0,
    onAdjustSessionTime: (newDurationMinutes: Int, newRemainingSeconds: Int, newElapsedSeconds: Int) -> Unit = { _, _, _ -> },
    modifier: Modifier = Modifier
) {
    val settings by repository.settingsFlow.collectAsState(initial = FocusSettings())
    var customMinutesInput by remember { mutableStateOf(currentSessionDurationMinutes.toString()) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(PureBlack)
            .padding(WindowInsets.statusBars.asPaddingValues())
    ) {
        FocusGramTopBar(
            title = "Developer Diagnostics",
            onBack = onBack
        )

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                FocusGramCard(
                    modifier = Modifier.fillMaxWidth(),
                    backgroundColor = DarkCardElevated
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Current State",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = FocusGold
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "AppLock: ${settings.appLockEnabled} · Daily Limit: ${settings.dailyLimitMinutes}m\n" +
                                    "Cooldown: ${settings.cooldownMinutes}m · FirstRun: ${settings.isFirstRun}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextPrimary
                        )
                    }
                }
            }

            item {
                FocusGramSectionHeader(title = "Active Session Controller")
            }

            item {
                FocusGramCard(
                    modifier = Modifier.fillMaxWidth(),
                    backgroundColor = DarkCardElevated
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "Adjust Session Time",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = FocusGold
                        )

                        Text(
                            text = "Duration: ${currentSessionDurationMinutes}m · Remaining: ${currentSessionRemainingSeconds / 60}m ${currentSessionRemainingSeconds % 60}s · Elapsed: ${currentSessionElapsedSeconds / 60}m ${currentSessionElapsedSeconds % 60}s",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextPrimary
                        )

                        // Quick +/- adjustments
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    val newDuration = currentSessionDurationMinutes + 5
                                    val newRemaining = currentSessionRemainingSeconds + 300
                                    onAdjustSessionTime(newDuration, newRemaining, currentSessionElapsedSeconds)
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("+5m")
                            }
                            OutlinedButton(
                                onClick = {
                                    val newDuration = currentSessionDurationMinutes + 15
                                    val newRemaining = currentSessionRemainingSeconds + 900
                                    onAdjustSessionTime(newDuration, newRemaining, currentSessionElapsedSeconds)
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("+15m")
                            }
                            OutlinedButton(
                                onClick = {
                                    val newRemaining = (currentSessionRemainingSeconds - 300).coerceAtLeast(0)
                                    val newDuration = maxOf(1, currentSessionDurationMinutes - 5)
                                    onAdjustSessionTime(newDuration, newRemaining, currentSessionElapsedSeconds)
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("-5m")
                            }
                            OutlinedButton(
                                onClick = {
                                    // Set remaining to 60s for immediate testing of sunset colors & session expiry
                                    val newRemaining = 60
                                    val newDuration = maxOf(1, (currentSessionElapsedSeconds + 60 + 59) / 60)
                                    onAdjustSessionTime(newDuration, newRemaining, currentSessionElapsedSeconds)
                                },
                                modifier = Modifier.weight(1.3f)
                            ) {
                                Text("Set 1m")
                            }
                        }

                        // Preset chips
                        Text(
                            text = "Presets:",
                            style = MaterialTheme.typography.labelMedium,
                            color = TextMuted
                        )
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            listOf(1, 5, 9, 15, 26, 27, 32, 45, 60).forEach { mins ->
                                FilterChip(
                                    selected = currentSessionDurationMinutes == mins,
                                    onClick = {
                                        customMinutesInput = mins.toString()
                                        val newRemaining = if (currentSessionElapsedSeconds > 0) {
                                            (mins * 60 - currentSessionElapsedSeconds).coerceAtLeast(0)
                                        } else {
                                            mins * 60
                                        }
                                        val newElapsed = if (currentSessionElapsedSeconds > 0) currentSessionElapsedSeconds else 0
                                        onAdjustSessionTime(mins, newRemaining, newElapsed)
                                    },
                                    label = { Text("${mins}m") },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = FocusGold,
                                        selectedLabelColor = PureBlack
                                    )
                                )
                            }
                        }

                        // Custom minutes input field
                        OutlinedTextField(
                            value = customMinutesInput,
                            onValueChange = { customMinutesInput = it.filter { ch -> ch.isDigit() } },
                            label = { Text("Set Custom Minutes") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = FocusGold,
                                focusedLabelColor = FocusGold,
                                focusedTextColor = TextPrimary,
                                unfocusedTextColor = TextPrimary
                            )
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = {
                                    val mins = customMinutesInput.toIntOrNull() ?: currentSessionDurationMinutes
                                    if (mins > 0) {
                                        val newRemaining = if (currentSessionElapsedSeconds > 0) {
                                            (mins * 60 - currentSessionElapsedSeconds).coerceAtLeast(0)
                                        } else {
                                            mins * 60
                                        }
                                        val newElapsed = if (currentSessionElapsedSeconds > 0) currentSessionElapsedSeconds else 0
                                        onAdjustSessionTime(mins, newRemaining, newElapsed)
                                    }
                                },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(containerColor = FocusGold, contentColor = PureBlack)
                            ) {
                                Text("Set Total Duration", fontWeight = FontWeight.Bold)
                            }

                            Button(
                                onClick = {
                                    val mins = customMinutesInput.toIntOrNull() ?: 15
                                    if (mins > 0) {
                                        val newRemaining = mins * 60
                                        val newDuration = maxOf(mins, (currentSessionElapsedSeconds + newRemaining + 59) / 60)
                                        onAdjustSessionTime(newDuration, newRemaining, currentSessionElapsedSeconds)
                                    }
                                },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(containerColor = InstagramBlue)
                            ) {
                                Text("Set Remaining", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            item {
                FocusGramSectionHeader(title = "Testing & Simulation")
            }

            item {
                FocusGramCard(
                    modifier = Modifier.fillMaxWidth(),
                    backgroundColor = DarkCardElevated
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = onSimulateUpdate,
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = InstagramBlue)
                        ) {
                            Text("Simulate Update Available (v3.1.0)")
                        }
                        Button(
                            onClick = onSimulateNotice,
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = FocusGreen)
                        ) {
                            Text("Simulate Remote Notice (popup.json)")
                        }
                        Button(
                            onClick = onToggleLimitsBypass,
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isLimitsBypassed) AlertRed else FocusGold,
                                contentColor = if (isLimitsBypassed) Color.White else PureBlack
                            )
                        ) {
                            Text(
                                text = if (isLimitsBypassed) "Don't Skip Cooldowns & Daily Limits" else "Skip Cooldowns & Daily Limits",
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(30.dp))
            }
        }
    }
}
