package com.example.helloworld

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.GestureDetector
import android.view.ViewConfiguration
import android.widget.OverScroller
import androidx.appcompat.widget.AppCompatEditText

/** Draw display-only markers without altering the editable text, clipboard, or saved bytes. */
class WhitespaceEditText @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : AppCompatEditText(context, attrs) {
    var showLineBreaks = false
    var showTabs = false
    var showSpaces = false
    var lineBreakLabels: List<String> = emptyList()
    var markerColor = 0xff888888.toInt()
    private val markerPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var basePaddingRight: Int? = null
    private val flingScroller = OverScroller(context)
    private val touchConfig = ViewConfiguration.get(context)
    private var selectionGesture = false
    private val flingDetector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onDown(e: MotionEvent): Boolean {
            selectionGesture = hasSelection()
            return true
        }

        override fun onLongPress(e: MotionEvent) { selectionGesture = true }

        override fun onFling(first: MotionEvent?, last: MotionEvent, velocityX: Float, velocityY: Float): Boolean {
            if (selectionGesture || hasSelection() || pinching || last.pointerCount != 1 ||
                kotlin.math.abs(velocityY) <= kotlin.math.abs(velocityX) ||
                kotlin.math.abs(velocityY) < touchConfig.scaledMinimumFlingVelocity) return false
            val maxY = verticalScrollLimit()
            if (maxY == 0) return false
            flingScroller.fling(scrollX, scrollY.coerceIn(0, maxY), 0,
                (-velocityY).toInt().coerceIn(-touchConfig.scaledMaximumFlingVelocity, touchConfig.scaledMaximumFlingVelocity),
                scrollX, scrollX, 0, maxY)
            postInvalidateOnAnimation()
            return true
        }
    })

    private fun verticalScrollLimit(): Int =
        ((layout?.height ?: 0) - (height - totalPaddingTop - totalPaddingBottom)).coerceAtLeast(0)

    private fun stopFling() { flingScroller.forceFinished(true) }

    override fun computeScroll() {
        if (flingScroller.computeScrollOffset()) {
            scrollTo(scrollX, flingScroller.currY.coerceIn(0, verticalScrollLimit()))
            postInvalidateOnAnimation()
        } else super.computeScroll()
    }

    override fun onDetachedFromWindow() {
        stopFling()
        super.onDetachedFromWindow()
    }

    fun updateMarkerPadding() {
        val base = basePaddingRight ?: paddingRight.also { basePaddingRight = it }
        val extra = if (showLineBreaks) kotlin.math.ceil(maxOf(paint.measureText("←"), paint.measureText("↵"), paint.measureText("CR") * .6f) + resources.displayMetrics.density * 2).toInt() else 0
        if (paddingRight != base + extra) setPadding(paddingLeft, paddingTop, base + extra, paddingBottom)
    }

    fun setWordWrapEnabled(enabled: Boolean) {
        stopFling()
        setHorizontallyScrolling(!enabled)
        if (enabled) scrollTo(0, scrollY)
        requestLayout()
        invalidate()
    }
    var onZoomRequested: ((Float, Float, Float) -> Unit)? = null
    var onZoomFinished: (() -> Unit)? = null
    private var pinching = false
    var pinchZoomEnabled = false
        set(value) {
            field = value
            if (!value) {
                pinching = false
                parent?.requestDisallowInterceptTouchEvent(false)
            }
        }
    private val scaleDetector = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            if (pinchZoomEnabled) onZoomRequested?.invoke(detector.scaleFactor, detector.focusX, detector.focusY)
            return true
        }
    }).apply { isQuickScaleEnabled = false; isStylusScaleEnabled = false }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_DOWN) stopFling()
        if (event.pointerCount > 1) {
            stopFling()
            selectionGesture = true
            val cancelFling = MotionEvent.obtain(event).apply { action = MotionEvent.ACTION_CANCEL }
            flingDetector.onTouchEvent(cancelFling)
            cancelFling.recycle()
        }
        if (!pinchZoomEnabled) {
            val handled = super.onTouchEvent(event)
            flingDetector.onTouchEvent(event)
            return handled
        }
        if (event.pointerCount > 1 && !pinching && onZoomRequested != null) {
            pinching = true
            val cancel = MotionEvent.obtain(event).apply { action = MotionEvent.ACTION_CANCEL }
            super.onTouchEvent(cancel)
            cancel.recycle()
            parent.requestDisallowInterceptTouchEvent(true)
        }
        scaleDetector.onTouchEvent(event)
        if (pinching) {
            if (event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_CANCEL) {
                pinching = false
                parent.requestDisallowInterceptTouchEvent(false)
                onZoomFinished?.invoke()
            }
            return true
        }
        val handled = super.onTouchEvent(event)
        flingDetector.onTouchEvent(event)
        return handled
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (!showLineBreaks && !showTabs && !showSpaces) return
        val textLayout = layout ?: return
        val value = text ?: return
        markerPaint.set(paint)
        markerPaint.color = markerColor
        markerPaint.alpha = 170
        // getLineBounds includes TextView's top padding and vertical-gravity offset.
        val textTop = getLineBounds(0, null) - textLayout.getLineBaseline(0)
        val first = textLayout.getLineForVertical((scrollY + extendedPaddingTop - textTop).coerceAtLeast(0))
        val last = textLayout.getLineForVertical((scrollY + height - extendedPaddingBottom - textTop).coerceAtLeast(0))
        val saved = canvas.save()
        val clip = MarkerGeometry.clip(scrollX, scrollY, width, height, compoundPaddingLeft, extendedPaddingTop,
            basePaddingRight ?: compoundPaddingRight, extendedPaddingBottom)
        canvas.clipRect(clip.left, clip.top, clip.right, clip.bottom)
        // The View canvas already has -scrollX/-scrollY applied. Do not subtract again.
        var breakIndex = MarkerGeometry.breakIndex(value, textLayout.getLineStart(first))
        for (line in first..last) {
            val baseline = MarkerGeometry.y(textLayout.getLineBaseline(line), textTop)
            for (offset in textLayout.getLineStart(line) until textLayout.getLineEnd(line).coerceAtMost(value.length)) {
                val mark = when (value[offset]) {
                    '\n' -> {
                        val label = lineBreakLabels.getOrElse(breakIndex++) { "LF" }
                        if (showLineBreaks) LineEndings.marker(label) else null
                    }
                    '\t' -> if (showTabs) "→" else null
                    ' ' -> if (showSpaces) "·" else null
                    '\u3000' -> if (showSpaces) "□" else null
                    else -> null
                }
                if (mark != null) {
                    markerPaint.textSize = if (mark == "CR") textSize * .6f else textSize
                    canvas.drawText(mark, MarkerGeometry.x(textLayout.getPrimaryHorizontal(offset), compoundPaddingLeft), baseline, markerPaint)
                }
            }
        }
        canvas.restoreToCount(saved)
    }
}
