package com.example.helloworld

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.ScrollView
import android.widget.TextView

/** Shared app-styled selectors. No platform Spinner layouts or popup backgrounds. */
internal object AppChoiceUi {
    data class Choice(val label: String, val icon: Int)
    fun dp(context: Context, value: Int) = (context.resources.displayMetrics.density * value).toInt()
    fun shape(context: Context, color: Int, border: Int? = null, radius: Int = 16) = GradientDrawable().apply {
        setColor(color); cornerRadius = dp(context, radius).toFloat()
        border?.let { setStroke(dp(context, 1), it) }
    }
    fun row(context: Context, colors: EditorAppearance, choice: Choice, selected: Boolean, trailing: Int): LinearLayout {
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            minimumHeight = dp(context, 60); setPadding(dp(context, 16), dp(context, 12), dp(context, 16), dp(context, 12))
            background = RippleDrawable(ColorStateList.valueOf(colors.notice),
                shape(context, if (selected) colors.notice else colors.surface), null)
            isFocusable = true
        }
        fun icon(resource: Int, color: Int) = ImageView(context).apply {
            setImageResource(resource); setColorFilter(color)
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }
        row.addView(icon(choice.icon, colors.accent), LinearLayout.LayoutParams(dp(context, 24), dp(context, 24)))
        row.addView(TextView(context).apply {
            text = choice.label; textSize = 16f; setTextColor(colors.text)
            setPadding(dp(context, 14), 0, dp(context, 12), 0)
        }, LinearLayout.LayoutParams(0, -2, 1f))
        row.addView(icon(trailing, if (selected) colors.accent else colors.secondary),
            LinearLayout.LayoutParams(dp(context, 24), dp(context, 24)))
        return row
    }

    fun dropdown(context: Context, colors: EditorAppearance, label: String, choices: List<Choice>, selected: Int, choose: (Int) -> Unit): View {
        val anchor = row(context, colors, choices[selected], false, R.drawable.ic_dropdown)
        anchor.background = RippleDrawable(ColorStateList.valueOf(colors.notice), shape(context, colors.background, colors.secondary), null)
        anchor.contentDescription = "$label: ${choices[selected].label}"
        androidx.core.view.ViewCompat.setStateDescription(anchor, context.getString(R.string.choice_collapsed))
        var popup: PopupWindow? = null
        anchor.setOnClickListener {
            if (popup?.isShowing == true) { popup?.dismiss(); return@setOnClickListener }
            val options = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL; setPadding(dp(context, 8), dp(context, 8), dp(context, 8), dp(context, 8))
            }
            choices.forEachIndexed { index, choice ->
                options.addView(row(context, colors, choice, index == selected,
                    if (index == selected) R.drawable.ic_choice_check else 0).apply {
                    isSelected = index == selected
                    setOnClickListener { popup?.dismiss(); choose(index) }
                }, LinearLayout.LayoutParams(-1, -2))
            }
            val scroll = ScrollView(context).apply { addView(options) }
            popup = PopupWindow(scroll, anchor.width, -2, true).apply {
                setBackgroundDrawable(shape(context, colors.surface)); elevation = dp(context, 12).toFloat()
                isOutsideTouchable = true
                setOnDismissListener {
                    androidx.core.view.ViewCompat.setStateDescription(anchor, context.getString(R.string.choice_collapsed))
                }
                showAsDropDown(anchor, 0, dp(context, 6))
            }
            androidx.core.view.ViewCompat.setStateDescription(anchor, context.getString(R.string.choice_expanded))
        }
        anchor.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) {}
            override fun onViewDetachedFromWindow(v: View) { popup?.dismiss(); popup = null }
        })
        return anchor
    }
}
