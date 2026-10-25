package com.example.helloworld

internal object LocalPathPolicy {
    private val volume = Regex("[0-9A-Fa-f]{4}-[0-9A-Fa-f]{4}")
    fun documentPath(id: String, primaryRoot: String): String? {
        val key = id.substringBefore(':')
        val relative = id.substringAfter(':', "")
        val root = when {
            key.equals("primary", true) -> primaryRoot
            key == "home" -> "$primaryRoot/Documents"
            volume.matches(key) -> "/storage/$key"
            else -> return null
        }
        if (relative.isEmpty() || relative.startsWith('/') || relative.split('/').any { it == ".." || it == "." } || '\u0000' in relative) return null
        return "$root/$relative"
    }
    fun sharedPath(path: String, primaryRoot: String): String? {
        if ('\u0000' in path || path.split('/').any { it == ".." || it == "." }) return null
        val normalized = if (path.startsWith("/sdcard/")) primaryRoot + path.removePrefix("/sdcard") else path
        if (normalized.startsWith("$primaryRoot/")) return normalized
        val parts = normalized.split('/')
        return if (parts.size >= 4 && parts[1] == "storage" && volume.matches(parts[2])) normalized else null
    }
}
