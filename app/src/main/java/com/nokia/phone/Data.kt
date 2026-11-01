package com.nokia.phone

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.provider.ContactsContract
import android.provider.Telephony
import android.telephony.SmsManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONArray
import org.json.JSONObject
import java.text.Normalizer

/** Bỏ dấu tiếng Việt + chữ thường, dùng để sắp xếp / nhảy theo chữ cái. */
fun plain(s: String): String =
    Normalizer.normalize(s, Normalizer.Form.NFD)
        .replace(Regex("\\p{Mn}+"), "")
        .replace('đ', 'd').replace('Đ', 'D')
        .lowercase()

/**
 * Tìm trong danh bạ bằng MỘT ô duy nhất: gõ vài chữ cái đầu hoặc gõ tên / số bất kỳ.
 * Không phân biệt hoa thường và dấu. Thứ tự kết quả:
 * tên bắt đầu bằng chuỗi gõ > có từ bắt đầu bằng chuỗi gõ > tên chứa chuỗi gõ > số điện thoại chứa chuỗi gõ.
 */
fun filterContacts(all: List<Contact>, query: String): List<Contact> {
    val q = plain(query).trim()
    if (q.isEmpty()) return all
    val numeric = q.all { it.isDigit() || it == '+' }
    val groups = Array(4) { ArrayList<Contact>() }
    for (c in all) {
        val k = c.key
        val r = when {
            k.startsWith(q) -> 0
            k.split(' ', '-', '.', '_', ',').any { it.startsWith(q) } -> 1
            k.contains(q) -> 2
            numeric && numberHas(c.number, q) -> 3
            else -> -1
        }
        if (r >= 0) groups[r].add(c)
    }
    return groups.flatMap { it }
}

/** Số có chứa chuỗi tìm không (0908... khớp cả +84908...). */
private fun numberHas(number: String, q: String): Boolean {
    val n = number.filter { it.isDigit() }
    val d = q.filter { it.isDigit() }
    if (d.isEmpty()) return false
    return n.contains(d) || (d.startsWith("0") && d.length > 1 && n.contains(d.drop(1)))
}

/** Chỉ giữ ký tự quay số được. */
fun dialable(n: String): String = n.filter { it.isDigit() || it == '+' || it == '*' || it == '#' }

// ---------------------------------------------------------------- Danh bạ

data class Contact(val name: String, val number: String, val key: String)

object Contacts {
    fun load(ctx: Context): List<Contact> {
        val out = ArrayList<Contact>()
        val seen = HashSet<String>()
        try {
            ctx.contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                arrayOf(
                    ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                    ContactsContract.CommonDataKinds.Phone.NUMBER
                ), null, null, null
            )?.use { c ->
                while (c.moveToNext()) {
                    val name = c.getString(0)?.trim().orEmpty()
                    val num = dialable(c.getString(1)?.trim().orEmpty())
                    if (num.isEmpty()) continue
                    val shown = name.ifEmpty { num }
                    if (seen.add("$shown|$num")) out.add(Contact(shown, num, plain(shown)))
                }
            }
        } catch (_: Exception) {}
        return out.sortedBy { it.key }
    }
}

// ---------------------------------------------------------------- Tin nhắn

data class Sms(val addr: String, val body: String, val date: Long, val sent: Boolean, val id: Long = -1L)
data class SmsThread(val key: String, val addr: String, val name: String, val last: Sms)

/** Khóa gộp hội thoại: 9 số cuối (để +84.. và 0.. về cùng một người), số/chữ khác thì giữ nguyên. */
fun addrKey(a: String): String {
    val d = a.filter { it.isDigit() }
    return if (d.length >= 7) d.takeLast(9) else a.trim().lowercase()
}

fun buildThreads(all: List<Sms>, contacts: List<Contact>): List<SmsThread> {
    val names = HashMap<String, String>()
    contacts.forEach { names.putIfAbsent(addrKey(it.number), it.name) }
    val map = LinkedHashMap<String, SmsThread>()
    for (s in all) {              // all đã sắp xếp mới -> cũ
        val k = addrKey(s.addr)
        if (!map.containsKey(k)) map[k] = SmsThread(k, s.addr, names[k] ?: s.addr, s)
    }
    return map.values.toList()
}

