package com.nokia.phone

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import kotlinx.coroutines.delay

/**
 * Dịch vụ chuông báo thức: tự phát nhạc (kênh ALARM, lặp), rung và bật màn hình báo thức,
 * không phụ thuộc vào việc thông báo có kêu hay không. Dừng bằng AlarmActivity / nút trên thông báo,
 * hoặc tự tắt sau 2 phút.
 */
class AlarmService : Service() {
    companion object {
        @Volatile var ringing = false
        private const val CH = "nokia_alarm_ring"
        private const val NOTI_ID = 3000
        private const val ACTION_STOP = "com.nokia.phone.ALARM_STOP"
        private const val ACTION_SNOOZE = "com.nokia.phone.ALARM_SNOOZE"
        private const val RING_MS = 2 * 60 * 1000L

        fun stop(ctx: Context) { ctx.startService(Intent(ctx, AlarmService::class.java).setAction(ACTION_STOP)) }
        fun snooze(ctx: Context) { ctx.startService(Intent(ctx, AlarmService::class.java).setAction(ACTION_SNOOZE)) }
    }

    private var player: MediaPlayer? = null
    private var wake: PowerManager.WakeLock? = null
    private val handler = Handler(Looper.getMainLooper())

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> { AlarmStore.cancelSnooze(this); finishRinging(); return START_NOT_STICKY }
            ACTION_SNOOZE -> { AlarmStore.snooze(this); finishRinging(); return START_NOT_STICKY }
        }
        val i = intent?.getIntExtra("i", -1) ?: -1
        val label = alarmLabel(this, i)

        val full = PendingIntent.getActivity(this, 1,
            Intent(this, AlarmActivity::class.java).putExtra("label", label)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val stopPi = PendingIntent.getService(this, 2,
            Intent(this, AlarmService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val snoozePi = PendingIntent.getService(this, 3,
            Intent(this, AlarmService::class.java).setAction(ACTION_SNOOZE),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

        if (Build.VERSION.SDK_INT >= 26) {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            // Chuông do dịch vụ tự phát nên kênh này im lặng, chỉ để hiện thông báo/full-screen
            nm.createNotificationChannel(NotificationChannel(CH, "Báo thức đang reo", NotificationManager.IMPORTANCE_HIGH).apply {
                setSound(null, null); enableVibration(false)
            })
        }
        val n: Notification = NotificationCompat.Builder(this, CH)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("Báo thức").setContentText(label)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setOngoing(true)
            .setContentIntent(full)
            .setFullScreenIntent(full, true)
            .addAction(0, "Báo lại", snoozePi)
            .addAction(0, "Tắt", stopPi)
            .build()
        try {
            ServiceCompat.startForeground(this, NOTI_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        } catch (_: Exception) {
            alarmFallbackNotify(this, i)
            stopSelf()
            return START_NOT_STICKY
        }
        if (ringing) return START_NOT_STICKY   // đang reo rồi: chỉ cập nhật thông báo
        ringing = true

        try {
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            wake = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "nokia:alarm").apply { acquire(RING_MS + 10_000L) }
        } catch (_: Exception) {}
        startSound()
        startVibrate()
        // Có thể bị Android chặn khi app ở nền; khi đó full-screen intent của thông báo sẽ mở màn hình báo thức
        try {
            startActivity(Intent(this, AlarmActivity::class.java).putExtra("label", label)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP))
        } catch (_: Exception) {}
        handler.postDelayed({ finishRinging() }, RING_MS)
        return START_NOT_STICKY
    }

    private fun play(uri: Uri?): MediaPlayer? {
        if (uri == null) return null
        val mp = MediaPlayer()
        return try {
            mp.setAudioAttributes(AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
            mp.setDataSource(this, uri)
            mp.isLooping = true
            mp.prepare()
            mp.start()
            mp
        } catch (_: Exception) {
            try { mp.release() } catch (_: Exception) {}
            null
        }
    }

    private fun startSound() {
        val chosen = AlarmStore.alarmSound(this)
        if (chosen == null) return   // người dùng chọn "Im lặng"
        // Âm lượng kênh báo thức = 0 thì chuông có reo cũng không nghe được: nâng lên mức vừa
        try {
            val am = getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager
            if (am.getStreamVolume(android.media.AudioManager.STREAM_ALARM) == 0)
                am.setStreamVolume(android.media.AudioManager.STREAM_ALARM,
                    maxOf(1, am.getStreamMaxVolume(android.media.AudioManager.STREAM_ALARM) * 7 / 10), 0)
        } catch (_: Exception) {}
        player = play(chosen)
            ?: play(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM))
            ?: play(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE))
    }

    @Suppress("DEPRECATION")
    private fun startVibrate() {
        val v = getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator ?: return
        val pattern = longArrayOf(0, 600, 400, 600, 400)
        try {
            if (Build.VERSION.SDK_INT >= 26) v.vibrate(VibrationEffect.createWaveform(pattern, 0))
            else v.vibrate(pattern, 0)
        } catch (_: Exception) {}
    }

    private fun cleanup() {
        ringing = false
        handler.removeCallbacksAndMessages(null)
        try { player?.stop() } catch (_: Exception) {}
        try { player?.release() } catch (_: Exception) {}
        player = null
        try { (getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator)?.cancel() } catch (_: Exception) {}
        try { if (wake?.isHeld == true) wake?.release() } catch (_: Exception) {}
        wake = null
    }

    private fun finishRinging() {
        cleanup()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() { cleanup(); super.onDestroy() }
}

/** Màn hình báo thức kiểu Nokia: hiện trên màn hình khóa. OK/Dừng = tắt, Báo lại = sau 5 phút. */
class AlarmActivity : LcdActivity() {
    override val overLock = true
    private var label by mutableStateOf("")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        label = intent?.getStringExtra("label") ?: ""
        setLcd(left = { "Dừng" }, right = { "Báo lại" }) { AlarmLcd() }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        intent.getStringExtra("label")?.let { label = it }
    }

    override fun onKey(k: String) {
        when (k) {
            "OK", "SOFTL", "END", "CALL" -> { AlarmService.stop(this); finish() }
            "SOFTR" -> { AlarmService.snooze(this); finish() }
        }
    }

    @Composable
    private fun AlarmLcd() {
        var t by remember { mutableIntStateOf(0) }
        LaunchedEffect(Unit) { while (true) { t++; delay(400) } }
        // Chuông đã tắt (từ thông báo hoặc tự hết giờ) thì đóng màn hình này
        LaunchedEffect(Unit) { delay(2500); while (AlarmService.ringing) delay(500); finish() }
        Column(Modifier.fillMaxSize(), Arrangement.SpaceEvenly, Alignment.CenterHorizontally) {
            Text(if (t % 2 == 0) "● BÁO THỨC" else "○ BÁO THỨC", color = INK, fontFamily = MONO,
                fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Text(label, color = INK, fontFamily = MONO, fontWeight = FontWeight.Bold, fontSize = 56.sp)
            Text("OK: dừng", color = INK, fontFamily = MONO, fontSize = 13.sp)
        }
    }
}
