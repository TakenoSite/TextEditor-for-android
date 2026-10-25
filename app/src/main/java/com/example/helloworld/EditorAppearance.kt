package com.example.helloworld

import android.app.Activity
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.view.View

internal class EditorAppearance(val dark: Boolean) {
    val background = Color.parseColor(if (dark) "#202124" else "#EFEFEF")
    val surface = Color.parseColor(if (dark) "#292B2F" else "#FAFAFA")
    val text = Color.parseColor(if (dark) "#ECEFF1" else "#242424")
    val secondary = Color.parseColor(if (dark) "#B0B5BC" else "#666666")
    val accent = Color.parseColor(if (dark) "#81C784" else "#388E3C")
    val notice = Color.parseColor(if (dark) "#2D4032" else "#E5EEE5")
    val gutter = Color.parseColor(if (dark) "#27292C" else "#E9E9E9")
    val tint get() = ColorStateList.valueOf(secondary)

    fun applySystemBars(activity: Activity) {
        activity.window.statusBarColor = surface
        activity.window.navigationBarColor = if (android.os.Build.VERSION.SDK_INT < 26) Color.parseColor("#303030") else background
        @Suppress("DEPRECATION")
        activity.window.decorView.systemUiVisibility = if (dark) 0 else
            View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or
                (if (android.os.Build.VERSION.SDK_INT >= 26) View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR else 0)
    }

    companion object {
        fun isDark(context: Context) = context.getSharedPreferences("editor", Context.MODE_PRIVATE).getBoolean("dark", false)
        fun theme(context: Context) = if (isDark(context)) R.style.Theme_Editor_Dark else R.style.Theme_HelloWorld
    }
}
