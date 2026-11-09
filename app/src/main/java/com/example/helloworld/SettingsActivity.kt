package com.example.helloworld

import android.content.res.ColorStateList
import android.os.Bundle
import android.view.Gravity
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import androidx.appcompat.widget.SwitchCompat

class SettingsActivity : LocalizedActivity() {
    private val prefs by lazy { getSharedPreferences("editor", MODE_PRIVATE) }
    private lateinit var colors: EditorAppearance
    private lateinit var categories: LinearLayout
    private var nextId = 10000
    private var storageButton: TextView? = null
    private val storagePermission = StoragePermissionController(this) { updateStorageAccess() }

    override fun onResume() { super.onResume(); updateStorageAccess() }
    private fun updateStorageAccess() {
        storageButton?.text = getString(if (StorageAccess.granted(this)) R.string.access_granted else R.string.access_allow)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(EditorAppearance.theme(this))
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        colors = EditorAppearance(EditorAppearance.isDark(this))
        colors.applySystemBars(this)
        findViewById<LinearLayout>(R.id.settingsRoot).setBackgroundColor(colors.background)
        findViewById<LinearLayout>(R.id.settingsHeader).setBackgroundColor(colors.surface)
        findViewById<TextView>(R.id.settingsTitle).setTextColor(colors.text)
        findViewById<ImageView>(R.id.settingsBack).apply { setColorFilter(colors.secondary); setOnClickListener { finish() } }
        categories = findViewById(R.id.settingsCategories)
        val themeCard = category(getString(R.string.settings_theme))
        themeCard.addView(AppChoiceUi.dropdown(this, colors, getString(R.string.settings_theme), listOf(
            AppChoiceUi.Choice(getString(R.string.settings_light), R.drawable.ic_theme_light),
            AppChoiceUi.Choice(getString(R.string.settings_dark), R.drawable.ic_theme_dark)
        ), if (colors.dark) 1 else 0) { index ->
            val dark = index == 1
            if (dark != colors.dark) { prefs.edit().putBoolean("dark", dark).apply(); recreate() }
        }, LinearLayout.LayoutParams(-1, -2))

        val display = category(getString(R.string.settings_display))
        toggle(display, getString(R.string.settings_numbers), "numbers", true)
        toggle(display, getString(R.string.word_wrap), "wrap", false)
        toggle(display, getString(R.string.settings_pinch), "pinch_zoom", false)
        slider(display, getString(R.string.settings_font), 10, 32, prefs.getFloat("size", 16f).toInt(), "sp") {
            prefs.edit().putFloat("size", it.toFloat()).apply()
        }
        slider(display, getString(R.string.settings_spacing), 0, 16, prefs.getInt("spacing", 2), "dp") {
            prefs.edit().putInt("spacing", it).apply()
        }

        val symbols = category(getString(R.string.settings_symbols))
        toggle(symbols, getString(R.string.settings_breaks), "show_breaks", false)
        toggle(symbols, getString(R.string.settings_tabs), "show_tabs", false)
        toggle(symbols, getString(R.string.settings_spaces), "show_spaces", false)

        val saving = category(getString(R.string.settings_autosave))
        toggle(saving, getString(R.string.settings_save_changes), "autosave", false)
        slider(saving, getString(R.string.settings_save_delay), 1, 10, prefs.getInt("save_minutes", 1), getString(R.string.minutes_unit)) {
            prefs.edit().putInt("save_minutes", it).apply()
        }
        val files = category(getString(R.string.settings_files))
        storageButton = TextView(this).apply {
            textSize = 16f; setTextColor(colors.accent); minimumHeight = dp(56)
            gravity = Gravity.CENTER_VERTICAL; isFocusable = true
            setOnClickListener { if (!storagePermission.request()) setText(R.string.access_settings_error) }
        }
        files.addView(storageButton, LinearLayout.LayoutParams(-1, -2))
        val languageCard = category(getString(R.string.settings_language))
        val selected = AppLanguage.selected(this)
        languageCard.addView(AppChoiceUi.dropdown(this, colors, getString(R.string.settings_language), listOf(
            AppChoiceUi.Choice("日本語", R.drawable.ic_language), AppChoiceUi.Choice("English", R.drawable.ic_language)
        ), AppLanguage.tags.indexOf(selected)) { index ->
            val tag = AppLanguage.tags[index]
            if (tag != selected) {
                prefs.edit().putString("language", tag).putBoolean("language_setup_complete", true).apply()
                recreate()
            }
        }.apply { id = R.id.languageSelector }, LinearLayout.LayoutParams(-1, -2))
    }

    private fun category(label: String): LinearLayout {
        val section = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(12), dp(16), dp(12))
            background = android.graphics.drawable.GradientDrawable().apply { setColor(this@SettingsActivity.colors.surface); cornerRadius = dp(12).toFloat() }
        }
        section.addView(TextView(this).apply {
            text = label; textSize = 15f; setTextColor(colors.accent); setPadding(0, dp(4), 0, dp(10))
            androidx.core.view.ViewCompat.setAccessibilityHeading(this, true)
        })
        categories.addView(section, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(16) })
        return section
    }

    private fun toggle(parent: LinearLayout, label: String, key: String, default: Boolean) {
        parent.addView(SwitchCompat(this).apply {
            id = nextId++; text = label; textSize = 16f; setTextColor(colors.text)
            gravity = Gravity.CENTER_VERTICAL; minimumHeight = dp(56); setPadding(0, dp(8), 0, dp(8))
            compoundDrawablePadding = dp(12)
            isChecked = prefs.getBoolean(key, default)
            setOnCheckedChangeListener { _, value -> prefs.edit().putBoolean(key, value).apply() }
        }, LinearLayout.LayoutParams(-1, -2))
    }

    private fun slider(parent: LinearLayout, label: String, min: Int, max: Int, initial: Int, unit: String, update: (Int) -> Unit) {
        val value = TextView(this).apply { textSize = 16f; setTextColor(colors.text); setPadding(0, dp(12), 0, 0) }
        value.text = "$label: $initial $unit"
        parent.addView(value)
        parent.addView(SeekBar(this).apply {
            id = nextId++; this.max = max - min; progress = initial.coerceIn(min, max) - min
            contentDescription = label
            progressTintList = ColorStateList.valueOf(colors.accent)
            thumbTintList = ColorStateList.valueOf(colors.accent)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    value.text = "$label: ${progress + min} $unit"
                    if (fromUser) update(progress + min)
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: SeekBar?) {}
            })
        }, LinearLayout.LayoutParams(-1, dp(48)))
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
}
