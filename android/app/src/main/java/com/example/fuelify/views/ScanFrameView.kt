package com.example.fuelify.views

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View

class ScanFrameView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private val cornerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#F97316")   // orange
        strokeWidth = 6f
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }

    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#55AAAAAA")
        strokeWidth = 1.5f
        style = Paint.Style.STROKE
    }

    private val cornerLen = 48f   // length of each corner arm
    private val radius    = 18f   // corner radius

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        val pad = 4f

        // Full rounded border (dim)
        val rect = RectF(pad, pad, w - pad, h - pad)
        canvas.drawRoundRect(rect, radius, radius, borderPaint)

        // Orange corner brackets
        // Top-left
        canvas.drawLine(pad, pad + radius + cornerLen, pad, pad + radius, cornerPaint)
        canvas.drawArc(RectF(pad, pad, pad + radius * 2, pad + radius * 2), 180f, 90f, false, cornerPaint)
        canvas.drawLine(pad + radius, pad, pad + radius + cornerLen, pad, cornerPaint)

        // Top-right
        canvas.drawLine(w - pad - radius - cornerLen, pad, w - pad - radius, pad, cornerPaint)
        canvas.drawArc(RectF(w - pad - radius * 2, pad, w - pad, pad + radius * 2), 270f, 90f, false, cornerPaint)
        canvas.drawLine(w - pad, pad + radius, w - pad, pad + radius + cornerLen, cornerPaint)

        // Bottom-left
        canvas.drawLine(pad, h - pad - radius - cornerLen, pad, h - pad - radius, cornerPaint)
        canvas.drawArc(RectF(pad, h - pad - radius * 2, pad + radius * 2, h - pad), 90f, 90f, false, cornerPaint)
        canvas.drawLine(pad + radius, h - pad, pad + radius + cornerLen, h - pad, cornerPaint)

        // Bottom-right
        canvas.drawLine(w - pad - radius - cornerLen, h - pad, w - pad - radius, h - pad, cornerPaint)
        canvas.drawArc(RectF(w - pad - radius * 2, h - pad - radius * 2, w - pad, h - pad), 0f, 90f, false, cornerPaint)
        canvas.drawLine(w - pad, h - pad - radius - cornerLen, w - pad, h - pad - radius, cornerPaint)
    }
}
