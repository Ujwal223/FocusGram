package com.ujwal.focusgram.core.ui.components

import android.content.Context
import android.media.AudioAttributes
import android.os.Build
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.HapticFeedbackConstants
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ujwal.focusgram.core.ui.theme.PureBlack
import kotlin.math.abs

/**
 * CupertinoWheelPicker
 * Authentic Apple Clock styled rotating drum wheel picker.
 * Features:
 * - 3D cylindrical drum perspective: center item is perfectly flat (0°), 1 above/below are tilted,
 *   2 above/below tilt progressively and vanish into top/bottom shadow gradients.
 * - Center row frosted lens highlight with subtle borders.
 * - Hardware CLOCK_TICK haptics with Vibrator fallback.
 * - Smooth inertial snap fling behavior.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CupertinoWheelPicker(
    items: List<Int>,
    selectedIndex: Int,
    onItemSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
    visibleItemsCount: Int = 5,
    itemHeight: Dp = 46.dp,
    label: String = "min",
    showGradientShadows: Boolean = true
) {
    val view = LocalView.current
    val density = LocalDensity.current
    val itemHeightPx = with(density) { itemHeight.toPx() }

    val halfCount = visibleItemsCount / 2
    val totalHeight = itemHeight * visibleItemsCount

    val initialIndex = selectedIndex.coerceIn(0, (items.size - 1).coerceAtLeast(0))
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = initialIndex)
    val snapFlingBehavior = rememberSnapFlingBehavior(lazyListState = listState)

    // Calculate current centered index based on scroll offset
    val currentCenteredIndex by remember {
        derivedStateOf {
            val firstVisible = listState.firstVisibleItemIndex
            val offset = listState.firstVisibleItemScrollOffset
            val extra = if (offset > itemHeightPx / 2f) 1 else 0
            (firstVisible + extra).coerceIn(0, (items.size - 1).coerceAtLeast(0))
        }
    }

    // Trigger iOS CLOCK_TICK haptic feedback and notify on index change
    LaunchedEffect(listState) {
        snapshotFlow { currentCenteredIndex }
            .collect { index ->
                if (index in items.indices) {
                    performWheelHaptic(view.context, view)
                    onItemSelected(index)
                }
            }
    }

    Box(
        modifier = modifier
            .height(totalHeight)
            .fillMaxWidth(),
        contentAlignment = Alignment.Center
    ) {
        // iOS Clock Center Lens Highlight Bar with subtle outline
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(itemHeight)
                .clip(RoundedCornerShape(8.dp))
                .background(Color.White.copy(alpha = 0.08f))
                .border(
                    width = 0.5.dp,
                    color = Color.White.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(8.dp)
                )
        )

        LazyColumn(
            state = listState,
            flingBehavior = snapFlingBehavior,
            contentPadding = PaddingValues(vertical = itemHeight * halfCount),
            modifier = Modifier
                .fillMaxWidth()
                .height(totalHeight),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            items(count = items.size) { index ->
                val itemValue = items[index]
                val isSelected = index == currentCenteredIndex

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(itemHeight)
                        .graphicsLayer {
                            // Calculate continuous distance from center for ultra-smooth 3D projection
                            val scrollFraction = if (itemHeightPx > 0f) listState.firstVisibleItemScrollOffset / itemHeightPx else 0f
                            val continuousCenter = listState.firstVisibleItemIndex + scrollFraction
                            val distanceFromCenter = index - continuousCenter
                            val absDistance = abs(distanceFromCenter)

                            // 3D Drum Perspective:
                            // Center item (0) is perfectly flat (rotationX = 0f)
                            // Item 1 above (-1) tilts top away (+28f)
                            // Item 1 below (+1) tilts bottom away (-28f)
                            // Outer items progressively tilt and vanish into shadow
                            rotationX = (-distanceFromCenter * 28f).coerceIn(-65f, 65f)
                            scaleX = (1f - absDistance * 0.09f).coerceIn(0.70f, 1f)
                            scaleY = (1f - absDistance * 0.09f).coerceIn(0.70f, 1f)
                            alpha = if (absDistance > 2.4f) {
                                0f
                            } else {
                                (1f - absDistance * 0.36f).coerceIn(0.12f, 1f)
                            }
                            cameraDistance = 24f * density.density
                        },
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = itemValue.toString(),
                        color = if (isSelected) Color.White else Color(0xFF8E8E93),
                        fontSize = if (isSelected) 30.sp else 24.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        textAlign = TextAlign.Center
                    )
                    if (label.isNotBlank()) {
                        Text(
                            text = " $label",
                            color = if (isSelected) Color.White.copy(alpha = 0.85f) else Color(0xFF636366),
                            fontSize = if (isSelected) 17.sp else 14.sp,
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                            modifier = Modifier.padding(start = 4.dp, top = 2.dp)
                        )
                    }
                }
            }
        }

        if (showGradientShadows) {
            // Top cylinder gradient shadow (fades upper tilted items into pure black)
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .height(itemHeight * 1.5f)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                PureBlack,
                                PureBlack.copy(alpha = 0.85f),
                                Color.Transparent
                            )
                        )
                    )
            )

            // Bottom cylinder gradient shadow (fades lower tilted items into pure black)
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(itemHeight * 1.5f)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                PureBlack.copy(alpha = 0.85f),
                                PureBlack
                            )
                        )
                    )
            )
        }
    }
}

/**
 * CupertinoOptionWheelPicker
 * Reusable iOS drum wheel picker for discrete options such as "Off", "5s", "10 min", etc.
 * Features 3D cylinder perspective with center item flat, progressive tilt, and gradient fades.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun <T> CupertinoOptionWheelPicker(
    options: List<T>,
    selectedIndex: Int,
    onOptionSelected: (Int, T) -> Unit,
    modifier: Modifier = Modifier,
    labelProvider: (T) -> String = { it.toString() },
    visibleItemsCount: Int = 3,
    itemHeight: Dp = 38.dp,
    showGradientShadows: Boolean = true
) {
    val view = LocalView.current
    val density = LocalDensity.current
    val itemHeightPx = with(density) { itemHeight.toPx() }

    val halfCount = visibleItemsCount / 2
    val totalHeight = itemHeight * visibleItemsCount

    val initialIndex = selectedIndex.coerceIn(0, (options.size - 1).coerceAtLeast(0))
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = initialIndex)
    val snapFlingBehavior = rememberSnapFlingBehavior(lazyListState = listState)

    val currentCenteredIndex by remember {
        derivedStateOf {
            val firstVisible = listState.firstVisibleItemIndex
            val offset = listState.firstVisibleItemScrollOffset
            val extra = if (offset > itemHeightPx / 2f) 1 else 0
            (firstVisible + extra).coerceIn(0, (options.size - 1).coerceAtLeast(0))
        }
    }

    LaunchedEffect(listState) {
        snapshotFlow { currentCenteredIndex }
            .collect { index ->
                if (index in options.indices) {
                    performWheelHaptic(view.context, view)
                    onOptionSelected(index, options[index])
                }
            }
    }

    Box(
        modifier = modifier
            .height(totalHeight)
            .fillMaxWidth(),
        contentAlignment = Alignment.Center
    ) {
        // Center frosted highlight lens
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(itemHeight)
                .clip(RoundedCornerShape(8.dp))
                .background(Color.White.copy(alpha = 0.08f))
                .border(
                    width = 0.5.dp,
                    color = Color.White.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(8.dp)
                )
        )

        LazyColumn(
            state = listState,
            flingBehavior = snapFlingBehavior,
            contentPadding = PaddingValues(vertical = itemHeight * halfCount),
            modifier = Modifier
                .fillMaxWidth()
                .height(totalHeight),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            items(count = options.size) { index ->
                val itemValue = options[index]
                val isSelected = index == currentCenteredIndex
                val displayLabel = labelProvider(itemValue)

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(itemHeight)
                        .graphicsLayer {
                            val scrollFraction = if (itemHeightPx > 0f) listState.firstVisibleItemScrollOffset / itemHeightPx else 0f
                            val continuousCenter = listState.firstVisibleItemIndex + scrollFraction
                            val distanceFromCenter = index - continuousCenter
                            val absDistance = abs(distanceFromCenter)

                            rotationX = (-distanceFromCenter * 26f).coerceIn(-60f, 60f)
                            scaleX = (1f - absDistance * 0.08f).coerceIn(0.75f, 1f)
                            scaleY = (1f - absDistance * 0.08f).coerceIn(0.75f, 1f)
                            alpha = if (absDistance > 1.8f) {
                                0f
                            } else {
                                (1f - absDistance * 0.40f).coerceIn(0.18f, 1f)
                            }
                            cameraDistance = 24f * density.density
                        },
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = displayLabel,
                        color = if (isSelected) Color.White else Color(0xFF8E8E93),
                        fontSize = if (isSelected) 18.sp else 14.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }

        if (showGradientShadows) {
            // Top cylinder shadow
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .height(itemHeight * 0.9f)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                PureBlack,
                                PureBlack.copy(alpha = 0.75f),
                                Color.Transparent
                            )
                        )
                    )
            )

            // Bottom cylinder shadow
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(itemHeight * 0.9f)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                PureBlack.copy(alpha = 0.75f),
                                PureBlack
                            )
                        )
                    )
            )
        }
    }
}

private fun getVibrator(context: Context): Vibrator? {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val vibratorManager = context.getSystemService(VibratorManager::class.java)
        vibratorManager?.defaultVibrator
    } else {
        context.getSystemService(Vibrator::class.java)
    }
}

/**
 * Perform crisp haptic tick even when user has system "touch feedback" disabled.
 * Uses both View ignore flags, and falls back to physical Vibrator with USAGE_ALARM.
 */
private fun performWheelHaptic(context: Context, view: android.view.View) {
    try {
        // 1. Try View haptics with ignore flags
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING
        } else {
            @Suppress("DEPRECATION")
            HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING or HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING
        }
        val performed = view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK, flags)
        if (performed) return

        // 2. Hardware Vibrator fallback with USAGE_ALARM to bypass system touch vibration disablement
        val vibrator = getVibrator(context) ?: return
        if (!vibrator.hasVibrator()) return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val attributes = VibrationAttributes.Builder()
                .setUsage(VibrationAttributes.USAGE_ALARM)
                .build()
            val effect = VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK)
            vibrator.vibrate(effect, attributes)
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val audioAttributes = AudioAttributes.Builder()
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .setUsage(AudioAttributes.USAGE_ALARM)
                .build()
            val effect = VibrationEffect.createOneShot(12L, VibrationEffect.DEFAULT_AMPLITUDE)
            @Suppress("DEPRECATION")
            vibrator.vibrate(effect, audioAttributes)
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(10L)
        }
    } catch (_: Exception) {}
}
