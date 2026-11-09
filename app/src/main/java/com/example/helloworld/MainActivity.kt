package com.example.helloworld

import android.app.AlertDialog
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.OpenableColumns
import android.provider.DocumentsContract
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.content.Context
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ImageView
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.PopupWindow
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import java.io.File
import java.util.ArrayDeque
import java.util.regex.Pattern
import kotlin.math.roundToInt

class MainActivity : AppCompatActivity() {
    private lateinit var editor: WhitespaceEditText
    private lateinit var lines: LineNumberView
    private lateinit var title: TextView
    private lateinit var location: TextView
    private var document: Uri? = null
    private var localDocumentPath: String? = null
    private var name = "無題.txt"
    private var dirty = false
    private var changing = false
    private var readOnly = false
    private var originalSource = ""
    private var appearanceDark = false
    private val colors get() = EditorAppearance(appearanceDark)
    private val undo = ArrayDeque<String>()
    private val redo = ArrayDeque<String>()
    private val prefs by lazy { getSharedPreferences("editor", MODE_PRIVATE) }
    private val handler = Handler(Looper.getMainLooper())
    private val autosave = Runnable { if (dirty && document != null && prefs.getBoolean("autosave", false)) save(false) }
    private var query = ""
    private var matchCase = false
    private var useRegex = false
    private var wrapSearch = true
    private var searchCardStatus: TextView? = null
    private var historyRetryUri: Uri? = null
    private var reconnectSourceUri: Uri? = null
    private var preview: MarkdownPreview? = null
    private val previewLifecycle = PreviewLifecycle()
    private val previewVisible get() = previewLifecycle.visible
    private var stateToken = java.util.UUID.randomUUID().toString()
    private val renderPreview = Runnable {
        if (!isFinishing && !isDestroyed && findViewById<View>(R.id.markdownHost).isAttachedToWindow &&
            previewLifecycle.takeRenderRequest()) {
            if (preview == null) preview = MarkdownPreview(findViewById(R.id.markdownHost), appearanceDark)
            preview?.render(editor.text.toString())
        }
    }

    private fun searchMessage(text: String) {
        historyRetryUri = null
        findViewById<View>(R.id.reconnectFile).visibility = View.GONE
        searchCardStatus?.text = text
        findViewById<TextView>(R.id.searchNotice).apply {
            this.text = text
        }
        findViewById<View>(R.id.searchNoticeCard).visibility = View.VISIBLE
    }
    private var navigationOverlay: FrameLayout? = null
    private var navigationCard: View? = null
    private var navigationClosing = false
    private var zoomPercent = 100f

