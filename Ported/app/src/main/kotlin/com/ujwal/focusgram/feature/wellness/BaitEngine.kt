package com.ujwal.focusgram.feature.wellness

import android.content.Context
import android.util.Log
import com.ujwal.focusgram.core.datastore.SettingsRepository
import java.util.Random

/**
 * BaitOutcome
 * Possible outcomes of activating the gamified "Bait Me" button.
 */
enum class BaitOutcome(val label: String, val subtext: String) {
    OPEN_AD_SITE_AND_RESET("💸 Session Reset!", "All session credits have been reset. Better luck next time."),
    ADD_TEN_MINUTES("⏰ +10 Minutes!", "You earned 10 extra minutes. Use them wisely!"),
    REDUCE_SESSION_TIME("⏳ Time Deducted!", "The Bait Me took 1 to 5 minutes away!"),
    INCREASE_COOLDOWN("🧊 Cooldown Increased!", "Cooldown period extended by 10 minutes."),
    END_REEL_SESSION("🎬 Reel Session Ended!", "Your reel session has been cut short."),
    END_APP_SESSION("📱 App Session Ended!", "Your Instagram session has been ended.")
}

/**
 * BaitEngine
 * Weighted random gamification engine that rewards or penalizes risky app interactions.
 */
class BaitEngine(
    private val context: Context,
    private val settingsRepository: SettingsRepository
) {
    private val random = Random()
    private var lastActivationTime = 0L

    val isOnCooldown: Boolean
        get() = (System.currentTimeMillis() - lastActivationTime) < COOLDOWN_MILLIS

    val remainingCooldownMinutes: Int
        get() {
            val elapsed = System.currentTimeMillis() - lastActivationTime
            val remaining = (COOLDOWN_MILLIS - elapsed) / (1000 * 60)
            return remaining.toInt().coerceAtLeast(0)
        }

    fun roll(): BaitOutcome {
        val r = random.nextInt(100)
        return when {
            r < 30 -> BaitOutcome.OPEN_AD_SITE_AND_RESET
            r < 50 -> BaitOutcome.ADD_TEN_MINUTES
            r < 65 -> BaitOutcome.REDUCE_SESSION_TIME
            r < 80 -> BaitOutcome.INCREASE_COOLDOWN
            r < 90 -> BaitOutcome.END_REEL_SESSION
            else -> BaitOutcome.END_APP_SESSION
        }
    }

    suspend fun activate(
        onResetSession: () -> Unit,
        onEndSession: () -> Unit,
        onOpenUrl: (String) -> Unit
    ): BaitOutcome {
        val outcome = roll()
        lastActivationTime = System.currentTimeMillis()

        Log.i(TAG, "Bait Me activated. Rolled outcome: ${outcome.name}")

        when (outcome) {
            BaitOutcome.OPEN_AD_SITE_AND_RESET -> {
                onResetSession()
                onOpenUrl(AD_WEBSITE_URL)
            }
            BaitOutcome.ADD_TEN_MINUTES -> {
                settingsRepository.addInstaMinutes(10)
            }
            BaitOutcome.REDUCE_SESSION_TIME -> {
                val deduction = 1 + random.nextInt(5)
                for (i in 0 until deduction) {
                    settingsRepository.deductInstaMinute()
                }
            }
            BaitOutcome.INCREASE_COOLDOWN -> {
                settingsRepository.setCooldownMinutes(25)
            }
            BaitOutcome.END_REEL_SESSION -> {
                onResetSession()
            }
            BaitOutcome.END_APP_SESSION -> {
                onEndSession()
            }
        }

        return outcome
    }

    companion object {
        private const val TAG = "BaitEngine"
        private const val COOLDOWN_MILLIS = 30 * 60 * 1000L // 30 minutes

        const val AD_WEBSITE_URL =
            "https://www.effectivecpmnetwork.com/qbwsaqj5?key=e547ad0035c9e857ba0ee18506a45f13"
    }
}
