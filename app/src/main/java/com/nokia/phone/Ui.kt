package com.nokia.phone

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Mọi chữ trong màn hình LCD to hơn 50% so với trước (bàn phím bên dưới giữ nguyên). */
const val LCD_FONT_SCALE = 1.5f

@Composable
fun LcdFontScale(content: @Composable () -> Unit) {
    val d = LocalDensity.current
    CompositionLocalProvider(LocalDensity provides Density(d.density, d.fontScale * LCD_FONT_SCALE)) { content() }
}

@Composable
fun Msg(t: String) {
    Box(Modifier.fillMaxSize().padding(8.dp), contentAlignment = Alignment.Center) {
        Text(t, color = INK, fontFamily = MONO, fontWeight = FontWeight.Bold, fontSize = 16.sp, textAlign = TextAlign.Center)
    }
}

/** Tiêu đề màn hình: chữ to, đậm, có khoảng cách với nội dung bên dưới. */
@Composable
fun Head(t: String) {
    Column(Modifier.fillMaxWidth()) {
        Text(t, Modifier.fillMaxWidth().background(INK).padding(horizontal = 6.dp, vertical = 5.dp),
            color = LCD, fontSize = 16.sp, fontWeight = FontWeight.Bold, fontFamily = MONO,
            maxLines = 1, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(8.dp))
    }
}

/** Dòng hướng dẫn phím: đặt ngay dưới tiêu đề, chữ thường (không tô nền) để khỏi nhầm với mục đang chọn. */
@Composable
fun Hint(t: String) {
    Text(t, Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 2.dp),
        color = INK, fontFamily = MONO, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
}

/** Đọc toàn bộ một tin nhắn (cuộn được). */
@Composable
fun MsgView(name: String, m: Sms?, idx: Int, total: Int) {
    if (m == null) { Msg("(trống)"); return }
    Column(Modifier.fillMaxSize()) {
        Head("$name  ${idx + 1}/$total")
        Text((if (m.sent) "Đã gửi  " else "Nhận  ") +
            SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date(m.date)),
            Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
            color = INK, fontFamily = MONO, fontSize = 12.sp)
        key(idx) {
            Text(m.body, Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(4.dp),
                color = INK, fontFamily = MONO, fontSize = 16.sp)
        }
        Hint("#: xóa tin")
    }
}

/** Soạn tin: bước 0 nhập số nhận, bước 1 gõ nội dung kiểu multi-tap. */
@Composable
fun ComposeView(stage: Int, to: String, name: String, entry: TextEntry) {
    Column(Modifier.fillMaxSize()) {
        val who = if (name.isNotEmpty() && name != to) "$name ($to)" else to
        Head("Đến: " + who.ifEmpty { "..." })
        if (stage == 0) {
            Column(Modifier.fillMaxSize().padding(8.dp), Arrangement.Center, Alignment.CenterHorizontally) {
                Text(to.ifEmpty { "Nhập số" }, color = INK, fontFamily = MONO, fontWeight = FontWeight.Bold, fontSize = 30.sp)
                Spacer(Modifier.height(10.dp))
                Text("Gõ số hoặc ▲▼ chọn từ danh bạ", color = INK, fontFamily = MONO, fontSize = 13.sp, textAlign = TextAlign.Center)
                Text("OK: tiếp", color = INK, fontFamily = MONO, fontSize = 13.sp)
            }
        } else {
            Text(entry.text + "_", Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState(), reverseScrolling = true).padding(4.dp),
                color = INK, fontFamily = MONO, fontSize = 16.sp)
            Text("[${MODES[entry.mode]}]  ${entry.text.length}   *=dấu   #=kiểu gõ",
                Modifier.fillMaxWidth().background(INK).padding(horizontal = 6.dp, vertical = 2.dp),
                color = LCD, fontFamily = MONO, fontSize = 12.sp, maxLines = 1)
        }
    }
}

/** Xem ảnh một tấm; ◀ ▶ hoặc vuốt ngang để chuyển ảnh. */
@Composable
fun GalleryView(ok: Boolean, loaded: Boolean, count: Int, idx: Int, bmp: Bitmap?, onStep: (Int) -> Unit) {
    when {
        !ok -> Msg("Cần quyền truy cập ảnh")
        count == 0 -> Msg(if (loaded) "Chưa có ảnh nào" else "Đang tải...")
        else -> Box(Modifier.fillMaxSize().pointerInput(count) {
            var acc = 0f
            detectHorizontalDragGestures(onDragEnd = {
                if (acc > 80f) onStep(-1) else if (acc < -80f) onStep(1)
                acc = 0f
            }) { _, d -> acc += d }
        }) {
            if (bmp != null) Image(bmp.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
            else Msg("Đang tải...")
            Text("${idx + 1}/$count", Modifier.align(Alignment.BottomCenter).background(LCD).padding(horizontal = 6.dp),
                color = INK, fontFamily = MONO, fontWeight = FontWeight.Bold, fontSize = 13.sp)
        }
    }
}
