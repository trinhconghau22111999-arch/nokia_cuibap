package com.nokia.phone

import android.Manifest
import android.app.Activity
import android.app.ActivityManager
import android.app.role.RoleManager
import android.telecom.TelecomManager
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.IntentFilter
import android.media.AudioManager
import android.media.RingtoneManager
import android.os.SystemClock
import androidx.compose.ui.graphics.Path
import android.app.ActivityOptions
import android.graphics.Rect
import android.content.Intent
import android.content.res.Configuration
import android.os.Handler
import android.os.Looper
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import android.widget.Toast
import android.provider.ContactsContract
import android.provider.Settings
import android.provider.Telephony
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.compose.ui.viewinterop.AndroidView
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.activity.compose.setContent
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.MarqueeAnimationMode
import androidx.compose.foundation.MarqueeSpacing
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.key.*
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import kotlin.math.abs
import kotlin.math.roundToInt
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

import android.graphics.Bitmap
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

val LCD = Color(0xFFC7F0D8)
val INK = Color(0xFF1B2B1B)
val KEY = Color(0xFFD5DAE3)
val KEYTXT = Color(0xFF1B2230)
val MONO = FontFamily.Monospace

val MENU = listOf(
    "Nhật ký" to "calllog", "Danh bạ" to "contacts", "Tin nhắn" to "messages",
    "Máy ảnh" to "camera", "Thư viện" to "gallery", "Nhạc" to "music",
    "Đồng hồ" to "clock", "Lịch" to "calendar", "Ghi âm" to "recorder",
    "Máy tính" to "calc", "Rắn săn mồi" to "snake", "Zalo Web" to "zaloweb", "TV" to "tv", "YouTube" to "youtube",
    "Cài đặt" to "settings"
)
val LETTERS = mapOf("2" to "ABC", "3" to "DEF", "4" to "GHI", "5" to "JKL",
    "6" to "MNO", "7" to "PQRS", "8" to "TUV", "9" to "WXYZ", "*" to "+", "0" to "_", "#" to "⇧")

data class AppInfo(val label: String, val pkg: String)

/** Tăng mỗi khi người dùng bấm nút Home (launcher chạy lại) -> về màn hình chờ. */
val homeTick = mutableIntStateOf(0)

/** true khi MainActivity (màn hình LCD) đang nằm trong chế độ chia đôi màn hình. */
val inSplit = mutableStateOf(false)

class Snake {
    val w = 10; val h = 14
    var body by mutableStateOf(listOf(5 to 7, 4 to 7, 3 to 7))
    var dir by mutableStateOf(1 to 0)
    var food by mutableStateOf(8 to 3)
    var dead by mutableStateOf(false)
    var score by mutableIntStateOf(0)
    private var lastDir = 1 to 0   // hướng của bước đã thực hiện gần nhất (để chặn quay đầu 180° khi bấm nhanh)
    fun reset() { body = listOf(5 to 7, 4 to 7, 3 to 7); dir = 1 to 0; lastDir = 1 to 0; dead = false; score = 0 }
    /**
     * Thời gian giữa hai bước (ms). MỘT đường cong duy nhất, nhanh dần đều theo từng mồi (~4,3%/mồi),
     * không có bước nhảy ở bất kỳ cấp nào: 850ms lúc đầu -> ~550ms (10 mồi) -> ~230ms (30 mồi)
     * -> ~95ms (50 mồi) -> chạm mức nhanh nhất 60ms ở khoảng 60 mồi.
     */
    fun stepMs(): Long = maxOf(60.0, 850.0 * Math.pow(0.9568, score.toDouble())).toLong()
    // Chỉ cấm quay ngược so với hướng của bước ĐÃ chạy, không phải hướng vừa bấm (bấm 2 phím nhanh trong 1 bước không còn tự cắn mình)
    fun turn(d: Pair<Int, Int>) { if (d.first != -lastDir.first || d.second != -lastDir.second) dir = d }
    fun step() {
        if (dead) return
        val hd = body[0]
        val n = ((hd.first + dir.first + w) % w) to ((hd.second + dir.second + h) % h)
        lastDir = dir
        val grow = n == food
        // Đuôi sẽ rời đi trong bước này (trừ khi ăn mồi) nên đầu được phép vào ô đuôi
        if (n in (if (grow) body else body.dropLast(1))) { dead = true; return }
        if (grow) {
            score++
            body = listOf(n) + body
            var f: Pair<Int, Int>
            do { f = (0 until w).random() to (0 until h).random() } while (f in body)
            food = f
        } else body = listOf(n) + body.dropLast(1)
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        if (Build.VERSION.SDK_INT >= 28) {
            window.attributes.layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
        @Suppress("DEPRECATION")
        window.statusBarColor = android.graphics.Color.TRANSPARENT
        hideBars()
        inSplit.value = isInMultiWindowMode
        takeDial(intent)
        setContent { Phone() }
    }

    /** ACTION_DIAL / tel:... từ app khác -> điền số vào màn hình chờ. */
    private fun takeDial(i: Intent?) {
        val u = i?.data ?: return
        if (u.scheme == "tel" && (i.action == Intent.ACTION_DIAL || i.action == Intent.ACTION_VIEW))
            NokiaState.pendingDial = dialable(Uri.decode(u.schemeSpecificPart))
        // SENDTO sms:/smsto:... từ app khác -> mở màn hình soạn tin tới số đó
        if (i.action == Intent.ACTION_SENDTO && (u.scheme == "sms" || u.scheme == "smsto" || u.scheme == "mms" || u.scheme == "mmsto"))
            NokiaState.pendingSms = dialable(Uri.decode(u.schemeSpecificPart.substringBefore('?')))
    }

    private fun hideBars() {
        val c = WindowInsetsControllerCompat(window, window.decorView)
        c.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        c.hide(WindowInsetsCompat.Type.navigationBars())
        c.show(WindowInsetsCompat.Type.statusBars())
        c.isAppearanceLightStatusBars = true // nền LCD sáng -> icon tối
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideBars()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.getBooleanExtra("menu", false)) NokiaState.openMenu = true
        takeDial(intent)
        homeTick.intValue++
    }

    override fun onResume() {
        super.onResume()
        NokiaState.lcdResumed = true
        UsageTracker.resume()
        Sound.applyPending(this)
        NokiaAccessibilityService.instance?.hideCursor()
        if (inSplit.value) ensureKeypad() else autoSplit()
    }

    override fun onPause() {
        NokiaState.lcdResumed = false
        UsageTracker.pause(this)
        super.onPause()
    }

    override fun onMultiWindowModeChanged(isInMultiWindowMode: Boolean, newConfig: Configuration) {
        super.onMultiWindowModeChanged(isInMultiWindowMode, newConfig)
        inSplit.value = isInMultiWindowMode
        if (isInMultiWindowMode) ensureKeypad() else {
            NokiaState.keypad?.finish()
            NokiaAccessibilityService.instance?.hideCursor()
        }
    }

    /** Vào app lần đầu: nếu đã bật dịch vụ Trợ năng thì tự chia đôi màn hình. */
    private fun autoSplit() {
        val svc = NokiaAccessibilityService.instance ?: return
        if (NokiaState.splitTried) return
        NokiaState.splitTried = true
        Handler(Looper.getMainLooper()).postDelayed({ svc.toggleSplit() }, 500)
    }

    /** Đã chia đôi mà chưa có bàn phím -> mở bàn phím Nokia ở nửa còn lại (nửa dưới). */
    private fun ensureKeypad() {
        if (NokiaState.keypadAlive) return
        Handler(Looper.getMainLooper()).postDelayed({
            if (inSplit.value && !NokiaState.keypadAlive) {
                try {
                    startActivity(Intent(this, KeypadActivity::class.java).addFlags(
                        Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_LAUNCH_ADJACENT or Intent.FLAG_ACTIVITY_MULTIPLE_TASK))
                } catch (_: Exception) {}
            }
        }, 700)
    }
}

fun dateNow(): String = SimpleDateFormat("EEEE, dd/MM/yyyy", Locale.getDefault()).format(Date())

fun timeNow(): String = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())

