package com.ujwal.focusgram.feature.diagnostics

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import com.ujwal.focusgram.core.ui.components.FocusGramTopBar
import com.ujwal.focusgram.core.ui.theme.AlertRed
import com.ujwal.focusgram.core.ui.theme.DarkCard
import com.ujwal.focusgram.core.ui.theme.DarkCardElevated
import com.ujwal.focusgram.core.ui.theme.FocusGold
import com.ujwal.focusgram.core.ui.theme.FocusGreen
import com.ujwal.focusgram.core.ui.theme.InstagramBlue
import com.ujwal.focusgram.core.ui.theme.PureBlack
import com.ujwal.focusgram.core.ui.theme.TextPrimary
import com.ujwal.focusgram.core.ui.theme.TextSecondary
import kotlinx.coroutines.launch

/**
 * DebugMenuScreen
 * Hidden developer diagnostics panel:
 * - Level & XP override
 * - Session credit balances mock
 * - Hard state reset
 */
@Composable
fun DebugMenuScreen(
    repository: SettingsRepository,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val settings by repository.settingsFlow.collectAsState(initial = FocusSettings())
    val scope = rememberCoroutineScope()

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
                            text = "Level: ${settings.userLevel} · XP: ${settings.userXP}\n" +
                                    "Reels Min: ${settings.reelsMinutes} · Insta Min: ${settings.instaMinutes}\n" +
                                    "Ads Today: ${settings.adsWatchedToday} · AppLock: ${settings.appLockEnabled}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextPrimary
                        )
                    }
                }
            }

            item {
                FocusGramSectionHeader(title = "Level & XP Grants")
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { scope.launch { repository.addXP(20) } },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = InstagramBlue)
                    ) {
                        Text("+20 XP")
                    }
                    Button(
                        onClick = { scope.launch { repository.addXP(100) } },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = FocusGreen)
                    ) {
                        Text("+100 XP (Lvl Up)")
                    }
                }
            }

            item {
                FocusGramSectionHeader(title = "Credit Balances")
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { scope.launch { repository.addReelsMinutes(15) } },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = FocusGold)
                    ) {
                        Text("+15m Reels")
                    }
                    Button(
                        onClick = { scope.launch { repository.addInstaMinutes(30) } },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = InstagramBlue)
                    ) {
                        Text("+30m Insta")
                    }
                }
            }

            item {
                FocusGramSectionHeader(title = "Danger Zone")
            }

            item {
                OutlinedButton(
                    onClick = {
                        scope.launch {
                            repository.deductReelsMinute()
                            repository.deductInstaMinute()
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Drain 1 Minute Each", color = AlertRed)
                }
            }

            item {
                Spacer(modifier = Modifier.height(30.dp))
            }
        }
    }
}