    private val openLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) load(uri, true)
    }
    private val recentLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            result.data?.getStringExtra(RecentFilesActivity.SELECTED_URI)?.let { link ->
                confirmDiscard { load(Uri.parse(link), false, fromHistory = true) }
            }
        }
    }
    private val saveLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        if (uri != null) {
            document = uri
            localDocumentPath = null
            name = displayName(uri)
            save(false)
        }
    }
    private val reconnectLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val old = reconnectSourceUri
        reconnectSourceUri = null
        val uri = result.data?.data
        if (result.resultCode == RESULT_OK && uri != null && load(uri, true)) {
            if (old != null && old != uri) RecentFilesStore(this).remove(old.toString())
        }
    }
    private val storagePermission = StoragePermissionController(this) {
        if (StorageAccess.granted(this)) historyRetryUri?.let { uri -> confirmDiscard { load(uri, false, fromHistory = true) } }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        appearanceDark = EditorAppearance.isDark(this)
        setTheme(EditorAppearance.theme(this))
        super.onCreate(savedInstanceState)
        // Restore the editor payload from app-private storage, not a large Android Bundle.
        val editorState = savedInstanceState?.let { saved ->
            val token = saved.getString("editor_state_token")
            if (token == null) saved else try {
                EditorStateStore(this).load(token).also { stateToken = token }
            } catch (e: Exception) {
                android.util.Log.e("EditorState", "Unable to restore editor snapshot", e)
                message("編集状態を復元できませんでした。保存済みファイルを開き直します。")
                null
            }
        }
        onBackPressedDispatcher.addCallback(this, object : androidx.activity.OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (navigationOverlay != null) closeNavigation()
                else if (previewVisible) setPreview(false)
                else confirmDiscard { finish() }
            }
        })
        setContentView(R.layout.activity_main)
        editor = findViewById(R.id.editor)
        lines = findViewById(R.id.lineNumbers)
        zoomPercent = EditorZoom.clamp(editorState?.getFloat("zoom_percent", prefs.getFloat("zoom_percent", 100f)) ?: prefs.getFloat("zoom_percent", 100f))
        editor.onZoomRequested = { factor, x, y -> applyZoom(zoomPercent * factor, x, y) }
        editor.onZoomFinished = { saveZoom() }
        title = findViewById(R.id.fileTitle)
        location = findViewById(R.id.filePath)
        applyZoom(zoomPercent)
        editor.setWordWrapEnabled(prefs.getBoolean("wrap", false))
        lines.visibility = if (prefs.getBoolean("numbers", true)) View.VISIBLE else View.GONE
        lines.editor = editor
        editor.setOnScrollChangeListener { _, _, _, _, _ -> lines.invalidate() }
        editor.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> lines.invalidate() }
        editor.setOnFocusChangeListener { _, focused -> editor.isCursorVisible = focused }
        findViewById<View>(R.id.closeSearchNotice).setOnClickListener {
            findViewById<View>(R.id.searchNoticeCard).visibility = View.GONE
            historyRetryUri = null
        }
        findViewById<View>(R.id.reconnectFile).setOnClickListener {
            historyRetryUri?.let { old ->
                if (!StorageAccess.granted(this)) {
                    if (!storagePermission.request()) showFileAccessIssue(old, "権限設定を開けませんでした。設定のファイルアクセスを確認してください。")
                    return@setOnClickListener
                }
                confirmDiscard {
                    reconnectSourceUri = old
                    val request = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                        addCategory(Intent.CATEGORY_OPENABLE)
                        type = "*/*"
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
                        if (android.os.Build.VERSION.SDK_INT >= 26) putExtra(DocumentsContract.EXTRA_INITIAL_URI, old)
                    }
                    reconnectLauncher.launch(request)
                }
            }
        }
        editor.addTextChangedListener(object : TextWatcher {
            private var before = ""
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
                if (!changing) before = s.toString()
            }
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                if (changing) return
                if (before != s.toString()) {
                    undo.addLast(before)
                    if (undo.size > 50) undo.removeFirst()
                    redo.clear()
                    dirty = true
                    refresh()
                    scheduleAutosave()
                }
            }
        })
        findViewById<View>(R.id.menuButton).setOnClickListener { showNavigation() }
        findViewById<View>(R.id.openTop).setOnClickListener { showFileMenu() }
        findViewById<View>(R.id.editTop).setOnClickListener { setPreview(false); editor.requestFocus(); keyboard() }
        findViewById<View>(R.id.markdownEdit).setOnClickListener { setPreview(false) }
        findViewById<View>(R.id.markdownPreview).setOnClickListener { setPreview(true) }
        findViewById<View>(R.id.moreTop).setOnClickListener { showMore(it) }
        findViewById<View>(R.id.toolOpen).setOnClickListener { open() }
        findViewById<View>(R.id.toolNew).setOnClickListener { confirmDiscard { newDocument() } }
        findViewById<View>(R.id.toolUndo).setOnClickListener { history(undo, redo) }
        findViewById<View>(R.id.toolRedo).setOnClickListener { history(redo, undo) }
        findViewById<View>(R.id.toolSearch).setOnClickListener { searchDialog() }
        findViewById<View>(R.id.toolPrevious).setOnClickListener { findMatch(false) }
        findViewById<View>(R.id.toolNext).setOnClickListener { findMatch(true) }
        findViewById<View>(R.id.toolSave).setOnClickListener { save(false) }
        findViewById<View>(R.id.toolClose).setOnClickListener { clearEditorText() }
        if (editorState != null) {
            reconnectSourceUri = editorState.getString("reconnect_source")?.let(Uri::parse)
            historyRetryUri = editorState.getString("history_retry")?.let(Uri::parse)
            document = editorState.getString("uri")?.let(Uri::parse)
            localDocumentPath = editorState.getString("local_path")
            name = editorState.getString("name") ?: name
            dirty = editorState.getBoolean("dirty")
            replaceText(editorState.getString("text") ?: "")
            readOnly = editorState.getBoolean("readonly")
            originalSource = editorState.getString("original") ?: ""
            editorState.getStringArrayList("undo")?.forEach { undo.addLast(it) }
            editorState.getStringArrayList("redo")?.forEach { redo.addLast(it) }
            query = editorState.getString("query") ?: ""
            matchCase = editorState.getBoolean("matchCase")
            useRegex = editorState.getBoolean("regex")
            wrapSearch = editorState.getBoolean("wrapSearch", true)
            editor.setSelection(editorState.getInt("selectionStart").coerceIn(0, editor.length()), editorState.getInt("selectionEnd").coerceIn(0, editor.length()))
            editor.post { editor.scrollTo(editorState.getInt("scrollX"), editorState.getInt("scrollY")) }
            editor.isFocusableInTouchMode = !readOnly
            refresh()
            historyRetryUri?.let { showFileAccessIssue(it, editorState.getString("history_issue") ?: "ファイルを再選択してアクセス権を更新してください。") }
        } else if (intent?.action == Intent.ACTION_VIEW || intent?.action == Intent.ACTION_EDIT) {
            intent.data?.let { load(it, false) }
        } else {
            refresh()
            val previous = prefs.getString("last_document_uri", null)
                ?: RecentFilesStore(this).all().firstOrNull()?.uri
            previous?.let { load(Uri.parse(it), false, fromHistory = true) }
        }
        if (editorState != null) setPreview(editorState.getBoolean("markdown_preview", false))
        else updatePreviewTabs() // load() already selected and rendered the initial document.
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if ((intent.action == Intent.ACTION_VIEW || intent.action == Intent.ACTION_EDIT) && intent.data != null) {
            confirmDiscard { load(intent.data!!, false) }
        }
    }

    override fun onSaveInstanceState(androidState: Bundle) {
        val outState = Bundle()
        outState.putBoolean("markdown_preview", previewVisible)
        outState.putString("local_path", localDocumentPath)
        outState.putString("reconnect_source", reconnectSourceUri?.toString())
        outState.putString("history_retry", historyRetryUri?.toString())
        outState.putString("history_issue", findViewById<TextView>(R.id.searchNotice).text.toString())
        outState.putFloat("zoom_percent", zoomPercent)
        outState.putString("uri", document?.toString())
        outState.putString("name", name)
        outState.putString("text", editor.text.toString())
        outState.putBoolean("dirty", dirty)
        outState.putBoolean("readonly", readOnly)
        outState.putString("original", originalSource)
        outState.putStringArrayList("undo", ArrayList(undo))
        outState.putStringArrayList("redo", ArrayList(redo))
        outState.putString("query", query)
        outState.putBoolean("matchCase", matchCase)
        outState.putBoolean("regex", useRegex)
        outState.putBoolean("wrapSearch", wrapSearch)
        outState.putInt("selectionStart", editor.selectionStart)
        outState.putInt("selectionEnd", editor.selectionEnd)
        outState.putInt("scrollX", editor.scrollX)
        outState.putInt("scrollY", editor.scrollY)
        try {
            EditorStateStore(this).save(stateToken, outState)
            androidState.putString("editor_state_token", stateToken)
        } catch (e: Exception) {
            android.util.Log.e("EditorState", "Unable to save editor snapshot", e)
            message("編集状態を退避できませんでした。未保存の変更を手動で保存してください。")
        }
        super.onSaveInstanceState(androidState)
    }

    override fun onResume() {
        super.onResume()
        if (appearanceDark != EditorAppearance.isDark(this)) { recreate(); return }
        applyEditorSettings()
    }

    override fun onPause() {
        previewLifecycle.pause()
        stopPreview()
        saveZoom()
        super.onPause()
    }

    override fun onPostResume() {
        super.onPostResume()
        previewLifecycle.resume()
        handler.removeCallbacks(renderPreview)
        handler.post(renderPreview)
    }

    private fun stopPreview() {
        handler.removeCallbacks(renderPreview)
        preview?.close()
        preview = null
    }

    private fun scheduleAutosave() {
        handler.removeCallbacks(autosave)
        if (dirty && prefs.getBoolean("autosave", false)) {
            handler.postDelayed(autosave, prefs.getInt("save_minutes", 1).coerceIn(1, 10) * 60_000L)
        }
    }

    private fun setPreview(visible: Boolean) {
        previewLifecycle.select(visible)
        stopPreview()
        if (visible) {
            (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager)
                .hideSoftInputFromWindow(editor.windowToken, 0)
            editor.clearFocus()
            handler.post(renderPreview)
        }
        findViewById<View>(R.id.editorPane).visibility = if (visible) View.GONE else View.VISIBLE
        findViewById<View>(R.id.markdownHost).visibility = if (visible) View.VISIBLE else View.GONE
        findViewById<View>(R.id.toolBar).visibility = if (visible) View.GONE else View.VISIBLE
        updatePreviewTabs()
    }

    private fun updatePreviewTabs() {
        listOf(R.id.markdownEdit to !previewVisible, R.id.markdownPreview to previewVisible).forEach { (id, selected) ->
            findViewById<TextView>(id).apply {
                isSelected = selected
                setTextColor(if (selected) colors.accent else colors.text)
                setTypeface(null, if (selected) android.graphics.Typeface.BOLD else android.graphics.Typeface.NORMAL)
            }
        }
    }

    private fun applyEditorSettings() {
        val c = colors
        fun paint(view: View) {
            // The preview owns its colors; do not traverse WebView implementation children.
            if (view.id == R.id.markdownHost) return
            when (view) {
                is TextView -> { view.setTextColor(c.text); view.compoundDrawableTintList = c.tint }
                is ImageView -> {
                    androidx.core.widget.ImageViewCompat.setImageTintList(view, c.tint)
                    view.setColorFilter(c.secondary)
                }
            }
            if (view is ViewGroup) {
                if (view.background != null) view.setBackgroundColor(c.background)
                for (i in 0 until view.childCount) paint(view.getChildAt(i))
            }
        }
        paint(findViewById(R.id.mainContent))
        colors.applySystemBars(this)
        editor.setTextColor(c.text)
        applyZoom(zoomPercent)
        lines.setTextColor(c.secondary)
        lines.setBackgroundColor(c.gutter)
        location.setTextColor(c.secondary)
        findViewById<TextView>(R.id.fileInfo).setTextColor(c.secondary)
        findViewById<View>(R.id.searchNoticeCard).setBackgroundColor(c.notice)
        editor.setWordWrapEnabled(prefs.getBoolean("wrap", false))
        lines.visibility = if (prefs.getBoolean("numbers", true)) View.VISIBLE else View.GONE
        editor.showLineBreaks = prefs.getBoolean("show_breaks", false)
        editor.showTabs = prefs.getBoolean("show_tabs", false)
        editor.showSpaces = prefs.getBoolean("show_spaces", false)
        editor.pinchZoomEnabled = prefs.getBoolean("pinch_zoom", false)
        editor.markerColor = c.secondary
        editor.updateMarkerPadding()
        editor.invalidate()
        refresh()
        updatePreviewTabs()
        scheduleAutosave()
    }

    private fun refresh() {
        title.text = name + if (dirty) " *" else ""
        location.text = localDocumentPath ?: document?.let { if (it.scheme == "file") it.path else it.toString() } ?: "新しいファイル"
        val output = LineEndings.encode(editor.text.toString(), originalSource)
        editor.lineBreakLabels = LineEndings.labels(output)
        editor.invalidate()
        findViewById<TextView>(R.id.fileInfo).apply {
            text = "UTF-8 · ${LineEndings.label(output)}"
            visibility = View.VISIBLE
        }
        lines.updateText(editor.text ?: "")
        lines.postInvalidate()
        findViewById<View>(R.id.toolUndo).alpha = if (undo.isEmpty()) .35f else 1f
        findViewById<View>(R.id.toolRedo).alpha = if (redo.isEmpty()) .35f else 1f
        findViewById<View>(R.id.toolSave).alpha = if (dirty) 1f else .4f
    }

    private fun replaceText(text: String) {
        changing = true
        editor.setText(text)
        editor.setSelection(0)
        changing = false
        refresh()
    }

    private fun newDocument() {
        setPreview(false)
        document = null
        localDocumentPath = null
        name = "無題.txt"
        originalSource = ""
        dirty = false
        undo.clear(); redo.clear()
        replaceText("")
    }

    private fun open() = confirmDiscard {
        openLauncher.launch(arrayOf("text/*", "application/json", "application/xml", "application/octet-stream"))
    }

    private fun showFileMenu() {
        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(colors.surface)
            elevation = dp(8).toFloat()
        }
        val popup = PopupWindow(column, dp(188), LinearLayout.LayoutParams.WRAP_CONTENT, true).apply {
            setBackgroundDrawable(android.graphics.drawable.ColorDrawable(colors.surface))
            isOutsideTouchable = true
            elevation = dp(8).toFloat()
        }
        fun item(icon: Int, label: String, enabled: Boolean = true, action: () -> Unit) {
            val row = LinearLayout(this).apply {
                gravity = Gravity.CENTER_VERTICAL
                orientation = LinearLayout.HORIZONTAL
                minimumHeight = dp(49)
                alpha = if (enabled) 1f else .35f
                setOnClickListener { if (enabled) { popup.dismiss(); action() } }
            }
            row.addView(ImageView(this).apply {
                setImageResource(icon)
                setColorFilter(colors.secondary)
                setPadding(dp(12), dp(12), dp(12), dp(12))
                contentDescription = label
            }, LinearLayout.LayoutParams(dp(48), dp(48)))
            row.addView(TextView(this).apply {
                text = label
                textSize = 16f
                setTextColor(colors.text)
            })
            column.addView(row)
        }
        item(R.drawable.ic_add, "新規作成") { confirmDiscard { newDocument() } }
        item(R.drawable.ic_folder_open, "開く") { open() }
        item(R.drawable.ic_save, "保存", dirty) { save(false) }
        item(R.drawable.ic_content_copy, "名前を付けて保存") { save(true) }
        item(R.drawable.ic_refresh, "再読み込み", document != null) { reload() }
        item(R.drawable.ic_close, "閉じる") { confirmDiscard { newDocument() } }
        val statusBarHeight = resources.getIdentifier("status_bar_height", "dimen", "android")
            .takeIf { it != 0 }?.let { resources.getDimensionPixelSize(it) } ?: dp(24)
        popup.showAtLocation(findViewById(R.id.openTop), Gravity.TOP or Gravity.RIGHT, 0, statusBarHeight)
        column.alpha = 0f
        column.animate().alpha(1f).setDuration(120).start()
    }

    private fun reload() {
        val uri = document ?: return
        confirmDiscard { load(uri, false) }
    }

    private fun load(uri: Uri, persist: Boolean, fromHistory: Boolean = false): Boolean {
        return try {
            if (persist) persistDocumentAccess(uri)
            val cached = RecentFilesStore(this).all().firstOrNull { it.uri == uri.toString() }?.localPath
                ?: prefs.getString("last_document_path", null).takeIf { prefs.getString("last_document_uri", null) == uri.toString() }
            val opened = LocalDocumentAccess.read(this, uri, cached)
            val text = opened.text
            setPreview(false)
            document = uri
            localDocumentPath = opened.path
            name = opened.path?.let { File(it).name } ?: displayName(uri)
            dirty = false
            undo.clear(); redo.clear()
            originalSource = text
            replaceText(LineEndings.normalize(text))
            rememberRecent(uri)
            historyRetryUri = null
            findViewById<View>(R.id.searchNoticeCard).visibility = View.GONE
            if (persist && uri.scheme == "content" && !hasPersistentReadAccess(uri) && !(StorageAccess.granted(this) && localDocumentPath != null)) {
                showFileAccessIssue(uri, "このファイルのアクセス権を保持できませんでした。再選択からファイルを選び直してください。")
            }
            editor.alpha = .78f
            editor.animate().alpha(1f).setDuration(140).start()
            setPreview(MarkdownRenderer.previewOnOpen(name))
            true
        } catch (e: Exception) {
            if (fromHistory) showFileAccessIssue(uri, if (!StorageAccess.granted(this)) "すべてのファイルへのアクセスを許可してください。" else "ファイルを開けませんでした。移動・削除、またはファイルの提供元を確認してください。")
            else message("開けませんでした: ${e.localizedMessage}")
            false
        }
    }

    private fun showFileAccessIssue(uri: Uri, text: String) {
        searchMessage(text)
        historyRetryUri = uri
        findViewById<TextView>(R.id.reconnectFile).apply {
            this.text = if (StorageAccess.granted(this@MainActivity)) "再選択" else "アクセスを許可"
            setTextColor(colors.accent); visibility = View.VISIBLE
        }
    }

    private fun persistDocumentAccess(uri: Uri) {
        if (uri.scheme == "content") DocumentGrantPolicy.acquire { flags -> contentResolver.takePersistableUriPermission(uri, flags) }
    }

    private fun hasPersistentReadAccess(uri: Uri) = contentResolver.persistedUriPermissions.any { it.uri == uri && it.isReadPermission }

    private fun rememberRecent(uri: Uri) {
        persistDocumentAccess(uri)
        RecentFilesStore(this).record(uri.toString(), name, localDocumentPath)
        prefs.edit().putString("last_document_uri", uri.toString())
            .putString("last_document_path", localDocumentPath).apply()
    }

    private fun displayName(uri: Uri): String {
        if (uri.scheme == "file") return uri.lastPathSegment ?: "無題.txt"
        return try {
            contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            } ?: uri.lastPathSegment ?: "無題.txt"
        } catch (_: Exception) { uri.lastPathSegment ?: "無題.txt" }
    }

    private fun save(asNew: Boolean) {
        if (asNew || document == null) { saveLauncher.launch(name); return }
        try {
            val uri = document!!
            val output = LineEndings.encode(editor.text.toString(), originalSource)
            LocalDocumentAccess.write(this, uri, localDocumentPath, output)
            localDocumentPath = LocalDocumentAccess.path(this, uri, localDocumentPath)
            originalSource = output
            dirty = false
            rememberRecent(uri)
            refresh()
            message("保存しました")
        } catch (e: Exception) {
            AlertDialog.Builder(this).setMessage("上書きできませんでした。別名で保存しますか？\n${e.localizedMessage}")
                .setPositiveButton("別名で保存") { _, _ -> save(true) }.setNegativeButton("キャンセル", null).show()
        }
    }

    private fun confirmDiscard(action: () -> Unit) {
        if (!dirty) { action(); return }
        AlertDialog.Builder(this).setTitle("未保存の変更")
            .setMessage("変更が保存されていません。破棄して続行しますか？")
            .setPositiveButton("破棄") { _, _ -> action() }
            .setNegativeButton("キャンセル", null).show()
    }

    private fun clearEditorText() {
        if (readOnly) { searchMessage(getString(R.string.clear_text_read_only)); return }
        if (editor.text.isNullOrEmpty()) return
        AlertDialog.Builder(this)
            .setTitle(R.string.clear_text_title)
            .setMessage(R.string.clear_text_confirm)
            .setNegativeButton(R.string.clear_text_cancel, null)
            .setPositiveButton(R.string.clear_text_action) { _, _ ->
                if (!readOnly) editor.text?.clear()
            }.show()
    }

    private fun history(from: ArrayDeque<String>, to: ArrayDeque<String>) {
        if (from.isEmpty()) return
        to.addLast(editor.text.toString())
        replaceText(from.removeLast())
        dirty = true
        refresh()
    }

    private fun searchDialog() {
        setPreview(false)
        val panel = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(22), dp(8), dp(22), 0) }
        val search = EditText(this).apply { hint = "検索テキスト"; setSingleLine(true); setText(query) }
        val replacement = EditText(this).apply { hint = "以下に置き換え"; setSingleLine(true) }
        val case = CheckBox(this).apply { text = "大文字と小文字を区別"; isChecked = matchCase }
        val regex = CheckBox(this).apply { text = "正規表現"; isChecked = useRegex }
        val wrap = CheckBox(this).apply { text = "一周する"; isChecked = wrapSearch }
        listOf(search, replacement, case, regex, wrap).forEach(panel::addView)
        val status = TextView(this).apply {
            text = ""
            textSize = 14f
            setTextColor(colors.secondary)
            setPadding(0, dp(8), 0, dp(8))
            accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
        }
        val statusCard = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            visibility = View.GONE
        }
        statusCard.addView(status, LinearLayout.LayoutParams(0, -2, 1f))
        statusCard.addView(ImageView(this).apply {
            setImageResource(R.drawable.ic_close)
            scaleType = ImageView.ScaleType.CENTER
            contentDescription = "検索結果を閉じる"
            isFocusable = true
            setOnClickListener { statusCard.visibility = View.GONE }
        }, LinearLayout.LayoutParams(dp(48), dp(48)))
        panel.addView(statusCard)
        searchCardStatus = status
        val dialog = AlertDialog.Builder(this).setTitle("検索").setView(panel).setNegativeButton("閉じる", null).create()
        dialog.setOnDismissListener { if (searchCardStatus === status) searchCardStatus = null }
        fun options() { query = search.text.toString(); matchCase = case.isChecked; useRegex = regex.isChecked; wrapSearch = wrap.isChecked }
        val actions = LinearLayout(this).apply { gravity = Gravity.END; setPadding(dp(12), 0, dp(12), dp(8)) }
        fun add(label: String, click: () -> Unit) {
            actions.addView(TextView(this).apply {
                text = label; setTextColor(colors.accent); setPadding(dp(8), dp(12), dp(8), dp(12))
                setOnClickListener { statusCard.visibility = View.VISIBLE; options(); click() }
            })
        }
        add("すべて置換") { replaceAll(replacement.text.toString()) }
        add("置換") { replaceOne(replacement.text.toString()) }
        add("検索") { findMatch(true) }
        panel.addView(actions)
        dialog.show()
    }

    private fun pattern(): Pattern? = try {
        if (query.isEmpty()) { searchMessage("検索テキストを入力してください"); null }
        else SearchEngine.compile(query, useRegex, matchCase)
    } catch (e: java.util.regex.PatternSyntaxException) {
        searchMessage("正規表現に誤りがあります: ${e.description}（位置 ${e.index + 1}）")
        null
    }

    private fun findMatch(forward: Boolean) {
        val p = pattern() ?: return
        val text = editor.text.toString()
        val matcher = p.matcher(text)
        var range: IntRange? = null
        if (forward) {
            val start = editor.selectionEnd.coerceAtLeast(0)
            if (matcher.find(start) || (wrapSearch && matcher.find(0))) range = matcher.start() until matcher.end()
        } else {
            val end = editor.selectionStart.coerceAtLeast(0)
            while (matcher.find()) { if (matcher.end() <= end) range = matcher.start() until matcher.end() else break }
            if (range == null && wrapSearch) { matcher.reset(); while (matcher.find()) range = matcher.start() until matcher.end() }
        }
        if (range == null) searchMessage("一致するテキストが見つかりませんでした")
        else {
            editor.requestFocus(); editor.setSelection(range.first, range.last + 1)
            val line = text.take(range.first).count { it == '\n' } + 1
            searchMessage("${line}行目に一致しました（${range.last + 1 - range.first}文字）")
        }
    }

    private fun replaceOne(with: String) {
        val p = pattern() ?: return
        val start = editor.selectionStart
        val end = editor.selectionEnd
        if (start < 0 || end < start) { findMatch(true); return }
        try {
            val result = SearchEngine.replaceSelection(p, editor.text.toString(), start, end, with, useRegex)
            if (result == null) { findMatch(true); return }
            editor.text?.replace(start, end, result)
            findMatch(true)
        } catch (_: Exception) { searchMessage("置換式を確認してください（グループ参照は \$1 形式です）") }
    }

    private fun replaceAll(with: String) {
        val p = pattern() ?: return
        val old = editor.text.toString()
        val matcher = p.matcher(old)
        if (!matcher.find()) { searchMessage("一致するテキストが見つかりませんでした"); return }
        try {
            val result = SearchEngine.replaceAll(p, old, with, useRegex)
            editor.setText(result); searchMessage("置換しました")
        } catch (_: Exception) { searchMessage("置換に失敗しました。置換式のグループ参照などを確認してください") }
    }

    private fun showMore(anchor: View) {
        PopupMenu(this, anchor).apply {
            listOf("検索と置換", "共有", "行番号へ行く", "統計", "折り返し", "読み取り専用", "拡大率")
                .forEachIndexed { index, label -> menu.add(0, index, index, label) }
            setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    0 -> searchDialog()
                    1 -> share()
                    2 -> goToLine()
                    3 -> stats()
                    4 -> { val wrap = !prefs.getBoolean("wrap", false); prefs.edit().putBoolean("wrap", wrap).apply(); editor.setWordWrapEnabled(wrap) }
                    5 -> { readOnly = !readOnly; editor.isFocusableInTouchMode = !readOnly; message(if (readOnly) "読み取り専用" else "編集できます") }
                    6 -> showZoomDialog()
                }
                true
            }
            show()
        }
    }

    private fun showZoomDialog() {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(12), dp(16), dp(8))
        }
        val input = EditText(this).apply {
            setSingleLine(true)
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            imeOptions = EditorInfo.IME_ACTION_DONE
            gravity = Gravity.CENTER
            setTextColor(colors.text)
            setText(zoomPercent.roundToInt().toString())
            contentDescription = "拡大率 パーセント"
        }
        fun applyInput(): Boolean {
            val number = input.text.toString().toFloatOrNull()
            if (number == null || number !in EditorZoom.MIN..EditorZoom.MAX) {
                input.error = "25〜400で入力してください"
                return false
            }
            applyZoom(number)
            saveZoom()
            input.error = null
            return true
        }
        fun step(delta: Int) {
            val value = input.text.toString().toFloatOrNull() ?: zoomPercent
            applyZoom(value + delta)
            saveZoom()
            input.error = null
            input.setText(zoomPercent.roundToInt().toString())
        }
        fun zoomButton(icon: Int, label: String, action: () -> Unit) = ImageView(this).apply {
            setImageResource(icon); setColorFilter(colors.secondary); scaleType = ImageView.ScaleType.CENTER
            contentDescription = label; isFocusable = true; setOnClickListener { action() }
        }
        row.addView(zoomButton(R.drawable.ic_remove, "縮小") { step(-10) }, LinearLayout.LayoutParams(dp(48), dp(48)))
        row.addView(input, LinearLayout.LayoutParams(0, dp(48), 1f))
        row.addView(TextView(this).apply { text = "%"; textSize = 16f; setTextColor(colors.text) })
        row.addView(zoomButton(R.drawable.ic_add, "拡大") { step(10) }, LinearLayout.LayoutParams(dp(48), dp(48)))
        val dialog = AlertDialog.Builder(this).setTitle("拡大率").setView(row)
            .setPositiveButton("完了", null).create()
        input.setOnEditorActionListener { _, action, _ ->
            if (action == EditorInfo.IME_ACTION_DONE) {
                if (applyInput()) {
                    (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).hideSoftInputFromWindow(input.windowToken, 0)
                    dialog.dismiss()
                }
                true
            } else false
        }
        input.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val number = s.toString().toFloatOrNull()
                if (number != null && number in EditorZoom.MIN..EditorZoom.MAX) { applyZoom(number); saveZoom(); input.error = null }
            }
        })
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener { if (applyInput()) dialog.dismiss() }
        }
        dialog.show()
    }

    private fun saveZoom() { prefs.edit().putFloat("zoom_percent", zoomPercent).apply() }

    private fun applyZoom(percent: Float, focusX: Float = 0f, focusY: Float = 0f) {
        val previous = zoomPercent
        zoomPercent = EditorZoom.clamp(percent)
        val ratio = zoomPercent / previous
        val x = ((editor.scrollX + focusX) * ratio - focusX).toInt().coerceAtLeast(0)
        val y = ((editor.scrollY + focusY) * ratio - focusY).toInt().coerceAtLeast(0)
        val size = EditorZoom.scale(prefs.getFloat("size", 16f), zoomPercent)
        editor.setTextSize(size)
        lines.setTextSize(size)
        editor.updateMarkerPadding()
        val spacing = EditorZoom.scale(dp(prefs.getInt("spacing", 2)).toFloat(), zoomPercent)
        editor.setLineSpacing(spacing, 1f)
        lines.setLineSpacing(spacing, 1f)
        lines.layoutParams = lines.layoutParams.apply { width = maxOf(dp(31), lines.paint.measureText(lines.text.toString().substringAfterLast('\n')).toInt() + dp(8)) }
        if (ratio != 1f) editor.post {
            val maxY = ((editor.layout?.height ?: 0) + editor.totalPaddingTop + editor.totalPaddingBottom - editor.height).coerceAtLeast(0)
            editor.scrollTo(if (prefs.getBoolean("wrap", false)) 0 else x, y.coerceAtMost(maxY))
        }
    }

    private fun showNavigation() {
        if (navigationOverlay != null) return
        (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager)
            .hideSoftInputFromWindow(editor.windowToken, 0)
        if (editor.selectionEnd >= 0) editor.setSelection(editor.selectionEnd)
        editor.clearFocus()
        editor.isCursorVisible = false
        findViewById<View>(R.id.mainContent).requestFocus()
        val host = findViewById<FrameLayout>(android.R.id.content)
        val overlay = FrameLayout(this)
        val shade = View(this).apply {
            setBackgroundColor(0x66000000)
            alpha = 0f
            contentDescription = "メニューを閉じる"
            setOnClickListener { closeNavigation() }
        }
        overlay.addView(shade, FrameLayout.LayoutParams(-1, -1))
        val cardWidth = minOf(dp(300), (resources.displayMetrics.widthPixels * .84f).toInt())
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = android.graphics.drawable.GradientDrawable().apply {
                setColor(this@MainActivity.colors.surface)
                cornerRadii = floatArrayOf(0f, 0f, dp(16).toFloat(), dp(16).toFloat(), dp(16).toFloat(), dp(16).toFloat(), 0f, 0f)
            }
            clipToOutline = true
            elevation = dp(12).toFloat()
            isClickable = true
            translationX = -cardWidth.toFloat()
        }
        val heading = TextView(this).apply {
            text = "TextEditor"
            textSize = 21f
            setTextColor(colors.text)
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(24), 0, dp(24), 0)
            setBackgroundColor(colors.notice)
            androidx.core.view.ViewCompat.setAccessibilityHeading(this, true)
        }
        card.addView(heading, LinearLayout.LayoutParams(-1, dp(96)))
        val list = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(8), dp(12), dp(8), dp(16))
        }
        val items = arrayOf("新規作成", "ファイルを開く", "最近使用したファイル", "共有", "設定", "ヘルプ")
        items.forEachIndexed { index, label ->
            list.addView(TextView(this).apply {
                text = label
                textSize = 16f
                setTextColor(colors.text)
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(16), 0, dp(16), 0)
                isFocusable = true
                val value = android.util.TypedValue()
                theme.resolveAttribute(android.R.attr.selectableItemBackground, value, true)
                setBackgroundResource(value.resourceId)
                setOnClickListener { closeNavigation { navigationAction(index) } }
            }, LinearLayout.LayoutParams(-1, dp(56)))
        }
        card.addView(ScrollView(this).apply { addView(list) }, LinearLayout.LayoutParams(-1, 0, 1f))
        overlay.addView(card, FrameLayout.LayoutParams(cardWidth, -1, Gravity.LEFT))
        navigationOverlay = overlay
        navigationCard = card
        host.addView(overlay, FrameLayout.LayoutParams(-1, -1))
        host.getChildAt(0).importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        card.animate().translationX(0f).setDuration(320).start()
        shade.animate().alpha(1f).setDuration(320).start()
        heading.sendAccessibilityEvent(android.view.accessibility.AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED)
    }

    private fun closeNavigation(after: () -> Unit = {}) {
        val overlay = navigationOverlay ?: return
        if (navigationClosing) return
        navigationClosing = true
        overlay.getChildAt(0).animate().alpha(0f).setDuration(280).start()
        navigationCard?.animate()?.translationX(-navigationCard!!.width.toFloat())?.setDuration(280)?.withEndAction {
            val host = findViewById<FrameLayout>(android.R.id.content)
            host.removeView(overlay)
            host.getChildAt(0).importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_AUTO
            navigationOverlay = null
            navigationCard = null
            navigationClosing = false
            findViewById<View>(R.id.menuButton).requestFocus()
            after()
        }?.start()
    }

    private fun navigationAction(index: Int) {
        when (index) {
                0 -> confirmDiscard { newDocument() }
                1 -> open()
                2 -> recentLauncher.launch(Intent(this, RecentFilesActivity::class.java))
                3 -> share()
                4 -> settings()
                5 -> startActivity(Intent(this, HelpActivity::class.java))
        }
    }

    private fun settings() {
        startActivity(Intent(this, SettingsActivity::class.java))
    }

    private fun goToLine() {
        setPreview(false)
        val input = EditText(this).apply { inputType = android.text.InputType.TYPE_CLASS_NUMBER; hint = "行番号" }
        AlertDialog.Builder(this).setTitle("行番号へ行く").setView(input).setPositiveButton("移動") { _, _ ->
            val n = (input.text.toString().toIntOrNull() ?: 1).coerceAtLeast(1)
            var pos = 0
            repeat((n - 1).coerceAtMost(editor.lineCount)) { val next = editor.text.toString().indexOf('\n', pos); if (next >= 0) pos = next + 1 }
            editor.requestFocus(); editor.setSelection(pos)
        }.setNegativeButton("キャンセル", null).show()
    }

    private fun stats() {
        val text = editor.text.toString()
        AlertDialog.Builder(this).setTitle("統計").setMessage("行数: ${text.count { it == '\n' } + 1}\n文字数: ${text.length}\n単語数: ${text.trim().split(Regex("\\s+")).count { it.isNotBlank() }}")
            .setPositiveButton("閉じる", null).show()
    }

    private fun share() {
        try { startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, editor.text.toString()), "テキストを共有")) }
        catch (_: ActivityNotFoundException) { message("共有できるアプリがありません") }
    }

    private fun keyboard() { (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).showSoftInput(editor, InputMethodManager.SHOW_IMPLICIT) }
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
    private fun message(text: String) = Toast.makeText(this, text, Toast.LENGTH_SHORT).show()
    override fun onDestroy() {
        stopPreview()
        handler.removeCallbacks(autosave)
        if (isFinishing) EditorStateStore(this).delete(stateToken)
        super.onDestroy()
    }
}
