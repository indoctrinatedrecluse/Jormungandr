package org.jormungandr.shell.copilot.local

import com.intellij.codeInsight.inline.completion.*
import com.intellij.codeInsight.inline.completion.elements.InlineCompletionGrayTextElement
import com.intellij.codeInsight.inline.completion.suggestion.InlineCompletionSingleSuggestion
import com.intellij.codeInsight.inline.completion.suggestion.InlineCompletionSuggestion
import com.intellij.openapi.diagnostic.Logger

/**
 * Native Inline Ghost-Text Completion Provider for Jörmungandr.
 * Delivers local, offline AI autocomplete directly in the editor using Ollama/vLLM FIM models.
 */
class JormungandrInlineCompletionProvider : InlineCompletionProvider {

    private val LOG = Logger.getInstance(JormungandrInlineCompletionProvider::class.java)

    override val id: InlineCompletionProviderID = InlineCompletionProviderID("jormungandr.copilot.inline")

    override fun isEnabled(event: InlineCompletionEvent): Boolean {
        // Trigger on direct typing changes in code files
        return event is InlineCompletionEvent.DocumentChange
    }

    override suspend fun getSuggestion(request: InlineCompletionRequest): InlineCompletionSuggestion {
        val editor = request.editor
        val offset = request.endOffset
        val document = editor.document
        val textLength = document.textLength

        if (offset < 0 || offset > textLength) {
            return InlineCompletionSuggestion.Empty
        }

        val prefix = document.charsSequence.subSequence(0, offset).toString()
        val suffix = document.charsSequence.subSequence(offset, textLength).toString()
        val fileExtension = request.file.virtualFile?.extension?.lowercase() ?: "py"

        val completion = FimCompletionService.getCompletion(prefix, suffix, fileExtension)
        if (completion.isNullOrEmpty()) {
            return InlineCompletionSuggestion.Empty
        }

        return InlineCompletionSingleSuggestion.build {
            emit(InlineCompletionGrayTextElement(completion))
        }
    }
}
