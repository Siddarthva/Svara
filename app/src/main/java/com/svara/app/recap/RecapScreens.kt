package com.svara.app.recap

import android.graphics.Bitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.TrendingDown
import androidx.compose.material.icons.automirrored.rounded.TrendingUp
import androidx.compose.material.icons.rounded.AutoGraph
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Timeline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.svara.app.appearance.MotionLevel
import com.svara.app.domain.recap.Recap
import com.svara.app.domain.recap.RecapEngine
import com.svara.app.domain.recap.RecapScrim
import com.svara.app.domain.recap.RecapTransition
import com.svara.app.ui.theme.RecapGradient
import androidx.compose.ui.zIndex
import androidx.compose.ui.graphics.compositeOver
import com.svara.app.domain.recap.RecapKind
import com.svara.app.domain.recap.RecapPeriod
import com.svara.app.domain.recap.RankedSong
import com.svara.app.ui.components.ArtworkImage
import com.svara.app.ui.components.BackButton
import com.svara.app.ui.motion.LocalMotionGroups
import com.svara.app.ui.navigation.Navigator
import com.svara.app.ui.presets.common.rememberAlbumColour
import com.svara.app.ui.theme.LocalAppearance
import com.svara.app.ui.theme.RecapTokens
import com.svara.app.ui.theme.Spacing
import kotlinx.coroutines.launch
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlin.math.roundToInt
import kotlin.math.sin

// ---------- text ----------

fun RecapPeriod.label(): String {
    val f = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
    return when (kind) {
        RecapKind.WEEK -> "${f.format(start)} – ${f.format(end.minusDays(1))}"
        RecapKind.MONTH -> DateTimeFormatter.ofPattern("LLLL yyyy").format(start)
        RecapKind.YEAR -> start.year.toString()
    }
}

private fun minutesText(ms: Long) = "%,d minutes".format(ms / 60_000)
private fun durationText(ms: Long): String {
    val m = ms / 60_000
    return if (m >= 60) "${m / 60} h ${m % 60} min" else "$m min"
}
private fun plays(n: Int) = if (n == 1) "1 play" else "$n plays"

/** The story's cards, in order; each appears only when its data exists. */
enum class RecapCard {
    INTRO, TOTAL, TOP_SONG, TOP_SONGS, ARTISTS, ALBUM, GENRES, WHEN, STREAK, NEW, REPLAYED, COMPARE, FINALE;

    fun available(r: Recap): Boolean = when (this) {
        INTRO, FINALE -> true
        TOTAL -> r.listenedMs > 0
        TOP_SONG -> r.topSongs.isNotEmpty()
        TOP_SONGS -> r.topSongs.size > 1
        ARTISTS -> r.topArtists.isNotEmpty()
        ALBUM -> r.topAlbum != null
        GENRES -> r.topGenres.isNotEmpty()
        WHEN -> r.plays > 0
        STREAK -> r.streakDays > 0
        NEW -> r.newSongs > 0
        REPLAYED -> r.mostReplayed != null
        COMPARE -> r.changePercent != null
    }

    /** Heading and lines: what TalkBack reads and what a shared image shows. */
    fun poster(r: Recap): SharePoster {
        val period = r.period.label()
        return when (this) {
            INTRO -> SharePoster(r.period.kind.title, listOf(period, "${plays(r.plays)} of ${r.distinctSongs} songs"), period)
            TOTAL -> SharePoster(minutesText(r.listenedMs), buildList {
                val albums = r.minutes / RecapTokens.AVERAGE_ALBUM_MINUTES
                if (albums >= 1) add("That's about $albums full ${if (albums == 1L) "album" else "albums"} of ${RecapTokens.AVERAGE_ALBUM_MINUTES} minutes")
                if (r.countOnlyPlays > 0) add("${plays(r.countOnlyPlays)} from before listening time was recorded are not included")
            }, period)
            TOP_SONG -> r.topSongs.first().let { s -> SharePoster("Top song: ${s.title}", listOfNotNull(s.artist, plays(s.plays)), period) }
            TOP_SONGS -> SharePoster("Top songs", r.topSongs.mapIndexed { i, s -> "${i + 1}. ${s.title}" + (s.artist?.let { " – $it" } ?: "") + ", ${plays(s.plays)}" }, period)
            ARTISTS -> SharePoster("Top artist: ${r.topArtists.first().name}", r.topArtists.mapIndexed { i, a -> "${i + 1}. ${a.name}, ${plays(a.plays)}" }, period)
            ALBUM -> r.topAlbum!!.let { a -> SharePoster("Top album: ${a.name}", listOfNotNull(a.artist, plays(a.plays)), period) }
            GENRES -> SharePoster("Top genres", r.topGenres.mapIndexed { i, g -> "${i + 1}. ${g.name}, ${plays(g.plays)}" }, period)
            WHEN -> SharePoster("When you listen", listOfNotNull(
                r.favouriteTime?.let { "Most plays in the ${it.label.lowercase()}" },
                r.mostActiveDay?.let { "Busiest day: ${DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL).format(it)}, ${plays(r.mostActiveDayPlays)}" },
            ), period)
            STREAK -> SharePoster("${r.streakDays}-day listening streak", listOfNotNull(r.longestSessionMs.takeIf { it > 0 }?.let { "Longest session: ${durationText(it)}" }), period)
            NEW -> SharePoster("${r.newSongs} new ${if (r.newSongs == 1) "song" else "songs"}", listOf("Played for the first time this ${r.period.kind.label.lowercase()}") + r.newSongSamples.map { it.title }, period)
            REPLAYED -> r.mostReplayed!!.let { s -> SharePoster("Most replayed: ${s.title}", listOfNotNull(s.artist, "${plays(r.mostReplayedInADay)} in a single day"), period) }
            COMPARE -> r.changePercent!!.let { c -> SharePoster(if (c >= 0) "Up $c%" else "Down ${-c}%", listOf("Listening time compared with the previous ${r.period.kind.label.lowercase()}"), period) }
            FINALE -> SharePoster(r.period.kind.title, listOfNotNull(
                r.topSongs.firstOrNull()?.let { "Top song: ${it.title}" },
                r.topArtists.firstOrNull()?.let { "Top artist: ${it.name}" },
                r.listenedMs.takeIf { it > 0 }?.let { minutesText(it) },
                plays(r.plays),
            ), period)
        }
    }

