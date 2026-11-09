package com.example.helloworld

internal object LanguageSetupPolicy {
    // Existing users who already chose a language keep their choice without another prompt.
    fun required(completed: Boolean, hasSavedLanguage: Boolean) = !completed && !hasSavedLanguage
}
