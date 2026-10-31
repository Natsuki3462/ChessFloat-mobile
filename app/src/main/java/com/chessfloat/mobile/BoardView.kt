package com.chessfloat.mobile

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.view.MotionEvent
import android.view.View

data class Piece(val type: Int, val isWhite: Boolean)

data class GameState(
    val pieces: Map<Int, Piece> = emptyMap(),
    val last: Pair<Int, Int>? = null,
    val flipped: Boolean = false,
    val isMyTurn: Boolean = true,
    val isCheck: Boolean = false,
    val kingSquare: Int? = null,
    val isOver: Boolean = false,
    val engineReady: Boolean = false,
    val engineName: String = "",
    val ply: Int = 0,
    val sans: List<String> = emptyList(),
    val turnWhite: Boolean = true,
    val myWhite: Boolean = true
)

/**
 * 自绘棋盘。坐标与桌面版一致：a1 = 0 ... h8 = 63。
 * 触摸改为「点起点 -> 点终点」，再点同一点取消（手机上没有 hover / 右键）。
 */
@SuppressLint("ViewConstructor")
class BoardView(context: Context) : View(context) {

    var state: GameState = GameState()
        set(value) { field = value; invalidate() }

    var suggestion: Pair<Int, Int>? = null
        set(value) { field = value; invalidate() }

    var suggestionStale = false
        set(value) { field = value; invalidate() }

    var selected: Int? = null
        set(value) { field = value; invalidate() }

    var legalTargets: Set<Int> = emptySet()
        set(value) { field = value; invalidate() }

    var showCoords: Boolean = true
        set(value) { field = value; invalidate() }

    var onSquareTap: ((Int) -> Unit)? = null

