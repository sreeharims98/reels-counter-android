package com.reelscounter.detect

import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import com.reelscounter.TrackedApp

/**
 * Knows which view IDs mark the Reels / Shorts feed. Apps rename these over time; users can add
 * more IDs in Settings, and the Diagnostics panel shows the IDs currently on screen.
 */
object ReelsDetector {

    val defaultIds: Map<TrackedApp, List<String>> = mapOf(
        // Full-screen vertical pager used by the Reels tab and by reels opened from feed/profile.
        TrackedApp.INSTAGRAM to listOf("clips_viewer_view_pager"),
        TrackedApp.YOUTUBE to listOf("reel_player_page_container", "reel_progress_bar", "reel_recycler"),
    )

    private fun qualify(app: TrackedApp, id: String) = if (':' in id) id else "${app.packageName}:id/$id"

    fun idsFor(app: TrackedApp, extra: List<String>): List<String> =
        (defaultIds.getValue(app) + extra).map { qualify(app, it) }.distinct()

    fun isInReels(root: AccessibilityNodeInfo, app: TrackedApp, extra: List<String>): Boolean =
        idsFor(app, extra).any { id ->
            runCatching { root.findAccessibilityNodeInfosByViewId(id) }.getOrNull()
                ?.any { it.isVisibleToUser } == true
        }

    /**
     * Is this scroll event from the full-screen reels pager (not a comments sheet or a carousel)?
     * With no source node we can't tell, so we assume yes.
     */
    fun isReelPager(source: AccessibilityNodeInfo?, app: TrackedApp, extra: List<String>, screenW: Int, screenH: Int): Boolean {
        source ?: return true
        val id = source.viewIdResourceName
        if (id != null && id in idsFor(app, extra)) return true
        val r = Rect().also { source.getBoundsInScreen(it) }
        return r.height() >= screenH * 0.75 && r.width() >= screenW * 0.8
    }

    /** All distinct view IDs in the window (package prefix stripped), for the Diagnostics panel. */
    fun collectViewIds(root: AccessibilityNodeInfo, limit: Int = 400): List<String> {
        val out = LinkedHashSet<String>()
        val queue = ArrayDeque<AccessibilityNodeInfo>().apply { add(root) }
        var visited = 0
        while (queue.isNotEmpty() && out.size < limit && visited < 3000) {
            val n = queue.removeFirst()
            visited++
            n.viewIdResourceName?.let { out += it.substringAfter(":id/") }
            for (i in 0 until n.childCount) n.getChild(i)?.let(queue::addLast)
        }
        return out.toList()
    }
}
