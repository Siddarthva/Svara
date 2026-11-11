package com.svara.app.ui.presets.common

import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.LibraryMusic
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import com.svara.app.appearance.NavTab
import com.svara.app.appearance.PresetId
import com.svara.app.appearance.tokens.TabTint
import com.svara.app.ui.theme.LocalPresetUi
import kotlin.math.abs

/** Generic Material Symbols per tab: outlined when inactive, filled when active. */
private fun navIcon(tab: NavTab, preset: PresetId, filled: Boolean): ImageVector = when (tab) {
    // SOLACE's source describes its home tab as a play button inside a circle.
    NavTab.HOME -> if (preset == PresetId.TALA) {
        if (filled) Icons.Filled.PlayCircle else Icons.Outlined.PlayCircle
    } else if (filled) Icons.Filled.Home else Icons.Outlined.Home
    NavTab.SEARCH -> if (filled) Icons.Filled.Search else Icons.Outlined.Search
    NavTab.LIBRARY -> if (filled) Icons.Filled.LibraryMusic else Icons.Outlined.LibraryMusic
    NavTab.SONGS -> Icons.Rounded.MusicNote
    NavTab.ALBUMS -> Icons.Rounded.Album
    NavTab.ARTISTS -> Icons.Rounded.Person
    NavTab.FOLDERS -> Icons.Rounded.Folder
    NavTab.PLAYLISTS -> Icons.AutoMirrored.Rounded.QueueMusic
    NavTab.SETTINGS -> Icons.Rounded.Settings
}

/**
 * The layout presets' bottom bar: labels always visible, icons outlined when inactive and filled
 * when active, tinted with the preset's active colour. Tap scales the item (Halo); the tint
 * cross-fades over the preset's tab duration (SOLACE), or switches instantly.
 */
@Composable
fun PresetNavBar(tabs: List<NavTab>, selected: NavTab?, onSelect: (NavTab) -> Unit, modifier: Modifier = Modifier) {
    val p = LocalPresetUi.current
    Column(modifier.fillMaxWidth().background(p.colors.surface)) {
        Row(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .selectableGroup()
                .testTag("preset_nav"),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            tabs.forEach { tab -> NavItem(tab, tab == selected, p.navigation.label(tab)) { onSelect(tab) } }
        }
    }
}

@Composable
private fun RowScope.NavItem(tab: NavTab, active: Boolean, label: String, onClick: () -> Unit) {
    val p = LocalPresetUi.current
    val accent = p.navigation.activeTint == TabTint.ACCENT
    val iconTarget = when {
        !active -> p.colors.secondaryText
        accent -> p.colors.accent
        else -> p.colors.primaryText
    }
    // Labels are text: the accent tint uses its AA-safe text form.
    val labelTarget = when {
        !active -> p.colors.secondaryText
        accent -> p.colors.accentText
        else -> p.colors.primaryText
    }
    val ms = p.effects.tabCrossfadeMs
    val iconTint by animateColorAsState(iconTarget, tween(ms), label = "tabIcon")
    val labelTint by animateColorAsState(labelTarget, tween(ms), label = "tabLabel")
    Column(
        Modifier
            .weight(1f)
            .heightIn(min = p.spacing.touch)
            .presetClickable(role = Role.Tab) { onClick() }
            .semantics(mergeDescendants = true) {
                selected = active
                contentDescription = label
            }
            .padding(vertical = p.spacing.s)
            .testTag("tab_${tab.name}"),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Crossfade(active, animationSpec = tween(p.effects.iconMorphMs), label = "tabIcon") { filled ->
            Icon(navIcon(tab, p.id, filled), contentDescription = null, tint = iconTint, modifier = Modifier.size(p.sizes.icon))
        }
        Spacer(Modifier.height(p.spacing.xs))
        Text(label, style = p.type.caption, color = labelTint, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/**
 * Mini-player gestures shared by the presets: tap (not on the buttons) expands; long press opens
 * [onLongPress]; a sideways swipe skips when [onNext]/[onPrevious] are given; an upward swipe
 * expands when [swipeUpOpens]. TalkBack gets the same as custom actions.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MiniFrame(
    description: String,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
    onNext: (() -> Unit)? = null,
    onPrevious: (() -> Unit)? = null,
    swipeUpOpens: Boolean = false,
    onLongPress: (() -> Unit)? = null,
    content: @Composable RowScope.() -> Unit,
) {
    val p = LocalPresetUi.current
    val swipes = onNext != null || onPrevious != null || swipeUpOpens
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = p.sizes.miniPlayerHeight)
            .testTag("mini_player")
            .combinedClickable(onClickLabel = "Open now playing", onLongClickLabel = onLongPress?.let { "More options" }, onLongClick = onLongPress, onClick = onOpen)
            .then(
                if (!swipes) Modifier else Modifier.pointerInput(onNext, onPrevious, swipeUpOpens) {
                    val threshold = p.spacing.touch.toPx()
                    var dx = 0f
                    var dy = 0f
                    detectDragGestures(
                        onDragStart = { dx = 0f; dy = 0f },
                        onDragEnd = {
                            when {
                                swipeUpOpens && dy < -threshold && -dy > abs(dx) -> onOpen()
                                dx < -threshold && abs(dx) > abs(dy) -> onNext?.invoke()
                                dx > threshold && abs(dx) > abs(dy) -> onPrevious?.invoke()
                            }
                        },
                    ) { change, amount ->
                        change.consume()
                        dx += amount.x
                        dy += amount.y
                    }
                }
            )
            .semantics {
                contentDescription = description
                customActions = listOfNotNull(
                    onNext?.let { CustomAccessibilityAction("Next track") { it(); true } },
                    onPrevious?.let { CustomAccessibilityAction("Previous track") { it(); true } },
                )
            }
            .padding(horizontal = p.spacing.s),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}
