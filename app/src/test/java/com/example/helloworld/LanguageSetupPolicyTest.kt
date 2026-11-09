package com.example.helloworld

import org.junit.Assert.*
import org.junit.Test

class LanguageSetupPolicyTest {
    @Test fun freshInstallRequiresSelection() {
        assertTrue(LanguageSetupPolicy.required(false, false))
    }
    @Test fun savedLanguageSkipsPromptForExistingUsers() {
        assertFalse(LanguageSetupPolicy.required(false, true))
    }
    @Test fun completedSetupNeverRepeats() {
        assertFalse(LanguageSetupPolicy.required(true, true))
        assertFalse(LanguageSetupPolicy.required(true, false))
    }
}
