package com.example.helloworld

import org.junit.Assert.*
import org.junit.Test

class SearchEngineTest {
    @Test fun filenameWildcardFindsTxt() {
        val p = SearchEngine.compile("*.txt", true, true)
        val m = p.matcher("メモ test.txt next.csv")
        assertTrue(m.find())
        assertEquals("test.txt", m.group())
        assertFalse(p.matcher("testXtxt").find())
    }
    @Test fun regexDigitsAndGroups() {
        val p = SearchEngine.compile("([a-z]+)-(\\d+)", true, false)
        assertEquals("123:ABC", SearchEngine.replaceAll(p, "ABC-123", "\$2:\$1", true))
    }
    @Test fun anchorsMatchEachLine() {
        val p = SearchEngine.compile("^foo$", true, true)
        assertEquals("bar\nX\nX", SearchEngine.replaceAll(p, "bar\nfoo\nfoo", "X", true))
    }
    @Test fun literalReplacementStaysLiteral() {
        val p = SearchEngine.compile(".", false, true)
        assertEquals("a\$1", SearchEngine.replaceAll(p, "a.", "\$1", false))
    }
    @Test fun selectedMatchPreservesLookbehindContext() {
        val p = SearchEngine.compile("(?<=ID:)(\\d+)", true, true)
        assertEquals("[42]", SearchEngine.replaceSelection(p, "ID:42", 3, 5, "[\$1]", true))
        assertNull(SearchEngine.replaceSelection(p, "XX:42", 3, 5, "[\$1]", true))
    }
    @Test fun whitespaceCanBeSearched() {
        assertTrue(SearchEngine.compile(" ", false, true).matcher("a b").find())
    }
    @Test(expected = java.util.regex.PatternSyntaxException::class)
    fun invalidRegexIsRejected() { SearchEngine.compile("[", true, true) }
}
