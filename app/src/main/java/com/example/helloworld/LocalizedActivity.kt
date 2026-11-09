package com.example.helloworld

import android.content.Context
import android.content.res.Configuration
import androidx.appcompat.app.AppCompatActivity
import java.util.Locale

internal object AppLanguage {
    val tags = listOf("ja", "en")
    fun normalize(tag: String?): String = if (tag == "en") "en" else "ja"
    fun selected(context: Context): String = normalize(context.getSharedPreferences("editor", Context.MODE_PRIVATE).getString("language", "ja"))
}

/** Activity-local resources; never changes the device language or Locale default. */
open class LocalizedActivity : AppCompatActivity() {
    private var attachedLanguage = "ja"
    protected var languageChangePending = false
        private set
    override fun attachBaseContext(newBase: Context) {
        attachedLanguage = AppLanguage.selected(newBase)
        val configuration = Configuration(newBase.resources.configuration)
        configuration.setLocale(Locale.forLanguageTag(attachedLanguage))
        super.attachBaseContext(newBase.createConfigurationContext(configuration))
    }

    override fun onResume() {
        super.onResume()
        languageChangePending = AppLanguage.selected(this) != attachedLanguage
        if (languageChangePending) recreate()
    }
}
