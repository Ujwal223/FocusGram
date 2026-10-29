package com.ujwal.focusgram.core.ui.components

import android.content.Context
import android.media.AudioAttributes
import android.os.Build
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.HapticFeedbackConstants
import com.ujwal.focusgram.core.utils.HapticHelper
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
 * Rotating drum wheel picker with 3D cylindrical projection and inertial snap flinging.
 *
 * Items above and below the center index rotate along the X-axis and scale down
 * to simulate a physical tumbler drum.
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
    showGradientShadows: Boolean = true,
    labelProvider: ((Int) -> String)? = null
) {
    val view = LocalView.current
    val density = LocalDensity.current
    val itemHeightPx = with(density) { itemHeight.toPx() }

    val halfCount = visibleItemsCount / 2
    val totalHeight = itemHeight * visibleItemsCount

    val initialIndex = selectedIndex.coerceIn(0, (items.size - 1).coerceAtLeast(0))
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = initialIndex)
    val snapFlingBehavior = rememberSnapFlingBehavior(lazyListState = listState)

    val currentCenteredIndex by remember {
        derivedStateOf {
            val layoutInfo = listState.layoutInfo
            val visibleItems = layoutInfo.visibleItemsInfo
            if (visibleItems.isEmpty()) {
                initialIndex
            } else {
                val viewportCenter = (layoutInfo.viewportStartOffset + layoutInfo.viewportEndOffset) / 2
                val closest = visibleItems.minByOrNull { item ->
                    abs((item.offset + item.size / 2) - viewportCenter)
                }
                closest?.index ?: initialIndex
            }
        }
    }

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
                            val layoutInfo = listState.layoutInfo
                            val viewportCenter = (layoutInfo.viewportStartOffset + layoutInfo.viewportEndOffset) / 2f
                            val itemInfo = layoutInfo.visibleItemsInfo.firstOrNull { it.index == index }
                            val distanceFromCenter = if (itemInfo != null && itemHeightPx > 0f) {
                                ((itemInfo.offset + itemInfo.size / 2f) - viewportCenter) / itemHeightPx
                            } else {
                                (index - currentCenteredIndex).toFloat()
                            }
                            val absDistance = abs(distanceFromCenter)

                            // Cylindrical drum perspective tilt and foreshortening
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
                    val displayText = labelProvider?.invoke(itemValue) ?: itemValue.toString()
                    Text(
                        text = displayText,
                        color = if (isSelected) Color.White else Color(0xFF8E8E93),
                        fontSize = if (isSelected) 30.sp else 24.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        textAlign = TextAlign.Center
                    )
                    if (label.isNotBlank() && labelProvider == null) {
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
 * Cylindrical drum wheel picker for discrete option lists with center magnification.
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
            val layoutInfo = listState.layoutInfo
            val visibleItems = layoutInfo.visibleItemsInfo
            if (visibleItems.isEmpty()) {
                initialIndex
            } else {
                val viewportCenter = (layoutInfo.viewportStartOffset + layoutInfo.viewportEndOffset) / 2
                val closest = visibleItems.minByOrNull { item ->
                    abs((item.offset + item.size / 2) - viewportCenter)
                }
                closest?.index ?: initialIndex
            }
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
                            val layoutInfo = listState.layoutInfo
                            val viewportCenter = (layoutInfo.viewportStartOffset + layoutInfo.viewportEndOffset) / 2f
                            val itemInfo = layoutInfo.visibleItemsInfo.firstOrNull { it.index == index }
                            val distanceFromCenter = if (itemInfo != null && itemHeightPx > 0f) {
                                ((itemInfo.offset + itemInfo.size / 2f) - viewportCenter) / itemHeightPx
                            } else {
                                (index - currentCenteredIndex).toFloat()
                            }
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
 * Wheel tick haptic fallback when system-level touch feedback is disabled.
 */
private fun performWheelHaptic(context: Context, view: android.view.View) {
    HapticHelper.performWheelTick(context, view)
}

