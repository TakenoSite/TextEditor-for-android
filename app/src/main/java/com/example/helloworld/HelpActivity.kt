package com.example.helloworld

import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.pm.PackageInfoCompat
import androidx.core.view.ViewCompat

class HelpActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(EditorAppearance.theme(this))
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_help)
        val colors = EditorAppearance(EditorAppearance.isDark(this))
        colors.applySystemBars(this)
        findViewById<View>(R.id.helpRoot).setBackgroundColor(colors.background)
        findViewById<View>(R.id.helpHeader).setBackgroundColor(colors.surface)
        findViewById<TextView>(R.id.helpTitle).setTextColor(colors.text)
        findViewById<ImageView>(R.id.helpBack).apply { setColorFilter(colors.secondary); setOnClickListener { finish() } }
        val sections = findViewById<LinearLayout>(R.id.helpSections)
        val scroll = findViewById<ScrollView>(R.id.helpScroll)

        fun section(title: Int, linkId: Int): LinearLayout {
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(18), dp(18), dp(18), dp(18))
                background = android.graphics.drawable.GradientDrawable().apply { setColor(colors.surface); cornerRadius = dp(12).toFloat() }
            }
            card.addView(TextView(this).apply {
                setText(title); textSize = 18f; setTextColor(colors.accent)
                setPadding(0, 0, 0, dp(12))
                ViewCompat.setAccessibilityHeading(this, true)
            })
            sections.addView(card, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(16) })
            findViewById<TextView>(linkId).apply {
                setTextColor(colors.accent)
                setOnClickListener { scroll.smoothScrollTo(0, (sections.top + card.top).coerceAtLeast(0)) }
            }
            return card
        }
        fun body(parent: LinearLayout, text: String) {
            parent.addView(TextView(this).apply {
                this.text = text; textSize = 15f; setTextColor(colors.text)
                setLineSpacing(dp(4).toFloat(), 1f)
            })
        }
        val overview = section(R.string.help_overview, R.id.helpOverviewLink)
        body(overview, getString(R.string.help_overview_body))
        val usage = section(R.string.help_usage, R.id.helpUsageLink)
        val steps = listOf(
            R.string.help_files_heading to R.string.help_files_body,
            R.string.help_toolbar_heading to R.string.help_toolbar_body,
            R.string.help_search_heading to R.string.help_search_body,
            R.string.help_recent_heading to R.string.help_recent_body,
            R.string.help_display_heading to R.string.help_display_body,
            R.string.help_autosave_heading to R.string.help_autosave_body,
            R.string.help_markdown_heading to R.string.help_markdown_body
        )
        steps.forEachIndexed { index, (heading, content) ->
            usage.addView(TextView(this).apply {
                setText(heading); textSize = 16f; setTextColor(colors.text)
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                setPadding(0, if (index == 0) 0 else dp(22), 0, dp(8))
                ViewCompat.setAccessibilityHeading(this, true)
            })
            body(usage, getString(content))
        }
        val version = section(R.string.help_version, R.id.helpVersionLink)
        val info = try {
            @Suppress("DEPRECATION")
            val packageInfo = if (Build.VERSION.SDK_INT >= 33) packageManager.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0)) else packageManager.getPackageInfo(packageName, 0)
            getString(R.string.help_version_body, getString(R.string.app_name), packageInfo.versionName ?: "—", PackageInfoCompat.getLongVersionCode(packageInfo).toString())
        } catch (_: PackageManager.NameNotFoundException) { getString(R.string.help_version_unknown) }
        body(version, info)
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
}
