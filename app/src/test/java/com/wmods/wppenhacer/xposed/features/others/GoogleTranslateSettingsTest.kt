package com.wmods.wppenhacer.xposed.features.others

import org.junit.Assert.*
import org.junit.Test

class GoogleTranslateSettingsTest {
    @Test fun explicitSourceAndTargetSurviveRoundTrip() {
        val value = GoogleTranslationConfig(true, "id", "tr")
        assertEquals(value, GoogleTranslateSettings.decode(value.encode()))
    }
    @Test fun perChatOffOverridesEnabledGlobalWithoutLosingPair() {
        val off = GoogleTranslationConfig(false, "ar", "en")
        assertEquals(off, GoogleTranslateSettings.resolve(off.encode(), "on|auto|tr", null, null))
    }
    @Test fun oldPerChatSelectionSurvivesGlobalMigration() {
        assertEquals(GoogleTranslationConfig(true, "auto", "id"),
            GoogleTranslateSettings.resolve(null, "on|ar|tr", "id", "en"))
    }
    @Test fun inheritanceUsesBothGlobalLanguages() {
        assertEquals(GoogleTranslationConfig(true, "id", "tr"),
            GoogleTranslateSettings.resolve(null, "on|id|tr", null, "en"))
    }
    @Test fun oldGlobalSelectionMigratesWithDetection() {
        assertEquals(GoogleTranslationConfig(true, "auto", "ar"),
            GoogleTranslateSettings.resolve(null, null, null, "ar"))
    }
    @Test fun groupsKeepTheirOwnIdAndDoNotCollapseToParticipant() {
        assertEquals("120363000123456789@g.us", GoogleTranslateSettings.normalizeChatId("120363000123456789@g.us"))
        assertEquals("905551234567-1234567890@g.us", GoogleTranslateSettings.normalizeChatId("905551234567-1234567890@g.us"))
        assertNotEquals(GoogleTranslateSettings.normalizeChatId("123@g.us"), GoogleTranslateSettings.normalizeChatId("123@s.whatsapp.net"))
    }
    @Test fun invalidSettingsDoNotEnableUnexpectedRequests() {
        assertNull(GoogleTranslateSettings.decode("on|auto|auto"))
        assertNull(GoogleTranslateSettings.decode("on|invalid|en"))
        assertEquals(GoogleTranslationConfig(), GoogleTranslateSettings.resolve("broken", null, null, null))
        assertNull(GoogleTranslateSettings.normalizeChatId("status@broadcast"))
        assertNull(GoogleTranslateSettings.normalizeChatId("123@newsletter"))
    }
    @Test fun editingDraftDoesNotChangeSavedConfiguration() {
        val saved = GoogleTranslationConfig(true, "auto", "en")
        val draft = saved.copy(source = "id", target = "tr")
        assertEquals("on|auto|en", saved.encode())
        assertEquals("on|id|tr", draft.encode())
    }
}
