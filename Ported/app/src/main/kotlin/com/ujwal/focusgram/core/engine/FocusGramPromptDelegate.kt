package com.ujwal.focusgram.core.engine

import android.content.Context
import android.net.Uri
import android.util.Log
import org.mozilla.geckoview.AllowOrDeny
import org.mozilla.geckoview.Autocomplete
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.GeckoSession

/**
 * FocusGramPromptDelegate
 * Handles browser prompts (file picker, camera uploads, JavaScript alerts, password saves)
 * through native Android Jetpack contracts while preventing data leakage and compositor freezing.
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
