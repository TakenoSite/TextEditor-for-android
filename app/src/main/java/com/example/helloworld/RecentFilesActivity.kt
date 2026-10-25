package com.example.helloworld

import android.content.Intent
import android.os.Bundle
import android.text.TextUtils
import android.view.Gravity
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import java.text.DateFormat
import java.util.Date

class RecentFilesActivity : AppCompatActivity() {
    private val storagePermission = StoragePermissionController(this) { updateStorageAccess() }

    override fun onResume() {
        super.onResume()
        updateStorageAccess()
    }

    private fun updateStorageAccess() {
        val colors = EditorAppearance(EditorAppearance.isDark(this))
        findViewById<TextView>(R.id.recentStorageAccess).apply {
            visibility = if (StorageAccess.granted(this@RecentFilesActivity)) android.view.View.GONE else android.view.View.VISIBLE
            text = "すべてのファイルへのアクセスを許可"
            setTextColor(colors.accent)
            setBackgroundColor(colors.notice)
            setOnClickListener { if (!storagePermission.request()) text = "権限設定を開けませんでした" }
        }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(EditorAppearance.theme(this))
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_recent_files)
        val colors = EditorAppearance(EditorAppearance.isDark(this))
        colors.applySystemBars(this)
        findViewById<LinearLayout>(R.id.recentRoot).setBackgroundColor(colors.background)
        findViewById<LinearLayout>(R.id.recentHeader).setBackgroundColor(colors.surface)
        findViewById<TextView>(R.id.recentTitle).setTextColor(colors.text)
        findViewById<ImageView>(R.id.recentBack).apply { setColorFilter(colors.secondary); setOnClickListener { finish() } }
        val list = findViewById<LinearLayout>(R.id.recentList)
        val files = RecentFilesStore(this).all()
        if (files.isEmpty()) {
            list.addView(TextView(this).apply {
                text = "履歴はありません"; textSize = 16f; setTextColor(colors.secondary)
                gravity = Gravity.CENTER; setPadding(dp(16), dp(48), dp(16), dp(48))
            })
        }
        val dateFormat = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
        for (file in files) {
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(12), dp(12), dp(16), dp(12))
                background = android.graphics.drawable.GradientDrawable().apply { setColor(colors.surface); cornerRadius = dp(12).toFloat() }
                isFocusable = true
                val value = android.util.TypedValue()
                theme.resolveAttribute(android.R.attr.selectableItemBackground, value, true)
                foreground = androidx.appcompat.content.res.AppCompatResources.getDrawable(this@RecentFilesActivity, value.resourceId)
                setOnClickListener {
                    setResult(RESULT_OK, Intent().putExtra(SELECTED_URI, file.uri))
                    finish()
                }
            }
            row.addView(ImageView(this).apply {
                setImageResource(R.drawable.ic_description); setColorFilter(colors.secondary)
                importantForAccessibility = android.view.View.IMPORTANT_FOR_ACCESSIBILITY_NO
            }, LinearLayout.LayoutParams(dp(32), dp(32)).apply { marginEnd = dp(12) })
            val details = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
            details.addView(TextView(this).apply {
                text = file.name; textSize = 17f; setTextColor(colors.text)
                maxLines = 2; ellipsize = TextUtils.TruncateAt.END
            })
            details.addView(TextView(this).apply {
                text = file.localPath ?: file.uri; textSize = 12f; setTextColor(colors.secondary)
                maxLines = 2; ellipsize = TextUtils.TruncateAt.MIDDLE; setPadding(0, dp(4), 0, dp(4))
            })
            details.addView(TextView(this).apply {
                text = dateFormat.format(Date(file.openedAt)); textSize = 12f; setTextColor(colors.secondary)
            })
            row.addView(details, LinearLayout.LayoutParams(0, -2, 1f))
            list.addView(row, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(10) })
        }
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
    companion object { const val SELECTED_URI = "selected_uri" }
}
