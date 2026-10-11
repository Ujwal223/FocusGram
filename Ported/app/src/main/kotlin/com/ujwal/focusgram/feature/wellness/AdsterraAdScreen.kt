package com.ujwal.focusgram.feature.wellness

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.view.ViewGroup
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.ujwal.focusgram.core.ui.theme.FocusGreen
import com.ujwal.focusgram.core.ui.theme.PureBlack
import com.ujwal.focusgram.core.ui.theme.TextMuted
import com.ujwal.focusgram.core.ui.theme.TextSecondary
import kotlinx.coroutines.delay

private const val AD_HTML = """
<!DOCTYPE html>
<html>
<head>
  <meta name="viewport" content="width=device-width,initial-scale=1.0,maximum-scale=1.0,user-scalable=no">
  <style>
    * { margin:0; padding:0; box-sizing:border-box; }
    html,body { width:100%; height:100%; background:#111; display:flex; flex-direction:column; align-items:center; justify-content:space-around; }
    .ad-slot { width:100%; text-align:center; }
  </style>
</head>
<body>
  <div class="ad-slot">
    <div style="color:#666;font-size:10px;margin-bottom:4px;">Ad 1</div>
    <script async="async" data-cfasync="false" src="https://pl18364273.effectivecpmnetwork.com/e8a9b107824c939fb63d96c218c1336a/invoke.js"></script>
    <div id="container-e8a9b107824c939fb63d96c218c1336a"></div>
  </div>
  <div class="ad-slot">
    <div style="color:#666;font-size:10px;margin-bottom:4px;">Ad 2</div>
    <script>
      atOptions = {'key':'99233324430f9128f2b01c30b6eebc20','format':'iframe','height':250,'width':300,'params':{}};
    </script>
    <script src="https://www.highperformanceformat.com/99233324430f9128f2b01c30b6eebc20/invoke.js"></script>
  </div>
</body>
</html>
"""

/**
 * AdsterraAdScreen
 * Isolated rewarded ad viewing container running outside uBlock Origin.
 * Enforces a 20s countdown timer and 2 external browser ad clicks to claim focus minutes.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun AdsterraAdScreen(
    sessionType: String = "Instagram",
    requiredSeconds: Int = 20,
    onRewardClaimed: () -> Unit,
    onCancel: () -> Unit
) {
    var elapsedSeconds by remember { mutableIntStateOf(0) }
    var adsClicked by remember { mutableIntStateOf(0) }
    var webViewRef by remember { mutableStateOf<WebView?>(null) }

    val timerDone = elapsedSeconds >= requiredSeconds
    val bothClicked = adsClicked >= 2
    val isComplete = timerDone && bothClicked

    // 20s Timer Loop
    LaunchedEffect(Unit) {
        while (elapsedSeconds < requiredSeconds) {
            delay(1000L)
            elapsedSeconds++
        }
    }

    val statusText = when {
        isComplete -> "Reward Unlocked! Tap below to continue."
        bothClicked -> "Both ads clicked! Waiting for timer (${requiredSeconds - elapsedSeconds}s)..."
        timerDone -> "Timer done! Please click both ads ($adsClicked/2) to continue."
        else -> "Tap BOTH ads below to earn reward ($adsClicked/2 clicked)"
    }

    val statusColor = if (bothClicked) FocusGreen else TextSecondary

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = PureBlack
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
        ) {
            // Header Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Videocam,
                    contentDescription = null,
                    tint = TextMuted,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = " Sponsored Reward",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = "${elapsedSeconds.coerceAtMost(requiredSeconds)}s / ${requiredSeconds}s",
                    style = MaterialTheme.typography.labelLarge,
                    color = if (isComplete) FocusGreen else TextSecondary
                )
                IconButton(onClick = onCancel) {
                    Text("✕", color = Color.White, fontSize = 16.sp)
                }
            }

            // Progress Bar
            LinearProgressIndicator(
                progress = { (elapsedSeconds.toFloat() / requiredSeconds).coerceIn(0f, 1f) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp),
                color = if (isComplete) FocusGreen else Color(0xFF3797EF),
                trackColor = Color(0xFF222222)
            )

            // Hint Text
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = statusText,
                    fontSize = 12.sp,
                    color = statusColor,
                    fontWeight = FontWeight.Medium
                )
            }

            // Isolated Ad WebView Host (Strictly bypasses uBlock Origin for Adsterra)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .background(Color(0xFF111111))
            ) {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { ctx ->
                        WebView(ctx).apply {
                            layoutParams = ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT
                            )
                            settings.apply {
                                javaScriptEnabled = true
                                domStorageEnabled = true
                                databaseEnabled = true
                                loadWithOverviewMode = true
                                useWideViewPort = true
                            }
                            setBackgroundColor(0xFF111111.toInt())

                            webViewClient = object : WebViewClient() {
                                override fun shouldOverrideUrlLoading(
                                    view: WebView?,
                                    request: WebResourceRequest?
                                ): Boolean {
                                    val url = request?.url?.toString() ?: ""
                                    if (url.isNotBlank() && !url.contains("adsterra.com") && !url.startsWith("about:")) {
                                        if (adsClicked < 2) {
                                            adsClicked++
                                        }
                                        try {
                                            val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                            }
                                            ctx.startActivity(browserIntent)
                                        } catch (_: Exception) {}
                                        return true
                                    }
                                    return false
                                }
                            }

                            loadDataWithBaseURL("https://adsterra.com", AD_HTML, "text/html", "UTF-8", null)
                            webViewRef = this
                        }
                    }
                )
            }

            // Bottom Action Area
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = onRewardClaimed,
                    enabled = isComplete,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = FocusGreen,
                        contentColor = PureBlack,
                        disabledContainerColor = Color.White.copy(alpha = 0.12f),
                        disabledContentColor = Color.White.copy(alpha = 0.3f)
                    )
                ) {
                    Icon(
                        imageVector = if (isComplete) Icons.Default.CheckCircle else Icons.Default.Timer,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = if (isComplete) " Continue & Earn Reward (+2 min)" else " Complete Ad Actions to Unlock",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                }

                // Retry Button if ads fail to load
                if (timerDone && !bothClicked) {
                    Button(
                        onClick = {
                            elapsedSeconds = 0
                            adsClicked = 0
                            webViewRef?.loadDataWithBaseURL("https://adsterra.com", AD_HTML, "text/html", "UTF-8", null)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF262626),
                            contentColor = Color.White
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = " Reload Ads & Retry",
                            fontSize = 13.sp
                        )
                    }
                }
            }
        }
    }
}
