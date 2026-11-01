package com.nokia.phone

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.provider.Telephony
import androidx.core.app.NotificationCompat

/** phonecuibap có đang là ứng dụng nhắn tin (SMS) mặc định không. */
fun isDefaultSms(ctx: Context): Boolean = try {
    Telephony.Sms.getDefaultSmsPackage(ctx) == ctx.packageName
} catch (_: Exception) { false }

/**
 * Chỉ được hệ thống gọi khi phonecuibap là ứng dụng SMS mặc định.
 * Khi đó Android KHÔNG tự lưu tin đến nữa -> phải tự ghi vào hộp thư, nếu không tin sẽ mất.
 */
class SmsDeliverReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_DELIVER_ACTION) return
        val msgs = Telephony.Sms.Intents.getMessagesFromIntent(intent)
        if (msgs.isNullOrEmpty()) return
        val addr = msgs[0].displayOriginatingAddress ?: msgs[0].originatingAddress ?: ""
        val body = msgs.joinToString("") { it.displayMessageBody ?: "" }
        val sentAt = msgs[0].timestampMillis
        val sub = intent.getIntExtra("subscription", -1)
        val pending = goAsync()
        Thread {
            try {
                val v = ContentValues().apply {
                    put("address", addr); put("body", body)
                    put("date", System.currentTimeMillis()); put("date_sent", sentAt)
                    put("read", 0); put("seen", 0)
                    if (sub >= 0) put("sub_id", sub)
                }
                try { ctx.contentResolver.insert(Telephony.Sms.Inbox.CONTENT_URI, v) } catch (_: Exception) {}
                notifyNew(ctx, addr, body)
            } finally { pending.finish() }
        }.start()
    }

    private fun notifyNew(ctx: Context, addr: String, body: String) {
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= 26)
            nm.createNotificationChannel(NotificationChannel("nokia_sms", "Tin nhắn", NotificationManager.IMPORTANCE_HIGH))
        val open = PendingIntent.getActivity(ctx, 10,
            Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val n = NotificationCompat.Builder(ctx, "nokia_sms")
            .setSmallIcon(android.R.drawable.sym_action_chat)
            .setContentTitle(addr).setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true).setContentIntent(open)
            .build()
        try { nm.notify((System.currentTimeMillis() % 100000).toInt() + 3000, n) } catch (_: SecurityException) {}
    }
}

/** MMS (tin có ảnh) chưa hỗ trợ; khai báo để đủ điều kiện làm ứng dụng SMS mặc định. */
class MmsDeliverReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {}
}

/** "Trả lời bằng tin nhắn" khi từ chối cuộc gọi. */
class RespondViaMessageService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        try {
            val to = intent?.data?.schemeSpecificPart?.substringBefore('?')?.let { Uri.decode(it) }
            val text = intent?.getStringExtra(Intent.EXTRA_TEXT)
            if (!to.isNullOrEmpty() && !text.isNullOrEmpty()) SmsRepo.send(this, to, text)
        } catch (_: Exception) {}
        stopSelf(startId)
        return START_NOT_STICKY
    }
}
