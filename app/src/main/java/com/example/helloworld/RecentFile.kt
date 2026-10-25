package com.example.helloworld

internal data class RecentFile(val uri: String, val name: String, val openedAt: Long, val localPath: String? = null)

internal object RecentFileHistory {
    const val LIMIT = 100
    fun remove(current: List<RecentFile>, uri: String): List<RecentFile> = current.filterNot { it.uri == uri }
    fun record(current: List<RecentFile>, file: RecentFile): List<RecentFile> =
        (listOf(file) + current.filterNot { it.uri == file.uri }).take(LIMIT)
}
