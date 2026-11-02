package com.ujwal.focusgram.feature.applock

import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.keyframes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.ujwal.focusgram.core.security.EncryptedStorage
import com.ujwal.focusgram.core.ui.theme.AlertRed
import com.ujwal.focusgram.core.ui.theme.DarkCard
import com.ujwal.focusgram.core.ui.theme.FocusGold
import com.ujwal.focusgram.core.ui.theme.PureBlack
import com.ujwal.focusgram.core.ui.theme.PureWhite
import com.ujwal.focusgram.core.ui.theme.TextMuted
import com.ujwal.focusgram.core.ui.theme.TextSecondary
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private val ChatLockAccent = Color(0xFF9D4EDD)
private val ChatLockAccentGlow = Color(0xFFC77DFF)
private val KeypadCardBg: Color
    @Composable get() = if (isSystemInDarkTheme()) Color(0xFF16161A) else Color(0xFFE5E5EA)

/**
 * PIN and biometric authentication gate for locked Direct Messages.
 */
@Composable
fun ChatLockScreen(
    encryptedStorage: EncryptedStorage,
    isBiometricAllowed: Boolean = true,
    title: String = "Chat Protected",
    subtitle: String = "Enter 4-digit PIN to view conversation",
    customVerifyPin: ((String) -> Boolean)? = null,
    onCancel: (() -> Unit)? = null,
    onSetupPin: (() -> Unit)? = null,
    onUnlockSuccess: () -> Unit
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val coroutineScope = rememberCoroutineScope()

    var enteredPin by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val shakeOffset = remember { Animatable(0f) }
    val hasConfiguredPin = remember { (customVerifyPin != null) || encryptedStorage.hasPin() || encryptedStorage.hasChatPin() }

    fun triggerBiometricPrompt() {
        if (!isBiometricAllowed) return
        // BiometricPrompt requires a FragmentActivity. Unwraps ContextWrapper chains.
        var ctx = context
        while (ctx is android.content.ContextWrapper) {
            if (ctx is FragmentActivity) break
            ctx = ctx.baseContext
        }
        val activity = ctx as? FragmentActivity ?: return

        val biometricManager = BiometricManager.from(activity)
        val canAuth = biometricManager.canAuthenticate(
            BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.BIOMETRIC_WEAK
        )
        if (canAuth == BiometricManager.BIOMETRIC_SUCCESS) {
            val executor = ContextCompat.getMainExecutor(activity)
            val prompt = BiometricPrompt(
                activity,
                executor,
                object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                        super.onAuthenticationSucceeded(result)
                        onUnlockSuccess()
                    }

                    override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                        super.onAuthenticationError(errorCode, errString)
                        if (errorCode != BiometricPrompt.ERROR_USER_CANCELED &&
                            errorCode != BiometricPrompt.ERROR_NEGATIVE_BUTTON &&
                            errorCode != BiometricPrompt.ERROR_CANCELED
                        ) {
                            errorMessage = errString.toString()
                        }
                    }

                    override fun onAuthenticationFailed() {
                        super.onAuthenticationFailed()
                        errorMessage = "Biometric not recognized"
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    }
                }
            )

            val promptInfo = BiometricPrompt.PromptInfo.Builder()
                .setTitle(title)
                .setSubtitle("Authenticate to view locked messages")
                .setNegativeButtonText("Use PIN")
                .build()

            prompt.authenticate(promptInfo)
        }
    }

    LaunchedEffect(isBiometricAllowed) {
        if (isBiometricAllowed) {
            triggerBiometricPrompt()
        }
    }

    fun verifyEnteredPin(pin: String) {
        if (encryptedStorage.isLockedOut()) {
            val remainingSec = encryptedStorage.getRemainingLockoutSeconds()
            errorMessage = "Too many attempts. Locked out for ${remainingSec}s."
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            return
        }

        val isValid = if (customVerifyPin != null) {
            customVerifyPin(pin)
        } else {
            encryptedStorage.verifyPin(pin)
        }

        if (isValid) {
            errorMessage = null
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            onUnlockSuccess()
        } else {
            errorMessage = "Incorrect PIN"
            enteredPin = ""
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            coroutineScope.launch {
                shakeOffset.snapTo(0f)
                shakeOffset.animateTo(
                    targetValue = 0f,
                    animationSpec = keyframes {
                        durationMillis = 400
                        0f at 0
                        (-25f) at 50
                        25f at 100
                        (-20f) at 150
                        20f at 200
                        (-10f) at 250
                        10f at 300
                        0f at 400
                    }
                )
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

    val isDark = isSystemInDarkTheme()

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = if (isDark) PureBlack else PureWhite
    ) {
        val bgGradient = if (isDark) {
            Brush.verticalGradient(
                colors = listOf(
                    Color(0xFF0F0B17),
                    Color(0xFF060608),
                    PureBlack
                )
            )
        } else {
            Brush.verticalGradient(
                colors = listOf(
                    Color(0xFFF9F5FF),
                    Color(0xFFF2EFF7),
                    PureWhite
                )
            )
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(bgGradient)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .systemBarsPadding()
                    .padding(horizontal = 28.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp, bottom = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (onCancel != null) {
                        IconButton(
                            onClick = onCancel,
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background((if (isDark) Color.White else PureBlack).copy(alpha = 0.08f))
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back to inbox",
                                tint = if (isDark) Color.White else PureBlack,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    } else {
                        Spacer(modifier = Modifier.size(36.dp))
                    }

                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(ChatLockAccent.copy(alpha = 0.15f))
                            .border(0.5.dp, ChatLockAccent.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                            .padding(horizontal = 10.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Shield,
                            contentDescription = null,
                            tint = ChatLockAccentGlow,
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(
                            text = "ENCRYPTED GATE",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = ChatLockAccentGlow,
                            letterSpacing = 0.8.sp
                        )
                    }

                    Spacer(modifier = Modifier.size(36.dp))
                }

                Spacer(modifier = Modifier.weight(0.7f))

                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.linearGradient(
                                colors = listOf(
                                    ChatLockAccent.copy(alpha = 0.25f),
                                    Color(0xFF3C096C).copy(alpha = 0.15f)
                                )
                            )
                        )
                        .border(1.dp, ChatLockAccent.copy(alpha = 0.4f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = "Lock",
                        tint = ChatLockAccentGlow,
                        modifier = Modifier.size(32.dp)
                    )
                }

                Spacer(modifier = Modifier.height(18.dp))

                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                    color = if (isDark) Color.White else Color(0xFF111115),
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = if (!hasConfiguredPin) "No PIN configured on this device" else subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isDark) TextSecondary else Color(0xFF6C6C70),
                    textAlign = TextAlign.Center
                )

                if (!hasConfiguredPin && onSetupPin != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    androidx.compose.material3.TextButton(
                        onClick = onSetupPin,
                        colors = androidx.compose.material3.ButtonDefaults.textButtonColors(
                            contentColor = ChatLockAccentGlow
                        )
                    ) {
                        Text(
                            text = "Set Up PIN to Unlock",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(28.dp))

                Row(
                    modifier = Modifier
                        .offset { IntOffset(shakeOffset.value.roundToInt(), 0) }
                        .padding(vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(18.dp)
                ) {
                    for (i in 0 until 4) {
                        val isFilled = i < enteredPin.length
                        Box(
                            modifier = Modifier
                                .size(16.dp)
                                .clip(CircleShape)
                                .background(
                                    if (isFilled) ChatLockAccentGlow else (if (isDark) Color.White.copy(alpha = 0.18f) else Color.Black.copy(alpha = 0.12f))
                                )
                                .border(
                                    width = if (isFilled) 1.5.dp else 1.dp,
                                    color = if (isFilled) (if (isDark) Color.White else PureBlack) else (if (isDark) Color.White.copy(alpha = 0.1f) else Color.Black.copy(alpha = 0.08f)),
                                    shape = CircleShape
                                )
                        )
                    }
                }

                if (errorMessage != null) {
                    Text(
                        text = errorMessage ?: "",
                        color = AlertRed,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                } else {
                    Spacer(modifier = Modifier.height(24.dp))
                }

                Spacer(modifier = Modifier.weight(1.2f))

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    val bioKey = if (isBiometricAllowed) "BIO" else ""
                    val keypadRows = listOf(
                        listOf("1", "2", "3"),
                        listOf("4", "5", "6"),
                        listOf("7", "8", "9"),
                        listOf(bioKey, "0", "DEL")
                    )

                    for (row in keypadRows) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            for (key in row) {
                                KeypadCell(
                                    key = key,
                                    onDigit = { onDigitPressed(it) },
                                    onBackspace = { onBackspacePressed() },
                                    onBiometric = { triggerBiometricPrompt() }
                                )
                            }
                        }
                    }

                    if (com.ujwal.focusgram.BuildConfig.DEBUG) {
                        Spacer(modifier = Modifier.height(6.dp))
                        TextButton(
                            onClick = onUnlockSuccess,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "⚡ Skip Chat Lock (Debug)",
                                color = FocusGold,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun KeypadCell(
    key: String,
    onDigit: (String) -> Unit,
    onBackspace: () -> Unit,
    onBiometric: () -> Unit
) {
    val isDark = isSystemInDarkTheme()

    Box(
        modifier = Modifier
            .size(72.dp)
            .clip(CircleShape)
            .background(if (key.isNotBlank()) KeypadCardBg else Color.Transparent)
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
                imageVector = Icons.AutoMirrored.Filled.Backspace,
                contentDescription = "Delete",
                tint = if (isDark) Color.White.copy(alpha = 0.75f) else Color.Black.copy(alpha = 0.75f),
                modifier = Modifier.size(22.dp)
            )
            "BIO" -> Icon(
                imageVector = Icons.Default.Fingerprint,
                contentDescription = "Biometric",
                tint = ChatLockAccentGlow,
                modifier = Modifier.size(28.dp)
            )
            "" -> {
                // Empty spacer cell so layout remains symmetrical without any biometric icon
            }
            else -> Text(
                text = key,
                fontSize = 25.sp,
                fontWeight = FontWeight.Medium,
                color = if (isDark) Color.White else Color(0xFF111115)
            )
        }
    }
}
