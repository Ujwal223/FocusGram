package com.ujwal.focusgram.core.ui.components

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs

/**
 * CupertinoWheelPicker
 * Authentic Apple Clock styled rotating drum wheel picker.
 * Features 3D cylindrical drum perspective (rotationX), center row frosted lens highlight,
 * smooth inertial snap fling physics, and hardware CLOCK_TICK haptic feedback on every step.
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
    label: String = "min"
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
                    view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
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
        // iOS Clock Center Lens Highlight Bar
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(itemHeight)
                .clip(RoundedCornerShape(10.dp))
                .background(Color.White.copy(alpha = 0.08f))
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

                // Compute 3D cylindrical drum perspective based on item layout info
                val layoutInfo = listState.layoutInfo
                val itemInfo = layoutInfo.visibleItemsInfo.firstOrNull { it.index == index }
                val itemCenter = itemInfo?.let { it.offset + it.size / 2f } ?: 0f
                val viewportCenter = (layoutInfo.viewportEndOffset - layoutInfo.viewportStartOffset) / 2f
                val distanceFromCenter = if (itemInfo != null) {
                    (itemCenter - viewportCenter) / itemHeightPx
                } else {
                    (index - currentCenteredIndex).toFloat()
                }

                val absDistance = abs(distanceFromCenter)
                val rotationAngle = (distanceFromCenter * -22f).coerceIn(-65f, 65f)
                val scale = (1f - absDistance * 0.07f).coerceIn(0.72f, 1f)
                val alpha = (1f - absDistance * 0.32f).coerceIn(0.18f, 1f)

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(itemHeight)
                        .graphicsLayer {
                            rotationX = rotationAngle
                            scaleX = scale
                            scaleY = scale
                            this.alpha = alpha
                            cameraDistance = 12f * density.density
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
    }
}
