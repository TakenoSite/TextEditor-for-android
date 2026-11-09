package com.example.helloworld

import android.graphics.Typeface
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.Spanned
import android.text.TextWatcher
import android.text.style.ForegroundColorSpan
import androidx.core.content.ContextCompat
import java.util.concurrent.Future
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

/** Color spans only: no setText(), no edits, no changes to composing/selection spans. */
internal class SyntaxHighlighter(private val editor: WhitespaceEditText) {
    private class SyntaxColor(color: Int) : ForegroundColorSpan(color)
    private val main = Handler(Looper.getMainLooper())
    private val originalTypeface = editor.typeface
    private var language: SyntaxLexer.Language? = null
    private var dark = false
    private var generation = 0
    private var active = false
    private var closed = false
    private var applying = false
    private var work: Future<*>? = null
    private val recolor = Runnable { analyze() }
    private val watcher = object : TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        override fun afterTextChanged(s: Editable?) { if (!applying) schedule() }
    }

    init { editor.addTextChangedListener(watcher) }

    fun configure(fileName: String, dark: Boolean) {
        val next = SyntaxLexer.language(fileName)
        if (language == next && this.dark == dark) return
        language = next
        this.dark = dark
        editor.typeface = if (next == null) originalTypeface else Typeface.MONOSPACE
        clearColors()
        schedule()
    }

    fun resume() { if (!closed) { active = true; schedule() } }
    fun pause() {
        active = false
        generation++
        main.removeCallbacksAndMessages(null)
        work?.cancel(true)
        worker.purge()
    }

    private fun schedule() {
        generation++
        main.removeCallbacks(recolor)
        work?.cancel(true)
        worker.purge()
        if (!closed && active) main.postDelayed(recolor, 160L)
    }

    private fun clearColors() {
        val text = editor.text ?: return
        applying = true
        try { text.getSpans(0, text.length, SyntaxColor::class.java).forEach { text.removeSpan(it) } }
        finally { applying = false }
    }

    private fun analyze() {
        if (closed || !active) return
        val selectedLanguage = language
        val revision = generation
        if (selectedLanguage == null || editor.length() > SyntaxLexer.MAX_LENGTH) { clearColors(); return }
        val snapshot = editor.text.toString()
        work = worker.submit {
            val tokens = SyntaxLexer.tokenize(snapshot, selectedLanguage)
            if (Thread.currentThread().isInterrupted) return@submit
            main.post {
                if (!closed && active && revision == generation && editor.text.toString() == snapshot) {
                    val text = editor.text ?: return@post
                    clearColors()
                    applying = true
                    editor.beginBatchEdit()
                    try {
                        tokens.forEach { token ->
                            text.setSpan(SyntaxColor(color(token.kind)), token.start, token.end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                        }
                    } finally {
                        editor.endBatchEdit()
                        applying = false
                    }
                }
            }
        }
    }

    private fun color(kind: SyntaxLexer.Kind): Int {
        val id = if (dark) when (kind) {
            SyntaxLexer.Kind.KEYWORD -> R.color.syntax_dark_keyword
            SyntaxLexer.Kind.STRING -> R.color.syntax_dark_string
            SyntaxLexer.Kind.COMMENT -> R.color.syntax_dark_comment
            SyntaxLexer.Kind.NUMBER -> R.color.syntax_dark_number
            SyntaxLexer.Kind.TYPE -> R.color.syntax_dark_type
            SyntaxLexer.Kind.DIRECTIVE -> R.color.syntax_dark_directive
        } else when (kind) {
            SyntaxLexer.Kind.KEYWORD -> R.color.syntax_light_keyword
            SyntaxLexer.Kind.STRING -> R.color.syntax_light_string
            SyntaxLexer.Kind.COMMENT -> R.color.syntax_light_comment
            SyntaxLexer.Kind.NUMBER -> R.color.syntax_light_number
            SyntaxLexer.Kind.TYPE -> R.color.syntax_light_type
            SyntaxLexer.Kind.DIRECTIVE -> R.color.syntax_light_directive
        }
        return ContextCompat.getColor(editor.context, id)
    }

    fun close() {
        pause()
        closed = true
        editor.removeTextChangedListener(watcher)
    }

    companion object {
        private val worker = ThreadPoolExecutor(1, 1, 30L, TimeUnit.SECONDS,
            LinkedBlockingQueue<Runnable>()).apply { allowCoreThreadTimeOut(true) }
    }
}
