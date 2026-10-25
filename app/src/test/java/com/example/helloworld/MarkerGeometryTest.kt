package com.example.helloworld

import org.junit.Assert.*
import org.junit.Test

class MarkerGeometryTest {
    @Test fun frameworkAppliesScrollingExactlyOnce() {
        val x = MarkerGeometry.x(145f, 3)
        val y = MarkerGeometry.y(240, 7)
        assertEquals(148f, x, .001f)
        assertEquals(247f, y, .001f)
        // These are the resulting screen coordinates after View's canvas transform.
        assertEquals(108f, x - 40f, .001f)
        assertEquals(67f, y - 180f, .001f)
    }
    @Test fun clippingTracksTheScrolledViewport() {
        assertEquals(MarkerGeometry.Clip(43, 187, 352, 673),
            MarkerGeometry.clip(40, 180, 320, 500, 3, 7, 8, 7))
    }
    @Test fun softWrapDoesNotAdvanceHardBreakLabels() {
        val text = "abcdef\nx\n"
        // Visual lines: [abc] [def\n] [x\n] []. The second visual line is still logical line 1.
        assertEquals(0, MarkerGeometry.breakIndex(text, 0))
        assertEquals(0, MarkerGeometry.breakIndex(text, 3))
        assertEquals(1, MarkerGeometry.breakIndex(text, 7))
        assertEquals(2, MarkerGeometry.breakIndex(text, 9))
        val labels = LineEndings.labels("abcdef\r\nx\n")
        assertEquals("↵", LineEndings.marker(labels[MarkerGeometry.breakIndex(text, 3)]))
        assertEquals("←", LineEndings.marker(labels[MarkerGeometry.breakIndex(text, 7)]))
    }
    @Test fun blankLinesStillAdvanceHardBreakIndex() {
        assertEquals(2, MarkerGeometry.breakIndex("\n\nx", 2))
        assertEquals(0, MarkerGeometry.breakIndex("", 0))
    }
    @Test fun baselineUsesActualLayoutRatherThanFixedLineHeight() {
        assertEquals(103f, MarkerGeometry.y(96, 7), .001f)
        assertEquals(131f, MarkerGeometry.y(124, 7), .001f)
    }
}
