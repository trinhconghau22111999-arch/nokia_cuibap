package com.nokia.phone

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import android.os.Build
import androidx.core.content.ContextCompat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

data class Alarm(val h: Int, val m: Int, val on: Boolean)

/** Lưu danh sách báo thức (SharedPreferences) và đặt lịch bằng AlarmManager. */
object AlarmStore {
    private const val PREF = "alarms"
    private const val MAX = 10

    fun load(ctx: Context): List<Alarm> {
        val s = ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE).getString("list", "") ?: ""
        return s.split(",").mapNotNull { p ->
            val x = p.split(":")
            val h = x.getOrNull(0)?.toIntOrNull()
            val m = x.getOrNull(1)?.toIntOrNull()
            if (x.size == 3 && h != null && m != null) Alarm(h, m, x[2] == "1") else null
        }.take(MAX)
    }

    fun save(ctx: Context, list: List<Alarm>) {
        ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit()
            .putString("list", list.joinToString(",") { "${it.h}:${it.m}:${if (it.on) 1 else 0}" }).apply()
    }

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE)

    /** Nhạc báo thức: chưa chọn = mặc định của máy, chuỗi rỗng = im lặng. */
    fun alarmSound(ctx: Context): Uri? {
        val s = prefs(ctx).getString("sound", null)
        return when {
            s == null -> RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            s.isEmpty() -> null
            else -> Uri.parse(s)
        }
    }
    fun setAlarmSound(ctx: Context, uri: Uri?) { prefs(ctx).edit().putString("sound", uri?.toString() ?: "").apply() }
    fun resetAlarmSound(ctx: Context) { prefs(ctx).edit().remove("sound").apply() }

    private fun pi(ctx: Context, i: Int): PendingIntent = PendingIntent.getBroadcast(
        ctx, 100 + i, Intent(ctx, AlarmReceiver::class.java).putExtra("i", i),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

    /** Thời điểm reo kế tiếp (ms) của một báo thức giờ:phút; nếu phút hiện tại đã qua thì tính sang ngày mai. */
    fun nextTrigger(h: Int, m: Int): Long = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, h); set(Calendar.MINUTE, m)
        set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        if (timeInMillis <= System.currentTimeMillis()) add(Calendar.DAY_OF_YEAR, 1)
    }.timeInMillis

    private fun showPi(ctx: Context) = PendingIntent.getActivity(ctx, 0, Intent(ctx, MainActivity::class.java),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

    /** Huỷ lịch cũ rồi đặt lại cho lần reo kế tiếp của từng báo thức đang bật (lặp mỗi ngày). */
    fun schedule(ctx: Context, list: List<Alarm> = load(ctx)) {
        val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        for (i in 0 until MAX) am.cancel(pi(ctx, i))
        val show = showPi(ctx)
        list.take(MAX).forEachIndexed { i, a ->
            if (!a.on) return@forEachIndexed
            try {
                am.setAlarmClock(AlarmManager.AlarmClockInfo(nextTrigger(a.h, a.m), show), pi(ctx, i))
            } catch (_: SecurityException) {}
        }
    }

    private fun snoozePi(ctx: Context): PendingIntent = PendingIntent.getBroadcast(
        ctx, 99, Intent(ctx, AlarmReceiver::class.java).putExtra("i", -1),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

    /** Báo lại sau [minutes] phút. */
    fun snooze(ctx: Context, minutes: Int = 5) {
        val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        try {
            am.setAlarmClock(AlarmManager.AlarmClockInfo(System.currentTimeMillis() + minutes * 60_000L, showPi(ctx)), snoozePi(ctx))
        } catch (_: SecurityException) {}
    }

    fun cancelSnooze(ctx: Context) {
        (ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager).cancel(snoozePi(ctx))
    }
}

/**
 * Máy hay chặn báo thức vì tối ưu pin. Lần đầu đặt báo thức thì xin cho app được chạy nền
 * (hộp thoại hệ thống, chỉ hỏi một lần). Trả về true nếu đã mở hộp thoại.
 */
fun askIgnoreBatteryOnce(ctx: Context): Boolean {
    if (Build.VERSION.SDK_INT < 23) return false
    val sp = ctx.getSharedPreferences("alarms", Context.MODE_PRIVATE)
    if (sp.getBoolean("askedBattery", false)) return false
    val pm = ctx.getSystemService(Context.POWER_SERVICE) as PowerManager
    if (pm.isIgnoringBatteryOptimizations(ctx.packageName)) return false
    sp.edit().putBoolean("askedBattery", true).apply()
    return try {
        ctx.startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
            .setData(Uri.parse("package:" + ctx.packageName)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    } catch (_: Exception) { false }
}

/** Nhãn giờ hiển thị khi báo thức reo (báo lại thì lấy giờ hiện tại). */
fun alarmLabel(ctx: Context, i: Int): String =
    AlarmStore.load(ctx).getOrNull(i)?.let { "%02d:%02d".format(it.h, it.m) }
        ?: SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())

/** Đường dự phòng: không bật được dịch vụ chuông thì đăng thông báo có nhạc như trước. */
fun alarmFallbackNotify(ctx: Context, i: Int) {
    val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    val sound = AlarmStore.alarmSound(ctx)
    if (Build.VERSION.SDK_INT >= 26) {
        val ch = NotificationChannel("nokia_alarm_fb", "Báo thức (dự phòng)", NotificationManager.IMPORTANCE_HIGH).apply {
            setSound(sound, AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 600, 400, 600, 400, 600)
        }
        nm.createNotificationChannel(ch)
    }
    val open = PendingIntent.getActivity(ctx, 0, Intent(ctx, MainActivity::class.java),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    @Suppress("DEPRECATION")
    val b = if (Build.VERSION.SDK_INT >= 26) Notification.Builder(ctx, "nokia_alarm_fb")
    else Notification.Builder(ctx).setSound(sound).setPriority(Notification.PRIORITY_HIGH)
    val n = b.setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
        .setContentTitle("Báo thức").setContentText(alarmLabel(ctx, i))
        .setContentIntent(open).setAutoCancel(true).setCategory(Notification.CATEGORY_ALARM).build()
    n.flags = n.flags or Notification.FLAG_INSISTENT
    try { nm.notify(2000 + i.coerceAtLeast(0), n) } catch (_: SecurityException) {}
}

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        val i = intent.getIntExtra("i", -1)          // -1 = báo lại
        AlarmStore.schedule(ctx)                      // đặt lại cho ngày mai
        try {
            ContextCompat.startForegroundService(ctx, Intent(ctx, AlarmService::class.java).putExtra("i", i))
        } catch (_: Exception) { alarmFallbackNotify(ctx, i) }
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) { AlarmStore.schedule(ctx); PowerStore.schedule(ctx) }
}
