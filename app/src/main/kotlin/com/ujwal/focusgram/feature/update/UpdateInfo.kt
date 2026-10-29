package com.ujwal.focusgram.feature.update

/**
 * Encapsulates version metadata retrieved from the GitHub Releases API.
 */
data class UpdateInfo(
    val latestVersion: String,
    val releaseUrl: String,
    val downloadUrl: String?,
    val whatsNew: String,
    val isUpdateAvailable: Boolean
)

sealed interface UpdateCheckResult {
    data class Available(val updateInfo: UpdateInfo) : UpdateCheckResult
    data object UpToDate : UpdateCheckResult
    data class Failed(val reason: String) : UpdateCheckResult
}
