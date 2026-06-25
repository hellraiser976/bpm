package com.example.audiobpmeditor

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import kotlin.math.max
import kotlin.math.min

class CutTimelineView @JvmOverloads constructor(
    ctx: Context, attrs: AttributeSet? = null
) : View(ctx, attrs) {

    var durationMs: Long = 1L
        set(v){ field = max(1L, v); invalidate() }

    var markerMs: Long = 0L
        set(v){ field = v.coerceIn(0, durationMs); invalidate(); onMarkerChanged?.invoke(field) }

    val cuts = mutableListOf<Long>() // sorted
    var selectedSegmentIndex: Int = -1
        private set

    var onMarkerChanged: ((Long)->Unit)? = null
    var onSelectionChanged: ((startMs: Long, endMs: Long)->Unit)? = null

    private val bg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#E8E8E8") }
    private val segPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#B39DDB") }
    private val segSelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#FFEB3B") }
    private val cutPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#D32F2F"); strokeWidth = 4f }
    private val markerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#1A237E"); strokeWidth = 5f }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.DKGRAY; textSize = 28f }

    private val gesture = GestureDetector(context, object: GestureDetector.SimpleOnGestureListener(){
        override fun onDown(e: MotionEvent): Boolean = true
        override fun onSingleTapUp(e: MotionEvent): Boolean {
            markerMs = xToMs(e.x)
            requestFocus()
            return true
        }
        override fun onDoubleTap(e: MotionEvent): Boolean {
            val ms = xToMs(e.x)
            selectSegmentAt(ms)
            return true
        }
    })

    init {
        isFocusable = true
        isFocusableInTouchMode = true
        isClickable = true
    }

    fun addCutAtMarker(): Boolean {
        if (markerMs <= 0L || markerMs >= durationMs) return false
        if (cuts.any { kotlin.math.abs(it - markerMs) < 20 }) return false
        cuts.add(markerMs)
        cuts.sort()
        selectedSegmentIndex = -1
        invalidate()
        return true
    }

    fun getSegments(): List<Pair<Long, Long>> {
        val points = listOf(0L) + cuts + listOf(durationMs)
        return points.zipWithNext()
    }

    private fun selectSegmentAt(ms: Long) {
        val segs = getSegments()
        val idx = segs.indexOfFirst { ms >= it.first && ms < it.second }
        selectedSegmentIndex = idx
        if (idx >= 0) {
            onSelectionChanged?.invoke(segs[idx].first, segs[idx].second)
        }
        invalidate()
    }

    fun eraseSelected(): Pair<Long, Long>? {
        if (selectedSegmentIndex < 0) return null
        val seg = getSegments().getOrNull(selectedSegmentIndex) ?: return null
        selectedSegmentIndex = -1
        invalidate()
        return seg
    }

    fun clearAll() {
        cuts.clear()
        selectedSegmentIndex = -1
        markerMs = 0
        durationMs = 1
        invalidate()
    }

    fun stepMarker(deltaFrames: Int) {
        // 1 frame = 1/25 s = 40ms
        markerMs = (markerMs + deltaFrames * 40L).coerceIn(0, durationMs)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        return when (keyCode) {
            KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT,
            KeyEvent.KEYCODE_SYSTEM_NAVIGATION_LEFT, KeyEvent.KEYCODE_SYSTEM_NAVIGATION_RIGHT -> {
                stepMarker(if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) -1 else 1)
                true
            }
            else -> super.onKeyDown(keyCode, event)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        requestFocus()
        return gesture.onTouchEvent(event)
    }

    private fun msToX(ms: Long): Float {
        return (ms.toFloat() / durationMs) * width
    }
    private fun xToMs(x: Float): Long {
        return ((x / width.coerceAtLeast(1)) * durationMs).toLong().coerceIn(0, durationMs)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawRect(0f,0f,width.toFloat(),height.toFloat(), bg)
        // segments
        val segs = getSegments()
        segs.forEachIndexed { i, (s,e) ->
            val left = msToX(s)
            val right = msToX(e)
            canvas.drawRect(left, 0f, right, height.toFloat(), if (i == selectedSegmentIndex) segSelPaint else segPaint)
        }
        // cuts
        cuts.forEach { c ->
            val x = msToX(c)
            canvas.drawLine(x, 0f, x, height.toFloat(), cutPaint)
        }
        // marker
        val mx = msToX(markerMs)
        canvas.drawLine(mx, 0f, mx, height.toFloat(), markerPaint)
        // time labels
        canvas.drawText("0:00", 8f, height-8f, textPaint)
        val durStr = "${durationMs/1000}.${(durationMs%1000)/100}s"
        canvas.drawText(durStr, width - 120f, height-8f, textPaint)
    }
}
