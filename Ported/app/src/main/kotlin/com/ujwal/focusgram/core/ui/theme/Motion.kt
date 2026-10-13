package com.ujwal.focusgram.core.ui.theme

import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView

/**
 * FocusGram Motion & Spring Physics
 * Tactile spring dynamics tailored for responsive OLED dark mode interactions.
 */
object FocusGramMotion {
    val BouncySpring = spring<Float>(
        dampingRatio = Spring.DampingRatioMediumBouncy,
        stiffness = Spring.StiffnessLow
    )

    val SnappySpring = spring<Float>(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessMedium
    )

    val GentleSpring = spring<Float>(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessLow
    )
}

/**
 * Modifier to apply a tactile spring bounce and subtle scale on press
 * accompanied by haptic feedback.
 */
fun Modifier.tactilePress(
    targetScale: Float = 0.96f,
    onClick: () -> Unit
): Modifier = composed {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val view = LocalView.current

    val scale by animateFloatAsState(
        targetValue = if (isPressed) targetScale else 1f,
        animationSpec = FocusGramMotion.SnappySpring,
        label = "tactilePressScale"
    )

    this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .clickable(
            interactionSource = interactionSource,
            indication = null,
            onClick = {
                view.performHapticFeedback(
                    HapticFeedbackConstants.KEYBOARD_TAP
                )
                onClick()
            }
        )
}

/**
 * Modifier to detect long press (600ms) and trigger onLongPress callback.
 * Provides haptic feedback on trigger.
 * NOTE: Use tactilePressWithLongPress for combined short tap + long press handling.
 */
fun Modifier.longPress(
    durationMillis: Long = 600L,
    onLongPress: () -> Unit
): Modifier = composed {
    val view = LocalView.current

    this.pointerInput(Unit) {
        detectTapGestures(
            onLongPress = {
                view.performHapticFeedback(
                    HapticFeedbackConstants.LONG_PRESS
                )
                onLongPress()
            }
        )
    }
}

/**
 * Combined modifier for buttons that need both short-tap and long-press handling.
 * Properly chains gesture detection to avoid event consumption conflicts.
 */
fun Modifier.tactilePressWithLongPress(
    targetScale: Float = 0.96f,
    onClick: () -> Unit,
    onLongPress: (() -> Unit)? = null
): Modifier = composed {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val view = LocalView.current

    val scale by animateFloatAsState(
        targetValue = if (isPressed) targetScale else 1f,
        animationSpec = FocusGramMotion.SnappySpring,
        label = "tactilePressScale"
    )

    this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .pointerInput(Unit) {
            detectTapGestures(
                onPress = {
                    val pressInteraction = PressInteraction.Press(it)
                    interactionSource.emit(pressInteraction)
                    tryAwaitRelease()
                    interactionSource.emit(
                        PressInteraction.Release(pressInteraction)
                    )
                },
                onTap = {
                    view.performHapticFeedback(
                        HapticFeedbackConstants.KEYBOARD_TAP
                    )
                    onClick()
                },
                onLongPress = {
                    view.performHapticFeedback(
                        HapticFeedbackConstants.LONG_PRESS
                    )
                    onLongPress?.invoke()
                }
            )
        }
}