package com.svara.app.ui.theme

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Component sizes for ETHEREAL's own composables (and the older density variants they keep).
 * These are the values those composables already drew with; they moved here so the preset
 * packages contain no raw sizes.
 */
object AnahataTokens {
    val none: Dp = 0.dp

    // Mini-player.
    val miniProgress: Dp = 2.dp
    val miniProgressDense: Dp = 3.dp
    val miniHeight: Dp = 56.dp
    val miniHeightDense: Dp = 64.dp
    val miniArt: Dp = 44.dp
    val miniArtDense: Dp = 48.dp
    val miniSwipe: Dp = 64.dp
    val denseButton: Dp = 56.dp
    val denseIcon: Dp = 32.dp

    // Player body.
    val bassPulse: Dp = 140.dp
    val playerMaxArtwork: Dp = 480.dp

    // Tiles and grids.
    val tileArtworkCentred: Dp = 168.dp
    val tileEditorial: Dp = 156.dp
    val tileDense: Dp = 112.dp
    val tileColour: Dp = 148.dp
    val tileIcon: Dp = 40.dp
    val tileGap: Dp = 6.dp
    val gridEditorial: Dp = 184.dp
    val gridDense: Dp = 120.dp
    val gridDefault: Dp = 150.dp
}
