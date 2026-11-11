package com.svara.app.ui.theme

import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.svara.app.R

/**
 * The one text family for every preset: Roboto, bundled (Apache License 2.0, see
 * assets/licenses/roboto-LICENSE.txt) as four static weights. Bundled rather than the platform's
 * "sans-serif", which follows the phone's system font: manufacturers' font-style settings can
 * make that a serif face. Glyphs outside the Latin subset fall back to the system fonts.
 */
val AppFont: FontFamily = FontFamily(
    Font(R.font.roboto_regular, FontWeight.Normal),
    Font(R.font.roboto_medium, FontWeight.Medium),
    Font(R.font.roboto_semibold, FontWeight.SemiBold),
    Font(R.font.roboto_bold, FontWeight.Bold),
)
