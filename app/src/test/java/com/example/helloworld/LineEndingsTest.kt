package com.example.helloworld

import org.junit.Assert.*
import org.junit.Test

class LineEndingsTest {
    @Test fun displayMarkersFollowLineEndingStyle() {
        assertEquals("←", LineEndings.marker("LF"))
        assertEquals("↵", LineEndings.marker("CRLF"))
        assertEquals("CR", LineEndings.marker("CR"))
    }
    @Test fun individualLineLabelsDistinguishFormats() {
        assertEquals(listOf("CRLF", "LF", "CR"), LineEndings.labels("a\r\nb\nc\r"))
        assertEquals(emptyList<String>(), LineEndings.labels("no newline"))
    }
    @Test fun detectAllFormats() {
        assertEquals("LF", LineEndings.label("a\nb\n"))
        assertEquals("CRLF", LineEndings.label("a\r\nb\r\n"))
        assertEquals("CR", LineEndings.label("a\rb\r"))
        assertEquals("混在", LineEndings.label("a\r\nb\nc\r"))
        assertEquals("LF", LineEndings.label(""))
    }
    @Test fun normalizeForEditing() {
        assertEquals("a\nb\nc\n", LineEndings.normalize("a\r\nb\nc\r"))
    }
    @Test fun preserveCrlfAndCrOnSave() {
        assertEquals("a\r\nb\r\nc", LineEndings.encode("a\nb\nc", "old\r\ntext"))
        assertEquals("a\rb\rc", LineEndings.encode("a\nb\nc", "old\rtext"))
    }
    @Test fun mixedFileRoundTripAndNewLines() {
        val original = "a\r\nb\nc\r"
        assertEquals(original, LineEndings.encode(LineEndings.normalize(original), original))
        assertEquals("A\r\nB\nC\rD\r\n", LineEndings.encode("A\nB\nC\nD\n", original))
    }
    @Test fun newDocumentsUseLf() {
        assertEquals("one\ntwo\n", LineEndings.encode("one\ntwo\n", ""))
    }
}
