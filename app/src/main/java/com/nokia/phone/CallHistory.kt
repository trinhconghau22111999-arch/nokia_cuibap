package com.nokia.phone

import android.content.Context
import android.provider.CallLog
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Một dòng trong nhật ký cuộc gọi. kind = CallHistory.IN / OUT / MISSED. */
data class CallEntry(val name: String, val number: String, val kind: Int, val date: Long, val durSec: Long) {
    /** Tên trong danh bạ; không có thì hiện số; số ẩn thì hiện "Số ẩn". */
    val label: String get() = name.ifEmpty { number.ifEmpty { "Số ẩn" } }
}

/**
 * Nhật ký cuộc gọi dồn chung: gọi đến, gọi đi và cuộc gọi nhỡ nằm trong một danh sách (mới nhất ở trên).
 * Đọc từ nhật ký của hệ thống nên cần quyền READ_CALL_LOG.
 */
object CallHistory {
    const val IN = 1
    const val OUT = 2
    const val MISSED = 3

    fun load(ctx: Context, limit: Int = 300): List<CallEntry> {
        val out = ArrayList<CallEntry>()
        try {
            ctx.contentResolver.query(
                CallLog.Calls.CONTENT_URI,
                arrayOf(
                    CallLog.Calls.NUMBER, CallLog.Calls.CACHED_NAME, CallLog.Calls.TYPE,
                    CallLog.Calls.DATE, CallLog.Calls.DURATION
                ), null, null, CallLog.Calls.DATE + " DESC"
            )?.use { c ->
                while (c.moveToNext() && out.size < limit) {
                    val kind = when (c.getInt(2)) {
                        CallLog.Calls.OUTGOING_TYPE -> OUT
                        CallLog.Calls.MISSED_TYPE, CallLog.Calls.REJECTED_TYPE, CallLog.Calls.BLOCKED_TYPE -> MISSED
                        else -> IN
                    }
                    val raw = c.getString(0)?.trim().orEmpty()
                    // Máy cũ lưu số ẩn là "-1", "-2", "-3"
                    val number = if (raw.startsWith("-")) "" else dialable(raw)
                    val name = c.getString(1)?.trim().orEmpty()
                    out.add(CallEntry(name, number, kind, c.getLong(3), c.getLong(4)))
                }
            }
        } catch (_: Exception) {}
        return out
    }

    /** Dòng trong danh sách: "Gọi đến:", "Gọi đi:", "Gọi nhỡ:" (dùng chữ vì phông máy không vẽ được mũi tên). */
    fun line(e: CallEntry): String =
        (when (e.kind) { OUT -> "Gọi đi: "; MISSED -> "Gọi nhỡ: "; else -> "Gọi đến: " }) + e.label

    /** Dòng chi tiết của mục đang chọn, gộp một dòng: loại, số (khi dòng trên hiện tên), ngày giờ, thời lượng. */
    fun detail(e: CallEntry): String {
        val kind = when (e.kind) { OUT -> "Đi"; MISSED -> "Nhỡ"; else -> "Đến" }
        val at = SimpleDateFormat("dd/MM HH:mm", Locale.getDefault()).format(Date(e.date))
        val num = if (e.name.isNotEmpty() && e.name != e.number && e.number.isNotEmpty()) " " + e.number else ""
        return kind + num + "  " + at + if (e.durSec > 0) "  " + mmss(e.durSec * 1000) + "s" else ""
    }
}
