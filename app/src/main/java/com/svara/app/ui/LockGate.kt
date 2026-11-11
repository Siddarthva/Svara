package com.svara.app.ui

import android.content.Context
import android.content.ContextWrapper
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.Button
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material3.Text
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.svara.app.ui.components.EmptyState

/**
 * Optional app lock for library and history areas. Playback controls stay usable; the lock only
 * hides what's in the library. Unlocks once per foreground session.
 */
@Composable
fun LockGate(locked: Boolean, onUnlocked: () -> Unit, content: @Composable () -> Unit) {
    if (!locked) {
        content()
        return
    }
    val context = LocalContext.current
    val prompt = { authenticate(context, onUnlocked) }
    LaunchedEffect(Unit) { prompt() }
    EmptyState(
        icon = Icons.Rounded.Lock,
        title = "Library locked",
        message = "Unlock with your fingerprint, face or screen lock to see your library and listening history.",
        modifier = Modifier.fillMaxSize(),
        action = { Button(onClick = prompt) { Text("Unlock") } },
    )
}

private fun authenticate(context: Context, onUnlocked: () -> Unit) {
    val activity = context.findFragmentActivity() ?: return
    val prompt = BiometricPrompt(
        activity,
        ContextCompat.getMainExecutor(activity),
        object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) = onUnlocked()
        },
    )
    prompt.authenticate(
        BiometricPrompt.PromptInfo.Builder()
            .setTitle("Unlock SVARĀ")
            .setSubtitle("Your library and listening history")
            .setAllowedAuthenticators(BIOMETRIC_WEAK or DEVICE_CREDENTIAL)
            .build()
    )
}

private tailrec fun Context.findFragmentActivity(): FragmentActivity? = when (this) {
    is FragmentActivity -> this
    is ContextWrapper -> baseContext.findFragmentActivity()
    else -> null
}