object SmsRepo {
    private fun prefs(ctx: Context) = ctx.getSharedPreferences("sms_sent", Context.MODE_PRIVATE)

    /** Tin do app này gửi (Android không tự lưu tin của app không phải ứng dụng SMS mặc định). */
    private fun localSent(ctx: Context): List<Sms> = try {
        val a = JSONArray(prefs(ctx).getString("list", "[]"))
        (0 until a.length()).map {
            val o = a.getJSONObject(it)
            Sms(o.getString("a"), o.getString("b"), o.getLong("d"), true)
        }
    } catch (_: Exception) { emptyList() }

    private fun writeLocal(ctx: Context, list: List<Sms>) {
        val a = JSONArray()
        list.take(100).forEach { a.put(JSONObject().put("a", it.addr).put("b", it.body).put("d", it.date)) }
        prefs(ctx).edit().putString("list", a.toString()).apply()
    }

    private fun saveSent(ctx: Context, addr: String, body: String) {
        val cur = localSent(ctx).toMutableList()
        cur.add(0, Sms(addr, body, System.currentTimeMillis(), true))
        writeLocal(ctx, cur)
    }

    /** Là ứng dụng SMS mặc định: phải tự ghi tin đã gửi vào hộp thư hệ thống. */
    private fun storeSent(ctx: Context, addr: String, body: String): Boolean = try {
        val v = ContentValues().apply {
            put("address", addr); put("body", body)
            put("date", System.currentTimeMillis()); put("read", 1)
        }
        ctx.contentResolver.insert(Telephony.Sms.Sent.CONTENT_URI, v) != null
    } catch (_: Exception) { false }

    fun loadAll(ctx: Context): List<Sms> {
        val out = ArrayList<Sms>()
        try {
            ctx.contentResolver.query(
                Telephony.Sms.CONTENT_URI, arrayOf("address", "body", "date", "type", "_id"),
                null, null, "date DESC"
            )?.use { c ->
                var n = 0
                while (c.moveToNext() && n < 3000) {
                    val type = c.getInt(3)
                    if (type == 3) continue          // bản nháp
                    out.add(Sms(c.getString(0) ?: "", c.getString(1) ?: "", c.getLong(2), type != 1, c.getLong(4)))
                    n++
                }
            }
        } catch (_: Exception) {}
        for (l in localSent(ctx)) {
            val dup = out.any {
                it.sent && addrKey(it.addr) == addrKey(l.addr) && it.body == l.body &&
                    Math.abs(it.date - l.date) < 120_000
            }
            if (!dup) out.add(l)
        }
        val hidden = hiddenIds(ctx)
        return out.filter { smsId(it) !in hidden }.sortedByDescending { it.date }
    }

    // ---- Xóa tin ----
    // Là ứng dụng SMS mặc định => xóa thật khỏi hộp thư hệ thống.
    // Chưa là mặc định => Android không cho xóa, chỉ ẩn khỏi Tin nhắn của app này (bản gốc vẫn còn).
    private fun hprefs(ctx: Context) = ctx.getSharedPreferences("sms_hidden", Context.MODE_PRIVATE)
    fun smsId(s: Sms): String = addrKey(s.addr) + "|" + s.date + "|" + s.body.hashCode()
    private fun hiddenIds(ctx: Context): Set<String> = hprefs(ctx).getStringSet("ids", emptySet()) ?: emptySet()
    fun hide(ctx: Context, list: List<Sms>) {
        val cur = HashSet(hiddenIds(ctx))     // bản sao: Set trả về từ SharedPreferences không được sửa trực tiếp
        list.forEach { cur.add(smsId(it)) }
        hprefs(ctx).edit().putStringSet("ids", cur).apply()
    }

    /** Trả về true nếu đã xóa thật khỏi máy, false nếu chỉ ẩn trong app. */
    fun delete(ctx: Context, list: List<Sms>): Boolean {
        if (!isDefaultSms(ctx)) { hide(ctx, list); return false }
        val failed = ArrayList<Sms>()
        val localIds = list.filter { it.id < 0 }.map { smsId(it) }.toSet()
        for (s in list) {
            if (s.id < 0) continue
            val n = try {
                ctx.contentResolver.delete(ContentUris.withAppendedId(Telephony.Sms.CONTENT_URI, s.id), null, null)
            } catch (_: Exception) { 0 }
            if (n <= 0) failed.add(s)
        }
        if (localIds.isNotEmpty()) writeLocal(ctx, localSent(ctx).filter { smsId(it) !in localIds })
        if (failed.isNotEmpty()) hide(ctx, failed)     // xóa hụt thì ít nhất ẩn đi
        return failed.isEmpty()
    }

