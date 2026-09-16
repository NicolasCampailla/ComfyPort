package com.comfyport.theme

import androidx.compose.ui.graphics.Color

val Black = Color(0xFF000000)
val DarkGray = Color(0xFF121212)
val CardGray = Color(0xFF1A1A1E)
val CardBorder = Color(0xFF282830)
val CardBackgroundElevated = Color(0xFF222228)
val SurfaceBackground = Color(0xFF0D0D10)
val White = Color(0xFFFFFFFF)
val LightGray = Color(0xFFE0E0E0)
val AccentGray = Color(0xFF8E8E98)
val AccentRed = Color(0xFFFF5252)
val SuccessGreen = Color(0xFF00E676)

data class AppHighlightColor(
    val id: String,
    val label: String,
    val primary: Color,
    val onPrimary: Color,
    val gradientEnd: Color,
    val subtleBackground: Color,
    val borderHighlight: Color
) {
    companion object {
        val CYAN = AppHighlightColor(
            id = "cyan",
            label = "Electric Cyan",
            primary = Color(0xFF00E5FF),
            onPrimary = Color(0xFF000000),
            gradientEnd = Color(0xFF00B0FF),
            subtleBackground = Color(0x2200E5FF),
            borderHighlight = Color(0x6600E5FF)
        )
        val EMERALD = AppHighlightColor(
            id = "emerald",
            label = "Emerald Green",
            primary = Color(0xFF00E676),
            onPrimary = Color(0xFF000000),
            gradientEnd = Color(0xFF00B0FF),
            subtleBackground = Color(0x2200E676),
            borderHighlight = Color(0x6600E676)
        )
        val PURPLE = AppHighlightColor(
            id = "purple",
            label = "Vibrant Violet",
            primary = Color(0xFFA855F7),
            onPrimary = Color(0xFFFFFFFF),
            gradientEnd = Color(0xFF6366F1),
            subtleBackground = Color(0x25A855F7),
            borderHighlight = Color(0x66A855F7)
        )
        val AMBER = AppHighlightColor(
            id = "amber",
            label = "Sunset Amber",
            primary = Color(0xFFFF9100),
            onPrimary = Color(0xFF000000),
            gradientEnd = Color(0xFFFF5722),
            subtleBackground = Color(0x22FF9100),
            borderHighlight = Color(0x66FF9100)
        )
        val PLATINUM = AppHighlightColor(
            id = "platinum",
            label = "Platinum White",
            primary = Color(0xFFFFFFFF),
            onPrimary = Color(0xFF000000),
            gradientEnd = Color(0xFFE0E0E0),
            subtleBackground = Color(0x20FFFFFF),
            borderHighlight = Color(0x55FFFFFF)
        )

        const val DEFAULT_CUSTOM_HEX = "#FF2A6D"

        val CUSTOM_DEFAULT = AppHighlightColor(
            id = DEFAULT_CUSTOM_HEX,
            label = "Custom Hex",
            primary = Color(0xFFFF2A6D),
            onPrimary = Color(0xFFFFFFFF),
            gradientEnd = Color(0xFFFF5252),
            subtleBackground = Color(0x22FF2A6D),
            borderHighlight = Color(0x66FF2A6D)
        )

        val PRESETS: List<AppHighlightColor> = listOf(CYAN, EMERALD, PURPLE, AMBER, PLATINUM)

        fun values(): Array<AppHighlightColor> = PRESETS.toTypedArray()

        fun fromId(id: String?): AppHighlightColor {
            if (id.isNullOrBlank()) return CYAN
            if (id.equals("custom", ignoreCase = true)) return CUSTOM_DEFAULT
            PRESETS.firstOrNull { it.id.equals(id, ignoreCase = true) }?.let { return it }
            return parseHex(id) ?: CYAN
        }

        fun parseHex(hexStr: String): AppHighlightColor? {
            val cleaned = hexStr.trim().removePrefix("#")
            if (cleaned.length != 6 && cleaned.length != 8) return null
            return try {
                val colorLong = cleaned.toLong(16)
                val argb = if (cleaned.length == 6) {
                    0xFF000000L or colorLong
                } else {
                    colorLong
                }
                val primaryColor = Color(argb)
                val r = ((argb shr 16) and 0xFF) / 255.0
                val g = ((argb shr 8) and 0xFF) / 255.0
                val b = (argb and 0xFF) / 255.0
                val luminance = 0.299 * r + 0.587 * g + 0.114 * b
                val onPrimaryColor = if (luminance > 0.5) Color(0xFF000000) else Color(0xFFFFFFFF)

                val subtle = primaryColor.copy(alpha = 0.18f)
                val border = primaryColor.copy(alpha = 0.45f)
                val gradient = primaryColor.copy(alpha = 0.85f)

                val formattedHex = "#" + cleaned.takeLast(6).uppercase()
                AppHighlightColor(
                    id = formattedHex,
                    label = "Custom ($formattedHex)",
                    primary = primaryColor,
                    onPrimary = onPrimaryColor,
                    gradientEnd = gradient,
                    subtleBackground = subtle,
                    borderHighlight = border
                )
            } catch (e: Exception) {
                null
            }
        }
    }
}
