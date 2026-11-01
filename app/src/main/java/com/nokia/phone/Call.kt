package com.nokia.phone

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.ContactsContract
import android.telecom.Call
import android.telecom.CallAudioState
import android.telecom.DisconnectCause
import android.telecom.InCallService
import android.telecom.TelecomManager
import android.telecom.VideoProfile
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.app.NotificationCompat
import java.util.concurrent.ConcurrentHashMap

/** Thông tin hiển thị ngắn sau khi cuộc gọi kết thúc. */
data class CallEnd(val name: String, val number: String, val text: String, val durMs: Long)

/**
 * Trạng thái cuộc gọi dùng chung giữa NokiaInCallService (nhận sự kiện từ hệ thống)
 * và CallActivity (màn hình LCD). Chỉ hoạt động khi phonecuibap là "ứng dụng gọi điện mặc định".
 */
object CallHub {
    var calls by mutableStateOf(listOf<Call>())
        private set
    var tick by mutableIntStateOf(0)
        private set
    var ended by mutableStateOf<CallEnd?>(null)
    var recording by mutableStateOf(false)
    @Volatile var service: NokiaInCallService? = null
    val names = ConcurrentHashMap<String, String>()

    fun bump() { tick++ }
    /** Gọi trong composable để Compose vẽ lại mỗi khi cuộc gọi đổi trạng thái. */
    fun watch(): Int = tick

    fun add(c: Call) { calls = calls + c; bump() }
    fun remove(c: Call) { calls = calls - c; bump() }

    @Suppress("DEPRECATION")
    fun stateOf(c: Call): Int = c.state

    fun ringing(): Call? = calls.firstOrNull { stateOf(it) == Call.STATE_RINGING }
    fun primary(): Call? = ringing()
        ?: calls.firstOrNull { stateOf(it) != Call.STATE_DISCONNECTED }
        ?: calls.firstOrNull()

    fun numberOf(c: Call): String = Uri.decode(c.details?.handle?.schemeSpecificPart.orEmpty())

    /** Tên trong danh bạ (cần quyền Danh bạ), không có thì trả về chính số. */
    fun nameOf(ctx: Context, number: String): String {
        if (number.isEmpty()) return "Số ẩn"
        names[number]?.let { return it }
        try {
            val u = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(number))
            var found = number
            ctx.contentResolver.query(u, arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME), null, null, null)?.use {
                if (it.moveToFirst()) found = it.getString(0)?.ifBlank { null } ?: number
            }
            names[number] = found
            return found
        } catch (_: Exception) {}
        return number
    }

    fun answer(c: Call) = c.answer(VideoProfile.STATE_AUDIO_ONLY)
    fun reject(c: Call) = c.reject(false, null)

    /** Tắt chuông cuộc gọi đến mà không từ chối. */
    fun silence(ctx: Context) {
        try { (ctx.getSystemService(Context.TELECOM_SERVICE) as TelecomManager).silenceRinger() } catch (_: Exception) {}
    }

    @Suppress("DEPRECATION")
    fun speakerOn(): Boolean = service?.callAudioState?.route == CallAudioState.ROUTE_SPEAKER

    @Suppress("DEPRECATION")
    fun muted(): Boolean = service?.callAudioState?.isMuted == true

    @Suppress("DEPRECATION")
    fun toggleSpeaker() {
        service?.setAudioRoute(
            if (speakerOn()) CallAudioState.ROUTE_WIRED_OR_EARPIECE else CallAudioState.ROUTE_SPEAKER
        )
    }

    fun toggleMute() { service?.setMuted(!muted()) }
}

const val CH_INCOMING = "incoming_call"
const val CH_ONGOING = "ongoing_call"
const val CH_MISSED = "missed_call"
const val ACT_ANSWER = "com.nokia.phone.CALL_ANSWER"
const val ACT_DECLINE = "com.nokia.phone.CALL_DECLINE"
const val ACT_HANGUP = "com.nokia.phone.CALL_HANGUP"
const val NOTI_INCOMING = 4101
const val NOTI_MISSED = 4102
const val NOTI_ONGOING = 4103

class NokiaInCallService : InCallService() {
    private val cb = object : Call.Callback() {
        override fun onStateChanged(call: Call, state: Int) { CallHub.bump(); refreshNoti() }
        override fun onDetailsChanged(call: Call, details: Call.Details) { CallHub.bump() }
    }

    override fun onCreate() {
        super.onCreate()
        CallHub.service = this
        if (Build.VERSION.SDK_INT >= 26) {
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(
                NotificationChannel(CH_INCOMING, "Cuộc gọi đến", NotificationManager.IMPORTANCE_HIGH).apply {
                    setSound(null, null)          // chuông do hệ thống phát (nhạc chuông chọn trong Cài đặt)
                    enableVibration(false)
                })
            nm.createNotificationChannel(
                NotificationChannel(CH_ONGOING, "Đang gọi", NotificationManager.IMPORTANCE_LOW))
            nm.createNotificationChannel(
                NotificationChannel(CH_MISSED, "Cuộc gọi nhỡ", NotificationManager.IMPORTANCE_DEFAULT))
        }
    }

    override fun onDestroy() {
        if (CallHub.service === this) CallHub.service = null
        super.onDestroy()
    }

