package com.ujwal.focusgram.feature.history

import org.json.JSONObject
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.WebExtension

/**
 * Bridges WebExtension messages from content scripts into [ReelHistory].
 */
class ReelMessageDelegate(private val history: ReelHistory) : WebExtension.MessageDelegate {
    // Instagram shortcode regex: alphanumeric with underscores, dashes, dots, at least 3 chars
    private val codeRe = Regex("^[A-Za-z0-9_.-]{3,}$")

    override fun onConnect(port: WebExtension.Port) {
        port.setDelegate(object : WebExtension.PortDelegate {
            override fun onPortMessage(message: Any, p: WebExtension.Port) {
                handlePayload(message)
            }
            override fun onDisconnect(p: WebExtension.Port) {}
        })
    }

    override fun onMessage(
        nativeApp: String,
        message: Any,
        sender: WebExtension.MessageSender,
    ): GeckoResult<Any>? {
        val count = handlePayload(message) ?: return null
        return GeckoResult.fromValue(JSONObject().put("count", count))
    }

    private fun handlePayload(message: Any): Int? {
        val m = when (message) {
            is JSONObject -> message
            is String -> try { JSONObject(message) } catch (_: Exception) { null }
            else -> null
        } ?: return null

        val code = m.optString("code")
        if (!codeRe.matches(code)) return null
        val author = m.optString("author")
        val caption = m.optString("caption")

        return when (m.optString("type")) {
            "watched" -> history.onWatched(code, author, caption)
            "meta" -> {
                history.onMeta(code, author, caption)
                history.sessionCount.value
            }
            else -> null
        }
    }
}
