package com.example.helloworld

import org.junit.Assert.*
import org.junit.Test

class PreviewLifecycleTest {
    @Test fun initialMdWaitsForResumedScreen() {
        val state = PreviewLifecycle()
        state.select(true)
        assertFalse(state.takeRenderRequest())
        state.resume()
        assertTrue(state.takeRenderRequest())
        assertFalse(state.takeRenderRequest())
    }

    @Test fun recentResultWhilePausedDoesNotRenderOldFile() {
        val state = PreviewLifecycle()
        state.resume()
        state.select(true)
        assertTrue(state.takeRenderRequest())
        state.pause()
        state.select(false) // load begins: invalidate old preview
        state.select(true) // new MD loaded by the activity-result callback
        assertFalse(state.takeRenderRequest())
        state.resume()
        assertTrue(state.takeRenderRequest())
        assertFalse(state.takeRenderRequest())
    }

    @Test fun returningFromHistoryWithoutSelectionRecreatesPreviewOnce() {
        val state = PreviewLifecycle()
        state.select(true)
        state.resume()
        assertTrue(state.takeRenderRequest())
        state.pause()
        state.resume()
        assertTrue(state.takeRenderRequest())
        state.resume()
        assertFalse(state.takeRenderRequest())
    }

    @Test fun switchingToTextCancelsPendingMdRender() {
        val state = PreviewLifecycle()
        state.select(true)
        state.select(false)
        state.resume()
        assertFalse(state.visible)
        assertFalse(state.takeRenderRequest())
    }

    @Test fun rapidRequestsCoalesceAndEditorChoiceSurvivesReturn() {
        val state = PreviewLifecycle()
        state.resume()
        repeat(50) { state.select(true) }
        assertTrue(state.takeRenderRequest())
        assertFalse(state.takeRenderRequest())
        state.select(false)
        state.pause()
        state.resume()
        assertFalse(state.takeRenderRequest())
    }

    @Test fun repeatedHistoryTransitionsStaySingleShot() {
        val state = PreviewLifecycle()
        state.select(true)
        repeat(100) {
            state.resume()
            assertTrue(state.takeRenderRequest())
            assertFalse(state.takeRenderRequest())
            state.pause()
            assertFalse(state.takeRenderRequest())
        }
    }
}
