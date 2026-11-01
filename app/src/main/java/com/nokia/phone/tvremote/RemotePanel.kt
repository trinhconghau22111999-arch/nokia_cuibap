package com.nokia.phone.tvremote

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ClipDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import org.json.JSONObject
import java.util.concurrent.Executors

/**
 * Giao diện tay cầm điều khiển TV, GỌN trong nửa màn hình phía trên (không còn chuột cảm ứng).
 *
 * Từ trên xuống: tên + trạng thái kết nối | 4 nút tròn (tắt màn hình, giọng nói, tắt cảm ứng TV,
 * phóng to) | khu giữa: 4 nút mũi tên TRÁI/PHẢI/LÊN/XUỐNG (cụm chữ thập) bên trái, bên phải là
 * LÙI + chất lượng 360p/720p + BÀI TIẾP | 2 thanh trượt (âm lượng, độ sáng) | hàng media.
 *
 * Mỗi lệnh là 1 tin JSON {"type":"remote","action":...} gửi qua [sendRaw] (DataChannel tới app TV):
 *   key (key = up|down|left|right) · volume / brightness (level 0..100) · quality (q = 360|720) ·
 *   back · fullscreen · media (cmd = rew|fwd|playpause|next) · touchlock (on) · screen (on) · voice (q)
 *
 * [sendRaw] trả về số TV nhận được lệnh; [onMic] do nơi chứa giao diện xử lý (mở nhận dạng giọng nói
 * rồi gọi [sendVoice]).
 */
