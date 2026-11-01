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
        addView(btn, LayoutParams(dp(46), dp(46)))
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
        for (b in listOf(screenBox, labeled(btnMic, "GIỌNG\nNÓI"), touchBox, labeled(btnFull, "PHÓNG TO\nMÀN HÌNH"))) {
            top.addView(b, LayoutParams(0, -2, 1f))
        }
        addView(top, LayoutParams(-1, -2).also { it.topMargin = dp(5) })
        refreshScreenButton(); refreshTouchButton()

        // ---- Khu giữa: cụm 4 mũi tên (trái) | LÙI + chất lượng + BÀI TIẾP (phải)
        val mid = LinearLayout(context).apply { orientation = HORIZONTAL; minimumHeight = dp(96) }

        val cross = LinearLayout(context).apply { orientation = VERTICAL }
        fun cell(v: View?) = (v ?: View(context))
        fun crossRow(l: View?, m: View?, r: View?) = LinearLayout(context).apply {
            orientation = HORIZONTAL
            for (v in listOf(l, m, r)) addView(cell(v), LayoutParams(0, -1, 1f).also { p -> p.setMargins(dp(3), dp(3), dp(3), dp(3)) })
        }
        cross.addView(crossRow(null, arrow(IconButton.UP, "up"), null), LayoutParams(-1, 0, 1f))
        cross.addView(crossRow(arrow(IconButton.LEFT, "left"), null, arrow(IconButton.RIGHT, "right")), LayoutParams(-1, 0, 1f))
        cross.addView(crossRow(null, arrow(IconButton.DOWN, "down"), null), LayoutParams(-1, 0, 1f))
        mid.addView(cross, LayoutParams(0, -1, 1.15f))

        val side = LinearLayout(context).apply { orientation = VERTICAL }
        val btnBack = textButton("LÙI", 16f) { send(JSONObject().put("action", "back")) }
        side.addView(btnBack, LayoutParams(-1, 0, 1.2f).also { it.setMargins(dp(3), dp(3), dp(3), dp(3)) })
        val q = LinearLayout(context).apply { orientation = HORIZONTAL }
        btnQ360 = qualityButton(360); btnQ720 = qualityButton(720)
        q.addView(btnQ360, LayoutParams(0, -1, 1f).also { it.rightMargin = dp(3) })
        q.addView(btnQ720, LayoutParams(0, -1, 1f).also { it.leftMargin = dp(3) })
        side.addView(q, LayoutParams(-1, 0, 1f).also { it.setMargins(dp(3), dp(3), dp(3), dp(3)) })
        val btnNext = textButton("BÀI TIẾP", 12f) { send(JSONObject().put("action", "media").put("cmd", "next")) }
        side.addView(btnNext, LayoutParams(-1, 0, 1f).also { it.setMargins(dp(3), dp(3), dp(3), dp(3)) })
        mid.addView(side, LayoutParams(0, -1, 1f).also { it.leftMargin = dp(6) })
        addView(mid, LayoutParams(-1, 0, 1f).also { it.topMargin = dp(2) })
        refreshQuality()

        // ---- 2 thanh trượt: âm lượng, độ sáng
        addView(sliderRow("ÂM LƯỢNG", "volume", 50), LayoutParams(-1, dp(27)).also { it.topMargin = dp(2) })
        addView(sliderRow("ĐỘ SÁNG", "brightness", 70), LayoutParams(-1, dp(27)))

        // ---- Hàng media: lùi 10s | phát/dừng | tới 10s
        val media = LinearLayout(context).apply { orientation = HORIZONTAL; gravity = Gravity.CENTER }
        fun mediaBtn(kind: Int, cmd: String) = IconButton(context, kind).also { b ->
            b.rect = true
            b.setOnClickListener { send(JSONObject().put("action", "media").put("cmd", cmd)) }
        }
        val mediaBtns = listOf(mediaBtn(IconButton.REW, "rew"), mediaBtn(IconButton.PLAY, "playpause"), mediaBtn(IconButton.FWD, "fwd"))
        for ((i, b) in mediaBtns.withIndex()) {
            media.addView(b, LayoutParams(0, dp(38), 1f).also {
                if (i > 0) it.leftMargin = dp(3); if (i < mediaBtns.size - 1) it.rightMargin = dp(3)
            })
        }
        addView(media, LayoutParams(-1, -2).also { it.topMargin = dp(3) })
    }

    /** Nút mũi tên: bấm gửi 1 lần, GIỮ thì lặp lại liên tục (dời con trỏ / chọn nhanh trên TV). */
    @SuppressLint("ClickableViewAccessibility")
    private fun arrow(kind: Int, key: String): IconButton = IconButton(context, kind).also { b ->
        b.rect = true
        val repeat = object : Runnable {
            override fun run() {
                send(JSONObject().put("action", "key").put("key", key), warn = false)
                ui.postDelayed(this, 130)
            }
        }
        b.setOnTouchListener { v, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    v.isPressed = true
                    Haptics.tick(v)
                    send(JSONObject().put("action", "key").put("key", key))
                    ui.postDelayed(repeat, 450)
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    v.isPressed = false
                    ui.removeCallbacks(repeat)
                }
            }
            true
        }
    }

    /** Thanh trượt: nhãn | thanh dày | số; nút kéo tròn. */
    private fun sliderRow(label: String, action: String, initial: Int): View = LinearLayout(context).apply {
        orientation = HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
        val value = TextView(context).apply {
            text = "$initial"; textSize = 11.5f; setTextColor(RemoteTheme.ink); gravity = Gravity.END
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        }
        addView(caption(label, 9.5f).also { it.gravity = Gravity.START }, LayoutParams(dp(66), -2))
        val trackH = dp(13)
        val bg = GradientDrawable().apply {
            setColor(RemoteTheme.track); cornerRadius = trackH / 2f; setStroke(dp(2), RemoteTheme.ink); setSize(-1, trackH)
        }
        val fg = GradientDrawable().apply { setColor(RemoteTheme.ink); cornerRadius = trackH / 2f; setSize(-1, trackH) }
        val layers = LayerDrawable(arrayOf<Drawable>(bg, ClipDrawable(fg, Gravity.START, ClipDrawable.HORIZONTAL)))
        layers.setId(0, android.R.id.background); layers.setId(1, android.R.id.progress)
        val knob = GradientDrawable().apply {
            shape = GradientDrawable.OVAL; setColor(RemoteTheme.accent); setStroke(dp(2), RemoteTheme.ink); setSize(dp(20), dp(20))
        }
        val sb = SeekBar(context).apply {
            max = 100; progress = initial
            progressDrawable = layers
            thumb = knob
            splitTrack = false
            setPadding(dp(14), 0, dp(14), 0)
            var last = 0L
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(s: SeekBar, p: Int, fromUser: Boolean) {
                    value.text = "$p"
                    if (!fromUser) return
                    val now = SystemClock.uptimeMillis()
                    if (now - last >= 80) {
                        last = now
                        send(JSONObject().put("action", action).put("level", p), warn = false)
                    }
                }
                override fun onStartTrackingTouch(s: SeekBar) {}
                override fun onStopTrackingTouch(s: SeekBar) {
                    send(JSONObject().put("action", action).put("level", s.progress))
                }
            })
        }
        addView(sb, LayoutParams(0, -1, 1f))
        addView(value, LayoutParams(dp(30), -2))
    }

    private fun qualityButton(q: Int) = TextView(context).apply {
        text = "${q}p"; textSize = 12.5f; gravity = Gravity.CENTER; letterSpacing = 0.04f
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
        for ((b, q) in listOf(btnQ360 to 360, btnQ720 to 720)) {
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
