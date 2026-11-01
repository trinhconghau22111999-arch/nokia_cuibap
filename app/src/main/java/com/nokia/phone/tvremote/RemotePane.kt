package com.nokia.phone.tvremote

import android.app.Activity
import android.os.Handler
import android.os.Looper
import android.view.View

/**
 * App con "Điều khiển TV" trong ô LCD phía trên của launcher Nokia (cùng kiểu với TvPane /
 * YouTubePane: có [view] để nhúng). Dùng màu ô LCD cho hợp giao diện Nokia.
 *
 * Mở pane = bật kết nối tới app TV; [destroy] = ngắt hẳn (rời app con là dừng).
 */
class RemotePane(act: Activity, onMic: () -> Unit) : RemoteLinkService.ConnectionStatusListener {

    private val ui = Handler(Looper.getMainLooper())
    private val panel: RemotePanel
    private var attached: RemoteLinkService? = null
    private var dead = false
    private var host: Activity? = act

    /** Gốc giao diện để nhúng vào LCD. */
    val view: View get() = panel

    init {
        RemoteTheme.lcd(0xFFC7F0D8.toInt(), 0xFF1B2B1B.toInt())
        panel = RemotePanel(act, "ĐIỀU KHIỂN TV", { RemoteLink.send(it) }, onMic)
        RemoteLink.start(act)
        ui.post(object : Runnable {
            override fun run() {
                if (dead) return
                val svc = RemoteLinkService.instance
                if (svc !== attached) {
                    attached?.removeConnectionStatusListener(this@RemotePane)
                    attached = svc
                    svc?.addConnectionStatusListener(this@RemotePane)
                    if (svc == null) panel.setConnected(false)
                }
                ui.postDelayed(this, 1000)
            }
        })
    }

    override fun onConnectionStatusChanged(connectedCount: Int, maxViewers: Int) {
        panel.setConnected(connectedCount > 0)
    }

    /** Phím cứng ▲▼◀▶ OK của thân máy Nokia -> gửi thẳng lệnh phím tới TV. */
    fun key(k: String) {
        val name = when (k) { "UP" -> "up"; "DOWN" -> "down"; "LEFT" -> "left"; "RIGHT" -> "right"; "OK" -> "ok"; else -> return }
        panel.sendKey(name)
    }

    /** Câu nói đã nhận dạng -> TV mở YouTube tìm video / chuyển TV ⇄ YouTube. */
    fun sendVoice(q: String) = panel.sendVoice(q)

    fun destroy() {
        dead = true
        ui.removeCallbacksAndMessages(null)
        attached?.removeConnectionStatusListener(this)
        attached = null
        panel.destroy()
        host?.let { RemoteLink.stop(it) }
        host = null
    }
}
