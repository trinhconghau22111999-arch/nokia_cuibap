package com.nokia.phone.tvremote

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.database.ChildEventListener
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.nokia.phone.tvremote.webrtc.PeerConnectionManager
import com.nokia.phone.tvremote.webrtc.SignalingClient
import org.webrtc.EglBase

private const val TAG = "RemoteLink"

/** Số TV tối đa điều khiển cùng lúc. */
const val MAX_TV = 4

/** Trạng thái kết nối riêng của từng TV đang được điều khiển. */
private class TvConn(val viewerId: String) {
    var signalingClient: SignalingClient? = null
    var peerConnectionManager: PeerConnectionManager? = null
    var reconnectAttempt = 0
    var removed = false
    var reconnectRunnable: Runnable? = null
}

/**
 * Cầu nối tới app TV (dùng lại đường truyền tệp có sẵn của app TV): mã phòng cố định 123456,
 * tín hiệu WebRTC qua Firebase Realtime Database, lệnh đi qua DataChannel.
 * Chạy nền (foreground service) để giữ kết nối khi tắt màn hình; chỉ chạy trong lúc đang mở
 * app con "Điều khiển TV" - rời app con là dừng.
 */
object RemoteLink {
    /** Mã ghép nối cố định, khớp TransferHost.CODE bên app TV. */
    const val CODE = "123456"

    /**
     * Cấu hình Firebase (cùng dự án với app TV / truyentep.html) nạp thẳng trong code, nên không
     * cần google-services.json và không cần plugin google-services.
     */
    private fun ensureFirebase(ctx: Context) {
        val app = ctx.applicationContext
        if (FirebaseApp.getApps(app).isEmpty()) {
            FirebaseApp.initializeApp(
                app,
                FirebaseOptions.Builder()
                    .setApiKey("AIzaSyCPwMKElbsQLjOeSVddGAfjNiHnPHV9b1A")
                    .setApplicationId("1:949360436062:web:c9b5b5d47ee4f76fdce5cf")
                    .setDatabaseUrl("https://qrremod-default-rtdb.asia-southeast1.firebasedatabase.app")
                    .setProjectId("qrremod")
                    .setGcmSenderId("949360436062")
                    .setStorageBucket("qrremod.firebasestorage.app")
                    .build()
            )
        }
    }

    fun start(ctx: Context) {
        try {
            ensureFirebase(ctx)
            // updateChildren (không setValue) để KHÔNG xoá nhánh "viewers" mà TV đang chờ.
            FirebaseDatabase.getInstance().reference.child("rooms").child(CODE).updateChildren(
                mapOf("status" to "waiting", "consentGivenAt" to System.currentTimeMillis())
            )
            ContextCompat.startForegroundService(
                ctx.applicationContext,
                Intent(ctx.applicationContext, RemoteLinkService::class.java)
                    .putExtra(RemoteLinkService.EXTRA_ROOM_CODE, CODE)
            )
        } catch (e: Throwable) {
            Log.e(TAG, "Không bật được kết nối TV: ${e.message}", e)
        }
    }

    fun stop(ctx: Context) {
        val svc = RemoteLinkService.instance
        if (svc != null) svc.stopSharingNow()
        else try { ctx.applicationContext.stopService(Intent(ctx.applicationContext, RemoteLinkService::class.java)) } catch (_: Throwable) {}
    }

    /** Gửi 1 lệnh JSON tới các TV đang kết nối (gọi ngoài luồng UI). @return số TV nhận được. */
    fun send(json: String): Int = RemoteLinkService.instance?.sendRemoteCommand(json) ?: 0
}

class RemoteLinkService : Service() {

    /** Báo số TV đang kết nối mỗi khi thay đổi (UI cập nhật chấm xanh/đỏ). */
    interface ConnectionStatusListener {
        fun onConnectionStatusChanged(connectedCount: Int, maxViewers: Int)
    }
    private val statusListeners = java.util.concurrent.CopyOnWriteArrayList<ConnectionStatusListener>()

    fun addConnectionStatusListener(listener: ConnectionStatusListener) {
        statusListeners.add(listener)
        listener.onConnectionStatusChanged(connectedCount(), MAX_TV)
    }

    fun removeConnectionStatusListener(listener: ConnectionStatusListener) { statusListeners.remove(listener) }

    private fun connectedCount(): Int =
        conns.values.count { it.peerConnectionManager != null && it.reconnectAttempt == 0 }

    private fun notifyStatusListeners() {
        val count = connectedCount()
        handler.post { statusListeners.forEach { it.onConnectionStatusChanged(count, MAX_TV) } }
    }

