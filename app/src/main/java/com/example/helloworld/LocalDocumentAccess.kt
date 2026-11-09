package com.example.helloworld

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.MediaStore
import java.io.File

internal object LocalDocumentAccess {
    data class Opened(val text: String, val path: String?)
    @Suppress("DEPRECATION")
    private fun root() = Environment.getExternalStorageDirectory().absolutePath

    fun path(context: Context, uri: Uri, cached: String? = null): String? {
        val primary = root()
        fun safe(value: String?) = value?.let { LocalPathPolicy.sharedPath(it, primary) }
        safe(cached)?.let { return it }
        if (uri.scheme == "file") return safe(uri.path)
        if (uri.scheme != "content") return null
        val id = try { DocumentsContract.getDocumentId(uri) } catch (_: IllegalArgumentException) { null }
        if (uri.authority == "com.android.externalstorage.documents" && id != null) {
            return LocalPathPolicy.documentPath(id, primary)
        }
        if (uri.authority == "com.android.providers.downloads.documents" && id?.startsWith("raw:") == true) return safe(id.removePrefix("raw:"))
        safe(dataPath(context, uri))?.let { return it }
        if (!StorageAccess.granted(context)) return null
        if (id != null && uri.authority in setOf("com.android.providers.downloads.documents", "com.android.providers.media.documents")) {
            val number = id.substringAfter(':', id).toLongOrNull() ?: return null
            val kind = id.substringBefore(':')
            val source = when (kind) {
                "image" -> MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                "video" -> MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                "audio" -> MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
                else -> MediaStore.Files.getContentUri("external")
            }
            return safe(dataPath(context, ContentUris.withAppendedId(source, number)))
        }
        return null
    }

    private fun dataPath(context: Context, uri: Uri): String? = try {
        context.contentResolver.query(uri, arrayOf("_data"), null, null, null)?.use {
            if (it.moveToFirst() && !it.isNull(0)) it.getString(0) else null
        }
    } catch (_: Exception) { null }

    fun read(context: Context, uri: Uri, cached: String?): Opened {
        val local = path(context, uri, cached)
        if (StorageAccess.granted(context) && local != null) {
            try { return Opened(File(local).readText(Charsets.UTF_8), local) } catch (_: java.io.IOException) { /* Try provider. */ }
            catch (_: SecurityException) { /* A protected path still needs SAF or may be inaccessible. */ }
        }
        val text = if (uri.scheme == "file") File(uri.path ?: error(context.getString(R.string.path_missing))).readText(Charsets.UTF_8) else
            context.contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: error(context.getString(R.string.read_failed))
        return Opened(text, local)
    }

    fun write(context: Context, uri: Uri, cached: String?, text: String) {
        val local = path(context, uri, cached)
        if (StorageAccess.granted(context) && local != null) {
            val file = File(local)
            if (file.isFile && file.canWrite()) { file.writeText(text, Charsets.UTF_8); return }
        }
        if (uri.scheme == "file") File(uri.path ?: error(context.getString(R.string.path_missing))).writeText(text, Charsets.UTF_8) else
            (context.contentResolver.openOutputStream(uri, "wt") ?: error(context.getString(R.string.write_failed))).bufferedWriter(Charsets.UTF_8).use { it.write(text) }
    }
}
