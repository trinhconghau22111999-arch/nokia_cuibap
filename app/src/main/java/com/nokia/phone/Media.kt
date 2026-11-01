package com.nokia.phone

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.media.MediaMetadataRetriever
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import android.provider.MediaStore
import android.widget.Toast
import androidx.camera.core.CameraControl
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// ---------------------------------------------------------------- Máy ảnh

class CamHolder {
    var capture: ImageCapture? = null
    var control: CameraControl? = null
    var hasFlash = false
    var torch = false      // trạng thái đèn flash mong muốn, áp dụng ngay khi camera mở xong
}

/** Khung xem trước camera nằm gọn trong ô màn hình LCD. */
@Composable
fun CameraView(holder: CamHolder, front: Boolean, flash: Boolean) {
    val ctx = LocalContext.current
    // COMPATIBLE (TextureView): khung xem trước bị cắt đúng theo ô LCD. Chế độ mặc định (SurfaceView) có thể
    // tràn ra ngoài ô, đè lên cả bàn phím bên dưới khi phóng to để lấp đầy (FILL_CENTER).
    val view = remember {
        PreviewView(ctx).apply {
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }
    }
    SideEffect { holder.torch = flash }
    LaunchedEffect(flash) { if (holder.hasFlash) holder.control?.enableTorch(flash) }
    DisposableEffect(front) {
        val future = ProcessCameraProvider.getInstance(ctx)
        var provider: ProcessCameraProvider? = null
        var disposed = false
        future.addListener({
            if (disposed) return@addListener
            try {
                val p = future.get()
                provider = p
                val preview = Preview.Builder().build().also { it.setSurfaceProvider(view.surfaceProvider) }
                val cap = ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY).build()
                p.unbindAll()
                val camera = p.bindToLifecycle(
                    ctx as LifecycleOwner,
                    if (front) CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA,
                    preview, cap
                )
                holder.capture = cap
                holder.control = camera.cameraControl
                holder.hasFlash = camera.cameraInfo.hasFlashUnit()
                if (holder.torch && holder.hasFlash) camera.cameraControl.enableTorch(true)
            } catch (_: Exception) {
                Toast.makeText(ctx, "Không mở được camera", Toast.LENGTH_SHORT).show()
            }
        }, ContextCompat.getMainExecutor(ctx))
        onDispose {
            disposed = true
            try { provider?.unbindAll() } catch (_: Exception) {}
            holder.capture = null
            holder.control = null
            holder.hasFlash = false
        }
    }
    AndroidView({ view }, Modifier.fillMaxSize().clipToBounds())
}

/** Chụp và lưu vào Pictures/Nokia (hiện trong Thư viện). */
fun takePhoto(ctx: Context, holder: CamHolder) {
    val cap = holder.capture ?: return
    val name = "NOKIA_" + SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
    val v = ContentValues().apply {
        put(MediaStore.MediaColumns.DISPLAY_NAME, name)
        put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
        if (Build.VERSION.SDK_INT >= 29) put(MediaStore.MediaColumns.RELATIVE_PATH, "Pictures/Nokia")
    }
    val o = ImageCapture.OutputFileOptions.Builder(
        ctx.contentResolver, MediaStore.Images.Media.EXTERNAL_CONTENT_URI, v
    ).build()
    cap.takePicture(o, ContextCompat.getMainExecutor(ctx), object : ImageCapture.OnImageSavedCallback {
        override fun onImageSaved(output: ImageCapture.OutputFileResults) {
            // Không hiện thông báo: hiệu ứng đóng khung chụp ở màn hình đã báo là đã chụp
        }
        override fun onError(exception: ImageCaptureException) {
            Toast.makeText(ctx, "Chụp ảnh lỗi", Toast.LENGTH_SHORT).show()
        }
    })
}

// ---------------------------------------------------------------- Thư viện ảnh

