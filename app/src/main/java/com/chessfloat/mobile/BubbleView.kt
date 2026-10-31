package com.chessfloat.mobile

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration

@SuppressLint("ViewConstructor")
class BubbleView(context: Context) : View(context) {
    var winPct: Int? = null
        set(value) { field = value; invalidate() }

    var onTap: (() -> Unit)? = null
    var onLongPress: (() -> Unit)? = null
    var onDragBy: ((Float, Float) -> Unit)? = null
    var onDragEnd: (() -> Unit)? = null

    private val density = resources.displayMetrics.density
    private val sizePx = (56 * density).toInt()
    private val slop = ViewConfiguration.get(context).scaledTouchSlop
    private val LONG_PRESS_MS = 600L

    private var downTime = 0L
    private var downRawX = 0f
    private var downRawY = 0f
    private var lastRawX = 0f
    private var lastRawY = 0f
    private var dragging = false

    private val font = Typeface.create("sans-serif-black", Typeface.NORMAL)

    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = font
        style = Paint.Style.STROKE
        strokeWidth = 2f * density
        strokeJoin = Paint.Join.ROUND
        color = 0xFF000000.toInt()
    }

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = font
        style = Paint.Style.FILL
        textSize = 15f * density
        color = Theme.TEXT
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(sizePx, sizePx)
    }

    override fun onDraw(canvas: Canvas) {
        val r = sizePx / 2f
        val pct = winPct
        val text: String
        if (pct != null) {
            text = "$pct"
            textPaint.color = when {
                pct >= 60 -> Theme.ACCENT
                pct <= 40 -> Theme.WARN
                else -> 0xFFEDF1F7.toInt()
            }
            strokePaint.alpha = 255
        } else {
            text = "♟"
            textPaint.color = 0x88FFFFFF.toInt()
            strokePaint.alpha = 120
        }
        textPaint.textSize = 15f * density
        strokePaint.textSize = 15f * density
        val baseline = r + textPaint.textSize * 0.36f
        canvas.drawText(text, r, baseline, strokePaint)
        canvas.drawText(text, r, baseline, textPaint)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downRawX = event.rawX
                downRawY = event.rawY
                lastRawX = event.rawX
                lastRawY = event.rawY
                dragging = false
                downTime = SystemClock.uptimeMillis()
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = event.rawX - downRawX
                val dy = event.rawY - downRawY
                if (!dragging && kotlin.math.hypot(dx, dy) > slop) dragging = true
                if (dragging) {
                    onDragBy?.invoke(event.rawX - lastRawX, event.rawY - lastRawY)
                    lastRawX = event.rawX
                    lastRawY = event.rawY
                }
                return true
            }
            MotionEvent.ACTION_UP -> {
                if (dragging) {
                    onDragEnd?.invoke()
                } else {
                    performClick()
                    if (SystemClock.uptimeMillis() - downTime >= LONG_PRESS_MS) {
                        onLongPress?.invoke()
                    } else {
                        onTap?.invoke()
                    }
                }
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                if (dragging) onDragEnd?.invoke()
                dragging = false
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }
}
