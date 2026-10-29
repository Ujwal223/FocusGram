package com.ujwal.focusgram.core.engine

import android.content.Context
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.content.ContextCompat
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.GeckoSession

/**
 * Filters and delegates Android runtime and content origin permission requests from GeckoView.
 */
class FocusGramPermissionDelegate(
    private val context: Context,
    private val onAndroidPermissionRequest: ((Array<String>, (Boolean) -> Unit) -> Unit)? = null
) : GeckoSession.PermissionDelegate {

    override fun onAndroidPermissionsRequest(
        session: GeckoSession,
        permissions: Array<out String>?,
        callback: GeckoSession.PermissionDelegate.Callback
    ) {
        val permsList = permissions?.filterNotNull() ?: emptyList()
        Log.d(TAG, "onAndroidPermissionsRequest: ${permsList.joinToString()}")
        val ungranted = permsList.filter {
            ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
        }

        // Android 13+ (API 33+) deprecates broad storage permissions and auto-denies them;
        // modern media selection goes through SAF / Photo Picker instead.
        val actionableUngranted = if (android.os.Build.VERSION.SDK_INT >= 33) {
            ungranted.filter {
                it != android.Manifest.permission.READ_EXTERNAL_STORAGE &&
                it != android.Manifest.permission.WRITE_EXTERNAL_STORAGE
            }
        } else {
            ungranted
        }

        if (actionableUngranted.isEmpty()) {
            callback.grant()
            return
        }

        if (onAndroidPermissionRequest != null) {
            Handler(Looper.getMainLooper()).post {
                try {
                    onAndroidPermissionRequest.invoke(actionableUngranted.toTypedArray()) { granted ->
                        // Grant to GeckoView so it advances to onFilePrompt where SAF handles the URI
                        callback.grant()
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error requesting Android permissions: ${e.message}", e)
                    callback.grant()
                }
            }
        } else {
            callback.grant()
        }
    }

    override fun onContentPermissionRequest(
        session: GeckoSession,
        perm: GeckoSession.PermissionDelegate.ContentPermission
    ): GeckoResult<Int> {
        Log.d(TAG, "onContentPermissionRequest: uri=${perm.uri}, perm=${perm.permission}")
        val uri = perm.uri?.lowercase() ?: ""
        return if (uri.contains("instagram.com") || uri.contains("cdninstagram.com")) {
            GeckoResult.fromValue(GeckoSession.PermissionDelegate.ContentPermission.VALUE_ALLOW)
        } else {
            GeckoResult.fromValue(GeckoSession.PermissionDelegate.ContentPermission.VALUE_DENY)
        }
    }

    override fun onMediaPermissionRequest(
        session: GeckoSession,
        uri: String,
        video: Array<out GeckoSession.PermissionDelegate.MediaSource>?,
        audio: Array<out GeckoSession.PermissionDelegate.MediaSource>?,
        callback: GeckoSession.PermissionDelegate.MediaCallback
    ) {
        Log.d(TAG, "onMediaPermissionRequest: uri=$uri, video=${video?.size}, audio=${audio?.size}")
        val videoSource = video?.firstOrNull()
        val audioSource = audio?.firstOrNull()
        if (videoSource != null || audioSource != null) {
            callback.grant(videoSource, audioSource)
        } else {
            callback.reject()
        }
    }

    companion object {
        private const val TAG = "PermissionDelegate"
    }
}
