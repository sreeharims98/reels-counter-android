package com.reelscounter.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.Icon
import android.os.Build
import android.util.Log
import androidx.core.app.ServiceCompat
import com.reelscounter.TrackedApp
import com.reelscounter.data.AppSettings
import com.reelscounter.limits.LimitScope
import com.reelscounter.limits.Usage
import com.reelscounter.ui.MainActivity

/**
 * The always-on notification. The status-bar icon is the count itself, rendered as a bitmap,
 * so today's number is visible next to the clock/notch without pulling the shade down.
 */
class CounterNotification(private val service: Service) {
    private val nm = service.getSystemService(NotificationManager::class.java)
    private var inForeground = false
    private var lastKey: String? = null

    init {
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, "Reel counter", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Shows today's reel count"
                setShowBadge(false)
            },
        )
    }

    fun update(usage: Usage, settings: AppSettings) {
        if (!settings.showNotification) {
            stop()
            return
        }
        val key = "${usage.counts}|${usage.totalSeconds / 60}|${settings.limits}"
        if (key == lastKey && inForeground) return
        lastKey = key
        val n = build(usage, settings)
        if (!inForeground) {
            try {
                val type = if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0
                ServiceCompat.startForeground(service, ID, n, type)
            } catch (e: Exception) {
                Log.w(TAG, "startForeground failed, posting plain notification", e)
                nm.notify(ID, n)
            }
            inForeground = true
        } else {
            nm.notify(ID, n)
        }
    }

    fun stop() {
        if (!inForeground) return
        ServiceCompat.stopForeground(service, ServiceCompat.STOP_FOREGROUND_REMOVE)
        nm.cancel(ID)
        inForeground = false
        lastKey = null
    }

    private fun build(usage: Usage, s: AppSettings): Notification {
        val total = usage.totalCount
        val title = "Today: $total reels · ${formatMinutes(usage.totalSeconds)}"
        val text = TrackedApp.entries.joinToString("  ·  ") { "${it.shortLabel} ${usage.count(it)}" }
        val open = PendingIntent.getActivity(
            service, 0, Intent(service, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val b = Notification.Builder(service, CHANNEL)
            .setSmallIcon(Icon.createWithBitmap(countBitmap(total)))
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(open)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setCategory(Notification.CATEGORY_STATUS)

        // Progress toward the tighter of the enabled limits (combined scope only).
        if (s.scope == LimitScope.COMBINED) {
            val fractions = buildList {
                if (s.countLimitEnabled) add(total.toFloat() / s.countLimit to "$total/${s.countLimit} reels")
                if (s.timeLimitEnabled) add(usage.totalSeconds / 60f / s.timeLimitMinutes to "${usage.totalSeconds / 60}/${s.timeLimitMinutes} min")
            }
            fractions.maxByOrNull { it.first }?.let { (f, label) ->
                b.setProgress(100, (f * 100).toInt().coerceIn(0, 100), false)
                b.setSubText(if (f >= 1f) "Limit reached" else label)
            }
        }
        return b.build()
    }

    private fun countBitmap(count: Int): Bitmap {
        val size = 96
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val label = if (count > 999) "999+" else count.toString()
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
            textSize = when (label.length) { 1, 2 -> 72f; 3 -> 52f; else -> 38f }
        }
        val y = size / 2f - (paint.descent() + paint.ascent()) / 2f
        Canvas(bmp).drawText(label, size / 2f, y, paint)
        return bmp
    }

    companion object {
        private const val CHANNEL = "counter"
        private const val ID = 42
        private const val TAG = "ReelsCounter"
    }
}
