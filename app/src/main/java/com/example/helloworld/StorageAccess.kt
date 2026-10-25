package com.example.helloworld

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

internal object StorageAccess {
    fun granted(context: Context): Boolean = if (Build.VERSION.SDK_INT >= 30) Environment.isExternalStorageManager() else
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
}

/** Only launches the permission UI following an explicit tap. Does not grant permissions itself. */
internal class StoragePermissionController(private val activity: AppCompatActivity, changed: () -> Unit) {
    private val runtime = activity.registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { changed() }
    private val settings = activity.registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { changed() }
    fun request(): Boolean {
        if (Build.VERSION.SDK_INT < 30) {
            runtime.launch(arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE))
            return true
        }
        try {
            settings.launch(Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:${activity.packageName}")))
            return true
        } catch (_: android.content.ActivityNotFoundException) {
            return try { settings.launch(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)); true }
            catch (_: android.content.ActivityNotFoundException) { false }
        }
    }
}
