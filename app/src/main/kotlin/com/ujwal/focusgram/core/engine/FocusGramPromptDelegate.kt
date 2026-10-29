package com.ujwal.focusgram.core.engine

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log
import org.mozilla.geckoview.AllowOrDeny
import org.mozilla.geckoview.Autocomplete
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.GeckoSession

/**
 * Bridges GeckoView prompts (file uploads, dialogs, form submissions) to native Android handlers.
 */
class FocusGramPromptDelegate(
    private val context: Context,
    private val filePickerHandler: FilePickerHandler? = null
) : GeckoSession.PromptDelegate {

    fun interface FilePickerHandler {
        fun onPickFiles(
            mimeTypes: Array<String>,
            allowMultiple: Boolean,
            capture: Int,
            onResult: (List<Uri>) -> Unit
        )
    }

    override fun onFilePrompt(
        session: GeckoSession,
        prompt: GeckoSession.PromptDelegate.FilePrompt
    ): GeckoResult<GeckoSession.PromptDelegate.PromptResponse> {
        val handler = filePickerHandler
        if (handler == null) {
            Log.w(TAG, "No FilePickerHandler attached. Dismissing file prompt.")
            return GeckoResult.fromValue(prompt.dismiss())
        }

        val mimeTypes = prompt.mimeTypes ?: arrayOf("*/*")
        val isMultiple = prompt.type == GeckoSession.PromptDelegate.FilePrompt.Type.MULTIPLE
        val capture = prompt.capture

        Log.i(TAG, "onFilePrompt triggered: type=${prompt.type}, isMultiple=$isMultiple, capture=$capture, mimeTypes=${mimeTypes.joinToString()}")

        val result = GeckoResult<GeckoSession.PromptDelegate.PromptResponse>()

        // GeckoView invokes onFilePrompt on a worker thread; launcher contracts require main looper
        Handler(Looper.getMainLooper()).post {
            try {
                handler.onPickFiles(mimeTypes, isMultiple, capture) { uris ->
                    try {
                        if (uris.isEmpty()) {
                            Log.i(TAG, "File picker dismissed or canceled by user.")
                            result.complete(prompt.dismiss())
                        } else if (isMultiple) {
                            Log.i(TAG, "Confirming multiple files (${uris.size}) to GeckoView: ${uris.joinToString()}")
                            result.complete(prompt.confirm(context, uris.toTypedArray()))
                        } else {
                            Log.i(TAG, "Confirming single file to GeckoView: ${uris.first()}")
                            result.complete(prompt.confirm(context, uris.first()))
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error confirming file prompt: ${e.message}", e)
                        result.complete(prompt.dismiss())
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error initiating file picker on UI thread: ${e.message}", e)
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

    override fun onLoginSave(
        session: GeckoSession,
        request: GeckoSession.PromptDelegate.AutocompleteRequest<Autocomplete.LoginSaveOption>
    ): GeckoResult<GeckoSession.PromptDelegate.PromptResponse> {
        Log.i(TAG, "Login save prompt encountered. Auto-confirming to preserve login credentials.")
        val option = request.options.firstOrNull()
        return if (option != null) {
            GeckoResult.fromValue(request.confirm(option))
        } else {
            GeckoResult.fromValue(request.dismiss())
        }
    }

    override fun onRepostConfirmPrompt(
        session: GeckoSession,
        prompt: GeckoSession.PromptDelegate.RepostConfirmPrompt
    ): GeckoResult<GeckoSession.PromptDelegate.PromptResponse> {
        Log.i(TAG, "Repost confirm prompt encountered during form submission. Auto-allowing.")
        return GeckoResult.fromValue(prompt.confirm(AllowOrDeny.ALLOW))
    }

    override fun onBeforeUnloadPrompt(
        session: GeckoSession,
        prompt: GeckoSession.PromptDelegate.BeforeUnloadPrompt
    ): GeckoResult<GeckoSession.PromptDelegate.PromptResponse> {
        Log.i(TAG, "Before unload prompt encountered. Auto-allowing navigation.")
        return GeckoResult.fromValue(prompt.confirm(AllowOrDeny.ALLOW))
    }

    override fun onChoicePrompt(
        session: GeckoSession,
        prompt: GeckoSession.PromptDelegate.ChoicePrompt
    ): GeckoResult<GeckoSession.PromptDelegate.PromptResponse> {
        Log.d(TAG, "Choice prompt encountered. Auto-dismissing.")
        return GeckoResult.fromValue(prompt.dismiss())
    }

    override fun onPopupPrompt(
        session: GeckoSession,
        prompt: GeckoSession.PromptDelegate.PopupPrompt
    ): GeckoResult<GeckoSession.PromptDelegate.PromptResponse> {
        Log.d(TAG, "Popup prompt encountered for target: ${prompt.targetUri}. Auto-dismissing.")
        return GeckoResult.fromValue(prompt.dismiss())
    }

    override fun onButtonPrompt(
        session: GeckoSession,
        prompt: GeckoSession.PromptDelegate.ButtonPrompt
    ): GeckoResult<GeckoSession.PromptDelegate.PromptResponse> {
        Log.d(TAG, "Button prompt encountered. Auto-dismissing.")
        return GeckoResult.fromValue(prompt.dismiss())
    }

    companion object {
        private const val TAG = "PromptDelegate"
    }
}
