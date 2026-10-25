package com.example.helloworld

import org.junit.Assert.*
import org.junit.Test

class EditorZoomTest {
    @Test fun defaultAndScale() {
        assertEquals(16f, EditorZoom.scale(16f, 100f), .001f)
        assertEquals(32f, EditorZoom.scale(16f, 200f), .001f)
        assertEquals(8f, EditorZoom.scale(16f, 50f), .001f)
    }
    @Test fun boundsForInputAndPinch() {
        assertEquals(25f, EditorZoom.clamp(0f), .001f)
        assertEquals(400f, EditorZoom.clamp(1000f), .001f)
        assertEquals(100f, EditorZoom.clamp(Float.NaN), .001f)
    }
}