    private val squarePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Theme.TEXT_FAINT
        textAlign = Paint.Align.CENTER
    }

    private val boardRect = RectF()

    private fun layoutBoard() {
        val side = minOf(width, height).toFloat()
        val pad = maxOf(11f * resources.displayMetrics.density, side * 0.036f)
        val inner = maxOf(40f, side - 2 * pad)
        boardRect.set(
            (width - inner) / 2f,
            (height - inner) / 2f,
            (width + inner) / 2f,
            (height + inner) / 2f
        )
    }

    private fun squareSize() = boardRect.width() / 8f

    fun squareRect(square: Int): RectF {
        val size = squareSize()
        val file = square % 8
        val rank = square / 8
        val col = if (state.flipped) 7 - file else file
        val row = if (state.flipped) rank else 7 - rank
        return RectF(
            boardRect.left + col * size,
            boardRect.top + row * size,
            boardRect.left + (col + 1) * size,
            boardRect.top + (row + 1) * size
        )
    }

    private fun squareAt(x: Float, y: Float): Int? {
        if (!boardRect.contains(x, y)) return null
        val size = squareSize()
        val col = ((x - boardRect.left) / size).toInt()
        val row = ((y - boardRect.top) / size).toInt()
        if (col !in 0..7 || row !in 0..7) return null
        val file = if (state.flipped) 7 - col else col
        val rank = if (state.flipped) row else 7 - row
        return rank * 8 + file
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        layoutBoard()
        textPaint.textSize = maxOf(8f, boardRect.width() / 8f * 0.20f)
        textPaint.typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    }

    override fun onDraw(canvas: Canvas) {
        if (boardRect.width() <= 1f) {
            layoutBoard()
            if (boardRect.width() <= 1f) return
        }

        val radius = maxOf(4f, boardRect.width() * 0.012f)
        canvas.save()
        val clip = Path().apply { addRoundRect(boardRect, radius, radius, Path.Direction.CW) }
        canvas.clipPath(clip)

        drawSquares(canvas)
        drawMarkers(canvas)
        drawPieces(canvas)
        drawHints(canvas)
        drawArrow(canvas)

        canvas.restore()

        drawCoords(canvas)
        linePaint.color = Theme.BOARD_EDGE
        linePaint.strokeWidth = 1f
        canvas.drawRoundRect(boardRect, radius, radius, linePaint)
    }

    private fun drawSquares(canvas: Canvas) {
        for (rank in 0..7) {
            for (file in 0..7) {
                val sq = rank * 8 + file
                squarePaint.color =
                    if ((file + rank) % 2 == 0) Theme.SQ_DARK else Theme.SQ_LIGHT
                canvas.drawRect(squareRect(sq), squarePaint)
            }
        }
    }

    private fun drawMarkers(canvas: Canvas) {
        state.last?.let { (from, to) ->
            fillPaint.color = Theme.LAST_MOVE
            canvas.drawRect(squareRect(from), fillPaint)
            canvas.drawRect(squareRect(to), fillPaint)
        }

        if (state.isCheck) {
            state.kingSquare?.let { sq ->
                val r = squareRect(sq)
                val shader = RadialGradient(
                    r.centerX(), r.centerY(), r.width() * 0.75f,
                    Theme.CHECK_GLOW, Theme.CHECK_GLOW and 0x00FFFFFF,
                    Shader.TileMode.CLAMP
                )
                fillPaint.shader = shader
                canvas.drawRect(r, fillPaint)
                fillPaint.shader = null
            }
        }

        selected?.let { sq ->
            fillPaint.color = Theme.SELECTED
            val r = squareRect(sq)
            canvas.drawRoundRect(
                RectF(r.left + 1, r.top + 1, r.right - 1, r.bottom - 1), 3f, 3f, fillPaint
            )
        }
    }

    private fun drawPieces(canvas: Canvas) {
        for ((sq, piece) in state.pieces) {
            val r = squareRect(sq)
            val fill = if (piece.isWhite) Theme.PIECE_WHITE_FILL else Theme.PIECE_BLACK_FILL
            val edge = if (piece.isWhite) Theme.PIECE_WHITE_EDGE else Theme.PIECE_BLACK_EDGE
            Pieces.draw(canvas, r, piece.type, piece.isWhite, fill, edge)
        }
    }

    private fun drawHints(canvas: Canvas) {
        if (selected == null) return
        val size = squareSize()
        for (target in legalTargets) {
            val r = squareRect(target)
            val occupied = state.pieces.containsKey(target)
            if (!occupied) {
                fillPaint.color = Theme.HINT_DOT
                canvas.drawCircle(r.centerX(), r.centerY(), size * 0.13f, fillPaint)
            } else {
                linePaint.color = Theme.HINT_DOT
                linePaint.strokeWidth = maxOf(2f, size * 0.055f)
                val inset = size * 0.07f
                canvas.drawCircle(
                    r.centerX(), r.centerY(), size / 2 - inset, linePaint
                )
            }
        }
    }

    private fun drawArrow(canvas: Canvas) {
        val move = suggestion ?: return
        val (from, to) = move
        val size = squareSize()
        val start = squareRect(from)
        val end = squareRect(to)
        val sx = start.centerX(); val sy = start.centerY()
        val ex = end.centerX(); val ey = end.centerY()

        val vx = ex - sx; val vy = ey - sy
        val len = kotlin.math.hypot(vx, vy)
        if (len < size * 0.2f) return
        val ux = vx / len; val uy = vy / len
        val nx = -uy; val ny = ux

        val tailX = sx + ux * size * 0.22f; val tailY = sy + uy * size * 0.22f
        val tipX = ex - ux * size * 0.16f; val tipY = ey - uy * size * 0.16f
        val shaftW = maxOf(2.2f, size * 0.095f)
        val headW = maxOf(shaftW * 2.3f, size * 0.30f)
        val headLen = maxOf(size * 0.26f, minOf(size * 0.42f, len * 0.38f))
        val neckX = tipX - ux * headLen; val neckY = tipY - uy * headLen

        val path = Path().apply {
            moveTo(tailX + nx * shaftW / 2, tailY + ny * shaftW / 2)
            lineTo(neckX + nx * shaftW / 2, neckY + ny * shaftW / 2)
            lineTo(neckX + nx * headW / 2, neckY + ny * headW / 2)
            lineTo(tipX, tipY)
            lineTo(neckX - nx * headW / 2, neckY - ny * headW / 2)
            lineTo(neckX - nx * shaftW / 2, neckY - ny * shaftW / 2)
            lineTo(tailX - nx * shaftW / 2, tailY - ny * shaftW / 2)
            close()
        }

        val base = if (suggestionStale) Theme.STALE else Theme.ACCENT
        fillPaint.color = (base and 0x00FFFFFF) or ((if (suggestionStale) 120 else 215) shl 24)
        linePaint.color = 0x50000000
        linePaint.strokeWidth = 1.2f
        canvas.drawPath(path, fillPaint)
        canvas.drawPath(path, linePaint)
    }

    private fun drawCoords(canvas: Canvas) {
        if (!showCoords) return
        val size = squareSize()
        val pad = maxOf(11f * resources.displayMetrics.density, minOf(width, height) * 0.036f)
        val fm = textPaint.fontMetrics
        val baseline = boardRect.bottom + pad * 0.02f - fm.ascent * 0.55f
        for (i in 0..7) {
            val fileIdx = if (state.flipped) 7 - i else i
            canvas.drawText(
                ('a' + fileIdx).toString(),
                boardRect.left + i * size + size / 2,
                baseline,
                textPaint
            )
            val rankIdx = if (state.flipped) i else 7 - i
            canvas.drawText(
                (rankIdx + 1).toString(),
                boardRect.left - pad * 0.72f,
                boardRect.top + i * size + size / 2 + (fm.ascent + fm.descent) / -2,
                textPaint
            )
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_DOWN) {
            val sq = squareAt(event.x, event.y)
            if (sq != null) {
                performClick()
                onSquareTap?.invoke(sq)
                return true
            }
        }
        return true
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }
}
