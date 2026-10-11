package com.ujwal.focusgram.feature.applock

import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.keyframes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backspace
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.ujwal.focusgram.core.security.EncryptedStorage
import com.ujwal.focusgram.core.ui.theme.AlertRed
import com.ujwal.focusgram.core.ui.theme.DarkCard
import com.ujwal.focusgram.core.ui.theme.InstagramBlue
import com.ujwal.focusgram.core.ui.theme.PureBlack
import com.ujwal.focusgram.core.ui.theme.TextMuted
import com.ujwal.focusgram.core.ui.theme.TextSecondary
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * AppLockScreen
 * Hardware-backed PIN and Biometric lock gate protecting FocusGram.
 */
@Composable
fun AppLockScreen(
    encryptedStorage: EncryptedStorage,
    isBiometricAllowed: Boolean = true,
    title: String = "FocusGram is Locked",
    subtitle: String = "Enter your PIN to unlock",
    onUnlockSuccess: () -> Unit
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val coroutineScope = rememberCoroutineScope()

    var enteredPin by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val shakeOffset = remember { Animatable(0f) }

    fun triggerBiometricPrompt() {
        if (isBiometricAllowed && context is FragmentActivity) {
            val biometricManager = BiometricManager.from(context)
            if (biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.BIOMETRIC_WEAK)
                == BiometricManager.BIOMETRIC_SUCCESS
            ) {
                val executor = ContextCompat.getMainExecutor(context)
                val prompt = BiometricPrompt(
                    context,
                    executor,
                    object : BiometricPrompt.AuthenticationCallback() {
                        override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                            super.onAuthenticationSucceeded(result)
                            onUnlockSuccess()
                        }
                    }
                )

                val promptInfo = BiometricPrompt.PromptInfo.Builder()
                    .setTitle("Unlock FocusGram")
                    .setSubtitle("Confirm your biometric to proceed")
                    .setNegativeButtonText("Use PIN")
                    .build()

                prompt.authenticate(promptInfo)
            }
        }
    }

    // Trigger BiometricPrompt if enabled and available
    LaunchedEffect(Unit) {
        triggerBiometricPrompt()
    }

    fun verifyEnteredPin(pin: String) {
        if (encryptedStorage.isLockedOut()) {
            val remainingSec = encryptedStorage.getRemainingLockoutSeconds()
            errorMessage = "Too many attempts. Locked out for ${remainingSec}s."
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            return
        }

        val isValid = encryptedStorage.verifyPin(pin)
        if (isValid) {
            onUnlockSuccess()
        } else {
            errorMessage = "Incorrect PIN"
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            coroutineScope.launch {
                shakeOffset.animateTo(
                    targetValue = 0f,
                    animationSpec = keyframes {
                        durationMillis = 350
                        0f at 0
                        (-20f) at 50
                        20f at 100
                        (-15f) at 150
                        15f at 200
                        (-8f) at 250
                        8f at 300
                        0f at 350
                    }
                )
                enteredPin = ""
            }
        }
    }

    fun onDigitPressed(digit: String) {
        if (enteredPin.length < 4) {
            val newPin = enteredPin + digit
            enteredPin = newPin
            errorMessage = null
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)

            if (newPin.length == 4) {
                verifyEnteredPin(newPin)
            }
        }
    }

    fun onBackspacePressed() {
        if (enteredPin.isNotEmpty()) {
            enteredPin = enteredPin.dropLast(1)
            errorMessage = null
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = PureBlack
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.weight(1f))

            // Lock Icon
            Box(
                modifier = Modifier
                    .size(68.dp)
                    .clip(CircleShape)
                    .background(InstagramBlue.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = null,
                    tint = InstagramBlue,
                    modifier = Modifier.size(32.dp)
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = title,
                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                color = Color.White
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary
            )

            Spacer(modifier = Modifier.height(36.dp))

            // 4 PIN Dots with Shake Offset
            Row(
                modifier = Modifier
                    .offset { IntOffset(shakeOffset.value.roundToInt(), 0) }
                    .padding(vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                for (i in 0 until 4) {
                    val isFilled = i < enteredPin.length
                    Box(
                        modifier = Modifier
                            .size(18.dp)
                            .clip(CircleShape)
                            .background(
                                if (isFilled) InstagramBlue else Color.White.copy(alpha = 0.2f)
                            )
                    )
                }
            }

            // Error Text
            if (errorMessage != null) {
                Text(
                    text = errorMessage!!,
                    color = AlertRed,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 12.dp)
                )
            } else {
                Spacer(modifier = Modifier.height(28.dp))
            }

            Spacer(modifier = Modifier.weight(1.5f))

            // 3x4 Numeric Keypad
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                val keypadRows = listOf(
                    listOf("1", "2", "3"),
                    listOf("4", "5", "6"),
                    listOf("7", "8", "9"),
                    listOf("BIO", "0", "DEL")
                )

                for (row in keypadRows) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        for (key in row) {
                            KeypadButton(
                                key = key,
                                onDigit = { onDigitPressed(it) },
                                onBackspace = { onBackspacePressed() },
                                onBiometric = {
                                    triggerBiometricPrompt()
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun KeypadButton(
    key: String,
    onDigit: (String) -> Unit,
    onBackspace: () -> Unit,
    onBiometric: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(76.dp)
            .clip(CircleShape)
            .background(if (key.isNotBlank()) DarkCard else Color.Transparent)
            .clickable(enabled = key.isNotBlank()) {
                when (key) {
                    "DEL" -> onBackspace()
                    "BIO" -> onBiometric()
                    else -> onDigit(key)
                }
            },
        contentAlignment = Alignment.Center
    ) {
        when (key) {
            "DEL" -> Icon(
                imageVector = Icons.Default.Backspace,
                contentDescription = "Delete",
                tint = Color.White.copy(alpha = 0.7f),
                modifier = Modifier.size(24.dp)
            )
            "BIO" -> Icon(
                imageVector = Icons.Default.Fingerprint,
                contentDescription = "Biometric",
                tint = InstagramBlue,
                modifier = Modifier.size(28.dp)
            )
            else -> Text(
                text = key,
                fontSize = 26.sp,
                fontWeight = FontWeight.Medium,
                color = Color.White
            )
        }
    }
}
