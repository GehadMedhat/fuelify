package com.example.fuelify.ui

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.ViewConfiguration
import android.widget.FrameLayout
import android.widget.Scroller
import kotlin.math.abs
import kotlin.math.roundToInt

class DrumRollPicker @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0
) : FrameLayout(context, attrs, defStyle) {

    // ── Public config ─────────────────────────────────────────────────────────
    var unit: String = ""; set(v) { field = v; invalidate() }
    var minValue: Int = 0
    var maxValue: Int = 100
        set(v) { field = v; clampScroll(); invalidate() }

    private var _value: Int = 0
    var value: Int
        get() = _value
        set(v) {
            _value = v.coerceIn(minValue, maxValue)
            // scrollOffset: 0 means minValue is centred; each step = one itemHeight
            if (itemHeight > 0f) {
                scrollOffset = (_value - minValue) * itemHeight
            } else {
                pendingScrollNeeded = true   // defer until onSizeChanged
            }
            invalidate()
        }
    var onValueChanged: ((Int) -> Unit)? = null

    // ── Internal scroll state ─────────────────────────────────────────────────
    private var scrollOffset = 0f          // pixels; 0 = minValue centred
    private var pendingScrollNeeded = false
    private var lastY = 0f
    private var velocityTracker: VelocityTracker? = null
    private val scroller = Scroller(context)
    private val minFling = ViewConfiguration.get(context).scaledMinimumFlingVelocity

    // ── Derived layout ────────────────────────────────────────────────────────
    private val itemHeight get() = if (height == 0) 0f else height / VISIBLE_ITEMS.toFloat()
    private val centerY   get() = height / 2f

    // ── Paints ────────────────────────────────────────────────────────────────
    private val paintBig = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK; typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.RIGHT
    }
    private val paintSmall = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#CCCCCC"); typeface = Typeface.DEFAULT; textAlign = Paint.Align.CENTER
    }
    private val paintUnit = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#888888"); typeface = Typeface.DEFAULT; textAlign = Paint.Align.LEFT
    }
    private val paintBg = Paint().apply {
        color = Color.parseColor("#EDFFC5"); style = Paint.Style.FILL
    }
    private val paintLine = Paint().apply {
        color = Color.parseColor("#C8EE90"); strokeWidth = 2f
    }

    init { setWillNotDraw(false); isClickable = true }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (pendingScrollNeeded && itemHeight > 0f) {
            scrollOffset = (_value - minValue) * itemHeight
            pendingScrollNeeded = false
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val ih = itemHeight
        if (ih == 0f) return

        // Green highlight band exactly at centre
        val top = centerY - ih / 2f
        val bot = centerY + ih / 2f
        canvas.drawRect(0f, top, width.toFloat(), bot, paintBg)
        canvas.drawLine(0f, top, width.toFloat(), top, paintLine)
        canvas.drawLine(0f, bot, width.toFloat(), bot, paintLine)

        // Text sizes
        paintBig.textSize   = ih * 0.36f
        paintSmall.textSize = ih * 0.26f
        paintUnit.textSize  = ih * 0.28f

        // Which fractional index is at the centre right now
        val centreIdx = scrollOffset / ih   // e.g. 3.0 means item-3 is centred

        val half = VISIBLE_ITEMS / 2 + 1
        val lo = (centreIdx - half).toInt().coerceAtLeast(0)
        val hi = (centreIdx + half).toInt().coerceAtMost(maxValue - minValue)

        for (i in lo..hi) {
            val itemVal = minValue + i
            // Y-centre of this item on screen
            val itemY = centerY + (i - centreIdx) * ih

            val dist = abs(itemY - centerY)
            val isCenter = dist < ih * 0.5f

            if (isCenter) {
                // Draw big number right-of-centre, unit to the left of centre — actually
                // we want: number centred a bit left, unit just to the right
                val numStr = itemVal.toString()
                val unitStr = unit
                val numW = paintBig.measureText(numStr)
                val unitW = paintUnit.measureText(unitStr)
                val gap = ih * 0.12f
                val totalW = numW + gap + unitW
                val startX = width / 2f - totalW / 2f

                val bounds = Rect()
                paintBig.getTextBounds(numStr, 0, numStr.length, bounds)
                val baseline = itemY + bounds.height() / 2f

                canvas.drawText(numStr, startX + numW, baseline, paintBig)
                canvas.drawText(unitStr, startX + numW + gap, baseline, paintUnit)
            } else {
                val fade = (1f - (dist / (ih * (VISIBLE_ITEMS / 2f + 0.5f))).coerceIn(0f, 1f))
                paintSmall.alpha = (255 * fade * fade).toInt()
                val bounds = Rect()
                paintSmall.getTextBounds(itemVal.toString(), 0, itemVal.toString().length, bounds)
                val baseline = itemY + bounds.height() / 2f
                canvas.drawText(itemVal.toString(), width / 2f, baseline, paintSmall)
            }
        }
    }

    // ── Touch ─────────────────────────────────────────────────────────────────
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                scroller.forceFinished(true)
                velocityTracker?.recycle()
                velocityTracker = VelocityTracker.obtain()
                velocityTracker?.addMovement(event)
                lastY = event.y
            }
            MotionEvent.ACTION_MOVE -> {
                val dy = lastY - event.y
                scrollOffset = (scrollOffset + dy).coerceIn(0f, maxScroll())
                lastY = event.y
                velocityTracker?.addMovement(event)
                updateValue()
                invalidate()
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                velocityTracker?.computeCurrentVelocity(1000)
                val vy = velocityTracker?.yVelocity ?: 0f
                velocityTracker?.recycle(); velocityTracker = null
                if (abs(vy) > minFling) {
                    scroller.fling(0, scrollOffset.toInt(), 0, (-vy).toInt(),
                        0, 0, 0, maxScroll().toInt())
                    postInvalidateOnAnimation()
                } else {
                    snapToNearest()
                }
            }
        }
        return true
    }

    override fun computeScroll() {
        if (scroller.computeScrollOffset()) {
            scrollOffset = scroller.currY.toFloat().coerceIn(0f, maxScroll())
            updateValue()
            invalidate()
        } else {
            if (!scroller.isFinished) snapToNearest()
        }
    }

    private fun snapToNearest() {
        val ih = itemHeight; if (ih == 0f) return
        val nearest = (scrollOffset / ih).roundToInt() * ih
        scroller.startScroll(0, scrollOffset.toInt(), 0, (nearest - scrollOffset).toInt(), 180)
        postInvalidateOnAnimation()
    }

    private fun updateValue() {
        val ih = itemHeight; if (ih == 0f) return
        val newVal = (minValue + (scrollOffset / ih).roundToInt()).coerceIn(minValue, maxValue)
        if (newVal != _value) { _value = newVal; onValueChanged?.invoke(newVal) }
    }

    private fun clampScroll() {
        scrollOffset = scrollOffset.coerceIn(0f, maxScroll())
    }

    private fun maxScroll() = itemHeight * (maxValue - minValue)

    companion object { private const val VISIBLE_ITEMS = 5 }
}