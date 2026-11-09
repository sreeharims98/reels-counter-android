package com.reelscounter.service

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import kotlin.math.abs

/**
 * A black "island" pill at the top of the screen, around the camera cutout: count on the left,
 * watch time on the right, a gap in the middle for a centered punch-hole camera. Draggable.
 * Uses TYPE_ACCESSIBILITY_OVERLAY, so it must be created from the AccessibilityService context.
 */
class OverlayController(
    private val ctx: Context,
    private val onMoved: (x: Int, y: Int) -> Unit,
) {
    private val wm = ctx.getSystemService(WindowManager::class.java)
    private val dp = ctx.resources.displayMetrics.density
    private var root: LinearLayout? = null
    private var left: TextView? = null
    private var right: TextView? = null
    private var params: WindowManager.LayoutParams? = null

    val isShowing get() = root != null

    fun show(countText: String, timeText: String, limitReached: Boolean, x: Int, y: Int) {
        if (root == null) create(x, y)
        left?.text = countText
        right?.text = timeText
        left?.setTextColor(if (limitReached) Color.rgb(255, 99, 99) else Color.WHITE)
    }

    fun hide() {
        root?.let { runCatching { wm.removeView(it) } }
        root = null; left = null; right = null; params = null
    }

    private fun label() = TextView(ctx).apply {
        setTextColor(Color.WHITE)
        textSize = 13f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun create(x: Int, y: Int) {
        val l = label()
        val r = label()
        val gap = View(ctx)
        val pill = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding((12 * dp).toInt(), (5 * dp).toInt(), (12 * dp).toInt(), (5 * dp).toInt())
            background = GradientDrawable().apply {
                cornerRadius = 50 * dp
                setColor(Color.argb(235, 0, 0, 0))
            }
            addView(l)
            addView(gap, LinearLayout.LayoutParams((34 * dp).toInt(), 1))
            addView(r)
        }
        val p = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            this.x = x
            this.y = y
            if (Build.VERSION.SDK_INT >= 28) {
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }
        pill.setOnTouchListener(DragListener(p, pill))
        wm.addView(pill, p)
        root = pill; left = l; right = r; params = p
    }

    private inner class DragListener(private val p: WindowManager.LayoutParams, private val v: View) : View.OnTouchListener {
        private var downX = 0f
        private var downY = 0f
        private var startX = 0
        private var startY = 0
        private var moved = false

        override fun onTouch(view: View, e: MotionEvent): Boolean {
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = e.rawX; downY = e.rawY; startX = p.x; startY = p.y; moved = false
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = e.rawX - downX
                    val dy = e.rawY - downY
                    if (abs(dx) > 8 * dp || abs(dy) > 8 * dp) moved = true
                    if (moved) {
                        p.x = startX + dx.toInt()
                        p.y = (startY + dy.toInt()).coerceAtLeast(0)
                        runCatching { wm.updateViewLayout(v, p) }
                    }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> if (moved) onMoved(p.x, p.y)
            }
            return true
        }
    }
}
