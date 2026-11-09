package com.example.helloworld

import android.content.res.ColorStateList
import android.os.Bundle
import android.view.Gravity
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Spinner
import android.widget.ArrayAdapter
import android.widget.AdapterView
import android.view.View
import androidx.appcompat.app.AppCompatActivity
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
        val radio = RadioGroup(this).apply { id = nextId++; orientation = RadioGroup.VERTICAL }
        val lightId = nextId++
        val darkId = nextId++
        radio.addView(RadioButton(this).apply { id = lightId; setText(R.string.settings_light); setTextColor(colors.text); minimumHeight = dp(48) })
        radio.addView(RadioButton(this).apply { id = darkId; setText(R.string.settings_dark); setTextColor(colors.text); minimumHeight = dp(48) })
        radio.check(if (colors.dark) darkId else lightId)
        radio.setOnCheckedChangeListener { _, checked ->
            val dark = checked == darkId
            if (dark != colors.dark) { prefs.edit().putBoolean("dark", dark).apply(); recreate() }
        }
        themeCard.addView(radio)

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
        languageCard.addView(Spinner(this, Spinner.MODE_DROPDOWN).apply {
            id = R.id.languageSelector
            contentDescription = getString(R.string.settings_language)
            adapter = ArrayAdapter(this@SettingsActivity, android.R.layout.simple_spinner_item,
                listOf("日本語", "English")).apply { setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
            setSelection(AppLanguage.tags.indexOf(selected), false)
            onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onNothingSelected(parent: AdapterView<*>?) {}
                override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                    val tag = AppLanguage.tags[position]
                    if (tag != AppLanguage.selected(this@SettingsActivity)) {
                        prefs.edit().putString("language", tag).apply()
                        recreate()
                    }
                }
            }
        }, LinearLayout.LayoutParams(-1, dp(56)))
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
