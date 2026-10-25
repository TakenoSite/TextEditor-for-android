package com.example.helloworld

import org.junit.Assert.*
import org.junit.Test

class RecentFileHistoryTest {
    @Test fun recordsLocalPathAlongsideProviderLink() {
        val file = RecentFile("content://provider/1", "test.txt", 1, "/storage/emulated/0/Download/test.txt")
        assertEquals(file, RecentFileHistory.record(emptyList(), file).single())
    }
    @Test fun reconnectRemovesOnlyTheOldLink() {
        val old = RecentFile("content://old", "a.txt", 1)
        val other = RecentFile("content://other", "a.txt", 2)
        val new = RecentFile("content://document/new", "a.txt", 3)
        val updated = RecentFileHistory.record(listOf(other, old), new)
        assertEquals(listOf(new, other), RecentFileHistory.remove(updated, old.uri))
    }
    @Test fun openingMovesExistingUriToTopAndUpdatesName() {
        val old = listOf(RecentFile("content://a", "a.txt", 10), RecentFile("content://b", "b.txt", 9))
        val newest = RecentFile("content://b", "renamed.txt", 11)
        assertEquals(listOf(newest, old[0]), RecentFileHistory.record(old, newest))
    }
    @Test fun differentUrisWithSameNameRemainSeparate() {
        val a = RecentFile("content://one", "test.txt", 1)
        val b = RecentFile("content://two", "test.txt", 2)
        assertEquals(listOf(b, a), RecentFileHistory.record(listOf(a), b))
    }
    @Test fun maximumHistoryIsBounded() {
        val files = (1..100).map { RecentFile("content://$it", "$it.txt", it.toLong()) }
        val result = RecentFileHistory.record(files, RecentFile("content://new", "new.txt", 200))
        assertEquals(100, result.size)
        assertEquals("content://new", result.first().uri)
        assertFalse(result.any { it.uri == "content://100" })
    }
    @Test fun firstOpenedFileStartsHistory() {
        val file = RecentFile("content://first", "first.txt", 1)
        assertEquals(listOf(file), RecentFileHistory.record(emptyList(), file))
    }
}
