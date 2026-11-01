package com.nokia.phone.tvremote

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.view.MotionEvent
import android.view.View
import kotlin.math.min

/**
 * Bảng màu của remote. Mặc định kiểu Sony (nền trắng, viền đen, nhấn cam);
 * trong launcher Nokia đổi sang màu ô LCD (xanh nhạt + mực xanh đen) bằng [lcd].
 */
object RemoteTheme {
    var paper = Color.WHITE
    var ink = Color.BLACK
    var pressed = 0xFFE4E4E4.toInt()
    var accent = 0xFFFF6B00.toInt()
    var track = 0xFFE6E6E6.toInt()
    var muted = 0xFF6B6B6B.toInt()

    fun sony() {
        paper = Color.WHITE; ink = Color.BLACK; pressed = 0xFFE4E4E4.toInt()
        accent = 0xFFFF6B00.toInt(); track = 0xFFE6E6E6.toInt(); muted = 0xFF6B6B6B.toInt()
    }

    fun lcd(paperColor: Int, inkColor: Int) {
        paper = paperColor; ink = inkColor
        pressed = 0xFFA9D9BC.toInt(); accent = inkColor; track = 0xFFB4E0C5.toInt(); muted = 0xFF4F6B57.toInt()
    }
}

/** Nút tròn / chữ nhật bo góc viền mực, icon tự vẽ (không phụ thuộc font nên không bị ô vuông lỗi). */
class IconButton(ctx: Context, private val kind: Int) : View(ctx) {
    var inverted = false
        set(v) { field = v; invalidate() }
    /** Màu nền riêng (0 = dùng màu mặc định); có màu thì icon vẽ màu giấy. */
    var tint = 0
        set(v) { field = v; invalidate() }
    /** true: nền hình chữ nhật bo góc (icon vẽ theo nửa cạnh ngắn), false: nền tròn. */
    var rect = false
        set(v) { field = v; invalidate() }

