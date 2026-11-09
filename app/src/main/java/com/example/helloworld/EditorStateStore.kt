package com.example.helloworld

import android.content.Context
import android.os.Bundle
import android.os.Parcel
import android.util.AtomicFile
import java.io.File
import java.util.UUID

/** Only the small token crosses Android's saved-state Binder transaction. */
internal class EditorStateStore(context: Context) {
    private val directory = File(context.noBackupFilesDir, "editor-state")

    fun delete(token: String) = AtomicFile(fileFor(token)).delete()

    fun save(token: String, state: Bundle) {
        check(directory.isDirectory || directory.mkdirs())
        val parcel = Parcel.obtain()
        try {
            parcel.writeBundle(state)
            val file = AtomicFile(fileFor(token))
            val stream = file.startWrite()
            try {
                stream.write(parcel.marshall())
                file.finishWrite(stream)
            } catch (e: Exception) {
                file.failWrite(stream)
                throw e
            }
        } finally { parcel.recycle() }
    }

    fun load(token: String): Bundle {
        val bytes = AtomicFile(fileFor(token)).openRead().use { it.readBytes() }
        val parcel = Parcel.obtain()
        try {
            parcel.unmarshall(bytes, 0, bytes.size)
            parcel.setDataPosition(0)
            return requireNotNull(parcel.readBundle(javaClass.classLoader))
        } finally { parcel.recycle() }
    }

    private fun fileFor(token: String): File {
        require(UUID.fromString(token).toString() == token)
        return File(directory, "$token.state")
    }
}
