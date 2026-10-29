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
 * Spring animation specs for touch press and release states.
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
 * Animates a scale-down press state and triggers a keyboard tap haptic on click.
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
 * Detects a sustained press and triggers haptic feedback on recognition.
 * Prefer [tactilePressWithLongPress] if the element also handles short taps.
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
 * Handles concurrent tap and long-press gestures without pointer event collisions.
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