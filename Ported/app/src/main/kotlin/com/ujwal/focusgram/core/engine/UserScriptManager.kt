package com.ujwal.focusgram.core.engine

import android.content.Context
import android.util.Log
import com.ujwal.focusgram.core.model.FocusSettings
import org.mozilla.geckoview.GeckoSession

/**
 * UserScriptManager
 * Loads, configures, and evaluates FocusGram client-side scripts inside GeckoSession,
 * including stealth privacy hooks, ad cosmetic filters, and UI guardrails.
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
                    hideFeedPosts: ${settings.hideFeedPosts},
                    blurExplore: ${settings.blurExplore},
                    blurReels: ${settings.blurReels},
                    blurFeed: ${settings.blurFeed},
                    unblurMode: '${settings.unblurMode}',
                    tapToUnblur: ${settings.tapToUnblur},
                    requireLongPress: ${settings.requireLongPress},
                    hideLikeCounts: ${settings.hideLikeCounts},
                    hideFollowerCounts: ${settings.hideFollowerCounts},
                    hideSuggestedPosts: ${settings.hideSuggestedPosts},
                    hideShopTab: ${settings.hideShopTab},
                    blockAutoplay: ${settings.noAutoplay},
                    grayscale: ${settings.grayscaleEnabled},
                    minimalMode: ${settings.minimalModeEnabled}
                };
                window.__FOCUSGRAM_GHOST_CONFIG__ = {
                    enabled: ${settings.ghostModeEnabled},
                    igStory: ${settings.ghostHideStorySeen},
                    igSeen: ${settings.ghostHideDirectSeen},
                    igTyping: ${settings.ghostHideTypingIndicator}
                };
                if (window.__focusgramUpdateStyles) {
                    window.__focusgramUpdateStyles();
                }
                if (window.__focusgramGhostSettings) {
                    window.__focusgramGhostSettings.enabled = ${settings.ghostModeEnabled};
                    window.__focusgramGhostSettings.hideStorySeen = ${settings.ghostHideStorySeen};
                    window.__focusgramGhostSettings.hideDirectSeen = ${settings.ghostHideDirectSeen};
                    window.__focusgramGhostSettings.hideTyping = ${settings.ghostHideTypingIndicator};
                }
                if (window.__fgSetGrayscale) {
                    window.__fgSetGrayscale(${settings.grayscaleEnabled});
                }
                document.documentElement.setAttribute('data-fg-block-dm-reels', '${settings.blockDmReelsSwiping}');
                window.postMessage({
                    source: "GHOSTIFY_EXTENSION",
                    type: "GHOSTIFY_SETTINGS_UPDATE",
                    settings: {
                        igTyping: ${settings.ghostModeEnabled && settings.ghostHideTypingIndicator},
                        igSeen: ${settings.ghostModeEnabled && settings.ghostHideDirectSeen},
                        igStory: ${settings.ghostModeEnabled && settings.ghostHideStorySeen}
                    }
                }, "*");
                window.postMessage({
                    source: "FOCUSGRAM_HOST",
                    type: "FOCUSGRAM_UPDATE",
                    config: window.__FOCUSGRAM_CONFIG__,
                    ghost: window.__FOCUSGRAM_GHOST_CONFIG__
                }, "*");
            })();
        """.trimIndent()
        evaluate(session, configJs)
    }

    /**
     * Evaluates a UserScript inside the active GeckoSession document.
     * Safely URL-encodes the script body to prevent Gecko's C++ parser from
     * rejecting scripts containing newlines or control characters.
     */
    fun evaluate(session: GeckoSession, script: String) {
        if (script.isBlank()) return
        try {
            val wrapped = "(function(){ try {\n$script\n} catch(e) { console.error('[UserScriptManager] Execution error:', e); } })();"
            val encoded = "javascript:" + java.net.URLEncoder.encode(wrapped, "UTF-8").replace("+", "%20")
            session.loadUri(encoded)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to evaluate UserScript: ${e.message}", e)
        }
    }

    fun injectAll(session: GeckoSession, settings: FocusSettings) {
        val bundle = StringBuilder()
        bundle.append(loadScript("content_hider.js")).append("\n")
        bundle.append(loadScript("autoplay_blocker.js")).append("\n")
        bundle.append(loadScript("grayscale.js")).append("\n")
        bundle.append(loadScript("native_feel.js")).append("\n")
        evaluate(session, bundle.toString())
        injectConfig(session, settings)
    }

    companion object {
        private const val TAG = "UserScriptManager"
    }
}
