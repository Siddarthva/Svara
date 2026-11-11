package com.svara.app.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.svara.app.appearance.AnimationExtra
import com.svara.app.appearance.CustomField
import com.svara.app.appearance.Customization
import com.svara.app.appearance.CustomizationCodec
import com.svara.app.ui.theme.LocalAppearance
import com.svara.app.ui.theme.Spacing

/** Appearance › Motion › Animation extras: a master switch and, under Advanced, one switch per extra. */
@Composable
internal fun AnimationExtrasSection(custom: Customization, set: (CustomField, String) -> Unit) {
    SwitchRow("Animation extras", "Small signature touches for this theme", custom.animationExtras) { set(CustomField.EXTRAS, it.toString()) }
    if (!custom.animationExtras) return
    var open by rememberSaveable { mutableStateOf(false) }
    ListItem(
        headlineContent = { Text("Advanced") },
        supportingContent = { Text(if (open) "Showing every extra" else "Choose extras one by one") },
        trailingContent = { Icon(if (open) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, null) },
        modifier = Modifier.heightIn(min = Spacing.touch).clickable { open = !open },
    )
    if (!open) return
    val preset = LocalAppearance.current.preset
    AnimationExtra.entries.filter { preset in it.themes }.forEach { extra ->
        if (!extra.built) {
            ListItem(headlineContent = { Text(extra.label) }, supportingContent = { Text("Coming in a later update") })
        } else {
            val on = extra !in custom.extrasOff
            SwitchRow(extra.label, if (on) "On" else "Off", on) { enable ->
                val next = if (enable) custom.extrasOff - extra else custom.extrasOff + extra
                set(CustomField.EXTRAS_OFF, CustomizationCodec.encodeList(next.sortedBy { it.ordinal }))
            }
        }
    }
}
