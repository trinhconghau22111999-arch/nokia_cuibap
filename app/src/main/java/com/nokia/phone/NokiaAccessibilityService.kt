package com.nokia.phone

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import android.widget.Toast

/** Trạng thái dùng chung giữa MainActivity (màn hình LCD, nửa trên) và KeypadActivity (bàn phím, nửa dưới). */
object NokiaState {
    @Volatile var lcdResumed = false
    @Volatile var keypadAlive = false
    var keypad: Activity? = null
    @Volatile var keypadTop = 0          // toạ độ y (px) của mép trên cửa sổ bàn phím
    var splitTried = false
    var openMenu = false
    @Volatile var pendingSms: String? = null    // số từ app khác (SENDTO sms:...) chờ mở màn hình soạn tin
    @Volatile var pendingDial: String? = null   // số từ app khác (ACTION_DIAL tel:...) chờ điền vào màn hình chờ
    var listener: ((String) -> Unit)? = null
}

/**
 * - Tự bật/tắt chế độ chia đôi màn hình.
 * - Điều khiển app ở nửa trên bằng bàn phím: con trỏ (D-pad), bấm (OK), bấm giữ (Gọi),
 *   quay lại (mũi tên), cuộn (D-pad chạm mép) và nhập chữ (phím số, kiểu bấm nhiều lần).
 */
class NokiaAccessibilityService : AccessibilityService() {
    companion object { @Volatile var instance: NokiaAccessibilityService? = null }

    private var cursor: View? = null
    private var lp: WindowManager.LayoutParams? = null
    private var cx = 0f
    private var cy = 0f
    private var cursorInit = false
    private var lastKey = ""
    private var lastTime = 0L
    private var idx = 0
    private var abc = false
    private val size: Int get() = (28 * resources.displayMetrics.density).toInt()

    override fun onServiceConnected() { super.onServiceConnected(); instance = this }
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}
    override fun onUnbind(intent: Intent?): Boolean { hideCursor(); instance = null; return super.onUnbind(intent) }

    fun toggleSplit() { performGlobalAction(GLOBAL_ACTION_TOGGLE_SPLIT_SCREEN) }

    /** Khóa và tắt màn hình (Android 9+); dùng cho lịch bật tắt nguồn. */
    fun lockScreen(): Boolean = Build.VERSION.SDK_INT >= 28 && performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN)

    // ---- Con trỏ ----
    private fun showCursor() {
        if (cursor != null) return
        val wm = getSystemService(WINDOW_SERVICE) as WindowManager
        val v = View(this)
        v.background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(Color.argb(150, 255, 59, 48))
            setStroke(4, Color.WHITE)
        }
        val p = WindowManager.LayoutParams(
            size, size, WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        )
        p.gravity = Gravity.TOP or Gravity.START
        try { wm.addView(v, p); cursor = v; lp = p } catch (_: Exception) {}
        // Ẩn bàn phím ảo của máy: bàn phím Nokia thay thế
        try { softKeyboardController.setShowMode(SHOW_MODE_HIDDEN) } catch (_: Exception) {}
    }

    fun hideCursor() {
        cursor?.let {
            try { (getSystemService(WINDOW_SERVICE) as WindowManager).removeView(it) } catch (_: Exception) {}
        }
        cursor = null; lp = null; cursorInit = false
        try { softKeyboardController.setShowMode(SHOW_MODE_AUTO) } catch (_: Exception) {}
    }

    private fun moveCursor() {
        val v = cursor ?: return
        val p = lp ?: return
        p.x = (cx - size / 2).toInt()
        p.y = (cy - size / 2).toInt()
        try { (getSystemService(WINDOW_SERVICE) as WindowManager).updateViewLayout(v, p) } catch (_: Exception) {}
    }

    // ---- Điều khiển app ở nửa trên ----
    fun control(k: String) {
        showCursor()
        val dm = resources.displayMetrics
        val w = dm.widthPixels.toFloat()
        val bottom = (if (NokiaState.keypadTop > 0) NokiaState.keypadTop else dm.heightPixels / 2).toFloat() - 8f
        if (!cursorInit) { cx = w / 2f; cy = bottom / 2f; cursorInit = true }
        val step = 80f
        val edge = 90f
        when (k) {
            "UP" -> if (cy <= edge) swipe(w / 2, bottom * 0.3f, w / 2, bottom * 0.75f) else cy -= step
            "DOWN" -> if (cy >= bottom - edge) swipe(w / 2, bottom * 0.75f, w / 2, bottom * 0.3f) else cy += step
            "LEFT" -> if (cx <= edge) swipe(w * 0.25f, cy, w * 0.8f, cy) else cx -= step
            "RIGHT" -> if (cx >= w - edge) swipe(w * 0.8f, cy, w * 0.25f, cy) else cx += step
            "OK" -> tap(cx, cy, 60)
            "CALL" -> tap(cx, cy, 700)           // bấm giữ
            "SOFTR" -> performGlobalAction(GLOBAL_ACTION_BACK)
            else -> typeKey(k)
        }
        cx = cx.coerceIn(0f, w)
        cy = cy.coerceIn(0f, bottom)
        moveCursor()
    }

    private fun tap(x: Float, y: Float, dur: Long) {
        val p = Path().apply { moveTo(x, y) }
        dispatchGesture(GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(p, 0, dur)).build(), null, null)
    }

    private fun swipe(x1: Float, y1: Float, x2: Float, y2: Float) {
        val p = Path().apply { moveTo(x1, y1); lineTo(x2, y2) }
        dispatchGesture(GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(p, 0, 280)).build(), null, null)
    }

    // ---- Nhập chữ ----
    private fun topAppRoot(): AccessibilityNodeInfo? {
        rootInActiveWindow?.let { if (it.packageName?.toString() != packageName) return it }
        val keyTop = if (NokiaState.keypadTop > 0) NokiaState.keypadTop else resources.displayMetrics.heightPixels / 2
        for (w in windows) {
            if (w.type != AccessibilityWindowInfo.TYPE_APPLICATION) continue
            val r = Rect()
            w.getBoundsInScreen(r)
            if (r.bottom > keyTop + 40) continue
            val root = w.root ?: continue
            if (root.packageName?.toString() == packageName) continue
            return root
        }
        return null
    }

    private fun typeKey(k: String) {
        if (k == "#") {
            abc = !abc
            Toast.makeText(this, if (abc) "abc" else "123", Toast.LENGTH_SHORT).show()
            return
        }
        val node = topAppRoot()?.findFocus(AccessibilityNodeInfo.FOCUS_INPUT) ?: return
        var cur = node.text?.toString() ?: ""
        if (Build.VERSION.SDK_INT >= 26 && node.isShowingHintText) cur = ""
        val now = SystemClock.uptimeMillis()
        val next: String
        if (k == "*") {                           // xoá một ký tự
            next = cur.dropLast(1)
            lastKey = ""
        } else if (!abc) {                        // chế độ số
            next = cur + k
            lastKey = ""
        } else {                                  // chế độ chữ: bấm nhiều lần để đổi chữ
            val cycle = when (k) {
                "1" -> "1.,?!"
                "0" -> " 0"
                else -> (LETTERS[k] ?: k).lowercase() + k
            }
            val again = k == lastKey && now - lastTime < 1000
            idx = if (again) (idx + 1) % cycle.length else 0
            val ch = cycle[idx].toString()
            next = if (again && cur.isNotEmpty()) cur.dropLast(1) + ch else cur + ch
            lastKey = k
            lastTime = now
        }
        val args = Bundle()
        args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, next)
        node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
    }
}
