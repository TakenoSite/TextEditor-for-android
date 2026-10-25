package com.example.helloworld

internal object EditorZoom {
    const val MIN = 25f
    const val MAX = 400f
    fun clamp(percent: Float): Float = if (percent.isFinite()) percent.coerceIn(MIN, MAX) else 100f
    fun scale(base: Float, percent: Float): Float = base * clamp(percent) / 100f
}
