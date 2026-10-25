package com.example.helloworld

import android.content.Intent

internal object DocumentGrantPolicy {
    fun acquire(take: (Int) -> Unit): Boolean {
        try {
            take(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            return true
        } catch (_: SecurityException) { /* A read-only document may reject a write grant. */ }
        try {
            take(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            return true
        } catch (_: SecurityException) { return false }
    }
}