    private val d = resources.displayMetrics.density
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
    }

    init { isClickable = true }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        if (e.actionMasked == MotionEvent.ACTION_DOWN) Haptics.tick(this)
        return super.onTouchEvent(e)
    }

    private fun darken(c: Int) = Color.rgb((Color.red(c) * 0.8f).toInt(), (Color.green(c) * 0.8f).toInt(), (Color.blue(c) * 0.8f).toInt())

    override fun drawableStateChanged() { super.drawableStateChanged(); invalidate() }

    override fun onDraw(c: Canvas) {
        val ink = RemoteTheme.ink; val paper = RemoteTheme.paper
        val cx = width / 2f; val cy = height / 2f
        val r = min(width, height) / 2f - 2f * d
        fill.color = if (tint != 0) (if (isPressed) darken(tint) else tint) else if (inverted) ink else if (isPressed) RemoteTheme.pressed else paper
        line.color = ink; line.strokeWidth = 2.2f * d
        if (rect) {
            val bg = RectF(2f * d, 2f * d, width - 2f * d, height - 2f * d)
            c.drawRoundRect(bg, 12f * d, 12f * d, fill)
            c.drawRoundRect(bg, 12f * d, 12f * d, line)
        } else {
            c.drawCircle(cx, cy, r, fill)
            c.drawCircle(cx, cy, r, line)
        }

        line.color = if (inverted || tint != 0) paper else ink
        line.strokeWidth = 3f * d
        if (kind == POWER) {
            val ri = r * 0.38f
            c.drawArc(RectF(cx - ri, cy - ri, cx + ri, cy + ri), -60f, 300f, false, line)
            c.drawLine(cx, cy - r * 0.46f, cx, cy - r * 0.04f, line)
        } else if (kind == REW || kind == FWD) {
            // Tua 10 giây: 2 tam giác cùng hướng + số "10" bên dưới
            fill.color = line.color
            val dir = if (kind == FWD) 1f else -1f
            val t = r * 0.22f
            for (k in 0..1) {
                val x = cx + dir * (k - 0.5f) * t * 1.7f
                val tri = Path().apply {
                    moveTo(x + dir * t, cy - r * 0.16f)
                    lineTo(x - dir * t * 0.8f, cy - r * 0.16f - t)
                    lineTo(x - dir * t * 0.8f, cy - r * 0.16f + t)
                    close()
                }
                c.drawPath(tri, fill)
            }
            fill.textSize = r * 0.40f; fill.textAlign = Paint.Align.CENTER
            fill.typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            c.drawText("10", cx, cy + r * 0.62f, fill)
        } else if (kind == PLAY) {
            // Phát / dừng trong 1 nút: tam giác + 2 vạch
            fill.color = line.color
            val t = r * 0.30f
            val tx = cx - r * 0.24f
            c.drawPath(Path().apply {
                moveTo(tx + t, cy); lineTo(tx - t * 0.8f, cy - t * 1.1f); lineTo(tx - t * 0.8f, cy + t * 1.1f); close()
            }, fill)
            val bw = r * 0.10f
            c.drawRect(cx + r * 0.22f, cy - t * 1.0f, cx + r * 0.22f + bw, cy + t * 1.0f, fill)
            c.drawRect(cx + r * 0.46f, cy - t * 1.0f, cx + r * 0.46f + bw, cy + t * 1.0f, fill)
        } else if (kind == NEXT) {
            fill.color = line.color
            val t = r * 0.30f
            val tx = cx - r * 0.08f
            c.drawPath(Path().apply {
                moveTo(tx + t, cy); lineTo(tx - t * 0.9f, cy - t * 1.1f); lineTo(tx - t * 0.9f, cy + t * 1.1f); close()
            }, fill)
            c.drawRect(cx + r * 0.34f, cy - t * 1.1f, cx + r * 0.34f + r * 0.11f, cy + t * 1.1f, fill)
        } else if (kind == FULL) {
            // Phóng to: 4 góc khung hướng ra ngoài
            val o = r * 0.50f; val l = r * 0.24f
            for (sx in intArrayOf(-1, 1)) for (sy in intArrayOf(-1, 1)) {
                val px = cx + sx * o; val py = cy + sy * o
                c.drawLine(px, py, px - sx * l, py, line)
                c.drawLine(px, py, px, py - sy * l, line)
            }
        } else if (kind == TOUCH) {
            // Chạm: chấm tròn + 2 vòng lan tỏa; đang khóa (inverted) thêm nét gạch chéo
            fill.color = line.color
            c.drawCircle(cx, cy, r * 0.10f, fill)
            line.strokeWidth = 2.6f * d
            c.drawCircle(cx, cy, r * 0.30f, line)
            c.drawCircle(cx, cy, r * 0.52f, line)
            if (inverted) {
                line.strokeWidth = 3.4f * d
                c.drawLine(cx - r * 0.58f, cy + r * 0.58f, cx + r * 0.58f, cy - r * 0.58f, line)
            }
        } else if (kind == UP || kind == DOWN || kind == LEFT || kind == RIGHT) {
            // Mũi tên điều hướng: tam giác đặc to, hướng theo nút
            fill.color = line.color
            val s = min(width, height) * 0.24f
            val p = Path()
            when (kind) {
                UP -> { p.moveTo(cx, cy - s); p.lineTo(cx - s * 1.1f, cy + s * 0.8f); p.lineTo(cx + s * 1.1f, cy + s * 0.8f) }
                DOWN -> { p.moveTo(cx, cy + s); p.lineTo(cx - s * 1.1f, cy - s * 0.8f); p.lineTo(cx + s * 1.1f, cy - s * 0.8f) }
                LEFT -> { p.moveTo(cx - s, cy); p.lineTo(cx + s * 0.8f, cy - s * 1.1f); p.lineTo(cx + s * 0.8f, cy + s * 1.1f) }
                else -> { p.moveTo(cx + s, cy); p.lineTo(cx - s * 0.8f, cy - s * 1.1f); p.lineTo(cx - s * 0.8f, cy + s * 1.1f) }
            }
            p.close()
            c.drawPath(p, fill)
        } else {
            val cap = RectF(cx - r * 0.14f, cy - r * 0.50f, cx + r * 0.14f, cy + r * 0.06f)
            c.drawRoundRect(cap, r * 0.14f, r * 0.14f, line)
            c.drawArc(RectF(cx - r * 0.30f, cy - r * 0.34f, cx + r * 0.30f, cy + r * 0.26f), 0f, 180f, false, line)
            c.drawLine(cx, cy + r * 0.26f, cx, cy + r * 0.46f, line)
            c.drawLine(cx - r * 0.16f, cy + r * 0.46f, cx + r * 0.16f, cy + r * 0.46f, line)
        }
    }

    companion object {
        const val POWER = 0; const val MIC = 1; const val TOUCH = 2; const val FULL = 3
        const val REW = 4; const val FWD = 5; const val PLAY = 6; const val NEXT = 7
        const val UP = 8; const val DOWN = 9; const val LEFT = 10; const val RIGHT = 11
    }
}
