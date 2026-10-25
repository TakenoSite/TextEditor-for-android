package com.example.helloworld

/** onDraw uses scrolled content coordinates, not screen/viewport coordinates. */
internal object MarkerGeometry {
    data class Clip(val left: Int, val top: Int, val right: Int, val bottom: Int)
    fun clip(scrollX: Int, scrollY: Int, width: Int, height: Int, left: Int, top: Int, right: Int, bottom: Int) =
        Clip(scrollX + left, scrollY + top, scrollX + width - right, scrollY + height - bottom)

    fun x(layoutX: Float, paddingLeft: Int): Float = layoutX + paddingLeft
    fun y(layoutBaseline: Int, textTop: Int): Float = (layoutBaseline + textTop).toFloat()
    fun breakIndex(text: CharSequence, visualLineStart: Int): Int {
        var index = 0
        for (offset in 0 until visualLineStart.coerceIn(0, text.length)) if (text[offset] == '\n') index++
        return index
    }
}
