package com.nokia.phone

import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Nhận dạng giọng nói tiếng Việt bằng SpeechRecognizer của Android (dịch vụ Google trên máy). Có mạng: dùng nhận dạng trực tuyến như thường;
 * không có mạng (hoặc lỗi mạng giữa chừng): tự chuyển sang gói giọng nói tiếng Việt ngoại tuyến đã tải về máy.
 * Mọi hàm phải gọi trên luồng chính; các callback cũng chạy trên luồng chính.
 */
const val SILENCE_MS = 2000L
/** Sau khi bấm AI (bộ nhận dạng sẵn sàng), đợi người dùng bắt đầu nói tối đa chừng này; quá thì dừng nghe. */
const val START_WAIT_MS = 3000L
/** Mã lỗi riêng: không có mạng và máy chưa tải gói giọng nói tiếng Việt ngoại tuyến. */
const val ERR_NO_OFFLINE = 9001

class VoiceInput(private val ctx: Context) {
    private var rec: SpeechRecognizer? = null
    private val handler = Handler(Looper.getMainLooper())
    private val silenceStop = Runnable { stop() }          // im lặng đủ lâu -> chốt luôn, không chờ bộ nhận dạng
    private fun armSilence() { handler.removeCallbacks(silenceStop); handler.postDelayed(silenceStop, SILENCE_MS) }
    private fun disarmSilence() { handler.removeCallbacks(silenceStop); handler.removeCallbacks(startTimeout) }
    private var curFail: ((Int) -> Unit)? = null
    private val startTimeout = Runnable {            // 3 giây từ lúc sẵn sàng mà chưa ai nói -> dừng, báo "Không nghe thấy gì"
        val f = curFail
        cancel()
        f?.invoke(SpeechRecognizer.ERROR_SPEECH_TIMEOUT)
    }

    fun available(): Boolean = try { SpeechRecognizer.isRecognitionAvailable(ctx) } catch (_: Throwable) { false }

    /** Có mạng internet đang dùng được không (không xác định được thì coi như có, để giữ hành vi cũ). */
    private fun online(): Boolean = try {
        val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val caps = cm.getNetworkCapabilities(cm.activeNetwork)
        caps != null && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    } catch (_: Throwable) { true }

    fun start(
        onPartial: (String) -> Unit,
        onLevel: (Float) -> Unit,
        onResult: (List<String>) -> Unit,
        onError: (Int) -> Unit
    ) {
        cancel()
        startRec(!online(), onPartial, onLevel, onResult, onError)
    }

    private fun startRec(
        offline: Boolean,
        onPartial: (String) -> Unit,
        onLevel: (Float) -> Unit,
        onResult: (List<String>) -> Unit,
        onError: (Int) -> Unit
    ) {
        val r = try { SpeechRecognizer.createSpeechRecognizer(ctx) } catch (_: Throwable) { null }
        if (r == null) { onError(SpeechRecognizer.ERROR_CLIENT); return }
        rec = r
        val fail = onError   // đặt tên khác để không lẫn với RecognitionListener.onError bên dưới
        curFail = fail
        r.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) { handler.removeCallbacks(startTimeout); handler.postDelayed(startTimeout, START_WAIT_MS) }
            override fun onBeginningOfSpeech() { handler.removeCallbacks(startTimeout); armSilence() }
            override fun onRmsChanged(rmsdB: Float) { onLevel(rmsdB) }
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onError(error: Int) {
                disarmSilence()
                val netErr = error == SpeechRecognizer.ERROR_NETWORK || error == SpeechRecognizer.ERROR_NETWORK_TIMEOUT ||
                    error == SpeechRecognizer.ERROR_SERVER
                if (!offline && netErr) {
                    // Mạng chập chờn / máy chủ lỗi -> thử lại một lần bằng gói ngoại tuyến
                    handler.post { cancel(); startRec(true, onPartial, onLevel, onResult, fail) }
                } else if (offline && (netErr || error == 12 || error == 13)) {
                    fail(ERR_NO_OFFLINE)    // chưa có gói tiếng Việt ngoại tuyến trên máy
                } else fail(error)
            }
            override fun onResults(results: Bundle?) {
                disarmSilence()
                val l = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION).orEmpty()
                onResult(l)
            }
            override fun onPartialResults(partialResults: Bundle?) {
                partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.let {
                    onPartial(it)
                    handler.removeCallbacks(startTimeout)
                    armSilence()       // còn đang nói (chữ vẫn thay đổi) -> đếm lại 2 giây
                }
            }
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
        val i = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "vi-VN")
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "vi-VN")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)
            // Gợi ý cho bộ nhận dạng: im lặng 2 giây là xong (một số máy bỏ qua gợi ý này, đã có bộ đếm riêng ở trên)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, SILENCE_MS)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, SILENCE_MS)
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, ctx.packageName)
            if (offline) putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
        }
        try { r.startListening(i) } catch (_: Throwable) { onError(SpeechRecognizer.ERROR_CLIENT) }
    }

    /** Nói xong sớm: bấm OK khi đang nghe -> nhận dạng luôn phần đã nói. */
    fun stop() { try { rec?.stopListening() } catch (_: Throwable) {} }

    /** Hủy hẳn, không trả kết quả. */
    fun cancel() {
        disarmSilence()
        val r = rec ?: return
        rec = null
        try { r.setRecognitionListener(null) } catch (_: Throwable) {}
        try { r.cancel() } catch (_: Throwable) {}
        try { r.destroy() } catch (_: Throwable) {}
    }
}

