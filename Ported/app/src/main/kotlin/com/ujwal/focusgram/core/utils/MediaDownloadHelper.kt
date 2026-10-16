package com.ujwal.focusgram.core.utils

import android.app.DownloadManager
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Base64
import android.util.Log
import android.widget.Toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream

data class DownloadProgressState(
    val isDownloading: Boolean = false,
    val progress: Float = 0f, // 0f to 1f
    val filename: String = "",
    val isCompleted: Boolean = false,
    val isFailed: Boolean = false
)

/**
 * MediaDownloadHelper
 * Native client-side media downloader using Android System DownloadManager & MediaStore.
 * Compliant with on-device architecture: downloads directly to user's public Downloads directory.
 */
object MediaDownloadHelper {
    private const val TAG = "MediaDownloadHelper"

    private val _downloadProgressFlow = MutableStateFlow(DownloadProgressState())
    val downloadProgressFlow: StateFlow<DownloadProgressState> = _downloadProgressFlow.asStateFlow()

    private val helperScope = CoroutineScope(Dispatchers.IO)
    private var progressJob: Job? = null

    private var lastDownloadUrl: String = ""
    private var lastDownloadTimestamp: Long = 0L

    /**
     * Determines whether media is a video based on explicit URL and filename markers.
     * Note: NEVER treat "/v/" in CDN URLs as video because all Meta CDN photos contain "/v/".
     */
    fun detectIsVideo(url: String, filename: String = ""): Boolean {
        val lowerUrl = url.lowercase()
        val lowerFile = filename.lowercase()
        if (lowerUrl.contains(".jpg") || lowerUrl.contains(".jpeg") || lowerUrl.contains(".png") ||
            lowerUrl.contains(".webp") || lowerUrl.contains("dst-jpg") || lowerUrl.contains("dst-png") ||
            lowerUrl.contains("dst-webp") || lowerUrl.startsWith("data:image") ||
            lowerFile.endsWith(".jpg") || lowerFile.endsWith(".jpeg") ||
            lowerFile.endsWith(".png") || lowerFile.endsWith(".webp")
        ) {
            return false
        }
        if (lowerUrl.contains(".mp4") || lowerUrl.contains(".webm") || lowerUrl.contains("video_dashinit") ||
            lowerUrl.contains("/v/t50.") || lowerUrl.contains("/v/t16.") ||
            lowerUrl.startsWith("data:video") || lowerFile.endsWith(".mp4")
        ) {
            return true
        }
        return false
    }

    /**
     * Formats target filename according to FocusGram specification:
     * Author name (first 10-13 characters) followed by ' - Focusgram.<ext>'
     * E.g. '@ujwal - Focusgram.mp4' or '@ujwal - Focusgram.jpg'.
     */
    fun formatTargetFilename(rawFilename: String, isVideo: Boolean): String {
        val extension = if (isVideo) "mp4" else "jpg"
        var clean = rawFilename.trim()

        // Strip known extension if present
        if (clean.endsWith(".mp4", ignoreCase = true) || clean.endsWith(".jpg", ignoreCase = true) ||
            clean.endsWith(".jpeg", ignoreCase = true) || clean.endsWith(".png", ignoreCase = true)
        ) {
            clean = clean.substringBeforeLast('.')
        }

        // Handle slide suffix (e.g. _2 for carousel slides)
        var slideSuffix = ""
        val slideMatch = Regex("_(\\d+)$").find(clean)
        if (slideMatch != null) {
            slideSuffix = "_" + slideMatch.groupValues[1]
            clean = clean.removeSuffix(slideSuffix)
        }

        // Extract author prefix
        val authorName = when {
            clean.startsWith("@") -> clean.removePrefix("@").substringBefore(" - ").substringBefore('_').trim()
            clean.contains(" - Focusgram", ignoreCase = true) -> {
                val idx = clean.indexOf(" - Focusgram", ignoreCase = true)
                if (idx != -1) clean.substring(0, idx).trim().removePrefix("@") else ""
            }
            clean.startsWith("instagram_", ignoreCase = true) -> ""
            clean.startsWith("Focusgram", ignoreCase = true) -> ""
            clean.isNotBlank() -> clean.substringBefore('_').trim().removePrefix("@")
            else -> ""
        }

        val sanitizedAuthor = authorName
            .replace(Regex("[^a-zA-Z0-9._-]"), "")
            .take(13)
            .ifBlank { "Focusgram" }

        val prefix = if (sanitizedAuthor.equals("Focusgram", ignoreCase = true)) "@Focusgram" else "@$sanitizedAuthor"
        return "$prefix - Focusgram$slideSuffix.$extension"
    }

