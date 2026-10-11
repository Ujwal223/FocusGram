package com.ujwal.focusgram.feature.wellness

import com.ujwal.focusgram.core.datastore.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * CreditStore
 * Manages earned focus time balances (reels and Instagram minutes)
 * acquired through mindful discipline challenges and rewarded ads.
 */
class CreditStore(private val settingsRepository: SettingsRepository) {

    val reelsMinutes: Flow<Int> = settingsRepository.settingsFlow.map { it.reelsMinutes }
    val instaMinutes: Flow<Int> = settingsRepository.settingsFlow.map { it.instaMinutes }
    val adsWatchedToday: Flow<Int> = settingsRepository.settingsFlow.map { it.adsWatchedToday }
    val adsWatchedAllTime: Flow<Int> = settingsRepository.settingsFlow.map { it.adsWatchedAllTime }
    val userLevel: Flow<Int> = settingsRepository.settingsFlow.map { it.userLevel }
    val userXP: Flow<Int> = settingsRepository.settingsFlow.map { it.userXP }

    val canWatchAdToday: Flow<Boolean> = settingsRepository.settingsFlow.map {
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        if (it.lastAdDate != today) true else it.adsWatchedToday < MAX_DAILY_ADS
    }

    suspend fun recordRewardedAd(isReels: Boolean = false) {
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        if (isReels) {
            settingsRepository.addReelsMinutes(MINUTES_PER_AD)
        } else {
            settingsRepository.addInstaMinutes(MINUTES_PER_AD)
        }
        settingsRepository.recordRewardedAd(todayKey = today, minutesEarned = 0)
        settingsRepository.addXP(XP_PER_AD)
    }

    suspend fun deductActiveMinute(isReels: Boolean) {
        if (isReels) {
            settingsRepository.deductReelsMinute()
        } else {
            settingsRepository.deductInstaMinute()
        }
    }

    companion object {
        const val MINUTES_PER_AD = 2
        const val MAX_DAILY_ADS = 5
        const val XP_PER_AD = 25
    }
}
