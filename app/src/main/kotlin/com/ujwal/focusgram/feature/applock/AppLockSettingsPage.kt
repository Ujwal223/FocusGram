package com.ujwal.focusgram.feature.applock

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.ujwal.focusgram.core.datastore.SettingsRepository
import com.ujwal.focusgram.core.model.FocusSettings
import com.ujwal.focusgram.core.security.EncryptedStorage
import com.ujwal.focusgram.core.ui.components.FocusGramTopBar
import com.ujwal.focusgram.core.ui.components.IOSGroupedCard
import com.ujwal.focusgram.core.ui.components.IOSSectionTitle
import com.ujwal.focusgram.core.ui.components.IOSSettingsRow
import com.ujwal.focusgram.core.ui.theme.PureBlack
import kotlinx.coroutines.launch

private val IOSBlue = Color(0xFF007AFF)
private val IOSGreen = Color(0xFF34C759)
private val IOSOrange = Color(0xFFFF9500)

/**
 * Settings page for toggling PIN and biometric authentication requirements.
 */
@Composable
fun AppLockSettingsPage(
    repository: SettingsRepository,
    encryptedStorage: EncryptedStorage,
    onBack: () -> Unit,
    onChangePin: () -> Unit,
    onChangeChatPin: () -> Unit = {},
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
            title = "App Lock",
            onBack = onBack
        )

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
        ) {
            item {
                IOSSectionTitle(title = "APP PROTECTION")
                IOSGroupedCard {
                    IOSSettingsRow(
                        title = "App Lock Protection",
                        subtitle = "Require 4-digit PIN or biometrics upon opening FocusGram",
                        icon = Icons.Default.Lock,
                        iconBgColor = IOSBlue,
                        checked = settings.appLockEnabled,
                        onCheckedChange = { isChecked ->
                            scope.launch {
                                repository.setAppLock(isChecked, settings.biometricEnabled)
                            }
                            if (isChecked && !encryptedStorage.hasPin()) {
                                onChangePin()
                            }
                        },
                        showDivider = false
                    )
                }
            }

            if (settings.appLockEnabled) {
                item {
                    IOSSectionTitle(title = "AUTHENTICATION METHOD")
                    IOSGroupedCard {
                        IOSSettingsRow(
                            title = "Biometric Unlock",
                            subtitle = "Use fingerprint or face unlock",
                            icon = Icons.Default.Fingerprint,
                            iconBgColor = IOSGreen,
                            checked = settings.biometricEnabled,
                            onCheckedChange = { isChecked ->
                                scope.launch {
                                    repository.setAppLock(settings.appLockEnabled, isChecked)
                                }
                            }
                        )
                        IOSSettingsRow(
                            title = if (encryptedStorage.hasPin()) "Change 4-Digit PIN" else "Set 4-Digit PIN",
                            subtitle = "Update master passcode for app entry",
                            icon = Icons.Default.Lock,
                            iconBgColor = IOSOrange,
                            onClick = onChangePin,
                            showDivider = false
                        )
                    }
                }

                item {
                    IOSSectionTitle(title = "AUTO-LOCK BEHAVIOR")
                    IOSGroupedCard {
                        IOSSettingsRow(
                            title = "Auto-Lock in Background",
                            subtitle = "Lock immediately whenever FocusGram is minimized",
                            icon = Icons.Default.Schedule,
                            iconBgColor = Color(0xFF636366),
                            checked = settings.autoLockOnBackground,
                            onCheckedChange = { isChecked ->
                                scope.launch {
                                    repository.setAutoLockBackground(isChecked)
                                }
                            },
                            showDivider = false
                        )
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(40.dp))
            }
        }
    }
}
