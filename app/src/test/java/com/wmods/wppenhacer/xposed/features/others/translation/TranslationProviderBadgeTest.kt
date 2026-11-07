package com.wmods.wppenhacer.xposed.features.others.translation

import org.junit.Assert.*
import org.junit.Test

/**
 * Tests for translation provider badge labels
 */
class TranslationProviderBadgeTest {
    
    @Test
    fun `Google provider badge is exactly GT`() {
        val provider = GoogleTranslationProvider()
        val badge = provider.getBadge()
        
        assertEquals("GT", badge)
        assertNotEquals("gT", badge)
        assertNotEquals("gt", badge)
        assertNotEquals("Gt", badge)
    }
    
    @Test
    fun `Google badge contains no lowercase letters`() {
        val badge = GoogleTranslationProvider().getBadge()
        assertFalse("Google badge should not contain lowercase", badge.any { it.isLowerCase() })
    }
    
    @Test
    fun `Google badge is deterministic`() {
        val provider = GoogleTranslationProvider()
        repeat(10) {
            assertEquals("GT", provider.getBadge())
        }
    }
    
    @Test
    fun `Google badge is short`() {
        val badge = GoogleTranslationProvider().getBadge()
        assertTrue("Google badge should be short", badge.length in 2..4)
    }
    
    @Test
    fun `Badge format matches expected constants`() {
        // These are the only two valid badge strings
        val googleBadge = GoogleTranslationProvider().getBadge()
        assertTrue("Badge must be GT or AIT", googleBadge in setOf("GT", "AIT"))
    }
}
