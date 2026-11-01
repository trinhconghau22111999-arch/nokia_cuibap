package com.nokia.phone

import android.Manifest
import android.accounts.AccountManager
import android.app.AlarmManager
import android.app.AlertDialog
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.wifi.ScanResult
import android.net.wifi.WifiConfiguration
import android.net.wifi.WifiManager
import android.net.wifi.WifiNetworkSuggestion
import android.os.BatteryManager
import android.os.Build
import android.os.Environment
import android.os.PowerManager
import android.os.StatFs
import android.os.SystemClock
import android.provider.Settings
import android.telecom.PhoneAccountHandle
import android.telecom.TelecomManager
import android.telephony.SubscriptionInfo
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager
import android.text.InputType
import android.view.WindowManager
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import androidx.core.content.ContextCompat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

/** Các mục trong Menu > Cài đặt (thứ tự hiển thị). */
val SETTINGS = listOf(
    "wallpaper", "sound", "wifi", "sim", "airplane", "hotspot", "brightness",
    "battery", "storage", "accounts", "power", "bt", "reset", "launcher"
)

/** Các màn hình có số liệu thay đổi theo thời gian -> làm tươi mỗi giây. */
val INFO_SCREENS = setOf("settings", "wifi", "sim", "brightness", "battery", "storage", "accounts", "power")

fun permOk(ctx: Context, p: String) = ContextCompat.checkSelfPermission(ctx, p) == PackageManager.PERMISSION_GRANTED

