package com.example.helloworld

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class LogicalLinesTest {
    @Test fun emptyDocumentHasFirstLine() {
        assertArrayEquals(intArrayOf(0), LogicalLines.starts(""))
    }

    @Test fun fallbackGlyphsAndSurrogatePairsKeepOffsets() {
        val text = "日本語▼\n😀→\n終わり\n"
        val starts = LogicalLines.starts(text)
        assertArrayEquals(intArrayOf(0, 5, 9, 13), starts)
        assertEquals(-2, starts.binarySearch(2)) // Wrapped continuation has no number.
    }

    @Test fun moreThanFiveThousandLinesAreNotTruncated() {
        val starts = LogicalLines.starts("a\n".repeat(12000))
        assertEquals(12001, starts.size)
        assertEquals(24000, starts.last())
    }

    @Test fun veryLongLineDoesNotCreateLogicalNumbers() {
        assertArrayEquals(intArrayOf(0, 100001), LogicalLines.starts("x".repeat(100000) + "\n▼"))
    }
}
