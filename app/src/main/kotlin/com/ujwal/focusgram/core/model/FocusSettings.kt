package com.ujwal.focusgram.core.model

/**
 * User-configurable toggles and operational thresholds stored on-device.
 *
 * All state is persisted via DataStore Preferences. Defaults prioritize distraction
 * elimination (ad blocking, autoplay suppression, explore blurring).
 */
data class FocusSettings(
    val noAds: Boolean = true,
    val noStories: Boolean = false,
    val noReels: Boolean = false,
    val noAutoplay: Boolean = true,
    val noDMs: Boolean = false,
    val blurExplore: Boolean = true,
    val blurReels: Boolean = false,
    val blurFeed: Boolean = false,
    val unblurMode: String = "tap", // "tap" reveals on click; "hold" requires continuous touch
    val tapToUnblur: Boolean = true,
    val requireLongPress: Boolean = false,
    val hideSuggestedPosts: Boolean = false,
    val hideLikeCounts: Boolean = false,
    val hideFollowerCounts: Boolean = false,
    val hideShopTab: Boolean = true,
    val minimalModeEnabled: Boolean = false,
    val hideFeedPosts: Boolean = false,
    val blockDmReelsSwiping: Boolean = true,

    val showBreathGate: Boolean = true,
    val breathGateSeconds: Int = 10,
    val requireWordChallenge: Boolean = true,
    val wordChallengeCount: Int = 30,
    val effortFrictionEnabled: Boolean = true,
    val cooldownMinutes: Int = 5,
    val dailyLimitMinutes: Int = 60,
    val dailyLimitAdClaimedDate: String = "",
    val dailyLimitExtensionsCount: Int = 0,

    val grayscaleEnabled: Boolean = false,
    val smoothScroll120Hz: Boolean = true,
    val reelsHistoryEnabled: Boolean = true,

    val adsWatchedAllTime: Int = 0,
    val lastAdDate: String = "",

    val appLockEnabled: Boolean = false,
    val biometricEnabled: Boolean = false,
    val autoLockOnBackground: Boolean = true,
    val isFirstRun: Boolean = true,

    val startupPage: String = "home",
    val videoDownloadEnabled: Boolean = false,
    val sanitizeLinks: Boolean = true,
    val notifySessionEnd: Boolean = false,
    val notifyPersistent: Boolean = true,
    val notifyEvery5Minutes: Boolean = true,
    val portraitOnly: Boolean = true,
    val showSessionTimeInBottomBar: Boolean = true,
    val appIcon: String = "default",
    val customIconsEnabled: Boolean = false,
    val storySearchEnabled: Boolean = true,
    val customBioFontsEnabled: Boolean = true,

    val chatLockMode: String = "individual", // "off", "individual", "whole_dm"
    val chatLockAll: Boolean = false,
    val lockedChatIds: Set<String> = emptySet(),
    val lockedChatUsernames: Map<String, String> = emptyMap(),
    val useSeparateChatPin: Boolean = false,
    val chatBiometricEnabled: Boolean = true,

    val ghostModeEnabled: Boolean = false,
    val ghostHideStorySeen: Boolean = true,
    val ghostHideDirectSeen: Boolean = true,
    val ghostHideTypingIndicator: Boolean = true,

    val activeSessionDurationMinutes: Int = 0,
    val activeSessionRemainingSeconds: Int = 0,
    val activeSessionElapsedSeconds: Int = 0,
    val activeSessionReelsWatched: Int = 0,
    val activeSessionReelCodes: Set<String> = emptySet(),
    val activeSessionIsActive: Boolean = false
)