fun aiErrorText(code: Int): String = when (code) {
    SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Không nghe thấy gì.\nBấm Nói để thử lại."
    ERR_NO_OFFLINE -> "Không có mạng và máy chưa có gói giọng nói tiếng Việt ngoại tuyến.\nTải ở: Cài đặt > Google > Nhận dạng giọng nói."
    SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Cần có mạng để nhận dạng giọng nói."
    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Chưa cấp quyền Micro cho app."
    SpeechRecognizer.ERROR_AUDIO -> "Micro đang bận hoặc lỗi âm thanh."
    SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Bộ nhận dạng đang bận, thử lại sau giây lát."
    SpeechRecognizer.ERROR_SERVER -> "Máy chủ nhận dạng giọng nói lỗi, thử lại."
    else -> "Không nhận dạng được (mã $code).\nKiểm tra mạng và app Google."
}

/** Bật / tắt đèn pin (flash). Trả về false nếu máy không có hoặc camera đang bận. */
object Torch {
    fun set(ctx: Context, on: Boolean): Boolean = try {
        val cm = ctx.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        val id = cm.cameraIdList.firstOrNull {
            cm.getCameraCharacteristics(it).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
        }
        if (id == null) false else { cm.setTorchMode(id, on); true }
    } catch (_: Throwable) { false }
}

val AI_TIPS = listOf(
    "Gọi cho mẹ",
    "Gọi vào số 0123456789 (hoặc đọc: không một hai ba bốn năm sáu bảy tám chín)",
    "Nhắn tin cho Nam nội dung tối nay họp",
    "Chụp ảnh / chụp ảnh tự sướng",
    "Mở thư viện / mở máy ảnh",
    "Mở bài Hãy trao cho anh",
    "Phát nhạc của Sơn Tùng",
    "Đặt báo thức 6 giờ 30",
    "Đặt báo thức sau 20 phút",
    "Mở Zalo / mở Facebook",
    "Mở TV / xem kênh HTV9 / đài Cà Mau",
    "Mở YouTube / xem video Sơn Tùng",
    "Mở danh bạ / tin nhắn / lịch",
    "Bật đèn pin / tăng âm lượng",
    "Bây giờ là mấy giờ?"
)

/**
 * Màn hình AI trên LCD.
 * state: 0 = rảnh, 1 = đang nghe, 3 = chờ xác nhận (gọi / nhắn), 4 = hiện kết quả / lỗi.
 * kind (khi state 3): 1 = gọi, 2 = gửi tin, 3 = soạn tin.
 */
