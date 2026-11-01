package com.nokia.phone

import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.provider.Settings

/** Hỗ trợ cài đặt âm thanh: nhạc chuông cuộc gọi (hệ thống) và nhạc báo thức (của app). */
object Sound {
    private fun sp(ctx: Context) = ctx.getSharedPreferences("sound", Context.MODE_PRIVATE)

    fun title(ctx: Context, uri: Uri?): String {
        if (uri == null) return "Im lặng"
        return try { RingtoneManager.getRingtone(ctx, uri)?.getTitle(ctx) ?: "Mặc định" } catch (_: Exception) { "Mặc định" }
    }

    private fun currentRingtone(ctx: Context): Uri? =
        try { RingtoneManager.getActualDefaultRingtoneUri(ctx, RingtoneManager.TYPE_RINGTONE) } catch (_: Exception) { null }

    fun ringtoneTitle(ctx: Context): String = title(ctx, currentRingtone(ctx))
    fun alarmTitle(ctx: Context): String = title(ctx, AlarmStore.alarmSound(ctx))

    fun pickerIntent(ctx: Context, type: Int): Intent {
        val existing = if (type == RingtoneManager.TYPE_ALARM) AlarmStore.alarmSound(ctx) else currentRingtone(ctx)
        return Intent(RingtoneManager.ACTION_RINGTONE_PICKER)
            .putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, type)
            .putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
            .putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, true)
            .putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, existing)
    }

    @Suppress("DEPRECATION")
    fun picked(data: Intent?): Uri? =
        if (Build.VERSION.SDK_INT >= 33) data?.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI, Uri::class.java)
        else data?.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)

    /** Đặt nhạc chuông cuộc gọi của hệ thống. Cần quyền "Sửa đổi cài đặt hệ thống"; chưa có thì lưu lại và trả false. */
    fun setRingtone(ctx: Context, uri: Uri?): Boolean {
        if (uri == Settings.System.DEFAULT_RINGTONE_URI) return true
        if (!Settings.System.canWrite(ctx)) {
            sp(ctx).edit().putString("pending_ring", uri?.toString() ?: "").apply()
            return false
        }
        return try { RingtoneManager.setActualDefaultRingtoneUri(ctx, RingtoneManager.TYPE_RINGTONE, uri); true } catch (_: Exception) { false }
    }

    /** Gọi khi quay lại app: nếu vừa cấp quyền thì áp dụng nhạc chuông đã chọn trước đó. */
    fun applyPending(ctx: Context) {
        val p = sp(ctx).getString("pending_ring", null) ?: return
        if (!Settings.System.canWrite(ctx)) return
        try {
            RingtoneManager.setActualDefaultRingtoneUri(ctx, RingtoneManager.TYPE_RINGTONE, if (p.isEmpty()) null else Uri.parse(p))
        } catch (_: Exception) {}
        sp(ctx).edit().remove("pending_ring").apply()
    }
}
