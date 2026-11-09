package com.example.helloworld

import java.util.Locale

internal object DocumentFilePolicy {
    // Unknown extensions must not fall back to text/plain (which can append .txt).
    const val GENERIC_MIME = "application/vnd.texteditor.document"
    val openMimeTypes = arrayOf("text/*", "application/json", "application/xml", "application/octet-stream",
        "application/javascript", "application/x-javascript", "application/ecmascript", "application/typescript", GENERIC_MIME)

    fun mimeForName(name: String): String = when (name.substringAfterLast('.', "").lowercase(Locale.ROOT)) {
        "txt" -> "text/plain"
        "py", "pyw", "pyi" -> "text/x-python"
        "js", "mjs", "cjs", "jsx" -> "application/javascript"
        "ts", "tsx", "mts", "cts" -> "text/typescript"
        "md" -> "text/markdown"
        "json" -> "application/json"
        "xml" -> "application/xml"
        "html", "htm" -> "text/html"
        "css" -> "text/css"
        "c", "h" -> "text/x-c"
        "cpp", "cc", "cxx", "hpp", "hh", "hxx" -> "text/x-c++src"
        "java" -> "text/x-java"
        else -> GENERIC_MIME
    }

    fun validName(name: String) = name.isNotBlank() && name != "." && name != ".." &&
        name.none { it == '/' || it == '\\' || it.isISOControl() }

    // Suggest removal only for the exact extra suffix; never silently strip a user's .txt.
    fun suggestedName(requested: String, actual: String) =
        if (actual == "$requested.txt") requested else actual

    /** A picker rename is normal. Ask only about a possible extra .txt; never rename silently. */
    fun extraTxtSuggestion(requested: String, actual: String): String? {
        if (actual == requested || !actual.endsWith(".txt", ignoreCase = true)) return null
        val base = actual.dropLast(4)
        if (base == requested) return requested
        val extension = base.substringAfterLast('.', "").lowercase(Locale.ROOT)
        return base.takeIf {
            SyntaxLexer.language(base) != null || extension in setOf("md", "json", "xml", "html", "htm", "css")
        }
    }
}
