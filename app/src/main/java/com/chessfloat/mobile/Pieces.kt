package com.chessfloat.mobile

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface

/**
 * 棋子矢量路径，坐标系 0..100，由桌面版 app/pieces.py 的 QPainterPath 逐条移植。
 * 用 Path.op(UNION) 复刻 QPainterPath.united()。
 */
object Pieces {

    const val PAWN = 1
    const val KNIGHT = 2
    const val BISHOP = 3
    const val ROOK = 4
    const val QUEEN = 5
    const val KING = 6

    private val cache = HashMap<Int, Pair<Path, RectF>>()

    private fun rounded(l: Float, t: Float, w: Float, h: Float, r: Float): Path {
        val p = Path()
        p.addRoundRect(RectF(l, t, l + w, t + h), r, r, Path.Direction.CW)
        return p
    }

    private fun union(vararg paths: Path): Path {
        var acc = Path(paths[0])
        for (i in 1 until paths.size) {
            val next = Path()
            next.op(acc, paths[i], Path.Op.UNION)
            acc = next
        }
        return acc
    }

    private fun pawn(): Path {
        val head = Path().apply { addOval(RectF(37.5f, 17.5f, 62.5f, 42.5f), Path.Direction.CW) }

        val body = Path().apply {
            moveTo(50f, 41f)
            cubicTo(58f, 44f, 61f, 56f, 62f, 68f)
            lineTo(67f, 72f); lineTo(69f, 80f)
            lineTo(31f, 80f); lineTo(33f, 72f); lineTo(38f, 68f)
            cubicTo(39f, 56f, 42f, 44f, 50f, 41f)
            close()
        }
        return union(head, body, rounded(24f, 79f, 52f, 13f, 4f))
    }

    private fun rook(): Path {
        val crown = Path().apply {
            moveTo(25f, 10f); lineTo(37f, 10f); lineTo(37f, 19f); lineTo(44f, 19f)
            lineTo(44f, 10f); lineTo(56f, 10f); lineTo(56f, 19f); lineTo(63f, 19f)
            lineTo(63f, 10f); lineTo(75f, 10f); lineTo(75f, 30f); lineTo(25f, 30f)
            close()
        }
        val body = Path().apply {
            moveTo(31f, 28f); lineTo(69f, 28f); lineTo(64f, 74f); lineTo(36f, 74f); close()
        }
        return union(crown, body, rounded(24f, 73f, 52f, 14f, 4f))
    }

    private fun knight(): Path {
        val body = Path().apply {
            moveTo(72f, 92f); lineTo(72f, 74f)
            cubicTo(70f, 58f, 65f, 44f, 58f, 33f)
            cubicTo(56f, 27f, 55f, 21f, 54f, 17f)
            lineTo(49f, 6f)
            cubicTo(45f, 12f, 41f, 16f, 37f, 21f)
            cubicTo(31f, 28f, 23f, 35f, 19f, 43f)
            cubicTo(15f, 47f, 14f, 53f, 19f, 55f)
            cubicTo(25f, 58f, 31f, 59f, 35f, 65f)
            cubicTo(35f, 74f, 33f, 82f, 30f, 92f)
            close()
        }
        return union(body, rounded(24f, 84f, 54f, 10f, 3f))
    }

    private fun bishop(): Path {
        val ball = Path().apply { addOval(RectF(44.5f, 7.5f, 55.5f, 18.5f), Path.Direction.CW) }
        val mitre = Path().apply {
            moveTo(50f, 19f)
            cubicTo(39f, 28f, 33f, 40f, 33f, 50f)
            lineTo(67f, 50f)
            cubicTo(67f, 40f, 61f, 28f, 50f, 19f)
            close()
        }
        val body = Path().apply {
            moveTo(38f, 57f); lineTo(62f, 57f); lineTo(65f, 74f); lineTo(35f, 74f); close()
        }
        return union(ball, mitre, rounded(30f, 49f, 40f, 9f, 3f), body, rounded(24f, 73f, 52f, 14f, 4f))
    }

    private fun queen(): Path {
        val crown = Path().apply {
            moveTo(19f, 41f); lineTo(22f, 16f); lineTo(31f, 33f); lineTo(36f, 12f)
            lineTo(44f, 32f); lineTo(50f, 9f); lineTo(56f, 32f); lineTo(64f, 12f)
            lineTo(69f, 33f); lineTo(78f, 16f); lineTo(81f, 41f)
            close()
        }
        val pearls = Path()
        for ((cx, cy) in listOf(22f to 16f, 36f to 12f, 50f to 9f, 64f to 12f, 78f to 16f)) {
            pearls.addOval(RectF(cx - 4.2f, cy - 4.2f, cx + 4.2f, cy + 4.2f), Path.Direction.CW)
        }
        val body = Path().apply {
            moveTo(37f, 48f); lineTo(63f, 48f); lineTo(66f, 74f); lineTo(34f, 74f); close()
        }
        return union(
            crown, pearls, rounded(28f, 40f, 44f, 9f, 3f), body, rounded(23f, 73f, 54f, 14f, 4f)
        )
    }

