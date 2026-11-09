package com.example.helloworld

import org.junit.Assert.*
import org.junit.Test
import com.example.helloworld.SyntaxLexer.Kind
import com.example.helloworld.SyntaxLexer.Language

class SyntaxLexerTest {
    private fun colored(source: String, language: Language) = SyntaxLexer.tokenize(source, language)
        .map { source.substring(it.start, it.end) to it.kind }

    @Test fun detectsAllLanguagesAndUppercaseExtensions() {
        val extensions = mapOf("C" to Language.C, "hpp" to Language.CPP, "PY" to Language.PYTHON,
            "mjs" to Language.JAVASCRIPT, "tsx" to Language.TYPESCRIPT, "java" to Language.JAVA,
            "kts" to Language.KOTLIN, "cs" to Language.CSHARP, "go" to Language.GO,
            "rs" to Language.RUST, "swift" to Language.SWIFT)
        extensions.forEach { (extension, language) -> assertEquals(language, SyntaxLexer.language("test.$extension")) }
        listOf("README.md", "notes.txt", "untitled", "file.json").forEach { assertNull(SyntaxLexer.language(it)) }
    }

    @Test fun highlightsRepresentativeKeywordsForEveryLanguage() {
        val examples = mapOf(Language.C to "typedef", Language.CPP to "constexpr", Language.PYTHON to "def",
            Language.JAVASCRIPT to "function", Language.TYPESCRIPT to "interface", Language.JAVA to "record",
            Language.KOTLIN to "fun", Language.CSHARP to "namespace", Language.GO to "func",
            Language.RUST to "fn", Language.SWIFT to "guard")
        examples.forEach { (language, keyword) -> assertEquals(listOf(keyword to Kind.KEYWORD), colored(keyword, language)) }
    }

    @Test fun stringsAndCommentsTakePriorityOverKeywords() {
        assertEquals(listOf("\"return // 42\"" to Kind.STRING, "// return 42" to Kind.COMMENT),
            colored("\"return // 42\" // return 42", Language.JAVA))
        assertEquals(listOf("# def 123" to Kind.COMMENT), colored("# def 123", Language.PYTHON))
    }

    @Test fun highlightsTypesNumbersAndDirectives() {
        assertEquals(listOf("#include" to Kind.DIRECTIVE, "int" to Kind.TYPE, "0xFF" to Kind.NUMBER,
            "1.5e-2" to Kind.NUMBER), colored("#include int value = 0xFF + 1.5e-2;", Language.C))
    }

    @Test fun handlesMultilineStringsAndNestedComments() {
        val triple = "\"\"\"line\nreturn\"\"\""
        listOf(Language.PYTHON, Language.KOTLIN, Language.JAVA, Language.SWIFT, Language.CSHARP).forEach {
            assertEquals(listOf(triple to Kind.STRING), colored(triple, it))
        }
        val comment = "/* outer /* nested */ still comment */"
        listOf(Language.KOTLIN, Language.RUST, Language.SWIFT).forEach {
            assertEquals(listOf(comment to Kind.COMMENT, "return" to Kind.KEYWORD), colored("$comment return", it))
        }
    }

    @Test fun handlesRawAndVerbatimStrings() {
        val rust = "r##\"hello \"world\"\"##"
        assertEquals(listOf(rust to Kind.STRING), colored(rust, Language.RUST))
        val cpp = "R\"tag(hello \"world\")tag\""
        assertEquals(listOf(cpp to Kind.STRING), colored(cpp, Language.CPP))
        val cs = "@\"hello \"\"world\"\"\""
        assertEquals(listOf(cs to Kind.STRING), colored(cs, Language.CSHARP))
        val go = "`raw\\`"
        assertEquals(listOf(go to Kind.STRING, "return" to Kind.KEYWORD), colored("$go return", Language.GO))
    }

    @Test fun handlesEscapesAndRustLifetimes() {
        val escaped = "\"say \\\"hi\\\"\""
        assertEquals(listOf(escaped to Kind.STRING), colored(escaped, Language.JAVASCRIPT))
        assertEquals(listOf("'a" to Kind.TYPE, "'x'" to Kind.STRING), colored("'a 'x'", Language.RUST))
    }

    @Test fun leavesUnknownAndLargeDocumentsUnhighlighted() {
        assertTrue(SyntaxLexer.tokenize("return", null).isEmpty())
        assertTrue(SyntaxLexer.tokenize("a".repeat(SyntaxLexer.MAX_LENGTH + 1), Language.C).isEmpty())
        assertTrue(SyntaxLexer.tokenize("return ".repeat(25_000), Language.C).size <= SyntaxLexer.MAX_TOKENS)
    }

    @Test fun tokenRangesAreOrderedValidAndSourceIsUnchanged() {
        val source = "/* 日本語 😀 */\r\nreturn \"こんにちは\" + 42;\n\"unfinished"
        Language.values().forEach { language ->
            val tokens = SyntaxLexer.tokenize(source, language)
            var end = 0
            tokens.forEach {
                assertTrue(it.start >= end)
                assertTrue(it.end > it.start && it.end <= source.length)
                end = it.end
            }
        }
        assertEquals("/* 日本語 😀 */\r\nreturn \"こんにちは\" + 42;\n\"unfinished", source)
    }
}