@Composable
fun AiScreen(
    state: Int, heard: String, msg: String, level: Float,
    cands: List<Contact>, pick: Int, kind: Int, body: String
) {
    Column(Modifier.fillMaxSize()) {
        Head("AI GIỌNG NÓI")
        when (state) {
            1 -> Column(Modifier.fillMaxSize().padding(horizontal = 6.dp), Arrangement.Top, Alignment.CenterHorizontally) {
                val n = (((level + 2f) / 12f).coerceIn(0f, 1f) * 10f).toInt()
                Text("●  Đang nghe...", color = INK, fontSize = 22.sp, fontWeight = FontWeight.Bold, fontFamily = MONO)
                Spacer(Modifier.height(6.dp))
                Text("█".repeat(n) + "░".repeat(10 - n), color = INK, fontSize = 18.sp, fontFamily = MONO)
                Spacer(Modifier.height(10.dp))
                Text(if (heard.isEmpty()) "Hãy nói lệnh của bạn" else heard,
                    color = INK, fontSize = 18.sp, fontFamily = MONO, fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center, maxLines = 5, overflow = TextOverflow.Ellipsis)
            }
            3 -> {
                val c = cands.getOrNull(pick)
                Column(Modifier.fillMaxSize().padding(horizontal = 6.dp).verticalScroll(rememberScrollState()),
                    Arrangement.Top, Alignment.CenterHorizontally) {
                    Text(when (kind) { 1 -> "Gọi cho"; 2 -> "Gửi tin nhắn cho"; else -> "Soạn tin cho" },
                        color = INK, fontSize = 14.sp, fontFamily = MONO)
                    Text(c?.name ?: "", color = INK, fontSize = 24.sp, fontWeight = FontWeight.Bold, fontFamily = MONO,
                        textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    if (c != null && c.name != c.number)
                        Text(c.number, color = INK, fontSize = 16.sp, fontFamily = MONO)
                    if (kind == 2) {
                        Spacer(Modifier.height(6.dp))
                        Text("\"" + body + "\"", color = INK, fontSize = 16.sp, fontFamily = MONO, fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center, maxLines = 6, overflow = TextOverflow.Ellipsis)
                    }
                    Spacer(Modifier.height(6.dp))
                    if (cands.size > 1)
                        Text("▲▼ đổi người (${pick + 1}/${cands.size})", color = INK, fontSize = 12.sp, fontFamily = MONO)
                    Text("Nghe được: $heard", Modifier.fillMaxWidth(), color = INK, fontSize = 11.sp, fontFamily = MONO,
                        textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
            5 -> Column(Modifier.fillMaxSize().padding(horizontal = 6.dp).verticalScroll(rememberScrollState())) {
                Text("Bấm OK rồi nói, ví dụ:", color = INK, fontSize = 13.sp, fontFamily = MONO, fontWeight = FontWeight.Bold)
                AI_TIPS.forEach { Text("• $it", color = INK, fontSize = 12.sp, fontFamily = MONO) }
            }
            else -> Column(Modifier.fillMaxSize().padding(horizontal = 6.dp).verticalScroll(rememberScrollState())) {
                if (msg.isNotEmpty()) {
                    if (heard.isNotEmpty()) Text("Bạn nói: $heard", color = INK, fontSize = 12.sp, fontFamily = MONO)
                    Text(msg, Modifier.fillMaxWidth().padding(vertical = 6.dp), color = INK, fontSize = 17.sp,
                        fontFamily = MONO, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                    Text("OK / Nói: ra lệnh tiếp", Modifier.fillMaxWidth(), color = INK, fontSize = 12.sp,
                        fontFamily = MONO, textAlign = TextAlign.Center)
                } else {
                    Text("Bấm OK rồi nói", Modifier.fillMaxWidth().padding(top = 24.dp), color = INK, fontSize = 18.sp,
                        fontFamily = MONO, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                    Text("Bấm ← 2 lần ở màn hình chờ: xem hướng dẫn", Modifier.fillMaxWidth().padding(top = 10.dp), color = INK,
                        fontSize = 11.sp, fontFamily = MONO, textAlign = TextAlign.Center)
                }
            }
        }
    }
}


/** Bỏ các từ thừa đầu/cuối câu nói khi tìm trong YouTube: "tìm bài Liễu Thanh Yên trên YouTube" -> "bài Liễu Thanh Yên". */
fun ytCleanQuery(raw: String): String {
    var q = raw.trim()
    val head = Regex("^(hãy |làm ơn |cho tôi |giúp tôi |mở |phát |xem |tìm kiếm |tìm |search )+", RegexOption.IGNORE_CASE)
    val tail = Regex(" (trên|ở|on) (youtube|du tup|you tube)$", RegexOption.IGNORE_CASE)
    q = q.replace(head, "").replace(tail, "").trim()
    return q.ifBlank { raw.trim() }
}

/** Lớp phủ "đang nghe" của nút AI trong YouTube (vẽ đè lên khung YouTube trong ô LCD). */
@Composable
fun YtListenOverlay(heard: String, level: Float) {
    Column(Modifier.fillMaxSize().background(LCD).padding(horizontal = 6.dp), Arrangement.Top, Alignment.CenterHorizontally) {
        val n = (((level + 2f) / 12f).coerceIn(0f, 1f) * 10f).toInt()
        Spacer(Modifier.height(16.dp))
        Text("●  Đang nghe...", color = INK, fontSize = 22.sp, fontWeight = FontWeight.Bold, fontFamily = MONO)
        Spacer(Modifier.height(6.dp))
        Text("█".repeat(n) + "░".repeat(10 - n), color = INK, fontSize = 18.sp, fontFamily = MONO)
        Spacer(Modifier.height(10.dp))
        Text(if (heard.isEmpty()) "Nói tên video cần tìm" else heard,
            color = INK, fontSize = 18.sp, fontFamily = MONO, fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center, maxLines = 5, overflow = TextOverflow.Ellipsis)
    }
}