/** Lịch tháng (tuần bắt đầu từ thứ 2), mỗi ô có ngày dương và ngày âm bên dưới. offset = số tháng lệch so với tháng hiện tại. */
@Composable
fun MonthCalendar(offset: Int, onStep: (Int) -> Unit = {}) {
    val first = remember(offset) {
        Calendar.getInstance().apply { set(Calendar.DAY_OF_MONTH, 1); add(Calendar.MONTH, offset) }
    }
    val today = Calendar.getInstance()
    val year = first.get(Calendar.YEAR)
    val month = first.get(Calendar.MONTH) + 1
    val sameMonth = year == today.get(Calendar.YEAR) && month == today.get(Calendar.MONTH) + 1
    val days = first.getActualMaximum(Calendar.DAY_OF_MONTH)
    val lead = (first.get(Calendar.DAY_OF_WEEK) + 5) % 7
    val rows = (lead + days + 6) / 7
    val lunar = remember(offset) { (1..days).map { Lunar.fromSolar(it, month, year) } }
    val footer = if (sameMonth) lunar[today.get(Calendar.DAY_OF_MONTH) - 1].let {
        "Hôm nay âm lịch: ${it.day}/${it.month}${if (it.leap) " (nhuận)" else ""} năm ${Lunar.canChi(it.year)}"
    } else "Năm ${Lunar.canChi(lunar[14].year)}"
    Column(Modifier.fillMaxSize()) {
        // ◀ ▶ bấm cảm ứng được để chuyển tháng
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("◀", Modifier.clickable { onStep(-1) }.padding(horizontal = 16.dp, vertical = 4.dp),
                color = INK, fontFamily = MONO, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Text("Tháng $month/$year", Modifier.weight(1f), textAlign = TextAlign.Center, maxLines = 1,
                color = INK, fontFamily = MONO, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Text("▶", Modifier.clickable { onStep(1) }.padding(horizontal = 16.dp, vertical = 4.dp),
                color = INK, fontFamily = MONO, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        }
        Row(Modifier.fillMaxWidth()) {
            listOf("T2", "T3", "T4", "T5", "T6", "T7", "CN").forEach {
                Text(it, Modifier.weight(1f), textAlign = TextAlign.Center, color = INK,
                    fontFamily = MONO, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }
        }
        for (r in 0 until rows) {
            Row(Modifier.fillMaxWidth().weight(1f)) {
                for (c in 0 until 7) {
                    val d = r * 7 + c - lead + 1
                    val isToday = sameMonth && d == today.get(Calendar.DAY_OF_MONTH)
                    val fg = if (isToday) LCD else INK
                    Box(Modifier.weight(1f).fillMaxHeight().padding(1.dp)
                        .background(if (isToday) INK else Color.Transparent),
                        contentAlignment = Alignment.Center) {
                        if (d in 1..days) {
                            val l = lunar[d - 1]
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(d.toString(), color = fg, fontFamily = MONO, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                                Text(if (l.day == 1) "1/${l.month}" else l.day.toString(), color = fg,
                                    fontFamily = MONO, fontSize = 10.sp, maxLines = 1,
                                    fontWeight = if (l.day == 1) FontWeight.Bold else FontWeight.Normal)
                            }
                        }
                    }
                }
            }
        }
        Text(footer, Modifier.fillMaxWidth().padding(vertical = 3.dp), textAlign = TextAlign.Center,
            color = INK, fontFamily = MONO, fontWeight = FontWeight.Bold, fontSize = 11.sp, maxLines = 2)
    }
}

@Composable
fun FieldBox(text: String, selected: Boolean, size: Int, onClick: () -> Unit) {
    Text(text, Modifier.clickable(onClick = onClick)
        .background(if (selected) INK else LCD).padding(horizontal = 10.dp, vertical = 4.dp),
        color = if (selected) LCD else INK, fontFamily = MONO, fontWeight = FontWeight.Bold, fontSize = size.sp)
}

val VOL = listOf(
    "Nhạc chuông" to AudioManager.STREAM_RING,
    "Báo thức" to AudioManager.STREAM_ALARM,
    "Phương tiện" to AudioManager.STREAM_MUSIC
)

fun volLine(ctx: Context, name: String, stream: Int): String {
    val am = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    val max = maxOf(am.getStreamMaxVolume(stream), 1)
    val cur = am.getStreamVolume(stream)
    val f = Math.round(cur * 10f / max)
    return name.padEnd(12) + "█".repeat(f) + "░".repeat(10 - f)
}

fun isDefaultDialer(ctx: Context): Boolean = try {
    (ctx.getSystemService(Context.TELECOM_SERVICE) as TelecomManager).defaultDialerPackage == ctx.packageName
} catch (_: Exception) { false }

fun isBtOn(ctx: Context): Boolean = try {
    ((ctx.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter)?.isEnabled == true
} catch (_: Exception) { false }

/** Đồng hồ, ngày và dòng hướng dẫn mở khóa (dùng chung cho màn hình khóa và khung xem thử hình nền). */
@Composable
fun BoxScope.LockTexts(time: String, date: String, armed: Boolean, onPhoto: Boolean = false) {
    // Có ảnh nền do người dùng chọn -> chữ trắng (kèm bóng mờ nhẹ cho dễ đọc); nền mặc định -> chữ đen như cũ
    val c = if (onPhoto) Color.White else INK
    val sh = if (onPhoto) androidx.compose.ui.text.TextStyle(
        shadow = androidx.compose.ui.graphics.Shadow(Color.Black.copy(alpha = 0.55f), Offset(0f, 2f), 6f)) else androidx.compose.ui.text.TextStyle.Default
    Column(Modifier.fillMaxSize().statusBarsPadding().padding(top = 36.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(time, color = c, style = sh, fontSize = 70.sp, fontWeight = FontWeight.Bold, fontFamily = MONO)
        Text(date, color = c, style = sh, fontSize = 20.sp, fontWeight = FontWeight.Bold, fontFamily = MONO, maxLines = 2, textAlign = TextAlign.Center)
    }
    Text(if (armed) "Bấm  *  để mở khóa" else "Bấm \"Menu\" và \"*\" để mở khóa",
        Modifier.align(Alignment.BottomCenter).padding(bottom = 22.dp),
        color = c, style = sh, fontSize = 18.sp, fontWeight = FontWeight.Bold, fontFamily = MONO)
}

/** Màn hình khóa mặc định: hình nền che toàn bộ khung hiển thị, bấm Menu rồi * để mở khóa. */
@Composable
fun LockScreen(time: String, date: String, armed: Boolean, wall: ImageBitmap? = null) {
    Box(Modifier.fillMaxSize().pointerInput(Unit) { detectTapGestures { } }) {
        if (wall != null) {
            Image(wall, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        } else Canvas(Modifier.fillMaxSize()) {
            val w = size.width; val h = size.height
            drawRect(brush = Brush.verticalGradient(listOf(Color(0xFF8FD3F4), Color(0xFFDDF4E4))))
            drawCircle(Color(0xFFFFE9A8), radius = w * 0.13f, center = Offset(w * 0.8f, h * 0.30f))
            val far = Path().apply {
                moveTo(0f, h * 0.74f)
                quadraticBezierTo(w * 0.25f, h * 0.58f, w * 0.55f, h * 0.74f)
                quadraticBezierTo(w * 0.8f, h * 0.86f, w, h * 0.70f)
                lineTo(w, h); lineTo(0f, h); close()
            }
            drawPath(far, Color(0xFF7CCB93))
            val near = Path().apply {
                moveTo(0f, h * 0.86f)
                quadraticBezierTo(w * 0.35f, h * 0.74f, w * 0.7f, h * 0.88f)
                quadraticBezierTo(w * 0.88f, h * 0.94f, w, h * 0.84f)
                lineTo(w, h); lineTo(0f, h); close()
            }
            drawPath(near, Color(0xFF3F9E63))
        }
        LockTexts(time, date, armed, wall != null)
    }
}

@Composable
fun Phone() {
    val ctx = LocalContext.current
    var screen by remember { mutableStateOf("home") }
    var sel by remember { mutableIntStateOf(0) }
    var dial by remember { mutableStateOf("") }
    var buzz by remember { mutableStateOf(true) }
    var now by remember { mutableStateOf(timeNow()) }
    var apps by remember { mutableStateOf(listOf<AppInfo>()) }
    var appsMode by remember { mutableIntStateOf(0) }   // 0 = chữ, 1 = bảng icon
    var monthOffset by remember { mutableIntStateOf(0) }
    var alarms by remember { mutableStateOf(AlarmStore.load(ctx)) }
    var locked by remember { mutableStateOf(true) }
    var lockAt by remember { mutableLongStateOf(0L) }
    var btOn by remember { mutableStateOf(false) }
    var dialerOn by remember { mutableStateOf(false) }
    var smsOn by remember { mutableStateOf(isDefaultSms(ctx)) }
    var wallPick by remember { mutableStateOf<Bitmap?>(null) }          // ảnh vừa chọn, đang xem thử trước khi đặt
    var wallFx by remember { mutableStateOf(0.5f) }
    var wallFy by remember { mutableStateOf(0.5f) }
    var wallFw by remember { mutableStateOf(0f) }                         // kích thước khung xem thử (px)
    var wallFh by remember { mutableStateOf(0f) }
    var wallBmp by remember { mutableStateOf<ImageBitmap?>(null) }       // hình nền màn hình khóa do người dùng chọn
    var infoTick by remember { mutableIntStateOf(0) }      // làm tươi số liệu (wifi, pin, SIM...) mỗi giây
    var wifiNets by remember { mutableStateOf(listOf<WifiNet>()) }
    var power by remember { mutableStateOf(PowerStore.load(ctx)) }
    var pEdit by remember { mutableIntStateOf(0) }          // 0 = giờ tắt, 1 = giờ bật
    var settingsSel by remember { mutableIntStateOf(0) }    // nhớ mục đang chọn khi quay lại Cài đặt
    var volTick by remember { mutableIntStateOf(0) }
    var ringName by remember { mutableStateOf("") }
    var alarmName by remember { mutableStateOf("") }
    var editIdx by remember { mutableIntStateOf(0) }
    var editField by remember { mutableIntStateOf(0) }
    var eh by remember { mutableIntStateOf(7) }
    var em by remember { mutableIntStateOf(0) }
    var eon by remember { mutableStateOf(true) }
    var lcdRect by remember { mutableStateOf<androidx.compose.ui.geometry.Rect?>(null) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) { wallBmp = withContext(Dispatchers.IO) { Wallpaper.load(ctx) }?.asImageBitmap() }
    val wallReq = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { u ->
        if (u != null) scope.launch {
            val b = withContext(Dispatchers.IO) { Wallpaper.decode(ctx, u) }
            if (b != null) { wallFx = 0.5f; wallFy = 0.5f; wallPick = b }      // hiện khung xem thử, chưa đặt ngay
            else Toast.makeText(ctx, "Không đọc được ảnh này", Toast.LENGTH_SHORT).show()
        }
    }
    fun confirmWall() {
        val b = wallPick ?: return
        val x = wallFx; val y = wallFy; val w = wallFw; val h = wallFh
        scope.launch {
            val out = withContext(Dispatchers.IO) { Wallpaper.saveCrop(ctx, b, x, y, w, h) }
            wallPick = null
            if (out != null) { wallBmp = out.asImageBitmap(); Toast.makeText(ctx, "Đã đặt hình nền màn hình khóa", Toast.LENGTH_SHORT).show() }
            else Toast.makeText(ctx, "Không lưu được ảnh", Toast.LENGTH_SHORT).show()
        }
    }
    var contacts by remember { mutableStateOf(listOf<Contact>()) }
    var smsAll by remember { mutableStateOf(listOf<Sms>()) }
    var threads by remember { mutableStateOf(listOf<SmsThread>()) }
    var threadMsgs by remember { mutableStateOf(listOf<Sms>()) }
    var curKey by remember { mutableStateOf("") }
    var curAddr by remember { mutableStateOf("") }
    var curName by remember { mutableStateOf("") }
    var mv by remember { mutableIntStateOf(0) }
    var delTarget by remember { mutableStateOf(listOf<Sms>()) }         // tin sắp xóa (xác nhận)
    var delBack by remember { mutableStateOf("thread") }                // màn hình quay về sau khi xóa / hủy
    var delWhole by remember { mutableStateOf(false) }                  // true = xóa cả hội thoại
    var delName by remember { mutableStateOf("") }
    var cStage by remember { mutableIntStateOf(0) }
    var cTo by remember { mutableStateOf("") }
    var cName by remember { mutableStateOf("") }
    var cFrom by remember { mutableStateOf("new") }
    var cPick by remember { mutableIntStateOf(-1) }
    val entry = remember { TextEntry() }
    val webEntry = remember { TextEntry().also { it.mode = 0 } }   // bộ gõ cho Zalo Web / YouTube / TV (mặc định chữ thường)
    val calc = remember { CalcState() }
    val cam = remember { CamHolder() }
    var camFront by remember { mutableStateOf(false) }
    var camOk by remember { mutableStateOf(false) }
    var flash by remember { mutableStateOf(false) }
    var shutterTick by remember { mutableIntStateOf(0) }
    val shut = remember { Animatable(0f) }
    LaunchedEffect(shutterTick) { if (shutterTick > 0) { shut.animateTo(1f, tween(90)); shut.animateTo(0f, tween(160)) } }
    var gIds by remember { mutableStateOf(listOf<Long>()) }
    var gIdx by remember { mutableIntStateOf(0) }
    var gBmp by remember { mutableStateOf<Bitmap?>(null) }
    var gOk by remember { mutableStateOf(false) }
    var gLoaded by remember { mutableStateOf(false) }
    var recs by remember { mutableStateOf(listOf<RecItem>()) }
    var playing by remember { mutableStateOf<String?>(null) }
    var recTick by remember { mutableIntStateOf(0) }
    var recording by remember { mutableStateOf(false) }
    var recPaused by remember { mutableStateOf(false) }
    var calls by remember { mutableStateOf(listOf<CallEntry>()) }       // Nhật ký cuộc gọi
    var clLoaded by remember { mutableStateOf(false) }
    var ctLoaded by remember { mutableStateOf(false) }                  // danh bạ đã tải xong chưa
    var tracks by remember { mutableStateOf(listOf<Track>()) }          // Trình phát nhạc
    var mLoaded by remember { mutableStateOf(false) }
    var mCur by remember { mutableIntStateOf(-1) }                      // chỉ số bài đang phát (-1 = chưa phát)
    var mTick by remember { mutableIntStateOf(0) }
    var fromHome by remember { mutableStateOf(false) }                  // app mở bằng phím mũi tên từ màn hình chờ -> Về = màn hình chờ
    // ---- AI giọng nói: 0 rảnh, 1 đang nghe, 3 chờ xác nhận, 4 hiện kết quả / lỗi
    var aiState by remember { mutableIntStateOf(0) }                    // 5 = màn hình hướng dẫn (bấm ← 2 lần ở màn hình chờ)
    var endAt by remember { mutableStateOf(0L) }                       // lần bấm phím Tắt (đỏ) gần nhất, để nhận biết bấm 2 lần = khóa màn hình của app
    var aiOpenedAt by remember { mutableStateOf(0L) }                  // lúc mở màn hình AI, để nhận biết bấm ← 2 lần liên tiếp
    var aiHeard by remember { mutableStateOf("") }
    var aiMsg by remember { mutableStateOf("") }
    var aiLevel by remember { mutableFloatStateOf(0f) }
    var aiKind by remember { mutableIntStateOf(1) }                    // 1 gọi, 2 gửi tin, 3 soạn tin
    var aiBody by remember { mutableStateOf("") }
    var aiCands by remember { mutableStateOf(listOf<Contact>()) }      // người khớp tên (hoặc số đọc ra)
    var aiPick by remember { mutableIntStateOf(0) }
    var aiStartTick by remember { mutableIntStateOf(0) }               // tăng khi vào màn hình AI -> tự bắt đầu nghe
    var aiMusicQ by remember { mutableStateOf<String?>(null) }         // bài nhạc chờ phát sau khi danh sách nhạc tải xong ("" = bất kỳ)
    var aiShoot by remember { mutableStateOf(false) }                  // tự chụp sau khi mở máy ảnh bằng giọng nói
    val voice = remember { VoiceInput(ctx) }
    var permThen by remember { mutableStateOf<(() -> Unit)?>(null) }
    var permMust by remember { mutableStateOf(listOf<String>()) }
    // Ô tìm kiếm của Danh bạ (dùng chung bộ gõ multi-tap với soạn tin; chỉ tính khi đang ở màn hình Danh bạ)
    val cQuery = if (screen == "contacts") entry.text else ""
    val cShown = remember(contacts, cQuery) { filterContacts(contacts, cQuery) }
    val cLines = remember(contacts, cShown) {
        val cnt = contacts.groupingBy { it.name }.eachCount()
        cShown.map { if ((cnt[it.name] ?: 0) > 1) it.name + "  " + it.number else it.name }
    }
    val snake = remember { Snake() }
    // ---- App con YouTube / TV (tách từ repo TV): WebView nhúng ngay trong ô LCD phía trên
    var ytPane by remember { mutableStateOf<YouTubePane?>(null) }
    var tvPane by remember { mutableStateOf<TvPane?>(null) }
    var zwPane by remember { mutableStateOf<ZaloPane?>(null) }
    var fullView by remember { mutableStateOf<android.view.View?>(null) }
    var fullCb by remember { mutableStateOf<android.webkit.WebChromeClient.CustomViewCallback?>(null) }
    var webQuery by remember { mutableStateOf("") }      // từ khóa AI chờ đưa vào app con vừa mở
    val webHost = remember {
        object : FullscreenHost {
            override fun showFull(v: android.view.View, cb: android.webkit.WebChromeClient.CustomViewCallback?) { fullView = v; fullCb = cb }
            override fun hideFull() { fullView = null; try { fullCb?.onCustomViewHidden() } catch (_: Exception) {}; fullCb = null }
            override fun launchVoice() { Toast.makeText(ctx, "Dùng nút AI để tìm bằng giọng nói", Toast.LENGTH_SHORT).show() }
            override fun setBarVisible(v: Boolean) {}
        }
    }
    // Chỉ khi đang ở TV / YouTube mới cho xoay ngang (xoay ngang = tự full màn); màn khác giữ dọc như Nokia thật
    LaunchedEffect(screen, inSplit.value) {
        val web = (screen == "youtube" || screen == "tv" || screen == "zaloweb") && !inSplit.value
        (ctx as? Activity)?.requestedOrientation =
            if (web) android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR else android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        // Zalo Web / YouTube / TV: cấm tuyệt đối bàn phím ảo của máy (ẩn ngay nếu đang hiện, và không cho tự bật)
        (ctx as? Activity)?.window?.let { w ->
            if (screen == "youtube" || screen == "tv" || screen == "zaloweb") {
                w.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN or WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING)
                WindowInsetsControllerCompat(w, w.decorView).hide(WindowInsetsCompat.Type.ime())
            } else w.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_UNSPECIFIED or WindowManager.LayoutParams.SOFT_INPUT_ADJUST_UNSPECIFIED)
        }
    }
    // YouTube: xoay ngang = tự bấm nút phóng to của video (video vẫn phát tiếp), xoay dọc = thu nhỏ lại
    val isLandscape = androidx.compose.ui.platform.LocalConfiguration.current.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
    LaunchedEffect(isLandscape, screen, ytPane) {
        if (screen != "youtube") return@LaunchedEffect
        val p = ytPane ?: return@LaunchedEffect
        delay(500)   // chờ bố cục xoay xong
        p.autoFullscreen(isLandscape)
    }
    DisposableEffect(Unit) {
        val owner = ctx as? androidx.lifecycle.LifecycleOwner
        val obs = androidx.lifecycle.LifecycleEventObserver { _, e ->
            if (e == androidx.lifecycle.Lifecycle.Event.ON_PAUSE) { ytPane?.onPause(); tvPane?.onPause(); zwPane?.onPause() }
            else if (e == androidx.lifecycle.Lifecycle.Event.ON_RESUME) { ytPane?.onResume(); tvPane?.onResume(); zwPane?.onResume() }
        }
        owner?.lifecycle?.addObserver(obs)
        onDispose { owner?.lifecycle?.removeObserver(obs); ytPane?.destroy(); tvPane?.destroy(); zwPane?.destroy() }
    }
    val focus = remember { FocusRequester() }
    val haptic = LocalHapticFeedback.current

    LaunchedEffect(Unit) { focus.requestFocus(); while (true) { now = timeNow(); delay(1000) } }
    // Dọn dữ liệu theo màn hình: rời nhóm màn hình nào thì xóa dữ liệu của nhóm đó, không nhớ màn cũ
    var prevScreen by remember { mutableStateOf(screen) }
    LaunchedEffect(screen) {
        fun grp(x: String) = when (x) {
            "messages", "thread", "msgview", "compose", "smsConfirm" -> "sms"
            "gallery", "galConfirm" -> "gallery"
            "recorder", "recConfirm", "recording" -> "rec"
            "power", "powerEdit" -> "power"
            else -> x
        }
        val p = prevScreen
        prevScreen = screen
        if (screen == "home" || screen == "menu") fromHome = false
        if (grp(p) == grp(screen)) return@LaunchedEffect
        when (grp(p)) {
            "apps" -> { apps = emptyList(); AppIcons.clear() }
            "youtube" -> { ytPane?.destroy(); ytPane = null; webHost.hideFull() }
            "tv" -> { tvPane?.destroy(); tvPane = null; webHost.hideFull() }
            "zaloweb" -> { zwPane?.destroy(); zwPane = null; webHost.hideFull(); webEntry.reset(); webEntry.mode = 0 }
            "contacts" -> { contacts = emptyList(); cPick = -1; ctLoaded = false; entry.reset() }
            "sms" -> {
                smsAll = emptyList(); threads = emptyList(); threadMsgs = emptyList()
                contacts = emptyList(); cPick = -1
                curKey = ""; curAddr = ""; curName = ""; mv = 0; delTarget = emptyList()
                cStage = 0; cTo = ""; cName = ""; cFrom = "new"
                entry.reset()
            }
            "gallery" -> { gBmp = null; gIds = emptyList(); gIdx = 0; gOk = false; gLoaded = false }
            "wifi" -> wifiNets = emptyList()
            "camera" -> {
                cam.capture = null; cam.control = null; cam.hasFlash = false; cam.torch = false
                camOk = false; camFront = false; flash = false
            }
            "rec" -> { recs = emptyList(); playing = null }
            "calllog" -> { calls = emptyList(); clLoaded = false }
            "music" -> { Music.stop(); mCur = -1; tracks = emptyList(); mLoaded = false }
            "calc" -> calc.clear()
            "snake" -> snake.reset()
            "calendar" -> monthOffset = 0
            "alarmEdit", "clock" -> if (grp(screen) != "alarmEdit" && grp(screen) != "clock") editIdx = 0
        }
    }
    LaunchedEffect(screen) { if (screen == "snake") while (true) { delay(snake.stepMs()); snake.step() } }
    LaunchedEffect(lockAt) { if (lockAt != 0L) { delay(4000); lockAt = 0L } }
    LaunchedEffect(screen) { if (screen == "settings") while (true) { btOn = isBtOn(ctx); dialerOn = isDefaultDialer(ctx); smsOn = isDefaultSms(ctx); delay(600) } }
    LaunchedEffect(screen) { if (screen in INFO_SCREENS) while (true) { infoTick++; delay(1000) } }
    LaunchedEffect(gIdx, gIds, screen) {
        if (screen == "gallery" && gIds.isNotEmpty()) {
            gBmp = null
            gBmp = withContext(Dispatchers.IO) { Gallery.bitmap(ctx, gIds[gIdx.coerceIn(0, gIds.lastIndex)]) }
        }
    }
    LaunchedEffect(playing, recording, screen) {
        if (screen == "recorder" || screen == "recording") while (playing != null || recording) { recTick++; delay(250) }
    }
    LaunchedEffect(screen) { if (screen == "music") while (true) { mTick++; delay(300) } }
    DisposableEffect(Unit) { onDispose { Music.stop() } }
    DisposableEffect(screen) {
        val s = screen
        onDispose {
            if (s == "recording" && Rec.active) { Rec.stop(); recording = false }
            if (s == "recorder") { Play.stop(); playing = null }
        }
    }
    DisposableEffect(Unit) {
        val r = object : BroadcastReceiver() {
            override fun onReceive(c: Context, i: Intent) { locked = true; lockAt = 0L }
        }
        ctx.registerReceiver(r, IntentFilter(Intent.ACTION_SCREEN_OFF))
        onDispose { try { ctx.unregisterReceiver(r) } catch (_: Exception) {} }
    }
    val tick = homeTick.intValue
    LaunchedEffect(tick) { if (tick > 0) { screen = if (NokiaState.openMenu) "menu" else "home"; NokiaState.openMenu = false; sel = 0 } }
    LaunchedEffect(tick) { NokiaState.pendingDial?.let { dial = it; NokiaState.pendingDial = null; screen = "home"; sel = 0 } }

    fun freeformOn(): Boolean =
        ctx.packageManager.hasSystemFeature(PackageManager.FEATURE_FREEFORM_WINDOW_MANAGEMENT) ||
            Settings.Global.getInt(ctx.contentResolver, "enable_freeform_support", 0) != 0

    /** Mở app trong cửa sổ có kích thước đúng bằng vùng nội dung của màn hình LCD nhỏ (cần bật cửa sổ tự do). */
    fun launch(i: Intent, inLcd: Boolean = true) {
        val r = lcdRect
        if (!inLcd || r == null || inSplit.value) {
            try { ctx.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } catch (_: Exception) {}
            return
        }
        val opts = ActivityOptions.makeBasic()
        opts.setLaunchBounds(Rect(r.left.roundToInt(), r.top.roundToInt(), r.right.roundToInt(), r.bottom.roundToInt()))
        // Ép chế độ cửa sổ tự do (API ẩn, có thể không dùng được trên một số máy -> bỏ qua)
        try {
            ActivityOptions::class.java.getMethod("setLaunchWindowingMode", Int::class.javaPrimitiveType).invoke(opts, 5)
        } catch (_: Throwable) {}
        // MULTIPLE_TASK: tạo task mới để khung được áp dụng ngay cả khi app đang chạy toàn màn hình
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_MULTIPLE_TASK)
        try {
            ctx.startActivity(i, opts.toBundle())
        } catch (_: Throwable) {
            try { ctx.startActivity(i) } catch (_: Exception) {}
        }
    }
    fun showDiag() {
        val r = lcdRect
        val msg = "${Build.MANUFACTURER} ${Build.MODEL} | Android ${Build.VERSION.SDK_INT}\n" +
            "cua so tu do: " + freeformOn() + "\n" +
            "khung LCD: " + (r?.let { "${it.left.roundToInt()},${it.top.roundToInt()} - ${it.right.roundToInt()},${it.bottom.roundToInt()}" } ?: "null")
        Toast.makeText(ctx, msg, Toast.LENGTH_LONG).show()
    }
    fun toggleSplit() {
        val svc = NokiaAccessibilityService.instance
        if (svc == null) Toast.makeText(ctx, "Hãy bật dịch vụ Trợ năng phonecuibap trước", Toast.LENGTH_LONG).show()
        else { NokiaState.splitTried = true; svc.toggleSplit() }
    }
    fun dialScreen(n: String) = launch(Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + Uri.encode(n))))
    fun placeCall(n: String) {
        try {
            val i = Intent(Intent.ACTION_CALL, Uri.parse("tel:" + Uri.encode(n))).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            // Máy 2 SIM: gọi bằng SIM đã chọn trong Cài đặt > SIM
            Sims.callHandle(ctx)?.let { i.putExtra(TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE, it) }
            ctx.startActivity(i)
        } catch (_: Exception) { dialScreen(n) }
    }
    var pendingCall by remember { mutableStateOf("") }
    val callPerm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        val n = pendingCall
        pendingCall = ""
        if (n.isNotEmpty()) { if (ok) placeCall(n) else dialScreen(n) }
    }
    fun loadApps() {
        val pm = ctx.packageManager
        val q = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        apps = pm.queryIntentActivities(q, 0)
            .map { AppInfo(it.loadLabel(pm).toString(), it.activityInfo.packageName) }
            .filter { it.pkg != ctx.packageName }
            .distinctBy { it.pkg }
            .sortedBy { it.label.lowercase() }
    }
    fun refreshSoundNames() { ringName = Sound.ringtoneTitle(ctx); alarmName = Sound.alarmTitle(ctx) }

    val ringPick = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        if (r.resultCode == Activity.RESULT_OK) {
            val uri = Sound.picked(r.data)
            if (!Sound.setRingtone(ctx, uri)) {
                Toast.makeText(ctx, "Hãy cấp quyền \"Sửa đổi cài đặt hệ thống\" cho phonecuibap", Toast.LENGTH_LONG).show()
                try {
                    ctx.startActivity(Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:" + ctx.packageName))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                } catch (_: Exception) {}
            }
            refreshSoundNames()
        }
    }
    val alarmPick = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        if (r.resultCode == Activity.RESULT_OK) {
            val uri = Sound.picked(r.data)
            if (uri == Settings.System.DEFAULT_ALARM_ALERT_URI) AlarmStore.resetAlarmSound(ctx) else AlarmStore.setAlarmSound(ctx, uri)
            refreshSoundNames()
        }
    }
    val btPerm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        Toast.makeText(ctx, if (ok) "Đã cấp quyền, bấm lại để đổi Bluetooth" else "Cần quyền Bluetooth để bật/tắt", Toast.LENGTH_SHORT).show()
    }
    val btEnable = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { btOn = isBtOn(ctx) }

    @Suppress("DEPRECATION")
    fun toggleBt() {
        val ad: BluetoothAdapter? = (ctx.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter
        if (ad == null) { Toast.makeText(ctx, "Máy không có Bluetooth", Toast.LENGTH_SHORT).show(); return }
        if (Build.VERSION.SDK_INT >= 31 &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            btPerm.launch(Manifest.permission.BLUETOOTH_CONNECT); return
        }
        try {
            if (!ad.isEnabled) {
                if (!(Build.VERSION.SDK_INT < 33 && ad.enable())) btEnable.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
            } else if (!(Build.VERSION.SDK_INT < 33 && ad.disable())) {
                Toast.makeText(ctx, "Android 13+ không cho app tự tắt Bluetooth, hãy gạt tắt trong màn hình này", Toast.LENGTH_LONG).show()
                try { ctx.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } catch (_: Exception) {}
            }
        } catch (_: SecurityException) {}
        btOn = isBtOn(ctx)
    }

    fun adjustVol(d: Int, wrap: Boolean = false) {
        val am = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val st = VOL[sel.coerceIn(0, VOL.lastIndex)].second
        val max = am.getStreamMaxVolume(st)
        val cur = am.getStreamVolume(st)
        val n = if (wrap && cur >= max) 0 else (cur + d).coerceIn(0, max)
        try { am.setStreamVolume(st, n, AudioManager.FLAG_PLAY_SOUND) } catch (_: SecurityException) {
            Toast.makeText(ctx, "Hãy tắt chế độ Không làm phiền để chỉnh âm lượng", Toast.LENGTH_SHORT).show()
        }
        volTick++
    }

    // ---- Cài đặt mới: wifi, độ sáng, SIM, lịch nguồn ----
    val wifiAdd = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        val res = r.data?.getIntegerArrayListExtra(Settings.EXTRA_WIFI_NETWORK_RESULT_LIST)
        val ok = r.resultCode == Activity.RESULT_OK && (res == null || res.all { it == 0 || it == 2 })
        Toast.makeText(ctx, if (ok) "Đã lưu wifi, máy sẽ tự kết nối" else "Chưa lưu được wifi", Toast.LENGTH_LONG).show()
        scope.launch { delay(3000); if (screen == "wifi") wifiNets = Wifi.nets(ctx) }
    }
    fun loadWifi() {
        wifiNets = Wifi.nets(ctx)
        Wifi.scan(ctx)
        scope.launch {
            delay(2500); if (screen == "wifi") wifiNets = Wifi.nets(ctx)
            delay(4000); if (screen == "wifi") wifiNets = Wifi.nets(ctx)
        }
    }
    fun connectWifi(n: WifiNet, pass: String) {
        val (intent, msg) = Wifi.connect(ctx, n, pass)
        if (msg.isNotEmpty()) Toast.makeText(ctx, msg, Toast.LENGTH_LONG).show()
        if (intent != null) {
            try { wifiAdd.launch(intent) } catch (_: Exception) {
                Toast.makeText(ctx, "Hãy nối wifi trong màn hình hệ thống", Toast.LENGTH_LONG).show()
                openSettings(ctx, Settings.ACTION_WIFI_SETTINGS)
            }
        }
    }
    fun pickWifi(i: Int) {
        val n = wifiNets.getOrNull(i - 1) ?: return
        if (n.connected) { Toast.makeText(ctx, "Đang nối với " + n.ssid, Toast.LENGTH_SHORT).show(); return }
        if (n.sec == 0 || n.sec == 1 || n.sec == 4) connectWifi(n, "")
        else Wifi.askPassword(ctx, n.ssid) { pass -> connectWifi(n, pass) }   // hiện bàn phím ảo của máy
    }
    fun askWriteSettings() {
        Toast.makeText(ctx, "Hãy cấp quyền \"Sửa đổi cài đặt hệ thống\" cho phonecuibap", Toast.LENGTH_LONG).show()
        try {
            ctx.startActivity(Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:" + ctx.packageName))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: Exception) {}
    }
    fun adjustBright(d: Int, wrap: Boolean = false) {
        if (!Bright.canWrite(ctx)) { askWriteSettings(); return }
        val cur = Bright.get(ctx)
        Bright.set(ctx, if (wrap && cur >= 255) 1 else (cur + d * 26).coerceIn(1, 255))
        infoTick++
    }
    fun simOk(perm: () -> Unit) {
        val rows = simRows(ctx)
        val sims = Sims.list(ctx)
        when (rows.getOrNull(sel)?.second) {
            "perm" -> perm()
            "call" -> { Sims.cycle(ctx, "call", sims, Sims.callSub(ctx, sims)); infoTick++ }
            "sms" -> { Sims.cycle(ctx, "sms", sims, Sims.smsSub(ctx, sims)); infoTick++ }
            // Android không cho app đổi SIM dữ liệu / bật tắt dữ liệu di động: mở đúng màn hình của hệ thống
            "data" -> openSettings(ctx, Settings.ACTION_NETWORK_OPERATOR_SETTINGS, Settings.ACTION_WIRELESS_SETTINGS)
            "mobile" -> openSettings(ctx, Settings.Panel.ACTION_INTERNET_CONNECTIVITY,
                Settings.ACTION_DATA_ROAMING_SETTINGS, Settings.ACTION_WIRELESS_SETTINGS)
        }
    }
    fun startPowerEdit(i: Int) {
        pEdit = i
        if (i == 0) { eh = power.offH; em = power.offM; eon = power.offOn }
        else { eh = power.onH; em = power.onM; eon = power.onOn }
        editField = 0
        screen = "powerEdit"
    }
    fun savePower() {
        val s = if (pEdit == 0) power.copy(offH = eh, offM = em, offOn = eon) else power.copy(onH = eh, onM = em, onOn = eon)
        power = s
        PowerStore.save(ctx, s)
        PowerStore.schedule(ctx, s)
        if (pEdit == 0 && eon && NokiaAccessibilityService.instance == null)
            Toast.makeText(ctx, "Cần bật dịch vụ Trợ năng phonecuibap để tự tắt màn hình", Toast.LENGTH_LONG).show()
        screen = "power"
        sel = pEdit
    }

    /** Khôi phục cài đặt gốc = xóa toàn bộ dữ liệu của riêng app này (báo thức, nhạc báo thức, quyền đã cấp). */
    fun doReset() {
        alarms = emptyList()
        AlarmStore.schedule(ctx, emptyList())
        val ok = try {
            (ctx.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).clearApplicationUserData()
        } catch (_: Exception) { false }
        if (!ok) {
            ctx.getSharedPreferences("alarms", Context.MODE_PRIVATE).edit().clear().apply()
            ctx.getSharedPreferences("sound", Context.MODE_PRIVATE).edit().clear().apply()
            ctx.getSharedPreferences("sms_sent", Context.MODE_PRIVATE).edit().clear().apply()
            locked = true; lockAt = 0L; screen = "home"; sel = 0; dial = ""
        }
    }

    val notifPerm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    val dialerReq = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        dialerOn = isDefaultDialer(ctx)
        Toast.makeText(ctx, if (dialerOn) "Đã đặt phonecuibap làm ứng dụng gọi điện" else "Chưa đặt làm ứng dụng gọi điện", Toast.LENGTH_SHORT).show()
        if (dialerOn && Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
            notifPerm.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
    fun requestDialer() {
        if (isDefaultDialer(ctx)) { Toast.makeText(ctx, "Đã là ứng dụng gọi điện mặc định", Toast.LENGTH_SHORT).show(); return }
        val i = if (Build.VERSION.SDK_INT >= 29)
            (ctx.getSystemService(Context.ROLE_SERVICE) as RoleManager).createRequestRoleIntent(RoleManager.ROLE_DIALER)
        else Intent(TelecomManager.ACTION_CHANGE_DEFAULT_DIALER)
            .putExtra(TelecomManager.EXTRA_CHANGE_DEFAULT_DIALER_PACKAGE_NAME, ctx.packageName)
        try { dialerReq.launch(i) } catch (_: Exception) { Toast.makeText(ctx, "Máy không hỗ trợ đổi ứng dụng gọi điện", Toast.LENGTH_SHORT).show() }
    }
    val smsReq = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        smsOn = isDefaultSms(ctx)
        Toast.makeText(ctx, if (smsOn) "Đã đặt phonecuibap làm ứng dụng nhắn tin" else "Chưa đặt làm ứng dụng nhắn tin", Toast.LENGTH_SHORT).show()
        if (smsOn && Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
            notifPerm.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
    fun requestSmsApp() {
        if (isDefaultSms(ctx)) { Toast.makeText(ctx, "Đã là ứng dụng nhắn tin mặc định", Toast.LENGTH_SHORT).show(); return }
        try {
            val i = if (Build.VERSION.SDK_INT >= 29)
                (ctx.getSystemService(Context.ROLE_SERVICE) as RoleManager).createRequestRoleIntent(RoleManager.ROLE_SMS)
            else Intent(Telephony.Sms.Intents.ACTION_CHANGE_DEFAULT)
                .putExtra(Telephony.Sms.Intents.EXTRA_PACKAGE_NAME, ctx.packageName)
            smsReq.launch(i)
        } catch (_: Exception) { Toast.makeText(ctx, "Máy không hỗ trợ đổi ứng dụng nhắn tin", Toast.LENGTH_SHORT).show() }
    }
    fun startEdit(i: Int) {
        editIdx = i
        val a = alarms.getOrNull(i) ?: Alarm(7, 0, true)
        eh = a.h; em = a.m; eon = a.on; editField = 0
        screen = "alarmEdit"
    }
    fun commitAlarms(list: List<Alarm>) {
        alarms = list
        AlarmStore.save(ctx, list)
        AlarmStore.schedule(ctx, list)
        screen = "clock"
        sel = editIdx.coerceIn(0, list.size)
    }
    fun saveAlarm() {
        val l = alarms.toMutableList()
        val a = Alarm(eh, em, eon)
        if (editIdx < l.size) l[editIdx] = a else l.add(a)
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
            notifPerm.launch(Manifest.permission.POST_NOTIFICATIONS)
        commitAlarms(l)
        if (eon && (Build.VERSION.SDK_INT < 33 ||
                ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED))
            askIgnoreBatteryOnce(ctx)   // cho phép chạy nền để báo thức không bị hệ thống chặn
        if (eon) {   // báo cho người dùng biết khi nào sẽ reo, để kiểm tra đã đặt đúng
            val min = ((AlarmStore.nextTrigger(eh, em) - System.currentTimeMillis() + 59_999L) / 60_000L).toInt()
            Toast.makeText(ctx, "Báo thức sau " + (if (min >= 60) "${min / 60} giờ " else "") + "${min % 60} phút", Toast.LENGTH_LONG).show()
        }
    }
    fun deleteAlarm() {
        val l = alarms.toMutableList()
        if (editIdx in l.indices) l.removeAt(editIdx)
        commitAlarms(l)
    }
    fun editMove(k: String) {
        val fields = if (screen == "powerEdit") 3 else if (editIdx < alarms.size) 4 else 3
        when (k) {
            "LEFT" -> editField = maxOf(editField - 1, 0)
            "RIGHT" -> editField = minOf(editField + 1, fields - 1)
            else -> {
                val d = if (k == "UP") 1 else -1
                when (editField) {
                    0 -> eh = (eh + d + 24) % 24
                    1 -> em = (em + d + 60) % 60
                    2 -> eon = !eon
                }
            }
        }
    }
    fun granted(p: String) = ContextCompat.checkSelfPermission(ctx, p) == PackageManager.PERMISSION_GRANTED

    val multiPerm = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { _ ->
        val t = permThen
        permThen = null
        if (t != null) {
            if (permMust.all { granted(it) }) t()
            else {
                // Không còn hộp thoại để hiện (bị từ chối hẳn, hoặc Android 13+ chặn "cài đặt bị hạn chế"
                // với app cài ngoài CH Play) -> mở thẳng trang Thông tin ứng dụng để người dùng cấp quyền
                val act = ctx as? android.app.Activity
                val blocked = permMust.filter { !granted(it) }.any {
                    act == null || !ActivityCompat.shouldShowRequestPermissionRationale(act, it)
                }
                if (blocked) {
                    Toast.makeText(ctx, "Bấm ⋮ > \"Cho phép cài đặt bị hạn chế\" (nếu có), rồi vào Quyền và bật quyền cần dùng",
                        Toast.LENGTH_LONG).show()
                    try {
                        ctx.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + ctx.packageName))
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    } catch (_: Exception) {}
                } else Toast.makeText(ctx, "Cần cấp quyền để dùng chức năng này", Toast.LENGTH_SHORT).show()
            }
        }
    }
    /** Xin quyền (nếu thiếu) rồi chạy then; must = các quyền bắt buộc, phần còn lại là tùy chọn. */
    fun need(perms: List<String>, must: List<String> = perms, then: () -> Unit) {
        val miss = perms.filter { !granted(it) }
        if (miss.isEmpty()) then() else { permThen = then; permMust = must; multiPerm.launch(miss.toTypedArray()) }
    }
    // ---- Xóa ảnh trong Thư viện ----
    var delPhotoId by remember { mutableLongStateOf(-1L) }
    fun afterPhotoDeleted(id: Long) {
        gIds = gIds.filterNot { it == id }
        gIdx = gIdx.coerceIn(0, maxOf(0, gIds.size - 1))
        gBmp = null
        Toast.makeText(ctx, "Đã xóa ảnh", Toast.LENGTH_SHORT).show()
    }
    val photoDelReq = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { r ->
        if (r.resultCode == Activity.RESULT_OK) {
            val id = delPhotoId
            if (Build.VERSION.SDK_INT == 29) {   // Android 10: được phép rồi thì phải xóa lại
                scope.launch {
                    if (withContext(Dispatchers.IO) { Gallery.delete(ctx, id) } is DelResult.Done) afterPhotoDeleted(id)
                    else Toast.makeText(ctx, "Không xóa được ảnh", Toast.LENGTH_SHORT).show()
                }
            } else afterPhotoDeleted(id)
        }
    }
    fun runDeletePhoto() {
        val id = gIds.getOrNull(gIdx) ?: return
        delPhotoId = id
        scope.launch {
            when (val res = withContext(Dispatchers.IO) { Gallery.delete(ctx, id) }) {
                is DelResult.Done -> afterPhotoDeleted(id)
                is DelResult.NeedUser -> photoDelReq.launch(IntentSenderRequest.Builder(res.sender).build())
                is DelResult.Failed -> Toast.makeText(ctx, "Không xóa được ảnh", Toast.LENGTH_SHORT).show()
            }
        }
    }
    /** Android 11+: hệ thống tự hỏi xác nhận nên xóa luôn; bản cũ hơn thì hỏi trước bằng màn hình của app. */
    fun askDeletePhoto() {
        if (gIds.isEmpty()) return
        if (Build.VERSION.SDK_INT >= 30) runDeletePhoto() else screen = "galConfirm"
    }
    val imgPerms = if (Build.VERSION.SDK_INT >= 33) listOf(Manifest.permission.READ_MEDIA_IMAGES)
        else listOf(Manifest.permission.READ_EXTERNAL_STORAGE)
    val camPerms = if (Build.VERSION.SDK_INT < 29) listOf(Manifest.permission.CAMERA, Manifest.permission.WRITE_EXTERNAL_STORAGE)
        else listOf(Manifest.permission.CAMERA)
    val audioPerms = if (Build.VERSION.SDK_INT >= 33) listOf(Manifest.permission.READ_MEDIA_AUDIO)
        else listOf(Manifest.permission.READ_EXTERNAL_STORAGE)

    fun inSms() = screen == "messages" || screen == "thread" || screen == "msgview" || screen == "compose" || screen == "smsConfirm"
    fun inRec() = screen == "recorder" || screen == "recConfirm" || screen == "recording"
    // Kết quả tải xong muộn mà đã rời màn hình thì bỏ đi, không giữ lại
    fun loadContacts() { scope.launch { val c = withContext(Dispatchers.IO) { Contacts.load(ctx) }; if (screen == "contacts" || inSms()) contacts = c; if (screen == "contacts") ctLoaded = true } }
    fun loadRecs() { scope.launch { val r = withContext(Dispatchers.IO) { Rec.list(ctx) }; if (inRec()) recs = r } }
    fun loadSms(after: (() -> Unit)? = null) {
        smsOn = isDefaultSms(ctx)
        scope.launch {
            val hasC = granted(Manifest.permission.READ_CONTACTS)
            val (all, cs) = withContext(Dispatchers.IO) {
                val c = if (hasC) Contacts.load(ctx) else emptyList()
                SmsRepo.loadAll(ctx) to c
            }
            if (!inSms()) return@launch
            smsAll = all
            if (cs.isNotEmpty()) contacts = cs
            threads = buildThreads(all, if (cs.isNotEmpty()) cs else contacts)
            if (curKey.isNotEmpty()) threadMsgs = all.filter { addrKey(it.addr) == curKey }
            after?.invoke()
        }
    }

    fun launchPkg(pkg: String, label: String) {
        val i = ctx.packageManager.getLaunchIntentForPackage(pkg)
        if (i != null) launch(i) else {
            Toast.makeText(ctx, "Chưa cài $label, đang mở CH Play", Toast.LENGTH_SHORT).show()
            try {
                ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$pkg"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            } catch (_: Exception) {}
        }
    }

    /** Gọi ngay, không xác nhận, không qua màn hình quay số của app. */
    fun callNow(n: String) {
        if (n.isEmpty()) return
        if (granted(Manifest.permission.CALL_PHONE)) placeCall(n)
        else { pendingCall = n; callPerm.launch(Manifest.permission.CALL_PHONE) }
    }

    fun openThread(key: String) {
        curKey = key
        val t = threads.firstOrNull { it.key == key }
        curAddr = t?.addr ?: ""
        curName = t?.name ?: curAddr
        threadMsgs = smsAll.filter { addrKey(it.addr) == key }
        screen = "thread"; sel = 0
    }
    /** Hỏi xác nhận xóa; back = màn hình quay về. whole = xóa cả hội thoại. */
    fun askDeleteSms(list: List<Sms>, back: String, whole: Boolean, name: String = "") {
        if (list.isEmpty()) return
        delTarget = list; delBack = back; delWhole = whole; delName = name
        screen = "smsConfirm"
    }
    fun doDeleteSms() {
        val target = delTarget; val back = delBack
        delTarget = emptyList()
        screen = back
        scope.launch {
            val real = withContext(Dispatchers.IO) { SmsRepo.delete(ctx, target) }
            loadSms {
                if (back == "messages") sel = sel.coerceIn(0, threads.size)
                else if (threadMsgs.isEmpty()) { curKey = ""; screen = "messages"; sel = 0 }   // hết tin trong hội thoại
                else sel = sel.coerceIn(0, threadMsgs.size)
                Toast.makeText(ctx, if (real) "Đã xóa" else "Đã xóa khỏi app (chưa xóa hẳn khỏi máy)", Toast.LENGTH_SHORT).show()
            }
        }
    }
    fun startCompose(to: String, name: String, from: String) {
        cTo = to; cName = name; cFrom = from; cPick = -1
        entry.reset()
        cStage = if (to.isEmpty()) 0 else 1
        screen = "compose"; sel = 0
        if (to.isEmpty() && contacts.isEmpty() && granted(Manifest.permission.READ_CONTACTS)) loadContacts()
    }
    LaunchedEffect(tick) { NokiaState.pendingSms?.let { n -> NokiaState.pendingSms = null; if (n.isNotEmpty()) startCompose(n, n, "new") } }
    fun doSend() {
        val body = entry.text.trim()
        if (body.isEmpty() || cTo.isEmpty()) {
            Toast.makeText(ctx, "Chưa có nội dung", Toast.LENGTH_SHORT).show(); return
        }
        need(listOf(Manifest.permission.SEND_SMS)) {
            if (SmsRepo.send(ctx, cTo, body)) {
                Toast.makeText(ctx, "Đã gửi", Toast.LENGTH_SHORT).show()
                val key = addrKey(cTo)
                loadSms { openThread(key) }
            } else Toast.makeText(ctx, "Gửi không được", Toast.LENGTH_SHORT).show()
        }
    }
    fun pickContact(d: Int) {
        if (contacts.isEmpty()) return
        cPick = if (cPick < 0) (if (d > 0) 0 else contacts.size - 1) else (cPick + d).coerceIn(0, contacts.size - 1)
        cTo = contacts[cPick].number; cName = contacts[cPick].name
    }

    fun startRecording() {
        need(listOf(Manifest.permission.RECORD_AUDIO)) {
            Play.stop(); playing = null
            if (Rec.active) {
                Toast.makeText(ctx, if (CallHub.recording) "Đang ghi âm cuộc gọi" else "Đang ghi âm", Toast.LENGTH_SHORT).show()
                return@need
            }
            if (Rec.start(ctx)) { recording = true; recPaused = false; screen = "recording"; sel = 0 }
            else Toast.makeText(ctx, "Không ghi âm được", Toast.LENGTH_SHORT).show()
        }
    }
    fun stopRecording() {
        val f = Rec.stop()
        recording = false; recPaused = false
        if (f == null) Toast.makeText(ctx, "Bản ghi quá ngắn, không lưu", Toast.LENGTH_SHORT).show()
        loadRecs()
        screen = "recorder"
        sel = if (f != null) 1 else 0
    }
    fun togglePause() {   // chỉ dùng khi đang ghi âm, bấm phím mũi tên
        if (Rec.paused) { Rec.resume(); recPaused = false }
        else if (Rec.pause()) recPaused = true
        else Toast.makeText(ctx, "Máy không hỗ trợ tạm dừng", Toast.LENGTH_SHORT).show()
    }
    fun togglePlay(i: Int) {
        val r = recs.getOrNull(i - 1) ?: return
        if (playing == r.file.absolutePath) { Play.stop(); playing = null }
        else if (Play.play(r.file) { playing = null }) playing = r.file.absolutePath
        else Toast.makeText(ctx, "Không phát được", Toast.LENGTH_SHORT).show()
    }
    fun deleteRec(i: Int) {
        val r = recs.getOrNull(i - 1) ?: return
        if (playing == r.file.absolutePath) { Play.stop(); playing = null }
        r.file.delete()
        loadRecs()
        sel = (sel - 1).coerceAtLeast(0)
        Toast.makeText(ctx, "Đã xóa", Toast.LENGTH_SHORT).show()
    }

    // ---- Nhật ký cuộc gọi ----
    fun loadCalls() {
        scope.launch {
            val l = withContext(Dispatchers.IO) { CallHistory.load(ctx) }
            if (screen == "calllog") { calls = l; clLoaded = true }
        }
    }

    // ---- Trình phát nhạc ----
    /** Phát bài thứ i; hết bài thì tự sang bài kế (hết danh sách thì dừng). */
    fun playTrack(i: Int) {
        val t = tracks.getOrNull(i) ?: return
        mCur = i; sel = i
        val ok = Music.play(ctx, t,
            onEnd = { if (i + 1 < tracks.size) playTrack(i + 1) },
            onError = { mCur = -1; Toast.makeText(ctx, "Không phát được bài này", Toast.LENGTH_SHORT).show() })
        if (!ok) { mCur = -1; Toast.makeText(ctx, "Không phát được bài này", Toast.LENGTH_SHORT).show() }
    }
    fun loadTracks() {
        scope.launch {
            val l = withContext(Dispatchers.IO) { MusicLib.load(ctx) }
            if (screen == "music") {
                tracks = l; mLoaded = true
                // Lệnh giọng nói "mở bài ...": tải xong danh sách thì tìm và phát luôn
                aiMusicQ?.let { q ->
                    aiMusicQ = null
                    val i = if (l.isEmpty()) -1
                        else if (q.isEmpty()) l.indices.random()
                        else (AiMatch.rank(l.map { plain(it.title + " " + it.artist) }, q).firstOrNull() ?: -1)
                    if (i >= 0) playTrack(i)
                    else Toast.makeText(ctx, "Không thấy bài \"$q\" trong máy", Toast.LENGTH_LONG).show()
                }
            }
        }
    }
    /** ◀ ▶: bài trước / bài sau. */
    fun musicStep(d: Int) {
        if (tracks.isEmpty()) return
        val base = if (mCur in tracks.indices) mCur else sel
        playTrack((base + d).coerceIn(0, tracks.size - 1))
    }
    fun adjustMusicVol(d: Int) {
        val am = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        try {
            am.adjustStreamVolume(AudioManager.STREAM_MUSIC,
                if (d > 0) AudioManager.ADJUST_RAISE else AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI)
        } catch (_: SecurityException) {}
    }

    fun gridMove(d: Int) { if (apps.isNotEmpty()) sel = (sel + d).coerceIn(0, apps.lastIndex) }
    fun open(s: String) {
        if (s != "music") aiMusicQ = null
        if (s != "camera") aiShoot = false
        screen = s; sel = 0; monthOffset = 0
        when (s) {
            "youtube" -> {
                ytPane?.destroy()
                val a = ctx as? Activity
                if (a != null) { val p = YouTubePane(a, webHost); ytPane = p; if (webQuery.isNotEmpty()) { p.search(webQuery); webQuery = "" } }
            }
            "zaloweb" -> {
                zwPane?.destroy()
                val a = ctx as? Activity
                if (a != null) zwPane = ZaloPane(a, webHost)
            }
            "tv" -> {
                tvPane?.destroy()
                val a = ctx as? Activity
                if (a != null) {
                    val p = TvPane(a, webHost); tvPane = p
                    if (webQuery.isNotEmpty()) {
                        val q = webQuery; webQuery = ""
                        if (!p.playByName(q)) Toast.makeText(ctx, "Không thấy kênh \"$q\"", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            "ai" -> { aiState = 0; aiHeard = ""; aiMsg = ""; aiOpenedAt = SystemClock.uptimeMillis(); aiStartTick++ }
            "snake" -> snake.reset()
            "apps" -> loadApps()
            "sound" -> refreshSoundNames()
            "wifi" -> need(Wifi.perms(), listOf(Manifest.permission.ACCESS_FINE_LOCATION)) { loadWifi() }
            "sim" -> need(Sims.perms(), listOf(Manifest.permission.READ_PHONE_STATE)) { infoTick++ }
            "accounts" -> need(listOf(Manifest.permission.GET_ACCOUNTS)) { infoTick++ }
            "power" -> power = PowerStore.load(ctx)
            "calc" -> calc.clear()
            "recorder" -> loadRecs()
            "calllog" -> { clLoaded = false; need(listOf(Manifest.permission.READ_CALL_LOG)) { loadCalls() } }
            "music" -> { mLoaded = false; mCur = -1; need(audioPerms) { loadTracks() } }
            "contacts" -> { entry.reset(); entry.mode = 0; need(listOf(Manifest.permission.READ_CONTACTS)) { loadContacts() } }
            "messages" -> {
                curKey = ""
                need(listOf(Manifest.permission.READ_SMS, Manifest.permission.SEND_SMS, Manifest.permission.READ_CONTACTS),
                    listOf(Manifest.permission.READ_SMS)) { loadSms() }
            }
            "camera" -> { camOk = false; camFront = false; flash = false; need(camPerms) { camOk = true } }
            "gallery" -> {
                gOk = false; gLoaded = false; gIds = emptyList(); gIdx = 0; gBmp = null
                need(imgPerms) {
                    gOk = true
                    scope.launch { val ids = withContext(Dispatchers.IO) { Gallery.ids(ctx) }; if (screen == "gallery") { gIds = ids; gLoaded = true } }
                }
            }
        }
    }
    /** Phím mũi tên ở màn hình chờ: mở thẳng app; bấm Về thì quay lại màn hình chờ (không qua Menu). */
    fun shortcut(s: String) { open(s); fromHome = true }
    fun back() {
        when (screen) {
            "home" -> {}
            "menu" -> open("home")
            "youtube" -> if (fullView != null) webHost.hideFull() else if (ytPane?.back() != true) open("menu")
            "zaloweb" -> if (fullView != null) webHost.hideFull() else if (zwPane?.back() != true) open("menu")
            "tv" -> if (fullView != null) webHost.hideFull() else if (tvPane?.back() != true) open("menu")
            "contacts" -> if (entry.text.isNotEmpty()) { entry.reset(); entry.mode = 0; sel = 0 }   // đang tìm: Về = xóa ô tìm
                else if (fromHome) open("home") else open("menu")
            "alarmEdit" -> screen = "clock"
            "powerEdit" -> { screen = "power"; sel = pEdit }
            "sound", "reset", "wifi", "sim", "brightness", "battery", "storage", "accounts", "power" -> {
                open("settings"); sel = settingsSel.coerceIn(0, SETTINGS.lastIndex)
            }
            "volume" -> open("sound")
            "thread" -> open("messages")
            "msgview" -> screen = "thread"
            "compose" -> if (cStage == 1 && cFrom == "new") cStage = 0
                else if (cFrom == "thread") screen = "thread" else open("messages")
            "recording" -> stopRecording()
            "recConfirm" -> screen = "recorder"
            "smsConfirm" -> screen = delBack
            "galConfirm" -> screen = "gallery"
            else -> if (fromHome) open("home") else open("menu")
        }
    }

    // ======================= AI điều khiển bằng giọng nói =======================
    /** Xong một lệnh AI: báo ngắn bằng Toast (nếu có) rồi về màn hình chính ngay, không dừng ở màn hình kết quả. */
    fun aiFinish(msg: String = "") {
        aiState = 0; aiMsg = ""; aiHeard = ""
        if (msg.isNotBlank()) Toast.makeText(ctx, msg, Toast.LENGTH_LONG).show()
        open("home")
    }
    fun aiOpen(id: String) {
        when (id) {
            "youtube", "tv", "zaloweb" -> open(id)      // mở ngay trong ô LCD
            else -> open(id)
        }
    }
    /** Tìm người (hoặc dùng luôn số đọc ra) rồi sang bước xác nhận. kind: 1 gọi, 2 gửi tin, 3 soạn tin. */
    fun aiPrepare(target: String, number: String, kind: Int, body: String) {
        aiKind = kind; aiBody = body
        if (number.isNotEmpty()) {
            aiCands = listOf(Contact(number, number, number)); aiPick = 0; aiState = 3
            return
        }
        aiState = 4; aiMsg = "Cần quyền Danh bạ để tìm người"
        need(listOf(Manifest.permission.READ_CONTACTS)) {
            aiMsg = "Đang tìm \"$target\"..."
            scope.launch {
                val all = withContext(Dispatchers.IO) { Contacts.load(ctx) }
                val idx = AiMatch.rank(all.map { it.key }, target)
                if (idx.isEmpty()) { aiState = 4; aiMsg = "Không thấy \"$target\"\ntrong danh bạ" }
                else { aiCands = idx.take(8).map { all[it] }; aiPick = 0; aiState = 3 }
            }
        }
    }
    fun aiExec(c: AiCmd) {
        aiState = 4; aiMsg = ""
        when (c) {
            is AiCmd.Call -> aiPrepare(c.target, c.number, 1, "")
            is AiCmd.Sms -> aiPrepare(c.target, c.number, if (c.body.isEmpty()) 3 else 2, c.body)
            is AiCmd.Camera -> {
                aiShoot = c.shoot
                open("camera")
                if (c.selfie) camFront = true
                if (c.shoot) Toast.makeText(ctx, "Chụp sau 3 giây...", Toast.LENGTH_SHORT).show()
            }
            is AiCmd.Gallery -> open("gallery")
            is AiCmd.Music -> { aiMusicQ = c.query; open("music") }
            is AiCmd.Open -> aiOpen(c.id)
            is AiCmd.OpenApp -> {
                val pm = ctx.packageManager
                val list = pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
                    .map { AppInfo(it.loadLabel(pm).toString(), it.activityInfo.packageName) }
                    .filter { it.pkg != ctx.packageName }
                    .distinctBy { it.pkg }
                val idx = AiMatch.rank(list.map { plain(it.label) }, c.name)
                if (idx.isEmpty()) aiMsg = "Không thấy ứng dụng \"${c.name}\""
                else { val a = list[idx[0]]; open("home"); launchPkg(a.pkg, a.label) }
            }
            is AiCmd.YouTube -> { webQuery = c.query; open("youtube") }
            is AiCmd.Tv -> { webQuery = c.query; open("tv") }
            is AiCmd.Volume -> {
                adjustMusicVol(if (c.up) 1 else -1); adjustMusicVol(if (c.up) 1 else -1)
                aiFinish(if (c.up) "Đã tăng âm lượng" else "Đã giảm âm lượng")
            }
            is AiCmd.Torch -> aiFinish(if (Torch.set(ctx, c.on)) (if (c.on) "Đã bật đèn pin" else "Đã tắt đèn pin")
                else "Không bật được đèn pin (máy không có flash hoặc camera đang bận)")
            is AiCmd.Bluetooth -> {
                if (isBtOn(ctx) == c.on) aiFinish("Bluetooth đã " + (if (c.on) "bật" else "tắt") + " rồi")
                else { toggleBt(); aiFinish("Đang " + (if (c.on) "bật" else "tắt") + " Bluetooth") }
            }
            is AiCmd.SetAlarm -> {
                if (Build.VERSION.SDK_INT >= 33 && !granted(Manifest.permission.POST_NOTIFICATIONS))
                    notifPerm.launch(Manifest.permission.POST_NOTIFICATIONS)
                val l = alarms + Alarm(c.h, c.m, true)
                alarms = l
                AlarmStore.save(ctx, l)
                AlarmStore.schedule(ctx, l)
                val min = ((AlarmStore.nextTrigger(c.h, c.m) - System.currentTimeMillis() + 59_999L) / 60_000L).toInt()
                aiFinish("Đã đặt báo thức %02d:%02d (sau %s%d phút)".format(c.h, c.m, if (min >= 60) "${min / 60} giờ " else "", min % 60))
                if (Build.VERSION.SDK_INT < 33 || granted(Manifest.permission.POST_NOTIFICATIONS)) askIgnoreBatteryOnce(ctx)
            }
            is AiCmd.Lock -> { open("home"); locked = true; lockAt = 0L }
            is AiCmd.Time -> { val cal = Calendar.getInstance(); aiFinish("Bây giờ là ${cal.get(Calendar.HOUR_OF_DAY)} giờ ${cal.get(Calendar.MINUTE)} phút") }
            is AiCmd.Date -> aiFinish("Hôm nay là " + dateNow())
            is AiCmd.Help -> aiMsg = ""     // hiện lại danh sách câu ví dụ
            is AiCmd.Cancel -> aiFinish()
            is AiCmd.Unknown -> aiMsg = "Chưa hiểu lệnh này.\nHãy nói rõ hơn, ví dụ:\n\"gọi cho mẹ\""
        }
    }
    fun aiHandle(list: List<String>) {
        val heard = list.filter { it.isNotBlank() }
        if (heard.isEmpty()) { aiState = 4; aiMsg = "Không nghe rõ.\nBấm Nói để thử lại."; return }
        // Thử lần lượt các cách nghe của bộ nhận dạng, lấy cách đầu tiên hiểu được
        val parsed = heard.map { it to AiParser.parse(it) }
        val best = parsed.firstOrNull { it.second !is AiCmd.Unknown } ?: parsed.first()
        aiHeard = best.first
        var cmd = best.second
        // Tìm kiếm thông minh: nói thẳng tên kênh/đài ("HTV9", "Cà Mau") mà không nói "TV" cũng mở đúng kênh
        if (cmd is AiCmd.Unknown || cmd is AiCmd.OpenApp) heard.firstNotNullOfOrNull { TvChannels.find(ctx, it) }?.let { cmd = AiCmd.Tv(it) }
        aiExec(cmd)
    }
    fun aiListen() {
        if (!voice.available()) {
            aiState = 4; aiMsg = "Máy chưa có dịch vụ nhận dạng giọng nói\n(cần app Google)"
            return
        }
        aiState = 4; aiMsg = "Cần quyền Micro để nghe"
        need(listOf(Manifest.permission.RECORD_AUDIO)) {
            aiState = 1; aiHeard = ""; aiMsg = ""; aiLevel = 0f
            voice.start(
                onPartial = { aiHeard = it },
                onLevel = { aiLevel = it },
                onResult = { list -> if (aiState == 1) aiHandle(list) },
                onError = { code -> if (aiState == 1) { aiState = 4; aiMsg = aiErrorText(code) } }
            )
        }
    }
    fun aiConfirm() {
        val p = aiCands.getOrNull(aiPick) ?: return
        when (aiKind) {
            1 -> { aiFinish(); callNow(p.number) }
            2 -> need(listOf(Manifest.permission.SEND_SMS)) {
                val ok = SmsRepo.send(ctx, p.number, aiBody)
                aiFinish(if (ok) "Đã gửi tin cho ${p.name}" else "Gửi tin không được")
            }
            else -> startCompose(p.number, p.name, "new")
        }
    }
    /** Màn hình hướng dẫn dùng AI. */
    fun aiGuide() { voice.cancel(); aiState = 5; aiMsg = ""; aiHeard = "" }
    fun aiPress(k: String) {
        val dbl = aiState != 5 && SystemClock.uptimeMillis() - aiOpenedAt < 800     // ← bấm lần 2 ngay sau lần 1 ở màn hình chờ
        when (aiState) {
            1 -> when (k) {
                "OK", "SOFTL" -> voice.stop()                                   // nói xong sớm
                "SOFTR" -> if (dbl) aiGuide() else { aiState = 0; aiMsg = ""; aiHeard = ""; voice.cancel() }
                "END" -> { aiState = 0; voice.cancel(); open("home") }
            }
            3 -> when (k) {
                "OK", "SOFTL" -> aiConfirm()
                "CALL" -> if (aiKind == 1) aiConfirm()
                "UP", "LEFT" -> aiPick = maxOf(aiPick - 1, 0)
                "DOWN", "RIGHT" -> aiPick = minOf(aiPick + 1, maxOf(aiCands.lastIndex, 0))
                "SOFTR" -> aiFinish()
                "END" -> open("home")
            }
            else -> when (k) {
                "OK", "SOFTL" -> aiListen()
                "SOFTR" -> if (dbl) aiGuide() else back()
                "END" -> open("home")
            }
        }
    }
    LaunchedEffect(aiStartTick) { if (aiStartTick > 0 && screen == "ai" && aiState != 5) aiListen() }
    // Chụp ảnh bằng giọng nói: mở máy ảnh, đợi 3 giây cho người dùng lấy khung rồi tự chụp
    LaunchedEffect(screen, camOk) {
        if (screen == "camera" && camOk && aiShoot) {
            aiShoot = false
            delay(3000)
            if (screen == "camera" && camOk) { shutterTick++; takePhoto(ctx, cam) }
        }
    }
    DisposableEffect(screen) { val sc = screen; onDispose { if (sc == "ai") voice.cancel() } }
    DisposableEffect(Unit) { onDispose { voice.cancel() } }

    fun press(k: String) {
        if (buzz) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        if (k == "END" && !locked) {   // bấm phím Tắt 2 lần liên tiếp: lần 1 về màn hình chờ, lần 2 khóa màn hình của app
            val t = SystemClock.uptimeMillis()
            val dbl = t - endAt < 600
            endAt = if (dbl) 0L else t
            if (dbl && screen == "home") { locked = true; lockAt = 0L; return }
        }
        if (locked) {   // Menu rồi * để mở khóa
            val t = SystemClock.uptimeMillis()
            if (k == "SOFTL") lockAt = t
            else if (k == "*" && lockAt != 0L && t - lockAt < 4000) { locked = false; lockAt = 0L }
            else lockAt = 0L
            return
        }
        if (screen == "youtube" || screen == "tv" || screen == "zaloweb") {
            // Gõ chữ vào ô nhập của trang bằng phím số của app: 2-9 bấm lặp = chọn chữ, * = dấu tiếng Việt,
            // # = đổi abc/Abc/ABC/123, phím mềm trái = Xóa, phím gọi (xanh) = Enter
            val web = when (screen) { "youtube" -> ytPane?.view; "tv" -> tvPane?.view; else -> zwPane?.view }
            when {
                k == "SOFTL" -> { WebTyping.type(web, webEntry, "DEL"); return }
                k == "CALL" -> { WebTyping.enter(web); return }
                k == "#" -> { webEntry.key("#", SystemClock.uptimeMillis()); Toast.makeText(ctx, MODES[webEntry.mode], Toast.LENGTH_SHORT).show(); return }
                k.length == 1 && (k[0].isDigit() || k == "*") -> { WebTyping.type(web, webEntry, k); return }
            }
        }
        if ((screen == "youtube" || screen == "tv" || screen == "zaloweb") && (k == "UP" || k == "DOWN" || k == "LEFT" || k == "RIGHT" || k == "OK")) {
            val code = when (k) {
                "UP" -> android.view.KeyEvent.KEYCODE_DPAD_UP; "DOWN" -> android.view.KeyEvent.KEYCODE_DPAD_DOWN
                "LEFT" -> android.view.KeyEvent.KEYCODE_DPAD_LEFT; "RIGHT" -> android.view.KeyEvent.KEYCODE_DPAD_RIGHT
                else -> android.view.KeyEvent.KEYCODE_DPAD_CENTER
            }
            (when (screen) { "youtube" -> ytPane?.view; "tv" -> tvPane?.view; else -> zwPane?.view })?.let { v ->
                v.requestFocus()
                v.dispatchKeyEvent(android.view.KeyEvent(android.view.KeyEvent.ACTION_DOWN, code))
                v.dispatchKeyEvent(android.view.KeyEvent(android.view.KeyEvent.ACTION_UP, code))
            }
            return
        }
        if (wallPick != null) {   // Xem thử hình nền: ▲▼◀▶ dời ảnh, OK = đặt, Về = hủy
            val st = 0.08f
            when (k) {
                "OK", "SOFTL" -> confirmWall()
                "SOFTR", "END" -> wallPick = null
                "LEFT" -> wallFx = (wallFx + st).coerceIn(0f, 1f)     // ảnh dịch sang trái
                "RIGHT" -> wallFx = (wallFx - st).coerceIn(0f, 1f)
                "UP" -> wallFy = (wallFy + st).coerceIn(0f, 1f)
                "DOWN" -> wallFy = (wallFy - st).coerceIn(0f, 1f)
            }
            return
        }
        if (screen == "recConfirm") {   // Xóa bản ghi? OK/Có = xóa, phím khác = hủy
            if (k == "OK" || k == "SOFTL") { deleteRec(sel); screen = "recorder" }
            else if (k == "END") open("home")
            else if (k == "SOFTR" || k == "UP" || k == "DOWN" || k == "LEFT" || k == "RIGHT") screen = "recorder"
            return
        }
        if (screen == "galConfirm") {   // Xóa ảnh? (Android 10 trở xuống) OK/Có = xóa, phím khác = hủy
            if (k == "OK" || k == "SOFTL") {
                screen = "gallery"
                if (Build.VERSION.SDK_INT < 29) need(listOf(Manifest.permission.WRITE_EXTERNAL_STORAGE)) { runDeletePhoto() }
                else runDeletePhoto()
            }
            else if (k == "END") open("home")
            else if (k == "SOFTR" || k == "UP" || k == "DOWN" || k == "LEFT" || k == "RIGHT") screen = "gallery"
            return
        }
        if (screen == "smsConfirm") {   // Xóa tin? OK/Có = xóa, phím khác = hủy
            if (k == "OK" || k == "SOFTL") doDeleteSms()
            else if (k == "END") open("home")
            else if (k == "SOFTR" || k == "UP" || k == "DOWN" || k == "LEFT" || k == "RIGHT") screen = delBack
            return
        }
        if (screen == "ai") { aiPress(k); return }
        val size = when (screen) { "menu" -> MENU.size; "apps" -> maxOf(apps.size, 1); "settings" -> SETTINGS.size; "sound", "volume" -> 3; "clock" -> alarms.size + 1
            "wifi" -> wifiNets.size + 1; "sim" -> simRows(ctx).size; "brightness" -> 1; "battery" -> 3; "storage" -> 3
            "accounts" -> maxOf(Accts.google(ctx).size, 1); "power" -> 2
            "contacts" -> maxOf(cShown.size, 1); "messages" -> threads.size + 1
            "thread" -> threadMsgs.size + 1; "msgview" -> maxOf(threadMsgs.size, 1); "recorder" -> recs.size + 1
            "calllog" -> maxOf(calls.size, 1); "music" -> maxOf(tracks.size, 1)
            else -> 1 }
        when (k) {
            "UP", "LEFT" -> when (screen) {
                "home" -> shortcut(if (k == "UP") "clock" else "calendar")      // ▲ Đồng hồ, ◀ Lịch
                "music" -> if (k == "LEFT") musicStep(-1) else sel = maxOf(sel - 1, 0)
                "snake" -> snake.turn(if (k == "UP") 0 to -1 else -1 to 0)
                "apps" -> if (appsMode == 1) gridMove(if (k == "UP") -APP_COLS else -1) else sel = maxOf(sel - 1, 0)
                "recording" -> if (k == "UP") togglePause()   // chỉ ▲ tạm dừng / tiếp tục; ◀ không làm gì
                "calendar" -> if (k == "LEFT") monthOffset--   // ▲▼ không phản hồi
                "volume" -> if (k == "LEFT") adjustVol(-1) else sel = maxOf(sel - 1, 0)
                "brightness" -> if (k == "LEFT" && sel == 0) adjustBright(-1) else sel = maxOf(sel - 1, 0)
                "alarmEdit", "powerEdit" -> editMove(k)
                "calc" -> calc.op(if (k == "UP") '+' else '×')
                "gallery" -> if (gIds.isNotEmpty()) gIdx = maxOf(gIdx - 1, 0)   // không nhảy vòng xuống cuối danh sách
                "camera" -> if (k == "LEFT") { camFront = !camFront; flash = false }
                else if (camFront) Toast.makeText(ctx, "Camera trước không có đèn flash", Toast.LENGTH_SHORT).show()
                else if (!cam.hasFlash) Toast.makeText(ctx, "Máy không có đèn flash", Toast.LENGTH_SHORT).show()
                else flash = !flash
                "msgview" -> mv = maxOf(mv - 1, 0)
                "compose" -> if (cStage == 0) pickContact(-1)
                else -> sel = maxOf(sel - 1, 0)
            }
            "DOWN", "RIGHT" -> when (screen) {
                "home" -> shortcut(if (k == "DOWN") "calc" else "music")       // ▼ Máy tính, ▶ Nhạc
                "music" -> if (k == "RIGHT") musicStep(1) else sel = minOf(sel + 1, size - 1)
                "snake" -> snake.turn(if (k == "DOWN") 0 to 1 else 1 to 0)
                "apps" -> if (appsMode == 1) gridMove(if (k == "DOWN") APP_COLS else 1) else sel = minOf(sel + 1, size - 1)
                "calendar" -> if (k == "RIGHT") monthOffset++   // ▲▼ không phản hồi
                "volume" -> if (k == "RIGHT") adjustVol(1) else sel = minOf(sel + 1, size - 1)
                "brightness" -> if (k == "RIGHT" && sel == 0) adjustBright(1) else sel = minOf(sel + 1, size - 1)
                "alarmEdit", "powerEdit" -> editMove(k)
                "calc" -> calc.op(if (k == "DOWN") '−' else '÷')
                "gallery" -> if (gIds.isNotEmpty()) gIdx = minOf(gIdx + 1, gIds.size - 1)
                "camera" -> if (k == "RIGHT") { camFront = !camFront; flash = false }
                "msgview" -> mv = minOf(mv + 1, size - 1)
                "compose" -> if (cStage == 0) pickContact(1)
                else -> sel = minOf(sel + 1, size - 1)
            }
            "OK", "SOFTL" -> when (screen) {
                "home" -> open("menu")
                "menu" -> when (val id = MENU[sel].second) {
                    else -> open(id)
                }
                "apps" -> apps.getOrNull(sel)?.let { a ->
                    ctx.packageManager.getLaunchIntentForPackage(a.pkg)?.let { launch(it) }
                }
                "settings" -> {
                    settingsSel = sel
                    when (SETTINGS.getOrElse(sel) { "reset" }) {
                        "wallpaper" -> try { wallReq.launch("image/*") } catch (_: Exception) { Toast.makeText(ctx, "Máy không có app chọn ảnh", Toast.LENGTH_SHORT).show() }
                        "launcher" -> launch(Intent(Settings.ACTION_HOME_SETTINGS))
                        "sound" -> open("sound")
                        "wifi" -> open("wifi")
                        "sim" -> open("sim")
                        // Android không cho app thường tự bật/tắt 2 mục này -> mở đúng màn hình của hệ thống
                        "airplane" -> openSettings(ctx, Settings.ACTION_AIRPLANE_MODE_SETTINGS, Settings.ACTION_WIRELESS_SETTINGS)
                        "hotspot" -> openSettings(ctx, "android.settings.TETHER_SETTINGS", Settings.ACTION_WIRELESS_SETTINGS)
                        "brightness" -> open("brightness")
                        "battery" -> open("battery")
                        "storage" -> open("storage")
                        "accounts" -> open("accounts")
                        "power" -> open("power")
                        "bt" -> toggleBt()
                        "dialer" -> requestDialer()
                        "smsapp" -> requestSmsApp()
                        else -> open("reset")
                    }
                }
                "wifi" -> when {
                    !granted(Manifest.permission.ACCESS_FINE_LOCATION) ->
                        need(Wifi.perms(), listOf(Manifest.permission.ACCESS_FINE_LOCATION)) { loadWifi() }
                    sel == 0 -> {
                        if (Wifi.toggle(ctx)) scope.launch { delay(2500); if (screen == "wifi") loadWifi() }
                        infoTick++
                    }
                    else -> pickWifi(sel)
                }
                "sim" -> simOk { need(Sims.perms(), listOf(Manifest.permission.READ_PHONE_STATE)) { infoTick++ } }
                "brightness" -> adjustBright(1, true)
                "battery" -> if (sel == 2) openSettings(ctx, Settings.ACTION_BATTERY_SAVER_SETTINGS)
                "accounts" -> if (!granted(Manifest.permission.GET_ACCOUNTS)) need(listOf(Manifest.permission.GET_ACCOUNTS)) { infoTick++ }
                    else openSettings(ctx, Settings.ACTION_SYNC_SETTINGS)
                "power" -> startPowerEdit(sel)
                "powerEdit" -> savePower()
                "sound" -> when (sel) {
                    0 -> ringPick.launch(Sound.pickerIntent(ctx, RingtoneManager.TYPE_RINGTONE))
                    1 -> open("volume")
                    else -> alarmPick.launch(Sound.pickerIntent(ctx, RingtoneManager.TYPE_ALARM))
                }
                "volume" -> adjustVol(1, true)
                "reset" -> doReset()
                "snake" -> if (snake.dead) snake.reset()
                "clock" -> startEdit(sel)
                "calendar" -> monthOffset = 0
                "alarmEdit" -> if (editField == 3 && editIdx < alarms.size) deleteAlarm() else saveAlarm()
                "contacts" -> cShown.getOrNull(sel)?.let { callNow(it.number) }
                "messages" -> if (sel == 0) startCompose("", "", "new") else threads.getOrNull(sel - 1)?.let { openThread(it.key) }
                "thread" -> if (sel == 0) startCompose(curAddr, curName, "thread") else { mv = sel - 1; screen = "msgview" }
                "msgview" -> startCompose(curAddr, curName, "thread")
                "compose" -> if (cStage == 0) { if (cTo.isNotEmpty()) cStage = 1 }
                    else if (k == "SOFTL") doSend() else entry.newline()
                "calc" -> if (k == "OK") calc.compute() else calc.back()
                "camera" -> if (camOk) { shutterTick++; takePhoto(ctx, cam) }
                "recorder" -> if (sel == 0) startRecording() else togglePlay(sel)
                "recording" -> stopRecording()
                "gallery" -> if (k == "SOFTL") askDeletePhoto()     // phím mềm trái = Xóa ảnh
                "calllog" -> if (!granted(Manifest.permission.READ_CALL_LOG)) need(listOf(Manifest.permission.READ_CALL_LOG)) { loadCalls() }
                    else calls.getOrNull(sel)?.let { callNow(it.number) }
                "music" -> if (!audioPerms.all { granted(it) }) need(audioPerms) { loadTracks() }
                    else if (tracks.isNotEmpty()) { if (sel == mCur && Music.active) Music.toggle() else playTrack(sel) }
            }
            "SOFTR" -> if (screen == "home") { if (dial.isNotEmpty()) dial = dial.dropLast(1) else shortcut("ai") }
                else if (screen == "compose" && cStage == 0 && cTo.isNotEmpty()) { cTo = cTo.dropLast(1); cName = "" }
                else if (screen == "compose" && cStage == 1 && entry.text.isNotEmpty()) entry.backspace()
                else if (screen == "contacts" && entry.text.isNotEmpty()) { entry.backspace(); sel = 0 }
                else back()
            "END" -> if (screen == "home") dial = "" else open("home")
            "CALL" -> when (screen) {
                "home" -> if (dial.isNotEmpty()) callNow(dial) else shortcut("calllog")   // chưa gõ số: phím gọi xanh mở Nhật ký
                "contacts" -> cShown.getOrNull(sel)?.let { callNow(it.number) }
                "thread", "msgview" -> callNow(dialable(curAddr))
                "calllog" -> calls.getOrNull(sel)?.let { callNow(it.number) }
            }
            else -> when (screen) {
                "home" -> if (dial.length < 12) dial += k      // tối đa 12 số
                "apps" -> if (k == "#") appsMode = 1 - appsMode   // # đổi Chữ / Icon
                "snake" -> when (k) {
                    "2" -> snake.turn(0 to -1); "8" -> snake.turn(0 to 1)
                    "4" -> snake.turn(-1 to 0); "6" -> snake.turn(1 to 0)
                }
                "calc" -> when (k) {
                    "*" -> calc.comma()
                    "#" -> calc.clear()
                    else -> if (k.length == 1 && k[0].isDigit()) calc.digit(k)
                }
                "contacts" -> if (k == "#") { entry.mode = if (entry.mode == 3) 0 else 3 }     // # đổi chữ <-> số
                    else if (k != "*") { entry.key(k, SystemClock.uptimeMillis()); sel = 0 }
                "compose" -> if (cStage == 0) {
                    if (k[0].isDigit()) { cTo += k; cName = "" } else if (k == "*" && cTo.isEmpty()) cTo = "+"
                } else entry.key(k, SystemClock.uptimeMillis())
                "recorder" -> if (k == "#" && sel > 0) screen = "recConfirm"
                "messages" -> if (k == "#" && sel > 0) threads.getOrNull(sel - 1)?.let { t ->
                    askDeleteSms(smsAll.filter { addrKey(it.addr) == t.key }, "messages", true, t.name) }
                "thread" -> if (k == "#" && sel > 0) askDeleteSms(listOfNotNull(threadMsgs.getOrNull(sel - 1)), "thread", false)
                "msgview" -> if (k == "#") askDeleteSms(listOfNotNull(threadMsgs.getOrNull(mv)), "thread", false)
                "music" -> when (k) {
                    "4" -> Music.seek(-10_000L)                                  // tua lùi 10 giây
                    "6" -> Music.seek(10_000L)                                   // tua tới 10 giây
                    "5" -> if (Music.active) Music.toggle() else if (tracks.isNotEmpty()) playTrack(sel)
                    "0" -> { Music.stop(); mCur = -1 }
                    "2" -> adjustMusicVol(1)
                    "8" -> adjustMusicVol(-1)
                }
            }
        }
    }

    // press là hàm cục bộ, mỗi lần vẽ lại là một bản mới (nhớ cShown, cLines... của lần vẽ đó). Bàn phím ảo và danh sách
    // (pointerInput(Unit)) hay giữ lại bản cũ -> danh bạ lúc đó còn trống nên ▲▼ không đi được. Luôn gọi qua bản mới nhất:
    val pressNow = rememberUpdatedState<(String) -> Unit>({ k -> press(k) })
    val tapItem: (Int) -> Unit = { i -> sel = i; pressNow.value("OK") }
    val scroll: (Int) -> Unit = { d -> pressNow.value(if (d > 0) "DOWN" else "UP") }

    SideEffect { NokiaState.listener = { k -> press(k) } }
    BackHandler { if (wallPick != null) wallPick = null else if (!locked) back() }

    val left = when (screen) {
        "home" -> "Menu"; "menu", "apps" -> "Chọn"; "settings", "sound", "wifi", "sim", "power" -> "Chọn"; "reset" -> "Có"
        "brightness" -> "Đổi"; "battery", "accounts" -> "Mở"; "powerEdit" -> "Lưu"
        "snake" -> if (snake.dead) "Chơi lại" else ""
        "clock" -> "Sửa"; "calendar" -> "Hôm nay"
        "contacts" -> "Gọi"; "messages", "thread" -> "Chọn"; "msgview" -> "Trả lời"
        "compose" -> if (cStage == 1) "Gửi" else "Tiếp"
        "calc" -> "Xóa"; "camera" -> "Chụp"; "recording" -> "Lưu"; "recConfirm", "smsConfirm", "galConfirm" -> "Có"
        "gallery" -> if (gIds.isNotEmpty()) "Xóa" else ""
        "recorder" -> if (sel == 0) "Ghi" else if (playing != null && playing == recs.getOrNull(sel - 1)?.file?.absolutePath) "Dừng" else "Nghe"
        "alarmEdit" -> if (editField == 3 && editIdx < alarms.size) "Xóa" else "Lưu"
        "calllog" -> "Gọi"
        "youtube", "tv", "zaloweb" -> "Xóa"   // xóa 1 ký tự đang gõ trong ô nhập của trang
        "ai" -> when (aiState) { 1 -> "Xong"; 3 -> (if (aiKind == 1) "Gọi" else if (aiKind == 2) "Gửi" else "Soạn"); else -> "Nói" }
        "music" -> if (Music.active && sel == mCur) (if (Music.paused) "Tiếp" else "Tạm dừng") else "Phát"
        else -> ""
    }
    val right = when (screen) {
        "home" -> if (dial.isEmpty()) "AI" else "Xóa"
        "ai" -> when (aiState) { 1 -> "Hủy"; 3 -> "Không"; else -> "Về" }
        "reset", "recConfirm", "smsConfirm", "galConfirm" -> "Không"
        "compose" -> if ((cStage == 0 && cTo.isNotEmpty()) || (cStage == 1 && entry.text.isNotEmpty())) "Xóa" else "Về"
        "contacts" -> if (entry.text.isNotEmpty()) "Xóa" else "Về"
        else -> "Về"
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        // Tỉ lệ gốc của màn hình điện thoại thật -> làm chuẩn cho màn hình LCD nhỏ
        val ratio = maxWidth / maxHeight
        Column(
            Modifier.fillMaxSize()
                .background(Brush.verticalGradient(listOf(Color(0xFF4469BC), Color(0xFF223C78))))
                .padding(bottom = 10.dp)
                .focusRequester(focus).focusable()
                .onKeyEvent { e ->
                    if (e.type != KeyEventType.KeyDown) return@onKeyEvent false
                    val c = e.utf16CodePoint.toChar()
                    when {
                        e.key == Key.DirectionUp -> press("UP")
                        e.key == Key.DirectionDown -> press("DOWN")
                        e.key == Key.DirectionLeft -> press("LEFT")
                        e.key == Key.DirectionRight -> press("RIGHT")
                        e.key == Key.DirectionCenter || e.key == Key.Enter -> press("OK")
                        e.key == Key.Backspace -> press("SOFTR")
                        c.isDigit() || c == '*' || c == '#' -> press(c.toString())
                        else -> return@onKeyEvent false
                    }
                    true
                }
        ) {
            // Màn hình LCD nhỏ (phần duy nhất thay đổi)
            Box(Modifier.fillMaxWidth().weight(0.72f).background(LCD), contentAlignment = Alignment.Center) {
            LcdFontScale {
            Box(Modifier.fillMaxSize()) {
                Column(Modifier.fillMaxSize().background(LCD).statusBarsPadding()) {
                    Box(Modifier.weight(1f).fillMaxWidth().onGloballyPositioned { lcdRect = it.boundsInWindow() }
                        // Camera: khung xem trước phủ kín vùng LCD, không chừa rìa hai bên
                        .padding(horizontal = if ((screen == "camera" && camOk) || screen == "youtube" || screen == "tv" || screen == "zaloweb") 0.dp else 6.dp,
                            vertical = if ((screen == "camera" && camOk) || screen == "youtube" || screen == "tv" || screen == "zaloweb") 0.dp else 2.dp)) {
                        when (screen) {
                            "home" -> Column(Modifier.fillMaxSize().clickable { press("OK") }, Arrangement.SpaceEvenly, Alignment.CenterHorizontally) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(now.take(5), color = INK, fontSize = 70.sp, fontWeight = FontWeight.Bold, fontFamily = MONO)
                                    Text(dateNow(), color = INK, fontSize = 20.sp, fontWeight = FontWeight.Bold, fontFamily = MONO,
                                        maxLines = 2, textAlign = TextAlign.Center)
                                }
                                Text(dial.takeLast(12), color = INK, fontSize = 48.sp, fontWeight = FontWeight.Bold, fontFamily = MONO, maxLines = 1, overflow = TextOverflow.Clip)
                            }
                            "ai" -> AiScreen(aiState, aiHeard, aiMsg, aiLevel, aiCands, aiPick, aiKind, aiBody)
                            "menu" -> Lines(MENU.map { it.first }, sel, tapItem, scroll)
                            "youtube" -> WebArea(ytPane?.view, fullView, false) { back() }
                            "zaloweb" -> WebArea(zwPane?.view, fullView) { back() }
                            "tv" -> WebArea(tvPane?.view, fullView) { back() }
                            "apps" -> AppsScreen(apps, appsMode, sel, { appsMode = it }, tapItem, scroll)
                            "clock" -> Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(now, Modifier.padding(top = 16.dp), color = INK, fontSize = 40.sp, fontWeight = FontWeight.Bold, fontFamily = MONO)
                                Text(dateNow(), color = INK, fontSize = 16.sp, fontWeight = FontWeight.Bold, fontFamily = MONO)
                                Spacer(Modifier.height(14.dp))
                                Text("BÁO THỨC", Modifier.fillMaxWidth().background(INK).padding(horizontal = 6.dp, vertical = 2.dp),
                                    color = LCD, fontSize = 12.sp, fontWeight = FontWeight.Bold, fontFamily = MONO)
                                Box(Modifier.weight(1f).fillMaxWidth()) {
                                    Lines(alarms.map { "%02d:%02d   %s".format(it.h, it.m, if (it.on) "BẬT" else "TẮT") } + "+ Thêm báo thức",
                                        sel, tapItem, scroll)
                                }
                            }
                            "alarmEdit" -> Column(Modifier.fillMaxSize(), Arrangement.SpaceEvenly, Alignment.CenterHorizontally) {
                                Text(if (editIdx < alarms.size) "Sửa báo thức" else "Báo thức mới",
                                    color = INK, fontSize = 16.sp, fontWeight = FontWeight.Bold, fontFamily = MONO)
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    FieldBox("%02d".format(eh), editField == 0, 56) { if (editField == 0) editMove("UP") else editField = 0 }
                                    Text(":", color = INK, fontSize = 56.sp, fontWeight = FontWeight.Bold, fontFamily = MONO)
                                    FieldBox("%02d".format(em), editField == 1, 56) { if (editField == 1) editMove("UP") else editField = 1 }
                                }
                                FieldBox(if (eon) "Báo thức: BẬT" else "Báo thức: TẮT", editField == 2, 18) { if (editField == 2) editMove("UP") else editField = 2 }
                                if (editIdx < alarms.size) FieldBox("Xóa báo thức", editField == 3, 18) { if (editField == 3) deleteAlarm() else editField = 3 }
                                Text("◀ ▶ chọn ô    ▲ ▼ đổi giá trị", color = INK, fontSize = 12.sp, fontFamily = MONO)
                            }
                            "calendar" -> MonthCalendar(monthOffset) { d -> monthOffset += d }
                            "settings" -> { val t = infoTick
                                Lines(SETTINGS.map { settingLabel(ctx, it, btOn, dialerOn, smsOn, wallBmp != null) }, sel, tapItem, scroll) }
                            "wifi" -> { val t = infoTick
                                if (!granted(Manifest.permission.ACCESS_FINE_LOCATION)) Msg("Cần quyền Vị trí để quét wifi\n(bấm OK để cấp)")
                                else Lines(wifiLines(ctx, wifiNets), sel, tapItem, scroll) }
                            "sim" -> { val t = infoTick
                                Lines(simRows(ctx).map { it.first }, sel, tapItem, scroll) }
                            "brightness" -> { val t = infoTick; val b = Bright.get(ctx)
                                Lines(listOf("Độ sáng " + Bright.bar(b) + " " + Bright.pct(b) + "%"), sel, tapItem, scroll) }
                            "battery" -> { val t = infoTick
                                Lines(listOf("Pin: " + Batt.percent(ctx) + "%" + if (Batt.charging(ctx)) " (đang sạc)" else "",
                                    "Dùng hôm nay: " + fmtDur(UsageTracker.todayMs(ctx)),
                                    "Tiết kiệm pin: " + (if (Batt.saverOn(ctx)) "BẬT" else "TẮT") + " (mở)"), sel, tapItem, scroll) }
                            "storage" -> { val t = infoTick; val (total, free) = Store.info()
                                Lines(listOf("Tổng: " + Store.gb(total), "Trống: " + Store.gb(free),
                                    "Đã dùng: " + Store.gb((total - free).coerceAtLeast(0L))), sel, tapItem, scroll) }
                            "accounts" -> { val t = infoTick; val acc = Accts.google(ctx)
                                if (!granted(Manifest.permission.GET_ACCOUNTS)) Msg("Cần quyền để xem tài khoản\n(bấm OK để cấp)")
                                else if (acc.isEmpty()) Msg("Không thấy tài khoản Google\n(bấm OK để mở Tài khoản)")
                                else Lines(acc, sel, tapItem, scroll) }
                            "power" -> { val t = infoTick
                                Column(Modifier.fillMaxSize()) {
                                    Head("LỊCH BẬT TẮT NGUỒN")
                                    Hint(if (NokiaAccessibilityService.instance != null) "Tắt/bật màn hình theo giờ" else "Cần bật Trợ năng để tắt màn hình")
                                    Spacer(Modifier.height(4.dp))
                                    Box(Modifier.weight(1f)) {
                                        Lines(listOf("Tắt lúc: %02d:%02d  %s".format(power.offH, power.offM, if (power.offOn) "BẬT" else "TẮT"),
                                            "Bật lúc: %02d:%02d  %s".format(power.onH, power.onM, if (power.onOn) "BẬT" else "TẮT")),
                                            sel, tapItem, scroll)
                                    }
                                } }
                            "powerEdit" -> Column(Modifier.fillMaxSize(), Arrangement.SpaceEvenly, Alignment.CenterHorizontally) {
                                Text(if (pEdit == 0) "Giờ tắt màn hình" else "Giờ bật màn hình",
                                    color = INK, fontSize = 16.sp, fontWeight = FontWeight.Bold, fontFamily = MONO)
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    FieldBox("%02d".format(eh), editField == 0, 56) { if (editField == 0) editMove("UP") else editField = 0 }
                                    Text(":", color = INK, fontSize = 56.sp, fontWeight = FontWeight.Bold, fontFamily = MONO)
                                    FieldBox("%02d".format(em), editField == 1, 56) { if (editField == 1) editMove("UP") else editField = 1 }
                                }
                                FieldBox((if (pEdit == 0) "Tắt: " else "Bật: ") + if (eon) "BẬT" else "TẮT", editField == 2, 18) {
                                    if (editField == 2) editMove("UP") else editField = 2
                                }
                                Text("◀ ▶ chọn ô    ▲ ▼ đổi giá trị", color = INK, fontSize = 12.sp, fontFamily = MONO)
                            }
                            "sound" -> Lines(listOf("Nhạc chuông: $ringName", "Âm lượng", "Nhạc báo thức: $alarmName"), sel, tapItem, scroll)
                            "volume" -> Lines(VOL.map { (n, st) -> volTick.let { volLine(ctx, n, st) } }, sel, tapItem, scroll)
                            "reset" -> Column(Modifier.fillMaxSize().padding(8.dp), Arrangement.Center, Alignment.CenterHorizontally) {
                                Text("Khôi phục cài đặt gốc?", color = INK, fontSize = 20.sp, fontWeight = FontWeight.Bold,
                                    fontFamily = MONO, textAlign = TextAlign.Center)
                                Spacer(Modifier.height(10.dp))
                                Text("Xóa toàn bộ dữ liệu của riêng app phonecuibap (báo thức, nhạc báo thức, quyền đã cấp). Máy của bạn không bị ảnh hưởng.",
                                    color = INK, fontSize = 14.sp, fontFamily = MONO, textAlign = TextAlign.Center)
                            }
                            "contacts" -> Column(Modifier.fillMaxSize()) {
                                val q = entry.text
                                Head("DANH BẠ  (" + (if (q.isEmpty()) contacts.size else cShown.size) + ")")
                                if (contacts.isEmpty()) Box(Modifier.weight(1f)) {
                                    Msg(if (!granted(Manifest.permission.READ_CONTACTS)) "Cần quyền Danh bạ" else if (!ctLoaded) "Đang truy xuất danh bạ..." else "Danh bạ trống")
                                } else {
                                    // Một ô tìm duy nhất: gõ chữ cái đầu hoặc tên / số bất kỳ (bấm lặp phím số như soạn tin, # đổi chữ <-> số)
                                    Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 2.dp)
                                        .border(2.dp, INK).padding(horizontal = 6.dp, vertical = 3.dp),
                                        verticalAlignment = Alignment.CenterVertically) {
                                        Text(if (q.isEmpty()) "Gõ tên hoặc số để tìm" else q.takeLast(18) + "_", Modifier.weight(1f),
                                            color = if (q.isEmpty()) INK.copy(alpha = 0.55f) else INK, fontFamily = MONO,
                                            fontWeight = FontWeight.Bold, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Clip)
                                        Text(if (entry.mode == 3) "123" else "abc", color = INK, fontFamily = MONO, fontSize = 12.sp)
                                    }
                                    Spacer(Modifier.height(2.dp))
                                    Box(Modifier.weight(1f)) {
                                        if (cShown.isEmpty()) Msg("Không tìm thấy") else Lines(cLines, sel, tapItem, scroll)
                                    }
                                }
                            }
                            "messages" -> Column(Modifier.fillMaxSize()) {
                                Box(Modifier.weight(1f)) {
                                    Lines(listOf("+ Soạn tin mới") + threads.map {
                                        (if (it.last.sent) "→ " else "") + it.name + ": " + it.last.body.replace('\n', ' ')
                                    }, sel, tapItem, scroll)
                                }
                                if (sel > 0) Hint("#: xóa hội thoại")
                            }
                            "thread" -> Column(Modifier.fillMaxSize()) {
                                Head(curName)
                                Box(Modifier.weight(1f)) {
                                    Lines(listOf("+ Trả lời") + threadMsgs.map {
                                        (if (it.sent) "→ " else "← ") + it.body.replace('\n', ' ')
                                    }, sel, tapItem, scroll)
                                }
                                if (sel > 0) Hint("#: xóa tin")
                            }
                            "msgview" -> MsgView(curName, threadMsgs.getOrNull(mv), mv, threadMsgs.size)
                            "compose" -> ComposeView(cStage, cTo, cName, entry)
                            "calc" -> CalcScreen(calc)
                            "camera" -> if (camOk) Box(Modifier.fillMaxSize().clipToBounds()) {
                                CameraView(cam, camFront, flash)
                                Box(Modifier.fillMaxSize().clickable { press("OK") })
                                Text((if (camFront) "Trước" else "Sau") + "  ◀▶ đổi  ▲ flash: " + (if (flash) "BẬT" else "TẮT"),
                                    Modifier.align(Alignment.TopCenter).background(LCD).padding(horizontal = 6.dp),
                                    color = INK, fontFamily = MONO, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                // Hiệu ứng "đóng khung" khi chụp: 4 cạnh khép vào giữa rồi mở lại
                                Canvas(Modifier.fillMaxSize()) {
                                    val p = shut.value
                                    if (p > 0f) {
                                        val hh = size.height / 2f * p
                                        val ww = size.width / 2f * p
                                        drawRect(INK, Offset(0f, 0f), Size(size.width, hh))
                                        drawRect(INK, Offset(0f, size.height - hh), Size(size.width, hh))
                                        drawRect(INK, Offset(0f, 0f), Size(ww, size.height))
                                        drawRect(INK, Offset(size.width - ww, 0f), Size(ww, size.height))
                                    }
                                }
                            } else Msg("Cần quyền Máy ảnh")
                            "gallery" -> GalleryView(gOk, gLoaded, gIds.size, gIdx, gBmp) { d ->
                                if (gIds.isNotEmpty()) { gIdx = (gIdx + d).coerceIn(0, gIds.size - 1) }
                            }
                            "recorder" -> Column(Modifier.fillMaxSize()) {
                                val t = recTick
                                Head("GHI ÂM  (${recs.size})")
                                val pr = recs.firstOrNull { it.file.absolutePath == playing }
                                Hint(if (pr != null) recProgress(pr) else "OK: ghi/nghe   #: xóa")
                                Spacer(Modifier.height(4.dp))
                                Box(Modifier.weight(1f)) { Lines(recLines(recs, playing, t), sel, tapItem, scroll) }
                            }
                            "recording" -> Column(Modifier.fillMaxSize()) {
                                val t = recTick
                                Head(if (recPaused) "‖ TẠM DỪNG" else if ((t / 2) % 2 == 0) "● ĐANG GHI ÂM" else "○ ĐANG GHI ÂM")
                                Column(Modifier.weight(1f).fillMaxWidth().clickable { press("OK") }, Arrangement.SpaceEvenly, Alignment.CenterHorizontally) {
                                    Text(mmss(Rec.elapsed()), color = INK, fontFamily = MONO, fontWeight = FontWeight.Bold, fontSize = 56.sp)
                                    val lv = if (recPaused) 0f else (Rec.level() / 32767f).coerceIn(0f, 1f)
                                    val bars = (Math.sqrt(lv.toDouble()) * 16).toInt().coerceIn(0, 16)
                                    Text("█".repeat(bars) + "░".repeat(16 - bars), color = INK, fontFamily = MONO, fontSize = 18.sp)
                                    Text((if (recPaused) "▲: tiếp tục" else "▲: tạm dừng") + "\nOK: lưu", color = INK, fontFamily = MONO, fontSize = 13.sp, textAlign = TextAlign.Center)
                                }
                            }
                            "galConfirm" -> Column(Modifier.fillMaxSize().padding(8.dp), Arrangement.Center, Alignment.CenterHorizontally) {
                                Text("Xóa ảnh này?", color = INK, fontFamily = MONO, fontWeight = FontWeight.Bold,
                                    fontSize = 20.sp, textAlign = TextAlign.Center)
                                Spacer(Modifier.height(8.dp))
                                Text((gIdx + 1).toString() + "/" + gIds.size, color = INK, fontFamily = MONO, fontSize = 14.sp)
                            }
                            "smsConfirm" -> Column(Modifier.fillMaxSize().padding(8.dp), Arrangement.Center, Alignment.CenterHorizontally) {
                                Text(if (delWhole) "Xóa cả hội thoại?" else "Xóa tin nhắn này?", color = INK, fontFamily = MONO,
                                    fontWeight = FontWeight.Bold, fontSize = 20.sp, textAlign = TextAlign.Center)
                                Spacer(Modifier.height(8.dp))
                                Text(if (delWhole) delName + "  (" + delTarget.size + " tin)" else delTarget.firstOrNull()?.body.orEmpty().replace('\n', ' '),
                                    color = INK, fontFamily = MONO, fontSize = 14.sp, textAlign = TextAlign.Center,
                                    maxLines = 3, overflow = TextOverflow.Ellipsis)
                                Spacer(Modifier.height(8.dp))
                                Text(if (smsOn) "Xóa hẳn khỏi máy" else "Chỉ xóa trong app này", color = INK.copy(alpha = 0.6f), fontFamily = MONO, fontSize = 12.sp)
                            }
                            "recConfirm" -> Column(Modifier.fillMaxSize().padding(8.dp), Arrangement.Center, Alignment.CenterHorizontally) {
                                val r = recs.getOrNull(sel - 1)
                                Text("Xóa bản ghi này?", color = INK, fontFamily = MONO, fontWeight = FontWeight.Bold,
                                    fontSize = 20.sp, textAlign = TextAlign.Center)
                                if (r != null) {
                                    Spacer(Modifier.height(8.dp))
                                    Text(SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date(r.file.lastModified())) +
                                        "  " + mmss(r.dur), color = INK, fontFamily = MONO, fontSize = 14.sp)
                                }
                            }
                            "calllog" -> Column(Modifier.fillMaxSize()) {
                                val cOk = granted(Manifest.permission.READ_CALL_LOG)
                                Head("NHẬT KÝ  (${calls.size})")
                                if (!cOk) Box(Modifier.weight(1f)) { Msg("Cần quyền Nhật ký cuộc gọi\n(bấm OK để cấp)") }
                                else if (calls.isEmpty()) Box(Modifier.weight(1f)) { Msg(if (clLoaded) "Chưa có cuộc gọi nào" else "Đang tải...") }
                                else {
                                    val e = calls.getOrNull(sel)
                                    // Ngày giờ + số hiện ngay dưới ô đang chọn
                                    val info = if (e == null) emptyList() else listOf(CallHistory.detail(e))   // số nằm ngay sau chữ Đi/Đến/Nhỡ, gộp một dòng
                                    Box(Modifier.weight(1f)) { Lines(calls.map { CallHistory.line(it) }, sel, tapItem, scroll, info) }
                                }
                            }
                            "music" -> Column(Modifier.fillMaxSize()) {
                                val t = mTick
                                val mAudioOk = audioPerms.all { granted(it) }
                                Head("NHẠC  (${tracks.size})")
                                if (!mAudioOk) Box(Modifier.weight(1f)) { Msg("Cần quyền truy cập nhạc\n(bấm OK để cấp)") }
                                else if (tracks.isEmpty()) Box(Modifier.weight(1f)) {
                                    Msg(if (mLoaded) "Không thấy bài nhạc nào\ntrong bộ nhớ / thẻ nhớ" else "Đang tải...")
                                } else {
                                    Hint(if (Music.active && mCur in tracks.indices) musicProgress() else "OK:phát ◀▶:bài 4/6:tua")
                                    Hint(tracks.getOrNull(sel)?.let { trackInfo(it) } ?: "")
                                    Spacer(Modifier.height(4.dp))
                                    val isPaused = Music.paused
                                    val mLines = remember(tracks, mCur, isPaused) { musicLines(tracks, mCur, isPaused) }
                                    Box(Modifier.weight(1f)) { Lines(mLines, sel, tapItem, scroll) }
                                }
                            }
                            "snake" -> Column(Modifier.fillMaxSize()) {
                                // 1 dòng cố định: trước đây từ cấp 10 chữ dài thêm 1 ký tự -> xuống dòng -> bàn chơi bị co lại
                                Text(if (snake.dead) "Thua! Điểm: ${snake.score}" else "Điểm:${snake.score} Cấp:${snake.score / 10 + 1}",
                                    color = INK, fontFamily = MONO, fontSize = 12.sp, maxLines = 1, softWrap = false, overflow = TextOverflow.Clip)
                                Canvas(Modifier.weight(1f).fillMaxWidth().pointerInput(Unit) {
                                    detectTapGestures { o ->
                                        if (snake.dead) snake.reset() else {
                                            val dx = o.x - size.width / 2f
                                            val dy = o.y - size.height / 2f
                                            if (abs(dx) > abs(dy)) snake.turn(if (dx > 0) 1 to 0 else -1 to 0)
                                            else snake.turn(if (dy > 0) 0 to 1 else 0 to -1)
                                        }
                                    }
                                }) {
                                    val c = minOf(size.width / snake.w, size.height / snake.h)
                                    val ox = (size.width - c * snake.w) / 2f
                                    val oy = (size.height - c * snake.h) / 2f
                                    drawRect(INK, Offset(ox, oy), Size(c * snake.w, c * snake.h), style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2f))
                                    snake.body.forEach { (x, y) -> drawRect(INK, Offset(ox + x * c, oy + y * c), Size(c - 2, c - 2)) }
                                    drawRect(INK, Offset(ox + snake.food.first * c + c / 4, oy + snake.food.second * c + c / 4), Size(c / 2, c / 2))
                                }
                            }
                        }
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        // Vùng chạm chỉ bao quanh chữ (không chiếm nửa hàng như trước)
                        Box(Modifier.clickable { press("SOFTL") }.padding(horizontal = 6.dp, vertical = 5.dp)) {
                            Text(left, color = INK, fontWeight = FontWeight.Bold, fontFamily = MONO, fontSize = 13.sp)
                        }
                        Box(Modifier.clickable { press("SOFTR") }.padding(horizontal = 6.dp, vertical = 5.dp)) {
                            Text(right, color = INK, fontWeight = FontWeight.Bold, fontFamily = MONO, fontSize = 13.sp)
                        }
                    }
                }
                if (locked) LockScreen(now.take(5), dateNow(), lockAt != 0L, wallBmp)
                wallPick?.let { pb ->
                    if (!locked) WallPreview(pb, wallFx, wallFy,
                        onSize = { w, h -> wallFw = w; wallFh = h },
                        onPan = { x, y -> wallFx = x; wallFy = y },
                        onSet = { press("OK") }, onCancel = { press("SOFTR") },
                        time = now.take(5), date = dateNow())
                }
            }
            }
            }

            if (!inSplit.value) {
                Spacer(Modifier.height(6.dp))
                Keys { k -> pressNow.value(k) }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun Lines(items: List<String>, sel: Int, onTap: (Int) -> Unit, onScroll: (Int) -> Unit, detail: List<String> = emptyList()) {
    // detail: các dòng chữ nhỏ chèn ngay dưới ô đang chọn (không chọn được, không chiếm số thứ tự)
    val extra = if (sel in items.indices) detail.size else 0
    // Mỗi hàng cao cố định; số hàng hiện ra = chiều cao còn trống / chiều cao hàng -> lấp đầy phần dưới màn hình
    val rowH = with(LocalDensity.current) { (14f * 1.7f).sp.toDp() }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val visible = maxOf(1, (maxHeight / rowH).toInt())
        val total = items.size + extra
        val start = (sel - visible / 2).coerceIn(0, maxOf(0, total - visible))
        Column(Modifier.fillMaxSize().pointerInput(Unit) {
            var acc = 0f
            detectVerticalDragGestures(onDragEnd = { acc = 0f }) { _, d ->
                acc += d
                // Ô chọn chạy theo hướng ngón tay: vuốt lên -> ô chọn đi lên, vuốt xuống -> ô chọn đi xuống
                if (acc < -45f) { onScroll(-1); acc = 0f } else if (acc > 45f) { onScroll(1); acc = 0f }
            }
        }) {
            for (row in start until minOf(total, start + visible)) {
                if (row > sel && row <= sel + extra) {
                    Box(Modifier.fillMaxWidth().height(rowH).background(LCD).padding(start = 20.dp, end = 6.dp),
                        contentAlignment = Alignment.CenterStart) {
                        Text(detail[row - sel - 1], color = INK, fontFamily = MONO, fontSize = 12.sp,
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                } else {
                    val i = if (row > sel + extra) row - extra else row
                    // key(i): đổi ô chọn thì chữ chạy bắt đầu lại từ đầu
                    androidx.compose.runtime.key(i) {
                        Box(Modifier.fillMaxWidth().height(rowH).clickable { onTap(i) }
                            .background(if (i == sel) INK else LCD).padding(horizontal = 6.dp),
                            contentAlignment = Alignment.CenterStart) {
                            if (i == sel) {
                                // Ô đang chọn mà tên dài hơn màn hình: chữ chạy ngang cho đọc hết.
                                // Dừng ~1,2 giây ở đầu để đọc trước, rồi chạy 40dp/giây (vừa đọc), hết thì lặp lại.
                                Text(items[i], Modifier.basicMarquee(
                                        iterations = Int.MAX_VALUE, animationMode = MarqueeAnimationMode.Immediately,
                                        spacing = MarqueeSpacing(32.dp), velocity = 40.dp),
                                    color = LCD, fontFamily = MONO, fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp, maxLines = 1, softWrap = false, overflow = TextOverflow.Clip)
                            } else {
                                Text(items[i], color = INK, fontFamily = MONO, fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            }
        }
    }
}

val GREEN = Color(0xFF2E9E4F)
val RED = Color(0xFFC0392B)
const val CALL_PATH = "M20.01 15.38c-1.23 0-2.42-.2-3.53-.56-.35-.12-.74-.03-1.01.24l-1.57 1.97c-2.83-1.35-5.48-3.9-6.89-6.83l1.95-1.66c.27-.28.35-.67.24-1.02-.37-1.11-.56-2.3-.56-3.53 0-.54-.45-.99-.99-.99H4.19C3.65 3 3 3.24 3 3.99 3 13.28 10.73 21 20.01 21c.71 0 .99-.63.99-1.18v-3.45c0-.54-.45-.99-.99-.99z"

/** Biểu tượng ống nghe điện thoại; hangUp = xoay 135° thành nút tắt máy. */
@Composable
fun PhoneIcon(color: Color, hangUp: Boolean, modifier: Modifier = Modifier) {
    val path = remember { PathParser().parsePathString(CALL_PATH).toPath() }
    Canvas(modifier) {
        val k = size.minDimension / 24f
        withTransform({
            if (hangUp) rotate(135f, Offset(size.width / 2f, size.height / 2f))
            scale(k, k, Offset.Zero)
        }) { drawPath(path, color) }
    }
}

/** Mũi tên hướng về bên trái (phím Quay lại). */
@Composable
fun BackArrow(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val w = size.width; val h = size.height; val sw = w * 0.14f
        val l = Offset(w * 0.10f, h * 0.5f); val r = Offset(w * 0.90f, h * 0.5f)
        drawLine(color, l, r, sw, cap = androidx.compose.ui.graphics.StrokeCap.Round)
        drawLine(color, l, Offset(w * 0.40f, h * 0.18f), sw, cap = androidx.compose.ui.graphics.StrokeCap.Round)
        drawLine(color, l, Offset(w * 0.40f, h * 0.82f), sw, cap = androidx.compose.ui.graphics.StrokeCap.Round)
    }
}

@Composable
fun K(mod: Modifier, shape: Shape = RoundedCornerShape(16.dp), bg: Color = KEY,
      onClick: () -> Unit, content: @Composable () -> Unit) {
    Box(mod.padding(3.dp).clip(shape).background(bg).clickable(onClick = onClick),
        contentAlignment = Alignment.Center) { content() }
}

@Composable
fun Glyph(t: String, size: Int = 18, fg: Color = KEYTXT) {
    Text(t, color = fg, fontWeight = FontWeight.Bold, fontSize = size.sp)
}

/** Phím số: chữ số lớn, các chữ cái đậm nằm ngay bên dưới. */
@Composable
fun NumKey(d: String, sub: String, mod: Modifier, onClick: () -> Unit) {
    K(mod, onClick = onClick) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(d, color = KEYTXT, fontWeight = FontWeight.Black, fontSize = 26.sp)
            Text(if (sub.isEmpty()) " " else sub, color = KEYTXT, fontWeight = FontWeight.ExtraBold,
                fontSize = 12.sp, letterSpacing = 1.sp)
        }
    }
}

@Composable
fun ColumnScope.Keys(p: (String) -> Unit) {
    val dk = Color(0xFFB4BCCB)
    // Bố cục như Nokia thật: phím mềm (cao) ở trên, Gọi/Tắt (thấp) ở dưới, D-pad tròn ở giữa.
    BoxWithConstraints(Modifier.fillMaxWidth().weight(0.27f).padding(horizontal = 12.dp), contentAlignment = Alignment.Center) {
        val s = minOf(maxHeight, maxWidth * 0.48f)
        val c = s / 3
        Row(Modifier.fillMaxWidth().height(s)) {
            Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.SpaceBetween) {
                K(Modifier.fillMaxWidth().height(s * 0.336f), shape = RoundedCornerShape(16.dp), onClick = { p("SOFTL") }) { Glyph("Menu", 20) }
                K(Modifier.fillMaxWidth().height(s * 0.336f), shape = RoundedCornerShape(14.dp), onClick = { p("CALL") }) {
                    PhoneIcon(GREEN, false, Modifier.size(31.dp))
                }
            }
            Column(Modifier.width(s).fillMaxHeight()) {
                Row(Modifier.height(c)) {
                    Spacer(Modifier.size(c))
                    K(Modifier.size(c), shape = RoundedCornerShape(10.dp), onClick = { p("UP") }) { Glyph("▲", 14) }
                    Spacer(Modifier.size(c))
                }
                Row(Modifier.height(c)) {
                    K(Modifier.size(c), shape = RoundedCornerShape(10.dp), onClick = { p("LEFT") }) { Glyph("◀", 14) }
                    K(Modifier.size(c), shape = RoundedCornerShape(10.dp), bg = dk, onClick = { p("OK") }) { Glyph("OK", 15) }
                    K(Modifier.size(c), shape = RoundedCornerShape(10.dp), onClick = { p("RIGHT") }) { Glyph("▶", 14) }
                }
                Row(Modifier.height(c)) {
                    Spacer(Modifier.size(c))
                    K(Modifier.size(c), shape = RoundedCornerShape(10.dp), onClick = { p("DOWN") }) { Glyph("▼", 14) }
                    Spacer(Modifier.size(c))
                }
            }
            Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.SpaceBetween) {
                K(Modifier.fillMaxWidth().height(s * 0.336f), shape = RoundedCornerShape(16.dp), onClick = { p("SOFTR") }) { BackArrow(KEYTXT, Modifier.size(34.dp)) }
                K(Modifier.fillMaxWidth().height(s * 0.336f), shape = RoundedCornerShape(14.dp), onClick = { p("END") }) {
                    PhoneIcon(RED, true, Modifier.size(31.dp))
                }
            }
        }
    }
    // Bàn phím số
    Column(Modifier.fillMaxWidth().weight(0.40f).padding(horizontal = 12.dp)) {
        listOf("123", "456", "789", "*0#").forEach { row ->
            Row(Modifier.weight(1f).fillMaxWidth()) {
                row.forEach { ch ->
                    val s = ch.toString()
                    NumKey(s, LETTERS[s] ?: "", Modifier.weight(1f).fillMaxHeight()) { p(s) }
                }
            }
        }
    }
}