    companion object {
        fun forRecap(r: Recap): List<RecapCard> = entries.filter { it.available(r) }
    }
}

// ---------- hub ----------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecapScreen(navigator: Navigator, viewModel: RecapViewModel = hiltViewModel()) {
    val enabled by viewModel.historyEnabled.collectAsStateWithLifecycle()
    val kind by viewModel.kind.collectAsStateWithLifecycle()
    val periods by viewModel.periods.collectAsStateWithLifecycle()
    val auto by viewModel.autoAdvance.collectAsStateWithLifecycle()
    val home by viewModel.homeCard.collectAsStateWithLifecycle()
    Scaffold(topBar = { TopAppBar(title = { Text("Recap") }, navigationIcon = { BackButton(navigator::back) }) }) { padding ->
        LazyColumn(Modifier.padding(padding).fillMaxSize()) {
            if (!enabled) {
                item(key = "off") {
                    Column(Modifier.padding(Spacing.l)) {
                        Text("Listening history is off", style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
                        Spacer(Modifier.height(Spacing.s))
                        Text(
                            "Recaps are worked out on this device from your listening history. Turn history on and your recap fills in as you listen. Nothing is sent anywhere.",
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        Spacer(Modifier.height(Spacing.l))
                        Button(onClick = { viewModel.enableHistory() }, modifier = Modifier.heightIn(min = Spacing.touch)) { Text("Turn on history") }
                    }
                }
            } else {
                item(key = "kinds") {
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = Spacing.l, vertical = Spacing.s)) {
                        RecapKind.entries.forEachIndexed { i, k ->
                            SegmentedButton(
                                selected = kind == k,
                                onClick = { viewModel.setKind(k) },
                                shape = SegmentedButtonDefaults.itemShape(i, RecapKind.entries.size),
                                modifier = Modifier.heightIn(min = Spacing.touch),
                            ) { Text(k.label) }
                        }
                    }
                }
                val list = periods
                if (list != null) items(list, key = { it.period.key }) { e ->
                    val current = e.period.endMs(ZoneId.systemDefault()) > System.currentTimeMillis()
                    val title = (if (current) "This ${e.period.kind.label.lowercase()} so far" else e.period.label())
                    val summary = if (e.unlocked) plays(e.plays)
                    else "Keep listening to unlock your recap: ${e.plays} of ${RecapEngine.MIN_PLAYS} plays"
                    ListItem(
                        leadingContent = { Icon(if (e.unlocked) Icons.Rounded.AutoGraph else Icons.Rounded.History, null) },
                        headlineContent = { Text(title) },
                        supportingContent = { Text(summary) },
                        modifier = Modifier
                            .heightIn(min = Spacing.touch)
                            .then(if (e.unlocked) Modifier.clickable(onClickLabel = "Open recap") { navigator.recapStory(e.period.key) } else Modifier)
                            .semantics(mergeDescendants = true) { contentDescription = "$title. $summary" },
                    )
                }
                item(key = "settings") {
                    Spacer(Modifier.height(Spacing.l))
                    SwitchLine("Auto-advance", "Move to the next card every ${RecapTokens.CARD_MS / 1000} seconds", auto, viewModel::setAutoAdvance)
                    SwitchLine("Show recap card on Home", "When a recap has enough plays", home, viewModel::setHomeCard)
                }
            }
        }
    }
}

