package com.example.helloworld

import org.junit.Assert.*
import org.junit.Test

class AppLanguageTest {
    @Test fun defaultsToJapanese() {
        assertEquals("ja", AppLanguage.normalize(null))
        assertEquals("ja", AppLanguage.normalize(""))
    }

    @Test fun supportsBothDropdownValues() {
        assertEquals(listOf("ja", "en"), AppLanguage.tags)
        AppLanguage.tags.forEach { assertEquals(it, AppLanguage.normalize(it)) }
    }

    @Test fun unsupportedStoredValueFallsBackSafely() {
        listOf("fr", "system", "EN", "ja-JP").forEach {
            assertEquals("ja", AppLanguage.normalize(it))
        }
    }
}
