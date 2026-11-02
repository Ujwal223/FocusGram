package com.ujwal.focusgram.feature.applock

import android.view.HapticFeedbackConstants
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ujwal.focusgram.core.datastore.SettingsRepository
import com.ujwal.focusgram.core.model.FocusSettings
import com.ujwal.focusgram.core.security.EncryptedStorage
import com.ujwal.focusgram.core.ui.components.FocusGramTopBar
import com.ujwal.focusgram.core.ui.theme.AlertRed
import com.ujwal.focusgram.core.ui.theme.DarkCard
import com.ujwal.focusgram.core.ui.theme.DarkCardElevated
import com.ujwal.focusgram.core.ui.theme.InstagramBlue
import com.ujwal.focusgram.core.ui.theme.PureBlack
import com.ujwal.focusgram.core.ui.theme.PureWhite
import com.ujwal.focusgram.core.ui.theme.TextPrimary
import com.ujwal.focusgram.core.ui.theme.TextSecondary
import com.ujwal.focusgram.core.utils.HapticHelper
import kotlinx.coroutines.launch

private val ChatLockAccent = Color(0xFF9D4EDD)

enum class PinSetupStep {
    VERIFY_CURRENT,
    ENTER_NEW,
    CONFIRM_NEW
}

/**
 * 4-digit PIN creation, confirmation, and change screen.
 *
 * If a PIN is already configured, enforces verification of the current PIN before
 * permitting the user to choose a new PIN.
 */
