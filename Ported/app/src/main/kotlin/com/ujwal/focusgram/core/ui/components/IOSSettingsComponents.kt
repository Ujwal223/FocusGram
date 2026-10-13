package com.ujwal.focusgram.core.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// iOS Palette
val IOSBg = Color(0xFF000000)
val IOSGroupBg = Color(0xFF1C1C1E)
val IOSDivider = Color(0xFF2C2C2E)
val IOSTextPrimary = Color(0xFFFFFFFF)

/**
 * DynamicNetworkIcon
 * Asynchronously loads project logos dynamically over the network on page open.
 * Does NOT bundle images in the APK to ensure zero APK bloat.
 */
@Composable
fun DynamicNetworkIcon(
    imageUrl: String,
    fallbackIcon: ImageVector,
    iconBgColor: Color,
    modifier: Modifier = Modifier,
    tint: Color = Color.White
) {
    var bitmap by remember(imageUrl) { mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null) }

    LaunchedEffect(imageUrl) {
        if (imageUrl.isNotBlank()) {
            withContext(Dispatchers.IO) {
                try {
                    val conn = java.net.URL(imageUrl).openConnection() as java.net.HttpURLConnection
                    conn.connectTimeout = 5000
                    conn.readTimeout = 5000
                    conn.instanceFollowRedirects = true
                    conn.setRequestProperty("User-Agent", "FocusGram/1.0")
                    conn.inputStream.use { stream ->
                        val decoded = android.graphics.BitmapFactory.decodeStream(stream)
                        if (decoded != null) {
                            bitmap = decoded.asImageBitmap()
                        }
                    }
                } catch (_: Exception) {}
            }
        }
    }

    if (bitmap != null) {
        Image(
            bitmap = bitmap!!,
            contentDescription = null,
            modifier = modifier
                .size(30.dp)
                .clip(RoundedCornerShape(7.dp)),
            contentScale = ContentScale.Crop
        )
    } else {
        Box(
            modifier = modifier
                .size(30.dp)
                .clip(RoundedCornerShape(7.dp))
                .background(iconBgColor),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = fallbackIcon,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}
val IOSTextSecondary = Color(0xFF8E8E93)
val IOSTextSection = Color(0xFF8E8E93)
val IOSGreen = Color(0xFF34C759)
val IOSBlue = Color(0xFF007AFF)
val IOSPurple = Color(0xFFAF52DE)
val IOSOrange = Color(0xFFFF9500)
val IOSRed = Color(0xFFFF3B30)
val IOSTeal = Color(0xFF5AC8FA)

/**
 * IOSSectionTitle
 * Small uppercase section header standard in iOS Settings and Instagram iOS settings.
 */
@Composable
fun IOSSectionTitle(title: String) {
    Text(
        text = title,
        color = IOSTextSection,
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.5.sp,
        modifier = Modifier.padding(start = 16.dp, top = 20.dp, bottom = 8.dp)
    )
}

/**
 * IOSGroupedCard
 * Rounded corner container grouping settings rows together.
 */
@Composable
fun IOSGroupedCard(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val shape = RoundedCornerShape(12.dp)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(IOSGroupBg)
    ) {
        content()
    }
}

/**
 * IOSSwitch
 * Apple iOS authentic toggle switch with fluid sliding animation and tactile styling.
 */
@Composable
fun IOSSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val trackColor by animateColorAsState(
        targetValue = if (checked) IOSGreen else Color(0xFF39393D),
        animationSpec = tween(durationMillis = 200),
        label = "ios_switch_track"
    )
    val thumbOffset by animateDpAsState(
        targetValue = if (checked) 20.dp else 2.dp,
        animationSpec = tween(durationMillis = 200),
        label = "ios_switch_thumb"
    )

    Box(
        modifier = modifier
            .size(width = 51.dp, height = 31.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(trackColor)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { onCheckedChange(!checked) },
        contentAlignment = Alignment.CenterStart
    ) {
        Box(
            modifier = Modifier
                .offset(x = thumbOffset)
                .size(27.dp)
                .shadow(elevation = 2.dp, shape = CircleShape)
                .clip(CircleShape)
                .background(Color.White)
        )
    }
}

/**
 * IOSSettingsRow
 * Single row inside an iOS grouped card.
 */
@Composable
fun IOSSettingsRow(
    title: String,
    subtitle: String? = null,
    icon: ImageVector? = null,
    iconUrl: String? = null,
    iconBgColor: Color = IOSBlue,
    checked: Boolean? = null,
    onCheckedChange: ((Boolean) -> Unit)? = null,
    trailingText: String? = null,
    onClick: (() -> Unit)? = null,
    showDivider: Boolean = true
) {
    val rowModifier = if (onClick != null && checked == null) {
        Modifier
            .fillMaxWidth()
            .clickable { onClick() }
    } else {
        Modifier.fillMaxWidth()
    }

    Column(modifier = rowModifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (iconUrl != null && icon != null) {
                DynamicNetworkIcon(
                    imageUrl = iconUrl,
                    fallbackIcon = icon,
                    iconBgColor = iconBgColor
                )
                Spacer(modifier = Modifier.width(14.dp))
            } else if (icon != null) {
                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .clip(RoundedCornerShape(7.dp))
                        .background(iconBgColor),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Spacer(modifier = Modifier.width(14.dp))
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    color = IOSTextPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Normal
                )
                if (subtitle != null) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = subtitle,
                        color = IOSTextSecondary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Normal,
                        lineHeight = 17.sp
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            if (checked != null && onCheckedChange != null) {
                IOSSwitch(
                    checked = checked,
                    onCheckedChange = onCheckedChange
                )
            } else if (trailingText != null || onClick != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (trailingText != null) {
                        Text(
                            text = trailingText,
                            color = IOSTextSecondary,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Normal
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                    }
                    if (onClick != null) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
                            contentDescription = null,
                            tint = Color(0xFF636366),
                            modifier = Modifier.size(13.dp)
                        )
                    }
                }
            }
        }

        if (showDivider) {
            HorizontalDivider(
                color = IOSDivider,
                thickness = 0.5.dp,
                modifier = Modifier.padding(start = if (icon != null) 60.dp else 16.dp)
            )
        }
    }
}