class RemotePanel(
    ctx: Context,
    private val title: String,
    private val sendRaw: (String) -> Int,
    private val onMic: () -> Unit
) : LinearLayout(ctx) {

    private val ui = Handler(Looper.getMainLooper())
    private val sender = Executors.newSingleThreadExecutor()

    private lateinit var statusDot: View
    private lateinit var statusText: TextView
    private lateinit var btnScreen: IconButton
    private lateinit var screenBox: LinearLayout
    private lateinit var btnTouch: IconButton
    private lateinit var touchBox: LinearLayout
    private lateinit var btnQ360: TextView
    private lateinit var btnQ480: TextView
    private lateinit var btnQ720: TextView

    private var tvScreenOn = true
    private var tvTouchOff = false
    private var quality = 0
    private var lastWarnAt = 0L

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    init {
        orientation = VERTICAL
        setBackgroundColor(RemoteTheme.paper)
        setPadding(dp(10), dp(4), dp(10), dp(4))
        build()
        setConnected(false)
    }

    // ---------------------------------------------------------------- Dựng giao diện

    private fun box(fill: Int, radiusDp: Int, strokeDp: Float) = GradientDrawable().apply {
        setColor(fill); cornerRadius = dp(radiusDp).toFloat()
        setStroke((strokeDp * resources.displayMetrics.density).toInt(), RemoteTheme.ink)
    }

    private fun caption(t: String, size: Float = 9f) = TextView(context).apply {
        text = t; textSize = size; setTextColor(RemoteTheme.ink); letterSpacing = 0.05f
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL); gravity = Gravity.CENTER
        setLineSpacing(0f, 0.95f)
    }

    private fun labeled(btn: View, label: String) = LinearLayout(context).apply {
        orientation = VERTICAL; gravity = Gravity.CENTER_HORIZONTAL
        addView(btn, LayoutParams(dp(50), dp(50)))
        addView(caption(label, 8.5f), LayoutParams(-2, -2).also { it.topMargin = dp(1) })
    }

    /** Nút chữ nhật bo góc có chữ (LÙI, BÀI TIẾP...). */
    private fun textButton(label: String, sizeSp: Float, onClick: () -> Unit) = TextView(context).apply {
        text = label; textSize = sizeSp; gravity = Gravity.CENTER; letterSpacing = 0.04f
        typeface = Typeface.create("sans-serif-black", Typeface.BOLD)
        setTextColor(RemoteTheme.ink); background = box(RemoteTheme.paper, 12, 2.2f)
        setOnClickListener { Haptics.tick(this); onClick() }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun build() {
        // ---- Đầu trang: tên + trạng thái kết nối
        val head = LinearLayout(context).apply { orientation = HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        head.addView(TextView(context).apply {
            text = title; textSize = 12.5f; setTextColor(RemoteTheme.ink); letterSpacing = 0.15f
            typeface = Typeface.create("sans-serif-black", Typeface.BOLD)
        }, LayoutParams(0, -2, 1f))
        statusText = TextView(context).apply {
            setTextColor(RemoteTheme.ink); textSize = 10.5f; letterSpacing = 0.03f
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        }
        statusDot = View(context)
        head.addView(statusDot, LayoutParams(dp(9), dp(9)).also { it.rightMargin = dp(6) })
        head.addView(statusText)
        addView(head, LayoutParams(-1, -2))
        addView(View(context).apply { setBackgroundColor(RemoteTheme.ink) },
            LayoutParams(-1, dp(2)).also { it.topMargin = dp(4) })

        // ---- Hàng 4 nút tròn
        btnScreen = IconButton(context, IconButton.POWER).also {
            it.setOnClickListener {
                tvScreenOn = !tvScreenOn
                send(JSONObject().put("action", "screen").put("on", tvScreenOn))
                refreshScreenButton()
            }
        }
        val btnMic = IconButton(context, IconButton.MIC).also { it.setOnClickListener { onMic() } }
        btnTouch = IconButton(context, IconButton.TOUCH).also {
            it.setOnClickListener {
                tvTouchOff = !tvTouchOff
                send(JSONObject().put("action", "touchlock").put("on", tvTouchOff))
                refreshTouchButton()
            }
        }
        val btnFull = IconButton(context, IconButton.FULL).also {
            it.setOnClickListener { send(JSONObject().put("action", "fullscreen")) }
        }
        screenBox = labeled(btnScreen, "TẮT\nMÀN HÌNH")
        touchBox = labeled(btnTouch, "TẮT CẢM\nỨNG TV")
        val top = LinearLayout(context).apply { orientation = HORIZONTAL; gravity = Gravity.CENTER }
        for (b in listOf(screenBox, touchBox, labeled(btnFull, "PHÓNG TO\nMÀN HÌNH"), labeled(btnMic, "GIỌNG\nNÓI"))) {
            top.addView(b, LayoutParams(0, -2, 1f))
        }
        addView(top, LayoutParams(-1, -2).also { it.topMargin = dp(5) })
        refreshScreenButton(); refreshTouchButton()

        // ---- Khu giữa: 2 thanh trượt DỌC (âm lượng, độ sáng) sát phải | LÙI + chất lượng + BÀI TIẾP
        val mid = LinearLayout(context).apply { orientation = HORIZONTAL; minimumHeight = dp(110) }

        val sliders = LinearLayout(context).apply { orientation = HORIZONTAL }
        sliders.addView(View(context), LayoutParams(0, -1, 0.5f))   // chừa trống bên trái để 2 thanh dồn sát phải
        sliders.addView(vSlider("ÂM LƯỢNG", "volume", 50), LayoutParams(0, -1, 1f).also { it.rightMargin = dp(9) })
        sliders.addView(vSlider("ĐỘ SÁNG", "brightness", 70), LayoutParams(0, -1, 1f).also { it.leftMargin = dp(9) })
        mid.addView(sliders, LayoutParams(0, -1, 1f))

        val side = LinearLayout(context).apply { orientation = VERTICAL }
        val btnBack = textButton("LÙI", 13f) { send(JSONObject().put("action", "back")) }
        side.addView(btnBack, LayoutParams(-1, 0, 1f).also { it.setMargins(dp(3), dp(3), dp(3), dp(3)) })
        val q = LinearLayout(context).apply { orientation = HORIZONTAL }
        btnQ360 = qualityButton(360); btnQ480 = qualityButton(480); btnQ720 = qualityButton(720)
        q.addView(btnQ360, LayoutParams(0, -1, 1f).also { it.rightMargin = dp(3) })
        q.addView(btnQ480, LayoutParams(0, -1, 1f).also { it.leftMargin = dp(3); it.rightMargin = dp(3) })
        q.addView(btnQ720, LayoutParams(0, -1, 1f).also { it.leftMargin = dp(3) })
        side.addView(q, LayoutParams(-1, 0, 1f).also { it.setMargins(dp(3), dp(3), dp(3), dp(3)) })
        val btnNext = textButton("BÀI TIẾP", 13f) { send(JSONObject().put("action", "media").put("cmd", "next")) }
        side.addView(btnNext, LayoutParams(-1, 0, 1f).also { it.setMargins(dp(3), dp(3), dp(3), dp(3)) })
        mid.addView(side, LayoutParams(0, -1, 1.7f).also { it.leftMargin = dp(6) })
        addView(mid, LayoutParams(-1, 0, 1f).also { it.topMargin = dp(2) })
        refreshQuality()

        // ---- Hàng media: lùi 10s | phát/dừng | tới 10s
        val media = LinearLayout(context).apply { orientation = HORIZONTAL; gravity = Gravity.CENTER }
        fun mediaBtn(kind: Int, cmd: String) = IconButton(context, kind).also { b ->
            b.rect = true
            b.setOnClickListener { send(JSONObject().put("action", "media").put("cmd", cmd)) }
        }
        val mediaBtns = listOf(mediaBtn(IconButton.REW, "rew"), mediaBtn(IconButton.PLAY, "playpause"), mediaBtn(IconButton.FWD, "fwd"))
        for ((i, b) in mediaBtns.withIndex()) {
            media.addView(b, LayoutParams(0, dp(50), 1f).also {
                if (i > 0) it.leftMargin = dp(3); if (i < mediaBtns.size - 1) it.rightMargin = dp(3)
            })
        }
        addView(media, LayoutParams(-1, -2).also { it.topMargin = dp(3) })
    }

    /** Thanh trượt DỌC: số ở trên, thanh ở giữa (kéo lên = tăng), nhãn ở dưới. */
    private fun vSlider(label: String, action: String, initial: Int): View = LinearLayout(context).apply {
        orientation = VERTICAL; gravity = Gravity.CENTER_HORIZONTAL
        val value = TextView(context).apply {
            text = "$initial"; textSize = 11.5f; setTextColor(RemoteTheme.ink); gravity = Gravity.CENTER
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        }
        addView(value, LayoutParams(-2, -2))
        var last = 0L
        val sl = VSlider(context, initial) { p, fromUser, done ->
            value.text = "$p"
            if (fromUser) {
                val now = SystemClock.uptimeMillis()
                if (done || now - last >= 80) {
                    last = now
                    send(JSONObject().put("action", action).put("level", p), warn = done)
                }
            }
        }
        addView(sl, LayoutParams(-1, 0, 1f).also { it.topMargin = dp(2); it.bottomMargin = dp(2) })
        addView(caption(label, 8.5f), LayoutParams(-2, -2))
    }

    private fun qualityButton(q: Int) = TextView(context).apply {
        text = "${q}p"; textSize = 13f; gravity = Gravity.CENTER; letterSpacing = 0.04f
        typeface = Typeface.create("sans-serif-black", Typeface.BOLD)
        setOnClickListener {
            Haptics.tick(this)
            quality = q
            refreshQuality()
            send(JSONObject().put("action", "quality").put("q", q))
        }
    }

    // ---------------------------------------------------------------- Trạng thái nút

    /** Nút đang chọn: nền mực chữ giấy; nút còn lại: nền giấy viền mực chữ mực. */
    private fun refreshQuality() {
        for ((b, q) in listOf(btnQ360 to 360, btnQ480 to 480, btnQ720 to 720)) {
            val on = quality == q
            b.background = box(if (on) RemoteTheme.ink else RemoteTheme.paper, 12, 2.2f)
            b.setTextColor(if (on) RemoteTheme.paper else RemoteTheme.ink)
        }
    }

    private fun refreshTouchButton() {
        btnTouch.inverted = tvTouchOff
        (touchBox.getChildAt(1) as TextView).text = if (tvTouchOff) "BẬT CẢM\nỨNG TV" else "TẮT CẢM\nỨNG TV"
    }

    /** TV đang sáng: nút đỏ (bấm để tắt); đã tắt: nút xanh (bấm để bật lại). */
    private fun refreshScreenButton() {
        btnScreen.tint = if (tvScreenOn) 0xFFE5322D.toInt() else 0xFF2BB673.toInt()
        (screenBox.getChildAt(1) as TextView).text = if (tvScreenOn) "TẮT\nMÀN HÌNH" else "MỞ\nMÀN HÌNH"
    }

    fun setConnected(on: Boolean) {
        statusDot.background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(if (on) 0xFF2BB673.toInt() else 0xFFE5322D.toInt())
            setStroke(dp(1), RemoteTheme.ink)
        }
        statusText.text = if (on) "ĐÃ KẾT NỐI" else "CHƯA KẾT NỐI"
    }

    // ---------------------------------------------------------------- Gửi lệnh

    /** Câu nói đã nhận dạng: TV mở YouTube tìm và mở video / chuyển TV ⇄ YouTube. */
    fun sendVoice(q: String) {
        if (q.isNotBlank()) send(JSONObject().put("action", "voice").put("q", q.trim()))
    }

    fun sendKey(key: String) { send(JSONObject().put("action", "key").put("key", key)) }

    private fun send(cmd: JSONObject, warn: Boolean = true) {
        val json = cmd.put("type", "remote").toString()
        try {
            sender.execute {
                val n = try { sendRaw(json) } catch (_: Throwable) { 0 }
                if (n == 0 && warn) {
                    val now = System.currentTimeMillis()
                    if (now - lastWarnAt > 3000) {
                        lastWarnAt = now
                        ui.post { Toast.makeText(context, "Chưa kết nối với TV", Toast.LENGTH_SHORT).show() }
                    }
                }
            }
        } catch (_: Exception) { /* đã đóng */ }
    }

    fun destroy() {
        ui.removeCallbacksAndMessages(null)
        sender.shutdown()
    }
}