@Composable
private fun SwitchLine(title: String, summary: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(summary) },
        trailingContent = { Switch(checked = checked, onCheckedChange = null) },
        modifier = Modifier
            .heightIn(min = Spacing.touch)
            .clickable { onChange(!checked) }
            .semantics(mergeDescendants = true) { contentDescription = "$title, ${if (checked) "on" else "off"}" },
    )
}

/** Home's entry: shows only with enough plays and when the user wants it. */
@Composable
fun RecapHomeCard(navigator: Navigator, viewModel: RecapHomeViewModel = hiltViewModel()) {
    val recap by viewModel.newest.collectAsStateWithLifecycle()
    val r = recap ?: return
    val scheme = MaterialTheme.colorScheme
    val rich = recapMotionRich()
    val phase = remember { Animatable(0f) }
    LaunchedEffect(rich) { if (rich) phase.animateTo(1f, tween(RecapTokens.ENTRANCE_MS * 2, easing = LinearOutSlowInEasing)) else phase.snapTo(1f) }
    val summary = "${r.period.label()}: ${plays(r.plays)}" + (r.listenedMs.takeIf { it > 0 }?.let { ", ${minutesText(it)}" } ?: "")
    Surface(
        shape = MaterialTheme.shapes.large,
        color = scheme.primaryContainer,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.l, vertical = Spacing.s)
            .heightIn(min = RecapTokens.homeCardHeight)
            .clickable(onClickLabel = "Open recap") { navigator.recapStory(r.period.key) }
            .semantics(mergeDescendants = true) { contentDescription = "Your Recap. $summary" },
    ) {
        Row(Modifier.padding(Spacing.l), verticalAlignment = Alignment.CenterVertically) {
            // Small preview: three dots rising to the top songs' relative play counts.
            Canvas(Modifier.size(RecapTokens.thumb)) {
                val max = (r.topSongs.maxOfOrNull { it.plays } ?: 1).toFloat()
                val dot = RecapTokens.previewDot.toPx()
                r.topSongs.take(3).forEachIndexed { i, s ->
                    val h = size.height * (s.plays / max) * phase.value
                    drawRoundRect(scheme.primary, Offset(i * (size.width / 3), size.height - h), Size(dot, h), CornerRadius(dot / 2))
                }
            }
            Spacer(Modifier.width(Spacing.l))
            Column(Modifier.weight(1f)) {
                Text("Your Recap", style = MaterialTheme.typography.titleMedium, color = scheme.onPrimaryContainer)
                Text(summary, style = MaterialTheme.typography.bodyMedium, color = scheme.onPrimaryContainer, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/** Recap animations run only when motion is on, not minimal (Efficiency, saver, heat) and not turned off. */
@Composable
private fun recapMotionRich(): Boolean {
    val a = LocalAppearance.current
    return RecapEngine.animationsAllowed(a.motion.enabled, LocalMotionGroups.current, a.custom.recapAnimations)
}

// ---------- story ----------

@Composable
fun RecapStoryScreen(navigator: Navigator, viewModel: RecapViewModel = hiltViewModel()) {
    val recap by viewModel.story.collectAsStateWithLifecycle()
    val auto by viewModel.autoAdvance.collectAsStateWithLifecycle()
    val r = recap
    val scheme = MaterialTheme.colorScheme
    if (r == null) {
        Box(Modifier.fillMaxSize().background(scheme.surface))
        return
    }
    val cards = remember(r) { RecapCard.forRecap(r) }
    val pager = rememberPagerState { cards.size }
    val scope = rememberCoroutineScope()
    val rich = recapMotionRich()
    val a = LocalAppearance.current
    val transition = RecapEngine.transition(a.motion.enabled, LocalMotionGroups.current, a.custom.recapAnimations)
    val gradient = RecapTokens.gradient(a.preset)
    suspend fun go(target: Int) {
        val t = target.coerceIn(0, cards.lastIndex)
        when (transition) {
            RecapTransition.INSTANT -> pager.scrollToPage(t)
            RecapTransition.FADE -> pager.animateScrollToPage(t, animationSpec = tween(RecapTokens.MINIMAL_FADE_MS))
            RecapTransition.WIPE -> pager.animateScrollToPage(t, animationSpec = tween(RecapTokens.WIPE_MS, easing = FastOutSlowInEasing))
        }
    }
    val autoOn = auto && a.motion.enabled
    var paused by remember { mutableStateOf(false) }
    val progress = remember { Animatable(0f) }
    var confirmPlay by remember { mutableStateOf(false) }
    val context = LocalContext.current

    // Auto-advance: the active bar fills over CARD_MS; pausing keeps its place.
    LaunchedEffect(pager.currentPage) { progress.snapTo(0f) }
    LaunchedEffect(pager.currentPage, paused, autoOn) {
        if (!autoOn || paused) return@LaunchedEffect
        val remaining = ((1f - progress.value) * RecapTokens.CARD_MS).roundToInt()
        progress.animateTo(1f, tween(remaining, easing = LinearEasing))
        if (pager.currentPage < cards.lastIndex) go(pager.currentPage + 1)
    }

    // One clock for the whole pager's drifting shapes; stopped when paused or not resumed.
    val fps = when {
        !rich -> 0
        LocalMotionGroups.current.level == MotionLevel.IMMERSIVE -> RecapTokens.IMMERSIVE_FPS
        else -> RecapTokens.BALANCED_FPS
    }
    val clock = remember { mutableFloatStateOf(0f) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(fps, paused, lifecycle) {
        if (fps == 0 || paused) return@LaunchedEffect
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            val frameNs = 1_000_000_000L / fps
            var last = 0L
            while (true) {
                withFrameNanos { t -> if (t - last >= frameNs) { clock.floatValue = t / 1_000_000_000f; last = t } }
            }
        }
    }

    val albumColour = r.topAlbum?.let { rememberAlbumColour(it.songId, it.albumId) }
    val baseColours = listOf(albumColour ?: scheme.primary, scheme.tertiary, scheme.secondary, scheme.primaryContainer)
    var dragY by remember { mutableFloatStateOf(0f) }
    val closeDistance = with(androidx.compose.ui.platform.LocalDensity.current) { RecapTokens.closeDragDistance.toPx() }

    fun shareCard(card: RecapCard) = scope.launch {
        val bmp = RecapShare.render(card.poster(r), shareColours(baseColours[cards.indexOf(card) % baseColours.size], scheme.surface, scheme.onSurface, scheme.primary))
        RecapShare.share(context, bmp)
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(scheme.surface)
            .draggable(
                rememberDraggableState { d -> dragY = (dragY + d).coerceAtLeast(0f) },
                Orientation.Vertical,
                onDragStopped = { if (dragY > closeDistance) navigator.back() else dragY = 0f },
            )
            .graphicsLayer { translationY = dragY },
    ) {
        // Taps: left part goes back, the rest forward; holding pauses. Swipes stay with the pager.
        HorizontalPager(
            pager,
            Modifier.fillMaxSize().pointerInput(cards.size) {
                detectTapGestures(
                    onPress = { paused = true; tryAwaitRelease(); paused = false },
                    onTap = { o ->
                        scope.launch {
                            if (o.x < size.width * RecapTokens.BACK_ZONE) go(pager.currentPage - 1)
                            else if (pager.currentPage < cards.lastIndex) go(pager.currentPage + 1)
                        }
                    },
                )
            },
            key = { cards[it].name },
        ) { page ->
            val card = cards[page]
            val colour = baseColours[page % baseColours.size]
            val active = pager.settledPage == page
            // Cards don't slide: the settled card stays put and the next one is revealed over it,
            // by a growing circle (wipe) or a fade. Read in the draw phase, so a new swipe or tap
            // mid-way just redirects the pager with no jump.
            Box(
                Modifier.fillMaxSize()
                    .zIndex(if (page == pager.settledPage) 0f else 1f)
                    .graphicsLayer {
                        val offset = (pager.currentPage - page) + pager.currentPageOffsetFraction
                        translationX = offset * size.width
                        val reveal = if (page == pager.settledPage) 1f else (1f - kotlin.math.abs(offset)).coerceIn(0f, 1f)
                        if (transition == RecapTransition.WIPE) {
                            clip = true
                            shape = CircleRevealShape(reveal, fromRight = page > pager.settledPage)
                        } else {
                            alpha = reveal
                        }
                    },
            ) { CardFrame(card, r, colour, clock, rich, active, gradient) }
        }
        Column(Modifier.statusBarsPadding().padding(horizontal = Spacing.l, vertical = Spacing.s)) {
            ProgressBars(cards.size, pager, progress, autoOn)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(r.period.label(), style = MaterialTheme.typography.labelLarge, color = scheme.onSurface, modifier = Modifier.weight(1f))
                IconButton(onClick = { shareCard(cards[pager.currentPage]) }) { Icon(Icons.Rounded.Share, "Share this card") }
                IconButton(onClick = navigator::back) { Icon(Icons.Rounded.Close, "Close recap") }
            }
        }
        if (cards[pager.currentPage] == RecapCard.FINALE) {
            Column(
                Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(Spacing.l).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(Spacing.s),
            ) {
                Button(onClick = { shareCard(RecapCard.FINALE) }, modifier = Modifier.fillMaxWidth().heightIn(min = Spacing.touch)) {
                    Icon(Icons.Rounded.Share, null); Spacer(Modifier.width(Spacing.s)); Text("Share")
                }
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                    if (RecapShare.canSave) OutlinedButton(
                        onClick = {
                            scope.launch {
                                val bmp = RecapShare.render(RecapCard.FINALE.poster(r), shareColours(baseColours[0], scheme.surface, scheme.onSurface, scheme.primary))
                                RecapShare.save(context, bmp)
                            }
                        },
                        modifier = Modifier.weight(1f).heightIn(min = Spacing.touch),
                    ) { Icon(Icons.Rounded.Download, null); Spacer(Modifier.width(Spacing.s)); Text("Save to gallery") }
                    // Android 8 and 9 need a storage permission to save, which SVARĀ does not request.
                    else Text("Use Share to save", style = MaterialTheme.typography.bodyMedium, color = scheme.onSurface, modifier = Modifier.weight(1f).align(Alignment.CenterVertically))
                    if (r.topSongs.isNotEmpty()) OutlinedButton(
                        onClick = { if (viewModel.hasQueue()) confirmPlay = true else viewModel.playTopSongs(r) },
                        modifier = Modifier.weight(1f).heightIn(min = Spacing.touch),
                    ) { Icon(Icons.Rounded.PlayArrow, null); Spacer(Modifier.width(Spacing.s)); Text("Play top songs") }
                }
                TextButton(onClick = navigator::back, modifier = Modifier.fillMaxWidth().heightIn(min = Spacing.touch)) { Text("Close") }
            }
        }
    }
    if (confirmPlay) AlertDialog(
        onDismissRequest = { confirmPlay = false },
        title = { Text("Replace the queue?") },
        text = { Text("Your current queue will be replaced with this recap's top songs.") },
        confirmButton = { TextButton(onClick = { confirmPlay = false; viewModel.playTopSongs(r) }) { Text("Replace") } },
        dismissButton = { TextButton(onClick = { confirmPlay = false }) { Text("Cancel") } },
    )
}

/** A circle growing from the side the card comes from; [fraction] 1 covers the whole card. */
private class CircleRevealShape(private val fraction: Float, private val fromRight: Boolean) : androidx.compose.ui.graphics.Shape {
    override fun createOutline(size: Size, layoutDirection: androidx.compose.ui.unit.LayoutDirection, density: androidx.compose.ui.unit.Density): androidx.compose.ui.graphics.Outline {
        val centre = Offset(if (fromRight) size.width else 0f, size.height / 2)
        val radius = kotlin.math.hypot(size.width, size.height) * fraction
        return androidx.compose.ui.graphics.Outline.Generic(androidx.compose.ui.graphics.Path().apply { addOval(androidx.compose.ui.geometry.Rect(centre, radius)) })
    }
}

private fun shareColours(start: Color, end: Color, text: Color, accent: Color) =
    RecapShare.Colours(start.toArgb(), end.toArgb(), text.toArgb(), accent.toArgb())

@Composable
private fun ProgressBars(count: Int, pager: PagerState, progress: Animatable<Float, *>, auto: Boolean) {
    val scheme = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().height(RecapTokens.progressHeight)
            .clearAndSetSemantics { contentDescription = "Card ${pager.currentPage + 1} of $count" },
        horizontalArrangement = Arrangement.spacedBy(RecapTokens.progressGap),
    ) {
        repeat(count) { i ->
            Box(
                Modifier.weight(1f).fillMaxSize().drawBehind {
                    drawRoundRect(scheme.onSurface.copy(alpha = RecapTokens.TRACK_ALPHA), cornerRadius = CornerRadius(size.height))
                    val f = when {
                        i < pager.currentPage -> 1f
                        i > pager.currentPage -> 0f
                        auto -> progress.value
                        else -> 1f
                    }
                    if (f > 0f) drawRoundRect(scheme.onSurface, size = Size(size.width * f, size.height), cornerRadius = CornerRadius(size.height))
                },
            )
        }
    }
}

/** A card's bold background, drifting shapes, scrim and content. */
@Composable
private fun CardFrame(card: RecapCard, r: Recap, colour: Color, clock: State<Float>, rich: Boolean, active: Boolean, gradient: RecapGradient) {
    val scheme = MaterialTheme.colorScheme
    val stops = when (gradient) {
        RecapGradient.MULTI_STOP -> listOf(colour, scheme.tertiary, scheme.secondary, scheme.surface)
        RecapGradient.DARK_GLOW -> listOf(colour.copy(alpha = RecapTokens.GLOW_ALPHA).compositeOver(scheme.surface), scheme.surface)
        else -> listOf(colour, scheme.surface)
    }
    val brush = when (gradient) {
        RecapGradient.SOFT_RADIAL, RecapGradient.DARK_GLOW -> Brush.radialGradient(stops)
        RecapGradient.CRISP_LINEAR -> Brush.verticalGradient(stops)
        RecapGradient.SMOOTH_DIAGONAL, RecapGradient.MULTI_STOP -> Brush.linearGradient(stops)
    }
    // The lightest scrim that keeps text at AA over every colour in this card's background.
    val scrimAlpha = remember(stops, scheme.surface, scheme.onSurface) {
        RecapScrim.alpha(stops.map { it.toArgb() }, scheme.surface.toArgb(), scheme.onSurface.toArgb(), RecapTokens.SCRIM_ALPHA, RecapTokens.SCRIM_STEP, RecapTokens.MIN_CONTRAST)
    }
    val poster = remember(card, r) { card.poster(r) }
    val description = (listOf(poster.heading) + poster.lines).joinToString(". ")
    Box(
        Modifier
            .fillMaxSize()
            .background(brush)
            .drawBehind {
                // Layered shapes; their drift reads the shared clock in the draw phase only.
                val t = clock.value
                val shape = colour.copy(alpha = RecapTokens.SHAPE_ALPHA)
                drawCircle(shape, size.minDimension * 0.45f, Offset(size.width * (0.8f + 0.05f * sin(t * 0.5f)), size.height * 0.2f))
                drawCircle(shape, size.minDimension * 0.3f, Offset(size.width * 0.15f, size.height * (0.75f + 0.03f * sin(t * 0.7f))))
                drawRect(scheme.surface.copy(alpha = scrimAlpha))
            }
            .semantics(mergeDescendants = true) { contentDescription = description },
    ) {
        Column(
            Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = Spacing.xl, vertical = Spacing.xxl * 2),
            verticalArrangement = Arrangement.Center,
        ) {
            CardContent(card, r, rich, active)
        }
    }
}

