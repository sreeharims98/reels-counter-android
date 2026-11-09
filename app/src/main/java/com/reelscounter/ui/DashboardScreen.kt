package com.reelscounter.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.reelscounter.ReelsApp
import com.reelscounter.TrackedApp
import com.reelscounter.data.AppSettings
import com.reelscounter.data.DayStats
import com.reelscounter.limits.LimitScope
import com.reelscounter.limits.Usage
import com.reelscounter.service.ReelsAccessibilityService
import com.reelscounter.service.formatMinutes
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

@Composable
fun DashboardScreen(app: ReelsApp, modifier: Modifier = Modifier) {
    val usage by app.stats.today.collectAsStateWithLifecycle(Usage())
    val settings by app.settings.settings.collectAsStateWithLifecycle(AppSettings())
    val historyFlow = remember { app.stats.history(7) }
    val history by historyFlow.collectAsStateWithLifecycle(emptyList())
    val running by ReelsAccessibilityService.running.collectAsStateWithLifecycle()
    val setup = rememberSetupState()

    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Today", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        if (!setup.essentialsDone || !setup.battery) SetupCard(setup)
        else if (!running) Text(
            "Counter service is enabled but not running. Toggle it off and on in Accessibility settings.",
            color = MaterialTheme.colorScheme.error,
        )

        TodayCard(usage, settings)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            TrackedApp.entries.forEach { AppCard(it, usage, settings, Modifier.weight(1f)) }
        }
        WeekCard(history)
    }
}

@Composable
private fun TodayCard(usage: Usage, s: AppSettings) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text("${usage.totalCount}", fontSize = 56.sp, fontWeight = FontWeight.Black, lineHeight = 56.sp)
                Spacer(Modifier.size(8.dp))
                Column(Modifier.padding(bottom = 8.dp)) {
                    Text("reels & shorts", style = MaterialTheme.typography.titleMedium)
                    Text("${formatMinutes(usage.totalSeconds)} watched", style = MaterialTheme.typography.bodyMedium)
                }
            }
            if (s.scope == LimitScope.COMBINED) {
                if (s.countLimitEnabled) LimitBar("Reel limit", usage.totalCount.toFloat(), s.countLimit.toFloat(), "${usage.totalCount} / ${s.countLimit}")
                if (s.timeLimitEnabled) LimitBar(
                    "Time limit", usage.totalSeconds / 60f, s.timeLimitMinutes.toFloat(),
                    "${usage.totalSeconds / 60} / ${s.timeLimitMinutes} min",
                )
            }
            if (!s.countLimitEnabled && !s.timeLimitEnabled) {
                Text("No limits set. Add one in Settings.", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun LimitBar(label: String, used: Float, limit: Float, text: String) {
    val f = if (limit <= 0f) 0f else (used / limit).coerceIn(0f, 1f)
    val reached = used >= limit
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row {
            Text(label, style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
            Text(
                if (reached) "$text · blocked" else text,
                style = MaterialTheme.typography.labelLarge,
                color = if (reached) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
            )
        }
        LinearProgressIndicator(
            progress = { f },
            modifier = Modifier.fillMaxWidth().height(8.dp),
            color = if (reached) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
            drawStopIndicator = {},
        )
    }
}

@Composable
private fun AppCard(app: TrackedApp, usage: Usage, s: AppSettings, modifier: Modifier) {
    Card(modifier) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(10.dp).background(app.color(), CircleShape))
                Spacer(Modifier.size(6.dp))
                Text("${app.label} ${app.feedName}", style = MaterialTheme.typography.labelLarge)
            }
            if (!s.isTracked(app)) {
                Text("Not tracked", style = MaterialTheme.typography.bodyMedium)
            } else {
                Text("${usage.count(app)}", fontSize = 32.sp, fontWeight = FontWeight.Bold)
                Text(formatMinutes(usage.seconds(app)), style = MaterialTheme.typography.bodyMedium)
                if (s.scope == LimitScope.PER_APP) {
                    if (s.countLimitEnabled) LimitBar("Reels", usage.count(app).toFloat(), s.countLimit.toFloat(), "${usage.count(app)}/${s.countLimit}")
                    if (s.timeLimitEnabled) LimitBar(
                        "Time", usage.seconds(app) / 60f, s.timeLimitMinutes.toFloat(),
                        "${usage.seconds(app) / 60}/${s.timeLimitMinutes}m",
                    )
                }
            }
        }
    }
}

@Composable
private fun WeekCard(history: List<DayStats>) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Last 7 days", style = MaterialTheme.typography.titleMedium)
            if (history.isEmpty()) return@Column
            val max = (history.maxOfOrNull { it.usage.totalCount } ?: 0).coerceAtLeast(1)
            val track = MaterialTheme.colorScheme.surfaceVariant
            Row(Modifier.fillMaxWidth().height(160.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                history.forEach { day ->
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("${day.usage.totalCount}", style = MaterialTheme.typography.labelSmall)
                        Canvas(Modifier.weight(1f).fillMaxWidth().padding(vertical = 4.dp)) {
                            drawRoundRect(track, cornerRadius = CornerRadius(8f))
                            var top = size.height
                            // Stacked: Instagram at the bottom, YouTube above.
                            TrackedApp.entries.forEach { a ->
                                val h = size.height * day.usage.count(a) / max
                                if (h > 0f) {
                                    top -= h
                                    drawRoundRect(a.color(), Offset(0f, top), Size(size.width, h), CornerRadius(8f))
                                }
                            }
                        }
                        Text(dayLabel(day.day), style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center)
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                TrackedApp.entries.forEach { a ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(10.dp).background(a.color(), CircleShape))
                        Spacer(Modifier.size(4.dp))
                        Text(a.label, style = MaterialTheme.typography.labelMedium)
                    }
                }
                Spacer(Modifier.weight(1f))
                Text(
                    "Avg ${history.sumOf { it.usage.totalCount } / history.size}/day",
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.Gray,
                )
            }
        }
    }
}

private fun dayLabel(iso: String): String = runCatching {
    LocalDate.parse(iso).dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault()).take(2)
}.getOrDefault("")