    override fun onCallAdded(call: Call) {
        call.registerCallback(cb)
        CallHub.ended = null
        CallHub.add(call)
        refreshNoti()
        openUi()   // cuộc gọi đến: thêm full-screen intent trong thông báo phòng khi bị chặn mở trực tiếp
    }

    override fun onCallRemoved(call: Call) {
        call.unregisterCallback(cb)
        val d = call.details
        val number = CallHub.numberOf(call)
        val code = d?.disconnectCause?.code ?: DisconnectCause.UNKNOWN
        val dur = if (d != null && d.connectTimeMillis > 0) System.currentTimeMillis() - d.connectTimeMillis else 0L
        val text = when (code) {
            DisconnectCause.REMOTE -> "Đối phương cúp máy"
            DisconnectCause.REJECTED -> "Đã từ chối"
            DisconnectCause.MISSED -> "Cuộc gọi nhỡ"
            DisconnectCause.BUSY -> "Máy bận"
            DisconnectCause.CANCELED -> "Đã hủy"
            DisconnectCause.ERROR -> "Không gọi được"
            else -> "Kết thúc"
        }
        val name = d?.callerDisplayName?.takeIf { it.isNotBlank() }
            ?: CallHub.names[number] ?: number.ifEmpty { "Số ẩn" }
        CallHub.remove(call)
        if (CallHub.calls.isEmpty()) {
            if (CallHub.recording) { Rec.stop(); CallHub.recording = false }   // lưu bản ghi cuộc gọi
            CallHub.ended = CallEnd(name, number, text, dur)
        }
        refreshNoti()
        if (code == DisconnectCause.MISSED) notifyMissed(name)
    }

    @Suppress("DEPRECATION")
    override fun onCallAudioStateChanged(audioState: CallAudioState?) { CallHub.bump() }

    override fun onBringToForeground(showDialpad: Boolean) { openUi() }

    private fun uiIntent() = Intent(this, CallActivity::class.java)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)

    private fun openUi() {
        try { startActivity(uiIntent()) } catch (_: Exception) {}
    }

    private fun pi(i: Intent, rc: Int): PendingIntent = PendingIntent.getActivity(
        this, rc, i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

    private fun act(a: String, rc: Int): PendingIntent = PendingIntent.getBroadcast(
        this, rc, Intent(a).setPackage(packageName), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

    private fun labelOf(call: Call): String {
        val number = CallHub.numberOf(call)
        return call.details?.callerDisplayName?.takeIf { it.isNotBlank() } ?: CallHub.nameOf(this, number)
    }

    /** Cuộc gọi đến -> thông báo full-screen (mở CallActivity); đang gọi -> thông báo giữ cuộc gọi; hết -> xóa. */
    private fun refreshNoti() {
        val nm = getSystemService(NotificationManager::class.java)
        val r = CallHub.ringing()
        if (r != null) {
            nm.cancel(NOTI_ONGOING)
            val open = pi(uiIntent(), 0)
            val n = NotificationCompat.Builder(this, CH_INCOMING)
                .setSmallIcon(android.R.drawable.sym_call_incoming)
                .setContentTitle(labelOf(r))
                .setContentText("Cuộc gọi đến")
                .setCategory(NotificationCompat.CATEGORY_CALL)
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setContentIntent(open)
                .setFullScreenIntent(open, true)
                .addAction(0, "Nghe", act(ACT_ANSWER, 1))
                .addAction(0, "Từ chối", act(ACT_DECLINE, 2))
                .build()
            nm.notify(NOTI_INCOMING, n)
            return
        }
        nm.cancel(NOTI_INCOMING)
        val c = CallHub.primary()
        if (c == null) { nm.cancel(NOTI_ONGOING); return }
        val st = CallHub.stateOf(c)
        val b = NotificationCompat.Builder(this, CH_ONGOING)
            .setSmallIcon(android.R.drawable.sym_action_call)
            .setContentTitle(labelOf(c))
            .setContentText(if (st == Call.STATE_HOLDING) "Đang giữ" else "Đang gọi")
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(pi(uiIntent(), 3))
            .addAction(0, "Kết thúc", act(ACT_HANGUP, 4))
        val t = c.details?.connectTimeMillis ?: 0L
        if (t > 0) b.setWhen(t).setUsesChronometer(true)
        nm.notify(NOTI_ONGOING, b.build())
    }

    private fun notifyMissed(name: String) {
        val n = NotificationCompat.Builder(this, CH_MISSED)
            .setSmallIcon(android.R.drawable.sym_call_missed)
            .setContentTitle("Cuộc gọi nhỡ")
            .setContentText(name)
            .setCategory(NotificationCompat.CATEGORY_MISSED_CALL)
            .setAutoCancel(true)
            .setContentIntent(pi(Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP), 5))
            .build()
        getSystemService(NotificationManager::class.java).notify(NOTI_MISSED, n)
    }
}

/** Nút Nghe / Từ chối / Kết thúc trên thông báo. */
class CallActionReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        when (i.action) {
            ACT_ANSWER -> CallHub.ringing()?.let {
                CallHub.answer(it)
                try {
                    c.startActivity(Intent(c, CallActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP))
                } catch (_: Exception) {}
            }
            ACT_DECLINE -> CallHub.ringing()?.let { CallHub.reject(it) }
            ACT_HANGUP -> CallHub.primary()?.disconnect()
        }
    }
}