    /**
     * Starts visual perimeter progress in the bottom nav bar while URL is resolving.
     * Starts at 0.05f without fake timer loops so visual pace reflects actual network download.
     */
    fun startIndeterminateProgress(filename: String = "") {
        progressJob?.cancel()
        _downloadProgressFlow.value = DownloadProgressState(
            isDownloading = true,
            progress = 0.05f,
            filename = filename,
            isCompleted = false,
            isFailed = false
        )
    }

    /**
     * Reverses progress bar from current position back to start in red/dark-red and cancels animation.
     */
    fun triggerDownloadFailure(context: Context, message: String = "Download failed") {
        progressJob?.cancel()
        val startProgress = _downloadProgressFlow.value.progress.coerceAtLeast(0.12f)
        progressJob = helperScope.launch {
            _downloadProgressFlow.value = DownloadProgressState(
                isDownloading = true,
                progress = startProgress,
                filename = "",
                isCompleted = false,
                isFailed = true
            )
            android.os.Handler(android.os.Looper.getMainLooper()).post {
                Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
            }
            val steps = 20
            for (i in steps downTo 0) {
                delay(20L) // 20 * 20ms = ~400ms smooth reverse animation
                _downloadProgressFlow.value = _downloadProgressFlow.value.copy(
                    progress = startProgress * (i.toFloat() / steps)
                )
            }
            delay(60L)
            _downloadProgressFlow.value = DownloadProgressState(
                isDownloading = false,
                progress = 0f,
                filename = "",
                isCompleted = false,
                isFailed = false
            )
        }
    }