/** Mở màn hình cài đặt của hệ thống; thử lần lượt các action cho tới khi máy mở được một cái. */
fun openSettings(ctx: Context, vararg actions: String) {
    for (a in actions) {
        try {
            ctx.startActivity(Intent(a).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            return
        } catch (_: Exception) {}
    }
}

fun airplaneOn(ctx: Context): Boolean = try {
    Settings.Global.getInt(ctx.contentResolver, Settings.Global.AIRPLANE_MODE_ON, 0) != 0
} catch (_: Exception) { false }

fun fmtDur(ms: Long): String {
    val m = ms / 60_000
    return if (m >= 60) "%dg %02dp".format(m / 60, m % 60) else "${m}p"
}

fun settingLabel(ctx: Context, id: String, btOn: Boolean, dialerOn: Boolean, smsOn: Boolean = false, wallOn: Boolean = false): String = when (id) {
    "wallpaper" -> "Hình nền khóa: " + if (wallOn) "đã chọn ảnh" else "mặc định (chọn ảnh)"
    "launcher" -> "Chọn launcher"
    "sound" -> "Cài đặt âm thanh"
    "wifi" -> "Wifi: " + (if (!Wifi.isOn(ctx)) "TẮT" else Wifi.currentSsid(ctx) ?: "BẬT")
    "sim" -> "SIM"
    "airplane" -> "Chế độ máy bay: " + if (airplaneOn(ctx)) "BẬT" else "TẮT"
    "hotspot" -> "Chia sẻ dữ liệu (phát wifi)"
    "brightness" -> "Độ sáng: " + Bright.pct(Bright.get(ctx)) + "%"
    "battery" -> "Pin: " + Batt.percent(ctx) + "%"
    "storage" -> "Bộ nhớ"
    "accounts" -> "Tài khoản"
    "power" -> "Lịch bật tắt nguồn"
    "bt" -> "Bluetooth: " + if (btOn) "BẬT" else "TẮT"
    "dialer" -> "Ứng dụng gọi: " + if (dialerOn) "Nokia" else "khác (chọn)"
    "smsapp" -> "Ứng dụng nhắn tin: " + if (smsOn) "Nokia" else "khác (chọn)"
    else -> "Khôi phục cài đặt gốc"
}

// ---------------------------------------------------------------- Wifi

data class WifiNet(val ssid: String, val level: Int, val sec: Int, val connected: Boolean)
// sec: 0 = mở, 1 = WEP, 2 = WPA/WPA2, 3 = WPA3, 4 = loại khác (doanh nghiệp...) -> phải nối trong màn hình hệ thống

object Wifi {
    private fun wm(ctx: Context) = ctx.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager

    fun perms(): List<String> = listOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)

    fun isOn(ctx: Context): Boolean = try { wm(ctx).isWifiEnabled } catch (_: Exception) { false }

    @Suppress("DEPRECATION")
    fun currentSsid(ctx: Context): String? = try {
        val s = wm(ctx).connectionInfo?.ssid?.trim('"')
        if (s.isNullOrEmpty() || s == "<unknown ssid>" || s == "0x") null else s
    } catch (_: Exception) { null }

    @Suppress("DEPRECATION")
    fun scan(ctx: Context) { try { wm(ctx).startScan() } catch (_: Exception) {} }

    private fun secOf(c: String): Int = when {
        c.contains("EAP") || c.contains("OWE") -> 4
        c.contains("SAE") && !c.contains("PSK") -> 3
        c.contains("PSK") || c.contains("WPA") || c.contains("RSN") -> 2
        c.contains("WEP") -> 1
        else -> 0
    }

    @Suppress("DEPRECATION")
    fun nets(ctx: Context): List<WifiNet> {
        if (!permOk(ctx, Manifest.permission.ACCESS_FINE_LOCATION)) return emptyList()
        val cur = currentSsid(ctx)
        val res: List<ScanResult> = try { wm(ctx).scanResults ?: emptyList() } catch (_: Exception) { emptyList() }
        return res.filter { !it.SSID.isNullOrEmpty() }
            .groupBy { it.SSID }
            .mapNotNull { (ssid, l) ->
                val best = l.maxByOrNull { it.level } ?: return@mapNotNull null
                WifiNet(ssid, WifiManager.calculateSignalLevel(best.level, 5), secOf(best.capabilities ?: ""), ssid == cur)
            }
            .sortedWith(compareByDescending<WifiNet> { it.connected }.thenByDescending { it.level })
    }

    /** true = đã tự bật/tắt được; false = Android 10+ không cho, đã mở bảng wifi của hệ thống. */
    @Suppress("DEPRECATION")
    fun toggle(ctx: Context): Boolean {
        if (Build.VERSION.SDK_INT < 29) {
            return try { val w = wm(ctx); w.isWifiEnabled = !w.isWifiEnabled; true } catch (_: Exception) { false }
        }
        openSettings(ctx, Settings.Panel.ACTION_WIFI, Settings.ACTION_WIFI_SETTINGS)
        return false
    }

    /**
     * Lưu + kết nối wifi. Trả về (Intent cần chạy bằng launcher, thông báo).
     * Android 11+: dùng hộp thoại lưu mạng của hệ thống (app thường không được tự nối wifi).
     */
    @Suppress("DEPRECATION")
    fun connect(ctx: Context, n: WifiNet, pass: String): Pair<Intent?, String> {
        if (n.sec == 1 || n.sec == 4) {
            openSettings(ctx, Settings.ACTION_WIFI_SETTINGS)
            return null to "Loại wifi này phải nối trong màn hình hệ thống"
        }
        if (n.sec == 2 && pass.length < 8) return null to "Mật khẩu tối thiểu 8 ký tự"
        if (n.sec == 3 && pass.isEmpty()) return null to "Chưa nhập mật khẩu"
        if (Build.VERSION.SDK_INT >= 29) {
            val b = WifiNetworkSuggestion.Builder().setSsid(n.ssid)
            if (n.sec == 3) b.setWpa3Passphrase(pass) else if (n.sec == 2) b.setWpa2Passphrase(pass)
            val sug = b.build()
            if (Build.VERSION.SDK_INT >= 30) {
                val i = Intent(Settings.ACTION_WIFI_ADD_NETWORKS)
                    .putParcelableArrayListExtra(Settings.EXTRA_WIFI_NETWORK_LIST, arrayListOf(sug))
                return i to ""
            }
            val st = try { wm(ctx).addNetworkSuggestions(listOf(sug)) } catch (_: Exception) { -1 }
            return null to (if (st == WifiManager.STATUS_NETWORK_SUGGESTIONS_SUCCESS ||
                st == WifiManager.STATUS_NETWORK_SUGGESTIONS_ERROR_ADD_DUPLICATE)
                "Đã lưu, hãy bấm Cho phép ở thông báo wifi của máy" else "Không lưu được wifi")
        }
        return try {
            val w = wm(ctx)
            val c = WifiConfiguration()
            c.SSID = "\"" + n.ssid + "\""
            if (n.sec == 0) c.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.NONE)
            else c.preSharedKey = "\"" + pass + "\""
            val id = w.addNetwork(c)
            if (id < 0) null to "Không lưu được wifi"
            else { w.disconnect(); w.enableNetwork(id, true); w.reconnect(); null to "Đang kết nối..." }
        } catch (_: Exception) { null to "Không kết nối được" }
    }

    /** Hộp nhập mật khẩu: dùng bàn phím ảo của máy (EditText thật, tự bật bàn phím). */
    fun askPassword(ctx: Context, ssid: String, onOk: (String) -> Unit) {
        val et = EditText(ctx)
        et.setSingleLine()
        et.hint = "Mật khẩu"
        et.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        val show = CheckBox(ctx)
        show.text = "Hiện mật khẩu"
        show.setOnCheckedChangeListener { _, on ->
            et.inputType = InputType.TYPE_CLASS_TEXT or
                (if (on) InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD else InputType.TYPE_TEXT_VARIATION_PASSWORD)
            et.setSelection(et.text.length)
        }
        val box = LinearLayout(ctx)
        box.orientation = LinearLayout.VERTICAL
        val p = (20 * ctx.resources.displayMetrics.density).toInt()
        box.setPadding(p, p / 2, p, 0)
        box.addView(et)
        box.addView(show)
        val dlg = AlertDialog.Builder(ctx).setTitle("Wifi: $ssid").setView(box)
            .setPositiveButton("Kết nối") { _, _ -> onOk(et.text.toString()) }
            .setNegativeButton("Hủy", null)
            .create()
        dlg.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)
        dlg.show()
        et.requestFocus()
    }
}

