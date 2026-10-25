package com.example.helloworld

import org.junit.Assert.*
import org.junit.Test

class LocalPathPolicyTest {
    private val root = "/storage/emulated/0"
    @Test fun sharedDocumentLocations() {
        assertEquals("$root/Documents/test.txt", LocalPathPolicy.documentPath("primary:Documents/test.txt", root))
        assertEquals("$root/Documents/test.txt", LocalPathPolicy.documentPath("home:test.txt", root))
        assertEquals("/storage/ABCD-1234/folder/test.txt", LocalPathPolicy.documentPath("ABCD-1234:folder/test.txt", root))
    }
    @Test fun aliasesAndKnownLocalPaths() {
        assertEquals("$root/Download/test.txt", LocalPathPolicy.sharedPath("/sdcard/Download/test.txt", root))
        assertEquals("$root/日本語.txt", LocalPathPolicy.sharedPath("$root/日本語.txt", root))
        assertEquals("/storage/ABCD-1234/test.txt", LocalPathPolicy.sharedPath("/storage/ABCD-1234/test.txt", root))
    }
    @Test fun rejectOutsideSharedStorage() {
        assertNull(LocalPathPolicy.sharedPath("/data/data/other/private.txt", root))
        assertNull(LocalPathPolicy.sharedPath("${root}other/test.txt", root))
        assertNull(LocalPathPolicy.documentPath("unknown:test.txt", root))
    }
    @Test fun rejectTraversalAndMalformedPaths() {
        assertNull(LocalPathPolicy.documentPath("primary:../private.txt", root))
        assertNull(LocalPathPolicy.documentPath("primary:/absolute.txt", root))
        assertNull(LocalPathPolicy.documentPath("primary:test\u0000.txt", root))
        assertNull(LocalPathPolicy.sharedPath("$root/../private.txt", root))
    }
    @Test fun supportsCurrentUserStorageRoot() {
        assertEquals("/storage/emulated/10/Download/a.txt", LocalPathPolicy.documentPath("primary:Download/a.txt", "/storage/emulated/10"))
    }
}
