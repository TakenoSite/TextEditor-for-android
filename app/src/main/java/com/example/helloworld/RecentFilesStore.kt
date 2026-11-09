package com.example.helloworld

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

internal class RecentFilesStore(private val context: Context) {
    private val prefs = context.getSharedPreferences("recent_files", Context.MODE_PRIVATE)

    fun all(): List<RecentFile> {
        val array = try { JSONArray(prefs.getString("items", "[]")) } catch (_: Exception) { return emptyList() }
        val files = mutableListOf<RecentFile>()
        for (index in 0 until array.length()) {
            val item = array.optJSONObject(index) ?: continue
            val uri = item.optString("uri")
            if (uri.isNotEmpty()) files.add(RecentFile(uri, item.optString("name", context.getString(R.string.untitled_file)), item.optLong("openedAt"), item.optString("localPath").takeIf { it.isNotBlank() }))
        }
        return files.distinctBy { it.uri }.sortedByDescending { it.openedAt }.take(RecentFileHistory.LIMIT)
    }

    fun record(uri: String, name: String, localPath: String? = null) {
        val current = all()
        val files = RecentFileHistory.record(current, RecentFile(uri, name, System.currentTimeMillis(), localPath ?: current.firstOrNull { it.uri == uri }?.localPath))
        save(files)
    }

    fun remove(uri: String) { save(RecentFileHistory.remove(all(), uri)) }

    private fun save(files: List<RecentFile>) {
        val array = JSONArray()
        files.forEach { file -> array.put(JSONObject().put("uri", file.uri).put("name", file.name).put("openedAt", file.openedAt).put("localPath", file.localPath ?: "")) }
        prefs.edit().putString("items", array.toString()).apply()
    }
}