/** Plays its entrance once each time the card becomes the visible one. */
@Composable
private fun entrance(active: Boolean, rich: Boolean, delayMs: Int = 0): State<Float> {
    val a = remember { Animatable(if (rich) 0f else 1f) }
    LaunchedEffect(active, rich) {
        if (!rich) { a.snapTo(1f); return@LaunchedEffect }
        if (active) a.animateTo(1f, tween(RecapTokens.ENTRANCE_MS, delayMillis = delayMs, easing = FastOutSlowInEasing)) else a.snapTo(0f)
    }
    return a.asState()
}

private fun Modifier.rise(p: State<Float>, distance: Float) = graphicsLayer {
    alpha = p.value
    translationY = (1f - p.value) * distance
}

@Composable
private fun CardContent(card: RecapCard, r: Recap, rich: Boolean, active: Boolean) {
    val scheme = MaterialTheme.colorScheme
    val type = MaterialTheme.typography
    val motion = LocalAppearance.current.theme.spec.tokens.motion
    val shift = with(androidx.compose.ui.platform.LocalDensity.current) { Spacing.xl.toPx() }
    val p = entrance(active, rich)
    val poster = remember(card, r) { card.poster(r) }
    @Composable fun Big(text: String) = Text(text, style = type.displayMedium, fontWeight = FontWeight.Bold, color = scheme.onSurface, modifier = Modifier.rise(p, shift))
    @Composable fun Line(text: String) = Text(text, style = type.titleMedium, color = scheme.onSurface, modifier = Modifier.padding(top = Spacing.s).rise(p, shift))

    when (card) {
        RecapCard.TOTAL -> {
            val count = remember { Animatable(if (rich) 0f else r.minutes.toFloat()) }
            LaunchedEffect(active, rich) {
                if (!rich) count.snapTo(r.minutes.toFloat())
                else if (active) { count.snapTo(0f); count.animateTo(r.minutes.toFloat(), tween(RecapTokens.COUNT_UP_MS, easing = LinearOutSlowInEasing)) }
            }
            Text("You listened for", style = type.titleLarge, color = scheme.onSurface)
            Text("%,d".format(count.value.roundToInt()), style = type.displayLarge, fontWeight = FontWeight.Bold, color = scheme.onSurface)
            Text("minutes", style = type.titleLarge, color = scheme.onSurface)
            poster.lines.forEach { Line(it) }
        }
        RecapCard.TOP_SONG -> {
            val s = r.topSongs.first()
            HeroArtwork(s.songId, s.albumId, rich, active, glow = true, stiffness = motion.springStiffness, damping = motion.springDamping)
            Spacer(Modifier.height(Spacing.l))
            Text("Your top song", style = type.titleMedium, color = scheme.onSurface)
            Big(s.title)
            s.artist?.let { Line(it) }
            Line(plays(s.plays))
        }
        RecapCard.TOP_SONGS -> {
            Big("Top songs")
            r.topSongs.forEachIndexed { i, s -> RankRow(i, s, rich, active) }
        }
        RecapCard.ARTISTS -> {
            Text("Your top artist", style = type.titleMedium, color = scheme.onSurface)
            Big(r.topArtists.first().name)
            Spacer(Modifier.height(Spacing.l))
            val max = r.topArtists.first().plays.toFloat()
            r.topArtists.forEachIndexed { i, a -> GrowBar("${i + 1}. ${a.name}", a.plays / max, i, rich, active) }
        }
        RecapCard.ALBUM -> {
            val a = r.topAlbum!!
            val turn = entrance(active, rich)
            Box(Modifier.graphicsLayer { rotationY = (1f - turn.value) * RecapTokens.PAGE_TURN_DEGREES; alpha = turn.value }) {
                // The stack: two offset layers behind the cover.
                Box(Modifier.graphicsLayer { rotationZ = RecapTokens.STACK_ROTATION; translationX = RecapTokens.stackOffset.toPx() }) {
                    Surface(shape = MaterialTheme.shapes.large, color = scheme.secondaryContainer, modifier = Modifier.size(RecapTokens.heroArtwork)) {}
                }
                Box(Modifier.graphicsLayer { rotationZ = -RecapTokens.STACK_ROTATION; translationX = -RecapTokens.stackOffset.toPx() }) {
                    Surface(shape = MaterialTheme.shapes.large, color = scheme.tertiaryContainer, modifier = Modifier.size(RecapTokens.heroArtwork)) {}
                }
                ArtworkImage(a.songId, a.albumId, Modifier.size(RecapTokens.heroArtwork), shape = MaterialTheme.shapes.large)
            }
            Spacer(Modifier.height(Spacing.l))
            Text("Top album", style = type.titleMedium, color = scheme.onSurface)
            Big(a.name)
            poster.lines.forEach { Line(it) }
        }
        RecapCard.GENRES -> {
            Big("Top genres")
            Spacer(Modifier.height(Spacing.l))
            val max = r.topGenres.first().plays.toFloat()
            r.topGenres.forEachIndexed { i, g -> GrowBar(g.name, g.plays / max, i, rich, active) }
        }
        RecapCard.WHEN -> {
            Big("When you listen")
            Spacer(Modifier.height(Spacing.l))
            HeatStrip(r.hours, "Plays by hour of day", rich, active)
            Spacer(Modifier.height(Spacing.s))
            HeatStrip(r.weekdays, "Plays by day of week", rich, active)
            poster.lines.forEach { Line(it) }
        }
        RecapCard.STREAK -> {
            Icon(Icons.Rounded.Timeline, null, tint = scheme.primary, modifier = Modifier.size(RecapTokens.thumb))
            Big(poster.heading)
            poster.lines.forEach { Line(it) }
        }
        RecapCard.NEW -> {
            Big(poster.heading)
            Line(poster.lines.first())
            Spacer(Modifier.height(Spacing.l))
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                r.newSongSamples.forEachIndexed { i, s ->
                    val q = entrance(active, rich, delayMs = staggerDelay(i))
                    ArtworkImage(s.songId, s.albumId, Modifier.size(RecapTokens.heroArtwork / 3).rise(q, shift), shape = MaterialTheme.shapes.medium)
                }
            }
        }
        RecapCard.REPLAYED -> {
            val s = r.mostReplayed!!
            HeroArtwork(s.songId, s.albumId, rich, active, glow = false, stiffness = motion.springStiffness, damping = motion.springDamping)
            Spacer(Modifier.height(Spacing.l))
            Text("Most replayed", style = type.titleMedium, color = scheme.onSurface)
            Big(s.title)
            val pulse = remember { Animatable(1f) }
            LaunchedEffect(active, rich) {
                if (rich && active) {
                    pulse.animateTo(RecapTokens.PULSE_SCALE, tween(RecapTokens.PULSE_MS / 2))
                    pulse.animateTo(1f, tween(RecapTokens.PULSE_MS / 2))
                }
            }
            Text(
                "${plays(r.mostReplayedInADay)} in a single day",
                style = type.headlineSmall, color = scheme.onSurface,
                modifier = Modifier.padding(top = Spacing.s).graphicsLayer { scaleX = pulse.value; scaleY = pulse.value },
            )
        }
        RecapCard.COMPARE -> {
            val c = r.changePercent!!
            Icon(if (c >= 0) Icons.AutoMirrored.Rounded.TrendingUp else Icons.AutoMirrored.Rounded.TrendingDown, null, tint = scheme.primary, modifier = Modifier.size(RecapTokens.thumb))
            Big(poster.heading)
            poster.lines.forEach { Line(it) }
        }
        RecapCard.INTRO, RecapCard.FINALE -> {
            if (card == RecapCard.FINALE) Sparkle(rich, active)
            Text(poster.period, style = type.titleMedium, color = scheme.onSurface)
            Big(poster.heading)
            poster.lines.forEach { Line(it) }
        }
    }
}

