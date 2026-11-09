package com.example.helloworld

/** UI-thread-only state: do not render from file-load or Activity-result callbacks. */
internal class PreviewLifecycle {
    var visible = false
        private set
    private var resumed = false
    private var pending = false

    fun select(preview: Boolean) {
        visible = preview
        pending = preview
    }

    fun resume() { resumed = true }
    fun pause() { resumed = false; pending = visible }

    fun takeRenderRequest(): Boolean {
        if (!resumed || !visible || !pending) return false
        pending = false
        return true
    }
}
