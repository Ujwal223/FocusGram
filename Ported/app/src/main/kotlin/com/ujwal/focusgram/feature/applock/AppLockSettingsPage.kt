package com.ujwal.focusgram.feature.applock

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ujwal.focusgram.core.datastore.SettingsRepository
import com.ujwal.focusgram.core.model.FocusSettings
import com.ujwal.focusgram.core.security.EncryptedStorage
import androidx.activity.compose.BackHandler
import com.ujwal.focusgram.core.ui.components.FocusGramCard
import com.ujwal.focusgram.core.ui.components.FocusGramSectionHeader
import com.ujwal.focusgram.core.ui.components.FocusGramToggleRow
import com.ujwal.focusgram.core.ui.components.FocusGramTopBar
import com.ujwal.focusgram.core.ui.theme.AlertRed
import com.ujwal.focusgram.core.ui.theme.InstagramBlue
import com.ujwal.focusgram.core.ui.theme.PureBlack
import kotlinx.coroutines.launch

/**
 * AppLockSettingsPage
 * Configure PIN protection, biometric sensor authentication, and auto-lock timeout.
 */
@Composable
fun AppLockSettingsPage(
    repository: SettingsRepository,
    encryptedStorage: EncryptedStorage,
    onBack: () -> Unit,
    onChangePin: () -> Unit,
    modifier: Modifier = Modifier
) {
    val settings by repository.settingsFlow.collectAsState(initial = FocusSettings())
    val scope = rememberCoroutineScope()

    BackHandler {
        onBack()
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(PureBlack)
            .padding(WindowInsets.statusBars.asPaddingValues())
    ) {
        FocusGramTopBar(
            title = "App Lock Settings",
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
                    title = "Security Guard",
                    subtitle = "Restrict app access to prevent unconscious Instagram opens"
                )
            }

            item {
                FocusGramToggleRow(
                    title = "Enable App Lock",
                    subtitle = if (settings.appLockEnabled) "App requires 4-digit PIN to open" else "Disabled",
                    checked = settings.appLockEnabled,
                    onCheckedChange = { isChecked ->
                        if (isChecked && !encryptedStorage.hasPin()) {
                            onChangePin()
                        } else {
                            scope.launch { repository.setAppLock(isChecked, settings.biometricEnabled) }
                        }
                    },
                    icon = Icons.Default.Lock
                )
            }

            if (settings.appLockEnabled) {
                item {
                    FocusGramToggleRow(
                        title = "Biometric Unlock",
                        subtitle = "Use fingerprint or face recognition to unlock",
                        checked = settings.biometricEnabled,
                        onCheckedChange = { scope.launch { repository.setAppLock(true, it) } }
                    )
                }

                item {
                    FocusGramToggleRow(
                        title = "Auto-Lock on Background",
                        subtitle = "Immediately lock whenever leaving FocusGram",
                        checked = settings.autoLockOnBackground,
                        onCheckedChange = { isChecked ->
                            scope.launch { repository.setAutoLockBackground(isChecked) }
                        }
                    )
                }

                item {
                    Spacer(modifier = Modifier.height(10.dp))
                    Button(
                        onClick = onChangePin,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = InstagramBlue)
                    ) {
                        Text("Change PIN", color = PureBlack)
                    }
                }

                item {
                    OutlinedButton(
                        onClick = {
                            scope.launch {
                                encryptedStorage.clearPin()
                                repository.setAppLock(enabled = false, biometric = false)
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Disable & Remove PIN", color = AlertRed)
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(30.dp))
            }
        }
    }
}
