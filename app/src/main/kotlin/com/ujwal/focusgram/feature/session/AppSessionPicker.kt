package com.ujwal.focusgram.feature.session

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ujwal.focusgram.core.ui.components.CupertinoWheelPicker
import com.ujwal.focusgram.core.ui.theme.PureBlack
import com.ujwal.focusgram.core.ui.theme.PureWhite

/**
 * Intention timer modal for configuring planned Instagram session duration (1 to 60 minutes).
 */
@Composable
fun AppSessionPicker(
    onSessionSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
    onDismiss: (() -> Unit)? = null
) {
    val minutesList = remember { (1..60).toList() }
    var selectedMinuteIndex by remember { mutableIntStateOf(14) } // Default: 15 min
    val selectedMinutes = minutesList.getOrElse(selectedMinuteIndex) { 15 }

    val isDark = isSystemInDarkTheme()

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(if (isDark) PureBlack else PureWhite)
            .padding(horizontal = 24.dp, vertical = 24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth()
        ) {
            if (onDismiss != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(if (isDark) Color(0xFF1C1C1E) else Color(0xFFF2F2F7))
                            .border(0.5.dp, if (isDark) Color(0xFF2C2C2E) else Color(0xFFE5E5EA), CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = if (isDark) Color.White else PureBlack,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            } else {
                Spacer(modifier = Modifier.height(24.dp))
            }

            Spacer(modifier = Modifier.height(12.dp))

            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(if (isDark) Color(0xFF1C1C1E) else Color(0xFFF2F2F7))
                    .border(1.dp, if (isDark) Color(0xFF2C2C2E) else Color(0xFFE5E5EA), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Timer,
                    contentDescription = "Timer",
                    tint = if (isDark) Color.White else PureBlack,
                    modifier = Modifier.size(30.dp)
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = "Set Your Intention",
                color = if (isDark) Color.White else Color(0xFF111115),
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-0.5).sp
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "How long do you plan to use\nInstagram right now?",
                color = if (isDark) Color(0xFF8E8E93) else Color(0xFF6C6C70),
                fontSize = 15.sp,
                textAlign = TextAlign.Center,
                lineHeight = 20.sp,
                modifier = Modifier.padding(horizontal = 16.dp)
            )

            Spacer(modifier = Modifier.weight(1f))

            CupertinoWheelPicker(
                items = minutesList,
                selectedIndex = selectedMinuteIndex,
                onItemSelected = { selectedMinuteIndex = it },
                label = "min",
                itemHeight = 48.dp,
                visibleItemsCount = 5,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            )

            Spacer(modifier = Modifier.weight(1f))

            Button(
                onClick = { onSessionSelected(selectedMinutes) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(26.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isDark) Color.White else PureBlack,
                    contentColor = if (isDark) Color.Black else PureWhite
                )
            ) {
                Text(
                    text = "Start $selectedMinutes-Minute Session",
                    color = if (isDark) Color.Black else PureWhite,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = "FocusGram will pause your session when time expires.",
                color = if (isDark) Color(0xFF636366) else Color(0xFF8E8E93),
                fontSize = 12.sp,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