    @Suppress("DEPRECATION")
    fun send(ctx: Context, to: String, body: String): Boolean = try {
        val sub = Sims.smsSubOrNull(ctx)   // máy 2 SIM: gửi bằng SIM đã chọn trong Cài đặt > SIM
        val sm: SmsManager =
            if (sub != null) {
                if (Build.VERSION.SDK_INT >= 31) ctx.getSystemService(SmsManager::class.java).createForSubscriptionId(sub)
                else SmsManager.getSmsManagerForSubscriptionId(sub)
            } else if (Build.VERSION.SDK_INT >= 31) ctx.getSystemService(SmsManager::class.java) else SmsManager.getDefault()
        val parts = sm.divideMessage(body)
        if (parts.size > 1) sm.sendMultipartTextMessage(to, null, parts, null, null)
        else sm.sendTextMessage(to, null, body, null, null)
        if (!(isDefaultSms(ctx) && storeSent(ctx, to, body))) saveSent(ctx, to, body)
        true
    } catch (_: Exception) { false }
}

// ---------------------------------------------------------------- Bộ gõ multi-tap

private val MULTI = mapOf(
    "1" to "1.,?!'\"-()@/:_", "2" to "abc2", "3" to "def3", "4" to "ghi4", "5" to "jkl5",
    "6" to "mno6", "7" to "pqrs7", "8" to "tuv8", "9" to "wxyz9", "0" to " 0"
)

/** Chữ cái gốc -> các dạng có dấu, phím * xoay vòng qua các dạng này. */
private val VIET = listOf(
    "a" to "àáảãạăằắẳẵặâầấẩẫậ", "e" to "èéẻẽẹêềếểễệ", "i" to "ìíỉĩị",
    "o" to "òóỏõọôồốổỗộơờớởỡợ", "u" to "ùúủũụưừứửữự", "y" to "ỳýỷỹỵ", "d" to "đ"
)

val MODES = listOf("abc", "Abc", "ABC", "123")

class TextEntry {
    var text by mutableStateOf("")
    var mode by mutableIntStateOf(1)          // 0 abc, 1 Abc, 2 ABC, 3 123
    private var lastKey = ""
    private var lastAt = 0L
    private var idx = 0

    fun reset() { text = ""; mode = 1; lastKey = ""; idx = 0 }
    fun backspace() { if (text.isNotEmpty()) text = text.dropLast(1); lastKey = "" }
    fun newline() { text += "\n"; lastKey = "" }

    private fun upper(base: String): Boolean = when (mode) {
        2 -> true
        1 -> {
            val t = base.trimEnd(' ')
            base.isEmpty() || base.endsWith("\n") ||
                (t.length < base.length && (t.endsWith(".") || t.endsWith("!") || t.endsWith("?")))
        }
        else -> false
    }

    fun key(k: String, now: Long) {
        when (k) {
            "#" -> { mode = (mode + 1) % 4; lastKey = "" }
            "*" -> diacritic()
            else -> {
                if (mode == 3) { if (k[0].isDigit()) text += k; lastKey = ""; return }
                val set = MULTI[k] ?: return
                val cycle = k == lastKey && now - lastAt < 1000 && text.isNotEmpty()
                val base = if (cycle) text.dropLast(1) else text
                idx = if (cycle) (idx + 1) % set.length else 0
                val c = set[idx]
                val ch = if (c.isLetter() && upper(base)) c.uppercaseChar() else c
                text = base + ch
                lastKey = k
                lastAt = now
            }
        }
    }

    private fun diacritic() {
        if (text.isEmpty()) return
        val ch = text.last()
        val low = ch.lowercaseChar()
        for ((b, v) in VIET) {
            val all = b + v
            val i = all.indexOf(low)
            if (i >= 0) {
                val n = all[(i + 1) % all.length]
                text = text.dropLast(1) + (if (ch.isUpperCase()) n.uppercaseChar() else n)
                lastKey = ""
                return
            }
        }
    }
}