/** Thanh trượt dọc tự vẽ: rãnh dày viền mực, phần đã kéo tô mực, nút kéo tròn. */
class VSlider(
    ctx: Context,
    initial: Int,
    private val onChange: (value: Int, fromUser: Boolean, done: Boolean) -> Unit
) : View(ctx) {
    var value = initial.coerceIn(0, 100)
        private set

    private val d = resources.displayMetrics.density
    private val p = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)

    private fun pad() = 14f * d
    private fun trackW() = 16f * d

    override fun onDraw(c: android.graphics.Canvas) {
        val cx = width / 2f
        val top = pad(); val bot = height - pad()
        val tw = trackW()
        val rect = android.graphics.RectF(cx - tw / 2, top, cx + tw / 2, bot)
        p.style = android.graphics.Paint.Style.FILL; p.color = RemoteTheme.track
        c.drawRoundRect(rect, tw / 2, tw / 2, p)
        val y = bot - (bot - top) * value / 100f
        p.color = RemoteTheme.ink
        c.drawRoundRect(android.graphics.RectF(cx - tw / 2, y, cx + tw / 2, bot), tw / 2, tw / 2, p)
        p.style = android.graphics.Paint.Style.STROKE; p.strokeWidth = 2f * d
        c.drawRoundRect(rect, tw / 2, tw / 2, p)
        p.style = android.graphics.Paint.Style.FILL; p.color = RemoteTheme.accent
        c.drawCircle(cx, y, 11f * d, p)
        p.style = android.graphics.Paint.Style.STROKE; p.color = RemoteTheme.ink
        c.drawCircle(cx, y, 11f * d, p)
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE, MotionEvent.ACTION_UP -> {
                parent?.requestDisallowInterceptTouchEvent(true)
                val top = pad(); val bot = height - pad()
                val v = (((bot - e.y) / (bot - top)) * 100f).toInt().coerceIn(0, 100)
                val up = e.actionMasked == MotionEvent.ACTION_UP
                if (v != value || up) { value = v; invalidate(); onChange(v, true, up) }
                if (up) performClick()
            }
            MotionEvent.ACTION_CANCEL -> onChange(value, true, true)
        }
        return true
    }

    override fun performClick(): Boolean = super.performClick()
}
