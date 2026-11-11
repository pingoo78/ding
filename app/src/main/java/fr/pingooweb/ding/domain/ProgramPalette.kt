package fr.pingooweb.ding.domain

/**
 * Palette des programmes : 8 paires (conteneur pastel, couleur forte), index 0..7 (valeurs ARGB).
 * Sur un conteneur : texte = encre du thème. Sur la couleur forte : blanc (clair) ou #14161F (sombre).
 */
object ProgramPalette {
    const val SIZE = 8

    val names = listOf("Lavande", "Menthe", "Pêche", "Ciel", "Rose", "Citron", "Corail", "Ardoise")

    val containerLight: List<Long> = listOf(
        0xFFE6E0FF, 0xFFD7F2E3, 0xFFFFE3D1, 0xFFDCEBFF,
        0xFFFFDDE8, 0xFFF4EFC2, 0xFFFFE0DA, 0xFFE3E7EE,
    )
    val strongLight: List<Long> = listOf(
        0xFF5B45E0, 0xFF127A4B, 0xFFC2410C, 0xFF1D5FD1,
        0xFFBE185D, 0xFF716000, 0xFFC2341A, 0xFF3F4A5E,
    )
    val containerDark: List<Long> = listOf(
        0xFF2A2450, 0xFF173A2A, 0xFF3D2417, 0xFF172B47,
        0xFF3F1A2A, 0xFF353112, 0xFF401C16, 0xFF232833,
    )
    val strongDark: List<Long> = listOf(
        0xFFB7A9FF, 0xFF6EE7A8, 0xFFFFB48A, 0xFF8CB8FF,
        0xFFF9A8C9, 0xFFE8D96A, 0xFFFF9A85, 0xFFB4BFD3,
    )

    /** Couleurs fortes (accent) clair / sombre. */
    val light: List<Long> get() = strongLight
    val dark: List<Long> get() = strongDark

    fun argb(index: Int, darkTheme: Boolean): Long {
        val i = index.coerceIn(0, SIZE - 1)
        return if (darkTheme) strongDark[i] else strongLight[i]
    }

    fun containerArgb(index: Int, darkTheme: Boolean): Long {
        val i = index.coerceIn(0, SIZE - 1)
        return if (darkTheme) containerDark[i] else containerLight[i]
    }
}