/** Kết quả xóa ảnh: xong / thất bại / cần người dùng cho phép qua hộp thoại của hệ thống. */
sealed class DelResult {
    object Done : DelResult()
    object Failed : DelResult()
    class NeedUser(val sender: android.content.IntentSender) : DelResult()
}

object Gallery {
    /**
     * Xóa một ảnh. Android 11+: hệ thống tự hỏi xác nhận (createDeleteRequest) rồi tự xóa.
     * Android 10: xóa thẳng, nếu ảnh của app khác thì hệ thống hỏi cho phép rồi phải gọi lại.
     * Android 9 trở xuống: xóa thẳng (cần quyền ghi bộ nhớ).
     */
    fun delete(ctx: Context, id: Long): DelResult {
        val u = uri(id)
        if (Build.VERSION.SDK_INT >= 30)
            return try { DelResult.NeedUser(MediaStore.createDeleteRequest(ctx.contentResolver, listOf(u)).intentSender) }
            catch (_: Exception) { DelResult.Failed }
        return try {
            if (ctx.contentResolver.delete(u, null, null) > 0) DelResult.Done else DelResult.Failed
        } catch (e: SecurityException) {
            if (Build.VERSION.SDK_INT >= 29 && e is android.app.RecoverableSecurityException)
                DelResult.NeedUser(e.userAction.actionIntent.intentSender)
            else DelResult.Failed
        } catch (_: Exception) { DelResult.Failed }
    }

    fun ids(ctx: Context): List<Long> {
        val out = ArrayList<Long>()
        try {
            ctx.contentResolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                arrayOf(MediaStore.Images.Media._ID), null, null,
                MediaStore.Images.Media.DATE_ADDED + " DESC"
            )?.use { c -> while (c.moveToNext()) out.add(c.getLong(0)) }
        } catch (_: Exception) {}
        return out
    }

    private fun uri(id: Long): Uri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id)

    /** Giải mã thu nhỏ (cạnh dài ~maxDim) và xoay đúng chiều theo EXIF. */
    fun bitmap(ctx: Context, id: Long, maxDim: Int = 1280): Bitmap? = try {
        val u = uri(id)
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        ctx.contentResolver.openInputStream(u)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var s = 1
        while (bounds.outWidth / (s * 2) >= maxDim || bounds.outHeight / (s * 2) >= maxDim) s *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = s }
        val b = ctx.contentResolver.openInputStream(u)?.use { BitmapFactory.decodeStream(it, null, opts) }
        val rot: Float = ctx.contentResolver.openInputStream(u)?.use {
            when (ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                else -> 0f
            }
        } ?: 0f
        if (b != null && rot != 0f)
            Bitmap.createBitmap(b, 0, 0, b.width, b.height, Matrix().apply { postRotate(rot) }, true)
        else b
    } catch (_: Throwable) { null }
}

// ---------------------------------------------------------------- Ghi âm

data class RecItem(val file: File, val dur: Long)

fun mmss(ms: Long): String = "%d:%02d".format(ms / 60000, (ms / 1000) % 60)

object Rec {
    private var mr: MediaRecorder? = null
    private var cur: File? = null
    var startedAt = 0L
        private set
    var paused = false
        private set
    private var pausedAt = 0L
    private var pausedTotal = 0L
    val active: Boolean get() = mr != null

    /** Thời gian đã ghi (không tính lúc tạm dừng). */
    fun elapsed(): Long =
        if (mr == null) 0L else (if (paused) pausedAt else SystemClock.elapsedRealtime()) - startedAt - pausedTotal

    fun pause(): Boolean {
        val r = mr ?: return false
        if (paused) return true
        return try { r.pause(); paused = true; pausedAt = SystemClock.elapsedRealtime(); true } catch (_: Exception) { false }
    }

    fun resume(): Boolean {
        val r = mr ?: return false
        if (!paused) return true
        return try {
            r.resume(); pausedTotal += SystemClock.elapsedRealtime() - pausedAt; paused = false; true
        } catch (_: Exception) { false }
    }

