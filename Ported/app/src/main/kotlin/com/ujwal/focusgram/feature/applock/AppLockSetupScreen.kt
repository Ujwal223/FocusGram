package com.ujwal.focusgram.feature.applock

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.ui.unit.sp
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
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
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ujwal.focusgram.core.datastore.SettingsRepository
import com.ujwal.focusgram.core.security.EncryptedStorage
import com.ujwal.focusgram.core.ui.components.FocusGramTopBar
import com.ujwal.focusgram.core.ui.theme.AlertRed
import com.ujwal.focusgram.core.ui.theme.DarkCard
import com.ujwal.focusgram.core.ui.theme.DarkCardElevated
import com.ujwal.focusgram.core.ui.theme.InstagramBlue
import com.ujwal.focusgram.core.ui.theme.PureBlack
import com.ujwal.focusgram.core.ui.theme.TextMuted
import com.ujwal.focusgram.core.ui.theme.TextPrimary
import com.ujwal.focusgram.core.ui.theme.TextSecondary
import kotlinx.coroutines.launch

/**
 * AppLockSetupScreen
 * 4-digit PIN creation and confirmation with AES-256 GCM encrypted storage.
 */
@Composable
fun AppLockSetupScreen(
    encryptedStorage: EncryptedStorage,
    settingsRepository: SettingsRepository,
    onBack: () -> Unit,
    onSuccess: () -> Unit,
    title: String = "Set App Lock PIN",
    subtitle: String = "Used to unlock FocusGram and secure your mindful limits.",
    isChatPinSetup: Boolean = false,
    modifier: Modifier = Modifier
) {
    var step by remember { mutableStateOf(1) } // 1: Enter, 2: Confirm
    var firstPin by remember { mutableStateOf("") }
    var confirmPin by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val scope = rememberCoroutineScope()
    val view = LocalView.current

    val currentInput = if (step == 1) firstPin else confirmPin

    fun onDigitPress(digit: String) {
        errorMessage = null
        if (currentInput.length < 4) {
            val next = currentInput + digit
            if (step == 1) {
                firstPin = next
                if (next.length == 4) {
                    step = 2
                    view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                }
            } else {
                confirmPin = next
                if (next.length == 4) {
                    if (firstPin == confirmPin) {
                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        scope.launch {
                            if (isChatPinSetup) {
                                encryptedStorage.saveChatPin(confirmPin)
                                settingsRepository.setUseSeparateChatPin(true)
                            } else {
                                encryptedStorage.savePin(confirmPin)
                                settingsRepository.setAppLock(enabled = true, biometric = false)
                            }
                            onSuccess()
                        }
                    } else {
                        errorMessage = "PINs do not match. Try again."
                        step = 1
                        firstPin = ""
                        confirmPin = ""
                        view.performHapticFeedback(HapticFeedbackConstants.REJECT)
                    }
                }
            }
        }
    }

    fun onBackspace() {
        errorMessage = null
        if (step == 1 && firstPin.isNotEmpty()) {
            firstPin = firstPin.dropLast(1)
        } else if (step == 2 && confirmPin.isNotEmpty()) {
            confirmPin = confirmPin.dropLast(1)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(PureBlack)
            .padding(WindowInsets.statusBars.asPaddingValues())
    ) {
        FocusGramTopBar(
            title = title,
            onBack = onBack
        )

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(24.dp)
        ) {
            Spacer(modifier = Modifier.height(24.dp))

            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(InstagramBlue.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = null,
                    tint = InstagramBlue,
                    modifier = Modifier.size(30.dp)
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = if (step == 1) "Enter a 4-digit PIN" else "Confirm your PIN",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                color = TextPrimary
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary
            )

            Spacer(modifier = Modifier.height(30.dp))

            // PIN Dots Indicator
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                repeat(4) { idx ->
                    val isFilled = idx < currentInput.length
                    Box(
                        modifier = Modifier
                            .size(16.dp)
                            .clip(CircleShape)
                            .background(if (isFilled) InstagramBlue else DarkCardElevated)
                    )
                }
            }

            if (errorMessage != null) {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = errorMessage!!,
                    style = MaterialTheme.typography.bodySmall,
                    color = AlertRed
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            // Numeric Keypad
            NumericKeypad(
                onDigit = { onDigitPress(it) },
                onDelete = { onBackspace() }
            )

            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}

@Composable
private fun NumericKeypad(
    onDigit: (String) -> Unit,
    onDelete: () -> Unit
) {
    val rows = listOf(
        listOf("1", "2", "3"),
        listOf("4", "5", "6"),
        listOf("7", "8", "9"),
        listOf("", "0", "DEL")
    )

    Column(
        verticalArrangement = Arrangement.spacedBy(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        rows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                row.forEach { key ->
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .clip(CircleShape)
                            .background(if (key.isNotBlank()) DarkCard else PureBlack)
                            .clickable(enabled = key.isNotBlank()) {
                                if (key == "DEL") onDelete() else onDigit(key)
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        if (key == "DEL") {
                            Text(
                                text = "⌫",
                                fontSize = 22.sp,
                                color = TextSecondary,
                                fontWeight = FontWeight.Bold
                            )
                        } else if (key.isNotBlank()) {
                            Text(
                                text = key,
                                fontSize = 24.sp,
                                color = TextPrimary,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }
        }
    }
}
