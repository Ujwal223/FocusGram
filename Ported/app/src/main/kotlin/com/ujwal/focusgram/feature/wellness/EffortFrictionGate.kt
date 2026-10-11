package com.ujwal.focusgram.feature.wellness

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ujwal.focusgram.core.ui.theme.DarkCard
import com.ujwal.focusgram.core.ui.theme.FocusGreen
import com.ujwal.focusgram.core.ui.theme.PureBlack
import com.ujwal.focusgram.core.ui.theme.TextMuted
import com.ujwal.focusgram.core.ui.theme.TextSecondary

/**
 * EffortFrictionGate
 * Imposes intentional friction via a 30-word mindfulness discipline mantra
 * or an optional rewarded ad bypass (+2 minutes) before allowing Instagram access.
 */
@Composable
fun EffortFrictionGate(
    sessionType: String = "Instagram", // "Instagram" or "Reels"
    remainingCreditMinutes: Int = 0,
    onProceed: () -> Unit,
    onWatchAd: () -> Unit,
    onCancel: () -> Unit
) {
    val focusManager = LocalFocusManager.current
    val scrollState = rememberScrollState()

    // Curated 30-word discipline mantras
    val mantras = remember {
        listOf(
            "I choose to control my focus and not let algorithmic feeds govern my attention. Real life happens outside this screen, and my precious time deserves deliberate purpose and unwavering disciplined presence.",
            "Technology is a tool for my growth, not a trap for my restless dopamine. I enter with conscious intent, observe with clear detachment, and leave before endless scrolling steals my peaceful day.",
            "True freedom comes from voluntary discipline. I possess mastery over my impulses, refusing to mindlessly consume superficial digital noise when profound real-world accomplishments demand my undivided attention and creative energy today."
        )
    }

    val targetMantra = remember { mantras.random() }
    var userInput by remember { mutableStateOf("") }

    // Normalize text for comparison
    val normalizedTarget = remember(targetMantra) { targetMantra.trim().lowercase().replace(Regex("\\s+"), " ") }
    val normalizedInput = userInput.trim().lowercase().replace(Regex("\\s+"), " ")

    val isCompleted = normalizedInput == normalizedTarget
    val targetWords = remember(normalizedTarget) { normalizedTarget.split(" ") }
    val inputWords = normalizedInput.split(" ").filter { it.isNotBlank() }
    val matchingWords = inputWords.filterIndexed { index, word ->
        index < targetWords.size && targetWords[index] == word
    }.size

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = PureBlack
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

            // Friction Header Icon
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
                color = Color.White
            )

            Text(
                text = "Type the 30-word discipline mantra or watch a rewarded ad to earn $sessionType time.",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 6.dp, bottom = 20.dp)
            )

            // Current credit banner
            if (remainingCreditMinutes > 0) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(FocusGreen.copy(alpha = 0.15f))
                        .border(1.dp, FocusGreen.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.AccessTime,
                        contentDescription = null,
                        tint = FocusGreen,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = " You have $remainingCreditMinutes min remaining",
                        color = FocusGreen,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            // Target Mantra Card
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(DarkCard)
                    .border(1.dp, Color(0xFF262626), RoundedCornerShape(14.dp))
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
                        color = Color(0xFFE0E0E0)
                    )
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // User Typing Field
            OutlinedTextField(
                value = userInput,
                onValueChange = { userInput = it },
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
                    unfocusedBorderColor = Color(0xFF333333),
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    cursorColor = Color(0xFFFF9800)
                )
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Button 1: Complete Challenge
            Button(
                onClick = onProceed,
                enabled = isCompleted || remainingCreditMinutes > 0,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = FocusGreen,
                    contentColor = PureBlack,
                    disabledContainerColor = Color.White.copy(alpha = 0.1f),
                    disabledContentColor = Color.White.copy(alpha = 0.3f)
                )
            ) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = if (isCompleted) " Challenge Complete — Continue" else " Unlock Session",
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Button 2: Rewarded Ad Bypass
            Button(
                onClick = onWatchAd,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFFF9800),
                    contentColor = PureBlack
                )
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = " Watch Rewarded Ad (+2 min)",
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Button 3: Cancel / Back Out
            OutlinedButton(
                onClick = onCancel,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White.copy(alpha = 0.7f)),
                border = ButtonDefaults.outlinedButtonBorder.copy(brush = Brush.linearGradient(listOf(Color(0xFF333333), Color(0xFF333333))))
            ) {
                Text("Close & Stay Focused")
            }

            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}
