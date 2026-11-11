package com.svara.app.appearance

import com.svara.app.appearance.PresetId.ANAHATA
import com.svara.app.appearance.PresetId.RAGA
import com.svara.app.appearance.PresetId.SHRUTI
import com.svara.app.appearance.PresetId.SPANDA
import com.svara.app.appearance.PresetId.TALA

/**
 * Small signature animations. Each theme uses its own set; [built] is false for the ones that are
 * not available yet, which Appearance lists as "Coming in a later update".
 */
enum class AnimationExtra(val label: String, val themes: Set<PresetId>, val built: Boolean, val defaultOn: Boolean = true) {
    HEART_BURST("Heart burst", setOf(ANAHATA, SHRUTI, RAGA), built = true),
    EQUALIZER_BARS("Equalizer bars", setOf(SHRUTI, RAGA, SPANDA), built = false),
    PRESS_DEPRESS("Press and release", setOf(ANAHATA, SHRUTI, TALA), built = true),
    SCRUBBER_MAGNIFY("Scrubber magnify", setOf(ANAHATA, TALA), built = false),
    KINETIC_LYRICS("Kinetic lyrics", setOf(SHRUTI, TALA), built = false),
    GRADIENT_BLEED("Gradient bleed", setOf(ANAHATA, SHRUTI, RAGA), built = false),
    MINI_PLAYER_SWIPE("Mini-player swipe", setOf(SHRUTI, SPANDA), built = false),
    ELASTIC_COVER("Elastic cover", setOf(TALA), built = false),
    PILL_MORPH("Pill morph", setOf(TALA, RAGA), built = false),
    SPRING_CONTEXT_MENU("Spring context menu", setOf(TALA), built = false),
    LIVING_COVER("Living cover", setOf(ANAHATA, TALA), built = false),
    EXPAND_DRAWER("Credits drawer", setOf(ANAHATA, SPANDA), built = false),
    RADAR_PULSE("Scan pulse", setOf(SPANDA), built = false),
    CARD_FLIP("Card flip on shuffle", setOf(RAGA, SPANDA), built = false),
    FROSTED_DEPTH("Frosted depth", setOf(TALA, SPANDA), built = false),
    AUDIO_BADGE("Audio badge", setOf(TALA, SPANDA), built = false),
    PLAY_PAUSE_MORPH("Play and pause morph", setOf(ANAHATA, TALA), built = true),
    TAB_INDICATOR_SLIDE("Tab indicator slide", setOf(SHRUTI), built = false),
    LIST_STAGGER("List stagger", setOf(ANAHATA, RAGA), built = false),
    SKELETON_SHIMMER("Loading shimmer", setOf(ANAHATA), built = false),
    SLEEP_TIMER_RING("Sleep timer ring", setOf(SHRUTI), built = false),
    NOTE_DRIFT("Note drift", setOf(RAGA, SPANDA), built = true),
    ARTWORK_LIFT("Artwork lift", setOf(ANAHATA), built = false),
    PULL_TO_REFRESH_WAVE("Pull to refresh wave", setOf(RAGA), built = false),
    VINYL("Vinyl", setOf(ANAHATA, SPANDA), built = false, defaultOn = false),
}

object ExtrasPolicy {
    val DEFAULT_OFF: Set<AnimationExtra> = AnimationExtra.entries.filter { !it.defaultOn }.toSet()

    /**
     * Whether [extra] may run: built, part of [preset]'s set, the master switch and its own switch
     * on, and motion not at the minimal level (reduced motion, Efficiency, battery saver, heat).
     */
    fun allowed(extra: AnimationExtra, preset: PresetId, groups: MotionGroups, custom: Customization): Boolean =
        extra.built && preset in extra.themes && custom.animationExtras && extra !in custom.extrasOff && !groups.minimal
}
