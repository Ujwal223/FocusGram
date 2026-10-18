package com.ujwal.focusgram.core.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * OriginalIcon (Historical Instagram Brand Icon)
 * Represents an authentic Instagram brand era logo downloaded dynamically on demand.
 */
data class OriginalIcon(
    val id: String,
    val title: String,
    val subtitle: String,
    val downloadUrl: String,
    val year: String = ""
)

typealias HistoricalIcon = OriginalIcon

/**
 * HistoricalIconManager
 * Dynamically downloads authentic original Instagram brand logos on demand,
 * preventing any APK size bloat.
 */
object HistoricalIconManager {
    private const val TAG = "HistoricalIconManager"

    val ORIGINAL_ICONS = listOf(
        OriginalIcon(
            id = "polaroid_camera",
            title = "Polaroid Camera",
            subtitle = "Iconic debut Polaroid camera with rainbow stripe",
            downloadUrl = "https://logovent.com/blog/wp-content/uploads/2024/06/Polaroid-Camera-Instagram-Logo.png"
        ),
        OriginalIcon(
            id = "viewfinder",
            title = "Viewfinder",
            subtitle = "Retro brown leather camera with viewfinder banner",
            downloadUrl = "https://logovent.com/blog/wp-content/uploads/2024/06/Viewfinder-Logo.png"
        ),
        OriginalIcon(
            id = "minimalist_colors",
            title = "Minimalist Colors",
            subtitle = "Flat minimalist neon sunset camera outline",
            downloadUrl = "https://logovent.com/blog/wp-content/uploads/2024/06/Minimalist-with-Colors.png"
        ),
        OriginalIcon(
            id = "vibrant_version",
            title = "Vibrant Gradient",
            subtitle = "Refined brand identity with vibrant illumination",
            downloadUrl = "https://logovent.com/blog/wp-content/uploads/2024/06/Vibrant-Version.png"
        )
    )

    val HISTORICAL_ICONS = ORIGINAL_ICONS

    private val _downloadedIcons = MutableStateFlow<Map<String, Bitmap>>(emptyMap())
    val downloadedIcons: StateFlow<Map<String, Bitmap>> = _downloadedIcons.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _downloadProgress = MutableStateFlow(0f)
    val downloadProgress: StateFlow<Float> = _downloadProgress.asStateFlow()

    /**
     * Ensures all original icons are loaded from cache or downloaded on-the-fly.
     */
    suspend fun loadHistoricalIcons(context: Context) {
        if (_isLoading.value) return
        withContext(Dispatchers.IO) {
            _isLoading.value = true
            try {
                val iconDir = File(context.cacheDir, "original_icons").apply { mkdirs() }
                val loadedMap = HashMap<String, Bitmap>()

                // 1. First check existing disk cache
                for (icon in ORIGINAL_ICONS) {
                    val file = File(iconDir, "${icon.id}.png")
                    if (file.exists() && file.length() > 0) {
                        decodeBitmapSafely(file)?.let { loadedMap[icon.id] = it }
                    }
                }
                _downloadedIcons.value = loadedMap.toMap()

                // 2. Identify missing icons to download
                val missing = ORIGINAL_ICONS.filter { !loadedMap.containsKey(it.id) }
                if (missing.isEmpty()) {
                    _downloadProgress.value = 1f
                    return@withContext
                }

                var completed = 0
                for (icon in missing) {
                    val file = File(iconDir, "${icon.id}.png")
                    try {
                        val connection = (URL(icon.downloadUrl).openConnection() as HttpURLConnection).apply {
                            connectTimeout = 10000
                            readTimeout = 15000
                            setRequestProperty("User-Agent", "FocusGram/1.0 (Android; Mobile)")
                        }
                        if (connection.responseCode in 200..299) {
                            connection.inputStream.use { input ->
                                FileOutputStream(file).use { output ->
                                    input.copyTo(output)
                                }
                            }
                            decodeBitmapSafely(file)?.let { bitmap ->
                                loadedMap[icon.id] = bitmap
                                _downloadedIcons.value = loadedMap.toMap()
                            }
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed downloading icon ${icon.id}: ${e.message}", e)
                    }
                    completed++
                    _downloadProgress.value = completed.toFloat() / missing.size
                }
            } finally {
                _isLoading.value = false
            }
        }
    }

    private fun decodeBitmapSafely(file: File): Bitmap? {
        return try {
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            BitmapFactory.decodeFile(file.absolutePath, options)

            var inSampleSize = 1
            while (options.outWidth / inSampleSize > 512 || options.outHeight / inSampleSize > 512) {
                inSampleSize *= 2
            }
            val decodeOptions = BitmapFactory.Options().apply {
                this.inSampleSize = inSampleSize
            }
            BitmapFactory.decodeFile(file.absolutePath, decodeOptions)
        } catch (e: Exception) {
            Log.e(TAG, "Error decoding file ${file.name}: ${e.message}")
            null
        }
    }
}
