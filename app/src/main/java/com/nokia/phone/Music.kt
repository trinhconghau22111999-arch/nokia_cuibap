package com.nokia.phone

import android.content.ContentUris
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore

/** Một bài nhạc. sd = true khi nằm trên thẻ nhớ (không phải bộ nhớ trong của máy). */
data class Track(val id: Long, val title: String, val artist: String, val dur: Long, val sd: Boolean)

/** Thư viện nhạc: đọc từ MediaStore nên có cả bài trong bộ nhớ máy lẫn thẻ nhớ (thẻ phải được hệ thống quét). */
object MusicLib {
    fun uri(id: Long): Uri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id)

    @Suppress("DEPRECATION")
    private fun isSdPath(path: String): Boolean {
        if (path.isEmpty()) return false
        val internal = Environment.getExternalStorageDirectory().absolutePath
        return !(path.startsWith(internal) || path.startsWith("/sdcard") || path.startsWith("/storage/emulated/"))
    }

    @Suppress("DEPRECATION")
    fun load(ctx: Context): List<Track> {
        val out = ArrayList<Track>()
        val q29 = Build.VERSION.SDK_INT >= 29
        val cols = arrayOf(
            MediaStore.Audio.Media._ID, MediaStore.Audio.Media.TITLE, MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.DURATION,
            if (q29) "volume_name" else MediaStore.Audio.Media.DATA     // Android 10+: tên ổ (external_primary = bộ nhớ máy)
        )
        try {
            ctx.contentResolver.query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, cols,
                MediaStore.Audio.Media.IS_MUSIC + " != 0", null, null
            )?.use { c ->
                while (c.moveToNext()) {
                    val title = c.getString(1)?.trim().orEmpty().ifEmpty { "(không tên)" }
                    var artist = c.getString(2)?.trim().orEmpty()
                    if (artist == "<unknown>") artist = ""
                    val src = c.getString(4).orEmpty()
                    val sd = if (q29) src.isNotEmpty() && src != "external_primary" && src != "internal" else isSdPath(src)
                    out.add(Track(c.getLong(0), title, artist, c.getLong(3), sd))
                }
            }
        } catch (_: Exception) {}
        return out.sortedBy { plain(it.title) }
    }
}

/** Bộ phát nhạc dùng chung; tự tạm dừng khi có cuộc gọi / app khác giành âm thanh. */
@Suppress("DEPRECATION")
object Music {
    private var mp: MediaPlayer? = null
    private var am: AudioManager? = null
    private var resumeOnGain = false

    var paused = false
        private set
    val active: Boolean get() = mp != null

    private val focus = AudioManager.OnAudioFocusChangeListener { change ->
        when (change) {
            AudioManager.AUDIOFOCUS_LOSS -> { resumeOnGain = false; pause() }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> if (isPlaying()) { resumeOnGain = true; pause() }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> try { mp?.setVolume(0.2f, 0.2f) } catch (_: Exception) {}
            AudioManager.AUDIOFOCUS_GAIN -> {
                try { mp?.setVolume(1f, 1f) } catch (_: Exception) {}
                if (resumeOnGain) { resumeOnGain = false; resume() }
            }
            else -> {}
        }
    }

    /** Phát bài t. onEnd: hát hết bài; onError: phát lỗi giữa chừng. Trả về false nếu không mở được file. */
    fun play(ctx: Context, t: Track, onEnd: () -> Unit, onError: () -> Unit): Boolean {
        stop()
        val app = ctx.applicationContext
        val a = app.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        am = a
        val m = MediaPlayer()
        return try {
            m.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build()
            )
            m.setDataSource(app, MusicLib.uri(t.id))
            m.prepare()
            m.setOnCompletionListener { stop(); onEnd() }
            m.setOnErrorListener { _, _, _ -> stop(); onError(); true }
            a.requestAudioFocus(focus, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN)
            m.start()
            mp = m
            paused = false
            true
        } catch (_: Exception) {
            try { m.release() } catch (_: Exception) {}
            false
        }
    }

    fun stop() {
        val m = mp
        mp = null
        paused = false
        resumeOnGain = false
        if (m != null) {
            try { m.stop() } catch (_: Exception) {}
            try { m.release() } catch (_: Exception) {}
            try { am?.abandonAudioFocus(focus) } catch (_: Exception) {}
        }
    }

    fun isPlaying(): Boolean = try { mp?.isPlaying == true } catch (_: Exception) { false }

    fun pause() {
        try { if (mp?.isPlaying == true) { mp?.pause(); paused = true } } catch (_: Exception) {}
    }

    fun resume() {
        try { if (mp != null && paused) { mp?.start(); paused = false } } catch (_: Exception) {}
    }

    fun toggle() { if (paused) resume() else pause() }

    /** Tua tới / lui deltaMs (âm = lui). */
    fun seek(deltaMs: Long) {
        val m = mp ?: return
        try {
            val target = (m.currentPosition + deltaMs).coerceIn(0L, maxOf(0L, m.duration - 500L))
            m.seekTo(target.toInt())
        } catch (_: Exception) {}
    }

    fun pos(): Long = try { (mp?.currentPosition ?: 0).toLong() } catch (_: Exception) { 0L }
    fun dur(): Long = try { (mp?.duration ?: 0).toLong() } catch (_: Exception) { 0L }
}

/** Danh sách hiển thị: ▶ bài đang phát, ‖ bài đang tạm dừng. */
fun musicLines(tracks: List<Track>, cur: Int, paused: Boolean): List<String> =
    tracks.mapIndexed { i, t -> (if (i == cur) (if (paused) "‖ " else "▶ ") else "  ") + t.title }

/** ▶ 0:05 ███░░░░░░░ 3:21 */
fun musicProgress(): String {
    val pos = Music.pos()
    val d = Music.dur()
    val b = if (d > 0) (pos * 10f / d).toInt().coerceIn(0, 10) else 0
    return (if (Music.paused) "‖ " else "▶ ") + mmss(pos) + " " + "█".repeat(b) + "░".repeat(10 - b) + " " + mmss(d)
}

/** Ca sĩ · thời lượng · nơi lưu (Máy / Thẻ nhớ). */
fun trackInfo(t: Track): String =
    (if (t.artist.isNotEmpty()) t.artist + " · " else "") + mmss(t.dur) + " · " + (if (t.sd) "Thẻ nhớ" else "Máy")