private fun staggerDelay(i: Int) = if (i < RecapTokens.STAGGER_ITEMS) i * RecapTokens.STAGGER_MS else 0

@Composable
private fun HeroArtwork(songId: Long, albumId: Long, rich: Boolean, active: Boolean, glow: Boolean, stiffness: Float, damping: Float) {
    val scale = remember { Animatable(if (rich) RecapTokens.ENTRANCE_SCALE else 1f) }
    LaunchedEffect(active, rich) {
        if (!rich) scale.snapTo(1f)
        else if (active) scale.animateTo(1f, spring(dampingRatio = damping, stiffness = stiffness)) else scale.snapTo(RecapTokens.ENTRANCE_SCALE)
    }
    val glowColour = MaterialTheme.colorScheme.primary.copy(alpha = RecapTokens.GLOW_ALPHA)
    ArtworkImage(
        songId, albumId,
        Modifier
            .size(RecapTokens.heroArtwork)
            .drawBehind { if (glow) drawCircle(glowColour, size.maxDimension * 0.6f) }
            .graphicsLayer { scaleX = scale.value; scaleY = scale.value },
        shape = MaterialTheme.shapes.large,
    )
}

@Composable
private fun RankRow(i: Int, s: RankedSong, rich: Boolean, active: Boolean) {
    val p = entrance(active, rich, delayMs = staggerDelay(i))
    val shift = with(androidx.compose.ui.platform.LocalDensity.current) { Spacing.l.toPx() }
    val scheme = MaterialTheme.colorScheme
    Row(
        Modifier.padding(vertical = Spacing.xs).graphicsLayer { alpha = p.value; translationX = (1f - p.value) * shift },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("${i + 1}", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = scheme.primary, modifier = Modifier.width(RecapTokens.thumb))
        ArtworkImage(s.songId, s.albumId, Modifier.size(RecapTokens.thumb), shape = MaterialTheme.shapes.small)
        Spacer(Modifier.width(Spacing.m))
        Column(Modifier.weight(1f)) {
            Text(s.title, style = MaterialTheme.typography.titleMedium, color = scheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(listOfNotNull(s.artist, plays(s.plays)).joinToString(" • "), style = MaterialTheme.typography.bodyMedium, color = scheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun GrowBar(label: String, fraction: Float, i: Int, rich: Boolean, active: Boolean) {
    val p = entrance(active, rich, delayMs = staggerDelay(i))
    val scheme = MaterialTheme.colorScheme
    Column(Modifier.padding(vertical = Spacing.xs)) {
        Text(label, style = MaterialTheme.typography.titleSmall, color = scheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Box(
            Modifier.fillMaxWidth().height(RecapTokens.barHeight).drawBehind {
                drawRoundRect(scheme.onSurface.copy(alpha = RecapTokens.TRACK_ALPHA), cornerRadius = CornerRadius(size.height / 2))
                drawRoundRect(scheme.primary, size = Size(size.width * fraction * p.value, size.height), cornerRadius = CornerRadius(size.height / 2))
            },
        )
    }
}

@Composable
private fun HeatStrip(values: List<Int>, label: String, rich: Boolean, active: Boolean) {
    val p = entrance(active, rich)
    val scheme = MaterialTheme.colorScheme
    val max = (values.maxOrNull() ?: 0).coerceAtLeast(1).toFloat()
    Text(label, style = MaterialTheme.typography.labelLarge, color = scheme.onSurface)
    Canvas(Modifier.fillMaxWidth().height(RecapTokens.heatHeight)) {
        val gap = RecapTokens.progressGap.toPx()
        val w = (size.width - gap * (values.size - 1)) / values.size
        // Cells fill in left to right as the entrance runs.
        val shown = (values.size * p.value).roundToInt()
        values.forEachIndexed { i, v ->
            val alpha = if (i < shown) RecapTokens.TRACK_ALPHA + (1f - RecapTokens.TRACK_ALPHA) * (v / max) else RecapTokens.TRACK_ALPHA / 2
            drawRoundRect(scheme.primary.copy(alpha = alpha), Offset(i * (w + gap), 0f), Size(w, size.height), CornerRadius(gap))
        }
    }
}

/** A short drift of small accent dots on the finale; 600 ms, once. */
@Composable
private fun Sparkle(rich: Boolean, active: Boolean) {
    if (!rich) return
    val t = remember { Animatable(0f) }
    LaunchedEffect(active) { if (active) { t.snapTo(0f); t.animateTo(1f, tween(RecapTokens.SPARKLE_MS)) } }
    val colour = MaterialTheme.colorScheme.primary
    Canvas(Modifier.fillMaxWidth().height(RecapTokens.heatHeight)) {
        if (t.value <= 0f || t.value >= 1f) return@Canvas
        val dot = RecapTokens.sparkleDot.toPx()
        repeat(STAR_COUNT) { i ->
            val x = size.width * ((i * GOLDEN) % 1f)
            val y = size.height * (1f - t.value) * ((i % 3 + 1) / 3f)
            drawCircle(colour.copy(alpha = 1f - t.value), dot, Offset(x, y))
        }
    }
}

private const val STAR_COUNT = 12
private const val GOLDEN = 0.618f