fun wifiLines(ctx: Context, nets: List<WifiNet>): List<String> =
    listOf("Wifi: " + if (Wifi.isOn(ctx)) "BẬT" else "TẮT") +
        nets.map {
            (if (it.connected) "✓" else " ") + "▂▄▆█".take(it.level.coerceIn(0, 4)).padEnd(4) +
                (if (it.sec > 0) "🔒" else "  ") + it.ssid
        }

// ---------------------------------------------------------------- SIM

data class SimInfo(val subId: Int, val slot: Int, val carrier: String, val number: String, val net: String)

object Sims {
    private fun prefs(ctx: Context) = ctx.getSharedPreferences("sim", Context.MODE_PRIVATE)

    fun perms(): List<String> =
        if (Build.VERSION.SDK_INT >= 26) listOf(Manifest.permission.READ_PHONE_STATE, Manifest.permission.READ_PHONE_NUMBERS)
        else listOf(Manifest.permission.READ_PHONE_STATE)

    private fun netName(t: Int): String = when (t) {
        20 -> "5G"
        19 -> "4G+"
        13 -> "4G"
        3, 5, 6, 8, 9, 10, 12, 14, 15, 17 -> "3G"
        1, 2, 4, 7, 11, 16 -> "2G"
        18 -> "Wifi"
        else -> "Không sóng"
    }

    @Suppress("DEPRECATION")
    private fun number(sm: SubscriptionManager, s: SubscriptionInfo): String {
        var n = s.number ?: ""
        if (n.isEmpty() && Build.VERSION.SDK_INT >= 33) n = try { sm.getPhoneNumber(s.subscriptionId) } catch (_: Exception) { "" }
        return n
    }

    fun defVoice(): Int = SubscriptionManager.getDefaultVoiceSubscriptionId()
    fun defSms(): Int = SubscriptionManager.getDefaultSmsSubscriptionId()
    fun defData(): Int = SubscriptionManager.getDefaultDataSubscriptionId()

