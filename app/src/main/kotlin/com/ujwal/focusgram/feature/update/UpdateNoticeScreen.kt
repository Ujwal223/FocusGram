package com.ujwal.focusgram.feature.update

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ujwal.focusgram.core.ui.theme.DarkCardElevated
import com.ujwal.focusgram.core.ui.theme.FocusGreen
import com.ujwal.focusgram.core.ui.theme.InstagramBlue
import com.ujwal.focusgram.core.ui.theme.LightGrayBg
import com.ujwal.focusgram.core.ui.theme.PureBlack
import com.ujwal.focusgram.core.ui.theme.PureWhite
import com.ujwal.focusgram.core.ui.theme.SecondaryText
import com.ujwal.focusgram.core.ui.theme.TextPrimary
import com.ujwal.focusgram.core.ui.theme.TextSecondary
import com.ujwal.focusgram.core.utils.HapticHelper

/**
 * Notice presented to users updating from legacy versions.
 * Informs the user that the app transitioned to new infrastructure,
 * login/settings could not be ported due to privacy guarantees,
 * and they need to log in again.
 */
@Composable
fun UpdateNoticeScreen(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isDark = isSystemInDarkTheme()
    val view = LocalView.current
    val scrollState = rememberScrollState()
    val bgColor = if (isDark) PureBlack else LightGrayBg
    val cardBg = if (isDark) DarkCardElevated else PureWhite
    val cardBorder = if (isDark) Color(0x1FFFFFFF) else Color(0x0F000000)
    val textPrimaryColor = if (isDark) TextPrimary else PureBlack
    val textSecondaryColor = if (isDark) TextSecondary else SecondaryText

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(bgColor)
            .padding(WindowInsets.statusBars.asPaddingValues())
            .padding(WindowInsets.navigationBars.asPaddingValues())
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(horizontal = 24.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                Spacer(modifier = Modifier.height(28.dp))

                // Hero badge
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .clip(CircleShape)
                        .background(InstagramBlue.copy(alpha = 0.12f))
                        .border(
                            1.dp,
                            InstagramBlue.copy(alpha = 0.35f),
                            CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = null,
                        tint = InstagramBlue,
                        modifier = Modifier.size(38.dp)
                    )
                }

                Spacer(modifier = Modifier.height(26.dp))

                Text(
                    text = "App shifted to new infra",
                    style = MaterialTheme.typography.headlineSmall.copy(
                        fontWeight = FontWeight.Bold,
                        color = textPrimaryColor,
                        lineHeight = 32.sp
                    ),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 8.dp)
                )

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = "The app is shifted to new infra but your login and settings couldn't be ported as Focusgram doesnt have access to your data.\n\nYou need to relogin into the app. Sorry for the inconvenience.",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        color = textSecondaryColor,
                        lineHeight = 22.sp
                    ),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 12.dp)
                )

                Spacer(modifier = Modifier.height(32.dp))

                // Privacy explanation card
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(cardBg)
                        .border(1.dp, cardBorder, RoundedCornerShape(20.dp))
                        .padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    NoticeItemRow(
                        icon = Icons.Default.Lock,
                        iconTint = FocusGreen,
                        title = "100% Client-Side Privacy",
                        description = "Focusgram never accesses, stores, or transmits your credentials or private data off-device.",
                        textPrimaryColor = textPrimaryColor,
                        textSecondaryColor = textSecondaryColor
                    )

                    NoticeItemRow(
                        icon = Icons.Default.Info,
                        iconTint = InstagramBlue,
                        title = "One-Time Relogin",
                        description = "Please log in to your Instagram account to continue with the new engine.",
                        textPrimaryColor = textPrimaryColor,
                        textSecondaryColor = textSecondaryColor
                    )
                }
            }

            // Bottom action button
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 28.dp, bottom = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Button(
                    onClick = {
                        HapticHelper.performConfirm(view)
                        onDismiss()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = InstagramBlue
                    ),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text(
                        text = "Ok, fine",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    )
                }
            }
        }
    }
}

@Composable
private fun NoticeItemRow(
    icon: ImageVector,
    iconTint: Color,
    title: String,
    description: String,
    textPrimaryColor: Color,
    textSecondaryColor: Color
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(iconTint.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(20.dp)
            )
        }

        Spacer(modifier = Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge.copy(
                    fontWeight = FontWeight.SemiBold,
                    color = textPrimaryColor
                )
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall.copy(
                    color = textSecondaryColor,
                    lineHeight = 17.sp
                )
            )
        }
    }
}
