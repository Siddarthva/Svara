package com.svara.app.widget

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.datastore.preferences.core.Preferences
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalSize
import androidx.glance.action.ActionParameters
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.LinearProgressIndicator
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ContentScale
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.semantics.contentDescription
import androidx.glance.semantics.semantics
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.svara.app.MainActivity
import com.svara.app.R
import com.svara.app.appearance.Argb
import com.svara.app.appearance.Brightness
import com.svara.app.appearance.ColourSystem
import com.svara.app.appearance.colour.EffectivePalette
import com.svara.app.data.appearance.CustomizationRepository
import com.svara.app.data.appearance.PaletteRepository
import com.svara.app.data.appearance.ThemeRepository
import com.svara.app.data.artwork.Artwork
import com.svara.app.data.artwork.ArtworkLoader
import com.svara.app.data.covers.CoverOverrides
import com.svara.app.domain.repository.FavoritesRepository
import com.svara.app.domain.repository.LibraryRepository
import com.svara.app.ui.theme.WidgetTokens
import com.svara.app.ui.theme.toColor
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@EntryPoint
@InstallIn(SingletonComponent::class)
interface WidgetEntryPoint {
    fun store(): WidgetStateStore
    fun updater(): WidgetUpdater
    fun themes(): ThemeRepository
    fun customization(): CustomizationRepository
    fun palettes(): PaletteRepository
    fun artwork(): ArtworkLoader
    fun covers(): CoverOverrides
    fun library(): LibraryRepository
    fun favourites(): FavoritesRepository
}

internal fun Context.widgetEntry(): WidgetEntryPoint = EntryPointAccessors.fromApplication(applicationContext, WidgetEntryPoint::class.java)

enum class WidgetKind { SMALL, MEDIUM, LARGE }

/** Colours resolved for one widget: from the active preset, the custom colour or the artwork. */
data class WidgetColours(val background: Color, val text: Color, val secondary: Color, val accent: Color)

/**
 * The three SVARĀ widgets share one composable; each kind only decides what fits. Content comes
 * from the stored snapshot (no polling), artwork from the one artwork loader at widget size, and
 * colours from the effective palette, so the widgets follow preset, custom colour and artwork.
 */
abstract class SvaraWidget(private val kind: WidgetKind) : GlanceAppWidget() {
    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val entry = context.widgetEntry()
        val snapshot = entry.store().read()
        val artPx = when (kind) { WidgetKind.SMALL -> WidgetTokens.SMALL_ART_PX; WidgetKind.MEDIUM -> WidgetTokens.MEDIUM_ART_PX; WidgetKind.LARGE -> WidgetTokens.LARGE_ART_PX }
        val bitmap = if (snapshot.empty) null else withContext(Dispatchers.IO) {
            val cover = entry.covers().custom(snapshot.songId, snapshot.albumId)
            runCatching { entry.artwork().load(Artwork(snapshot.songId, snapshot.albumId, cover?.scope, cover?.version ?: 0), artPx) }.getOrNull()
        }
        val palettes = withContext(Dispatchers.IO) { WidgetSettings.AccentSource.entries.associateWith { coloursFor(context, entry, it, snapshot) } }
        provideContent {
            val settings = WidgetSettings.from(currentState<Preferences>())
            WidgetContent(kind, snapshot, settings, palettes.getValue(settings.accent), bitmap)
        }
    }

    private suspend fun coloursFor(context: Context, entry: WidgetEntryPoint, source: WidgetSettings.AccentSource, s: NowPlayingSnapshot): WidgetColours {
        val theme = entry.themes().resolved.value
        val brightness = entry.customization().customization.value.brightness
        val dark = when (brightness) {
            Brightness.DARK -> true
            Brightness.LIGHT -> false
            Brightness.SYSTEM -> (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        }
        val albumAccent: Argb? = if (source == WidgetSettings.AccentSource.ARTWORK && !s.empty) {
            val song = entry.library().songById(s.songId)
            runCatching { entry.palettes().coloursFor(s.songId, s.albumId, song?.dateModifiedSec ?: 0, song?.album, song?.fileName.orEmpty()).accent }.getOrNull()
        } else null
        val t = when (source) {
            WidgetSettings.AccentSource.PRESET -> theme.copy(colourSystem = ColourSystem.PRESET)
            WidgetSettings.AccentSource.CUSTOM -> theme
            WidgetSettings.AccentSource.ARTWORK -> if (albumAccent != null) theme.copy(colourSystem = ColourSystem.ALBUM) else theme
        }
        val p = EffectivePalette.build(t.spec.tokens, t, dark, false, albumAccent).palette
        return WidgetColours(p.background.toColor(), p.primaryText.toColor(), p.secondaryText.toColor(), p.accent.toColor())
    }
}