    fun list(ctx: Context): List<SimInfo> {
        if (!permOk(ctx, Manifest.permission.READ_PHONE_STATE)) return emptyList()
        return try {
            val sm = ctx.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as SubscriptionManager
            val tm = ctx.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
            val dataSub = defData()
            (sm.activeSubscriptionInfoList ?: emptyList()).sortedBy { it.simSlotIndex }.map { s ->
                val t = tm.createForSubscriptionId(s.subscriptionId)
                val type = try {
                    val d = t.dataNetworkType
                    val v = t.voiceNetworkType
                    if (s.subscriptionId == dataSub && d != 0) d else if (v != 0) v else d
                } catch (_: Exception) { 0 }
                SimInfo(s.subscriptionId, s.simSlotIndex + 1, s.carrierName?.toString() ?: "SIM", number(sm, s), netName(type))
            }
        } catch (_: Exception) { emptyList() }
    }

    /** SIM dùng để gọi / nhắn tin trong app này: theo lựa chọn của người dùng, chưa chọn thì theo mặc định của máy. */
    private fun chosen(ctx: Context, key: String, sims: List<SimInfo>, def: Int): Int {
        val v = prefs(ctx).getInt(key, -1)
        return if (sims.any { it.subId == v }) v else def
    }
    fun callSub(ctx: Context, sims: List<SimInfo>) = chosen(ctx, "call", sims, defVoice())
    fun smsSub(ctx: Context, sims: List<SimInfo>) = chosen(ctx, "sms", sims, defSms())

    /** Bấm để chuyển sang SIM kế tiếp. */
    fun cycle(ctx: Context, key: String, sims: List<SimInfo>, current: Int) {
        if (sims.size < 2) return
        val i = sims.indexOfFirst { it.subId == current }
        prefs(ctx).edit().putInt(key, sims[(i + 1) % sims.size].subId).apply()
    }

    fun label(sims: List<SimInfo>, id: Int): String =
        sims.firstOrNull { it.subId == id }?.let { "SIM ${it.slot}" } ?: "mặc định"

    fun dataEnabled(ctx: Context): Boolean? = try {
        if (Build.VERSION.SDK_INT < 26 || !permOk(ctx, Manifest.permission.READ_PHONE_STATE)) null
        else (ctx.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager).isDataEnabled
    } catch (_: Exception) { null }

    /** SIM để nhắn tin khi máy có từ 2 SIM trở lên; 1 SIM / chưa cấp quyền thì null (dùng mặc định). */
    fun smsSubOrNull(ctx: Context): Int? {
        val sims = list(ctx)
        return if (sims.size < 2) null else smsSub(ctx, sims)
    }

    /** Tài khoản gọi (PhoneAccountHandle) của SIM đã chọn để gọi; null = để hệ thống tự chọn. */
    @Suppress("DEPRECATION")
    fun callHandle(ctx: Context): PhoneAccountHandle? {
        val sims = list(ctx)
        if (sims.size < 2) return null
        val sub = callSub(ctx, sims)
        return try {
            val tc = ctx.getSystemService(Context.TELECOM_SERVICE) as TelecomManager
            val tm = ctx.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
            val sm = ctx.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as SubscriptionManager
            val icc = if (Build.VERSION.SDK_INT < 29) sm.getActiveSubscriptionInfo(sub)?.iccId else null
            tc.callCapablePhoneAccounts.firstOrNull { h ->
                (Build.VERSION.SDK_INT >= 30 &&
                    try { tm.createForPhoneAccountHandle(h)?.subscriptionId == sub } catch (_: Exception) { false }) ||
                    (icc != null && icc.isNotEmpty() && icc == h.id)
            }
        } catch (_: Exception) { null }
    }
}