    fun download(context: Context, url: String, filename: String = "") {
        if (url.isBlank()) {
            triggerDownloadFailure(context, "No media URL found to download")
            return
        }

        val now = System.currentTimeMillis()
        if (url == lastDownloadUrl && (now - lastDownloadTimestamp) < 1500L) {
            Log.d(TAG, "Ignoring duplicate download trigger within debounce window")
            return
        }
        lastDownloadUrl = url
        lastDownloadTimestamp = now

        try {
            val isVideo = detectIsVideo(url, filename)
            val targetFilename = formatTargetFilename(filename, isVideo)

            progressJob?.cancel()

            if (url.startsWith("data:")) {
                val commaIndex = url.indexOf(',')
                if (commaIndex != -1) {
                    val base64Data = url.substring(commaIndex + 1)
                    val bytes = Base64.decode(base64Data, Base64.DEFAULT)
                    saveBytesToDownloads(context, bytes, targetFilename, isVideo)
                    Log.i(TAG, "Saved data URI to Downloads: $targetFilename")

                    progressJob = helperScope.launch {
                        _downloadProgressFlow.value = DownloadProgressState(isDownloading = true, progress = 0.5f, filename = targetFilename)
                        delay(150L)
                        _downloadProgressFlow.value = DownloadProgressState(isDownloading = false, progress = 1.0f, filename = targetFilename, isCompleted = true)
                        android.os.Handler(android.os.Looper.getMainLooper()).post {
                            Toast.makeText(context, "Download complete", Toast.LENGTH_SHORT).show()
                        }
                        delay(1200L)
                        _downloadProgressFlow.value = DownloadProgressState(isDownloading = false, progress = 0f, filename = "", isCompleted = false)
                    }
                    return
                }
            }

            val uri = Uri.parse(url)
            val request = DownloadManager.Request(uri).apply {
                setTitle(targetFilename)
                setDescription("FocusGram Media Download")
                setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, targetFilename)
                setAllowedOverMetered(true)
                setAllowedOverRoaming(true)
                addRequestHeader("User-Agent", "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Mobile Safari/537.36")
                addRequestHeader("Referer", "https://www.instagram.com/")
                addRequestHeader("Accept", "*/*")
            }

            val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as? DownloadManager
            if (manager != null) {
                val downloadId = try {
                    manager.enqueue(request)
                } catch (e: Exception) {
                    Log.w(TAG, "DownloadManager.enqueue failed, falling back to direct HTTP: ${e.message}")
                    downloadDirectHttp(context, url, targetFilename, isVideo)
                    return
                }

                Log.i(TAG, "Download enqueued for $targetFilename (id=$downloadId) from $url")

                _downloadProgressFlow.value = DownloadProgressState(
                    isDownloading = true,
                    progress = 0.08f,
                    filename = targetFilename,
                    isCompleted = false,
                    isFailed = false
                )

                progressJob = helperScope.launch {
                    var lastProgress = 0.08f
                    var isDone = false
                    val startTime = System.currentTimeMillis()

                    while (!isDone && (System.currentTimeMillis() - startTime < 60000L)) {
                        delay(120L)
                        try {
                            val query = DownloadManager.Query().setFilterById(downloadId)
                            val cursor = manager.query(query)
                            if (cursor != null && cursor.moveToFirst()) {
                                val bytesCol = cursor.getColumnIndex(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)
                                val totalCol = cursor.getColumnIndex(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)
                                val statusCol = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)
                                val reasonCol = cursor.getColumnIndex(DownloadManager.COLUMN_REASON)

                                val bytesDownloaded = if (bytesCol != -1) cursor.getLong(bytesCol) else 0L
                                val totalBytes = if (totalCol != -1) cursor.getLong(totalCol) else -1L
                                val status = if (statusCol != -1) cursor.getInt(statusCol) else -1
                                val reason = if (reasonCol != -1) cursor.getInt(reasonCol) else -1
                                cursor.close()

                                when (status) {
                                    DownloadManager.STATUS_SUCCESSFUL -> {
                                        isDone = true
                                        _downloadProgressFlow.value = DownloadProgressState(
                                            isDownloading = false,
                                            progress = 1.0f,
                                            filename = targetFilename,
                                            isCompleted = true,
                                            isFailed = false
                                        )
                                        android.os.Handler(android.os.Looper.getMainLooper()).post {
                                            Toast.makeText(context, "Download complete", Toast.LENGTH_SHORT).show()
                                        }
                                        delay(1200L)
                                        _downloadProgressFlow.value = DownloadProgressState(
                                            isDownloading = false,
                                            progress = 0f,
                                            filename = "",
                                            isCompleted = false
                                        )
                                    }
                                    DownloadManager.STATUS_FAILED -> {
                                        isDone = true
                                        Log.w(TAG, "DownloadManager failed with reason $reason, retrying via direct HTTP")
                                        downloadDirectHttp(context, url, targetFilename, isVideo)
                                    }
                                    else -> {
                                        val computed = if (totalBytes > 0) {
                                            (bytesDownloaded.toFloat() / totalBytes.toFloat()).coerceIn(0.08f, 0.98f)
                                        } else {
                                            (lastProgress + 0.02f).coerceAtMost(0.85f)
                                        }
                                        lastProgress = computed
                                        _downloadProgressFlow.value = DownloadProgressState(
                                            isDownloading = true,
                                            progress = computed,
                                            filename = targetFilename,
                                            isCompleted = false,
                                            isFailed = false
                                        )
                                    }
                                }
                            } else {
                                cursor?.close()
                                if (lastProgress >= 0.7f) {
                                    isDone = true
                                    _downloadProgressFlow.value = DownloadProgressState(
                                        isDownloading = false,
                                        progress = 1.0f,
                                        filename = targetFilename,
                                        isCompleted = true,
                                        isFailed = false
                                    )
                                    android.os.Handler(android.os.Looper.getMainLooper()).post {
                                        Toast.makeText(context, "Download complete", Toast.LENGTH_SHORT).show()
                                    }
                                    delay(1200L)
                                    _downloadProgressFlow.value = DownloadProgressState(
                                        isDownloading = false,
                                        progress = 0f,
                                        filename = "",
                                        isCompleted = false
                                    )
                                }
                            }
                        } catch (e: Exception) {
                            Log.w(TAG, "Error checking download progress: ${e.message}")
                        }
                    }
                }
            } else {
                Log.w(TAG, "DownloadManager unavailable, falling back to direct HTTP")
                downloadDirectHttp(context, url, targetFilename, isVideo)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to download media: ${e.message}", e)
            triggerDownloadFailure(context, "Download failed: ${e.message ?: "Unknown error"}")
        }
    }

    private fun downloadDirectHttp(context: Context, url: String, targetFilename: String, isVideo: Boolean) {
        helperScope.launch {
            try {
                Log.i(TAG, "Starting direct HTTP download for $targetFilename from $url")
                _downloadProgressFlow.value = DownloadProgressState(
                    isDownloading = true,
                    progress = 0.08f,
                    filename = targetFilename,
                    isCompleted = false,
                    isFailed = false
                )
                val connection = (java.net.URL(url).openConnection() as java.net.HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 15000
                    readTimeout = 30000
                    setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Mobile Safari/537.36")
                    setRequestProperty("Referer", "https://www.instagram.com/")
                    setRequestProperty("Accept", "*/*")
                    instanceFollowRedirects = true
                }
                connection.connect()
                val code = connection.responseCode
                if (code in 200..299) {
                    val contentLength = connection.contentLengthLong
                    val bytesOut = java.io.ByteArrayOutputStream()
                    connection.inputStream.use { input ->
                        val buffer = ByteArray(8192)
                        var read: Int
                        var downloaded = 0L
                        while (input.read(buffer).also { read = it } != -1) {
                            bytesOut.write(buffer, 0, read)
                            downloaded += read
                            if (contentLength > 0) {
                                val prog = (downloaded.toFloat() / contentLength.toFloat()).coerceIn(0.08f, 0.98f)
                                _downloadProgressFlow.value = DownloadProgressState(
                                    isDownloading = true,
                                    progress = prog,
                                    filename = targetFilename,
                                    isCompleted = false,
                                    isFailed = false
                                )
                            }
                        }
                    }
                    val data = bytesOut.toByteArray()
                    saveBytesToDownloads(context, data, targetFilename, isVideo)
                    _downloadProgressFlow.value = DownloadProgressState(
                        isDownloading = false,
                        progress = 1.0f,
                        filename = targetFilename,
                        isCompleted = true,
                        isFailed = false
                    )
                    android.os.Handler(android.os.Looper.getMainLooper()).post {
                        Toast.makeText(context, "Download complete", Toast.LENGTH_SHORT).show()
                    }
                    delay(1200L)
                    _downloadProgressFlow.value = DownloadProgressState(isDownloading = false, progress = 0f)
                } else {
                    Log.e(TAG, "Direct HTTP download returned status $code")
                    triggerDownloadFailure(context, "Download failed (HTTP $code)")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Direct HTTP download error: ${e.message}", e)
                triggerDownloadFailure(context, "Download failed: ${e.message ?: "Network error"}")
            }
        }
    }

    private fun saveBytesToDownloads(context: Context, bytes: ByteArray, filename: String, isVideo: Boolean) {
        // Inspect magic bytes to verify whether bytes are an image or a video
        val isActuallyImage = bytes.size >= 4 && (
            (bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte() && bytes[2] == 0xFF.toByte()) || // JPEG
            (bytes[0] == 0x89.toByte() && bytes[1] == 0x50.toByte() && bytes[2] == 0x4E.toByte() && bytes[3] == 0x47.toByte()) // PNG
        )
        val resolvedIsVideo = if (isActuallyImage) false else isVideo
        val resolvedFilename = if (isActuallyImage && filename.endsWith(".mp4", ignoreCase = true)) {
            filename.substringBeforeLast('.') + ".jpg"
        } else {
            filename
        }
        val mimeType = if (resolvedIsVideo) "video/mp4" else "image/jpeg"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, resolvedFilename)
                put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            }
            val collection = if (resolvedIsVideo) MediaStore.Video.Media.EXTERNAL_CONTENT_URI else MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            val itemUri = context.contentResolver.insert(collection, values)
            if (itemUri != null) {
                context.contentResolver.openOutputStream(itemUri)?.use { out ->
                    out.write(bytes)
                }
            }
        } else {
            @Suppress("DEPRECATION")
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val file = File(downloadsDir, resolvedFilename)
            FileOutputStream(file).use { out ->
                out.write(bytes)
            }
        }
    }
}
