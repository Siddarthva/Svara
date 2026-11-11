package com.svara.app.widget

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.datastore.preferences.core.Preferences
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.getAppWidgetState
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.state.PreferencesGlanceStateDefinition
import com.svara.app.ui.theme.Spacing
import kotlinx.coroutines.launch

/**
 * Configuration for one widget: artwork, background (transparent, translucent, solid), accent
 * source (preset, current app colour, artwork), compact or expanded, and text and control
 * visibility. Opens when the widget is added (optional) and again from the launcher's
 * "Reconfigure" where supported. Saving redraws only this widget.
 */
class WidgetConfigActivity : ComponentActivity() {

    private val widgetId by lazy { intent?.extras?.getInt(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID) ?: AppWidgetManager.INVALID_APPWIDGET_ID }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Backing out leaves the widget with its defaults (configuration is optional).
        setResult(Activity.RESULT_CANCELED, result())
        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                Surface(Modifier.fillMaxSize()) { ConfigScreen() }
            }
        }
    }

    private fun result() = Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)

    @Composable
    private fun ConfigScreen() {
        val scope = rememberCoroutineScope()
        var loaded by remember { mutableStateOf<WidgetSettings?>(null) }
        androidx.compose.runtime.LaunchedEffect(Unit) {
            val glanceId = GlanceAppWidgetManager(this@WidgetConfigActivity).getGlanceIdBy(widgetId)
            val prefs = getAppWidgetState<Preferences>(this@WidgetConfigActivity, PreferencesGlanceStateDefinition, glanceId)
            loaded = WidgetSettings.from(prefs)
        }
        val s = loaded ?: return
        var settings by remember { mutableStateOf(s) }
        Column(Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(Spacing.l)) {
            Text("Widget", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.semantics { heading() })
            Text(
                "The widget follows SVARĀ's theme. It updates when the song, play state or appearance changes; progress shows where the song was at that moment.",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Toggle("Show artwork", settings.showArtwork) { settings = settings.copy(showArtwork = it) }
            Toggle("Show song and artist", settings.showText) { settings = settings.copy(showText = it) }
            Toggle("Show controls", settings.showControls) { settings = settings.copy(showControls = it) }
            Toggle("Compact", settings.compact) { settings = settings.copy(compact = it) }
            Label("Background")
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                WidgetSettings.Background.entries.forEach { b -> FilterChip(selected = settings.background == b, onClick = { settings = settings.copy(background = b) }, label = { Text(b.label) }) }
            }
            Text("Transparency depends on your launcher; some draw their own background behind widgets.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Label("Colour")
            Column {
                WidgetSettings.AccentSource.entries.forEach { a -> FilterChip(selected = settings.accent == a, onClick = { settings = settings.copy(accent = a) }, label = { Text(a.label) }) }
            }
            Row(Modifier.fillMaxWidth().padding(top = Spacing.l), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = { finish() }, modifier = Modifier.heightIn(min = Spacing.touch)) { Text("Cancel") }
                Button(
                    onClick = {
                        scope.launch {
                            val manager = GlanceAppWidgetManager(this@WidgetConfigActivity)
                            val glanceId = manager.getGlanceIdBy(widgetId)
                            updateAppWidgetState(this@WidgetConfigActivity, glanceId) { p ->
                                p[WidgetSettings.ART] = settings.showArtwork
                                p[WidgetSettings.BACKGROUND] = settings.background.name
                                p[WidgetSettings.ACCENT] = settings.accent.name
                                p[WidgetSettings.COMPACT] = settings.compact
                                p[WidgetSettings.TEXT] = settings.showText
                                p[WidgetSettings.CONTROLS] = settings.showControls
                            }
                            listOf(SmallWidget(), MediumWidget(), LargeWidget()).forEach { w -> runCatching { w.update(this@WidgetConfigActivity, glanceId) } }
                            setResult(Activity.RESULT_OK, result())
                            finish()
                        }
                    },
                    modifier = Modifier.heightIn(min = Spacing.touch),
                ) { Text("Save") }
            }
        }
    }

    @Composable
    private fun Toggle(label: String, value: Boolean, onChange: (Boolean) -> Unit) {
        Row(Modifier.fillMaxWidth().heightIn(min = Spacing.touch), verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Switch(checked = value, onCheckedChange = onChange)
        }
    }

    @Composable
    private fun Label(text: String) {
        Text(text, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = Spacing.l, bottom = Spacing.s))
    }
}
