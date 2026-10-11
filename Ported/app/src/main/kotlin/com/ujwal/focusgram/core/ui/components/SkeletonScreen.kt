package com.ujwal.focusgram.core.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.ujwal.focusgram.core.ui.theme.DarkCard
import com.ujwal.focusgram.core.ui.theme.DarkCardElevated
import com.ujwal.focusgram.core.ui.theme.PureBlack

enum class SkeletonType {
    FEED, REELS, EXPLORE, MESSAGES, PROFILE, GENERIC
}

fun getSkeletonTypeFromUrl(url: String): SkeletonType {
    val lower = url.lowercase()
    return when {
        lower.contains("/reels") || lower.contains("/reel") -> SkeletonType.REELS
        lower.contains("/explore") -> SkeletonType.EXPLORE
        lower.contains("/direct") || lower.contains("/messages") || lower.contains("/inbox") -> SkeletonType.MESSAGES
        lower.contains("/accounts") -> SkeletonType.GENERIC
        else -> SkeletonType.FEED
    }
}

@Composable
fun ShimmerBrush(): Brush {
    val transition = rememberInfiniteTransition(label = "shimmerTransition")
    val translateAnim by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmerTranslate"
    )

    return Brush.linearGradient(
        colors = listOf(
            DarkCard,
            DarkCardElevated,
            DarkCard
        ),
        start = Offset(translateAnim - 500f, translateAnim - 500f),
        end = Offset(translateAnim, translateAnim)
    )
}

/**
 * SkeletonScreen
 * Native OLED shimmer skeleton displayed during Instagram page cold-start.
 */
@Composable
fun SkeletonScreen(
    type: SkeletonType = SkeletonType.FEED,
    modifier: Modifier = Modifier
) {
    val brush = ShimmerBrush()

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(PureBlack)
            .padding(top = 16.dp)
    ) {
        when (type) {
            SkeletonType.FEED -> FeedSkeleton(brush)
            SkeletonType.REELS -> ReelsSkeleton(brush)
            SkeletonType.EXPLORE -> ExploreSkeleton(brush)
            SkeletonType.MESSAGES -> MessagesSkeleton(brush)
            SkeletonType.PROFILE -> ProfileSkeleton(brush)
            SkeletonType.GENERIC -> FeedSkeleton(brush)
        }
    }
}

@Composable
private fun FeedSkeleton(brush: Brush) {
    Column(modifier = Modifier.fillMaxSize()) {
        // Stories Tray Shimmer
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            repeat(5) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(end = 16.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(CircleShape)
                            .background(brush)
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Box(
                        modifier = Modifier
                            .width(36.dp)
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(brush)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Feed Post 1
        PostSkeleton(brush)
    }
}

@Composable
private fun PostSkeleton(brush: Brush) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(brush)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Box(
                modifier = Modifier
                    .width(100.dp)
                    .height(12.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(brush)
            )
        }

        // Post Media Area
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(300.dp)
                .background(brush)
        )

        // Post Actions
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            repeat(3) {
                Box(
                    modifier = Modifier
                        .padding(end = 16.dp)
                        .size(22.dp)
                        .clip(CircleShape)
                        .background(brush)
                )
            }
        }
    }
}

@Composable
private fun ReelsSkeleton(brush: Brush) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .width(160.dp)
                    .height(260.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(brush)
            )
            Spacer(modifier = Modifier.height(20.dp))
            Box(
                modifier = Modifier
                    .width(140.dp)
                    .height(14.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(brush)
            )
        }
    }
}

@Composable
private fun ExploreSkeleton(brush: Brush) {
    Column(modifier = Modifier.fillMaxSize()) {
        repeat(3) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 2.dp)
            ) {
                repeat(3) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(130.dp)
                            .padding(horizontal = 1.dp)
                            .background(brush)
                    )
                }
            }
        }
    }
}

@Composable
private fun MessagesSkeleton(brush: Brush) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        repeat(6) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(CircleShape)
                        .background(brush)
                )
                Spacer(modifier = Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Box(
                        modifier = Modifier
                            .width(120.dp)
                            .height(14.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(brush)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Box(
                        modifier = Modifier
                            .width(180.dp)
                            .height(10.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(brush)
                    )
                }
            }
        }
    }
}

@Composable
private fun ProfileSkeleton(brush: Brush) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(86.dp)
                .clip(CircleShape)
                .background(brush)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Box(
            modifier = Modifier
                .width(140.dp)
                .height(14.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(brush)
        )
        Spacer(modifier = Modifier.height(24.dp))
        ExploreSkeleton(brush)
    }
}
