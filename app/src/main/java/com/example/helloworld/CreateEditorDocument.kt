package com.example.helloworld

import android.content.Context
import android.content.Intent
import androidx.activity.result.contract.ActivityResultContracts

/** Open the Android save screen directly, using the current name as its initial suggestion. */
internal class CreateEditorDocument : ActivityResultContracts.CreateDocument(DocumentSaveOperation.CREATE_MIME_TYPE) {
    override fun createIntent(context: Context, input: String): Intent =
        super.createIntent(context, input).setType(DocumentFilePolicy.mimeForName(input))
}
