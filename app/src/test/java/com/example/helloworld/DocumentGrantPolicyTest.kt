package com.example.helloworld

import org.junit.Assert.*
import org.junit.Test

class DocumentGrantPolicyTest {
    @Test fun keepsReadAndWriteWhenAvailable() {
        val requests = mutableListOf<Int>()
        assertTrue(DocumentGrantPolicy.acquire { requests.add(it) })
        assertEquals(listOf(3), requests)
    }
    @Test fun fallsBackToReadOnly() {
        val requests = mutableListOf<Int>()
        assertTrue(DocumentGrantPolicy.acquire {
            requests.add(it)
            if (it == 3) throw SecurityException("Read-only document")
        })
        assertEquals(listOf(3, 1), requests)
    }
    @Test fun temporaryGrantDoesNotPretendToBePersistent() {
        val requests = mutableListOf<Int>()
        assertFalse(DocumentGrantPolicy.acquire {
            requests.add(it)
            throw SecurityException("No persistable grant")
        })
        assertEquals(listOf(3, 1), requests)
    }
}
