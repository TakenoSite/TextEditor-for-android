package com.example.helloworld

import java.util.regex.Matcher
import java.util.regex.Pattern

internal object SearchEngine {
    fun compile(query: String, regex: Boolean, matchCase: Boolean): Pattern = Pattern.compile(
        if (regex && query.startsWith("*")) wildcard(query) else if (regex) query else Pattern.quote(query),
        Pattern.MULTILINE or if (matchCase) 0 else Pattern.CASE_INSENSITIVE or Pattern.UNICODE_CASE
    )

    // Compatibility for filename wildcards such as *.txt; other input remains Java regex.
    private fun wildcard(query: String): String = buildString {
        for (c in query) append(when (c) {
            '*' -> "[^\\s/\\\\]*"
            '?' -> "[^\\s/\\\\]"
            else -> Pattern.quote(c.toString())
        })
    }

    fun replaceAll(pattern: Pattern, text: String, replacement: String, regex: Boolean): String =
        pattern.matcher(text).replaceAll(if (regex) replacement else Matcher.quoteReplacement(replacement))

    fun replaceSelection(pattern: Pattern, text: String, start: Int, end: Int, replacement: String, regex: Boolean): String? {
        val matcher = pattern.matcher(text)
        while (matcher.find()) {
            if (matcher.start() == start && matcher.end() == end) {
                val buffer = StringBuffer()
                matcher.appendReplacement(buffer, if (regex) replacement else Matcher.quoteReplacement(replacement))
                return buffer.substring(start)
            }
        }
        return null
    }
}