class SmallWidget : SvaraWidget(WidgetKind.SMALL)
class MediumWidget : SvaraWidget(WidgetKind.MEDIUM)
class LargeWidget : SvaraWidget(WidgetKind.LARGE)

class SmallWidgetReceiver : GlanceAppWidgetReceiver() { override val glanceAppWidget: GlanceAppWidget = SmallWidget() }
class MediumWidgetReceiver : GlanceAppWidgetReceiver() { override val glanceAppWidget: GlanceAppWidget = MediumWidget() }
class LargeWidgetReceiver : GlanceAppWidgetReceiver() { override val glanceAppWidget: GlanceAppWidget = LargeWidget() }

@Composable
private fun WidgetContent(kind: WidgetKind, s: NowPlayingSnapshot, settings: WidgetSettings, c: WidgetColours, art: Bitmap?) {
    val size = LocalSize.current
    val bg = when (settings.background) {
        WidgetSettings.Background.TRANSPARENT -> c.background.copy(alpha = 0f)
        WidgetSettings.Background.TRANSLUCENT -> c.background.copy(alpha = WidgetTokens.TRANSLUCENT_ALPHA)
        WidgetSettings.Background.SOLID -> c.background
    }
    val open = actionStartActivity(Intent().setClassName("com.svara.app", MainActivity::class.java.name).setAction(MainActivity.ACTION_OPEN_NOW_PLAYING))
    val root = GlanceModifier.fillMaxSize().background(ColorProvider(bg)).cornerRadius(WidgetTokens.corner).padding(WidgetTokens.padding)
    if (s.empty) {
        Column(root.clickable(open), verticalAlignment = Alignment.CenterVertically, horizontalAlignment = Alignment.CenterHorizontally) {
            Image(ImageProvider(R.drawable.ic_widget_music), "SVARĀ", GlanceModifier.size(WidgetTokens.icon), colorFilter = ColorFilter.tint(ColorProvider(c.accent)))
            Text("Nothing queued. Tap to choose music.", style = TextStyle(color = ColorProvider(c.text), fontSize = WidgetTokens.body))
        }
        return
    }
    val compact = settings.compact || kind == WidgetKind.SMALL && size.height < WidgetTokens.tallSmall
    when (kind) {
        WidgetKind.SMALL -> Row(root, verticalAlignment = Alignment.CenterVertically) {
            if (settings.showArtwork) Artwork(art, c, GlanceModifier.size(WidgetTokens.smallArt).clickable(open))
            if (settings.showText) Titles(s, c, GlanceModifier.defaultWeight().padding(horizontal = WidgetTokens.gap).clickable(open), compact)
            else Spacer(GlanceModifier.defaultWeight())
            if (settings.showControls) Controls(s, c, showSkip = size.width >= WidgetTokens.skipFitsWidth)
        }
        WidgetKind.MEDIUM -> Row(root, verticalAlignment = Alignment.CenterVertically) {
            if (settings.showArtwork) Artwork(art, c, GlanceModifier.size(WidgetTokens.mediumArt).clickable(open))
            Column(GlanceModifier.defaultWeight().fillMaxHeight().padding(start = WidgetTokens.gap), verticalAlignment = Alignment.CenterVertically) {
                if (settings.showText) Titles(s, c, GlanceModifier.fillMaxWidth().clickable(open), compact)
                if (!compact) Progress(s, c)
                if (settings.showControls) Controls(s, c, showSkip = true)
            }
        }
        WidgetKind.LARGE -> Column(root, horizontalAlignment = Alignment.CenterHorizontally) {
            if (settings.showArtwork) Artwork(art, c, GlanceModifier.defaultWeight().fillMaxWidth().clickable(open))
            if (settings.showText) Titles(s, c, GlanceModifier.fillMaxWidth().padding(top = WidgetTokens.gap).clickable(open), compact)
            Progress(s, c)
            Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconAction(if (s.favourite) R.drawable.ic_widget_favourite else R.drawable.ic_widget_favourite_outline, if (s.favourite) "Remove from favourites" else "Add to favourites", c.accent, actionRunCallback<FavouriteAction>())
                Spacer(GlanceModifier.defaultWeight())
                if (settings.showControls) Controls(s, c, showSkip = true)
                Spacer(GlanceModifier.defaultWeight())
                val queue = actionStartActivity(Intent().setClassName("com.svara.app", MainActivity::class.java.name).setAction(MainActivity.ACTION_OPEN_QUEUE))
                IconAction(R.drawable.ic_widget_queue, "Open queue", c.secondary, queue)
            }
            if (!compact && s.upNext.isNotEmpty()) {
                Text("Up next: " + s.upNext.joinToString(" · "), style = TextStyle(color = ColorProvider(c.secondary), fontSize = WidgetTokens.caption), maxLines = 1)
            }
        }
    }
}

