package com.comfyport.theme

import androidx.compose.ui.graphics.Color
import com.comfyport.data.AppSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeHighlightTest {

    @Test
    fun testFromId_ValidPresetIds() {
        assertEquals(AppHighlightColor.CYAN, AppHighlightColor.fromId("cyan"))
        assertEquals(AppHighlightColor.EMERALD, AppHighlightColor.fromId("emerald"))
        assertEquals(AppHighlightColor.PURPLE, AppHighlightColor.fromId("purple"))
        assertEquals(AppHighlightColor.AMBER, AppHighlightColor.fromId("amber"))
        assertEquals(AppHighlightColor.PLATINUM, AppHighlightColor.fromId("platinum"))
    }

    @Test
    fun testFromId_CaseInsensitive() {
        assertEquals(AppHighlightColor.CYAN, AppHighlightColor.fromId("CYAN"))
        assertEquals(AppHighlightColor.EMERALD, AppHighlightColor.fromId("Emerald"))
        assertEquals(AppHighlightColor.PURPLE, AppHighlightColor.fromId("PuRpLe"))
        assertEquals(AppHighlightColor.AMBER, AppHighlightColor.fromId("AMBER"))
        assertEquals(AppHighlightColor.PLATINUM, AppHighlightColor.fromId("PLATINUM"))
    }

    @Test
    fun testFromId_FallbackToCyan() {
        // null, blank, and unknown IDs fall back to CYAN
        assertEquals(AppHighlightColor.CYAN, AppHighlightColor.fromId(null))
        assertEquals(AppHighlightColor.CYAN, AppHighlightColor.fromId(""))
        assertEquals(AppHighlightColor.CYAN, AppHighlightColor.fromId("unknown_color"))
        // Removed 'rose' should gracefully fall back to CYAN
        assertEquals(AppHighlightColor.CYAN, AppHighlightColor.fromId("rose"))
        assertEquals(AppHighlightColor.CYAN, AppHighlightColor.fromId("ROSE"))
    }

    @Test
    fun testCustomHex_Parsing() {
        // Valid 6-digit hex with '#'
        val customWithHash = AppHighlightColor.fromId("#FF5722")
        assertEquals("#FF5722", customWithHash.id)
        assertEquals("Custom (#FF5722)", customWithHash.label)
        assertEquals(Color(0xFFFF5722), customWithHash.primary)

        // Valid 6-digit hex without '#'
        val customNoHash = AppHighlightColor.fromId("3F51B5")
        assertEquals("#3F51B5", customNoHash.id)
        assertEquals(Color(0xFF3F51B5), customNoHash.primary)

        // Invalid hex formats return null from parseHex and fall back to CYAN in fromId
        assertNull(AppHighlightColor.parseHex("12345"))
        assertNull(AppHighlightColor.parseHex("GGFFFF"))
        assertNull(AppHighlightColor.parseHex("#1234567"))
        assertEquals(AppHighlightColor.CYAN, AppHighlightColor.fromId("invalid_hex"))
    }

    @Test
    fun testCustomDefault_RedPinkishColor() {
        assertEquals("#FF2A6D", AppHighlightColor.DEFAULT_CUSTOM_HEX)
        val customDefault = AppHighlightColor.CUSTOM_DEFAULT
        assertEquals("#FF2A6D", customDefault.id)
        assertEquals("Custom Hex", customDefault.label)
        assertEquals(Color(0xFFFF2A6D), customDefault.primary)
        assertEquals(Color(0xFFFFFFFF), customDefault.onPrimary)

        // fromId("custom") returns CUSTOM_DEFAULT
        assertEquals(customDefault, AppHighlightColor.fromId("custom"))
        assertEquals(customDefault, AppHighlightColor.fromId("CUSTOM"))
    }

    @Test
    fun testCustomHex_LuminanceHighContrast() {
        // Very bright color (Yellow: #FFFF00) -> onPrimary should be Black for high readability
        val brightColor = AppHighlightColor.parseHex("#FFFF00")
        assertNotNull(brightColor)
        assertEquals(Color(0xFF000000), brightColor!!.onPrimary)

        // Very dark color (Dark Navy: #0D1B2A) -> onPrimary should be White
        val darkColor = AppHighlightColor.parseHex("#0D1B2A")
        assertNotNull(darkColor)
        assertEquals(Color(0xFFFFFFFF), darkColor!!.onPrimary)
    }

    @Test
    fun testAppSettings_HighlightColorDefaultAndCopy() {
        val defaultSettings = AppSettings()
        assertEquals("cyan", defaultSettings.highlightColor)

        val updatedPreset = defaultSettings.copy(highlightColor = "emerald")
        assertEquals("emerald", updatedPreset.highlightColor)
        assertEquals(AppHighlightColor.EMERALD, AppHighlightColor.fromId(updatedPreset.highlightColor))

        val updatedCustom = defaultSettings.copy(highlightColor = "#E91E63")
        assertEquals("#E91E63", updatedCustom.highlightColor)
        assertEquals("#E91E63", AppHighlightColor.fromId(updatedCustom.highlightColor).id)
    }

    @Test
    fun testAllPresetColors_HaveValidProperties() {
        assertEquals(5, AppHighlightColor.PRESETS.size)
        AppHighlightColor.values().forEach { color ->
            assertTrue(color.id.isNotBlank())
            assertTrue(color.label.isNotBlank())
            assertNotNull(color.primary)
            assertNotNull(color.onPrimary)
            assertNotNull(color.gradientEnd)
            assertNotNull(color.subtleBackground)
            assertNotNull(color.borderHighlight)
        }
    }
}
