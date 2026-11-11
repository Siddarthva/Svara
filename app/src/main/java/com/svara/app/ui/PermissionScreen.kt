package com.svara.app.ui

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

val audioPermission: String =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) Manifest.permission.READ_MEDIA_AUDIO
    else Manifest.permission.READ_EXTERNAL_STORAGE

fun Context.hasAudioPermission(): Boolean =
    ContextCompat.checkSelfPermission(this, audioPermission) == PackageManager.PERMISSION_GRANTED

/** Runtime notification permission exists from Android 13. */
val notificationPermission: String? =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) Manifest.permission.POST_NOTIFICATIONS else null

fun Context.notificationsAllowed(): Boolean = NotificationManagerCompat.from(this).areNotificationsEnabled()

/** Ask only when the platform needs it and it hasn't been granted yet. */
fun Context.shouldAskNotifications(): Boolean = notificationPermission != null &&
    ContextCompat.checkSelfPermission(this, notificationPermission) != PackageManager.PERMISSION_GRANTED

/** Explains why SVARĀ needs audio access before asking for it. */
@Composable
fun PermissionScreen(onResult: (Boolean) -> Unit) {
    val context = LocalContext.current
    var deniedOnce by rememberSaveable { mutableStateOf(false) }
    var permanentlyDenied by rememberSaveable { mutableStateOf(false) }
    // Notifications are optional: whatever the answer, continue into the app.
    val notificationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { onResult(true) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted && notificationPermission != null && context.shouldAskNotifications()) {
            notificationLauncher.launch(notificationPermission)
            return@rememberLauncherForActivityResult
        }
        if (!granted) {
            deniedOnce = true
            val activity = context.findActivity()
            permanentlyDenied = activity != null && !ActivityCompat.shouldShowRequestPermissionRationale(activity, audioPermission)
        }
        onResult(granted)
    }

    Surface(Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 32.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            Text("SVARĀ", style = MaterialTheme.typography.displaySmall, color = MaterialTheme.colorScheme.primary)
            Text("Your music. Your world.", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(32.dp))
            Text(
                "To play your music, SVARĀ needs permission to read the audio files on this device.",
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.semantics { heading() },
            )
            Spacer(Modifier.height(24.dp))
            Point(Icons.Rounded.LibraryMusic, "Reads audio only", "Titles, artists, albums and artwork come from your own files.")
            Point(Icons.Rounded.CloudOff, "Nothing leaves your phone", "No account, no cloud library, no uploads. Works fully offline.")
            Point(Icons.Rounded.Lock, "No copies", "SVARĀ never duplicates your songs. It keeps only playlists, favorites and history.")
            Point(Icons.Rounded.Folder, "You stay in control", "Choose which folders appear in Settings → Music folders.")
            Spacer(Modifier.height(32.dp))
            if (permanentlyDenied) {
                Text(
                    "Access was turned off. You can allow “Music and audio” for SVARĀ in system settings.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
                Spacer(Modifier.height(12.dp))
                Button(onClick = { context.openAppSettings() }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                    Text("Open settings")
                }
            } else {
                Button(onClick = { launcher.launch(audioPermission) }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                    Text(if (deniedOnce) "Try again" else "Allow access to music")
                }
            }
            if (deniedOnce && !permanentlyDenied) {
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = { context.openAppSettings() }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                    Text("Open settings")
                }
            }
        }
    }
}

@Composable
private fun Point(icon: ImageVector, title: String, body: String) {
    Row(Modifier.padding(vertical = 8.dp), verticalAlignment = Alignment.Top) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(16.dp))
        Column {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun Context.openAppSettings() {
    startActivity(
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    )
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
