package com.ujwal.focusgram.core.engine

import android.content.Context
import android.util.Log
import com.ujwal.focusgram.core.model.FocusSettings
import org.mozilla.geckoview.GeckoSession

/**
 * Reads asset scripts and evaluates them in the running GeckoSession document context.
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
     * Serializes [FocusSettings] and chat lock state to `window.__FOCUSGRAM_CONFIG__`
     * and dispatches a DOM custom event for active script listeners.
     */
    fun injectConfig(session: GeckoSession, settings: FocusSettings) {
        val lockedIdsJson = org.json.JSONArray(settings.lockedChatIds.map { it.lowercase().trim().removePrefix("@") }).toString()
        val lockedUsersJson = org.json.JSONObject(settings.lockedChatUsernames).toString()
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
                    minimalMode: ${settings.minimalModeEnabled},
                    videoDownloadEnabled: ${settings.videoDownloadEnabled},
                    portraitOnly: ${settings.portraitOnly},
                    storySearchEnabled: ${settings.storySearchEnabled},
                    customBioFontsEnabled: ${settings.customBioFontsEnabled}
                };
                window.__FOCUSGRAM_GHOST_CONFIG__ = {
                    enabled: ${settings.ghostModeEnabled},
                    hideStorySeen: ${settings.ghostHideStorySeen},
                    hideDirectSeen: ${settings.ghostHideDirectSeen},
                    hideTyping: ${settings.ghostHideTypingIndicator},
                    igStory: ${settings.ghostHideStorySeen},
                    igSeen: ${settings.ghostHideDirectSeen},
                    igTyping: ${settings.ghostHideTypingIndicator}
                };
                window.__fgChatLockMode = '${settings.chatLockMode}';
                window.__fgChatLockAll = ${settings.chatLockAll || settings.chatLockMode == "whole_dm"};
                window.__fgLockedChatIds = $lockedIdsJson;
                window.__fgLockedChatUsernames = $lockedUsersJson;
                window.__fgThreadUsernameMap = $lockedUsersJson;

                const syncDetail = {
                    chatLockMode: '${settings.chatLockMode}',
                    chatLockAll: ${settings.chatLockAll || settings.chatLockMode == "whole_dm"},
                    lockedChatIds: $lockedIdsJson,
                    lockedChatUsernames: $lockedUsersJson,
                    threadUsernameMap: $lockedUsersJson
                };
                try {
                    window.dispatchEvent(new CustomEvent('focusgram:chat-lock-sync', { detail: syncDetail }));
                    document.dispatchEvent(new CustomEvent('focusgram:chat-lock-sync', { detail: syncDetail }));
                } catch(_) {}

                if (window.__focusgramUpdateStyles) {
                    window.__focusgramUpdateStyles();
                }
                if (window.__focusgramGhostSettings) {
                    window.__focusgramGhostSettings.enabled = ${settings.ghostModeEnabled};
                    window.__focusgramGhostSettings.hideStorySeen = ${settings.ghostHideStorySeen};
                    window.__focusgramGhostSettings.hideDirectSeen = ${settings.ghostHideDirectSeen};
                    window.__focusgramGhostSettings.hideTyping = ${settings.ghostHideTypingIndicator};
                    window.__focusgramGhostSettings.igStory = ${settings.ghostHideStorySeen};
                    window.__focusgramGhostSettings.igSeen = ${settings.ghostHideDirectSeen};
                    window.__focusgramGhostSettings.igTyping = ${settings.ghostHideTypingIndicator};
                }
                if (window.__fgSetGrayscale) {
                    window.__fgSetGrayscale(${settings.grayscaleEnabled});
                }
                const filterUpdatePayload = {
                    blockAds: ${settings.noAds},
                    blockSponsored: ${settings.noAds},
                    blockSuggested: ${settings.hideSuggestedPosts},
                    storySearchEnabled: ${settings.storySearchEnabled},
                    customBioFontsEnabled: ${settings.customBioFontsEnabled}
                };
                if (window.__fgSetFilterConfig) {
                    window.__fgSetFilterConfig(filterUpdatePayload);
                }
                try {
                    window.dispatchEvent(new CustomEvent('focusgram:filter-config-update', { detail: filterUpdatePayload }));
                    document.dispatchEvent(new CustomEvent('focusgram:filter-config-update', { detail: filterUpdatePayload }));
                } catch(_) {}
                document.documentElement.setAttribute('data-fg-block-dm-reels', '${settings.blockDmReelsSwiping}');
                window.postMessage({
                    source: "GHOSTIFY_EXTENSION",
                    type: "GHOSTIFY_SETTINGS_UPDATE",
                    settings: {
                        enabled: ${settings.ghostModeEnabled},
                        hideStorySeen: ${settings.ghostModeEnabled && settings.ghostHideStorySeen},
                        hideDirectSeen: ${settings.ghostModeEnabled && settings.ghostHideDirectSeen},
                        hideTyping: ${settings.ghostModeEnabled && settings.ghostHideTypingIndicator},
                        igStory: ${settings.ghostModeEnabled && settings.ghostHideStorySeen},
                        igSeen: ${settings.ghostModeEnabled && settings.ghostHideDirectSeen},
                        igTyping: ${settings.ghostModeEnabled && settings.ghostHideTypingIndicator}
                    }
                }, "*");
                window.postMessage({
                    source: "FOCUSGRAM_HOST",
                    type: "FOCUSGRAM_FILTER_UPDATE",
                    config: filterUpdatePayload
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
     * Executes JavaScript inside the active session.
     *
     * Wraps the script in an IIFE and percent-encodes it because Gecko's parser
     * rejects raw newlines or unencoded control characters in `javascript:` URIs.
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
        val scripts = listOf(
            "fetch_interceptor.js",
            "content_hider.js",
            "autoplay_blocker.js",
            "grayscale.js",
            "native_feel.js",
            "media_downloader.js",
            "stealth_instagram.js"
        )
        val bundle = StringBuilder()
        for (name in scripts) {
            val code = loadScript(name)
            if (code.isNotBlank()) {
                bundle.append("try {\n")
                    .append(code)
                    .append("\n} catch (e) { console.error('[FocusGram] Script error in ").append(name).append(":', e); }\n")
            }
        }
        evaluate(session, bundle.toString())
        injectConfig(session, settings)
    }

    companion object {
        private const val TAG = "UserScriptManager"
    }
}
