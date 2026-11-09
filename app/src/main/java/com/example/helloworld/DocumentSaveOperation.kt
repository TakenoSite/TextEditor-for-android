package com.example.helloworld

/** Write first; publish a new association only when writing and closing succeeded. */
internal object DocumentSaveOperation {
    const val CREATE_MIME_TYPE = DocumentFilePolicy.GENERIC_MIME

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
