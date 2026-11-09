package com.reelscounter.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect

data class SetupState(val accessibility: Boolean, val notifications: Boolean, val battery: Boolean) {
    val essentialsDone get() = accessibility && notifications
}

/** Re-reads permission state every time the screen resumes (e.g. returning from Settings). */
@Composable
fun rememberSetupState(): SetupState {
    val ctx = LocalContext.current
    var tick by remember { mutableIntStateOf(0) }
    LifecycleResumeEffect(Unit) {
        tick++
        onPauseOrDispose { }
    }
    return remember(tick) {
        SetupState(
            Permissions.accessibilityEnabled(ctx),
            Permissions.notificationsGranted(ctx),
            Permissions.batteryUnrestricted(ctx),
        )
    }
}

@Composable
fun SetupCard(state: SetupState, onChanged: () -> Unit = {}) {
    val ctx = LocalContext.current
    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { onChanged() }

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Finish setup", style = MaterialTheme.typography.titleMedium)

            SetupStep(
                done = state.notifications,
                title = "Allow notifications",
                body = "Needed for the always-visible counter.",
                action = "Allow",
            ) {
                if (Build.VERSION.SDK_INT >= 33) notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }

            SetupStep(
                done = state.accessibility,
                title = "Turn on Reels Counter in Accessibility",
                body = "Settings → Accessibility → Installed apps → Reels Counter. If the switch is greyed out " +
                    "(Android 13+, app installed from an APK), open App info → ⋮ menu → \"Allow restricted settings\" first.",
                action = "Open",
                secondary = "App info" to { Permissions.openAppInfo(ctx) },
            ) { Permissions.openAccessibilitySettings(ctx) }

            SetupStep(
                done = state.battery,
                title = "Optional: no battery restrictions",
                body = "Stops some phones (Xiaomi, Samsung, Oppo…) from killing the counter in the background.",
                action = "Allow",
            ) { Permissions.requestBatteryUnrestricted(ctx) }
        }
    }
}

@Composable
private fun SetupStep(
    done: Boolean,
    title: String,
    body: String,
    action: String,
    secondary: Pair<String, () -> Unit>? = null,
    onClick: () -> Unit,
) {
    Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Icon(
            if (done) Icons.Filled.CheckCircle else Icons.Filled.Warning,
            contentDescription = null,
            tint = if (done) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
            modifier = Modifier.padding(top = 2.dp),
        )
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (!done) {
                Text(body, style = MaterialTheme.typography.bodySmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton(onClick = onClick) { Text(action) }
                    secondary?.let { (label, fn) -> TextButton(onClick = fn) { Text(label) } }
                }
            }
        }
    }
}
