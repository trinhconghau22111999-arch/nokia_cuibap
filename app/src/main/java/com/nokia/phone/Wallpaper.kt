package com.nokia.phone

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.io.File
import kotlin.math.roundToInt

/** Hình nền do người dùng chọn cho màn hình khóa (khung hiển thị phía trên). Lưu bản đã cắt đúng khung trong bộ nhớ riêng của app. */
object Wallpaper {
    private fun file(ctx: Context) = File(ctx.filesDir, "lock_wallpaper.jpg")

    fun load(ctx: Context): Bitmap? = try {
        val f = file(ctx)
        if (f.exists()) BitmapFactory.decodeFile(f.path) else null
    } catch (_: Throwable) { null }

    /** Đọc ảnh từ uri (thu nhỏ, xoay đúng chiều) để xem thử; lỗi thì trả null. */
    fun decode(ctx: Context, u: Uri, maxDim: Int = 1600): Bitmap? = try {
        val cr = ctx.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        cr.openInputStream(u)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var s = 1
        while (bounds.outWidth / (s * 2) >= maxDim || bounds.outHeight / (s * 2) >= maxDim) s *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = s }
        val b = cr.openInputStream(u)?.use { BitmapFactory.decodeStream(it, null, opts) }
        val rot: Float = try {
            cr.openInputStream(u)?.use {
                when (ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                    ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                    ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                    else -> 0f
                }
            } ?: 0f
        } catch (_: Throwable) { 0f }
        if (b != null && rot != 0f)
            Bitmap.createBitmap(b, 0, 0, b.width, b.height, Matrix().apply { postRotate(rot) }, true)
        else b
    } catch (_: Throwable) { null }

    /**
     * Cắt đúng phần đang thấy trong khung xem thử (khung rộng fw x cao fh, ảnh phủ kín khung,
     * fx/fy = vị trí kéo 0..1) rồi lưu lại. Trả về ảnh đã cắt.
     */
    fun saveCrop(ctx: Context, b: Bitmap, fx: Float, fy: Float, fw: Float, fh: Float): Bitmap? = try {
        val out = if (fw > 0f && fh > 0f) {
            val s = maxOf(fw / b.width, fh / b.height)
            val vw = minOf(fw / s, b.width.toFloat()); val vh = minOf(fh / s, b.height.toFloat())
            val l = ((b.width - vw) * fx).roundToInt().coerceIn(0, b.width - 1)
            val t = ((b.height - vh) * fy).roundToInt().coerceIn(0, b.height - 1)
            Bitmap.createBitmap(b, l, t, vw.roundToInt().coerceIn(1, b.width - l), vh.roundToInt().coerceIn(1, b.height - t))
        } else b
        file(ctx).outputStream().use { out.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        out
    } catch (_: Throwable) { null }
}

/**
 * Khung xem thử hình nền: phủ đúng vùng màn hình khóa. Kéo ngón tay (hoặc ▲▼◀▶ ở MainActivity) để dời ảnh,
 * rồi bấm Đặt. fx/fy = vị trí phần đang thấy trong ảnh, 0..1 (0,5 = giữa).
 */
@Composable
fun WallPreview(
    bmp: Bitmap, fx: Float, fy: Float,
    onSize: (Float, Float) -> Unit, onPan: (Float, Float) -> Unit,
    onSet: () -> Unit, onCancel: () -> Unit,
    time: String = "", date: String = ""
) {
    val img = remember(bmp) { bmp.asImageBitmap() }
    val curFx by rememberUpdatedState(fx)
    val curFy by rememberUpdatedState(fy)
    Box(
        Modifier.fillMaxSize().background(Color.Black)
            .onSizeChanged { onSize(it.width.toFloat(), it.height.toFloat()) }
            .pointerInput(bmp) {
                detectDragGestures { change, d ->
                    change.consume()
                    val w = size.width.toFloat(); val h = size.height.toFloat()
                    val s = maxOf(w / bmp.width, h / bmp.height)
                    val maxL = bmp.width - w / s; val maxT = bmp.height - h / s
                    // kéo ảnh sang phải => phần thấy dịch sang trái
                    val nx = if (maxL > 1f) (curFx - d.x / s / maxL).coerceIn(0f, 1f) else curFx
                    val ny = if (maxT > 1f) (curFy - d.y / s / maxT).coerceIn(0f, 1f) else curFy
                    onPan(nx, ny)
                }
            }
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width; val h = size.height
            val s = maxOf(w / bmp.width, h / bmp.height)
            val vw = minOf(w / s, bmp.width.toFloat()); val vh = minOf(h / s, bmp.height.toFloat())
            val l = (bmp.width - vw) * fx; val t = (bmp.height - vh) * fy
            drawImage(
                img,
                IntOffset(l.roundToInt(), t.roundToInt()), IntSize(vw.roundToInt(), vh.roundToInt()),
                IntOffset.Zero, IntSize(w.roundToInt(), h.roundToInt())
            )
        }
        Box(Modifier.fillMaxSize().border(3.dp, INK))
        LockTexts(time, date, false, true)    // hiện đồng hồ + dòng hướng dẫn mở khóa như màn hình khóa thật
        // Điều khiển xem thử: nằm phía trên dòng hướng dẫn mở khóa, chỉ có nền mờ sau chữ
        Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(bottom = 56.dp, start = 6.dp, end = 6.dp)) {
            Text("Kéo ảnh hoặc bấm ▲▼◀▶ để di chuyển", Modifier.align(Alignment.CenterHorizontally)
                .background(Color.White.copy(alpha = 0.7f)).padding(horizontal = 6.dp, vertical = 2.dp),
                color = INK, fontFamily = MONO, fontSize = 12.sp, textAlign = TextAlign.Center)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Box(Modifier.background(Color.White.copy(alpha = 0.7f)).clickable { onSet() }.padding(horizontal = 10.dp, vertical = 5.dp)) {
                    Text("Đặt", color = INK, fontWeight = FontWeight.Bold, fontFamily = MONO, fontSize = 13.sp)
                }
                Box(Modifier.background(Color.White.copy(alpha = 0.7f)).clickable { onCancel() }.padding(horizontal = 10.dp, vertical = 5.dp)) {
                    Text("Hủy", color = INK, fontWeight = FontWeight.Bold, fontFamily = MONO, fontSize = 13.sp)
                }
            }
        }
    }
}