@Composable
private fun Artwork(art: Bitmap?, c: WidgetColours, modifier: GlanceModifier) {
    if (art != null) {
        Image(ImageProvider(art), "Album artwork", modifier.cornerRadius(WidgetTokens.artCorner), contentScale = ContentScale.Crop)
    } else {
        // Fallback: the accent with a music symbol, never an empty hole.
        Box(modifier.background(ColorProvider(c.accent.copy(alpha = WidgetTokens.FALLBACK_ALPHA))).cornerRadius(WidgetTokens.artCorner), contentAlignment = Alignment.Center) {
            Image(ImageProvider(R.drawable.ic_widget_music), null, GlanceModifier.size(WidgetTokens.icon), colorFilter = ColorFilter.tint(ColorProvider(c.text)))
        }
    }
}

@Composable
private fun Titles(s: NowPlayingSnapshot, c: WidgetColours, modifier: GlanceModifier, compact: Boolean) {
    Column(modifier) {
        Text(s.title, style = TextStyle(color = ColorProvider(c.text), fontSize = WidgetTokens.title, fontWeight = FontWeight.Medium), maxLines = 1)
        if (!compact || s.artist.isNotEmpty()) Text(s.artist, style = TextStyle(color = ColorProvider(c.secondary), fontSize = WidgetTokens.body), maxLines = 1)
    }
}

/** Progress as of the last track or play-state change; widgets can't animate it without frequent updates. */
@Composable
private fun Progress(s: NowPlayingSnapshot, c: WidgetColours) {
    LinearProgressIndicator(
        progress = s.progress,
        modifier = GlanceModifier.fillMaxWidth().height(WidgetTokens.progress).padding(vertical = WidgetTokens.progressPadding),
        color = ColorProvider(c.accent),
        backgroundColor = ColorProvider(c.secondary.copy(alpha = WidgetTokens.TRACK_ALPHA)),
    )
}

@Composable
private fun Controls(s: NowPlayingSnapshot, c: WidgetColours, showSkip: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (showSkip) IconAction(R.drawable.ic_widget_previous, "Previous", c.text, actionRunCallback<PreviousAction>())
        IconAction(
            if (s.isPlaying) R.drawable.ic_widget_pause else R.drawable.ic_widget_play,
            if (s.isPlaying) "Pause" else "Play", c.accent, actionRunCallback<PlayPauseAction>(), large = true,
        )
        if (showSkip) IconAction(R.drawable.ic_widget_next, "Next", c.text, actionRunCallback<NextAction>())
    }
}

@Composable
private fun IconAction(icon: Int, label: String, tint: Color, action: androidx.glance.action.Action, large: Boolean = false) {
    Box(
        GlanceModifier.size(if (large) WidgetTokens.touchLarge else WidgetTokens.touch).clickable(action).semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Image(ImageProvider(icon), label, GlanceModifier.size(if (large) WidgetTokens.iconLarge else WidgetTokens.icon), colorFilter = ColorFilter.tint(ColorProvider(tint)))
    }
}

/** Widget button actions run the real MediaSession commands (see [WidgetCommands]). */
class PlayPauseAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) = WidgetCommands.playPause(context)
}

class NextAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) = WidgetCommands.next(context)
}

class PreviousAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) = WidgetCommands.previous(context)
}

class FavouriteAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) = WidgetCommands.toggleFavourite(context)
}