    private val conns = LinkedHashMap<String, TvConn>()
    private var viewersRef: DatabaseReference? = null
    private var viewersListener: ChildEventListener? = null

    private val eglBase: EglBase = EglBase.create()
    private var peerFactory: org.webrtc.PeerConnectionFactory? = null
    private var roomCode: String? = null
    private var stopping = false
    private var watchStarted = false
    private var wakeLock: PowerManager.WakeLock? = null
    private val handler = Handler(Looper.getMainLooper())

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        instance = this
    }

    /** Gửi 1 lệnh (JSON {"type":"remote",...}) tới mọi TV đang kết nối. @return số TV nhận được. */
    fun sendRemoteCommand(json: String): Int {
        var ok = 0
        for (conn in conns.values.toList()) {
            val pcm = conn.peerConnectionManager ?: continue
            if (pcm.sendJsonMessage(json)) ok++
        }
        return ok
    }

    /** Dừng NGAY, đồng bộ; gọi lại nhiều lần vẫn an toàn. */
    fun stopSharingNow() {
        stopping = true
        cleanupSession()
        markRoomEnded()
        stopSelf()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP_SHARING) {
            stopSharingNow()
            return START_NOT_STICKY
        }
        roomCode = intent?.getStringExtra(EXTRA_ROOM_CODE) ?: roomCode

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIF_ID, buildNotification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(NOTIF_ID, buildNotification())
        }
        if (!notifQuiet) handler.postDelayed({ notifQuiet = true; updateNotification() }, 1000)
        acquireWakeLock()

        val code = roomCode
        if (code != null && !watchStarted) startWatching(code)
        else if (code == null) { Log.e(TAG, "Thiếu mã phòng"); stopSelf() }
        return START_NOT_STICKY
    }

    private fun acquireWakeLock() {
        if (wakeLock != null) return
        val pm = getSystemService(POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "NokiaPhone:RemoteLink").also {
            it.setReferenceCounted(false)
            it.acquire(30 * 60 * 1000L)   // tự nhả sau 30 phút phòng khi quên tắt
        }
    }

    private fun startWatching(code: String) {
        watchStarted = true
        peerFactory = peerFactory ?: PeerConnectionManager.createFactory(this, eglBase)
        watchViewers(code)
    }

    /**
     * Theo dõi danh sách "viewer" (rooms/{code}/viewers): app TV xuất hiện ở đây như một viewer.
     * Node không có "present" là rác của lần chạy trước -> xoá, không cấp kênh.
     */
    private fun watchViewers(code: String) {
        val ref = FirebaseDatabase.getInstance().reference.child("rooms").child(code).child("viewers")
        viewersRef = ref
        val listener = object : ChildEventListener {
            override fun onChildAdded(snapshot: DataSnapshot, prevKey: String?) {
                val id = snapshot.key ?: return
                if (conns.containsKey(id)) return
                if (!snapshot.hasChild("present")) { snapshot.ref.removeValue(); return }
                if (conns.size >= MAX_TV) return
                val conn = TvConn(id)
                conn.reconnectRunnable = Runnable { if (!conn.removed) connect(code, conn) }
                conns[id] = conn
                connect(code, conn)
            }
            override fun onChildRemoved(snapshot: DataSnapshot) {
                val id = snapshot.key ?: return
                val conn = conns.remove(id) ?: return
                conn.removed = true
                conn.reconnectRunnable?.let { handler.removeCallbacks(it) }
                conn.peerConnectionManager?.release()
                conn.signalingClient?.release()
                updateNotification()
            }
            override fun onChildChanged(snapshot: DataSnapshot, prevKey: String?) {}
            override fun onChildMoved(snapshot: DataSnapshot, prevKey: String?) {}
            override fun onCancelled(error: DatabaseError) {
                Log.e(TAG, "Mất quyền đọc rooms/$code/viewers: ${error.code} ${error.message}")
            }
        }
        viewersListener = listener
        ref.addChildEventListener(listener)
    }

    /** Mở kênh WebRTC (chỉ DataChannel, không video) tới 1 TV. */
    private fun connect(code: String, conn: TvConn) {
        if (stopping || conn.removed) return
        val factory = peerFactory ?: return

        var sigRef: SignalingClient? = null
        var pcmRef: PeerConnectionManager? = null
        val sig = SignalingClient(
            roomCode = code, viewerId = conn.viewerId, isHost = true,
            listener = object : SignalingClient.Listener {
                override fun onOfferReceived(sdp: String) {}
                override fun onAnswerReceived(sdp: String) { conn.peerConnectionManager?.handleAnswer(sdp) }
                override fun onIceCandidateReceived(sdpMid: String, sdpMLineIndex: Int, candidate: String) {
                    conn.peerConnectionManager?.addIceCandidate(sdpMid, sdpMLineIndex, candidate)
                }
                override fun onRemoteDisconnected() {
                    if (conn.signalingClient !== sigRef) return
                    scheduleReconnect(code, conn)
                }
            }
        )
        sigRef = sig
        conn.signalingClient = sig

        val pcm = PeerConnectionManager(
            factory = factory, isHost = true, signalingClient = sig, remoteSink = null,
            onConnected = {
                conn.reconnectAttempt = 0
                conn.reconnectRunnable?.let { handler.removeCallbacks(it) }
                updateNotification()
            },
            onDisconnected = {
                if (conn.peerConnectionManager === pcmRef) scheduleReconnect(code, conn)
            },
            onFileReceived = { _, _, _ -> /* remote chỉ gửi lệnh, bỏ qua tệp */ }
        )
        pcmRef = pcm
        conn.peerConnectionManager = pcm
        pcm.init()
        pcm.startFileTransferOffer()
        updateNotification()
    }

    /** Nối lại riêng từng TV với thời gian chờ tăng dần. */
    private fun scheduleReconnect(code: String, conn: TvConn) {
        if (stopping || conn.removed) return
        conn.peerConnectionManager?.release()
        conn.peerConnectionManager = null
        conn.signalingClient?.release()
        conn.signalingClient = null

        conn.reconnectRunnable?.let { handler.removeCallbacks(it) }
        val delay = minOf(BASE_RECONNECT_DELAY_MS * (1 shl conn.reconnectAttempt.coerceAtMost(5)), MAX_RECONNECT_DELAY_MS)
        conn.reconnectAttempt++
        conn.reconnectRunnable?.let { handler.postDelayed(it, delay) }
        updateNotification()
    }

    private fun markRoomEnded() {
        roomCode?.let { code ->
            try {
                val roomRef = FirebaseDatabase.getInstance().reference.child("rooms").child(code)
                roomRef.child("status").setValue("ended")
                roomRef.child("viewers").removeValue()
            } catch (_: Throwable) {}
        }
    }

    private var notifQuiet = false

    private fun buildNotification(): android.app.Notification {
        val channelId = if (notifQuiet) "tv_remote_quiet" else "tv_remote"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(channelId, "Điều khiển TV",
                    if (notifQuiet) NotificationManager.IMPORTANCE_MIN else NotificationManager.IMPORTANCE_LOW)
            )
        }
        val stopIntent = PendingIntent.getService(
            this, 0,
            Intent(this, RemoteLinkService::class.java).apply { action = ACTION_STOP_SHARING },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(this, channelId)
            .setContentTitle("Đang điều khiển TV")
            .setContentText(if (connectedCount() > 0) "Đã kết nối TV." else "Đang chờ kết nối TV...")
            .setSmallIcon(android.R.drawable.ic_menu_view)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(if (notifQuiet) NotificationCompat.PRIORITY_MIN else NotificationCompat.PRIORITY_LOW)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Kết thúc", stopIntent)
            .build()
    }

    private fun updateNotification() {
        try { getSystemService(NotificationManager::class.java).notify(NOTIF_ID, buildNotification()) } catch (_: Throwable) {}
        notifyStatusListeners()
    }

    private var sessionCleanedUp = false

    private fun cleanupSession() {
        if (sessionCleanedUp) return
        sessionCleanedUp = true
        conns.values.forEach { conn ->
            conn.removed = true
            conn.reconnectRunnable?.let { handler.removeCallbacks(it) }
            conn.peerConnectionManager?.release()
            conn.signalingClient?.release()
        }
        conns.clear()
        viewersListener?.let { viewersRef?.removeEventListener(it) }
        viewersListener = null
        viewersRef = null

        peerFactory?.dispose()
        peerFactory = null
        eglBase.release()
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
        notifyStatusListeners()
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        stopping = true
        cleanupSession()
        markRoomEnded()
        instance = null
        stopSelf()
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        stopping = true
        cleanupSession()
        instance = null
        super.onDestroy()
    }

    companion object {
        const val EXTRA_ROOM_CODE = "extra_room_code"
        const val ACTION_STOP_SHARING = "action_stop_sharing"
        private const val NOTIF_ID = 43
        private const val BASE_RECONNECT_DELAY_MS = 2000L
        private const val MAX_RECONNECT_DELAY_MS = 30_000L

        @Volatile var instance: RemoteLinkService? = null
            private set
    }
}
