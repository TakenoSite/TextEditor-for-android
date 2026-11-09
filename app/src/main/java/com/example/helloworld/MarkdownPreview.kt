package com.example.helloworld

import android.app.AlertDialog
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.RenderProcessGoneDetail
import android.widget.FrameLayout
import android.widget.TextView
import android.widget.Toast
import java.io.ByteArrayInputStream
import java.util.concurrent.Future
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

/** Owns the read-only WebView; never grants it network, file or content access. */
internal class MarkdownPreview(private val host: FrameLayout, private val dark: Boolean) {
    private val context = host.context
    private val main = Handler(Looper.getMainLooper())
    private var work: Future<*>? = null
    private var generation = 0
    private var closed = false
    private val status = TextView(context).apply {
        setPadding(24, 24, 24, 24)
        setTextColor(EditorAppearance(dark).text)
        accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
    }
    private var web: WebView? = null

    private fun createWeb(): WebView = WebView(context).apply {
        setBackgroundColor(EditorAppearance(dark).background)
        settings.apply {
            javaScriptEnabled = false
            allowFileAccess = false
            allowContentAccess = false
            blockNetworkLoads = true
            blockNetworkImage = true
            loadsImagesAutomatically = false
            domStorageEnabled = false
            setSupportZoom(true)
            builtInZoomControls = true
            displayZoomControls = false
        }
        webViewClient = object : WebViewClient() {
            @android.annotation.TargetApi(26)
            override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
                Log.e("MarkdownPreview", "WebView renderer exited: crashed=${detail.didCrash()}")
                if (web === view) {
                    generation++
                    work?.cancel(true)
                    web = null
                    releaseWeb(view)
                    showError()
                } else {
                    releaseWeb(view)
                }
                // Acknowledge this renderer loss so Android does not terminate the app.
                return true
            }

            override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest) =
                WebResourceResponse("text/plain", "UTF-8", ByteArrayInputStream(ByteArray(0)))

            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                if (request.hasGesture() && request.isForMainFrame) openExternal(request.url)
                return true
            }
        }
        contentDescription = context.getString(R.string.markdown_preview)
    }

    init {
        host.setBackgroundColor(EditorAppearance(dark).background)
        host.addView(status, FrameLayout.LayoutParams(-1, -2))
    }

    fun render(source: String) {
        if (closed) return
        val current = ++generation
        work?.cancel(true)
        renderer.purge()
        status.setText(R.string.markdown_loading)
        status.visibility = View.VISIBLE
        val target = try {
            web ?: createWeb().also {
                web = it
                host.addView(it, 0, FrameLayout.LayoutParams(-1, -1))
            }
        } catch (e: RuntimeException) {
            Log.e("MarkdownPreview", "Unable to create WebView", e)
            showError()
            return
        }
        target.visibility = View.INVISIBLE
        work = renderer.submit {
            val result = runCatching { MarkdownRenderer.render(source, dark) }
            if (Thread.currentThread().isInterrupted) return@submit
            main.post {
                if (!closed && generation == current && web === target) {
                    result.onSuccess { html ->
                        try {
                            target.loadDataWithBaseURL("https://markdown.invalid/", html, "text/html", "UTF-8", null)
                            target.visibility = View.VISIBLE
                            status.visibility = View.GONE
                        } catch (e: RuntimeException) {
                            Log.e("MarkdownPreview", "Unable to load preview HTML", e)
                            web = null
                            releaseWeb(target)
                            showError()
                        }
                    }.onFailure {
                        Log.e("MarkdownPreview", "Markdown conversion failed", it)
                        showError()
                    }
                }
            }
        }
    }

    private fun showError() {
        web?.visibility = View.INVISIBLE
        status.setText(R.string.markdown_error)
        status.visibility = View.VISIBLE
    }

    private fun releaseWeb(view: WebView) {
        host.removeView(view)
        try { view.destroy() }
        catch (e: RuntimeException) { Log.w("MarkdownPreview", "Unable to destroy WebView", e) }
    }

    private fun openExternal(uri: Uri) {
        if (uri.scheme !in listOf("http", "https") || uri.host.isNullOrBlank() || uri.host == "markdown.invalid") {
            Toast.makeText(context, R.string.markdown_link_error, Toast.LENGTH_SHORT).show()
            return
        }
        AlertDialog.Builder(context).setTitle(R.string.markdown_external).setMessage(uri.toString())
            .setNegativeButton(R.string.markdown_cancel, null)
            .setPositiveButton(R.string.markdown_open) { _, _ ->
                try { context.startActivity(Intent(Intent.ACTION_VIEW, uri).addCategory(Intent.CATEGORY_BROWSABLE)) }
                catch (_: ActivityNotFoundException) {
                    Toast.makeText(context, R.string.markdown_link_error, Toast.LENGTH_SHORT).show()
                }
            }.show()
    }

    fun close() {
        closed = true
        generation++
        work?.cancel(true)
        renderer.purge()
        main.removeCallbacksAndMessages(null)
        web?.let { releaseWeb(it) }
        web = null
        host.removeAllViews()
    }

    companion object {
        // At most one conversion across rapid document changes or Activity recreation.
        private val renderer = ThreadPoolExecutor(1, 1, 30L, TimeUnit.SECONDS,
            LinkedBlockingQueue<Runnable>()).apply { allowCoreThreadTimeOut(true) }
    }
}