@Composable
fun AppLockSetupScreen(
    encryptedStorage: EncryptedStorage,
    settingsRepository: SettingsRepository,
    onBack: () -> Unit,
    onSuccess: () -> Unit,
    modifier: Modifier = Modifier,
    title: String? = null,
    subtitle: String? = null,
    isChatPinSetup: Boolean = false
) {
    val settings by settingsRepository.settingsFlow.collectAsState(initial = FocusSettings())
    val scope = rememberCoroutineScope()
    val view = LocalView.current

    val hasExistingPin = remember {
        if (isChatPinSetup) {
            encryptedStorage.hasChatPin() || encryptedStorage.hasPin()
        } else {
            encryptedStorage.hasPin()
        }
    }

    val initialStep = if (hasExistingPin) PinSetupStep.VERIFY_CURRENT else PinSetupStep.ENTER_NEW
    var step by remember { mutableStateOf(initialStep) }

    var currentPinInput by remember { mutableStateOf("") }
    var newPinInput by remember { mutableStateOf("") }
    var confirmPinInput by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val currentInput = when (step) {
        PinSetupStep.VERIFY_CURRENT -> currentPinInput
        PinSetupStep.ENTER_NEW -> newPinInput
        PinSetupStep.CONFIRM_NEW -> confirmPinInput
    }

    val accentColor = if (isChatPinSetup) ChatLockAccent else InstagramBlue

    val screenTitle = title ?: when {
        isChatPinSetup && encryptedStorage.hasChatPin() -> "Change Chat PIN"
        isChatPinSetup -> "Set Chat PIN"
        encryptedStorage.hasPin() -> "Change App PIN"
        else -> "Set App PIN"
    }

    val headlineText = when (step) {
        PinSetupStep.VERIFY_CURRENT -> {
            if (isChatPinSetup && !encryptedStorage.hasChatPin() && encryptedStorage.hasPin()) {
                "Enter App Lock PIN"
            } else if (isChatPinSetup) {
                "Enter Current Chat PIN"
            } else {
                "Enter Current PIN"
            }
        }
        PinSetupStep.ENTER_NEW -> {
            if (hasExistingPin) "Enter New 4-Digit PIN" else "Enter a 4-Digit PIN"
        }
        PinSetupStep.CONFIRM_NEW -> {
            if (hasExistingPin) "Confirm New PIN" else "Confirm Your PIN"
        }
    }

    val subtitleText = when (step) {
        PinSetupStep.VERIFY_CURRENT -> {
            if (isChatPinSetup && !encryptedStorage.hasChatPin() && encryptedStorage.hasPin()) {
                "Confirm your master App Lock PIN to configure a separate Chat PIN."
            } else if (isChatPinSetup) {
                "Confirm your current Chat PIN to choose a new one."
            } else {
                "Confirm your current PIN to choose a new one."
            }
        }
        PinSetupStep.ENTER_NEW -> {
            if (isChatPinSetup) {
                "Choose a 4-digit PIN for protected conversations."
            } else {
                "Choose a 4-digit passcode to secure FocusGram."
            }
        }
        PinSetupStep.CONFIRM_NEW -> {
            "Re-enter the 4-digit PIN to ensure it matches."
        }
    }

    fun handleBack() {
        if (step == PinSetupStep.CONFIRM_NEW) {
            step = PinSetupStep.ENTER_NEW
            confirmPinInput = ""
            errorMessage = null
        } else {
            onBack()
        }
    }

    BackHandler {
        handleBack()
    }

    fun onDigitPress(digit: String) {
        if (encryptedStorage.isLockedOut()) {
            val remaining = encryptedStorage.getRemainingLockoutSeconds()
            errorMessage = "Vault is locked out. Try again in $remaining seconds."
            HapticHelper.performReject(view)
            return
        }
        errorMessage = null
        if (currentInput.length < 4) {
            val next = currentInput + digit
            when (step) {
                PinSetupStep.VERIFY_CURRENT -> {
                    currentPinInput = next
                    if (next.length == 4) {
                        val isValid = if (isChatPinSetup && encryptedStorage.hasChatPin()) {
                            encryptedStorage.verifyChatPin(next)
                        } else {
                            encryptedStorage.verifyPin(next)
                        }
                        if (isValid) {
                            HapticHelper.performConfirm(view)
                            step = PinSetupStep.ENTER_NEW
                            currentPinInput = ""
                            errorMessage = null
                        } else {
                            HapticHelper.performReject(view)
                            if (encryptedStorage.isLockedOut()) {
                                val remaining = encryptedStorage.getRemainingLockoutSeconds()
                                errorMessage = "Too many attempts. Locked out for $remaining seconds."
                            } else {
                                errorMessage = "Incorrect PIN. Try again."
                            }
                            currentPinInput = ""
                        }
                    }
                }
                PinSetupStep.ENTER_NEW -> {
                    newPinInput = next
                    if (next.length == 4) {
                        step = PinSetupStep.CONFIRM_NEW
                        view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                        errorMessage = null
                    }
                }
                PinSetupStep.CONFIRM_NEW -> {
                    confirmPinInput = next
                    if (next.length == 4) {
                        if (newPinInput == confirmPinInput) {
                            HapticHelper.performConfirm(view)
                            scope.launch {
                                if (isChatPinSetup) {
                                    encryptedStorage.saveChatPin(confirmPinInput)
                                    settingsRepository.setUseSeparateChatPin(true)
                                } else {
                                    encryptedStorage.savePin(confirmPinInput)
                                    settingsRepository.setAppLock(enabled = true, biometric = settings.biometricEnabled)
                                }
                                onSuccess()
                            }
                        } else {
                            errorMessage = "New PINs do not match. Try again."
                            step = PinSetupStep.ENTER_NEW
                            newPinInput = ""
                            confirmPinInput = ""
                            HapticHelper.performReject(view)
                        }
                    }
                }
            }
        }
    }

    fun onBackspace() {
        errorMessage = null
        when (step) {
            PinSetupStep.VERIFY_CURRENT -> {
                if (currentPinInput.isNotEmpty()) {
                    currentPinInput = currentPinInput.dropLast(1)
                }
            }
            PinSetupStep.ENTER_NEW -> {
                if (newPinInput.isNotEmpty()) {
                    newPinInput = newPinInput.dropLast(1)
                }
            }
            PinSetupStep.CONFIRM_NEW -> {
                if (confirmPinInput.isNotEmpty()) {
                    confirmPinInput = confirmPinInput.dropLast(1)
                }
            }
        }
    }

    val isDark = isSystemInDarkTheme()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(if (isDark) PureBlack else PureWhite)
            .padding(WindowInsets.statusBars.asPaddingValues())
    ) {
        FocusGramTopBar(
            title = screenTitle,
            onBack = { handleBack() }
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
                    .background(accentColor.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (step == PinSetupStep.VERIFY_CURRENT) Icons.Default.Shield else Icons.Default.Lock,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(30.dp)
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = headlineText,
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                color = if (isDark) TextPrimary else Color(0xFF111115)
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = subtitleText,
                style = MaterialTheme.typography.bodySmall,
                color = if (isDark) TextSecondary else Color(0xFF6C6C70)
            )

            Spacer(modifier = Modifier.height(30.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                repeat(4) { idx ->
                    val isFilled = idx < currentInput.length
                    Box(
                        modifier = Modifier
                            .size(16.dp)
                            .clip(CircleShape)
                            .background(
                                if (isFilled) accentColor else (if (isDark) DarkCardElevated else Color(0xFFE5E5EA))
                            )
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
    val isDark = isSystemInDarkTheme()
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
                            .background(if (key.isNotBlank()) (if (isDark) DarkCard else Color(0xFFE5E5EA)) else Color.Transparent)
                            .clickable(enabled = key.isNotBlank()) {
                                if (key == "DEL") onDelete() else onDigit(key)
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        if (key == "DEL") {
                            Text(
                                text = "⌫",
                                fontSize = 22.sp,
                                color = if (isDark) TextSecondary else Color(0xFF6C6C70),
                                fontWeight = FontWeight.Bold
                            )
                        } else if (key.isNotBlank()) {
                            Text(
                                text = key,
                                fontSize = 24.sp,
                                color = if (isDark) TextPrimary else Color(0xFF111115),
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }
        }
    }
}