/** Các dòng của màn hình SIM: (chữ hiển thị, mã hành động). */
fun simRows(ctx: Context): List<Pair<String, String>> {
    if (!permOk(ctx, Manifest.permission.READ_PHONE_STATE)) return listOf("Cần quyền Điện thoại (OK để cấp)" to "perm")
    val sims = Sims.list(ctx)
    if (sims.isEmpty()) return listOf("Không có SIM" to "info")
    val rows = mutableListOf<Pair<String, String>>()
    for (s in sims) {
        rows.add("SIM ${s.slot}: ${s.carrier}" to "info")
        rows.add("  Mạng: ${s.net}" to "info")
        rows.add("  Số: ${s.number.ifEmpty { "(chưa có)" }}" to "info")
    }
    rows.add("Gọi bằng: " + Sims.label(sims, Sims.callSub(ctx, sims)) to "call")
    rows.add("Nhắn tin bằng: " + Sims.label(sims, Sims.smsSub(ctx, sims)) to "sms")
    rows.add("Dữ liệu: " + Sims.label(sims, Sims.defData()) to "data")
    val on = Sims.dataEnabled(ctx)
    rows.add("Dữ liệu di động: " + (if (on == null) "mở" else if (on) "BẬT" else "TẮT") to "mobile")
    return rows
}

// ---------------------------------------------------------------- Độ sáng

object Bright {
    fun get(ctx: Context): Int = try {
        Settings.System.getInt(ctx.contentResolver, Settings.System.SCREEN_BRIGHTNESS)
    } catch (_: Exception) { 128 }

    fun auto(ctx: Context): Boolean = try {
        Settings.System.getInt(ctx.contentResolver, Settings.System.SCREEN_BRIGHTNESS_MODE) ==
            Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC
    } catch (_: Exception) { false }

    fun canWrite(ctx: Context) = Settings.System.canWrite(ctx)

    fun set(ctx: Context, v: Int): Boolean = try {
        Settings.System.putInt(ctx.contentResolver, Settings.System.SCREEN_BRIGHTNESS_MODE,
            Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL)
        Settings.System.putInt(ctx.contentResolver, Settings.System.SCREEN_BRIGHTNESS, v.coerceIn(1, 255))
    } catch (_: Exception) { false }

    fun setAuto(ctx: Context, on: Boolean): Boolean = try {
        Settings.System.putInt(ctx.contentResolver, Settings.System.SCREEN_BRIGHTNESS_MODE,
            if (on) Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC else Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL)
    } catch (_: Exception) { false }

    fun pct(v: Int) = (v.coerceIn(0, 255) * 100f / 255f).roundToInt()

    fun bar(v: Int): String {
        val f = Math.round(v.coerceIn(0, 255) * 8f / 255f)
        return "█".repeat(f) + "░".repeat(8 - f)
    }
}

// ---------------------------------------------------------------- Pin, thời gian dùng, bộ nhớ, tài khoản

object Batt {
    private fun sticky(ctx: Context): Intent? = ctx.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))

    fun percent(ctx: Context): Int {
        val i = sticky(ctx) ?: return 0
        val l = i.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val s = i.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
        return if (l < 0 || s <= 0) 0 else l * 100 / s
    }

    fun charging(ctx: Context): Boolean {
        val st = sticky(ctx)?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        return st == BatteryManager.BATTERY_STATUS_CHARGING || st == BatteryManager.BATTERY_STATUS_FULL
    }

    fun saverOn(ctx: Context): Boolean = try {
        (ctx.getSystemService(Context.POWER_SERVICE) as PowerManager).isPowerSaveMode
    } catch (_: Exception) { false }
}

/** Thời gian dùng app này trong ngày (tính lúc MainActivity đang hiện trên màn hình). */
object UsageTracker {
    private var startedAt = 0L
    private fun day() = SimpleDateFormat("yyyyMMdd", Locale.US).format(Date())
    private fun prefs(ctx: Context) = ctx.getSharedPreferences("usage", Context.MODE_PRIVATE)

    fun resume() { if (startedAt == 0L) startedAt = SystemClock.elapsedRealtime() }

    fun pause(ctx: Context) {
        if (startedAt == 0L) return
        val add = SystemClock.elapsedRealtime() - startedAt
        startedAt = 0L
        val p = prefs(ctx)
        val d = day()
        val cur = if (p.getString("day", "") == d) p.getLong("ms", 0L) else 0L
        p.edit().putString("day", d).putLong("ms", cur + add).apply()
    }

