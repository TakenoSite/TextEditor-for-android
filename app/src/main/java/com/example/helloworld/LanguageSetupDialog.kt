package com.example.helloworld

import android.app.Dialog
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.Window
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatDialog
import androidx.appcompat.widget.AppCompatButton
import androidx.fragment.app.DialogFragment

/** Restorable first-run selection; no device-locale changes and no network calls. */
class LanguageSetupDialog : DialogFragment() {
    private var selected = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        selected = savedInstanceState?.getInt("selected", 0)?.coerceIn(0, 1) ?: 0
        isCancelable = false
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putInt("selected", selected)
        super.onSaveInstanceState(outState)
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val context = requireContext()
        val colors = EditorAppearance(EditorAppearance.isDark(context), monochrome = true)
        fun dp(value: Int) = AppChoiceUi.dp(context, value)
        val content = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(24), dp(24), dp(24))
        }
        content.addView(ImageView(context).apply {
            setImageResource(R.drawable.ic_language); setColorFilter(colors.accent)
            setPadding(dp(12), dp(12), dp(12), dp(12))
            background = AppChoiceUi.shape(context, colors.notice, radius = 18)
            importantForAccessibility = android.view.View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }, LinearLayout.LayoutParams(dp(56), dp(56)))
        fun label(value: String, size: Float, color: Int, bold: Boolean = false) = TextView(context).apply {
            text = value; textSize = size; setTextColor(color)
            if (bold) setTypeface(null, Typeface.BOLD)
            setPadding(0, dp(16), 0, dp(8))
        }
        content.addView(label(getString(R.string.language_welcome), 14f, colors.accent))
        content.addView(label(getString(R.string.language_setup_title), 24f, colors.text, true).apply {
            androidx.core.view.ViewCompat.setAccessibilityHeading(this, true)
        })
        val options = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL; setPadding(0, dp(12), 0, dp(16)) }
        content.addView(options, LinearLayout.LayoutParams(-1, -2))
        fun updateOptions() {
            options.removeAllViews()
            listOf("日本語", "English").forEachIndexed { index, language ->
                options.addView(AppChoiceUi.row(context, colors, AppChoiceUi.Choice(language, R.drawable.ic_language),
                    index == selected, if (index == selected) R.drawable.ic_choice_check else 0).apply {
                    isSelected = index == selected
                    setOnClickListener { selected = index; updateOptions(); options.getChildAt(index).requestFocus() }
                }, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(8) })
            }
        }
        updateOptions()
        content.addView(AppCompatButton(context).apply {
            text = getString(R.string.language_continue); isAllCaps = false; textSize = 16f
            minimumHeight = dp(56); gravity = Gravity.CENTER
            backgroundTintList = null; supportBackgroundTintList = null
            setTextColor(if (colors.dark) Color.parseColor("#202020") else Color.WHITE)
            background = android.graphics.drawable.RippleDrawable(
                android.content.res.ColorStateList.valueOf(colors.notice), AppChoiceUi.shape(context, colors.accent), null)
            setOnClickListener {
                context.getSharedPreferences("editor", android.content.Context.MODE_PRIVATE).edit()
                    .putString("language", AppLanguage.tags[selected]).putBoolean("language_setup_complete", true).apply()
                dismiss()
                activity?.recreate()
            }
        }, LinearLayout.LayoutParams(-1, -2))
        return AppCompatDialog(context).apply {
            supportRequestWindowFeature(Window.FEATURE_NO_TITLE)
            setContentView(ScrollView(context).apply {
                background = AppChoiceUi.shape(context, colors.surface, radius = 24)
                addView(content)
            })
            setCanceledOnTouchOutside(false)
        }
    }

    override fun onStart() {
        super.onStart()
        val metrics = resources.displayMetrics
        dialog?.window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            val width = minOf(metrics.widthPixels - AppChoiceUi.dp(requireContext(), 40), AppChoiceUi.dp(requireContext(), 420))
            setLayout(width, android.view.ViewGroup.LayoutParams.WRAP_CONTENT)
            addFlags(android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            attributes = attributes.apply { dimAmount = .48f }
        }
    }
}
