package com.svara.app.appearance.colour

import com.svara.app.appearance.Argb

/*
 * The 64-colour custom-accent palette: eight groups of eight, in a fixed order. This file is the
 * single source of truth for swatch names and values, and the one data file allowed to hold raw
 * colour values outside the token files.
 */

data class Swatch(val id: String, val name: String, val colour: Argb, val group: SwatchGroup)

enum class SwatchGroup(val label: String) {
    NEUTRALS("Neutrals"), REDS("Reds"), ORANGES("Oranges"), YELLOWS("Yellows"),
    GREENS("Greens"), BLUES("Blues"), PURPLES("Purples"), PINKS("Pinks"),
}

object Swatches {
    private fun group(group: SwatchGroup, vararg entries: Pair<String, Long>) =
        entries.map { (name, rgb) -> Swatch(name.lowercase().replace(' ', '_'), name, Argb(0xFF000000 or rgb), group) }

    val all: List<Swatch> = listOf(
        group(
            SwatchGroup.NEUTRALS,
            "White" to 0xFFFFFF, "Pearl" to 0xE6E6E6, "Silver" to 0xC0C0C0, "Ash" to 0x9E9E9E,
            "Slate" to 0x708090, "Graphite" to 0x4A4F57, "Charcoal" to 0x36393F, "Ink" to 0x1F2226,
        ),
        group(
            SwatchGroup.REDS,
            "Crimson" to 0xDC143C, "Ruby" to 0xE0115F, "Scarlet" to 0xFF2400, "Cherry" to 0xD2042D,
            "Vermilion" to 0xE34234, "Carmine" to 0x960018, "Garnet" to 0x9B2D30, "Maroon" to 0x800000,
        ),
        group(
            SwatchGroup.ORANGES,
            "Amber" to 0xFFBF00, "Tangerine" to 0xF28500, "Pumpkin" to 0xFF7518, "Persimmon" to 0xEC5800,
            "Burnt Orange" to 0xCC5500, "Copper" to 0xB87333, "Rust" to 0xB7410E, "Apricot" to 0xF5A25D,
        ),
        group(
            SwatchGroup.YELLOWS,
            "Gold" to 0xFFD700, "Saffron" to 0xF4C430, "Honey" to 0xE8B923, "Lemon" to 0xFFF44F,
            "Mustard" to 0xE1AD01, "Sunflower" to 0xFFC512, "Straw" to 0xE4D96F, "Champagne" to 0xF7E7A1,
        ),
        group(
            SwatchGroup.GREENS,
            "Emerald" to 0x50C878, "Mint" to 0x3EB489, "Sage" to 0x9CAF88, "Jade" to 0x00A86B,
            "Forest" to 0x228B22, "Teal Green" to 0x00827F, "Lime" to 0xA4DE02, "Olive" to 0x708238,
        ),
        group(
            SwatchGroup.BLUES,
            "Sky" to 0x87CEEB, "Sapphire" to 0x0F52BA, "Cobalt" to 0x0047AB, "Azure" to 0x007FFF,
            "Midnight" to 0x191970, "Cerulean" to 0x007BA7, "Denim" to 0x1560BD, "Ice" to 0xA5D8FF,
        ),
        group(
            SwatchGroup.PURPLES,
            "Violet" to 0x8F00FF, "Lavender" to 0xB57EDC, "Amethyst" to 0x9966CC, "Plum" to 0x8E4585,
            "Indigo" to 0x4B0082, "Orchid" to 0xDA70D6, "Grape" to 0x6F2DA8, "Lilac" to 0xC8A2C8,
        ),
        group(
            SwatchGroup.PINKS,
            "Rose" to 0xFF6B9D, "Coral Pink" to 0xF88379, "Magenta" to 0xFF00FF, "Blush" to 0xDE5D83,
            "Fuchsia" to 0xC154C1, "Bubblegum" to 0xFFC1CC, "Hot Pink" to 0xFF69B4, "Raspberry" to 0xE30B5C,
        ),
    ).flatten()

    fun byId(id: String?): Swatch? = all.firstOrNull { it.id == id }

    fun grouped(): Map<SwatchGroup, List<Swatch>> = all.groupBy { it.group }

    /** Swatches with real hue (everything but the neutrals), used for fallback colours. */
    val chromatic: List<Swatch> get() = all.filter { it.group != SwatchGroup.NEUTRALS }
}
