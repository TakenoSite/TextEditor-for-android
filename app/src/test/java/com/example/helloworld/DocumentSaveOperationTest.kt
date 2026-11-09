package com.example.helloworld

import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

class DocumentSaveOperationTest {
    @Test fun doesNotRequestForcedTxtExtension() {
        assertEquals("*/*", DocumentSaveOperation.CREATE_MIME_TYPE)
    }

    @Test fun preservesNamesAndSourceForCommonExtensions() {
        listOf("script.py", "README.md", "main.c", "app.js", "notes.txt", "拡張子なし").forEach { filename ->
            var written = ""
            val source = "日本語\r\n\tvalue = 42\r\n"
            val saved = DocumentSaveOperation.execute("new-uri", source,
                write = { target, text -> assertEquals("new-uri", target); written = text },
                name = { filename }, path = { null })
            assertEquals(source, written)
            assertEquals(filename, saved.name)
            assertEquals(source, saved.source)
            assertNull(saved.path)
        }
    }

    @Test fun usesProviderActualNameAfterWrite() {
        val events = mutableListOf<String>()
        val saved = DocumentSaveOperation.execute("created-uri", "content",
            write = { _, _ -> events.add("closed") },
            name = { events.add("name"); "notes (1).md" },
            path = { events.add("path"); "Download/notes (1).md" })
        assertEquals(listOf("closed", "name", "path"), events)
        assertEquals("notes (1).md", saved.name)
        assertEquals("created-uri", saved.target)
    }

    @Test fun writeFailureDoesNotPublishNewAssociation() {
        var current = "original-uri"
        var metadataRead = false
        try {
            val saved = DocumentSaveOperation.execute("new-uri", "unsaved",
                write = { _, _ -> throw IOException("close failed") },
                name = { metadataRead = true; "new.py" }, path = { null })
            current = saved.target
            fail("Expected failure")
        } catch (_: IOException) { }
        assertEquals("original-uri", current)
        assertFalse(metadataRead)
    }
}
