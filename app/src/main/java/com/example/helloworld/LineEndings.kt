package com.example.helloworld

internal object LineEndings {
    private val breaks = Regex("\\r\\n|\\r|\\n")
    fun normalize(text: String): String = breaks.replace(text, "\n")
    fun marker(label: String): String = when (label) {
        "CRLF" -> "↵"
        "LF" -> "←"
        else -> label
    }
    fun labels(text: String): List<String> = breaks.findAll(text).map {
        when (it.value) { "\r\n" -> "CRLF"; "\r" -> "CR"; else -> "LF" }
    }.toList()
    fun label(text: String): String {
        val styles = breaks.findAll(text).map { it.value }.toSet()
        return when {
            styles.size > 1 -> "混在"
            styles.firstOrNull() == "\r\n" -> "CRLF"
            styles.firstOrNull() == "\r" -> "CR"
            else -> "LF"
        }
    }
    // Keep the per-line style of a mixed file too; newly added lines use its first style.
    fun encode(normalized: String, original: String): String {
        val separators = breaks.findAll(original).map { it.value }.toList()
        val default = separators.firstOrNull() ?: "\n"
        var index = 0
        return normalize(normalized).replace(Regex("\n")) {
            (separators.getOrNull(index++) ?: default)
        }
    }
}
