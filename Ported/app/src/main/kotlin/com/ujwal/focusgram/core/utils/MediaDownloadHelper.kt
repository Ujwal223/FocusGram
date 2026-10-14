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
import java.io.File
import java.io.FileOutputStream

/**
 * MediaDownloadHelper
 * Native client-side media downloader using Android System DownloadManager & MediaStore.
 * Compliant with on-device architecture: downloads directly to user's public Downloads directory.
 */
object MediaDownloadHelper {
    private const val TAG = "MediaDownloadHelper"

    fun download(context: Context, url: String, filename: String = "") {
        if (url.isBlank()) {
            Toast.makeText(context, "No media URL found to download", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            val isVideo = url.contains(".mp4") || url.contains("video") || url.contains("/v/") || url.startsWith("data:video")
            val extension = if (isVideo) "mp4" else "jpg"
            val targetFilename = if (filename.isNotBlank()) {
                if (filename.contains(".")) filename else "$filename.$extension"
            } else {
                "FocusGram_${System.currentTimeMillis()}.$extension"
            }

            if (url.startsWith("data:")) {
                val commaIndex = url.indexOf(',')
                if (commaIndex != -1) {
                    val base64Data = url.substring(commaIndex + 1)
                    val bytes = Base64.decode(base64Data, Base64.DEFAULT)
                    saveBytesToDownloads(context, bytes, targetFilename, isVideo)
                    Toast.makeText(context, "Saved to Downloads: $targetFilename", Toast.LENGTH_SHORT).show()
                    Log.i(TAG, "Saved data URI to Downloads: $targetFilename")
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
                manager.enqueue(request)
                Toast.makeText(context, "Download started: $targetFilename", Toast.LENGTH_SHORT).show()
                Log.i(TAG, "Download enqueued for $targetFilename from $url")
            } else {
                Toast.makeText(context, "Download service unavailable", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to download media: ${e.message}", e)
            Toast.makeText(context, "Download failed: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun saveBytesToDownloads(context: Context, bytes: ByteArray, filename: String, isVideo: Boolean) {
        val mimeType = if (isVideo) "video/mp4" else "image/jpeg"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
                put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            }
            val collection = if (isVideo) MediaStore.Video.Media.EXTERNAL_CONTENT_URI else MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            val itemUri = context.contentResolver.insert(collection, values)
            if (itemUri != null) {
                context.contentResolver.openOutputStream(itemUri)?.use { out ->
                    out.write(bytes)
                }
            }
        } else {
            @Suppress("DEPRECATION")
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val file = File(downloadsDir, filename)
            FileOutputStream(file).use { out ->
                out.write(bytes)
            }
        }
    }
}
