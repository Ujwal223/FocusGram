package com.ujwal.focusgram.feature.wellness

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ClipboardManager
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalTextToolbar
import androidx.compose.ui.platform.TextToolbar
import androidx.compose.ui.platform.TextToolbarStatus
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ujwal.focusgram.core.ui.theme.DarkCard
import com.ujwal.focusgram.core.ui.theme.FocusGold
import com.ujwal.focusgram.core.ui.theme.FocusGreen
import com.ujwal.focusgram.core.ui.theme.PureBlack
import com.ujwal.focusgram.core.ui.theme.PureWhite
import com.ujwal.focusgram.core.ui.theme.TextMuted
import com.ujwal.focusgram.core.ui.theme.TextSecondary

/**
 * Friction gate requiring the user to type an intentional discipline mantra to unlock an intentional browsing window.
 */
@Composable
fun EffortFrictionGate(
    sessionType: String = "Instagram", // "Instagram" or "Reels"
    wordCount: Int = 10,
    onProceed: () -> Unit,
    onCancel: () -> Unit
) {
    val focusManager = LocalFocusManager.current
    val scrollState = rememberScrollState()

    val targetMantra = remember(wordCount) {
        DisciplineMantraRepository.getNextMantra(wordCount)
    }
    var userInput by remember { mutableStateOf("") }

    // Normalize whitespace and case to ensure fair match against prompt
    val normalizedTarget = remember(targetMantra) { targetMantra.trim().lowercase().replace(Regex("\\s+"), " ") }
    val normalizedInput = userInput.trim().lowercase().replace(Regex("\\s+"), " ")

    val isCompleted = normalizedInput == normalizedTarget
    val targetWords = remember(normalizedTarget) { normalizedTarget.split(" ").filter { it.isNotBlank() } }
    val inputWords = normalizedInput.split(" ").filter { it.isNotBlank() }
    val matchingWords = inputWords.filterIndexed { index, word ->
        index < targetWords.size && targetWords[index] == word
    }.size

    val isDark = isSystemInDarkTheme()

    // Disable copy-paste menu toolbar and system clipboard access so the user must type consciously
    val emptyTextToolbar = remember {
        object : TextToolbar {
            override val status: TextToolbarStatus
                get() = TextToolbarStatus.Hidden
            override fun hide() {}
            override fun showMenu(
                rect: Rect,
                onCopyRequested: (() -> Unit)?,
                onPasteRequested: (() -> Unit)?,
                onCutRequested: (() -> Unit)?,
                onSelectAllRequested: (() -> Unit)?
            ) {}
        }
    }

    val noOpClipboardManager = remember {
        object : ClipboardManager {
            override fun getText(): AnnotatedString? = null
            override fun setText(annotatedString: AnnotatedString) {}
            override fun hasText(): Boolean = false
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = if (isDark) PureBlack else PureWhite
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .verticalScroll(scrollState)
                .padding(horizontal = 24.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.linearGradient(
                            listOf(Color(0xFFE65100), Color(0xFFFF9800))
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.FitnessCenter,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(36.dp)
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = "Effort Friction Gate",
                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                color = if (isDark) Color.White else Color(0xFF111115)
            )

            Text(
                text = "Type the ${targetWords.size}-word discipline mantra to earn $sessionType time.",
                style = MaterialTheme.typography.bodyMedium,
                color = if (isDark) TextSecondary else Color(0xFF6C6C70),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 6.dp, bottom = 20.dp)
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(if (isDark) DarkCard else Color(0xFFF2F2F7))
                    .border(1.dp, if (isDark) Color(0xFF262626) else Color(0xFFE5E5EA), RoundedCornerShape(14.dp))
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "DISCIPLINE MANTRA",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFFFF9800),
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "$matchingWords / ${targetWords.size} words",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isCompleted) FocusGreen else TextMuted
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = targetMantra,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontFamily = FontFamily.Monospace,
                        lineHeight = 22.sp,
                        color = if (isDark) Color(0xFFE0E0E0) else Color(0xFF222225)
                    )
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            CompositionLocalProvider(
                LocalTextToolbar provides emptyTextToolbar,
                LocalClipboardManager provides noOpClipboardManager
            ) {
                OutlinedTextField(
                    value = userInput,
                    onValueChange = { newValue ->
                        // Reject multi-character batch inserts from paste, adb input text, or external force-push
                        if (isSingleCharOrDelete(userInput, newValue)) {
                            userInput = newValue
                        }
                    },
                    placeholder = {
                        Text("Type the mantra accurately above...", color = TextMuted)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3,
                    maxLines = 5,
                    shape = RoundedCornerShape(14.dp),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = if (isCompleted) FocusGreen else Color(0xFFFF9800),
                        unfocusedBorderColor = if (isDark) Color(0xFF333333) else Color(0xFFD1D1D6),
                        focusedTextColor = if (isDark) Color.White else Color.Black,
                        unfocusedTextColor = if (isDark) Color.White else Color.Black,
                        cursorColor = Color(0xFFFF9800)
                    )
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = onProceed,
                enabled = isCompleted,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = FocusGreen,
                    contentColor = PureBlack,
                    disabledContainerColor = if (isDark) Color.White.copy(alpha = 0.1f) else Color.Black.copy(alpha = 0.08f),
                    disabledContentColor = if (isDark) Color.White.copy(alpha = 0.3f) else Color.Black.copy(alpha = 0.3f)
                )
            ) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = if (isCompleted) " Challenge Complete: Continue" else " Unlock Session",
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            val outlineBorderColor = if (isDark) Color(0xFF333333) else Color(0xFFD1D1D6)
            OutlinedButton(
                onClick = onCancel,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = if (isDark) Color.White.copy(alpha = 0.7f) else Color.Black.copy(alpha = 0.7f)
                ),
                border = ButtonDefaults.outlinedButtonBorder(enabled = true).copy(
                    brush = Brush.linearGradient(listOf(outlineBorderColor, outlineBorderColor))
                )
            ) {
                Text("Close & Stay Focused")
            }

            if (com.ujwal.focusgram.BuildConfig.DEBUG) {
                Spacer(modifier = Modifier.height(10.dp))
                TextButton(
                    onClick = onProceed,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "⚡ Skip Friction Challenge (Debug)",
                        color = FocusGold,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}

/**
 * Validates that an edit is either a single character insertion, a single character replacement,
 * or a deletion. Blocks multi-character batch inserts from paste, adb input text, or external injection.
 */
internal fun isSingleCharOrDelete(oldText: String, newText: String): Boolean {
    // If text was shortened or unchanged, allow (deletion or no-op)
    if (newText.length < oldText.length) {
        return true
    }
    // Find common prefix length
    var prefix = 0
    while (prefix < oldText.length && prefix < newText.length && oldText[prefix] == newText[prefix]) {
        prefix++
    }
    // Find common suffix length
    var oldSuffix = oldText.length - 1
    var newSuffix = newText.length - 1
    while (oldSuffix >= prefix && newSuffix >= prefix && oldText[oldSuffix] == newText[newSuffix]) {
        oldSuffix--
        newSuffix--
    }
    // Number of new characters introduced
    val insertedCount = newSuffix - prefix + 1
    return insertedCount <= 1
}
