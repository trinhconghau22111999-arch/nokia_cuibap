package com.nokia.phone

import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

/**
 * Khung màn hình Nokia dùng lại cho các màn hình ngoài MainActivity (cuộc gọi, ghi âm...):
 * thân máy xanh + màn hình LCD + hàng phím mềm + bàn phím. Khi đang chia đôi màn hình thì
 * không vẽ bàn phím (đã có KeypadActivity ở nửa dưới, phím được chuyển qua NokiaState.listener).
 */
abstract class LcdActivity : ComponentActivity() {
    /** true = hiện trên màn hình khóa và bật sáng màn hình (dùng cho cuộc gọi). */
    protected open val overLock = false
    protected var split by mutableStateOf(false)

    private var prevListener: ((String) -> Unit)? = null

    /** Nhận phím từ bàn phím ảo, bàn phím cứng hoặc KeypadActivity: "0".."9", "*", "#", UP, DOWN, LEFT, RIGHT, OK, SOFTL, SOFTR, CALL, END. */
    abstract fun onKey(k: String)

    @Suppress("DEPRECATION")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (overLock) {
            if (Build.VERSION.SDK_INT >= 27) {
                setShowWhenLocked(true)
                setTurnScreenOn(true)
            } else {
                window.addFlags(
                    WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
                )
            }
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        WindowCompat.setDecorFitsSystemWindows(window, false)
        if (Build.VERSION.SDK_INT >= 28) {
            window.attributes.layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
        window.statusBarColor = android.graphics.Color.TRANSPARENT
        hideBars()
        split = isInMultiWindowMode
    }

    protected fun setLcd(left: () -> String, right: () -> String, lcd: @Composable BoxScope.() -> Unit) {
        setContent { NokiaFrame(left(), right(), split, { k -> onKey(k) }, lcd) }
    }

    protected fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_SHORT).show()

    private fun hideBars() {
        val c = WindowInsetsControllerCompat(window, window.decorView)
        c.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        c.hide(WindowInsetsCompat.Type.navigationBars())
        c.show(WindowInsetsCompat.Type.statusBars())
        c.isAppearanceLightStatusBars = true
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideBars()
    }

    override fun onMultiWindowModeChanged(isInMultiWindowMode: Boolean, newConfig: Configuration) {
        super.onMultiWindowModeChanged(isInMultiWindowMode, newConfig)
        split = isInMultiWindowMode
    }

    override fun onResume() {
        super.onResume()
        // Nhận phím từ KeypadActivity khi đang chia đôi màn hình
        prevListener = NokiaState.listener
        NokiaState.listener = { k -> onKey(k) }
        NokiaState.lcdResumed = true
        NokiaAccessibilityService.instance?.hideCursor()
    }

    override fun onPause() {
        NokiaState.lcdResumed = false
        NokiaState.listener = prevListener     // trả phím lại cho MainActivity
        super.onPause()
    }
}

@Composable
fun NokiaFrame(
    left: String, right: String, split: Boolean,
    press: (String) -> Unit, lcd: @Composable BoxScope.() -> Unit
) {
    val focus = remember { FocusRequester() }
    val haptic = LocalHapticFeedback.current
    val p: (String) -> Unit = { k ->
        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        press(k)
    }
    LaunchedEffect(Unit) { focus.requestFocus() }
    Column(
        Modifier.fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF4469BC), Color(0xFF223C78))))
            .padding(bottom = 10.dp)
            .focusRequester(focus).focusable()
            .onKeyEvent { e ->
                if (e.type != KeyEventType.KeyDown) return@onKeyEvent false
                val c = e.utf16CodePoint.toChar()
                when {
                    e.key == Key.DirectionUp -> p("UP")
                    e.key == Key.DirectionDown -> p("DOWN")
                    e.key == Key.DirectionLeft -> p("LEFT")
                    e.key == Key.DirectionRight -> p("RIGHT")
                    e.key == Key.DirectionCenter || e.key == Key.Enter -> p("OK")
                    e.key == Key.Backspace -> p("SOFTR")
                    c.isDigit() || c == '*' || c == '#' -> p(c.toString())
                    else -> return@onKeyEvent false
                }
                true
            }
    ) {
        Box(Modifier.fillMaxWidth().weight(0.72f).background(LCD)) {
            LcdFontScale {
            Column(Modifier.fillMaxSize().background(LCD).statusBarsPadding()) {
                Box(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 6.dp, vertical = 2.dp), content = lcd)
                // Hai nút mềm chỉ rộng vừa chữ (vùng bấm/nền sáng khi chạm không kéo dài nửa màn hình)
                Row(Modifier.fillMaxWidth()) {
                    Box(Modifier.clickable { p("SOFTL") }.padding(horizontal = 8.dp, vertical = 5.dp)) {
                        Text(left, color = INK, fontWeight = FontWeight.Bold, fontFamily = MONO, fontSize = 13.sp)
                    }
                    Spacer(Modifier.weight(1f))
                    Box(Modifier.clickable { p("SOFTR") }.padding(horizontal = 8.dp, vertical = 5.dp)) {
                        Text(right, color = INK, fontWeight = FontWeight.Bold, fontFamily = MONO, fontSize = 13.sp)
                    }
                }
            }
            }
        }
        // Viền đen dày ngăn cách màn hình LCD (trên) và bàn phím (dưới)
        Box(Modifier.fillMaxWidth().height(6.dp).background(INK))
        if (!split) {
            Spacer(Modifier.height(6.dp))
            Keys(p)
        }
    }
}
