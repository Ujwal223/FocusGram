package com.ujwal.focusgram.core.engine

import android.content.Context
import android.util.Log
import com.ujwal.focusgram.core.model.FocusSettings
import org.mozilla.geckoview.GeckoSession

/**
 * UserScriptManager
 * Loads, configures, and evaluates FocusGram client-side UserScripts inside GeckoSession,
 * including Ghostify privacy hooks, ad cosmetic filters, and UI guardrails.
 */
class UserScriptManager(private val context: Context) {

    private val scriptCache = mutableMapOf<String, String>()

    fun loadScript(scriptName: String): String {
        return scriptCache.getOrPut(scriptName) {
            try {
                context.assets.open("scripts/$scriptName").bufferedReader().use { it.readText() }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load UserScript from assets: $scriptName", e)
                ""
            }
        }
    }

    /**
     * Injects the dynamic FocusSettings JSON config into the web execution environment
     * and broadcasts updates to active scripts.
     */
    fun injectConfig(session: GeckoSession, settings: FocusSettings) {
        val configJs = """
            (function() {
                window.__FOCUSGRAM_CONFIG__ = {
                    noAds: ${settings.noAds},
                    hideStories: ${settings.noStories},
                    hideReels: ${settings.noReels},
                    blurExplore: ${settings.blurExplore},
                    hideLikeCounts: ${settings.hideLikeCounts},
                    hideFollowerCounts: ${settings.hideFollowerCounts},
                    hideSuggestedPosts: ${settings.hideSuggestedPosts},
                    hideShopTab: ${settings.hideShopTab},
                    blockAutoplay: ${settings.noAutoplay},
                    grayscale: ${settings.grayscaleEnabled}
                };
                window.__GHOSTIFY_CONFIG__ = {
                    enabled: ${settings.ghostModeEnabled},
                    igStory: ${settings.ghostHideStorySeen},
                    igSeen: ${settings.ghostHideDirectSeen},
                    igTyping: ${settings.ghostHideTypingIndicator}
                };
                if (window.__focusgramUpdateStyles) {
                    window.__focusgramUpdateStyles();
                }
                window.postMessage({
                    source: "FOCUSGRAM_HOST",
                    type: "GHOSTIFY_UPDATE",
                    config: window.__GHOSTIFY_CONFIG__
                }, "*");
            })();
        """.trimIndent()
        evaluate(session, configJs)
    }

    /**
     * Evaluates a UserScript inside the active GeckoSession document.
     */
    fun evaluate(session: GeckoSession, script: String) {
        if (script.isBlank()) return
        val wrapped = "javascript:(function(){ try { $script } catch(e) { console.error('[UserScriptManager] Execution error:', e); } })();"
        session.loadUri(wrapped)
    }

    fun injectAll(session: GeckoSession, settings: FocusSettings) {
        evaluate(session, loadScript("ghostify_instagram.js"))
        evaluate(session, loadScript("content_hider.js"))
        evaluate(session, loadScript("autoplay_blocker.js"))
        evaluate(session, loadScript("grayscale.js"))
        evaluate(session, loadScript("spa_navigation_monitor.js"))
        evaluate(session, loadScript("native_feel.js"))
        evaluate(session, loadScript("reel_metadata_extractor.js"))
        injectConfig(session, settings)
    }

    companion object {
        private const val TAG = "UserScriptManager"
    }
}
