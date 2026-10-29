package com.ujwal.focusgram.feature.wellness

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.ujwal.focusgram.core.ui.theme.PureBlack
import kotlinx.coroutines.delay

private val ADSTERRA_INTERNAL_DOMAINS = listOf(
    "effectivecpmnetwork.com",
    "highperformanceformat.com",
    "effectivegatecpm.com",
    "profitableratecpm.com",
    "profitablecpmratenetwork.com",
    "profitableratecpmnetwork.com",
    "aqml.org",
    "afders.org",
    "bicea.org",
    "arwf.org",
    "cdn.show-sb.com",
    "api3.adsterratools.com",
    "adsterra.com"
)

private fun isAdsterraInternalDomain(url: String): Boolean {
    return ADSTERRA_INTERNAL_DOMAINS.any { url.contains(it, ignoreCase = true) }
}

private const val AD_HTML = """
<!DOCTYPE html>
<html>
<head>
  <meta name="viewport" content="width=device-width,initial-scale=1.0,maximum-scale=1.0,user-scalable=no">
  <style>
    * { margin:0; padding:0; box-sizing:border-box; }
    html,body {
      width:100%;
      min-height:100%;
      background:#111111;
      display:flex;
      flex-direction:column;
      align-items:center;
      justify-content:center;
      gap:16px;
      padding:12px 0;
      overflow-x:hidden;
      overflow-y:auto;
    }
    .ad-card {
      width:320px;
      min-height:270px;
      background:#18181A;
      border:1px solid #2C2C2E;
      border-radius:12px;
      display:flex;
      flex-direction:column;
      align-items:center;
      justify-content:center;
      padding:8px 0;
      box-shadow: 0 4px 12px rgba(0,0,0,0.5);
    }
    .ad-title {
      font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif;
      font-size:11px;
      color:#8E8E93;
      text-transform:uppercase;
      letter-spacing:0.8px;
      margin-bottom:8px;
    }
    .ad-slot {
      width:300px;
      min-height:250px;
      display:flex;
      align-items:center;
      justify-content:center;
      background:#18181A;
      overflow:hidden;
    }
  </style>
</head>
<body>
  <div class="ad-card">
    <div class="ad-title">Sponsored Ad 1</div>
    <div class="ad-slot">
      <div id="container-e8a9b107824c939fb63d96c218c1336a"></div>
      <script async="async" data-cfasync="false" src="https://pl18364273.effectivecpmnetwork.com/e8a9b107824c939fb63d96c218c1336a/invoke.js"></script>
    </div>
  </div>

  <div class="ad-card">
    <div class="ad-title">Sponsored Ad 2</div>
    <div class="ad-slot">
      <script type="text/javascript">
        atOptions = {
          'key' : '99233324430f9128f2b01c30b6eebc20',
          'format' : 'iframe',
          'height' : 250,
          'width' : 300,
          'params' : {}
        };
      </script>
      <script type="text/javascript" src="https://www.highperformanceformat.com/99233324430f9128f2b01c30b6eebc20/invoke.js"></script>
    </div>
  </div>
</body>
</html>
"""

