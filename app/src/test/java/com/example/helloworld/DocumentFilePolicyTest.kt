package com.example.helloworld

import org.junit.Assert.*
import org.junit.Test

class DocumentFilePolicyTest {
    @Test fun selectsPythonMimeFromNewNameNotOldTxtName() {
        assertEquals("text/x-python", DocumentFilePolicy.mimeForName("main.py"))
        assertEquals("text/x-python", DocumentFilePolicy.mimeForName("MAIN.PY"))
        assertEquals("text/plain", DocumentFilePolicy.mimeForName("notes.txt"))
    }

    @Test fun acceptsCommonJavascriptProviderMimeTypes() {
        listOf("application/javascript", "application/x-javascript", "application/ecmascript").forEach {
            assertTrue("Missing picker MIME $it", it in DocumentFilePolicy.openMimeTypes)
        }
        assertTrue("text/*" in DocumentFilePolicy.openMimeTypes)
        assertTrue("application/octet-stream" in DocumentFilePolicy.openMimeTypes)
    }

    @Test fun javascriptVariantsAreNotPlainTextMime() {
        listOf("app.js", "app.JS", "app.mjs", "app.cjs", "app.jsx").forEach {
            assertEquals("application/javascript", DocumentFilePolicy.mimeForName(it))
        }
    }

    @Test fun unknownAndExtensionlessNamesDoNotUseWildcardOrTxtMime() {
        listOf("新規ファイル", "custom.unknown", "main.rs", "source.kt", ".config").forEach {
            assertEquals(DocumentFilePolicy.GENERIC_MIME, DocumentFilePolicy.mimeForName(it))
        }
    }

    @Test fun suggestsRequestedNameForExactlyAppendedTxt() {
        assertEquals("main.py", DocumentFilePolicy.suggestedName("main.py", "main.py.txt"))
        assertEquals("app.js", DocumentFilePolicy.suggestedName("app.js", "app.js.txt"))
        assertEquals("README.md", DocumentFilePolicy.suggestedName("README.md", "README.md.txt"))
    }

    @Test fun preservesIntentionalTxtAndPickerEditsAndCollisionNames() {
        assertEquals("main.py.txt", DocumentFilePolicy.suggestedName("main.py.txt", "main.py.txt"))
        assertEquals("other.txt", DocumentFilePolicy.suggestedName("main.py", "other.txt"))
        assertEquals("main (1).py", DocumentFilePolicy.suggestedName("main.py", "main (1).py"))
        assertEquals("main.txt.py", DocumentFilePolicy.suggestedName("main.py", "main.txt.py"))
    }

    @Test fun validatesNamesWithoutSilentlyTrimmingThem() {
        listOf("main.py", "テスト.js", "no-extension", "notes.txt", "my file.md").forEach {
            assertTrue(DocumentFilePolicy.validName(it))
        }
        listOf("", " ", ".", "..", "../main.py", "folder/main.py", "folder\\main.py", "file\nname", "a\u0000b").forEach {
            assertFalse(DocumentFilePolicy.validName(it))
        }
    }

    @Test fun normalPickerEditsAndCollisionNamesDoNotNeedAnotherDialog() {
        listOf("other.js", "main (1).py", "メモ.txt", "main.py", "新規ファイル").forEach {
            assertNull(DocumentFilePolicy.extraTxtSuggestion("original.js", it))
        }
        assertNull(DocumentFilePolicy.extraTxtSuggestion("main.py.txt", "main.py.txt"))
    }

    @Test fun detectsExtraTxtEvenWhenNameWasEnteredOnlyInPicker() {
        assertEquals("main.py", DocumentFilePolicy.extraTxtSuggestion("新規ファイル", "main.py.txt"))
        assertEquals("app.js", DocumentFilePolicy.extraTxtSuggestion("old.txt", "app.js.txt"))
        assertEquals("README.md", DocumentFilePolicy.extraTxtSuggestion("New file", "README.md.txt"))
        assertEquals("custom.abc", DocumentFilePolicy.extraTxtSuggestion("custom.abc", "custom.abc.txt"))
    }
}
