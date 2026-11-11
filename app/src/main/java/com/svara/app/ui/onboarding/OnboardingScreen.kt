package com.svara.app.ui.onboarding

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.CreateNewFolder
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.svara.app.domain.model.FolderRuleType
import com.svara.app.settings.SettingsViewModel
import com.svara.app.ui.audioPermission
import com.svara.app.ui.notificationPermission
import com.svara.app.ui.shouldAskNotifications
import androidx.compose.ui.platform.LocalContext
import com.svara.app.ui.theme.LocalReducedMotion
import kotlinx.coroutines.launch

private const val PAGES = 3

/** Three short pages: what SVARĀ is (and that nothing leaves the phone), music access, and folders. */
@Composable
fun OnboardingScreen(
    hasPermission: Boolean,
    onPermissionResult: (Boolean) -> Unit,
    onDone: () -> Unit,
    settingsViewModel: SettingsViewModel = hiltViewModel(),
) {
    val pager = rememberPagerState { PAGES }
    val scope = rememberCoroutineScope()
    val reducedMotion = LocalReducedMotion.current
    var pickedFolder by rememberSaveable { mutableStateOf<String?>(null) }
    var denied by rememberSaveable { mutableStateOf(false) }

    val context = LocalContext.current
    // Optional and asked right after audio access; playback works either way.
    val notificationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        scope.launch { pager.animateScrollToPage(2) }
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        denied = !granted
        onPermissionResult(granted)
        if (granted && notificationPermission != null && context.shouldAskNotifications()) {
            notificationLauncher.launch(notificationPermission)
        } else if (granted) {
            scope.launch { pager.animateScrollToPage(2) }
        }
    }
    val folderLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            settingsViewModel.addFolder(uri.toString(), FolderRuleType.INCLUDE)
            pickedFolder = uri.lastPathSegment?.substringAfter(':')?.ifBlank { "Selected folder" }
        }
    }

    fun goTo(page: Int) = scope.launch { if (reducedMotion) pager.scrollToPage(page) else pager.animateScrollToPage(page) }

    Surface(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().safeDrawingPadding()) {
            HorizontalPager(state = pager, modifier = Modifier.weight(1f), userScrollEnabled = true) { page ->
                when (page) {
                    0 -> Page(
                        Icons.Rounded.LibraryMusic, "SVARĀ", "Your music. Your world.",
                        "SVARĀ plays the music already on this phone, and organizes it into albums, artists, playlists and more. No account, no streaming, no copies.",
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.CloudOff, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Text(
                                "Nothing leaves your phone. SVARĀ has no internet access and no analytics; playlists, favorites and history stay on this device.",
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(start = 12.dp),
                            )
                        }
                    }
                    1 -> Page(
                        Icons.Rounded.CheckCircle, "Music access",
                        if (hasPermission) "Access granted" else "One permission",
                        "SVARĀ needs permission to read audio files. It reads titles, artists, artwork and lyrics from your files. It never changes or deletes them.",
                    ) {
                        if (!hasPermission) {
                            Button(onClick = { permissionLauncher.launch(audioPermission) }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                                Text(if (denied) "Try again" else "Allow access to music")
                            }
                            if (denied) {
                                Text(
                                    "Without access SVARĀ can't show your music. If the prompt doesn't appear, allow “Music and audio” in system settings.",
                                    color = MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                            }
                        }
                    }
                    else -> Page(
                        Icons.Rounded.CreateNewFolder, "Choose folders", "Optional",
                        "By default SVARĀ shows music from every folder and hides ringtones, notification sounds, alarms and recordings. " +
                            "You can pick a music folder now, or change this any time in Settings → Music folders.",
                    ) {
                        OutlinedButton(onClick = { folderLauncher.launch(null) }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                            Text(pickedFolder?.let { "Using “$it” — choose another" } ?: "Choose a music folder")
                        }
                    }
                }
            }
            Row(
                Modifier.fillMaxWidth().padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onDone, modifier = Modifier.heightIn(min = 48.dp)) { Text("Skip") }
                Spacer(Modifier.weight(1f))
                PageDots(pager.currentPage)
                Spacer(Modifier.weight(1f))
                val last = pager.currentPage == PAGES - 1
                Button(
                    onClick = { if (last) onDone() else goTo(pager.currentPage + 1) },
                    modifier = Modifier.heightIn(min = 48.dp),
                ) { Text(if (last) "Start listening" else "Next") }
            }
        }
    }
}

@Composable
private fun Page(icon: ImageVector, title: String, subtitle: String, body: String, extra: (@Composable () -> Unit)? = null) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 28.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(64.dp))
        Spacer(Modifier.height(24.dp))
        Text(title, style = MaterialTheme.typography.headlineLarge, modifier = Modifier.semantics { heading() })
        Text(subtitle, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(16.dp))
        Text(body, style = MaterialTheme.typography.bodyLarge)
        if (extra != null) {
            Spacer(Modifier.height(24.dp))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { extra() }
        }
    }
}

@Composable
private fun PageDots(current: Int) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.semantics(mergeDescendants = true) { contentDescription = "Page ${current + 1} of $PAGES" },
    ) {
        repeat(PAGES) { i ->
            Box(
                Modifier.size(if (i == current) 10.dp else 8.dp).clip(CircleShape)
                    .background(if (i == current) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant)
            )
        }
    }
}
