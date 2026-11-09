package com.example.helloworld

/** Write first; publish a new association only when writing and closing succeeded. */
internal object DocumentSaveOperation {
    // A fixed text/plain MIME lets providers append .txt to names such as script.py.
    const val CREATE_MIME_TYPE = "*/*"

    data class Saved<T>(val target: T, val name: String, val path: String?, val source: String)

    fun <T> execute(
        target: T,
        source: String,
        write: (T, String) -> Unit,
        name: (T) -> String,
        path: (T) -> String?
    ): Saved<T> {
        write(target, source)
        return Saved(target, name(target), path(target), source)
    }
}