    fun todayMs(ctx: Context): Long {
        val p = prefs(ctx)
        val saved = if (p.getString("day", "") == day()) p.getLong("ms", 0L) else 0L
        val live = if (startedAt != 0L) SystemClock.elapsedRealtime() - startedAt else 0L
        return saved + live
    }
}

object Store {
    fun gb(b: Long): String = String.format(Locale.getDefault(), "%.1f GB", b / 1e9)

    /** (tổng, còn trống) của bộ nhớ trong. */
    fun info(): Pair<Long, Long> = try {
        val s = StatFs(Environment.getDataDirectory().path)
        s.totalBytes to s.availableBytes
    } catch (_: Exception) { 0L to 0L }
}

object Accts {
    fun google(ctx: Context): List<String> {
        if (!permOk(ctx, Manifest.permission.GET_ACCOUNTS)) return emptyList()
        return try { AccountManager.get(ctx).getAccountsByType("com.google").map { it.name } } catch (_: Exception) { emptyList() }
    }
}

// ---------------------------------------------------------------- Lịch bật tắt nguồn

data class PowerSched(val offH: Int, val offM: Int, val offOn: Boolean, val onH: Int, val onM: Int, val onOn: Boolean)

/**
 * Android không cho app thường tắt/bật nguồn máy, nên "tắt" = khóa & tắt màn hình (qua dịch vụ Trợ năng),
 * "bật" = bật sáng màn hình.
 */
object PowerStore {
    private fun prefs(ctx: Context) = ctx.getSharedPreferences("power", Context.MODE_PRIVATE)

    fun load(ctx: Context): PowerSched = prefs(ctx).let {
        PowerSched(it.getInt("offH", 23), it.getInt("offM", 0), it.getBoolean("offOn", false),
            it.getInt("onH", 6), it.getInt("onM", 0), it.getBoolean("onOn", false))
    }

    fun save(ctx: Context, s: PowerSched) {
        prefs(ctx).edit().putInt("offH", s.offH).putInt("offM", s.offM).putBoolean("offOn", s.offOn)
            .putInt("onH", s.onH).putInt("onM", s.onM).putBoolean("onOn", s.onOn).apply()
    }

    private fun pi(ctx: Context, code: Int, act: String): PendingIntent = PendingIntent.getBroadcast(
        ctx, code, Intent(ctx, PowerReceiver::class.java).putExtra("act", act),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

    private fun setAt(am: AlarmManager, t: Long, p: PendingIntent) {
        try {
            if (Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms())
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, t, p)
            else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, t, p)
        } catch (_: SecurityException) {}
    }

    /** Hủy lịch cũ rồi đặt lại cho lần kế tiếp (lặp mỗi ngày). */
    fun schedule(ctx: Context, s: PowerSched = load(ctx)) {
        val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        am.cancel(pi(ctx, 300, "off"))
        am.cancel(pi(ctx, 301, "on"))
        if (s.offOn) setAt(am, AlarmStore.nextTrigger(s.offH, s.offM), pi(ctx, 300, "off"))
        if (s.onOn) setAt(am, AlarmStore.nextTrigger(s.onH, s.onM), pi(ctx, 301, "on"))
    }
}

class PowerReceiver : BroadcastReceiver() {
    @Suppress("DEPRECATION")
    override fun onReceive(ctx: Context, intent: Intent) {
        PowerStore.schedule(ctx)   // đặt lại cho ngày mai
        when (intent.getStringExtra("act")) {
            "off" -> NokiaAccessibilityService.instance?.lockScreen()
            "on" -> try {
                val pm = ctx.getSystemService(Context.POWER_SERVICE) as PowerManager
                pm.newWakeLock(PowerManager.FULL_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP or PowerManager.ON_AFTER_RELEASE,
                    "nokia:power").acquire(5000)
            } catch (_: Exception) {}
        }
    }
}
