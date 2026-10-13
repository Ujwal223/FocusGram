package com.ujwal.focusgram.feature.webview

import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.ujwal.focusgram.core.engine.GeckoSessionManager
import com.ujwal.focusgram.core.ui.components.SkeletonScreen
import com.ujwal.focusgram.core.ui.components.getSkeletonTypeFromUrl
import com.ujwal.focusgram.core.ui.theme.DarkCardElevated
import com.ujwal.focusgram.core.ui.theme.InstagramBlue
import com.ujwal.focusgram.core.ui.theme.PureBlack
import org.mozilla.geckoview.GeckoView

/**
 * GeckoViewContainer
 * Jetpack Compose host for Mozilla GeckoView.
 * Provides edge-to-edge window insets, top progress indicator, and OLED skeleton loader.
 */
@Composable
fun GeckoViewContainer(
    sessionManager: GeckoSessionManager,
    grayscale: Boolean = false,
    modifier: Modifier = Modifier
) {
    val isLoading by sessionManager.isLoading.collectAsState()
    val progress by sessionManager.progress.collectAsState()
    val currentUrl by sessionManager.currentUrl.collectAsState()

    var initialLoadCompleted by remember { mutableStateOf(false) }

    if (!isLoading && progress >= 100) {
        initialLoadCompleted = true
    }

    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner, sessionManager) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            when (event) {
                androidx.lifecycle.Lifecycle.Event.ON_STOP -> sessionManager.onPause()
                androidx.lifecycle.Lifecycle.Event.ON_START -> sessionManager.onResume()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(PureBlack)
            .padding(WindowInsets.ime.asPaddingValues())
    ) {
        // Native GeckoView host
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { context ->
                GeckoView(context).apply {
                    layoutParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    setSession(sessionManager.session)
                }
            },
            update = { geckoView ->
                if (geckoView.session != sessionManager.session) {
                    geckoView.setSession(sessionManager.session)
                }
            },
            onRelease = { geckoView ->
                geckoView.releaseSession()
            }
        )

        // Initial skeleton shimmer screen during cold start
        AnimatedVisibility(
            visible = !initialLoadCompleted && isLoading,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            SkeletonScreen(type = getSkeletonTypeFromUrl(currentUrl))
        }

        // Top progress indicator for incremental page navigation
        AnimatedVisibility(
            visible = isLoading,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(WindowInsets.statusBars.asPaddingValues())
        ) {
            LinearProgressIndicator(
                progress = { progress / 100f },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.5.dp),
                color = InstagramBlue,
                trackColor = DarkCardElevated,
                strokeCap = StrokeCap.Round
            )
        }
    }
}