    /** Biên độ hiện tại 0..32767 để vẽ thanh mức âm. */
    fun level(): Int = try { mr?.maxAmplitude ?: 0 } catch (_: Exception) { 0 }

    private fun dir(ctx: Context): File = File(ctx.filesDir, "recordings").apply { mkdirs() }

    @Suppress("DEPRECATION")
    fun start(ctx: Context): Boolean {
        val f = File(dir(ctx), "GA_" + SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date()) + ".m4a")
        val r = if (Build.VERSION.SDK_INT >= 31) MediaRecorder(ctx) else MediaRecorder()
        return try {
            r.setAudioSource(MediaRecorder.AudioSource.MIC)
            r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            r.setAudioEncodingBitRate(96000)
            r.setAudioSamplingRate(44100)
            r.setOutputFile(f.absolutePath)
            r.prepare()
            r.start()
            mr = r; cur = f; startedAt = SystemClock.elapsedRealtime()
            paused = false; pausedTotal = 0L
            true
        } catch (_: Exception) {
            try { r.release() } catch (_: Exception) {}
            f.delete()
            false
        }
    }

    /** Dừng và lưu; trả về file nếu hợp lệ. */
    fun stop(): File? {
        val r = mr ?: return null
        val f = cur
        mr = null; cur = null; paused = false
        var ok = true
        try { r.stop() } catch (_: RuntimeException) { ok = false }
        try { r.release() } catch (_: Exception) {}
        if (!ok) { f?.delete(); return null }
        return f
    }

    fun list(ctx: Context): List<RecItem> =
        (dir(ctx).listFiles()?.toList() ?: emptyList())
            .filter { it.extension == "m4a" }
            .sortedByDescending { it.lastModified() }
            .map { RecItem(it, duration(it)) }

    private fun duration(f: File): Long = try {
        val r = MediaMetadataRetriever()
        r.setDataSource(f.absolutePath)
        val d = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
        r.release()
        d
    } catch (_: Exception) { 0L }
}

object Play {
    private var mp: MediaPlayer? = null

    fun play(f: File, onEnd: () -> Unit): Boolean {
        stop()
        return try {
            val m = MediaPlayer()
            m.setDataSource(f.absolutePath)
            m.prepare()
            m.setOnCompletionListener { stop(); onEnd() }
            m.start()
            mp = m
            true
        } catch (_: Exception) { false }
    }

    fun stop() {
        try { mp?.stop() } catch (_: Exception) {}
        try { mp?.release() } catch (_: Exception) {}
        mp = null
    }

    fun pos(): Long = try { (mp?.currentPosition ?: 0).toLong() } catch (_: Exception) { 0L }
}

/** Dòng hiển thị cho danh sách ghi âm (tick chỉ để buộc vẽ lại khi đang phát). */
fun recLines(recs: List<RecItem>, playing: String?, @Suppress("UNUSED_PARAMETER") tick: Int): List<String> =
    listOf("+ Ghi âm mới") + recs.map { r ->
        val on = playing == r.file.absolutePath
        val stamp = SimpleDateFormat("dd/MM HH:mm", Locale.getDefault()).format(Date(r.file.lastModified()))
        (if (on) "■ " else "▶ ") + stamp + "  " + (if (on) mmss(Play.pos()) + "/" else "") + mmss(r.dur)
    }

/** Dòng tiến trình khi đang nghe một bản ghi: ▶ 0:05 ███░░░░░░░ 0:29 */
fun recProgress(r: RecItem): String {
    val pos = Play.pos()
    val b = if (r.dur > 0) (pos * 10f / r.dur).toInt().coerceIn(0, 10) else 0
    return "▶ " + mmss(pos) + " " + "█".repeat(b) + "░".repeat(10 - b) + " " + mmss(r.dur)
}
