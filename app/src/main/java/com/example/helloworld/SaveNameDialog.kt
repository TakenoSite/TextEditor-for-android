package com.example.helloworld

import android.app.Dialog
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.RippleDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.Window
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatDialog
import androidx.appcompat.widget.AppCompatButton
import androidx.appcompat.widget.AppCompatEditText
import androidx.fragment.app.DialogFragment

/** Fragment arguments and saved state retain name/created URI through rotation. */
class SaveNameDialog : DialogFragment() {
    private lateinit var input: AppCompatEditText

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val context = requireContext()
        val colors = EditorAppearance(EditorAppearance.isDark(context))
        fun dp(v: Int) = AppChoiceUi.dp(context, v)
        val content = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL; setPadding(dp(24), dp(24), dp(24), dp(24))
        }
        content.addView(TextView(context).apply {
            text = getString(if (requireArguments().containsKey("uri")) R.string.save_name_confirm else R.string.action_save_as)
            textSize = 22f; setTextColor(colors.text)
            androidx.core.view.ViewCompat.setAccessibilityHeading(this, true)
        })
        requireArguments().getString("actual")?.let { actual ->
            content.addView(TextView(context).apply {
                text = getString(R.string.save_name_changed, actual); textSize = 14f; setTextColor(colors.secondary)
                setPadding(0, dp(12), 0, dp(8))
            })
        }
        input = AppCompatEditText(context).apply {
            setText(savedInstanceState?.getString("name") ?: requireArguments().getString("name"))
            setSingleLine(true); textSize = 16f; setTextColor(colors.text); setHintTextColor(colors.secondary)
            setHint(R.string.save_filename); contentDescription = getString(R.string.save_filename)
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            setPadding(dp(14), dp(14), dp(14), dp(14))
            background = AppChoiceUi.shape(context, colors.background, colors.secondary, 12)
            setSelection(text?.length ?: 0)
        }
        content.addView(input, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(20); bottomMargin = dp(20) })
        val buttons = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        fun button(label: Int, primary: Boolean, click: () -> Unit) = AppCompatButton(context).apply {
            setText(label); isAllCaps = false; textSize = 16f; minimumHeight = dp(52)
            backgroundTintList = null; supportBackgroundTintList = null
            setTextColor(if (primary) { if (colors.dark) Color.parseColor("#202020") else Color.WHITE } else colors.text)
            background = RippleDrawable(ColorStateList.valueOf(colors.notice),
                AppChoiceUi.shape(context, if (primary) colors.accent else colors.surface), null)
            setOnClickListener { click() }
        }
        buttons.addView(button(if (requireArguments().containsKey("uri")) R.string.action_save else R.string.save_choose_location, true) {
            val filename = input.text.toString()
            if (!DocumentFilePolicy.validName(filename)) { input.error = getString(R.string.save_filename_invalid) }
            else {
                parentFragmentManager.setFragmentResult(RESULT, Bundle().apply {
                    putString("name", filename); putString("uri", requireArguments().getString("uri"))
                })
                dismiss()
            }
        }, LinearLayout.LayoutParams(-1, -2))
        buttons.addView(button(R.string.action_cancel, false) { dismiss() }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })
        content.addView(buttons)
        return AppCompatDialog(context).apply {
            supportRequestWindowFeature(Window.FEATURE_NO_TITLE)
            setContentView(ScrollView(context).apply {
                background = AppChoiceUi.shape(context, colors.surface, radius = 24); addView(content)
            })
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        if (::input.isInitialized) outState.putString("name", input.text.toString())
        super.onSaveInstanceState(outState)
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            setLayout(minOf(resources.displayMetrics.widthPixels - AppChoiceUi.dp(requireContext(), 40),
                AppChoiceUi.dp(requireContext(), 420)), -2)
        }
    }

    companion object {
        const val RESULT = "save_name_result"
        fun create(name: String, uri: String? = null, actual: String? = null) = SaveNameDialog().apply {
            arguments = Bundle().apply {
                putString("name", name)
                if (uri != null) putString("uri", uri)
                if (actual != null) putString("actual", actual)
            }
        }
    }
}