/**
 * Isolated WebView container for opt-in rewarded ad viewing.
 *
 * Runs inside standard Android system WebView completely decoupled from GeckoView
 * to preserve uBlock Origin blocking in Instagram browsing sessions.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun AdsterraAdScreen(
    sessionType: String = "Instagram",
    requiredSeconds: Int = 20,
    onRewardClaimed: () -> Unit,
    onCancel: () -> Unit
) {
    var timerCycle by rememberSaveable { mutableIntStateOf(0) }
    var elapsedSeconds by rememberSaveable { mutableIntStateOf(0) }
    var adsClicked by rememberSaveable { mutableIntStateOf(0) }
    var lastClickedUrl by rememberSaveable { mutableStateOf("") }
    var lastClickTime by rememberSaveable { mutableStateOf(0L) }
    var webViewRef by remember { mutableStateOf<WebView?>(null) }

    val isSettingToggle = sessionType == "setting_toggle"
    val isVoluntarySupport = sessionType == "voluntary"
    val isEffortFriction = sessionType == "effort_friction"
    val isSessionExtend = sessionType == "session_extend"

    val timerDone = elapsedSeconds >= requiredSeconds
    val bothClicked = adsClicked >= 2
    val isComplete = if (isSettingToggle || isVoluntarySupport || isEffortFriction) timerDone else (timerDone && bothClicked)

    // Ensure clean countdown and state when entering a setting toggle, voluntary support, or friction session
    LaunchedEffect(sessionType) {
        if (isSettingToggle || isVoluntarySupport || isEffortFriction) {
            elapsedSeconds = 0
            adsClicked = 0
            timerCycle++
        }
    }

    // Preserves elapsed countdown when re-entering the screen
    LaunchedEffect(timerCycle) {
        while (elapsedSeconds < requiredSeconds) {
            delay(1000L)
            elapsedSeconds++
        }
    }

    val statusText = when {
        isComplete -> when {
            isVoluntarySupport -> "Thank you for supporting FocusGram! Your contribution keeps this project free."
            isSettingToggle -> "Ad viewed! Tap below to apply your setting."
            isEffortFriction -> "Ad viewed! Tap below to proceed to Instagram."
            else -> "Reward Unlocked! Tap below to extend session by 5 minutes."
        }
        isSettingToggle || isVoluntarySupport || isEffortFriction -> "Viewing ad (${requiredSeconds - elapsedSeconds}s remaining)..."
        bothClicked -> "Both ads clicked! Waiting for countdown (${requiredSeconds - elapsedSeconds}s)..."
        timerDone -> "Timer completed. Please tap both ads ($adsClicked/2) to continue."
        else -> "Tap BOTH ads below to unlock your reward ($adsClicked/2 clicked)"
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = PureBlack
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onCancel,
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF1C1C1E))
                        .border(0.5.dp, Color(0xFF2C2C2E), CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = when {
                            isVoluntarySupport -> "Support FocusGram"
                            isSettingToggle -> "Unlock Setting"
                            isEffortFriction -> "Focus Bypass"
                            else -> "Extend Session"
                        },
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = when {
                            isVoluntarySupport -> "Voluntary Contribution"
                            isSettingToggle -> "Sponsored Verification"
                            isEffortFriction -> "Sponsored Access"
                            else -> "Sponsored Reward"
                        },
                        color = Color(0xFF8E8E93),
                        fontSize = 12.sp
                    )
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF1C1C1E))
                        .border(0.5.dp, Color(0xFF2C2C2E), RoundedCornerShape(12.dp))
                        .padding(horizontal = 10.dp, vertical = 5.dp)
                ) {
                    Text(
                        text = "${elapsedSeconds.coerceAtMost(requiredSeconds)}s / ${requiredSeconds}s",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = if (isComplete) Color.White else Color(0xFF8E8E93)
                    )
                }
            }

            LinearProgressIndicator(
                progress = { (elapsedSeconds.toFloat() / requiredSeconds).coerceIn(0f, 1f) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp),
                color = Color.White,
                trackColor = Color(0xFF2C2C2E)
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Color(0xFF1C1C1E))
                .border(0.5.dp, Color(0xFF2C2C2E), RoundedCornerShape(10.dp))
                .padding(horizontal = 14.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = statusText,
                    fontSize = 12.sp,
                    color = if (isComplete) Color.White else Color(0xFF8E8E93),
                    fontWeight = FontWeight.Medium
                )
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .border(0.5.dp, Color(0xFF2C2C2E), RoundedCornerShape(12.dp))
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
                                loadWithOverviewMode = true
                                useWideViewPort = true
                                mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                                mediaPlaybackRequiresUserGesture = false
                                cacheMode = WebSettings.LOAD_DEFAULT
                                javaScriptCanOpenWindowsAutomatically = true
                                setSupportMultipleWindows(true)
                                val defaultUa = userAgentString
                                if (defaultUa.contains("; wv")) {
                                    userAgentString = defaultUa.replace("; wv", "")
                                }
                            }
                            setBackgroundColor(0xFF111111.toInt())

                            fun handleAdClick(url: String): Boolean {
                                val clean = url.trim()
                                if (clean.isNotBlank() && !isAdsterraInternalDomain(clean) && !clean.startsWith("about:") && !clean.startsWith("data:")) {
                                    val now = System.currentTimeMillis()
                                    if (clean != lastClickedUrl || now - lastClickTime > 1500L) {
                                        lastClickedUrl = clean
                                        lastClickTime = now
                                        if (adsClicked < 2) {
                                            adsClicked++
                                        }
                                    }
                                    try {
                                        val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(clean)).apply {
                                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                        }
                                        ctx.startActivity(browserIntent)
                                    } catch (_: Exception) {}
                                    return true
                                }
                                return false
                            }

                            webViewClient = object : WebViewClient() {
                                override fun shouldOverrideUrlLoading(
                                    view: WebView?,
                                    request: WebResourceRequest?
                                ): Boolean {
                                    val url = request?.url?.toString() ?: ""
                                    return handleAdClick(url)
                                }

                                @Deprecated("Deprecated in Java")
                                override fun shouldOverrideUrlLoading(
                                    view: WebView?,
                                    url: String?
                                ): Boolean {
                                    return handleAdClick(url ?: "")
                                }
                            }

                            webChromeClient = object : WebChromeClient() {
                                override fun onCreateWindow(
                                    view: WebView?,
                                    isDialog: Boolean,
                                    isUserGesture: Boolean,
                                    resultMsg: Message?
                                ): Boolean {
                                    val transport = resultMsg?.obj as? WebView.WebViewTransport
                                    val tempWebView = WebView(ctx).apply {
                                        settings.javaScriptEnabled = true
                                        settings.domStorageEnabled = true
                                        settings.setSupportMultipleWindows(true)
                                        webViewClient = object : WebViewClient() {
                                            override fun shouldOverrideUrlLoading(
                                                view: WebView?,
                                                request: WebResourceRequest?
                                            ): Boolean {
                                                val url = request?.url?.toString() ?: ""
                                                return handleAdClick(url)
                                            }

                                            @Deprecated("Deprecated in Java")
                                            override fun shouldOverrideUrlLoading(
                                                view: WebView?,
                                                url: String?
                                            ): Boolean {
                                                return handleAdClick(url ?: "")
                                            }

                                            override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                                                super.onPageStarted(view, url, favicon)
                                                val u = url ?: ""
                                                if (u.isNotBlank() && !u.startsWith("about:") && !u.startsWith("data:")) {
                                                    if (!isAdsterraInternalDomain(u)) {
                                                        handleAdClick(u)
                                                    }
                                                }
                                            }
                                        }
                                    }
                                    transport?.webView = tempWebView
                                    resultMsg?.sendToTarget()
                                    return true
                                }
                            }

                            loadDataWithBaseURL("https://effectivecpmnetwork.com", AD_HTML, "text/html", "UTF-8", null)
                            webViewRef = this
                        }
                    }
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = {
                        elapsedSeconds = 0
                        adsClicked = 0
                        onRewardClaimed()
                    },
                    enabled = isComplete,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = RoundedCornerShape(26.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.White,
                        contentColor = Color.Black,
                        disabledContainerColor = Color(0xFF1C1C1E),
                        disabledContentColor = Color(0xFF636366)
                    )
                ) {
                    Icon(
                        imageVector = if (isComplete) Icons.Default.CheckCircle else Icons.Default.Timer,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    val buttonLabel = when {
                        !isComplete -> if (isSettingToggle || isVoluntarySupport || isEffortFriction) {
                            "Viewing Ad (${(requiredSeconds - elapsedSeconds).coerceAtLeast(0)}s)"
                        } else {
                            "Complete Ad Actions to Unlock"
                        }
                        isVoluntarySupport -> "Done (Return to Settings)"
                        isSettingToggle -> "Continue & Apply Setting"
                        isEffortFriction -> "Continue to Instagram"
                        else -> "Continue & Extend Session (+5 min)"
                    }
                    Text(
                        text = buttonLabel,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                }

                if (!isComplete && timerDone && !bothClicked) {
                    Button(
                        onClick = {
                            timerCycle++
                            elapsedSeconds = 0
                            adsClicked = 0
                            lastClickedUrl = ""
                            lastClickTime = 0L
                            webViewRef?.loadDataWithBaseURL("https://effectivecpmnetwork.com", AD_HTML, "text/html", "UTF-8", null)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp),
                        shape = RoundedCornerShape(22.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF1C1C1E),
                            contentColor = Color.White
                        ),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF2C2C2E))
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Reload Ads & Retry",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }
    }
}

/**
 * Pre-warms the Android system WebView and Chromium V8 runtime in the background using about:blank.
 *
 * Runs isolated from GeckoView to avoid cold-start latency when opening [AdsterraAdScreen],
 * without touching Instagram browsing sessions or uBlock Origin state.
 */
object AdPreloader {
    @Volatile
    private var isPrewarmed = false

    fun prewarm(context: Context) {
        if (isPrewarmed) return
        try {
            isPrewarmed = true
            Handler(Looper.getMainLooper()).post {
                try {
                    val prewarmView = WebView(context.applicationContext)
                    prewarmView.settings.apply {
                        javaScriptEnabled = true
                        domStorageEnabled = true
                    }
                    prewarmView.loadUrl("about:blank")
                } catch (_: Exception) {}
            }
        } catch (_: Exception) {}
    }
}

