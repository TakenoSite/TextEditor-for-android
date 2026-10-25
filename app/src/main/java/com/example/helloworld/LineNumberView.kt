package com.example.helloworld

import android.content.Context
import android.graphics.Canvas
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatTextView

/** Use the editor's actual baselines, including fallback fonts and wrapped lines. */
class LineNumberView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : AppCompatTextView(context, attrs) {
    var editor: WhitespaceEditText? = null
    private var starts = intArrayOf(0)

    fun updateText(value: CharSequence) {
        starts = LogicalLines.starts(value)
        text = starts.size.toString() // Measurement only; onDraw renders visible logical lines.
        val density = resources.displayMetrics.density
        val required = maxOf((31 * density).toInt(), kotlin.math.ceil(paint.measureText(text.toString()) + 8 * density).toInt())
        layoutParams?.let { if (it.width != required) { it.width = required; layoutParams = it } }
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        val source = editor ?: return
        val layout = source.layout ?: return
        if (layout.lineCount == 0) return
        paint.color = currentTextColor
        val top = source.getLineBounds(0, null) - layout.getLineBaseline(0)
        val first = layout.getLineForVertical((source.scrollY - top).coerceAtLeast(0))
        val last = layout.getLineForVertical((source.scrollY + source.height - top).coerceAtLeast(0))
        for (visualLine in first..last) {
            val logicalLine = starts.binarySearch(layout.getLineStart(visualLine))
            if (logicalLine < 0) continue // Continuation of a wrapped logical line.
            val label = (logicalLine + 1).toString()
            canvas.drawText(label, width - paddingRight - paint.measureText(label),
                (top + layout.getLineBaseline(visualLine) - source.scrollY).toFloat(), paint)
        }
    }
}
