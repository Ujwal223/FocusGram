package com.ujwal.focusgram.core.engine

import android.content.Context
import android.net.Uri
import android.util.Log
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.GeckoSession

/**
 * FocusGramPromptDelegate
 * Handles browser prompts (file picker, camera uploads, JavaScript alerts)
 * through native Android Jetpack contracts while preventing data leakage.
 */
class FocusGramPromptDelegate(
    private val context: Context,
    private val filePickerHandler: FilePickerHandler? = null
) : GeckoSession.PromptDelegate {

    fun interface FilePickerHandler {
        fun onPickFiles(
            mimeTypes: Array<String>,
            allowMultiple: Boolean,
            onResult: (List<Uri>) -> Unit
        )
    }

    override fun onFilePrompt(
        session: GeckoSession,
        prompt: GeckoSession.PromptDelegate.FilePrompt
    ): GeckoResult<GeckoSession.PromptDelegate.PromptResponse> {
        val handler = filePickerHandler
        if (handler == null) {
            Log.d(TAG, "No FilePickerHandler attached. Dismissing file prompt.")
            return GeckoResult.fromValue(prompt.dismiss())
        }

        val mimeTypes = prompt.mimeTypes ?: arrayOf("*/*")
        val isMultiple = prompt.type == GeckoSession.PromptDelegate.FilePrompt.Type.MULTIPLE

        val result = GeckoResult<GeckoSession.PromptDelegate.PromptResponse>()

        handler.onPickFiles(mimeTypes, isMultiple) { uris ->
            try {
                if (uris.isEmpty()) {
                    result.complete(prompt.dismiss())
                } else if (isMultiple) {
                    result.complete(prompt.confirm(context, uris.toTypedArray()))
                } else {
                    result.complete(prompt.confirm(context, uris.first()))
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error confirming file prompt: ${e.message}", e)
                result.complete(prompt.dismiss())
            }
        }

        return result
    }

    override fun onAlertPrompt(
        session: GeckoSession,
        prompt: GeckoSession.PromptDelegate.AlertPrompt
    ): GeckoResult<GeckoSession.PromptDelegate.PromptResponse> {
        Log.i(TAG, "Alert prompt encountered: ${prompt.message}. Auto-dismissing.")
        return GeckoResult.fromValue(prompt.dismiss())
    }

    companion object {
        private const val TAG = "PromptDelegate"
    }
}
