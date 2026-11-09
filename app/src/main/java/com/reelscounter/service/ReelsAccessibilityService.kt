package com.reelscounter.service

import android.accessibilityservice.AccessibilityService
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.widget.Toast
import com.reelscounter.ReelsApp
import com.reelscounter.TrackedApp
import com.reelscounter.data.AppSettings
import com.reelscounter.data.OverlayMode
import com.reelscounter.detect.ReelPresence
import com.reelscounter.detect.ReelSwipeCounter
import com.reelscounter.detect.ReelsDetector
import com.reelscounter.detect.Transition
import com.reelscounter.limits.LimitPolicy
import com.reelscounter.limits.Usage
import com.reelscounter.limits.Verdict
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** Snapshot of what the service last saw inside a tracked app, shown in Settings → Diagnostics. */
data class Diagnostics(val app: TrackedApp, val atMillis: Long, val inReels: Boolean, val viewIds: List<String>)

class ReelsAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "ReelsCounter"
        private val _running = MutableStateFlow(false)
        val running: StateFlow<Boolean> = _running
        private val _diagnostics = MutableStateFlow<Diagnostics?>(null)
        val diagnostics: StateFlow<Diagnostics?> = _diagnostics

        /** Windows from these packages don't mean the user left the reels feed. */
        private fun isTransient(pkg: String) =
            pkg == "com.android.systemui" || pkg == "com.reelscounter" ||
                "inputmethod" in pkg || "keyboard" in pkg || "honeyboard" in pkg
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var app: ReelsApp
    private lateinit var notifier: CounterNotification
    private lateinit var overlay: OverlayController
    private lateinit var power: PowerManager

    private val presence = ReelPresence()
    private val counter = ReelSwipeCounter()
    private var settings = AppSettings()
    private var dbUsage = Usage()
    private val pendingSeconds = mutableMapOf<TrackedApp, Long>()

    private var lastDetectAt = 0L
    private var lastDiagnosticsAt = 0L
    private var lastToastAt = 0L
    private var blockJob: Job? = null

    private val usage: Usage get() = dbUsage.plusSeconds(pendingSeconds)

    private val settleCheck = Runnable {
        val cur = presence.current ?: return@Runnable
        if (counter.checkSettled(SystemClock.uptimeMillis()) && rawDetect() == cur) onNewReel(cur)
    }

    // ---- lifecycle ----

    override fun onServiceConnected() {
        super.onServiceConnected()
        app = application as ReelsApp
        power = getSystemService(PowerManager::class.java)
        notifier = CounterNotification(this)
        overlay = OverlayController(this) { x, y -> scope.launch { app.settings.setOverlayPosition(x, y) } }
        _running.value = true

        scope.launch { app.settings.settings.collect { settings = it; refreshUi() } }
        scope.launch { app.stats.today.collect { dbUsage = it; refreshUi() } }
        scope.launch { tickLoop() }
        Log.i(TAG, "service connected")
    }

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        flushSeconds()
        _running.value = false
        handler.removeCallbacksAndMessages(null)
        if (::overlay.isInitialized) overlay.hide()
        if (::notifier.isInitialized) notifier.stop()
        scope.cancel()
        super.onDestroy()
    }

    // ---- events ----

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (!::app.isInitialized) return
        val pkg = event.packageName?.toString() ?: return
        val tracked = TrackedApp.fromPackage(pkg)?.takeIf { settings.isTracked(it) }

        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                if (tracked == null) {
                    if (!isTransient(pkg)) handle(presence.forceExit())
                    return
                }
                detect(force = true)
                captureDiagnostics(tracked)
            }

            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> if (tracked != null) detect(force = false)

            AccessibilityEvent.TYPE_VIEW_SCROLLED -> {
                if (tracked == null) return
                if (presence.current != tracked) {
                    detect(force = false)
                    return
                }
                val dm = resources.displayMetrics
                if (!ReelsDetector.isReelPager(event.source, tracked, settings.extraIds(tracked), dm.widthPixels, dm.heightPixels)) return
                counter.onScroll(event.fromIndex, SystemClock.uptimeMillis())
                handler.removeCallbacks(settleCheck)
                handler.postDelayed(settleCheck, counter.settleDelayMs + 30)
            }
        }
    }

    // ---- detection ----

    /** Which tracked app's reels feed is on screen right now (no smoothing), or null. */
    private fun rawDetect(): TrackedApp? {
        val root = rootInActiveWindow ?: return null
        val a = TrackedApp.fromPackage(root.packageName) ?: return null
        if (!settings.isTracked(a)) return null
        return if (ReelsDetector.isInReels(root, a, settings.extraIds(a))) a else null
    }

    private fun detect(force: Boolean): TrackedApp? {
        val now = SystemClock.uptimeMillis()
        if (!force && now - lastDetectAt < 300) return presence.current
        lastDetectAt = now
        val raw = rawDetect()
        handle(presence.update(raw, now))
        return raw
    }

    private fun handle(t: Transition) {
        when (t) {
            Transition.None -> return
            is Transition.Entered -> {
                Log.d(TAG, "entered ${t.app}")
                counter.reset()
                onNewReel(t.app)
            }
            is Transition.Switched -> {
                flushSeconds()
                counter.reset()
                onNewReel(t.to)
            }
            is Transition.Exited -> {
                Log.d(TAG, "exited ${t.app}")
                flushSeconds()
                counter.reset()
                handler.removeCallbacks(settleCheck)
            }
        }
        refreshUi()
    }

    private fun captureDiagnostics(a: TrackedApp) {
        val now = SystemClock.uptimeMillis()
        if (now - lastDiagnosticsAt < 2000) return
        lastDiagnosticsAt = now
        val root = rootInActiveWindow ?: return
        if (TrackedApp.fromPackage(root.packageName) != a) return
        _diagnostics.value = Diagnostics(
            app = a,
            atMillis = System.currentTimeMillis(),
            inReels = ReelsDetector.isInReels(root, a, settings.extraIds(a)),
            viewIds = ReelsDetector.collectViewIds(root),
        )
    }

    // ---- counting, time & limits ----

    private fun onNewReel(a: TrackedApp) {
        when (val v = LimitPolicy.evaluate(a, usage, settings.limits)) {
            is Verdict.Blocked -> block(a, v)
            Verdict.Allowed -> scope.launch { app.stats.recordReel(a) }
        }
    }

    private suspend fun tickLoop() {
        var sinceFlush = 0
        while (scope.isActive) {
            delay(1000)
            if (presence.current == null) continue
            val raw = detect(force = true)
            if (raw == null || !power.isInteractive) continue
            pendingSeconds[raw] = (pendingSeconds[raw] ?: 0L) + 1
            if (++sinceFlush >= 10) {
                flushSeconds()
                sinceFlush = 0
            }
            (LimitPolicy.evaluate(raw, usage, settings.limits) as? Verdict.Blocked)?.let { block(raw, it) }
            refreshUi()
        }
    }

    private fun flushSeconds() {
        if (pendingSeconds.isEmpty() || !::app.isInitialized) return
        val batch = pendingSeconds.toMap()
        pendingSeconds.clear()
        // Keep the unflushed seconds visible until the DB write lands.
        dbUsage = dbUsage.plusSeconds(batch)
        scope.launch { batch.forEach { (a, s) -> app.stats.addWatchSeconds(a, s) } }
    }

    private fun block(a: TrackedApp, v: Verdict.Blocked) {
        if (blockJob?.isActive == true) return
        val now = SystemClock.uptimeMillis()
        if (now - lastToastAt > 3000) {
            lastToastAt = now
            Toast.makeText(this, blockMessage(a, v), Toast.LENGTH_LONG).show()
        }
        Log.i(TAG, "blocking $a: $v")
        blockJob = scope.launch {
            repeat(3) {
                performGlobalAction(GLOBAL_ACTION_BACK)
                delay(900)
                if (rawDetect() == null) return@launch
            }
            performGlobalAction(GLOBAL_ACTION_HOME)
        }
    }

    // ---- notification + overlay ----

    private fun refreshUi() {
        if (!::notifier.isInitialized) return
        val u = usage
        notifier.update(u, settings)

        val showOverlay = when (settings.overlayMode) {
            OverlayMode.OFF -> false
            OverlayMode.IN_REELS -> presence.current != null
            OverlayMode.ALWAYS -> true
        }
        if (showOverlay) {
            val cur = presence.current ?: TrackedApp.INSTAGRAM
            val reached = LimitPolicy.evaluate(cur, u, settings.limits) is Verdict.Blocked
            runCatching {
                overlay.show("▶ ${u.totalCount}", formatMinutes(u.totalSeconds), reached, settings.overlayX, settings.overlayY)
            }.onFailure { Log.w(TAG, "overlay failed", it) }
        } else if (overlay.isShowing) {
            overlay.hide()
        }
    }
}
