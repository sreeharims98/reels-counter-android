package com.reelscounter.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.reelscounter.ReelsApp
import com.reelscounter.TrackedApp
import com.reelscounter.data.AppSettings
import com.reelscounter.data.OverlayMode
import com.reelscounter.detect.ReelsDetector
import com.reelscounter.limits.LimitScope
import com.reelscounter.service.ReelsAccessibilityService
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

@Composable
fun SettingsScreen(app: ReelsApp, modifier: Modifier = Modifier) {
    val s by app.settings.settings.collectAsStateWithLifecycle(AppSettings())
    val repo = app.settings
    val scope = rememberCoroutineScope()
    fun save(block: suspend () -> Unit) { scope.launch { block() } }
    var confirmReset by remember { mutableStateOf(false) }

    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Settings", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)

        Section("Track") {
            TrackedApp.entries.forEach { a ->
                SwitchRow("${a.label} ${a.feedName}", s.isTracked(a)) { on -> save { repo.setTracked(a, on) } }
            }
        }

        Section("Daily limits") {
            SwitchRow("Limit number of reels", s.countLimitEnabled) { save { repo.setCountLimitEnabled(it) } }
            if (s.countLimitEnabled) NumberField("Reels per day", s.countLimit) { save { repo.setCountLimit(it) } }
            HorizontalDivider()
            SwitchRow("Limit watch time", s.timeLimitEnabled) { save { repo.setTimeLimitEnabled(it) } }
            if (s.timeLimitEnabled) NumberField("Minutes per day", s.timeLimitMinutes) { save { repo.setTimeLimitMinutes(it) } }
            HorizontalDivider()
            Text("Limits apply to", style = MaterialTheme.typography.bodyLarge)
            Choice(
                options = listOf(LimitScope.COMBINED to "Both apps combined", LimitScope.PER_APP to "Each app separately"),
                selected = s.scope,
            ) { save { repo.setScope(it) } }
            Text(
                "When a limit is reached, opening Reels or Shorts shows a message and goes back automatically. " +
                    "The rest of Instagram and YouTube keeps working. Limits reset at midnight.",
                style = MaterialTheme.typography.bodySmall,
            )
        }

        Section("Always-visible counter") {
            SwitchRow("Notification + status bar count", s.showNotification) { save { repo.setShowNotification(it) } }
            Text("Floating pill at the top of the screen", style = MaterialTheme.typography.bodyLarge)
            Choice(
                options = listOf(OverlayMode.OFF to "Off", OverlayMode.IN_REELS to "While watching", OverlayMode.ALWAYS to "Always"),
                selected = s.overlayMode,
            ) { save { repo.setOverlayMode(it) } }
            Text("Drag the pill to move it.", style = MaterialTheme.typography.bodySmall)
            TextButton(onClick = { save { repo.setOverlayPosition(0, 0) } }) { Text("Reset pill position") }
        }

        Section("Data") {
            OutlinedButton(onClick = { confirmReset = true }) { Text("Reset today's count") }
        }

        DiagnosticsSection(s) { a, ids -> save { repo.setExtraIds(a, ids) } }
    }

    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text("Reset today?") },
            text = { Text("This deletes today's reel count and watch time. It can't be undone.") },
            confirmButton = {
                TextButton(onClick = { confirmReset = false; scope.launch { app.stats.resetToday() } }) { Text("Reset") }
            },
            dismissButton = { TextButton(onClick = { confirmReset = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun DiagnosticsSection(s: AppSettings, onExtraIds: (TrackedApp, String) -> Unit) {
    val diag by ReelsAccessibilityService.diagnostics.collectAsStateWithLifecycle()
    val clipboard = LocalClipboardManager.current
    Section("Detection (advanced)") {
        Text(
            "If reels aren't being counted after an Instagram/YouTube update, open the Reels/Shorts feed, " +
                "come back here, and look for an ID that only shows up there. Add it below.",
            style = MaterialTheme.typography.bodySmall,
        )
        TrackedApp.entries.forEach { a ->
            Text(
                "${a.label} built-in: ${ReelsDetector.defaultIds.getValue(a).joinToString()}",
                style = MaterialTheme.typography.labelSmall,
            )
            val raw = if (a == TrackedApp.INSTAGRAM) s.extraInstagramIds else s.extraYoutubeIds
            DebouncedTextField("Extra ${a.label} IDs (comma separated)", raw) { onExtraIds(a, it) }
        }
        val d = diag
        if (d == null) {
            Text("Nothing captured yet. Open Instagram or YouTube with the service on.", style = MaterialTheme.typography.bodySmall)
        } else {
            Text(
                "Last seen: ${d.app.label} at ${DateFormat.getTimeInstance().format(Date(d.atMillis))} · " +
                    "in ${d.app.feedName}: ${if (d.inReels) "yes" else "no"} · ${d.viewIds.size} IDs",
                style = MaterialTheme.typography.labelLarge,
            )
            Box(
                Modifier.fillMaxWidth().heightIn(max = 220.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
                    .verticalScroll(rememberScrollState()).horizontalScroll(rememberScrollState()).padding(8.dp),
            ) {
                Text(d.viewIds.joinToString("\n"), fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
            }
            TextButton(onClick = { clipboard.setText(AnnotatedString(d.viewIds.joinToString("\n"))) }) { Text("Copy IDs") }
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            content()
        }
    }
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun NumberField(label: String, value: Int, onValid: (Int) -> Unit) {
    var text by remember { mutableStateOf(value.toString()) }
    LaunchedEffect(value) { if (text.toIntOrNull() != value) text = value.toString() }
    OutlinedTextField(
        value = text,
        onValueChange = { t ->
            val digits = t.filter(Char::isDigit).take(4)
            text = digits
            digits.toIntOrNull()?.takeIf { it > 0 }?.let(onValid)
        },
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.width(200.dp),
    )
}

@Composable
private fun DebouncedTextField(label: String, value: String, onChange: (String) -> Unit) {
    var text by remember(value) { mutableStateOf(value) }
    LaunchedEffect(text) {
        kotlinx.coroutines.delay(600)
        if (text != value) onChange(text)
    }
    OutlinedTextField(
        value = text,
        onValueChange = { text = it },
        label = { Text(label) },
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun <T> Choice(options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit) {
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        options.forEachIndexed { i, (v, label) ->
            SegmentedButton(
                selected = v == selected,
                onClick = { onSelect(v) },
                shape = SegmentedButtonDefaults.itemShape(i, options.size),
            ) { Text(label, maxLines = 1) }
        }
    }
}