    private fun king(): Path {
        val cross = Path().apply {
            moveTo(46f, 4f); lineTo(54f, 4f); lineTo(54f, 14f); lineTo(64f, 14f)
            lineTo(64f, 22f); lineTo(54f, 22f); lineTo(54f, 33f); lineTo(46f, 33f)
            lineTo(46f, 22f); lineTo(36f, 22f); lineTo(36f, 14f); lineTo(46f, 14f)
            close()
        }
        val crown = Path().apply {
            moveTo(33f, 30f)
            cubicTo(40f, 36f, 60f, 36f, 67f, 30f)
            lineTo(68f, 40f); lineTo(32f, 40f)
            close()
        }
        val body = Path().apply {
            moveTo(38f, 47f); lineTo(62f, 47f); lineTo(66f, 74f); lineTo(34f, 74f); close()
        }
        return union(
            cross, crown, rounded(30f, 39f, 40f, 9f, 3f), body, rounded(23f, 73f, 54f, 14f, 4f)
        )
    }

    private fun build(type: Int): Pair<Path, RectF> {
        val p = when (type) {
            PAWN -> pawn()
            KNIGHT -> knight()
            BISHOP -> bishop()
            ROOK -> rook()
            QUEEN -> queen()
            KING -> king()
            else -> Path()
        }
        p.fillType = Path.FillType.WINDING
        val b = RectF()
        p.computeBounds(b, true)
        return p to b
    }

    private fun path(type: Int): Pair<Path, RectF> =
        cache.getOrPut(type) { build(type) }

    private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL_AND_STROKE }
    private val detailPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL_AND_STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    fun draw(canvas: Canvas, rect: RectF, type: Int, isWhite: Boolean, fill: Int, edge: Int) {
        val (p, bounds) = path(type)
        if (bounds.width() <= 0f || bounds.height() <= 0f) return

        val pad = rect.width() * 0.075f
        val availW = rect.width() - pad * 2
        val availH = rect.height() - pad * 2
        val scale = minOf(availW / bounds.width(), availH / bounds.height())
        if (scale <= 0f) return

        canvas.save()
        canvas.translate(
            rect.centerX() - bounds.centerX() * scale,
            rect.centerY() - bounds.centerY() * scale
        )
        canvas.scale(scale, scale)

        val stroke = (maxOf(0.9f, rect.width() * 0.028f) / scale).coerceAtLeast(0.1f)

        canvas.save()
        canvas.translate(0f, 1.6f)
        shadowPaint.style = Paint.Style.FILL
        shadowPaint.color = if (isWhite) Color.argb(55, 0, 0, 0) else Color.argb(70, 0, 0, 0)
        canvas.drawPath(p, shadowPaint)
        canvas.restore()

        bodyPaint.color = fill
        bodyPaint.strokeWidth = stroke
        bodyPaint.strokeCap = Paint.Cap.ROUND
        bodyPaint.strokeJoin = Paint.Join.ROUND
        bodyPaint.style = Paint.Style.FILL
        canvas.drawPath(p, bodyPaint)
        bodyPaint.style = Paint.Style.STROKE
        bodyPaint.color = edge
        canvas.drawPath(p, bodyPaint)

        detailPaint.color = edge
        detailPaint.strokeWidth = stroke * 0.85f
        detailPaint.style = Paint.Style.FILL_AND_STROKE
        for (line in details(type)) canvas.drawPath(line, detailPaint)

        canvas.restore()
    }

    private fun details(type: Int): List<Path> {
        val out = ArrayList<Path>()
        when (type) {
            ROOK -> for (y in listOf(36f, 64f)) {
                out.add(Path().apply { moveTo(33f, y); lineTo(67f, y) })
            }
            BISHOP -> out.add(Path().apply { moveTo(50f, 24f); lineTo(57f, 40f) })
            KNIGHT -> {
                out.add(Path().apply { addOval(RectF(35.4f, 27.4f, 40.6f, 32.6f), Path.Direction.CW) })
                out.add(Path().apply { addOval(RectF(19f, 47f, 23f, 51f), Path.Direction.CW) })
            }
            PAWN -> out.add(Path().apply { moveTo(42f, 45f); lineTo(58f, 45f) })
        }
        return out
    }
}
