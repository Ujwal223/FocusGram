package com.ujwal.focusgram.core.model

/**
 * FocusSettings
 * Immutable data model representing all user-configurable focus toggles,
 * digital wellness guardrails, mindful gate parameters, and reward credits.
 */
data class FocusSettings(
    // Focus Mode & Content Guardrails
    val noAds: Boolean = true,
    val noStories: Boolean = false,
    val noReels: Boolean = false,
    val noAutoplay: Boolean = true,
    val noDMs: Boolean = false,
    val blurExplore: Boolean = true,
    val blurReels: Boolean = false,
    val blurFeed: Boolean = false,
    val unblurMode: String = "tap", // "tap" or "hold"
    val tapToUnblur: Boolean = true,
    val requireLongPress: Boolean = false,
    val hideSuggestedPosts: Boolean = false,
    val hideLikeCounts: Boolean = false,
    val hideFollowerCounts: Boolean = false,
    val hideShopTab: Boolean = true,
    val minimalModeEnabled: Boolean = false,
    val hideFeedPosts: Boolean = false,
    val blockDmReelsSwiping: Boolean = true,

    // Mindful Gates & Wellness
    val showBreathGate: Boolean = true,
    val breathGateSeconds: Int = 10,
    val requireWordChallenge: Boolean = true,
    val wordChallengeCount: Int = 30,
    val effortFrictionEnabled: Boolean = true,
    val cooldownMinutes: Int = 15,
    val dailyLimitMinutes: Int = 60,
    val dailyLimitAdClaimedDate: String = "",

    // Appearance & System
    val grayscaleEnabled: Boolean = false,
    val smoothScroll120Hz: Boolean = true,
    val reelsHistoryEnabled: Boolean = true,

    // Rewarded Ads & Discipline Economy
    val reelsMinutes: Int = 0,
    val instaMinutes: Int = 0,
    val adsWatchedToday: Int = 0,
    val adsWatchedAllTime: Int = 0,
    val lastAdDate: String = "",
    val userLevel: Int = 1,
    val userXP: Int = 0,

    // App Lock & Privacy
    val appLockEnabled: Boolean = false,
    val biometricEnabled: Boolean = false,
    val autoLockOnBackground: Boolean = true,
    val isFirstRun: Boolean = false,

    // Extras & Notifications
    val startupPage: String = "home",
    val videoDownloadEnabled: Boolean = true,
    val sanitizeLinks: Boolean = true,
    val notifySessionEnd: Boolean = false,
    val notifyPersistent: Boolean = false,

    // Ghost Mode (Privacy & Stealth)
    val ghostModeEnabled: Boolean = false,
    val ghostHideStorySeen: Boolean = true,
    val ghostHideDirectSeen: Boolean = true,
    val ghostHideTypingIndicator: Boolean = true
)
